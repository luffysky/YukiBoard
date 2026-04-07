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
    /** 最大寬度（螢幕寬度的 70%） */
    private val maxBubbleW = (context.resources.displayMetrics.widthPixels * 0.7f)
    private var wrappedLines: List<String> = emptyList()

    init {
        visibility = GONE
        setOnClickListener {
            onBubbleTapped?.invoke()
        }
    }

    /** TTS 朗讀回呼 — 由 IME Service 注入 */
    var onSpeakRequest: ((text: String) -> Unit)? = null

    /** 泡泡被點擊的回呼（可用於展開詳細分析等） */
    var onBubbleTapped: (() -> Unit)? = null

    /** 當前顯示的泡泡是否為防詐警告（點擊時觸發不同行為） */
    var isFraudWarning = false

    /** 顯示泡泡，自動定位到 anchorView 上方，並用 TTS 朗讀 */
    fun show(message: String, anchorView: View) {
        text = message
        wrappedLines = emptyList()  // 重新計算
        isVisible = true
        visibility = VISIBLE
        alpha = 1f

        // TTS 朗讀泡泡文字
        onSpeakRequest?.invoke(message)

        // 定位到 anchor 上方
        post {
            positionAbove(anchorView)
            invalidate()
            // 防詐警告顯示更久（5 秒），一般泡泡 2.5 秒
            val duration = if (isFraudWarning) 5000L else DISPLAY_DURATION_MS
            scheduleHide(duration)
        }
    }

    fun hideNow() {
        visibility = GONE
        isVisible = false
    }

    override fun onDraw(canvas: Canvas) {
        if (!isVisible || text.isEmpty()) return

        val lineH = textPaint.textSize * 1.3f
        val maxTextW = maxBubbleW - paddingH * 2
        if (wrappedLines.isEmpty()) wrappedLines = wrapText(text, textPaint, maxTextW)

        val contentW = wrappedLines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val bubbleW = (contentW + paddingH * 2).coerceAtMost(maxBubbleW)
        val bubbleH = paddingV * 2 + wrappedLines.size * lineH

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

        // 文字（多行）
        textPaint.textAlign = Paint.Align.CENTER
        val cx = width / 2f
        var y = bubbleRect.top + paddingV + textPaint.textSize
        for (line in wrappedLines) {
            canvas.drawText(line, cx, y, textPaint)
            y += lineH
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxTextW = maxBubbleW - paddingH * 2
        wrappedLines = wrapText(text, textPaint, maxTextW)
        val contentW = wrappedLines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val lineH = textPaint.textSize * 1.3f
        val w = (contentW + paddingH * 2 + 20 * density).toInt().coerceAtMost(maxBubbleW.toInt())
        val h = (paddingV * 2 + wrappedLines.size * lineH + 12 * density).toInt()
        setMeasuredDimension(w, h)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null)
            if (count <= 0) break
            lines.add(remaining.substring(0, count))
            remaining = remaining.substring(count)
            if (lines.size >= 4) {
                if (remaining.isNotEmpty()) lines[lines.size - 1] = lines.last().dropLast(1) + "..."
                break
            }
        }
        return lines.ifEmpty { listOf(text.take(20) + "...") }
    }

    private fun positionAbove(anchor: View) {
        val parent = parent as? ViewGroup ?: return
        val anchorLoc = IntArray(2)
        val parentLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        parent.getLocationInWindow(parentLoc)

        val x = anchorLoc[0] - parentLoc[0] + anchor.width / 2 - measuredWidth / 2
        val y = anchorLoc[1] - parentLoc[1] - measuredHeight - (4 * density).toInt()

        // 確保泡泡不超出父容器邊界
        val maxX = (parent.width - measuredWidth).coerceAtLeast(0)
        translationX = x.toFloat().coerceIn(0f, maxX.toFloat())
        translationY = y.toFloat().coerceAtLeast(0f)
    }

    private val hideRunnable = Runnable {
        animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction { hideNow() }
            .start()
    }

    private fun scheduleHide(duration: Long = DISPLAY_DURATION_MS) {
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, duration)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(hideRunnable)
        animate().cancel()
    }
}
