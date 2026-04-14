package com.snowrealm.yukiboard.ui.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * 共用 UI 建構工具，供 SettingsActivity 與所有 Section Binder 使用。
 * 不持有 Activity 引用——只需要 Context。
 */
object SettingsUiHelper {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * 建立一張簡單卡片（標題 + 分隔線），呼叫者可再 addView 子項目。
     */
    fun simpleCard(context: Context, title: String): LinearLayout {
        val dp = { v: Int -> dp(context, v) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E2D4A.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }

            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTextColor(0xFF7FD8FF.toInt())
                setPadding(dp(4), 0, 0, dp(8))
            })

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { setMargins(0, 0, 0, dp(4)) }
                setBackgroundColor(0xFF2A3555.toInt())
            })
        }
    }

    /**
     * 建立群組卡片（標題 + 可選主開關 + 子項目）。
     */
    fun cardGroup(
        context: Context,
        title: String,
        masterValue: Boolean?,
        masterSetter: ((Boolean) -> Unit)?,
        childViews: MutableList<View>,
        builder: () -> Unit
    ): LinearLayout {
        val dp = { v: Int -> dp(context, v) }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E2D4A.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        titleRow.addView(TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(0xFF7FD8FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (masterValue != null && masterSetter != null) {
            @Suppress("UseSwitchCompatOrMaterialCode")
            titleRow.addView(Switch(context).apply {
                isChecked = masterValue
                setOnCheckedChangeListener { _, checked ->
                    masterSetter(checked)
                    childViews.forEach {
                        it.isEnabled = checked
                        it.alpha = if (checked) 1.0f else 0.4f
                    }
                }
            })
        }

        card.addView(titleRow)

        card.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { setMargins(0, 0, 0, dp(4)) }
            setBackgroundColor(0xFF2A3555.toInt())
        })

        builder()

        childViews.forEach { child ->
            card.addView(child)
            if (masterValue != null) {
                child.isEnabled = masterValue
                child.alpha = if (masterValue) 1.0f else 0.4f
            }
        }

        return card
    }

    /**
     * Switch 列：標題 + 副標題 + Switch。
     */
    @Suppress("UseSwitchCompatOrMaterialCode")
    fun switchRow(
        context: Context,
        title: String,
        subtitle: String,
        initialValue: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val dp = { v: Int -> dp(context, v) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))

            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textContainer.addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(0xFFE8F4FF.toInt())
            })
            textContainer.addView(TextView(context).apply {
                text = subtitle
                textSize = 11f
                setTextColor(0xFF6688AA.toInt())
            })
            addView(textContainer)

            addView(Switch(context).apply {
                isChecked = initialValue
                setOnCheckedChangeListener { _, checked -> onChanged(checked) }
            })
        }
    }

    /**
     * 帶背景色的按鈕。
     */
    fun styledButton(
        context: Context,
        label: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): Button {
        val dp = { v: Int -> dp(context, v) }
        return Button(context).apply {
            text = label
            setTextColor(textColor)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }
}
