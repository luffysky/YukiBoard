package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 極簡彈幕 — 閃避 + 動作，15-30 秒一局。
 *
 * 玩家控制角色在鍵盤網格上閃避彈幕（紅色格子）。
 * 滑動移動，存活越久分數越高。
 * 彈幕模式：直線、扇形、旋轉。
 */
class BulletHellMiniGame(context: Context) : GameLogic {

    override val gameMode = GameMode.BULLET_HELL
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var playerRow = 0; private var playerCol = 0
    private val bullets = mutableListOf<Bullet>()
    private var bulletAccum = 0L
    private var survivalMs = 0L
    private var scoreAccum = 0L
    private var pattern = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); survivalMs = 0; pattern = 0; scoreAccum = 0
        playerRow = rows / 2; playerCol = cols / 2
        bullets.clear(); bulletAccum = 0
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return

        survivalMs += deltaMs

        // 每 500ms 加 1 分（存活獎勵）
        scoreAccum += deltaMs
        if (scoreAccum >= 500) {
            scoreManager.addScore((scoreAccum / 500).toInt())
            scoreAccum %= 500
        }

        bulletAccum += deltaMs
        val spawnRate = (600 - survivalMs / 100).coerceAtLeast(200)
        if (bulletAccum >= spawnRate) {
            bulletAccum -= spawnRate; spawnBullet()
        }

        // 移動彈幕
        val iter = bullets.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.moveAccum += deltaMs
            if (b.moveAccum >= 250) {
                b.moveAccum = 0
                b.row += b.dr; b.col += b.dc
                if (b.row !in 0 until rows || b.col !in 0 until cols) {
                    iter.remove(); continue
                }
                if (b.row == playerRow && b.col == playerCol) {
                    gameOver(); return
                }
            }
        }

        // 每 10 秒切換彈幕模式
        if (survivalMs / 10000 > pattern) {
            pattern = (survivalMs / 10000).toInt()
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        // 點按鍵 = 傳送到該位置
        if (row in 0 until rows && col in 0 until cols) {
            playerRow = row; playerCol = col
            // 傳送後立即檢查碰撞
            if (bullets.any { it.row == playerRow && it.col == playerCol }) {
                gameOver()
            }
        }
    }

    override fun onSwipe(direction: SwipeDirection) {
        when (direction) {
            SwipeDirection.UP -> playerRow = (playerRow - 1).coerceAtLeast(0)
            SwipeDirection.DOWN -> playerRow = (playerRow + 1).coerceAtMost(rows - 1)
            SwipeDirection.LEFT -> playerCol = (playerCol - 1).coerceAtLeast(0)
            SwipeDirection.RIGHT -> playerCol = (playerCol + 1).coerceAtMost(cols - 1)
        }
    }

    override fun destroy() { status = GameStatus.IDLE }

    private fun spawnBullet() {
        when (pattern % 3) {
            0 -> { // 從上方直線
                val col = Random.nextInt(cols)
                bullets.add(Bullet(0, col, 1, 0))
            }
            1 -> { // 從左側
                val row = Random.nextInt(rows)
                bullets.add(Bullet(row, 0, 0, 1))
            }
            2 -> { // 對角線
                val side = Random.nextInt(2)
                if (side == 0) bullets.add(Bullet(0, Random.nextInt(cols), 1, if (Random.nextBoolean()) 1 else -1))
                else bullets.add(Bullet(Random.nextInt(rows), 0, if (Random.nextBoolean()) 1 else -1, 1))
            }
        }
    }

    private fun gameOver() {
        status = GameStatus.GAME_OVER
        scoreManager.saveHighScoreIfNeeded(gameMode)
        onGameEvent?.invoke(GameEvent.GAME_OVER)
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c].reset()
    }

    private fun renderGrid() {
        clearGrid()
        // 彈幕
        for (b in bullets) {
            if (b.row in 0 until rows && b.col in 0 until cols) {
                grid[b.row][b.col].glowColor = 0xFFFF1744.toInt()
                grid[b.row][b.col].isActive = true
                grid[b.row][b.col].gameLabel = "•"
            }
        }
        // 玩家
        if (playerRow in 0 until rows && playerCol in 0 until cols) {
            grid[playerRow][playerCol].glowColor = 0xFF00E5FF.toInt()
            grid[playerRow][playerCol].isActive = true
            grid[playerRow][playerCol].gameLabel = "★"
        }
    }

    private data class Bullet(var row: Int, var col: Int, val dr: Int, val dc: Int, var moveAccum: Long = 0)
}
