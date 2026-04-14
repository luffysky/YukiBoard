package com.snowrealm.yukiboard.app.ime

import android.view.View
import android.widget.FrameLayout

/**
 * Sprint 2: 收斂 IME panel 容器的掛載與清理。
 *
 * 各 panel 的建立邏輯仍留在 service，這裡只處理 host 層責任。
 */
class ImePanelHost(
    private val panelContainer: FrameLayout,
    private val topPanelContainer: FrameLayout,
) {

    fun clearContainers() {
        panelContainer.removeAllViews()
        panelContainer.visibility = View.GONE
        topPanelContainer.removeAllViews()
        topPanelContainer.visibility = View.GONE
    }

    fun attachPanel(
        panelView: View,
        isInputPanel: Boolean,
        onOverlayPanelShown: () -> Unit,
        onOverlayPanelAttached: () -> Unit,
    ) {
        if (isInputPanel) {
            topPanelContainer.addView(panelView)
            topPanelContainer.visibility = View.VISIBLE
            return
        }

        panelContainer.addView(panelView)
        panelContainer.visibility = View.VISIBLE
        onOverlayPanelShown()
        onOverlayPanelAttached()
    }

    fun hidePanels(onOverlayPanelHidden: () -> Unit) {
        clearContainers()
        onOverlayPanelHidden()
    }
}
