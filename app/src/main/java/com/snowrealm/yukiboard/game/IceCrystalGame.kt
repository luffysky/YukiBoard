package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 冰晶收集 — 輸入連動 + 主題解鎖。
 *
 * 冰晶隨機出現在鍵盤上，打出對應字母收集。
 * 收集特定數量解鎖凜空紫主題。
 * 冰晶會融化（倒計時消失），手速要快！
 */
class IceCrystalGame(context: Context) : GameLogic {

    override val gameMode = GameMode.ICE_CRYSTAL
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = GAME_DURATION_MS
    private val crystals = mutableListOf<Crystal>()
    private var spawnAccum = 0L
    private var totalCollected = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); timeLeftMs = GAME_DURATION_MS
        crystals.clear(); totalCollected = 0; spawnAccum = 0
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return

        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { gameOver(); return }

        spawnAccum += deltaMs
        if (spawnAccum >= 1200 && crystals.size < MAX_CRYSTALS) {
            spawnAccum -= 1200; spawnCrystal()
        }

        // 冰晶融化
        crystals.removeAll { c ->
            c.meltMs -= deltaMs
            c.meltMs <= 0
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        // 優先匹配位置，其次匹配字母
        val upper = label.uppercase()
        val hit = crystals.firstOrNull { it.row == row && it.col == col }
            ?: crystals.firstOrNull { it.letter == upper }
        if (hit != null) {
            crystals.remove(hit)
            totalCollected++
            val bonus = if (hit.isRare) 30 else 10
            scoreManager.addScore(bonus)
            scoreManager.incrementCombo()
            onGameEvent?.invoke(if (hit.isRare) GameEvent.PERFECT else GameEvent.EAT)
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    fun getTotalCollected(): Int = totalCollected

    private fun spawnCrystal() {
        val row = Random.nextInt(rows)
        val col = Random.nextInt(cols)
        if (crystals.any { it.row == row && it.col == col }) return
        val letter = ('A' + Random.nextInt(26)).toString()
        val isRare = Random.nextFloat() < 0.15f
        val meltTime = if (isRare) 2500L else 4000L
        crystals.add(Crystal(row, col, letter, meltTime, isRare))
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
        for (c in crystals) {
            if (c.row in 0 until rows && c.col in 0 until cols) {
                val cell = grid[c.row][c.col]
                cell.isActive = true
                cell.glowColor = if (c.isRare) 0xFFE040FB.toInt() else 0xFF80DEEA.toInt()
                cell.gameLabel = if (c.isRare) "💎" else "❄"
                cell.animState = if (c.meltMs < 1500) KeyCell.ANIM_FADE else KeyCell.ANIM_PULSE
            }
        }
    }

    private data class Crystal(val row: Int, val col: Int, val letter: String, var meltMs: Long, val isRare: Boolean)

    companion object {
        const val GAME_DURATION_MS = 45_000L
        const val MAX_CRYSTALS = 8
    }
}
