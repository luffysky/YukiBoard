# YukiBoard × Insight 全面整合 — Android 端任務包

> 目標：登入一個 Insight 帳號，YukiBoard 所有功能自動解鎖
> 基於現有 SnowRealmAuthClient、InsightStickerApi、SnowRealmProvider 擴充
> 與 Insight 後端任務包搭配執行

---

## 整合後的使用者體驗

```
未登入：
→ AI 功能需邀請碼或自填 Key
→ 只有內建主題和預設貼圖
→ 無問卷、無分享

登入 Insight 帳號後自動解鎖：
→ AI 補字直接可用（平台提供，免填 Key）
→ Free / Plus / Pro 等級對應不同 AI 模型
→ Insight 購買的主題自動同步到鍵盤
→ 貼圖包自動更新
→ 打字情境觸發問卷 → 回答得 Z 幣
→ Claw 產出內容一鍵分享到 Insight 貼文
→ 設定雲端備份，換手機不怕
```

---

## 第一批 — InsightApiClient（統一 API 入口）

### 新增 `data/InsightApiClient.kt`

所有對 Insight 的 HTTP 呼叫統一走這裡，auth token 管理、retry、error 只寫一次。

```kotlin
package com.snowrealm.yukiboard.data

import android.content.Context
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Insight Engine 統一 API 客戶端。
 *
 * 統一管理：
 * - Bearer token 自動附加
 * - 401 自動 refresh token 後重試一次
 * - 錯誤訊息統一格式
 * - Platform-Key header
 *
 * 其他 client（InsightStickerApi、SnowRealmProvider 等）
 * 逐步遷移到使用此 client。
 */
class InsightApiClient private constructor(private val context: Context) {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        @Volatile private var instance: InsightApiClient? = null
        fun getInstance(context: Context): InsightApiClient =
            instance ?: synchronized(this) {
                instance ?: InsightApiClient(context.applicationContext).also { instance = it }
            }
    }

    private val baseUrl = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')
    private val platformKey = BuildConfig.PLATFORM_API_KEY
    private val authClient = SnowRealmAuthClient.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── 核心請求方法 ──────────────────────────────────────────

    suspend fun get(path: String): ApiResult =
        execute(buildRequest(path).get().build())

    suspend fun post(path: String, body: JsonObject): ApiResult =
        execute(
            buildRequest(path)
                .post(body.toString().toRequestBody(JSON))
                .build()
        )

    suspend fun postRaw(path: String, body: String): ApiResult =
        execute(buildRequest(path).post(body.toRequestBody(JSON)).build())

    // ── SSE 串流（AI proxy 用）─────────────────────────────────

    /**
     * 發起 SSE 串流請求，逐 chunk 回調。
     * 不回傳，由 onChunk 持續接收，onDone/onError 結束。
     */
    suspend fun stream(
        path: String,
        body: JsonObject,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(path)
                .post(body.toString().toRequestBody(JSON))
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                onError("HTTP ${response.code}")
                return@withContext
            }
            val source = response.body?.source() ?: run { onError("empty body"); return@withContext }
            val buffer = okio.Buffer()
            while (!source.exhausted()) {
                source.read(buffer, 8192)
                val line = buffer.readUtf8()
                line.split("\n").forEach { raw ->
                    val trimmed = raw.trim()
                    if (trimmed.startsWith("data:")) {
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") return@forEach
                        try {
                            val chunk = json.parseToJsonElement(data).jsonObject
                            val text = chunk["text"]?.jsonPrimitive?.content
                                ?: chunk["choices"]?.jsonArray
                                    ?.firstOrNull()?.jsonObject
                                    ?.get("delta")?.jsonObject
                                    ?.get("content")?.jsonPrimitive?.content
                                ?: return@forEach
                            if (text.isNotEmpty()) onChunk(text)
                        } catch (_: Exception) {}
                    }
                }
            }
            onDone()
        } catch (e: Exception) {
            onError(e.message ?: "network error")
        }
    }

    // ── 內部工具 ──────────────────────────────────────────────

    private fun buildRequest(path: String): Request.Builder {
        val url = if (path.startsWith("http")) path else "$baseUrl$path"
        val builder = Request.Builder()
            .url(url)
            .header("X-Platform-Key", platformKey)
            .header("Content-Type", "application/json")
        authClient.getToken()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    private suspend fun execute(request: Request): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                // 401：嘗試 refresh 後重試一次
                if (response.code == 401) {
                    val refreshed = authClient.refreshIfNeeded()
                    if (refreshed) {
                        val retryReq = request.newBuilder()
                            .header("Authorization", "Bearer ${authClient.getToken() ?: ""}")
                            .build()
                        val retryResp = httpClient.newCall(retryReq).execute()
                        val retryBody = retryResp.body?.string() ?: ""
                        return@withContext if (retryResp.isSuccessful) {
                            ApiResult.Success(parseJson(retryBody))
                        } else {
                            ApiResult.Error(retryResp.code, parseError(retryBody))
                        }
                    }
                    return@withContext ApiResult.Unauthorized
                }

                if (response.isSuccessful) {
                    ApiResult.Success(parseJson(bodyStr))
                } else {
                    ApiResult.Error(response.code, parseError(bodyStr))
                }
            } catch (e: Exception) {
                ApiResult.NetworkError(e.message ?: "unknown")
            }
        }

    private fun parseJson(body: String): JsonElement =
        try { json.parseToJsonElement(body) } catch (_: Exception) { JsonNull }

    private fun parseError(body: String): String =
        try {
            json.parseToJsonElement(body).jsonObject["error"]
                ?.jsonPrimitive?.content ?: body.take(100)
        } catch (_: Exception) { body.take(100) }

    // ── 結果型別 ──────────────────────────────────────────────

    sealed class ApiResult {
        data class Success(val data: JsonElement) : ApiResult()
        data class Error(val code: Int, val message: String) : ApiResult()
        object Unauthorized : ApiResult()
        data class NetworkError(val message: String) : ApiResult()

        val isSuccess get() = this is Success
        fun dataOrNull() = (this as? Success)?.data
        fun errorMessage() = when (this) {
            is Error -> message
            is NetworkError -> message
            is Unauthorized -> "請重新登入"
            else -> null
        }
    }
}
```

---

## 第二批 — UserProfile 補欄位 + /me 同步

### 修改 `auth/SnowRealmAuthClient.kt`

**2-A：擴充 UserProfile**

找到 `data class UserProfile`，補充欄位：

```kotlin
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String = "",
    val role: String = "",
    val tenantId: String = "",
    val zCoinBalance: Int = 0,
    // ── 新增欄位 ──
    val avatarUrl: String = "",           // 頭像 URL
    val plan: UserPlan = UserPlan.FREE,   // 訂閱等級
    val insightUsername: String = ""      // Insight 平台 username
)

enum class UserPlan {
    FREE,   // 免費用戶：基本 AI 額度
    PLUS,   // Plus：進階模型
    PRO,    // Pro：最高等級
    ENTERPRISE  // 企業
}
```

**2-B：fetchMe() 解析新欄位**

在 `fetchMe()` 解析 MeResponse 時加入：

```kotlin
val user = UserProfile(
    id = me?.userId ?: userId,
    email = me?.userName ?: loginEmail,
    displayName = me?.displayName ?: me?.userName ?: loginEmail,
    role = me?.role ?: "",
    tenantId = me?.tenantId ?: "",
    avatarUrl = me?.avatarUrl ?: "",          // 新增
    plan = parsePlan(me?.plan ?: me?.role),   // 新增
    insightUsername = me?.userName ?: ""      // 新增
)

// 解析 plan 的輔助函式（加在 companion object）
private fun parsePlan(raw: String?): UserPlan = when (raw?.lowercase()) {
    "plus"        -> UserPlan.PLUS
    "pro"         -> UserPlan.PRO
    "enterprise"  -> UserPlan.ENTERPRISE
    else          -> UserPlan.FREE
}
```

**2-C：MeResponse 加新欄位**

```kotlin
@Serializable
data class MeResponse(
    val userId: String = "",
    val userName: String = "",
    val displayName: String = "",   // 新增
    val role: String = "",
    val tenantId: String = "",
    val tenantName: String = "",
    val adminTitle: String = "",
    val avatarUrl: String = "",     // 新增
    val plan: String = ""           // 新增："free"/"plus"/"pro"
)
```

**2-D：currentUser 提供給 UI**

在 `SnowRealmAuthClient` 加：

```kotlin
val currentUser: UserProfile?
    get() {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return UserProfile(
            id = prefs.getString(KEY_USER_ID, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
            avatarUrl = prefs.getString(KEY_AVATAR_URL, "") ?: "",
            plan = parsePlan(prefs.getString(KEY_PLAN, null))
        )
    }

// companion object 加常數
private const val KEY_AVATAR_URL = "avatar_url"
private const val KEY_PLAN = "user_plan"
```

---

## 第三批 — SnowRealmProvider 升級串流

### 修改 `claw/provider/SnowRealmProvider.kt`

完整替換 `complete()` 方法，改為 SSE 串流：

```kotlin
override suspend fun complete(
    prompt: String,
    systemPrompt: String,
    model: String
): Flow<String> = flow {
    val inviteCode = inviteManager.getActivatedCode()
    val authToken = authClient.getToken()

    if (inviteCode == null && authToken == null) {
        throw IllegalStateException("未啟用邀請碼且未登入")
    }

    val parts = (if (model.isBlank()) defaultModel else model).split("/", limit = 2)
    val provider = if (parts.size == 2) parts[0] else "anthropic"
    val modelName = if (parts.size == 2) parts[1] else parts[0]

    val deviceId = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ANDROID_ID
    ) ?: "unknown"

    val requestBody = buildJsonObject {
        if (inviteCode != null) put("invite_code", inviteCode)
        if (authToken != null) put("auth_token", authToken)
        put("device_id", deviceId)
        put("provider", provider)
        put("model", modelName)
        put("prompt", prompt)
        if (systemPrompt.isNotBlank()) put("system_prompt", systemPrompt)
        put("max_tokens", 1024)
        put("stream", true)   // ← 啟用串流
    }

    val request = Request.Builder()
        .url("$baseUrl/api/yukiboard/ai-proxy")
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Platform-Key", platformApiKey)
        .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    val response = withContext(Dispatchers.IO) {
        client.newCall(request).execute()
    }

    if (!response.isSuccessful) {
        val errBody = response.body?.string() ?: ""
        val errMsg = try {
            json.parseToJsonElement(errBody).jsonObject["error"]
                ?.jsonPrimitive?.content
        } catch (_: Exception) { null }
        throw RuntimeException(errMsg ?: "AI 代理錯誤 (${response.code})")
    }

    // SSE 串流解析
    val source = response.body?.source()
        ?: throw RuntimeException("empty response body")
    val buffer = okio.Buffer()
    var totalTokens = 0

    try {
        while (!source.exhausted()) {
            source.read(buffer, 4096)
            val chunk = buffer.readUtf8()
            chunk.split("\n").forEach { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) return@forEach
                val data = trimmed.removePrefix("data:").trim()
                if (data == "[DONE]") return@forEach
                try {
                    val obj = json.parseToJsonElement(data).jsonObject
                    // 支援兩種格式：直接 text 或 OpenAI delta
                    val text = obj["text"]?.jsonPrimitive?.content
                        ?: obj["choices"]?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("delta")?.jsonObject
                            ?.get("content")?.jsonPrimitive?.content
                        ?: return@forEach
                    if (text.isNotEmpty()) emit(text)
                    // 累計 token（若 server 有提供）
                    obj["tokens_used"]?.jsonPrimitive?.intOrNull?.let {
                        totalTokens += it
                    }
                } catch (_: Exception) {}
            }
        }
    } finally {
        source.close()
        response.close()
    }

    // 結束後扣本地額度
    if (totalTokens > 0) inviteManager.consumeTokens(totalTokens)

}.flowOn(Dispatchers.IO)
```

同時新增根據 UserPlan 選 model：

```kotlin
/**
 * 根據使用者訂閱等級回傳推薦 model。
 * Free → 快速小模型，Pro → 最強模型
 */
fun getRecommendedModel(plan: SnowRealmAuthClient.UserPlan): String = when (plan) {
    SnowRealmAuthClient.UserPlan.FREE   -> "anthropic/claude-haiku-4-5-20251001"
    SnowRealmAuthClient.UserPlan.PLUS   -> "anthropic/claude-sonnet-4-5-20250514"
    SnowRealmAuthClient.UserPlan.PRO,
    SnowRealmAuthClient.UserPlan.ENTERPRISE -> "anthropic/claude-opus-4-6"
    else -> defaultModel
}
```

---

## 第四批 — InsightSurveyClient（情境問卷）

### 新增 `data/InsightSurveyClient.kt`

```kotlin
package com.snowrealm.yukiboard.data

import android.content.Context
import android.content.SharedPreferences
import com.snowrealm.yukiboard.emotion.EmotionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Insight 情境問卷客戶端。
 *
 * 觸發時機（由 YukiBoardIMEService 呼叫）：
 * - EmotionState 更新為 SAD / ANGRY / EXCITED 時
 * - 每次觸發間隔至少 30 分鐘（節流）
 * - 使用者可在設定頁關閉
 *
 * 隱私設計：
 * - 只傳情緒標籤（SAD/ANGRY）和 App 名稱
 * - 不傳原始文字
 * - 完全 opt-in，預設關閉
 */
class InsightSurveyClient(private val context: Context) {

    private val api = InsightApiClient.getInstance(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_survey", Context.MODE_PRIVATE)

    private val THROTTLE_MS = 30 * 60 * 1000L  // 30分鐘最多觸發一次

    data class SurveyQuestion(
        val surveyId: String,
        val question: String,
        val options: List<String>,
        val rewardZCoin: Int = 0       // 回答得到的 Z 幣
    )

    /**
     * 檢查當前情境是否有對應問卷。
     * 節流：30 分鐘內不重複觸發。
     * @return 問卷內容，null 表示沒有或已節流
     */
    suspend fun checkTrigger(
        emotion: EmotionState.Tone,
        appPackage: String
    ): SurveyQuestion? = withContext(Dispatchers.IO) {
        // 節流檢查
        val lastTrigger = prefs.getLong("last_trigger_ms", 0)
        if (System.currentTimeMillis() - lastTrigger < THROTTLE_MS) return@withContext null

        val result = api.post("/api/yukiboard/survey/trigger", buildJsonObject {
            put("emotion", emotion.name)
            put("app_package", appPackage)
        })

        val data = result.dataOrNull()?.jsonObject ?: return@withContext null
        val question = data["question"]?.jsonPrimitive?.content ?: return@withContext null
        val options = data["options"]?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: return@withContext null

        prefs.edit().putLong("last_trigger_ms", System.currentTimeMillis()).apply()

        SurveyQuestion(
            surveyId = data["survey_id"]?.jsonPrimitive?.content ?: "",
            question = question,
            options = options,
            rewardZCoin = data["reward_zcoin"]?.jsonPrimitive?.intOrNull ?: 0
        )
    }

    /**
     * 提交問卷答案。
     * @return 獲得的 Z 幣數量（0 = 提交失敗或無獎勵）
     */
    suspend fun submitAnswer(
        surveyId: String,
        optionIndex: Int
    ): Int = withContext(Dispatchers.IO) {
        val result = api.post("/api/yukiboard/survey/answer", buildJsonObject {
            put("survey_id", surveyId)
            put("option_index", optionIndex)
        })
        result.dataOrNull()?.jsonObject
            ?.get("reward_zcoin")?.jsonPrimitive?.intOrNull ?: 0
    }

    /** 問卷過去 30 天的答題紀錄數（顯示在設定頁） */
    fun getLocalAnswerCount(): Int =
        prefs.getInt("answer_count_30d", 0)
}
```

### 整合到 YukiBoardIMEService

在 `onCreate()` 加：
```kotlin
private var surveyClient: InsightSurveyClient? = null

// 在 init 區
if (prefs.insightSurveyEnabled) {
    surveyClient = InsightSurveyClient(applicationContext)
}
```

在 EmotionState listener 加（`onStartInputView` 之後）：
```kotlin
EmotionState.addListener { snapshot ->
    if (snapshot.tone in listOf(
            EmotionState.Tone.SAD,
            EmotionState.Tone.ANGRY,
            EmotionState.Tone.EXCITED
        ) && snapshot.confidence > 0.5f
    ) {
        serviceScope.launch {
            val survey = surveyClient?.checkTrigger(
                snapshot.tone,
                currentInputEditorInfo?.packageName ?: ""
            ) ?: return@launch
            // 在候選字列右側顯示問卷小標
            withContext(Dispatchers.Main) {
                keyboardView.showSurveyHint(survey)
            }
        }
    }
}
```

在 `onDestroy()` 加：
```kotlin
EmotionState.removeListener(emotionListener)
```

### KeyboardView 加問卷 Hint UI

在 `KeyboardView.kt` 加（跟防詐騙 hint 類似）：

```kotlin
// 問卷提示
private var pendingSurvey: InsightSurveyClient.SurveyQuestion? = null
var onSurveyHintClicked: ((InsightSurveyClient.SurveyQuestion) -> Unit)? = null

fun showSurveyHint(survey: InsightSurveyClient.SurveyQuestion) {
    pendingSurvey = survey
    invalidate()
}

fun clearSurveyHint() {
    pendingSurvey = null
    invalidate()
}
```

在 `drawCandidateBar()` 右側加問卷小標（黃綠色，與防詐橘色區分）：

```kotlin
// 問卷提示（候選列最右側，不超過 40dp 寬）
pendingSurvey?.let { survey ->
    val hintW = 38f * density
    val hintLeft = width - hintW - 2f * density
    // 底色
    val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8F5E9.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(
        RectF(hintLeft, 3f * density, width - 3f * density, candidateBarHeight - 3f * density),
        6f * density, 6f * density, hintPaint
    )
    // 文字
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E7D32.toInt()
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("📊 ▶", hintLeft + hintW / 2f, candidateBarHeight * 0.67f, tp)
}
```

---

## 第五批 — InsightThemeSync（主題雲端同步）

### 新增 `data/InsightThemeSync.kt`

```kotlin
package com.snowrealm.yukiboard.data

import android.content.Context
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Insight 主題 / 皮膚雲端同步。
 *
 * 功能：
 * 1. 登入後自動拉取在 Insight 購買的主題
 * 2. 本地設定（主題、背景）上傳備份
 * 3. 換手機後登入自動恢復設定
 */
class InsightThemeSync(private val context: Context) {

    private val api = InsightApiClient.getInstance(context)
    private val themeManager = ThemeManager.getInstance(context)

    data class RemoteTheme(
        val id: String,
        val name: String,
        val previewUrl: String,
        val isPurchased: Boolean,
        val isFree: Boolean
    )

    /**
     * 從 Insight 拉取使用者擁有的主題清單。
     * 登入後自動呼叫一次（onStartInputView）。
     */
    suspend fun fetchOwnedThemes(): List<RemoteTheme> = withContext(Dispatchers.IO) {
        val result = api.get("/api/yukiboard/themes")
        val arr = result.dataOrNull()?.jsonObject
            ?.get("themes")?.jsonArray ?: return@withContext emptyList()

        arr.mapNotNull { elem ->
            val obj = elem.jsonObject
            try {
                RemoteTheme(
                    id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    previewUrl = obj["preview_url"]?.jsonPrimitive?.content ?: "",
                    isPurchased = obj["is_purchased"]?.jsonPrimitive?.booleanOrNull ?: false,
                    isFree = obj["is_free"]?.jsonPrimitive?.booleanOrNull ?: true
                )
            } catch (_: Exception) { null }
        }
    }

    /**
     * 上傳目前的鍵盤設定到 Insight（備份用）。
     * 只傳主題 ID，不傳個人內容。
     */
    suspend fun backupSettings(
        currentThemeId: String,
        keyboardHeight: Float,
        keyboardOpacity: Float
    ): Boolean = withContext(Dispatchers.IO) {
        val result = api.post("/api/yukiboard/settings/backup", buildJsonObject {
            put("theme_id", currentThemeId)
            put("keyboard_height_ratio", keyboardHeight)
            put("keyboard_opacity", keyboardOpacity)
        })
        result.isSuccess
    }

    /**
     * 從 Insight 恢復設定（首次登入或換手機後）。
     * @return 是否有設定被恢復
     */
    suspend fun restoreSettings(): Boolean = withContext(Dispatchers.IO) {
        val result = api.get("/api/yukiboard/settings/restore")
        val data = result.dataOrNull()?.jsonObject ?: return@withContext false

        val themeId = data["theme_id"]?.jsonPrimitive?.content
        if (!themeId.isNullOrBlank()) {
            // 切換到備份的主題
            val theme = themeManager.allThemes.find { it.id == themeId }
            if (theme != null) {
                withContext(Dispatchers.Main) {
                    themeManager.currentTheme = theme
                }
                return@withContext true
            }
        }
        false
    }
}
```

### 整合到 YukiBoardIMEService

```kotlin
private var themeSync: InsightThemeSync? = null

// onStartInputView 登入後觸發一次
private fun syncInsightIfNeeded() {
    if (!authClient.isLoggedIn) return
    serviceScope.launch {
        // 拉主題
        val ownedThemes = themeSync?.fetchOwnedThemes() ?: return@launch
        // 更新 ThemeManager 的可用清單（已購買的主題解鎖）
        ThemeManager.getInstance(applicationContext)
            .unlockRemoteThemes(ownedThemes.map { it.id })
    }
}
```

---

## 第六批 — InsightSocialClient（分享到 Insight）

### 新增 `data/InsightSocialClient.kt`

```kotlin
package com.snowrealm.yukiboard.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Insight 社群分享客戶端。
 *
 * 功能：
 * - Claw Skill 產出的內容一鍵分享到 Insight 貼文
 * - 使用者主動觸發，不自動分享
 */
class InsightSocialClient(private val context: Context) {

    private val api = InsightApiClient.getInstance(context)

    data class ShareResult(
        val postId: String,
        val postUrl: String
    )

    /**
     * 分享文字到 Insight 貼文。
     * @param content 要分享的文字（Claw 產出的內容）
     * @param sourceSkill 哪個 Skill 產生的（標注來源）
     * @return 貼文 URL，null 表示失敗
     */
    suspend fun shareText(
        content: String,
        sourceSkill: String = ""
    ): ShareResult? = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext null

        val result = api.post("/api/yukiboard/social/share", buildJsonObject {
            put("content", content.take(500))  // 限制長度
            put("source", "yukiboard")
            if (sourceSkill.isNotBlank()) put("skill", sourceSkill)
        })

        val data = result.dataOrNull()?.jsonObject ?: return@withContext null
        val postId = data["post_id"]?.jsonPrimitive?.content ?: return@withContext null
        val postUrl = data["post_url"]?.jsonPrimitive?.content ?: ""

        ShareResult(postId, postUrl)
    }
}
```

### 在 ClawPanelView 加分享按鈕

在 Claw Skill 結果顯示後，加一個「分享到 Insight ↗」按鈕：

```kotlin
// 在 ClawResultFloatingPanel 或 ClawPanelView 的結果 UI 加
var onShareToInsight: ((content: String) -> Unit)? = null

// 按鈕觸發
onShareToInsight?.invoke(resultText)
```

在 `YukiBoardIMEService` 設定回呼：

```kotlin
clawResultFloatingPanel?.onShareToInsight = { content ->
    serviceScope.launch {
        val result = InsightSocialClient(applicationContext).shareText(content)
        withContext(Dispatchers.Main) {
            if (result != null) {
                keyboardView.showToast("已分享到 Insight")
            } else {
                keyboardView.showToast("分享失敗，請確認已登入")
            }
        }
    }
}
```

---

## 第七批 — YukiPreferences 新增開關 + 設定頁 UI

### 在 `YukiPreferences.kt` 加入

```kotlin
// Insight 整合開關
var insightSurveyEnabled: Boolean by BoolPref("insight_survey", false)   // 預設關閉（opt-in）
var insightThemeSyncEnabled: Boolean by BoolPref("insight_theme_sync", true)
var insightSettingsBackup: Boolean by BoolPref("insight_settings_backup", true)
```

### 在 `SettingsActivity.kt` 帳號卡片加入

```kotlin
// 在登入狀態卡片下方加 Insight 整合區塊
val auth = SnowRealmAuthClient.getInstance(this)
if (auth.isLoggedIn) {
    val user = auth.currentUser
    // 顯示 plan badge（Free/Plus/Pro）
    val planText = when (user?.plan) {
        UserPlan.PLUS -> "Plus ⭐"
        UserPlan.PRO  -> "Pro 🔥"
        else          -> "Free"
    }
    // 加 Insight 功能開關
    addSwitchRow(card, "情境問卷", "打字時觸發問卷，回答得 Z 幣（需登入）",
        prefs.insightSurveyEnabled) { prefs.insightSurveyEnabled = it }
    addSwitchRow(card, "主題雲端同步", "自動同步在 Insight 購買的主題",
        prefs.insightThemeSyncEnabled) { prefs.insightThemeSyncEnabled = it }
    addSwitchRow(card, "設定備份", "登入後自動備份鍵盤設定，換手機不怕",
        prefs.insightSettingsBackup) { prefs.insightSettingsBackup = it }
}
```

---

## 執行順序

```
第一批：InsightApiClient.kt（其他都靠它）
第二批：UserProfile 補欄位 + fetchMe 解析
第三批：SnowRealmProvider 串流升級
第四批：InsightSurveyClient + IMEService 整合 + KeyboardView hint
第五批：InsightThemeSync + IMEService 整合
第六批：InsightSocialClient + ClawPanelView 分享按鈕
第七批：YukiPreferences 開關 + SettingsActivity UI
每批完成後 Build 驗證
```

---

## 注意事項

1. `InsightSurveyClient` 預設 **opt-in 關閉**，使用者主動開啟才生效
2. 分享功能不自動發文，使用者手動點按鈕才觸發
3. 主題同步只解鎖已購買的主題 ID，不下載主題檔（主題檔仍在 App 內）
4. 所有 Insight API 呼叫都要檢查 `authClient.isLoggedIn`，未登入靜默跳過
5. `InsightApiClient` 的 401 自動 refresh 最多重試一次，避免無限循環
