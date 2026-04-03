package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 鍵盤旅行者 — 跑酷 + 打字動力。
 *
 * 角色在鍵盤最左列自動向右跑，打字加速。
 * 障礙物隨機出現，按對應鍵跳過。打錯減速。
 * 跑完一定距離 = 過關。
 */
class KeyboardTravelerGame(context: Context) : GameLogic {

    override val gameMode = GameMode.KEYBOARD_TRAVELER
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var playerRow = 0
    private var distance = 0
    private var speed = 1.0f
    private var moveAccum = 0L
    private val obstacles = mutableListOf<Obstacle>()
    private var obstacleAccum = 0L

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); distance = 0; speed = 1.0f
        playerRow = rows / 2; obstacles.clear()
        moveAccum = 0; obstacleAccum = 0
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return

        moveAccum += deltaMs
        val interval = (400 / speed).toLong().coerceAtLeast(80)
        if (moveAccum >= interval) {
            moveAccum -= interval; distance++
            scoreManager.addScore(1)
            // 速度隨時間自然衰減（不打字就減速）
            speed = (speed - 0.005f).coerceAtLeast(0.5f)
            moveObstacles()
        }

        obstacleAccum += deltaMs
        if (obstacleAccum >= 2000) {
            obstacleAccum -= 2000; spawnObstacle()
        }

        if (distance >= GOAL_DISTANCE) {
            status = GameStatus.GAME_OVER
            scoreManager.saveHighScoreIfNeeded(gameMode)
            onGameEvent?.invoke(GameEvent.VICTORY)
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        // 打字 = 加速
        speed = (speed + 0.15f).coerceAtMost(3.0f)
        scoreManager.incrementCombo()

        // 如果按的鍵跟障礙物匹配 → 消除
        val hit = obstacles.firstOrNull { it.row == row && it.col == 1 && it.label == label.uppercase() }
        if (hit != null) {
            obstacles.remove(hit)
            scoreManager.addScore(20)
            onGameEvent?.invoke(GameEvent.PERFECT)
        }
    }

    override fun onSwipe(direction: SwipeDirection) {
        when (direction) {
            SwipeDirection.UP -> playerRow = (playerRow - 1).coerceAtLeast(0)
            SwipeDirection.DOWN -> playerRow = (playerRow + 1).coerceAtMost(rows - 1)
            else -> {}
        }
    }

    override fun destroy() { status = GameStatus.IDLE }

    private fun spawnObstacle() {
        val row = Random.nextInt(rows)
        val label = ('A' + Random.nextInt(26)).toString()
        obstacles.add(Obstacle(row, cols - 1, label))
    }

    private fun moveObstacles() {
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val o = iterator.next()
            o.col--
            if (o.col < 0) {
                iterator.remove()
                continue
            }
            if (o.col == 0 && o.row == playerRow) {
                speed = (speed - 0.5f).coerceAtLeast(0.3f)
                scoreManager.resetCombo()
                onGameEvent?.invoke(GameEvent.HIT)
                iterator.remove()
            }
        }
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c].reset()
    }

    private fun renderGrid() {
        clearGrid()
        // 玩家
        if (playerRow in 0 until rows) {
            grid[playerRow][0].isActive = true
            grid[playerRow][0].glowColor = 0xFF00E5FF.toInt()
            grid[playerRow][0].gameLabel = "🏃"
        }
        // 障礙物
        for (o in obstacles) {
            if (o.row in 0 until rows && o.col in 0 until cols) {
                grid[o.row][o.col].isActive = true
                grid[o.row][o.col].glowColor = 0xFFFF1744.toInt()
                grid[o.row][o.col].gameLabel = o.label
            }
        }
        // 進度條（最上行）
        val progress = (distance.toFloat() / GOAL_DISTANCE * cols).toInt().coerceAtMost(cols)
        for (c in 0 until progress) {
            if (grid[0][c].glowColor == 0) {
                grid[0][c].glowColor = 0xFF76FF03.toInt()
            }
        }
    }

    private data class Obstacle(val row: Int, var col: Int, val label: String)

    companion object {
        const val GOAL_DISTANCE = 200
    }
}
