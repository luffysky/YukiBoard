package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 按鍵掃雷 — 策略 + 運氣。
 *
 * 鍵盤格子下藏地雷，按鍵翻開。
 * 數字表示周圍地雷數，翻到雷 = 遊戲結束。
 * 翻開所有安全格 = 勝利。
 */
class KeyboardMinesweeperGame(context: Context) : GameLogic {

    override val gameMode = GameMode.KEYBOARD_MINESWEEPER
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private lateinit var mines: Array<BooleanArray>
    private lateinit var revealed: Array<BooleanArray>
    private lateinit var flagged: Array<BooleanArray>
    private var mineCount = 0
    private var safeCount = 0
    private var revealedCount = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); revealedCount = 0
        mines = Array(rows) { BooleanArray(cols) }
        revealed = Array(rows) { BooleanArray(cols) }
        flagged = Array(rows) { BooleanArray(cols) }

        mineCount = (rows * cols * MINE_RATIO).toInt().coerceAtLeast(3)
        safeCount = rows * cols - mineCount

        // 隨機放雷
        var placed = 0
        while (placed < mineCount) {
            val r = Random.nextInt(rows); val c = Random.nextInt(cols)
            if (!mines[r][c]) { mines[r][c] = true; placed++ }
        }

        clearGrid(); renderGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }
    override fun update(deltaMs: Long) { if (status == GameStatus.PLAYING) renderGrid() }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        if (row !in 0 until rows || col !in 0 until cols) return
        if (revealed[row][col] || flagged[row][col]) return

        if (mines[row][col]) {
            // 踩雷
            revealAll()
            status = GameStatus.GAME_OVER
            scoreManager.saveHighScoreIfNeeded(gameMode)
            onGameEvent?.invoke(GameEvent.GAME_OVER)
            return
        }

        reveal(row, col)
        scoreManager.addScore(10)

        if (revealedCount >= safeCount) {
            status = GameStatus.GAME_OVER
            scoreManager.addScore(mineCount * 50)
            scoreManager.saveHighScoreIfNeeded(gameMode)
            onGameEvent?.invoke(GameEvent.VICTORY)
        }
    }

    override fun onSwipe(direction: SwipeDirection) {
        // 長按可用滑動替代：標記旗幟（簡化版不實作）
    }

    override fun destroy() { status = GameStatus.IDLE }

    private fun reveal(r: Int, c: Int) {
        if (r !in 0 until rows || c !in 0 until cols) return
        if (revealed[r][c] || mines[r][c]) return

        revealed[r][c] = true
        revealedCount++

        val adj = countAdjacent(r, c)
        if (adj == 0) {
            // 遞迴展開
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                reveal(r + dr, c + dc)
            }
        }
    }

    private fun countAdjacent(r: Int, c: Int): Int {
        var count = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr; val nc = c + dc
            if (nr in 0 until rows && nc in 0 until cols && mines[nr][nc]) count++
        }
        return count
    }

    private fun revealAll() {
        for (r in 0 until rows) for (c in 0 until cols) revealed[r][c] = true
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c].reset()
    }

    private fun renderGrid() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = grid[r][c]
                if (!revealed[r][c]) {
                    cell.gameLabel = "■"
                    cell.glowColor = 0xFF546E7A.toInt()
                    cell.isActive = false
                } else if (mines[r][c]) {
                    cell.gameLabel = "💣"
                    cell.glowColor = 0xFFFF1744.toInt()
                    cell.isActive = true
                    cell.animState = KeyCell.ANIM_EXPLODE
                } else {
                    val adj = countAdjacent(r, c)
                    cell.gameLabel = if (adj > 0) adj.toString() else ""
                    cell.glowColor = when (adj) {
                        1 -> 0xFF42A5F5.toInt()
                        2 -> 0xFF66BB6A.toInt()
                        3 -> 0xFFFF7043.toInt()
                        in 4..8 -> 0xFFE53935.toInt()
                        else -> 0x00000000
                    }
                    cell.isActive = adj > 0
                }
            }
        }
    }

    companion object { const val MINE_RATIO = 0.18f }
}
