package com.snowrealm.yukiboard.animation

import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

/**
 * Lottie 實作的 [CharacterAnimation]。
 *
 * ## 動畫檔案規範
 * 將各動畫 JSON 放在 `app/src/main/assets/lottie/` 下：
 * ```
 * assets/lottie/
 *   yuki_idle.json       ← 浮動循環
 *   yuki_typing.json     ← 打字反應（一次性）
 *   yuki_happy.json      ← 開心（一次性）
 *   yuki_listening.json  ← 聆聽語音（循環直到停止）
 *   yuki_switch_lang.json← 切語言特效
 * ```
 *
 * ## 命名對應
 * [playReaction] 的 `animationName` 直接對應 `assets/lottie/yuki_<name>.json`。
 */
class LottieCharacterAnimation(private val view: LottieAnimationView) : CharacterAnimation {

    override fun playIdle() {
        view.cancelAnimation()
        view.setAnimation("lottie/yuki_idle.json")
        view.repeatCount = LottieDrawable.INFINITE
        view.playAnimation()
    }

    override fun playReaction(animationName: String, onEnd: (() -> Unit)?) {
        view.cancelAnimation()
        view.setAnimation("lottie/yuki_$animationName.json")

        if (onEnd != null) {
            view.repeatCount = 0
            view.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    view.removeAnimatorListener(this)
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.removeAnimatorListener(this)
                    onEnd()
                }
            })
        } else {
            // 無結束回呼：循環播放（例如 listening）
            view.repeatCount = LottieDrawable.INFINITE
        }

        view.playAnimation()
    }

    override fun stop() {
        view.cancelAnimation()
    }
}
