package com.snowrealm.yukiboard.game

enum class GameStatus {
    IDLE,       // 等待開始
    PLAYING,    // 遊戲進行中
    PAUSED,     // 暫停
    GAME_OVER   // 結束
}

enum class GameMode(val displayName: String, val icon: String, val category: GameCategory = GameCategory.CLASSIC) {
    // 經典（原有）
    SNAKE("貪吃蛇", "🐍", GameCategory.CLASSIC),
    WHACK_A_MOLE("打地鼠", "🔨", GameCategory.CLASSIC),
    RHYTHM("節奏", "🥁", GameCategory.CLASSIC),
    TYPING_CHALLENGE("打字", "⌨️", GameCategory.CLASSIC),
    // 動作
    WORD_GUARDIAN("詞彙守護者", "🏰", GameCategory.ACTION),
    KEYBOARD_TRAVELER("鍵盤旅行者", "🏃", GameCategory.ACTION),
    BULLET_HELL("極簡彈幕", "💥", GameCategory.ACTION),
    ICE_CRYSTAL("冰晶收集", "❄️", GameCategory.ACTION),
    WARSHIP_CRUISE("戰艦巡航", "🚢", GameCategory.ACTION),
    // 益智
    SPELLING_DUEL("拼字對決", "📖", GameCategory.PUZZLE),
    KEYBOARD_MINESWEEPER("按鍵掃雷", "💣", GameCategory.PUZZLE),
    MEMORY_FLIP("記憶翻牌", "🃏", GameCategory.PUZZLE),
    YUKI_QUIZ("雪凜問答", "❓", GameCategory.PUZZLE),
    COUPLE_QUIZ("情侶測驗", "💕", GameCategory.PUZZLE),
    // 挑戰
    TYPING_RACE("打字競速", "🏎️", GameCategory.CHALLENGE),
    RANDOM_CHALLENGE("隨機挑戰", "🎲", GameCategory.CHALLENGE),
    MOOD_MINER("情緒採礦", "⛏️", GameCategory.CHALLENGE),
    VOICE_GAME("語音遊戲", "🎙️", GameCategory.CHALLENGE),
    // 文字
    WORDLE("猜詞", "🔮", GameCategory.PUZZLE),
    WORD_CHAIN("接龍", "🎭", GameCategory.PUZZLE);
}

enum class GameCategory(val displayName: String) {
    CLASSIC("經典"),
    ACTION("動作"),
    PUZZLE("益智"),
    CHALLENGE("挑戰")
}
