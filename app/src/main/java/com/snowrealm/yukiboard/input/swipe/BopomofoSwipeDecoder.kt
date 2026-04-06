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

        // 大千式鍵盤完整 8 方向鄰近表（含斜角 + 聲調鍵）
        private val ADJACENT: Map<String, List<String>> = mapOf(
            // Row 0（數字列）
            "ㄅ" to listOf("ㄉ", "ˇ", "ㄆ", "ㄊ"),
            "ㄉ" to listOf("ㄅ", "ˇ", "ˋ", "ㄆ", "ㄊ", "ㄍ"),
            "ˇ"  to listOf("ㄉ", "ˋ", "ㄊ", "ㄍ"),
            "ˋ"  to listOf("ˇ", "ㄓ", "ˊ", "ㄍ", "ㄐ", "ㄔ"),
            "ㄓ" to listOf("ˋ", "ˊ", "˙", "ㄐ", "ㄔ", "ㄗ"),
            "ˊ"  to listOf("ㄓ", "˙", "ㄔ", "ㄗ", "ㄧ"),
            "˙"  to listOf("ˊ", "ㄚ", "ㄗ", "ㄧ", "ㄛ"),
            "ㄚ" to listOf("˙", "ㄞ", "ㄧ", "ㄛ", "ㄟ"),
            "ㄞ" to listOf("ㄚ", "ㄢ", "ㄛ", "ㄟ", "ㄣ"),
            "ㄢ" to listOf("ㄞ", "ㄟ", "ㄣ"),
            // Row 1（QWERTY 列）
            "ㄆ" to listOf("ㄅ", "ㄉ", "ˇ", "ㄊ", "ㄇ", "ㄋ"),
            "ㄊ" to listOf("ㄅ", "ㄉ", "ˇ", "ˋ", "ㄆ", "ㄍ", "ㄇ", "ㄋ", "ㄎ"),
            "ㄍ" to listOf("ㄉ", "ˇ", "ˋ", "ㄊ", "ㄐ", "ㄋ", "ㄎ", "ㄑ"),
            "ㄐ" to listOf("ˇ", "ˋ", "ㄓ", "ㄍ", "ㄔ", "ㄎ", "ㄑ", "ㄕ"),
            "ㄔ" to listOf("ˋ", "ㄓ", "ˊ", "ㄐ", "ㄗ", "ㄑ", "ㄕ", "ㄘ"),
            "ㄗ" to listOf("ㄓ", "ˊ", "˙", "ㄔ", "ㄧ", "ㄕ", "ㄘ", "ㄨ"),
            "ㄧ" to listOf("ˊ", "˙", "ㄚ", "ㄗ", "ㄛ", "ㄘ", "ㄨ", "ㄜ"),
            "ㄛ" to listOf("˙", "ㄚ", "ㄞ", "ㄧ", "ㄟ", "ㄨ", "ㄜ", "ㄠ"),
            "ㄟ" to listOf("ㄚ", "ㄞ", "ㄢ", "ㄛ", "ㄣ", "ㄜ", "ㄠ", "ㄤ"),
            "ㄣ" to listOf("ㄞ", "ㄢ", "ㄟ", "ㄠ", "ㄤ"),
            // Row 2（ASDF 列）
            "ㄇ" to listOf("ㄆ", "ㄊ", "ㄋ", "ㄈ", "ㄌ"),
            "ㄋ" to listOf("ㄆ", "ㄊ", "ㄍ", "ㄇ", "ㄎ", "ㄈ", "ㄌ", "ㄏ"),
            "ㄎ" to listOf("ㄊ", "ㄍ", "ㄐ", "ㄋ", "ㄑ", "ㄌ", "ㄏ", "ㄒ"),
            "ㄑ" to listOf("ㄍ", "ㄐ", "ㄔ", "ㄎ", "ㄕ", "ㄏ", "ㄒ", "ㄖ"),
            "ㄕ" to listOf("ㄐ", "ㄔ", "ㄗ", "ㄑ", "ㄘ", "ㄒ", "ㄖ", "ㄙ"),
            "ㄘ" to listOf("ㄔ", "ㄗ", "ㄧ", "ㄕ", "ㄨ", "ㄖ", "ㄙ", "ㄩ"),
            "ㄨ" to listOf("ㄗ", "ㄧ", "ㄛ", "ㄘ", "ㄜ", "ㄙ", "ㄩ", "ㄝ"),
            "ㄜ" to listOf("ㄧ", "ㄛ", "ㄟ", "ㄨ", "ㄠ", "ㄩ", "ㄝ", "ㄡ"),
            "ㄠ" to listOf("ㄛ", "ㄟ", "ㄣ", "ㄜ", "ㄤ", "ㄝ", "ㄡ", "ㄥ"),
            "ㄤ" to listOf("ㄟ", "ㄣ", "ㄠ", "ㄡ", "ㄥ"),
            // Row 3（ZXCV 列）
            "ㄈ" to listOf("ㄇ", "ㄋ", "ㄌ", "ㄦ"),
            "ㄌ" to listOf("ㄇ", "ㄋ", "ㄎ", "ㄈ", "ㄏ", "ㄦ"),
            "ㄏ" to listOf("ㄋ", "ㄎ", "ㄑ", "ㄌ", "ㄒ"),
            "ㄒ" to listOf("ㄎ", "ㄑ", "ㄕ", "ㄏ", "ㄖ"),
            "ㄖ" to listOf("ㄑ", "ㄕ", "ㄘ", "ㄒ", "ㄙ"),
            "ㄙ" to listOf("ㄕ", "ㄘ", "ㄨ", "ㄖ", "ㄩ"),
            "ㄩ" to listOf("ㄘ", "ㄨ", "ㄜ", "ㄙ", "ㄝ"),
            "ㄝ" to listOf("ㄨ", "ㄜ", "ㄠ", "ㄩ", "ㄡ"),
            "ㄡ" to listOf("ㄜ", "ㄠ", "ㄤ", "ㄝ", "ㄥ"),
            "ㄥ" to listOf("ㄠ", "ㄤ", "ㄡ"),
            // Row 4
            "ㄦ" to listOf("ㄈ", "ㄌ"),
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
