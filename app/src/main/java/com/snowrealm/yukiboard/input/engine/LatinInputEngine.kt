package com.snowrealm.yukiboard.input.engine

import android.content.Context
import com.snowrealm.yukiboard.input.langpack.LanguagePackManager
import com.snowrealm.yukiboard.input.router.InputResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 通用拉丁字母輸入引擎 — 可載入任意語言的詞庫。
 *
 * 與 EnglishInputEngine 共用相同的 Trie + 候選詞邏輯，
 * 但詞庫來自 LanguagePackManager 下載的詞庫檔案。
 */
class LatinInputEngine(
    private val context: Context,
    private val langId: String
) : InputEngine {

    private val buffer = StringBuilder()
    private val root = TrieNode()
    private var cachedCandidates: List<String> = emptyList()
    private var loaded = false

    init {
        loadDictionary()
    }

    private fun loadDictionary() {
        val mgr = LanguagePackManager.getInstance(context)
        val dictFile = mgr.getDictFile(langId)
        if (dictFile.exists()) {
            try {
                BufferedReader(InputStreamReader(dictFile.inputStream(), Charsets.UTF_8)).use { reader ->
                    var rank = 0
                    reader.forEachLine { line ->
                        val word = line.trim()
                        if (word.isNotEmpty() && !word.startsWith("#")) {
                            rank++
                            insert(word, 20001 - rank)
                        }
                    }
                }
                loaded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 重新載入詞庫（安裝新詞庫後呼叫） */
    fun reloadDictionary() {
        // 清空 Trie
        root.children.clear()
        root.isWord = false
        loaded = false
        loadDictionary()
    }

    val isLoaded: Boolean get() = loaded

    override val composingText: String get() = buffer.toString()

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle
        buffer.append(char)
        updateCandidateCache()
        return InputResult.ComposingUpdated(buffer.toString())
    }

    override fun processDelete(): InputResult {
        if (buffer.isEmpty()) return InputResult.ComposingCleared
        buffer.deleteCharAt(buffer.lastIndex)
        updateCandidateCache()
        return if (buffer.isEmpty()) InputResult.ComposingCleared
        else InputResult.ComposingUpdated(buffer.toString())
    }

    override fun processSpace(): InputResult {
        if (buffer.isEmpty()) return InputResult.CommitText(" ")
        val text = buffer.toString()
        val corrected = if (cachedCandidates.isNotEmpty() && !isWord(text)) {
            cachedCandidates.first()
        } else {
            text
        }
        buffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText("$corrected ")
    }

    override fun processEnter(): InputResult {
        if (buffer.isEmpty()) return InputResult.Idle
        val text = buffer.toString()
        buffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (buffer.isEmpty()) return InputResult.Idle
        val text = buffer.toString()
        buffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> = cachedCandidates

    fun selectCandidate(index: Int): String? {
        val word = cachedCandidates.getOrNull(index) ?: return null
        buffer.clear()
        cachedCandidates = emptyList()
        return word
    }

    override fun reset() {
        buffer.clear()
        cachedCandidates = emptyList()
    }

    // ═══════════════════════════════════════════
    // Trie 實作
    // ═══════════════════════════════════════════

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isWord = false
        var word = ""
        var frequency = 0
    }

    private fun insert(word: String, frequency: Int) {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        node.isWord = true
        node.word = word.lowercase()
        node.frequency = maxOf(node.frequency, frequency)
    }

    private fun isWord(word: String): Boolean {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children[ch] ?: return false
        }
        return node.isWord
    }

    private fun suggest(prefix: String, maxResults: Int = 9): List<String> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()
        var node = root
        for (ch in lower) {
            node = node.children[ch] ?: return emptyList()
        }
        val candidates = mutableListOf<Pair<String, Int>>()
        collectWords(node, candidates, maxResults * 3)
        return candidates
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>, limit: Int) {
        if (results.size >= limit) return
        if (node.isWord) results.add(node.word to node.frequency)
        for ((_, child) in node.children.entries.sortedBy { it.key }) {
            if (results.size >= limit) break
            collectWords(child, results, limit)
        }
    }

    private fun updateCandidateCache() {
        val prefix = buffer.toString()
        if (prefix.isEmpty()) {
            cachedCandidates = emptyList()
            return
        }
        cachedCandidates = suggest(prefix, if (prefix.length == 1) 5 else 9)
    }
}
