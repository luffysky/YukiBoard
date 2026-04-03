package com.snowrealm.yukiboard.emotion

/**
 * 統一情緒狀態 — 所有情緒模組的共用數據源。
 *
 * 職責分工（不重疊）：
 * - ToneDetector       → 偵測輸入文字情緒 → 寫入 EmotionState
 * - MoodDiarySkill     → 讀取 EmotionState → 產生 AI 回應
 * - EmotionalCooldown  → 監聽 EmotionState → 極端時攔截發送
 * - MoodMiner          → 累積 EmotionState 歷史 → 產生週報
 */
object EmotionState {

    enum class Tone {
        POSITIVE, NEGATIVE, NEUTRAL, ANGRY, SAD, EXCITED
    }

    data class Snapshot(
        val tone: Tone = Tone.NEUTRAL,
        val confidence: Float = 0f,
        val triggerText: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    @Volatile
    var current: Snapshot = Snapshot()
        private set

    private val listeners = mutableListOf<(Snapshot) -> Unit>()

    /** ToneDetector 呼叫：更新情緒狀態 */
    fun update(tone: Tone, confidence: Float, text: String) {
        current = Snapshot(tone, confidence, text)
        listeners.forEach { it(current) }
    }

    /** EmotionalCooldown / MoodMiner 訂閱變化 */
    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    /** 是否處於需要冷靜的極端狀態 */
    fun isExtreme(): Boolean {
        return current.tone == Tone.ANGRY && current.confidence > 0.7f
    }
}
