package com.snowrealm.yukiboard.animation

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sin

/**
 * VRM 角色渲染 View — 使用 Filament 引擎渲染 3D 角色。
 *
 * VRM 是 glTF 2.0 的擴展格式，Filament 可直接載入其 glTF 部分。
 * 表情透過 morph target（blend shape）控制。
 * 動作透過 glTF 內嵌動畫 + 程序化骨骼動畫控制。
 *
 * 支援的動作系統（參照 Insight YukirinScene）：
 * - 表情轉場（smooth lerp）
 * - 自動眨眼
 * - 內嵌 glTF 動畫播放
 * - 程序化動作（nod / shake_head / tilt_head / bow / excited）
 * - 隨機 idle 動作排程
 */
class VrmCharacterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr) {

    companion object {
        init {
            Utils.init()
        }

        // VRM 表情名稱 → morph target 名稱映射
        private val EXPRESSION_MAP = mapOf(
            "neutral" to "Neutral",
            "happy" to "Happy",
            "angry" to "Angry",
            "sad" to "Sad",
            "relaxed" to "Relaxed",
            "surprised" to "Surprised",
            "blink" to "Blink",
            "blinkLeft" to "BlinkLeft",
            "blinkRight" to "BlinkRight",
        )

        // VRM 1.0 標準表情索引（多數 VRoid Studio 輸出的模型遵循此順序）
        private val VRM_EXPRESSION_INDEX = mapOf(
            "Happy" to 0,
            "Angry" to 1,
            "Sad" to 2,
            "Relaxed" to 3,
            "Surprised" to 4,
            "Neutral" to 5,
            "Blink" to 6,
            "BlinkLeft" to 7,
            "BlinkRight" to 8,
            // 口型（lip sync 用）
            "aa" to 9,
            "ih" to 10,
            "ou" to 11,
            "ee" to 12,
            "oh" to 13,
        )
    }

    // ── Filament 引擎 ──────────────────────────────────────────────────────
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var filamentAsset: FilamentAsset? = null
    private var animator: Animator? = null

    private var uiHelper: UiHelper? = null
    private var displayHelper: DisplayHelper? = null
    private var choreographer: Choreographer? = null

    private var isReady = false
    private var isDestroyed = false
    private var needsRender = true
    private var lastFrameTimeNanos = 0L

    // ── glTF 動畫狀態 ──────────────────────────────────────────────────────
    private var currentAnimIndex = -1
    private var animationTime = 0.0
    private var isAnimating = false
    private var loopAnimation = false
    private var onAnimationEnd: (() -> Unit)? = null

    // ── 表情混合 ────────────────────────────────────────────────────────────
    private var targetExpressionWeights = mutableMapOf<String, Float>()
    private var currentExpressionWeights = mutableMapOf<String, Float>()

    // ── Morph target 快取 ─────────────────────────────────────────────────
    // entity instance → morph target count
    private var morphEntities = mutableListOf<Pair<Int, Int>>()

    // ── 眨眼定時 ────────────────────────────────────────────────────────────
    private var blinkTimer = 0f
    private var nextBlinkTime = 3f + (Math.random() * 4f).toFloat()
    private var isBlinking = false

    // ── 程序化動作 ──────────────────────────────────────────────────────────
    private var proceduralAction: ProceduralAction? = null

    // ── 隨機 idle 排程 ─────────────────────────────────────────────────────
    private var randomIdleEnabled = true
    private var randomIdleTimer = 0f
    private var nextRandomIdleTime = 4f + (Math.random() * 9f).toFloat()
    var onRandomAction: ((String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isDestroyed) return
            choreographer?.postFrameCallback(this)

            val dt = if (lastFrameTimeNanos == 0L) 1f / 60f
                     else (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            lastFrameTimeNanos = frameTimeNanos

            update(dt)
            render()
        }
    }

    init {
        setZOrderOnTop(true)
        isClickable = false
        isFocusable = false
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                initFilament()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                updateViewport(w, h)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopRendering()
            }
        })
    }

    private fun initFilament() {
        if (isDestroyed || engine != null) return

        engine = Engine.create().also { eng ->
            renderer = eng.createRenderer()
            scene = eng.createScene()

            val cameraEntity = EntityManager.get().create()
            camera = eng.createCamera(cameraEntity).apply {
                setProjection(45.0, 1.0, 0.1, 100.0, Camera.Fov.VERTICAL)
                lookAt(0.0, 0.8, 2.5, 0.0, 0.8, 0.0, 0.0, 1.0, 0.0)
            }

            view = eng.createView().apply {
                this.camera = this@VrmCharacterView.camera
                this.scene = this@VrmCharacterView.scene
                isPostProcessingEnabled = false
            }

            // 透明背景
            renderer?.clearOptions = Renderer.ClearOptions().apply {
                clear = true
            }

            // 環境光
            val ibl = IndirectLight.Builder()
                .intensity(30000f)
                .build(eng)
            scene?.indirectLight = ibl

            // 主方向光
            val lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 0.98f, 0.95f)
                .intensity(100000f)
                .direction(0.3f, -1f, -0.5f)
                .castShadows(false)
                .build(eng, lightEntity)
            scene?.addEntity(lightEntity)

            // gltfio
            val materialProvider = UbershaderProvider(eng)
            assetLoader = AssetLoader(eng, materialProvider, EntityManager.get())
            resourceLoader = ResourceLoader(eng)
        }

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            isOpaque = false
            attachTo(this@VrmCharacterView)
        }
        displayHelper = DisplayHelper(context)
        choreographer = Choreographer.getInstance()

        isReady = true
        startRendering()
    }

    // =========================================================================
    // 模型載入
    // =========================================================================

    /** 從 assets 載入 VRM/GLB 模型 */
    fun loadModelFromAsset(assetPath: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.assets.open(assetPath).use { it.readBytes() }
                withContext(Dispatchers.Main) { loadModelBytes(bytes) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 從檔案路徑載入 */
    fun loadModelFromFile(filePath: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = File(filePath).readBytes()
                withContext(Dispatchers.Main) { loadModelBytes(bytes) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 從 URL 下載並快取 */
    fun loadModelFromUrl(url: String) {
        val cacheFile = File(context.cacheDir, "vrm_model_${url.hashCode()}.glb")
        scope.launch(Dispatchers.IO) {
            try {
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    val conn = java.net.URL(url).openConnection()
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.getInputStream().use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val bytes = cacheFile.readBytes()
                withContext(Dispatchers.Main) { loadModelBytes(bytes) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadModelBytes(bytes: ByteArray) {
        val eng = engine ?: return
        val loader = assetLoader ?: return
        val resLoader = resourceLoader ?: return

        // 清除舊模型
        filamentAsset?.let { asset ->
            scene?.removeEntities(asset.entities)
            loader.destroyAsset(asset)
        }
        morphEntities.clear()

        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }

        filamentAsset = loader.createAsset(buffer)?.also { asset ->
            resLoader.loadResources(asset)
            asset.releaseSourceData()
            scene?.addEntities(asset.entities)
            animator = asset.getInstance().animator

            // 掃描含 morph target 的 entity
            buildMorphEntityList(eng, asset)

            // 設定初始姿勢
            animator?.let { anim ->
                if (anim.animationCount > 0) {
                    anim.applyAnimation(0, 0f)
                    anim.updateBoneMatrices()
                }
            }

            needsRender = true
        }
    }

    /** 掃描含 morph target 的 entity，供表情控制用 */
    private fun buildMorphEntityList(eng: Engine, asset: FilamentAsset) {
        val rm = eng.renderableManager
        for (entity in asset.entities) {
            val instance = rm.getInstance(entity)
            if (instance == 0) continue
            val count = rm.getMorphTargetCount(instance)
            if (count > 0) {
                morphEntities.add(Pair(instance, count))
            }
        }
    }

    // =========================================================================
    // glTF 動畫播放
    // =========================================================================

    /** 播放動畫（by index） */
    fun playAnimation(index: Int, loop: Boolean = false, onEnd: (() -> Unit)? = null) {
        val anim = animator ?: return
        if (index < 0 || index >= anim.animationCount) return

        currentAnimIndex = index
        animationTime = 0.0
        isAnimating = true
        loopAnimation = loop
        this.onAnimationEnd = onEnd
    }

    /** 播放動畫（by name） */
    fun playAnimationByName(name: String, loop: Boolean = false, onEnd: (() -> Unit)? = null) {
        val anim = animator ?: return
        for (i in 0 until anim.animationCount) {
            if (anim.getAnimationName(i).contains(name, ignoreCase = true)) {
                playAnimation(i, loop, onEnd)
                return
            }
        }
        if (anim.animationCount > 0) playAnimation(0, loop, onEnd)
    }

    /** 停止動畫 */
    fun stopAnimation() {
        isAnimating = false
        currentAnimIndex = -1
        onAnimationEnd = null
    }

    /** 取得可用動畫清單 */
    fun getAnimationNames(): List<String> {
        val anim = animator ?: return emptyList()
        return (0 until anim.animationCount).map { anim.getAnimationName(it) }
    }

    // =========================================================================
    // 表情系統（VRM blend shape / morph targets）
    // =========================================================================

    /** 設定表情（清除其他，只保留指定表情） */
    fun setExpression(name: String, weight: Float = 1f) {
        targetExpressionWeights.clear()
        targetExpressionWeights[name] = weight.coerceIn(0f, 1f)
    }

    /** 混合表情（不清除其他） */
    fun blendExpression(name: String, weight: Float) {
        targetExpressionWeights[name] = weight.coerceIn(0f, 1f)
    }

    /** 清除所有表情（回 neutral） */
    fun clearExpressions() {
        targetExpressionWeights.clear()
    }

    /** 平滑轉場到指定表情（模仿 Insight transitionExpression） */
    fun transitionExpression(name: String, weight: Float = 0.85f, durationMs: Int = 300) {
        // 先清除其他表情
        targetExpressionWeights.clear()
        targetExpressionWeights[name] = weight.coerceIn(0f, 1f)
        // lerp 速度由 update() 自動處理
    }

    // =========================================================================
    // 程序化動作系統（對應 Insight 的 bone-based 動畫）
    // =========================================================================

    /**
     * 播放程序化動作。
     * 由於 Filament 不直接暴露骨骼名稱，這裡使用 TransformManager
     * 對根場景節點做微小變換模擬動作效果。
     */
    fun playProceduralAction(action: String, onEnd: (() -> Unit)? = null) {
        proceduralAction = when (action) {
            "nod" -> NodAction(onEnd)
            "shake_head" -> ShakeHeadAction(onEnd)
            "tilt_head" -> TiltHeadAction(onEnd)
            "bow" -> BowAction(onEnd)
            "excited" -> ExcitedBounceAction(onEnd)
            "sway" -> SwayAction(onEnd)
            "wave" -> WaveAction(onEnd)
            "think" -> ThinkAction(onEnd)
            "stretch" -> StretchAction(onEnd)
            else -> null
        }
    }

    /** 啟用/禁用隨機 idle 動作 */
    fun setRandomIdleEnabled(enabled: Boolean) {
        randomIdleEnabled = enabled
        if (!enabled) {
            randomIdleTimer = 0f
        }
    }

    // =========================================================================
    // 每幀更新
    // =========================================================================

    private fun update(dt: Float) {
        if (!isReady || isDestroyed) return

        val anim = animator

        // glTF 動畫更新
        if (anim != null && isAnimating && currentAnimIndex >= 0) {
            animationTime += dt.toDouble()
            val duration = anim.getAnimationDuration(currentAnimIndex).toDouble()

            if (loopAnimation) {
                animationTime %= duration.coerceAtLeast(0.001)
            } else if (animationTime >= duration) {
                animationTime = duration
                isAnimating = false
                onAnimationEnd?.invoke()
                onAnimationEnd = null
            }

            anim.applyAnimation(currentAnimIndex, animationTime.toFloat())
        }

        // 程序化動作更新
        proceduralAction?.let { action ->
            action.update(dt)
            applyProceduralTransform(action)
            if (action.isFinished) {
                resetProceduralTransform()
                action.onEnd?.invoke()
                proceduralAction = null
            }
        }

        // 自動眨眼
        blinkTimer += dt
        if (!isBlinking && blinkTimer >= nextBlinkTime) {
            isBlinking = true
            blinkTimer = 0f
        }
        if (isBlinking) {
            val blinkPhase = blinkTimer
            val blinkWeight = when {
                blinkPhase < 0.05f -> blinkPhase / 0.05f
                blinkPhase < 0.1f -> 1f
                blinkPhase < 0.15f -> 1f - (blinkPhase - 0.1f) / 0.05f
                else -> {
                    isBlinking = false
                    nextBlinkTime = 2f + (Math.random() * 5f).toFloat()
                    blinkTimer = 0f
                    0f
                }
            }
            applyMorphTarget("Blink", blinkWeight)
        }

        // 表情混合（smooth lerp）
        for ((name, target) in targetExpressionWeights) {
            val current = currentExpressionWeights.getOrDefault(name, 0f)
            val lerped = current + (target - current) * (dt * 5f).coerceAtMost(1f)
            currentExpressionWeights[name] = lerped
            val morphName = EXPRESSION_MAP[name] ?: name
            applyMorphTarget(morphName, lerped)
        }
        // 淡出不再需要的表情
        val keysToRemove = mutableListOf<String>()
        for ((name, current) in currentExpressionWeights) {
            if (name !in targetExpressionWeights && current > 0.01f) {
                val lerped = current * (1f - dt * 5f).coerceAtLeast(0f)
                currentExpressionWeights[name] = lerped
                val morphName = EXPRESSION_MAP[name] ?: name
                applyMorphTarget(morphName, lerped)
            } else if (name !in targetExpressionWeights) {
                keysToRemove.add(name)
            }
        }
        keysToRemove.forEach { currentExpressionWeights.remove(it) }

        // 隨機 idle 動作排程
        if (randomIdleEnabled && proceduralAction == null) {
            randomIdleTimer += dt
            if (randomIdleTimer >= nextRandomIdleTime) {
                randomIdleTimer = 0f
                nextRandomIdleTime = 4f + (Math.random() * 9f).toFloat()
                val actions = listOf("nod", "tilt_head", "sway", "excited")
                val chosen = actions.random()
                playProceduralAction(chosen)
                onRandomAction?.invoke(chosen)
            }
        }

        anim?.updateBoneMatrices()
        needsRender = true
    }

    /**
     * 套用 morph target 權重。
     *
     * VRM 標準表情在 morph target 中的順序是固定的（VRM 1.0 spec）：
     * 0=Happy, 1=Angry, 2=Sad, 3=Relaxed, 4=Surprised,
     * 5=Neutral, 6=Blink, 7=BlinkLeft, 8=BlinkRight
     *
     * 但實際排列依模型而異，Filament 不提供名稱查詢，
     * 因此我們對所有含 morph 的 entity 以索引映射方式套用。
     */
    private fun applyMorphTarget(name: String, weight: Float) {
        val eng = engine ?: return
        val rm = eng.renderableManager

        // VRM 表情名稱→常見索引映射（多數 VRM 模型遵循此順序）
        val targetIndex = VRM_EXPRESSION_INDEX[name] ?: return

        for ((instance, count) in morphEntities) {
            if (targetIndex < count) {
                try {
                    rm.setMorphWeights(instance, floatArrayOf(weight), targetIndex)
                } catch (_: Exception) {
                    // 部分 entity 可能不支援此索引
                }
            }
        }
    }


    /** 將程序化動作的變換套用到模型根節點 */
    private fun applyProceduralTransform(action: ProceduralAction) {
        val asset = filamentAsset ?: return
        val eng = engine ?: return
        val tm = eng.transformManager
        val root = asset.root
        val instance = tm.getInstance(root)
        if (instance == 0) return

        // 取得原始 transform 並套用微小旋轉/位移
        val mat = FloatArray(16)
        tm.getTransform(instance, mat)

        // 套用程序化旋轉（繞 X/Y/Z 軸）
        val rx = action.rotX
        val ry = action.rotY
        val rz = action.rotZ
        val ty = action.transY

        // 簡化版：直接修改 rotation 分量
        // mat[0..3] = column 0, mat[4..7] = column 1, etc.
        // 對 4x4 矩陣做微小旋轉疊加
        val cosX = kotlin.math.cos(rx); val sinX = sin(rx)
        val cosY = kotlin.math.cos(ry); val sinY = sin(ry)
        val cosZ = kotlin.math.cos(rz); val sinZ = sin(rz)

        // 組合 Rz * Ry * Rx 旋轉矩陣（小角度）
        val r = FloatArray(16)
        r[0]  = cosY * cosZ
        r[1]  = cosY * sinZ
        r[2]  = -sinY
        r[4]  = sinX * sinY * cosZ - cosX * sinZ
        r[5]  = sinX * sinY * sinZ + cosX * cosZ
        r[6]  = sinX * cosY
        r[8]  = cosX * sinY * cosZ + sinX * sinZ
        r[9]  = cosX * sinY * sinZ - sinX * cosZ
        r[10] = cosX * cosY
        r[3]  = 0f; r[7] = 0f; r[11] = 0f
        r[12] = 0f; r[13] = ty; r[14] = 0f; r[15] = 1f

        tm.setTransform(instance, r)
    }

    private fun resetProceduralTransform() {
        val asset = filamentAsset ?: return
        val eng = engine ?: return
        val tm = eng.transformManager
        val root = asset.root
        val instance = tm.getInstance(root)
        if (instance == 0) return

        // 重置為單位矩陣
        val identity = FloatArray(16).also {
            it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f
        }
        tm.setTransform(instance, identity)
    }

    private fun render() {
        if (!isReady || isDestroyed || !needsRender) return

        val rndr = renderer ?: return
        val vw = view ?: return
        val sc = swapChain

        if (sc != null && rndr.beginFrame(sc, 0)) {
            rndr.render(vw)
            rndr.endFrame()
        }
    }

    private fun updateViewport(w: Int, h: Int) {
        view?.viewport = Viewport(0, 0, w, h)
        camera?.let { cam ->
            val aspect = w.toDouble() / h.toDouble()
            cam.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
        }
        needsRender = true
    }

    private fun startRendering() {
        choreographer?.postFrameCallback(frameCallback)
    }

    private fun stopRendering() {
        choreographer?.removeFrameCallback(frameCallback)
    }

    fun destroy() {
        isDestroyed = true
        stopRendering()
        scope.cancel()

        val eng = engine ?: return

        filamentAsset?.let { asset ->
            scene?.removeEntities(asset.entities)
            assetLoader?.destroyAsset(asset)
        }
        filamentAsset = null
        animator = null
        morphEntities.clear()

        view?.let { eng.destroyView(it) }
        scene?.let { eng.destroyScene(it) }
        renderer?.let { eng.destroyRenderer(it) }
        camera?.let { eng.destroyCameraComponent(camera!!.entity) }

        resourceLoader?.destroy()
        assetLoader?.destroy()

        uiHelper?.detach()

        eng.destroy()
        engine = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    // =========================================================================
    // 程序化動作定義（對應 Insight YukirinScene 的骨骼動畫）
    // =========================================================================

    /** 程序化動作基底，每幀更新旋轉/位移值 */
    abstract class ProceduralAction(val onEnd: (() -> Unit)? = null) {
        var t = 0f
        var rotX = 0f  // 繞 X 軸旋轉（pitch：點頭/鞠躬）
        var rotY = 0f  // 繞 Y 軸旋轉（yaw：搖頭）
        var rotZ = 0f  // 繞 Z 軸旋轉（roll：歪頭/搖擺）
        var transY = 0f // Y 軸位移（彈跳）
        var isFinished = false
        abstract fun update(dt: Float)
    }

    /** 點頭（對應 Insight playNod） */
    class NodAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 6f  // ~0.1 per frame @ 60fps
            val decay = (1f - t / (Math.PI.toFloat() * 2f)).coerceAtLeast(0f)
            rotX = sin(t * 3f) * 0.22f * decay
            if (t >= Math.PI.toFloat() * 2f) {
                rotX = 0f
                isFinished = true
            }
        }
    }

    /** 搖頭（對應 Insight playShakeHead） */
    class ShakeHeadAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 5.4f  // ~0.09 per frame
            val decay = (1f - t / (Math.PI.toFloat() * 2.2f)).coerceAtLeast(0f)
            rotY = sin(t * 3.5f) * 0.28f * decay
            if (t >= Math.PI.toFloat() * 2.2f) {
                rotY = 0f
                isFinished = true
            }
        }
    }

    /** 歪頭（對應 Insight playTiltHead） */
    class TiltHeadAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        private val dir = if (Math.random() > 0.5) 1f else -1f
        override fun update(dt: Float) {
            t += dt * 2.4f  // ~0.04 per frame
            rotZ = sin(t.coerceAtMost(Math.PI.toFloat())) * 0.28f * dir
            if (t >= Math.PI.toFloat() * 3f) {
                rotZ = 0f
                isFinished = true
            }
        }
    }

    /** 鞠躬（對應 Insight playBow） */
    class BowAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 2.4f
            rotX = sin(t.coerceAtMost(Math.PI.toFloat())) * 0.35f
            if (t >= Math.PI.toFloat() * 2f) {
                rotX = 0f
                isFinished = true
            }
        }
    }

    /** 興奮抖動（對應 Insight playExcitedBounce） */
    class ExcitedBounceAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 12f  // ~0.2 per frame
            val decay = (1f - t / (Math.PI.toFloat() * 2.5f)).coerceAtLeast(0f)
            transY = abs(sin(t * 5f)) * 0.025f * decay
            if (t >= Math.PI.toFloat() * 2.5f) {
                transY = 0f
                isFinished = true
            }
        }
    }

    /** 左右搖擺（對應 Insight playSway） */
    class SwayAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 2.7f  // ~0.045 per frame
            val decay = (1f - t / (Math.PI.toFloat() * 3f)).coerceAtLeast(0f)
            rotZ = sin(t * 2f) * 0.07f * decay
            if (t >= Math.PI.toFloat() * 3f) {
                rotZ = 0f
                isFinished = true
            }
        }
    }

    /** 揮手 — 用 Y 軸旋轉 + Z 軸搖擺模擬（對應 Insight playArmWave） */
    class WaveAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 4.2f  // ~0.07 per frame
            rotZ = sin(t * 3f) * 0.15f
            rotY = 0.1f
            if (t >= Math.PI.toFloat() * 2.5f) {
                rotZ = 0f; rotY = 0f
                isFinished = true
            }
        }
    }

    /** 思考 — 微傾 + 小幅旋轉（對應 Insight playThinkPose） */
    class ThinkAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 2.4f
            val p = (t / (Math.PI.toFloat() * 0.5f)).coerceAtMost(1f)
            val ease = if (p < 0.5f) 2f * p * p else 1f - (-2f * p + 2f).let { it * it } / 2f
            rotZ = 0.07f * ease
            rotX = -0.05f * ease
            if (t >= Math.PI.toFloat() * 3.5f) {
                rotZ = 0f; rotX = 0f
                isFinished = true
            }
        }
    }

    /** 伸展 — 上抬 + 旋轉（對應 Insight playStretch） */
    class StretchAction(onEnd: (() -> Unit)? = null) : ProceduralAction(onEnd) {
        override fun update(dt: Float) {
            t += dt * 2.4f
            val ext = sin((t * 0.7f).coerceAtMost(Math.PI.toFloat()))
            transY = ext * 0.03f
            rotX = -ext * 0.06f
            if (t >= Math.PI.toFloat() * 2.8f) {
                transY = 0f; rotX = 0f
                isFinished = true
            }
        }
    }
}
