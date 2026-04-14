package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.speech.AndroidSpeechEngine
import com.snowrealm.yukiboard.speech.FasterWhisperSpeechEngine
import com.snowrealm.yukiboard.speech.SpeechInputEngine
import com.snowrealm.yukiboard.speech.WhisperSpeechEngine

/**
 * 收斂 IME 語音輸入的決策與狀態協調。
 *
 * 目前先處理：
 * - 是否應停止當前 listening
 * - 權限/overlay gating
 * - engine recreate 判斷
 * - locale / silence threshold / engine name
 * - start/stop 的共享狀態切換
 */
class ImeVoiceController {

    fun shouldStopCurrentListening(engine: SpeechInputEngine?): Boolean {
        return engine?.isListening == true
    }

    fun canStartVoiceInput(isOverlayShowing: Boolean): Boolean {
        return !isOverlayShowing
    }

    fun handleMissingPermission(
        hasRecordAudioPermission: Boolean,
        openPermissionSettings: () -> Unit,
        showPermissionToast: () -> Unit,
    ): Boolean {
        if (hasRecordAudioPermission) return true
        openPermissionSettings()
        showPermissionToast()
        return false
    }

    fun shouldRecreateEngine(
        currentEngine: SpeechInputEngine?,
        desiredEngineType: Int,
    ): Boolean {
        return when {
            currentEngine == null -> true
            desiredEngineType == 2 && currentEngine !is FasterWhisperSpeechEngine -> true
            desiredEngineType == 1 && currentEngine !is WhisperSpeechEngine -> true
            desiredEngineType == 0 && currentEngine !is AndroidSpeechEngine -> true
            else -> false
        }
    }

    fun resolveEngineName(engine: SpeechInputEngine?): String {
        return when (engine) {
            is FasterWhisperSpeechEngine -> "Faster-Whisper"
            is WhisperSpeechEngine -> "Whisper API"
            else -> "Android 內建"
        }
    }

    fun resolveLocaleHint(
        speechLanguage: String,
        inputMode: InputMode,
    ): String {
        return when (speechLanguage) {
            "auto" -> when (inputMode) {
                InputMode.ENGLISH -> "en-US"
                InputMode.BOPOMOFO,
                InputMode.CANGJIE,
                InputMode.SPEED_CANGJIE -> "zh-TW"
                InputMode.PINYIN -> "zh-CN"
                InputMode.JAPANESE -> "ja-JP"
                InputMode.KOREAN -> "ko-KR"
                InputMode.GERMAN -> "de-DE"
                InputMode.FRENCH -> "fr-FR"
                InputMode.SPANISH -> "es-ES"
                InputMode.VIETNAMESE -> "vi-VN"
                InputMode.ITALIAN -> "it-IT"
                else -> "zh-TW"
            }
            "mixed" -> ""
            else -> speechLanguage
        }
    }

    fun resolveSilenceThreshold(voiceSilenceSensitivity: Int): Int {
        return when (voiceSilenceSensitivity) {
            0 -> 800
            2 -> 2000
            else -> 1200
        }
    }

    fun onVoiceInputStarted(
        stopTts: () -> Unit,
        setVoiceInputActive: (Boolean) -> Unit,
        playListening: () -> Unit,
    ) {
        stopTts()
        setVoiceInputActive(true)
        playListening()
    }

    fun onVoiceInputStopped(
        stopListening: () -> Unit,
        setVoiceInputActive: (Boolean) -> Unit,
        clearVoiceListeningLocale: () -> Unit,
        playIdle: () -> Unit,
    ) {
        stopListening()
        setVoiceInputActive(false)
        clearVoiceListeningLocale()
        playIdle()
    }

    fun bindOverlayCallbacks(
        stopVoiceInput: () -> Unit,
        playIdle: () -> Unit,
        playListening: () -> Unit,
        onPauseRecording: () -> Unit = {},
    ): VoiceOverlayCallbacks {
        return VoiceOverlayCallbacks(
            onStop = stopVoiceInput,
            onPause = {
                onPauseRecording()
                playIdle()
            },
            onResume = playListening,
        )
    }

    fun shouldShowPartialResult(voiceFinalHandled: Boolean): Boolean {
        return !voiceFinalHandled
    }

    fun onFinalResultReceived(voiceFinalHandled: Boolean): Boolean {
        return !voiceFinalHandled
    }

    fun shouldPostProcessResult(result: String, voicePostProcessEnabled: Boolean): Boolean {
        return result.isNotEmpty() && voicePostProcessEnabled
    }

    fun shouldShowFinalResult(result: String, voicePostProcessEnabled: Boolean): Boolean {
        return result.isNotEmpty() && !voicePostProcessEnabled
    }

    fun shouldDismissOverlay(result: String): Boolean {
        return result.isEmpty()
    }

    fun resolveErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            FasterWhisperSpeechEngine.ERROR_NO_SERVER -> "語音辨識失敗：未設定 Server URL"
            WhisperSpeechEngine.ERROR_NO_API_KEY -> "語音辨識失敗：未設定 API Key"
            WhisperSpeechEngine.ERROR_NO_PERMISSION,
            FasterWhisperSpeechEngine.ERROR_NO_PERMISSION -> "語音辨識失敗：無麥克風權限"
            WhisperSpeechEngine.ERROR_AUDIO_INIT,
            FasterWhisperSpeechEngine.ERROR_AUDIO_INIT -> "語音辨識失敗：錄音初始化失敗"
            WhisperSpeechEngine.ERROR_NETWORK,
            FasterWhisperSpeechEngine.ERROR_NETWORK -> "語音辨識失敗：網路錯誤"
            else -> "語音辨識錯誤 ($errorCode)"
        }
    }
}

data class VoiceOverlayCallbacks(
    val onStop: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
)
