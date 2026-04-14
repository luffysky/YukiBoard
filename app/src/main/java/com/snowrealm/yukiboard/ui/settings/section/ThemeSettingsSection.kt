package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.snowrealm.yukiboard.theme.ThemeManager
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp

/**
 * 主題選擇 section（RadioGroup 主題列表）。
 *
 * 從 SettingsActivity buildThemeCard() 抽出。
 */
class ThemeSettingsSection(
    private val themeManager: ThemeManager
) {

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
            text = "主題 / Theme"
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

        val themeGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(context, 8), dp(context, 4), 0, dp(context, 8))
        }

        val themes = themeManager.availableThemes
        themes.forEach { theme ->
            themeGroup.addView(RadioButton(context).apply {
                id = theme.id.hashCode()
                text = theme.displayName
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = theme.id == themeManager.currentTheme.id
            })
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            themes.firstOrNull { it.id.hashCode() == checkedId }?.let {
                themeManager.currentTheme = it
            }
        }
        card.addView(themeGroup)
        return card
    }
}
