package com.snowrealm.yukiboard.claw.provider

import kotlinx.coroutines.flow.Flow

enum class AIProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI,
    GROQ,
    SNOWREALM
}

data class AIProviderConfig(
    val type: AIProviderType,
    val apiKey: String,
    val model: String,
    val baseUrl: String
)

interface AIProvider {
    val type: AIProviderType
    val availableModels: List<String>
    val defaultModel: String
    suspend fun complete(prompt: String, systemPrompt: String, model: String = defaultModel): Flow<String>
    suspend fun validateKey(apiKey: String): Boolean
    fun estimateTokenCount(text: String): Int
}
