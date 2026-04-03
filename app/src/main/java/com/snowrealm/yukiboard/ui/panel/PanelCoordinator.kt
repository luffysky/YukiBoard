package com.snowrealm.yukiboard.ui.panel

import com.snowrealm.yukiboard.ui.floating.FloatingPanelManager

/**
 * 統一協調內嵌面板與懸浮面板，確保同功能不會在兩處同時開啟。
 *
 * 規則：
 * - 同一個 PanelType 只能在內嵌 OR 懸浮其中一處開啟
 * - 懸浮面板開啟時 → 自動關閉對應的內嵌面板
 * - 內嵌面板開啟時 → 自動關閉對應的懸浮面板
 * - 懸浮面板內部複用內嵌面板的 View（不重寫 UI）
 */
class PanelCoordinator {

    var onCloseEmbeddedPanel: ((PanelType) -> Unit)? = null
    var floatingPanelManager: FloatingPanelManager? = null

    private var activeEmbeddedPanel: PanelType? = null

    /** 當內嵌面板開啟時呼叫 */
    fun onEmbeddedPanelOpened(type: PanelType) {
        activeEmbeddedPanel = type
        // 關閉同功能的懸浮面板
        floatingPanelManager?.hidePanel(type.name.lowercase())
    }

    /** 當內嵌面板關閉時呼叫 */
    fun onEmbeddedPanelClosed(type: PanelType) {
        if (activeEmbeddedPanel == type) {
            activeEmbeddedPanel = null
        }
    }

    /** 當懸浮面板要開啟時呼叫，回傳 true 表示可以開啟 */
    fun onFloatingPanelOpening(type: PanelType): Boolean {
        // 關閉同功能的內嵌面板
        if (activeEmbeddedPanel == type) {
            onCloseEmbeddedPanel?.invoke(type)
            activeEmbeddedPanel = null
        }
        return true
    }

    /** 當懸浮面板關閉時呼叫 */
    fun onFloatingPanelClosed(type: PanelType) {
        // 不需要做什麼，只是通知
    }

    fun getActiveEmbeddedPanel(): PanelType? = activeEmbeddedPanel
}
