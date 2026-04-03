package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 打地鼠遊戲。
 *
 * 地鼠隨機從按鍵「冒出」（按鍵發黃色光），在時間內按下即打中。
 * 難度隨時間提升（同時多隻地鼠、出現時間變短）。
 * 連擊時流光顏色從藍→紫→金色。
 */
class WhackAMoleGame(context: Context) : GameLogic {

    override val gameMode = GameMode.WHACK_A_MOLE
    override var status = GameStatus.IDLE
        private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0
    private var cols = 0

    // 活躍地鼠列表
    data class Mole(val row: Int, val col: Int, var timeLeftMs: Long)
    private val activeMoles = mutableListOf<Mole>()

    // 遊戲參數
    private var totalTimeMs = 60_000L  // 60 秒
    private var elapsedMs = 0L
    private var spawnAccumulator = 0L
    private var spawnInterval = 1200L  // 初始每 1.2 秒出一隻
    private val moleLifespan = 2000L   // 地鼠存活 2 秒
    private var maxActiveMoles = 2     // 同時最多幾隻

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows
        this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        activeMoles.clear()
        elapsedMs = 0
        spawnAccumulator = 0
        spawnInterval = 1200L
        maxActiveMoles = 2
        clearGrid()
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

        // 時間到
        if (elapsedMs >= totalTimeMs) {
            status = GameStatus.GAME_OVER
            scoreManager.saveHighScoreIfNeeded(gameMode)
            onGameEvent?.invoke(GameEvent.GAME_OVER)
            return
        }

        // 難度遞增
        val progress = elapsedMs.toFloat() / totalTimeMs
        spawnInterval = (1200L - (progress * 700).toLong()).coerceAtLeast(400L)
        maxActiveMoles = (2 + (progress * 4).toInt()).coerceAtMost(6)

        // 生成地鼠
        spawnAccumulator += deltaMs
        if (spawnAccumulator >= spawnInterval && activeMoles.size < maxActiveMoles) {
            spawnAccumulator -= spawnInterval
            spawnMole()
        }

        // 更新地鼠生命
        val expired = mutableListOf<Mole>()
        for (mole in activeMoles) {
            mole.timeLeftMs -= deltaMs
            if (mole.timeLeftMs <= 0) {
                expired.add(mole)
            }
        }

        // 地鼠逃走
        for (mole in expired) {
            activeMoles.remove(mole)
            grid[mole.row][mole.col].reset()
            grid[mole.row][mole.col].animState = KeyCell.ANIM_FADE
            grid[mole.row][mole.col].animProgress = 0f
            scoreManager.resetCombo()
            onGameEvent?.invoke(GameEvent.MISS)
        }

        // 更新動畫
        updateAnimations(deltaMs)
        renderMoles()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return

        val mole = activeMoles.find { it.row == row && it.col == col }
        if (mole != null) {
            // 打中！
            activeMoles.remove(mole)
            scoreManager.addScore(10 + scoreManager.combo * 2)
            scoreManager.incrementCombo()

            grid[row][col].reset()
            grid[row][col].glowColor = scoreManager.getComboColor()
            grid[row][col].animState = KeyCell.ANIM_EXPLODE
            grid[row][col].animProgress = 0f
            grid[row][col].gameLabel = "💥"

            onGameEvent?.invoke(GameEvent.HIT)
            if (scoreManager.combo >= 3) {
                onGameEvent?.invoke(GameEvent.COMBO)
            }
        } else {
            // 按錯
            grid[row][col].glowColor = 0xFFFF4444.toInt()
            grid[row][col].animState = KeyCell.ANIM_SHAKE
            grid[row][col].animProgress = 0f
            scoreManager.resetCombo()
        }
    }

    override fun onSwipe(direction: SwipeDirection) {
        // 打地鼠不需要滑動
    }

    override fun destroy() {
        status = GameStatus.IDLE
        activeMoles.clear()
    }

    /** 剩餘時間（秒） */
    fun remainingSeconds(): Int = ((totalTimeMs - elapsedMs) / 1000).toInt().coerceAtLeast(0)

    // ── 內部 ──

    private fun spawnMole() {
        val occupied = activeMoles.map { Pair(it.row, it.col) }.toSet()
        val available = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (Pair(r, c) !in occupied) available.add(Pair(r, c))
            }
        }
        if (available.isEmpty()) return

        val (r, c) = available[Random.nextInt(available.size)]
        activeMoles.add(Mole(r, c, moleLifespan))
    }

    private fun renderMoles() {
        for (mole in activeMoles) {
            val cell = grid[mole.row][mole.col]
            if (cell.animState == KeyCell.ANIM_NONE || cell.animState == KeyCell.ANIM_PULSE) {
                cell.glowColor = 0xFFFFD54F.toInt()  // 黃色
                cell.gameLabel = "🐹"
                cell.isActive = true
                cell.animState = KeyCell.ANIM_PULSE
            }
        }
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
                                if (!cell.isActive) cell.reset()
                            }
                        }
                    }
                }
            }
        }
    }
}
