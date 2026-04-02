package com.snowrealm.yukiboard.game

enum class GameStatus {
    IDLE,       // 等待開始
    PLAYING,    // 遊戲進行中
    PAUSED,     // 暫停
    GAME_OVER   // 結束
}

enum class GameMode(val displayName: String, val icon: String) {
    SNAKE("貪吃蛇", "🐍"),
    WHACK_A_MOLE("打地鼠", "🔨"),
    RHYTHM("節奏", "🥁"),
    TYPING_CHALLENGE("打字", "⌨️")
}
