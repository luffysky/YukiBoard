package com.snowrealm.yukiboard.input.swipe

import com.snowrealm.yukiboard.ui.keyboard.KeyModel

/**
 * 注音滑動輸入解碼器。
 *
 * 將滑動經過的按鍵序列轉換為注音符號序列：
 * 1. 去除連續重複符號
 * 2. 過濾非注音符號（聲調可保留）
 * 3. 產生主要解碼序列 + 鄰近鍵替代方案
 */
class BopomofoSwipeDecoder {

    companion object {
        // 注音符號 Unicode 範圍
        private val BOPOMOFO_RANGE = '\u3105'..'\u312F'

        // 聲調符號
        private val TONE_MARKS = setOf('ˊ', 'ˇ', 'ˋ', '˙')

        // 大千式鍵盤鄰近符號表（每個注音的上下左右鄰居）
        private val ADJACENT: Map<String, List<String>> = mapOf(
            // Row 1
            "ㄅ" to listOf("ㄉ", "ㄆ"),
            "ㄉ" to listOf("ㄅ", "ˇ", "ㄊ"),
            "ˇ" to listOf("ㄉ", "ˋ", "ㄍ"),
            "ˋ" to listOf("ˇ", "ㄓ", "ㄐ"),
            "ㄓ" to listOf("ˋ", "ˊ", "ㄔ"),
            "ˊ" to listOf("ㄓ", "˙", "ㄗ"),
            "˙" to listOf("ˊ", "ㄚ", "ㄧ"),
            "ㄚ" to listOf("˙", "ㄞ", "ㄛ"),
            "ㄞ" to listOf("ㄚ", "ㄢ", "ㄟ"),
            "ㄢ" to listOf("ㄞ", "ㄣ"),
            // Row 2
            "ㄆ" to listOf("ㄅ", "ㄊ", "ㄇ"),
            "ㄊ" to listOf("ㄉ", "ㄆ", "ㄍ", "ㄋ"),
            "ㄍ" to listOf("ˇ", "ㄊ", "ㄐ", "ㄎ"),
            "ㄐ" to listOf("ˋ", "ㄍ", "ㄔ", "ㄑ"),
            "ㄔ" to listOf("ㄓ", "ㄐ", "ㄗ", "ㄕ"),
            "ㄗ" to listOf("ˊ", "ㄔ", "ㄧ", "ㄘ"),
            "ㄧ" to listOf("˙", "ㄗ", "ㄛ", "ㄨ"),
            "ㄛ" to listOf("ㄚ", "ㄧ", "ㄟ", "ㄜ"),
            "ㄟ" to listOf("ㄞ", "ㄛ", "ㄣ", "ㄠ"),
            "ㄣ" to listOf("ㄢ", "ㄟ", "ㄤ"),
            // Row 3
            "ㄇ" to listOf("ㄆ", "ㄋ", "ㄈ"),
            "ㄋ" to listOf("ㄊ", "ㄇ", "ㄎ", "ㄌ"),
            "ㄎ" to listOf("ㄍ", "ㄋ", "ㄑ", "ㄏ"),
            "ㄑ" to listOf("ㄐ", "ㄎ", "ㄕ", "ㄒ"),
            "ㄕ" to listOf("ㄔ", "ㄑ", "ㄘ", "ㄖ"),
            "ㄘ" to listOf("ㄗ", "ㄕ", "ㄨ", "ㄙ"),
            "ㄨ" to listOf("ㄧ", "ㄘ", "ㄜ", "ㄩ"),
            "ㄜ" to listOf("ㄛ", "ㄨ", "ㄠ", "ㄝ"),
            "ㄠ" to listOf("ㄟ", "ㄜ", "ㄤ", "ㄡ"),
            "ㄤ" to listOf("ㄣ", "ㄠ", "ㄥ"),
            // Row 4
            "ㄈ" to listOf("ㄇ", "ㄌ"),
            "ㄌ" to listOf("ㄋ", "ㄈ", "ㄏ"),
            "ㄏ" to listOf("ㄎ", "ㄌ", "ㄒ"),
            "ㄒ" to listOf("ㄑ", "ㄏ", "ㄖ"),
            "ㄖ" to listOf("ㄕ", "ㄒ", "ㄙ"),
            "ㄙ" to listOf("ㄘ", "ㄖ", "ㄩ"),
            "ㄩ" to listOf("ㄨ", "ㄙ", "ㄝ"),
            "ㄝ" to listOf("ㄜ", "ㄩ", "ㄡ"),
            "ㄡ" to listOf("ㄠ", "ㄝ", "ㄥ"),
            "ㄥ" to listOf("ㄤ", "ㄡ"),
            // Row 5
            "ㄦ" to listOf("ㄈ"),
        )
    }

    /** 從滑過的按鍵解碼出注音符號序列（去除連續重複） */
    fun decode(keys: List<KeyModel>): List<String> {
        val symbols = mutableListOf<String>()
        for (key in keys) {
            val label = key.label
            if (!isBopomofoOrTone(label)) continue
            // 去連續重複
            if (symbols.lastOrNull() != label) {
                symbols.add(label)
            }
        }
        return symbols
    }

    /**
     * 產生替代解碼方案（鄰近鍵容錯）。
     *
     * 對每個位置嘗試替換為鄰近鍵，產生 N 個替代序列。
     * @return 最多 [maxAlternatives] 個替代序列
     */
    fun alternatives(primary: List<String>, maxAlternatives: Int = 6): List<List<String>> {
        val results = mutableListOf<List<String>>()
        for (i in primary.indices) {
            val neighbors = ADJACENT[primary[i]] ?: continue
            for (neighbor in neighbors) {
                if (!isBopomofoChar(neighbor)) continue
                val alt = primary.toMutableList()
                alt[i] = neighbor
                if (alt != primary && alt !in results) {
                    results.add(alt)
                    if (results.size >= maxAlternatives) return results
                }
            }
        }
        return results
    }

    /** 取得指定符號的鄰近鍵 */
    fun getAdjacentKeys(symbol: String): List<String> = ADJACENT[symbol] ?: emptyList()

    private fun isBopomofoOrTone(label: String): Boolean {
        if (label.length != 1) return false
        val ch = label[0]
        return ch in BOPOMOFO_RANGE || ch in TONE_MARKS
    }

    private fun isBopomofoChar(label: String): Boolean {
        if (label.length != 1) return false
        return label[0] in BOPOMOFO_RANGE
    }
}
