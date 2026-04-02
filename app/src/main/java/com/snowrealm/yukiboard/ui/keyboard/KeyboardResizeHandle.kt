package com.snowrealm.yukiboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 鍵盤頂部拖曳把手 — 上下拖動調整鍵盤高度。
 *
 * 顯示為一條水平橫線 + 小三角指示器。
 * 長按後拖動：垂直方向調整高度。
 */
class KeyboardResizeHandle(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val handleHeight = (10 * density).toInt()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 高度變化回呼，參數為拖動的 delta Y（負值 = 鍵盤變大） */
    var onHeightDrag: ((deltaY: Float) -> Unit)? = null

    /** 寬度變化回呼，參數為拖動的 delta X */
    var onWidthDrag: ((deltaX: Float) -> Unit)? = null

    private var isDragging = false
    private var lastTouchY = 0f
    private var lastTouchX = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, handleHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val color = if (isDragging) theme.candidateSelectedColorInt else theme.candidateTextColorInt
        linePaint.color = color
        linePaint.alpha = if (isDragging) 200 else 80

        // 中央橫線
        val cx = width / 2f
        val cy = height / 2f
        val lineW = 40f * density
        canvas.drawLine(cx - lineW / 2, cy, cx + lineW / 2, cy, linePaint)

        // 小三角指示器
        indicatorPaint.color = color
        indicatorPaint.alpha = if (isDragging) 180 else 60
        val triH = 3f * density
        val triW = 8f * density
        canvas.drawLine(cx - triW, cy - triH, cx, cy - triH * 2, indicatorPaint)
        canvas.drawLine(cx, cy - triH * 2, cx + triW, cy - triH, indicatorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastTouchY = event.rawY
                lastTouchX = event.rawX
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dy = event.rawY - lastTouchY
                    val dx = event.rawX - lastTouchX
                    if (kotlin.math.abs(dy) > 2 * density) {
                        onHeightDrag?.invoke(dy)
                        lastTouchY = event.rawY
                    }
                    if (kotlin.math.abs(dx) > 2 * density) {
                        onWidthDrag?.invoke(dx)
                        lastTouchX = event.rawX
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
