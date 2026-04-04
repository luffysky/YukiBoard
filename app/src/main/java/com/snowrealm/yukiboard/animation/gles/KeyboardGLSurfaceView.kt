package com.snowrealm.yukiboard.animation.gles

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * 鍵盤流光 [GLSurfaceView]。
 *
 * 設計為半透明覆蓋層，疊加在 [com.snowrealm.yukiboard.ui.keyboard.KeyboardView] 之上。
 * XML 佈局中使用 `android:background="@android:color/transparent"` 讓底層鍵盤穿透可見。
 *
 * 使用方式：
 * ```xml
 * <com.snowrealm.yukiboard.animation.gles.KeyboardGLSurfaceView
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:background="@android:color/transparent" />
 * ```
 */
class KeyboardGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = FlowLightRenderer()

    init {
        // 讓觸控事件穿透到下方的 KeyboardView
        isClickable = false
        isFocusable = false
        setEGLContextClientVersion(2)
        // 需要 alpha 通道讓流光透明疊加
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
        setZOrderOnTop(true)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** 動態調整流光整體透明度（0.0 = 關閉，1.0 = 全亮） */
    fun setFlowAlpha(alpha: Float) {
        queueEvent { renderer.alpha = alpha.coerceIn(0f, 1f) }
    }

    /** 動態調整流光顏色（ARGB Int） */
    fun setFlowColor(colorInt: Int) {
        queueEvent { renderer.setFlowColor(colorInt) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onPause() {
        super.onPause()
        // 省電：暫停渲染
    }

    override fun onResume() {
        super.onResume()
    }
}
