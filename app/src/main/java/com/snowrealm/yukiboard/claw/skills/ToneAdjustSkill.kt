package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class ToneAdjustSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "tone_adjust"
    override val name = "語氣調整"
    override val description = "正式/casual/可愛/專業四種語氣"
    override val tokensPerCall = 300

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = """將以下文字分別改寫為四種語氣，每種一行，用 --- 分隔：
1. 正式語氣
2. 輕鬆語氣
3. 可愛語氣
4. 專業語氣

原文：${input.text}"""
        val systemPrompt = "你是語氣轉換專家。保持原意，只改語氣。每種語氣一行，用 --- 分隔。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val suggestions = result.toString().trim()
            .split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ClawOutput(suggestions, OutputType.REWRITE, tokensPerCall)
    }
}
