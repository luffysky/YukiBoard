package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 輸入法選擇 section（啟用/停用輸入法 checkbox 列表）。
 *
 * 從 SettingsActivity buildInputMethodCard() 抽出。
 */
class InputMethodSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
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
            text = "輸入法選擇"
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

        val allModes = listOf(
            InputMode.ENGLISH,
            InputMode.BOPOMOFO,
            InputMode.PINYIN,
            InputMode.CANGJIE,
            InputMode.SPEED_CANGJIE,
            InputMode.JAPANESE,
            InputMode.KOREAN,
        )

        val saved = prefs.enabledInputModes
        val enabledSet = if (saved.isBlank()) {
            allModes.map { it.name }.toMutableSet()
        } else {
            saved.split(",").filter { it.isNotBlank() }.toMutableSet()
        }

        card.addView(TextView(context).apply {
            text = tr("勾選要啟用的輸入法（切換語言時循環）")
            textSize = 12f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 8))
        })

        for (mode in allModes) {
            val cb = CheckBox(context).apply {
                text = "${mode.shortLabel}  ${mode.displayName}"
                setTextColor(0xFFE8F4FF.toInt())
                textSize = 14f
                isChecked = mode.name in enabledSet
                setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabledSet.add(mode.name) else enabledSet.remove(mode.name)
                    if (enabledSet.isEmpty()) {
                        enabledSet.add(InputMode.ENGLISH.name)
                        isChecked = true
                        Toast.makeText(context, "至少須啟用一個輸入法", Toast.LENGTH_SHORT).show()
                    }
                    prefs.enabledInputModes = enabledSet.joinToString(",")
                }
            }
            card.addView(cb)
        }

        return card
    }
}
