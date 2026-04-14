package com.snowrealm.yukiboard.ui.settings.section

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.input.snippet.SnippetManager
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp

/**
 * Snippet 管理 section（自訂 snippet CRUD + 內建列表）。
 *
 * 從 SettingsActivity buildSnippetCard() 抽出。
 */
class SnippetSettingsSection(
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
            text = "Snippet 管理（開發者模式）"
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

        val snippetMgr = SnippetManager(context)
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun refreshSnippetList() {
            listContainer.removeAllViews()

            val custom = snippetMgr.getCustomSnippets()
            if (custom.isNotEmpty()) {
                listContainer.addView(TextView(context).apply {
                    text = tr("自訂 Snippet")
                    textSize = 13f
                    setTextColor(0xFF7FD8FF.toInt())
                    setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 4))
                })
                for ((abbr, expansion) in custom) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(TextView(context).apply {
                        text = "$abbr → ${expansion.take(40)}"
                        setTextColor(0xFFE8F4FF.toInt())
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(TextView(context).apply {
                        text = "✕"
                        setTextColor(0xFFFF6B6B.toInt())
                        textSize = 16f
                        setPadding(dp(context, 8), 0, dp(context, 4), 0)
                        setOnClickListener {
                            snippetMgr.removeCustomSnippet(abbr)
                            refreshSnippetList()
                        }
                    })
                    listContainer.addView(row)
                }
            }

            // 內建 snippet（摺疊）
            val builtIn = snippetMgr.getBuiltInAbbreviations()
            val builtInContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val toggleLabel = TextView(context).apply {
                text = tr("▸ 內建 Snippet (${builtIn.size} 個) — 點擊展開")
                textSize = 13f
                setTextColor(0xFF6688AA.toInt())
                setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 4))
                setOnClickListener {
                    if (builtInContainer.visibility == View.GONE) {
                        builtInContainer.visibility = View.VISIBLE
                        text = tr("▾ 內建 Snippet (${builtIn.size} 個) — 點擊收合")
                    } else {
                        builtInContainer.visibility = View.GONE
                        text = tr("▸ 內建 Snippet (${builtIn.size} 個) — 點擊展開")
                    }
                }
            }
            for (abbr in builtIn) {
                val result = snippetMgr.expand(abbr)
                builtInContainer.addView(TextView(context).apply {
                    text = "$abbr → ${result?.expansion?.replace("\n", "↵")?.take(50) ?: ""}"
                    setTextColor(0xFF8899AA.toInt())
                    textSize = 12f
                    setPadding(dp(context, 12), dp(context, 2), dp(context, 4), dp(context, 2))
                })
            }
            listContainer.addView(toggleLabel)
            listContainer.addView(builtInContainer)
        }

        refreshSnippetList()

        // 新增按鈕
        card.addView(Button(context).apply {
            text = tr("+ 新增 Snippet")
            setTextColor(0xFF7FD8FF.toInt())
            setBackgroundColor(0xFF1A3A5C.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(context, 8), 0, dp(context, 4)) }
            setOnClickListener {
                val dialogView = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 8))
                }
                val abbrInput = EditText(context).apply {
                    hint = "縮寫（例如：log）"
                    setHintTextColor(0x88FFFFFF.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF1A3A5C.toInt())
                    setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
                }
                val expansionInput = EditText(context).apply {
                    hint = "展開文字（例如：console.log()）"
                    setHintTextColor(0x88FFFFFF.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF1A3A5C.toInt())
                    setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
                    minLines = 2
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(context, 8) }
                }
                dialogView.addView(abbrInput)
                dialogView.addView(expansionInput)

                AlertDialog.Builder(context)
                    .setTitle(tr("新增 Snippet"))
                    .setView(dialogView)
                    .setPositiveButton(tr("新增")) { _, _ ->
                        val abbr = abbrInput.text.toString().trim()
                        val expansion = expansionInput.text.toString()
                        if (abbr.isNotBlank() && expansion.isNotBlank()) {
                            snippetMgr.addCustomSnippet(abbr, expansion)
                            refreshSnippetList()
                        }
                    }
                    .setNegativeButton(tr("取消"), null)
                    .show()
            }
        })

        card.addView(listContainer)
        return card
    }
}
