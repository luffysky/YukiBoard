package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class GrammarCheckSkill(private val context: Context, private val provider: AIProvider) : ClawSkill {
    override val id = "grammar_check"
    override val name = "文法糾錯"
    override val description = "一鍵修正錯字和文法"
    override val tokensPerCall = 200

    override suspend fun execute(input: ClawInput): ClawOutput {
        val promptTemplate = try {
            context.resources.openRawResource(R.raw.claw_grammar_prompt)
                .bufferedReader().readText()
        } catch (_: Exception) {
            "請修正以下文字的錯字、文法和標點符號。只回傳修正後的文字，不要任何解釋。\n原文：{input}"
        }
        val prompt = promptTemplate.replace("{input}", input.text)
        val systemPrompt = "你是繁體中文文法糾錯專家。只回傳修正結果。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.REWRITE, tokensPerCall)
    }
}
