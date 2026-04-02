package com.snowrealm.yukiboard.theme

import android.graphics.Color

/**
 * 鍵盤主題資料類別。
 *
 * 所有顏色以 ARGB Int 儲存，方便直接傳給 [android.graphics.Paint.color]。
 * 透過 [ThemeManager] 取得預設或自訂主題。
 */
data class KeyboardTheme(
    val id: String,
    val displayName: String,

    // 背景
    val backgroundColorInt: Int,

    // 按鍵
    val keyColorInt: Int,
    val specialKeyColorInt: Int,
    val keyLabelColorInt: Int,
    val keyPressedColorInt: Int,

    // 候選詞列
    val candidateBarColorInt: Int,
    val candidateTextColorInt: Int,
    val candidateSelectedColorInt: Int,

    // 組字文字（composing）
    val composingTextColorInt: Int,

    // 流光透明度（0.0 ~ 1.0）
    val flowLightAlpha: Float = 0.55f
) {
    companion object {
        /** 預設雪域深藍主題 */
        fun default(): KeyboardTheme = KeyboardTheme(
            id                     = "snow_dark",
            displayName            = "雪域深藍",
            backgroundColorInt     = Color.parseColor("#CC1A2035"),
            keyColorInt            = Color.parseColor("#FF253050"),
            specialKeyColorInt     = Color.parseColor("#FF1A2D4A"),
            keyLabelColorInt       = Color.parseColor("#FFE8F4FF"),
            keyPressedColorInt     = Color.parseColor("#FF3D6A9E"),
            candidateBarColorInt   = Color.parseColor("#EE111827"),
            candidateTextColorInt  = Color.parseColor("#FFB0D4FF"),
            candidateSelectedColorInt = Color.parseColor("#FF4A90E2"),
            composingTextColorInt  = Color.parseColor("#FF7FD8FF"),
            flowLightAlpha         = 0.55f
        )

        /** 淺色主題（白色鍵盤） */
        fun light(): KeyboardTheme = KeyboardTheme(
            id                     = "snow_light",
            displayName            = "雪域白晝",
            backgroundColorInt     = Color.parseColor("#FFEEF3FA"),
            keyColorInt            = Color.parseColor("#FFFFFFFF"),
            specialKeyColorInt     = Color.parseColor("#FFD0DDF0"),
            keyLabelColorInt       = Color.parseColor("#FF1A2035"),
            keyPressedColorInt     = Color.parseColor("#FFB0C8E8"),
            candidateBarColorInt   = Color.parseColor("#FFE2EAF8"),
            candidateTextColorInt  = Color.parseColor("#FF1A4080"),
            candidateSelectedColorInt = Color.parseColor("#FF4A90E2"),
            composingTextColorInt  = Color.parseColor("#FF0060C0"),
            flowLightAlpha         = 0.3f
        )

        fun all(): List<KeyboardTheme> = listOf(default(), light())
    }
}
