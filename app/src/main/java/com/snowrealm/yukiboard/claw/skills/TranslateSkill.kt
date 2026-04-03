package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class TranslateSkill(private val context: Context, private val provider: AIProvider) : ClawSkill {
    override val id = "translate"
    override val name = "即時翻譯"
    override val description = "中英日翻譯"
    override val tokensPerCall = 300

    var targetLanguage: String = "English"

    override suspend fun execute(input: ClawInput): ClawOutput {
        val promptTemplate = context.resources.openRawResource(R.raw.claw_translate_prompt)
            .bufferedReader().readText()

        val prompt = promptTemplate
            .replace("{target_language}", targetLanguage)
            .replace("{input}", input.text)

        val result = StringBuilder()
        provider.complete(prompt, "").collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.TRANSLATE, tokensPerCall)
    }
}
