package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class YukiChatSkill(private val context: Context, private val provider: AIProvider) : ClawSkill {
    override val id = "yuki_chat"
    override val name = "雪凜聊天"
    override val description = "跟雪凜對話，幫你想怎麼說"
    override val tokensPerCall = 600

    override suspend fun execute(input: ClawInput): ClawOutput {
        val promptTemplate = try {
            context.resources.openRawResource(R.raw.claw_yuki_chat_prompt)
                .bufferedReader().readText()
        } catch (_: Exception) {
            "你是雪凜，SnowRealm 的 AI 角色。個性：善意毒舌、短句LINE風格、有梗但真誠。用 1-2 句回應。\n用戶說：{input}"
        }
        val prompt = promptTemplate.replace("{input}", input.text)
        val systemPrompt = "你是雪凜。不要長篇大論。用繁體中文。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.REPLY, tokensPerCall)
    }
}
