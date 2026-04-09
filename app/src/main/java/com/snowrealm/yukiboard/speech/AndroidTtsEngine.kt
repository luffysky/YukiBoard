package com.snowrealm.yukiboard.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Android 原生 [TextToSpeech] 語音輸出引擎。
 *
 * 免費、離線可用、支援大部分語言。
 * 品質取決於裝置安裝的 TTS 引擎（Google TTS / Samsung TTS 等）。
 */
class AndroidTtsEngine(context: Context, private val onInitDone: ((Boolean) -> Unit)? = null) : SpeechOutputEngine {

    private val tag = "AndroidTtsEngine"
    private var tts: TextToSpeech? = null
    private val pendingCallbacks = java.util.concurrent.ConcurrentHashMap<String, (() -> Unit)?>()
    private var selectedVoiceName: String? = null
    private var hasCustomVoice = false  // 成功套用自訂語音後設為 true

    override var isReady: Boolean = false
        private set

    override val isSpeaking: Boolean
        get() = tts?.isSpeaking == true

    /** 所有可用語音資訊 */
    data class VoiceInfo(
        val name: String,
        val locale: Locale,
        val quality: Int,
        val isNetworkRequired: Boolean
    ) {
        /** 顯示用標籤：語言名 + 品質 + 名稱 */
        fun displayLabel(): String {
            val lang = locale.displayLanguage
            val country = locale.displayCountry.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
            val q = if (quality >= 400) "★" else if (quality >= 300) "☆" else ""
            val net = if (isNetworkRequired) " ☁" else ""
            return "$lang$country $q$net — $name"
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { id ->
                            pendingCallbacks.remove(id)?.invoke()
                        }
                    }
                    @Deprecated("Deprecated in API 21+")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { pendingCallbacks.remove(it) }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(tag, "TTS error: utterance=$utteranceId code=$errorCode")
                        utteranceId?.let { pendingCallbacks.remove(it) }
                    }
                })
                // 使用媒體音訊通道（跟隨媒體音量）
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // 套用已選語音
                val voiceName = selectedVoiceName
                if (!voiceName.isNullOrEmpty()) {
                    applyVoiceByName(voiceName)
                }
                Log.i(tag, "TTS initialized, engine=${tts?.defaultEngine}, voices=${getAvailableVoices().size}")
            } else {
                Log.e(tag, "TTS init failed, status=$status")
            }
            onInitDone?.invoke(isReady)
        }
    }

    /** 取得所有可用語音列表（依語言分組排序） */
    fun getAvailableVoices(): List<VoiceInfo> {
        val engine = tts ?: return emptyList()
        if (!isReady) return emptyList()
        return try {
            engine.voices?.map { voice ->
                VoiceInfo(
                    name = voice.name,
                    locale = voice.locale,
                    quality = voice.quality,
                    isNetworkRequired = voice.isNetworkConnectionRequired
                )
            }?.sortedWith(compareBy<VoiceInfo> { it.locale.language }
                .thenBy { it.locale.country }
                .thenByDescending { it.quality }
                .thenBy { it.name }
            ) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "getAvailableVoices failed", e)
            emptyList()
        }
    }

    /** 依語音名稱設定語音 */
    fun setVoice(voiceName: String?) {
        selectedVoiceName = voiceName
        if (voiceName.isNullOrEmpty()) {
            hasCustomVoice = false
        } else if (isReady) {
            applyVoiceByName(voiceName)
        }
    }

    private fun applyVoiceByName(voiceName: String) {
        val engine = tts ?: return
        try {
            val voice = engine.voices?.firstOrNull { it.name == voiceName }
            if (voice != null) {
                engine.voice = voice
                hasCustomVoice = true
                Log.i(tag, "Voice set: ${voice.name} (${voice.locale})")
            } else {
                // 嘗試部分名稱匹配（不同 TTS 引擎可能有不同前綴）
                val partial = engine.voices?.firstOrNull {
                    it.name.contains(voiceName, ignoreCase = true) ||
                    voiceName.contains(it.name, ignoreCase = true)
                }
                if (partial != null) {
                    engine.voice = partial
                    hasCustomVoice = true
                    Log.i(tag, "Voice set (partial match): ${partial.name} (${partial.locale})")
                } else {
                    hasCustomVoice = false
                    Log.w(tag, "Voice not found: $voiceName, available=${engine.voices?.size ?: 0}")
                }
            }
        } catch (e: Exception) {
            hasCustomVoice = false
            Log.e(tag, "setVoice failed: $voiceName", e)
        }
    }

    override fun speak(
        text: String,
        locale: String?,
        speed: Float,
        onDone: (() -> Unit)?
    ) {
        val engine = tts ?: return
        if (!isReady) {
            Log.w(tag, "TTS not ready, skipping: ${text.take(30)}")
            return
        }
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        // 設定語言（注意：setLanguage 會覆蓋已選語音，有自訂語音時跳過）
        val currentVoiceName = selectedVoiceName
        if (!currentVoiceName.isNullOrEmpty() && hasCustomVoice) {
            // 每次朗讀前重新確認語音（某些 TTS 引擎會在 setLanguage 後重置 voice）
            if (engine.voice?.name != currentVoiceName) {
                applyVoiceByName(currentVoiceName)
            }
            Log.i(tag, "speak: using custom voice=${engine.voice?.name}, text=${text.take(30)}")
        } else {
            val javaLocale = locale?.let { parseLocale(it) } ?: detectLocale(text)
            val langResult = engine.setLanguage(javaLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Language not supported: $javaLocale (result=$langResult), falling back to default")
                engine.setLanguage(Locale.getDefault())
            }
            Log.i(tag, "speak: locale=$javaLocale, langResult=$langResult, text=${text.take(30)}")
        }

        // 設定語速
        engine.setSpeechRate(speed.coerceIn(0.25f, 4.0f))

        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            pendingCallbacks[utteranceId] = onDone
        }

        // 使用 STREAM_MUSIC（媒體音量）
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val speakResult = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (speakResult != TextToSpeech.SUCCESS) {
            Log.e(tag, "speak() returned error: $speakResult")
        }
    }

    override fun stop() {
        tts?.stop()
        pendingCallbacks.clear()
    }

    override fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    /** 從 locale code ("zh-TW") 解析 Java Locale */
    private fun parseLocale(code: String): Locale {
        val parts = code.split("-", "_")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            else -> Locale(parts[0], parts[1], parts[2])
        }
    }

    /** 根據文字內容自動偵測語言 */
    private fun detectLocale(text: String): Locale {
        val sample = text.take(50)
        return when {
            sample.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' } -> Locale.JAPAN
            sample.any { it in '\uAC00'..'\uD7AF' } -> Locale.KOREA
            sample.any { it in '\u4E00'..'\u9FFF' } -> Locale.TRADITIONAL_CHINESE
            sample.all { it.code < 128 } -> Locale.US
            else -> Locale.getDefault()
        }
    }
}
