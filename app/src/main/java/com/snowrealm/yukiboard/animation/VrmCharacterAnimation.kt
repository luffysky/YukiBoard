package com.snowrealm.yukiboard.animation

/**
 * VRM 模型實作的 [CharacterAnimation]。
 *
 * 將鍵盤事件映射到 VRM 的：
 * 1. 表情轉場（blend shape / morph target）
 * 2. glTF 內嵌動畫（若可用）
 * 3. 程序化動作（nod / shake_head / wave / bow / excited / sway / think / stretch）
 *
 * 動作系統參照 Insight Engine 的 YukirinScene.tsx：
 * - 表情使用 VRM expressionManager 等效（happy / surprised / sad / relaxed / angry）
 * - 動作使用根節點 transform 模擬骨骼動畫
 * - 隨機 idle 動作排程（4-13 秒間隔）
 */
class VrmCharacterAnimation(private val view: VrmCharacterView) : CharacterAnimation {

    // 用於 postDelayed 的具名 Runnable（可被 removeCallbacks 清除）
    private var pendingOnEnd: Runnable? = null

    // 鍵盤事件 → 表情 + 動作配置
    private data class ReactionConfig(
        val expression: String?,
        val expressionWeight: Float = 0.85f,
        val proceduralAction: String? = null,
        val glTfAnimName: String? = null,
        val loop: Boolean = false
    )

    private val reactionMap = mapOf(
        // 基礎鍵盤事件
        "idle"        to ReactionConfig("neutral",   0.85f, glTfAnimName = "idle", loop = true),
        "typing"      to ReactionConfig("happy",     0.6f,  proceduralAction = "nod"),
        "happy"       to ReactionConfig("happy",     0.85f, proceduralAction = "excited"),
        "switch_lang" to ReactionConfig("surprised", 0.85f, proceduralAction = "shake_head"),
        "listening"   to ReactionConfig("relaxed",   0.7f,  proceduralAction = "sway"),

        // 遊戲事件
        "game_start"  to ReactionConfig("happy",     0.85f, proceduralAction = "wave"),
        "game_over"   to ReactionConfig("sad",       0.85f, proceduralAction = "bow"),
        "victory"     to ReactionConfig("happy",     0.85f, proceduralAction = "excited"),

        // Yuki 特殊反應
        "freeze"      to ReactionConfig("surprised", 0.9f),
        "think"       to ReactionConfig("neutral",   0.5f,  proceduralAction = "think"),
        "angry"       to ReactionConfig("angry",     0.85f, proceduralAction = "shake_head"),
        "shy"         to ReactionConfig("relaxed",   0.7f,  proceduralAction = "tilt_head"),
        "wink"        to ReactionConfig("happy",     0.7f,  proceduralAction = "tilt_head"),

        // 對應 Insight 的隨機 idle 動作
        "nod"         to ReactionConfig("neutral",   0.5f,  proceduralAction = "nod"),
        "shake_head"  to ReactionConfig("neutral",   0.5f,  proceduralAction = "shake_head"),
        "tilt_head"   to ReactionConfig("relaxed",   0.5f,  proceduralAction = "tilt_head"),
        "bow"         to ReactionConfig(null,         0f,    proceduralAction = "bow"),
        "wave"        to ReactionConfig("happy",     0.7f,  proceduralAction = "wave"),
        "excited"     to ReactionConfig("happy",     0.85f, proceduralAction = "excited"),
        "sway"        to ReactionConfig("relaxed",   0.5f,  proceduralAction = "sway"),
        "stretch"     to ReactionConfig("relaxed",   0.5f,  proceduralAction = "stretch"),
    )

    override fun playIdle() {
        view.clearExpressions()
        view.transitionExpression("neutral", 0.85f)
        view.setRandomIdleEnabled(true)

        // 嘗試播放 glTF idle 動畫（若模型內建）
        view.playAnimationByName("idle", loop = true)
    }

    override fun playReaction(animationName: String, onEnd: (() -> Unit)?) {
        val config = reactionMap[animationName] ?: ReactionConfig("happy", 0.7f)

        // 暫停隨機 idle（反應中不要亂動）
        view.setRandomIdleEnabled(false)

        // 設定表情
        view.clearExpressions()
        config.expression?.let {
            view.transitionExpression(it, config.expressionWeight)
        }

        // 播放程序化動作
        if (config.proceduralAction != null) {
            view.playProceduralAction(config.proceduralAction) {
                // 動作結束，回 idle 表情
                view.clearExpressions()
                view.transitionExpression("neutral", 0.85f)
                view.setRandomIdleEnabled(true)
                onEnd?.invoke()
            }
        } else if (config.glTfAnimName != null) {
            // 播放 glTF 內嵌動畫
            view.playAnimationByName(config.glTfAnimName, loop = config.loop || onEnd == null) {
                view.clearExpressions()
                view.transitionExpression("neutral", 0.85f)
                view.setRandomIdleEnabled(true)
                onEnd?.invoke()
            }
        } else {
            // 無動畫，僅切表情，延遲後回呼
            if (onEnd != null) {
                pendingOnEnd?.let { view.removeCallbacks(it) }
                pendingOnEnd = Runnable {
                    view.clearExpressions()
                    view.transitionExpression("neutral", 0.85f)
                    view.setRandomIdleEnabled(true)
                    onEnd()
                }.also { view.postDelayed(it, 1000) }
            } else {
                view.setRandomIdleEnabled(true)
            }
        }
    }

    override fun stop() {
        pendingOnEnd?.let { view.removeCallbacks(it) }
        pendingOnEnd = null
        view.stopAnimation()
        view.clearExpressions()
        view.setRandomIdleEnabled(false)
    }
}
