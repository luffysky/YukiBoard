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
    val backgroundGradientStartInt: Int? = null,
    val backgroundGradientEndInt: Int? = null,
    val backgroundGradientAngle: Float = 180f,

    // 按鍵
    val keyColorInt: Int,
    val specialKeyColorInt: Int,
    val keyLabelColorInt: Int,
    val keyPressedColorInt: Int,
    val keyBorderColorInt: Int = 0,
    val keyBorderWidthDp: Float = 0f,
    val keyShadowColorInt: Int = 0,
    val keyShadowRadiusDp: Float = 0f,
    val keyCornerRadiusDp: Float = 8f,
    val keyFontSizeSp: Float = 18f,

    // 候選詞列
    val candidateBarColorInt: Int,
    val candidateTextColorInt: Int,
    val candidateSelectedColorInt: Int,

    // 組字文字（composing）
    val composingTextColorInt: Int,

    // 工具列
    val toolbarColorInt: Int = 0,        // 0 = 使用 candidateBarColorInt
    val toolbarIconColorInt: Int = 0,    // 0 = 使用 candidateTextColorInt
    val toolbarActiveColorInt: Int = 0,  // 0 = 使用 candidateSelectedColorInt

    // 面板
    val panelBackgroundColorInt: Int = 0, // 0 = 使用 backgroundColorInt

    // 強調色
    val accentColorInt: Int = 0,         // 0 = 使用 candidateSelectedColorInt

    // 流光
    val flowLightAlpha: Float = 0.55f,
    val flowLightColorInt: Int = Color.parseColor("#FFE5F0FF"), // 冰藍

    // 深色/淺色
    val isDark: Boolean = true
) {
    /** 取得有效的工具列背景色（fallback 到 candidateBarColorInt） */
    fun effectiveToolbarColor(): Int = if (toolbarColorInt != 0) toolbarColorInt else candidateBarColorInt
    fun effectiveToolbarIconColor(): Int = if (toolbarIconColorInt != 0) toolbarIconColorInt else candidateTextColorInt
    fun effectiveToolbarActiveColor(): Int = if (toolbarActiveColorInt != 0) toolbarActiveColorInt else candidateSelectedColorInt
    fun effectivePanelBackground(): Int = if (panelBackgroundColorInt != 0) panelBackgroundColorInt else backgroundColorInt
    fun effectiveAccentColor(): Int = if (accentColorInt != 0) accentColorInt else candidateSelectedColorInt

    companion object {
        /** 1. 雪域深藍（預設） */
        fun default(): KeyboardTheme = KeyboardTheme(
            id                        = "snow_dark",
            displayName               = "雪域深藍",
            backgroundColorInt        = Color.parseColor("#CC1A2035"),
            keyColorInt               = Color.parseColor("#FF253050"),
            specialKeyColorInt        = Color.parseColor("#FF1A2D4A"),
            keyLabelColorInt          = Color.parseColor("#FFE8F4FF"),
            keyPressedColorInt        = Color.parseColor("#FF3D6A9E"),
            candidateBarColorInt      = Color.parseColor("#EE111827"),
            candidateTextColorInt     = Color.parseColor("#FFB0D4FF"),
            candidateSelectedColorInt = Color.parseColor("#FF4A90E2"),
            composingTextColorInt     = Color.parseColor("#FF7FD8FF"),
            flowLightAlpha            = 0.25f,
            flowLightColorInt         = Color.parseColor("#FFB0D4FF"),
            isDark                    = true
        )

        /** 2. 雪域白晝 */
        fun light(): KeyboardTheme = KeyboardTheme(
            id                        = "snow_light",
            displayName               = "雪域白晝",
            backgroundColorInt        = Color.parseColor("#FFEEF3FA"),
            keyColorInt               = Color.parseColor("#FFFFFFFF"),
            specialKeyColorInt        = Color.parseColor("#FFD0DDF0"),
            keyLabelColorInt          = Color.parseColor("#FF1A2035"),
            keyPressedColorInt        = Color.parseColor("#FFB0C8E8"),
            candidateBarColorInt      = Color.parseColor("#FFE2EAF8"),
            candidateTextColorInt     = Color.parseColor("#FF1A4080"),
            candidateSelectedColorInt = Color.parseColor("#FF4A90E2"),
            composingTextColorInt     = Color.parseColor("#FF0060C0"),
            flowLightAlpha            = 0.15f,
            flowLightColorInt         = Color.parseColor("#FFA0C0E8"),
            keyShadowColorInt         = Color.parseColor("#22000000"),
            keyShadowRadiusDp         = 2f,
            isDark                    = false
        )

        /** 3. 深空紫 */
        fun deepSpace(): KeyboardTheme = KeyboardTheme(
            id                        = "deep_space",
            displayName               = "深空紫",
            backgroundColorInt        = Color.parseColor("#FF0D0B1E"),
            backgroundGradientStartInt = Color.parseColor("#FF0D0B1E"),
            backgroundGradientEndInt  = Color.parseColor("#FF1A1040"),
            keyColorInt               = Color.parseColor("#FF1A1538"),
            specialKeyColorInt        = Color.parseColor("#FF2A1F50"),
            keyLabelColorInt          = Color.parseColor("#FFD8D0F0"),
            keyPressedColorInt        = Color.parseColor("#FF4A3080"),
            candidateBarColorInt      = Color.parseColor("#EE0A0818"),
            candidateTextColorInt     = Color.parseColor("#FFC0B0E8"),
            candidateSelectedColorInt = Color.parseColor("#FF7C3AED"),
            composingTextColorInt     = Color.parseColor("#FFA78BFA"),
            accentColorInt            = Color.parseColor("#FF7C3AED"),
            flowLightAlpha            = 0.2f,
            flowLightColorInt         = Color.parseColor("#FF9070E0"),
            isDark                    = true
        )

        /** 4. 櫻花 */
        fun sakura(): KeyboardTheme = KeyboardTheme(
            id                        = "sakura",
            displayName               = "櫻花夢境",
            backgroundColorInt        = Color.parseColor("#FFFFF0F6"),
            keyColorInt               = Color.parseColor("#FFFFFFFF"),
            specialKeyColorInt        = Color.parseColor("#FFFFD6E7"),
            keyLabelColorInt          = Color.parseColor("#FF4A2040"),
            keyPressedColorInt        = Color.parseColor("#FFFFB0D0"),
            candidateBarColorInt      = Color.parseColor("#FFFFF5F9"),
            candidateTextColorInt     = Color.parseColor("#FF9B4070"),
            candidateSelectedColorInt = Color.parseColor("#FFEC4899"),
            composingTextColorInt     = Color.parseColor("#FFD946A8"),
            accentColorInt            = Color.parseColor("#FFEC4899"),
            keyShadowColorInt         = Color.parseColor("#18EC4899"),
            keyShadowRadiusDp         = 3f,
            flowLightAlpha            = 0.15f,
            flowLightColorInt         = Color.parseColor("#FFFFB0D0"),
            isDark                    = false
        )

        /** 5. 極光 */
        fun aurora(): KeyboardTheme = KeyboardTheme(
            id                        = "aurora",
            displayName               = "極光雪原",
            backgroundColorInt        = Color.parseColor("#FF0F2027"),
            backgroundGradientStartInt = Color.parseColor("#FF0F2027"),
            backgroundGradientEndInt  = Color.parseColor("#FF2C5364"),
            keyColorInt               = Color.parseColor("#FF1B3A4B"),
            specialKeyColorInt        = Color.parseColor("#FF153040"),
            keyLabelColorInt          = Color.parseColor("#FFD0F0E8"),
            keyPressedColorInt        = Color.parseColor("#FF2A6858"),
            candidateBarColorInt      = Color.parseColor("#EE0A1820"),
            candidateTextColorInt     = Color.parseColor("#FF80D8C0"),
            candidateSelectedColorInt = Color.parseColor("#FF00D4AA"),
            composingTextColorInt     = Color.parseColor("#FF5EEAD4"),
            accentColorInt            = Color.parseColor("#FF00D4AA"),
            flowLightAlpha            = 0.2f,
            flowLightColorInt         = Color.parseColor("#FF40E8C0"),
            isDark                    = true
        )

        /** 6. 賽博龐克 */
        fun cyberpunk(): KeyboardTheme = KeyboardTheme(
            id                        = "cyberpunk",
            displayName               = "霓虹都市",
            backgroundColorInt        = Color.parseColor("#FF0A0A0A"),
            keyColorInt               = Color.parseColor("#FF1A1A2E"),
            specialKeyColorInt        = Color.parseColor("#FF12122A"),
            keyLabelColorInt          = Color.parseColor("#FFE0E0FF"),
            keyPressedColorInt        = Color.parseColor("#FF3A1050"),
            keyBorderColorInt         = Color.parseColor("#6600F5FF"),
            keyBorderWidthDp          = 0.5f,
            candidateBarColorInt      = Color.parseColor("#EE08081A"),
            candidateTextColorInt     = Color.parseColor("#FF00F5FF"),
            candidateSelectedColorInt = Color.parseColor("#FFFF006E"),
            composingTextColorInt     = Color.parseColor("#FF00F5FF"),
            accentColorInt            = Color.parseColor("#FFFF006E"),
            flowLightAlpha            = 0.3f,
            flowLightColorInt         = Color.parseColor("#FFFF40A0"),
            isDark                    = true
        )

        /** 7. 抹茶 */
        fun matcha(): KeyboardTheme = KeyboardTheme(
            id                        = "matcha",
            displayName               = "抹茶日和",
            backgroundColorInt        = Color.parseColor("#FFF5F0E8"),
            keyColorInt               = Color.parseColor("#FFFFFFFF"),
            specialKeyColorInt        = Color.parseColor("#FFC8E6C9"),
            keyLabelColorInt          = Color.parseColor("#FF2E3B2E"),
            keyPressedColorInt        = Color.parseColor("#FFA5D6A7"),
            candidateBarColorInt      = Color.parseColor("#FFEEE8D8"),
            candidateTextColorInt     = Color.parseColor("#FF3E6B3E"),
            candidateSelectedColorInt = Color.parseColor("#FF4CAF50"),
            composingTextColorInt     = Color.parseColor("#FF2E7D32"),
            accentColorInt            = Color.parseColor("#FF4CAF50"),
            keyShadowColorInt         = Color.parseColor("#184CAF50"),
            keyShadowRadiusDp         = 2f,
            flowLightAlpha            = 0.12f,
            flowLightColorInt         = Color.parseColor("#FFA0D8A0"),
            isDark                    = false
        )

        /** 8. 午夜金 */
        fun midnightGold(): KeyboardTheme = KeyboardTheme(
            id                        = "midnight_gold",
            displayName               = "午夜鎏金",
            backgroundColorInt        = Color.parseColor("#FF0D1B2A"),
            backgroundGradientStartInt = Color.parseColor("#FF0D1B2A"),
            backgroundGradientEndInt  = Color.parseColor("#FF1B2838"),
            keyColorInt               = Color.parseColor("#FF1B2838"),
            specialKeyColorInt        = Color.parseColor("#FF152030"),
            keyLabelColorInt          = Color.parseColor("#FFF0E8D0"),
            keyPressedColorInt        = Color.parseColor("#FF3A3018"),
            keyBorderColorInt         = Color.parseColor("#33FFD700"),
            keyBorderWidthDp          = 0.5f,
            candidateBarColorInt      = Color.parseColor("#EE0A1420"),
            candidateTextColorInt     = Color.parseColor("#FFDAA520"),
            candidateSelectedColorInt = Color.parseColor("#FFFFD700"),
            composingTextColorInt     = Color.parseColor("#FFFFD700"),
            accentColorInt            = Color.parseColor("#FFFFD700"),
            flowLightAlpha            = 0.2f,
            flowLightColorInt         = Color.parseColor("#FFFFD070"),
            isDark                    = true
        )

        fun all(): List<KeyboardTheme> = listOf(
            default(), light(), deepSpace(), sakura(),
            aurora(), cyberpunk(), matcha(), midnightGold()
        )

        fun findById(id: String): KeyboardTheme? = all().find { it.id == id }
    }
}
