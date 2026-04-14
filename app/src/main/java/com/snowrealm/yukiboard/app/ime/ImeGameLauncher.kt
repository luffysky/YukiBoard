package com.snowrealm.yukiboard.app.ime

import android.content.Context
import com.snowrealm.yukiboard.game.*
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState

/**
 * 遊戲建立與啟動 — 從 YukiBoardIMEService.startGame() + setGameEventCallback() 抽出。
 */
class ImeGameLauncher {

    data class GameLaunchResult(
        val game: GameLogic,
        val gameKeyboardView: GameKeyboardView,
        val loop: GameLoopRunner,
    )

    fun launch(
        context: Context,
        gameMode: GameMode,
        onBackToNormal: () -> Unit,
        onGameEvent: (GameEvent, GameLogic) -> Unit,
    ): GameLaunchResult {
        val game: GameLogic = createGame(context, gameMode)

        val gkv = GameKeyboardView(context)
        gkv.setTheme(KeyboardTheme.default())

        val rows = KeyboardState.defaultQwertyRows()
        val gridRows = rows.size
        val gridCols = rows.maxOfOrNull { it.size } ?: 10

        game.init(gridRows, gridCols)
        gkv.attachGame(game)
        gkv.onBackToNormal = onBackToNormal

        setGameEventCallback(game) { event -> onGameEvent(event, game) }

        val loop = GameLoopRunner()
        loop.attach(game) { gkv.invalidate() }
        game.start()
        loop.start()

        return GameLaunchResult(game, gkv, loop)
    }

    private fun createGame(context: Context, gameMode: GameMode): GameLogic = when (gameMode) {
        GameMode.SNAKE -> SnakeGame(context)
        GameMode.WHACK_A_MOLE -> WhackAMoleGame(context)
        GameMode.RHYTHM -> RhythmGame(context)
        GameMode.TYPING_CHALLENGE -> TypingChallengeGame(context)
        GameMode.WORD_GUARDIAN -> WordGuardianGame(context)
        GameMode.KEYBOARD_TRAVELER -> KeyboardTravelerGame(context)
        GameMode.MOOD_MINER -> MoodMinerGame(context)
        GameMode.BULLET_HELL -> BulletHellMiniGame(context)
        GameMode.ICE_CRYSTAL -> IceCrystalGame(context)
        GameMode.WARSHIP_CRUISE -> WarshipCruiseGame(context)
        GameMode.SPELLING_DUEL -> SpellingDuelGame(context)
        GameMode.KEYBOARD_MINESWEEPER -> KeyboardMinesweeperGame(context)
        GameMode.MEMORY_FLIP -> MemoryFlipGame(context)
        GameMode.YUKI_QUIZ -> YukiQuizGame(context)
        GameMode.COUPLE_QUIZ -> CoupleQuizGame(context)
        GameMode.TYPING_RACE -> TypingRaceGame(context)
        GameMode.RANDOM_CHALLENGE -> RandomChallengeGame(context)
        GameMode.VOICE_GAME -> VoiceGame(context)
        GameMode.WORDLE -> WordleGame(context)
        GameMode.WORD_CHAIN -> WordChainGame(context)
        GameMode.CROSSWORD -> CrosswordGame(context)
        GameMode.MUSIC_TYPING -> MusicTypingGame(context)
        GameMode.YUKI_HOUSE -> YukiHouseGame(context)
        GameMode.TYPING_FARM -> TypingFarmGame(context)
        GameMode.SPEED_TYPING_BATTLE -> SpeedTypingBattleGame(context)
    }

    private fun setGameEventCallback(game: GameLogic, callback: (GameEvent) -> Unit) {
        when (game) {
            is SnakeGame -> game.onGameEvent = callback
            is WhackAMoleGame -> game.onGameEvent = callback
            is RhythmGame -> game.onGameEvent = callback
            is TypingChallengeGame -> game.onGameEvent = callback
            is WordGuardianGame -> game.onGameEvent = callback
            is KeyboardTravelerGame -> game.onGameEvent = callback
            is MoodMinerGame -> game.onGameEvent = callback
            is BulletHellMiniGame -> game.onGameEvent = callback
            is IceCrystalGame -> game.onGameEvent = callback
            is WarshipCruiseGame -> game.onGameEvent = callback
            is SpellingDuelGame -> game.onGameEvent = callback
            is KeyboardMinesweeperGame -> game.onGameEvent = callback
            is MemoryFlipGame -> game.onGameEvent = callback
            is YukiQuizGame -> game.onGameEvent = callback
            is CoupleQuizGame -> game.onGameEvent = callback
            is TypingRaceGame -> game.onGameEvent = callback
            is RandomChallengeGame -> game.onGameEvent = callback
            is VoiceGame -> game.onGameEvent = callback
            is WordleGame -> game.onGameEvent = callback
            is WordChainGame -> game.onGameEvent = callback
            is CrosswordGame -> game.onGameEvent = callback
            is MusicTypingGame -> game.onGameEvent = callback
            is YukiHouseGame -> game.onGameEvent = callback
            is TypingFarmGame -> game.onGameEvent = callback
            is SpeedTypingBattleGame -> game.onGameEvent = callback
        }
    }
}
