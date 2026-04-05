package com.snowrealm.yukiboard.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 遊戲懸浮面板 — 320×260dp，分類 Tab + 卡片網格。
 *
 * 分類：經典/動作/益智/挑戰
 * 點選卡片啟動遊戲。
 */
class GameFloatingPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val onGameSelected: (GameMode) -> Unit
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private var selectedCategory = GameCategory.CLASSIC

    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tabTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
        isFakeBoldText = true
    }

    private val tabRects = mutableListOf<Pair<RectF, GameCategory>>()
    private val cardRects = mutableListOf<Pair<RectF, GameMode>>()

    private val tabHeight = 32f * density
    private val titleHeight = 28f * density
    private val cardSize = 68f * density
    private val cardPadding = 6f * density
    private val cornerRadius = 10f * density

    var scoreManager: ScoreManager? = null

    private var scrollY = 0f
    private var lastTouchY = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (320 * density).toInt()
        val h = (260 * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        tabRects.clear(); cardRects.clear()

        // 背景
        canvas.drawColor(theme.backgroundColorInt)

        // 標題
        titlePaint.color = theme.keyLabelColorInt
        canvas.drawText("🎮 遊戲中心", width / 2f, titleHeight - 6 * density, titlePaint)

        // Tab 列
        val categories = GameCategory.entries
        val tabWidth = width.toFloat() / categories.size
        for ((i, cat) in categories.withIndex()) {
            val left = i * tabWidth
            val rect = RectF(left, titleHeight, left + tabWidth, titleHeight + tabHeight)
            tabRects.add(rect to cat)

            tabPaint.color = if (cat == selectedCategory) theme.effectiveToolbarActiveColor()
            else theme.effectiveToolbarColor()
            canvas.drawRect(rect, tabPaint)

            tabTextPaint.color = if (cat == selectedCategory) 0xFFFFFFFF.toInt()
            else theme.candidateTextColorInt
            canvas.drawText(cat.displayName, rect.centerX(), rect.centerY() + 4 * density, tabTextPaint)
        }

        // 遊戲卡片
        val games = GameMode.entries.filter { it.category == selectedCategory }
        val cardsPerRow = ((width - cardPadding) / (cardSize + cardPadding)).toInt().coerceAtLeast(1)
        val startY = titleHeight + tabHeight + cardPadding

        canvas.save()
        canvas.clipRect(0f, startY, width.toFloat(), height.toFloat())

        for ((i, mode) in games.withIndex()) {
            val col = i % cardsPerRow
            val row = i / cardsPerRow
            val left = cardPadding + col * (cardSize + cardPadding)
            val top = startY + row * (cardSize + cardPadding) + scrollY

            if (top + cardSize < startY || top > height) continue

            val rect = RectF(left, top, left + cardSize, top + cardSize)
            cardRects.add(rect to mode)

            val isLocked = mode == GameMode.VOICE_GAME
            cardPaint.color = if (isLocked) 0xFF444444.toInt() else getCategoryColor(selectedCategory)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)

            iconPaint.color = if (isLocked) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawText(if (isLocked) "🔒" else mode.icon, rect.centerX(), rect.centerY(), iconPaint)

            labelPaint.color = if (isLocked) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawText(if (isLocked) "即將推出" else mode.displayName, rect.centerX(), rect.bottom - 6 * density, labelPaint)

            val hs = scoreManager?.getHighScore(mode) ?: 0
            if (hs > 0) {
                labelPaint.color = 0xBBFFFFFF.toInt()
                canvas.drawText("$hs", rect.centerX(), rect.top + 12 * density, labelPaint)
            }
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                if (event.y > titleHeight + tabHeight) {
                    val games = GameMode.entries.filter { it.category == selectedCategory }
                    val cardsPerRow = ((width - cardPadding) / (cardSize + cardPadding)).toInt().coerceAtLeast(1)
                    val totalRows = (games.size + cardsPerRow - 1) / cardsPerRow
                    val totalContentHeight = totalRows * (cardSize + cardPadding) + cardPadding
                    val visibleHeight = height - (titleHeight + tabHeight + cardPadding)
                    val maxScrollUp = if (totalContentHeight > visibleHeight) -(totalContentHeight - visibleHeight) else 0f
                    scrollY = (scrollY + dy).coerceIn(maxScrollUp, 0f)
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // Tab 點擊
                for ((rect, cat) in tabRects) {
                    if (rect.contains(event.x, event.y)) {
                        selectedCategory = cat; scrollY = 0f; invalidate(); return true
                    }
                }
                // 卡片點擊
                for ((rect, mode) in cardRects) {
                    if (rect.contains(event.x, event.y)) {
                        if (mode == GameMode.VOICE_GAME) return true // 鎖定中
                        onGameSelected(mode); return true
                    }
                }
            }
        }
        return true
    }

    private fun getCategoryColor(cat: GameCategory): Int = when (cat) {
        GameCategory.CLASSIC -> 0xFF1565C0.toInt()
        GameCategory.ACTION -> 0xFFE65100.toInt()
        GameCategory.PUZZLE -> 0xFF6A1B9A.toInt()
        GameCategory.CHALLENGE -> 0xFF2E7D32.toInt()
    }
}
