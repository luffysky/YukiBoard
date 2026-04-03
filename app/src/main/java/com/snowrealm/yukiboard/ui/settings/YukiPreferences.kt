package com.snowrealm.yukiboard.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlin.reflect.KProperty

/**
 * YukiBoard 全��偏好設定。
 * 所有功能開關集中管理，使用 BoolPref / FloatPref / IntPref 委派。
 */
class YukiPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_prefs", Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════════════════════
    // AI 功能
    // ════════���═══════════════��══════════════════════════════════
    var aiEnabled: Boolean by BoolPref("ai_enabled", true)
    var aiPrediction: Boolean by BoolPref("ai_prediction", true)
    var aiWriter: Boolean by BoolPref("ai_writer", true)
    var aiTranslate: Boolean by BoolPref("ai_translate", true)
    var aiCupid: Boolean by BoolPref("ai_cupid", true)
    var aiWhisper: Boolean by BoolPref("ai_whisper", true)
    var aiGrammarCheck: Boolean by BoolPref("ai_grammar_check", true)
    var aiToneAdjust: Boolean by BoolPref("ai_tone_adjust", true)
    var aiSummarize: Boolean by BoolPref("ai_summarize", true)
    var aiMemeText: Boolean by BoolPref("ai_meme_text", true)
    var aiEmojiTranslate: Boolean by BoolPref("ai_emoji_translate", true)
    var aiQuickReply: Boolean by BoolPref("ai_quick_reply", true)
    var aiCodeComplete: Boolean by BoolPref("ai_code_complete", true)
    var aiRegexGen: Boolean by BoolPref("ai_regex_gen", true)
    var aiCommitMessage: Boolean by BoolPref("ai_commit_message", true)
    var aiYukiChat: Boolean by BoolPref("ai_yuki_chat", true)
    var aiMoodDiary: Boolean by BoolPref("ai_mood_diary", true)
    var aiWeather: Boolean by BoolPref("ai_weather", true)

    // ══════════════════════════════���════════════════════════���═══
    // 雪凜行為
    // ═══════════════════════════════════════════════════════���═══
    var yukiSarcasticComments: Boolean by BoolPref("yuki_sarcastic", true)
    var yukiEmotionalCooldown: Boolean by BoolPref("yuki_cooldown", true)
    var yukiMoodDiaryPassive: Boolean by BoolPref("yuki_mood_passive", true)
    var yukiWeatherAlert: Boolean by BoolPref("yuki_weather_alert", true)
    var yukiLateNightComment: Boolean by BoolPref("yuki_late_night", true)

    // ══════════════════════════���═════════════════════════��══════
    // 感測器
    // ══════���════════════════════════════════════════��═══════════
    var shakeToUndoEnabled: Boolean by BoolPref("shake_to_undo", true)
    var gyroscopeCursorEnabled: Boolean by BoolPref("gyro_cursor", true)
    var gyroscopeSensitivity: Float by FloatPref("gyro_sensitivity", 1.5f)
    var appAutoDetectEnabled: Boolean by BoolPref("app_auto_detect", true)

    // ═══════════════════════════════���══════════════════════════���
    // 動畫特效
    // ════════════════════��═══════════════════════��══════════════
    var flowLightEnabled: Boolean by BoolPref("flow_light", true)
    var characterAnimEnabled: Boolean by BoolPref("character_anim", true)
    var keyParticleEffect: Boolean by BoolPref("key_particle", true)
    var keyPressVibration: Boolean by BoolPref("key_vibration", true)
    var keySoundEnabled: Boolean by BoolPref("key_sound", false)

    // ═════���═══════════════════════════════════════════���═════════
    // 遊戲系統
    // ═���════════════════��═════════════════════════════���══════════
    var gamesEnabled: Boolean by BoolPref("games_enabled", true)
    var gameBackgroundMusic: Boolean by BoolPref("game_bgm", false)
    var gameSoundEffects: Boolean by BoolPref("game_sfx", true)

    // ════════════════════════════���════════════════════════��═════
    // 懸浮面板
    // ════���════════════��═════════════════════════════════════════
    var floatingPanelEnabled: Boolean by BoolPref("floating_panel", true)
    var weatherPanelEnabled: Boolean by BoolPref("weather_panel", true)
    var stickerPanelEnabled: Boolean by BoolPref("sticker_panel", true)

    // ══════���══════════════════════════════════════════════════��═
    // 輸入行為
    // ═════════════════════════���═════════════════════════════════
    var swipeInput: Boolean by BoolPref("swipe_input", true)
    var autoCorrect: Boolean by BoolPref("auto_correct", true)
    var snippetEnabled: Boolean by BoolPref("snippet_enabled", true)
    var secretMessageMode: Boolean by BoolPref("secret_message", true)
    var toneDetection: Boolean by BoolPref("tone_detection", true)
    var smartCorrection: Boolean by BoolPref("smart_correction", true)
    var adaptiveDict: Boolean by BoolPref("adaptive_dict", true)
    var phrasePredict: Boolean by BoolPref("phrase_predict", true)
    var habitTracking: Boolean by BoolPref("habit_tracking", true)

    // ════════════════════════════���══════════════════════════════
    // 連線功能
    // ══════��══════════���══════════════════════════════��══════════
    var remoteKeyboardEnabled: Boolean by BoolPref("remote_keyboard", true)
    var multiplayerEnabled: Boolean by BoolPref("multiplayer", true)

    // ══��═════════════════════════��═════════════════════���════════
    // 鍵盤尺寸 / 懸浮模式（保留原有欄位）
    // ════════════════════════════════════��══════════════════════
    var hapticFeedbackEnabled: Boolean by BoolPref("haptic_feedback", true)

    var keyboardHeightRatio: Float
        get() = prefs.getFloat(KEY_KB_HEIGHT, 0.35f)
        set(value) = prefs.edit().putFloat(KEY_KB_HEIGHT, value.coerceIn(MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO)).apply()

    var keyboardWidthRatio: Float
        get() = prefs.getFloat(KEY_KB_WIDTH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_KB_WIDTH, value.coerceIn(MIN_WIDTH_RATIO, MAX_WIDTH_RATIO)).apply()

    var floatingModeEnabled: Boolean by BoolPref("floating_mode", false)

    var floatingX: Int by IntPref("floating_x", -1)
    var floatingY: Int by IntPref("floating_y", -1)

    // ════��═════════════════════════════��════════════════════════
    // 委派實作
    // ════════���════════════════���═════════════════════════════════

    private inner class BoolPref(private val key: String, private val default: Boolean) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            prefs.getBoolean(key, default)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            prefs.edit().putBoolean(key, value).apply()
    }

    private inner class FloatPref(private val key: String, private val default: Float) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Float =
            prefs.getFloat(key, default)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) =
            prefs.edit().putFloat(key, value).apply()
    }

    private inner class IntPref(private val key: String, private val default: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            prefs.getInt(key, default)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
            prefs.edit().putInt(key, value).apply()
    }

    companion object {
        private const val KEY_KB_HEIGHT = "keyboard_height_ratio"
        private const val KEY_KB_WIDTH = "keyboard_width_ratio"

        const val MIN_HEIGHT_RATIO = 0.25f
        const val MAX_HEIGHT_RATIO = 0.55f
        const val MIN_WIDTH_RATIO = 0.5f
        const val MAX_WIDTH_RATIO = 1.0f
    }
}
