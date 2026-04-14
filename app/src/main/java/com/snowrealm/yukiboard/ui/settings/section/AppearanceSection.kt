package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp

/**
 * 外觀模式 section（跟隨系統/深色/淺色 RadioGroup）。
 *
 * 從 SettingsActivity buildAppearanceCard() 抽出。
 */
class AppearanceSection {

    fun buildCard(context: Context): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1E2D4A.toInt())
                cornerRadius = dp(context, 12).toFloat()
            }
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(context, 12)) }
        }
        card.addView(TextView(context).apply {
            text = "外觀模式 / Appearance"
            textSize = 16f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 4), 0, 0, dp(context, 8))
        })
        card.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
            ).apply { setMargins(0, 0, 0, dp(context, 4)) }
            setBackgroundColor(0xFF2A3555.toInt())
        })

        val group = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(context, 8), dp(context, 4), 0, dp(context, 8))
        }

        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val modes = listOf(
            Triple(1, "跟隨系統 / Follow System", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
            Triple(2, "深色 / Dark", AppCompatDelegate.MODE_NIGHT_YES),
            Triple(3, "淺色 / Light", AppCompatDelegate.MODE_NIGHT_NO)
        )

        modes.forEach { (id, label, mode) ->
            group.addView(RadioButton(context).apply {
                this.id = id
                text = label
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = currentMode == mode
            })
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            modes.firstOrNull { it.first == checkedId }?.let {
                AppCompatDelegate.setDefaultNightMode(it.third)
            }
        }

        card.addView(group)
        return card
    }
}
