package com.snowrealm.yukiboard.input.router

import android.content.Context
import com.snowrealm.yukiboard.input.candidate.CandidateManager
import com.snowrealm.yukiboard.input.engine.ChewingInputEngine
import com.snowrealm.yukiboard.input.engine.EnglishInputEngine
import com.snowrealm.yukiboard.input.engine.InputEngine

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
    private val englishEngine: InputEngine = EnglishInputEngine()
    private val bopomofoEngine: InputEngine = ChewingInputEngine(context)
    private val candidateManager = CandidateManager()

    private var currentMode: InputMode = InputMode.ENGLISH
    private var activeEngine: InputEngine = englishEngine

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
        candidateManager.clear()
        return result
    }

    /**
     * 使用者從候選列選擇第 [index] 個候選字。
     */
    fun selectCandidate(index: Int): InputResult {
        if (activeEngine is ChewingInputEngine) {
            val result = (activeEngine as ChewingInputEngine).selectCandidateAt(index)
            updateCandidates()
            return result
        }
        // 英文候選：直接 commit 選中的字
        val selected = candidateManager.selectAt(index)
        if (selected != null) {
            activeEngine.reset()
            candidateManager.clear()
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
        val raw = activeEngine.getCandidates()
        candidateManager.update(raw)
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
