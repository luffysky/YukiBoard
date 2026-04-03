package com.snowrealm.yukiboard.game

import android.content.Context
import kotlin.random.Random

/**
 * 貪吃蛇遊戲。
 *
 * 蛇在按鍵網格上爬行，每個按鍵是一格。
 * - 食物隨機出現在某按鍵上，按下該鍵或蛇頭碰到即吃掉
 * - 蛇身經過的按鍵發流光藍色軌跡，蛇頭發白色強光
 * - WASD 或滑動控制方向
 * - 撞牆或撞自己 = 死亡
 */
class SnakeGame(context: Context) : GameLogic {

    override val gameMode = GameMode.SNAKE
    override var status = GameStatus.IDLE
        private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0
    private var cols = 0

    // 蛇身位置列表（head = last）
    private val snake = mutableListOf<Pair<Int, Int>>()
    private var direction = SwipeDirection.RIGHT
    private var nextDirection = SwipeDirection.RIGHT
    private var foodPos: Pair<Int, Int>? = null

    // 移動計時
    private var moveAccumulator = 0L
    private val moveInterval = 350L // ms per step
    private val speedUpPerFood = 8L  // 每吃一個加速

    private var foodEaten = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows
        this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        snake.clear()
        foodEaten = 0
        moveAccumulator = 0
        direction = SwipeDirection.RIGHT
        nextDirection = SwipeDirection.RIGHT

        // 蛇起始位置（中間偏左，長度 3）
        val startRow = rows / 2
        val startCol = cols / 4
        for (i in 0 until 3) {
            snake.add(Pair(startRow, startCol + i))
        }

        clearGrid()
        placeFood()
        renderSnake()
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

        moveAccumulator += deltaMs
        val currentInterval = (moveInterval - foodEaten * speedUpPerFood).coerceAtLeast(120L)

        if (moveAccumulator >= currentInterval) {
            moveAccumulator -= currentInterval
            direction = nextDirection
            moveSnake()
        }

        updateAnimations(deltaMs)
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        // 按下食物所在的鍵 = 額外加分
        if (foodPos == Pair(row, col)) {
            scoreManager.addScore(5) // 主動按的額外加分
        }

        // 也可用按鍵位置當方向
        val head = snake.lastOrNull() ?: return
        when {
            col < head.second -> nextDirection = SwipeDirection.LEFT
            col > head.second -> nextDirection = SwipeDirection.RIGHT
            row < head.first -> nextDirection = SwipeDirection.UP
            row > head.first -> nextDirection = SwipeDirection.DOWN
        }
    }

    override fun onSwipe(direction: SwipeDirection) {
        // 防止 180 度轉向
        val opposite = when (this.direction) {
            SwipeDirection.UP -> SwipeDirection.DOWN
            SwipeDirection.DOWN -> SwipeDirection.UP
            SwipeDirection.LEFT -> SwipeDirection.RIGHT
            SwipeDirection.RIGHT -> SwipeDirection.LEFT
        }
        if (direction != opposite) {
            nextDirection = direction
        }
    }

    override fun destroy() {
        status = GameStatus.IDLE
    }

    // ── 核心邏輯 ──

    private fun moveSnake() {
        val head = snake.last()
        val newHead = when (direction) {
            SwipeDirection.UP -> Pair(head.first - 1, head.second)
            SwipeDirection.DOWN -> Pair(head.first + 1, head.second)
            SwipeDirection.LEFT -> Pair(head.first, head.second - 1)
            SwipeDirection.RIGHT -> Pair(head.first, head.second + 1)
        }

        // 撞牆
        if (newHead.first < 0 || newHead.first >= rows ||
            newHead.second < 0 || newHead.second >= cols) {
            gameOver()
            return
        }

        // 撞自己
        if (newHead in snake) {
            gameOver()
            return
        }

        snake.add(newHead)

        // 吃到食物
        if (newHead == foodPos) {
            foodEaten++
            scoreManager.addScore(10)
            scoreManager.incrementCombo()
            onGameEvent?.invoke(GameEvent.EAT)
            placeFood()
            // 不移除尾巴（蛇變長）
        } else {
            scoreManager.resetCombo()
            snake.removeAt(0)
        }

        renderSnake()
    }

    private fun gameOver() {
        status = GameStatus.GAME_OVER
        scoreManager.saveHighScoreIfNeeded(gameMode)
        onGameEvent?.invoke(GameEvent.GAME_OVER)

        // 全格紅色閃爍
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r][c].glowColor = 0xFFFF4444.toInt()
                grid[r][c].animState = KeyCell.ANIM_SHAKE
                grid[r][c].animProgress = 0f
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

    private fun renderSnake() {
        // 清除非蛇非食物的格子
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val pos = Pair(r, c)
                if (pos !in snake && pos != foodPos) {
                    grid[r][c].glowColor = 0
                    grid[r][c].isActive = false
                    grid[r][c].gameLabel = null
                }
            }
        }

        // 蛇身：藍色軌跡
        snake.forEachIndexed { i, (r, c) ->
            val cell = grid[r][c]
            cell.isActive = true
            val isHead = (i == snake.size - 1)
            cell.glowColor = if (isHead) 0xFFFFFFFF.toInt() else 0xFF4FC3F7.toInt()
            cell.gameLabel = if (isHead) "●" else "○"
        }

        // 食物
        foodPos?.let { (r, c) ->
            grid[r][c].glowColor = 0xFFFF7043.toInt()
            grid[r][c].gameLabel = "★"
            grid[r][c].isActive = true
            grid[r][c].animState = KeyCell.ANIM_PULSE
        }
    }

    private fun placeFood() {
        val empty = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (Pair(r, c) !in snake) empty.add(Pair(r, c))
            }
        }
        foodPos = if (empty.isNotEmpty()) empty[Random.nextInt(empty.size)] else null
    }

    private fun updateAnimations(deltaMs: Long) {
        val step = deltaMs / 300f // 300ms 完成一次動畫
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = grid[r][c]
                if (cell.animState != KeyCell.ANIM_NONE) {
                    cell.animProgress = (cell.animProgress + step).coerceAtMost(1f)
                    if (cell.animProgress >= 1f && cell.animState != KeyCell.ANIM_PULSE) {
                        cell.animState = KeyCell.ANIM_NONE
                        cell.animProgress = 0f
                    } else if (cell.animState == KeyCell.ANIM_PULSE && cell.animProgress >= 1f) {
                        cell.animProgress = 0f // 脈衝循環
                    }
                }
            }
        }
    }
}

enum class GameEvent {
    EAT, HIT, PERFECT, GOOD, MISS, COMBO, GAME_OVER, VICTORY
}
