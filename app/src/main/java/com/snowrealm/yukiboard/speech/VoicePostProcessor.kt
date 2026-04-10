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
你是語音辨識後處理助手。只輸出處理後文字，不要任何解釋。

規則：
1. 移除填充詞（嗯、那個、就是、然後、對對對、好好好、我覺得說）
2. 移除明顯重複（「我我我覺得」→「我覺得」）
3. 改口修正：若說話者用「不是」「更正」「我是說」「啊不對」修正了自己，只保留最後版本
4. 統一繁體中文（不要簡體）
5. 專有名詞不得修改（Apple、iPhone、LINE、ChatGPT、SnowRealm、YouTube）
6. 保持原有語氣，不要擴寫或腦補

範例：
輸入：我我覺得這個 really 好用
輸出：我覺得這個 really 好用

輸入：明天去台中...不是 台南
輸出：明天去台南

輸入：嗯然後就是我想要訂一個...那個...咖啡廳
輸出：我想要訂一個咖啡廳

輸入：請用 LINE 傳給我
輸出：請用 LINE 傳給我

原始文字：$raw""".trimIndent()
        language.isBlank() -> """
你是語音辨識後處理助手。只輸出處理後文字，不要任何解釋。

規則：
1. 移除各語言填充詞（中：嗯/那個/就是，英：um/uh/like，日：えーと/あの，韓：음/그냥）
2. 改口修正：保留最後版本
3. 中文統一繁體，其他語言保持原文
4. 專有名詞不得修改（Apple、LINE、ChatGPT、SnowRealm）
5. 保持語言混用的順序

範例：
輸入：我覺得這個 feature 很好用 like really 好用
輸出：我覺得這個 feature 很好用

原始文字：$raw""".trimIndent()
        else -> """
You are a voice transcription cleaner. Output only the cleaned text, no explanations.

Rules:
1. Remove filler words (um, uh, like, you know, basically, I mean, so, right)
2. Remove repetitions (keep last occurrence)
3. If speaker corrects themselves with "no wait", "I mean", "actually", keep only the corrected version
4. Keep proper nouns unchanged (Apple, LINE, ChatGPT, SnowRealm)
5. Keep original tone, don't expand or add content

Examples:
Input: um I I think this is like really useful
Output: I think this is really useful

Input: let's meet at the coffee shop no wait the restaurant
Output: let's meet at the restaurant

Input: send it via LINE
Output: send it via LINE

Raw: $raw""".trimIndent()
    }

    fun destroy() { scope.cancel() }
}
