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
    private var pendingCallbacks = mutableMapOf<String, (() -> Unit)?>()

    override var isReady: Boolean = false
        private set

    override val isSpeaking: Boolean
        get() = tts?.isSpeaking == true

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
                // 確保使用正確音頻通道（USAGE_MEDIA + CONTENT_TYPE_SPEECH）
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                Log.i(tag, "TTS initialized, engine=${tts?.defaultEngine}")
            } else {
                Log.e(tag, "TTS init failed, status=$status")
            }
            onInitDone?.invoke(isReady)
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

        // 設定語言
        val javaLocale = locale?.let { parseLocale(it) } ?: detectLocale(text)
        val langResult = engine.setLanguage(javaLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(tag, "Language not supported: $javaLocale (result=$langResult), falling back to default")
            engine.setLanguage(Locale.getDefault())
        }
        Log.i(tag, "speak: locale=$javaLocale, langResult=$langResult, text=${text.take(30)}")

        // 設定語速
        engine.setSpeechRate(speed.coerceIn(0.25f, 4.0f))

        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            pendingCallbacks[utteranceId] = onDone
        }

        // 使用 STREAM_NOTIFICATION 不受靜音和 AudioFocus 影響
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
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
