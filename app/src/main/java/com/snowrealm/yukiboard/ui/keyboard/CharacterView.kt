package com.snowrealm.yukiboard.ui.keyboard

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Path
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.snowrealm.yukiboard.animation.CharacterAnimation
import com.snowrealm.yukiboard.animation.LottieCharacterAnimation
import com.snowrealm.yukiboard.claw.skills.AnimationProfile
import kotlin.random.Random

/**
 * 雪凜（Yuki）角色 Lottie 動畫 View。
 *
 * 在鍵盤上自由遊走，根據輸入狀態播放不同動畫：
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
    private var wanderAnimator: ObjectAnimator? = null
    private var wanderEnabled = true
    private var animationProfile = AnimationProfile()
    private var keystrokeCount = 0

    init {
        playIdle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 等 layout 完成後開始遊走
        post { startWandering() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWandering()
    }

    /** 開始在鍵盤範圍內隨機遊走 */
    private fun startWandering() {
        if (!wanderEnabled) return
        wanderToNext()
    }

    private fun stopWandering() {
        wanderAnimator?.cancel()
        wanderAnimator = null
    }

    private fun wanderToNext() {
        val parent = parent as? android.view.ViewGroup ?: return
        val maxX = (parent.width - width).coerceAtLeast(0).toFloat()
        val maxY = (parent.height - height).coerceAtLeast(0).toFloat()

        val startX = translationX
        val startY = translationY
        val targetX = Random.nextFloat() * maxX
        val targetY = Random.nextFloat() * maxY

        // 貝茲曲線控制點：在起終點中間偏移，產生弧線
        val midX = (startX + targetX) / 2f
        val midY = (startY + targetY) / 2f
        val perpX = -(targetY - startY)  // 垂直方向偏移
        val perpY = targetX - startX
        val perpLen = Math.sqrt((perpX * perpX + perpY * perpY).toDouble()).toFloat().coerceAtLeast(1f)
        val curveStrength = (0.3f + Random.nextFloat() * 0.4f) * if (Random.nextBoolean()) 1f else -1f
        val ctrlX = (midX + perpX / perpLen * perpLen * curveStrength).coerceIn(0f, maxX)
        val ctrlY = (midY + perpY / perpLen * perpLen * curveStrength).coerceIn(0f, maxY)

        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(ctrlX, ctrlY, targetX, targetY)
        }

        // 根據距離決定時間
        val dx = targetX - startX
        val dy = targetY - startY
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val baseDuration = (2500L + (dist * 4).toLong()).coerceIn(2500L, 6000L)
        val duration = (baseDuration / animationProfile.wanderSpeedMultiplier.coerceAtLeast(0.1f)).toLong()

        // 面朝移動方向
        scaleX = if (dx < 0) -1f else 1f

        wanderAnimator = ObjectAnimator.ofFloat(this, "translationX", "translationY", path).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (wanderEnabled) {
                        postDelayed({ wanderToNext() }, 1500L + Random.nextLong(2500L))
                    }
                }
            })
            start()
        }
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

    /** 套用 AI 推薦的動畫描述檔 */
    fun applyAnimationProfile(profile: AnimationProfile) {
        animationProfile = profile
        keystrokeCount = 0
        // 若有 idle variant，切換對應 idle 動畫
        if (profile.idleVariant.isNotEmpty()) {
            isIdling = false
            (characterAnimation as? LottieCharacterAnimation)?.playVariant("idle", profile.idleVariant) {
                // variant 結束後回到預設 idle
                playIdle()
            }
        }
    }

    /** 每次按鍵呼叫，依 reactionFrequency 決定是否播放打字動畫 */
    fun onKeystroke() {
        keystrokeCount++
        if (keystrokeCount % animationProfile.reactionFrequency == 0) {
            playTyping()
        }
    }

    fun playTyping() = playReaction("typing")
    fun playHappy() = playReaction("happy")
    fun playSwitchLang() = playReaction("switch_lang")
    fun playGameStart() = playReaction("game_start")
    fun playGameOver() = playReaction("game_over")
    fun playVictory() = playReaction("victory")
    fun playFreeze() = playReaction("freeze")
    fun playListening() {
        isIdling = false
        characterAnimation.playReaction("listening", null)  // 持續播放，不回 idle
    }

    fun stopAll() {
        isIdling = false
        wanderEnabled = false
        stopWandering()
        characterAnimation.stop()
    }
}
