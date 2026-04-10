package com.snowrealm.yukiboard.speech

import com.snowrealm.yukiboard.claw.ClawClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class VoicePostProcessor(private val clawClient: ClawClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun process(raw: String, language: String, onResult: (String) -> Unit) {
        if (raw.isBlank() || raw.length <= 4) { onResult(raw); return }
        scope.launch {
            try {
                val result = StringBuilder()
                clawClient.streamSkill(
                    prompt = buildPrompt(raw, language),
                    systemPrompt = "你是語音辨識後處理助手。只輸出處理後文字，不要任何解釋。"
                ).collect { token -> result.append(token) }
                val cleaned = result.toString().trim()
                withContext(Dispatchers.Main) {
                    onResult(if (cleaned.isNotBlank()) cleaned else raw)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onResult(raw) }
            }
        }
    }

    private fun buildPrompt(raw: String, language: String) = when {
        language.startsWith("zh") -> """
清理以下語音辨識文字：
1. 移除填充詞（嗯、那個、就是、然後、對對對）
2. 移除明顯重複
3. 說話者改變主意時只保留最後意圖
4. 統一繁體中文
5. 保持原有語氣，不過度潤飾
6. 只輸出處理後文字

原始文字：$raw""".trimIndent()
        language.isBlank() -> """
清理以下多語言語音辨識文字：
1. 移除各語言的填充詞（中：嗯/那個/就是，英：um/uh/like，日：えーと/あの，韓：음/그）
2. 移除重複，保留最後意圖
3. 中文統一繁體，其他語言保持原文
4. 保持語言切換的原有順序和語氣
5. 只輸出處理後文字

原始文字：$raw""".trimIndent()
        else -> """Clean up this voice transcription:
1. Remove filler words (um, uh, like, you know)
2. Remove repetitions; keep final intent if speaker corrects themselves
3. Keep original tone; output cleaned text only

Raw: $raw""".trimIndent()
    }

    fun destroy() { scope.cancel() }
}
