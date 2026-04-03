package com.snowrealm.yukiboard.yuki

import com.snowrealm.yukiboard.emotion.EmotionState
import com.snowrealm.yukiboard.emotion.EmotionState.Tone

/**
 * 語氣偵測 — 本地關鍵字情緒分析（0 token）。
 *
 * 偵測結果寫入 [EmotionState]，供其他模組讀取：
 * - EmotionalCooldownManager 監聽極端情緒
 * - MoodDiarySkill 產生 AI 回應
 * - MoodMiner 累積週報統計
 */
class ToneDetector {

    data class DetectionResult(
        val tone: Tone,
        val confidence: Float,
        val matchedWords: List<String>
    )

    /** 分析文字情緒，同時更新 EmotionState */
    fun analyze(text: String): DetectionResult {
        val matched = mutableListOf<String>()
        val scores = mutableMapOf<Tone, Float>()

        for ((tone, words) in WORD_MAP) {
            var count = 0
            for (word in words) {
                if (text.contains(word)) {
                    count++
                    matched.add(word)
                }
            }
            if (count > 0) {
                scores[tone] = count.toFloat() / words.size
            }
        }

        // 最高分的情緒
        val topEntry = scores.maxByOrNull { it.value }
        val tone = topEntry?.key ?: Tone.NEUTRAL
        val confidence = topEntry?.value?.coerceIn(0f, 1f) ?: 0f

        // 寫入統一情緒狀態
        EmotionState.update(tone, confidence, text)

        return DetectionResult(tone, confidence, matched)
    }

    /** 取得雪凜針對負面/悲傷情緒的回應 */
    fun getYukiResponse(tone: Tone): String? = when (tone) {
        Tone.NEGATIVE -> NEGATIVE_RESPONSES.random()
        Tone.SAD -> SAD_RESPONSES.random()
        Tone.ANGRY -> ANGRY_RESPONSES.random()
        else -> null
    }

    companion object {
        private val WORD_MAP = mapOf(
            Tone.POSITIVE to listOf(
                "開心", "好棒", "太好了", "感謝", "謝謝", "喜歡", "愛",
                "讚", "幸福", "快樂", "哈哈", "嘻嘻", "耶", "太讚",
                "happy", "love", "great", "awesome", "nice", "yay"
            ),
            Tone.NEGATIVE to listOf(
                "難過", "討厭", "煩", "無聊", "爛", "廢",
                "hate", "annoying", "boring", "terrible", "awful"
            ),
            Tone.ANGRY to listOf(
                "生氣", "氣死", "受不了", "夠了", "白痴", "垃圾",
                "angry", "furious", "pissed", "wtf"
            ),
            Tone.SAD to listOf(
                "哭", "慘", "孤單", "寂寞", "痛", "累", "失望",
                "想哭", "好難", "撐不住",
                "sad", "lonely", "tired", "crying", "depressed"
            ),
            Tone.EXCITED to listOf(
                "天啊", "太猛", "狂", "神", "超扯", "猛",
                "OMG", "wow", "insane", "amazing", "incredible"
            )
        )

        private val NEGATIVE_RESPONSES = listOf(
            "別想太多，有我在 💙",
            "沒關係的，休息一下吧",
            "我雖然毒舌但我關心你 ❄"
        )

        private val SAD_RESPONSES = listOf(
            "難過的時候就打字吧，我聽著 💙",
            "抱抱，會過去的 🫂",
            "你不是一個人，雪凜陪你 ❄"
        )

        private val ANGRY_RESPONSES = listOf(
            "深呼吸...1、2、3 😮‍💨",
            "冷靜一下，別衝動",
            "氣歸氣，先不要發出去"
        )
    }
}
