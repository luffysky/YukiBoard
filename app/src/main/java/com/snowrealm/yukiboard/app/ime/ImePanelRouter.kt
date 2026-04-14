package com.snowrealm.yukiboard.app.ime

import android.view.View
import com.snowrealm.yukiboard.ui.panel.PanelInputTarget
import com.snowrealm.yukiboard.ui.panel.PanelType

/**
 * 收斂 IME panel 切換 orchestration。
 *
 * 目前只負責：
 * - toggle action 分流
 * - open 前置 cleanup
 * - panel attach routing
 * - close 時的收尾順序
 *
 * 不處理各 panel 的具體建立。
 */
class ImePanelRouter(
    private val imePanelCoordinator: ImePanelCoordinator,
    private val imePanelCleanup: ImePanelCleanup,
    private val imePanelHost: ImePanelHost,
) {

    fun routeToggle(
        currentActivePanel: PanelType?,
        requestedPanel: PanelType,
        updatePanelSelection: (PanelType?) -> Unit,
        openVoiceInput: () -> Unit,
        openSettings: () -> Unit,
        showPanel: (PanelType) -> Unit,
        closePanel: () -> Unit,
    ) {
        when (val action = imePanelCoordinator.onPanelToggle(currentActivePanel, requestedPanel)) {
            PanelToggleAction.CloseCurrentPanel -> closePanel()
            PanelToggleAction.OpenVoiceInput -> {
                updatePanelSelection(null)
                openVoiceInput()
            }
            PanelToggleAction.OpenSettings -> {
                updatePanelSelection(null)
                openSettings()
            }
            is PanelToggleAction.ShowPanel -> showPanel(action.panel)
        }
    }

    fun preparePanelOpen(
        panel: PanelType,
        updatePanelSelection: (PanelType?) -> Unit,
        commitPendingComposition: () -> Unit,
        clearPanelBindings: () -> Unit,
        resetInputUiState: () -> Unit,
    ) {
        updatePanelSelection(panel)
        imePanelCleanup.prepareForPanelOpen(
            commitPendingComposition = commitPendingComposition,
            clearPanelBindings = clearPanelBindings,
            resetInputUiState = resetInputUiState,
            clearPanelContainers = imePanelHost::clearContainers,
        )
    }

    fun attachPanel(
        panel: PanelType,
        panelView: View,
        onOverlayPanelShown: () -> Unit,
        onOverlayPanelAttached: () -> Unit,
    ) {
        val isInputPanel = panelView is PanelInputTarget || panel == PanelType.CLIPBOARD
        imePanelHost.attachPanel(
            panelView = panelView,
            isInputPanel = isInputPanel,
            onOverlayPanelShown = onOverlayPanelShown,
            onOverlayPanelAttached = onOverlayPanelAttached,
        )
    }

    fun closePanel(
        updatePanelSelection: (PanelType?) -> Unit,
        clearPanelBindings: () -> Unit,
        resetInputUiState: () -> Unit,
        finishComposingText: () -> Unit,
        restoreOverlayOrder: () -> Unit,
        restoreKeyboardFocus: () -> Unit,
        restoreKeyboardBackground: () -> Unit,
        onOverlayPanelHidden: () -> Unit,
    ) {
        updatePanelSelection(null)
        imePanelCleanup.cleanupAfterPanelClose(
            clearPanelBindings = clearPanelBindings,
            hidePanels = {
                imePanelHost.hidePanels(
                    onOverlayPanelHidden = onOverlayPanelHidden,
                )
            },
            resetInputUiState = resetInputUiState,
            finishComposingText = finishComposingText,
            restoreOverlayOrder = restoreOverlayOrder,
            restoreKeyboardFocus = restoreKeyboardFocus,
            restoreKeyboardBackground = restoreKeyboardBackground,
        )
    }
}
