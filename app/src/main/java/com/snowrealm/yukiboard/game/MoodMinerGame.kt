package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 情緒採礦 — 被動收集 + 週報。
 *
 * 不同顏色礦石代表不同情緒，隨機出現在鍵盤上。
 * 按下對應鍵收集礦石。累積礦石解鎖情緒報告。
 * 節奏平緩，適合放鬆遊玩。
 */
class MoodMinerGame(context: Context) : GameLogic {

    override val gameMode = GameMode.MOOD_MINER
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = GAME_DURATION_MS
    private val minerals = mutableListOf<Mineral>()
    private var spawnAccum = 0L
    private val collected = mutableMapOf<MoodType, Int>()

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); timeLeftMs = GAME_DURATION_MS
        minerals.clear(); collected.clear(); spawnAccum = 0
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return

        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { gameOver(); return }

        spawnAccum += deltaMs
        if (spawnAccum >= SPAWN_INTERVAL) {
            spawnAccum -= SPAWN_INTERVAL
            if (minerals.size < MAX_MINERALS) spawnMineral()
        }

        // 礦石閃爍過期（過期則重置連擊）
        val beforeSize = minerals.size
        minerals.removeAll { m ->
            m.lifeMs -= deltaMs
            m.lifeMs <= 0
        }
        if (minerals.size < beforeSize) scoreManager.resetCombo()

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        val hit = minerals.firstOrNull { it.row == row && it.col == col }
        if (hit != null) {
            minerals.remove(hit)
            collected[hit.mood] = (collected[hit.mood] ?: 0) + 1
            scoreManager.addScore(hit.mood.points)
            scoreManager.incrementCombo()
            onGameEvent?.invoke(GameEvent.EAT)
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    fun getCollectionSummary(): Map<MoodType, Int> = collected.toMap()

    private fun spawnMineral() {
        val row = Random.nextInt(rows)
        val col = Random.nextInt(cols)
        if (minerals.any { it.row == row && it.col == col }) return
        val mood = MoodType.entries[Random.nextInt(MoodType.entries.size)]
        minerals.add(Mineral(row, col, mood, MINERAL_LIFE_MS))
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
        for (m in minerals) {
            if (m.row in 0 until rows && m.col in 0 until cols) {
                val cell = grid[m.row][m.col]
                cell.isActive = true
                cell.glowColor = m.mood.color
                cell.gameLabel = m.mood.icon
                cell.animState = if (m.lifeMs < 2000) KeyCell.ANIM_PULSE else KeyCell.ANIM_NONE
            }
        }
    }

    private data class Mineral(val row: Int, val col: Int, val mood: MoodType, var lifeMs: Long)

    enum class MoodType(val icon: String, val color: Int, val points: Int) {
        JOY("😊", 0xFFFFEB3B.toInt(), 10),
        CALM("😌", 0xFF81D4FA.toInt(), 8),
        ENERGY("⚡", 0xFFFF9800.toInt(), 12),
        LOVE("💖", 0xFFE91E63.toInt(), 15),
        FOCUS("🎯", 0xFF7C4DFF.toInt(), 20)
    }

    companion object {
        const val GAME_DURATION_MS = 60_000L
        const val SPAWN_INTERVAL = 1500L
        const val MINERAL_LIFE_MS = 5000L
        const val MAX_MINERALS = 6
    }
}
