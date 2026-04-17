package com.snowrealm.yukiboard.app.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Sprint 7-1：network entry / OkHttp builder 單一 owner。
 *
 * 在 S7-0 audit 中列出 26 個 [OkHttpClient.Builder] 直接呼叫點，分布於 auth / data / provider /
 * speech / weather / gif / sticker / security / langpack / UI / IME 各層，彼此 timeout 與 header
 * 策略分散。本 owner 先收斂 timeout policy，後續 sprint 會再處理 header / cache / interceptor
 * 共享。
 *
 * 使用方式：
 * ```
 * private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STANDARD).build()
 * ```
 *
 * 若 caller 仍需微調特定 timeout，直接在回傳的 [OkHttpClient.Builder] 上呼叫
 * `.connectTimeout(...)` / `.readTimeout(...)` / `.writeTimeout(...)` 覆蓋即可。
 *
 * 本輪刻意不做：
 * - 共享 OkHttpClient instance（仍各 caller 自行 `.build()`，保留 interceptor/cookieJar 彈性）
 * - 共享 ConnectionPool / Dispatcher
 * - 注入 auth header / Platform-Key（仍由各 caller 自行加）
 *
 * 以上留給後續 `S7-2` / `S7-3` / 未來 sprint 處理。
 */
object AppHttpClient {

    /**
     * 常見 timeout profile。callsite 依使用場景挑 base，必要時再 override 特定欄位。
     *
     * 命名對應的現況：
     * - [SHORT]：快速 REST（CloudSync、天氣、Giphy、字典/學習 panel、本地測試）
     * - [STANDARD]：一般 REST（auth、Supabase、sticker、image commit）
     * - [DEFAULT]：標準 gateway（Insight API、Groq、langpack、政府公開資料、雲端 TTS）
     * - [STREAMING]：LLM streaming（OpenAI、Anthropic、Gemini、Whisper、fraud model）
     * - [STREAMING_LONG]：Insight streaming proxy（SnowRealmProvider）
     */
    enum class Profile(
        internal val connectSec: Long,
        internal val readSec: Long,
        internal val writeSec: Long,
    ) {
        SHORT(10, 10, 10),
        STANDARD(10, 15, 15),
        DEFAULT(15, 30, 15),
        STREAMING(30, 60, 30),
        STREAMING_LONG(30, 90, 30),
    }

    /**
     * 建立預先套用 [profile] timeout 的 [OkHttpClient.Builder]。
     * caller 可繼續加 interceptor / cookieJar / cache 或 override 單一 timeout。
     */
    fun newBuilder(profile: Profile): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(profile.connectSec, TimeUnit.SECONDS)
            .readTimeout(profile.readSec, TimeUnit.SECONDS)
            .writeTimeout(profile.writeSec, TimeUnit.SECONDS)
}
