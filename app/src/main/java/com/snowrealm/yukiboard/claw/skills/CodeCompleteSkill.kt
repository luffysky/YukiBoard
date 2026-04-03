package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class CodeCompleteSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "code_complete"
    override val name = "程式碼補全"
    override val description = "開發者模式中 AI 補完程式碼"
    override val tokensPerCall = 500

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = "補全以下程式碼片段，只回傳補全的部分（不重複已有的程式碼）。\n\n```\n${input.text}\n```"
        val systemPrompt = "你是程式碼補全引擎。只輸出補全的程式碼，不要解釋、不要 markdown 標記。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val code = result.toString().trim()
            .removePrefix("```").removeSuffix("```").trim()

        return ClawOutput(listOf(code), OutputType.REWRITE, tokensPerCall)
    }
}
