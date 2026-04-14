package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 智慧輸入 section（糾錯 / 學詞 / 預測 / 習慣追蹤 + 候選字顏色說明）。
 */
class SmartInputSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {
    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("智慧輸入"), null, null, children) {
            children += SettingsUiHelper.switchRow(context, tr("智慧糾錯"), tr("AI 偵測打錯字並自動修正"), prefs.smartCorrection) { prefs.smartCorrection = it }
            children += SettingsUiHelper.switchRow(context, tr("自動學詞"), tr("常用字彙自動加入字典"), prefs.adaptiveDict) { prefs.adaptiveDict = it }
            children += SettingsUiHelper.switchRow(context, tr("語句預測"), tr("預測下一個可能的詞或片語"), prefs.phrasePredict) { prefs.phrasePredict = it }
            children += SettingsUiHelper.switchRow(context, tr("習慣追蹤"), tr("學習打字習慣提升預測精準度"), prefs.habitTracking) { prefs.habitTracking = it }
            children += buildCandidateColorLegend(context)
        }
    }

    private fun buildCandidateColorLegend(context: Context): View {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
        }
        outer.addView(TextView(context).apply {
            text = tr("候選字顏色說明")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(0, 0, 0, dp(context, 6))
        })
        val legends = listOf(
            0xFFCCCCCC.toInt() to "白/灰 — 字典匹配（一般候選）",
            0xFFFF9933.toInt() to "橙色 — 智慧糾錯（拼字修正）",
            0xFF66CC88.toInt() to "綠色 — 自適應學習（常用詞）",
            0xFF66AAFF.toInt() to "藍色 — 語句預測（bigram/trigram）",
            0xFFFFD700.toInt() to "金色 — 使用者詞庫（自訂/學習詞）",
            0xFFBB88FF.toInt() to "紫色 — 整句預測",
            0xFFE8B84D.toInt() to "金色 ✨ — AI 候選（Claw AI 生成）"
        )
        for ((color, desc) in legends) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(context, 2), 0, dp(context, 2))
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 10), dp(context, 10)).apply {
                    setMargins(0, 0, dp(context, 8), 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            })
            row.addView(TextView(context).apply {
                text = desc
                textSize = 11f
                setTextColor(0xFFAABBCC.toInt())
            })
            outer.addView(row)
        }
        return outer
    }
}
