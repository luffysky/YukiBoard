package com.snowrealm.yukiboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 懸浮鍵盤外殼 — 包裝鍵盤內容，提供拖曳移動功能。
 *
 * 結構：
 * ┌─────────────────────────┐
 * │ 拖曳條（dragBar）        │  ← 抓住拖動整個鍵盤
 * │ ┌─────────────────────┐ │
 * │ │ 鍵盤內容（childView）│ │
 * │ └─────────────────────┘ │
 * │ [固定] [調整大小] [關閉]  │  ← 底部操作列
 * └─────────────────────────┘
 */
class FloatingKeyboardWrapper(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val dragBarHeight = (24 * density).toInt()
    private val actionBarHeight = (28 * density).toInt()
    private val cornerR = 12f * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dragPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val actionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 拖動位移回呼 */
    var onDragMove: ((deltaX: Float, deltaY: Float) -> Unit)? = null

    /** 固定/解除固定回呼 */
    var onToggleDock: (() -> Unit)? = null

    /** 關閉懸浮模式回呼 */
    var onClose: (() -> Unit)? = null

    /** 調整大小回呼 (deltaWidth) */
    var onResize: ((Int) -> Unit)? = null

    /** 縮成懸浮球回呼 */
    var onMinimize: (() -> Unit)? = null

    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val dragBarRect = RectF()
    private val dockBtnRect = RectF()
    private val resizeBtnRect = RectF()
    private val minimizeBtnRect = RectF()
    private val closeBtnRect = RectF()

    override fun onDraw(canvas: Canvas) {
        // 圓角背景
        bgPaint.color = theme.backgroundColorInt
        val bgRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bgRect, cornerR, cornerR, bgPaint)

        // 拖曳條
        dragPaint.color = theme.candidateTextColorInt
        dragPaint.alpha = if (isDragging) 200 else 80
        val cx = width / 2f
        val cy = dragBarHeight / 2f
        val lineW = 36f * density
        canvas.drawLine(cx - lineW / 2, cy, cx + lineW / 2, cy, dragPaint)

        // 底部操作列
        val actionTop = (height - actionBarHeight).toFloat()
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, actionTop, width.toFloat(), height.toFloat(), bgPaint)

        val btnW = width / 4f
        // 固定按鈕
        dockBtnRect.set(0f, actionTop, btnW, height.toFloat())
        actionPaint.color = theme.candidateTextColorInt
        canvas.drawText("📌", dockBtnRect.centerX(), dockBtnRect.centerY() + actionPaint.textSize / 3f, actionPaint)

        // 調整大小按鈕
        resizeBtnRect.set(btnW, actionTop, btnW * 2, height.toFloat())
        actionPaint.color = theme.candidateTextColorInt
        canvas.drawText("↔", resizeBtnRect.centerX(), resizeBtnRect.centerY() + actionPaint.textSize / 3f, actionPaint)

        // 縮小（懸浮球）按鈕
        minimizeBtnRect.set(btnW * 2, actionTop, btnW * 3, height.toFloat())
        actionPaint.color = 0xFF7FD8FF.toInt()
        canvas.drawText("●", minimizeBtnRect.centerX(), minimizeBtnRect.centerY() + actionPaint.textSize / 3f, actionPaint)

        // 關閉按鈕
        closeBtnRect.set(btnW * 3, actionTop, width.toFloat(), height.toFloat())
        actionPaint.color = 0xFFFF6B6B.toInt()
        canvas.drawText("✕", closeBtnRect.centerX(), closeBtnRect.centerY() + actionPaint.textSize / 3f, actionPaint)

        // 右下角 resize 手柄
        val gripSize = 14f * density
        val gx = width - gripSize - 4f * density
        val gy = height - gripSize - 2f * density
        dragPaint.color = theme.candidateTextColorInt
        dragPaint.alpha = 120
        for (i in 0..2) {
            val off = i * 4f * density
            canvas.drawLine(gx + off, gy + gripSize, gx + gripSize, gy + off, dragPaint)
        }
        dragPaint.alpha = 255
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // 攔截拖曳條區域的觸控
        dragBarRect.set(0f, 0f, width.toFloat(), dragBarHeight.toFloat())
        if (dragBarRect.contains(event.x, event.y)) {
            return true
        }
        // 攔截底部操作列
        val actionTop = (height - actionBarHeight).toFloat()
        if (event.y > actionTop) {
            return true
        }
        // 攔截右下角 resize 手柄
        val gripZone = 24f * density
        if (event.x > width - gripZone && event.y > height - gripZone) {
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val gripZone = 24f * density
                if (event.x > width - gripZone && event.y > height - gripZone) {
                    // 右下角 resize 拖曳
                    isResizing = true
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                } else if (event.y < dragBarHeight) {
                    isDragging = true
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    onDragMove?.invoke(dx, dy)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                } else if (isResizing) {
                    val dx = (event.rawX - lastTouchX).toInt()
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    onResize?.invoke(dx)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    invalidate()
                    performClick()
                    return true
                }
                if (isResizing) {
                    isResizing = false
                    performClick()
                    return true
                }

                // 底部按鈕
                val actionTop = (height - actionBarHeight).toFloat()
                if (event.y > actionTop) {
                    when {
                        dockBtnRect.contains(event.x, event.y) -> onToggleDock?.invoke()
                        resizeBtnRect.contains(event.x, event.y) -> {
                            // 切換 80% ↔ 100% 寬度
                            val screenW = resources.displayMetrics.widthPixels
                            val currentW = (layoutParams as? FrameLayout.LayoutParams)?.width ?: screenW
                            val targetW = if (currentW < screenW * 0.9f) screenW else (screenW * 0.8f).toInt()
                            onResize?.invoke(targetW - currentW)
                        }
                        minimizeBtnRect.contains(event.x, event.y) -> onMinimize?.invoke()
                        closeBtnRect.contains(event.x, event.y) -> onClose?.invoke()
                    }
                    performClick()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isResizing = false
                invalidate()
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
