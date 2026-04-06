package com.snowrealm.yukiboard.ui.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
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
    var aiFraudDetect: Boolean by BoolPref("ai_fraud_detect", true)
    var aiFraudPassiveScan: Boolean by BoolPref("ai_fraud_passive", true)  // 被動掃描（打字時自動偵測）
    var aiFraudUrlScan: Boolean by BoolPref("ai_fraud_url_scan", true)    // 連結風險掃描

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
    var vrmCharacterEnabled: Boolean by BoolPref("vrm_character", false)
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
    var autoInputMode: Boolean by BoolPref("auto_input_mode", false)
    var swipeInput: Boolean by BoolPref("swipe_input", true)
    var autoCorrect: Boolean by BoolPref("auto_correct", true)
    var snippetEnabled: Boolean by BoolPref("snippet_enabled", true)
    var secretMessageMode: Boolean by BoolPref("secret_message", false)
    var toneDetection: Boolean by BoolPref("tone_detection", true)
    var rawBopomofoMode: Boolean by BoolPref("raw_bopomofo", false)
    var swipeTrailEnabled: Boolean by BoolPref("swipe_trail", true)

    /** 啟用的輸入法（逗號分隔的 InputMode name，空=全開） */
    var enabledInputModes: String
        get() = prefs.getString("enabled_input_modes", "") ?: ""
        set(value) = prefs.edit().putString("enabled_input_modes", value).apply()

    /** 語音引擎：0=Android 內建, 1=OpenAI Whisper, 2=Faster-Whisper（自架） */
    var speechEngineType: Int by IntPref("speech_engine_type", 0)
    /** Faster-Whisper Server URL */
    var fasterWhisperServerUrl: String
        get() = prefs.getString("faster_whisper_server_url", "https://stt.snowrealm.pet") ?: "https://stt.snowrealm.pet"
        set(value) = prefs.edit().putString("faster_whisper_server_url", value).apply()

    // ═══════════════════════════════════════════════════════════
    // TTS 語音輸出
    // ═══════════════════════════════════════════════════════════
    /** TTS 引擎：0=Android 內建, 1=Google Cloud TTS */
    var ttsEngineType: Int by IntPref("tts_engine_type", 0)
    /** TTS 朗讀翻譯結果 */
    var ttsTranslateRead: Boolean by BoolPref("tts_translate_read", true)
    /** TTS Yuki 角色語音回應 */
    var ttsYukiVoice: Boolean by BoolPref("tts_yuki_voice", true)
    /** TTS 無障礙候選字朗讀 */
    var ttsCandidateRead: Boolean by BoolPref("tts_candidate_read", false)
    /** TTS 語速（0.5~2.0） */
    var ttsSpeed: Float by FloatPref("tts_speed", 1.0f)
    /** TTS 語音名稱（空字串 = 使用系統預設） */
    var ttsVoiceName: String
        get() = prefs.getString("tts_voice_name", "") ?: ""
        set(value) = prefs.edit().putString("tts_voice_name", value).apply()
    /** Google Cloud TTS API Key */
    var googleCloudTtsApiKey: String
        get() = prefs.getString("google_cloud_tts_key", "") ?: ""
        set(value) = prefs.edit().putString("google_cloud_tts_key", value).apply()
    var smartCorrection: Boolean by BoolPref("smart_correction", true)
    var adaptiveDict: Boolean by BoolPref("adaptive_dict", true)
    var phrasePredict: Boolean by BoolPref("phrase_predict", true)
    var habitTracking: Boolean by BoolPref("habit_tracking", true)

    // ════════════════════════════���══════════════════════════════
    // 連線功能
    // ══════��══════════���══════════════════════════════��══════════
    var remoteKeyboardEnabled: Boolean by BoolPref("remote_keyboard", true)

    // ═══════════════════════════════════════════════════════════
    // Insight 整合
    // ═══════════════════════════════════════════════════════════
    var insightSurveyEnabled: Boolean by BoolPref("insight_survey", false)     // 預設關（opt-in）
    var insightThemeSyncEnabled: Boolean by BoolPref("insight_theme_sync", true)
    var insightSettingsBackup: Boolean by BoolPref("insight_settings_backup", true)

    // ══��═════════════════════════��═════════════════════���════════
    // 鍵盤尺寸 / 懸浮模式（保留原有欄位）
    // ════════════════════════════════════��══════════════════════
    var keyboardHeightRatio: Float
        get() = prefs.getFloat(KEY_KB_HEIGHT, 0.35f)
        set(value) = prefs.edit().putFloat(KEY_KB_HEIGHT, value.coerceIn(MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO)).apply()

    var keyboardWidthRatio: Float
        get() = prefs.getFloat(KEY_KB_WIDTH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_KB_WIDTH, value.coerceIn(MIN_WIDTH_RATIO, MAX_WIDTH_RATIO)).apply()

    var floatingModeEnabled: Boolean by BoolPref("floating_mode", false)

    var floatingX: Int by IntPref("floating_x", -1)
    var floatingY: Int by IntPref("floating_y", -1)

    // 自訂背景
    var useCustomBackground: Boolean by BoolPref("use_custom_bg", false)
    var videoBgWarnShown: Boolean by BoolPref("video_bg_warn_shown", false)
    var bgOverlayAlpha: Int by IntPref("bg_overlay_alpha", 100)
    var bgOffsetX: Float by FloatPref("bg_offset_x", 0.5f)   // 0.0=最左 ~ 1.0=最右
    var bgOffsetY: Float by FloatPref("bg_offset_y", 0.5f)   // 0.0=最上 ~ 1.0=最下
    var bgScaleMode: Int by IntPref("bg_scale_mode", 0)       // 0=保持比例(裁切) 1=整張塞入(拉伸)
    var keyScalePercent: Int by IntPref("key_scale_percent", 100)  // 按鍵大小 70~130%
    var keyOpacity: Int by IntPref("key_opacity", 255)             // 按鍵背景不透明度 0~255
    var keyTextOpacity: Int by IntPref("key_text_opacity", 255)    // 按鍵文字不透明度 0~255

    // 透明鍵盤（透視到底層 App）
    var transparentKeyboard: Boolean by BoolPref("transparent_keyboard", false)
    var transparentKeyboardAlpha: Int by IntPref("transparent_keyboard_alpha", 40) // 0~255

    // 自訂字體顏色（0 = 跟隨主題）
    var customKeyLabelColor: Int by IntPref("custom_key_label_color", 0)

    // ═══════════════════════════════════════════════════════════
    // 個別按鍵尺寸（JSON: {"keyId": {"w": 1.0, "h": 1.0}, ...}）
    // keyId = keyCode（特殊鍵）或 label（字元鍵）
    // w/h = 寬度/高度倍率，1.0 = 預設
    // ═══════════════════════════════════════════════════════════
    private var perKeySizeJson: String
        get() = prefs.getString("per_key_size_json", "{}") ?: "{}"
        set(value) = prefs.edit().putString("per_key_size_json", value).apply()

    fun getPerKeyWidth(keyId: String): Float {
        return try {
            val json = JSONObject(perKeySizeJson)
            json.optJSONObject(keyId)?.optDouble("w", 1.0)?.toFloat() ?: 1.0f
        } catch (_: Exception) { 1.0f }
    }

    fun getPerKeyHeight(keyId: String): Float {
        return try {
            val json = JSONObject(perKeySizeJson)
            json.optJSONObject(keyId)?.optDouble("h", 1.0)?.toFloat() ?: 1.0f
        } catch (_: Exception) { 1.0f }
    }

    fun setPerKeySize(keyId: String, w: Float, h: Float) {
        val json = try { JSONObject(perKeySizeJson) } catch (_: Exception) { JSONObject() }
        val entry = JSONObject().put("w", w.toDouble()).put("h", h.toDouble())
        json.put(keyId, entry)
        perKeySizeJson = json.toString()
    }

    fun resetPerKeySize(keyId: String) {
        val json = try { JSONObject(perKeySizeJson) } catch (_: Exception) { JSONObject() }
        json.remove(keyId)
        perKeySizeJson = json.toString()
    }

    fun resetAllPerKeySizes() {
        perKeySizeJson = "{}"
    }

    fun getAllPerKeySizes(): Map<String, Pair<Float, Float>> {
        val result = mutableMapOf<String, Pair<Float, Float>>()
        try {
            val json = JSONObject(perKeySizeJson)
            json.keys().forEach { key ->
                val obj = json.optJSONObject(key)
                if (obj != null) {
                    result[key] = Pair(
                        obj.optDouble("w", 1.0).toFloat(),
                        obj.optDouble("h", 1.0).toFloat()
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

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
