package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 語音遊戲 — 依賴 WhisperSkill（A4 完成後接入）。
 *
 * 玩法：
 * - 螢幕顯示一句話，玩家用語音唸出
 * - AI 比對語音辨識結果和目標文字
 * - 依準確度評分（Perfect/Good/Miss）
 *
 * 目前為 stub，等 Whisper API 串接後實作。
 */
class VoiceGame(context: Context) : GameLogic {

    override val gameMode = GameMode.VOICE_GAME
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        clearGrid()
        // 顯示等待訊息
        showMessage("🎙️ 語音遊戲\n即將推出...")
        status = GameStatus.PLAYING
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }
    override fun update(deltaMs: Long) {}
    override fun onKeyPress(row: Int, col: Int, label: String) {}
    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c].reset()
    }

    private fun showMessage(msg: String) {
        val lines = msg.split("\n")
        val startRow = (rows / 2 - lines.size / 2).coerceAtLeast(0)
        for ((li, line) in lines.withIndex()) {
            val r = startRow + li
            if (r >= rows) break
            val startCol = (cols / 2 - line.length / 2).coerceAtLeast(0)
            for ((ci, ch) in line.withIndex()) {
                val c = startCol + ci
                if (c < cols) {
                    grid[r][c].gameLabel = ch.toString()
                    grid[r][c].glowColor = 0xFF7C4DFF.toInt()
                }
            }
        }
    }
}
