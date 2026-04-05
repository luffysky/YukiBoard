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
    private val authClient = SnowRealmAuthClient.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

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
                            val text = chunk["text"]?.jsonPrimitive?.content ?: return@forEach
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

    // ── 內部工具 ──

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
