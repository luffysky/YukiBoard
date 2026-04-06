package com.snowrealm.yukiboard.ui.panel

/**
 * 鍵盤上方可切換的面板類型。
 */
enum class PanelType(val icon: String, val displayName: String) {
    VOICE("🎤", "語音"),
    TRANSLATE("🌐", "翻譯"),
    GIF("GIF", "GIF"),
    EMOJI("😀", "表情"),
    KAOMOJI("顔", "顔文字"),
    STICKER("🖼", "貼圖"),
    CLIPBOARD("📋", "剪貼簿"),
    REMOTE("🔗", "遠端"),
    CLAW("🐾", "Claw AI"),
    CALCULATOR("🧮", "計算機"),
    THEME("🎨", "主題"),
    SETTINGS("⚙", "設定")
}
