package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 打字對決 — 與雪凜 AI 比速度打同一段文字。
 *
 * 規則：
 * - 顯示一段文字，玩家和雪凜同時打
 * - 雪凜速度隨難度漸增（60→120 字/分）
 * - 先打完的人贏，30 秒限時
 */
class SpeedTypingBattleGame(context: Context) : GameLogic {

    override val gameMode = GameMode.SPEED_TYPING_BATTLE
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = 30_000L
    private var playerProgress = 0
    private var yukiProgress = 0
    private var yukiSpeedMs = 800L // 每字毫秒數（越低越快）
    private var yukiAccum = 0L
    private var round = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    private val sentences = listOf(
        "今天天氣真好",
        "我喜歡吃拉麵",
        "雪凜最可愛了",
        "台灣好美麗啊",
        "加油繼續努力",
        "明天會更好的",
        "一起去看電影",
        "晚安好夢早安",
        "你好嗎我很好",
        "快樂每一天喔",
    )

    private var currentSentence = ""

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        timeLeftMs = 30_000L
        round = 0
        yukiSpeedMs = 800L
        nextRound()
        status = GameStatus.PLAYING
        renderGrid()
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { status = GameStatus.GAME_OVER; renderGrid(); return }

        // 雪凜自動打字
        yukiAccum += deltaMs
        while (yukiAccum >= yukiSpeedMs && yukiProgress < currentSentence.length) {
            yukiAccum -= yukiSpeedMs
            yukiProgress++
        }

        // 雪凜打完 → 玩家輸這回合
        if (yukiProgress >= currentSentence.length) {
            onGameEvent?.invoke(GameEvent.MISS)
            scoreManager.addScore(20) // 參與分
            round++
            if (round >= 5) { status = GameStatus.GAME_OVER }
            else nextRound()
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return

        if (label.length == 1 && playerProgress < currentSentence.length) {
            val expected = currentSentence[playerProgress].toString()
            if (label == expected) {
                playerProgress++
                scoreManager.addScore(10)

                // 玩家打完 → 贏這回合
                if (playerProgress >= currentSentence.length) {
                    val bonus = (currentSentence.length - yukiProgress) * 20
                    scoreManager.addScore(bonus)
                    onGameEvent?.invoke(GameEvent.PERFECT)
                    round++
                    if (round >= 5) { status = GameStatus.GAME_OVER }
                    else {
                        yukiSpeedMs = (yukiSpeedMs - 80).coerceAtLeast(300L)
                        nextRound()
                    }
                }
            }
        }
        renderGrid()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun nextRound() {
        currentSentence = sentences.random()
        playerProgress = 0
        yukiProgress = 0
        yukiAccum = 0
    }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        clearGrid()

        setRowText(0, "⚔️ 打字對決 R${round + 1}/5 ⏱${timeLeftMs / 1000}s")

        // 目標文字
        if (1 < rows) setRowText(1, "  $currentSentence")

        // 玩家進度
        if (2 < rows) {
            val bar = "👤 " + "█".repeat(playerProgress) + "░".repeat(currentSentence.length - playerProgress)
            setRowText(2, bar)
        }

        // 雪凜進度
        if (3 < rows) {
            val bar = "🌸 " + "█".repeat(yukiProgress) + "░".repeat(currentSentence.length - yukiProgress)
            setRowText(3, bar)
        }

        // 結束訊息
        if (status == GameStatus.GAME_OVER && 4 < rows) {
            setRowText(4, "🏁 結束！+${scoreManager.currentScore}")
        }
    }

    private fun setRowText(row: Int, text: String) {
        if (row >= rows) return
        for (c in 0 until cols.coerceAtMost(text.length)) {
            grid[row][c] = grid[row][c].copy(gameLabel = text[c].toString())
        }
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) {
            grid[r][c] = KeyCell(r, c)
        }
    }
}
