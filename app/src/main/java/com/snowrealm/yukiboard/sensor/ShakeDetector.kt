package com.snowrealm.yukiboard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * 搖手機偵測器 — 偵測快速搖晃觸發復原動作。
 *
 * 門檻：加速度 >= 2.5G（約 24.5 m/s²），時間窗 500ms 內兩次以上。
 * 偵測後觸發回呼，並進入冷卻期 1 秒避免連續觸發。
 */
class ShakeDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onShakeListener: (() -> Unit)? = null
    private var enabled = false

    // 偵測參數
    private val shakeThresholdGravity = 2.5f
    private val shakeTimeWindowMs = 500L
    private val shakeCooldownMs = 1000L

    private var shakeTimestamp = 0L
    private var shakeCount = 0
    private var lastShakeTime = 0L

    fun setOnShakeListener(listener: () -> Unit) {
        onShakeListener = listener
    }

    fun start() {
        if (accelerometer == null || enabled) return
        enabled = true
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!enabled) return
        enabled = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        if (gForce > shakeThresholdGravity) {
            val now = System.currentTimeMillis()

            // 冷卻期
            if (now - lastShakeTime < shakeCooldownMs) return

            // 時間窗重置
            if (now - shakeTimestamp > shakeTimeWindowMs) {
                shakeTimestamp = now
                shakeCount = 0
            }

            shakeCount++

            if (shakeCount >= 2) {
                lastShakeTime = now
                shakeCount = 0
                onShakeListener?.invoke()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}
