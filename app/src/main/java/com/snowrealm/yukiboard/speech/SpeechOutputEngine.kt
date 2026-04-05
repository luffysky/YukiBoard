package com.snowrealm.yukiboard.speech

/**
 * 語音輸出（TTS）引擎介面。
 *
 * 解耦底層 TTS 實作，讓業務層不依賴 Android TextToSpeech 或第三方 SDK。
 * 可替換為：Android 內建 TTS、Google Cloud TTS、Azure TTS 等。
 */
interface SpeechOutputEngine {

    /** 引擎是否已初始化完成 */
    val isReady: Boolean

    /** 是否正在朗讀 */
    val isSpeaking: Boolean

    /**
     * 朗讀文字。
     * @param text 要朗讀的文字
     * @param locale 語系代碼，例如 "zh-TW", "en-US"；null 則自動偵測
     * @param speed 語速倍率，1.0f = 正常
     * @param onDone 朗讀完畢回呼（可為 null）
     */
    fun speak(
        text: String,
        locale: String? = null,
        speed: Float = 1.0f,
        onDone: (() -> Unit)? = null
    )

    /** 停止朗讀 */
    fun stop()

    /** 釋放資源 */
    fun destroy()
}
