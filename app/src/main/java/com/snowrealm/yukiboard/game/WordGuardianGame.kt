package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 詞彙守護者 — 塔防 + 打字。
 *
 * 敵人（詞彙）從左側出發向右推進，玩家打出對應字母消滅。
 * 敵人到達右端 = 扣血，血量歸零 = 遊戲結束。
 */
class WordGuardianGame(context: Context) : GameLogic {

    override val gameMode = GameMode.WORD_GUARDIAN
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0
    private var cols = 0
    private var hp = MAX_HP
    private val enemies = mutableListOf<Enemy>()
    private var spawnAccum = 0L
    private var moveAccum = 0L
    private var spawnInterval = 3000L
    private var moveInterval = 1200L
    private var wave = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); hp = MAX_HP; wave = 0
        enemies.clear(); spawnAccum = 0; moveAccum = 0
        spawnInterval = 3000L; moveInterval = 1200L
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return

        spawnAccum += deltaMs
        if (spawnAccum >= spawnInterval) {
            spawnAccum -= spawnInterval; spawnEnemy()
            wave++
            if (wave % 5 == 0) { spawnInterval = (spawnInterval - 200).coerceAtLeast(800) }
            // 存活 50 波 = 勝利
            if (wave >= MAX_WAVES && enemies.isEmpty()) {
                status = GameStatus.GAME_OVER
                scoreManager.addScore(200)
                scoreManager.saveHighScoreIfNeeded(gameMode)
                onGameEvent?.invoke(GameEvent.VICTORY)
                return
            }
        }

        moveAccum += deltaMs
        if (moveAccum >= moveInterval) {
            moveAccum -= moveInterval; moveEnemies()
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        val upper = label.uppercase()
        val hit = enemies.firstOrNull { it.currentChar().toString() == upper }
        if (hit != null) {
            hit.charIndex++
            if (hit.charIndex >= hit.word.length) {
                enemies.remove(hit)
                scoreManager.addScore(hit.word.length * 10)
                scoreManager.incrementCombo()
                onGameEvent?.invoke(GameEvent.EAT)
            }
        } else {
            scoreManager.resetCombo()
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun spawnEnemy() {
        val word = WORDS[Random.nextInt(WORDS.size)]
        val row = Random.nextInt(rows)
        enemies.add(Enemy(word, row, 0, 0))
    }

    private fun moveEnemies() {
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.col++
            if (e.col >= cols) {
                hp--; iterator.remove()
                onGameEvent?.invoke(GameEvent.HIT)
                if (hp <= 0) { gameOver(); return }
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
        // HP 顯示在最右列
        for (r in 0 until minOf(hp, rows)) {
            grid[r][cols - 1].glowColor = 0xFF4CAF50.toInt()
            grid[r][cols - 1].gameLabel = "♥"
        }
        // 敵人
        for (e in enemies) {
            if (e.row in 0 until rows && e.col in 0 until cols) {
                val cell = grid[e.row][e.col]
                cell.isActive = true
                cell.glowColor = 0xFFFF5722.toInt()
                cell.gameLabel = e.currentChar().toString()
                cell.animState = KeyCell.ANIM_PULSE
            }
        }
    }

    private data class Enemy(val word: String, val row: Int, var col: Int, var charIndex: Int) {
        fun currentChar(): Char = word[charIndex.coerceIn(0, word.length - 1)]
    }

    companion object {
        const val MAX_HP = 5
        const val MAX_WAVES = 50
        private val WORDS = listOf(
            "CAT", "DOG", "FIRE", "SNOW", "RAIN", "STAR", "MOON", "SUN",
            "CODE", "PLAY", "GAME", "TYPE", "WORD", "LOVE", "COOL", "FAST",
            "JUMP", "HERO", "DARK", "LITE", "YUKI", "ICE", "KEY", "WIN"
        )
    }
}
