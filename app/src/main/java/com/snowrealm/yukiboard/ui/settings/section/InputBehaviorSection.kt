package com.snowrealm.yukiboard.ui.settings.section

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 輸入行為 section（中英切換 / 滑動 / 自動更正 / RIME / 詞庫匯出入 /
 * 學習開關 / Snippet / 私房話 / 語氣 / 懸浮 / 遠端等）。
 *
 * 從 SettingsActivity section 6（原 lines 531-636）抽出。
 * 語音引擎設定已獨立為 SpeechSettingsSection。
 */
class InputBehaviorSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String,
    private val onExportDict: () -> Unit,
    private val onImportDict: () -> Unit,
) {

    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("輸入行為"), null, null, children) {
            children += SettingsUiHelper.switchRow(context, tr("智慧中英切換"), tr("自動判斷注音/英文意圖（免手動切換）"), prefs.autoInputMode) { prefs.autoInputMode = it }
            children += SettingsUiHelper.switchRow(context, tr("滑動輸入"), tr("滑動手勢輸入"), prefs.swipeInput) { prefs.swipeInput = it }
            children += SettingsUiHelper.switchRow(context, tr("滑動軌跡特效"), tr("滑動時顯示紫色光暈路徑"), prefs.swipeTrailEnabled) { prefs.swipeTrailEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("顯示觸控面積"), tr("打字時顯示手指接觸範圍，方便確認是否按到"), prefs.showTouchArea) { prefs.showTouchArea = it }
            children += SettingsUiHelper.switchRow(context, tr("自動更正"), tr("英文自動更正"), prefs.autoCorrect) { prefs.autoCorrect = it }
            children += SettingsUiHelper.switchRow(context, tr("RIME 詞庫"), tr("啟用 RIME 大型注音詞庫（更準確的候選字）"), prefs.rimeDictEnabled) { prefs.rimeDictEnabled = it }

            // 詞庫匯出/匯入
            children += buildDictButtons(context)

            children += SettingsUiHelper.switchRow(context, tr("滑動自我學習"), tr("記住你選過的滑動候選，越用越準"), prefs.swipeSelfLearn) { prefs.swipeSelfLearn = it }
            children += SettingsUiHelper.switchRow(context, tr("糾錯自我學習"), tr("記住你接受/拒絕的校正，越改越聰明"), prefs.correctionSelfLearn) { prefs.correctionSelfLearn = it }
            children += SettingsUiHelper.switchRow(context, tr("Snippet 展開"), tr("開發者模式：縮寫 + Tab 展開"), prefs.snippetEnabled) { prefs.snippetEnabled = it }

            children += SettingsUiHelper.switchRow(context, tr("私房話模式"), tr("AES-256 加密訊息（Enter 送出時加密）"), prefs.secretMessageMode) { prefs.secretMessageMode = it }
            children += buildPasswordRow(context)

            children += SettingsUiHelper.switchRow(context, tr("語氣偵測"), tr("偵測打字語氣"), prefs.toneDetection) { prefs.toneDetection = it }
            children += SettingsUiHelper.switchRow(context, tr("懸浮面板"), tr("啟用懸浮面板功能"), prefs.floatingPanelEnabled) { prefs.floatingPanelEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("遠端鍵盤"), tr("藍牙/WiFi 遠端控制"), prefs.remoteKeyboardEnabled) { prefs.remoteKeyboardEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("語音後處理"), tr("AI 自動清除口語贅詞（嗯、那個…）"), prefs.voicePostProcessEnabled) { prefs.voicePostProcessEnabled = it }
        }
    }

    private fun buildDictButtons(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4))
            addView(SettingsUiHelper.styledButton(context, tr("匯出詞庫"), 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
                onExportDict()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(context, 40), 1f).apply { setMargins(0, 0, dp(context, 4), 0) }
            })
            addView(SettingsUiHelper.styledButton(context, tr("匯入詞庫"), 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
                onImportDict()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(context, 40), 1f).apply { setMargins(dp(context, 4), 0, 0, 0) }
            })
        }
    }

    private fun buildPasswordRow(context: Context): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 4))
        }
        row.addView(TextView(context).apply {
            text = tr("悄悄話密碼")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        })
        val pwdBtn = TextView(context).apply {
            text = if (prefs.secretMessagePassword.isEmpty()) "未設定（使用預設金鑰）" else "已設定 ●●●●"
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = GradientDrawable().apply {
                setColor(0xFF1A2A3A.toInt())
                cornerRadius = dp(context, 6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }
        pwdBtn.setOnClickListener {
            val input = EditText(context).apply {
                hint = "輸入新密碼（留空=使用預設金鑰）"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(prefs.secretMessagePassword)
                setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            }
            AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(tr("設定悄悄話密碼"))
                .setMessage("雙方需要使用相同密碼才能加解密。\n留空則使用舊版預設金鑰。")
                .setView(input)
                .setPositiveButton(tr("確定")) { _, _ ->
                    prefs.secretMessagePassword = input.text.toString()
                    (it as TextView).text = if (prefs.secretMessagePassword.isEmpty()) "未設定（使用預設金鑰）" else "已設定 ●●●●"
                }
                .setNegativeButton(tr("取消"), null)
                .show()
        }
        row.addView(pwdBtn)
        return row
    }
}
