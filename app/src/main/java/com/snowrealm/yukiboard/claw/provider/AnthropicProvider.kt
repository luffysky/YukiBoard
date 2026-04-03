package com.snowrealm.yukiboard.claw.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AnthropicProvider(private val apiKey: String) : AIProvider {

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override val type: AIProviderType = AIProviderType.ANTHROPIC

    override val availableModels: List<String> = listOf(
        "claude-haiku-4-5-20251001",
        "claude-sonnet-4-5-20250514"
    )

    override val defaultModel: String = "claude-haiku-4-5-20251001"

    override suspend fun complete(
        prompt: String,
        systemPrompt: String,
        model: String
    ): Flow<String> = flow {
        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("stream", true)
            put("system", systemPrompt)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(BASE_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        when (response.code) {
            401 -> {
                response.close()
                throw InvalidKeyException("Invalid Anthropic API key")
            }
            429 -> {
                response.close()
                throw RateLimitException("Anthropic rate limit exceeded")
            }
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            response.close()
            throw IOException("Anthropic API error ${response.code}: $errorBody")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val reader = body.charStream().buffered()

        try {
            var currentEvent = ""
            var line = reader.readLine()

            while (line != null) {
                when {
                    line.startsWith("event: ") -> {
                        currentEvent = line.removePrefix("event: ").trim()
                        if (currentEvent == "message_stop") break
                    }
                    line.startsWith("data: ") && currentEvent == "content_block_delta" -> {
                        val data = line.removePrefix("data: ").trim()
                        try {
                            val jsonObject = json.parseToJsonElement(data).jsonObject
                            val deltaType = jsonObject["type"]?.jsonPrimitive?.content
                            if (deltaType == "content_block_delta") {
                                val delta = jsonObject["delta"]?.jsonObject
                                if (delta != null) {
                                    val textDeltaType = delta["type"]?.jsonPrimitive?.content
                                    if (textDeltaType == "text_delta") {
                                        val text = delta["text"]?.jsonPrimitive?.content
                                        if (!text.isNullOrEmpty()) {
                                            emit(text)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Skip malformed JSON lines
                        }
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
        val requestBody = buildJsonObject {
            put("model", defaultModel)
            put("max_tokens", 1)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "hi")
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(BASE_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
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
                tokens += 1
            } else if (char.code in 0x4E00..0x9FFF ||
                char.code in 0x3400..0x4DBF ||
                char.code in 0x3000..0x303F
            ) {
                tokens += 3
            } else {
                tokens += 1
            }
        }
        return (tokens + 3) / 4
    }
}
