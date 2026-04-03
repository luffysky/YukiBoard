package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class AIPredictionSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "ai_prediction"
    override val name = "AI 補字"
    override val description = "智慧預測下一個詞"
    override val tokensPerCall = 200

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = "根據以下輸入，預測最可能的 5 個後續詞或短語。只輸出詞語，每行一個，不要編號不要解釋。\n\n輸入：${input.text}\n上下文：${input.context}"
        val systemPrompt = "你是繁體中文輸入法的智慧預測引擎。回應簡短精準。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val suggestions = result.toString().trim().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(5)

        return ClawOutput(suggestions, OutputType.PREDICTION, tokensPerCall)
    }
}
