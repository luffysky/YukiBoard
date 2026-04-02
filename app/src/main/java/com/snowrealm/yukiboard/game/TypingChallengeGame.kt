package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 打字挑戰遊戲。
 *
 * 目標字母顯示在按鍵上（發橘色光），按對即爆炸消失，下一個目標出現。
 * 按錯紅色震動。60 秒計時模式，打越多分越高。
 */
class TypingChallengeGame(context: Context) : GameLogic {

    override val gameMode = GameMode.TYPING_CHALLENGE
    override var status = GameStatus.IDLE
        private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0
    private var cols = 0

    // 目標
    private var targetRow = -1
    private var targetCol = -1
    private var targetChar = ""
    private var totalTimeMs = 60_000L
    private var elapsedMs = 0L
    private var totalAttempts = 0
    private var correctHits = 0

    // 可用字母（對應到鍵盤按鍵）
    private val letterPool = "qwertyuiopasdfghjklzxcvbnm"

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows
        this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        totalAttempts = 0
        correctHits = 0
        elapsedMs = 0

        clearGrid()
        placeTarget()
        status = GameStatus.PLAYING
    }

    override fun pause() {
        if (status == GameStatus.PLAYING) status = GameStatus.PAUSED
    }

    override fun resume() {
        if (status == GameStatus.PAUSED) status = GameStatus.PLAYING
    }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return

        elapsedMs += deltaMs

        if (elapsedMs >= totalTimeMs) {
            status = GameStatus.GAME_OVER
            scoreManager.saveHighScoreIfNeeded(gameMode)
            val accuracy = if (totalAttempts > 0) correctHits.toFloat() / totalAttempts else 0f
            onGameEvent?.invoke(if (accuracy > 0.9f) GameEvent.VICTORY else GameEvent.GAME_OVER)
            return
        }

        updateAnimations(deltaMs)

        // 正確率 > 90% 時提示鼓勵
        if (totalAttempts > 5) {
            val accuracy = correctHits.toFloat() / totalAttempts
            if (accuracy > 0.9f && scoreManager.combo > 0 && scoreManager.combo % 5 == 0) {
                onGameEvent?.invoke(GameEvent.COMBO)
            }
        }
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return

        totalAttempts++

        if (label.equals(targetChar, ignoreCase = true)) {
            // 按對！
            correctHits++
            scoreManager.addScore(10 + scoreManager.combo * 2)
            scoreManager.incrementCombo()

            grid[targetRow][targetCol].gameLabel = "✓"
            grid[targetRow][targetCol].glowColor = 0xFF00E676.toInt()  // 綠色
            grid[targetRow][targetCol].animState = KeyCell.ANIM_EXPLODE
            grid[targetRow][targetCol].animProgress = 0f

            onGameEvent?.invoke(GameEvent.HIT)
            if (scoreManager.combo >= 3) onGameEvent?.invoke(GameEvent.COMBO)

            // 立刻放下一個目標
            placeTarget()
        } else {
            // 按錯
            scoreManager.resetCombo()
            grid[row][col].glowColor = 0xFFFF4444.toInt()
            grid[row][col].animState = KeyCell.ANIM_SHAKE
            grid[row][col].animProgress = 0f
            onGameEvent?.invoke(GameEvent.MISS)
        }
    }

    override fun onSwipe(direction: SwipeDirection) { }

    override fun destroy() {
        status = GameStatus.IDLE
    }

    /** 剩餘時間（秒） */
    fun remainingSeconds(): Int = ((totalTimeMs - elapsedMs) / 1000).toInt().coerceAtLeast(0)

    /** 正確率 */
    fun accuracy(): Float = if (totalAttempts > 0) correctHits.toFloat() / totalAttempts else 1f

    // ── 內部 ──

    private fun placeTarget() {
        // 隨機選按鍵位置（排除第一行數字和最後兩行功能鍵）
        val validRows = (1 until (rows - 2).coerceAtLeast(1))
        val r = validRows.random()
        val c = Random.nextInt(cols)
        val ch = letterPool[Random.nextInt(letterPool.length)]

        targetRow = r
        targetCol = c
        targetChar = ch.toString()

        grid[r][c].gameLabel = ch.uppercase()
        grid[r][c].glowColor = 0xFFFF7043.toInt()  // 橘色
        grid[r][c].isActive = true
        grid[r][c].animState = KeyCell.ANIM_PULSE
        grid[r][c].animProgress = 0f
    }

    private fun clearGrid() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r][c].reset()
            }
        }
    }

    private fun updateAnimations(deltaMs: Long) {
        val step = deltaMs / 300f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = grid[r][c]
                if (cell.animState != KeyCell.ANIM_NONE) {
                    cell.animProgress = (cell.animProgress + step).coerceAtMost(1f)
                    if (cell.animProgress >= 1f) {
                        when (cell.animState) {
                            KeyCell.ANIM_PULSE -> cell.animProgress = 0f
                            else -> {
                                cell.animState = KeyCell.ANIM_NONE
                                cell.animProgress = 0f
                                if (r != targetRow || c != targetCol) {
                                    cell.reset()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
