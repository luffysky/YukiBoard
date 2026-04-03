package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*

/**
 * AI 語音增強 Skill。
 *
 * 語音輸入的流程統一由 [com.snowrealm.yukiboard.speech.SpeechInputEngine] 處理：
 * - 預設使用 AndroidSpeechEngine（免費，Android 原生）
 * - 開啟「AI 語音增強」時，走 Whisper API 做降噪/後處理
 *
 * 此 Skill 不是獨立的語音入口，而是 AndroidSpeechEngine 的 **後處理增強**。
 * 由 YukiBoardIMEService.toggleVoiceInput() 統一觸發。
 */
class WhisperSkill : ClawSkill {
    override val id = "whisper"
    override val name = "AI 語音增強"
    override val description = "Whisper 降噪 + 後處理（增強原生語音辨識）"
    override val tokensPerCall = 500

    override suspend fun execute(input: ClawInput): ClawOutput {
        // input.text = AndroidSpeechEngine 的原生辨識結果
        // 此 Skill 用 Whisper API 做二次處理（降噪、標點、修正）
        // TODO: Implement Whisper API post-processing
        // 目前先直接回傳原生結果
        val result = input.text.ifBlank { "語音增強功能開發中" }
        return ClawOutput(listOf(result), OutputType.REWRITE, 0)
    }
}
