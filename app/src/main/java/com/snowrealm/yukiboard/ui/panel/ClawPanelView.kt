package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * Claw AI 建議面板 — 橫向捲動卡片式 UI。
 */
class ClawPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var suggestions: List<String> = emptyList()
        set(value) { field = value; scrollOffset = 0f; invalidate() }

    var isLocked: Boolean = false
        set(value) { field = value; invalidate() }

    var onSuggestionSelected: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onLockTapped: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        textAlign = Paint.Align.LEFT
    }
    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f * density
        textAlign = Paint.Align.CENTER
    }

    private val cardRects = mutableListOf<Pair<RectF, Int>>()
    private var scrollOffset = 0f
    private var lastTouchX = 0f
    private var isDragging = false

    private val cardWidth = 160f * density
    private val cardHeight = 120f * density
    private val cardGap = 8f * density
    private val cardCorner = 10f * density
    private val avatarSize = 40f * density
    private val padding = 12f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cardRects.clear()

        // 背景
        bgPaint.color = theme.effectivePanelBackground()
        bgPaint.alpha = 240
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 關閉按鈕
        closePaint.color = theme.candidateTextColorInt
        canvas.drawText("\u2715", width - 20f * density, 20f * density, closePaint)

        // 標題
        titlePaint.color = theme.effectiveAccentColor()
        canvas.drawText("\uD83D\uDC3E Claw AI", padding + avatarSize + 8f * density, 24f * density, titlePaint)

        if (isLocked) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = theme.candidateTextColorInt
            textPaint.textSize = 16f * density
            canvas.drawText("\uD83D\uDD12 \u8F38\u5165\u9080\u8ACB\u78BC\u89E3\u9396 AI", width / 2f, height / 2f, textPaint)
            textPaint.textSize = 14f * density
            textPaint.textAlign = Paint.Align.LEFT
            return
        }

        if (suggestions.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = theme.candidateTextColorInt
            canvas.drawText("\u9078\u64C7\u4E00\u500B Skill \u958B\u59CB", width / 2f, height / 2f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            return
        }

        // 卡片區域
        val cardsTop = 36f * density
        val cardsLeft = padding

        canvas.save()
        canvas.clipRect(cardsLeft, cardsTop, width.toFloat() - padding, height - padding)

        suggestions.forEachIndexed { i, text ->
            val left = cardsLeft + i * (cardWidth + cardGap) + scrollOffset
            val right = left + cardWidth
            if (right < 0 || left > width) return@forEachIndexed

            val rect = RectF(left, cardsTop, right, cardsTop + cardHeight)

            cardPaint.color = theme.keyColorInt
            cardPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, cardCorner, cardCorner, cardPaint)

            // 卡片邊框
            cardPaint.style = Paint.Style.STROKE
            cardPaint.strokeWidth = 1f * density
            cardPaint.color = theme.effectiveAccentColor()
            cardPaint.alpha = 60
            canvas.drawRoundRect(rect, cardCorner, cardCorner, cardPaint)
            cardPaint.style = Paint.Style.FILL
            cardPaint.alpha = 255

            // 卡片內文字（自動換行）
            textPaint.color = theme.keyLabelColorInt
            textPaint.textAlign = Paint.Align.LEFT
            val textPadding = 10f * density
            val maxWidth = cardWidth - textPadding * 2
            val lines = wrapText(text, textPaint, maxWidth)
            var ty = rect.top + textPadding + textPaint.textSize
            for (line in lines) {
                if (ty > rect.bottom - textPadding) break
                canvas.drawText(line, rect.left + textPadding, ty, textPaint)
                ty += textPaint.textSize * 1.3f
            }

            cardRects.add(rect to i)
        }

        canvas.restore()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count == 0) break
            lines.add(text.substring(start, start + count))
            start += count
        }
        return lines
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                if (Math.abs(dx) > 8f * density) isDragging = true
                if (isDragging) {
                    scrollOffset += dx
                    // 限制捲動範圍
                    val maxScroll = 0f
                    val minScroll = -(suggestions.size * (cardWidth + cardGap) - width + padding * 2).coerceAtLeast(0f)
                    scrollOffset = scrollOffset.coerceIn(minScroll, maxScroll)
                    lastTouchX = event.x
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 檢查關閉按鈕
                    if (event.x > width - 40f * density && event.y < 36f * density) {
                        onClose?.invoke()
                        return true
                    }
                    // 檢查鎖定狀態
                    if (isLocked) {
                        onLockTapped?.invoke()
                        return true
                    }
                    // 檢查卡片點擊
                    for ((rect, index) in cardRects) {
                        if (rect.contains(event.x, event.y) && index < suggestions.size) {
                            onSuggestionSelected?.invoke(suggestions[index])
                            performClick()
                            return true
                        }
                    }
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
