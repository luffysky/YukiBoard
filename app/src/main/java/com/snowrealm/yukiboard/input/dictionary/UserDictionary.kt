package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import android.content.SharedPreferences

/**
 * 使用者自訂詞庫。
 *
 * 支援兩種模式：
 * 1. 自動學習：使用者輸入提交後自動記錄（僅記詞，無注音）
 * 2. 手動新增：使用者在設定頁手動加詞 + 注音 + 分類（供注音輸入查詢）
 *
 * 儲存格式：
 * - word_頻率：word → frequency（自動學習）
 * - manual_詞：word|bopomofo|category（手動新增，有注音 + 分類）
 *
 * 分類：一般, 人名, 地名, 專有名詞, 工作用語, 網路用語, 自訂
 */
class UserDictionary(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_user_dict", Context.MODE_PRIVATE)

    // 自動學習詞庫：word → frequency
    private val learnedWords = mutableMapOf<String, Int>()

    // 手動新增詞庫：word → ManualEntry(bopomofo, category)
    data class ManualEntry(val bopomofo: String, val category: String = DEFAULT_CATEGORY)

    private val manualWords = mutableMapOf<String, ManualEntry>()

    // 反向索引：bopomofo → List<word>（供注音查詢）
    private val bopomofIndex = mutableMapOf<String, MutableList<String>>()

    companion object {
        const val DEFAULT_CATEGORY = "一般"
        val BUILT_IN_CATEGORIES = listOf("一般", "人名", "地名", "專有名詞", "工作用語", "網路用語", "自訂")
    }

    init {
        loadAll()
    }

    // ─────────────────────────────────────────
    // 自動學習（原有功能，保持相容）
    // ─────────────────────────────────────────

    /** 自動學習：使用者輸入提交後呼叫 */
    fun learn(word: String) {
        if (word.length < 2) return
        val lower = word.lowercase()
        val freq = (learnedWords[lower] ?: 0) + 1
        learnedWords[lower] = freq
        prefs.edit().putInt("word_$lower", freq).apply()
        updateLearnedWordList()
    }

    /** 移除自動學習的詞 */
    fun forget(word: String) {
        val lower = word.lowercase()
        learnedWords.remove(lower)
        prefs.edit().remove("word_$lower").apply()
        updateLearnedWordList()
    }

    /** 前綴匹配查詢（英文自動補字用） */
    fun suggest(prefix: String, maxResults: Int = 5): List<String> {
        if (prefix.length < 2) return emptyList()
        val lower = prefix.lowercase()
        return learnedWords.entries
            .filter { it.key.startsWith(lower) }
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /** 取得所有自動學習詞彙（按頻率排序） */
    fun getAll(): List<Pair<String, Int>> =
        learnedWords.entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun contains(word: String): Boolean = learnedWords.containsKey(word.lowercase())

    val size: Int get() = learnedWords.size

    // ─────────────────────────────────────────
    // 手動新增詞彙（新功能）
    // ─────────────────────────────────────────

    /**
     * 手動新增詞彙（帶注音 + 分類）。
     * 例如：addManual("迷客夏", "ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ", "專有名詞")
     */
    fun addManual(word: String, bopomofo: String, category: String = DEFAULT_CATEGORY) {
        if (word.isBlank() || bopomofo.isBlank()) return
        manualWords[word] = ManualEntry(bopomofo, category)
        // 更新反向索引
        bopomofIndex.getOrPut(bopomofo) { mutableListOf() }.let {
            if (word !in it) it.add(word)
        }
        saveManualWords()
    }

    /**
     * 刪除手動新增詞彙。
     */
    fun removeManual(word: String) {
        val entry = manualWords.remove(word) ?: return
        bopomofIndex[entry.bopomofo]?.remove(word)
        if (bopomofIndex[entry.bopomofo]?.isEmpty() == true) {
            bopomofIndex.remove(entry.bopomofo)
        }
        saveManualWords()
    }

    /**
     * 根據注音查詢手動新增的詞彙。
     * 支援精確匹配和無聲調模糊匹配。
     *
     * @param bopomofo 注音字串，例如 "ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ"
     */
    fun queryByBopomofo(bopomofo: String): List<String> {
        val results = mutableListOf<String>()

        // 精確匹配
        bopomofIndex[bopomofo]?.let { results.addAll(it) }

        // 無聲調模糊匹配
        if (results.isEmpty()) {
            val stripped = bopomofo.replace(Regex("[ˉˊˇˋ˙]"), "").trim()
            for ((bopo, words) in bopomofIndex) {
                val bStripped = bopo.replace(Regex("[ˉˊˇˋ˙]"), "").trim()
                if (bStripped == stripped) results.addAll(words)
            }
        }

        return results.distinct()
    }

    /** 取得所有手動新增詞彙（word, bopomofo, category） */
    fun getAllManual(): List<Pair<String, String>> =
        manualWords.entries.map { it.key to it.value.bopomofo }

    /** 取得所有手動新增詞彙（含分類） */
    fun getAllManualWithCategory(): List<Triple<String, String, String>> =
        manualWords.entries.map { Triple(it.key, it.value.bopomofo, it.value.category) }

    /** 依分類查詢 */
    fun queryByCategory(category: String): List<Triple<String, String, String>> {
        if (category == "全部") return getAllManualWithCategory()
        return manualWords.entries
            .filter { it.value.category == category }
            .map { Triple(it.key, it.value.bopomofo, it.value.category) }
    }

    /** 取得所有使用中的分類（含內建） */
    fun getAllCategories(): List<String> {
        val used = manualWords.values.map { it.category }.toSet()
        return (BUILT_IN_CATEGORIES + used).distinct()
    }

    /**
     * 更新手動新增詞彙。
     * @param oldWord 原本的詞
     * @param newWord 新的詞
     * @param newBopomofo 新的注音
     * @param newCategory 新的分類
     */
    fun updateManual(oldWord: String, newWord: String, newBopomofo: String, newCategory: String = DEFAULT_CATEGORY) {
        removeManual(oldWord)
        addManual(newWord, newBopomofo, newCategory)
    }

    /**
     * 匯出手動詞庫為 TSV 格式字串（word\tbopomofo\tcategory 每行一筆）。
     */
    fun exportToString(): String =
        manualWords.entries.joinToString("\n") { "${it.key}\t${it.value.bopomofo}\t${it.value.category}" }

    /**
     * 從 TSV 格式字串匯入手動詞彙。
     * 支援舊格式 (word\tbopomofo) 及新格式 (word\tbopomofo\tcategory)。
     * @return 成功匯入的筆數
     */
    fun importFromString(data: String): Int {
        var count = 0
        for (line in data.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val parts = trimmed.split("\t")
            if (parts.size >= 2) {
                val word = parts[0].trim()
                val bopo = parts[1].trim()
                val cat = if (parts.size >= 3) parts[2].trim() else DEFAULT_CATEGORY
                if (word.isNotBlank() && bopo.isNotBlank()) {
                    addManual(word, bopo, cat)
                    count++
                }
            }
        }
        return count
    }

    /** 清空全部 */
    fun clear() {
        learnedWords.clear()
        manualWords.clear()
        bopomofIndex.clear()
        prefs.edit().clear().apply()
    }

    // ─────────────────────────────────────────
    // 私有方法
    // ─────────────────────────────────────────

    private fun loadAll() {
        // 載入自動學習詞
        learnedWords.clear()
        val wordList = prefs.getString("_word_list", null)?.split(",") ?: emptyList()
        for (word in wordList) {
            if (word.isBlank()) continue
            val freq = prefs.getInt("word_$word", 0)
            if (freq > 0) learnedWords[word] = freq
        }

        // 載入手動新增詞（支援新格式 word\tbopo\tcategory 和舊格式 word\tbopo）
        manualWords.clear()
        bopomofIndex.clear()
        val manualList = prefs.getString("_manual_list", null)?.split("||") ?: emptyList()
        for (entry in manualList) {
            if (entry.isBlank()) continue
            val parts = entry.split("\t")
            if (parts.size >= 2) {
                val word = parts[0]
                val bopo = parts[1]
                val cat = if (parts.size >= 3) parts[2] else DEFAULT_CATEGORY
                manualWords[word] = ManualEntry(bopo, cat)
                bopomofIndex.getOrPut(bopo) { mutableListOf() }.add(word)
            }
        }
    }

    private fun updateLearnedWordList() {
        prefs.edit().putString("_word_list", learnedWords.keys.joinToString(",")).apply()
    }

    private fun saveManualWords() {
        val encoded = manualWords.entries.joinToString("||") { "${it.key}\t${it.value.bopomofo}\t${it.value.category}" }
        prefs.edit().putString("_manual_list", encoded).apply()
    }
}
