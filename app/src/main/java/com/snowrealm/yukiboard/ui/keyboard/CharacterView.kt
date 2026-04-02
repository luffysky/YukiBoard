package com.snowrealm.yukiboard.ui.keyboard

import android.content.Context
import android.util.AttributeSet
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.snowrealm.yukiboard.animation.CharacterAnimation
import com.snowrealm.yukiboard.animation.LottieCharacterAnimation

/**
 * 雪凜（Yuki）角色 Lottie 動畫 View。
 *
 * 浮動在鍵盤上方，根據輸入狀態播放不同動畫：
 * - idle: 呼吸浮動
 * - typing: 打字反應
 * - happy: 選詞成功
 * - switch_lang: 切換語言
 * - game_start / game_over / victory: 遊戲事件
 */
class CharacterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LottieAnimationView(context, attrs, defStyleAttr) {

    private val characterAnimation: CharacterAnimation = LottieCharacterAnimation(this)
    private var isIdling = false

    init {
        // 預設播放 idle 動畫
        playIdle()
    }

    fun playIdle() {
        if (isIdling) return
        isIdling = true
        characterAnimation.playIdle()
    }

    /**
     * 播放一次性反應動畫，結束後自動回到 idle。
     */
    fun playReaction(name: String) {
        isIdling = false
        characterAnimation.playReaction(name) {
            playIdle()
        }
    }

    // ── 便捷方法 ──

    fun playTyping() = playReaction("typing")
    fun playHappy() = playReaction("happy")
    fun playSwitchLang() = playReaction("switch_lang")
    fun playGameStart() = playReaction("game_start")
    fun playGameOver() = playReaction("game_over")
    fun playVictory() = playReaction("victory")
    fun playListening() {
        isIdling = false
        characterAnimation.playReaction("listening", null)  // 持續播放，不回 idle
    }

    fun stopAll() {
        isIdling = false
        characterAnimation.stop()
    }
}
