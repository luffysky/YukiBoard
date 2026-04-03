package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * Claw AI Skill 選擇工具列 — 分類按鈕列 + token 額度顯示。
 * 支援 mode-based filtering（一般/開發者模式顯示不同技能）。
 */
class ClawToolbarView(context: Context) : View(context) {

    data class SkillButton(val id: String, val icon: String, val label: String, val category: String = "general")

    private val density = context.resources.displayMetrics.density
    private val barHeight = (36 * density).toInt()

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var isLocked: Boolean = false
        set(value) { field = value; invalidate() }

    var onSkillSelected: ((String) -> Unit)? = null
    var activeSkillId: String? = null
        set(value) { field = value; invalidate() }

    /** 設定目前模式，過濾技能按鈕 */
    var currentMode: String = "normal"
        set(value) { field = value; invalidate() }

    /** Token 剩餘額度，Int.MAX_VALUE = 無限 */
    var remainingTokens: Int = Int.MAX_VALUE
        set(value) { field = value; invalidate() }

    private var scrollOffset = 0f
    private var lastTouchX = 0f
    private var isDragging = false

    private val allButtons = listOf(
        // 原有
        SkillButton("writer", "✍️", "寫手", "general"),
        SkillButton("cupid", "💘", "情聖", "general"),
        SkillButton("translate", "🌐", "翻譯", "general"),
        SkillButton("whisper", "🎤", "語音", "general"),
        SkillButton("ai_prediction", "✨", "補字", "general"),
        // T1 文字
        SkillButton("grammar_check", "📝", "糾錯", "general"),
        SkillButton("tone_adjust", "🎭", "語氣", "general"),
        SkillButton("summarize", "📋", "摘要", "general"),
        // T2 社交
        SkillButton("quick_reply", "💬", "快回", "general"),
        SkillButton("meme_text", "🤣", "迷因", "general"),
        SkillButton("emoji_translate", "😎", "Emoji", "general"),
        // T3 開發者
        SkillButton("code_complete", "💻", "補碼", "developer"),
        SkillButton("regex_gen", "🔍", "Regex", "developer"),
        SkillButton("commit_message", "📦", "Commit", "developer"),
        // T4 情境
        SkillButton("yuki_chat", "❄️", "雪凜", "general"),
        SkillButton("mood_diary", "📔", "心情", "general"),
        SkillButton("weather", "🌤️", "天氣", "general"),
        SkillButton("animation_suggest", "🎬", "動畫", "general")
    )

    private val filteredButtons: List<SkillButton>
        get() = allButtons.filter { btn ->
            when (currentMode) {
                "developer" -> true  // 開發者看全部
                else -> btn.category != "developer"
            }
        }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 7f * density
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 8f * density
    }
    private val buttonRects = mutableListOf<Pair<RectF, SkillButton>>()

    private val btnSize = 40f * density

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

        val buttons = filteredButtons
        val cornerR = 6f * density

        // Token 額度顯示（右上角）
        if (remainingTokens != Int.MAX_VALUE) {
            tokenPaint.color = if (remainingTokens < 500) 0xFFFF6B6B.toInt() else theme.candidateTextColorInt
            val tokenText = "🪙 $remainingTokens"
            canvas.drawText(tokenText, width - 6f * density, 12f * density, tokenPaint)
        }

        // 可水平捲動的按鈕列
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())

        buttons.forEachIndexed { i, btn ->
            val left = i * btnSize + scrollOffset
            val right = left + btnSize
            if (right < 0 || left > width) return@forEachIndexed

            val rect = RectF(left + 2 * density, 2 * density, right - 2 * density, height - 2 * density)
            buttonRects.add(rect to btn)

            if (btn.id == activeSkillId) {
                highlightPaint.color = theme.effectiveToolbarActiveColor()
                highlightPaint.alpha = 60
                canvas.drawRoundRect(rect, cornerR, cornerR, highlightPaint)
            }

            iconPaint.color = if (btn.id == activeSkillId) theme.effectiveToolbarActiveColor() else theme.effectiveToolbarIconColor()
            canvas.drawText(btn.icon, rect.centerX(), rect.centerY() + 2f * density, iconPaint)

            // 小標籤
            labelPaint.color = theme.candidateTextColorInt
            labelPaint.alpha = if (btn.id == activeSkillId) 255 else 150
            canvas.drawText(btn.label, rect.centerX(), rect.bottom - 1f * density, labelPaint)

            if (isLocked) {
                labelPaint.alpha = 180
                canvas.drawText("🔒", rect.centerX(), rect.top + 8f * density, labelPaint)
            }
        }

        canvas.restore()
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
                if (Math.abs(dx) > 5f * density) isDragging = true
                if (isDragging) {
                    scrollOffset += dx
                    val maxScroll = 0f
                    val minScroll = -(filteredButtons.size * btnSize - width).coerceAtLeast(0f)
                    scrollOffset = scrollOffset.coerceIn(minScroll, maxScroll)
                    lastTouchX = event.x
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
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
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
