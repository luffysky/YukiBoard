package com.snowrealm.yukiboard.app.ime

/**
 * Sprint 2: 收斂 panel 切換前後的 cleanup 與 input reset。
 *
 * 保持 service 只決定何時切換，不直接展開所有 cleanup 步驟。
 */
class ImePanelCleanup {

    fun prepareForPanelOpen(
        commitPendingComposition: () -> Unit,
        clearPanelBindings: () -> Unit,
        resetInputUiState: () -> Unit,
        clearPanelContainers: () -> Unit,
    ) {
        commitPendingComposition()
        clearPanelBindings()
        resetInputUiState()
        clearPanelContainers()
    }

    fun cleanupAfterPanelClose(
        clearPanelBindings: () -> Unit,
        hidePanels: () -> Unit,
        resetInputUiState: () -> Unit,
        finishComposingText: () -> Unit,
        restoreOverlayOrder: () -> Unit,
        restoreKeyboardFocus: () -> Unit,
        restoreKeyboardBackground: () -> Unit,
    ) {
        clearPanelBindings()
        hidePanels()
        resetInputUiState()
        finishComposingText()
        restoreOverlayOrder()
        restoreKeyboardFocus()
        restoreKeyboardBackground()
    }
}
