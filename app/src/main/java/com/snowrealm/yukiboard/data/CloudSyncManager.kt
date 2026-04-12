package com.snowrealm.yukiboard.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.input.dictionary.UserDictionary
import com.snowrealm.yukiboard.input.smart.*
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
 * 帳號系統綁定 Insight Engine（SnowRealmAuthClient）。
 * 資料存 Supabase（SupabaseClient）。
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
    private val auth = SnowRealmAuthClient.getInstance(context)
    private val prefs = YukiPreferences(context)
    private val metaPrefs: SharedPreferences =
        context.getSharedPreferences("cloud_sync_meta", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val isSyncEnabled: Boolean get() = metaPrefs.getBoolean(PREF_SYNC_ENABLED, false)
    val lastSyncTime: Long get() = metaPrefs.getLong(PREF_LAST_SYNC, 0)

    private var commitsSinceLastSync = 0

    /**
     * 取得 userId，若 token 存在但 userId 未儲存（OAuth 回呼時 fetchMe 失敗），
     * 會嘗試重新呼叫 fetchMe() 補齊使用者資料。
     */
    private suspend fun resolveUserId(): String? {
        auth.currentUserId?.let { return it }
        if (!auth.isLoggedIn) return null
        // token 存在但 userId 為空 → 補呼叫 fetchMe
        Log.d(TAG, "userId null but token exists, calling fetchMe to resolve")
        val me = auth.fetchMe()
        return me?.id ?: auth.currentUserId
    }

    // ─────────────────────────────────────────
    // Supabase Token 交換（Insight JWT → Supabase JWT）
    // ─────────────────────────────────────────

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedSupabaseToken: String? = null
    private var supabaseTokenExpiry: Long = 0

    /**
     * 取得 YukiBoard Supabase 能認的 JWT。
     * 流程：拿 Insight JWT → 打 Insight /api/auth/supabase-token → 換回 Supabase JWT。
     * 快取 1 小時，過期前 5 分鐘自動重新換。
     */
    private suspend fun getSupabaseToken(): String? {
        // 快取有效就直接用
        val now = System.currentTimeMillis()
        cachedSupabaseToken?.let { token ->
            if (now < supabaseTokenExpiry - 5 * 60 * 1000) return token
        }

        val insightToken = auth.getToken() ?: return null
        val baseUrl = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/supabase-token")
                    .header("Authorization", "Bearer $insightToken")
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) {
                    Log.e(TAG, "supabase-token exchange failed: ${response.code} $body")
                    return@withContext null
                }
                val obj = json.parseToJsonElement(body).jsonObject
                val token = obj["token"]?.jsonPrimitive?.contentOrNull
                val expiresIn = obj["expiresIn"]?.jsonPrimitive?.longOrNull ?: 3600
                if (token != null) {
                    cachedSupabaseToken = token
                    supabaseTokenExpiry = now + expiresIn * 1000
                    Log.d(TAG, "Supabase token exchanged, expires in ${expiresIn}s")
                }
                token
            } catch (e: Exception) {
                Log.e(TAG, "supabase-token exchange error", e)
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
        if (!auth.isLoggedIn) return

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
        val settingsJson = exportPrefsToJson()
        val data = buildJsonObject {
            put("user_id", userId)
            put("settings", settingsJson)
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
        val themeJson = exportThemeToJson()
        val data = buildJsonObject {
            put("user_id", userId)
            put("theme_config", themeJson)
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

        importPrefsFromJson(settingsObj)
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

        importThemeFromJson(themeObj)
        return SyncResult.Success
    }

    // ─────────────────────────────────────────
    // Prefs ↔ JSON
    // ─────────────────────────────────────────

    /** 把 YukiPreferences 匯出成 JSON（只同步使用者可控的設定） */
    private fun exportPrefsToJson(): JsonObject {
        return buildJsonObject {
            // AI 開關
            put("ai_enabled", prefs.aiEnabled)
            put("ai_prediction", prefs.aiPrediction)
            put("ai_writer", prefs.aiWriter)
            put("ai_translate", prefs.aiTranslate)
            put("ai_cupid", prefs.aiCupid)
            put("ai_whisper", prefs.aiWhisper)
            put("ai_grammar_check", prefs.aiGrammarCheck)
            put("ai_tone_adjust", prefs.aiToneAdjust)
            put("ai_summarize", prefs.aiSummarize)
            put("ai_emoji_translate", prefs.aiEmojiTranslate)
            put("ai_quick_reply", prefs.aiQuickReply)
            put("ai_code_complete", prefs.aiCodeComplete)
            put("ai_regex_gen", prefs.aiRegexGen)
            // 感測器
            put("shake_to_undo", prefs.shakeToUndoEnabled)
            put("gyro_cursor", prefs.gyroscopeCursorEnabled)
            put("gyro_sensitivity", prefs.gyroscopeSensitivity)
            put("app_auto_detect", prefs.appAutoDetectEnabled)
            // 動畫 / 音效
            put("flow_light", prefs.flowLightEnabled)
            put("character_anim", prefs.characterAnimEnabled)
            put("key_particle", prefs.keyParticleEffect)
            put("key_vibration", prefs.keyPressVibration)
            put("key_sound", prefs.keySoundEnabled)
            put("key_sound_style", prefs.keySoundStyle)
            put("key_sound_volume", prefs.keySoundVolume)
            // 輸入
            put("auto_input_mode", prefs.autoInputMode)
            put("swipe_input", prefs.swipeInput)
            put("settings_language", prefs.settingsLanguage)
            // 面板
            put("floating_panel", prefs.floatingPanelEnabled)
            put("weather_panel", prefs.weatherPanelEnabled)
            put("sticker_panel", prefs.stickerPanelEnabled)
            // 遊戲
            put("games_enabled", prefs.gamesEnabled)
            put("game_bgm", prefs.gameBackgroundMusic)
            put("game_sfx", prefs.gameSoundEffects)
            // 雪凜
            put("yuki_sarcastic", prefs.yukiSarcasticComments)
            put("yuki_cooldown", prefs.yukiEmotionalCooldown)
            put("yuki_mood_passive", prefs.yukiMoodDiaryPassive)
            put("yuki_weather_alert", prefs.yukiWeatherAlert)
            put("yuki_late_night", prefs.yukiLateNightComment)
        }
    }

    /** 從 JSON 匯入偏好設定 */
    private fun importPrefsFromJson(obj: JsonObject) {
        obj["ai_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiEnabled = it }
        obj["ai_prediction"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiPrediction = it }
        obj["ai_writer"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiWriter = it }
        obj["ai_translate"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiTranslate = it }
        obj["ai_cupid"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiCupid = it }
        obj["ai_whisper"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiWhisper = it }
        obj["ai_grammar_check"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiGrammarCheck = it }
        obj["ai_tone_adjust"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiToneAdjust = it }
        obj["ai_summarize"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiSummarize = it }
        obj["ai_emoji_translate"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiEmojiTranslate = it }
        obj["ai_quick_reply"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiQuickReply = it }
        obj["ai_code_complete"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiCodeComplete = it }
        obj["ai_regex_gen"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiRegexGen = it }
        obj["shake_to_undo"]?.jsonPrimitive?.booleanOrNull?.let { prefs.shakeToUndoEnabled = it }
        obj["gyro_cursor"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gyroscopeCursorEnabled = it }
        obj["gyro_sensitivity"]?.jsonPrimitive?.floatOrNull?.let { prefs.gyroscopeSensitivity = it }
        obj["app_auto_detect"]?.jsonPrimitive?.booleanOrNull?.let { prefs.appAutoDetectEnabled = it }
        obj["flow_light"]?.jsonPrimitive?.booleanOrNull?.let { prefs.flowLightEnabled = it }
        obj["character_anim"]?.jsonPrimitive?.booleanOrNull?.let { prefs.characterAnimEnabled = it }
        obj["key_particle"]?.jsonPrimitive?.booleanOrNull?.let { prefs.keyParticleEffect = it }
        obj["key_vibration"]?.jsonPrimitive?.booleanOrNull?.let { prefs.keyPressVibration = it }
        obj["key_sound"]?.jsonPrimitive?.booleanOrNull?.let { prefs.keySoundEnabled = it }
        obj["key_sound_style"]?.jsonPrimitive?.contentOrNull?.let { prefs.keySoundStyle = it }
        obj["key_sound_volume"]?.jsonPrimitive?.floatOrNull?.let { prefs.keySoundVolume = it }
        obj["auto_input_mode"]?.jsonPrimitive?.booleanOrNull?.let { prefs.autoInputMode = it }
        obj["swipe_input"]?.jsonPrimitive?.booleanOrNull?.let { prefs.swipeInput = it }
        obj["settings_language"]?.jsonPrimitive?.contentOrNull?.let { prefs.settingsLanguage = it }
        obj["floating_panel"]?.jsonPrimitive?.booleanOrNull?.let { prefs.floatingPanelEnabled = it }
        obj["weather_panel"]?.jsonPrimitive?.booleanOrNull?.let { prefs.weatherPanelEnabled = it }
        obj["sticker_panel"]?.jsonPrimitive?.booleanOrNull?.let { prefs.stickerPanelEnabled = it }
        obj["games_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gamesEnabled = it }
        obj["game_bgm"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gameBackgroundMusic = it }
        obj["game_sfx"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gameSoundEffects = it }
        obj["yuki_sarcastic"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiSarcasticComments = it }
        obj["yuki_cooldown"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiEmotionalCooldown = it }
        obj["yuki_mood_passive"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiMoodDiaryPassive = it }
        obj["yuki_weather_alert"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiWeatherAlert = it }
        obj["yuki_late_night"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiLateNightComment = it }
    }

    // ─────────────────────────────────────────
    // Theme ↔ JSON
    // ─────────────────────────────────────────

    private fun exportThemeToJson(): JsonObject {
        return buildJsonObject {
            put("use_custom_background", prefs.useCustomBackground)
            put("bg_overlay_alpha", prefs.bgOverlayAlpha)
            put("bg_scale_mode", prefs.bgScaleMode)
            put("bg_offset_x", prefs.bgOffsetX)
            put("bg_offset_y", prefs.bgOffsetY)
            put("bg_rotation", prefs.bgRotation)
            put("keyboard_height_ratio", prefs.keyboardHeightRatio)
            put("toolbar_anim_style", prefs.toolbarAnimStyle)
        }
    }

    private fun importThemeFromJson(obj: JsonObject) {
        obj["use_custom_background"]?.jsonPrimitive?.booleanOrNull?.let { prefs.useCustomBackground = it }
        obj["bg_overlay_alpha"]?.jsonPrimitive?.intOrNull?.let { prefs.bgOverlayAlpha = it }
        obj["bg_scale_mode"]?.jsonPrimitive?.intOrNull?.let { prefs.bgScaleMode = it }
        obj["bg_offset_x"]?.jsonPrimitive?.floatOrNull?.let { prefs.bgOffsetX = it }
        obj["bg_offset_y"]?.jsonPrimitive?.floatOrNull?.let { prefs.bgOffsetY = it }
        obj["bg_rotation"]?.jsonPrimitive?.intOrNull?.let { prefs.bgRotation = it }
        obj["keyboard_height_ratio"]?.jsonPrimitive?.floatOrNull?.let { prefs.keyboardHeightRatio = it }
        obj["toolbar_anim_style"]?.jsonPrimitive?.contentOrNull?.let { prefs.toolbarAnimStyle = it }
    }

    // ─────────────────────────────────────────
    // 學習資料 上傳/下載
    // ─────────────────────────────────────────

    /** 上傳所有學習資料（7 種系統合併為一筆） */
    private suspend fun uploadLearningData(userId: String, token: String?): SyncResult {
        val data = buildJsonObject {
            put("user_id", userId)
            put("adaptive_dict", exportAdaptiveDict())
            put("personal_dict", exportPersonalDict())
            put("phrase_predict", exportPhrasePredict())
            put("sentence_predict", exportSentencePredict())
            put("style_profile", exportStyleProfile())
            put("typing_habits", exportTypingHabits())
            put("correction_learn", exportCorrectionLearn())
            put("updated_at", JsonPrimitive("now()"))
        }
        return upsert("user_learning_data", "user_id=eq.$userId", data, token)
    }

    /** 下載所有學習資料 */
    private suspend fun downloadLearningData(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_learning_data",
            "user_id=eq.$userId",
            token
        )
        val row = rows?.firstOrNull()?.jsonObject ?: return SyncResult.Success

        row["adaptive_dict"]?.jsonObject?.let { importAdaptiveDict(it) }
        row["personal_dict"]?.jsonObject?.let { importPersonalDict(it) }
        row["phrase_predict"]?.jsonObject?.let { importPhrasePredict(it) }
        row["sentence_predict"]?.jsonObject?.let { importSentencePredict(it) }
        row["style_profile"]?.jsonObject?.let { importStyleProfile(it) }
        row["typing_habits"]?.jsonObject?.let { importTypingHabits(it) }
        row["correction_learn"]?.jsonObject?.let { importCorrectionLearn(it) }

        return SyncResult.Success
    }

    // ─────────────────────────────────────────
    // AdaptiveDictionary ↔ JSON
    // ─────────────────────────────────────────

    private fun exportAdaptiveDict(): JsonObject {
        val sp = context.getSharedPreferences("yukiboard_adaptive_dict", Context.MODE_PRIVATE)
        val index = sp.getString("_ad_index", null)?.split(",") ?: return buildJsonObject {}
        return buildJsonObject {
            for (word in index) {
                if (word.isBlank()) continue
                sp.getString("ad_$word", null)?.let { put(word, it) }
            }
        }
    }

    private fun importAdaptiveDict(obj: JsonObject) {
        val sp = context.getSharedPreferences("yukiboard_adaptive_dict", Context.MODE_PRIVATE)
        val editor = sp.edit()
        val keys = mutableListOf<String>()
        for ((word, value) in obj) {
            val remoteRaw = value.jsonPrimitive.contentOrNull ?: continue
            val localRaw = sp.getString("ad_$word", null)

            if (localRaw == null) {
                // 本地沒有 → 直接用雲端
                editor.putString("ad_$word", remoteRaw)
            } else {
                // 兩邊都有 → 取頻率較高的（格式: "freq|timestamp"）
                val localFreq = localRaw.split("|").getOrNull(0)?.toIntOrNull() ?: 0
                val remoteFreq = remoteRaw.split("|").getOrNull(0)?.toIntOrNull() ?: 0
                if (remoteFreq > localFreq) {
                    editor.putString("ad_$word", remoteRaw)
                }
            }
            keys.add(word)
        }
        // 合併既有 index
        val existing = sp.getString("_ad_index", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val merged = (existing + keys).distinct()
        editor.putString("_ad_index", merged.joinToString(","))
        editor.apply()
    }

    // ─────────────────────────────────────────
    // PersonalDictionary ↔ JSON
    // ─────────────────────────────────────────

    private fun exportPersonalDict(): JsonObject {
        val sp = context.getSharedPreferences("yukiboard_personal_dict", Context.MODE_PRIVATE)
        val index = sp.getString("_pd_index", null)?.split(",") ?: return buildJsonObject {}
        return buildJsonObject {
            for (word in index) {
                if (word.isBlank()) continue
                sp.getString("pd_$word", null)?.let { put(word, it) }
            }
        }
    }

    private fun importPersonalDict(obj: JsonObject) {
        val sp = context.getSharedPreferences("yukiboard_personal_dict", Context.MODE_PRIVATE)
        val editor = sp.edit()
        val keys = mutableListOf<String>()
        for ((word, value) in obj) {
            val remoteRaw = value.jsonPrimitive.contentOrNull ?: continue
            val localRaw = sp.getString("pd_$word", null)

            if (localRaw == null) {
                // 本地沒有 → 直接用雲端
                editor.putString("pd_$word", remoteRaw)
            } else {
                // 兩邊都有 → 取 tier 或 frequency 較高的（格式: "tier|freq|timestamp"）
                val localParts = localRaw.split("|")
                val remoteParts = remoteRaw.split("|")
                val localTier = localParts.getOrNull(0)?.toIntOrNull() ?: 0
                val remoteTier = remoteParts.getOrNull(0)?.toIntOrNull() ?: 0
                val localFreq = localParts.getOrNull(1)?.toIntOrNull() ?: 0
                val remoteFreq = remoteParts.getOrNull(1)?.toIntOrNull() ?: 0
                if (remoteTier > localTier || (remoteTier == localTier && remoteFreq > localFreq)) {
                    editor.putString("pd_$word", remoteRaw)
                }
            }
            keys.add(word)
        }
        val existing = sp.getString("_pd_index", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val merged = (existing + keys).distinct()
        editor.putString("_pd_index", merged.joinToString(","))
        editor.apply()
    }

    // ─────────────────────────────────────────
    // PhrasePredictor ↔ JSON
    // ─────────────────────────────────────────

    private fun exportPhrasePredict(): JsonObject {
        val sp = context.getSharedPreferences("yukiboard_phrase_predict", Context.MODE_PRIVATE)
        return buildJsonObject {
            // bigrams
            put("bi_keys", sp.getString("_bi_keys", "") ?: "")
            val biKeys = sp.getString("_bi_keys", null)?.split(",") ?: emptyList()
            for (key in biKeys) {
                if (key.isBlank()) continue
                sp.getString("bi_$key", null)?.let { put("bi_$key", it) }
            }
            // trigrams
            put("tri_keys", sp.getString("_tri_keys", "") ?: "")
            val triKeys = sp.getString("_tri_keys", null)?.split(",") ?: emptyList()
            for (key in triKeys) {
                if (key.isBlank()) continue
                sp.getString("tri_$key", null)?.let { put("tri_$key", it) }
            }
        }
    }

    private fun importPhrasePredict(obj: JsonObject) {
        val sp = context.getSharedPreferences("yukiboard_phrase_predict", Context.MODE_PRIVATE)
        val editor = sp.edit()
        for ((key, value) in obj) {
            val raw = value.jsonPrimitive.contentOrNull ?: continue
            if (key == "bi_keys") {
                val existing = sp.getString("_bi_keys", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                val incoming = raw.split(",").filter { it.isNotBlank() }
                editor.putString("_bi_keys", (existing + incoming).distinct().joinToString(","))
            } else if (key == "tri_keys") {
                val existing = sp.getString("_tri_keys", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                val incoming = raw.split(",").filter { it.isNotBlank() }
                editor.putString("_tri_keys", (existing + incoming).distinct().joinToString(","))
            } else {
                // bi_xxx or tri_xxx — 直接覆寫（雲端為主）
                editor.putString(key, raw)
            }
        }
        editor.apply()
    }

    // ─────────────────────────────────────────
    // SentencePredictor ↔ JSON
    // ─────────────────────────────────────────

    private fun exportSentencePredict(): JsonObject {
        val sp = context.getSharedPreferences("yukiboard_sentence_predict", Context.MODE_PRIVATE)
        return buildJsonObject {
            put("sb_keys", sp.getString("_sb_keys", "") ?: "")
            val sbKeys = sp.getString("_sb_keys", null)?.split(";;") ?: emptyList()
            for (key in sbKeys) {
                if (key.isBlank()) continue
                sp.getString("sb_$key", null)?.let { put("sb_$key", it) }
            }
            put("sp_keys", sp.getString("_sp_keys", "") ?: "")
            val spKeys = sp.getString("_sp_keys", null)?.split(";;") ?: emptyList()
            for (key in spKeys) {
                if (key.isBlank()) continue
                sp.getString("sp_$key", null)?.let { put("sp_$key", it) }
            }
        }
    }

    private fun importSentencePredict(obj: JsonObject) {
        val sp = context.getSharedPreferences("yukiboard_sentence_predict", Context.MODE_PRIVATE)
        val editor = sp.edit()
        for ((key, value) in obj) {
            val raw = value.jsonPrimitive.contentOrNull ?: continue
            if (key == "sb_keys") {
                val existing = sp.getString("_sb_keys", null)?.split(";;")?.filter { it.isNotBlank() } ?: emptyList()
                val incoming = raw.split(";;").filter { it.isNotBlank() }
                editor.putString("_sb_keys", (existing + incoming).distinct().joinToString(";;"))
            } else if (key == "sp_keys") {
                val existing = sp.getString("_sp_keys", null)?.split(";;")?.filter { it.isNotBlank() } ?: emptyList()
                val incoming = raw.split(";;").filter { it.isNotBlank() }
                editor.putString("_sp_keys", (existing + incoming).distinct().joinToString(";;"))
            } else {
                editor.putString(key, raw)
            }
        }
        editor.apply()
    }

    // ─────────────────────────────────────────
    // StyleProfiler ↔ JSON
    // ─────────────────────────────────────────

    private fun exportStyleProfile(): JsonObject {
        val sp = context.getSharedPreferences("yukiboard_style", Context.MODE_PRIVATE)
        return buildJsonObject {
            sp.getString("_suffixes", null)?.let { put("suffixes", it) }
            sp.getString("_global_style", null)?.let { put("global_style", it) }
            val appKeys = sp.getString("_app_keys", null)
            appKeys?.let { put("app_keys", it) }
            appKeys?.split(",")?.forEach { pkg ->
                if (pkg.isBlank()) return@forEach
                sp.getString("app_style_$pkg", null)?.let { put("app_style_$pkg", it) }
            }
        }
    }

    private fun importStyleProfile(obj: JsonObject) {
        val sp = context.getSharedPreferences("yukiboard_style", Context.MODE_PRIVATE)
        val editor = sp.edit()
        obj["suffixes"]?.jsonPrimitive?.contentOrNull?.let { editor.putString("_suffixes", it) }
        obj["global_style"]?.jsonPrimitive?.contentOrNull?.let { editor.putString("_global_style", it) }
        obj["app_keys"]?.jsonPrimitive?.contentOrNull?.let { keys ->
            val existing = sp.getString("_app_keys", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val incoming = keys.split(",").filter { it.isNotBlank() }
            editor.putString("_app_keys", (existing + incoming).distinct().joinToString(","))
        }
        for ((key, value) in obj) {
            if (key.startsWith("app_style_")) {
                value.jsonPrimitive.contentOrNull?.let { editor.putString(key, it) }
            }
        }
        editor.apply()
    }

    // ─────────────────────────────────────────
    // TypingHabitTracker ↔ JSON
    // ─────────────────────────────────────────

    private fun exportTypingHabits(): JsonObject {
        val sp = context.getSharedPreferences("yukiboard_habits", Context.MODE_PRIVATE)
        return buildJsonObject {
            // period buckets
            val periodKeys = sp.getString("_period_keys", null)
            periodKeys?.let { put("period_keys", it) }
            periodKeys?.split(",")?.forEach { key ->
                if (key.isBlank()) return@forEach
                sp.getString("period_$key", null)?.let { put("period_$key", it) }
            }
            // app buckets
            val appKeys = sp.getString("_app_keys", null)
            appKeys?.let { put("app_keys", it) }
            appKeys?.split(",")?.forEach { key ->
                if (key.isBlank()) return@forEach
                sp.getString("app_$key", null)?.let { put("app_$key", it) }
            }
        }
    }

    private fun importTypingHabits(obj: JsonObject) {
        val sp = context.getSharedPreferences("yukiboard_habits", Context.MODE_PRIVATE)
        val editor = sp.edit()
        obj["period_keys"]?.jsonPrimitive?.contentOrNull?.let { keys ->
            val existing = sp.getString("_period_keys", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val incoming = keys.split(",").filter { it.isNotBlank() }
            editor.putString("_period_keys", (existing + incoming).distinct().joinToString(","))
        }
        obj["app_keys"]?.jsonPrimitive?.contentOrNull?.let { keys ->
            val existing = sp.getString("_app_keys", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val incoming = keys.split(",").filter { it.isNotBlank() }
            editor.putString("_app_keys", (existing + incoming).distinct().joinToString(","))
        }
        for ((key, value) in obj) {
            if (key.startsWith("period_") && key != "period_keys" ||
                key.startsWith("app_") && key != "app_keys") {
                value.jsonPrimitive.contentOrNull?.let { editor.putString(key, it) }
            }
        }
        editor.apply()
    }

    // ─────────────────────────────────────────
    // CorrectionLearnManager ↔ JSON
    // ─────────────────────────────────────────

    private fun exportCorrectionLearn(): JsonObject {
        val sp = context.getSharedPreferences("yukiboard_correction_learn", Context.MODE_PRIVATE)
        val index = sp.getString("_cl_index", null)?.split(",") ?: return buildJsonObject {}
        return buildJsonObject {
            for (typed in index) {
                if (typed.isBlank()) continue
                sp.getString("cl_$typed", null)?.let { put(typed, it) }
            }
        }
    }

    private fun importCorrectionLearn(obj: JsonObject) {
        val sp = context.getSharedPreferences("yukiboard_correction_learn", Context.MODE_PRIVATE)
        val editor = sp.edit()
        val keys = mutableListOf<String>()
        for ((typed, value) in obj) {
            val raw = value.jsonPrimitive.contentOrNull ?: continue
            editor.putString("cl_$typed", raw)
            keys.add(typed)
        }
        val existing = sp.getString("_cl_index", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val merged = (existing + keys).distinct()
        editor.putString("_cl_index", merged.joinToString(","))
        editor.apply()
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
