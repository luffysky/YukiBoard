package com.snowrealm.yukiboard.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.input.dictionary.UserDictionary
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * YukiBoard 雲端同步管理器。
 *
 * 同步範圍：
 * 1. 偏好設定（YukiPreferences → user_settings）
 * 2. 自訂詞庫（UserDictionary → user_dictionary）
 * 3. 主題設定（背景/流光/角色 → user_themes）
 *
 * 帳號系統綁定 Insight Engine（SnowRealmAuthClient）。
 * 資料存 Supabase（SupabaseClient）。
 */
class CloudSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSync"
        private const val PREF_LAST_SYNC = "cloud_sync_last_time"
        private const val PREF_SYNC_ENABLED = "cloud_sync_enabled"

        @Volatile
        private var instance: CloudSyncManager? = null

        fun getInstance(context: Context): CloudSyncManager {
            return instance ?: synchronized(this) {
                instance ?: CloudSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val supabase = SupabaseClient.getInstance()
    private val auth = SnowRealmAuthClient.getInstance(context)
    private val prefs = YukiPreferences(context)
    private val metaPrefs: SharedPreferences =
        context.getSharedPreferences("cloud_sync_meta", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val isSyncEnabled: Boolean get() = metaPrefs.getBoolean(PREF_SYNC_ENABLED, false)
    val lastSyncTime: Long get() = metaPrefs.getLong(PREF_LAST_SYNC, 0)

    fun setSyncEnabled(enabled: Boolean) {
        metaPrefs.edit().putBoolean(PREF_SYNC_ENABLED, enabled).apply()
    }

    // ─────────────────────────────────────────
    // 上傳（本地 → 雲端）
    // ─────────────────────────────────────────

    /** 全量上傳（偏好 + 詞庫 + 主題） */
    suspend fun uploadAll(): SyncResult = withContext(Dispatchers.IO) {
        val userId = auth.currentUserId ?: return@withContext SyncResult.NotLoggedIn
        if (!supabase.isConfigured) return@withContext SyncResult.Error("Supabase 未設定")
        val token = auth.getToken()

        try {
            val r1 = uploadSettings(userId, token)
            val r2 = uploadDictionary(userId, token)
            val r3 = uploadThemes(userId, token)

            metaPrefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()

            val errors = listOfNotNull(
                (r1 as? SyncResult.Error)?.message,
                (r2 as? SyncResult.Error)?.message,
                (r3 as? SyncResult.Error)?.message
            )
            if (errors.isEmpty()) SyncResult.Success
            else SyncResult.Error("部分失敗: ${errors.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "uploadAll failed", e)
            SyncResult.Error("上傳失敗: ${e.message}")
        }
    }

    /** 上傳偏好設定 */
    private suspend fun uploadSettings(userId: String, token: String?): SyncResult {
        val settingsJson = exportPrefsToJson()
        val data = buildJsonObject {
            put("user_id", userId)
            put("settings", settingsJson)
            put("updated_at", JsonPrimitive("now()"))
        }
        return upsert("user_settings", "user_id=eq.$userId", data, token)
    }

    /** 上傳自訂詞庫 */
    private suspend fun uploadDictionary(userId: String, token: String?): SyncResult {
        val userDict = UserDictionary(context)
        val entries = userDict.getAllManualWithCategory()
        if (entries.isEmpty()) return SyncResult.Success

        // 批次 upsert — 逐筆插入（Supabase REST 不支援原生 upsert array）
        var errorCount = 0
        for ((word, bopomofo, category) in entries) {
            val data = buildJsonObject {
                put("user_id", userId)
                put("word", word)
                put("bopomofo", bopomofo)
                put("category", category)
                put("deleted", false)
                put("updated_at", JsonPrimitive("now()"))
            }
            val result = upsert(
                "user_dictionary",
                "user_id=eq.$userId&word=eq.${encode(word)}",
                data, token
            )
            if (result is SyncResult.Error) errorCount++
        }

        return if (errorCount == 0) SyncResult.Success
        else SyncResult.Error("詞庫上傳 $errorCount 筆失敗")
    }

    /** 上傳主題設定 */
    private suspend fun uploadThemes(userId: String, token: String?): SyncResult {
        val themeJson = exportThemeToJson()
        val data = buildJsonObject {
            put("user_id", userId)
            put("theme_config", themeJson)
            put("updated_at", JsonPrimitive("now()"))
        }
        return upsert("user_themes", "user_id=eq.$userId", data, token)
    }

    // ─────────────────────────────────────────
    // 下載（雲端 → 本地）
    // ─────────────────────────────────────────

    /** 全量下載（偏好 + 詞庫 + 主題） */
    suspend fun downloadAll(): SyncResult = withContext(Dispatchers.IO) {
        val userId = auth.currentUserId ?: return@withContext SyncResult.NotLoggedIn
        if (!supabase.isConfigured) return@withContext SyncResult.Error("Supabase 未設定")
        val token = auth.getToken()

        try {
            val r1 = downloadSettings(userId, token)
            val r2 = downloadDictionary(userId, token)
            val r3 = downloadThemes(userId, token)

            metaPrefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()

            val errors = listOfNotNull(
                (r1 as? SyncResult.Error)?.message,
                (r2 as? SyncResult.Error)?.message,
                (r3 as? SyncResult.Error)?.message
            )
            if (errors.isEmpty()) SyncResult.Success
            else SyncResult.Error("部分失敗: ${errors.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "downloadAll failed", e)
            SyncResult.Error("下載失敗: ${e.message}")
        }
    }

    /** 下載偏好設定 */
    private suspend fun downloadSettings(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_settings",
            "user_id=eq.$userId&select=settings",
            token
        )
        val settingsObj = rows?.firstOrNull()
            ?.jsonObject?.get("settings")?.jsonObject
            ?: return SyncResult.Success // 雲端無資料，不算錯

        importPrefsFromJson(settingsObj)
        return SyncResult.Success
    }

    /** 下載自訂詞庫 */
    private suspend fun downloadDictionary(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_dictionary",
            "user_id=eq.$userId&deleted=eq.false&select=word,bopomofo,category",
            token
        ) ?: return SyncResult.Success

        val userDict = UserDictionary(context)
        for (row in rows) {
            val obj = row.jsonObject
            val word = obj["word"]?.jsonPrimitive?.contentOrNull ?: continue
            val bopomofo = obj["bopomofo"]?.jsonPrimitive?.contentOrNull ?: continue
            val category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "一般"
            // 只新增本地不存在的詞（不覆蓋本地修改）
            val existing = userDict.getAllManual().map { it.first }
            if (word !in existing) {
                userDict.addManual(word, bopomofo, category)
            }
        }
        return SyncResult.Success
    }

    /** 下載主題設定 */
    private suspend fun downloadThemes(userId: String, token: String?): SyncResult {
        val rows = supabase.select(
            "user_themes",
            "user_id=eq.$userId&select=theme_config",
            token
        )
        val themeObj = rows?.firstOrNull()
            ?.jsonObject?.get("theme_config")?.jsonObject
            ?: return SyncResult.Success

        importThemeFromJson(themeObj)
        return SyncResult.Success
    }

    // ─────────────────────────────────────────
    // Prefs ↔ JSON
    // ─────────────────────────────────────────

    /** 把 YukiPreferences 匯出成 JSON（只同步使用者可控的設定） */
    private fun exportPrefsToJson(): JsonObject {
        return buildJsonObject {
            // AI 開關
            put("ai_enabled", prefs.aiEnabled)
            put("ai_prediction", prefs.aiPrediction)
            put("ai_writer", prefs.aiWriter)
            put("ai_translate", prefs.aiTranslate)
            put("ai_cupid", prefs.aiCupid)
            put("ai_whisper", prefs.aiWhisper)
            put("ai_grammar_check", prefs.aiGrammarCheck)
            put("ai_tone_adjust", prefs.aiToneAdjust)
            put("ai_summarize", prefs.aiSummarize)
            put("ai_emoji_translate", prefs.aiEmojiTranslate)
            put("ai_quick_reply", prefs.aiQuickReply)
            put("ai_code_complete", prefs.aiCodeComplete)
            put("ai_regex_gen", prefs.aiRegexGen)
            // 感測器
            put("shake_to_undo", prefs.shakeToUndoEnabled)
            put("gyro_cursor", prefs.gyroscopeCursorEnabled)
            put("gyro_sensitivity", prefs.gyroscopeSensitivity)
            put("app_auto_detect", prefs.appAutoDetectEnabled)
            // 動畫 / 音效
            put("flow_light", prefs.flowLightEnabled)
            put("character_anim", prefs.characterAnimEnabled)
            put("key_particle", prefs.keyParticleEffect)
            put("key_vibration", prefs.keyPressVibration)
            put("key_sound", prefs.keySoundEnabled)
            put("key_sound_style", prefs.keySoundStyle)
            put("key_sound_volume", prefs.keySoundVolume)
            // 輸入
            put("auto_input_mode", prefs.autoInputMode)
            put("swipe_input", prefs.swipeInput)
            put("settings_language", prefs.settingsLanguage)
            // 面板
            put("floating_panel", prefs.floatingPanelEnabled)
            put("weather_panel", prefs.weatherPanelEnabled)
            put("sticker_panel", prefs.stickerPanelEnabled)
            // 遊戲
            put("games_enabled", prefs.gamesEnabled)
            put("game_bgm", prefs.gameBackgroundMusic)
            put("game_sfx", prefs.gameSoundEffects)
            // 雪凜
            put("yuki_sarcastic", prefs.yukiSarcasticComments)
            put("yuki_cooldown", prefs.yukiEmotionalCooldown)
            put("yuki_mood_passive", prefs.yukiMoodDiaryPassive)
            put("yuki_weather_alert", prefs.yukiWeatherAlert)
            put("yuki_late_night", prefs.yukiLateNightComment)
        }
    }

    /** 從 JSON 匯入偏好設定 */
    private fun importPrefsFromJson(obj: JsonObject) {
        obj["ai_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiEnabled = it }
        obj["ai_prediction"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiPrediction = it }
        obj["ai_writer"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiWriter = it }
        obj["ai_translate"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiTranslate = it }
        obj["ai_cupid"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiCupid = it }
        obj["ai_whisper"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiWhisper = it }
        obj["ai_grammar_check"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiGrammarCheck = it }
        obj["ai_tone_adjust"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiToneAdjust = it }
        obj["ai_summarize"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiSummarize = it }
        obj["ai_emoji_translate"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiEmojiTranslate = it }
        obj["ai_quick_reply"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiQuickReply = it }
        obj["ai_code_complete"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiCodeComplete = it }
        obj["ai_regex_gen"]?.jsonPrimitive?.booleanOrNull?.let { prefs.aiRegexGen = it }
        obj["shake_to_undo"]?.jsonPrimitive?.booleanOrNull?.let { prefs.shakeToUndoEnabled = it }
        obj["gyro_cursor"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gyroscopeCursorEnabled = it }
        obj["gyro_sensitivity"]?.jsonPrimitive?.floatOrNull?.let { prefs.gyroscopeSensitivity = it }
        obj["app_auto_detect"]?.jsonPrimitive?.booleanOrNull?.let { prefs.appAutoDetectEnabled = it }
        obj["flow_light"]?.jsonPrimitive?.booleanOrNull?.let { prefs.flowLightEnabled = it }
        obj["character_anim"]?.jsonPrimitive?.booleanOrNull?.let { prefs.characterAnimEnabled = it }
        obj["key_particle"]?.jsonPrimitive?.booleanOrNull?.let { prefs.keyParticleEffect = it }
        obj["key_vibration"]?.jsonPrimitive?.booleanOrNull?.let { prefs.keyPressVibration = it }
        obj["key_sound"]?.jsonPrimitive?.booleanOrNull?.let { prefs.keySoundEnabled = it }
        obj["key_sound_style"]?.jsonPrimitive?.contentOrNull?.let { prefs.keySoundStyle = it }
        obj["key_sound_volume"]?.jsonPrimitive?.floatOrNull?.let { prefs.keySoundVolume = it }
        obj["auto_input_mode"]?.jsonPrimitive?.booleanOrNull?.let { prefs.autoInputMode = it }
        obj["swipe_input"]?.jsonPrimitive?.booleanOrNull?.let { prefs.swipeInput = it }
        obj["settings_language"]?.jsonPrimitive?.contentOrNull?.let { prefs.settingsLanguage = it }
        obj["floating_panel"]?.jsonPrimitive?.booleanOrNull?.let { prefs.floatingPanelEnabled = it }
        obj["weather_panel"]?.jsonPrimitive?.booleanOrNull?.let { prefs.weatherPanelEnabled = it }
        obj["sticker_panel"]?.jsonPrimitive?.booleanOrNull?.let { prefs.stickerPanelEnabled = it }
        obj["games_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gamesEnabled = it }
        obj["game_bgm"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gameBackgroundMusic = it }
        obj["game_sfx"]?.jsonPrimitive?.booleanOrNull?.let { prefs.gameSoundEffects = it }
        obj["yuki_sarcastic"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiSarcasticComments = it }
        obj["yuki_cooldown"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiEmotionalCooldown = it }
        obj["yuki_mood_passive"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiMoodDiaryPassive = it }
        obj["yuki_weather_alert"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiWeatherAlert = it }
        obj["yuki_late_night"]?.jsonPrimitive?.booleanOrNull?.let { prefs.yukiLateNightComment = it }
    }

    // ─────────────────────────────────────────
    // Theme ↔ JSON
    // ─────────────────────────────────────────

    private fun exportThemeToJson(): JsonObject {
        return buildJsonObject {
            put("use_custom_background", prefs.useCustomBackground)
            put("bg_overlay_alpha", prefs.bgOverlayAlpha)
            put("bg_scale_mode", prefs.bgScaleMode)
            put("bg_offset_x", prefs.bgOffsetX)
            put("bg_offset_y", prefs.bgOffsetY)
            put("bg_rotation", prefs.bgRotation)
            put("keyboard_height_ratio", prefs.keyboardHeightRatio)
            put("toolbar_anim_style", prefs.toolbarAnimStyle)
        }
    }

    private fun importThemeFromJson(obj: JsonObject) {
        obj["use_custom_background"]?.jsonPrimitive?.booleanOrNull?.let { prefs.useCustomBackground = it }
        obj["bg_overlay_alpha"]?.jsonPrimitive?.intOrNull?.let { prefs.bgOverlayAlpha = it }
        obj["bg_scale_mode"]?.jsonPrimitive?.intOrNull?.let { prefs.bgScaleMode = it }
        obj["bg_offset_x"]?.jsonPrimitive?.floatOrNull?.let { prefs.bgOffsetX = it }
        obj["bg_offset_y"]?.jsonPrimitive?.floatOrNull?.let { prefs.bgOffsetY = it }
        obj["bg_rotation"]?.jsonPrimitive?.intOrNull?.let { prefs.bgRotation = it }
        obj["keyboard_height_ratio"]?.jsonPrimitive?.floatOrNull?.let { prefs.keyboardHeightRatio = it }
        obj["toolbar_anim_style"]?.jsonPrimitive?.contentOrNull?.let { prefs.toolbarAnimStyle = it }
    }

    // ─────────────────────────────────────────
    // Supabase upsert helper
    // ─────────────────────────────────────────

    private suspend fun upsert(
        table: String,
        query: String,
        data: JsonObject,
        token: String?
    ): SyncResult {
        return try {
            // 先嘗試更新
            val updated = supabase.update(table, query, data, token)
            if (updated == null || (updated is JsonArray && updated.isEmpty())) {
                // 沒有更新到 → 插入
                supabase.insert(table, data, token)
            }
            SyncResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "upsert $table failed", e)
            SyncResult.Error("$table: ${e.message}")
        }
    }

    /** URL encode for Supabase query */
    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    // ─────────────────────────────────────────
    // 結果
    // ─────────────────────────────────────────

    sealed class SyncResult {
        object Success : SyncResult()
        object NotLoggedIn : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
