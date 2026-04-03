package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 隨機挑戰 — 每局隨機規則。
 *
 * 雪凜隨機出題：打指定字母 N 次、倒序打字、
 * 只用左手/右手打字等。完成挑戰 = 加分。
 * AI 評分功能預留（接 ClawSkill）。
 */
class RandomChallengeGame(context: Context) : GameLogic {

    override val gameMode = GameMode.RANDOM_CHALLENGE
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = GAME_DURATION_MS
    private var currentChallenge: Challenge? = null
    private var challengeProgress = 0
    private var challengesDone = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset(); timeLeftMs = GAME_DURATION_MS
        challengesDone = 0; challengeProgress = 0
        nextChallenge(); clearGrid(); status = GameStatus.PLAYING
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
        val ch = currentChallenge ?: return
        val upper = label.uppercase()

        when (ch.type) {
            ChallengeType.REPEAT_LETTER -> {
                if (upper == ch.targetLetter) {
                    challengeProgress++
                    scoreManager.addScore(5)
                    if (challengeProgress >= ch.targetCount) completeChallenge()
                }
            }
            ChallengeType.SEQUENCE -> {
                val expected = ch.sequence.getOrNull(challengeProgress)?.toString()
                if (upper == expected) {
                    challengeProgress++
                    scoreManager.addScore(8)
                    if (challengeProgress >= ch.sequence.length) completeChallenge()
                } else {
                    challengeProgress = 0
                    scoreManager.resetCombo()
                }
            }
            ChallengeType.AVOID_LETTER -> {
                if (upper == ch.targetLetter) {
                    timeLeftMs -= 3000
                    scoreManager.resetCombo()
                    onGameEvent?.invoke(GameEvent.HIT)
                } else {
                    challengeProgress++
                    scoreManager.addScore(3)
                    if (challengeProgress >= ch.targetCount) completeChallenge()
                }
            }
        }
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun completeChallenge() {
        challengesDone++
        scoreManager.addScore(50)
        scoreManager.incrementCombo()
        timeLeftMs += 5000
        onGameEvent?.invoke(GameEvent.PERFECT)
        nextChallenge()
    }

    private fun nextChallenge() {
        challengeProgress = 0
        val type = ChallengeType.entries[Random.nextInt(ChallengeType.entries.size)]
        currentChallenge = when (type) {
            ChallengeType.REPEAT_LETTER -> {
                val count = (5 + challengesDone).coerceAtMost(15)
                val letter = ('A' + Random.nextInt(26)).toString()
                Challenge(type, "打 $letter ${count} 次", letter, count)
            }
            ChallengeType.SEQUENCE -> {
                val len = (4 + challengesDone).coerceAtMost(8)
                val seq = (1..len).map { ('A' + Random.nextInt(26)) }.joinToString("")
                Challenge(type, "依序打: $seq", sequence = seq)
            }
            ChallengeType.AVOID_LETTER -> {
                val letter = ('A' + Random.nextInt(26)).toString()
                Challenge(type, "打 10 字不碰 $letter", letter, 10)
            }
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
        val ch = currentChallenge ?: return
        // 挑戰描述
        for ((i, c) in ch.description.withIndex()) {
            val col = i.coerceIn(0, cols - 1)
            grid[0][col].gameLabel = c.toString()
            grid[0][col].glowColor = 0xFFFF9800.toInt()
        }
        // 進度條
        val midRow = rows / 2
        val target = ch.targetCount.coerceAtLeast(ch.sequence.length)
        val progress = if (target > 0) (challengeProgress.toFloat() / target * cols).toInt() else 0
        for (c in 0 until progress.coerceAtMost(cols)) {
            if (midRow in 0 until rows) {
                grid[midRow][c].glowColor = 0xFF76FF03.toInt()
                grid[midRow][c].isActive = true
            }
        }
    }

    enum class ChallengeType { REPEAT_LETTER, SEQUENCE, AVOID_LETTER }

    data class Challenge(
        val type: ChallengeType,
        val description: String,
        val targetLetter: String = "",
        val targetCount: Int = 0,
        val sequence: String = ""
    )

    companion object { const val GAME_DURATION_MS = 60_000L }
}
