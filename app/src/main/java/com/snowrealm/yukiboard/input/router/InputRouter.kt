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
    private val cangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = false) }
    private val speedCangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = true) }
    private val japaneseEngine: InputEngine by lazy { JapaneseInputEngine() }
    private val koreanEngine: InputEngine by lazy { KoreanInputEngine() }

    val candidateManager = CandidateManager()
    val smartInput = SmartInputManager(context)

    /** 候選字來源映射（每次 updateCandidates 時更新） */
    var candidateSourceMap: Map<String, CandidateSource> = emptyMap()
        private set

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
        InputMode.AUTO -> englishEngine
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

        val result = activeEngine.processChar(label)
        updateCandidates()
        return result
    }

    fun processDelete(): InputResult {
        val result = activeEngine.processDelete()
        updateCandidates()
        return result
    }

    fun processSpace(): InputResult {
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
        return result
    }

    /**
     * 使用者從候選列選擇第 [index] 個候選字。
     */
    fun selectCandidate(index: Int): InputResult {
        // 候選列可能被自適應字典重新排序，需把 UI 索引映射回引擎索引
        val displayedText = candidateManager.candidates.getOrNull(index)
        val result = when (val eng = activeEngine) {
            is ChewingInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } ?: index else index
                eng.selectCandidateAt(engineIndex)
            }
            is PinyinInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } ?: index else index
                eng.selectCandidateAt(engineIndex)
            }
            is CangjieInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } ?: index else index
                eng.selectCandidateAt(engineIndex)
            }
            is JapaneseInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } ?: index else index
                eng.selectCandidateAt(engineIndex)
            }
            is KoreanInputEngine -> {
                val engineIndex = if (displayedText != null) eng.getCandidates().indexOf(displayedText).takeIf { it >= 0 } ?: index else index
                eng.selectCandidateAt(engineIndex)
            }
            else -> {
                // 英文：通用候選選擇
                val selected = candidateManager.selectAt(index)
                if (selected != null) {
                    activeEngine.reset()
                    candidateManager.clear()
                    onTextCommitted(selected)
                    smartInput.onCandidateHit()
                    return InputResult.CommitText(selected)
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
        val char = label[0]
        val targetMode = when {
            char in '\u3105'..'\u312F' -> InputMode.BOPOMOFO
            char in '\u3131'..'\u3163' -> InputMode.KOREAN  // 韓文 Jamo
            char in '\u3041'..'\u309F' -> InputMode.JAPANESE // 平假名
            char in '\u30A0'..'\u30FF' -> InputMode.JAPANESE // 片假名
            char.isLetter()            -> InputMode.ENGLISH
            else                       -> currentMode
        }
        if (targetMode != currentMode) {
            switchTo(targetMode)
        }
    }

    private fun updateCandidates() {
        val raw = activeEngine.getCandidates()
        val composing = activeEngine.composingText

        // 非英文模式：引擎候選 + 自適應字典 + 上下文 bigram/trigram + 整句預測
        if (currentMode != InputMode.ENGLISH) {
            candidateSourceMap = emptyMap() // 非英文暫不追蹤來源
            // ── 整句預測（放在候選列末端，用於直接選句）──
            val sentenceSuggestions = smartInput.getSentencePredictions(
                contextBeforeCursor, composing, 3
            )

            if (raw.size > 1) {
                // 從上下文提取最近幾個字/詞（CJK 每個字算一個 token）
                val contextWords = extractContextWords(contextBeforeCursor)
                val contextPredictions = if (contextWords.isNotEmpty()) {
                    smartInput.predictWithContext(contextWords, 10)
                        .withIndex()
                        .associate { (i, w) -> w to (10f - i).coerceAtLeast(1f) }
                } else emptyMap()

                val boosted = raw.sortedWith(compareByDescending<String> { word ->
                    var score = 0f
                    // 自適應字典分數
                    val freq = smartInput.adaptiveDictionary.getFrequency(word)
                    if (freq > 0) score += freq + 50
                    // 上下文 bigram/trigram 加權
                    val ctxScore = contextPredictions[word]
                    if (ctxScore != null) score += ctxScore * 5
                    score
                }.thenBy { raw.indexOf(it) })  // 其他保持原順序

                // 合併：單字候選在前，整句建議在後
                val combined = boosted.toMutableList()
                for (s in sentenceSuggestions) {
                    if (s !in combined) combined.add(s)
                }
                candidateManager.update(combined)
            } else {
                val combined = raw.toMutableList()
                for (s in sentenceSuggestions) {
                    if (s !in combined) combined.add(s)
                }
                candidateManager.update(combined)
            }
            return
        }

        // ─── 英文模式：統合多來源候選 + 統一排序 ───
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
            // composing 為空：下一詞預測 + 情境建議
            val predictions = smartInput.getPhrasePredictions(5)
            predictions.forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (6f - i).coerceAtLeast(1f)
            }
            val context = smartInput.getContextSuggestions(currentAppPackage, 3)
            context.forEachIndexed { i, word ->
                scored[word] = (scored[word] ?: 0f) + (5f - i).coerceAtLeast(1f)
            }
        }

        val sorted = scored.entries
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }
            .toMutableList()

        // 英文也加整句預測
        val enSentences = smartInput.getSentencePredictions(
            contextBeforeCursor, composing, 3
        )
        for (s in enSentences) {
            if (s !in sorted) sorted.add(s)
        }

        // 整句預測標記來源
        for (s in enSentences) {
            sourceMap.putIfAbsent(s, CandidateSource.SENTENCE)
        }

        candidateSourceMap = sourceMap
        candidateManager.update(sorted)
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
