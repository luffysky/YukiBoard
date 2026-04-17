package com.snowrealm.yukiboard.claw.provider

import com.snowrealm.yukiboard.app.net.AppHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class InvalidKeyException(message: String) : Exception(message)
class RateLimitException(message: String) : Exception(message)

class OpenAIProvider(private val apiKey: String) : AIProvider {

    companion object {
        private const val BASE_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODELS_URL = "https://api.openai.com/v1/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STREAMING).build()

    override val type: AIProviderType = AIProviderType.OPENAI

    override val availableModels: List<String> = listOf("gpt-4o-mini", "gpt-4o")

    override val defaultModel: String = "gpt-4o-mini"

    override suspend fun complete(
        prompt: String,
        systemPrompt: String,
        model: String
    ): Flow<String> = flow {
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
            401 -> {
                response.close()
                throw InvalidKeyException("Invalid OpenAI API key")
            }
            429 -> {
                response.close()
                throw RateLimitException("OpenAI rate limit exceeded")
            }
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            response.close()
            throw IOException("OpenAI API error ${response.code}: $errorBody")
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
                            if (!content.isNullOrEmpty()) {
                                emit(content)
                            }
                        }
                    } catch (_: Exception) {
                        // Skip malformed JSON lines
                    }
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
            .url(MODELS_URL)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val isValid = response.isSuccessful
            response.close()
            isValid
        } catch (_: IOException) {
            false
        }
    }

    override fun estimateTokenCount(text: String): Int {
        var tokens = 0
        for (char in text) {
            if (char.code <= 0x7F) {
                // ASCII: roughly 4 chars per token
                tokens += 1
            } else if (char.code in 0x4E00..0x9FFF ||
                char.code in 0x3400..0x4DBF ||
                char.code in 0x3000..0x303F
            ) {
                // CJK characters: roughly 1.5 chars per token
                tokens += 3
            } else {
                tokens += 1
            }
        }
        // ASCII tokens counted as 1 per char, divide by 4 for estimate
        // CJK tokens counted as 3 per char, divide by 4.5 for estimate
        // Simplified: use weighted average
        return (tokens + 3) / 4
    }
}
