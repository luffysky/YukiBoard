package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class QuickReplySkill(private val provider: AIProvider) : ClawSkill {
    override val id = "quick_reply"
    override val name = "快速回覆"
    override val description = "建議整句回覆"
    override val tokensPerCall = 400

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = "對方傳來以下訊息，請建議 3 種不同風格的回覆（友善/幽默/簡短）。每種回覆用 --- 分隔。\n\n收到的訊息：${input.text}"
        val systemPrompt = "你是回覆建議助手。用台灣繁體中文，LINE 聊天風格。每個回覆不超過 20 字。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val suggestions = result.toString().trim()
            .split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ClawOutput(suggestions, OutputType.REPLY, tokensPerCall)
    }
}
