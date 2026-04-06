package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 台灣版 Wordle — 注音猜詞遊戲。
 *
 * 規則：
 * - 每局一個 4 字詞語，6 次猜測機會
 * - 鍵盤格上顯示猜測結果：🟩正確 🟨有但位置錯 ⬜沒有
 * - 打字輸入 4 個中文字後按 Enter 提交
 */
class WordleGame(context: Context) : GameLogic {

    override val gameMode = GameMode.WORDLE
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var answer = ""
    private var guesses = mutableListOf<String>()
    private var currentInput = ""
    private var timeLeftMs = 120_000L  // 2 分鐘

    var onGameEvent: ((GameEvent) -> Unit)? = null

    private val wordPool = listOf(
        "春暖花開", "一心一意", "天氣晴朗", "心想事成", "四季如春",
        "天長地久", "花好月圓", "龍飛鳳舞", "一見鍾情", "萬事如意",
        "才華洋溢", "歡天喜地", "風和日麗", "金玉滿堂", "大吉大利",
        "平安喜樂", "高山流水", "明月當空", "星光燦爛", "陽光普照",
        "百花齊放", "鳥語花香", "山明水秀", "氣象萬千", "朝氣蓬勃",
        "勇往直前", "馬到成功", "如魚得水", "蒸蒸日上", "欣欣向榮"
    )

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        answer = wordPool.random()
        guesses.clear()
        currentInput = ""
        scoreManager.reset()
        timeLeftMs = 120_000L
        status = GameStatus.PLAYING
        renderGrid()
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

        when {
            label == "⌫" || label == "DEL" -> {
                if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
            }
            label == "↵" || label == "ENTER" || label == "確認" -> {
                if (currentInput.length == 4) submitGuess()
            }
            label.length == 1 && label[0].code >= 0x4E00 -> {
                if (currentInput.length < 4) currentInput += label
            }
        }
        renderGrid()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun submitGuess() {
        guesses.add(currentInput)
        if (currentInput == answer) {
            val bonus = (7 - guesses.size) * 100
            scoreManager.addScore(bonus)
            onGameEvent?.invoke(GameEvent.PERFECT)
            gameOver()
        } else if (guesses.size >= 6) {
            onGameEvent?.invoke(GameEvent.MISS)
            gameOver()
        } else {
            scoreManager.addScore(10) // 參與分
        }
        currentInput = ""
    }

    private fun gameOver() {
        status = GameStatus.GAME_OVER
    }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        clearGrid()

        // 標題列
        setRowText(0, "🔮 猜詞 ${guesses.size}/6  ⏱${timeLeftMs / 1000}s")

        // 已猜列
        guesses.forEachIndexed { i, guess ->
            if (i + 1 < rows) {
                val hints = buildHints(guess)
                setRowText(i + 1, "$guess $hints")
            }
        }

        // 輸入列
        val inputRow = guesses.size + 1
        if (inputRow < rows && status == GameStatus.PLAYING) {
            val display = currentInput.padEnd(4, '＿')
            setRowText(inputRow, "▶ $display")
        }

        // 結束訊息
        if (status == GameStatus.GAME_OVER) {
            val msgRow = (guesses.size + 2).coerceAtMost(rows - 1)
            if (guesses.lastOrNull() == answer) {
                setRowText(msgRow, "🎉 猜對！+${scoreManager.currentScore}")
            } else {
                setRowText(msgRow, "答案：$answer")
            }
        }
    }

    private fun buildHints(guess: String): String {
        val result = Array(4) { "⬜" }
        val answerChars = answer.toList()
        val used = BooleanArray(4)

        for (i in 0 until 4) {
            if (i < guess.length && guess[i] == answerChars[i]) {
                result[i] = "🟩"; used[i] = true
            }
        }
        for (i in 0 until guess.length.coerceAtMost(4)) {
            if (result[i] == "🟩") continue
            for (j in 0 until 4) {
                if (!used[j] && guess[i] == answerChars[j]) {
                    result[i] = "🟨"; used[j] = true; break
                }
            }
        }
        return result.joinToString("")
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
