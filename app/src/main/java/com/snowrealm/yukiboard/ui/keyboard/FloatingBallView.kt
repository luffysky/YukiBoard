package com.snowrealm.yukiboard.ui.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 懸浮球 — 鍵盤最小化後的圓形拖曳球。
 *
 * 點擊 → 展開回懸浮鍵盤
 * 長按拖曳 → 移動位置
 * 外觀：半透明漸層球 + 雪花圖示 + 呼吸脈動動畫
 */
class FloatingBallView(context: Context) : View(context) {

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 點擊展開回呼 */
    var onExpand: (() -> Unit)? = null

    /** 拖動回呼 */
    var onDragMove: ((deltaX: Float, deltaY: Float) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    val ballSize = (48 * density).toInt()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f * density
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isDragging = false
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    // 呼吸動畫
    private var pulseScale = 1f
    private val pulseAnimator = ValueAnimator.ofFloat(1f, 1.12f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulseScale = it.animatedValue as Float
            invalidate()
        }
    }

    // 出現彈跳動畫
    private var appearScale = 0f
    private val appearAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = OvershootInterpolator(2f)
        addUpdateListener {
            appearScale = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        appearAnimator.start()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        appearAnimator.cancel()
        pulseAnimator.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(ballSize, ballSize)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val baseR = width / 2f - 4f * density
        val scale = appearScale * pulseScale
        val r = baseR * scale

        // 外圈光暈
        glowPaint.shader = RadialGradient(
            cx, cy, r * 1.3f,
            intArrayOf(0x404A90E2.toInt(), 0x00000000),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.3f, glowPaint)
        glowPaint.shader = null

        // 漸層球體
        bgPaint.shader = RadialGradient(
            cx - r * 0.2f, cy - r * 0.3f, r * 1.2f,
            intArrayOf(0xDD4A90E2.toInt(), 0xDD1A3A6E.toInt()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, bgPaint)
        bgPaint.shader = null

        // 邊框
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 1.5f * density
        bgPaint.color = 0x607FD8FF.toInt()
        canvas.drawCircle(cx, cy, r, bgPaint)
        bgPaint.style = Paint.Style.FILL

        // 雪花圖示
        iconPaint.color = 0xFFFFFFFF.toInt()
        iconPaint.alpha = (255 * appearScale).toInt()
        canvas.drawText("❄", cx, cy + 8f * density, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                downTime = System.currentTimeMillis()
                // 暫停脈動
                pulseScale = 1f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                val totalDist = Math.hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                if (totalDist > 8 * density) {
                    isDragging = true
                    onDragMove?.invoke(dx, dy)
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && System.currentTimeMillis() - downTime < 300) {
                    // 短按 → 展開
                    onExpand?.invoke()
                    performClick()
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
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
