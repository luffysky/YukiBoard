package com.snowrealm.yukiboard.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 使用 Android 原生 [SpeechRecognizer] 的語音輸入引擎。
 *
 * 注意事項：
 * - 需要 `RECORD_AUDIO` 權限，在 Android 6+ 須動態申請。
 * - [SpeechRecognizer] 必須在主執行緒建立與操作。
 * - 部分裝置（無 GMS）可能不支援，使用前呼叫 [SpeechRecognizer.isRecognitionAvailable]。
 */
class AndroidSpeechEngine(context: Context) : SpeechInputEngine {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override var isListening: Boolean = false
        private set

    override fun startListening(
        locale: String,
        onPartialResult: (String) -> Unit,
        onFinalResult:   (String) -> Unit,
        onError:         (Int)    -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }

                override fun onError(error: Int) {
                    isListening = false
                    onError(error)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val best = if (scores != null && matches != null) {
                        matches.getOrNull(scores.indices.maxByOrNull { scores[it] } ?: 0) ?: ""
                    } else matches?.firstOrNull() ?: ""
                    onFinalResult(best)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    onPartialResult(partial?.firstOrNull() ?: "")
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val isMixed = locale.isBlank()
        val effectiveLocale = if (isMixed) Locale.getDefault().toLanguageTag() else locale

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, effectiveLocale)
            if (!isMixed) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, effectiveLocale)
            }
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            // 開啟多語言支援：列出所有常用語言，引擎會自動判斷
            putStringArrayListExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, ArrayList(SUPPORTED_LOCALES))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // 降低靜音偵測敏感度，避免過早截斷
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
            // 偏好離線模型（如有）→ 減少延遲
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            // 多語言辨識：所有支援語言都加入額外辨識清單
            val extraLangs = SUPPORTED_LOCALES.filter { it != effectiveLocale }
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", extraLangs.toTypedArray())
        }

        recognizer?.startListening(intent)
    }

    override fun stopListening() {
        recognizer?.stopListening()
        isListening = false
    }

    override fun cancel() {
        recognizer?.cancel()
        isListening = false
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    companion object {
        /** 支援的語言清單 — 作為額外辨識語言 */
        val SUPPORTED_LOCALES = listOf(
            "en-US", "zh-TW", "zh-CN", "zh-HK",
            "ja-JP", "ko-KR",
            "es-ES", "fr-FR", "de-DE", "pt-BR",
            "th-TH", "vi-VN", "id-ID",
            "it-IT", "ru-RU", "ar-SA", "tr-TR"
        )
    }
}
