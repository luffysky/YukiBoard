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
    private val games = GameMode.entries.toTypedArray()
    private val cardColors = intArrayOf(
        0xFF2E7D32.toInt(),  // 綠（蛇）
        0xFFE65100.toInt(),  // 橘（地鼠）
        0xFF6A1B9A.toInt(),  // 紫（節奏）
        0xFF1565C0.toInt()   // 藍（打字）
    )

    var scoreManager: ScoreManager? = null

    private val cardHeight = 70f * density
    private val cardPadding = 8f * density
    private val cornerRadius = 12f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        // 標題 + 2 行卡片
        val totalHeight = (40f * density + 2 * (cardHeight + cardPadding * 2) + 16f * density).toInt()
        setMeasuredDimension(w, totalHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.backgroundColorInt)
        cardRects.clear()

        // 標題
        titlePaint.color = theme.keyLabelColorInt
        canvas.drawText("🎮 選擇遊戲", width / 2f, 28f * density, titlePaint)

        // 2x2 卡片排列
        val cardWidth = (width - cardPadding * 3) / 2f
        val startY = 40f * density

        games.forEachIndexed { i, mode ->
            val col = i % 2
            val row = i / 2
            val left = cardPadding + col * (cardWidth + cardPadding)
            val top = startY + row * (cardHeight + cardPadding)
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)
            cardRects.add(rect to mode)

            // 卡片背景
            cardPaint.color = cardColors[i]
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)

            // 圖示
            iconPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText(mode.icon, rect.centerX(), rect.centerY() - 2f * density, iconPaint)

            // 名稱
            labelPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText(mode.displayName, rect.centerX(), rect.bottom - 10f * density, labelPaint)

            // 最高分
            val highScore = scoreManager?.getHighScore(mode) ?: 0
            if (highScore > 0) {
                highScorePaint.color = 0xCCFFFFFF.toInt()
                canvas.drawText("最高: $highScore", rect.centerX(), rect.top + 14f * density, highScorePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = cardRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (hit != null) {
                onGameSelected(hit.second)
                return true
            }
        }
        return true
    }
}
