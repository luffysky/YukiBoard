package com.snowrealm.yukiboard.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.KeyModel
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import kotlin.math.abs
import kotlin.math.sin

/**
 * 遊戲鍵盤 View — 在按鍵網格上渲染遊戲元素。
 *
 * 覆蓋 KeyboardView 在遊戲模式使用。
 * 每個按鍵根據 KeyCell 狀態繪製發光、動畫、覆蓋標籤。
 */
class GameKeyboardView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f * density
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val hudLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 12f * density
    }
    private val hudRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 12f * density
    }

    private var theme = KeyboardTheme.default()
    private var game: GameLogic? = null
    private val keyRows: List<List<KeyModel>> = KeyboardState.defaultQwertyRows()

    // 按鍵位置快取
    private val keyRectCache = mutableListOf<Triple<RectF, Int, Int>>() // rect, row, col
    private val keyLabelMap = mutableMapOf<Pair<Int, Int>, String>()    // (row,col) → label

    private val hudHeight = 32f * density
    private var rowHeight = 44f * density
    private val keyPaddingH = 3f * density
    private val keyPaddingV = 3f * density
    private val cornerRadius = 8f * density

    var hudText: String = ""
    var onBackToMenu: (() -> Unit)? = null

    // 滑動偵測
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) > abs(dy)) {
                game?.onSwipe(if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT)
            } else {
                game?.onSwipe(if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP)
            }
            return true
        }
    })

    fun setTheme(t: KeyboardTheme) {
        theme = t
    }

    fun attachGame(gameLogic: GameLogic) {
        game = gameLogic
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        // 如果父容器有固定高度，動態計算 rowHeight 使所有行都能顯示
        if (hMode == MeasureSpec.EXACTLY && hSize > 0 && keyRows.isNotEmpty()) {
            rowHeight = ((hSize - hudHeight) / keyRows.size).coerceAtLeast(28f * density)
            setMeasuredDimension(w, hSize)
        } else {
            val totalHeight = (hudHeight + keyRows.size * rowHeight).toInt()
            setMeasuredDimension(w, totalHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.backgroundColorInt)
        drawHUD(canvas)
        drawGameGrid(canvas)
    }

    private fun drawHUD(canvas: Canvas) {
        // HUD 背景
        keyPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), hudHeight, keyPaint)

        val g = game ?: return
        val sm = g.scoreManager

        // 左：分數
        hudLeftPaint.color = theme.keyLabelColorInt
        canvas.drawText("分數: ${sm.currentScore}", 12f * density, hudHeight * 0.65f, hudLeftPaint)

        // 中：連擊 & 狀態
        if (sm.combo > 1) {
            hudPaint.color = sm.getComboColor()
            canvas.drawText("${sm.combo}x 連擊!", width / 2f, hudHeight * 0.65f, hudPaint)
        } else if (hudText.isNotEmpty()) {
            hudPaint.color = theme.composingTextColorInt
            canvas.drawText(hudText, width / 2f, hudHeight * 0.65f, hudPaint)
        }

        // 右：時間/狀態
        hudRightPaint.color = theme.candidateTextColorInt
        val statusText = when (g.status) {
            GameStatus.PLAYING -> {
                when (g) {
                    is WhackAMoleGame -> "${g.remainingSeconds()}s"
                    is TypingChallengeGame -> "${g.remainingSeconds()}s | ${(g.accuracy() * 100).toInt()}%"
                    else -> "▶"
                }
            }
            GameStatus.GAME_OVER -> "結束!"
            GameStatus.PAUSED -> "暫停"
            else -> ""
        }
        canvas.drawText(statusText, width.toFloat() - 12f * density, hudHeight * 0.65f, hudRightPaint)
    }

    private fun drawGameGrid(canvas: Canvas) {
        keyRectCache.clear()
        keyLabelMap.clear()

        val g = game ?: return
        val grid = g.grid

        val totalUnits = keyRows.maxOfOrNull { row ->
            row.sumOf { it.widthWeight.toDouble() }.toFloat()
        } ?: 10f

        keyRows.forEachIndexed { rowIndex, row ->
            val top = hudHeight + rowIndex * rowHeight
            var x = 0f
            val unitWidth = width.toFloat() / totalUnits

            row.forEachIndexed { colIndex, key ->
                val keyWidth = unitWidth * key.widthWeight
                val rect = RectF(
                    x + keyPaddingH,
                    top + keyPaddingV,
                    x + keyWidth - keyPaddingH,
                    top + rowHeight - keyPaddingV
                )
                keyRectCache.add(Triple(rect, rowIndex, colIndex))
                keyLabelMap[Pair(rowIndex, colIndex)] = key.label

                // 基底按鍵色
                keyPaint.color = if (key.isSpecial) theme.specialKeyColorInt else theme.keyColorInt
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyPaint)

                // 遊戲覆蓋層
                if (rowIndex < grid.size && colIndex < grid[rowIndex].size) {
                    val cell = grid[rowIndex][colIndex]
                    drawCellOverlay(canvas, rect, cell, key.label)
                } else {
                    // 超出遊戲網格的按鍵正常顯示
                    labelPaint.color = theme.keyLabelColorInt
                    val textY = rect.top + (rect.height() + labelPaint.textSize) / 2f - labelPaint.descent()
                    canvas.drawText(key.label, rect.centerX(), textY, labelPaint)
                }

                x += keyWidth
            }
        }
    }

    private fun drawCellOverlay(canvas: Canvas, rect: RectF, cell: KeyCell, originalLabel: String) {
        // 發光層
        if (cell.glowColor != 0) {
            glowPaint.color = cell.glowColor

            // 動畫效果
            when (cell.animState) {
                KeyCell.ANIM_PULSE -> {
                    val alpha = (180 + 75 * sin(cell.animProgress * Math.PI * 2).toFloat()).toInt()
                    glowPaint.color = (cell.glowColor and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glowPaint)
                }
                KeyCell.ANIM_EXPLODE -> {
                    val expand = cell.animProgress * 4f * density
                    val alpha = (255 * (1f - cell.animProgress)).toInt()
                    glowPaint.color = (cell.glowColor and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
                    val expandedRect = RectF(
                        rect.left - expand, rect.top - expand,
                        rect.right + expand, rect.bottom + expand
                    )
                    canvas.drawRoundRect(expandedRect, cornerRadius, cornerRadius, glowPaint)
                }
                KeyCell.ANIM_FADE -> {
                    val alpha = (200 * (1f - cell.animProgress)).toInt()
                    glowPaint.color = (cell.glowColor and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glowPaint)
                }
                KeyCell.ANIM_SHAKE -> {
                    val shakeX = sin(cell.animProgress * Math.PI * 6).toFloat() * 3f * density
                    val shakeRect = RectF(rect.left + shakeX, rect.top, rect.right + shakeX, rect.bottom)
                    glowPaint.color = (cell.glowColor and 0x00FFFFFF) or 0xBB000000.toInt()
                    canvas.drawRoundRect(shakeRect, cornerRadius, cornerRadius, glowPaint)
                }
                else -> {
                    glowPaint.color = (cell.glowColor and 0x00FFFFFF) or 0xAA000000.toInt()
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glowPaint)
                }
            }
        }

        // 標籤
        val displayLabel = cell.gameLabel ?: originalLabel
        labelPaint.color = if (cell.glowColor != 0) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
        val textY = rect.top + (rect.height() + labelPaint.textSize) / 2f - labelPaint.descent()
        canvas.drawText(displayLabel, rect.centerX(), textY, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            val hit = keyRectCache.firstOrNull { it.first.contains(event.x, event.y) }
            if (hit != null) {
                val (_, row, col) = hit
                val label = keyLabelMap[Pair(row, col)] ?: ""
                game?.onKeyPress(row, col, label)
                performClick()
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** View 從視窗移除時通知外部清理遊戲迴圈 */
    var onDetached: (() -> Unit)? = null

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        game?.destroy()
        game = null
        onDetached?.invoke()
    }
}
