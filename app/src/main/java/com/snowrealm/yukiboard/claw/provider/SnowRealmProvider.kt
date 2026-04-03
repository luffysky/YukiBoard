package com.snowrealm.yukiboard.claw.provider

import kotlinx.coroutines.flow.Flow

class SnowRealmProvider : AIProvider {

    override val type: AIProviderType = AIProviderType.SNOWREALM

    override val availableModels: List<String> = listOf("snowrealm-v1")

    override val defaultModel: String = "snowrealm-v1"

    override suspend fun complete(
        prompt: String,
        systemPrompt: String,
        model: String
    ): Flow<String> {
        return kotlinx.coroutines.flow.flow {
            emit("[SnowRealm] Z幣整合尚未開放，請先使用 OpenAI 或 Anthropic。")
        }
    }

    override suspend fun validateKey(apiKey: String): Boolean {
        return false
    }

    override fun estimateTokenCount(text: String): Int {
        return text.length / 4
    }
}
