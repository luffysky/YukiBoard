package com.snowrealm.yukiboard.ui.floating.gesture

import android.view.MotionEvent
import android.view.View

/**
 * 標題列拖曳手勢處理器。
 *
 * 綁定到標題列 View，拖曳時回報 (deltaX, deltaY) 像素偏移，
 * 放開時呼叫 [onDragEnd] 以觸發吸附。
 */
class DragGestureHandler(
    private val view: View,
    private val onDrag: (dx: Int, dy: Int) -> Unit
) {
    var onDragEnd: (() -> Unit)? = null

    private var startRawX = 0f
    private var startRawY = 0f
    private var isDragging = false

    init {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    if (dx * dx + dy * dy > 25 || isDragging) {
                        isDragging = true
                        onDrag(dx, dy)
                        startRawX = event.rawX
                        startRawY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        onDragEnd?.invoke()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }
}
