package com.snowrealm.yukiboard.data

import android.content.Context
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.app.net.AppHttpClient
import com.snowrealm.yukiboard.app.session.UserSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Insight Engine 統一 API 客戶端。
 *
 * 統一管理：
 * - Bearer token 自動附加
 * - 401 自動 refresh token 後重試一次
 * - 錯誤訊息統一格式
 * - Platform-Key header
 *
 * Sprint 7-3 契約：本類是 Insight /api/ 路徑的唯一 gateway。
 * 新 caller 禁止自建 OkHttpClient / 自組 Authorization；改走本類 get / post / stream。
 * Auth token 與 refresh 皆透過 UserSessionRepository，不直達 SnowRealmAuthClient。
 */
class InsightApiClient private constructor(private val context: Context) {

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        @Volatile private var instance: InsightApiClient? = null
        fun getInstance(context: Context): InsightApiClient =
            instance ?: synchronized(this) {
                instance ?: InsightApiClient(context.applicationContext).also { instance = it }
            }
    }

    private val baseUrl = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')
    private val platformKey = BuildConfig.PLATFORM_API_KEY
    private val session = UserSessionRepository(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = AppHttpClient.newBuilder(AppHttpClient.Profile.DEFAULT).build()

    // ── 核心請求方法 ──

    suspend fun get(path: String): ApiResult =
        execute(buildRequest(path).get().build())

    suspend fun post(path: String, body: JsonObject): ApiResult =
        execute(
            buildRequest(path)
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()
        )

    // ── SSE 串流（AI proxy 用）──

    suspend fun stream(
        path: String,
        body: JsonObject,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(path)
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onError("HTTP ${response.code}")
                    return@use
                }
                val source = response.body?.source() ?: run {
                    onError("empty body")
                    return@use
                }
                // S9-A：SSE 解析
                // 依 SSE spec 以 blank line 做 event 邊界，多行 `data:` 欄位以 `\n` 拼接。
                // 改用 BufferedSource.readUtf8Line()：內部以字節邊界切行且正確處理 UTF-8 codepoint，
                // 取代舊版 `read(buffer, 8192) + readUtf8()` —— 舊版會在任意字節處切斷 multi-byte
                // UTF-8 字元、也會把還沒收完的最後一行當成完整 JSON 去解析。
                val dataBuffer = StringBuilder()
                var done = false
                while (!done) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (dataBuffer.isEmpty()) continue
                        val data = dataBuffer.toString()
                        dataBuffer.setLength(0)
                        if (data == "[DONE]") {
                            done = true
                            continue
                        }
                        dispatchSseData(data, onChunk)
                        continue
                    }
                    if (line.startsWith(":")) continue  // SSE comment
                    if (line.startsWith("data:")) {
                        val value = line.substring(5).let {
                            if (it.startsWith(" ")) it.substring(1) else it
                        }
                        if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                        dataBuffer.append(value)
                    }
                    // 其他 SSE 欄位（event:/id:/retry:）本 proxy 未用，安全略過
                }
                // stream 在未遇到 blank line 就關閉時，flush 殘留 buffer；
                // well-formed SSE 不會走到這裡，屬防禦性處理。
                if (dataBuffer.isNotEmpty() && !done) {
                    val data = dataBuffer.toString()
                    if (data != "[DONE]") dispatchSseData(data, onChunk)
                }
                onDone()
            }
        } catch (e: Exception) {
            onError(e.message ?: "network error")
        }
    }

    private fun dispatchSseData(data: String, onChunk: (String) -> Unit) {
        try {
            val chunk = json.parseToJsonElement(data).jsonObject
            val text = chunk["text"]?.jsonPrimitive?.content
            if (!text.isNullOrEmpty()) onChunk(text)
        } catch (_: Exception) {
            // 單一 event payload malformed 不中斷 stream
        }
    }

    // ── 內部工具 ──

    private fun buildRequest(path: String): Request.Builder {
        val url = if (path.startsWith("http")) path else "$baseUrl$path"
        val builder = Request.Builder()
            .url(url)
            .header("X-Platform-Key", platformKey)
            .header("Content-Type", "application/json")
        session.getAuthToken()?.let { token ->
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
                    val refreshed = session.refreshSession()
                    if (refreshed) {
                        val retryReq = request.newBuilder()
                            .header("Authorization", "Bearer ${session.getAuthToken() ?: ""}")
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

    // ── 結果型別 ──

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
