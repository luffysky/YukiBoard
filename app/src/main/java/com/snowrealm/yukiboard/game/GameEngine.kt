package com.snowrealm.yukiboard.game

import android.os.Handler
import android.os.Looper

/**
 * 遊戲引擎介面。
 * 每個具體遊戲實作此介面，GameKeyboardView 透過此操作遊戲。
 */
interface GameLogic {
    /** 遊戲模式 */
    val gameMode: GameMode
    /** 目前遊戲狀態 */
    val status: GameStatus
    /** 按鍵網格（遊戲畫面） */
    val grid: Array<Array<KeyCell>>
    /** 分數管理 */
    val scoreManager: ScoreManager

    /** 初始化遊戲（設定網格大小） */
    fun init(rows: Int, cols: Int)
    /** 開始遊戲 */
    fun start()
    /** 暫停 */
    fun pause()
    /** 繼續 */
    fun resume()
    /** 每幀更新（deltaMs = 距上次更新的毫秒數） */
    fun update(deltaMs: Long)
    /** 玩家按下某按鍵 */
    fun onKeyPress(row: Int, col: Int, label: String)
    /** 滑動方向輸入 */
    fun onSwipe(direction: SwipeDirection)
    /** 銷毀 */
    fun destroy()
}

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * 遊戲主迴圈管理器 — 60fps 更新。
 * 綁定 IME 生命週期：onStartInputView→resume，onFinishInputView→pause。
 */
class GameLoopRunner {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastUpdateTime = 0L
    private var game: GameLogic? = null
    private var onFrameCallback: (() -> Unit)? = null

    private val frameInterval = 16L // ~60fps

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            val delta = if (lastUpdateTime > 0) now - lastUpdateTime else frameInterval
            lastUpdateTime = now

            game?.let {
                if (it.status == GameStatus.PLAYING) {
                    it.update(delta)
                    onFrameCallback?.invoke()
                }
            }

            handler.postDelayed(this, frameInterval)
        }
    }

    fun attach(gameLogic: GameLogic, onFrame: () -> Unit) {
        game = gameLogic
        onFrameCallback = onFrame
    }

    fun start() {
        if (running) return
        running = true
        lastUpdateTime = 0
        handler.post(loopRunnable)
    }

    fun pause() {
        running = false
        handler.removeCallbacks(loopRunnable)
        game?.pause()
    }

    fun resume() {
        game?.resume()
        if (!running) {
            running = true
            lastUpdateTime = 0
            handler.post(loopRunnable)
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(loopRunnable)
        game?.destroy()
        game = null
    }
}
