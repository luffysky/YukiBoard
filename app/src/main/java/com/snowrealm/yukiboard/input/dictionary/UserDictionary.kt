package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import android.content.SharedPreferences

/**
 * 使用者自訂詞庫。
 *
 * 支援兩種模式：
 * 1. 自動學習：使用者輸入提交後自動記錄（僅記詞，無注音）
 * 2. 手動新增：使用者在設定頁手動加詞 + 注音（供注音輸入查詢）
 *
 * 儲存格式：
 * - word_頻率：word → frequency（自動學習）
 * - manual_詞：word → bopomofo（手動新增，有注音）
 */
class UserDictionary(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_user_dict", Context.MODE_PRIVATE)

    // 自動學習詞庫：word → frequency
    private val learnedWords = mutableMapOf<String, Int>()

    // 手動新增詞庫：word → bopomofo
    private val manualWords = mutableMapOf<String, String>()

    // 反向索引：bopomofo → List<word>（供注音查詢）
    private val bopomofIndex = mutableMapOf<String, MutableList<String>>()

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
     * 手動新增詞彙（帶注音）。
     * 例如：addManual("迷客夏", "ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ")
     */
    fun addManual(word: String, bopomofo: String) {
        if (word.isBlank() || bopomofo.isBlank()) return
        manualWords[word] = bopomofo
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
        val bopomofo = manualWords.remove(word) ?: return
        bopomofIndex[bopomofo]?.remove(word)
        if (bopomofIndex[bopomofo]?.isEmpty() == true) {
            bopomofIndex.remove(bopomofo)
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

    /** 取得所有手動新增詞彙（word → bopomofo） */
    fun getAllManual(): List<Pair<String, String>> =
        manualWords.entries.map { it.key to it.value }

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

        // 載入手動新增詞
        manualWords.clear()
        bopomofIndex.clear()
        val manualList = prefs.getString("_manual_list", null)?.split("||") ?: emptyList()
        for (entry in manualList) {
            if (entry.isBlank()) continue
            val parts = entry.split("\t")
            if (parts.size == 2) {
                val word = parts[0]
                val bopo = parts[1]
                manualWords[word] = bopo
                bopomofIndex.getOrPut(bopo) { mutableListOf() }.add(word)
            }
        }
    }

    private fun updateLearnedWordList() {
        prefs.edit().putString("_word_list", learnedWords.keys.joinToString(",")).apply()
    }

    private fun saveManualWords() {
        val encoded = manualWords.entries.joinToString("||") { "${it.key}\t${it.value}" }
        prefs.edit().putString("_manual_list", encoded).apply()
    }
}
