package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class CupidSkill(private val context: Context, private val provider: AIProvider) : ClawSkill {
    override val id = "cupid"
    override val name = "情聖月老"
    override val description = "讓訊息更有魅力"
    override val tokensPerCall = 600

    var isMale: Boolean = true

    override suspend fun execute(input: ClawInput): ClawOutput {
        val promptTemplate = try {
            val rawRes = if (isMale) R.raw.claw_cupid_male_prompt else R.raw.claw_cupid_female_prompt
            context.resources.openRawResource(rawRes).bufferedReader().readText()
        } catch (_: Exception) {
            val gender = if (isMale) "男生" else "女生"
            "你是情話改寫專家。用$gender 視角，將以下訊息改寫為 3 個更有魅力的版本。用 --- 分隔。\n原文：{input}"
        }
        val prompt = promptTemplate.replace("{input}", input.text)
        val systemPrompt = "你是情話大師。保持原意，提升魅力。用繁體中文。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val suggestions = result.toString().trim().split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ClawOutput(suggestions, OutputType.REWRITE, tokensPerCall)
    }
}
