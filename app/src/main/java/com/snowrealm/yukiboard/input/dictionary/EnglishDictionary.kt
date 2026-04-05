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
    /**
     * 滑動輸入候選建議 — 模糊匹配 + 評分排序。
     *
     * 演算法：
     * 1. 首字母必須匹配（或是首字母的鄰近鍵）
     * 2. 尾字母必須匹配（或是尾字母的鄰近鍵）
     * 3. 單字的每個字母必須在滑動路徑中出現（允許鄰近鍵替代）
     * 4. 按匹配分數 + 詞頻 + 詞長適配度排序
     */
    fun swipeSuggest(keySequence: List<Char>, maxResults: Int = 8): List<String> {
        if (keySequence.size < 2) return emptyList()
        val seq = keySequence.map { it.lowercaseChar() }
        val firstChar = seq.first()
        val lastChar = seq.last()
        val seqSet = seq.toSet()

        // 收集可能匹配的首字母（含鄰近鍵）
        val possibleFirsts = (ADJACENT_KEYS[firstChar] ?: emptySet()) + firstChar
        val possibleLasts = (ADJACENT_KEYS[lastChar] ?: emptySet()) + lastChar

        val candidates = mutableListOf<Triple<String, Int, Double>>() // word, freq, score

        for (startChar in possibleFirsts) {
            val startNode = root.children[startChar] ?: continue
            val allWords = mutableListOf<Pair<String, Int>>()
            collectWords(startNode, allWords, 2000)

            for ((word, freq) in allWords) {
                if (word.length < 2) continue
                // 尾字母檢查（含鄰近）
                if (word.last() !in possibleLasts) continue
                // 長度合理性：滑動經過的按鍵數跟目標詞長度差異不能太大
                if (word.length > seq.size + 2 || word.length < (seq.size * 0.3).toInt()) continue

                // 計算匹配分數
                val score = swipeMatchScore(word, seq, seqSet)
                if (score > 0.0) {
                    candidates.add(Triple(word, freq, score))
                }
            }
        }

        return candidates
            .sortedWith(compareByDescending<Triple<String, Int, Double>> { it.third }
                .thenByDescending { it.second })
            .take(maxResults)
            .map { it.first }
    }

    /**
     * 計算滑動匹配分數（0.0 = 不匹配，越高越好）。
     *
     * 核心：單字的每個字母必須在滑動路徑上找到（順序匹配），
     * 允許用鄰近鍵替代（扣分但不淘汰）。
     */
    private fun swipeMatchScore(word: String, seq: List<Char>, seqSet: Set<Char>): Double {
        var seqIdx = 0
        var matched = 0
        var adjacentUsed = 0

        for (ch in word) {
            // 在剩餘的滑動序列中找這個字母
            var found = false
            var tempIdx = seqIdx
            while (tempIdx < seq.size) {
                if (seq[tempIdx] == ch) {
                    seqIdx = tempIdx + 1
                    matched++
                    found = true
                    break
                }
                // 鄰近鍵替代
                val adj = ADJACENT_KEYS[ch]
                if (adj != null && seq[tempIdx] in adj) {
                    seqIdx = tempIdx + 1
                    matched++
                    adjacentUsed++
                    found = true
                    break
                }
                tempIdx++
            }
            if (!found) {
                // 這個字母完全不在路徑上 — 檢查是否在路徑集合中或鄰近鍵中
                if (ch in seqSet) {
                    matched++ // 在路徑上但順序不對，輕微匹配
                    adjacentUsed++
                } else {
                    val adj = ADJACENT_KEYS[ch]
                    if (adj != null && adj.any { it in seqSet }) {
                        // 鄰近鍵在路徑上
                        adjacentUsed += 2
                    } else {
                        return 0.0 // 完全不沾邊，淘汰
                    }
                }
            }
        }

        val matchRatio = matched.toDouble() / word.length
        val adjacentPenalty = adjacentUsed * 0.1
        // 詞長與滑動長度的適配度
        val lengthFit = 1.0 - (Math.abs(word.length - seq.size * 0.7) / seq.size.coerceAtLeast(1)).coerceIn(0.0, 0.5)

        return matchRatio * 0.6 + lengthFit * 0.3 + 0.1 - adjacentPenalty
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
        /** QWERTY 鍵盤鄰近鍵映射表（滑動輸入用） */
        private val ADJACENT_KEYS = mapOf(
            'q' to setOf('w', 'a'),
            'w' to setOf('q', 'e', 'a', 's'),
            'e' to setOf('w', 'r', 's', 'd'),
            'r' to setOf('e', 't', 'd', 'f'),
            't' to setOf('r', 'y', 'f', 'g'),
            'y' to setOf('t', 'u', 'g', 'h'),
            'u' to setOf('y', 'i', 'h', 'j'),
            'i' to setOf('u', 'o', 'j', 'k'),
            'o' to setOf('i', 'p', 'k', 'l'),
            'p' to setOf('o', 'l'),
            'a' to setOf('q', 'w', 's', 'z'),
            's' to setOf('a', 'w', 'e', 'd', 'z', 'x'),
            'd' to setOf('s', 'e', 'r', 'f', 'x', 'c'),
            'f' to setOf('d', 'r', 't', 'g', 'c', 'v'),
            'g' to setOf('f', 't', 'y', 'h', 'v', 'b'),
            'h' to setOf('g', 'y', 'u', 'j', 'b', 'n'),
            'j' to setOf('h', 'u', 'i', 'k', 'n', 'm'),
            'k' to setOf('j', 'i', 'o', 'l', 'm'),
            'l' to setOf('k', 'o', 'p'),
            'z' to setOf('a', 's', 'x'),
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )

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
