package com.snowrealm.yukiboard.ui.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 環形模式選單 — 長按 Tab 時彈出，4 個扇區對應 4 種模式。
 * 手指滑到扇區後放開即選中。0.2 秒 OvershootInterpolator 展開動畫。
 */
class RadialModeMenu(
    context: Context,
    private val theme: KeyboardTheme,
    private val currentMode: KeyboardMode,
    private val onModeSelected: (KeyboardMode) -> Unit
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val menuRadius = 95f * density
    private val innerRadius = 28f * density
    private val viewSize = (menuRadius * 2 + 40 * density).toInt()

    private var animScale = 0f
    private val expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 200
        interpolator = OvershootInterpolator(1.2f)
        addUpdateListener {
            animScale = it.animatedValue as Float
            invalidate()
        }
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        color = theme.keyLabelColorInt
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.backgroundColorInt
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
    }

    private val modes = KeyboardMode.entries.toTypedArray()
    private val sectorCount = modes.size
    private val sweepAngle = 360f / sectorCount
    // 4 sectors: top-right, bottom-right, bottom-left, top-left
    private val startAngles = FloatArray(sectorCount) { i -> -180f + i * sweepAngle }

    private var hoveredIndex = -1
    private val cx get() = width / 2f
    private val cy get() = height / 2f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        expandAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        expandAnimator.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSize, viewSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animScale <= 0f) return

        val save = canvas.save()
        canvas.scale(animScale, animScale, cx, cy)
        val alphaFactor = animScale.coerceIn(0f, 1f)

        // 半透明背景圓
        bgPaint.color = (theme.backgroundColorInt and 0x00FFFFFF) or
                ((0xDD * alphaFactor).toInt().coerceIn(0, 255) shl 24)
        canvas.drawCircle(cx, cy, menuRadius, bgPaint)

        val oval = RectF(cx - menuRadius, cy - menuRadius, cx + menuRadius, cy + menuRadius)

        modes.forEachIndexed { i, mode ->
            val isHovered = hoveredIndex == i
            val isCurrent = mode == currentMode

            sectorPaint.color = when {
                isHovered -> theme.candidateSelectedColorInt
                isCurrent -> (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x44000000
                else -> (theme.keyColorInt and 0x00FFFFFF) or 0x99000000.toInt()
            }
            sectorPaint.style = Paint.Style.FILL
            canvas.drawArc(oval, startAngles[i], sweepAngle, true, sectorPaint)

            // 分隔線
            val angle = Math.toRadians(startAngles[i].toDouble())
            canvas.drawLine(
                cx + innerRadius * cos(angle).toFloat(),
                cy + innerRadius * sin(angle).toFloat(),
                cx + menuRadius * cos(angle).toFloat(),
                cy + menuRadius * sin(angle).toFloat(),
                dividerPaint
            )

            // Icon + 文字
            val midAngle = Math.toRadians((startAngles[i] + sweepAngle / 2).toDouble())
            val labelR = (innerRadius + menuRadius) / 2f
            val lx = cx + labelR * cos(midAngle).toFloat()
            val ly = cy + labelR * sin(midAngle).toFloat()

            iconPaint.color = if (isHovered) theme.keyLabelColorInt else theme.composingTextColorInt
            canvas.drawText(mode.icon, lx, ly - 4f * density, iconPaint)

            textPaint.color = when {
                isHovered -> theme.keyLabelColorInt
                isCurrent -> theme.candidateSelectedColorInt
                else -> theme.candidateTextColorInt
            }
            canvas.drawText(mode.displayName, lx, ly + 14f * density, textPaint)
        }

        // 中心圓
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawCircle(cx, cy, innerRadius, bgPaint)
        textPaint.textSize = 9f * density
        textPaint.color = theme.composingTextColorInt
        canvas.drawText("模式", cx, cy + 4f * density, textPaint)
        textPaint.textSize = 12f * density

        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = hypot(dx, dy)

        when (event.action) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                hoveredIndex = if (dist > innerRadius && dist < menuRadius + 20 * density) {
                    getSectorIndex(dx, dy)
                } else -1
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (hoveredIndex in modes.indices) {
                    onModeSelected(modes[hoveredIndex])
                }
                hoveredIndex = -1
            }
        }
        return true
    }

    private fun getSectorIndex(dx: Float, dy: Float): Int {
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        // Normalize to -180..180
        while (angle < -180) angle += 360f
        while (angle > 180) angle -= 360f

        for (i in modes.indices) {
            val start = startAngles[i]
            val end = start + sweepAngle
            if (angle >= start && angle < end) return i
            // Wrap-around
            if (end > 180f && (angle + 360f) < end && (angle + 360f) >= start) return i
        }
        return -1
    }
}
