package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 剪貼簿 section（加密 / 敏感偵測 / 跨裝置同步）。
 */
class ClipboardSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {
    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("剪貼簿"), null, null, children) {
            children += SettingsUiHelper.switchRow(context, tr("加密功能"), tr("允許加密剪貼簿項目（AES-256）"), prefs.clipboardEncryptionEnabled) { prefs.clipboardEncryptionEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("敏感偵測"), tr("自動偵測信用卡、身分證等敏感內容"), prefs.clipboardAutoDetectSensitive) { prefs.clipboardAutoDetectSensitive = it }
            children += SettingsUiHelper.switchRow(context, tr("跨裝置同步"), tr("透過 Insight 同步剪貼簿（需登入）"), prefs.clipboardSyncEnabled) { prefs.clipboardSyncEnabled = it }
        }
    }
}
