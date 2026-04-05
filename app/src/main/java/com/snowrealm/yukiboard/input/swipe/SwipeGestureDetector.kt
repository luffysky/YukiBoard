package com.snowrealm.yukiboard.input.swipe

import android.graphics.PointF
import com.snowrealm.yukiboard.ui.keyboard.KeyModel

/**
 * 滑動手勢偵測器。
 *
 * 根據觸控距離與經過的按鍵數量判定「點擊」vs「滑動輸入」。
 * - 距離 ≥ [distanceThresholdPx] 且經過 ≥ 2 個不同按鍵 → 滑動
 * - 否則 → 點擊
 */
class SwipeGestureDetector(
    private val distanceThresholdPx: Float // 由外部傳入（12dp * density）
) {

    private var downX = 0f
    private var downY = 0f
    private var isActive = false
    private val keySequence = mutableListOf<KeyModel>()
    private val points = mutableListOf<PointF>()

    val swipeKeys: List<KeyModel> get() = keySequence
    val swipePoints: List<PointF> get() = points
    val isSwipeDetected: Boolean get() = isActive && keySequence.size >= 2

    fun onDown(x: Float, y: Float, key: KeyModel?) {
        downX = x
        downY = y
        isActive = false
        keySequence.clear()
        points.clear()
        points.add(PointF(x, y))
        if (key != null && !key.isSpecial) {
            keySequence.add(key)
        }
    }

    fun onMove(x: Float, y: Float, key: KeyModel?) {
        points.add(PointF(x, y))
        if (key != null && !key.isSpecial) {
            val last = keySequence.lastOrNull()
            if (last == null || last.label != key.label) {
                keySequence.add(key)
            }
        }
        if (!isActive) {
            val dist = Math.hypot((x - downX).toDouble(), (y - downY).toDouble())
            if (dist > distanceThresholdPx && keySequence.size >= 2) {
                isActive = true
            }
        }
    }

    /**
     * @return 滑過的按鍵序列（若判定為滑動），空列表（若判定為點擊）
     */
    fun onUp(): List<KeyModel> {
        val result = if (isSwipeDetected) keySequence.toList() else emptyList()
        reset()
        return result
    }

    fun reset() {
        isActive = false
        keySequence.clear()
        points.clear()
    }

    /** 計算滑動速度（px/ms），可用於判定快滑 vs 慢滑 */
    fun averageSpeed(durationMs: Long): Float {
        if (durationMs <= 0 || points.size < 2) return 0f
        var totalDist = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            totalDist += Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }
        return totalDist / durationMs
    }
}
