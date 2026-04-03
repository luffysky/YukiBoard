package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * Claw AI Skill 選擇工具列 — 5 個技能按鈕。
 */
class ClawToolbarView(context: Context) : View(context) {

    data class SkillButton(val id: String, val icon: String, val label: String)

    private val density = context.resources.displayMetrics.density
    private val barHeight = (32 * density).toInt()

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var isLocked: Boolean = false
        set(value) { field = value; invalidate() }

    var onSkillSelected: ((String) -> Unit)? = null
    var activeSkillId: String? = null
        set(value) { field = value; invalidate() }

    private val buttons = listOf(
        SkillButton("writer", "\u270D\uFE0F", "\u5BEB\u624B"),
        SkillButton("cupid", "\uD83D\uDC98", "\u60C5\u8056"),
        SkillButton("translate", "\uD83C\uDF10", "\u7FFB\u8B6F"),
        SkillButton("whisper", "\uD83C\uDFA4", "\u8A9E\u97F3"),
        SkillButton("ai_prediction", "\u2728", "\u88DC\u5B57")
    )

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 8f * density
    }
    private val buttonRects = mutableListOf<Pair<RectF, SkillButton>>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, barHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buttonRects.clear()

        bgPaint.color = theme.effectiveToolbarColor()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val btnWidth = width.toFloat() / buttons.size
        val cornerR = 4f * density

        buttons.forEachIndexed { i, btn ->
            val left = i * btnWidth
            val rect = RectF(left + 2 * density, 2 * density, left + btnWidth - 2 * density, height - 2 * density)
            buttonRects.add(rect to btn)

            if (btn.id == activeSkillId) {
                highlightPaint.color = theme.effectiveToolbarActiveColor()
                highlightPaint.alpha = 60
                canvas.drawRoundRect(rect, cornerR, cornerR, highlightPaint)
            }

            iconPaint.color = if (btn.id == activeSkillId) theme.effectiveToolbarActiveColor() else theme.effectiveToolbarIconColor()
            val textY = rect.centerY() + iconPaint.textSize / 3f
            canvas.drawText(btn.icon, rect.centerX(), textY, iconPaint)

            if (isLocked) {
                lockPaint.color = theme.candidateTextColorInt
                lockPaint.alpha = 180
                canvas.drawText("\uD83D\uDD12", rect.centerX(), rect.bottom - 2f * density, lockPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            for ((rect, btn) in buttonRects) {
                if (rect.contains(event.x, event.y)) {
                    onSkillSelected?.invoke(btn.id)
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
