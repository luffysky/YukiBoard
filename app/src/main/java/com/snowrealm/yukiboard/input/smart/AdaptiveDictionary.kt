package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences
import com.snowrealm.yukiboard.input.dictionary.EnglishDictionary

/**
 * 自適應字典 — 自動學習使用者常用詞彙。
 *
 * 規則：
 * - 使用者提交非字典詞時，計數 +1
 * - 達到 [PROMOTE_THRESHOLD] 次自動加入個人字典
 * - 已收錄詞每次使用提升優先級
 * - 定期淘汰 30 天未使用的低頻詞
 *
 * 儲存於 SharedPreferences（adaptive_dict）。
 */
class AdaptiveDictionary(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_adaptive_dict", Context.MODE_PRIVATE)

    private val entries = java.util.concurrent.ConcurrentHashMap<String, WordEntry>()

    init { loadAll() }

    /** 記錄一次使用。非字典詞達門檻自動收錄 */
    fun recordUsage(word: String, dictionary: EnglishDictionary) {
        val isCjk = word.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        // 英文至少 2 字元，中文允許單字
        if (!isCjk && word.length < 2) return
        val lower = word.lowercase()
        val isStandard = if (isCjk) false else dictionary.isWord(lower)

        val entry = entries.getOrPut(lower) {
            WordEntry(lower, 0, System.currentTimeMillis(), isStandard)
        }
        entry.frequency++
        entry.lastUsedMs = System.currentTimeMillis()

        // 非標準詞達門檻 → 提升為已知詞（中文詞用 2 次就收錄）
        val threshold = if (isCjk) CJK_PROMOTE_THRESHOLD else PROMOTE_THRESHOLD
        if (!isStandard && entry.frequency >= threshold && !entry.promoted) {
            entry.promoted = true
        }

        save(entry)
    }

    /** 取得詞彙使用頻率（0 = 未學過） */
    fun getFrequency(word: String): Int {
        return entries[word.lowercase()]?.frequency ?: 0
    }

    /** 查詢是否為已知詞（含自動學習的） */
    fun isKnownWord(word: String): Boolean {
        val entry = entries[word.lowercase()] ?: return false
        return entry.promoted || entry.isStandard
    }

    /** 前綴匹配，回傳按頻率排序的建議 */
    fun suggest(prefix: String, maxResults: Int = 5): List<String> {
        val isCjk = prefix.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        if (!isCjk && prefix.length < 2) return emptyList()
        val lower = prefix.lowercase()
        return entries.values
            .filter { it.word.startsWith(lower) && (it.promoted || it.isStandard) }
            .sortedByDescending { it.frequency }
            .take(maxResults)
            .map { it.word }
    }

    /** 取得所有已學習詞（已提升的非標準詞） */
    fun getLearnedWords(): List<Pair<String, Int>> {
        return entries.values
            .filter { it.promoted && !it.isStandard }
            .sortedByDescending { it.frequency }
            .map { it.word to it.frequency }
    }

    /** 取得排行榜 — 所有常用詞 Top N */
    fun getTopWords(n: Int = 20): List<Pair<String, Int>> {
        return entries.values
            .sortedByDescending { it.frequency }
            .take(n)
            .map { it.word to it.frequency }
    }

    /** 淘汰 30 天未使用的低頻詞 */
    fun purgeStale() {
        val threshold = System.currentTimeMillis() - STALE_MS
        val stale = entries.values.filter {
            it.lastUsedMs < threshold && it.frequency < PURGE_FREQ_THRESHOLD && !it.isStandard
        }
        for (entry in stale) {
            entries.remove(entry.word)
            prefs.edit().remove("ad_${entry.word}").apply()
        }
        updateIndex()
    }

    /** 手動移除詞彙 */
    fun forget(word: String) {
        val lower = word.lowercase()
        entries.remove(lower)
        prefs.edit().remove("ad_$lower").apply()
        updateIndex()
    }

    /** 詞庫大小 */
    val size: Int get() = entries.size

    // ─────────────── 序列化 ───────────────

    @Volatile private var indexDirty = false

    private fun save(entry: WordEntry) {
        val value = "${entry.frequency}|${entry.lastUsedMs}|${if (entry.isStandard) 1 else 0}|${if (entry.promoted) 1 else 0}"
        prefs.edit().putString("ad_${entry.word}", value).apply()
        indexDirty = true  // 延遲到 persist() 才寫入索引，避免每次按鍵都序列化全部 key
    }

    private fun loadAll() {
        entries.clear()
        val index = prefs.getString("_ad_index", null)?.split(",") ?: return
        for (word in index) {
            if (word.isBlank()) continue
            val raw = prefs.getString("ad_$word", null) ?: continue
            val parts = raw.split("|")
            if (parts.size < 4) continue
            entries[word] = WordEntry(
                word = word,
                frequency = parts[0].toIntOrNull() ?: 0,
                lastUsedMs = parts[1].toLongOrNull() ?: 0L,
                isStandard = parts[2] == "1",
                promoted = parts[3] == "1"
            )
        }
    }

    private fun updateIndex() {
        prefs.edit().putString("_ad_index", entries.keys.joinToString(",")).apply()
        indexDirty = false
    }

    /** 持久化（onFinishInputView 時呼叫），將延遲的索引寫入磁碟 */
    fun persist() {
        if (indexDirty) updateIndex()
    }

    data class WordEntry(
        val word: String,
        var frequency: Int,
        var lastUsedMs: Long,
        val isStandard: Boolean,
        var promoted: Boolean = false
    )

    companion object {
        const val PROMOTE_THRESHOLD = 3
        const val CJK_PROMOTE_THRESHOLD = 2
        const val PURGE_FREQ_THRESHOLD = 2
        const val STALE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
