package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.input.router.CandidateSource
import com.snowrealm.yukiboard.input.router.InputResult
import com.snowrealm.yukiboard.ui.panel.PanelInputTarget

/**
 * 收斂 IME 候選互動中的低風險 helper。
 *
 * 目前只處理：
 * - panel input target routing
 * - raw commit 前的 composing flush
 * - swipe candidate 狀態清理
 * - AI candidate / extra candidate 判斷
 *
 * 不改變 applyInputResult() 或 InputRouter 的核心語意。
 */
class ImeCandidateInteractionHandler {

    fun routeRawCommitToPanel(
        target: PanelInputTarget?,
        text: String,
    ): Boolean {
        if (target == null) return false
        target.onComposedTextInput(text)
        return true
    }

    fun flushComposingBeforeRawCommit(
        composingText: String,
        commitComposing: (String) -> Unit,
        resetInputState: () -> Unit,
        clearInputUiState: () -> Unit,
    ) {
        if (composingText.isEmpty()) return
        commitComposing(composingText)
        resetInputState()
        clearInputUiState()
    }

    fun routeCommittedCandidateToPanel(
        target: PanelInputTarget?,
        result: InputResult,
    ): Boolean {
        if (target == null || result !is InputResult.CommitText) return false
        target.onComposedTextInput(result.text)
        return true
    }

    fun isAICandidate(
        displayedText: String?,
        candidateSourceMap: Map<String, CandidateSource>,
    ): Boolean {
        return displayedText != null &&
            candidateSourceMap[displayedText] == CandidateSource.AI
    }

    fun isExtraCandidate(
        displayedText: String?,
        index: Int,
        routerCandidateCount: Int,
    ): Boolean {
        return displayedText != null && index >= routerCandidateCount
    }

    fun clearSwipeState(
        clearPendingFlag: () -> Unit,
        clearCandidates: () -> Unit,
        clearAccumulated: () -> Unit,
        clearInputUiState: () -> Unit,
    ) {
        clearPendingFlag()
        clearCandidates()
        clearAccumulated()
        clearInputUiState()
    }
}
