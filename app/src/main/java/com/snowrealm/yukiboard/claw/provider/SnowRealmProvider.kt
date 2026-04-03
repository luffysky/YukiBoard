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
        throw NotImplementedError("Z幣整合尚未開放")
    }

    override suspend fun validateKey(apiKey: String): Boolean {
        throw NotImplementedError("Z幣整合尚未開放")
    }

    override fun estimateTokenCount(text: String): Int {
        throw NotImplementedError("Z幣整合尚未開放")
    }
}
