package com.snowrealm.yukiboard.input.smart

/**
 * 情緒貼圖自動推薦引擎。
 *
 * 根據打字內容偵測情緒，匹配貼圖包標籤，
 * 在候選列末尾浮出 1~3 張推薦貼圖縮圖。
 *
 * 情緒分類：
 * - HAPPY   — 開心/興奮
 * - SAD     — 難過/沮喪
 * - ANGRY   — 生氣/不滿
 * - LOVE    — 喜愛/心動
 * - FUNNY   — 搞笑/無言
 * - COOL    — 酷/帥
 * - NEUTRAL — 無特定情緒
 */
class EmotionStickerEngine {

    /**
     * 偵測文字情緒。
     * @return 最匹配的情緒 + 信心分（0~1）
     */
    fun detectEmotion(text: String): EmotionResult {
        val lower = text.lowercase().trim()
        if (lower.length < 2) return EmotionResult(Emotion.NEUTRAL, 0f)

        val scores = mutableMapOf<Emotion, Float>()

        for ((emotion, keywords) in EMOTION_KEYWORDS) {
            var score = 0f
            for (kw in keywords) {
                if (lower.contains(kw)) score += 1f
            }
            if (score > 0) scores[emotion] = score
        }

        // Emoji 偵測
        for (ch in text) {
            val emotion = EMOJI_EMOTIONS[ch.toString()]
            if (emotion != null) {
                scores[emotion] = (scores[emotion] ?: 0f) + 2f // Emoji 權重更高
            }
        }

        if (scores.isEmpty()) return EmotionResult(Emotion.NEUTRAL, 0f)

        val best = scores.maxByOrNull { it.value }!!
        val confidence = (best.value / 5f).coerceIn(0f, 1f)
        return EmotionResult(best.key, confidence)
    }

    /**
     * 取得情緒對應的貼圖標籤。
     * 用於在 StickerPanelView 中搜尋匹配的貼圖。
     */
    fun getStickerTags(emotion: Emotion): List<String> = EMOTION_STICKER_TAGS[emotion] ?: emptyList()

    /**
     * 完整推薦流程：偵測情緒 → 回傳貼圖標籤。
     * @return 推薦標籤列表（空 = 不推薦）
     */
    fun recommend(text: String, minConfidence: Float = 0.2f): List<String> {
        val result = detectEmotion(text)
        if (result.confidence < minConfidence || result.emotion == Emotion.NEUTRAL) return emptyList()
        return getStickerTags(result.emotion)
    }

    // ─────────────── 資料結構 ───────────────

    enum class Emotion {
        HAPPY, SAD, ANGRY, LOVE, FUNNY, COOL, NEUTRAL
    }

    data class EmotionResult(val emotion: Emotion, val confidence: Float)

    companion object {
        val EMOTION_KEYWORDS = mapOf(
            Emotion.HAPPY to listOf(
                "開心", "高興", "太好了", "耶", "讚", "棒", "恭喜", "哈哈",
                "happy", "great", "awesome", "yay", "nice", "good", "excellent",
                "嘻嘻", "嘿嘿", "好開心", "太棒了"
            ),
            Emotion.SAD to listOf(
                "難過", "傷心", "嗚嗚", "好慘", "唉", "可惜", "遺憾",
                "sad", "sorry", "unfortunately", "miss", "cry",
                "QQ", "qaq", "哭哭", "好難過", "心痛"
            ),
            Emotion.ANGRY to listOf(
                "生氣", "氣死", "可惡", "靠", "煩死了", "討厭",
                "angry", "mad", "damn", "wtf", "annoying",
                "不爽", "火大", "怒"
            ),
            Emotion.LOVE to listOf(
                "喜歡", "愛", "好愛", "心動", "想你", "愛你",
                "love", "like", "adore", "crush", "heart",
                "mua", "親親", "抱抱", "❤"
            ),
            Emotion.FUNNY to listOf(
                "笑死", "哈哈哈", "lol", "xd", "www", "無言",
                "funny", "lmao", "rofl", "笑噴", "太好笑",
                "傻眼", "黑人問號"
            ),
            Emotion.COOL to listOf(
                "帥", "酷", "厲害", "猛", "神", "pro",
                "cool", "sick", "dope", "swag", "epic",
                "大神", "太猛了"
            )
        )

        val EMOJI_EMOTIONS = mapOf(
            "😀" to Emotion.HAPPY, "😊" to Emotion.HAPPY, "🎉" to Emotion.HAPPY,
            "😢" to Emotion.SAD, "😭" to Emotion.SAD, "😞" to Emotion.SAD,
            "😡" to Emotion.ANGRY, "🤬" to Emotion.ANGRY, "😤" to Emotion.ANGRY,
            "❤" to Emotion.LOVE, "😍" to Emotion.LOVE, "🥰" to Emotion.LOVE,
            "😂" to Emotion.FUNNY, "🤣" to Emotion.FUNNY, "😜" to Emotion.FUNNY,
            "😎" to Emotion.COOL, "🔥" to Emotion.COOL, "💪" to Emotion.COOL
        )

        val EMOTION_STICKER_TAGS = mapOf(
            Emotion.HAPPY to listOf("happy", "celebrate", "yay", "excited"),
            Emotion.SAD to listOf("sad", "cry", "comfort", "hug"),
            Emotion.ANGRY to listOf("angry", "mad", "rage", "annoyed"),
            Emotion.LOVE to listOf("love", "heart", "kiss", "romantic"),
            Emotion.FUNNY to listOf("funny", "laugh", "lol", "meme"),
            Emotion.COOL to listOf("cool", "awesome", "fire", "swag")
        )
    }
}
