package com.snowrealm.yukiboard.ui.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * YukiBoard 全域偏好設定。
 * 提供感測器功能和 UI 選項的開關。
 */
class YukiPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_prefs", Context.MODE_PRIVATE)

    /** 搖手機復原功能開關 */
    var shakeToUndoEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHAKE_UNDO, true)
        set(value) = prefs.edit().putBoolean(KEY_SHAKE_UNDO, value).apply()

    /** 陀螺儀游標功能開關 */
    var gyroscopeCursorEnabled: Boolean
        get() = prefs.getBoolean(KEY_GYRO_CURSOR, true)
        set(value) = prefs.edit().putBoolean(KEY_GYRO_CURSOR, value).apply()

    /** 陀螺儀靈敏度 0.5 ~ 3.0 */
    var gyroscopeSensitivity: Float
        get() = prefs.getFloat(KEY_GYRO_SENSITIVITY, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_GYRO_SENSITIVITY, value.coerceIn(0.5f, 3.0f)).apply()

    /** App 自動偵測模式切換開關 */
    var appAutoDetectEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_APP_DETECT, value).apply()

    /** 震動回饋開關 */
    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    /** Snippet 功能開關 */
    var snippetEnabled: Boolean
        get() = prefs.getBoolean(KEY_SNIPPET, true)
        set(value) = prefs.edit().putBoolean(KEY_SNIPPET, value).apply()

    /** 鍵盤高度比例 0.25 ~ 0.55（佔螢幕高度比例） */
    var keyboardHeightRatio: Float
        get() = prefs.getFloat(KEY_KB_HEIGHT, 0.35f)
        set(value) = prefs.edit().putFloat(KEY_KB_HEIGHT, value.coerceIn(MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO)).apply()

    /** 鍵盤寬度比例 0.5 ~ 1.0（佔螢幕寬度比例） */
    var keyboardWidthRatio: Float
        get() = prefs.getFloat(KEY_KB_WIDTH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_KB_WIDTH, value.coerceIn(MIN_WIDTH_RATIO, MAX_WIDTH_RATIO)).apply()

    /** 懸浮模式開關 */
    var floatingModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING, value).apply()

    /** 懸浮模式 X 位置 */
    var floatingX: Int
        get() = prefs.getInt(KEY_FLOATING_X, -1)
        set(value) = prefs.edit().putInt(KEY_FLOATING_X, value).apply()

    /** 懸浮模式 Y 位置 */
    var floatingY: Int
        get() = prefs.getInt(KEY_FLOATING_Y, -1)
        set(value) = prefs.edit().putInt(KEY_FLOATING_Y, value).apply()

    companion object {
        private const val KEY_SHAKE_UNDO = "shake_to_undo"
        private const val KEY_GYRO_CURSOR = "gyro_cursor"
        private const val KEY_GYRO_SENSITIVITY = "gyro_sensitivity"
        private const val KEY_APP_DETECT = "app_auto_detect"
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_SNIPPET = "snippet_enabled"
        private const val KEY_KB_HEIGHT = "keyboard_height_ratio"
        private const val KEY_KB_WIDTH = "keyboard_width_ratio"
        private const val KEY_FLOATING = "floating_mode"
        private const val KEY_FLOATING_X = "floating_x"
        private const val KEY_FLOATING_Y = "floating_y"

        const val MIN_HEIGHT_RATIO = 0.25f
        const val MAX_HEIGHT_RATIO = 0.55f
        const val MIN_WIDTH_RATIO = 0.5f
        const val MAX_WIDTH_RATIO = 1.0f
    }
}
