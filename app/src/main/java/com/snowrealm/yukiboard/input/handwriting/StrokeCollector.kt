package com.snowrealm.yukiboard.input.handwriting

import android.view.MotionEvent
import com.google.mlkit.vision.digitalink.Ink

/**
 * 筆跡收集器 — 將 MotionEvent 序列轉換為 ML Kit Ink 物件。
 *
 * 用法：
 * - ACTION_DOWN → startStroke()
 * - ACTION_MOVE → addPoint()
 * - ACTION_UP   → finishStroke()
 * - 辨識完 → reset()
 */
class StrokeCollector {

    private val inkBuilder = Ink.builder()
    private var strokeBuilder: Ink.Stroke.Builder? = null
    private var _hasStrokes = false

    val hasStrokes: Boolean get() = _hasStrokes

    fun startStroke(event: MotionEvent) {
        strokeBuilder = Ink.Stroke.builder().also {
            it.addPoint(Ink.Point.create(event.x, event.y, event.eventTime))
        }
    }

    fun addPoint(event: MotionEvent) {
        strokeBuilder?.addPoint(Ink.Point.create(event.x, event.y, event.eventTime))
    }

    fun finishStroke(event: MotionEvent) {
        val builder = strokeBuilder ?: return
        builder.addPoint(Ink.Point.create(event.x, event.y, event.eventTime))
        inkBuilder.addStroke(builder.build())
        strokeBuilder = null
        _hasStrokes = true
    }

    fun buildInk(): Ink {
        return inkBuilder.build()
    }

    fun reset() {
        // Ink.Builder 沒有 clear 方法，需要重建
        // 用反射或重新 assign 都不行，直接用新 instance
        _hasStrokes = false
        strokeBuilder = null
    }

    /**
     * 重建一個乾淨的 collector（因為 Ink.Builder 不可重用）。
     */
    fun newCollector(): StrokeCollector = StrokeCollector()
}
