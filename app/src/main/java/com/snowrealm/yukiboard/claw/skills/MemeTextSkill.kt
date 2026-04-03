package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class MemeTextSkill(private val context: Context, private val provider: AIProvider) : ClawSkill {
    override val id = "meme_text"
    override val name = "迷因生成"
    override val description = "產生流行語和梗圖文字"
    override val tokensPerCall = 200

    override suspend fun execute(input: ClawInput): ClawOutput {
        val promptTemplate = try {
            context.resources.openRawResource(R.raw.claw_meme_prompt)
                .bufferedReader().readText()
        } catch (_: Exception) {
            "你是台灣網路迷因專家。根據以下情境產生 3 個迷因文字，台灣網路風格。\n情境：{input}\n每個用 --- 分隔，不要解釋。"
        }
        val prompt = promptTemplate.replace("{input}", input.text)
        val systemPrompt = "你是台灣網路梗圖文字專家。回應要有梗、好笑、台灣風格。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val suggestions = result.toString().trim()
            .split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ClawOutput(suggestions, OutputType.REWRITE, tokensPerCall)
    }
}
