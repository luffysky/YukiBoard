package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 記憶翻牌 — 配對 + 計時。
 *
 * 鍵盤格子下藏配對符號，翻兩張：
 * - 相同 = 消除 + 加分
 * - 不同 = 翻回 + 扣時間
 * 全部消除 = 勝利。
 */
class MemoryFlipGame(context: Context) : GameLogic {

    override val gameMode = GameMode.MEMORY_FLIP
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = GAME_DURATION_MS
    private lateinit var symbols: Array<Array<String>>
    private lateinit var isMatched: Array<BooleanArray>
    private var firstFlip: Pair<Int, Int>? = null
    private var secondFlip: Pair<Int, Int>? = null
    private var flipCooldown = 0L
    private var pairsLeft = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); timeLeftMs = GAME_DURATION_MS
        firstFlip = null; secondFlip = null; flipCooldown = 0
        isMatched = Array(rows) { BooleanArray(cols) }
        generatePairs()
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { gameOver(); return }

        if (flipCooldown > 0) {
            flipCooldown -= deltaMs
            if (flipCooldown <= 0) {
                // 翻回
                firstFlip = null; secondFlip = null
            }
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        if (flipCooldown > 0) return
        if (row !in 0 until rows || col !in 0 until cols) return
        if (isMatched[row][col]) return

        val pos = row to col
        if (firstFlip == null) {
            firstFlip = pos
        } else if (secondFlip == null && pos != firstFlip) {
            secondFlip = pos
            val (r1, c1) = firstFlip!!
            val (r2, c2) = secondFlip!!

            if (symbols[r1][c1] == symbols[r2][c2]) {
                // 配對成功
                isMatched[r1][c1] = true
                isMatched[r2][c2] = true
                pairsLeft--
                scoreManager.addScore(20)
                scoreManager.incrementCombo()
                firstFlip = null; secondFlip = null
                onGameEvent?.invoke(GameEvent.PERFECT)

                if (pairsLeft <= 0) {
                    status = GameStatus.GAME_OVER
                    scoreManager.addScore((timeLeftMs / 1000).toInt() * 5)
                    scoreManager.saveHighScoreIfNeeded(gameMode)
                    onGameEvent?.invoke(GameEvent.VICTORY)
                }
            } else {
                // 不配對
                flipCooldown = 800
                scoreManager.resetCombo()
                timeLeftMs = (timeLeftMs - 1000).coerceAtLeast(0)
                onGameEvent?.invoke(GameEvent.MISS)
            }
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun generatePairs() {
        val totalCells = rows * cols
        val pairCount = totalCells / 2
        pairsLeft = pairCount

        val pool = SYMBOLS.take(pairCount.coerceAtMost(SYMBOLS.size))
        val allSymbols = (pool + pool).toMutableList()
        // 如果格子是奇數，加一個佔位
        while (allSymbols.size < totalCells) allSymbols.add("★")
        allSymbols.shuffle()

        symbols = Array(rows) { r -> Array(cols) { c -> allSymbols[r * cols + c] } }
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
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = grid[r][c]
                val pos = r to c
                when {
                    isMatched[r][c] -> {
                        cell.gameLabel = "✓"
                        cell.glowColor = 0xFF00E676.toInt()
                        cell.isActive = false
                    }
                    pos == firstFlip || pos == secondFlip -> {
                        cell.gameLabel = symbols[r][c]
                        cell.glowColor = 0xFFFFEB3B.toInt()
                        cell.isActive = true
                    }
                    else -> {
                        cell.gameLabel = "?"
                        cell.glowColor = 0xFF455A64.toInt()
                        cell.isActive = false
                    }
                }
            }
        }
    }

    companion object {
        const val GAME_DURATION_MS = 90_000L
        private val SYMBOLS = listOf(
            "🐱", "🐶", "🐰", "🦊", "🐻", "🐼", "🐨", "🦁",
            "🌟", "🌙", "☀️", "❄️", "🔥", "💧", "🌸", "🍀",
            "💎", "🎵", "🎮", "📚"
        )
    }
}
