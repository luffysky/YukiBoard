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
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    init {
        isClickable = false
        isFocusable = false
        isOpaque = false  // 讓 TextureView 背景透明
        surfaceTextureListener = this
    }

    /** 動態調整流光整體透明度（0.0 = 關閉，1.0 = 全亮） */
    fun setFlowAlpha(alpha: Float) {
        renderer.alpha = alpha.coerceIn(0f, 1f)
    }

    /** 動態調整流光顏色（ARGB Int） */
    fun setFlowColor(colorInt: Int) {
        renderer.setFlowColor(colorInt)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun onPause() {
        running = false
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

    private fun startRenderThread() {
        if (running) return
        running = true

        renderThread = Thread({
            initEGL()
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, surfaceWidth, surfaceHeight)

            while (running) {
                // alpha 為 0（功能關閉）時降低到 2fps 節能
                if (renderer.alpha <= 0.01f) {
                    try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                    continue
                }

                val w = surfaceWidth
                val h = surfaceHeight
                GLES20.glViewport(0, 0, w, h)
                renderer.onSurfaceChanged(null, w, h)
                renderer.onDrawFrame(null)

                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) break

                try { Thread.sleep(16) } catch (_: InterruptedException) { break }
            }

            releaseEGL()
        }, "FlowLightGL").also { it.start() }
    }

    private fun stopRenderThread() {
        running = false
        renderThread?.interrupt()
        try { renderThread?.join(500) } catch (_: Exception) {}
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
        eglConfig = configs[0]!!

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
}
