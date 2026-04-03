package com.snowrealm.yukiboard.ui.floating.gesture

import android.view.MotionEvent
import android.view.View

/**
 * 雙指縮放手勢處理器。
 *
 * 監聽雙指距離變化，回報寬高 dp 增減。
 */
class PinchGestureHandler(
    private val view: View,
    private val density: Float,
    private val onResize: (deltaWidthDp: Int, deltaHeightDp: Int) -> Unit
) {
    private var initialSpan = 0f
    private var isPinching = false

    init {
        view.setOnTouchListener { _, event ->
            if (event.pointerCount < 2) {
                isPinching = false
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    initialSpan = calculateSpan(event)
                    isPinching = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPinching) {
                        val currentSpan = calculateSpan(event)
                        val delta = currentSpan - initialSpan
                        val deltaDp = (delta / density).toInt()
                        if (kotlin.math.abs(deltaDp) > 2) {
                            onResize(deltaDp, deltaDp)
                            initialSpan = currentSpan
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                    isPinching = false
                    true
                }
                else -> false
            }
        }
    }

    private fun calculateSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
