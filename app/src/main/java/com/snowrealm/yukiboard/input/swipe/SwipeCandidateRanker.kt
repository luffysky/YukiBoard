package com.snowrealm.yukiboard.input.swipe

import com.snowrealm.yukiboard.input.engine.ChewingInputEngine
import com.snowrealm.yukiboard.input.smart.SmartInputManager

/**
 * 滑動輸入候選字排序器。
 *
 * 合併多來源候選（ChewingEngine 注音查詞 + 英文字典 + 自適應學習），
 * 以統一分數排序後回傳。
 */
class SwipeCandidateRanker(
    private val smartInput: SmartInputManager,
    private val chewingEngine: ChewingInputEngine?,
    private val decoder: BopomofoSwipeDecoder = BopomofoSwipeDecoder()
) {

    /**
     * 注音滑動排序：主序列 + 鄰近替代方案全部查詞，合併排分。
     *
     * @param bopomofoSymbols 主解碼注音序列（例：["ㄋ","ㄧ","ㄏ","ㄠ"]）
     * @param maxResults 最多回傳候選數
     */
    fun rankBopomofo(bopomofoSymbols: List<String>, maxResults: Int = 12): List<String> {
        if (chewingEngine == null || bopomofoSymbols.isEmpty()) return emptyList()

        val scored = mutableMapOf<String, Float>()

        // 1. 主序列查詞（最高權重）
        val primary = chewingEngine.lookupByBopomofoString(bopomofoSymbols.joinToString(""))
        primary.forEachIndexed { i, word ->
            scored[word] = (scored[word] ?: 0f) + (20f - i * 0.5f).coerceAtLeast(5f)
        }

        // 2. 鄰近替代序列查詞（降低權重）
        val alternatives = decoder.alternatives(bopomofoSymbols, 4)
        for (altSeq in alternatives) {
            val altResults = chewingEngine.lookupByBopomofoString(altSeq.joinToString(""))
            altResults.take(5).forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (8f - i).coerceAtLeast(1f)
            }
        }

        // 3. 自適應字典加分（使用者常用的詞優先）
        for ((word, score) in scored) {
            val freq = smartInput.adaptiveDictionary.getFrequency(word)
            if (freq > 0) scored[word] = score + freq + 10f
        }

        return scored.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /**
     * 英文滑動排序：合併字典匹配 + 智慧糾錯 + 自適應。
     *
     * @param chars 滑過的字母序列
     * @param maxResults 最多回傳候選數
     */
    fun rankEnglish(chars: List<Char>, maxResults: Int = 12): List<String> {
        if (chars.size < 2) return emptyList()

        val scored = mutableMapOf<String, Float>()

        // 1. SmartInput 滑動建議（含鄰近鍵容錯）
        val swipeSuggestions = smartInput.swipeSuggestEnglish(chars)
        swipeSuggestions.forEachIndexed { i, word ->
            scored[word] = (scored[word] ?: 0f) + (15f - i * 0.5f).coerceAtLeast(3f)
        }

        // 2. 自適應字典加分
        for ((word, score) in scored.toMap()) {
            val freq = smartInput.adaptiveDictionary.getFrequency(word)
            if (freq > 0) scored[word] = score + freq + 5f
        }

        return scored.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }
}
