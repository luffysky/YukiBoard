package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences

/**
 * 個人詞庫 — 三層自動學詞系統。
 *
 * 層級：
 * - PINNED    — 使用者手動釘選，永不降級
 * - LEARNED   — 7天內使用 ≥3 次自動升級，30天未用降級
 * - CANDIDATE — 初次提交進入候選，待觀察
 *
 * 整合 [AdaptiveDictionary] 的頻率資料 + [TypingHabitTracker] 的情境資料 +
 * UserDictionary 手動詞，提供統一的個人詞庫查詢介面。
 */
class PersonalDictionary(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_personal_dict", Context.MODE_PRIVATE)

    private val entries = java.util.concurrent.ConcurrentHashMap<String, PersonalWord>()

    init { loadAll() }

    // ─────────────── 學詞 ───────────────

    /** 記錄一次使用（由 SmartInputManager.onWordCommitted 呼叫） */
    fun recordUsage(word: String) {
        val key = word.lowercase().trim()
        if (key.isBlank() || key.length < 2 && !isCjk(key)) return

        val now = System.currentTimeMillis()
        val entry = entries.getOrPut(key) {
            PersonalWord(key, tier = Tier.CANDIDATE, frequency = 0, lastUsedMs = now, pinnedMs = 0)
        }
        entry.frequency++
        entry.lastUsedMs = now

        // 自動升級邏輯
        if (entry.tier == Tier.CANDIDATE) {
            val recentCount = entry.frequency
            if (recentCount >= PROMOTE_TO_LEARNED) {
                entry.tier = Tier.LEARNED
            }
        }

        save(entry)
    }

    /** 手動釘選（永不降級） */
    fun pin(word: String) {
        val key = word.lowercase().trim()
        val entry = entries.getOrPut(key) {
            PersonalWord(key, Tier.PINNED, 1, System.currentTimeMillis(), System.currentTimeMillis())
        }
        entry.tier = Tier.PINNED
        entry.pinnedMs = System.currentTimeMillis()
        save(entry)
    }

    /** 取消釘選（降為 LEARNED） */
    fun unpin(word: String) {
        val key = word.lowercase().trim()
        val entry = entries[key] ?: return
        if (entry.tier == Tier.PINNED) {
            entry.tier = Tier.LEARNED
            entry.pinnedMs = 0
            save(entry)
        }
    }

    /** 手動刪除 */
    fun remove(word: String) {
        val key = word.lowercase().trim()
        entries.remove(key)
        prefs.edit().remove("pd_$key").apply()
        updateIndex()
    }

    // ─────────────── 查詢 ───────────────

    /** 前綴匹配（只回傳 PINNED + LEARNED） */
    fun suggest(prefix: String, maxResults: Int = 8): List<String> {
        val lower = prefix.lowercase()
        if (lower.isBlank()) return emptyList()
        return entries.values
            .filter { it.tier != Tier.CANDIDATE && it.word.startsWith(lower) }
            .sortedWith(compareByDescending<PersonalWord> { it.tier.ordinal }
                .thenByDescending { it.frequency })
            .take(maxResults)
            .map { it.word }
    }

    /** 取得所有釘選詞 */
    fun getPinnedWords(): List<String> =
        entries.values.filter { it.tier == Tier.PINNED }
            .sortedByDescending { it.frequency }
            .map { it.word }

    /** 取得所有已學習詞 */
    fun getLearnedWords(): List<Pair<String, Int>> =
        entries.values.filter { it.tier == Tier.LEARNED }
            .sortedByDescending { it.frequency }
            .map { it.word to it.frequency }

    /** 取得所有候選詞（尚未升級） */
    fun getCandidateWords(): List<Pair<String, Int>> =
        entries.values.filter { it.tier == Tier.CANDIDATE }
            .sortedByDescending { it.frequency }
            .map { it.word to it.frequency }

    /** 取得全部詞彙（含層級資訊） */
    fun getAll(): List<PersonalWord> =
        entries.values.sortedWith(
            compareByDescending<PersonalWord> { it.tier.ordinal }
                .thenByDescending { it.frequency }
        )

    /** 是否為已知個人詞 */
    fun isKnown(word: String): Boolean {
        val entry = entries[word.lowercase()] ?: return false
        return entry.tier != Tier.CANDIDATE
    }

    val size: Int get() = entries.size

    // ─────────────── 維護 ───────────────

    /** 定期降級：30天未使用的 LEARNED → CANDIDATE，60天未使用的 CANDIDATE → 刪除 */
    fun maintenance() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()

        for (entry in entries.values) {
            val daysSinceUse = (now - entry.lastUsedMs) / DAY_MS

            when (entry.tier) {
                Tier.PINNED -> {} // 永不降級
                Tier.LEARNED -> {
                    if (daysSinceUse >= DEMOTE_DAYS) {
                        entry.tier = Tier.CANDIDATE
                        save(entry)
                    }
                }
                Tier.CANDIDATE -> {
                    if (daysSinceUse >= PURGE_DAYS) {
                        toRemove.add(entry.word)
                    }
                }
            }
        }

        for (key in toRemove) {
            entries.remove(key)
            prefs.edit().remove("pd_$key").apply()
        }
        if (toRemove.isNotEmpty()) updateIndex()
    }

    /** 匯出為文字（每行一個詞 + 層級） */
    fun export(): String {
        return entries.values
            .sortedWith(compareByDescending<PersonalWord> { it.tier.ordinal }
                .thenByDescending { it.frequency })
            .joinToString("\n") { "${it.word}\t${it.tier.name}\t${it.frequency}" }
    }

    // ─────────────── 序列化 ───────────────

    private fun save(entry: PersonalWord) {
        val value = "${entry.tier.ordinal}|${entry.frequency}|${entry.lastUsedMs}|${entry.pinnedMs}"
        prefs.edit().putString("pd_${entry.word}", value).apply()
        updateIndex()
    }

    private fun loadAll() {
        entries.clear()
        val index = prefs.getString("_pd_index", null)?.split(",") ?: return
        for (word in index) {
            if (word.isBlank()) continue
            val raw = prefs.getString("pd_$word", null) ?: continue
            val parts = raw.split("|")
            if (parts.size < 4) continue
            entries[word] = PersonalWord(
                word = word,
                tier = Tier.entries.getOrElse(parts[0].toIntOrNull() ?: 0) { Tier.CANDIDATE },
                frequency = parts[1].toIntOrNull() ?: 0,
                lastUsedMs = parts[2].toLongOrNull() ?: 0L,
                pinnedMs = parts[3].toLongOrNull() ?: 0L
            )
        }
    }

    private fun updateIndex() {
        prefs.edit().putString("_pd_index", entries.keys.joinToString(",")).apply()
    }

    private fun isCjk(s: String): Boolean =
        s.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }

    // ─────────────── 資料結構 ───────────────

    enum class Tier {
        CANDIDATE, // 觀察中
        LEARNED,   // 自動學習
        PINNED     // 手動釘選
    }

    data class PersonalWord(
        val word: String,
        var tier: Tier,
        var frequency: Int,
        var lastUsedMs: Long,
        var pinnedMs: Long
    )

    companion object {
        const val PROMOTE_TO_LEARNED = 3       // 使用 ≥3 次 → LEARNED
        const val DEMOTE_DAYS = 30L            // 30天未用 → 降級
        const val PURGE_DAYS = 60L             // 60天未用 → 刪除
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
