package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 雪凜問答 — 常識 + 語文 + SnowRealm 知識。
 *
 * 雪凜出題，4 個選項分佈在鍵盤格上。
 * 答對加分 + 時間，答錯扣時間。
 */
class YukiQuizGame(context: Context) : GameLogic {

    override val gameMode = GameMode.YUKI_QUIZ
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = GAME_DURATION_MS
    private var currentQ: Question? = null
    private var optionPositions = mutableListOf<Pair<Pair<Int, Int>, Int>>() // (row,col) → optionIndex
    private var questionsAsked = 0
    private var shuffledQuestions = QUESTIONS.shuffled()

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); timeLeftMs = GAME_DURATION_MS
        questionsAsked = 0; nextQuestion()
        clearGrid(); status = GameStatus.PLAYING
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
        val hit = optionPositions.firstOrNull { it.first == (row to col) } ?: return
        val q = currentQ ?: return

        if (hit.second == q.correctIndex) {
            scoreManager.addScore(25)
            scoreManager.incrementCombo()
            timeLeftMs = (timeLeftMs + 3000).coerceAtMost(GAME_DURATION_MS + 15_000)
            onGameEvent?.invoke(GameEvent.PERFECT)
        } else {
            timeLeftMs -= 5000
            scoreManager.resetCombo()
            onGameEvent?.invoke(GameEvent.MISS)
        }

        nextQuestion()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun nextQuestion() {
        // 每輪洗牌避免固定順序
        if (questionsAsked % QUESTIONS.size == 0) {
            shuffledQuestions = QUESTIONS.shuffled()
        }
        val base = shuffledQuestions[questionsAsked % shuffledQuestions.size]
        // 動態打亂選項順序，避免正確答案固定位置
        val indices = base.options.indices.shuffled()
        val shuffledOptions = indices.map { base.options[it] }
        val newCorrectIndex = indices.indexOf(base.correctIndex)
        currentQ = Question(base.question, shuffledOptions, newCorrectIndex)
        questionsAsked++
        placeOptions()
    }

    private fun placeOptions() {
        optionPositions.clear()
        val q = currentQ ?: return
        val positions = mutableListOf<Pair<Int, Int>>()
        // 選 4 個不重疊的位置（避開 row 0 題目列）
        val startRow = if (rows > 1) 1 else 0
        val available = rows * cols - (if (startRow == 1) cols else 0)
        val needed = q.options.size.coerceAtMost(available)
        while (positions.size < needed) {
            val r = startRow + Random.nextInt((rows - startRow).coerceAtLeast(1))
            val c = Random.nextInt(cols)
            if ((r to c) !in positions) positions.add(r to c)
        }
        for ((i, pos) in positions.withIndex()) {
            optionPositions.add(pos to i)
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
        val q = currentQ ?: return
        // 題目顯示在第一行
        for (c in 0 until cols) {
            val charIdx = c
            if (charIdx < q.question.length) {
                grid[0][c].gameLabel = q.question[charIdx].toString()
                grid[0][c].glowColor = 0xFF7C4DFF.toInt()
            }
        }
        // 選項
        val colors = intArrayOf(0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFFF7043.toInt(), 0xFFAB47BC.toInt())
        for ((pos, idx) in optionPositions) {
            val (r, c) = pos
            if (r in 0 until rows && c in 0 until cols && idx < q.options.size) {
                grid[r][c].gameLabel = q.options[idx]
                grid[r][c].glowColor = colors[idx % colors.size]
                grid[r][c].isActive = true
                grid[r][c].animState = KeyCell.ANIM_PULSE
            }
        }
    }

    data class Question(val question: String, val options: List<String>, val correctIndex: Int)

    companion object {
        const val GAME_DURATION_MS = 60_000L
        val QUESTIONS = listOf(
            Question("雪的日文？", listOf("雪", "雨", "風", "花"), 0),
            Question("HTTP狀態404？", listOf("找不到", "禁止", "錯誤", "OK"), 0),
            Question("Android語言？", listOf("Kotlin", "Swift", "Rust", "Go"), 0),
            Question("RGB紅色值？", listOf("FF0000", "00FF00", "0000FF", "FFFF00"), 0),
            Question("1KB=?B", listOf("1024", "1000", "512", "2048"), 0),
            Question("日本首都？", listOf("東京", "大阪", "京都", "橫濱"), 0),
            Question("最大行星？", listOf("木星", "土星", "地球", "火星"), 0),
            Question("WiFi全名？", listOf("Wireless\nFidelity", "Wide\nFi", "Wire\nFree", "Web\nFi"), 0),
            Question("Emoji起源？", listOf("日本", "美國", "韓國", "中國"), 0),
            Question("Unicode幾位？", listOf("可變", "8位", "16位", "32位"), 0),
            Question("鍵盤QWERTY？", listOf("防卡", "快速", "隨機", "字母"), 0),
            Question("Git建立者？", listOf("Linus", "Bill", "Steve", "Mark"), 0)
        )
    }
}
