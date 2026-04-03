package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences

/**
 * 語句預測引擎 — 根據打字習慣預測下一個詞或片語。
 *
 * 學習方式：
 * - 記錄 bigram（A→B）和 trigram（A+B→C）
 * - 每次 commit 更新 n-gram 計數
 * - 預測時合併 bigram + trigram 分數
 *
 * 用途：在候選列末端附加「接續建議」（灰色字，區分自動更正）。
 *
 * 儲存於 SharedPreferences（phrase_predict）。
 */
class PhrasePredictor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_phrase_predict", Context.MODE_PRIVATE)

    // bigram: "hello" → { "world":5, "there":3 }
    private val bigrams = mutableMapOf<String, MutableMap<String, Int>>()
    // trigram: "how are" → { "you":10, "things":2 }
    private val trigrams = mutableMapOf<String, MutableMap<String, Int>>()

    private val recentWords = ArrayDeque<String>(3)

    init { loadAll() }

    /** 記錄一個已提交的詞（按時間順序呼叫） */
    fun onWordCommitted(word: String) {
        val lower = word.lowercase().trim()
        if (lower.isBlank() || lower.length < 1) return

        // 更新 bigram
        if (recentWords.isNotEmpty()) {
            val prev = recentWords.last()
            incrementBigram(prev, lower)
        }

        // 更新 trigram
        if (recentWords.size >= 2) {
            val key = "${recentWords[recentWords.size - 2]} ${recentWords.last()}"
            incrementTrigram(key, lower)
        }

        recentWords.addLast(lower)
        if (recentWords.size > 3) recentWords.removeFirst()
    }

    /**
     * 預測下一個詞/片語。
     * @param maxResults 最多回傳幾個
     * @return 預測詞列表，按信心排序
     */
    fun predict(maxResults: Int = 5): List<PredictionItem> {
        if (recentWords.isEmpty()) return emptyList()

        val scores = mutableMapOf<String, Float>()

        // Trigram 預測（權重高）
        if (recentWords.size >= 2) {
            val key = "${recentWords[recentWords.size - 2]} ${recentWords.last()}"
            val triMap = trigrams[key]
            if (triMap != null) {
                val total = triMap.values.sum().toFloat()
                for ((word, count) in triMap) {
                    scores[word] = (scores[word] ?: 0f) + (count / total) * TRIGRAM_WEIGHT
                }
            }
        }

        // Bigram 預測
        val prev = recentWords.last()
        val biMap = bigrams[prev]
        if (biMap != null) {
            val total = biMap.values.sum().toFloat()
            for ((word, count) in biMap) {
                scores[word] = (scores[word] ?: 0f) + (count / total) * BIGRAM_WEIGHT
            }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { PredictionItem(it.key, it.value) }
    }

    /**
     * 帶前綴的預測 — 使用者正在打字中，過濾符合前綴的預測。
     */
    fun predictWithPrefix(prefix: String, maxResults: Int = 5): List<PredictionItem> {
        if (prefix.isBlank()) return predict(maxResults)
        val lower = prefix.lowercase()
        return predict(maxResults * 2)
            .filter { it.word.startsWith(lower) }
            .take(maxResults)
    }

    /** 重置最近詞（例如換行 / 切 App） */
    fun resetContext() {
        recentWords.clear()
    }

    /** 持久化到 SharedPreferences */
    fun save() {
        val editor = prefs.edit()

        // 存 bigram
        val biKeys = mutableListOf<String>()
        for ((key, map) in bigrams) {
            val encoded = map.entries.joinToString(";") { "${it.key}:${it.value}" }
            editor.putString("bi_$key", encoded)
            biKeys.add(key)
        }
        editor.putString("_bi_keys", biKeys.joinToString(","))

        // 存 trigram
        val triKeys = mutableListOf<String>()
        for ((key, map) in trigrams) {
            val encoded = map.entries.joinToString(";") { "${it.key}:${it.value}" }
            editor.putString("tri_$key", encoded)
            triKeys.add(key)
        }
        editor.putString("_tri_keys", triKeys.joinToString(","))

        editor.apply()
    }

    /** n-gram 統計資訊 */
    fun stats(): Stats = Stats(bigrams.size, trigrams.size)

    data class PredictionItem(val word: String, val confidence: Float)
    data class Stats(val bigramCount: Int, val trigramCount: Int)

    // ─────────────── 內部 ───────────────

    private fun incrementBigram(prev: String, next: String) {
        val map = bigrams.getOrPut(prev) { mutableMapOf() }
        map[next] = (map[next] ?: 0) + 1
        // 防止 map 過大
        if (map.size > MAX_ENTRIES_PER_KEY) pruneMap(map)
    }

    private fun incrementTrigram(key: String, next: String) {
        val map = trigrams.getOrPut(key) { mutableMapOf() }
        map[next] = (map[next] ?: 0) + 1
        if (map.size > MAX_ENTRIES_PER_KEY) pruneMap(map)
    }

    private fun pruneMap(map: MutableMap<String, Int>) {
        val sorted = map.entries.sortedBy { it.value }
        val toRemove = sorted.take(map.size - MAX_ENTRIES_PER_KEY + 10)
        for (entry in toRemove) map.remove(entry.key)
    }

    private fun loadAll() {
        // 載入 bigram
        val biKeys = prefs.getString("_bi_keys", null)?.split(",") ?: emptyList()
        for (key in biKeys) {
            if (key.isBlank()) continue
            val raw = prefs.getString("bi_$key", null) ?: continue
            bigrams[key] = parseMap(raw)
        }

        // 載入 trigram
        val triKeys = prefs.getString("_tri_keys", null)?.split(",") ?: emptyList()
        for (key in triKeys) {
            if (key.isBlank()) continue
            val raw = prefs.getString("tri_$key", null) ?: continue
            trigrams[key] = parseMap(raw)
        }
    }

    private fun parseMap(raw: String): MutableMap<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (pair in raw.split(";")) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                map[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
        return map
    }

    companion object {
        const val BIGRAM_WEIGHT = 1.0f
        const val TRIGRAM_WEIGHT = 2.5f
        const val MAX_ENTRIES_PER_KEY = 50
    }
}
