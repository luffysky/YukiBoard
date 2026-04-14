package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.ui.panel.PanelType

/**
 * Sprint 2: 收斂 IME panel 切換決策與選取狀態同步。
 *
 * 目前先處理切換入口，不搬動各 panel 的具體建立邏輯。
 */
class ImePanelCoordinator(
    private val imeCoordinator: ImeSessionCoordinator,
) {

    fun onPanelToggle(
        currentActivePanel: PanelType?,
        requestedPanel: PanelType,
    ): PanelToggleAction {
        if (currentActivePanel == requestedPanel) {
            return PanelToggleAction.CloseCurrentPanel
        }

        return when (requestedPanel) {
            PanelType.VOICE -> PanelToggleAction.OpenVoiceInput
            PanelType.SETTINGS -> PanelToggleAction.OpenSettings
            else -> PanelToggleAction.ShowPanel(requestedPanel)
        }
    }

    fun syncPanelSelection(
        panel: PanelType?,
        updateSelection: (PanelType?) -> Unit,
    ) {
        updateSelection(panel)
        imeCoordinator.showPanel(panel)
    }
}

sealed interface PanelToggleAction {
    data object CloseCurrentPanel : PanelToggleAction
    data object OpenVoiceInput : PanelToggleAction
    data object OpenSettings : PanelToggleAction
    data class ShowPanel(val panel: PanelType) : PanelToggleAction
}
