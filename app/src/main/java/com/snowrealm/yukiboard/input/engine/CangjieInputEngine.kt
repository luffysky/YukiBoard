package com.snowrealm.yukiboard.input.engine

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.input.router.InputResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 倉頡/速成輸入引擎。
 *
 * 支援兩種模式：
 * - **Full Cangjie（倉頡）**：完整 1-5 碼精確比對
 * - **Speed Cangjie（速成/簡易）**：只取首尾碼，顯示所有符合字元
 *
 * ## 按鍵對應
 * 24 個字母鍵（a-w, y）對應 24 個倉頡字根：
 * ```
 * a=日 b=月 c=金 d=木 e=水 f=火 g=土 h=竹 i=戈 j=十
 * k=大 l=中 m=一 n=弓 o=人 p=心 q=手 r=口 s=尸 t=廿
 * u=山 v=女 w=田 y=卜
 * ```
 *
 * ## 使用流程
 * 1. 使用者按字母鍵，composing bar 顯示對應字根（如 "ab" → "日月"）
 * 2. 按 Space 選第一個候選字，或點選候選列選字
 * 3. 最多 5 碼，超過自動丟棄
 *
 * @param speedMode true = 速成模式（首尾碼匹配），false = 完整倉頡模式
 */
class CangjieInputEngine(private val speedMode: Boolean = false, context: Context? = null) : InputEngine {

    init {
        // 嘗試從檔案載入更大的字典
        if (context != null) CangjieDict.loadFromResource(context)
    }

    companion object {
        /** 有效倉頡字母（x 不使用） */
        private const val VALID_KEYS = "abcdefghijklmnopqrstuvwy"

        /** 字母→字根對照表 */
        private val KEY_TO_RADICAL = mapOf(
            'a' to '日', 'b' to '月', 'c' to '金', 'd' to '木', 'e' to '水',
            'f' to '火', 'g' to '土', 'h' to '竹', 'i' to '戈', 'j' to '十',
            'k' to '大', 'l' to '中', 'm' to '一', 'n' to '弓', 'o' to '人',
            'p' to '心', 'q' to '手', 'r' to '口', 's' to '尸', 't' to '廿',
            'u' to '山', 'v' to '女', 'w' to '田', 'y' to '卜'
        )

        /** 最大碼數 */
        private const val MAX_CODE_LENGTH = 5
    }

    /** 字母碼緩衝區（如 "ab"） */
    private val codeBuffer = StringBuilder()

    /** 候選字快取 */
    private var cachedCandidates: List<String> = emptyList()

    /**
     * composing bar 顯示字根字元（如 codeBuffer="ab" → "日月"）
     */
    override val composingText: String
        get() = codeBuffer.map { KEY_TO_RADICAL[it] ?: it }.joinToString("")

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle
        val c = char[0].lowercaseChar()
        if (c !in VALID_KEYS) return InputResult.Idle
        if (codeBuffer.length >= MAX_CODE_LENGTH) return InputResult.Idle

        codeBuffer.append(c)
        updateCandidateCache()
        return InputResult.ComposingUpdated(composingText)
    }

    override fun processDelete(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.ComposingCleared
        codeBuffer.deleteCharAt(codeBuffer.lastIndex)
        if (codeBuffer.isEmpty()) {
            cachedCandidates = emptyList()
            return InputResult.ComposingCleared
        }
        updateCandidateCache()
        return InputResult.ComposingUpdated(composingText)
    }

    override fun processSpace(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.CommitText(" ")
        val candidates = getCandidates()
        if (candidates.isEmpty()) {
            // 無候選字，保留 composing
            return InputResult.ComposingUpdated(composingText)
        }
        val selected = candidates.first()
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(selected)
    }

    override fun processEnter(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.Idle
        // Enter = 提交原始字母碼
        val text = codeBuffer.toString()
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.Idle
        val candidates = getCandidates()
        val text = if (candidates.isNotEmpty()) candidates.first() else codeBuffer.toString()
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> = cachedCandidates

    /**
     * 選擇指定位置的候選字。
     * @param index 候選字索引
     * @return CommitText 或 Idle（索引越界）
     */
    fun selectCandidateAt(index: Int): InputResult {
        val candidate = cachedCandidates.getOrNull(index) ?: return InputResult.Idle
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(candidate)
    }

    override fun reset() {
        codeBuffer.clear()
        cachedCandidates = emptyList()
    }

    // ── 候選字查詢 ───────────────────────────────────────────

    private fun updateCandidateCache() {
        val code = codeBuffer.toString()
        if (code.isEmpty()) {
            cachedCandidates = emptyList()
            return
        }
        cachedCandidates = if (speedMode) {
            lookupSpeed(code)
        } else {
            lookupFull(code)
        }
    }

    /**
     * 完整倉頡模式：精確比對碼值。
     */
    private fun lookupFull(code: String): List<String> {
        return CangjieDict.lookup(code)
    }

    /**
     * 速成模式：
     * - 1 碼：顯示所有以該碼為首碼的字（取前 50）
     * - 2 碼以上：以第一碼和最後一碼進行首尾匹配
     */
    private fun lookupSpeed(code: String): List<String> {
        if (code.length == 1) {
            return CangjieDict.lookupByPrefix(code, 50)
        }
        return CangjieDict.lookupSpeed(code.first(), code.last())
    }
}

// ════════════════════════════════════════════════════════════════
// 倉頡字典 — Lazy Singleton
// ════════════════════════════════════════════════════════════════

/**
 * 倉頡碼→中文字典。
 *
 * 以 [Map<String, List<String>>] 儲存，key 為字母碼（如 "a", "ab"），
 * value 為該碼對應的所有中文字。
 *
 * 字典在首次存取時才載入（lazy），避免啟動延遲。
 * 涵蓋 3000+ 常用繁體中文字（倉頡第三代編碼）。
 */
internal object CangjieDict {

    /** 碼 → 字列表 */
    private var dict: Map<String, List<String>> = emptyMap()
        get() { if (field.isEmpty()) field = buildDict(); return field }

    /** 速成用：(首碼, 尾碼) → 字列表 */
    private var speedIndex: Map<Pair<Char, Char>, List<String>> = emptyMap()
        get() { if (field.isEmpty()) field = buildSpeedIndex(); return field }

    /** 首碼 → 字列表（速成 1 碼用） */
    private var prefixIndex: Map<Char, List<String>> = emptyMap()
        get() { if (field.isEmpty()) field = buildPrefixIndex(); return field }

    /** 從 res/raw/cangjie_dict.txt 載入更大字典 */
    fun loadFromResource(context: Context) {
        try {
            val result = mutableMapOf<String, MutableList<String>>()
            val input = context.resources.openRawResource(R.raw.cangjie_dict)
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith('#')) return@forEachLine
                    val spaceIdx = trimmed.indexOf(' ')
                    if (spaceIdx < 1) return@forEachLine
                    val code = trimmed.substring(0, spaceIdx)
                    val chars = trimmed.substring(spaceIdx + 1)
                    val list = result.getOrPut(code) { mutableListOf() }
                    for (ch in chars) {
                        if (!ch.isWhitespace() && ch.toString() !in list) {
                            list.add(ch.toString())
                        }
                    }
                }
            }
            if (result.size > 100) {
                // 合併內建字典（確保不遺漏）
                for ((code, builtinChars) in buildDict()) {
                    val fileChars = result.getOrPut(code) { mutableListOf() }
                    for (c in builtinChars) {
                        if (c !in fileChars) fileChars.add(c)
                    }
                }
                dict = result
                speedIndex = emptyMap()  // 觸發重建
                prefixIndex = emptyMap()
            }
        } catch (_: Exception) { /* 使用內建字典 */ }
    }

    fun lookup(code: String): List<String> = dict[code] ?: emptyList()

    fun lookupSpeed(first: Char, last: Char): List<String> {
        return speedIndex[first to last] ?: emptyList()
    }

    fun lookupByPrefix(code: String, limit: Int): List<String> {
        if (code.length != 1) return emptyList()
        return prefixIndex[code[0]]?.take(limit) ?: emptyList()
    }

    // ── 索引建構 ─────────────────────────────────────────────

    private fun buildSpeedIndex(): Map<Pair<Char, Char>, List<String>> {
        val index = mutableMapOf<Pair<Char, Char>, MutableList<String>>()
        for ((code, chars) in dict) {
            if (code.isEmpty()) continue
            val key = if (code.length == 1) code[0] to code[0] else code.first() to code.last()
            index.getOrPut(key) { mutableListOf() }.addAll(chars)
        }
        return index.mapValues { (_, v) -> v.distinct() }
    }

    private fun buildPrefixIndex(): Map<Char, List<String>> {
        val index = mutableMapOf<Char, MutableList<String>>()
        for ((code, chars) in dict) {
            if (code.isEmpty()) continue
            index.getOrPut(code[0]) { mutableListOf() }.addAll(chars)
        }
        return index.mapValues { (_, v) -> v.distinct() }
    }

    /**
     * Fallback 空字典 — 正式資料由 res/raw/cangjie_dict.txt 載入。
     */
    private fun buildDict(): Map<String, List<String>> = emptyMap()
}
