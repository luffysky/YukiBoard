package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

/**
 * AI 語音增強 Skill。
 *
 * 對 AndroidSpeechEngine 的原生辨識結果進行後處理：
 * - 自動加標點符號
 * - 修正語音辨識常見錯字
 * - 優化斷句
 *
 * 無 AI Provider 時用本地基礎標點處理。
 */
class WhisperSkill(private val provider: AIProvider? = null) : ClawSkill {
    override val id = "whisper"
    override val name = "AI 語音增強"
    override val description = "語音辨識後處理（加標點、修正、斷句）"
    override val tokensPerCall = 200

    override suspend fun execute(input: ClawInput): ClawOutput {
        val rawText = input.text.trim()
        if (rawText.isBlank()) {
            return ClawOutput(listOf("未偵測到語音"), OutputType.REWRITE, 0)
        }

        // 無 AI Provider：本地基礎標點處理
        if (provider == null) {
            val processed = addBasicPunctuation(rawText)
            return ClawOutput(listOf(processed), OutputType.REWRITE, 0)
        }

        // 有 AI Provider：智慧後處理
        val prompt = """語音辨識原始結果（可能缺少標點或有誤字）：
「$rawText」

請修正這段語音辨識結果：
1. 加上正確的標點符號（句號、逗號、問號等）
2. 修正可能的同音錯字
3. 保持原意不變，不要改寫或擴充
只輸出修正後的文字，不要解釋。"""

        val systemPrompt = "你是語音辨識後處理器。只修正標點和錯字，不改變原意。繁體中文。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val processed = result.toString().trim()
        return if (processed.isNotEmpty()) {
            ClawOutput(listOf(processed), OutputType.REWRITE, tokensPerCall)
        } else {
            ClawOutput(listOf(rawText), OutputType.REWRITE, 0)
        }
    }

    /** 本地基礎標點（不需 AI，0 token） */
    private fun addBasicPunctuation(text: String): String {
        var result = text
        if (result.isNotEmpty() && result.last().let { it.code in 0x4E00..0x9FFF || it.isLetter() }) {
            result += "。"
        }
        return result
    }
}
