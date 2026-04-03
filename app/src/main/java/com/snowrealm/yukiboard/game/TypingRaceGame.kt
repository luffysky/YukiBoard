package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 打字競速 v1.0 單機版。
 *
 * 顯示一段文字，玩家盡快打完。
 * 計算 WPM（每分鐘字數）和準確率。
 * 打錯不自動跳過，必須打對才進下一字。
 */
class TypingRaceGame(context: Context) : GameLogic {

    override val gameMode = GameMode.TYPING_RACE
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var targetText = ""
    private var currentIndex = 0
    private var correctCount = 0
    private var totalKeystrokes = 0
    private var elapsedMs = 0L

    var onGameEvent: ((GameEvent) -> Unit)? = null

    /** 最後一局的 WPM */
    var lastWpm = 0f; private set
    /** 最後一局的準確率 */
    var lastAccuracy = 0f; private set

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); currentIndex = 0; correctCount = 0
        totalKeystrokes = 0; lastWpm = 0f; lastAccuracy = 0f
        targetText = SENTENCES[Random.nextInt(SENTENCES.size)]
        elapsedMs = 0
        clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        elapsedMs += deltaMs
        // 120 秒逾時保護
        if (elapsedMs >= MAX_TIME_MS) {
            finish()
            return
        }
        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        if (currentIndex >= targetText.length) return
        totalKeystrokes++

        val expected = targetText[currentIndex]
        if (label.equals(expected.toString(), ignoreCase = true) ||
            (label == " " && expected == ' ')) {
            currentIndex++; correctCount++
            scoreManager.addScore(5)
            scoreManager.incrementCombo()

            if (currentIndex >= targetText.length) {
                finish()
            }
        } else {
            scoreManager.resetCombo()
            onGameEvent?.invoke(GameEvent.MISS)
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun finish() {
        val minutes = elapsedMs / 60000f
        val wordCount = targetText.split(" ").size
        lastWpm = if (minutes > 0) wordCount / minutes else 0f
        lastAccuracy = if (totalKeystrokes > 0) correctCount.toFloat() / totalKeystrokes * 100 else 0f

        scoreManager.addScore((lastWpm * 10).toInt())
        status = GameStatus.GAME_OVER
        scoreManager.saveHighScoreIfNeeded(gameMode)
        onGameEvent?.invoke(GameEvent.VICTORY)
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c].reset()
    }

    private fun renderGrid() {
        clearGrid()
        val charsPerRow = cols
        // 顯示目標文字在網格上
        for (i in targetText.indices) {
            val r = i / charsPerRow
            val c = i % charsPerRow
            if (r >= rows) break
            val cell = grid[r][c]
            cell.gameLabel = targetText[i].toString()
            cell.glowColor = when {
                i < currentIndex -> 0xFF00E676.toInt()    // 已打對
                i == currentIndex -> 0xFFFFEB3B.toInt()   // 目前
                else -> 0xFF616161.toInt()                // 未打
            }
            cell.isActive = i == currentIndex
            if (i == currentIndex) cell.animState = KeyCell.ANIM_PULSE
        }
    }

    companion object {
        const val MAX_TIME_MS = 120_000L
        private val SENTENCES = listOf(
            "the quick brown fox jumps over the lazy dog",
            "snow falls gently on the keyboard tonight",
            "yuki is the best keyboard companion ever",
            "practice makes perfect keep typing fast",
            "coding is fun when you have a great keyboard",
            "hello world this is my first program",
            "the weather is nice today lets go outside",
            "never give up and keep moving forward",
            "type faster and you will become a pro",
            "android kotlin jetpack compose material"
        )
    }
}
