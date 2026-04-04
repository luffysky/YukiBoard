# YukiBoard Cursor 總任務包

> 整合本次對話所有討論、決策、新增功能、Bug 修復、智慧候選字升級。
> 給 Claude Code 讀取，請依照批次順序執行。
> 每完成一個子項目打勾，完成整個批次後告訴我再繼續。
> 最後更新：2026-04-04

---

## 執行原則

- 所有功能都受 `YukiPreferences` 開關控制，執行前先 check
- 避免 `!!` 強制非空，改用 `?.let` 或 `?: return`
- `CoroutineScope` 一律綁定生命週期或在適當時機 cancel
- 每個批次做完 build 驗證，確保 `BUILD SUCCESSFUL`

---

## 第一批 — Bug 修復（立刻修，影響主要功能）

### BUG-1 注音輸入卡頓
**檔案：** `ChewingInputEngine.kt`、`YukiBoardIMEService.kt`

**原因：** `prefixCache`、`reverseIndex`、`nextCharIndex` 三個 lazy 索引第一次按注音鍵才觸發，在主執行緒跑，凍結 UI 1-3 秒。

**修復：**
在 `ChewingInputEngine.kt` 加入：
```kotlin
fun warmUp() {
    prefixCache
    reverseIndex
    nextCharIndex
}
```
在 `YukiBoardIMEService.kt` 的 `onCreateInputView()` 加入：
```kotlin
serviceScope.launch(Dispatchers.IO) {
    (inputRouter.getChewingEngine() as? ChewingInputEngine)?.warmUp()
}
```

### BUG-2 設定頁面無限跳掉
**檔案：** `SettingsActivity.kt`，`buildLanguageCard()`

**原因：** RadioGroup 初始化時 `isChecked = true` 觸發 `setOnCheckedChangeListener`，呼叫 `recreate()` 無限重建。

**修復：** 加 `isInitializing` flag：
```kotlin
var isInitializing = true
// ... 加入所有 RadioButton ...
isInitializing = false

group.setOnCheckedChangeListener { _, checkedId ->
    if (isInitializing) return@setOnCheckedChangeListener
    // ... 切換語言邏輯 + recreate()
}
```

### BUG-3 Anthropic Streaming 收不到回應
**檔案：** `AnthropicProvider.kt`

**原因：** SSE 解析邏輯中多餘的外層 `deltaType == "content_block_delta"` 判斷永遠不成立。

**修復：** 移除外層 type 判斷，直接取 `delta.type == "text_delta"` 的文字：
```kotlin
line.startsWith("data: ") && currentEvent == "content_block_delta" -> {
    val data = line.removePrefix("data: ").trim()
    try {
        val jsonObject = json.parseToJsonElement(data).jsonObject
        val delta = jsonObject["delta"]?.jsonObject
        if (delta != null) {
            val textDeltaType = delta["type"]?.jsonPrimitive?.content
            if (textDeltaType == "text_delta") {
                val text = delta["text"]?.jsonPrimitive?.content
                if (!text.isNullOrEmpty()) emit(text)
            }
        }
    } catch (_: Exception) { }
}
```

### BUG-4 savedBg!! crash
**檔案：** `YukiBoardIMEService.kt` 第 266 行

**修復：**
```kotlin
savedBg?.let { bg ->
    if (isVideoFile(bg)) bgView.setVideoBackground(bg)
    else bgView.setImageBackground(bg)
}
```

### BUG-5 到 BUG-8 !! 強制非空
- `InputRouter.kt` 第 247 行：`scored[key]!!` → `(scored[key] ?: 0f)`
- `SmartCorrectionEngine.kt` 第 114 行：同上
- `TranslateEngine.kt` 第 92 行：`currentTranslator!!` → `currentTranslator ?: createTranslator(from, to)`
- `MemoryFlipGame.kt` 第 75-76 行：`firstFlip!!` / `secondFlip!!` → `val flip1 = firstFlip ?: return`

### BUG-9 CoroutineScope 記憶體洩漏
**檔案：** `LoginActivity.kt`、`SettingsActivity.kt`、`KeyboardBackgroundView.kt`

**修復：**
- `LoginActivity.kt`、`SettingsActivity.kt`：`CoroutineScope(Dispatchers.Main).launch` → `lifecycleScope.launch`
- `KeyboardBackgroundView.kt`：加入 class 層級 scope，在 `onDetachedFromWindow()` cancel：
```kotlin
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    scope.cancel()
}
```

### BUG-10 StickerFloatingPanel 分頁篩選未接
**檔案：** `StickerFloatingPanel.kt` 第 109 行 TODO

在 `StickerPanelView.kt` 加入 `filterByCategory(category: String?)` 方法，在 Tab 點擊事件中呼叫。

### BUG-12/13 VrmCharacterView / LoginActivity !! 修復
- `VrmCharacterView.kt`：`renderer!!` → `val r = renderer ?: return`；`camera!!.entity` → `camera?.entity ?: return`
- `LoginActivity.kt`：`pendingCodeId!!` → `val codeId = pendingCodeId ?: run { showError("請先完成邀請碼驗證"); return@launch }`

---

## 第二批 — 全功能開關設定系統

**原則：本次對話決定，所有功能包括 Claude Code 後續做的功能，都要有開關可以關。**

### 更新 YukiPreferences.kt

加入所有開關（BoolPref 委派，SharedPreferences 讀寫）：

```kotlin
// ── AI 功能 ──────────────────────────────────────────
var aiEnabled: Boolean by BoolPref("ai_enabled", true)
var aiPrediction: Boolean by BoolPref("ai_prediction", true)
var aiWriter: Boolean by BoolPref("ai_writer", true)
var aiTranslate: Boolean by BoolPref("ai_translate", true)
var aiCupid: Boolean by BoolPref("ai_cupid", true)
var aiWhisper: Boolean by BoolPref("ai_whisper", true)
var aiGrammarCheck: Boolean by BoolPref("ai_grammar_check", true)
var aiToneAdjust: Boolean by BoolPref("ai_tone_adjust", true)
var aiSummarize: Boolean by BoolPref("ai_summarize", true)
var aiMemeText: Boolean by BoolPref("ai_meme_text", true)
var aiEmojiTranslate: Boolean by BoolPref("ai_emoji_translate", true)
var aiQuickReply: Boolean by BoolPref("ai_quick_reply", true)
var aiCodeComplete: Boolean by BoolPref("ai_code_complete", true)
var aiRegexGen: Boolean by BoolPref("ai_regex_gen", true)
var aiCommitMessage: Boolean by BoolPref("ai_commit_message", true)
var aiYukiChat: Boolean by BoolPref("ai_yuki_chat", true)
var aiMoodDiary: Boolean by BoolPref("ai_mood_diary", true)
var aiWeather: Boolean by BoolPref("ai_weather", true)

// ── 雪凜行為 ─────────────────────────────────────────
var yukiSarcasticComments: Boolean by BoolPref("yuki_sarcastic", true)
var yukiEmotionalCooldown: Boolean by BoolPref("yuki_cooldown", true)
var yukiMoodDiaryPassive: Boolean by BoolPref("yuki_mood_passive", true)
var yukiWeatherAlert: Boolean by BoolPref("yuki_weather_alert", true)
var yukiLateNightComment: Boolean by BoolPref("yuki_late_night", true)

// ── 感測器 ───────────────────────────────────────────
var shakeToUndoEnabled: Boolean by BoolPref("shake_undo", true)
var gyroscopeCursorEnabled: Boolean by BoolPref("gyro_cursor", true)
var appAutoDetectEnabled: Boolean by BoolPref("app_mode_detect", true)

// ── 動畫特效 ─────────────────────────────────────────
var flowLightEnabled: Boolean by BoolPref("flow_light", true)
var characterAnimEnabled: Boolean by BoolPref("character_anim", true)
var keyParticleEffect: Boolean by BoolPref("key_particle", true)
var keyPressVibration: Boolean by BoolPref("key_vibration", true)
var keySoundEnabled: Boolean by BoolPref("key_sound", false)

// ── 遊戲 ─────────────────────────────────────────────
var gamesEnabled: Boolean by BoolPref("games_enabled", true)
var gameBackgroundMusic: Boolean by BoolPref("game_bgm", false)
var gameSoundEffects: Boolean by BoolPref("game_sfx", true)

// ── 懸浮面板 ─────────────────────────────────────────
var floatingPanelEnabled: Boolean by BoolPref("floating_panel", true)
var weatherPanelEnabled: Boolean by BoolPref("weather_panel", true)
var stickerPanelEnabled: Boolean by BoolPref("sticker_panel", true)

// ── 輸入行為 ─────────────────────────────────────────
var swipeInput: Boolean by BoolPref("swipe_input", true)
var autoCorrect: Boolean by BoolPref("auto_correct", true)
var snippetEnabled: Boolean by BoolPref("snippet_expansion", true)
var secretMessageMode: Boolean by BoolPref("secret_message", true)
var toneDetection: Boolean by BoolPref("tone_detection", true)
var remoteKeyboardEnabled: Boolean by BoolPref("remote_keyboard", true)
var multiplayerEnabled: Boolean by BoolPref("multiplayer", true)

private inner class BoolPref(val key: String, val default: Boolean) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) =
        prefs.getBoolean(key, default)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()
}
```

### 重構 SettingsActivity.kt

六個群組 CardView 佈局：
1. **AI 功能** — 主開關 + 18 個子項目（主開關關閉子項目灰化）
2. **雪凜行為** — 微吐槽 / 情緒冷靜期 / 心情日記 / 天氣提醒 / 深夜關心
3. **感測器** — 搖手機復原 / 陀螺儀游標 / App 自動偵測
4. **動畫特效** — 流光 / 角色動畫 / 粒子效果 / 震動 / 音效
5. **遊戲** — 總開關 / 背景音樂 / 音效
6. **輸入行為** — 滑動 / 自動更正 / Snippet / 私房話 / 語氣偵測 / 懸浮面板 / 遠端鍵盤

主開關關閉時：`childViews.forEach { it.isEnabled = false; it.alpha = 0.4f }`

---

## 第三批 — 懸浮面板基礎架構

新增目錄：`app/src/main/java/com/snowrealm/yukiboard/ui/floating/`

### FloatingPanelConfig.kt
```kotlin
data class FloatingPanelConfig(
    val minWidthDp: Int = 200,
    val minHeightDp: Int = 120,
    val maxWidthDp: Int = -1,     // -1 = screenWidth / 2
    val maxHeightDp: Int = -1,    // -1 = screenHeight / 2
    val defaultWidthDp: Int = 280,
    val defaultHeightDp: Int = 200,
    val snapToEdge: Boolean = true,
    val snapThresholdDp: Int = 40
)
```

### FloatingPanel.kt（抽象基礎類別）
- show() / hide() / dismiss() / moveTo(x, y) / resize(w, h) / snapToNearestEdge()
- 標題列：[雪凜小圖] [標題] [─最小化] [×關閉]
- 右下角手柄 ◢ 24×24dp
- onShown() / onHidden() / onDismissed() 生命週期 hook

### FloatingPanelManager.kt
```kotlin
companion object {
    const val PANEL_WEATHER = "weather"
    const val PANEL_GAME = "game"
    const val PANEL_CLAW_RESULT = "claw_result"
    const val PANEL_STICKER = "sticker"
}
```
- showPanel / hidePanel / togglePanel / dismissAll
- 顯示前檢查對應 prefs 開關

### gesture/ 子目錄
- `DragGestureHandler.kt` — 標題列拖曳，更新 WindowManager.LayoutParams
- `PinchGestureHandler.kt` — 雙指縮放，套用 min/max 限制
- `ResizeHandle.kt` — 右下角 ◢ 手柄拖曳

靠邊吸附：40dp 閾值，200ms 動畫

### AndroidManifest.xml 新增
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

---

## 第四批 — 懸浮面板群

### B3 天氣懸浮面板

新增：`weather/WeatherData.kt`、`weather/WeatherManager.kt`、`ui/floating/WeatherFloatingPanel.kt`

- OpenWeatherMap API，30 分鐘快取
- 面板顯示：位置 + 溫濕度風速 + 6 小時預報橫向滾動 + 雪凜短句
- 雪凜天氣短句（本地邏輯，0 token）
- 穿衣建議（邀請碼解鎖，顯示 🔒）
- build.gradle 加 play-services-location
- `.env` 加 `OPENWEATHER_API_KEY`

雪凜天氣短句範例邏輯：
```kotlin
fun getYukiComment(weather: WeatherData): String = when {
    weather.rainProbability > 70 -> "今天要帶傘喦！不然頭髮會濕掉 😤"
    weather.temperature < 10    -> "超冷！出門記得穿厚一點 🥶"
    weather.temperature > 35    -> "熱死了啦，多喝水 🥵"
    weather.condition == SUNNY  -> "今天天氣超好！出去走走嗎？✨"
    else                         -> "普通的一天，加油！⛅"
}
```

### B4 Claw 結果懸浮面板

新增：`ui/floating/ClawResultFloatingPanel.kt`

- `showResult(skillName, results: List<String>)` 外部呼叫
- Streaming typewriter 效果
- ◀ 1/3 ▶ 版本切換
- [複製] / [✓ 插入輸入框] 按鈕（`InputConnection.commitText()`）

### B6 貼圖懸浮面板

新增：`ui/floating/StickerFloatingPanel.kt`（繼承 FloatingPanel，複用 StickerPanelView）

- 分頁：免費 / 角色 / 節日 / 全部
- 4 欄網格 + 稀有度角標（N灰 / R藍 / SR紫 / SSR金）
- 受 `prefs.stickerPanelEnabled` 控制

---

## 第五批 — 雪凜人格系統

### B7 雪凜微吐槽系統

新增：`yuki/YukiCommentEngine.kt`

觸發事件與範例短句：
```
同一個字打錯 3 次：「這個字你今天打錯三次了喔 🙄」/「要不要我教你怎麼拼？」
打了一長段又全刪：「那段我有看到 👀」/「剛才打的我覺得還不錯啊⋯」
打字速度突然變慢：「⋯⋯你還在嗎？」/「需要我幫你想嗎？」
深夜打字 00:00-05:00：「這麼晚了還在傳訊息⋯」/「早點睡喔」
遊戲輸了：「輸掉了呢，再來一次？」/「這局發揮有點失常喔 🙄」
```

規則：
- 10 分鐘冷卻
- 每個事件 3+ 條隨機選一
- 說話泡泡顯示 2-3 秒後淡出，不遮擋輸入框
- 受 `prefs.yukiSarcasticComments` 控制

### B8 情緒冷靜期

新增：`yuki/EmotionalCooldownManager.kt`

- 觸發詞庫（中文 + 英文激烈用語）
- 觸發流程：雪凜結冰動畫（`yuki_freeze.json`）→ 鍵盤鎖定 1 秒 → 確認對話框
- 對話框：「等一下。這句話確定要發嗎？」→「算了不發」/「還是發吧」
- 受 `prefs.yukiEmotionalCooldown` 控制

### B14 私房話模式 + 語氣偵測

新增：
- `claw/secret/SecretMessageManager.kt` — AES-256-GCM 加密，格式 `[YB🔐] {base64}`，受 `prefs.secretMessageMode` 控制
- `claw/mood/ToneDetector.kt` — 本地關鍵字情緒分析（0 token），NEGATIVE/SAD 時雪凜回應，受 `prefs.toneDetection` 控制

---

## 第六批 — AI Skill 全面升級

### B10 12 個新 Skill

**Tier 1 — 高頻剛需**
- `GrammarCheckSkill.kt` — 文法糾錯（200 token，`prefs.aiGrammarCheck`）
- `ToneAdjustSkill.kt` — 語氣調整 正式/輕鬆/可愛/專業（300 token，`prefs.aiToneAdjust`）
- `SummarizeSkill.kt` — 摘要精簡（300 token，`prefs.aiSummarize`）

**Tier 2 — 社群差異化**
- `MemeTextSkill.kt` — 迷因/流行語（200 token，`prefs.aiMemeText`）
- `EmojiTranslateSkill.kt` — 文字 ↔ Emoji（100 token，`prefs.aiEmojiTranslate`）
- `QuickReplySkill.kt` — 整句回覆建議（400 token，`prefs.aiQuickReply`）

**Tier 3 — 開發者模式限定**
- `CodeCompleteSkill.kt` — 程式碼補全（500 token，`prefs.aiCodeComplete`）
- `RegexGenSkill.kt` — 正則產生器（300 token，`prefs.aiRegexGen`）
- `CommitMessageSkill.kt` — Commit 訊息（400 token，`prefs.aiCommitMessage`）

**Tier 4 — 角色深度整合**
- `YukiChatSkill.kt` — 雪凜聊天，善意毒舌 LINE 短句風格（600 token，`prefs.aiYukiChat`）
- `MoodDiarySkill.kt` — 心情日記被動偵測（0-200 token，`prefs.aiMoodDiary`）
- `WeatherSkill.kt` — 天氣助理（0-300 token，`prefs.aiWeather`）

所有 Skill 執行前先 check 對應 prefs 開關。

### B11 Prompt 模板補完

新增到 `app/src/main/res/raw/`：
- `claw_grammar_prompt.txt` — 只回傳修正後的文字
- `claw_meme_prompt.txt` — 台灣網路梗，3 個用 `---` 分隔
- `claw_yuki_chat_prompt.txt` — 善意毒舌、1-2 句 LINE 短句風格
- `claw_weather_outfit_prompt.txt` — 穿衣建議，100 字以內
- `claw_random_challenge_prompt.txt` — 挑戰出題，不超過 20 字
- `claw_yuki_rating_prompt.txt` — 挑戰評分，不超過 30 字

### B12 ClawToolbar 更新

更新 `ui/panel/ClawToolbarView.kt`：
- 新增 🌤 天氣按鈕（受 `prefs.weatherPanelEnabled`）
- 新增 🎮 遊戲按鈕（受 `prefs.gamesEnabled`）
- Token 顯示右側：> 500 綠色 / > 0 橙色 / = 0 紅色 🔒
- 根據 KeyboardMode 動態顯示 Skill，過濾已關閉項目

### B13 RadialModeMenu 加入 GAME 模式

更新 `ui/keyboard/RadialModeMenu.kt`：
- 三選項（120度）→ 四選項（90度）
- 新增 `ModeOption(KeyboardMode.GAMING, "遊戲", "🎮")`

---

## 第七批 — AI Provider 擴充

### 新增 Provider

**GeminiProvider.kt**
- 免費方案：Gemini 2.0 Flash，1500 次/天，不需信用卡
- 申請：aistudio.google.com
- Base URL：`https://generativelanguage.googleapis.com/v1beta`

**GroqProvider.kt**
- 免費方案：每天有限額，速度最快，OpenAI 相容格式
- 申請：console.groq.com
- Base URL：`https://api.groq.com/openai/v1`

**GeminiNanoProvider.kt**
- Android 14+ 本地離線，完全免費，不需 API Key
- API：`android.ai.client.generativeai`（AICore）
- 不支援的裝置顯示灰色「需要 Android 14+」

### 更新 AIProviderType enum
```kotlin
enum class AIProviderType {
    OPENAI, ANTHROPIC,
    GEMINI,       // 新增，免費首選
    GROQ,         // 新增，速度最快
    GEMINI_NANO,  // 新增，本地離線
    SNOWREALM
}
```

### 首次使用預設邏輯
```
Android 14+ 且支援 Nano → 預設 Nano（完全免費離線）
其他 → 預設 Gemini API（免費額度）
用戶可自行切換
```

### App 內建 API 申請教學

新增 `ui/settings/ApiGuideActivity.kt`，五個平台分頁：

| 平台 | 網址 | 免費 | 需信用卡 | 雪凜推薦語 |
|------|------|------|---------|-----------|
| Gemini | aistudio.google.com | ✅ 1500次/天 | ❌ | 用 Google 帳號就能申請，完全免費，我最推這個！ |
| Groq | console.groq.com | ✅ 每日限額 | ❌ | 速度超快，補字幾乎零延遲，強力推薦！ |
| OpenAI | platform.openai.com | 新帳號 $5 | ✅ | 品質很穩定，適合重度使用者。 |
| Anthropic | console.anthropic.com | ❌ | ✅ | 理解中文超強，寫作潤稿效果很好。 |
| Gemini Nano | 不需申請 | ✅ 無限 | ❌ | 完全免費、不需網路、速度快。 |

每個平台：步驟說明 + 「我複製好了，帶我去填入 →」按鈕直接帶回 AISettingsActivity 對應欄位。

---

## 第八批 — 遊戲懸浮面板 + 13 款新遊戲

### GameFloatingPanel.kt
- 預設 320×260dp
- 分類 Tab：經典 / 動作 / 益智
- 面板顯示時鍵盤保持原本大小，鍵盤同時作為控制器

### 新增遊戲（13 款）

| 檔名 | 遊戲 | 核心機制 |
|------|------|---------|
| `WordGuardianGame.kt` | 詞彙守護者 🏰 | 塔防 + 打字，打字=砲彈擊退敵人 |
| `KeyboardTravelerGame.kt` | 鍵盤旅行者 🏃 | 招財跑酷，打字速度=移動速度，打字停頓就坐下吃便當 |
| `MoodMinerGame.kt` | 情緒採礦 💎 | 打字情緒生成原石，Swipe 收集，每週情緒週報 |
| `BulletHellMiniGame.kt` | 極簡彈幕 ✈️ | Swipe 控制飛機閃避，15-30 秒局，打字時自動暫停 |
| `IceCrystalGame.kt` | 冰晶收集 ❄️ | 輸入完整長句掉落冰晶，Swipe 收集 |
| `WarshipCruiseGame.kt` | 戰艦巡航 🚢 | 打字速度=戰艦動力，停止打字就漂流 |
| `SpellingDuelGame.kt` | 拼字對決 🔤 | 給 8 個字母拼最長英文單字 |
| `KeyboardMinesweeperGame.kt` | 按鍵掃雷 🎯 | 鍵盤版掃雷，三種難度 |
| `MemoryFlipGame.kt` | 記憶翻牌 🃏 | 按鍵背面配對 emoji，計時 |
| `YukiQuizGame.kt` | 雪凜問答 🌸 | 雪凜出題，按鍵作答 |
| `CoupleQuizGame.kt` | 情侶測驗 💘 | 毒舌情侶題，結果截圖分享 |
| `TypingRaceGame.kt` | 打字競速 🏆 | v1.0 單機版（v2.0 連線配對為未來版本） |
| `RandomChallengeGame.kt` | 隨機挑戰 🎲 | 雪凜 AI 出奇怪任務，AI 評分 |

---

## 第九批 — 智慧候選字升級

### 升級 1 — 注音自適應學習

更新 `ChewingInputEngine.kt`，加入 `ChewingAdaptiveDict`（內部 class 或獨立檔案）：
- SharedPreferences 儲存：注音 → 用戶選字頻率 Map
- `selectCandidateAt()` 選字後記錄頻率
- `lookupCandidates()` 用頻率重新排序（選過的字排前面）

### 升級 2 — 中文上下文串聯預測

更新 `ChewingInputEngine.kt`：
- 維護 `chineseNgramIndex`：上一個字 → 最常出現的下一個字
- 每次選字後更新 ngramIndex
- composing 為空時，在候選列前端顯示上下文預測

更新 `InputRouter.kt`：
- 非英文模式 composing 為空時，呼叫 `getContextualNextCandidates(lastChar)` 加到候選列最前端

### 升級 3 — 讀取輸入框上下文

更新 `YukiBoardIMEService.kt`：
```kotlin
fun getTextBeforeCursor(maxChars: Int = 50): String =
    currentInputConnection?.getTextBeforeCursor(maxChars, 0)?.toString() ?: ""
```

更新 `InputRouter.kt`：
```kotlin
var currentContextText: String = ""
// 在 updateCandidates() 的 composing 為空分支用 currentContextText 最後一個詞
```

### 升級 4 — 候選字視覺分類標示

更新 `CandidateManager.kt`：
```kotlin
data class Candidate(
    val text: String,
    val type: CandidateType = CandidateType.DICTIONARY,
    val score: Float = 0f
)

enum class CandidateType {
    DICTIONARY,   // 一般字典
    ADAPTIVE,     // 自適應（用戶選過）→ 淡藍色文字 #A0D8FF
    PREDICTION,   // 上下文預測 → 左側小點 ·
    CORRECTION,   // 糾錯建議 → 橙色文字
    AI            // AI 補字 → 前綴 ✨
}
```

更新 `KeyboardView.kt` 候選字渲染：微小視覺提示，不讓候選列看起來太亂。

---

## 第十批 — 語境預測升級 + 其他

### B9 NextActionPredictor 升級

更新 `input/predict/NextActionPredictor.kt`：

加入本地 `intentPatterns`（0 token）：
```kotlin
private val intentPatterns = mapOf(
    "明天要不要" to listOf("吃飯？", "出去玩？", "開會？"),
    "我覺得" to listOf("還不錯", "有點奇怪", "超棒的"),
    "可以幫我" to listOf("看一下嗎？", "傳給他嗎？")
)
```

AI 意圖預測（邀請碼解鎖，接 ClawClient）

### A4 WhisperSkill 實作

更新 `claw/skills/WhisperSkill.kt`（目前為空實作）：
- 音頻錄製（MediaRecorder / AudioRecord）
- OpenAI Whisper API 串接（multipart/form-data 上傳）
- 錄音 UI 整合到語音面板

### A7 邀請碼系統補完

- `InviteCodeActivity.kt` — UI 美化 + 驗證流程完整化
- `generate_invite_codes.py` — 產碼腳本（STANDARD/KOL/PERMANENT 三種類型）
- `TokenBudgetManager.kt` — 每日重置邏輯（跨日自動歸零 + 重置時間記錄）

---

## 第十一批 — API 串接 + 環境

### A1 GIF 引擎接 Giphy API
- `GiphyApiClient.kt`（trending + search）
- API Key 從 `BuildConfig.GIPHY_API_KEY` 讀取
- `GifPanelView.kt` 串接現有 UI

### A2 翻譯引擎接 MLKit
- MLKit Translation 依賴加入 `build.gradle`
- `TranslateEngine.kt` 實作（下載離線模型）
- `TranslatePanelView.kt` 串接（現有 7 語言 UI）

### A8 .env.example 範本檔案

建立 `.env.example`，值留空：
```
OPENAI_API_KEY=
ANTHROPIC_API_KEY=
GEMINI_API_KEY=
GROQ_API_KEY=
INVITE_HMAC_SECRET=
PLATFORM_API_KEY=
INSIGHT_API_BASE_URL=https://insight.snowrealm.pet
GIPHY_API_KEY=
OPENWEATHER_API_KEY=
YUKIBOARD_SUPABASE_URL=
YUKIBOARD_SUPABASE_ANON_KEY=
NEWEBPAY_MERCHANT_ID=
NEWEBPAY_HASH_KEY=
NEWEBPAY_HASH_IV=
```

加入 `CLAUDE.md` 說明各 Key 用途與申請網址。

---

## 第十二批 — Lottie 佔位動畫

缺少的 Lottie 動畫（需設計師出，或用 LottieFiles 素材應急）：
- `yuki_freeze.json` — 情緒冷靜期結冰特效
- `yuki_battle_idle.json` — 遊戲模式待機
- `yuki_cupid.json` — 情聖月老粉紅愛心

**處理方式：動畫不存在時 fallback 到 `yuki_happy.json`，不 crash。**

所有 Lottie 載入處加入 fallback 邏輯：
```kotlin
fun loadLottie(name: String): String {
    return try {
        context.assets.open("lottie/$name.json").bufferedReader().readText()
    } catch (e: Exception) {
        context.assets.open("lottie/yuki_happy.json").bufferedReader().readText()
    }
}
```

---

## 完整 Skill 清單（完成後共 19 個）

| # | ID | 名稱 | 狀態 | Token | 模式 | 設定開關 |
|---|---|---|:---:|:---:|---|---|
| 1 | ai_prediction | AI 補字 | ✅ | 100 | 全部 | aiPrediction |
| 2 | writer | 雪凜寫手 | ✅ | 800 | 全部 | aiWriter |
| 3 | translate | 即時翻譯 | ✅ | 300 | 全部 | aiTranslate |
| 4 | cupid | 情聖月老 | ✅ | 600 | 全部 | aiCupid |
| 5 | context | 情境感知 | ✅ | 0 | 全部 | — |
| 6 | animation_suggest | 角色動畫 | ✅ | 50 | 全部 | characterAnimEnabled |
| 7 | whisper | AI 語音 | ⬜ | 200 | 全部 | aiWhisper |
| 8 | grammar_check | 文法糾錯 | ⬜ | 200 | 全部 | aiGrammarCheck |
| 9 | tone_adjust | 語氣調整 | ⬜ | 300 | 全部 | aiToneAdjust |
| 10 | summarize | 摘要精簡 | ⬜ | 300 | 全部 | aiSummarize |
| 11 | meme_text | 迷因生成 | ⬜ | 200 | 全部 | aiMemeText |
| 12 | emoji_translate | Emoji 翻譯 | ⬜ | 100 | 全部 | aiEmojiTranslate |
| 13 | quick_reply | 快速回覆 | ⬜ | 400 | 全部 | aiQuickReply |
| 14 | code_complete | 程式碼補全 | ⬜ | 500 | 開發者 | aiCodeComplete |
| 15 | regex_gen | 正則產生器 | ⬜ | 300 | 開發者 | aiRegexGen |
| 16 | commit_message | Commit 訊息 | ⬜ | 400 | 開發者 | aiCommitMessage |
| 17 | yuki_chat | 雪凜聊天 | ⬜ | 600 | 全部 | aiYukiChat |
| 18 | mood_diary | 心情日記 | ⬜ | 0-200 | 全部 | aiMoodDiary |
| 19 | weather | 天氣助理 | ⬜ | 0-300 | 全部 | aiWeather |

---

## 未來版本（不在本次範圍）

- 打字競速 v2.0 連線配對（Supabase Realtime）
- 邀請碼 → Z幣購買 AI 用量
- SnowRealmCore 統一帳號登入
- 皮膚商城（Z幣付費）
- VRM 3D 角色升級
- AR 雪凜（ARCore v2.0）
- SnowRealmVR 輸入介面
- 情緒採礦週報接 Insight 分析
- 冰晶 / CoinBeast 跨 App 真實聯動
- 鍵盤旅行者距離接毛行天下
- 滑動輸入（Swipe）
- 單手模式
- 快速模板（常用句一鍵插入）
- 雲端設定同步（換機不怕）
- 語氣強度滑桿
