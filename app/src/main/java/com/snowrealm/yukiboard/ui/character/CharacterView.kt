package com.snowrealm.yukiboard.ui.character

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.snowrealm.yukiboard.animation.CharacterAnimation
import com.snowrealm.yukiboard.animation.LottieCharacterAnimation

/**
 * 角色動畫容器 View。
 *
 * 承載雪凜（Yuki）Lottie 浮動動畫，位於鍵盤上方。
 * 透過 [CharacterAnimation] 介面解耦，未來可替換成 Spine 或自繪引擎。
 *
 * XML 使用範例：
 * ```xml
 * <com.snowrealm.yukiboard.ui.character.CharacterView
 *     android:id="@+id/characterView"
 *     android:layout_width="match_parent"
 *     android:layout_height="160dp" />
 * ```
 */
class CharacterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val lottieView: LottieAnimationView = LottieAnimationView(context).also { lv ->
        lv.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        lv.repeatCount = LottieDrawable.INFINITE
        addView(lv)
    }

    private val animation: CharacterAnimation = LottieCharacterAnimation(lottieView)

    init {
        // 預設播放 idle（浮動）動畫
        animation.playIdle()
    }

    // -------------------------------------------------------------------------
    // 動畫觸發介面
    // -------------------------------------------------------------------------

    /** 按鍵時播放打字動作，結束後回到 idle */
    fun triggerTypingReaction() {
        animation.playReaction("typing") {
            animation.playIdle()
        }
    }

    /** 候選詞選中時播放開心動作 */
    fun triggerHappyReaction() {
        animation.playReaction("happy") {
            animation.playIdle()
        }
    }

    /** 語音輸入中播放聆聽動畫 */
    fun triggerListeningMode(active: Boolean) {
        if (active) animation.playReaction("listening", onEnd = null)
        else animation.playIdle()
    }

    /** 切換語言時播放特效 */
    fun triggerLanguageSwitch() {
        animation.playReaction("switch_lang") {
            animation.playIdle()
        }
    }
}
