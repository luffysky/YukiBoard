package com.snowrealm.yukiboard.claw.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Gemini AI Provider。
 *
 * 使用 Gemini API (generativelanguage.googleapis.com) 的 SSE streaming。
 * 免費方案：gemini-2.0-flash 每分鐘 15 次請求。
 */
class GeminiProvider(private val apiKey: String) : AIProvider {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override val type = AIProviderType.GEMINI
    override val availableModels = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash")
    override val defaultModel = "gemini-2.0-flash"

    override suspend fun complete(
        prompt: String,
        systemPrompt: String,
        model: String
    ): Flow<String> = flow {
        val url = "$BASE_URL/$model:streamGenerateContent?alt=sse&key=$apiKey"

        val requestBody = buildJsonObject {
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemPrompt) })
                    })
                })
            }
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        when (response.code) {
            400, 403 -> {
                response.close()
                throw InvalidKeyException("Invalid Gemini API key or quota exceeded")
            }
            429 -> {
                response.close()
                throw RateLimitException("Gemini rate limit exceeded")
            }
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            response.close()
            throw IOException("Gemini API error ${response.code}: $errorBody")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val reader = body.charStream().buffered()

        try {
            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    try {
                        val jsonObj = json.parseToJsonElement(data).jsonObject
                        val candidates = jsonObj["candidates"]?.jsonArray
                        if (candidates != null && candidates.isNotEmpty()) {
                            val content = candidates[0].jsonObject["content"]?.jsonObject
                            val parts = content?.get("parts")?.jsonArray
                            if (parts != null && parts.isNotEmpty()) {
                                val text = parts[0].jsonObject["text"]?.jsonPrimitive?.content
                                if (!text.isNullOrEmpty()) emit(text)
                            }
                        }
                    } catch (_: Exception) {}
                }
                line = reader.readLine()
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?key=$apiKey"
        val request = Request.Builder().url(url).get().build()
        try {
            val response = client.newCall(request).execute()
            val valid = response.isSuccessful
            response.close()
            valid
        } catch (_: IOException) { false }
    }

    override fun estimateTokenCount(text: String): Int {
        // Gemini tokenization similar to OpenAI
        return (text.length + 3) / 4
    }
}
