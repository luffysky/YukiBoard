package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.snowrealm.yukiboard.input.langpack.LanguagePack
import com.snowrealm.yukiboard.input.langpack.LanguagePackManager
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import kotlinx.coroutines.launch

/**
 * 語言套件管理 section（已啟用輸入法 + 可下載語言包）。
 *
 * 從 SettingsActivity buildLanguagePackCard() 抽出。
 */
class LanguagePackSection(
    private val tr: (String) -> String,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    fun buildCard(context: Context): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
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
            text = tr("語言套件 / Language Packs")
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

        val langPackMgr = LanguagePackManager.getInstance(context)
        val allPacks = LanguagePack.ALL
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 8))
        }

        fun refreshContent() {
            contentLayout.removeAllViews()

            // 已啟用的輸入法
            contentLayout.addView(TextView(context).apply {
                text = tr("已啟用的輸入法")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 13f
                setPadding(dp(context, 8), dp(context, 8), 0, dp(context, 4))
            })

            val enabledIds = langPackMgr.getEnabledInputMethods().map { it.id }.toSet()
            for (pack in allPacks) {
                if (!langPackMgr.isInstalled(pack.id)) continue

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
                }

                val cb = CheckBox(context).apply {
                    isChecked = pack.id in enabledIds
                    buttonTintList = ColorStateList.valueOf(0xFF64B5F6.toInt())
                    setOnCheckedChangeListener { _, checked ->
                        langPackMgr.toggleInputMethod(pack.id, checked)
                    }
                }
                row.addView(cb)

                row.addView(TextView(context).apply {
                    text = "${pack.icon} ${pack.displayName}"
                    setTextColor(0xFFE0E8F0.toInt())
                    textSize = 14f
                    setPadding(dp(context, 8), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                if (!langPackMgr.isBuiltin(pack.id)) {
                    row.addView(TextView(context).apply {
                        text = tr("移除")
                        setTextColor(0xFFEF5350.toInt())
                        textSize = 12f
                        setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
                        setOnClickListener {
                            langPackMgr.uninstall(pack.id)
                            langPackMgr.toggleInputMethod(pack.id, false)
                            refreshContent()
                        }
                    })
                }

                contentLayout.addView(row)
            }

            // 可下載的語言套件
            val available = langPackMgr.getAvailablePacks()
            if (available.isNotEmpty()) {
                contentLayout.addView(TextView(context).apply {
                    text = tr("可下載的語言套件")
                    setTextColor(0xFF90CAF9.toInt())
                    textSize = 13f
                    setPadding(dp(context, 8), dp(context, 16), 0, dp(context, 4))
                })

                for (pack in available) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
                    }

                    row.addView(TextView(context).apply {
                        text = "${pack.icon} ${pack.displayName} (${pack.nativeName})"
                        setTextColor(0xFFB0BEC5.toInt())
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })

                    row.addView(TextView(context).apply {
                        text = "${pack.dictWordCount / 1000}K 詞"
                        setTextColor(0xFF78909C.toInt())
                        textSize = 11f
                        setPadding(0, 0, dp(context, 8), 0)
                    })

                    val dlBtn = TextView(context).apply {
                        text = tr("下載")
                        setTextColor(0xFF42A5F5.toInt())
                        textSize = 13f
                        setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
                        background = GradientDrawable().apply {
                            setStroke(dp(context, 1), 0xFF42A5F5.toInt())
                            cornerRadius = dp(context, 6).toFloat()
                        }
                        setOnClickListener { btn ->
                            btn.isEnabled = false
                            (btn as TextView).text = tr("下載中…")
                            lifecycleScope.launch {
                                val result = langPackMgr.downloadAndInstall(pack.id)
                                if (result.success) {
                                    langPackMgr.toggleInputMethod(pack.id, true)
                                }
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                refreshContent()
                            }
                        }
                    }
                    row.addView(dlBtn)

                    contentLayout.addView(row)
                }
            }
        }

        refreshContent()
        card.addView(contentLayout)
        return card
    }
}
