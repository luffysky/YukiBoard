package com.snowrealm.yukiboard.ui.floating

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.ui.panel.StickerPanelView

/**
 * 貼圖懸浮面板 — 複用 [StickerPanelView]，不重寫 UI。
 *
 * 新增功能：
 * - 分頁 Tab（免費 / 角色 / 節日 / 全部）
 * - 稀有度角標由 StickerPanelView 內部處理
 */
class StickerFloatingPanel(
    context: Context
) : FloatingPanel(context, FloatingPanelConfig(
    defaultWidthDp = 320,
    minWidthDp = 260,
    maxWidthDp = 420,
    defaultHeightDp = 300,
    minHeightDp = 200
)) {
    override val panelId = "sticker"
    override val panelTitle = "🖼 貼圖"

    private var stickerView: StickerPanelView? = null
    var onStickerSelected: ((String) -> Unit)? = null

    private val tabs = listOf(
        TabItem("all", "全部"),
        TabItem("free", "免費"),
        TabItem("character", "角色"),
        TabItem("seasonal", "節日")
    )
    private var activeTab = "all"

    override fun createContentView(inflater: LayoutInflater): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Tab 列
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        tabs.forEach { tab ->
            tabRow.addView(buildTabButton(tab, tabRow))
        }
        tabScroll.addView(tabRow)
        root.addView(tabScroll)

        // 複用 StickerPanelView
        val panel = StickerPanelView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
            )
            onStickerSelected = { url ->
                this@StickerFloatingPanel.onStickerSelected?.invoke(url)
            }
        }
        stickerView = panel
        root.addView(panel)

        return root
    }

    private fun buildTabButton(tab: TabItem, parent: LinearLayout): TextView {
        return TextView(context).apply {
            text = tab.label
            textSize = 12f
            val isActive = tab.id == activeTab
            setTextColor(if (isActive) 0xFFFFFFFF.toInt() else 0xFF7FA8CC.toInt())
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            background = if (isActive) {
                GradientDrawable().apply {
                    setColor(0xFF4A90E2.toInt())
                    cornerRadius = dp(12).toFloat()
                }
            } else null
            setPadding(dp(12), dp(4), dp(12), dp(4))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            layoutParams = lp
            setOnClickListener {
                activeTab = tab.id
                // Rebuild tabs
                parent.removeAllViews()
                tabs.forEach { t -> parent.addView(buildTabButton(t, parent)) }
                filterStickers(tab.id)
            }
        }
    }

    private fun filterStickers(category: String) {
        stickerView?.filterCategory = category
    }

    private fun dp(value: Int): Int = dpToPx(value)

    private data class TabItem(val id: String, val label: String)
}
