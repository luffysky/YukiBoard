package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 打字農場 — 輸入文字澆水種田。
 *
 * 規則：
 * - 鍵盤變成田地，每格有不同作物種子
 * - 按下對應按鍵 = 澆水，作物從 🌱→🌿→🌻→🍎 四階段成長
 * - 45 秒內收穫越多越好
 */
class TypingFarmGame(context: Context) : GameLogic {

    override val gameMode = GameMode.TYPING_FARM
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = 45_000L
    private var harvested = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    private data class Crop(var stage: Int = 0, var watered: Int = 0) {
        val icon: String get() = when (stage) {
            0 -> "🌱"
            1 -> "🌿"
            2 -> "🌻"
            3 -> "🍎"
            else -> "✨"
        }
        val needWater: Int get() = when (stage) {
            0 -> 2
            1 -> 3
            2 -> 4
            else -> Int.MAX_VALUE
        }
    }

    private var crops = arrayOf<Array<Crop>>()

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        timeLeftMs = 45_000L
        harvested = 0
        // 從第 1 行開始種田（第 0 行顯示 HUD）
        crops = Array(rows) { Array(cols) { Crop() } }
        status = GameStatus.PLAYING
        renderGrid()
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { status = GameStatus.GAME_OVER }
        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        if (row < 1 || row >= rows || col < 0 || col >= cols) return

        val crop = crops[row][col]
        if (crop.stage > 3) return // 已收穫

        if (crop.stage == 3) {
            // 收穫！
            crop.stage = 4
            harvested++
            scoreManager.addScore(50)
            onGameEvent?.invoke(GameEvent.PERFECT)
            // 重新種
            crops[row][col] = Crop()
        } else {
            // 澆水
            crop.watered++
            if (crop.watered >= crop.needWater) {
                crop.stage++
                crop.watered = 0
                scoreManager.addScore(10)
                onGameEvent?.invoke(GameEvent.GOOD)
            }
        }
        renderGrid()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        clearGrid()

        setRowText(0, "🌱 農場 收穫$harvested  ⏱${timeLeftMs / 1000}s")

        // 顯示作物
        for (r in 1 until rows) {
            for (c in 0 until cols) {
                val crop = crops[r][c]
                val color = when (crop.stage) {
                    0 -> 0xFF4CAF50.toInt()
                    1 -> 0xFF66BB6A.toInt()
                    2 -> 0xFFFFC107.toInt()
                    3 -> 0xFFFF5722.toInt()
                    else -> 0
                }
                grid[r][c] = grid[r][c].copy(
                    gameLabel = crop.icon,
                    glowColor = color,
                    isActive = crop.stage == 3
                )
            }
        }

        if (status == GameStatus.GAME_OVER) {
            val msgRow = (rows / 2).coerceIn(1, rows - 1)
            setRowText(msgRow, "🎉 收穫${harvested}個 +${scoreManager.currentScore}")
        }
    }

    private fun setRowText(row: Int, text: String) {
        if (row >= rows) return
        for (c in 0 until cols.coerceAtMost(text.length)) {
            grid[row][c] = grid[row][c].copy(gameLabel = text[c].toString())
        }
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) {
            grid[r][c] = KeyCell(r, c)
        }
    }
}
