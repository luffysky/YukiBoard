package com.snowrealm.yukiboard.animation.gles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.TextureView

/**
 * 鍵盤流光 TextureView（取代 GLSurfaceView）。
 *
 * 使用 TextureView + 手動 EGL 管理，確保流光覆蓋層
 * 參與正常 View 層級，不會遮擋上方的 CharacterView。
 */
class KeyboardGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    val renderer = FlowLightRenderer()

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null

    @Volatile private var renderThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var surfaceWidth = 0
    @Volatile private var surfaceHeight = 0

    // S11-5：VSYNC align 用的獨立 lock / tick 計數；
    // 與 `this` lock（alpha=0 暫停 / setFlowAlpha / onPause）分開，
    // 避免 alpha=0 等待被每幀 VSYNC 喚醒後空轉。
    private val vsyncLock = Object()
    @Volatile private var vsyncTick: Long = 0L
    private var vsyncCallback: android.view.Choreographer.FrameCallback? = null

    init {
        isClickable = false
        isFocusable = false
        isOpaque = false  // 讓 TextureView 背景透明
        surfaceTextureListener = this
    }

    /** 動態調整流光整體透明度（0.0 = 關閉，1.0 = 全亮） */
    fun setFlowAlpha(alpha: Float) {
        val wasZero = renderer.alpha <= 0.01f
        renderer.alpha = alpha.coerceIn(0f, 1f)
        if (wasZero && alpha > 0.01f) {
            synchronized(this) { (this as Object).notifyAll() }
        }
    }

    /** 動態調整流光顏色（ARGB Int） */
    fun setFlowColor(colorInt: Int) {
        renderer.setFlowColor(colorInt)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun onPause() {
        running = false
        // S11-5：onPause 立即喚醒 render thread 可能的任一 wait（alpha=0 wait 或 VSYNC wait）
        // 避免原本最壞要等 wait(2000) timeout 才退出 loop。
        synchronized(this) { (this as Object).notifyAll() }
        synchronized(vsyncLock) { (vsyncLock as Object).notifyAll() }
    }

    fun onResume() {
        if (surfaceTexture != null && !running) {
            startRenderThread()
        }
    }

    // ─── SurfaceTextureListener ─────────────────────────────────────────

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        startRenderThread()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        stopRenderThread()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    // ─── GL 渲染執行緒 ──────────────────────────────────────────────────

    @Synchronized
    private fun startRenderThread() {
        if (running) return
        running = true

        startVsyncPump()

        renderThread = Thread({
            initEGL()
            try {
                renderer.onSurfaceCreated(null, null)
                renderer.onSurfaceChanged(null, surfaceWidth, surfaceHeight)

                loop@ while (running) {
                    // alpha 為 0（功能關閉）時完全暫停渲染，等待外部喚醒
                    if (renderer.alpha <= 0.01f) {
                        var interrupted = false
                        synchronized(this@KeyboardGLSurfaceView) {
                            try { (this@KeyboardGLSurfaceView as Object).wait(2000) }
                            catch (_: InterruptedException) { interrupted = true }
                        }
                        if (interrupted) break
                        continue
                    }

                    val w = surfaceWidth
                    val h = surfaceHeight
                    GLES20.glViewport(0, 0, w, h)
                    renderer.onSurfaceChanged(null, w, h)
                    renderer.onDrawFrame(null)

                    if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) break

                    // S11-5：以 Choreographer VSYNC tick 驅動下一幀，
                    // 取代固定 Thread.sleep(16)；
                    // 上限 2 * TARGET_FRAME_NS 作 fallback，
                    // 避免 VSYNC 停止（例如螢幕關閉 / Choreographer 未送達）時 render thread 永久卡住。
                    if (!waitNextVsync()) break
                }
            } finally {
                releaseEGL()
            }
        }, "FlowLightGL").also { it.start() }
    }

    /**
     * S11-5：render thread 呼叫此方法阻塞等下一個 VSYNC tick；
     * 回傳 `false` 代表 thread 被 interrupt，呼叫端應退出 render loop。
     * 超時 fallback（2 * TARGET_FRAME_NS）正常返回 true，避免 VSYNC 停送時 thread 卡住。
     */
    private fun waitNextVsync(): Boolean {
        val lastTick = vsyncTick
        val deadlineNs = System.nanoTime() + TARGET_FRAME_NS * 2
        synchronized(vsyncLock) {
            while (running && vsyncTick == lastTick) {
                val remainingNs = deadlineNs - System.nanoTime()
                if (remainingNs <= 0L) break
                val remainingMs = (remainingNs / 1_000_000L).coerceAtLeast(1L)
                try {
                    (vsyncLock as Object).wait(remainingMs)
                } catch (_: InterruptedException) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * S11-5：在 main thread 上以 Choreographer frame callback 持續送出 VSYNC tick，
     * render thread 以 `vsyncLock` wait 等到 tick 前進才畫下一幀。
     * 所有 Choreographer 呼叫都必須在具 Looper 的 thread 執行，這裡透過 main Looper 的 Handler 轉發。
     */
    private fun startVsyncPump() {
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                synchronized(vsyncLock) {
                    vsyncTick++
                    (vsyncLock as Object).notifyAll()
                }
                if (running) {
                    android.view.Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        vsyncCallback = cb
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.view.Choreographer.getInstance().postFrameCallback(cb)
        }
    }

    private fun stopVsyncPump() {
        val cb = vsyncCallback ?: return
        vsyncCallback = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.view.Choreographer.getInstance().removeFrameCallback(cb)
        }
        // 叫醒 render thread 可能正在 VSYNC wait 的狀態，讓它看到 running=false 後退出。
        synchronized(vsyncLock) { (vsyncLock as Object).notifyAll() }
    }

    private fun stopRenderThread() {
        running = false
        // S11-5：先停 VSYNC pump，再喚醒兩個 lock 的 wait，保證 render thread 能盡快看到 running=false
        stopVsyncPump()
        val t = renderThread
        if (t != null) {
            synchronized(this) { (this as Object).notifyAll() }
            t.interrupt()
            try {
                t.join(500)
                // S11-5：join 超時代表 thread 可能仍在跑（EGL context 未釋放）；
                // 記下來以便後續觀察，Sprint 12 再處理 startRenderThread 併發 race。
                if (t.isAlive) {
                    android.util.Log.w("FlowLightGL", "stopRenderThread: join timeout (500ms), thread still alive")
                }
            } catch (e: InterruptedException) {
                android.util.Log.w("FlowLightGL", "stopRenderThread: interrupted while joining", e)
                Thread.currentThread().interrupt()
            }
        }
        renderThread = null
    }

    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0] ?: run {
            android.util.Log.e("FlowLightGL", "No EGL config found")
            return
        }

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, surfaceAttribs, 0)

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun releaseEGL() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    companion object {
        // 60 FPS target frame period in nanoseconds (1_000_000_000 / 60)。
        // 用作 VSYNC wait 的 fallback deadline 計算基準，不再拿來硬鎖 render 速率。
        private const val TARGET_FRAME_NS = 16_666_666L
    }
}
