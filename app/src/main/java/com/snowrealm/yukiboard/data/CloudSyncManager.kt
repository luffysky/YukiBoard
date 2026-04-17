package com.snowrealm.yukiboard.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.snowrealm.yukiboard.app.session.UserSessionRepository
import com.snowrealm.yukiboard.input.dictionary.UserDictionary
import com.snowrealm.yukiboard.input.smart.*
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * YukiBoard 雲端同步管理器。
 *
 * 同步範圍：
 * 1. 偏好設定（YukiPreferences → user_settings）
 * 2. 自訂詞庫（UserDictionary → user_dictionary）
 * 3. 主題設定（背景/流光/角色 → user_themes）
 * 4. 學習資料（7 種智慧輸入系統 → user_learning_data）
 *    - AdaptiveDictionary（詞頻學習）
 *    - PersonalDictionary（三層自動學詞）
 *    - PhrasePredictor（bigram/trigram）
 *    - SentencePredictor（整句預測）
 *    - StyleProfiler（語氣風格）
 *    - TypingHabitTracker（打字習慣）
 *    - CorrectionLearnManager（校正學習）
 *
 * 帳號系統綁定 Insight Engine（經 `UserSessionRepository`）。
 * 資料存 Supabase（`SupabaseClient`）。
 *
 * Sprint 7-3 收斂：
 * - auth 直達 `SnowRealmAuthClient` 已改走 `UserSessionRepository`
 * - `/api/auth/supabase-token` exchange 改走 `InsightApiClient`（Insight 唯一 gateway）
 *
 * Sprint 7-4 收斂：
 * - `YukiPreferences` 38 欄設定直讀 → `prefs.exportSettingsSnapshot()` / `importSettingsSnapshot()`
 * - 主題 8 欄位直讀 → `prefs.exportThemeSnapshot()` / `importThemeSnapshot()`
 * - 7 組 subsystem pref schema（`yukiboard_adaptive_dict` 等）直讀 →
 *   各 subsystem 自有的 `exportSnapshot()` / `importSnapshot()`，schema 不再外漏。
 * - 本類回歸單一職責：只負責 Supabase gateway + 排程（24h / 50 commits）。
 */
class CloudSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSync"
        private const val PREF_LAST_SYNC = "cloud_sync_last_time"
        private const val PREF_SYNC_ENABLED = "cloud_sync_enabled"
        private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24 小時同步一次
        private const val SYNC_ON_COMMIT_THRESHOLD = 50  // 每提交 50 次觸發一次同步

        @Volatile
        private var instance: CloudSyncManager? = null

        fun getInstance(context: Context): CloudSyncManager {
            return instance ?: synchronized(this) {
                instance ?: CloudSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val supabase = SupabaseClient.getInstance()
    private val session = UserSessionRepository(context)
    private val insightApi = InsightApiClient.getInstance(context)
    private val prefs = YukiPreferences(context)
    private val metaPrefs: SharedPreferences =
        context.getSharedPreferences("cloud_sync_meta", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val isSyncEnabled: Boolean get() = metaPrefs.getBoolean(PREF_SYNC_ENABLED, false)
    val lastSyncTime: Long get() = metaPrefs.getLong(PREF_LAST_SYNC, 0)

    private var commitsSinceLastSync = 0

    /**
     * S9-C：long-lived `SmartInputManager` 的 `applyCloudSnapshot` callback。
     *
     * 由 `YukiBoardIMEService` 在 IME alive 時注入；下載學習資料時若非 null，
     * 走長生命週期路徑（prefs 寫入 + in-memory reload 同步發生於 subsystem 內），
     * 避免過去「throwaway instance 寫 prefs，但 long-lived subsystem 記憶體未同步」的不一致。
     *
     * 為 null 時走既有 throwaway 路徑（僅寫 prefs，下次 cold start 才生效）—
     * 登入流程若在 IME 未啟動前跑完也能保底寫入，不讓同步行為消失。
     */
    private var smartInputApplier: ((JsonObject) -> Unit)? = null

    /** 由 long-lived owner（例如 IMEService）注入；傳 null 可解除註冊。 */
    fun setSmartInputApplier(applier: ((JsonObject) -> Unit)?) {
        this.smartInputApplier = applier
    }

    /**
     * 取得 userId，若 token 存在但 userId 未儲存（OAuth 回呼時 fetchMe 失敗），
     * 會走 `UserSessionRepository.resolveVerifiedUserId()` 補齊使用者資料。
     *
     * 注意：此路徑與 `SettingsScreenCoordinator.verifySessionAsync` 形成 R-1 雙 fetchMe，
     * 屬 Sprint 8 bugfix 範圍，本輪不整併。
     */
    private suspend fun resolveUserId(): String? = session.resolveVerifiedUserId()

    // ─────────────────────────────────────────
    // Supabase Token 交換（Insight JWT → Supabase JWT）
    // ─────────────────────────────────────────

    private var cachedSupabaseToken: String? = null
    private var supabaseTokenExpiry: Long = 0

    /**
     * 取得 YukiBoard Supabase 能認的 JWT。
     * 流程：`InsightApiClient.post("/api/auth/supabase-token")` → 換回 Supabase JWT。
     * 快取 1 小時，過期前 5 分鐘自動重新換。
     *
     * Sprint 7-3：exchange HTTP 直打已改走 `InsightApiClient`（Insight 唯一 gateway）。
     * 行為差異：`InsightApiClient` 自帶 401 refresh + retry；原本直打拿到 401 會 return null。
     * 若 exchange 端點回 401 代表根本沒 session，retry 也無意義但不致命，等價於 return null。
     * Body 從空 `ByteArray` 改為空 JSON `{}`；若 Insight 後端嚴格檢查 body，行為可能不同。
     */
    private suspend fun getSupabaseToken(): String? {
        // 快取有效就直接用
        val now = System.currentTimeMillis()
        cachedSupabaseToken?.let { token ->
            if (now < supabaseTokenExpiry - 5 * 60 * 1000) return token
        }

        if (session.getAuthToken() == null) return null

        val result = insightApi.post("/api/auth/supabase-token", buildJsonObject {})
        return when (result) {
            is InsightApiClient.ApiResult.Success -> {
                val obj = result.data.jsonObject
                val token = obj["token"]?.jsonPrimitive?.contentOrNull
                val expiresIn = obj["expiresIn"]?.jsonPrimitive?.longOrNull ?: 3600
                if (token != null) {
                    cachedSupabaseToken = token
                    supabaseTokenExpiry = now + expiresIn * 1000
                    Log.d(TAG, "Supabase token exchanged, expires in ${expiresIn}s")
                }
                token
            }
            is InsightApiClient.ApiResult.Error -> {
                Log.e(TAG, "supabase-token exchange failed: ${result.code} ${result.message}")
                null
            }
            is InsightApiClient.ApiResult.Unauthorized -> null
            is InsightApiClient.ApiResult.NetworkError -> {
                Log.e(TAG, "supabase-token exchange network error: ${result.message}")
                null
            }
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        metaPrefs.edit().putBoolean(PREF_SYNC_ENABLED, enabled).apply()
    }

    /** 每次輸入 session 結束時呼叫（由 IMEService.onFinishInputView 觸發） */
    suspend fun onSessionEnd() {
        if (!isSyncEnabled) return
        if (!session.isLoggedIn()) return

        commitsSinceLastSync++
        val now = System.currentTimeMillis()
        val timeSinceSync = now - lastSyncTime

        // 超過 24 小時 或 累積 50 次 session → 上傳學習資料 + 詞庫
        if (timeSinceSync > SYNC_INTERVAL_MS || commitsSinceLastSync >= SYNC_ON_COMMIT_THRESHOLD) {
            commitsSinceLastSync = 0
            val userId = resolveUserId() ?: return
            val token = getSupabaseToken()
            try {
                uploadLearningData(userId, token)
                uploadDictionary(userId, token)
                metaPrefs.edit().putLong(PREF_LAST_SYNC, now).apply()
                Log.d(TAG, "Auto sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Auto sync failed", e)
            }
        }
    }

    /** 登入後立即同步（下載雲端資料到本地） */
    suspend fun syncOnLogin(): SyncResult {
        val userId = resolveUserId() ?: return SyncResult.NotLoggedIn
        return try {
            downloadAll()
        } catch (e: Exception) {
            Log.e(TAG, "syncOnLogin failed", e)
            SyncResult.Error("登入同步失敗: ${e.message}")
        }
    }

    // ─────────────────────────────────────────
    // 上傳（本地 → 雲端）
    // ─────────────────────────────────────────

    /** 全量上傳（偏好 + 詞庫 + 主題 + 學習資料） */
    suspend fun uploadAll(): SyncResult = withContext(Dispatchers.IO) {
        val userId = resolveUserId() ?: return@withContext SyncResult.NotLoggedIn
        if (!supabase.isConfigured) return@withContext SyncResult.Error("Supabase 未設定")
        val token = getSupabaseToken() ?: return@withContext SyncResult.Error("無法取得 Supabase token")

        try {
            val r1 = uploadSettings(userId, token)
            val r2 = uploadDictionary(userId, token)
            val r3 = uploadThemes(userId, token)
            val r4 = uploadLearningData(userId, token)

            metaPrefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()

            val errors = listOfNotNull(
                (r1 as? SyncResult.Error)?.message,
                (r2 as? SyncResult.Error)?.message,
                (r3 as? SyncResult.Error)?.message,
                (r4 as? SyncResult.Error)?.message
            )
            if (errors.isEmpty()) SyncResult.Success
            else SyncResult.Error("部分失敗: ${errors.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "uploadAll failed", e)
            SyncResult.Error("上傳失敗: ${e.message}")
        }
    }

    /** 上傳偏好設定 */
    private suspend fun uploadSettings(userId: String, token: String?): SyncResult {
        val data = buildJsonObject {
            put("user_id", userId)
            put("settings", prefs.exportSettingsSnapshot())
            put("updated_at", JsonPrimitive("now()"))
        }
        return upsert("user_settings", "user_id=eq.$userId", data, token)
    }

    /** 上傳自訂詞庫 */
    private suspend fun uploadDictionary(userId: String, token: String?): SyncResult {
        val userDict = UserDictionary(context)
        val entries = userDict.getAllManualWithCategory()
        if (entries.isEmpty()) return SyncResult.Success

        // 批次 upsert — 逐筆插入（Supabase REST 不支援原生 upsert array）
        var errorCount = 0
        for ((word, bopomofo, category) in entries) {
            val data = buildJsonObject {
                put("user_id", userId)
                put("word", word)
                put("bopomofo", bopomofo)
                put("category", category)
                put("deleted", false)
                put("updated_at", JsonPrimitive("now()"))
            }
            val result = upsert(
                "user_dictionary",
                "user_id=eq.$userId&word=eq.${encode(word)}",
                data, token
            )
            if (result is SyncResult.Error) errorCount++
        }

        return if (errorCount == 0) SyncResult.Success
        else SyncResult.Error("詞庫上傳 $errorCount 筆失敗")
    }

    /** 上傳主題設定 */
    private suspend fun uploadThemes(userId: String, token: String?): SyncResult {
        val data = buildJsonObject {
            put("user_id", userId)
            put("theme_config", prefs.exportThemeSnapshot())
            put("updated_at", JsonPrimitive("now()"))
        }
        return upsert("user_themes", "user_id=eq.$userId", data, token)
    }

    // ─────────────────────────────────────────
    // 下載（雲端 → 本地）
    // ─────────────────────────────────────────

    /** 全量下載（偏好 + 詞庫 + 主題 + 學習資料） */
    suspend fun downloadAll(): SyncResult = withContext(Dispatchers.IO) {
        val userId = resolveUserId() ?: return@withContext SyncResult.NotLoggedIn
        if (!supabase.isConfigured) return@withContext SyncResult.Error("Supabase 未設定")
        val token = getSupabaseToken() ?: return@withContext SyncResult.Error("無法取得 Supabase token")

        try {
            val r1 = downloadSettings(userId, token)
            val r2 = downloadDictionary(userId, token)
            val r3 = downloadThemes(userId, token)
            val r4 = downloadLearningData(userId, token)

            metaPrefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()

            val errors = listOfNotNull(
                (r1 as? SyncResult.Error)?.message,
                (r2 as? SyncResult.Error)?.message,
                (r3 as? SyncResult.Error)?.message,
                (r4 as? SyncResult.Error)?.message
            )
            if (errors.isEmpty()) SyncResult.Success
            else SyncResult.Error("部分失敗: ${errors.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "downloadAll failed", e)
            SyncResult.Error("下載失敗: ${e.message}")
        }
    }

    /** 下載偏好設定 */
    private suspend fun downloadSettings(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_settings",
            "user_id=eq.$userId&select=settings",
            token
        )
        val settingsObj = rows?.firstOrNull()
            ?.jsonObject?.get("settings")?.jsonObject
            ?: return SyncResult.Success // 雲端無資料，不算錯

        prefs.importSettingsSnapshot(settingsObj)
        return SyncResult.Success
    }

    /** 下載自訂詞庫 */
    private suspend fun downloadDictionary(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_dictionary",
            "user_id=eq.$userId&deleted=eq.false&select=word,bopomofo,category",
            token
        ) ?: return SyncResult.Success

        val userDict = UserDictionary(context)
        for (row in rows) {
            val obj = row.jsonObject
            val word = obj["word"]?.jsonPrimitive?.contentOrNull ?: continue
            val bopomofo = obj["bopomofo"]?.jsonPrimitive?.contentOrNull ?: continue
            val category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "一般"
            // 只新增本地不存在的詞（不覆蓋本地修改）
            val existing = userDict.getAllManual().map { it.first }
            if (word !in existing) {
                userDict.addManual(word, bopomofo, category)
            }
        }
        return SyncResult.Success
    }

    /** 下載主題設定 */
    private suspend fun downloadThemes(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_themes",
            "user_id=eq.$userId&select=theme_config",
            token
        )
        val themeObj = rows?.firstOrNull()
            ?.jsonObject?.get("theme_config")?.jsonObject
            ?: return SyncResult.Success

        prefs.importThemeSnapshot(themeObj)
        return SyncResult.Success
    }

    // ─────────────────────────────────────────
    // 學習資料 上傳/下載
    // ─────────────────────────────────────────

    /**
     * 上傳所有學習資料（7 種系統合併為一筆）。
     *
     * Sprint 7-4：payload 組裝全部改走各 subsystem 自有 `exportSnapshot()`，
     * 本類不再直讀 7 組子系統的 SharedPreferences schema。
     */
    private suspend fun uploadLearningData(userId: String, token: String?): SyncResult {
        val data = buildJsonObject {
            put("user_id", userId)
            put("adaptive_dict", AdaptiveDictionary(context).exportSnapshot())
            put("personal_dict", PersonalDictionary(context).exportSnapshot())
            put("phrase_predict", PhrasePredictor(context).exportSnapshot())
            put("sentence_predict", SentencePredictor(context).exportSnapshot())
            put("style_profile", StyleProfiler(context).exportSnapshot())
            put("typing_habits", TypingHabitTracker(context).exportSnapshot())
            put("correction_learn", CorrectionLearnManager(context).exportSnapshot())
            put("updated_at", JsonPrimitive("now()"))
        }
        return upsert("user_learning_data", "user_id=eq.$userId", data, token)
    }

    /**
     * 下載所有學習資料。
     *
     * Sprint 7-4：merge 策略改由各 subsystem 自己的 `importSnapshot()` 決定，
     * 本類不再內建 freq/tier 合併邏輯（已移回 AdaptiveDictionary / PersonalDictionary）。
     *
     * Sprint 9-C（方案 B + A）：
     * - subsystem `importSnapshot()` 已於 S9-C 內建 `loadAll()`，同一 instance prefs + 記憶體同步。
     * - 若 `smartInputApplier` 已注入（long-lived `SmartInputManager` 存活），走 long-lived 路徑
     *   讓 IME 同 session 內後續候選/糾錯立即吃到新資料。
     * - 未注入（登入流程在 IME 未啟動前跑完）→ fallback 既有 throwaway 路徑，下次 cold start 生效，
     *   同步行為不消失。
     */
    private suspend fun downloadLearningData(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_learning_data",
            "user_id=eq.$userId",
            token
        )
        val row = rows?.firstOrNull()?.jsonObject ?: return SyncResult.Success

        val applier = smartInputApplier
        if (applier != null) {
            applier(row)
        } else {
            row["adaptive_dict"]?.jsonObject?.let { AdaptiveDictionary(context).importSnapshot(it) }
            row["personal_dict"]?.jsonObject?.let { PersonalDictionary(context).importSnapshot(it) }
            row["phrase_predict"]?.jsonObject?.let { PhrasePredictor(context).importSnapshot(it) }
            row["sentence_predict"]?.jsonObject?.let { SentencePredictor(context).importSnapshot(it) }
            row["style_profile"]?.jsonObject?.let { StyleProfiler(context).importSnapshot(it) }
            row["typing_habits"]?.jsonObject?.let { TypingHabitTracker(context).importSnapshot(it) }
            row["correction_learn"]?.jsonObject?.let { CorrectionLearnManager(context).importSnapshot(it) }
        }

        return SyncResult.Success
    }

    // ─────────────────────────────────────────
    // Supabase upsert helper
    // ─────────────────────────────────────────

    private suspend fun upsert(
        table: String,
        query: String,
        data: JsonObject,
        token: String?
    ): SyncResult {
        return try {
            // 先嘗試更新
            val updated = supabase.update(table, query, data, token)
            if (updated == null || (updated is JsonArray && updated.isEmpty())) {
                // 沒有更新到 → 插入
                supabase.insert(table, data, token)
            }
            SyncResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "upsert $table failed", e)
            SyncResult.Error("$table: ${e.message}")
        }
    }

    /** URL encode for Supabase query */
    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    // ─────────────────────────────────────────
    // 結果
    // ─────────────────────────────────────────

    sealed class SyncResult {
        object Success : SyncResult()
        object NotLoggedIn : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
