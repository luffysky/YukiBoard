# YukiBoard 語音改善任務包 v2

> 根據 v8 zip 原始碼分析，找到三個根本問題

---

## 🔴 Fix 1：靜音偵測閾值太低（最影響體感）

### 根本原因

`WhisperSpeechEngine` 的 `silenceAmplitudeThreshold = 500`。
手機背景噪音（冷氣、環境聲）的 PCM 振幅通常在 300~800，
導致靜音偵測幾乎永遠不觸發，錄音一直累積靜音。
Whisper 收到含大量靜音的音訊 → 辨識變差或亂出字。

### `speech/WhisperSpeechEngine.kt`

```kotlin
// 原本：
private val silenceAmplitudeThreshold = 500

// 改為：
private val silenceAmplitudeThreshold = 1200  // 提高到 1200，過濾背景噪音
```

同時讓 `silenceThresholdMs` 短一點，說完話更快停止：

```kotlin
// 原本：
private val silenceThresholdMs = 3000L

// 改為：
private val silenceThresholdMs = 2000L  // 2 秒靜音就停，不要等 3 秒
```

### `speech/FasterWhisperSpeechEngine.kt`

找到對應的常數（名稱可能是 `SILENCE_AMPLITUDE_THRESHOLD`、`SILENCE_THRESHOLD_MS`），同樣改為：

```kotlin
private const val SILENCE_AMPLITUDE_THRESHOLD = 1200
private const val SILENCE_THRESHOLD_MS = 2000L
```

> **調校說明：** 1200 是一個保守的起點。如果在安靜環境下測試，可以試 800~1000；嘈雜環境可以到 1500~2000。最終可以讓使用者在設定頁調整靈敏度。

---

## 🟡 Fix 2：whisperLocale 截斷邏輯簡化

### 根本原因

```kotlin
val whisperLocale = locale.substringBefore("-")  // "zh-TW" → "zh"
```

這行現在碰巧沒壞，但 `transcribe()` 的 `when` 判斷依賴這個轉換，
邏輯很脆弱。如果未來傳入不標準的 locale 就會出問題。

### `speech/WhisperSpeechEngine.kt`

把 `whisperLocale` 的計算和 `transcribe()` 的 `when` 統一：

```kotlin
// 原本：
val whisperLocale = locale.substringBefore("-")

// 改為：
val whisperLocale = locale  // 直接傳原始 locale，讓 transcribe 自己處理
```

`transcribe()` 的 `when` 已經包含 `language.startsWith("zh-")` 的判斷，
傳 `"zh-TW"` 反而比傳 `"zh"` 更精確。

---

## 🟡 Fix 3：VoicePostProcessor prompt 加 few-shot 範例

### 根本原因

LLM 看到抽象規則不一定可靠，需要具體範例（few-shot）才能正確處理改口情況。

### `speech/VoicePostProcessor.kt` — 中文 prompt

```kotlin
language.startsWith("zh") -> """
你是語音辨識後處理助手。只輸出處理後文字，不要任何解釋。

規則：
1. 移除填充詞（嗯、那個、就是、然後、對對對、好好好、我覺得說）
2. 移除明顯重複（「我我我覺得」→「我覺得」）
3. 改口修正：若說話者用「不是」「更正」「我是說」「啊不對」修正了自己，只保留最後版本
4. 統一繁體中文（不要簡體）
5. 專有名詞不得修改（Apple、iPhone、LINE、ChatGPT、SnowRealm、YouTube）
6. 保持原有語氣，不要擴寫或腦補

範例：
輸入：我我覺得這個 really 好用
輸出：我覺得這個 really 好用

輸入：明天去台中...不是 台南
輸出：明天去台南

輸入：嗯然後就是我想要訂一個...那個...咖啡廳
輸出：我想要訂一個咖啡廳

輸入：請用 LINE 傳給我
輸出：請用 LINE 傳給我

原始文字：$raw""".trimIndent()
```

### 英文 prompt

```kotlin
else -> """
You are a voice transcription cleaner. Output only the cleaned text, no explanations.

Rules:
1. Remove filler words (um, uh, like, you know, basically, I mean, so, right)
2. Remove repetitions (keep last occurrence)
3. If speaker corrects themselves with "no wait", "I mean", "actually", keep only the corrected version
4. Keep proper nouns unchanged (Apple, LINE, ChatGPT, SnowRealm)
5. Keep original tone, don't expand or add content

Examples:
Input: um I I think this is like really useful
Output: I think this is really useful

Input: let's meet at the coffee shop no wait the restaurant
Output: let's meet at the restaurant

Input: send it via LINE
Output: send it via LINE

Raw: $raw""".trimIndent()
```

### 多語（blank language）prompt 同樣加範例

```kotlin
language.isBlank() -> """
你是語音辨識後處理助手。只輸出處理後文字，不要任何解釋。

規則：
1. 移除各語言填充詞（中：嗯/那個/就是，英：um/uh/like，日：えーと/あの，韓：음/그냥）
2. 改口修正：保留最後版本
3. 中文統一繁體，其他語言保持原文
4. 專有名詞不得修改（Apple、LINE、ChatGPT、SnowRealm）
5. 保持語言混用的順序

範例：
輸入：我覺得這個 feature 很好用 like really 好用
輸出：我覺得這個 feature 很好用

原始文字：$raw""".trimIndent()
```

---

## 🟢 Fix 4：設定頁加靈敏度調整（選填）

讓使用者可以根據環境調整靜音偵測靈敏度。

### `ui/settings/YukiPreferences.kt`

```kotlin
var voiceSilenceSensitivity: Int by IntPref("voice_silence_sensitivity", 1)
// 0 = 高靈敏（安靜環境，閾值 800）
// 1 = 標準（預設，閾值 1200）
// 2 = 低靈敏（嘈雜環境，閾值 2000）
```

### `speech/WhisperSpeechEngine.kt`

讓閾值可以從外部設定：

```kotlin
var silenceAmplitudeThreshold: Int = 1200  // 改為 var，可從外部調整
```

### `service/YukiBoardIMEService.kt`

在建立/初始化 WhisperSpeechEngine 後設定：

```kotlin
val threshold = when (prefs.voiceSilenceSensitivity) {
    0 -> 800
    2 -> 2000
    else -> 1200
}
(speechEngine as? WhisperSpeechEngine)?.silenceAmplitudeThreshold = threshold
(speechEngine as? FasterWhisperSpeechEngine)?.also {
    // FasterWhisper 同樣設定
}
```

### `ui/settings/SettingsActivity.kt` — 語音設定區

```kotlin
radioRow(
    "靜音偵測靈敏度",
    listOf(
        0 to "高（安靜環境）",
        1 to "標準（預設）",
        2 to "低（嘈雜環境）"
    ).map { it.first.toString() to it.second },
    prefs.voiceSilenceSensitivity.toString()
) { prefs.voiceSilenceSensitivity = it.toInt() }
```

---

## 執行順序

```
Fix 1  WhisperSpeechEngine silenceAmplitudeThreshold → 1200
       WhisperSpeechEngine silenceThresholdMs → 2000L
       FasterWhisperSpeechEngine 同樣設定
  → Build 測試：說完一句話，2 秒內停止錄音

Fix 2  whisperLocale 不再截斷，直接傳原始 locale

Fix 3  VoicePostProcessor 三個 prompt 都加 few-shot 範例

Fix 4  YukiPreferences 加 voiceSilenceSensitivity
       WhisperSpeechEngine.silenceAmplitudeThreshold 改為 var
       IMEService 根據設定調整閾值
       SettingsActivity 加靈敏度選項
```

---

## 驗證

```
Fix 1：
□ 說一句話停下來，2 秒內自動停止錄音（不要等 5 秒）
□ 說話中間短暫停頓不被截斷
□ 背景噪音環境下也能辨識

Fix 2：
□ 中文模式辨識繁體中文正確
□ 英文模式辨識英文正確

Fix 3：
□ 「嗯然後就是我要訂咖啡廳」→ 「我要訂咖啡廳」
□ 「明天去台中...不是台南」→ 「明天去台南」
□ 「LINE」「iPhone」等專有名詞不被改掉
□ 中英混說的句子語言不被改變

Fix 4：
□ 設定頁有靈敏度選項
□ 切換到「低靈敏」，嘈雜環境下不會一直截斷
```
