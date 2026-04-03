package com.snowrealm.yukiboard.ui.floating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * Claw AI 結果懸浮面板 — typewriter 效果 + 版本切換 + 複製/插入。
 */
class ClawResultFloatingPanel(
    context: Context
) : FloatingPanel(context, FloatingPanelConfig(
    defaultWidthDp = 300,
    minWidthDp = 240,
    maxWidthDp = 400,
    defaultHeightDp = 220,
    minHeightDp = 160
)) {
    override val panelId = "claw_result"
    override val panelTitle = "🐾 Claw AI"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var skillNameText: TextView? = null
    private var resultText: TextView? = null
    private var pageIndicator: TextView? = null
    private var prevButton: TextView? = null
    private var nextButton: TextView? = null

    private var results: List<String> = emptyList()
    private var currentIndex: Int = 0

    var onInsertToInput: ((String) -> Unit)? = null

    override fun createContentView(inflater: LayoutInflater): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }

        // Skill 名稱
        skillNameText = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(skillNameText)

        // 結果文字區
        resultText = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            minHeight = dp(80)
            background = GradientDrawable().apply {
                setColor(0xFF1A2540.toInt())
                cornerRadius = dp(8).toFloat()
            }
        }
        root.addView(resultText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        // 版本切換列
        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        prevButton = makeNavButton("◀") { switchPage(-1) }
        pageIndicator = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF7FA8CC.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nextButton = makeNavButton("▶") { switchPage(1) }

        navRow.addView(prevButton)
        navRow.addView(pageIndicator)
        navRow.addView(nextButton)
        root.addView(navRow)

        // 操作按鈕列
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        actionRow.addView(makeActionButton("📋 複製") { copyCurrentResult() })
        actionRow.addView(makeActionButton("✓ 插入輸入框") { insertCurrentResult() })
        root.addView(actionRow)

        return root
    }

    /** 外部呼叫：顯示 AI 結果 */
    fun showResult(skillName: String, resultList: List<String>) {
        results = resultList
        currentIndex = 0
        skillNameText?.text = skillName
        updatePage()

        if (!isShowing) show()

        // Typewriter 動畫
        playTypewriterEffect(resultList.firstOrNull() ?: "")
    }

    override fun onDismissed() {
        scope.cancel()
    }

    private fun switchPage(delta: Int) {
        if (results.isEmpty()) return
        val newIndex = currentIndex + delta
        if (newIndex < 0 || newIndex >= results.size) return
        currentIndex = newIndex
        updatePage()
        playTypewriterEffect(results[currentIndex])
    }

    private fun updatePage() {
        if (results.isEmpty()) {
            pageIndicator?.text = "無結果"
            prevButton?.alpha = 0.3f
            nextButton?.alpha = 0.3f
            return
        }
        pageIndicator?.text = "${currentIndex + 1} / ${results.size}"
        prevButton?.alpha = if (currentIndex > 0) 1f else 0.3f
        nextButton?.alpha = if (currentIndex < results.size - 1) 1f else 0.3f
    }

    private fun playTypewriterEffect(text: String) {
        scope.coroutineContext.cancelChildren()
        scope.launch {
            resultText?.text = ""
            for (i in text.indices) {
                resultText?.text = text.substring(0, i + 1)
                delay(25)
            }
        }
    }

    private fun copyCurrentResult() {
        val text = results.getOrNull(currentIndex) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Claw AI", text))
        Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
    }

    private fun insertCurrentResult() {
        val text = results.getOrNull(currentIndex) ?: return
        onInsertToInput?.invoke(text)
    }

    private fun makeNavButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { onClick() }
        }
    }

    private fun makeActionButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                setColor(0xFF4A90E2.toInt())
                cornerRadius = dp(6).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8); marginEnd = dp(8) }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int = dpToPx(value)
}
