package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 情侶測驗 — 互動 + 分享。
 *
 * 雪凜出情侶默契題，A/B 各選答案。
 * 答案一致 = 加分，不一致 = 提示。
 * 單人版：模擬對方回答。
 */
class CoupleQuizGame(context: Context) : GameLogic {

    override val gameMode = GameMode.COUPLE_QUIZ
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var currentQ: CoupleQuestion? = null
    private var playerAnswer = -1
    private var questionIndex = 0
    private var optionPositions = mutableListOf<Pair<Pair<Int, Int>, Int>>()

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); questionIndex = 0; playerAnswer = -1
        nextQuestion(); clearGrid(); status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }
    override fun update(deltaMs: Long) { if (status == GameStatus.PLAYING) renderGrid() }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return
        val hit = optionPositions.firstOrNull { it.first == (row to col) } ?: return
        playerAnswer = hit.second

        // 模擬對方答案（偏向附近選項增加趣味性）
        val q = currentQ ?: return
        val partnerAnswer = Random.nextInt(q.options.size)

        if (playerAnswer == partnerAnswer) {
            scoreManager.addScore(30)
            scoreManager.incrementCombo()
            onGameEvent?.invoke(GameEvent.PERFECT)
        } else {
            scoreManager.addScore(5)
            scoreManager.resetCombo()
            onGameEvent?.invoke(GameEvent.GOOD)
        }

        questionIndex++
        if (questionIndex >= QUESTIONS.size) {
            status = GameStatus.GAME_OVER
            scoreManager.saveHighScoreIfNeeded(gameMode)
            onGameEvent?.invoke(GameEvent.VICTORY)
        } else {
            nextQuestion()
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun nextQuestion() {
        currentQ = QUESTIONS[questionIndex % QUESTIONS.size]
        playerAnswer = -1
        placeOptions()
    }

    private fun placeOptions() {
        optionPositions.clear()
        val q = currentQ ?: return
        val positions = mutableListOf<Pair<Int, Int>>()
        val startRow = if (rows > 1) 1 else 0
        val availableSlots = (rows - startRow) * cols
        val needed = q.options.size.coerceAtMost(availableSlots)
        while (positions.size < needed) {
            val r = startRow + Random.nextInt((rows - startRow).coerceAtLeast(1))
            val c = Random.nextInt(cols)
            if ((r to c) !in positions) positions.add(r to c)
        }
        for ((i, pos) in positions.withIndex()) {
            optionPositions.add(pos to i)
        }
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c].reset()
    }

    private fun renderGrid() {
        clearGrid()
        val q = currentQ ?: return
        // 題目
        for ((i, ch) in q.question.withIndex()) {
            val c = i.coerceIn(0, cols - 1)
            grid[0][c].gameLabel = ch.toString()
            grid[0][c].glowColor = 0xFFE91E63.toInt()
        }
        // 選項
        val colors = intArrayOf(0xFFFF80AB.toInt(), 0xFF80D8FF.toInt(), 0xFFB9F6CA.toInt(), 0xFFFFFF8D.toInt())
        for ((pos, idx) in optionPositions) {
            val (r, c) = pos
            if (r in 0 until rows && c in 0 until cols && idx < q.options.size) {
                grid[r][c].gameLabel = q.options[idx]
                grid[r][c].glowColor = colors[idx % colors.size]
                grid[r][c].isActive = true
            }
        }
    }

    data class CoupleQuestion(val question: String, val options: List<String>)

    companion object {
        val QUESTIONS = listOf(
            CoupleQuestion("約會吃什麼？", listOf("火鍋", "壽司", "牛排", "拉麵")),
            CoupleQuestion("旅行去哪？", listOf("日本", "韓國", "歐洲", "海島")),
            CoupleQuestion("週末做什麼？", listOf("看電影", "逛街", "在家", "運動")),
            CoupleQuestion("最愛的季節？", listOf("春", "夏", "秋", "冬")),
            CoupleQuestion("養什麼寵物？", listOf("🐱貓", "🐶狗", "🐰兔", "🐹鼠")),
            CoupleQuestion("一起看什麼？", listOf("動漫", "韓劇", "電影", "綜藝")),
            CoupleQuestion("對方最萌？", listOf("笑", "撒嬌", "認真", "睡著")),
            CoupleQuestion("紀念日送？", listOf("花", "禮物", "手寫信", "驚喜")),
            CoupleQuestion("吵架先道歉？", listOf("我", "對方", "冷戰", "買吃的")),
            CoupleQuestion("最愛的顏色？", listOf("粉紅", "藍色", "黑色", "白色"))
        )
    }
}
