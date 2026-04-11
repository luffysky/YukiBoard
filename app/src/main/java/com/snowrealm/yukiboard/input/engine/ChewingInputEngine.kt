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

    /** 最近一次選字前的注音（供自動學詞記錄注音用） */
    var lastComposingBopomofo: String = ""
        private set

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

    // 使用者是否明確按了一聲（因字典不用 ˉ 標記一聲，需特殊處理）
    private var explicitTone1 = false

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
                    '1' -> '\u02C9'    // ˉ 一聲（macron）
                    '2' -> '\u02CA'
                    '3' -> '\u02C7'
                    '4' -> '\u02CB'
                    '5' -> '\u02D9'
                    else -> null
                }
                if (tone != null) bopomofoBuffer.append(tone)
                // 標記使用者明確指定了聲調
                explicitTone1 = (ch == '1')
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

        // 沒有新的注音在打，但有已選字 → commit 整句
        if (bopomofoBuffer.isEmpty() && selectedBuffer.isNotEmpty()) {
            val text = selectedBuffer.toString()
            reset()
            return InputResult.CommitText(text)
        }

        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            // 空白鍵 = 確認第一候選，累積到 selectedBuffer
            val selected = currentCandidates[0]
            selectedBuffer.append(selected)
            bopomofoBuffer.clear(); explicitTone1 = false
            isSelectingCandidate = false
            lastCommittedChar = selected
            // 沒有更多注音 → commit 整句
            val text = selectedBuffer.toString()
            reset()
            return InputResult.CommitText(text)
        }

        // 查字典（自動處理單/多音節）
        updateCandidatesFromBuffer()
        if (currentCandidates.isNotEmpty()) {
            isSelectingCandidate = true
            return InputResult.ComposingUpdated(composingText)
        }

        // 查無候選 → 嘗試容錯預測
        val fuzzyResults = fuzzyPredictFromBuffer(bopomofoBuffer.toString())
        if (fuzzyResults.isNotEmpty()) {
            currentCandidates = fuzzyResults
            isSelectingCandidate = true
            return InputResult.ComposingUpdated(composingText)
        }

        // 真的找不到 → 直接 commit 原注音文字
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun processEnter(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        // 先把未完成的注音轉成漢字（取第一候選）
        commitRemainingBopomofo()
        val text = selectedBuffer.toString()
        reset()
        return if (text.isNotEmpty()) InputResult.CommitText(text) else InputResult.Idle
    }

    override fun forceCommit(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        commitRemainingBopomofo()
        val text = selectedBuffer.toString()
        reset()
        return if (text.isNotEmpty()) InputResult.CommitText(text) else InputResult.Idle
    }

    /** 把 bopomofoBuffer 中未完成的注音轉成漢字加入 selectedBuffer */
    private fun commitRemainingBopomofo() {
        if (bopomofoBuffer.isEmpty()) return
        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            confirmFirstCandidate()
        } else {
            // 沒有候選：嘗試查字典
            updateCandidatesFromBuffer()
            if (currentCandidates.isNotEmpty()) {
                confirmFirstCandidate()
            } else {
                // 真的查不到：把注音原文加入（最後手段）
                selectedBuffer.append(bopomofoBuffer)
                bopomofoBuffer.clear(); explicitTone1 = false
            }
        }
    }

    /** 取得已選字累積 buffer（供 InputRouter 在 directCommit 時合併輸出） */
    fun getSelectedBuffer(): String = selectedBuffer.toString()

    override fun getCandidates(): List<String> = currentCandidates

    fun getCommonCharScore(ch: Char): Int = COMMON_CHAR_FREQ[ch] ?: 0

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
        // 保存選字前的注音（供自動學詞用）
        lastComposingBopomofo = bopomofoBuffer.toString()
        selectedBuffer.append(selected)
        bopomofoBuffer.clear()
        isSelectingCandidate = false
        lastCommittedChar = selected.last().toString()

        // 不立刻 commit：把選字累積在 selectedBuffer，讓使用者繼續打下一個詞
        // 產生下一字預測候選（用最後一個字查上下文）
        currentCandidates = getNextCharPredictions(lastCommittedChar)

        return InputResult.ComposingUpdated(composingText)
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

        // 合併 + 聲調排序：精確聲調匹配的排最前面
        val hasTone = bopomofo.isNotEmpty() && bopomofo.last() in toneMarks

        if (hasTone && explicitTone1) {
            // 使用者明確按了一聲：字典無 ˉ key，需從無標記 key 扣除其他聲調的字
            val bareKey = stripped  // ㄅㄤ
            val allChars = dict[bareKey] ?: emptyList()
            // 收集二聲~輕聲的字
            val otherToneChars = mutableSetOf<String>()
            for (tone in listOf('\u02CA', '\u02C7', '\u02CB', '\u02D9')) {
                val withTone = bareKey + tone
                dict[withTone]?.let { otherToneChars.addAll(it) }
            }
            // 一聲字 = 全部字 - 其他聲調字（排前面）
            val tone1Chars = allChars.filter { it !in otherToneChars }
            val otherChars = allChars.filter { it in otherToneChars }
            seen.addAll(tone1Chars)
            seen.addAll(otherChars)
            singleChars.forEach { if (it !in seen) seen.add(it) }
        } else if (hasTone) {
            // 有聲調（二~輕聲）：精確匹配優先
            val exactResult = dict[bopomofo] ?: emptyList()
            seen.addAll(exactResult)
            singleChars.forEach { if (it !in seen) seen.add(it) }
        } else if (stripped.isNotEmpty()) {
            // 無聲調（空白鍵/未選聲調）：從無標記 key 取全部字，常用字優先
            val baseChars = dict[stripped] ?: emptyList()
            // 按常用字頻率排序
            val sorted = baseChars.sortedByDescending { if (it.length == 1) COMMON_CHAR_FREQ[it[0]] ?: 0 else 0 }
            seen.addAll(sorted)
            // 補入各聲調查詢結果
            for (tone in listOf('\u02CA', '\u02C7', '\u02CB', '\u02D9')) {
                val withTone = stripped + tone
                val chars = dict[withTone] ?: continue
                chars.forEach { if (it !in seen) seen.add(it) }
            }
            singleChars.forEach { if (it !in seen) seen.add(it) }
        } else {
            seen.addAll(singleChars)
        }
        // 詞組放後面
        phrases.forEach { if (it !in seen) seen.add(it) }
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

        // 從詞組字典建立
        for ((_, words) in phraseDict) {
            for (word in words) {
                if (word.length < 2) continue
                for (i in 0 until word.length - 1) {
                    val ch = word[i].toString()
                    val map = index.getOrPut(ch) { mutableMapOf() }
                    val nextChar = word[i + 1].toString()
                    map[nextChar] = (map[nextChar] ?: 0) + 1
                    // 也記錄完整後綴（2~3 字）
                    val next = word.substring(i + 1)
                    if (next.length in 2..3 && next !in map) {
                        map[next] = 1
                    }
                }
            }
        }

        // 從主字典的多字詞條也建立（提高覆蓋率）
        for ((_, entries) in dict) {
            for (word in entries) {
                if (word.length < 2) continue
                for (i in 0 until word.length - 1) {
                    val ch = word[i].toString()
                    val map = index.getOrPut(ch) { mutableMapOf() }
                    val nextChar = word[i + 1].toString()
                    map[nextChar] = (map[nextChar] ?: 0) + 1
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
            val rawResults = lookupMultiSyllable(syllables)
            currentCandidates = if (rawResults.isNotEmpty()) {
                sortByToneMatch(rawResults, syllables)
            } else {
                fuzzyPredictFromBuffer(bopomofo)
            }
        } else {
            // ── 單音節：原邏輯 + 上下文排序 ──
            var regular = lookupCandidates(bopomofo)
            // 單音節查無結果 → 容錯預測
            if (regular.isEmpty()) {
                regular = fuzzyPredictFromBuffer(bopomofo)
            }

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

        // ── 4. 組合候選：從每個音節的 top 候選字排列組合成句子 ──
        // 例如 ㄕㄨ→輸, ㄌㄜ→了, ㄋㄧ→你 → 組合出「輸了你」
        val syllableTopChars = syllables.map { syllable ->
            val raw = lookupCandidates(syllable).filter { it.length == 1 }
            // 高頻常用字優先排序（字典序不反映使用頻率）
            val boosted = raw.sortedByDescending { COMMON_CHAR_FREQ[it[0]] ?: 0 }
            boosted.take(8)
        }
        if (syllableTopChars.all { it.isNotEmpty() }) {
            val composed = mutableListOf<String>()
            // 第一個：每個音節的最高頻候選
            val best = syllableTopChars.joinToString("") { it[0] }
            composed.add(best)
            // 各位置嘗試替換為第 2~4 候選
            for (pos in syllableTopChars.indices) {
                for (altIdx in 1 until minOf(4, syllableTopChars[pos].size)) {
                    val alt = syllableTopChars.mapIndexed { i, chars ->
                        if (i == pos) chars[altIdx] else chars[0]
                    }.joinToString("")
                    if (alt !in composed) composed.add(alt)
                }
            }
            // 組合候選排在最前面
            for (c in composed.take(10)) {
                if (c !in results) results.add(c)
            }
        }

        // ── 5. 每個音節的單字候選（backup） ──
        syllables.forEachIndexed { i, syllable ->
            val chars = lookupCandidates(syllable)
            results.addAll(chars.take(if (i == 0) 6 else 4))
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
            lastCommittedChar = first.last().toString()
            bopomofoBuffer.clear(); explicitTone1 = false
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

    // ─── 注音容錯用鄰近表（不含聲調鍵） ───
    private val BOPOMOFO_ADJACENT: Map<String, List<String>> = mapOf(
        "ㄅ" to listOf("ㄉ", "ㄆ", "ㄊ"),
        "ㄉ" to listOf("ㄅ", "ㄆ", "ㄊ", "ㄍ"),
        "ㄓ" to listOf("ㄐ", "ㄔ", "ㄗ"),
        "ㄚ" to listOf("ㄞ", "ㄧ", "ㄛ", "ㄟ"),
        "ㄞ" to listOf("ㄚ", "ㄢ", "ㄛ", "ㄟ", "ㄣ"),
        "ㄢ" to listOf("ㄞ", "ㄟ", "ㄣ"),
        "ㄆ" to listOf("ㄅ", "ㄉ", "ㄊ", "ㄇ", "ㄋ"),
        "ㄊ" to listOf("ㄅ", "ㄉ", "ㄆ", "ㄍ", "ㄇ", "ㄋ", "ㄎ"),
        "ㄍ" to listOf("ㄉ", "ㄊ", "ㄐ", "ㄋ", "ㄎ", "ㄑ"),
        "ㄐ" to listOf("ㄓ", "ㄍ", "ㄔ", "ㄎ", "ㄑ", "ㄕ"),
        "ㄔ" to listOf("ㄓ", "ㄐ", "ㄗ", "ㄑ", "ㄕ", "ㄘ"),
        "ㄗ" to listOf("ㄓ", "ㄔ", "ㄧ", "ㄕ", "ㄘ", "ㄨ"),
        "ㄧ" to listOf("ㄚ", "ㄗ", "ㄛ", "ㄘ", "ㄨ", "ㄜ"),
        "ㄛ" to listOf("ㄚ", "ㄞ", "ㄧ", "ㄟ", "ㄨ", "ㄜ", "ㄠ"),
        "ㄟ" to listOf("ㄚ", "ㄞ", "ㄢ", "ㄛ", "ㄣ", "ㄜ", "ㄠ", "ㄤ"),
        "ㄣ" to listOf("ㄞ", "ㄢ", "ㄟ", "ㄠ", "ㄤ"),
        "ㄇ" to listOf("ㄆ", "ㄊ", "ㄋ", "ㄈ", "ㄌ"),
        "ㄋ" to listOf("ㄆ", "ㄊ", "ㄍ", "ㄇ", "ㄎ", "ㄈ", "ㄌ", "ㄏ"),
        "ㄎ" to listOf("ㄊ", "ㄍ", "ㄐ", "ㄋ", "ㄑ", "ㄌ", "ㄏ", "ㄒ"),
        "ㄑ" to listOf("ㄍ", "ㄐ", "ㄔ", "ㄎ", "ㄕ", "ㄏ", "ㄒ", "ㄖ"),
        "ㄕ" to listOf("ㄐ", "ㄔ", "ㄗ", "ㄑ", "ㄘ", "ㄒ", "ㄖ", "ㄙ"),
        "ㄘ" to listOf("ㄔ", "ㄗ", "ㄧ", "ㄕ", "ㄨ", "ㄖ", "ㄙ", "ㄩ"),
        "ㄨ" to listOf("ㄗ", "ㄧ", "ㄛ", "ㄘ", "ㄜ", "ㄙ", "ㄩ", "ㄝ"),
        "ㄜ" to listOf("ㄧ", "ㄛ", "ㄟ", "ㄨ", "ㄠ", "ㄩ", "ㄝ", "ㄡ"),
        "ㄠ" to listOf("ㄛ", "ㄟ", "ㄣ", "ㄜ", "ㄤ", "ㄝ", "ㄡ", "ㄥ"),
        "ㄤ" to listOf("ㄟ", "ㄣ", "ㄠ", "ㄡ", "ㄥ"),
        "ㄈ" to listOf("ㄇ", "ㄋ", "ㄌ", "ㄦ"),
        "ㄌ" to listOf("ㄇ", "ㄋ", "ㄎ", "ㄈ", "ㄏ", "ㄦ"),
        "ㄏ" to listOf("ㄋ", "ㄎ", "ㄑ", "ㄌ", "ㄒ"),
        "ㄒ" to listOf("ㄎ", "ㄑ", "ㄕ", "ㄏ", "ㄖ"),
        "ㄖ" to listOf("ㄑ", "ㄕ", "ㄘ", "ㄒ", "ㄙ"),
        "ㄙ" to listOf("ㄕ", "ㄘ", "ㄨ", "ㄖ", "ㄩ"),
        "ㄩ" to listOf("ㄘ", "ㄨ", "ㄜ", "ㄙ", "ㄝ"),
        "ㄝ" to listOf("ㄨ", "ㄜ", "ㄠ", "ㄩ", "ㄡ"),
        "ㄡ" to listOf("ㄜ", "ㄠ", "ㄤ", "ㄝ", "ㄥ"),
        "ㄥ" to listOf("ㄠ", "ㄤ", "ㄡ"),
        "ㄦ" to listOf("ㄈ", "ㄌ"),
    )

    /**
     * 注音容錯預測 — 當 buffer 查無候選時，嘗試鄰近符號替換。
     */
    private fun fuzzyPredictFromBuffer(bopomofo: String): List<String> {
        if (bopomofo.isEmpty()) return emptyList()
        val results = linkedSetOf<String>()
        val syllables = segmentSyllables(bopomofo)
        val lastSyllable = syllables.lastOrNull() ?: return emptyList()
        val lastSylStripped = lastSyllable.trimEnd { it in toneMarks }

        // 策略一：最後一個音節的每個符號換成鄰近符號
        for (i in lastSylStripped.indices) {
            val ch = lastSylStripped[i].toString()
            val neighbors = BOPOMOFO_ADJACENT[ch] ?: continue
            for (neighbor in neighbors) {
                val altSyllable = lastSylStripped.substring(0, i) +
                    neighbor + lastSylStripped.substring(i + 1)
                val altBopomofo = if (syllables.size > 1) {
                    syllables.dropLast(1).joinToString("") + altSyllable
                } else altSyllable
                val altSyls = segmentSyllables(altBopomofo)
                val cands = if (altSyls.size >= 2) lookupMultiSyllable(altSyls)
                            else lookupCandidates(altBopomofo)
                results.addAll(cands.take(3))
                if (results.size >= 15) return results.toList()
            }
        }

        // 策略二：刪掉最後一個音節重查
        if (bopomofo.length > 1) {
            val shorter = bopomofo.dropLast(1)
            val shorterSyls = segmentSyllables(shorter)
            val shorterCands = if (shorterSyls.size >= 2) lookupMultiSyllable(shorterSyls)
                               else lookupCandidates(shorter)
            results.addAll(shorterCands.take(5))
        }

        return results.toList()
    }

    /**
     * 多音節候選按聲調匹配程度排序。
     */
    private fun sortByToneMatch(candidates: List<String>, syllables: List<String>): List<String> {
        if (syllables.isEmpty()) return candidates
        val strippedSyllables = syllables.map { it.trimEnd { c -> c in toneMarks } }

        data class Scored(val word: String, val score: Int)

        val scored = candidates.map { word ->
            var score = 0
            if (word.length == syllables.size) score += 2
            word.forEachIndexed { i, ch ->
                if (i >= syllables.size) return@forEachIndexed
                val inputStripped = strippedSyllables[i]
                val inputTone = syllables[i].lastOrNull()?.takeIf { it in toneMarks }
                val charReadings = reverseIndex[ch.toString()] ?: return@forEachIndexed
                for (reading in charReadings) {
                    val readingStripped = reading.trimEnd { it in toneMarks }
                    val readingTone = reading.lastOrNull()?.takeIf { it in toneMarks }
                    if (readingStripped == inputStripped) {
                        score += 1
                        if (inputTone == null || readingTone == inputTone) { score += 2; break }
                    }
                }
            }
            Scored(word, score)
        }
        return scored.sortedByDescending { it.score }.map { it.word }
    }

    /**
     * 不改變引擎狀態，只查詢指定注音是否有候選字（供 AUTO 模式評分用）。
     */
    fun getCandidatesForBopomofo(bopomofo: String): List<String> {
        if (bopomofo.isEmpty()) return emptyList()
        val syllables = segmentSyllables(bopomofo)
        return if (syllables.size >= 2) lookupMultiSyllable(syllables)
               else lookupCandidates(bopomofo)
    }

    companion object {
        /**
         * 中文高頻字權重表（前 200 常用字）。
         * 用於滑動輸入組合候選字排序：字典收錄順序不反映使用頻率，
         * 需要此表把「了、的、是、不」等超高頻字提前。
         */
        val COMMON_CHAR_FREQ: Map<Char, Int> = mapOf(
            '的' to 100, '一' to 99, '是' to 98, '不' to 97, '了' to 96,
            '在' to 95, '人' to 94, '有' to 93, '我' to 92, '他' to 91,
            '這' to 90, '中' to 89, '大' to 88, '來' to 87, '上' to 86,
            '個' to 85, '國' to 84, '到' to 83, '會' to 82, '說' to 81,
            '時' to 80, '要' to 79, '就' to 78, '出' to 77, '也' to 76,
            '你' to 75, '對' to 74, '生' to 73, '能' to 72, '子' to 71,
            '那' to 70, '得' to 69, '於' to 68, '著' to 67, '下' to 66,
            '自' to 65, '之' to 64, '年' to 63, '過' to 62, '發' to 61,
            '後' to 60, '作' to 59, '裡' to 58, '用' to 57, '道' to 56,
            '行' to 55, '所' to 54, '然' to 53, '家' to 52, '種' to 51,
            '事' to 50, '成' to 49, '方' to 48, '多' to 47, '經' to 46,
            '麼' to 45, '去' to 44, '法' to 43, '學' to 42, '如' to 41,
            '都' to 40, '同' to 39, '現' to 38, '當' to 37, '沒' to 36,
            '動' to 35, '面' to 34, '起' to 33, '看' to 32, '定' to 31,
            '天' to 30, '分' to 29, '還' to 28, '進' to 27, '好' to 26,
            '小' to 25, '部' to 24, '其' to 23, '些' to 22, '主' to 21,
            '樣' to 20, '理' to 19, '心' to 18, '她' to 17, '本' to 16,
            '前' to 15, '開' to 14, '但' to 13, '因' to 12, '只' to 11,
            '從' to 10, '想' to 9, '實' to 8, '日' to 7, '軍' to 6,
            '者' to 5, '意' to 4, '無' to 3, '力' to 2, '它' to 1,
            // 口語高頻補充
            '吃' to 75, '喝' to 70, '做' to 65, '打' to 60, '走' to 55,
            '給' to 50, '讓' to 45, '把' to 80, '被' to 75, '跟' to 70,
            '很' to 80, '可' to 75, '會' to 82, '和' to 78, '們' to 76,
            '為' to 68, '什' to 65, '怎' to 60, '嗎' to 55, '吧' to 50,
            '呢' to 50, '啊' to 45, '哦' to 40, '嘛' to 35, '喔' to 35,
            '嗯' to 30, '欸' to 25, '耶' to 20,
            // 動詞
            '知' to 60, '問' to 55, '買' to 50, '賣' to 45, '回' to 65,
            '找' to 55, '等' to 50, '聽' to 45, '寫' to 40, '讀' to 35,
            '住' to 50, '坐' to 45, '站' to 40, '睡' to 40, '醒' to 35,
            '輸' to 35, '贏' to 30, '死' to 40, '活' to 40, '愛' to 55,
            '恨' to 30, '怕' to 35, '哭' to 30, '笑' to 35, '玩' to 40,
            // 高頻動詞補充
            '幫' to 70, '忙' to 60, '教' to 55, '學' to 42, '帶' to 50,
            '送' to 50, '接' to 50, '拿' to 55, '放' to 50, '拉' to 45,
            '推' to 40, '關' to 55, '開' to 14, '換' to 50, '選' to 45,
            '穿' to 40, '洗' to 45, '切' to 35, '煮' to 35, '烤' to 30,
            '借' to 40, '還' to 28, '修' to 40, '裝' to 40, '搬' to 35,
            '丟' to 35, '撿' to 30, '抓' to 35, '摸' to 30, '踢' to 25,
            '跑' to 45, '飛' to 35, '跳' to 35, '游' to 35, '爬' to 30,
            '說' to 81, '講' to 55, '叫' to 50, '告' to 45, '訴' to 40,
            '記' to 50, '忘' to 45, '想' to 9, '猜' to 30, '懂' to 40,
            '試' to 45, '用' to 57, '需' to 45, '要' to 79, '該' to 45,
            '配' to 35, '加' to 50, '減' to 35, '算' to 40, '數' to 40,
            // 名詞高頻
            '人' to 94, '話' to 60, '錢' to 55, '車' to 50, '房' to 45,
            '路' to 45, '門' to 40, '水' to 50, '飯' to 45, '菜' to 40,
            '肉' to 35, '魚' to 35, '果' to 40, '花' to 35, '樹' to 30,
            '書' to 45, '字' to 50, '名' to 50, '題' to 40, '課' to 35,
            '班' to 35, '校' to 35, '店' to 40, '市' to 40, '區' to 35,
            '山' to 35, '海' to 35, '河' to 30, '風' to 35, '雨' to 30,
            '雪' to 25, '光' to 40, '色' to 35, '聲' to 35, '氣' to 40,
            '手' to 50, '頭' to 45, '眼' to 40, '口' to 40, '身' to 40,
            '腳' to 35, '臉' to 35, '耳' to 25, '鼻' to 25, '嘴' to 30,
            '電' to 50, '話' to 60, '機' to 50, '網' to 50, '視' to 40,
            // 形容詞
            '大' to 88, '小' to 25, '多' to 47, '少' to 40, '高' to 40,
            '低' to 30, '長' to 40, '短' to 30, '快' to 45, '慢' to 35,
            '新' to 45, '舊' to 30, '老' to 45, '遠' to 35, '近' to 30,
            '熱' to 35, '冷' to 35, '亮' to 30, '暗' to 25, '重' to 35,
            '輕' to 30, '厚' to 25, '薄' to 25, '深' to 30, '淺' to 25,
            '真' to 55, '假' to 35, '對' to 74, '錯' to 35, '難' to 40,
            '易' to 25, '苦' to 25, '甜' to 25, '酸' to 20, '辣' to 20,
            '美' to 40, '醜' to 20, '乾' to 30, '濕' to 20, '硬' to 20,
            '軟' to 20, '黑' to 30, '白' to 35, '紅' to 30, '藍' to 25,
            '綠' to 25, '黃' to 25,
            // 時間/位置
            '今' to 50, '明' to 45, '昨' to 35, '早' to 40, '晚' to 35,
            '先' to 40, '再' to 45, '已' to 40, '剛' to 35, '才' to 40,
            '正' to 40, '又' to 40, '太' to 45, '更' to 40, '最' to 45,
            '比' to 40, '另' to 25, '每' to 30, '各' to 25, '整' to 25,
            '裡' to 58, '外' to 40, '左' to 30, '右' to 30, '東' to 30,
            '西' to 35, '南' to 30, '北' to 30,
            // 連接詞/語助詞
            '而' to 45, '且' to 35, '或' to 40, '卻' to 35, '倒' to 30,
            '雖' to 30, '然' to 53, '但' to 13, '若' to 25, '除' to 25,
            '像' to 35, '跟' to 70, '及' to 30, '與' to 30,
            // 綁/棒/榜 — 避免與「幫」混淆（降低排名）
            '綁' to 15, '棒' to 20, '榜' to 15,
        )
    }
}
