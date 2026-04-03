package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 鍵盤上方工具列 — 水平排列面板切換按鈕。
 *
 * 圖示列：語音 | 翻譯 | GIF | 表情 | 顔文字 | 貼圖 | 剪貼簿 | 主題 | 設定
 */
class ToolbarView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val barHeight = (36 * density).toInt()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panels = PanelType.entries.toTypedArray()
    private val buttonRects = mutableListOf<Pair<RectF, PanelType>>()

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var activePanel: PanelType? = null
        set(value) { field = value; invalidate() }

    var onPanelSelected: ((PanelType) -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, barHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buttonRects.clear()

        // 背景
        bgPaint.color = theme.effectiveToolbarColor()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val btnWidth = width.toFloat() / panels.size
        val cornerR = 4f * density

        panels.forEachIndexed { i, panel ->
            val left = i * btnWidth
            val rect = RectF(left + 2 * density, 2 * density, left + btnWidth - 2 * density, height - 2 * density)
            buttonRects.add(rect to panel)

            // 選中高亮
            if (panel == activePanel) {
                highlightPaint.color = theme.effectiveToolbarActiveColor()
                highlightPaint.alpha = 60
                canvas.drawRoundRect(rect, cornerR, cornerR, highlightPaint)
            }

            // 圖示
            iconPaint.color = if (panel == activePanel) theme.effectiveToolbarActiveColor() else theme.effectiveToolbarIconColor()
            val textY = rect.centerY() + iconPaint.textSize / 3f
            canvas.drawText(panel.icon, rect.centerX(), textY, iconPaint)
        }

        // 底部分隔線
        bgPaint.color = (theme.keyColorInt and 0x00FFFFFF) or 0x40000000
        canvas.drawRect(0f, height - density, width.toFloat(), height.toFloat(), bgPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = buttonRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (hit != null) {
                val panel = hit.second
                // 再次點擊同一個面板 → 關閉
                if (activePanel == panel) {
                    activePanel = null
                    onPanelSelected?.invoke(panel) // 讓外部處理 toggle
                } else {
                    activePanel = panel
                    onPanelSelected?.invoke(panel)
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
