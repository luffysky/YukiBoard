package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 雪凜行為 section（微吐槽 / 情緒冷靜期 / 心情日記 / 天氣提醒 / 深夜關心）。
 */
class YukiBehaviorSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {
    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("雪凜行為"), prefs.yukiSarcasticComments, null, children) {
            children += SettingsUiHelper.switchRow(context, tr("微吐槽"), tr("雪凜偶爾的有趣短評"), prefs.yukiSarcasticComments) { prefs.yukiSarcasticComments = it }
            children += SettingsUiHelper.switchRow(context, tr("情緒冷靜期"), tr("偵測激烈文字時暫停提醒"), prefs.yukiEmotionalCooldown) { prefs.yukiEmotionalCooldown = it }
            children += SettingsUiHelper.switchRow(context, tr("心情日記被動偵測"), tr("自動分析打字情緒"), prefs.yukiMoodDiaryPassive) { prefs.yukiMoodDiaryPassive = it }
            children += SettingsUiHelper.switchRow(context, tr("天氣主動提醒"), tr("天氣變化時雪凜提醒"), prefs.yukiWeatherAlert) { prefs.yukiWeatherAlert = it }
            children += SettingsUiHelper.switchRow(context, tr("深夜關心"), tr("深夜打字時的溫馨提醒"), prefs.yukiLateNightComment) { prefs.yukiLateNightComment = it }
        }
    }
}
