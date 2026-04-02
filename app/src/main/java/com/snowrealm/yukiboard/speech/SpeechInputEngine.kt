package com.snowrealm.yukiboard.speech

/**
 * 語音輸入引擎介面。
 *
 * 解耦底層語音辨識實作，讓業務層不依賴 Android SpeechRecognizer 或第三方 SDK。
 * 未來可替換為：Whisper、Google Cloud Speech-to-Text、離線 Vosk 等。
 */
interface SpeechInputEngine {

    /** 當前是否正在收音 */
    val isListening: Boolean

    /**
     * 開始語音辨識。
     * @param locale 語系代碼，例如 "zh-TW", "en-US"
     * @param onPartialResult 中間識別結果回呼（可能多次觸發）
     * @param onFinalResult   最終識別結果回呼
     * @param onError         錯誤回呼，參數為錯誤碼
     */
    fun startListening(
        locale: String,
        onPartialResult: (String) -> Unit,
        onFinalResult:   (String) -> Unit,
        onError:         (Int)    -> Unit
    )

    /** 停止收音（取得目前結果） */
    fun stopListening()

    /** 取消並捨棄目前收音 */
    fun cancel()

    /** 釋放資源 */
    fun destroy()
}
