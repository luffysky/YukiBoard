package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 戰艦巡航 — 放置 + 打字動力。
 *
 * 戰艦從左向右行駛，打字 = 提供動力。
 * 途中出現敵艦，打出指定字母開砲擊沉。
 * 戰艦血量歸零 = 失敗，到達終點 = 勝利。
 */
class WarshipCruiseGame(context: Context) : GameLogic {

    override val gameMode = GameMode.WARSHIP_CRUISE
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var shipCol = 0
    private var shipRow = 0
    private var hp = MAX_HP
    private var fuel = 0f
    private val enemies = mutableListOf<EnemyShip>()
    private var moveAccum = 0L
    private var enemyAccum = 0L

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); shipCol = 0; shipRow = rows / 2
        hp = MAX_HP; fuel = 0f; enemies.clear()
        moveAccum = 0; enemyAccum = 0
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return

        moveAccum += deltaMs
        if (fuel > 0 && moveAccum >= 500) {
            moveAccum -= 500; shipCol++; fuel = (fuel - 0.3f).coerceAtLeast(0f)
            scoreManager.addScore(5)
            if (shipCol >= cols * 3) { victory(); return }
        }

        enemyAccum += deltaMs
        if (enemyAccum >= 3000) {
            enemyAccum -= 3000; spawnEnemy()
        }

        // 敵艦逼近
        val iter = enemies.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.timerMs -= deltaMs
            if (e.timerMs <= 0) {
                hp--; iter.remove()
                scoreManager.resetCombo()
                onGameEvent?.invoke(GameEvent.HIT)
                if (hp <= 0) { gameOver(); return }
            }
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        val upper = label.uppercase()
        fuel = (fuel + 0.5f).coerceAtMost(5f)

        val hit = enemies.firstOrNull { it.letter == upper }
        if (hit != null) {
            enemies.remove(hit)
            scoreManager.addScore(25)
            scoreManager.incrementCombo()
            onGameEvent?.invoke(GameEvent.EAT)
        }
    }

    override fun onSwipe(direction: SwipeDirection) {
        when (direction) {
            SwipeDirection.UP -> shipRow = (shipRow - 1).coerceAtLeast(0)
            SwipeDirection.DOWN -> shipRow = (shipRow + 1).coerceAtMost(rows - 1)
            else -> {}
        }
    }

    override fun destroy() { status = GameStatus.IDLE }

    private fun spawnEnemy() {
        val row = Random.nextInt(rows)
        val letter = ('A' + Random.nextInt(26)).toString()
        enemies.add(EnemyShip(row, letter, 5000L))
    }

    private fun victory() {
        status = GameStatus.GAME_OVER
        scoreManager.addScore(100)
        scoreManager.saveHighScoreIfNeeded(gameMode)
        onGameEvent?.invoke(GameEvent.VICTORY)
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
        // 進度條（row 0）
        val progress = (shipCol.toFloat() / (cols * 3) * cols).toInt().coerceIn(0, cols)
        for (c in 0 until progress) {
            grid[0][c].glowColor = 0xFF76FF03.toInt()
        }
        // 戰艦位置（固定在左側，進度用進度條表示）
        val displayCol = 1.coerceAtMost(cols - 1)
        if (shipRow in 0 until rows) {
            grid[shipRow][displayCol].isActive = true
            grid[shipRow][displayCol].glowColor = 0xFF1E88E5.toInt()
            grid[shipRow][displayCol].gameLabel = "🚢"
        }
        // 敵艦（根據剩餘時間分散在右半邊）
        for ((idx, e) in enemies.withIndex()) {
            val eCol = (cols - 2 - idx % 3).coerceIn(displayCol + 1, cols - 1)
            if (e.row in 0 until rows && eCol in 0 until cols) {
                grid[e.row][eCol].isActive = true
                grid[e.row][eCol].glowColor = 0xFFFF5252.toInt()
                grid[e.row][eCol].gameLabel = e.letter
                if (e.timerMs < 2000) grid[e.row][eCol].animState = KeyCell.ANIM_SHAKE
            }
        }
        // HP（左下角）
        val hpRow = (rows - 1).coerceAtLeast(0)
        for (i in 0 until hp.coerceAtMost(cols)) {
            grid[hpRow][i].gameLabel = "♥"
            grid[hpRow][i].glowColor = 0xFF4CAF50.toInt()
        }
    }

    private data class EnemyShip(val row: Int, val letter: String, var timerMs: Long)

    companion object { const val MAX_HP = 5 }
}
