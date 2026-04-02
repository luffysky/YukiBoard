package com.snowrealm.yukiboard.game

/**
 * 每個按鍵在遊戲中的狀態。
 * 遊戲引擎更新 KeyCell 陣列，GameKeyboardView 根據此渲染。
 */
data class KeyCell(
    val row: Int,
    val col: Int,
    /** 原始按鍵標籤 */
    val originalLabel: String = "",
    /** 遊戲中覆蓋顯示的標籤（null = 顯示原始） */
    var gameLabel: String? = null,
    /** 發光顏色 ARGB（0 = 不發光） */
    var glowColor: Int = 0,
    /** 是否為遊戲活躍格（如蛇身、地鼠位置） */
    var isActive: Boolean = false,
    /** 動畫狀態：0=無, 1=脈衝, 2=爆炸, 3=消失 */
    var animState: Int = ANIM_NONE,
    /** 動畫進度 0.0~1.0 */
    var animProgress: Float = 0f
) {
    companion object {
        const val ANIM_NONE = 0
        const val ANIM_PULSE = 1
        const val ANIM_EXPLODE = 2
        const val ANIM_FADE = 3
        const val ANIM_SHAKE = 4
    }

    fun reset() {
        gameLabel = null
        glowColor = 0
        isActive = false
        animState = ANIM_NONE
        animProgress = 0f
    }
}
