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
 * Groq AI Provider — 超快推論。
 *
 * 使用 Groq Cloud API（OpenAI 相容格式）。
 * 免費方案：每分鐘 30 次請求，每天 14,400 次。
 */
class GroqProvider(private val apiKey: String) : AIProvider {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override val type = AIProviderType.GROQ
    override val availableModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it", "mixtral-8x7b-32768")
    override val defaultModel = "llama-3.3-70b-versatile"

    override suspend fun complete(
        prompt: String,
        systemPrompt: String,
        model: String
    ): Flow<String> = flow {
        // Groq API 與 OpenAI 格式完全相容
        val requestBody = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(BASE_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        when (response.code) {
            401 -> { response.close(); throw InvalidKeyException("Invalid Groq API key") }
            429 -> { response.close(); throw RateLimitException("Groq rate limit exceeded") }
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            response.close()
            throw IOException("Groq API error ${response.code}: $errorBody")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val reader = body.charStream().buffered()

        try {
            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val jsonObject = json.parseToJsonElement(data).jsonObject
                        val choices = jsonObject["choices"]?.jsonArray
                        if (choices != null && choices.isNotEmpty()) {
                            val delta = choices[0].jsonObject["delta"]?.jsonObject
                            val content = delta?.get("content")?.jsonPrimitive?.content
                            if (!content.isNullOrEmpty()) emit(content)
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
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        try {
            val response = client.newCall(request).execute()
            val valid = response.isSuccessful
            response.close()
            valid
        } catch (_: IOException) { false }
    }

    override fun estimateTokenCount(text: String): Int = (text.length + 3) / 4
}
