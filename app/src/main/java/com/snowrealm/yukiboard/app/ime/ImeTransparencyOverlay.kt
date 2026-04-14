package com.snowrealm.yukiboard.app.ime

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 透明度調整 overlay UI — 從 YukiBoardIMEService.showTransparencyOverlay() 抽出。
 *
 * 呼叫端只需提供 prefs、容器、和回呼即可。
 */
class ImeTransparencyOverlay(
    private val context: Context,
    private val prefs: YukiPreferences,
) {

    private var overlayView: View? = null

    /** 回呼：背景遮罩透明度變更 */
    var onBgOverlayAlphaChanged: ((Int) -> Unit)? = null

    /** 回呼：按鍵不透明度變更 */
    var onKeyOpacityChanged: ((Int) -> Unit)? = null

    /** 回呼：文字不透明度變更 */
    var onKeyTextOpacityChanged: ((Int) -> Unit)? = null

    val isShowing: Boolean get() = overlayView != null

    /**
     * 建立 overlay 並插入到 [rootContainer] 的 [toolbarView] 之後。
     */
    fun show(rootContainer: LinearLayout, toolbarView: View) {
        hide(rootContainer)
        val dp = context.resources.displayMetrics.density

        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD1A1A2E.toInt())
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }

        // 標題列：標題 + 關閉按鈕
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "🎨 透明度調整"
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(0xFF8899AA.toInt())
            setPadding((8 * dp).toInt(), 0, 0, 0)
            setOnClickListener { hide(rootContainer) }
        })
        overlay.addView(header)

        // 背景遮罩透明度
        addSlider(overlay, dp,
            label = "背景遮罩",
            initial = prefs.bgOverlayAlpha,
        ) { value ->
            prefs.bgOverlayAlpha = value
            onBgOverlayAlphaChanged?.invoke(value)
        }

        // 按鍵不透明度
        addSlider(overlay, dp,
            label = "按鍵不透明度",
            initial = prefs.keyOpacity,
        ) { value ->
            prefs.keyOpacity = value
            onKeyOpacityChanged?.invoke(value)
        }

        // 文字不透明度
        addSlider(overlay, dp,
            label = "文字不透明度",
            initial = prefs.keyTextOpacity,
        ) { value ->
            prefs.keyTextOpacity = value
            onKeyTextOpacityChanged?.invoke(value)
        }

        // 插入到 toolbarView 之後
        val toolbarIndex = rootContainer.indexOfChild(toolbarView)
        if (toolbarIndex < 0) return
        rootContainer.addView(overlay, toolbarIndex + 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        overlayView = overlay
    }

    fun hide(rootContainer: LinearLayout) {
        overlayView?.let { rootContainer.removeView(it) }
        overlayView = null
    }

    // ---- private ----

    private fun addSlider(
        parent: LinearLayout,
        dp: Float,
        label: String,
        initial: Int,
        onChange: (Int) -> Unit,
    ) {
        val labelView = TextView(context).apply {
            text = "$label: %d%%".format((initial * 100) / 255)
            textSize = 11f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
        }
        parent.addView(labelView)
        parent.addView(SeekBar(context).apply {
            max = 255
            progress = initial
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    labelView.text = "$label: %d%%".format((p * 100) / 255)
                    onChange(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
    }
}
