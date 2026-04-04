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
    private val englishEngine: InputEngine = EnglishInputEngine(context)
    private val bopomofoEngine: InputEngine = ChewingInputEngine(context)
    private val pinyinEngine: InputEngine by lazy { PinyinInputEngine() }
    private val cangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = false) }
    private val speedCangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = true) }
    private val japaneseEngine: InputEngine by lazy { JapaneseInputEngine() }
    private val koreanEngine: InputEngine by lazy { KoreanInputEngine() }

    val candidateManager = CandidateManager()
    val smartInput = SmartInputManager(context)

    private var currentMode: InputMode = InputMode.ENGLISH
    private var activeEngine: InputEngine = englishEngine

    // 前一個提交詞（供智慧糾錯上下文用）
    private var lastCommittedWord: String? = null
    // 目前 App 套件名（供習慣追蹤用）
    var currentAppPackage: String? = null

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
        }
        candidateManager.clear()
        smartInput.resetContext()
        return result
    }

    /**
     * 使用者從候選列選擇第 [index] 個候選字。
     */
    fun selectCandidate(index: Int): InputResult {
        // 各引擎有各自的 selectCandidateAt 實作
        val result = when (val eng = activeEngine) {
            is ChewingInputEngine -> eng.selectCandidateAt(index)
            is PinyinInputEngine -> eng.selectCandidateAt(index)
            is CangjieInputEngine -> eng.selectCandidateAt(index)
            is JapaneseInputEngine -> eng.selectCandidateAt(index)
            else -> {
                // 英文/韓文：通用候選選擇
                val selected = candidateManager.selectAt(index)
                if (selected != null) {
                    activeEngine.reset()
                    candidateManager.clear()
                    onTextCommitted(selected)
                    return InputResult.CommitText(selected)
                }
                return InputResult.Idle
            }
        }
        if (result is InputResult.CommitText) onTextCommitted(result.text)
        updateCandidates()
        return result
    }

    fun reset() {
        activeEngine.reset()
        candidateManager.clear()
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
        val raw = activeEngine.getCandidates().toMutableList()
        val composing = activeEngine.composingText

        // 對英文模式追加智慧糾錯候選
        if (currentMode == InputMode.ENGLISH && composing.isNotEmpty()) {
            val corrections = smartInput.getCorrectionCandidates(composing, lastCommittedWord)
            if (corrections.isNotEmpty()) {
                val merged = (corrections + raw).distinct()
                candidateManager.update(merged)
                return
            }

            val adaptive = smartInput.adaptiveDictionary.suggest(composing, 3)
            raw.addAll(adaptive.filter { it !in raw })

            val prefixed = smartInput.getPhrasePredictionsWithPrefix(composing, 3)
            raw.addAll(prefixed.filter { it !in raw })
        }

        // composing 為空時顯示下一詞預測
        if (composing.isEmpty() && currentMode == InputMode.ENGLISH) {
            val predictions = smartInput.getPhrasePredictions(5)
            if (predictions.isNotEmpty()) {
                raw.addAll(predictions.filter { it !in raw })
            }
        }

        candidateManager.update(raw.distinct())
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
                    lastCommittedWord = word
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 輔助資料類型
// ─────────────────────────────────────────────────────────────────────────────

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
