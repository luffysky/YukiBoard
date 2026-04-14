package com.snowrealm.yukiboard.app.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.FrameLayout

/**
 * 面板控制列 UI（透明度滑桿 + 圖層交換按鈕）— 從 YukiBoardIMEService 抽出。
 */
class ImePanelControlBar(private val context: Context) {

    private var barView: LinearLayout? = null
    private var swapButton: TextView? = null

    val isShowing: Boolean get() = barView != null

    fun show(
        rootContainer: LinearLayout,
        toolbarView: View,
        panelContainer: FrameLayout,
        onTogglePanelLayer: () -> Unit,
    ) {
        hide(rootContainer)
        val dp = context.resources.displayMetrics.density

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC1A1A2E.toInt())
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        }

        bar.addView(TextView(context).apply {
            text = "面板透明度"
            textSize = 10f
            setTextColor(0xFF8899AA.toInt())
            setPadding(0, 0, (6 * dp).toInt(), 0)
        })

        bar.addView(SeekBar(context).apply {
            max = 255
            progress = 255
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    panelContainer.alpha = p / 255f
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        val swapBtn = TextView(context).apply {
            text = "⇅ 輸入"
            textSize = 11f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(0xFF2A2A4A.toInt())
                cornerRadius = 6 * dp
            }
            setOnClickListener { onTogglePanelLayer() }
        }
        bar.addView(swapBtn)
        swapButton = swapBtn

        val toolbarIndex = rootContainer.indexOfChild(toolbarView)
        if (toolbarIndex < 0) return
        rootContainer.addView(bar, toolbarIndex + 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        barView = bar
    }

    fun hide(rootContainer: LinearLayout) {
        barView?.let { rootContainer.removeView(it) }
        barView = null
        swapButton = null
    }

    /** 更新交換按鈕的文字和顏色（面板在前/後切換時呼叫） */
    fun updateSwapButton(isPanelBehind: Boolean) {
        swapButton?.let { btn ->
            if (isPanelBehind) {
                btn.text = "⇅ 面板"
                btn.setTextColor(0xFFFFD700.toInt())
            } else {
                btn.text = "⇅ 輸入"
                btn.setTextColor(0xFF7FD8FF.toInt())
            }
        }
    }
}
