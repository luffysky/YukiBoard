package com.snowrealm.yukiboard.language

/**
 * 語言包介面。
 *
 * 定義每個語言包提供的基本能力，讓 [com.snowrealm.yukiboard.input.router.InputRouter]
 * 透過介面操作，不依賴具體語言實作。
 */
interface LanguagePack {

    /** BCP 47 語言標籤，例如 "en-US", "zh-TW" */
    val localeTag: String

    /** 使用者友善顯示名稱 */
    val displayName: String

    /** 對應的輸入法模式識別碼 */
    val inputMode: com.snowrealm.yukiboard.input.router.InputMode

    /** 鍵盤按鍵列定義（由各語言包決定按鍵排列） */
    val keyRows: List<List<com.snowrealm.yukiboard.ui.keyboard.KeyModel>>

    /** 常用標點符號清單（顯示在符號面板） */
    val punctuations: List<String>

    /** 語言包是否支援候選詞 */
    val supportsCandidates: Boolean
}
