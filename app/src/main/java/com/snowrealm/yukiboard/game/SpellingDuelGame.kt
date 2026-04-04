package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 拼字對決 — 益智 + 詞彙。
 *
 * 顯示打散的字母，玩家按正確順序拼出單字。
 * 連續拼對加分加速，拼錯扣時間。
 */
class SpellingDuelGame(private val context: Context) : GameLogic {

    override val gameMode = GameMode.SPELLING_DUEL
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = GAME_DURATION_MS
    private var currentWord = ""
    private var shuffledChars = listOf<Char>()
    private var inputBuffer = StringBuilder()
    private var wordsCompleted = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); timeLeftMs = GAME_DURATION_MS
        wordsCompleted = 0; inputBuffer.clear()
        pickNewWord(); clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { gameOver(); return }
        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        val expected = currentWord.getOrNull(inputBuffer.length) ?: return
        if (label.uppercase() == expected.toString()) {
            inputBuffer.append(label.uppercase())
            scoreManager.addScore(5)

            if (inputBuffer.length >= currentWord.length) {
                // 拼完
                wordsCompleted++
                scoreManager.addScore(currentWord.length * 10)
                scoreManager.incrementCombo()
                onGameEvent?.invoke(GameEvent.PERFECT)
                inputBuffer.clear()
                pickNewWord()
            }
        } else {
            // 拼錯
            timeLeftMs -= 2000
            inputBuffer.clear()
            scoreManager.resetCombo()
            onGameEvent?.invoke(GameEvent.MISS)
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun pickNewWord() {
        currentWord = words[Random.nextInt(words.size)]
        shuffledChars = currentWord.toList().shuffled()
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
        // 在中間行顯示打散字母（使用預先洗好的順序，避免每幀變動）
        val midRow = rows / 2
        for ((i, ch) in shuffledChars.withIndex()) {
            val col = (cols / 2 - shuffledChars.size / 2 + i).coerceIn(0, cols - 1)
            if (midRow in 0 until rows && col in 0 until cols) {
                grid[midRow][col].gameLabel = ch.toString()
                grid[midRow][col].glowColor = 0xFF64FFDA.toInt()
                grid[midRow][col].isActive = true
            }
        }
        // 已輸入的字母（上方）
        val topRow = (midRow - 1).coerceAtLeast(0)
        for ((i, ch) in inputBuffer.withIndex()) {
            val col = (cols / 2 - currentWord.length / 2 + i).coerceIn(0, cols - 1)
            if (col in 0 until cols) {
                grid[topRow][col].gameLabel = ch.toString()
                grid[topRow][col].glowColor = 0xFF00E676.toInt()
            }
        }
        // 空位提示
        for (i in inputBuffer.length until currentWord.length) {
            val col = (cols / 2 - currentWord.length / 2 + i).coerceIn(0, cols - 1)
            if (col in 0 until cols) {
                grid[topRow][col].gameLabel = "_"
                grid[topRow][col].glowColor = 0x44FFFFFF
            }
        }
    }

    private fun loadWords(): List<String> {
        return try {
            context.resources.openRawResource(com.snowrealm.yukiboard.R.raw.spelling_words)
                .bufferedReader().readLines()
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() && it.length in 3..10 }
                .distinct()
        } catch (_: Exception) {
            FALLBACK_WORDS
        }
    }

    private val words: List<String> by lazy { loadWords() }

    companion object {
        const val GAME_DURATION_MS = 60_000L
        private val FALLBACK_WORDS = listOf(
            "SNOW", "YUKI", "TYPE", "GAME", "PLAY", "CODE", "FIRE", "RAIN",
            "STAR", "MOON", "LOVE", "COOL", "FAST", "HERO", "DARK", "LITE"
        )
    }
}
