package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 主題瀏覽面板 — 在鍵盤內直接預覽和切換主題。
 */
class ThemePanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 14f * density
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }
    private val previewKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onThemeSelected: ((KeyboardTheme) -> Unit)? = null

    var currentThemeId: String = "snow_dark"
        set(value) { field = value; invalidate() }

    var availableThemes: List<KeyboardTheme> = KeyboardTheme.all()
        set(value) { field = value; invalidate() }

    private val themeRects = mutableListOf<Pair<RectF, Int>>()
    private val cornerR = 10f * density
    private val headerH = 28f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        themeRects.clear()

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 標題
        titlePaint.color = theme.candidateTextColorInt
        canvas.drawText("選擇主題", 12f * density, headerH * 0.75f, titlePaint)

        // 主題卡片（水平捲動風格，2 列）
        val startY = headerH + 8 * density
        val cardW = 140f * density
        val cardH = 80f * density
        val pad = 8f * density

        availableThemes.forEachIndexed { i, t ->
            val col = i % 2
            val row = i / 2
            val x = pad + col * (cardW + pad)
            val y = startY + row * (cardH + pad)

            val rect = RectF(x, y, x + cardW, y + cardH)
            themeRects.add(rect to i)

            // 卡片背景 = 主題的背景色
            cardPaint.color = t.backgroundColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 邊框（選中時高亮）
            if (t.id == currentThemeId) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * density
                    color = theme.candidateSelectedColorInt
                }
                canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)
            }

            // 迷你鍵盤預覽（3 個按鍵色塊）
            val keyY = rect.top + 12 * density
            val keyH = 18f * density
            val keyW = 28f * density
            val keyPad = 4f * density
            val keyStartX = rect.left + 12 * density

            for (k in 0 until 3) {
                previewKeyPaint.color = if (k == 2) t.specialKeyColorInt else t.keyColorInt
                val kr = RectF(
                    keyStartX + k * (keyW + keyPad), keyY,
                    keyStartX + k * (keyW + keyPad) + keyW, keyY + keyH
                )
                canvas.drawRoundRect(kr, 3f * density, 3f * density, previewKeyPaint)

                // 按鍵上文字
                textPaint.color = t.keyLabelColorInt
                textPaint.textSize = 9f * density
                canvas.drawText(listOf("Q", "W", "⏎")[k], kr.centerX(), kr.centerY() + textPaint.textSize / 3f, textPaint)
            }

            // 候選列預覽
            val barY = keyY + keyH + 6 * density
            val barH = 12f * density
            previewKeyPaint.color = t.candidateBarColorInt
            canvas.drawRoundRect(
                RectF(rect.left + 8 * density, barY, rect.right - 8 * density, barY + barH),
                2f * density, 2f * density, previewKeyPaint
            )

            // 主題名稱
            textPaint.textSize = 11f * density
            textPaint.color = t.keyLabelColorInt
            canvas.drawText(t.displayName, rect.centerX(), rect.bottom - 6 * density, textPaint)
            textPaint.textSize = 12f * density

            // 選中勾號
            if (t.id == currentThemeId) {
                checkPaint.color = theme.candidateSelectedColorInt
                canvas.drawText("✓", rect.right - 14 * density, rect.top + 16 * density, checkPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = themeRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (hit != null) {
                val selected = availableThemes.getOrNull(hit.second)
                if (selected != null) {
                    currentThemeId = selected.id
                    onThemeSelected?.invoke(selected)
                    performClick()
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
