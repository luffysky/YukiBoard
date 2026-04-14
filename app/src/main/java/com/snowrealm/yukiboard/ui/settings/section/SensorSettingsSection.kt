package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 感測器 section（搖手機復原 / 陀螺儀游標 + 靈敏度 / App 自動切換）。
 */
class SensorSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {
    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("感測器"), null, null, children) {
            children += SettingsUiHelper.switchRow(context, tr("搖手機復原"), tr("搖晃手機觸發 Ctrl+Z"), prefs.shakeToUndoEnabled) { prefs.shakeToUndoEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("陀螺儀游標"), tr("長按空白鍵 + 傾斜控制游標"), prefs.gyroscopeCursorEnabled) { prefs.gyroscopeCursorEnabled = it }

            // 靈敏度滑桿
            val sensitivityLabel = TextView(context).apply {
                text = tr("靈敏度: %.1f").format(prefs.gyroscopeSensitivity)
                textSize = 12f
                setTextColor(0xFF7FD8FF.toInt())
                setPadding(dp(context, 12), dp(context, 4), 0, dp(context, 4))
            }
            children += sensitivityLabel
            children += SeekBar(context).apply {
                max = 25
                progress = ((prefs.gyroscopeSensitivity - 0.5f) * 10).toInt()
                setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = 0.5f + p * 0.1f
                        prefs.gyroscopeSensitivity = v
                        sensitivityLabel.text = tr("靈敏度: %.1f").format(v)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }

            children += SettingsUiHelper.switchRow(context, tr("App 自動切換模式"), tr("偵測前景 App 自動切換模式"), prefs.appAutoDetectEnabled) { prefs.appAutoDetectEnabled = it }
        }
    }
}
