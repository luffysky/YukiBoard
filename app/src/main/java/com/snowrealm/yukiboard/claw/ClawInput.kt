package com.snowrealm.yukiboard.claw

import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode

data class ClawInput(
    val text: String,
    val context: String = "",
    val appPackage: String = "",
    val language: String = "zh-TW",
    val mode: KeyboardMode = KeyboardMode.NORMAL
)
