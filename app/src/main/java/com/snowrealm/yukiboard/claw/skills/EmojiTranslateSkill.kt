package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class EmojiTranslateSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "emoji_translate"
    override val name = "Emoji 翻譯"
    override val description = "文字與 Emoji 互轉"
    override val tokensPerCall = 100

    override suspend fun execute(input: ClawInput): ClawOutput {
        val isEmojiInput = input.text.any { Character.getType(it).let { t -> t == Character.SURROGATE.toInt() || t == Character.OTHER_SYMBOL.toInt() } }
        val prompt = if (isEmojiInput) {
            "將以下 Emoji 翻譯成繁體中文文字描述（一行）：${input.text}"
        } else {
            "將以下文字轉換成對應的 Emoji 串（只用 Emoji，不要文字）：${input.text}"
        }
        val systemPrompt = "你是 Emoji 翻譯專家。只回傳翻譯結果，不解釋。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.REWRITE, tokensPerCall)
    }
}
