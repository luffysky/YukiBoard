package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import android.content.SharedPreferences

/**
 * 使用者自訂詞庫 — 學習使用者輸入的新詞。
 *
 * 儲存在 SharedPreferences，每個詞記錄使用頻率。
 * 當使用者手動輸入字典中不存在的詞並提交時，自動加入。
 */
class UserDictionary(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_user_dict", Context.MODE_PRIVATE)

    private val words = mutableMapOf<String, Int>() // word → frequency

    init {
        loadAll()
    }

    /** 加入或更新詞頻 */
    fun learn(word: String) {
        if (word.length < 2) return
        val lower = word.lowercase()
        val freq = (words[lower] ?: 0) + 1
        words[lower] = freq
        prefs.edit().putInt("word_$lower", freq).apply()
        updateWordList()
    }

    /** 刪除一個自訂詞 */
    fun forget(word: String) {
        val lower = word.lowercase()
        words.remove(lower)
        prefs.edit().remove("word_$lower").apply()
        updateWordList()
    }

    /** 取得所有使用者詞彙（按頻率排序） */
    fun getAll(): List<Pair<String, Int>> {
        return words.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    /** 前綴匹配查詢 */
    fun suggest(prefix: String, maxResults: Int = 5): List<String> {
        if (prefix.length < 2) return emptyList()
        val lower = prefix.lowercase()
        return words.entries
            .filter { it.key.startsWith(lower) }
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /** 是否包含此詞 */
    fun contains(word: String): Boolean = words.containsKey(word.lowercase())

    /** 詞庫大小 */
    val size: Int get() = words.size

    /** 清空所有自訂詞 */
    fun clear() {
        words.clear()
        prefs.edit().clear().apply()
    }

    private fun loadAll() {
        words.clear()
        val wordList = prefs.getString("_word_list", null)?.split(",") ?: return
        for (word in wordList) {
            if (word.isBlank()) continue
            val freq = prefs.getInt("word_$word", 0)
            if (freq > 0) words[word] = freq
        }
    }

    private fun updateWordList() {
        prefs.edit().putString("_word_list", words.keys.joinToString(",")).apply()
    }
}
