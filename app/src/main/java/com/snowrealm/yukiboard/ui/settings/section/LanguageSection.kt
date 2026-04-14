package com.snowrealm.yukiboard.ui.settings.section

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import java.util.Locale

/**
 * 語言選擇 section（繁體中文/English RadioGroup，切換後重啟 Activity）。
 *
 * 從 SettingsActivity buildLanguageCard() 抽出。
 */
class LanguageSection {

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
            text = "語言 / Language"
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

        val currentLang = context.resources.configuration.locales[0].language
        val langs = listOf(
            Triple(10, "繁體中文", "zh"),
            Triple(11, "English", "en")
        )

        var isInitializing = true
        langs.forEach { (id, label, lang) ->
            group.addView(RadioButton(context).apply {
                this.id = id
                text = label
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = currentLang == lang
            })
        }
        isInitializing = false
        group.setOnCheckedChangeListener { _, checkedId ->
            if (isInitializing) return@setOnCheckedChangeListener
            langs.firstOrNull { it.first == checkedId }?.let { (_, _, lang) ->
                val activity = context as? Activity ?: return@setOnCheckedChangeListener
                val locale = Locale(lang)
                Locale.setDefault(locale)
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                val restart = activity.intent
                activity.finish()
                activity.startActivity(restart)
            }
        }

        card.addView(group)
        return card
    }
}
