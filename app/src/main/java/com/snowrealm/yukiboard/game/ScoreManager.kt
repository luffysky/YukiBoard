package com.snowrealm.yukiboard.game

import android.content.Context
import android.content.SharedPreferences

/**
 * 遊戲分數管理器。
 * 各遊戲最高分持久化到 SharedPreferences。
 */
class ScoreManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_game_scores", Context.MODE_PRIVATE)

    var currentScore: Int = 0
        private set
    var combo: Int = 0
        private set
    var maxCombo: Int = 0
        private set

    fun addScore(points: Int) {
        currentScore += points
    }

    fun incrementCombo() {
        combo++
        if (combo > maxCombo) maxCombo = combo
    }

    fun resetCombo() {
        combo = 0
    }

    fun reset() {
        currentScore = 0
        combo = 0
        maxCombo = 0
    }

    fun getHighScore(gameMode: GameMode): Int {
        return prefs.getInt("highscore_${gameMode.name}", 0)
    }

    fun saveHighScoreIfNeeded(gameMode: GameMode): Boolean {
        val prev = getHighScore(gameMode)
        if (currentScore > prev) {
            prefs.edit().putInt("highscore_${gameMode.name}", currentScore).apply()
            return true
        }
        return false
    }

    /**
     * 根據連擊數回傳流光顏色。
     * 0~2: 藍色, 3~5: 紫色, 6~9: 金色, 10+: 彩虹
     */
    fun getComboColor(): Int = when {
        combo < 3  -> 0xFF4FC3F7.toInt()  // 藍
        combo < 6  -> 0xFFAB47BC.toInt()  // 紫
        combo < 10 -> 0xFFFFD54F.toInt()  // 金
        else       -> 0xFFFF7043.toInt()  // 彩虹橘
    }
}
