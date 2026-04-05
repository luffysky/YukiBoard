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
     * 編輯距離 1（所有長度）+ 編輯距離 2（≥5 字元）。
     */
    fun autocorrect(input: String, maxResults: Int = 5): List<String> {
        if (input.length < 2) return emptyList()

        val lower = input.lowercase()

        // 精確前綴匹配
        val exact = suggest(lower, maxResults)
        if (exact.isNotEmpty()) return exact

        val candidates = mutableMapOf<String, Int>() // word → score

        // 編輯距離 1：刪除、替換、插入、交換
        addEditDistance1Candidates(lower, candidates)

        // 編輯距離 2（≥5 字元才做，避免短詞誤判）
        if (lower.length >= 5) {
            addEditDistance2Candidates(lower, candidates)
        }

        return candidates.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    private fun addEditDistance1Candidates(word: String, out: MutableMap<String, Int>) {
        // 刪除一字元
        for (i in word.indices) {
            val attempt = word.removeRange(i, i + 1)
            if (attempt.length >= 2 && isWord(attempt)) {
                out[attempt] = maxOf(out[attempt] ?: 0, frequencyOf(attempt) + 200)
            }
        }
        // 交換相鄰字元
        for (i in 0 until word.length - 1) {
            val chars = word.toCharArray()
            chars[i] = word[i + 1]; chars[i + 1] = word[i]
            val attempt = String(chars)
            if (isWord(attempt)) {
                out[attempt] = maxOf(out[attempt] ?: 0, frequencyOf(attempt) + 250)
            }
        }
        // 替換一字元
        for (i in word.indices) {
            for (c in 'a'..'z') {
                if (c == word[i]) continue
                val attempt = word.replaceRange(i, i + 1, c.toString())
                if (isWord(attempt)) {
                    out[attempt] = maxOf(out[attempt] ?: 0, frequencyOf(attempt) + 150)
                }
            }
        }
        // 插入一字元
        for (i in 0..word.length) {
            for (c in 'a'..'z') {
                val attempt = word.substring(0, i) + c + word.substring(i)
                if (isWord(attempt)) {
                    out[attempt] = maxOf(out[attempt] ?: 0, frequencyOf(attempt) + 100)
                }
            }
        }
    }

    private fun addEditDistance2Candidates(word: String, out: MutableMap<String, Int>) {
        // 刪除兩字元
        for (i in word.indices) {
            val d1 = word.removeRange(i, i + 1)
            for (j in d1.indices) {
                val attempt = d1.removeRange(j, j + 1)
                if (attempt.length >= 3 && isWord(attempt)) {
                    out[attempt] = maxOf(out[attempt] ?: 0, frequencyOf(attempt) + 50)
                }
            }
        }
        // 刪除 1 + 替換 1
        for (i in word.indices) {
            val d1 = word.removeRange(i, i + 1)
            for (j in d1.indices) {
                for (c in 'a'..'z') {
                    if (c == d1[j]) continue
                    val attempt = d1.replaceRange(j, j + 1, c.toString())
                    if (isWord(attempt)) {
                        out[attempt] = maxOf(out[attempt] ?: 0, frequencyOf(attempt) + 30)
                    }
                }
            }
            if (out.size > 30) return // 避免計算量過大
        }
    }

    /**
     * 滑動輸入建議 — 根據滑過的按鍵字母序列，找出匹配的單字。
     * 匹配規則：
     * 1. 單字首字母 == 序列首字母
     * 2. 單字尾字母 == 序列尾字母
     * 3. 序列中的所有字母在單字中以子序列出現（允許跳過）
     * 4. 按頻率排序
     */
    fun swipeSuggest(keySequence: List<Char>, maxResults: Int = 8): List<String> {
        if (keySequence.size < 2) return emptyList()
        val seq = keySequence.map { it.lowercaseChar() }
        val firstChar = seq.first()
        val lastChar = seq.last()

        // 從 Trie 的首字母節點開始收集所有單字
        val startNode = root.children[firstChar] ?: return emptyList()
        val allWords = mutableListOf<Pair<String, Int>>()
        collectWords(startNode, allWords, 500)

        // 過濾：尾字母匹配 + 子序列匹配
        val matched = allWords.filter { (word, _) ->
            word.length >= 2 &&
            word.last() == lastChar &&
            isSubsequence(seq, word)
        }

        return matched
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    /** 檢查 pattern 是否為 text 的子序列 */
    private fun isSubsequence(pattern: List<Char>, text: String): Boolean {
        var pi = 0
        for (ch in text) {
            if (pi < pattern.size && ch == pattern[pi]) {
                pi++
            }
        }
        return pi >= pattern.size
    }

    /** 取得單字頻率分數 */
    fun frequencyOf(word: String): Int {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children[ch] ?: return 0
        }
        return if (node.isWord) node.frequency else 0
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
