package com.snowrealm.yukiboard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * 陀螺儀游標控制器 — 長按空白鍵時傾斜手機控制游標左右移動。
 *
 * 使用 TYPE_GYROSCOPE 偵測 Y 軸旋轉速度：
 * - 左傾（正值）→ 游標左移
 * - 右傾（負值）→ 游標右移
 *
 * 靈敏度可從 0.5（低）到 3.0（高）調整。
 * 需長按空白鍵啟動，避免誤觸。
 */
class GyroscopeCursorController(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val handler = Handler(Looper.getMainLooper())

    private var onCursorMoveListener: ((direction: CursorDirection) -> Unit)? = null
    @Volatile private var active = false
    @Volatile private var enabled = false

    /** 靈敏度（越高越敏感）1.0 = 預設 */
    var sensitivity: Float = 1.5f

    // 移動閾值（弧度/秒）
    private val moveThreshold = 0.8f
    // 移動節流（ms）
    private val moveIntervalMs = 80L
    private var lastMoveTime = 0L

    fun setOnCursorMoveListener(listener: (CursorDirection) -> Unit) {
        onCursorMoveListener = listener
    }

    /** 註冊感測器監聽（IME 啟動時呼叫） */
    fun register() {
        if (gyroscope == null || enabled) return
        enabled = true
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    /** 取消感測器監聽（IME 停止時呼叫） */
    fun unregister() {
        if (!enabled) return
        enabled = false
        sensorManager.unregisterListener(this)
    }

    /** 長按空白鍵啟動游標模式 */
    fun activate() {
        active = true
    }

    /** 放開空白鍵停用游標模式 */
    fun deactivate() {
        active = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return

        val now = System.currentTimeMillis()
        if (now - lastMoveTime < moveIntervalMs) return

        // Y 軸（gyroscope[1]）= 左右傾斜
        val rotationY = event.values[1]
        val adjustedThreshold = moveThreshold / sensitivity

        val direction = when {
            rotationY > adjustedThreshold  -> CursorDirection.LEFT
            rotationY < -adjustedThreshold -> CursorDirection.RIGHT
            else -> null
        }

        if (direction != null) {
            lastMoveTime = now
            handler.post { onCursorMoveListener?.invoke(direction) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}

enum class CursorDirection {
    LEFT, RIGHT
}
