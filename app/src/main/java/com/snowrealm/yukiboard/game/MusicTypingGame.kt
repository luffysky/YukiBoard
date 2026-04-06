package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 音樂打字 — 按鍵對應音符，打字就是演奏。
 *
 * 規則：
 * - 音符從頂部掉落到判定線，按下對應按鍵得分
 * - 判定等級：PERFECT(±100ms) / GOOD(±200ms) / MISS
 * - 連擊加分，30 秒一首曲子
 */
class MusicTypingGame(context: Context) : GameLogic {

    override val gameMode = GameMode.MUSIC_TYPING
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = 30_000L
    private var combo = 0
    private var noteIndex = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    private data class Note(val col: Int, val triggerTimeMs: Long, var hit: Boolean = false)

    // 預設曲譜（列索引 + 觸發時間）
    private val songNotes = mutableListOf<Note>()

    private fun generateSong(cols: Int) {
        songNotes.clear()
        val colRange = 1 until (cols - 1).coerceAtLeast(2)
        var t = 2000L
        repeat(40) {
            songNotes.add(Note(colRange.random(), t))
            t += (400L..800L).random()
        }
    }

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        timeLeftMs = 30_000L
        combo = 0; noteIndex = 0
        generateSong(cols)
        status = GameStatus.PLAYING
        renderGrid()
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { status = GameStatus.GAME_OVER; return }

        // 超時未按的音符標記 MISS
        val elapsed = 30_000L - timeLeftMs
        songNotes.filter { !it.hit && elapsed > it.triggerTimeMs + 500 }.forEach {
            it.hit = true
            combo = 0
            onGameEvent?.invoke(GameEvent.MISS)
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return

        val elapsed = 30_000L - timeLeftMs
        // 找最近的未命中音符（同列，±300ms）
        val note = songNotes.firstOrNull {
            !it.hit && it.col == col && kotlin.math.abs(elapsed - it.triggerTimeMs) < 300
        } ?: return

        note.hit = true
        val diff = kotlin.math.abs(elapsed - note.triggerTimeMs)
        when {
            diff < 100 -> {
                combo++
                scoreManager.addScore(100 + combo * 5)
                onGameEvent?.invoke(GameEvent.PERFECT)
            }
            diff < 200 -> {
                combo++
                scoreManager.addScore(50 + combo * 3)
                onGameEvent?.invoke(GameEvent.GOOD)
            }
            else -> {
                combo = 0
                scoreManager.addScore(10)
            }
        }
        renderGrid()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        clearGrid()

        setRowText(0, "🎵 音樂打字 連擊$combo  ⏱${timeLeftMs / 1000}s")

        val elapsed = 30_000L - timeLeftMs
        val judgeRow = rows - 2

        // 顯示即將到來的音符
        songNotes.filter { !it.hit }.forEach { note ->
            val timeDiff = note.triggerTimeMs - elapsed
            if (timeDiff in -200..3000) {
                val progress = 1f - (timeDiff.toFloat() / 3000f)
                val noteRow = (progress * judgeRow).toInt().coerceIn(1, rows - 1)
                if (note.col in 0 until cols) {
                    grid[noteRow][note.col] = grid[noteRow][note.col].copy(
                        gameLabel = "♪",
                        glowColor = 0xFF7C4DFF.toInt(),
                        isActive = true
                    )
                }
            }
        }

        // 判定線
        if (judgeRow in 1 until rows) {
            for (c in 0 until cols) {
                if (grid[judgeRow][c].gameLabel == null) {
                    grid[judgeRow][c] = grid[judgeRow][c].copy(
                        gameLabel = "─",
                        glowColor = 0x44FFFFFF
                    )
                }
            }
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
