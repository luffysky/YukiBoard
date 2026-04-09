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
 *
 * 支援 undo：每筆 Stroke 獨立儲存，可回退最後一筆。
 */
class StrokeCollector {

    // 保留所有已完成的 stroke，供 undo 及重建 Ink
    private val completedStrokes = mutableListOf<Ink.Stroke>()
    private var strokeBuilder: Ink.Stroke.Builder? = null

    val hasStrokes: Boolean get() = completedStrokes.isNotEmpty()
    val strokeCount: Int get() = completedStrokes.size

    fun startStroke(event: MotionEvent) {
        strokeBuilder = Ink.Stroke.builder().also {
            it.addPoint(Ink.Point.create(event.x, event.y, event.eventTime))
        }
    }

    fun addPoint(event: MotionEvent) {
        strokeBuilder?.addPoint(Ink.Point.create(event.x, event.y, event.eventTime))
    }

    /** 加入點（含壓力值，用於計算筆畫粗細） */
    fun addPoint(x: Float, y: Float, time: Long) {
        strokeBuilder?.addPoint(Ink.Point.create(x, y, time))
    }

    fun finishStroke(event: MotionEvent) {
        val builder = strokeBuilder ?: return
        builder.addPoint(Ink.Point.create(event.x, event.y, event.eventTime))
        completedStrokes.add(builder.build())
        strokeBuilder = null
    }

    /** 撤銷最後一筆，回傳是否還有筆畫 */
    fun undoLastStroke(): Boolean {
        if (completedStrokes.isNotEmpty()) {
            completedStrokes.removeAt(completedStrokes.lastIndex)
        }
        return completedStrokes.isNotEmpty()
    }

    fun buildInk(): Ink {
        val builder = Ink.builder()
        completedStrokes.forEach { builder.addStroke(it) }
        return builder.build()
    }

    fun reset() {
        completedStrokes.clear()
        strokeBuilder = null
    }

    fun newCollector(): StrokeCollector = StrokeCollector()
}
