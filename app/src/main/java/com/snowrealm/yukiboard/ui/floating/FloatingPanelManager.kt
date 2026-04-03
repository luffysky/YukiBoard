package com.snowrealm.yukiboard.ui.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 懸浮面板管理器。
 *
 * 管理所有懸浮面板的生命週期，確保開關設定和權限檢查。
 */
class FloatingPanelManager(private val context: Context) {

    companion object {
        const val PANEL_WEATHER = "weather"
        const val PANEL_GAME = "game"
        const val PANEL_CLAW_RESULT = "claw_result"
        const val PANEL_STICKER = "sticker"
    }

    private val panels = mutableMapOf<String, FloatingPanel>()
    private val prefs = YukiPreferences(context)

    fun registerPanel(panel: FloatingPanel) {
        panels[panel.panelId] = panel
    }

    fun showPanel(panelId: String) {
        if (!checkOverlayPermission()) return
        if (!checkPrefEnabled(panelId)) return
        panels[panelId]?.show()
    }

    fun hidePanel(panelId: String) {
        panels[panelId]?.hide()
    }

    fun dismissPanel(panelId: String) {
        panels[panelId]?.dismiss()
    }

    fun togglePanel(panelId: String) {
        val panel = panels[panelId] ?: return
        if (panel.isShowing) {
            panel.hide()
        } else {
            showPanel(panelId)
        }
    }

    fun dismissAll() {
        panels.values.forEach { if (it.isShowing) it.dismiss() }
    }

    fun isPanelShowing(panelId: String): Boolean {
        return panels[panelId]?.isShowing == true
    }

    /**
     * 檢查 SYSTEM_ALERT_WINDOW 權限。
     * 無權限時引導使用者到設定頁。
     */
    fun checkOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(context)) return true

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return false
    }

    /**
     * 根據面板 ID 檢查對應的設定開關。
     */
    private fun checkPrefEnabled(panelId: String): Boolean {
        if (!prefs.floatingPanelEnabled) return false
        return when (panelId) {
            PANEL_WEATHER -> prefs.weatherPanelEnabled
            PANEL_GAME -> prefs.gamesEnabled
            PANEL_STICKER -> prefs.stickerPanelEnabled
            PANEL_CLAW_RESULT -> prefs.aiEnabled
            else -> true
        }
    }
}
