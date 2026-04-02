package com.snowrealm.yukiboard.animation

/**
 * 角色動畫介面。
 *
 * 解耦動畫實作（Lottie / Spine / 自繪），讓 [com.snowrealm.yukiboard.ui.character.CharacterView]
 * 不依賴具體技術棧。
 */
interface CharacterAnimation {

    /** 播放循環 idle 動畫（浮動、呼吸） */
    fun playIdle()

    /**
     * 播放一次性反應動畫，結束後呼叫 [onEnd]。
     * @param animationName 動畫識別名稱（由具體實作對應到檔案或 Lottie segment）
     * @param onEnd 動畫結束回呼（可為 null 表示不需處理）
     */
    fun playReaction(animationName: String, onEnd: (() -> Unit)?)

    /** 停止所有動畫 */
    fun stop()
}
