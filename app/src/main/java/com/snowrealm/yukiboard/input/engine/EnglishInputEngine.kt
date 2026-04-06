package com.snowrealm.yukiboard.input.engine

import android.content.Context
import com.snowrealm.yukiboard.input.dictionary.EnglishDictionary
import com.snowrealm.yukiboard.input.router.InputResult

/**
 * 英文輸入引擎。
 *
 * 邏輯：
 * - 字母逐字加入 composing buffer
 * - Space / Enter → commit buffer 內容（若有選中候選詞則使用候選詞）
 * - Delete → 刪除最後一個字元；buffer 清空則回報 [InputResult.ComposingCleared]
 * - 內建 Trie 字典，輸入時即時產生候選詞
 */
class EnglishInputEngine(context: Context? = null) : InputEngine {

    private val buffer = StringBuilder()
    private val dictionary = EnglishDictionary(context)
    private var cachedCandidates: List<String> = emptyList()

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
        // 如果第一個候選詞和輸入完全不同（自動更正），使用候選詞
        val text = buffer.toString()
        val corrected = if (cachedCandidates.isNotEmpty() && !dictionary.isWord(text)) {
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

    override fun getCandidates(): List<String> {
        return cachedCandidates
    }

    /**
     * 選擇候選詞（由外部候選列點擊觸發）。
     * @return 選中的候選詞文字，或 null
     */
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

    private fun updateCandidateCache() {
        val prefix = buffer.toString()
        if (prefix.isEmpty()) {
            cachedCandidates = emptyList()
            return
        }
        // 1 個字母也給建議（高頻詞 top 5）
        var results = dictionary.suggest(prefix, if (prefix.length == 1) 5 else 9)
        // 先試鄰近鍵容錯（快且準）
        if (results.isEmpty() && prefix.length >= 2) {
            results = dictionary.fuzzyCorrectWithAdjacency(prefix, 5)
        }
        // 再試完整編輯距離（慢但全面）
        if (results.isEmpty() && prefix.length >= 2) {
            results = dictionary.autocorrect(prefix, 5)
        }
        cachedCandidates = results
    }

    /**
     * 不改變引擎狀態，只查詢英文前綴是否有候選詞（供 AUTO 模式評分用）。
     */
    fun getCandidatesForPrefix(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        return dictionary.suggest(prefix, 5)
    }
}
