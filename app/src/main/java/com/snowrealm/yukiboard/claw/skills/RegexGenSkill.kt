package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class RegexGenSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "regex_gen"
    override val name = "正則產生器"
    override val description = "描述需求產生 Regex"
    override val tokensPerCall = 300

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = "根據以下需求產生正則表達式。回傳格式：第一行正則，第二行簡短說明。\n\n需求：${input.text}"
        val systemPrompt = "你是正則表達式專家。回傳可直接使用的 regex。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.REWRITE, tokensPerCall)
    }
}
