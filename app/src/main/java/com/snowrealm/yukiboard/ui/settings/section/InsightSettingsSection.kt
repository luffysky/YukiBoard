package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * Insight 整合卡片（訂閱等級 + 情境問卷 / 主題同步 / 設定備份）。
 *
 * 從 SettingsActivity.onCreateInternal() 的 Insight 整合區塊（原 lines 751-776）抽出。
 */
class InsightSettingsSection(
    private val auth: SnowRealmAuthClient,
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {

    fun buildCard(context: Context): LinearLayout {
        val insightChildren = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("Insight 整合"), null, null, insightChildren) {
            // Plan badge
            if (auth.isLoggedIn) {
                val user = auth.currentUser
                val planText = when (user?.plan) {
                    SnowRealmAuthClient.UserPlan.PLUS -> "Plus \u2B50"
                    SnowRealmAuthClient.UserPlan.PRO -> "Pro \uD83D\uDD25"
                    SnowRealmAuthClient.UserPlan.ENTERPRISE -> "Enterprise \uD83C\uDFE2"
                    else -> "Free"
                }
                insightChildren += TextView(context).apply {
                    text = tr("訂閱等級：$planText")
                    textSize = 14f
                    setTextColor(0xFF40E0D0.toInt())
                    setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
                }
            }
            insightChildren += SettingsUiHelper.switchRow(context, tr("情境問卷"), tr("打字時觸發問卷，回答得 Z 幣"), prefs.insightSurveyEnabled) { prefs.insightSurveyEnabled = it }
            insightChildren += SettingsUiHelper.switchRow(context, tr("主題雲端同步"), tr("自動同步在 Insight 購買的主題"), prefs.insightThemeSyncEnabled) { prefs.insightThemeSyncEnabled = it }
            insightChildren += SettingsUiHelper.switchRow(context, tr("設定備份"), tr("登入後自動備份鍵盤設定"), prefs.insightSettingsBackup) { prefs.insightSettingsBackup = it }
        }
    }
}
