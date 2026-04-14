package com.snowrealm.yukiboard.app.ime

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.FloatingKeyboardWrapper
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 懸浮鍵盤 UI 建構 — 從 YukiBoardIMEService.wrapInFloatingMode() 抽出。
 *
 * 回傳 [FloatingModeResult] 以便 service 設回自己的 field。
 */
class ImeFloatingModeHelper(
    private val context: Context,
    private val prefs: YukiPreferences,
) {

    data class FloatingModeResult(
        val outerFrame: FrameLayout,
        val wrapper: FloatingKeyboardWrapper,
    )

    fun wrap(
        content: LinearLayout,
        theme: KeyboardTheme,
        onDock: () -> Unit,
        onClose: () -> Unit,
        onMinimize: () -> Unit,
    ): FloatingModeResult {
        val wrapper = FloatingKeyboardWrapper(context).apply {
            this.theme = theme
            setWillNotDraw(false)
        }

        val density = context.resources.displayMetrics.density
        val dragBarH = (24 * density).toInt()
        val actionBarH = (28 * density).toInt()

        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dragBarH
            bottomMargin = actionBarH
        }
        wrapper.addView(content, contentParams)

        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val kbWidth = (screenW * prefs.keyboardWidthRatio).toInt()

        val outerFrame = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
        }
        val outerH = (screenH * 0.65f).toInt()

        val wrapperParams = FrameLayout.LayoutParams(kbWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
        if (prefs.floatingX >= 0) {
            wrapperParams.gravity = Gravity.NO_GRAVITY
            wrapperParams.leftMargin = prefs.floatingX
            wrapperParams.topMargin = prefs.floatingY
        } else {
            wrapperParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        outerFrame.addView(wrapper, wrapperParams)

        wrapper.onDragMove = { dx, dy ->
            val lp = wrapper.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.NO_GRAVITY
            lp.leftMargin = (lp.leftMargin + dx.toInt()).coerceIn(0, screenW - kbWidth)
            lp.topMargin = (lp.topMargin + dy.toInt()).coerceIn(0, outerH - dragBarH - actionBarH)
            wrapper.layoutParams = lp
            prefs.floatingX = lp.leftMargin
            prefs.floatingY = lp.topMargin
        }

        wrapper.onToggleDock = onDock

        wrapper.onClose = onClose

        val minW = (screenW * 0.5f).toInt()
        val maxW = screenW
        wrapper.onResize = { deltaWidth ->
            val lp = wrapper.layoutParams as FrameLayout.LayoutParams
            val newW = (lp.width + deltaWidth).coerceIn(minW, maxW)
            lp.width = newW
            if (lp.leftMargin + newW > screenW) {
                lp.leftMargin = screenW - newW
            }
            wrapper.layoutParams = lp
            prefs.keyboardWidthRatio = newW.toFloat() / screenW
        }

        wrapper.onMinimize = onMinimize

        outerFrame.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, outerH
        )

        return FloatingModeResult(outerFrame, wrapper)
    }
}
