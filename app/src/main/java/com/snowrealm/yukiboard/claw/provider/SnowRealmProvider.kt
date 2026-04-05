package com.snowrealm.yukiboard.claw.provider

import android.content.Context
import android.provider.Settings
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.auth.InviteCodeManager
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SnowRealm AI 代理 — 透過 insight-engine 轉發，使用者不需自己填 API key。
 * 透過邀請碼驗證身份 + 每日 token 額度控制。
 */
class SnowRealmProvider(private val context: Context) : AIProvider {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val baseUrl = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')
    private val platformApiKey = BuildConfig.PLATFORM_API_KEY
    private val inviteManager = InviteCodeManager(context)
    private val authClient = SnowRealmAuthClient.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override val type: AIProviderType = AIProviderType.SNOWREALM

    override val availableModels: List<String> = listOf(
        "anthropic/claude-haiku-4-5-20251001",
        "openai/gpt-4o-mini"
    )

    override val defaultModel: String = "anthropic/claude-haiku-4-5-20251001"

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

        // 解析 provider/model 格式
        val parts = (if (model.isBlank()) defaultModel else model).split("/", limit = 2)
        val provider = if (parts.size == 2) parts[0] else "anthropic"
        val modelName = if (parts.size == 2) parts[1] else parts[0]

        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        val requestBody = buildJsonObject {
            if (inviteCode != null) put("invite_code", inviteCode)
            if (authToken != null) put("auth_token", authToken)
            put("device_id", deviceId)
            put("provider", provider)
            put("model", modelName)
            put("prompt", prompt)
            if (systemPrompt.isNotBlank()) put("system_prompt", systemPrompt)
            put("max_tokens", 1024)
            put("stream", true)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/yukiboard/ai-proxy")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Platform-Key", platformApiKey)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            val errMsg = try {
                json.parseToJsonElement(errBody).jsonObject["error"]?.jsonPrimitive?.content
            } catch (_: Exception) { null }
            throw RuntimeException(errMsg ?: "AI 代理錯誤 (${response.code})")
        }

        // SSE 串流解析
        val source = response.body?.source() ?: throw RuntimeException("empty response body")
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
                        val text = obj["text"]?.jsonPrimitive?.content ?: return@forEach
                        if (text.isNotEmpty()) emit(text)
                        obj["tokens_used"]?.jsonPrimitive?.content?.toIntOrNull()?.let {
                            totalTokens += it
                        }
                    } catch (_: Exception) {}
                }
            }
        } finally {
            source.close()
            response.close()
        }

        if (totalTokens > 0) inviteManager.consumeTokens(totalTokens)
    }.flowOn(IO)

    /** 根據使用者訂閱等級回傳推薦 model */
    fun getRecommendedModel(plan: SnowRealmAuthClient.UserPlan): String = when (plan) {
        SnowRealmAuthClient.UserPlan.FREE -> "anthropic/claude-haiku-4-5-20251001"
        SnowRealmAuthClient.UserPlan.PLUS -> "anthropic/claude-sonnet-4-6"
        SnowRealmAuthClient.UserPlan.PRO,
        SnowRealmAuthClient.UserPlan.ENTERPRISE -> "anthropic/claude-opus-4-6"
    }

    override suspend fun validateKey(apiKey: String): Boolean {
        // SnowRealm 用邀請碼或平台禮遇登入
        return inviteManager.isActivated() || authClient.isLoggedIn
    }

    override fun estimateTokenCount(text: String): Int {
        var count = 0
        for (ch in text) {
            count += if (ch.code > 0x7F) 3 else 1
        }
        return count / 4
    }
}
