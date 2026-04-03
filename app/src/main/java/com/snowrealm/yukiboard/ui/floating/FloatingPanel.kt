package com.snowrealm.yukiboard.ui.floating

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.ui.floating.gesture.DragGestureHandler
import com.snowrealm.yukiboard.ui.floating.gesture.ResizeHandle

/**
 * 懸浮面板抽象基礎類別。
 *
 * 子類實作 [createContentView] 提供面板內容，
 * 基礎類別負責視窗管理、標題列、拖曳、縮放、吸附。
 */
abstract class FloatingPanel(
    protected val context: Context,
    protected val config: FloatingPanelConfig = FloatingPanelConfig()
) {
    abstract val panelId: String
    abstract val panelTitle: String

    var isShowing: Boolean = false
        private set

    var currentX: Int = config.defaultX
    var currentY: Int = config.defaultY
    var currentWidthDp: Int = config.defaultWidthDp
    var currentHeightDp: Int = config.defaultHeightDp

    private var rootView: FrameLayout? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val density: Float get() = context.resources.displayMetrics.density

    // ─────────────────────────────────────────────────────────
    // 公開 API
    // ─────────────────────────────────────────────────────────

    fun show() {
        if (isShowing) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val root = buildRootView()
        rootView = root

        val lp = WindowManager.LayoutParams(
            dpToPx(currentWidthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentX
            y = currentY
        }
        layoutParams = lp

        wm.addView(root, lp)
        isShowing = true

        // 進場動畫
        root.scaleX = 0.8f
        root.scaleY = 0.8f
        root.alpha = 0f
        root.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(250)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        onShown()
    }

    fun hide() {
        if (!isShowing) return
        val root = rootView ?: return
        root.animate()
            .scaleX(0.8f).scaleY(0.8f).alpha(0f)
            .setDuration(200)
            .withEndAction {
                removeFromWindow()
                onHidden()
            }
            .start()
    }

    fun dismiss() {
        removeFromWindow()
        onDismissed()
    }

    fun moveTo(x: Int, y: Int) {
        currentX = x
        currentY = y
        layoutParams?.let { lp ->
            lp.x = x
            lp.y = y
            rootView?.let { windowManager?.updateViewLayout(it, lp) }
        }
    }

    fun resize(widthDp: Int, heightDp: Int) {
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val maxW = if (config.maxWidthDp > 0) config.maxWidthDp else (screenW / density / 2).toInt()
        val maxH = if (config.maxHeightDp > 0) config.maxHeightDp else (screenH / density / 2).toInt()

        currentWidthDp = widthDp.coerceIn(config.minWidthDp, maxW)
        currentHeightDp = heightDp.coerceIn(config.minHeightDp, maxH)

        layoutParams?.let { lp ->
            lp.width = dpToPx(currentWidthDp)
            // height stays WRAP_CONTENT, content controls actual height
            rootView?.let { windowManager?.updateViewLayout(it, lp) }
        }
    }

    fun snapToNearestEdge() {
        if (!config.snapToEdge) return
        val screenW = context.resources.displayMetrics.widthPixels
        val panelW = dpToPx(currentWidthDp)
        val centerX = currentX + panelW / 2
        val targetX = if (centerX < screenW / 2) 0 else screenW - panelW

        val animator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200
            addUpdateListener { moveTo(it.animatedValue as Int, currentY) }
        }
        animator.start()
    }

    // ─────────────────────────────────────────────────────────
    // 子類 Hook
    // ─────────────────────────────────────────────────────────

    abstract fun createContentView(inflater: LayoutInflater): View

    open fun onShown() {}
    open fun onHidden() {}
    open fun onDismissed() {}

    // ─────────────────────────────────────────────────────────
    // 內部建構
    // ─────────────────────────────────────────────────────────

    private fun buildRootView(): FrameLayout {
        val root = FrameLayout(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xEE1A2035.toInt())
                cornerRadius = config.cornerRadius * density
            }
            clipToOutline = true
            elevation = 8 * density
        }

        // 標題列
        val titleBar = buildTitleBar()
        container.addView(titleBar)

        // 面板內容
        val inflater = LayoutInflater.from(context)
        val content = createContentView(inflater)
        container.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { weight = 1f })

        root.addView(container, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        // 右下角縮放手柄
        val handle = ResizeHandle(context) { deltaXDp, deltaYDp ->
            resize(currentWidthDp + deltaXDp, currentHeightDp + deltaYDp)
        }
        root.addView(handle, FrameLayout.LayoutParams(
            dpToPx(24), dpToPx(24), Gravity.BOTTOM or Gravity.END
        ))

        return root
    }

    private fun buildTitleBar(): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(10), dpToPx(6), dpToPx(6), dpToPx(6))
            setBackgroundColor(0xFF0F1828.toInt())
        }

        // 雪凜小圖 placeholder
        bar.addView(TextView(context).apply {
            text = "❄"
            textSize = 14f
            setPadding(0, 0, dpToPx(6), 0)
        })

        // 標題
        bar.addView(TextView(context).apply {
            text = panelTitle
            textSize = 13f
            setTextColor(0xFFE8F4FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // 最小化按鈕
        bar.addView(makeTitleButton("─") { hide() })

        // 關閉按鈕
        bar.addView(makeTitleButton("×") { dismiss() })

        // 拖曳手勢
        DragGestureHandler(bar) { dx, dy ->
            moveTo(currentX + dx, currentY + dy)
        }.also { it.onDragEnd = { snapToNearestEdge() } }

        return bar
    }

    private fun makeTitleButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun removeFromWindow() {
        rootView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        rootView = null
        isShowing = false
    }

    protected fun dpToPx(dp: Int): Int = (dp * density).toInt()
}
