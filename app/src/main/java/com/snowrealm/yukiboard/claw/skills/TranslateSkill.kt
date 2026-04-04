package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class TranslateSkill(private val context: Context, private val provider: AIProvider) : ClawSkill {
    override val id = "translate"
    override val name = "即時翻譯"
    override val description = "多語言翻譯（中/英/日/韓/西/法/德）"
    override val tokensPerCall = 300

    var targetLanguage: String = "English"

    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            "繁體中文", "English", "日本語", "한국어",
            "Español", "Français", "Deutsch", "Português"
        )

        /** 自動偵測目標語言：如果原文是中文→翻英文，其他→翻中文 */
        fun autoDetectTarget(inputText: String): String {
            val hasCjk = inputText.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
            return if (hasCjk) "English" else "繁體中文"
        }
    }

    override suspend fun execute(input: ClawInput): ClawOutput {
        // 自動偵測目標語言
        val target = if (targetLanguage == "auto") autoDetectTarget(input.text) else targetLanguage

        val promptTemplate = try {
            context.resources.openRawResource(R.raw.claw_translate_prompt)
                .bufferedReader().readText()
        } catch (_: Exception) {
            "請將以下文字翻譯成{target_language}，保留原本語氣和風格。\n原文：{input}\n只輸出翻譯結果，不要解釋。"
        }

        val prompt = promptTemplate
            .replace("{target_language}", target)
            .replace("{input}", input.text)

        val result = StringBuilder()
        provider.complete(prompt, "").collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.TRANSLATE, tokensPerCall)
    }
}
