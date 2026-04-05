package com.snowrealm.yukiboard.yuki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

/**
 * 雪凜說話泡泡 — 顯示在角色上方，2-3 秒淡出。
 *
 * 使用方式：
 * ```
 * val bubble = YukiSpeechBubble(context)
 * parentContainer.addView(bubble)
 * bubble.show("吐槽文字", anchorView = characterView)
 * ```
 */
class YukiSpeechBubble(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val DISPLAY_DURATION_MS = 2500L
    private val FADE_DURATION_MS = 500L

    private var text = ""
    private var isVisible = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEE1A2540.toInt()
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A90E2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8F4FF.toInt()
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
    }

    private val bubbleRect = RectF()
    private val tailPath = Path()
    private val cornerRadius = 8f * density
    private val paddingH = 14f * density
    private val paddingV = 8f * density

    init {
        visibility = GONE
    }

    /** TTS 朗讀回呼 — 由 IME Service 注入 */
    var onSpeakRequest: ((text: String) -> Unit)? = null

    /** 顯示泡泡，自動定位到 anchorView 上方，並用 TTS 朗讀 */
    fun show(message: String, anchorView: View) {
        text = message
        isVisible = true
        visibility = VISIBLE
        alpha = 1f

        // TTS 朗讀泡泡文字
        onSpeakRequest?.invoke(message)

        // 定位到 anchor 上方
        post {
            positionAbove(anchorView)
            invalidate()
            scheduleHide()
        }
    }

    fun hideNow() {
        visibility = GONE
        isVisible = false
    }

    override fun onDraw(canvas: Canvas) {
        if (!isVisible || text.isEmpty()) return

        val textWidth = textPaint.measureText(text)
        val bubbleW = textWidth + paddingH * 2
        val bubbleH = textPaint.textSize + paddingV * 2

        val left = (width - bubbleW) / 2f
        val top = 0f
        bubbleRect.set(left, top, left + bubbleW, top + bubbleH)

        // 泡泡背景
        canvas.drawRoundRect(bubbleRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(bubbleRect, cornerRadius, cornerRadius, borderPaint)

        // 小尾巴（三角）
        val tailCx = width / 2f
        val tailTop = bubbleRect.bottom
        tailPath.reset()
        tailPath.moveTo(tailCx - 6 * density, tailTop)
        tailPath.lineTo(tailCx, tailTop + 8 * density)
        tailPath.lineTo(tailCx + 6 * density, tailTop)
        tailPath.close()
        canvas.drawPath(tailPath, bgPaint)
        canvas.drawPath(tailPath, borderPaint)

        // 文字
        val textY = bubbleRect.centerY() + textPaint.textSize / 3f
        canvas.drawText(text, width / 2f, textY, textPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textWidth = textPaint.measureText(text)
        val w = (textWidth + paddingH * 2 + 20 * density).toInt()
        val h = (textPaint.textSize + paddingV * 2 + 12 * density).toInt()
        setMeasuredDimension(w, h)
    }

    private fun positionAbove(anchor: View) {
        val parent = parent as? ViewGroup ?: return
        val anchorLoc = IntArray(2)
        val parentLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        parent.getLocationInWindow(parentLoc)

        val x = anchorLoc[0] - parentLoc[0] + anchor.width / 2 - measuredWidth / 2
        val y = anchorLoc[1] - parentLoc[1] - measuredHeight - (4 * density).toInt()

        translationX = x.toFloat().coerceAtLeast(0f)
        translationY = y.toFloat().coerceAtLeast(0f)
    }

    private val hideRunnable = Runnable {
        animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction { hideNow() }
            .start()
    }

    private fun scheduleHide() {
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, DISPLAY_DURATION_MS)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(hideRunnable)
        animate().cancel()
    }
}
