package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 遊戲 section（主開關 + 背景音樂 + 音效）。
 */
class GameSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {
    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("遊戲"), prefs.gamesEnabled, { prefs.gamesEnabled = it }, children) {
            children += SettingsUiHelper.switchRow(context, tr("背景音樂"), tr("遊戲背景音樂"), prefs.gameBackgroundMusic) { prefs.gameBackgroundMusic = it }
            children += SettingsUiHelper.switchRow(context, tr("音效"), tr("遊戲音效"), prefs.gameSoundEffects) { prefs.gameSoundEffects = it }
        }
    }
}
