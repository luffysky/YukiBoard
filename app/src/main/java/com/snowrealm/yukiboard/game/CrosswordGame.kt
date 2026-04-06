package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 填字遊戲 — 根據提示填入正確的字。
 *
 * 規則：
 * - 顯示一個四字成語，其中 1~2 個字被遮住
 * - 玩家輸入正確的字得分
 * - 10 題限時 90 秒
 */
class CrosswordGame(context: Context) : GameLogic {

    override val gameMode = GameMode.CROSSWORD
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = 90_000L
    private var currentQuestion = 0
    private var currentInput = ""
    private var missingIndices = listOf<Int>()

    var onGameEvent: ((GameEvent) -> Unit)? = null

    private data class Puzzle(val word: String, val hint: String)

    private val puzzles = listOf(
        Puzzle("天長地久", "形容時間很長"),
        Puzzle("心想事成", "心中想的都能達成"),
        Puzzle("花好月圓", "花開得好，月亮圓滿"),
        Puzzle("風和日麗", "風平浪靜、天氣好"),
        Puzzle("一心一意", "專心致志"),
        Puzzle("龍飛鳳舞", "形容書法生動"),
        Puzzle("馬到成功", "一開始就成功"),
        Puzzle("百花齊放", "形容百家爭鳴"),
        Puzzle("春暖花開", "春天來了花開了"),
        Puzzle("大吉大利", "非常吉祥順利"),
        Puzzle("金玉滿堂", "形容財富極多"),
        Puzzle("山明水秀", "形容景色優美"),
        Puzzle("高山流水", "知音，知己"),
        Puzzle("勇往直前", "勇敢地往前走"),
        Puzzle("如魚得水", "形容得到適合的環境"),
    ).shuffled()

    private var currentPuzzle: Puzzle? = null
    private var answerChars = listOf<Char>()

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        timeLeftMs = 90_000L
        currentQuestion = 0
        currentInput = ""
        status = GameStatus.PLAYING
        nextPuzzle()
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { status = GameStatus.GAME_OVER }
        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return

        when {
            label == "⌫" || label == "DEL" -> {
                if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
            }
            label == "↵" || label == "ENTER" || label == "確認" -> checkAnswer()
            label.length == 1 && label[0].code >= 0x4E00 -> {
                if (currentInput.length < missingIndices.size) currentInput += label
            }
        }
        renderGrid()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun nextPuzzle() {
        if (currentQuestion >= 10 || currentQuestion >= puzzles.size) {
            status = GameStatus.GAME_OVER
            renderGrid()
            return
        }
        currentPuzzle = puzzles[currentQuestion]
        val word = currentPuzzle!!.word
        // 隨機遮 1~2 個字
        val count = if (currentQuestion < 5) 1 else 2
        missingIndices = (0 until word.length).shuffled().take(count).sorted()
        answerChars = missingIndices.map { word[it] }
        currentInput = ""
        renderGrid()
    }

    private fun checkAnswer() {
        if (currentInput.length != missingIndices.size) return

        val correct = currentInput.toList() == answerChars
        if (correct) {
            val bonus = if (missingIndices.size == 1) 80 else 150
            scoreManager.addScore(bonus)
            onGameEvent?.invoke(GameEvent.PERFECT)
        } else {
            scoreManager.addScore(10) // 參與分
            onGameEvent?.invoke(GameEvent.MISS)
        }
        currentQuestion++
        nextPuzzle()
    }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        clearGrid()

        setRowText(0, "🔲 填字 ${currentQuestion + 1}/10  ⏱${timeLeftMs / 1000}s")

        val puzzle = currentPuzzle ?: return

        // 提示
        if (1 < rows) setRowText(1, "提示：${puzzle.hint}")

        // 顯示成語（遮住的字用 ＿ 或已輸入的字）
        if (2 < rows) {
            val display = StringBuilder()
            var inputIdx = 0
            for (i in puzzle.word.indices) {
                if (i in missingIndices) {
                    display.append(
                        if (inputIdx < currentInput.length) currentInput[inputIdx++]
                        else '＿'
                    )
                } else {
                    display.append(puzzle.word[i])
                }
            }
            setRowText(2, "  $display")
        }

        // 輸入提示
        if (3 < rows && status == GameStatus.PLAYING) {
            setRowText(3, "▶ 填入${missingIndices.size}個字")
        }

        // 結束訊息
        if (status == GameStatus.GAME_OVER && 4 < rows) {
            setRowText(4, "🎉 完成！+${scoreManager.currentScore}")
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
