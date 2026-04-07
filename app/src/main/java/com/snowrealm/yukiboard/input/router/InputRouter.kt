package com.snowrealm.yukiboard.input.router

import android.content.Context
import com.snowrealm.yukiboard.input.candidate.CandidateManager
import com.snowrealm.yukiboard.input.engine.*
import com.snowrealm.yukiboard.input.smart.SmartInputManager

/**
 * 多語言輸入路由核心。
 *
 * 支援輸入法：
 * - ENGLISH — 英文 QWERTY
 * - BOPOMOFO — 注音輸入
 * - PINYIN — 拼音輸入
 * - CANGJIE — 倉頡輸入
 * - SPEED_CANGJIE — 速成（簡易倉頡）
 * - JAPANESE — 日文羅馬字→假名→漢字
 * - KOREAN — 韓文 Jamo 組合
 *
 * 切換模式時前一軌道若有未提交的 composing，先強制 commit。
 */
class InputRouter(private val context: Context) {

    // -------------------------------------------------------------------------
    // 引擎（lazy 初始化，避免啟動時全載入）
    // -------------------------------------------------------------------------
    private val userDict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(context)
    private val englishEngine: InputEngine = EnglishInputEngine(context)
    private val bopomofoEngine: InputEngine = ChewingInputEngine(
        context,
        userDict = userDict
    )
    private val pinyinEngine: InputEngine by lazy { PinyinInputEngine() }
    private val cangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = false, context = context) }
    private val speedCangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = true, context = context) }
    private val japaneseEngine: InputEngine by lazy { JapaneseInputEngine(context) }
    private val koreanEngine: InputEngine by lazy { KoreanInputEngine(context) }

    val candidateManager = CandidateManager()
    val smartInput = SmartInputManager(context)

    /** 候選字來源映射（每次 updateCandidates 時更新） */
    var candidateSourceMap: Map<String, CandidateSource> = emptyMap()
        private set

    // ─── AUTO 模式：雙重解釋緩衝 ───
    val autoQwertyBuffer = StringBuilder()
    val autoBopomofoBuffer = mutableListOf<String>()
    var autoLastCommitWasEnglish = false; private set
    var autoEnglishScore = 0; private set
    var autoBopomofoScore = 0; private set

    /** AUTO 模式下目前的鍵盤佈局（使用者可自由切換，不影響 AUTO 判斷） */
    enum class AutoLayout { QWERTY, BOPOMOFO }
    var autoLayout: AutoLayout = AutoLayout.BOPOMOFO
        private set

    /** AUTO 模式下切換鍵盤佈局（短按切換鍵） */
    fun toggleAutoLayout() {
        autoLayout = if (autoLayout == AutoLayout.QWERTY) AutoLayout.BOPOMOFO else AutoLayout.QWERTY
    }

    // 注音符號 → 對應 QWERTY 小寫字母（大千式標準配置）
    private val BOPOMOFO_TO_QWERTY: Map<String, String> = mapOf(
        "ㄆ" to "q", "ㄊ" to "w", "ㄍ" to "e", "ㄐ" to "r", "ㄔ" to "t",
        "ㄗ" to "y", "ㄧ" to "u", "ㄛ" to "i", "ㄟ" to "o", "ㄣ" to "p",
        "ㄇ" to "a", "ㄋ" to "s", "ㄎ" to "d", "ㄑ" to "f", "ㄕ" to "g",
        "ㄘ" to "h", "ㄨ" to "j", "ㄜ" to "k", "ㄠ" to "l",
        "ㄈ" to "z", "ㄌ" to "x", "ㄏ" to "c", "ㄒ" to "v", "ㄖ" to "b",
        "ㄙ" to "n", "ㄩ" to "m",
    )
    private val QWERTY_TO_BOPOMOFO: Map<Char, String> = mapOf(
        'q' to "ㄆ", 'w' to "ㄊ", 'e' to "ㄍ", 'r' to "ㄐ", 't' to "ㄔ",
        'y' to "ㄗ", 'u' to "ㄧ", 'i' to "ㄛ", 'o' to "ㄟ", 'p' to "ㄣ",
        'a' to "ㄇ", 's' to "ㄋ", 'd' to "ㄎ", 'f' to "ㄑ", 'g' to "ㄕ",
        'h' to "ㄘ", 'j' to "ㄨ", 'k' to "ㄜ", 'l' to "ㄠ",
        'z' to "ㄈ", 'x' to "ㄌ", 'c' to "ㄏ", 'v' to "ㄒ", 'b' to "ㄖ",
        'n' to "ㄙ", 'm' to "ㄩ",
    )

    private var currentMode: InputMode = InputMode.ENGLISH
    private var activeEngine: InputEngine = englishEngine

    // 前一個提交詞（供智慧糾錯上下文用）
    private var lastCommittedWord: String? = null
    // 目前 App 套件名（供習慣追蹤用）
    var currentAppPackage: String? = null
    // 輸入框上下文（由 Service 從 getTextBeforeCursor 設定，200 字元）
    var contextBeforeCursor: String = ""
        set(value) {
            field = value
            // 同步更新整句上下文
            smartInput.updateSentenceContext(value)
        }

    // 語言循環順序（使用者可自訂，預設全開）
    var enabledModes: List<InputMode> = listOf(
        InputMode.ENGLISH,
        InputMode.BOPOMOFO,
        InputMode.PINYIN,
        InputMode.CANGJIE,
        InputMode.JAPANESE,
        InputMode.KOREAN,
    )

    init {
        if (englishEngine is EnglishInputEngine) {
            smartInput.setDictionary(
                com.snowrealm.yukiboard.input.dictionary.EnglishDictionary(context)
            )
        }
    }

    // -------------------------------------------------------------------------
    // 公開唯讀狀態
    // -------------------------------------------------------------------------

    val composingText: String get() = activeEngine.composingText
    val candidates: List<String> get() = candidateManager.candidates
    val inputMode: InputMode get() = currentMode
    fun getActiveEngine(): InputEngine = activeEngine

    private val bopomofoDecoder = com.snowrealm.yukiboard.input.swipe.BopomofoSwipeDecoder()
    private val swipeRanker by lazy {
        com.snowrealm.yukiboard.input.swipe.SwipeCandidateRanker(
            smartInput,
            bopomofoEngine as? ChewingInputEngine,
            bopomofoDecoder
        )
    }

    /** 滑動輸入：根據當前模式解碼按鍵序列為候選字（含鄰近鍵容錯排序） */
    fun swipeSuggest(keyLabels: List<String>): List<String> {
        if (keyLabels.size < 2) return emptyList()

        val isBopomofo = keyLabels.any { it.length == 1 && it[0] in '\u3105'..'\u312F' }

        return if (isBopomofo) {
            // 注音模式：解碼注音符號 + 鄰近替代方案排序
            val symbols = keyLabels.filter { l ->
                l.length == 1 && (l[0] in '\u3105'..'\u312F' || l[0] in setOf('ˊ', 'ˇ', 'ˋ', '˙'))
            }
            // 去連續重複
            val deduped = mutableListOf<String>()
            for (s in symbols) {
                if (deduped.lastOrNull() != s) deduped.add(s)
            }
            swipeRanker.rankBopomofo(deduped)
        } else {
            // 英文模式：字母序列 + 自適應排序
            val chars = keyLabels.mapNotNull { it.firstOrNull()?.lowercaseChar() }
            swipeRanker.rankEnglish(chars)
        }
    }

    // -------------------------------------------------------------------------
    // 語言切換
    // -------------------------------------------------------------------------

    /** 循環切換到下一個已啟用的輸入法 */
    fun toggleLanguage() {
        if (enabledModes.size <= 1) return
        val currentIndex = enabledModes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % enabledModes.size
        switchTo(enabledModes[nextIndex])
    }

    /** 直接切換到指定模式 */
    fun switchTo(mode: InputMode) {
        if (mode == currentMode) return
        // 切換前先提交當前 composing
        val commitResult = activeEngine.forceCommit()
        currentMode = mode
        activeEngine = engineForMode(mode)
        candidateManager.clear()
    }

    private fun engineForMode(mode: InputMode): InputEngine = when (mode) {
        InputMode.ENGLISH -> englishEngine
        InputMode.BOPOMOFO -> bopomofoEngine
        InputMode.PINYIN -> pinyinEngine
        InputMode.CANGJIE -> cangjieEngine
        InputMode.SPEED_CANGJIE -> speedCangjieEngine
        InputMode.JAPANESE -> japaneseEngine
        InputMode.KOREAN -> koreanEngine
        // AUTO 模式預設注音引擎（使用者打英文時自動切換）
        InputMode.AUTO -> bopomofoEngine
    }

    // -------------------------------------------------------------------------
    // 輸入處理
    // -------------------------------------------------------------------------

    fun processInput(label: String): InputResult {
        if (label.isEmpty()) return InputResult.Idle

        smartInput.onKeystroke()

        if (currentMode == InputMode.AUTO) {
            autoDetectAndSwitch(label)
        }

        // AUTO 模式下，按鍵 label 可能跟當前引擎不匹配
        // 例如注音鍵盤發出 "ㄇ" 但引擎切到了英文 → 需要轉成 "a"
        val effectiveLabel = if (inputMode == InputMode.AUTO) {
            val ch = label.firstOrNull()
            val isBopoLabel = ch != null && (ch in '\u3105'..'\u312F' || ch in setOf('ˉ','ˊ','ˇ','ˋ','˙'))
            if (isBopoLabel && activeEngine is EnglishInputEngine) {
                // 注音符號 → 英文引擎：轉成 QWERTY
                BOPOMOFO_TO_QWERTY[label] ?: label
            } else if (!isBopoLabel && ch != null && ch.isLetter() && activeEngine is ChewingInputEngine) {
                // 英文字母 → 注音引擎：轉成注音符號
                QWERTY_TO_BOPOMOFO[ch.lowercaseChar()] ?: label
            } else label
        } else label

        val result = activeEngine.processChar(effectiveLabel)
        updateCandidates()
        return result
    }

    fun processDelete(): InputResult {
        val result = activeEngine.processDelete()
        updateCandidates()
        return result
    }

    fun processSpace(): InputResult {
        // 空白鍵選字前：歸檔當前候選到歷史
        if (candidateManager.candidates.isNotEmpty()) {
            val firstCandidate = candidateManager.candidates.firstOrNull() ?: ""
            candidateManager.archiveCurrentAsHistory(firstCandidate)
        }
        val result = activeEngine.processSpace()
        if (result is InputResult.CommitText) {
            onTextCommitted(result.text)
        }
        updateCandidates()
        return result
    }

    fun processEnter(): InputResult {
        val result = activeEngine.processEnter()
        if (result is InputResult.CommitText) {
            onTextCommitted(result.text)
            // Enter = 句子結束，學習整句模式
            smartInput.onSentenceCommitted(result.text)
        }
        candidateManager.clear()
        smartInput.resetContext()
        lastCommittedWord = null  // Enter = 句子結束，不再顯示接續候選
        return result
    }

    /**
     * 使用者從候選列選擇第 [index] 個候選字。
     */
    fun selectCandidate(index: Int): InputResult {
        // 候選列可能被自適應字典重新排序，需把 UI 索引映射回引擎索引
        val displayedText = candidateManager.candidates.getOrNull(index)

        // 選字前：把當前段候選存入歷史（供後續顯示淺色歷史候選）
        if (displayedText != null) {
            candidateManager.archiveCurrentAsHistory(displayedText)
        }

        // 直接 commit 候選的共用邏輯（歷史候選 / 整句預測 / 接續候選等不在引擎內的候選）
        fun directCommit(text: String): InputResult {
            val eng = activeEngine
            if (eng is ChewingInputEngine) {
                // 注音引擎有 selectedBuffer（已選字累積）
                // 先把 selectedBuffer 內容一起 commit，避免前面已選的字消失
                val pending = eng.composingText.takeIf { it.isNotEmpty() && it != text }
                eng.reset()
                candidateManager.clear()
                val fullText = if (pending != null) pending + text else text
                onTextCommitted(fullText)
                smartInput.onCandidateHit()
                updateCandidates()
                return InputResult.CommitText(fullText)
            }
            activeEngine.reset()
            candidateManager.clear()
            onTextCommitted(text)
            smartInput.onCandidateHit()
            updateCandidates()  // 產生接續候選
            return InputResult.CommitText(text)
        }

        // 先讓引擎嘗試處理候選（引擎候選優先於 PREDICTION/HISTORY 標籤）
        // 避免因 updateCandidates 排序/標記導致引擎候選被誤判為 PREDICTION
        val result = when (val eng = activeEngine) {
            is ChewingInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } else null
                if (engineIndex != null) {
                    eng.selectCandidateAt(engineIndex)
                } else if (displayedText != null) {
                    return directCommit(displayedText)
                } else InputResult.Idle
            }
            is PinyinInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } else null
                if (engineIndex != null) {
                    eng.selectCandidateAt(engineIndex)
                } else if (displayedText != null) {
                    return directCommit(displayedText)
                } else InputResult.Idle
            }
            is CangjieInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } else null
                if (engineIndex != null) {
                    eng.selectCandidateAt(engineIndex)
                } else if (displayedText != null) {
                    return directCommit(displayedText)
                } else InputResult.Idle
            }
            is JapaneseInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } else null
                if (engineIndex != null) {
                    eng.selectCandidateAt(engineIndex)
                } else if (displayedText != null) {
                    return directCommit(displayedText)
                } else InputResult.Idle
            }
            is KoreanInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } else null
                if (engineIndex != null) {
                    eng.selectCandidateAt(engineIndex)
                } else if (displayedText != null) {
                    return directCommit(displayedText)
                } else InputResult.Idle
            }
            else -> {
                // 英文：通用候選選擇（含 fallback）
                val selected = candidateManager.selectAt(index)
                    ?: candidateManager.candidates.getOrNull(index)
                if (selected != null) {
                    return directCommit(selected)
                }
                return InputResult.Idle
            }
        }
        if (result is InputResult.CommitText) {
            onTextCommitted(result.text)
            smartInput.onCandidateHit()
        }
        updateCandidates()
        return result
    }

    fun reset() {
        activeEngine.reset()
        candidateManager.clear()
    }

    /** 預熱注音引擎索引（在 IO 執行緒呼叫） */
    fun warmUpChewing() {
        (bopomofoEngine as? ChewingInputEngine)?.warmUp()
    }

    // -------------------------------------------------------------------------
    // AUTO 模式自動偵測
    // -------------------------------------------------------------------------

    private fun autoDetectAndSwitch(label: String) {
        val ch = label.firstOrNull() ?: return

        // ── 非注音/英文的明確語言鍵 → 直接切 ──
        if (ch in '\u3131'..'\u3163') { if (currentMode != InputMode.KOREAN) switchToClean(InputMode.KOREAN); return }
        if (ch in '\u3041'..'\u30FF') { if (currentMode != InputMode.JAPANESE) switchToClean(InputMode.JAPANESE); return }

        // ── 取得這個鍵的雙重解讀 ──
        val isBopomofoKey = ch in '\u3105'..'\u312F' || ch in setOf('ˉ','ˊ','ˇ','ˋ','˙')
        val qwertyChar = if (isBopomofoKey) {
            BOPOMOFO_TO_QWERTY[label]?.firstOrNull()
        } else if (ch.isLetter()) {
            ch.lowercaseChar()
        } else null

        // 無法映射到英文的注音鍵（ㄅㄉㄦ 等數字行或聲調鍵）→ 明確注音
        if (qwertyChar == null && isBopomofoKey) {
            if (activeEngine !is ChewingInputEngine) {
                activeEngine.reset()
                candidateManager.clear()
                activeEngine = bopomofoEngine
                for (sym in autoBopomofoBuffer) bopomofoEngine.processChar(sym)
            }
            autoQwertyBuffer.clear()
            autoBopomofoBuffer.clear()
            return
        }

        // ── 累積雙重解讀 ──
        if (qwertyChar != null) autoQwertyBuffer.append(qwertyChar)
        if (isBopomofoKey) {
            autoBopomofoBuffer.add(label)
        } else if (qwertyChar != null) {
            QWERTY_TO_BOPOMOFO[qwertyChar]?.let { autoBopomofoBuffer.add(it) }
        }

        val englishSoFar = autoQwertyBuffer.toString()

        // ── 評分邏輯 ──
        var engScore = 0
        var bopoScore = 0

        // 訊號一：字典命中
        if (englishSoFar.length >= 2) {
            val engCands = (englishEngine as? EnglishInputEngine)?.getCandidatesForPrefix(englishSoFar) ?: emptyList()
            if (engCands.isNotEmpty()) {
                engScore += engCands.size.coerceAtMost(5) * 10 + 30
                // 完整匹配加大分（如 "apple" 完全命中字典詞）
                if (engCands.any { it.equals(englishSoFar, ignoreCase = true) }) {
                    engScore += 50
                }
            }
        }
        if (autoBopomofoBuffer.isNotEmpty()) {
            val bopoStr = autoBopomofoBuffer.joinToString("")
            val bopoCands = (bopomofoEngine as? ChewingInputEngine)?.getCandidatesForBopomofo(bopoStr) ?: emptyList()
            if (bopoCands.isNotEmpty()) {
                bopoScore += bopoCands.size.coerceAtMost(5) * 10 + 30
            } else if (autoBopomofoBuffer.size >= 3) {
                // 3+ 注音組合查無結果 → 很可能不是要打中文，減分
                bopoScore -= 20
            }
        }

        // 訊號二：上下文連續性
        if (autoLastCommitWasEnglish) engScore += 20 else bopoScore += 20

        // 訊號三：長度合理性
        if (englishSoFar.length in 2..15) engScore += 10
        if (autoBopomofoBuffer.size >= 2) bopoScore += 10

        // 訊號四：3+ 字元英文有候選但注音無候選 → 明確英文意圖
        if (englishSoFar.length >= 3 && engScore > 30 && bopoScore <= 30) {
            engScore += 40
        }

        autoEnglishScore = engScore
        autoBopomofoScore = bopoScore

        // ── 決策：只有分數差距明顯時才切換 ──
        // 前 2 字符閾值較高避免誤判，3+ 字符降低閾值加速切換
        val threshold = when {
            autoQwertyBuffer.length <= 1 -> 50  // 單字符不輕易切
            autoQwertyBuffer.length == 2 -> 30  // 2 字符適中
            else -> 15  // 3+ 字符，快速響應
        }

        if (engScore - bopoScore >= threshold && activeEngine !is EnglishInputEngine) {
            // 切到英文引擎（保持 currentMode = AUTO）
            activeEngine.reset()
            candidateManager.clear()
            activeEngine = englishEngine
            for (c in autoQwertyBuffer.toString().dropLast(1)) {
                englishEngine.processChar(c.toString())
            }
        } else if (bopoScore - engScore >= threshold && activeEngine !is ChewingInputEngine) {
            activeEngine.reset()
            candidateManager.clear()
            activeEngine = bopomofoEngine
            for (sym in autoBopomofoBuffer.dropLast(1)) {
                bopomofoEngine.processChar(sym)
            }
        }
        // 分數接近 → 繼續收集更多按鍵再決定
    }

    /**
     * AUTO 模式：取得中英混合候選。
     * 兩種語言交錯排列，優先語言在奇數位。
     */
    fun getAutoCombinedCandidates(): List<String> {
        val engCands = (englishEngine as? EnglishInputEngine)
            ?.getCandidatesForPrefix(autoQwertyBuffer.toString()) ?: emptyList()
        val bopoCands = (bopomofoEngine as? ChewingInputEngine)
            ?.getCandidatesForBopomofo(autoBopomofoBuffer.joinToString("")) ?: emptyList()

        // 交錯排列：優先語言的候選在前
        val primary = if (autoEnglishScore >= autoBopomofoScore) engCands else bopoCands
        val secondary = if (autoEnglishScore >= autoBopomofoScore) bopoCands else engCands
        val result = mutableListOf<String>()
        val maxLen = maxOf(primary.size, secondary.size)
        for (i in 0 until maxLen) {
            if (i < primary.size && primary[i] !in result) result.add(primary[i])
            if (i < secondary.size && secondary[i] !in result) result.add(secondary[i])
        }
        return result.take(15)
    }

    /** 切換模式並清空引擎狀態 */
    private fun switchToClean(mode: InputMode) {
        activeEngine.reset()
        candidateManager.clear()
        currentMode = mode
        activeEngine = engineForMode(mode)
    }

    private fun updateCandidates() {
        // AUTO 模式：合併中英雙語候選
        val raw = if (currentMode == InputMode.AUTO && autoQwertyBuffer.isNotEmpty()) {
            val combined = getAutoCombinedCandidates()
            if (combined.isNotEmpty()) combined else activeEngine.getCandidates().filter { it.isNotBlank() }
        } else {
            activeEngine.getCandidates().filter { it.isNotBlank() }
        }
        val composing = activeEngine.composingText

        // 非英文模式：引擎候選 + 自適應字典排序（composing 有值才加整句預測）
        if (currentMode != InputMode.ENGLISH) {
            val historySourceMap = mutableMapOf<String, CandidateSource>()

            if (raw.isEmpty() && candidateManager.historyCandidates.isEmpty()) {
                // 沒有候選也沒歷史就不顯示
                candidateSourceMap = emptyMap()
                candidateManager.update(emptyList())
                return
            }
            // composing 為空 = 使用者已送出文字
            if (composing.isEmpty()) {
                // 若剛提交了文字，顯示前後文接續候選（語義預測）
                if (!lastCommittedWord.isNullOrEmpty()) {
                    val continuations = generateContinuationCandidates(lastCommittedWord!!)
                    if (continuations.isNotEmpty()) {
                        val contSourceMap = mutableMapOf<String, CandidateSource>()
                        for (c in continuations) contSourceMap[c] = CandidateSource.PREDICTION
                        candidateSourceMap = contSourceMap
                        candidateManager.update(continuations)
                        return
                    }
                }
                candidateSourceMap = emptyMap()
                candidateManager.clear()
                return
            }
            if (raw.isEmpty()) {
                // 有 composing 但引擎沒候選 → 嘗試產生接續候選（選字後接續預測）
                val lastChar = composing.lastOrNull()?.toString() ?: ""
                val continuations = if (lastChar.isNotEmpty() && lastChar.any { it.code > 0x2E80 }) {
                    generateContinuationCandidates(lastChar)
                } else emptyList()

                if (continuations.isNotEmpty()) {
                    for (c in continuations) historySourceMap[c] = CandidateSource.PREDICTION
                    for (h in candidateManager.historyCandidates) historySourceMap[h] = CandidateSource.HISTORY
                    candidateSourceMap = historySourceMap
                    candidateManager.update(continuations)
                    return
                }
                // 真的沒有 → 只顯示歷史候選
                for (h in candidateManager.historyCandidates) historySourceMap[h] = CandidateSource.HISTORY
                candidateSourceMap = historySourceMap
                candidateManager.update(emptyList())
                return
            }

            // 有 composing 才做整句預測
            // 過濾非 CJK 候選（防止英文詞透過學習模型洩漏到中文/日文/韓文模式）
            val isCjkMode = currentMode in setOf(
                InputMode.BOPOMOFO, InputMode.PINYIN, InputMode.CANGJIE,
                InputMode.SPEED_CANGJIE, InputMode.JAPANESE, InputMode.KOREAN, InputMode.AUTO
            )
            val sentenceSuggestions = if (composing.isNotEmpty()) {
                smartInput.getSentencePredictions(contextBeforeCursor, composing, 3)
                    .filter { it.isNotBlank() }
                    .let { if (isCjkMode) it.filter { s -> s.any { c -> c.code > 0x2E80 } } else it }
            } else emptyList()

            if (raw.size > 1) {
                val contextWords = extractContextWords(contextBeforeCursor)
                val contextPredictions = if (contextWords.isNotEmpty()) {
                    smartInput.predictWithContext(contextWords, 10)
                        .let { if (isCjkMode) it.filter { w -> w.any { c -> c.code > 0x2E80 } } else it }
                        .withIndex()
                        .associate { (i, w) -> w to (10f - i).coerceAtLeast(1f) }
                } else emptyMap()

                // 中文上下文串聯預測：上一字 → 下一字
                val nextCharBoost = if (activeEngine is ChewingInputEngine && !lastCommittedWord.isNullOrEmpty()) {
                    val predictions = (activeEngine as ChewingInputEngine).getNextCharPredictions(lastCommittedWord!!)
                    predictions.withIndex().associate { (i, w) -> w to (15f - i * 0.5f).coerceAtLeast(1f) }
                } else emptyMap()

                val boosted = raw.sortedWith(compareByDescending<String> { word ->
                    var score = 0f
                    val freq = smartInput.adaptiveDictionary.getFrequency(word)
                    if (freq > 0) score += freq + 50
                    val ctxScore = contextPredictions[word]
                    if (ctxScore != null) score += ctxScore * 5
                    // 串聯預測加分（上一字的接續候選）
                    val ngramScore = nextCharBoost[word]
                    if (ngramScore != null) score += ngramScore * 3
                    // 個人詞庫加分（使用者學過的詞優先）
                    if (smartInput.personalDictionary.isKnown(word)) score += 30
                    // UserDictionary 加分
                    if (userDict.contains(word)) score += 20
                    score
                }.thenBy { raw.indexOf(it) })

                // 排序：整句預測 → 串聯接續候選 → 一般排序候選
                val combined = mutableListOf<String>()
                // 1. 整句預測排最前（最可能是用戶想要的完整詞）
                for (s in sentenceSuggestions) {
                    if (s !in combined) combined.add(s)
                }
                // 2. 串聯接續候選排第二（上一字預測的下一字）
                val continuationCands = nextCharBoost.entries
                    .filter { it.key in raw }
                    .sortedByDescending { it.value }
                    .map { it.key }
                for (c in continuationCands) {
                    if (c !in combined) combined.add(c)
                }
                // 3. 其餘候選按分數排
                for (b in boosted) {
                    if (b !in combined) combined.add(b)
                }
                // 標記來源
                for (h in candidateManager.historyCandidates) {
                    historySourceMap[h] = CandidateSource.HISTORY
                }
                for (s in sentenceSuggestions) {
                    historySourceMap[s] = CandidateSource.SENTENCE
                }
                for (c in continuationCands) {
                    historySourceMap[c] = CandidateSource.PREDICTION
                }
                candidateSourceMap = historySourceMap
                candidateManager.update(combined)
            } else {
                // 整句預測排前面
                val combined = mutableListOf<String>()
                for (s in sentenceSuggestions) {
                    if (s !in combined) combined.add(s)
                    historySourceMap[s] = CandidateSource.SENTENCE
                }
                for (r in raw) {
                    if (r !in combined) combined.add(r)
                }
                for (h in candidateManager.historyCandidates) {
                    historySourceMap[h] = CandidateSource.HISTORY
                }
                candidateSourceMap = historySourceMap
                candidateManager.update(combined)
            }
            return
        }

        // ─── 英文模式：統合多來源候選 + 統一排序 ───
        // composing 為空 = 已送出文字
        if (composing.isEmpty() && raw.isEmpty()) {
            // 英文模式也顯示接續候選
            if (!lastCommittedWord.isNullOrEmpty()) {
                val continuations = generateContinuationCandidates(lastCommittedWord!!)
                if (continuations.isNotEmpty()) {
                    val contSourceMap = mutableMapOf<String, CandidateSource>()
                    for (c in continuations) contSourceMap[c] = CandidateSource.PREDICTION
                    candidateSourceMap = contSourceMap
                    candidateManager.update(continuations)
                    return
                }
            }
            candidateSourceMap = emptyMap()
            candidateManager.clear()
            return
        }
        val scored = mutableMapOf<String, Float>()
        val sourceMap = mutableMapOf<String, CandidateSource>()

        // 1. 字典前綴匹配（基礎分 = 排名靠前的分高）
        raw.forEachIndexed { i, word ->
            scored[word] = (scored[word] ?: 0f) + (10f - i * 0.5f).coerceAtLeast(1f)
            sourceMap.putIfAbsent(word, CandidateSource.DICTIONARY)
        }

        if (composing.isNotEmpty()) {
            // 2. 智慧糾錯（拼錯時提供修正，高加分）
            val corrections = smartInput.getCorrectionCandidates(composing, lastCommittedWord)
            corrections.forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (8f - i).coerceAtLeast(2f)
                if (word !in raw) sourceMap[word] = CandidateSource.CORRECTION
            }

            // 3. 自適應字典（使用者學過的詞，中高加分）
            val adaptive = smartInput.adaptiveDictionary.suggest(composing, 5)
            adaptive.forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (6f - i * 0.5f).coerceAtLeast(1f)
                if (word !in raw) sourceMap[word] = CandidateSource.ADAPTIVE
            }

            // 4. 語句預測（bigram/trigram 符合前綴的，高加分）
            val prefixed = smartInput.getPhrasePredictionsWithPrefix(composing, 3)
            prefixed.forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (7f - i).coerceAtLeast(2f)
                if (word !in raw) sourceMap[word] = CandidateSource.PREDICTION
            }

            // 5. 使用者自訂詞庫（手動加的詞 + 自動學習的詞，高加分）
            val userSuggestions = userDict.suggest(composing, 5)
            userSuggestions.forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (9f - i).coerceAtLeast(3f)
                sourceMap[word] = CandidateSource.USER_DICT  // 最高優先來源
            }

            // 6. 個人詞庫三層詞（J-1: PINNED/LEARNED 加分）
            val personalSuggestions = smartInput.personalDictionary.suggest(composing, 5)
            personalSuggestions.forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (8f - i).coerceAtLeast(2f)
                sourceMap.putIfAbsent(word, CandidateSource.USER_DICT)
            }

            // 7. 語氣風格加分（J-2: 符合當前語氣偏好的候選字加分）
            for ((word, _) in scored.toMap()) {
                val boost = smartInput.getStyleBoost(word, currentAppPackage)
                if (boost > 0) scored[word] = (scored[word] ?: 0f) + boost
            }

            // 8. 完全匹配 composing 加分（使用者可能就是要打這個詞）
            scored[composing.lowercase()]?.let { prev ->
                scored[composing.lowercase()] = prev + 3f
            }
        } else {
            // composing 為空：不主動顯示預測，避免干擾
        }

        val sorted = scored.entries
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }
            .toMutableList()

        // 有 composing 才加整句預測，沒在打字不干擾
        if (composing.isNotEmpty()) {
            val enSentences = smartInput.getSentencePredictions(
                contextBeforeCursor, composing, 3
            )
            for (s in enSentences) {
                if (s !in sorted) sorted.add(s)
            }
            for (s in enSentences) {
                sourceMap.putIfAbsent(s, CandidateSource.SENTENCE)
            }
        }

        // 標記歷史候選字來源
        for (h in candidateManager.historyCandidates) {
            sourceMap[h] = CandidateSource.HISTORY
        }
        candidateSourceMap = sourceMap
        candidateManager.update(sorted)
    }

    /**
     * 根據最近提交的文字，產生前後文接續候選（語義預測）。
     * 用於選字後不清空候選列，讓使用者繼續選下一個詞。
     */
    private fun generateContinuationCandidates(committed: String): List<String> {
        val results = linkedSetOf<String>()
        val isCjk = committed.any { it.code > 0x2E80 }

        // 1. 注音引擎的 nextCharIndex（字 → 下一字/詞）
        if (activeEngine is ChewingInputEngine) {
            val lastChar = committed.last().toString()
            val nextChars = (activeEngine as ChewingInputEngine).getNextCharPredictions(lastChar)
            results.addAll(nextChars.take(15))
        }

        // 2. SmartInput 上下文預測（bigram/trigram）
        val contextWords = extractContextWords(contextBeforeCursor + committed)
        if (contextWords.isNotEmpty()) {
            val predictions = smartInput.predictWithContext(contextWords, 15)
                .let { if (isCjk) it.filter { w -> w.any { c -> c.code > 0x2E80 } } else it }
            results.addAll(predictions)
        }

        // 3. 整句預測
        val sentPreds = smartInput.getSentencePredictions(contextBeforeCursor + committed, "", 5)
            .filter { it.isNotBlank() }
            .let { if (isCjk) it.filter { s -> s.any { c -> c.code > 0x2E80 } } else it }
        results.addAll(sentPreds)

        // 4. 自適應字典高頻詞
        val adaptive = smartInput.adaptiveDictionary.getTopWords(10)
            .map { it.first }
            .let { if (isCjk) it.filter { w -> w.any { c -> c.code > 0x2E80 } } else it }
        results.addAll(adaptive)

        return results.toList().take(20)
    }

    /** 從游標前文字提取最近的 context words（CJK 每字一個 token，英文按空格分） */
    private fun extractContextWords(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        // 從尾端取最近 3 個 token
        val chars = trimmed.toList().reversed()
        for (ch in chars) {
            if (result.size >= 3) break
            if (ch.isWhitespace()) continue
            val code = ch.code
            val isCjk = code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
                    || code in 0x3040..0x30FF || code in 0xAC00..0xD7AF
            if (isCjk) {
                result.add(ch.toString())
            } else if (ch.isLetter()) {
                // 英文：回溯整個單詞
                val idx = trimmed.lastIndexOf(ch)
                val wordStart = trimmed.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), idx) + 1
                val word = trimmed.substring(wordStart, idx + 1)
                if (word.isNotBlank() && word !in result) result.add(word)
                break
            }
        }
        return result.reversed()
    }

    /** 記錄提交詞，供智慧輸入各模組學習 */
    private fun onTextCommitted(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        // AUTO 模式：commit 後重置雙重評分緩衝，準備下一個詞
        // 但保留當前引擎，不強制跳回英文（修復自動跳英文鍵盤問題）
        if (currentMode == InputMode.AUTO) {
            autoQwertyBuffer.clear()
            autoBopomofoBuffer.clear()
            autoEnglishScore = 0
            autoBopomofoScore = 0
            autoLastCommitWasEnglish = trimmed.all { it.code < 128 }
            // 不再重置 activeEngine — 讓使用者繼續用當前語言引擎
        }

        val hasCjk = trimmed.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        val hasKana = trimmed.any { it.code in 0x3040..0x30FF }
        val hasHangul = trimmed.any { it.code in 0xAC00..0xD7AF }

        if (hasCjk || hasKana || hasHangul) {
            for (ch in trimmed) {
                if (ch.isWhitespace()) continue
                val charStr = ch.toString()
                smartInput.onWordCommitted(charStr, currentAppPackage)
                lastCommittedWord = charStr
            }
            if (trimmed.length > 1) {
                smartInput.onWordCommitted(trimmed, currentAppPackage)
            }
            // 自動學詞：中文選字後記錄 word + bopomofo 到 UserDictionary
            // 供下次注音查詢時優先顯示使用者打過的詞
            if (hasCjk && activeEngine is ChewingInputEngine) {
                val bopo = (activeEngine as ChewingInputEngine).lastComposingBopomofo
                if (bopo.isNotEmpty() && trimmed.length >= 2) {
                    userDict.addManual(trimmed, bopo)
                }
                userDict.learn(trimmed)
            }
        } else {
            val words = trimmed.split("\\s+".toRegex())
            for (word in words) {
                if (word.isNotBlank()) {
                    smartInput.onWordCommitted(word, currentAppPackage)
                    userDict.learn(word)
                    lastCommittedWord = word
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 輔助資料類型
// ─────────────────────────────────────────────────────────────────────────────

/** 候選字來源分類（用於視覺標示） */
enum class CandidateSource {
    DICTIONARY,   // 字典前綴匹配
    CORRECTION,   // 智慧糾錯
    ADAPTIVE,     // 自適應字典（頻率學習）
    PREDICTION,   // 語句預測（bigram/trigram）
    USER_DICT,    // 使用者自訂詞庫
    SENTENCE,     // 整句預測
    AI,           // AI 建議（Claw）
    HISTORY,      // 歷史候選字（前面詞段未選的）
}

enum class InputMode(val displayName: String, val shortLabel: String) {
    ENGLISH("English", "EN"),
    BOPOMOFO("注音", "注"),
    PINYIN("拼音", "拼"),
    CANGJIE("倉頡", "倉"),
    SPEED_CANGJIE("速成", "速"),
    JAPANESE("日本語", "あ"),
    KOREAN("한국어", "한"),
    AUTO("自動", "A"),
}

/**
 * 引擎操作結果。密封類讓 Service 端做模式匹配而非 if-else 鏈。
 */
sealed class InputResult {
    /** 有文字要提交到目標 App */
    data class CommitText(val text: String) : InputResult()
    /** 組字緩衝已更新，需顯示在 composing bar */
    data class ComposingUpdated(val composing: String) : InputResult()
    /** 組字緩衝已清空 */
    object ComposingCleared : InputResult()
    /** 無動作 */
    object Idle : InputResult()
}
