package com.snowrealm.yukiboard.ui.floating

data class FloatingPanelConfig(
    val minWidthDp: Int = 200,
    val minHeightDp: Int = 120,
    val maxWidthDp: Int = -1,     // -1 = screenWidth / 2 at runtime
    val maxHeightDp: Int = -1,    // -1 = screenHeight / 2 at runtime
    val defaultWidthDp: Int = 280,
    val defaultHeightDp: Int = 200,
    val defaultX: Int = 20,
    val defaultY: Int = 80,
    val cornerRadius: Float = 16f,
    val backgroundAlpha: Float = 0.93f,
    val snapToEdge: Boolean = true,
    val snapThresholdDp: Int = 40
)
