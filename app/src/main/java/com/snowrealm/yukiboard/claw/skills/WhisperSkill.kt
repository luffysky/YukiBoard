package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*

class WhisperSkill : ClawSkill {
    override val id = "whisper"
    override val name = "AI 語音"
    override val description = "OpenAI Whisper 語音辨識"
    override val tokensPerCall = 500

    override suspend fun execute(input: ClawInput): ClawOutput {
        // TODO: Implement Whisper API integration with audio recording
        return ClawOutput(listOf("語音輸入功能開發中"), OutputType.PREDICTION, 0)
    }
}
