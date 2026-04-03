package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences

/**
 * 打字習慣追蹤器 — 捕捉使用者個人化模式。
 *
 * 追蹤項目：
 * - 詞頻排行（最愛用的詞 Top 50）
 * - 時段偏好（早/午/晚 常用詞不同）
 * - App 偏好（LINE 用語 vs IDE 用語）
 * - 打字速度曲線（用於智慧糾錯靈敏度調整）
 *
 * 結果供 [PhrasePredictor] 和 [SmartCorrectionEngine] 參考。
 */
class TypingHabitTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_habits", Context.MODE_PRIVATE)

    // 時段詞頻: "morning" → { "good":20, "coffee":5 }
    private val periodWords = mutableMapOf<String, MutableMap<String, Int>>()
    // App 詞頻: "com.linecorp.LINE" → { "哈哈":15, "OK":10 }
    private val appWords = mutableMapOf<String, MutableMap<String, Int>>()

    // 打字速度追蹤
    private var lastKeystrokeMs = 0L
    private val recentIntervals = mutableListOf<Long>()

    init { loadAll() }

    /** 記錄提交詞 + 當前情境 */
    fun recordCommit(word: String, appPackage: String? = null) {
        val lower = word.lowercase().trim()
        if (lower.isBlank()) return

        // 時段詞頻
        val period = currentPeriod()
        val pMap = periodWords.getOrPut(period) { mutableMapOf() }
        pMap[lower] = (pMap[lower] ?: 0) + 1
        if (pMap.size > MAX_WORDS_PER_BUCKET) pruneMap(pMap)

        // App 詞頻
        if (appPackage != null) {
            val aMap = appWords.getOrPut(appPackage) { mutableMapOf() }
            aMap[lower] = (aMap[lower] ?: 0) + 1
            if (aMap.size > MAX_WORDS_PER_BUCKET) pruneMap(aMap)
        }
    }

    /** 記錄按鍵時間，用於計算平均打字速度 */
    fun recordKeystroke() {
        val now = System.currentTimeMillis()
        if (lastKeystrokeMs > 0) {
            val interval = now - lastKeystrokeMs
            if (interval in 30..5000) {
                recentIntervals.add(interval)
                if (recentIntervals.size > 100) recentIntervals.removeAt(0)
            }
        }
        lastKeystrokeMs = now
    }

    /** 取得目前時段的熱門詞 */
    fun getPeriodSuggestions(maxResults: Int = 5): List<String> {
        val period = currentPeriod()
        val map = periodWords[period] ?: return emptyList()
        return map.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /** 取得特定 App 的熱門詞 */
    fun getAppSuggestions(appPackage: String, maxResults: Int = 5): List<String> {
        val map = appWords[appPackage] ?: return emptyList()
        return map.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /** 平均打字間隔（ms），用於調整糾錯靈敏度 */
    fun averageIntervalMs(): Long {
        if (recentIntervals.size < 5) return 200L
        return recentIntervals.takeLast(20).average().toLong()
    }

    /**
     * 是否打字偏快（容易 typo）。
     * 打字間隔 < 120ms = 快打模式，糾錯應更積極。
     */
    fun isTypingFast(): Boolean = averageIntervalMs() < 120

    /** 持久化 */
    fun save() {
        val editor = prefs.edit()
        saveBucket(editor, "period", periodWords)
        saveBucket(editor, "app", appWords)
        editor.apply()
    }

    /** 清除所有習慣資料 */
    fun clear() {
        periodWords.clear()
        appWords.clear()
        recentIntervals.clear()
        prefs.edit().clear().apply()
    }

    // ─────────────── 內部 ───────────────

    private fun currentPeriod(): String {
        val hour = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
    }

    private fun pruneMap(map: MutableMap<String, Int>) {
        val sorted = map.entries.sortedBy { it.value }
        val toRemove = sorted.take(map.size - MAX_WORDS_PER_BUCKET + 10)
        for (entry in toRemove) map.remove(entry.key)
    }

    private fun saveBucket(
        editor: SharedPreferences.Editor,
        prefix: String,
        data: Map<String, MutableMap<String, Int>>
    ) {
        val keys = mutableListOf<String>()
        for ((bucket, map) in data) {
            val encoded = map.entries.joinToString(";") { "${it.key}:${it.value}" }
            editor.putString("${prefix}_$bucket", encoded)
            keys.add(bucket)
        }
        editor.putString("_${prefix}_keys", keys.joinToString(","))
    }

    private fun loadAll() {
        loadBucket("period", periodWords)
        loadBucket("app", appWords)
    }

    private fun loadBucket(prefix: String, target: MutableMap<String, MutableMap<String, Int>>) {
        val keys = prefs.getString("_${prefix}_keys", null)?.split(",") ?: return
        for (key in keys) {
            if (key.isBlank()) continue
            val raw = prefs.getString("${prefix}_$key", null) ?: continue
            val map = mutableMapOf<String, Int>()
            for (pair in raw.split(";")) {
                val parts = pair.split(":")
                if (parts.size == 2) {
                    map[parts[0]] = parts[1].toIntOrNull() ?: 0
                }
            }
            target[key] = map
        }
    }

    companion object {
        const val MAX_WORDS_PER_BUCKET = 100
    }
}
