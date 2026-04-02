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
                    onFinalResult(matches?.firstOrNull() ?: "")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    onPartialResult(partial?.firstOrNull() ?: "")
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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
}
