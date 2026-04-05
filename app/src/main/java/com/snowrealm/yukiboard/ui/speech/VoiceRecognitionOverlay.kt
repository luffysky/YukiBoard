package com.snowrealm.yukiboard.ui.speech

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.sin

/**
 * 語音辨識浮動氣泡框 — 液態玻璃風格。
 *
 * 顯示在鍵盤上方，辨識中的文字顯示在此（不寫入輸入框），
 * 辨識完成後才一次性 commitText。
 *
 * 特效：毛玻璃半透明 + 漸層光澤邊框 + 聲波動畫 + 呼吸動畫
 */
class VoiceRecognitionOverlay(context: Context) : View(context) {

    private val dp = context.resources.displayMetrics.density

    // ── 狀態 ──
    enum class State { IDLE, LISTENING, PAUSED, FINAL, ERROR }

    var state: State = State.IDLE
        private set
    private var recognizedText = ""
    private var engineName = ""
    private var errorMessage = ""
    private var countdownSeconds = 3
    private var typewriterLen = 0

    // ── 回呼 ──
    var onConfirm: ((String) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null

    // ── 尺寸 ──
    private val overlayMarginH = 12 * dp
    private val overlayPaddingH = 16 * dp
    private val overlayPaddingV = 12 * dp
    private val cornerRadius = 20 * dp
    private val titleHeight = 20 * dp
    private val textLineHeight = 18 * dp
    private val textAreaHeight = textLineHeight * 2 + 4 * dp  // 兩行
    private val waveAreaHeight = 20 * dp
    private val buttonAreaHeight = 36 * dp
    private val buttonSpacing = 12 * dp

    // ── 繪圖 ──
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 12 * dp
        typeface = Typeface.DEFAULT_BOLD
    }

    private val enginePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        textSize = 10 * dp
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF0F4FF.toInt()
        textSize = 15 * dp
        typeface = Typeface.DEFAULT
    }

    private val errorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF6B6B.toInt()
        textSize = 13 * dp
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC7EB8FF.toInt()
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val btnTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * dp
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // ── 動畫 ──
    private var wavePhase = 0f
    private val waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            wavePhase = it.animatedValue as Float
            if (state == State.LISTENING) invalidate()
        }
    }

    private var breathAlpha = 0f
    private val breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            breathAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    private var typewriterAnimator: ValueAnimator? = null

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (countdownSeconds > 0) {
                countdownSeconds--
                invalidate()
                postDelayed(this, 1000)
            } else {
                onConfirm?.invoke(recognizedText)
                dismiss()
            }
        }
    }

    // ── 滾動 ──
    private val scroller = OverScroller(context)
    private var textScrollY = 0f
    private var maxScrollY = 0f
    private var lastTouchY = 0f
    private var isDraggingText = false
    private val textAreaRect = RectF()

    // ── 按鈕區域 ──
    private val cancelBtnRect = RectF()
    private val pauseBtnRect = RectF()
    private val stopBtnRect = RectF()
    private val confirmBtnRect = RectF()

    init {
        visibility = GONE
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════

    fun show(engine: String) {
        engineName = engine
        recognizedText = ""
        errorMessage = ""
        state = State.LISTENING
        countdownSeconds = 3
        textScrollY = 0f
        typewriterLen = 0
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()
        waveAnimator.start()
        breathAnimator.start()
        invalidate()
    }

    fun updatePartial(text: String) {
        if (state != State.LISTENING) return
        recognizedText = text
        // typewriter 動畫
        animateTypewriter(text.length)
        // 自動滾到底
        post { scrollToBottom() }
    }

    fun pause() {
        if (state != State.LISTENING) return
        state = State.PAUSED
        waveAnimator.cancel()
        invalidate()
    }

    fun resume() {
        if (state != State.PAUSED) return
        state = State.LISTENING
        waveAnimator.start()
        invalidate()
    }

    fun showFinal(text: String) {
        recognizedText = text
        typewriterLen = text.length
        state = State.FINAL
        waveAnimator.cancel()
        // 3 秒倒數自動送出
        countdownSeconds = 3
        removeCallbacks(countdownRunnable)
        postDelayed(countdownRunnable, 1000)
        invalidate()
    }

    fun showError(message: String) {
        errorMessage = message
        state = State.ERROR
        waveAnimator.cancel()
        invalidate()
        // 3 秒後自動關閉
        postDelayed({ dismiss() }, 3000)
    }

    fun dismiss() {
        waveAnimator.cancel()
        breathAnimator.cancel()
        typewriterAnimator?.cancel()
        removeCallbacks(countdownRunnable)
        animate().alpha(0f).setDuration(200).withEndAction {
            visibility = GONE
            state = State.IDLE
            recognizedText = ""
        }.start()
    }

    val isShowing: Boolean get() = visibility == VISIBLE && state != State.IDLE

    // ════════════════════════════════════════
    // Measure & Draw
    // ════════════════════════════════════════

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentW = MeasureSpec.getSize(widthMeasureSpec)
        val w = parentW
        val h = (overlayPaddingV * 2 + titleHeight + 6 * dp + textAreaHeight +
                waveAreaHeight + 8 * dp + buttonAreaHeight).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (state == State.IDLE) return

        val w = width.toFloat()
        val h = height.toFloat()
        val left = overlayMarginH
        val top = 4 * dp
        val right = w - overlayMarginH
        val bottom = h - 4 * dp
        val rect = RectF(left, top, right, bottom)

        // ── 1. 液態玻璃背景 ──
        drawGlassBackground(canvas, rect)

        // ── 2. 標題列 ──
        val titleX = left + overlayPaddingH
        val titleY = top + overlayPaddingV + titleHeight * 0.75f
        val titleText = when (state) {
            State.LISTENING -> "語音辨識中..."
            State.PAUSED -> "已暫停"
            State.FINAL -> "辨識完成 (${countdownSeconds}s)"
            State.ERROR -> "辨識失敗"
            else -> ""
        }
        canvas.drawText(titleText, titleX, titleY, titlePaint)

        // 引擎名稱（右側）
        val engineW = enginePaint.measureText(engineName)
        canvas.drawText(engineName, right - overlayPaddingH - engineW, titleY, enginePaint)

        // ── 3. 辨識文字區域（兩行 scrollable）──
        val textTop = top + overlayPaddingV + titleHeight + 6 * dp
        val textLeft = left + overlayPaddingH
        val textRight = right - overlayPaddingH
        val textBottom = textTop + textAreaHeight
        textAreaRect.set(textLeft, textTop, textRight, textBottom)

        if (state == State.ERROR) {
            canvas.drawText(errorMessage.take(60), textLeft, textTop + textLineHeight, errorPaint)
        } else {
            drawScrollableText(canvas, textLeft, textTop, textRight, textBottom)
        }

        // ── 4. 聲波動畫 ──
        val waveTop = textBottom + 4 * dp
        if (state == State.LISTENING) {
            drawWaveAnimation(canvas, left + overlayPaddingH, waveTop,
                right - overlayPaddingH, waveTop + waveAreaHeight)
        }

        // ── 5. 按鈕列 ──
        val btnTop = waveTop + waveAreaHeight + 4 * dp
        drawButtons(canvas, left + overlayPaddingH, btnTop,
            right - overlayPaddingH, btnTop + buttonAreaHeight)
    }

    private fun drawGlassBackground(canvas: Canvas, rect: RectF) {
        // 主背景：深色半透明
        bgPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                0xDD1A2540.toInt(),
                0xDD162038.toInt(),
                0xDD1A2540.toInt()
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        bgPaint.shader = null

        // 頂部光澤（液態玻璃效果）
        val glossRect = RectF(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.4f)
        val glossAlpha = (0x18 + (0x10 * breathAlpha)).toInt()
        bgPaint.color = Color.argb(glossAlpha, 255, 255, 255)
        canvas.drawRoundRect(glossRect, cornerRadius, cornerRadius, bgPaint)

        // 漸層邊框（呼吸動畫）
        val borderAlpha = (0.4f + 0.3f * breathAlpha)
        borderPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                Color.argb((borderAlpha * 255).toInt(), 120, 180, 255),
                Color.argb((borderAlpha * 180).toInt(), 160, 120, 255),
                Color.argb((borderAlpha * 255).toInt(), 120, 180, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        borderPaint.shader = null

        // 內陰影效果（頂部）
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.top + 8 * dp,
                0x20FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            RectF(rect.left + 2 * dp, rect.top + 1 * dp, rect.right - 2 * dp, rect.top + 8 * dp),
            cornerRadius, cornerRadius, shadowPaint
        )
    }

    private fun drawScrollableText(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float
    ) {
        val displayText = if (typewriterLen < recognizedText.length) {
            recognizedText.substring(0, typewriterLen)
        } else {
            recognizedText
        }

        if (displayText.isEmpty()) {
            // 佔位提示
            val hint = "等待語音輸入..."
            val hintPaint = TextPaint(textPaint).apply { color = 0x66FFFFFF.toInt() }
            canvas.drawText(hint, left, top + textLineHeight, hintPaint)
            return
        }

        val textWidth = (right - left).toInt()
        val layout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2 * dp, 1f)
            .setIncludePad(false)
            .build()

        // 計算最大滾動量
        val totalTextHeight = layout.height.toFloat()
        val visibleHeight = bottom - top
        maxScrollY = (totalTextHeight - visibleHeight).coerceAtLeast(0f)
        textScrollY = textScrollY.coerceIn(0f, maxScrollY)

        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        canvas.translate(left, top - textScrollY)
        layout.draw(canvas)
        canvas.restore()

        // 滾動指示（如果可滾動）
        if (maxScrollY > 0) {
            val scrollRatio = if (maxScrollY > 0) textScrollY / maxScrollY else 0f
            val indicatorH = (visibleHeight * (visibleHeight / totalTextHeight)).coerceAtLeast(8 * dp)
            val indicatorY = top + scrollRatio * (visibleHeight - indicatorH)
            val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x447EB8FF.toInt()
            }
            canvas.drawRoundRect(
                RectF(right - 3 * dp, indicatorY, right, indicatorY + indicatorH),
                1.5f * dp, 1.5f * dp, indicatorPaint
            )
        }
    }

    private fun drawWaveAnimation(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float
    ) {
        val barCount = 5
        val totalWidth = right - left
        val barWidth = 4 * dp
        val gap = (totalWidth - barCount * barWidth) / (barCount + 1)
        val maxBarHeight = bottom - top - 4 * dp
        val centerY = (top + bottom) / 2f

        for (i in 0 until barCount) {
            val phase = wavePhase + i * 0.8f
            val heightRatio = 0.3f + 0.7f * abs(sin(phase.toDouble())).toFloat()
            val barH = maxBarHeight * heightRatio
            val x = left + gap + i * (barWidth + gap)

            val barAlpha = (0.5f + 0.5f * heightRatio)
            wavePaint.color = Color.argb(
                (barAlpha * 200).toInt(),
                126, 184, 255
            )
            canvas.drawRoundRect(
                RectF(x, centerY - barH / 2, x + barWidth, centerY + barH / 2),
                barWidth / 2, barWidth / 2, wavePaint
            )
        }
    }

    private fun drawButtons(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float
    ) {
        val totalW = right - left
        val textY = { rect: RectF -> rect.centerY() + btnTextPaint.textSize * 0.35f }

        // 清空可選按鈕
        pauseBtnRect.setEmpty()
        stopBtnRect.setEmpty()

        when (state) {
            State.LISTENING -> {
                // 四按鈕：取消 | 暫停 | 停止 | 送出
                val btnW = (totalW - buttonSpacing * 3) / 4

                cancelBtnRect.set(left, top, left + btnW, bottom)
                btnBgPaint.color = 0x33FF6B6B.toInt()
                canvas.drawRoundRect(cancelBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFFFF8A8A.toInt()
                canvas.drawText("✕", cancelBtnRect.centerX(), textY(cancelBtnRect), btnTextPaint)

                val x2 = left + btnW + buttonSpacing
                pauseBtnRect.set(x2, top, x2 + btnW, bottom)
                btnBgPaint.color = 0x3366AAFF.toInt()
                canvas.drawRoundRect(pauseBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFF88CCFF.toInt()
                canvas.drawText("⏸ 暫停", pauseBtnRect.centerX(), textY(pauseBtnRect), btnTextPaint)

                val x3 = x2 + btnW + buttonSpacing
                stopBtnRect.set(x3, top, x3 + btnW, bottom)
                btnBgPaint.color = 0x33FFAA00.toInt()
                canvas.drawRoundRect(stopBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFFFFCC66.toInt()
                canvas.drawText("■ 停止", stopBtnRect.centerX(), textY(stopBtnRect), btnTextPaint)

                confirmBtnRect.set(right - btnW, top, right, bottom)
                btnBgPaint.color = 0x227EB8FF.toInt()
                canvas.drawRoundRect(confirmBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0x88B0D4FF.toInt()
                canvas.drawText("✓", confirmBtnRect.centerX(), textY(confirmBtnRect), btnTextPaint)
            }
            State.PAUSED -> {
                // 三按鈕：取消 | ▶ 繼續 | 送出
                val btnW = (totalW - buttonSpacing * 2) / 3

                cancelBtnRect.set(left, top, left + btnW, bottom)
                btnBgPaint.color = 0x33FF6B6B.toInt()
                canvas.drawRoundRect(cancelBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFFFF8A8A.toInt()
                canvas.drawText("✕ 取消", cancelBtnRect.centerX(), textY(cancelBtnRect), btnTextPaint)

                val x2 = left + btnW + buttonSpacing
                pauseBtnRect.set(x2, top, x2 + btnW, bottom)
                btnBgPaint.color = 0x3366FF66.toInt()
                canvas.drawRoundRect(pauseBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFF88FF88.toInt()
                canvas.drawText("▶ 繼續", pauseBtnRect.centerX(), textY(pauseBtnRect), btnTextPaint)

                confirmBtnRect.set(right - btnW, top, right, bottom)
                btnBgPaint.color = 0x337EB8FF.toInt()
                canvas.drawRoundRect(confirmBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFFB0D4FF.toInt()
                canvas.drawText("✓ 送出", confirmBtnRect.centerX(), textY(confirmBtnRect), btnTextPaint)
            }
            else -> {
                // FINAL / ERROR：兩按鈕：取消 | 送出
                val btnW = (totalW - buttonSpacing) / 2

                cancelBtnRect.set(left, top, left + btnW, bottom)
                btnBgPaint.color = 0x33FF6B6B.toInt()
                canvas.drawRoundRect(cancelBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFFFF8A8A.toInt()
                canvas.drawText("✕ 取消", cancelBtnRect.centerX(), textY(cancelBtnRect), btnTextPaint)

                confirmBtnRect.set(left + btnW + buttonSpacing, top, right, bottom)
                btnBgPaint.color = 0x447EB8FF.toInt()
                canvas.drawRoundRect(confirmBtnRect, 10 * dp, 10 * dp, btnBgPaint)
                btnTextPaint.color = 0xFFB0D4FF.toInt()
                canvas.drawText("✓ 送出", confirmBtnRect.centerX(), textY(confirmBtnRect), btnTextPaint)
            }
        }
    }

    // ════════════════════════════════════════
    // Touch — 文字區滾動 + 按鈕點擊
    // ════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (textAreaRect.contains(event.x, event.y) && maxScrollY > 0) {
                    isDraggingText = true
                    lastTouchY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingText) {
                    val dy = lastTouchY - event.y
                    textScrollY = (textScrollY + dy).coerceIn(0f, maxScrollY)
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingText) {
                    isDraggingText = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                // 按鈕點擊
                if (cancelBtnRect.contains(event.x, event.y)) {
                    onCancel?.invoke()
                    dismiss()
                    return true
                }
                if (!pauseBtnRect.isEmpty && pauseBtnRect.contains(event.x, event.y)) {
                    if (state == State.LISTENING) {
                        pause()
                        onPause?.invoke()
                    } else if (state == State.PAUSED) {
                        resume()
                        onResume?.invoke()
                    }
                    return true
                }
                if (!stopBtnRect.isEmpty && stopBtnRect.contains(event.x, event.y)) {
                    onStop?.invoke()
                    return true
                }
                if (confirmBtnRect.contains(event.x, event.y) && recognizedText.isNotEmpty()) {
                    removeCallbacks(countdownRunnable)
                    onConfirm?.invoke(recognizedText)
                    dismiss()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // ════════════════════════════════════════
    // 內部工具
    // ════════════════════════════════════════

    private fun animateTypewriter(targetLen: Int) {
        typewriterAnimator?.cancel()
        val start = typewriterLen
        if (targetLen <= start) {
            typewriterLen = targetLen
            invalidate()
            return
        }
        typewriterAnimator = ValueAnimator.ofInt(start, targetLen).apply {
            duration = ((targetLen - start) * 30L).coerceAtMost(400)
            addUpdateListener {
                typewriterLen = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun scrollToBottom() {
        textScrollY = maxScrollY
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator.cancel()
        breathAnimator.cancel()
        typewriterAnimator?.cancel()
        removeCallbacks(countdownRunnable)
    }
}
