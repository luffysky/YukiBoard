package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.input.router.CandidateSource
import com.snowrealm.yukiboard.ui.keyboard.KeyboardView

/**
 * Sprint 4 Round 1：`composingText / candidateState` 的正式 render 邊界。
 *
 * 責任（來自 `SPRINT4_INPUT_FLOW_REVIEW §3.3`）：
 * - 統一同步 `composingText / candidates`
 * - 統一處理清空與 post-update render
 * - 明確標示哪些屬於同步 render，哪些未來可轉為 state-driven
 *
 * 不負責：
 * - `InputRouter` 演算法
 * - 外部整合 client
 * - panel container 操作
 *
 * 設計：
 * - `keyboardView` 以 lambda provider 取。`YukiBoardIMEService.keyboardView` 在 `onCreateInputView`
 *   會隨懸浮/標準模式切換被重新指派；InputUiSync 必須在 call-time 解析當前實例，不能持有 snapshot。
 * - `coordinator` 是穩定實例（session 生命週期內不變），直接持有。
 * - 所有方法皆同步呼叫，不引入 coroutine / post，熱路徑時序與 `ic.beginBatchEdit` 相容。
 */
class InputUiSync(
    private val viewProvider: () -> KeyboardView,
    private val coordinator: ImeSessionCoordinator,
) {

    /**
     * 同步寫入 composing：view + coordinator 同時更新。
     *
     * 熱路徑呼叫端應以此取代分散的
     *   `keyboardView.updateComposingText(text) + coordinator.updateComposingText(text)`
     * 雙寫，避免兩者漂移。
     */
    fun renderComposing(text: String) {
        viewProvider().updateComposingText(text)
        coordinator.updateComposingText(text)
    }

    /**
     * 同步寫入 candidates：view + coordinator 同時更新（含 sourceMap 著色）。
     *
     * Sprint 3 Phase 2 Round 3 已讓 `ImeUiState.candidateState` 可承載 sourceMap；
     * 本 API 是該結構升級後第一個正式使用者。
     */
    fun renderCandidates(
        items: List<String>,
        sourceMap: Map<String, CandidateSource> = emptyMap(),
    ) {
        viewProvider().updateCandidates(items, sourceMap)
        coordinator.updateCandidates(items, sourceMap)
    }

    /** composing 清空捷徑 */
    fun clearComposing() {
        renderComposing("")
    }

    /** candidates 清空捷徑（含清空 sourceMap） */
    fun clearCandidates() {
        renderCandidates(emptyList(), emptyMap())
    }

    /** composing + candidates 同時清空 */
    fun clearAll() {
        clearComposing()
        clearCandidates()
    }
}
