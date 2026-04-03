package com.snowrealm.yukiboard.yuki

import kotlin.random.Random

/**
 * 雪凜微吐槽引擎 — 偵測打字行為觸發善意毒舌短句。
 *
 * 觸發事件：
 * - 同字打錯 3 次
 * - 長文全刪
 * - 打字速度明顯變慢
 * - 深夜打字（23:00-05:00）
 * - 遊戲輸了
 *
 * 規則：10 分鐘冷卻，不重複連續同一句。
 */
class YukiCommentEngine {

    private val COOLDOWN_MS = 10 * 60 * 1000L
    private var lastCommentTime = 0L
    private var lastCommentText = ""

    // ── 按鍵追蹤狀態 ──
    private var lastChar: Char = '\u0000'
    private var sameCharErrorCount = 0
    private var recentTextLength = 0
    private var lastKeystrokeTime = 0L
    private var recentIntervals = mutableListOf<Long>()

    /** 回傳吐槽短句，null 表示不觸發 */
    fun onKeystroke(char: Char, currentTextLength: Int): String? {
        val now = System.currentTimeMillis()

        // 記錄打字間隔
        if (lastKeystrokeTime > 0) {
            recentIntervals.add(now - lastKeystrokeTime)
            if (recentIntervals.size > 20) recentIntervals.removeAt(0)
        }
        lastKeystrokeTime = now

        // 同字連打偵測
        if (char == lastChar) {
            sameCharErrorCount++
        } else {
            sameCharErrorCount = 0
            lastChar = char
        }

        if (sameCharErrorCount >= 3) {
            sameCharErrorCount = 0
            return tryComment(Event.REPEAT_ERROR)
        }

        // 打字變慢偵測
        if (isTypingSlower()) {
            return tryComment(Event.TYPING_SLOW)
        }

        recentTextLength = currentTextLength
        return null
    }

    /** 偵測大量刪除 */
    fun onTextDeleted(deletedLength: Int, remainingLength: Int): String? {
        if (deletedLength > 20 && remainingLength < 5) {
            return tryComment(Event.LONG_DELETE)
        }
        return null
    }

    /** 深夜打字檢查 */
    fun onInputStarted(): String? {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour in 23..23 || hour in 0..4) {
            return tryComment(Event.LATE_NIGHT)
        }
        return null
    }

    /** 遊戲結束（輸了） */
    fun onGameLost(): String? = tryComment(Event.GAME_LOST)

    /** 重置狀態 */
    fun reset() {
        sameCharErrorCount = 0
        recentIntervals.clear()
        lastKeystrokeTime = 0L
    }

    // ─────────────── 內部邏輯 ───────────────

    private fun isTypingSlower(): Boolean {
        if (recentIntervals.size < 10) return false
        val recentAvg = recentIntervals.takeLast(5).average()
        val olderAvg = recentIntervals.take(5).average()
        return recentAvg > olderAvg * 2.0 && recentAvg > 800
    }

    private fun tryComment(event: Event): String? {
        val now = System.currentTimeMillis()
        if (now - lastCommentTime < COOLDOWN_MS) return null

        val pool = COMMENTS[event] ?: return null
        val candidates = pool.filter { it != lastCommentText }
        if (candidates.isEmpty()) return null

        val comment = candidates[Random.nextInt(candidates.size)]
        lastCommentTime = now
        lastCommentText = comment
        return comment
    }

    private enum class Event {
        REPEAT_ERROR, LONG_DELETE, TYPING_SLOW, LATE_NIGHT, GAME_LOST
    }

    companion object {
        private val COMMENTS = mapOf(
            Event.REPEAT_ERROR to listOf(
                "你手指還好嗎？同一個字打三次了 🤔",
                "是鍵盤的問題還是你的問題？",
                "打錯沒關係，反正我不會笑你（才怪）",
                "深呼吸...慢慢來 😌"
            ),
            Event.LONG_DELETE to listOf(
                "全刪了？我還以為是什麼大作 😂",
                "寫了那麼多又全刪...你到底在想什麼",
                "下次直接不要打不是更快？",
                "嗯...重新來過也是一種勇氣 ✨"
            ),
            Event.TYPING_SLOW to listOf(
                "在想什麼呢？打字都變慢了",
                "是不是在發呆？",
                "速度掉了喔，加油 💪",
                "等你...等到我都快睡著了 😴"
            ),
            Event.LATE_NIGHT to listOf(
                "這麼晚了還在打字？注意身體啊 🌙",
                "深夜手機人...要不要去睡覺？",
                "雪凜覺得你該睡了 💤",
                "熬夜打字會變笨的（真的）"
            ),
            Event.GAME_LOST to listOf(
                "沒關係，再來一局！",
                "菜是原罪 🫡",
                "你盡力了...吧？",
                "輸了也不丟臉，我不會跟別人說的 😏"
            )
        )
    }
}
