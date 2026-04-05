package com.snowrealm.yukiboard.input.smart

/**
 * 隱形連擊計數器 — 打字命中預測時累加連擊。
 *
 * 規則：
 * - 從候選列選字 或 預測命中 → combo +1
 * - 停止打字 [TIMEOUT_MS] 秒 → 歸零
 * - 達到閾值觸發視覺回饋（粒子特效）
 *
 * 閾值：5 → 小火花、10 → 中火花、20 → 大火花、50 → 全螢幕特效
 * 不顯示分數，純視覺回饋，玩家不會感受到「分數壓力」。
 */
class ComboTracker {

    var combo: Int = 0
        private set

    var maxCombo: Int = 0
        private set

    private var lastHitMs = 0L
    private var pendingEffect: ComboEffect? = null

    /** 檢查是否有待消費的特效 */
    fun consumeEffect(): ComboEffect? {
        val effect = pendingEffect
        pendingEffect = null
        return effect
    }

    /** 命中：從候選列選字成功 / 預測命中 */
    fun hit() {
        val now = System.currentTimeMillis()
        // 超時歸零
        if (lastHitMs > 0 && now - lastHitMs > TIMEOUT_MS) {
            combo = 0
        }
        combo++
        lastHitMs = now
        if (combo > maxCombo) maxCombo = combo

        // 檢查閾值觸發
        pendingEffect = when (combo) {
            THRESHOLD_SPARK -> ComboEffect.SPARK
            THRESHOLD_FLAME -> ComboEffect.FLAME
            THRESHOLD_BLAZE -> ComboEffect.BLAZE
            THRESHOLD_INFERNO -> ComboEffect.INFERNO
            else -> null
        }
    }

    /** 錯誤：打字沒命中預測（可選呼叫，不一定每次都觸發） */
    fun miss() {
        combo = 0
        pendingEffect = null
    }

    /** 定時檢查超時（由 KeyboardView 的 draw loop 呼叫） */
    fun checkTimeout() {
        if (combo > 0 && lastHitMs > 0) {
            if (System.currentTimeMillis() - lastHitMs > TIMEOUT_MS) {
                combo = 0
            }
        }
    }

    /** 是否正在連擊中（可用於 UI 呈現輕微光暈） */
    val isActive: Boolean get() = combo >= 3

    /** 連擊等級（0~4） */
    val level: Int get() = when {
        combo >= THRESHOLD_INFERNO -> 4
        combo >= THRESHOLD_BLAZE -> 3
        combo >= THRESHOLD_FLAME -> 2
        combo >= THRESHOLD_SPARK -> 1
        else -> 0
    }

    fun reset() {
        combo = 0
        pendingEffect = null
    }

    enum class ComboEffect {
        SPARK,    // 5 連擊：小火花
        FLAME,    // 10 連擊：中火花
        BLAZE,    // 20 連擊：大火花
        INFERNO   // 50 連擊：全螢幕
    }

    companion object {
        const val TIMEOUT_MS = 3000L
        const val THRESHOLD_SPARK = 5
        const val THRESHOLD_FLAME = 10
        const val THRESHOLD_BLAZE = 20
        const val THRESHOLD_INFERNO = 50
    }
}
