package com.snowrealm.yukiboard.input.smart

import com.snowrealm.yukiboard.input.dictionary.EnglishDictionary

/**
 * AI 智慧糾錯引擎 — 偵測使用者「可能想打什麼」並修正。
 *
 * 三階段糾錯：
 * 1. 鍵盤鄰近鍵偵測（typo：按到旁邊的鍵）
 * 2. 編輯距離 1-2 的候選匹配
 * 3. 上下文感知（前一個詞 → bigram 偏好）
 *
 * 不依賴網路，純本地運算。
 */
class SmartCorrectionEngine(
    private val dictionary: EnglishDictionary,
    private val adaptiveDictionary: AdaptiveDictionary,
    private val correctionLearnManager: CorrectionLearnManager? = null
) {

    /**
     * 嘗試糾錯。回傳修正建議列表（第一個最可能），空 = 不需修正。
     */
    fun correct(typed: String, prevWord: String? = null): List<String> {
        if (typed.length < 2) return emptyList()
        val lower = typed.lowercase()

        // 已是正確單字 → 不需糾錯
        if (dictionary.isWord(lower)) return emptyList()
        if (adaptiveDictionary.isKnownWord(lower)) return emptyList()

        val candidates = mutableMapOf<String, Float>()

        // 階段 1：鍵盤鄰近鍵替換
        addNeighborCandidates(lower, candidates)

        // 階段 2：編輯距離 1
        addEditDistance1(lower, candidates)

        // 階段 3：上下文加權（bigram）
        if (prevWord != null) {
            boostByContext(prevWord, candidates)
        }

        // 階段 4：校正學習加權（使用者歷史接受/拒絕）
        if (correctionLearnManager != null) {
            for ((candidate, score) in candidates.toMap()) {
                val learnBoost = correctionLearnManager.getBoost(lower, candidate)
                if (learnBoost != 0f) {
                    candidates[candidate] = score + learnBoost * CORRECTION_LEARN_WEIGHT
                }
            }
        }

        return candidates.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    /**
     * 快速判斷是否需要糾錯提示。
     */
    fun needsCorrection(typed: String): Boolean {
        if (typed.length < 3) return false
        val lower = typed.lowercase()
        return !dictionary.isWord(lower) && !adaptiveDictionary.isKnownWord(lower)
    }

    // ─────────────── 階段 1：鍵盤鄰近鍵 ───────────────

    private fun addNeighborCandidates(word: String, out: MutableMap<String, Float>) {
        for (i in word.indices) {
            val neighbors = KEYBOARD_NEIGHBORS[word[i]] ?: continue
            for (neighbor in neighbors) {
                val attempt = word.replaceRange(i, i + 1, neighbor.toString())
                if (dictionary.isWord(attempt)) {
                    val score = out.getOrDefault(attempt, 0f) + 3.0f
                    out[attempt] = score
                }
            }
        }
    }

    // ─────────────── 階段 2：編輯距離 1 ───────────────

    private fun addEditDistance1(word: String, out: MutableMap<String, Float>) {
        // 刪除一字元
        for (i in word.indices) {
            val attempt = word.removeRange(i, i + 1)
            if (attempt.length >= 2 && dictionary.isWord(attempt)) {
                out[attempt] = (out[attempt] ?: 0f) + 2.0f
            }
        }

        // 交換相鄰字元
        for (i in 0 until word.length - 1) {
            val chars = word.toCharArray()
            chars[i] = word[i + 1]
            chars[i + 1] = word[i]
            val attempt = String(chars)
            if (dictionary.isWord(attempt)) {
                out[attempt] = (out[attempt] ?: 0f) + 2.5f
            }
        }

        // 插入一字元
        for (i in 0..word.length) {
            for (c in 'a'..'z') {
                val attempt = word.substring(0, i) + c + word.substring(i)
                if (dictionary.isWord(attempt)) {
                    out[attempt] = (out[attempt] ?: 0f) + 1.5f
                }
            }
        }
    }

    // ─────────────── 階段 3：上下文加權 ───────────────

    private fun boostByContext(prevWord: String, candidates: MutableMap<String, Float>) {
        val lower = prevWord.lowercase()
        val boostSet = COMMON_BIGRAMS[lower] ?: return
        for (word in boostSet) {
            if (candidates.containsKey(word)) {
                candidates[word] = (candidates[word] ?: 0f) + 5.0f
            }
        }
    }

    companion object {
        /** 校正學習加分權重 */
        private const val CORRECTION_LEARN_WEIGHT = 8.0f

        /** QWERTY 鍵盤鄰近鍵映射 */
        private val KEYBOARD_NEIGHBORS = mapOf(
            'q' to "wa", 'w' to "qeas", 'e' to "wrds", 'r' to "etdf",
            't' to "ryfg", 'y' to "tugh", 'u' to "yijh", 'i' to "uokj",
            'o' to "iplk", 'p' to "ol",
            'a' to "qwsz", 's' to "awedxz", 'd' to "serfcx", 'f' to "drtgvc",
            'g' to "ftyhbv", 'h' to "gyujnb", 'j' to "huiknm", 'k' to "jiolm",
            'l' to "kop",
            'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb",
            'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
        )

        /** 常見英文雙詞組合（用於 bigram 加權） */
        private val COMMON_BIGRAMS = mapOf(
            "i" to setOf("am", "have", "want", "need", "think", "know", "like", "love", "will", "can"),
            "you" to setOf("are", "have", "can", "will", "need", "want", "know", "should"),
            "the" to setOf("best", "first", "most", "new", "same", "next", "last", "other"),
            "is" to setOf("not", "the", "very", "also", "that", "this"),
            "to" to setOf("the", "be", "do", "have", "get", "make", "go"),
            "in" to setOf("the", "this", "that", "my", "your", "our"),
            "it" to setOf("is", "was", "will", "can", "would", "should"),
            "have" to setOf("been", "to", "not", "the", "any", "some"),
            "do" to setOf("not", "you", "it", "the", "this", "that"),
            "not" to setOf("be", "have", "know", "want", "like", "need"),
            "thank" to setOf("you", "god"),
            "how" to setOf("are", "about", "do", "much", "many", "long"),
            "what" to setOf("is", "are", "do", "about", "the", "happened"),
            "can" to setOf("you", "not", "be", "we", "help"),
            "good" to setOf("morning", "night", "afternoon", "evening", "luck", "job")
        )
    }
}
