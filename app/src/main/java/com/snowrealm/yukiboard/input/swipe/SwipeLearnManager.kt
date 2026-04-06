package com.snowrealm.yukiboard.input.swipe

import android.content.Context
import android.content.SharedPreferences

/**
 * 滑動輸入自我學習管理器。
 *
 * 記錄使用者從滑動候選中選擇了哪個詞，
 * 下次相同滑動軌跡優先顯示學過的詞。
 *
 * Key: 注音序列（如 "ㄋㄧㄏㄠ"）
 * Value: 序列化的 word→selectCount 映射
 *
 * 儲存於 SharedPreferences（yukiboard_swipe_learn）。
 */
class SwipeLearnManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_swipe_learn", Context.MODE_PRIVATE)

    // bopomofoKey → (word → SelectionEntry)
    private val entries = mutableMapOf<String, MutableMap<String, SelectionEntry>>()

    init { loadAll() }

    /**
     * 記錄使用者選擇了滑動候選中的某個詞。
     */
    fun recordSelection(bopomofoKey: String, selectedWord: String) {
        if (bopomofoKey.isBlank() || selectedWord.isBlank()) return
        val wordMap = entries.getOrPut(bopomofoKey) { mutableMapOf() }
        val entry = wordMap.getOrPut(selectedWord) {
            SelectionEntry(0, System.currentTimeMillis())
        }
        entry.selectCount++
        entry.lastUsedMs = System.currentTimeMillis()
    }

    /**
     * 取得學習加分。基於選擇次數，使用 log 壓縮避免過度偏好。
     * @return 0f 如果沒學過，否則 1.0~5.0 區間的加分值
     */
    fun getBoost(bopomofoKey: String, word: String): Float {
        val entry = entries[bopomofoKey]?.get(word) ?: return 0f
        // log2(count+1) 壓縮，最高 5.0
        return kotlin.math.ln((entry.selectCount + 1).toDouble()).toFloat()
            .coerceIn(0.5f, 5.0f)
    }

    /**
     * 取得某注音序列下所有學過的詞及其選擇次數。
     */
    fun getLearnedWords(bopomofoKey: String): List<Pair<String, Int>> {
        return entries[bopomofoKey]?.entries
            ?.sortedByDescending { it.value.selectCount }
            ?.map { it.key to it.value.selectCount }
            ?: emptyList()
    }

    /**
     * 儲存所有資料到 SharedPreferences。
     */
    fun save() {
        val editor = prefs.edit()
        // 清除舊資料再完整寫入
        editor.clear()

        val keys = mutableListOf<String>()
        for ((bpmfKey, wordMap) in entries) {
            if (wordMap.isEmpty()) continue
            keys.add(bpmfKey)
            // 序列化: word1:count1:lastMs1|word2:count2:lastMs2|...
            val value = wordMap.entries.joinToString("|") { (word, entry) ->
                "${word}:${entry.selectCount}:${entry.lastUsedMs}"
            }
            editor.putString("sw_$bpmfKey", value)
        }
        editor.putString("_sw_index", keys.joinToString(","))
        editor.apply()
    }

    /**
     * 清除 60 天未使用的過期資料。
     */
    fun purgeStale() {
        val threshold = System.currentTimeMillis() - STALE_MS
        for ((bpmfKey, wordMap) in entries) {
            val stale = wordMap.entries.filter { it.value.lastUsedMs < threshold }
            for (entry in stale) {
                wordMap.remove(entry.key)
            }
        }
        // 移除空的 key
        entries.entries.removeAll { it.value.isEmpty() }
    }

    // ─────────────── 序列化 ───────────────

    private fun loadAll() {
        entries.clear()
        val index = prefs.getString("_sw_index", null)?.split(",") ?: return
        for (bpmfKey in index) {
            if (bpmfKey.isBlank()) continue
            val raw = prefs.getString("sw_$bpmfKey", null) ?: continue
            val wordMap = mutableMapOf<String, SelectionEntry>()
            for (part in raw.split("|")) {
                val segments = part.split(":")
                if (segments.size < 3) continue
                val word = segments[0]
                val count = segments[1].toIntOrNull() ?: continue
                val lastMs = segments[2].toLongOrNull() ?: continue
                wordMap[word] = SelectionEntry(count, lastMs)
            }
            if (wordMap.isNotEmpty()) {
                entries[bpmfKey] = wordMap
            }
        }
    }

    data class SelectionEntry(
        var selectCount: Int,
        var lastUsedMs: Long
    )

    companion object {
        /** 60 天過期 */
        const val STALE_MS = 60L * 24 * 60 * 60 * 1000
    }
}
