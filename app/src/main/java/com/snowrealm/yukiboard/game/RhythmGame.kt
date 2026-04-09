package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 節奏遊戲。
 *
 * 音符從鍵盤頂行往底行流動，到達指定按鍵時按下。
 * Perfect / Good / Miss 判定。
 * 內建幾首簡單節拍譜。
 */
class RhythmGame(context: Context) : GameLogic {

    override val gameMode = GameMode.RHYTHM
    override var status = GameStatus.IDLE
        private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0
    private var cols = 0

    // 音符
    data class Note(
        val targetCol: Int,     // 目標列
        val hitTimeMs: Long,    // 應按下的時間
        var currentRow: Float = -1f  // 目前浮動行位置
    )

    private val notes = mutableListOf<Note>()
    private var elapsedMs = 0L
    private var songDurationMs = 0L
    private var noteIndex = 0  // 下一個要處理的譜面索引
    private lateinit var songNotes: List<Pair<Int, Long>>  // (col, timeMs)

    // 判定窗口
    private val perfectWindowMs = 80L
    private val goodWindowMs = 180L
    private val noteSpeed = 0.003f  // 行/ms

    // 目標行（底部倒數第二行）
    private val targetRow get() = rows - 2

    var onGameEvent: ((GameEvent) -> Unit)? = null

    // 內建節拍譜（col, timeMs）
    private val builtInSongs = listOf(
        // 簡單四拍：左右交替
        listOf(
            2 to 1000L, 5 to 1500L, 8 to 2000L, 3 to 2500L,
            6 to 3000L, 1 to 3500L, 7 to 4000L, 4 to 4500L,
            2 to 5000L, 5 to 5300L, 8 to 5600L, 3 to 5900L,
            6 to 6200L, 1 to 6500L, 7 to 6800L, 4 to 7100L,
            0 to 7500L, 9 to 7800L, 2 to 8100L, 7 to 8400L,
            5 to 8700L, 3 to 9000L, 6 to 9300L, 8 to 9600L,
            1 to 10000L, 4 to 10300L, 7 to 10600L, 2 to 10900L,
            5 to 11200L, 0 to 11500L, 9 to 11800L, 3 to 12100L
        )
    )

    override fun init(rows: Int, cols: Int) {
        this.rows = rows
        this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        notes.clear()
        elapsedMs = 0
        noteIndex = 0

        // 選擇譜面
        val song = builtInSongs.firstOrNull() ?: return
        songNotes = song.map { (col, time) ->
            Pair(col.coerceIn(0, cols - 1), time)
        }
        songDurationMs = songNotes.lastOrNull()?.second?.plus(3000L) ?: 30000L

        clearGrid()
        status = GameStatus.PLAYING

        // 繪製目標行提示
        for (c in 0 until cols) {
            grid[targetRow][c].glowColor = (0xFF4FC3F7.toInt() and 0x00FFFFFF) or 0x33000000
        }
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

        // 歌曲結束
        if (elapsedMs >= songDurationMs && notes.isEmpty()) {
            status = GameStatus.GAME_OVER
            scoreManager.saveHighScoreIfNeeded(gameMode)
            onGameEvent?.invoke(if (scoreManager.currentScore > 200) GameEvent.VICTORY else GameEvent.GAME_OVER)
            return
        }

        // 生成新音符（提前 scrollTime 出現）
        val scrollTimeMs = (rows.toFloat() / noteSpeed).toLong()
        while (noteIndex < songNotes.size) {
            val (col, hitTime) = songNotes[noteIndex]
            if (elapsedMs >= hitTime - scrollTimeMs) {
                notes.add(Note(col, hitTime))
                noteIndex++
            } else break
        }

        // 更新音符位置
        clearGrid()
        // 目標行底色
        for (c in 0 until cols) {
            grid[targetRow][c].glowColor = (0xFF4FC3F7.toInt() and 0x00FFFFFF) or 0x33000000
        }

        val expiredNotes = mutableListOf<Note>()
        for (note in notes) {
            val timeSinceSpawn = elapsedMs - (note.hitTimeMs - scrollTimeMs)
            note.currentRow = timeSinceSpawn * noteSpeed

            val displayRow = note.currentRow.toInt()
            if (displayRow in 0 until rows && note.targetCol in 0 until cols) {
                val cell = grid[displayRow][note.targetCol]
                cell.glowColor = 0xFF7C4DFF.toInt()  // 紫色音符
                cell.gameLabel = "♪"
                cell.isActive = true
            }

            // 過了目標行太多 = Miss
            if (note.currentRow > targetRow + 2) {
                expiredNotes.add(note)
                scoreManager.resetCombo()
                onGameEvent?.invoke(GameEvent.MISS)
                // 灰色閃爍
                if (note.targetCol in 0 until cols) {
                    grid[targetRow][note.targetCol].glowColor = 0xFF666666.toInt()
                    grid[targetRow][note.targetCol].animState = KeyCell.ANIM_FADE
                    grid[targetRow][note.targetCol].animProgress = 0f
                }
            }
        }
        notes.removeAll(expiredNotes)

        updateAnimations(deltaMs)
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return

        // 找最接近目標行且在同一列的音符
        val matchNote = notes
            .filter { it.targetCol == col }
            .minByOrNull { kotlin.math.abs(it.currentRow - targetRow) }

        if (matchNote != null) {
            val distance = kotlin.math.abs(matchNote.currentRow - targetRow)
            val timeError = kotlin.math.abs(elapsedMs - matchNote.hitTimeMs)

            when {
                timeError <= perfectWindowMs || distance < 0.5f -> {
                    // Perfect!
                    notes.remove(matchNote)
                    scoreManager.addScore(20 + scoreManager.combo * 3)
                    scoreManager.incrementCombo()
                    grid[targetRow][col].glowColor = 0xFFFFFFFF.toInt()
                    grid[targetRow][col].animState = KeyCell.ANIM_EXPLODE
                    grid[targetRow][col].animProgress = 0f
                    grid[targetRow][col].gameLabel = "✦"
                    onGameEvent?.invoke(GameEvent.PERFECT)
                }
                timeError <= goodWindowMs || distance < 1.5f -> {
                    // Good
                    notes.remove(matchNote)
                    scoreManager.addScore(10 + scoreManager.combo)
                    scoreManager.incrementCombo()
                    grid[targetRow][col].glowColor = 0xFF4FC3F7.toInt()
                    grid[targetRow][col].animState = KeyCell.ANIM_PULSE
                    grid[targetRow][col].animProgress = 0f
                    grid[targetRow][col].gameLabel = "○"
                    onGameEvent?.invoke(GameEvent.GOOD)
                }
                else -> {
                    // Miss (太早或太遠)
                    scoreManager.resetCombo()
                    grid[targetRow][col].glowColor = 0xFF666666.toInt()
                    grid[targetRow][col].animState = KeyCell.ANIM_SHAKE
                    grid[targetRow][col].animProgress = 0f
                    onGameEvent?.invoke(GameEvent.MISS)
                }
            }
        }
    }

    override fun onSwipe(direction: SwipeDirection) { }

    override fun destroy() {
        status = GameStatus.IDLE
        notes.clear()
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
                            }
                        }
                    }
                }
            }
        }
    }
}
