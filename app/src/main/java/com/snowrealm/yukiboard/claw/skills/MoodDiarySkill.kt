package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class MoodDiarySkill(private val provider: AIProvider? = null) : ClawSkill {
    override val id = "mood_diary"
    override val name = "心情日記"
    override val description = "被動偵測打字情緒"
    override val tokensPerCall = 200

    // 本地關鍵字情緒分析（0 token）
    private val positiveWords = setOf("開心", "好棒", "太好了", "感謝", "謝謝", "喜歡", "愛", "讚", "幸福", "快樂", "哈哈", "嘻嘻")
    private val negativeWords = setOf("難過", "生氣", "討厭", "煩", "累", "痛", "哭", "慘", "爛", "廢", "無聊", "孤單")

    /**
     * 本地情緒分析，0 token。
     */
    fun analyzeLocal(text: String): String {
        val posCount = positiveWords.count { text.contains(it) }
        val negCount = negativeWords.count { text.contains(it) }
        return when {
            posCount > negCount -> "positive"
            negCount > posCount -> "negative"
            else -> "neutral"
        }
    }

    override suspend fun execute(input: ClawInput): ClawOutput {
        // 如果沒有 AI provider，用本地分析
        if (provider == null) {
            val mood = analyzeLocal(input.text)
            val comment = when (mood) {
                "positive" -> "今天心情不錯呢 ✨"
                "negative" -> "沒關係的，會過去的 💙"
                else -> "普通的一天，繼續加油 ⛅"
            }
            return ClawOutput(listOf(comment), OutputType.REPLY, 0)
        }

        val prompt = "分析以下文字的情緒，用一句雪凜風格的短評回應（不超過 15 字）。\n文字：${input.text}"
        val systemPrompt = "你是雪凜。善意毒舌，短句回應。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.REPLY, tokensPerCall)
    }
}
