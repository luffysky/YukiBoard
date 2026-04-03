package com.snowrealm.yukiboard.ui.floating.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * 右下角 ◢ 24×24dp 拖曳縮放手柄。
 */
class ResizeHandle(
    context: Context,
    private val onResize: (deltaWidthDp: Int, deltaHeightDp: Int) -> Unit
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66B0D4FF.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        // 三角形 ◢
        val path = android.graphics.Path().apply {
            moveTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ((event.rawX - startX) / density).toInt()
                val dy = ((event.rawY - startY) / density).toInt()
                if (dx != 0 || dy != 0) {
                    onResize(dx, dy)
                    startX = event.rawX
                    startY = event.rawY
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
