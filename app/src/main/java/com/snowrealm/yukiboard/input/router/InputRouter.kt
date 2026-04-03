package com.snowrealm.yukiboard.input.router

import android.content.Context
import com.snowrealm.yukiboard.input.candidate.CandidateManager
import com.snowrealm.yukiboard.input.engine.ChewingInputEngine
import com.snowrealm.yukiboard.input.engine.EnglishInputEngine
import com.snowrealm.yukiboard.input.engine.InputEngine
import com.snowrealm.yukiboard.input.smart.SmartInputManager

/**
 * 中英混合輸入路由核心。
 *
 * ## 雙軌並行邏輯
 *
 * ```
 * 按鍵輸入
 *    │
 *    ├─ 模式鎖定中（ENGLISH / BOPOMOFO）──► 直接交給對應引擎
 *    │
 *    └─ AUTO 模式
 *         │
 *         ├─ 字元是注音聲符 [ㄅ-ㄩ] ──► 切換到 BOPOMOFO 軌道
 *         ├─ 字元是 ASCII 字母       ──► 切換到 ENGLISH 軌道
 *         └─ 其他（數字/符號）       ──► 直接 commit
 * ```
 *
 * 雙軌並行：兩個引擎各自維護組字緩衝，切換模式時前一軌道若有未提交
 * 的 composing，先強制 commit，再將控制權交給另一軌道。
 */
class InputRouter(private val context: Context) {

    // -------------------------------------------------------------------------
    // 引擎
    // -------------------------------------------------------------------------
    private val englishEngine: InputEngine = EnglishInputEngine(context)
    private val bopomofoEngine: InputEngine = ChewingInputEngine(context)
    val candidateManager = CandidateManager()
    val smartInput = SmartInputManager(context)

    private var currentMode: InputMode = InputMode.ENGLISH
    private var activeEngine: InputEngine = englishEngine

    // 前一個提交詞（供智慧糾錯上下文用）
    private var lastCommittedWord: String? = null
    // 目前 App 套件名（供習慣追蹤用）
    var currentAppPackage: String? = null

    init {
        // 讓 SmartInputManager 參考英文字典
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

    fun toggleLanguage() {
        val next = if (currentMode == InputMode.ENGLISH) InputMode.BOPOMOFO else InputMode.ENGLISH
        switchTo(next)
    }

    private fun switchTo(mode: InputMode) {
        if (mode == currentMode) return
        // 切換前先提交當前 composing
        activeEngine.forceCommit()
        currentMode = mode
        activeEngine = if (mode == InputMode.ENGLISH) englishEngine else bopomofoEngine
    }

    // -------------------------------------------------------------------------
    // 輸入處理
    // -------------------------------------------------------------------------

    /**
     * 處理普通字元輸入。
     * 在 AUTO 模式下會依字元自動切軌，其餘模式直接轉給活躍引擎。
     */
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
        if (activeEngine is ChewingInputEngine) {
            val result = (activeEngine as ChewingInputEngine).selectCandidateAt(index)
            if (result is InputResult.CommitText) onTextCommitted(result.text)
            updateCandidates()
            return result
        }
        // 英文候選：直接 commit 選中的字
        val selected = candidateManager.selectAt(index)
        if (selected != null) {
            activeEngine.reset()
            candidateManager.clear()
            onTextCommitted(selected)
            return InputResult.CommitText(selected)
        }
        return InputResult.Idle
    }

    fun reset() {
        englishEngine.reset()
        bopomofoEngine.reset()
        candidateManager.clear()
    }

    // -------------------------------------------------------------------------
    // AUTO 模式自動偵測
    // -------------------------------------------------------------------------

    /**
     * 根據輸入字元判斷應走哪條軌道。
     *
     * 偵測規則：
     * - 注音符號（Unicode CJK 注音符號區 U+3105..U+312F）→ BOPOMOFO
     * - 英文字母 A-Za-z → ENGLISH
     * - 其他（數字、標點）→ 保持當前模式
     */
    private fun autoDetectAndSwitch(label: String) {
        val char = label[0]
        val targetMode = when {
            char in '\u3105'..'\u312F' -> InputMode.BOPOMOFO
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

        // 追加智慧糾錯候選（插在前面）
        if (composing.isNotEmpty()) {
            val corrections = smartInput.getCorrectionCandidates(composing, lastCommittedWord)
            if (corrections.isNotEmpty()) {
                // 糾錯候選放最前，去除重複
                val merged = (corrections + raw).distinct()
                candidateManager.update(merged)
                return
            }

            // 追加自適應字典建議
            val adaptive = smartInput.adaptiveDictionary.suggest(composing, 3)
            raw.addAll(adaptive.filter { it !in raw })

            // 追加帶前綴的語句預測
            val prefixed = smartInput.getPhrasePredictionsWithPrefix(composing, 3)
            raw.addAll(prefixed.filter { it !in raw })
        }

        // composing 為空時 = 剛提交完一個詞，顯示下一詞預測
        if (composing.isEmpty()) {
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

        // 判斷是否含 CJK 字元
        val hasCjk = trimmed.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }

        if (hasCjk) {
            // 中文：逐字記錄 bigram（字→字），同時也記錄整個詞組
            for (ch in trimmed) {
                if (ch.isWhitespace()) continue
                val charStr = ch.toString()
                smartInput.onWordCommitted(charStr, currentAppPackage)
                lastCommittedWord = charStr
            }
            // 如果是多字詞組，也整體記錄（讓 adaptive dict 學詞組）
            if (trimmed.length > 1) {
                smartInput.onWordCommitted(trimmed, currentAppPackage)
            }
        } else {
            // 英文：按空格拆詞
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

enum class InputMode { ENGLISH, BOPOMOFO, AUTO }

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
