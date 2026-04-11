package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences

/**
 * 自動校正自我學習管理器。
 *
 * 記錄使用者接受/拒絕了哪些自動校正建議，
 * 提高接受率高的校正權重，降低常被拒絕的。
 *
 * Key: typed word（使用者打的錯字）
 * Value: Map<correctedWord, AcceptRejectCount>
 *
 * 儲存於 SharedPreferences（yukiboard_correction_learn）。
 */
class CorrectionLearnManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_correction_learn", Context.MODE_PRIVATE)

    // typed → (candidate → CorrectionEntry)
    private val entries = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, CorrectionEntry>>()

    init { loadAll() }

    /**
     * 記錄使用者接受了某個糾錯建議。
     */
    fun recordAccepted(typed: String, corrected: String) {
        if (typed.isBlank() || corrected.isBlank()) return
        val lower = typed.lowercase()
        val corrLower = corrected.lowercase()
        val candidateMap = entries.getOrPut(lower) { mutableMapOf() }
        val entry = candidateMap.getOrPut(corrLower) { CorrectionEntry(0, 0) }
        entry.accepted++
    }

    /**
     * 記錄使用者拒絕了某個糾錯建議（選了其他候選或直接按掉）。
     */
    fun recordRejected(typed: String, corrected: String) {
        if (typed.isBlank() || corrected.isBlank()) return
        val lower = typed.lowercase()
        val corrLower = corrected.lowercase()
        val candidateMap = entries.getOrPut(lower) { mutableMapOf() }
        val entry = candidateMap.getOrPut(corrLower) { CorrectionEntry(0, 0) }
        entry.rejected++
    }

    /**
     * 取得校正學習加分。
     * - 正值：使用者經常接受此校正
     * - 負值：使用者經常拒絕此校正
     * - 0：沒有學習資料
     *
     * @return 範圍約 -3.0 ~ +3.0
     */
    fun getBoost(typed: String, candidate: String): Float {
        val entry = entries[typed.lowercase()]?.get(candidate.lowercase()) ?: return 0f
        val total = entry.accepted + entry.rejected
        if (total == 0) return 0f
        // 接受率 0~1，映射到 -3.0 ~ +3.0
        val acceptRate = entry.accepted.toFloat() / total
        return (acceptRate * 2f - 1f) * 3f  // [-3, +3]
    }

    /**
     * 取得常見錯字→最常接受的校正映射。
     * 用於快速校正。
     */
    fun getCommonMistakes(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((typed, candidateMap) in entries) {
            val best = candidateMap.entries
                .filter { it.value.accepted >= MIN_ACCEPT_FOR_COMMON }
                .maxByOrNull { it.value.accepted - it.value.rejected }
            if (best != null && best.value.accepted > best.value.rejected) {
                result[typed] = best.key
            }
        }
        return result
    }

    /**
     * 儲存所有資料到 SharedPreferences。
     */
    fun save() {
        val editor = prefs.edit()
        editor.clear()

        val keys = mutableListOf<String>()
        for ((typed, candidateMap) in entries) {
            if (candidateMap.isEmpty()) continue
            keys.add(typed)
            // 序列化: candidate1:accepted1:rejected1|candidate2:accepted2:rejected2|...
            val value = candidateMap.entries.joinToString("|") { (candidate, entry) ->
                "${candidate}:${entry.accepted}:${entry.rejected}"
            }
            editor.putString("cl_$typed", value)
        }
        editor.putString("_cl_index", keys.joinToString(","))
        editor.apply()
    }

    // ─────────────── 序列化 ───────────────

    private fun loadAll() {
        entries.clear()
        val index = prefs.getString("_cl_index", null)?.split(",") ?: return
        for (typed in index) {
            if (typed.isBlank()) continue
            val raw = prefs.getString("cl_$typed", null) ?: continue
            val candidateMap = mutableMapOf<String, CorrectionEntry>()
            for (part in raw.split("|")) {
                val segments = part.split(":")
                if (segments.size < 3) continue
                val candidate = segments[0]
                val accepted = segments[1].toIntOrNull() ?: continue
                val rejected = segments[2].toIntOrNull() ?: continue
                candidateMap[candidate] = CorrectionEntry(accepted, rejected)
            }
            if (candidateMap.isNotEmpty()) {
                entries[typed] = candidateMap
            }
        }
    }

    data class CorrectionEntry(
        var accepted: Int,
        var rejected: Int
    )

    companion object {
        /** 至少被接受 3 次才算「常見錯字」 */
        const val MIN_ACCEPT_FOR_COMMON = 3
    }
}
