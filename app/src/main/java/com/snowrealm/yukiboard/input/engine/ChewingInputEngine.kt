package com.snowrealm.yukiboard.input.engine

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.input.router.InputResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 注音輸入引擎 — 內建注音→漢字字典版。
 *
 * 從 `res/raw/bopomofo_dict.txt` 載入注音→漢字對照表，
 * 使用者輸入注音符號後按空白鍵即可取得候選字。
 *
 * 字典格式（每行）：`ㄅㄚ\t八 吧 巴 爸 …`
 * 含聲調版與無聲調版：無聲調版回傳所有聲調的聯集。
 */
class ChewingInputEngine(
    private val context: Context,
    private val userDict: com.snowrealm.yukiboard.input.dictionary.UserDictionary? = null
) : InputEngine {

    /** 直打注音模式：注音符號直接輸出，不轉漢字 */
    var rawBopomofoMode = false

    // ─── 注音組字緩衝 ───
    private val bopomofoBuffer = StringBuilder()
    // 已選字但尚未 commit 的暫存（連續選字場景）
    private val selectedBuffer = StringBuilder()

    // ─── 字典：注音拼音 → 漢字列表 ───
    private val dict: Map<String, List<String>> by lazy { loadDict() }
    // ─── 聲母簡拼詞庫：聲母序列 → 詞組列表 ───
    private val phraseDict: Map<String, List<String>> by lazy { loadPhraseDict() }

    // ─── 當前候選詞 ───
    private var currentCandidates: List<String> = emptyList()
    // 是否正在候選詞選擇狀態（按空白鍵後、選字前）
    private var isSelectingCandidate = false

    // ─── 注音聲母集合 ───
    private val initials = setOf(
        'ㄅ','ㄆ','ㄇ','ㄈ','ㄉ','ㄊ','ㄋ','ㄌ','ㄍ','ㄎ','ㄏ',
        'ㄐ','ㄑ','ㄒ','ㄓ','ㄔ','ㄕ','ㄖ','ㄗ','ㄘ','ㄙ'
    )
    private val medials = setOf('ㄧ','ㄨ','ㄩ')
    private val finals = setOf('ㄚ','ㄛ','ㄜ','ㄝ','ㄞ','ㄟ','ㄠ','ㄡ','ㄢ','ㄣ','ㄤ','ㄥ','ㄦ')

    // ─── 音節狀態（用於自動分音節） ───
    private enum class SyllableState { EMPTY, HAS_INITIAL, HAS_MEDIAL, HAS_FINAL, HAS_TONE }
    private enum class BopomofoCategory { INITIAL, MEDIAL, FINAL, TONE, UNKNOWN }

    private fun bopomofoCategory(ch: Char): BopomofoCategory = when {
        ch in initials -> BopomofoCategory.INITIAL
        ch in medials  -> BopomofoCategory.MEDIAL
        ch in finals   -> BopomofoCategory.FINAL
        ch in toneMarks -> BopomofoCategory.TONE
        else -> BopomofoCategory.UNKNOWN
    }

    /** 判斷是否該在新字元前分音節 */
    private fun shouldSplitBefore(state: SyllableState, cat: BopomofoCategory): Boolean = when (state) {
        SyllableState.EMPTY -> false
        SyllableState.HAS_INITIAL -> cat == BopomofoCategory.INITIAL
        SyllableState.HAS_MEDIAL -> cat == BopomofoCategory.INITIAL || cat == BopomofoCategory.MEDIAL
        SyllableState.HAS_FINAL -> cat != BopomofoCategory.TONE
        SyllableState.HAS_TONE -> true
    }

    private fun nextSyllableState(state: SyllableState, cat: BopomofoCategory): SyllableState = when (cat) {
        BopomofoCategory.INITIAL -> SyllableState.HAS_INITIAL
        BopomofoCategory.MEDIAL  -> SyllableState.HAS_MEDIAL
        BopomofoCategory.FINAL   -> SyllableState.HAS_FINAL
        BopomofoCategory.TONE    -> SyllableState.HAS_TONE
        BopomofoCategory.UNKNOWN -> state
    }

    /** 將注音緩衝自動分割成音節列表 */
    private fun segmentSyllables(buffer: String): List<String> {
        if (buffer.isEmpty()) return emptyList()
        val syllables = mutableListOf<String>()
        val current = StringBuilder()
        var state = SyllableState.EMPTY

        for (ch in buffer) {
            val cat = bopomofoCategory(ch)
            if (shouldSplitBefore(state, cat) && current.isNotEmpty()) {
                syllables.add(current.toString())
                current.clear()
                state = SyllableState.EMPTY
            }
            current.append(ch)
            state = nextSyllableState(state, cat)
        }
        if (current.isNotEmpty()) syllables.add(current.toString())
        return syllables
    }

    // ─── 最後提交的字（用於連續預測） ───
    private var lastCommittedChar: String = ""

    // ─── 聲調符號 ───
    private val toneMarks = setOf(
        '\u02C9', // ˉ 一聲（macron, 有時省略）
        '\u02CA', // ˊ 二聲
        '\u02C7', // ˇ 三聲
        '\u02CB', // ˋ 四聲
        '\u02D9', // ˙ 輕聲
    )

    override val composingText: String
        get() = buildString {
            append(selectedBuffer)
            val buf = bopomofoBuffer.toString()
            val syllables = segmentSyllables(buf)
            if (syllables.size > 1) {
                // 多音節用空格分隔，方便使用者辨識
                append(syllables.joinToString(" "))
            } else {
                append(buf)
            }
        }

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle

        val ch = char[0]

        // 直打注音模式：注音符號和聲調直接輸出
        if (rawBopomofoMode) {
            if (ch in '\u3105'..'\u312F' || ch in toneMarks) {
                return InputResult.CommitText(char)
            }
        }

        return when {
            // 注音符號
            ch in '\u3105'..'\u312F' -> {
                // 如果在候選選擇狀態，先確認第一個候選
                if (isSelectingCandidate) {
                    confirmFirstCandidate()
                }
                bopomofoBuffer.append(ch)
                updateCandidatesFromBuffer()
                InputResult.ComposingUpdated(composingText)
            }
            // 聲調符號 → 加入 buffer 並查字典
            ch in toneMarks -> {
                if (bopomofoBuffer.isEmpty()) return InputResult.Idle
                if (isSelectingCandidate) {
                    confirmFirstCandidate()
                }
                bopomofoBuffer.append(ch)
                updateCandidatesFromBuffer()
                InputResult.ComposingUpdated(composingText)
            }
            // 數字 2-5 作為聲調快捷（2=ˊ 3=ˇ 4=ˋ 5=˙，1=一聲/省略）
            ch in '1'..'5' && bopomofoBuffer.isNotEmpty() -> {
                if (isSelectingCandidate) {
                    // 數字也可作為候選序號（1-9 選字）
                    val idx = ch - '1'
                    if (idx < currentCandidates.size) {
                        return selectCandidateAt(idx)
                    }
                }
                val tone = when (ch) {
                    '1' -> null        // 一聲：不加標記，直接查無調版
                    '2' -> '\u02CA'
                    '3' -> '\u02C7'
                    '4' -> '\u02CB'
                    '5' -> '\u02D9'
                    else -> null
                }
                if (tone != null) bopomofoBuffer.append(tone)
                updateCandidatesFromBuffer()
                InputResult.ComposingUpdated(composingText)
            }
            else -> InputResult.Idle
        }
    }

    override fun processDelete(): InputResult {
        return when {
            isSelectingCandidate -> {
                // 取消候選選擇，回到注音緩衝
                isSelectingCandidate = false
                updateCandidatesFromBuffer()
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            bopomofoBuffer.isNotEmpty() -> {
                bopomofoBuffer.deleteCharAt(bopomofoBuffer.lastIndex)
                updateCandidatesFromBuffer()
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            selectedBuffer.isNotEmpty() -> {
                selectedBuffer.deleteCharAt(selectedBuffer.lastIndex)
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            else -> InputResult.ComposingCleared
        }
    }

    override fun processSpace(): InputResult {
        if (bopomofoBuffer.isEmpty() && selectedBuffer.isEmpty()) {
            return InputResult.CommitText(" ")
        }

        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            // 空白鍵 = 確認第一候選
            return selectCandidateAt(0)
        }

        // 查字典（自動處理單/多音節）
        updateCandidatesFromBuffer()
        if (currentCandidates.isNotEmpty()) {
            isSelectingCandidate = true
            return InputResult.ComposingUpdated(composingText)
        }

        // 查無候選 → 直接 commit 原注音文字
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun processEnter(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        // 如果有候選，選第一個；否則 commit 原文
        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            confirmFirstCandidate()
        }
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            confirmFirstCandidate()
        }
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> = currentCandidates

    override fun reset() {
        bopomofoBuffer.clear()
        selectedBuffer.clear()
        currentCandidates = emptyList()
        isSelectingCandidate = false
        lastCommittedChar = ""
    }

    /**
     * 外部呼叫：使用者從候選列點選第 [index] 個候選字。
     */
    fun selectCandidateAt(index: Int): InputResult {
        if (index !in currentCandidates.indices) return InputResult.Idle

        val selected = currentCandidates[index]
        selectedBuffer.append(selected)
        bopomofoBuffer.clear()
        isSelectingCandidate = false

        // 直接 commit 已選字
        val text = selectedBuffer.toString()
        selectedBuffer.clear()
        lastCommittedChar = text

        // 產生下一字預測候選
        currentCandidates = getNextCharPredictions(text)

        return InputResult.CommitText(text)
    }

    // ─────────────────────────────────────────────────────────────────
    // 字典查詢
    // ─────────────────────────────────────────────────────────────────

    private fun lookupCandidates(bopomofo: String): List<String> {
        if (bopomofo.isEmpty()) return emptyList()

        val seen = linkedSetOf<String>()     // 保持插入順序 + 自動去重
        val singleChars = linkedSetOf<String>()
        val phrases = linkedSetOf<String>()

        // ── 1. 精確查字典（含聲調）→ 單字 ──
        val exact = dict[bopomofo]
        if (!exact.isNullOrEmpty()) singleChars.addAll(exact)

        // ── 2. 無調版 → 展開所有聲調變體查字典 ──
        val stripped = bopomofo.trimEnd { it in toneMarks }
        if (stripped.isNotEmpty()) {
            // 查無聲調版
            val base = dict[stripped]
            if (!base.isNullOrEmpty()) singleChars.addAll(base)
            // 查所有聲調變體（ˊˇˋ˙）
            for (tone in toneMarks) {
                val withTone = stripped + tone
                if (withTone != bopomofo) {
                    val toneResult = dict[withTone]
                    if (!toneResult.isNullOrEmpty()) singleChars.addAll(toneResult)
                }
            }
        }

        // ── 3. 前綴模糊匹配 → 補充更多單字 ──
        if (bopomofo.length <= 2) {
            val cached = prefixCache[bopomofo]
            if (cached != null) singleChars.addAll(cached)
        }

        // ── 4. 聲母簡拼 → 詞組 ──
        val allInitial = bopomofo.all { it in initials || it in medials || it in finals }
        if (bopomofo.length >= 2 && allInitial) {
            val phraseResults = phraseDict[bopomofo]
            if (!phraseResults.isNullOrEmpty()) phrases.addAll(phraseResults)
        }

        // ── 5. 完整注音查詞組字典 ──
        val fullPhrases = dict[bopomofo]
        if (!fullPhrases.isNullOrEmpty()) {
            for (p in fullPhrases) { if (p.length > 1) phrases.add(p) }
        }

        // 合併：單字在前，詞組在後，自動去重
        seen.addAll(singleChars)
        seen.addAll(phrases)
        return seen.toList()
    }

    /** 預熱索引（在 IO 執行緒呼叫，避免首次按鍵凍結 UI） */
    fun warmUp() {
        prefixCache
        reverseIndex
        nextCharIndex
    }

    // ─── 前綴快取：注音前綴(1-2字元) → 常用單字 ───
    private val prefixCache: Map<String, List<String>> by lazy { buildPrefixCache() }

    private fun buildPrefixCache(): Map<String, List<String>> {
        val cache = mutableMapOf<String, MutableMap<String, Int>>()
        for ((key, chars) in dict) {
            // 取 1 字元和 2 字元前綴
            for (len in 1..minOf(2, key.length)) {
                val prefix = key.substring(0, len)
                if (prefix.all { it in initials || it in medials || it in finals }) {
                    val map = cache.getOrPut(prefix) { mutableMapOf() }
                    for (ch in chars) {
                        if (ch.length == 1) {
                            map[ch] = (map[ch] ?: 0) + 1
                        }
                    }
                }
            }
        }
        return cache.mapValues { (_, map) ->
            map.entries.sortedByDescending { it.value }.take(50).map { it.key }
        }
    }

    // ─── 反向索引：漢字 → 注音讀音集合（用於連續輸入過濾） ───
    private val reverseIndex: Map<String, Set<String>> by lazy { buildReverseIndex() }

    // ─── 連續預測索引：字 → 可能的下一個字/詞 ───
    private val nextCharIndex: Map<String, List<String>> by lazy { buildNextCharIndex() }

    /** 取得選字後的下一字預測候選 */
    fun getNextCharPredictions(committed: String): List<String> {
        if (committed.isEmpty()) return emptyList()
        val lastChar = committed.last().toString()
        return nextCharIndex[lastChar] ?: emptyList()
    }

    /** 用注音前綴過濾下一字預測（連續輸入核心） */
    private fun filterPredictionsByBopomofo(predictions: List<String>, bopomofo: String): List<String> {
        if (predictions.isEmpty() || bopomofo.isEmpty()) return emptyList()
        val stripped = bopomofo.trimEnd { it in toneMarks }
        val hasTone = stripped != bopomofo
        return predictions.filter { char ->
            val readings = reverseIndex[char] ?: return@filter false
            if (hasTone) {
                // 有聲調：精確匹配
                readings.any { it == bopomofo }
            } else {
                // 無聲調：前綴匹配（ㄍ 匹配 ㄍㄤ、ㄍㄨㄛ 等）
                readings.any { reading ->
                    val readingStripped = reading.trimEnd { it in toneMarks }
                    readingStripped.startsWith(stripped)
                }
            }
        }
    }

    private fun buildReverseIndex(): Map<String, Set<String>> {
        val reverse = mutableMapOf<String, MutableSet<String>>()
        for ((bopomofo, chars) in dict) {
            for (ch in chars) {
                if (ch.length == 1) {
                    reverse.getOrPut(ch) { mutableSetOf() }.add(bopomofo)
                }
            }
        }
        return reverse
    }

    private fun buildNextCharIndex(): Map<String, List<String>> {
        val index = mutableMapOf<String, MutableMap<String, Int>>()
        for ((_, words) in phraseDict) {
            for (word in words) {
                if (word.length < 2) continue
                for (i in 0 until word.length - 1) {
                    val ch = word[i].toString()
                    val next = word.substring(i + 1)
                    val map = index.getOrPut(ch) { mutableMapOf() }
                    // 短的下一字優先計數
                    val nextChar = word[i + 1].toString()
                    map[nextChar] = (map[nextChar] ?: 0) + 1
                    // 也記錄完整後綴（如果不超過 3 字）
                    if (next.length in 2..3 && next !in map) {
                        map[next] = 1
                    }
                }
            }
        }
        // 按頻率排序，每個字最多保留 30 個候選
        return index.mapValues { (_, map) ->
            map.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.length }) // 同頻率短的優先
                .take(30)
                .map { it.key }
        }
    }

    private fun updateCandidatesFromBuffer() {
        val bopomofo = bopomofoBuffer.toString()
        if (bopomofo.isEmpty()) {
            currentCandidates = if (lastCommittedChar.isNotEmpty()) {
                getNextCharPredictions(lastCommittedChar)
            } else emptyList()
            isSelectingCandidate = false
            return
        }

        // 自動分音節
        val syllables = segmentSyllables(bopomofo)

        if (syllables.size >= 2) {
            // ── 多音節：用多音節查詢（詞組優先） ──
            currentCandidates = lookupMultiSyllable(syllables)
        } else {
            // ── 單音節：原邏輯 + 上下文排序 ──
            val regular = lookupCandidates(bopomofo)

            if (lastCommittedChar.isNotEmpty()) {
                val predictions = getNextCharPredictions(lastCommittedChar)
                if (predictions.isNotEmpty()) {
                    val regularSet = regular.toSet()
                    val contextual = predictions.filter { it in regularSet }
                    val extraFiltered = filterPredictionsByBopomofo(
                        predictions.filter { it !in regularSet }, bopomofo
                    )
                    val remaining = regular.filter { it !in contextual.toSet() }
                    currentCandidates = (contextual + extraFiltered + remaining).distinct()
                    isSelectingCandidate = false
                    return
                }
            }

            currentCandidates = regular
        }
        isSelectingCandidate = false
    }

    /**
     * 多音節查詢 — 使用者連續輸入多個音節的注音。
     *
     * 例如 ㄅㄨㄧㄠ 分成 [ㄅㄨ, ㄧㄠ]，查詞組「不要」。
     * 策略：
     * 1. 取各音節聲母 → 聲母簡拼查 phraseDict
     * 2. 用各音節候選字做交叉過濾
     * 3. 查 dict 的完整注音拼接 key
     * 4. 最後音節的單字候選作為備選
     */
    private fun lookupMultiSyllable(syllables: List<String>): List<String> {
        val results = linkedSetOf<String>()

        // ── 0. 先查 UserDictionary 手動詞彙（最優先）──
        val fullBopomofo = syllables.joinToString(" ")
        userDict?.queryByBopomofo(fullBopomofo)?.let {
            results.addAll(it)
        }
        // 無聲調版也查一次
        val strippedBopo = syllables.joinToString(" ") { it.trimEnd { c -> c in toneMarks } }
        if (strippedBopo != fullBopomofo) {
            userDict?.queryByBopomofo(strippedBopo)?.let { results.addAll(it) }
        }

        // ── 1. 聲母簡拼查詢 ──
        val abbreviation = syllables.joinToString("") { syl ->
            val stripped = syl.trimEnd { it in toneMarks }
            if (stripped.isNotEmpty()) stripped[0].toString() else ""
        }
        if (abbreviation.length >= 2) {
            val phraseResults = phraseDict[abbreviation]
            if (!phraseResults.isNullOrEmpty()) {
                // 過濾：用各音節候選字驗證詞組每個字是否匹配注音
                val syllableCandidateSets = syllables.map { syl ->
                    lookupSingleSyllableChars(syl)
                }
                val filtered = phraseResults.filter { phrase ->
                    if (phrase.length != syllables.size) return@filter false
                    phrase.indices.all { i ->
                        phrase[i].toString() in syllableCandidateSets[i]
                    }
                }
                results.addAll(filtered)
                // 也加不嚴格過濾的（只要音節數量符合）
                val lenMatched = phraseResults.filter { it.length == syllables.size && it !in results }
                results.addAll(lenMatched.take(10))
            }
        }

        // ── 2. 完整注音拼接查 dict ──
        val fullKey = syllables.joinToString("")
        val dictResults = dict[fullKey]
        if (!dictResults.isNullOrEmpty()) results.addAll(dictResults)
        // 無聲調版
        val strippedKey = syllables.joinToString("") { it.trimEnd { c -> c in toneMarks } }
        if (strippedKey != fullKey) {
            val strippedResults = dict[strippedKey]
            if (!strippedResults.isNullOrEmpty()) results.addAll(strippedResults)
        }

        // ── 3. 更寬鬆的簡拼查詢（用完整音節而非單聲母） ──
        // 例如 ㄅㄨ+ㄧ → 聲母 ㄅㄧ，但也查 phraseDict 的前綴
        val fullAbbrev = syllables.joinToString("") { syl ->
            syl.trimEnd { it in toneMarks }
        }
        if (fullAbbrev != abbreviation && fullAbbrev.length >= 2) {
            val fullPhraseResults = phraseDict[fullAbbrev]
            if (!fullPhraseResults.isNullOrEmpty()) {
                results.addAll(fullPhraseResults.filter { it.length >= syllables.size }.take(10))
            }
        }

        // ── 4. 每個音節都加單字候選（backup） ──
        // 查不到詞組時，至少讓每個音節的字都顯示出來
        // 讓用戶可以逐字選，而不是只看到最後一個音節
        if (results.isEmpty()) {
            // 完全查不到詞組：每個音節都加，第一個音節排最前
            syllables.forEachIndexed { i, syllable ->
                val chars = lookupCandidates(syllable)
                results.addAll(chars.take(if (i == 0) 10 else 6))
            }
        } else {
            // 有部分結果：只補最後音節防止候選太少
            val lastSyllable = syllables.last()
            val lastChars = lookupCandidates(lastSyllable)
            results.addAll(lastChars.take(10))
        }

        return results.toList()
    }

    /**
     * 滑動輸入用：接收連續注音符號字串，分段成音節後查候選字。
     * 例如 "ㄅㄨㄧㄠ" → [ㄅㄨ, ㄧㄠ] → 查「不要」等詞組。
     */
    fun lookupByBopomofoString(bopomofoStr: String): List<String> {
        if (bopomofoStr.isEmpty()) return emptyList()
        val syllables = segmentSyllables(bopomofoStr)
        return if (syllables.size == 1) {
            lookupCandidates(syllables[0])
        } else {
            lookupMultiSyllable(syllables)
        }
    }

    /** 查單音節的所有可能單字（用於多音節過濾） */
    private fun lookupSingleSyllableChars(syllable: String): Set<String> {
        val chars = mutableSetOf<String>()
        val stripped = syllable.trimEnd { it in toneMarks }
        // 精確
        dict[syllable]?.let { chars.addAll(it.filter { c -> c.length == 1 }) }
        // 無調
        if (stripped != syllable) dict[stripped]?.let { chars.addAll(it.filter { c -> c.length == 1 }) }
        // 所有聲調變體
        for (tone in toneMarks) {
            dict[stripped + tone]?.let { chars.addAll(it.filter { c -> c.length == 1 }) }
        }
        // 前綴快取
        prefixCache[stripped]?.let { chars.addAll(it) }
        return chars
    }

    private fun confirmFirstCandidate() {
        if (currentCandidates.isNotEmpty()) {
            val first = currentCandidates[0]
            selectedBuffer.append(first)
            lastCommittedChar = first
            bopomofoBuffer.clear()
            currentCandidates = emptyList()
            isSelectingCandidate = false
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 字典載入
    // ─────────────────────────────────────────────────────────────────

    private fun loadPhraseDict(): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        try {
            val input = context.resources.openRawResource(R.raw.bopomofo_phrases)
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    if (line.isBlank() || line.startsWith('#')) return@forEachLine
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        val key = parts[0].trim()
                        val words = parts[1].trim().split(' ').filter { it.isNotBlank() }
                        if (key.isNotEmpty() && words.isNotEmpty()) {
                            map.getOrPut(key) { mutableListOf() }.addAll(words)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map.mapValues { (_, v) -> v.distinct() }
    }

    private fun loadDict(): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        try {
            val input = context.resources.openRawResource(R.raw.bopomofo_dict)
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        val key = parts[0].trim()
                        val chars = parts[1].trim().split(' ').filter { it.isNotBlank() }
                        if (key.isNotEmpty() && chars.isNotEmpty()) {
                            map.getOrPut(key) { mutableListOf() }.addAll(chars)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 字典載入失敗，降級為空字典
            e.printStackTrace()
        }
        // 去重
        return map.mapValues { (_, v) -> v.distinct() }
    }
}
