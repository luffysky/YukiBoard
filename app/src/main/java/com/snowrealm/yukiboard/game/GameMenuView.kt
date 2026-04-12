package com.snowrealm.yukiboard.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 遊戲選單 — 進入遊戲模式時顯示，4 張卡片選擇遊戲。
 */
class GameMenuView(
    context: Context,
    private val theme: KeyboardTheme,
    private val onGameSelected: (GameMode) -> Unit
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f * density
        color = theme.keyLabelColorInt
    }
    private val highScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
    }

    private val cardRects = mutableListOf<Pair<RectF, GameMode>>()
    private val tabRects = mutableListOf<Pair<RectF, GameCategory>>()
    private val backBtnRect = RectF()
    var onBackToNormal: (() -> Unit)? = null
    private var selectedCategory = GameCategory.CLASSIC
    private val categoryColors = mapOf(
        GameCategory.CLASSIC to 0xFF1565C0.toInt(),
        GameCategory.ACTION to 0xFFE65100.toInt(),
        GameCategory.PUZZLE to 0xFF6A1B9A.toInt(),
        GameCategory.CHALLENGE to 0xFF2E7D32.toInt()
    )

    var scoreManager: ScoreManager? = null

    private val cardHeight = 70f * density
    private val cardPadding = 8f * density
    private val cornerRadius = 12f * density

    private val tabHeight = 30f * density
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tabTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
    }

    // 內部捲動支持
    private var scrollOffset = 0f
    private var maxScrollOffset = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        val specH = MeasureSpec.getSize(heightMeasureSpec)
        val games = GameMode.entries.filter { it.category == selectedCategory }
        val cardsPerRow = ((w - cardPadding) / ((w - cardPadding * 3) / 2f + cardPadding)).toInt().coerceAtLeast(1)
        val rowCount = (games.size + cardsPerRow - 1) / cardsPerRow
        val contentHeight = (36f * density + tabHeight + rowCount * (cardHeight + cardPadding) + 16f * density).toInt()
        // 使用容器高度（不超出），超出部分透過內部捲動
        val h = if (specH > 0) specH else contentHeight
        maxScrollOffset = maxOf(0f, contentHeight.toFloat() - h.toFloat())
        scrollOffset = scrollOffset.coerceIn(0f, maxScrollOffset)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.backgroundColorInt)
        cardRects.clear(); tabRects.clear()

        // 套用捲動偏移
        canvas.save()
        canvas.translate(0f, -scrollOffset)

        // ⌨返回 按鈕（左上角）
        val btnW = 56f * density
        val btnH = 22f * density
        backBtnRect.set(6f * density, 4f * density, 6f * density + btnW, 4f * density + btnH)
        cardPaint.color = theme.specialKeyColorInt
        canvas.drawRoundRect(backBtnRect, 6f * density, 6f * density, cardPaint)
        labelPaint.color = theme.keyLabelColorInt
        canvas.drawText("⌨返回", backBtnRect.centerX(), backBtnRect.centerY() + 4f * density, labelPaint)

        // 標題
        titlePaint.color = theme.keyLabelColorInt
        canvas.drawText("🎮 選擇遊戲", width / 2f, 24f * density, titlePaint)

        // 分類 Tab
        val categories = GameCategory.entries
        val tabWidth = width.toFloat() / categories.size
        val tabTop = 30f * density
        for ((i, cat) in categories.withIndex()) {
            val left = i * tabWidth
            val rect = RectF(left, tabTop, left + tabWidth, tabTop + tabHeight)
            tabRects.add(rect to cat)
            tabPaint.color = if (cat == selectedCategory) (categoryColors[cat] ?: 0xFF1565C0.toInt()) else theme.backgroundColorInt
            canvas.drawRect(rect, tabPaint)
            tabTextPaint.color = if (cat == selectedCategory) 0xFFFFFFFF.toInt() else theme.candidateTextColorInt
            canvas.drawText(cat.displayName, rect.centerX(), rect.centerY() + 4 * density, tabTextPaint)
        }

        // 卡片
        val games = GameMode.entries.filter { it.category == selectedCategory }
        val cardWidth = (width - cardPadding * 3) / 2f
        val startY = tabTop + tabHeight + cardPadding
        val baseColor = categoryColors[selectedCategory] ?: 0xFF1565C0.toInt()

        games.forEachIndexed { i, mode ->
            val col = i % 2
            val row = i / 2
            val left = cardPadding + col * (cardWidth + cardPadding)
            val top = startY + row * (cardHeight + cardPadding)
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)
            cardRects.add(rect to mode)

            cardPaint.color = baseColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)

            iconPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText(mode.icon, rect.centerX(), rect.centerY() - 2f * density, iconPaint)

            labelPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText(mode.displayName, rect.centerX(), rect.bottom - 10f * density, labelPaint)

            val highScore = scoreManager?.getHighScore(mode) ?: 0
            if (highScore > 0) {
                highScorePaint.color = 0xCCFFFFFF.toInt()
                canvas.drawText("最高: $highScore", rect.centerX(), rect.top + 14f * density, highScorePaint)
            }
        }

        canvas.restore()

        // 捲動指示器（有可捲動內容時顯示）
        if (maxScrollOffset > 0f) {
            val trackH = height.toFloat() - 4 * density
            val thumbRatio = height.toFloat() / (height.toFloat() + maxScrollOffset)
            val thumbH = (trackH * thumbRatio).coerceAtLeast(20 * density)
            val thumbY = 2 * density + (trackH - thumbH) * (scrollOffset / maxScrollOffset)
            cardPaint.color = (theme.keyLabelColorInt and 0x00FFFFFF) or 0x44000000
            canvas.drawRoundRect(
                width - 5f * density, thumbY,
                width - 1f * density, thumbY + thumbH,
                2 * density, 2 * density, cardPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 觸控座標轉為內容座標（加上捲動偏移）
        val contentY = event.y + scrollOffset

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - event.y
                if (!isDragging && Math.abs(dy) > 8 * density) {
                    isDragging = true
                }
                if (isDragging && maxScrollOffset > 0f) {
                    scrollOffset = (scrollOffset + dy).coerceIn(0f, maxScrollOffset)
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // ⌨返回
                    if (backBtnRect.contains(event.x, contentY)) {
                        onBackToNormal?.invoke()
                        return true
                    }
                    // Tab 點擊
                    for ((rect, cat) in tabRects) {
                        if (rect.contains(event.x, contentY)) {
                            selectedCategory = cat
                            scrollOffset = 0f
                            requestLayout(); invalidate(); return true
                        }
                    }
                    // 卡片點擊
                    val hit = cardRects.firstOrNull { it.first.contains(event.x, contentY) }
                    if (hit != null) {
                        onGameSelected(hit.second)
                        return true
                    }
                }
                isDragging = false
            }
        }
        return true
    }
}
