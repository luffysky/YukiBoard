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
        val rawRes = if (isMale) R.raw.claw_cupid_male_prompt else R.raw.claw_cupid_female_prompt
        val promptTemplate = context.resources.openRawResource(rawRes)
            .bufferedReader().readText()

        val prompt = promptTemplate.replace("{input}", input.text)

        val result = StringBuilder()
        provider.complete(prompt, "").collect { result.append(it) }

        val suggestions = result.toString().trim().split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ClawOutput(suggestions, OutputType.REWRITE, tokensPerCall)
    }
}
