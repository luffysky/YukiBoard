package com.snowrealm.yukiboard.ui.keyboard

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Path
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.snowrealm.yukiboard.animation.CharacterAnimation
import com.snowrealm.yukiboard.animation.LottieCharacterAnimation
import com.snowrealm.yukiboard.animation.VrmCharacterAnimation
import com.snowrealm.yukiboard.animation.VrmCharacterView
import com.snowrealm.yukiboard.claw.skills.AnimationProfile
import kotlin.random.Random

/**
 * 雪凜（Yuki）角色動畫容器 View。
 *
 * 支援兩種渲染模式：
 * 1. Lottie 模式（預設）— 使用 Lottie JSON 動畫
 * 2. VRM 模式 — 使用 Filament 渲染 3D VRM 模型
 *
 * 在鍵盤上自由遊走，根據輸入狀態播放不同動畫：
 * - idle: 呼吸浮動
 * - typing: 打字反應（nod + happy 表情）
 * - happy: 選詞成功（excited bounce + happy 表情）
 * - switch_lang: 切換語言（shake_head + surprised 表情）
 * - game_start / game_over / victory: 遊戲事件
 *
 * VRM 動作系統參照 Insight Engine 的 YukirinScene.tsx，
 * 支援程序化骨骼動畫：nod / shake_head / tilt_head / bow / wave / excited / sway / think / stretch
 */
class CharacterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class RenderMode { LOTTIE, VRM }

    private var renderMode = RenderMode.LOTTIE

    // Lottie 渲染
    private var lottieView: LottieAnimationView? = null
    private var lottieAnimation: LottieCharacterAnimation? = null

    // VRM 渲染
    private var vrmView: VrmCharacterView? = null
    private var vrmAnimation: VrmCharacterAnimation? = null

    // 統一動畫介面
    private var characterAnimation: CharacterAnimation? = null

    private var isIdling = false
    private var wanderAnimator: ObjectAnimator? = null
    private var wanderEnabled = true
    private var animationProfile = AnimationProfile()
    private var keystrokeCount = 0

    init {
        isClickable = false
        isFocusable = false
        // 預設使用 Lottie 模式
        initLottieMode()
    }

    // =========================================================================
    // 渲染模式切換
    // =========================================================================

    /** 切換到 Lottie 渲染模式 */
    fun initLottieMode() {
        cleanupCurrentMode()
        renderMode = RenderMode.LOTTIE

        val lv = LottieAnimationView(context).apply {
            isClickable = false
            isFocusable = false
        }
        addView(lv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        lottieView = lv

        val anim = LottieCharacterAnimation(lv)
        lottieAnimation = anim
        characterAnimation = anim
        playIdle()
    }

    /**
     * 切換到 VRM 渲染模式，從指定 URL 載入模型。
     * 預設使用 Insight Engine 的 Yukirin.vrm。
     */
    fun initVrmMode(modelUrl: String = VRM_MODEL_URL) {
        cleanupCurrentMode()
        renderMode = RenderMode.VRM

        val vv = VrmCharacterView(context).apply {
            isClickable = false
            isFocusable = false
        }
        addView(vv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        vrmView = vv

        val anim = VrmCharacterAnimation(vv)
        vrmAnimation = anim
        characterAnimation = anim

        // 載入 VRM 模型
        vv.loadModelFromUrl(modelUrl)

        // VRM 載入後自動 playIdle
        vv.postDelayed({ playIdle() }, 2000)
    }

    private fun cleanupCurrentMode() {
        stopWandering()
        when (renderMode) {
            RenderMode.LOTTIE -> {
                lottieView?.let { removeView(it) }
                lottieView = null
                lottieAnimation = null
            }
            RenderMode.VRM -> {
                vrmView?.destroy()
                vrmView?.let { removeView(it) }
                vrmView = null
                vrmAnimation = null
            }
        }
        characterAnimation = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { startWandering() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWandering()
        if (renderMode == RenderMode.VRM) {
            vrmView?.destroy()
        }
    }

    // =========================================================================
    // 遊走動畫
    // =========================================================================

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
        val perpX = -(targetY - startY)
        val perpY = targetX - startX
        val perpLen = Math.sqrt((perpX * perpX + perpY * perpY).toDouble()).toFloat().coerceAtLeast(1f)
        val curveStrength = (0.3f + Random.nextFloat() * 0.4f) * if (Random.nextBoolean()) 1f else -1f
        val ctrlX = (midX + perpX / perpLen * perpLen * curveStrength).coerceIn(0f, maxX)
        val ctrlY = (midY + perpY / perpLen * perpLen * curveStrength).coerceIn(0f, maxY)

        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(ctrlX, ctrlY, targetX, targetY)
        }

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

    // =========================================================================
    // 動畫播放 API
    // =========================================================================

    fun playIdle() {
        if (isIdling) return
        isIdling = true
        characterAnimation?.playIdle()
    }

    fun playReaction(name: String) {
        isIdling = false
        characterAnimation?.playReaction(name) {
            playIdle()
        }
    }

    /** 套用 AI 推薦的動畫描述檔 */
    fun applyAnimationProfile(profile: AnimationProfile) {
        animationProfile = profile
        keystrokeCount = 0
        if (profile.idleVariant.isNotEmpty()) {
            isIdling = false
            (lottieAnimation)?.playVariant("idle", profile.idleVariant) {
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
        characterAnimation?.playReaction("listening", null)
    }

    fun stopAll() {
        isIdling = false
        wanderEnabled = false
        stopWandering()
        characterAnimation?.stop()
    }

    /** 當前渲染模式 */
    fun getCurrentRenderMode(): RenderMode = renderMode

    companion object {
        /** Insight Engine 提供的 Yukirin VRM 模型 URL */
        const val VRM_MODEL_URL = "https://insight.snowrealm.pet/models/Yukirin.vrm"
    }
}
