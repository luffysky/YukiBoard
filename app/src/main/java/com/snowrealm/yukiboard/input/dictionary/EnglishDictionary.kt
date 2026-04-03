package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import com.snowrealm.yukiboard.R
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 英文字典 — 提供單字補全和候選詞建議。
 *
 * 從 `res/raw/english_words.txt` 載入約 20,000 個高頻英文單字（按頻率排序），
 * 使用 Trie 做前綴匹配。
 *
 * 也支援無 Context 的輕量模式（僅用 isWord 判斷，無前綴建議）。
 */
class EnglishDictionary(private val context: Context? = null) {

    private val root = TrieNode()
    private var wordCount = 0

    init {
        if (context != null) {
            loadFromResource(context)
        } else {
            // 無 Context 模式：載入內建基本詞表供 isWord 使用
            BASIC_WORDS.forEachIndexed { index, word ->
                insert(word, BASIC_WORDS.size - index)
            }
        }
    }

    private fun loadFromResource(ctx: Context) {
        try {
            val input = ctx.resources.openRawResource(R.raw.english_words)
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                var rank = 0
                reader.forEachLine { line ->
                    val word = line.trim()
                    if (word.isNotEmpty()) {
                        rank++
                        // 頻率分數：排名越前分數越高（20000 - rank）
                        insert(word, 20001 - rank)
                    }
                }
                wordCount = rank
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 載入失敗降級為基本詞表
            BASIC_WORDS.forEachIndexed { index, word ->
                insert(word, BASIC_WORDS.size - index)
            }
        }
    }

    /** 插入單字到 Trie */
    fun insert(word: String, frequency: Int = 1) {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        node.isWord = true
        node.word = word.lowercase()
        node.frequency = maxOf(node.frequency, frequency)
    }

    /**
     * 根據前綴查詢候選詞。
     * @param prefix 前綴字串
     * @param maxResults 最多返回幾個結果
     * @return 按頻率降序排列的單字列表
     */
    fun suggest(prefix: String, maxResults: Int = 9): List<String> {
        if (prefix.isBlank()) return emptyList()

        val lowerPrefix = prefix.lowercase()
        var node = root
        for (ch in lowerPrefix) {
            node = node.children[ch] ?: return emptyList()
        }

        // 收集所有候選
        val candidates = mutableListOf<Pair<String, Int>>()
        collectWords(node, candidates, maxResults * 3)

        return candidates
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    /** 查詢是否為完整單字 */
    fun isWord(word: String): Boolean {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children[ch] ?: return false
        }
        return node.isWord
    }

    /**
     * 自動更正 — 找最接近的單字。
     * 使用簡單的編輯距離 1 匹配。
     */
    fun autocorrect(input: String, maxResults: Int = 3): List<String> {
        if (input.length < 2) return emptyList()

        val lower = input.lowercase()

        // 精確前綴匹配
        val exact = suggest(lower, maxResults)
        if (exact.isNotEmpty()) return exact

        val candidates = mutableListOf<Pair<String, Int>>()

        // 嘗試刪除最後一個字元
        if (lower.length > 2) {
            val trimmed = lower.dropLast(1)
            val trimSuggestions = suggest(trimmed, maxResults)
            trimSuggestions.forEach { candidates.add(it to 1) }
        }

        // 嘗試替換最後一個字元
        val withoutLast = lower.dropLast(1)
        for (c in 'a'..'z') {
            val attempt = withoutLast + c
            var node = root
            var valid = true
            for (ch in attempt) {
                val next = node.children[ch]
                if (next == null) { valid = false; break }
                node = next
            }
            if (valid) {
                val subCandidates = mutableListOf<Pair<String, Int>>()
                collectWords(node, subCandidates, 3)
                subCandidates.forEach { candidates.add(it) }
            }
        }

        return candidates
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(maxResults)
    }

    private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>, limit: Int) {
        if (results.size >= limit) return
        if (node.isWord) {
            results.add(node.word to node.frequency)
        }
        for ((_, child) in node.children.entries.sortedBy { it.key }) {
            if (results.size >= limit) break
            collectWords(child, results, limit)
        }
    }

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isWord = false
        var word = ""
        var frequency = 0
    }

    companion object {
        /** 基本詞表 — 無 Context 時的降級方案 */
        private val BASIC_WORDS = listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
            "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
            "is", "are", "was", "were", "been", "being", "has", "had", "am", "does",
            "did", "can", "could", "should", "shall", "may", "might", "must", "need",
            "when", "make", "like", "time", "no", "just", "him", "know", "take",
            "people", "into", "year", "your", "good", "some", "could", "them", "see",
            "other", "than", "then", "now", "look", "only", "come", "its", "over",
            "think", "also", "back", "after", "use", "two", "how", "our", "work",
            "first", "well", "way", "even", "new", "want", "because", "any", "these",
            "give", "day", "most", "us", "great", "help", "where", "here", "thing"
        )
    }
}
