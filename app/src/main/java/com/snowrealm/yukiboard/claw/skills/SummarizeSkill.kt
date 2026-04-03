package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class SummarizeSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "summarize"
    override val name = "摘要精簡"
    override val description = "長文縮成一句話"
    override val tokensPerCall = 300

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = "將以下文字精簡為一句話（不超過30字），保留核心意思。只回傳精簡結果。\n\n原文：${input.text}"
        val systemPrompt = "你是摘要專家。極簡風格，一句搞定。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.REWRITE, tokensPerCall)
    }
}
