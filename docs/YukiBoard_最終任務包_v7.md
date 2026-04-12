# YukiBoard 最終整合任務包 v7

> 整合 v3 / v4 / v5 / v6 + 所有新功能。
> 基於現有專案 YukiBoard-main，只實作缺少的部分。
> 給 Claude Code 讀取，請依照順序執行。
> 最後更新：2026年4月

---

## 現有專案已有（不需重複實作）

✅ MVVM + Clean Architecture、YukiBoardIMEService
✅ InputRouter 中英雙軌、KeyboardView Canvas 鍵盤
✅ OpenGL ES 流光動畫、Lottie 角色動畫
✅ RadialModeMenu 三模式（NORMAL/DESIGNER/DEVELOPER）
✅ 設計者/開發者模式快捷鍵、Snippet 系統
✅ ShakeDetector / GyroscopeCursorController / AppModeDetector
✅ 四款遊戲（Snake/WhackAMole/Rhythm/TypingChallenge）
✅ 7 個 Claw Skills（AIPrediction/Writer/Translate/Cupid/Context/Animation/Whisper）
✅ AI Provider 架構（OpenAI/Anthropic/SnowRealm 預留）
✅ InviteCodeManager、InviteCodeActivity、AISettingsActivity
✅ 貼圖系統（InsightStickerApi/StickerRepository）
✅ 面板 UI（Emoji/Kaomoji/Clipboard/Translate/GIF/Sticker/Theme）
✅ 遠端鍵盤（Bluetooth HID / WiFi WebSocket）
✅ SettingsActivity、YukiPreferences

---

## 執行順序

1. **Phase 1** — 全功能開關設定系統（最優先，其他功能依賴它）
2. **Phase 2** — 懸浮面板基礎架構
3. **Phase 3** — 天氣懸浮面板
4. **Phase 4** — Claw 結果懸浮面板
5. **Phase 5** — 遊戲懸浮面板 + 新增遊戲（12款）
6. **Phase 6** — 貼圖懸浮面板
7. **Phase 7** — 雪凜微吐槽系統
8. **Phase 8** — 情緒冷靜期
9. **Phase 9** — 語境預測升級
10. **Phase 10** — 新增 Skill（12個，T1-T4）
11. **Phase 11** — Prompt 模板補完
12. **Phase 12** — ClawToolbar 更新
13. **Phase 13** — RadialModeMenu 加入 GAME 模式
14. **Phase 14** — 私房話模式 + 語氣偵測

---

## Phase 1：全功能開關設定系統

> 原則：所有功能都有開關，使用者完全自主控制。
> 設定預設值都是「開啟」，讓用戶自行關閉不喜歡的功能。

### 更新 YukiPreferences.kt

在現有 `YukiPreferences.kt` 加入所有開關：

```kotlin
class YukiPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences("yukiboard_prefs", Context.MODE_PRIVATE)

    // ── AI 功能 ──────────────────────────────────────────────
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

    // ── 雪凜行為 ─────────────────────────────────────────────
    var yukiSarcasticComments: Boolean by BoolPref("yuki_sarcastic", true)
    var yukiEmotionalCooldown: Boolean by BoolPref("yuki_cooldown", true)
    var yukiMoodDiaryPassive: Boolean by BoolPref("yuki_mood_passive", true)
    var yukiWeatherAlert: Boolean by BoolPref("yuki_weather_alert", true)
    var yukiLateNightComment: Boolean by BoolPref("yuki_late_night", true)

    // ── 感測器 ───────────────────────────────────────────────
    var shakeToUndo: Boolean by BoolPref("shake_undo", true)
    var gyroscopeCursor: Boolean by BoolPref("gyro_cursor", true)
    var appModeDetection: Boolean by BoolPref("app_mode_detect", true)

    // ── 動畫特效 ─────────────────────────────────────────────
    var flowLightEnabled: Boolean by BoolPref("flow_light", true)
    var characterAnimEnabled: Boolean by BoolPref("character_anim", true)
    var keyParticleEffect: Boolean by BoolPref("key_particle", true)
    var keyPressVibration: Boolean by BoolPref("key_vibration", true)
    var keySoundEnabled: Boolean by BoolPref("key_sound", false)  // 預設關（避免擾人）

    // ── 遊戲系統 ─────────────────────────────────────────────
    var gamesEnabled: Boolean by BoolPref("games_enabled", true)
    var gameBackgroundMusic: Boolean by BoolPref("game_bgm", false)  // 預設關
    var gameSoundEffects: Boolean by BoolPref("game_sfx", true)

    // ── 懸浮面板 ─────────────────────────────────────────────
    var floatingPanelEnabled: Boolean by BoolPref("floating_panel", true)
    var weatherPanelEnabled: Boolean by BoolPref("weather_panel", true)
    var stickerPanelEnabled: Boolean by BoolPref("sticker_panel", true)

    // ── 輸入行為 ─────────────────────────────────────────────
    var swipeInput: Boolean by BoolPref("swipe_input", true)
    var autoCorrect: Boolean by BoolPref("auto_correct", true)
    var snippetExpansion: Boolean by BoolPref("snippet_expansion", true)
    var secretMessageMode: Boolean by BoolPref("secret_message", true)
    var toneDetection: Boolean by BoolPref("tone_detection", true)

    // ── 連線功能 ─────────────────────────────────────────────
    var remoteKeyboardEnabled: Boolean by BoolPref("remote_keyboard", true)
    var multiplayerEnabled: Boolean by BoolPref("multiplayer", true)

    // 代理實作
    private inner class BoolPref(val key: String, val default: Boolean) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>) =
            prefs.getBoolean(key, default)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            prefs.edit().putBoolean(key, value).apply()
    }
}
```

### 更新 SettingsActivity.kt

重構設定頁面，分為六個群組：

```
設定頁面結構
├── 🤖 AI 功能
│   ├── AI 功能總開關（主開關，關閉後以下全部灰色）
│   ├── AI 補字預測
│   ├── 雪凜寫手
│   ├── 即時翻譯
│   ├── 情聖月老
│   ├── AI 語音（Whisper）
│   ├── 文法糾錯
│   ├── 語氣調整
│   ├── 摘要精簡
│   ├── 迷因生成
│   ├── Emoji 翻譯
│   ├── 快速回覆
│   ├── 程式碼補全（開發者模式）
│   ├── 正則產生器（開發者模式）
│   ├── Commit 訊息（開發者模式）
│   ├── 雪凜聊天
│   ├── 心情日記
│   └── 天氣助理
│
├── ❄️ 雪凜行為
│   ├── 微吐槽（偶爾）
│   ├── 情緒冷靜期（偵測激烈文字時）
│   ├── 心情日記被動偵測
│   ├── 天氣主動提醒
│   └── 深夜關心
│
├── 📡 感測器
│   ├── 搖手機復原（Ctrl+Z）
│   ├── 陀螺儀游標控制
│   └── App 自動切換模式
│
├── ✨ 動畫特效
│   ├── 流光動畫
│   ├── 角色動畫（雪凜）
│   ├── 按鍵粒子效果
│   ├── 按鍵震動
│   └── 按鍵音效
│
├── 🎮 遊戲
│   ├── 遊戲功能總開關
│   ├── 背景音樂
│   └── 音效
│
└── ⚙️ 輸入行為
    ├── 滑動輸入
    ├── 自動更正
    ├── Snippet 展開
    ├── 私房話模式
    ├── 語氣偵測
    ├── 懸浮面板
    └── 遠端鍵盤
```

每個群組用 `CardView` 包裝，標題 + 分隔線視覺清晰。
主開關關閉時，子項目變灰色並無法操作（`isEnabled = false`）。

---

## Phase 2：懸浮面板基礎架構

### 新增目錄
```
app/src/main/java/com/snowrealm/yukiboard/ui/floating/
├── FloatingPanel.kt
├── FloatingPanelManager.kt
├── FloatingPanelConfig.kt
└── gesture/
    ├── DragGestureHandler.kt
    ├── PinchGestureHandler.kt
    └── ResizeHandle.kt
```

### FloatingPanelConfig.kt
```kotlin
data class FloatingPanelConfig(
    val minWidthDp: Int = 200,
    val minHeightDp: Int = 120,
    val maxWidthDp: Int = -1,    // -1 = 執行時計算 screenWidth / 2
    val maxHeightDp: Int = -1,   // -1 = 執行時計算 screenHeight / 2
    val defaultWidthDp: Int = 280,
    val defaultHeightDp: Int = 200,
    val defaultX: Int = 20,
    val defaultY: Int = 80,
    val cornerRadius: Float = 16f,
    val backgroundAlpha: Float = 0.93f,
    val snapToEdge: Boolean = true,
    val snapThresholdDp: Int = 40
)
```

### FloatingPanel.kt — 抽象基礎類別
```kotlin
abstract class FloatingPanel(
    protected val context: Context,
    protected val config: FloatingPanelConfig = FloatingPanelConfig()
) {
    abstract val panelId: String
    abstract val panelTitle: String

    var isShowing: Boolean = false
        private set

    var currentX: Int = config.defaultX
    var currentY: Int = config.defaultY
    var currentWidthDp: Int = config.defaultWidthDp
    var currentHeightDp: Int = config.defaultHeightDp

    fun show()
    fun hide()
    fun dismiss()
    fun moveTo(x: Int, y: Int)
    fun resize(widthDp: Int, heightDp: Int)
    fun snapToNearestEdge()

    abstract fun createContentView(inflater: LayoutInflater): View
    open fun onShown() {}
    open fun onHidden() {}
    open fun onDismissed() {}

    // 標題列：[雪凜小圖] [標題]  [─最小化] [×關閉]
    // 右下角手柄：◢ 24×24dp
}
```

### DragGestureHandler.kt
標題列綁定，拖曳時更新 `WindowManager.LayoutParams`，
放開時若在邊緣 40dp 內，吸附到最近邊緣（動畫 200ms）。

### PinchGestureHandler.kt
雙指縮放，套用 min(200×120dp) / max(半屏) 限制。

### ResizeHandle.kt
右下角 ◢ 24×24dp 觸控區，拖曳改變寬高，同樣套用限制。

### FloatingPanelManager.kt
```kotlin
class FloatingPanelManager(private val context: Context) {
    companion object {
        const val PANEL_WEATHER = "weather"
        const val PANEL_GAME = "game"
        const val PANEL_CLAW_RESULT = "claw_result"
        const val PANEL_STICKER = "sticker"
    }

    fun showPanel(panelId: String)
    fun hidePanel(panelId: String)
    fun dismissPanel(panelId: String)
    fun togglePanel(panelId: String)
    fun dismissAll()
    fun isPanelShowing(panelId: String): Boolean

    // 啟動前檢查設定開關
    private fun checkPrefEnabled(panelId: String): Boolean
}
```

### AndroidManifest.xml 新增
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

---

## Phase 3：天氣懸浮面板

### 新增目錄
```
app/src/main/java/com/snowrealm/yukiboard/weather/
├── WeatherData.kt
├── WeatherManager.kt
└── ui/WeatherFloatingPanel.kt
```

### WeatherData.kt
```kotlin
data class WeatherData(
    val locationName: String,
    val temperature: Int,
    val feelsLike: Int,
    val humidity: Int,
    val rainProbability: Int,
    val windSpeed: Float,
    val condition: WeatherCondition,
    val hourlyForecast: List<HourlyForecast>,
    val updatedAt: Long
)
enum class WeatherCondition { SUNNY, PARTLY_CLOUDY, CLOUDY, RAINY, STORMY, SNOWY, FOGGY, WINDY }
data class HourlyForecast(val hour: Int, val temperature: Int, val rainProbability: Int, val condition: WeatherCondition)
```

### WeatherManager.kt
- OpenWeatherMap API，30 分鐘快取
- `getYukiComment(weather)` — 本地邏輯，0 token
- `getQuickPhrases(weather)` — 本地，0 token
- 受 `prefs.weatherPanelEnabled` 控制

雪凜天氣短句：
```kotlin
fun getYukiComment(weather: WeatherData): String = when {
    weather.rainProbability > 70 -> "今天要帶傘喦！不然頭髮會濕掉 😤"
    weather.temperature < 10    -> "超冷！出門記得穿厚一點 🥶"
    weather.temperature < 15    -> "有點涼，帶件外套比較保險 🧥"
    weather.temperature > 35    -> "熱死了啦，多喝水 🥵"
    weather.temperature > 30    -> "今天很熱，防曬記得擦 ☀️"
    weather.condition == SUNNY  -> "今天天氣超好！出去走走嗎？✨"
    weather.condition == RAINY  -> "在家追劇的好天氣 🌧️"
    else                         -> "普通的一天，加油！⛅"
}
```

### WeatherFloatingPanel.kt
面板內容：位置 + 溫濕度風速 + 6小時預報橫向滾動 + 雪凜短句 + 快速短語/穿衣建議/分享

build.gradle 新增：
```groovy
implementation 'com.google.android.gms:play-services-location:21.2.0'
buildConfigField "String", "OPENWEATHER_API_KEY", "\"${localProps.getProperty('OPENWEATHER_API_KEY', '')}\""
```

---

## Phase 4：Claw 結果懸浮面板

```
app/src/main/java/com/snowrealm/yukiboard/ui/floating/
└── ClawResultFloatingPanel.kt
```

介面：`◀ 1/3 ▶` + 結果文字（typewriter 效果）+ `[複製] [✓ 插入輸入框]`

功能：
- `showResult(skillName, results: List<String>)` 外部呼叫
- Streaming 支援（逐字顯示）
- 插入輸入框透過 `InputConnection.commitText()`

---

## Phase 5：遊戲懸浮面板 + 全部遊戲（12款）

### GameFloatingPanel.kt
```
app/src/main/java/com/snowrealm/yukiboard/ui/floating/
└── GameFloatingPanel.kt
```

預設尺寸 320×260dp。面板顯示時**鍵盤保持原本大小**，同時作為控制器。

### 新增遊戲（8款，加上現有4款共12款）

新增到 `app/src/main/java/com/snowrealm/yukiboard/game/`

---

**WordGuardianGame.kt — 詞彙守護者 🏰**
```
概念：塔防 + 打字
候選詞列上方出現小敵人往鍵盤推進
輸入正確詞彙 = 發射砲彈擊退敵人
打字越準確，砲彈越強
結合 Z幣：擊退敵人得到 Z幣碎片（純視覺，之後接真實 Z幣）
難度隨時間提升（敵人變快、詞彙變難）
雪凜在面板左側指揮動畫
```

**KeyboardTravelerGame.kt — 鍵盤旅行者 🏃**
```
概念：放置 + 跑酷
招財🐹在候選詞列上方奔跑
打字速度 = 跑步速度
打字停頓 > 3秒：招財坐下休息、掏出便當吃
輸入「愛心」「開心」正面詞彙：招財撒花
輸入負面詞彙：招財搖頭
跑過障礙物按對應鍵跳躍
距離轉換為 CoinBeast 的跑步記錄（未來接 API）
```

**MoodMinerGame.kt — 情緒採礦 💎**
```
概念：被動收集 + 益智
根據打字內容在按鍵縫隙生成「情緒原石」
正面詞彙 = 金色/粉色原石
負面詞彙 = 灰色/黑色原石
中性詞彙 = 藍色/白色原石
Swipe 滑動軌跡可收集路過的原石
收集後累積成「情緒週報」（每週生成一份）
週報顯示這週的情緒分佈圓餅圖
雪凜給一段週評（接 AI，消耗少量 token）
```

**BulletHellMiniGame.kt — 極簡彈幕 ✈️**
```
概念：動作 + 閃避
不打字時，鍵盤透明度降低，變成彈幕遊戲場
Swipe 滑動軌跡控制小飛機移動
躲避從上方飄落的子彈
飛機是雪凜迷你版
局數極短（15-30秒）
打字時自動暫停，不干擾輸入
```

**IceCrystalGame.kt — 冰晶收集 ❄️**
```
概念：收集 + 輸入連動
正確輸入完整長句（>10字），鍵盤隨機掉落冰晶
Swipe 滑動軌跡若經過冰晶即收集
冰晶累積到一定數量可兌換主題皮膚預覽
每日冰晶上限，防止刷量
雪凜美學：藍白漸層冰晶，落下動畫優雅
```

**WarshipCruiseGame.kt — 戰艦巡航 🚢**
```
概念：放置 + 打字連動
按鍵區底層背景有一艘微型戰艦緩慢巡航
打字速度 = 戰艦動力（打越快火焰越亮）
打字停止：戰艦漂流、動力減弱
打字達到速度閾值：戰艦衝刺，背景顯示更多「帝國版圖」
每日巡航距離記錄
```

**SpellingDuelGame.kt — 拼字對決 🔤**
```
概念：益智 + 詞彙
給出 8 個英文字母（含母音）
在時間內拼出最長的英文單字
輸入後按 Enter 確認，系統驗證是否為真實單字
分數 = 字母數 × 難度係數
配合 SnowRealmZiku：學過的單字在這裡出現加分
雪凜點評你拼出的單字（好玩的短句）
```

**KeyboardMinesweeperGame.kt — 按鍵掃雷 🎯**
```
概念：策略 + 運氣
部分按鍵下方隱藏地雷
按鍵數字提示周圍地雷數（跟電腦掃雷一樣）
觸雷：雪凜驚嚇動畫 + 全鍵盤閃紅
過關：雪凜慶祝 + 流光金色爆炸
難度：簡單(5個雷)/中等(10個)/困難(15個)
```

**MemoryFlipGame.kt — 記憶翻牌 🃏**
```
概念：記憶 + 配對
鍵盤每個按鍵背面藏圖案（emoji 或雪凜表情）
按下按鍵翻開，找出兩個相同的配對
時間限制，越快分越高
進階模式：圖案只顯示 0.5 秒就蓋回去
```

**YukiQuizGame.kt — 雪凜問答 🌸**
```
概念：問答 + 角色互動
雪凜出題（常識/語文/SnowRealm 知識）
按對應數字鍵或字母鍵選擇答案
答對：雪凜開心動畫 + 流光
答錯：雪凜嘆氣 + 給正確答案
結合 Ziku：出現 Ziku 學過的語言題
```

**CoupleQuizGame.kt — 情侶測驗 💘**
```
概念：互動 + 社群分享
雪凜問一個情侶相關問題（非常規，有點毒舌）
用戶按鍵選擇答案
結果截圖分享（跟 ShareCard 聯動）
雪凜給出爆笑/毒舌評語
例題：「約會遲到要怎麼辦？」→ 狂傳訊息/假裝不知道/說謊說在路上
```

**TypingRaceMultiplayerGame.kt — 打字競速（連線）🏆**
```
概念：多人對戰 + 即時
v1.0 單機模式：跟自己最佳記錄比
v2.0 連線模式：
  - 產生 6 碼房間號
  - 朋友輸入房間號加入
  - 即時顯示雙方進度條
  - 打完同一段文字，比時間
  - 後端：Supabase Realtime WebSocket
  - 對手頭像用 SnowRealm 帳號頭貼（未來接）
  - 暫時用匿名名稱
勝利：雪凜舉旗慶祝
失敗：雪凜給安慰（微吐槽版：「輸給一個陌生人喔 🙄」）
```

**RandomChallengeGame.kt — 隨機挑戰 🎲**
```
概念：創意 + 限時
雪凜隨機出奇怪的打字挑戰
30秒內完成
例：
- 「打出所有你知道的水果名稱」
- 「用注音打一段情書」
- 「打出 10 個帶『心』字的詞」
- 「用英文打出今天的心情」
結果雪凜評分（AI 評分，消耗少量 token）
超有趣的可以截圖分享
```

### 遊戲分頁佈局（GameFloatingPanel 內）
```
選單頁（未開始）：
┌─────────────────────────────────────────┐
│ [雪] 遊戲  分數:1234            [─] [×] │
├─────────────────────────────────────────┤
│ 經典         動作         益智          │ ← 分類 Tab
├─────────────────────────────────────────┤
│ 🐍貪吃蛇  🔨打地鼠  🥁節奏  ⌨️打字     │
│ 🏰詞彙守護 🏃旅行者  💎採礦  ✈️彈幕    │
│ ❄️冰晶    🚢戰艦    🔤拼字  🎯掃雷    │
│ 🃏翻牌    🌸問答    💘情侶  🎲挑戰    │
│ 🏆競速                                  │
└───────────────────────────────────┐   │
                                    └◢  │
```

---

## Phase 6：貼圖懸浮面板

```
app/src/main/java/com/snowrealm/yukiboard/ui/floating/
└── StickerFloatingPanel.kt
```

繼承 `FloatingPanel`，內容複用現有 `StickerPanelView`。
分頁：免費 / 角色 / 節日 / 全部，4欄網格，稀有度角標（N灰/R藍/SR紫/SSR金）。

---

## Phase 7：雪凜微吐槽系統

```
app/src/main/java/com/snowrealm/yukiboard/yuki/
└── YukiCommentEngine.kt
```

### 設計原則
- **頻率極低**：每次觸發後有 10 分鐘冷卻，不煩人
- **時機要準**：只在有趣情境觸發，不在用戶趕時間時出現
- **可以關掉**：受 `prefs.yukiSarcasticComments` 控制
- **說完就過**：顯示 2-3 秒，不重複

### YukiCommentEngine.kt
```kotlin
class YukiCommentEngine(
    private val prefs: YukiPreferences
) {
    private var lastCommentTime = 0L
    private val COOLDOWN_MS = 10 * 60 * 1000L  // 10 分鐘冷卻

    // 觸發檢查（在 YukiBoardIMEService 呼叫）
    fun checkAndComment(event: TypingEvent): String? {
        if (!prefs.yukiSarcasticComments) return null
        if (System.currentTimeMillis() - lastCommentTime < COOLDOWN_MS) return null

        val comment = when (event) {
            is SameWordMistake3Times -> getMistakeComment(event.word)
            is LongTextDeleted       -> getDeleteComment()
            is TypingVerySlow        -> getSlowComment()
            is LateNightTyping       -> getLateNightComment()
            else                     -> null
        }

        if (comment != null) lastCommentTime = System.currentTimeMillis()
        return comment
    }

    // 同一個字打錯 3 次
    private fun getMistakeComment(word: String): String = listOf(
        "這個字你今天打錯三次了喔 🙄",
        "⋯⋯要不要我教你怎麼拼？",
        "沒關係，第四次一定會對的 👀"
    ).random()

    // 打了一長段又全刪掉
    private fun getDeleteComment(): String = listOf(
        "那段我有看到 👀",
        "剛才打的我覺得還不錯啊⋯",
        "想說又不說，是有什麼秘密嗎 🤔"
    ).random()

    // 打字速度突然變很慢
    private fun getSlowComment(): String = listOf(
        "⋯⋯你還在嗎？",
        "需要我幫你想嗎？",
        "我等你喔（真的在等）"
    ).random()

    // 深夜打字（00:00-05:00）
    private fun getLateNightComment(): String = listOf(
        "這麼晚了還在傳訊息⋯",
        "是在跟誰聊天啊 👀",
        "早點睡喔，明天還要上班"
    ).random()
}

sealed class TypingEvent {
    data class SameWordMistake3Times(val word: String) : TypingEvent()
    object LongTextDeleted : TypingEvent()
    object TypingVerySlow : TypingEvent()
    object LateNightTyping : TypingEvent()
}
```

吐槽顯示方式：
- 雪凜上方出現小說話泡泡（2-3秒後淡出）
- 雪凜有對應的微表情動畫
- 不遮擋輸入框

---

## Phase 8：情緒冷靜期

```
app/src/main/java/com/snowrealm/yukiboard/yuki/
└── EmotionalCooldownManager.kt
```

```kotlin
class EmotionalCooldownManager(
    private val context: Context,
    private val prefs: YukiPreferences
) {
    // 觸發詞庫（可擴充）
    private val intenseWords = setOf(
        "去死", "幹你", "殺了", "白痴", "廢物", "滾", "閉嘴",
        "fuck", "shit", "hate you", "idiot"
        // 更多⋯
    )

    // 檢查是否需要冷靜期
    fun shouldTriggerCooldown(text: String): Boolean {
        if (!prefs.yukiEmotionalCooldown) return false
        return intenseWords.any { text.contains(it, ignoreCase = true) }
    }

    // 觸發冷靜期（在 YukiBoardIMEService 呼叫）
    fun triggerCooldown(inputConnection: InputConnection) {
        // 1. 雪凜結冰動畫（yuki_freeze.json）
        // 2. 鍵盤所有按鍵 1 秒內無法操作
        // 3. 顯示確認對話框（懸浮在鍵盤上方）
        showConfirmDialog(inputConnection)
    }

    private fun showConfirmDialog(inputConnection: InputConnection) {
        // 對話框文字：「等一下。這句話確定要發嗎？」
        // 按鈕：「算了不發」 / 「還是發吧」
        // 選「算了不發」：清空目前輸入
        // 選「還是發吧」：繼續正常輸入
    }
}
```

動畫：`assets/lottie/yuki_freeze.json`（雪凜結冰特效，需新增）

---

## Phase 9：語境預測升級

修改 `input/predict/NextActionPredictor.kt`（現有檔案）：

```kotlin
class NextActionPredictor(
    private val prefs: YukiPreferences,
    private val clawClient: ClawClient? = null
) {
    // 升級：不只預測下一個字，預測「意圖」
    // 例：輸入「明天要不要⋯」→ 預測「吃飯」「開會」「出去玩」
    // 例：輸入「幫我訂⋯」→ 預測「餐廳」「機票」「外送」
    // 本地規則引擎（0 token）：
    private val intentPatterns = mapOf(
        "明天要不要" to listOf("吃飯？", "出去玩？", "開會？"),
        "我覺得" to listOf("還不錯", "有點奇怪", "超棒的"),
        "可以幫我" to listOf("看一下嗎？", "傳給他嗎？")
        // 更多⋯
    )

    // AI 意圖預測（邀請碼解鎖，消耗少量 token）
    suspend fun predictIntent(text: String): List<String>

    // 本地規則預測（0 token）
    fun predictLocal(text: String): List<String>
}
```

---

## Phase 10：新增 12 個 Skill（T1-T4）

新增到 `app/src/main/java/com/snowrealm/yukiboard/claw/skills/`

所有 Skill 執行前先檢查對應 `prefs` 開關。

### Tier 1
- **GrammarCheckSkill.kt** — `prefs.aiGrammarCheck`，200 token
- **ToneAdjustSkill.kt** — `prefs.aiToneAdjust`，300 token，四種語氣
- **SummarizeSkill.kt** — `prefs.aiSummarize`，300 token

### Tier 2
- **MemeTextSkill.kt** — `prefs.aiMemeText`，200 token，台灣網路梗
- **EmojiTranslateSkill.kt** — `prefs.aiEmojiTranslate`，100 token
- **QuickReplySkill.kt** — `prefs.aiQuickReply`，400 token，整句回覆

### Tier 3（開發者模式限定）
- **CodeCompleteSkill.kt** — `prefs.aiCodeComplete`，500 token
- **RegexGenSkill.kt** — `prefs.aiRegexGen`，300 token
- **CommitMessageSkill.kt** — `prefs.aiCommitMessage`，400 token

### Tier 4
- **YukiChatSkill.kt** — `prefs.aiYukiChat`，600 token，善意毒舌風格
- **MoodDiarySkill.kt** — `prefs.aiMoodDiary`，0-200 token，被動偵測
- **WeatherSkill.kt** — `prefs.aiWeather`，0-300 token

---

## Phase 11：Prompt 模板補完

新增到 `app/src/main/res/raw/`：

**claw_grammar_prompt.txt**
```
請修正以下文字的錯字、文法和標點符號。
只回傳修正後的文字，不要任何解釋。
原文：{input}
```

**claw_meme_prompt.txt**
```
你是台灣網路迷因專家，熟悉各種梗圖文字和流行語。
請根據以下情境產生 3 個迷因文字，台灣網路風格。
情境：{input}
每個用 --- 分隔，不要解釋。
```

**claw_yuki_chat_prompt.txt**
```
你是雪凜，SnowRealm 的 AI 角色。
個性：善意毒舌、短句LINE風格、有梗但真誠、偶爾撒嬌。
你在鍵盤上幫用戶想說什麼，不是客服機器人。
不要長篇大論，用 1-2 句 LINE 風格短句回應。
用戶說：{input}
```

**claw_weather_outfit_prompt.txt**
```
現在{city}天氣：{temp}°C，{condition}，濕度{humidity}%，風速{wind}m/s。
請用台灣繁體中文給出穿衣建議，100字以內，雪凜活潑風格。
```

**claw_random_challenge_prompt.txt**
```
請為 YukiBoard 打字挑戰遊戲設計一個有趣的題目。
要求：有趣、適合在手機鍵盤上打字完成、30秒內可以作答。
只回傳題目本身，不超過 20 字。
```

**claw_yuki_rating_prompt.txt**
```
用戶完成了以下打字挑戰，請用雪凜的風格給出 1-2 句評語。
雪凜個性：善意毒舌、有梗、不說廢話。
挑戰題目：{challenge}
用戶回答：{answer}
只回傳評語，不超過 30 字。
```

---

## Phase 12：ClawToolbar 更新

修改 `ui/panel/ClawToolbarView.kt`：

新增按鈕（受設定開關控制）：
- 🌤 天氣 → `floatingPanelManager.togglePanel(PANEL_WEATHER)`（`prefs.weatherPanelEnabled`）
- 🎮 遊戲 → `floatingPanelManager.togglePanel(PANEL_GAME)`（`prefs.gamesEnabled`）

Token 顯示（右側）：
```kotlin
fun updateTokenDisplay(remaining: Int, isActivated: Boolean) {
    when {
        !isActivated    -> { tokenView.text = "🔒"; tokenView.setOnClickListener { 跳InviteCodeActivity } }
        remaining > 500 -> { tokenView.text = "$remaining"; tokenView.setTextColor(綠色) }
        remaining > 0   -> { tokenView.text = "$remaining"; tokenView.setTextColor(橙色) }
        else            -> { tokenView.text = "0 🔒"; tokenView.setTextColor(紅色) }
    }
}
```

根據 `KeyboardMode` 動態顯示 Skill 按鈕：
```kotlin
fun updateForMode(mode: KeyboardMode) {
    val skills = when (mode) {
        NORMAL   -> listOf("ai_prediction","writer","cupid","translate","whisper","weather","game")
        DESIGNER -> listOf("writer","summarize","tone_adjust")
        DEVELOPER-> listOf("code_complete","regex_gen","commit_message","summarize")
        GAMING   -> listOf("game","meme_text","quick_reply","emoji_translate")
    }.filter { isSkillEnabled(it) }  // 過濾已關閉的 Skill
}
```

---

## Phase 13：RadialModeMenu 加入 GAME 模式

修改 `ui/keyboard/RadialModeMenu.kt`：

```kotlin
// 從三個選項（120度）改為四個（90度）
val modes = listOf(
    ModeOption(KeyboardMode.NORMAL,   "一般", "🌐"),
    ModeOption(KeyboardMode.DESIGNER, "設計", "🎨"),
    ModeOption(KeyboardMode.DEVELOPER,"開發", "💻"),
    ModeOption(KeyboardMode.GAMING,   "遊戲", "🎮")
)
// 角度間隔從 120f 改為 90f
val angleStep = 360f / modes.size  // = 90f
```

---

## Phase 14：私房話模式 + 語氣偵測

### SecretMessageManager.kt
```
app/src/main/java/com/snowrealm/yukiboard/claw/secret/
└── SecretMessageManager.kt
```

```kotlin
class SecretMessageManager(private val prefs: YukiPreferences) {
    // AES-256-GCM 加密
    fun encrypt(text: String): String  // 輸出：[YB🔐] {base64}
    fun decrypt(encrypted: String): String?
    fun isEncryptedMessage(text: String): Boolean
    // 觸發：候選詞列特殊按鈕，受 prefs.secretMessageMode 控制
}
```

### ToneDetector.kt
```
app/src/main/java/com/snowrealm/yukiboard/claw/mood/
└── ToneDetector.kt
```

```kotlin
class ToneDetector(private val prefs: YukiPreferences) {
    enum class Tone { POSITIVE, NEGATIVE, NEUTRAL, ANGRY, SAD, EXCITED }

    // 本地關鍵字分析，0 token
    fun analyze(text: String): Tone

    // 只在 NEGATIVE/SAD 時回傳雪凜回應（受 prefs.toneDetection 控制）
    fun getYukiResponse(tone: Tone): String? = when (tone) {
        NEGATIVE -> "還好嗎？有什麼我可以幫你的嗎 🤔"
        SAD      -> "沒關係的，會過去的 💙"
        else     -> null
    }
}
```

---

## 完整 Skill 清單（19個）

| # | ID | 名稱 | 狀態 | Token/次 | 模式 | 設定開關 |
|---|----|------|------|----------|------|---------|
| 1 | ai_prediction | AI 補字 | ✅ | 100 | 全部 | aiPrediction |
| 2 | writer | 雪凜寫手 | ✅ | 800 | 全部 | aiWriter |
| 3 | translate | 即時翻譯 | ✅ | 300 | 全部 | aiTranslate |
| 4 | cupid | 情聖月老 | ✅ | 600 | 全部 | aiCupid |
| 5 | context | 情境感知 | ✅ | 0 | 全部 | — |
| 6 | animation_suggest | 角色動畫 | ✅ | 50 | 全部 | characterAnimEnabled |
| 7 | whisper | AI 語音 | ✅ | 200 | 全部 | aiWhisper |
| 8 | grammar_check | 文法糾錯 | ❌ | 200 | 全部 | aiGrammarCheck |
| 9 | tone_adjust | 語氣調整 | ❌ | 300 | 全部 | aiToneAdjust |
| 10 | summarize | 摘要精簡 | ❌ | 300 | 全部 | aiSummarize |
| 11 | meme_text | 迷因生成 | ❌ | 200 | 全部 | aiMemeText |
| 12 | emoji_translate | Emoji 翻譯 | ❌ | 100 | 全部 | aiEmojiTranslate |
| 13 | quick_reply | 快速回覆 | ❌ | 400 | 全部 | aiQuickReply |
| 14 | code_complete | 程式碼補全 | ❌ | 500 | 開發者 | aiCodeComplete |
| 15 | regex_gen | 正則產生器 | ❌ | 300 | 開發者 | aiRegexGen |
| 16 | commit_message | Commit 訊息 | ❌ | 400 | 開發者 | aiCommitMessage |
| 17 | yuki_chat | 雪凜聊天 | ❌ | 600 | 全部 | aiYukiChat |
| 18 | mood_diary | 心情日記 | ❌ | 0-200 | 全部 | aiMoodDiary |
| 19 | weather | 天氣助理 | ❌ | 0-300 | 全部 | aiWeather |

---

## 完成後驗證清單

**Phase 1 — 設定系統**
- [ ] 設定頁面六個群組顯示正確
- [ ] 主開關關閉後子項目灰化
- [ ] 所有開關重啟 App 後保留狀態
- [ ] 每個功能執行前確實檢查開關

**Phase 2 — 懸浮面板**
- [ ] SYSTEM_ALERT_WINDOW 權限申請正常
- [ ] 拖曳移動流暢
- [ ] 雙指縮放 + 右下角手柄都能縮放
- [ ] min(200×120) / max(半屏) 限制正常
- [ ] 靠邊吸附動畫流暢
- [ ] 多面板同時顯示不衝突

**Phase 3 — 天氣**
- [ ] 天氣資訊正確顯示
- [ ] 雪凜短句根據天氣變化
- [ ] 快速短語可插入輸入框

**Phase 4 — Claw 結果**
- [ ] Streaming typewriter 效果正常
- [ ] 版本切換正常
- [ ] 插入輸入框正常

**Phase 5 — 遊戲**
- [ ] 12 款遊戲全部可啟動
- [ ] 鍵盤保持原本大小
- [ ] 打字競速 v1.0 單機模式正常
- [ ] 情緒採礦週報生成正常

**Phase 7 — 微吐槽**
- [ ] 10 分鐘冷卻正常
- [ ] 觸發時機準確（不在一般打字時觸發）
- [ ] 設定關掉後不再出現

**Phase 8 — 情緒冷靜期**
- [ ] 觸發詞偵測正常
- [ ] 鍵盤鎖定 1 秒正常
- [ ] 確認對話框選項正常

**Phase 10-14**
- [ ] 12 個新 Skill 全部可執行
- [ ] Tier 3 只在開發者模式顯示
- [ ] 所有 Skill 執行前檢查設定開關
- [ ] AES 加解密正確
- [ ] RadialModeMenu 四個選項 90 度間隔

---

## Lottie 動畫新增需求

現有 lottie 檔案需要補充（佔位動畫即可）：
- `yuki_freeze.json` — 情緒冷靜期結冰動畫
- `yuki_battle_idle.json` — 遊戲模式待機
- `yuki_victory.json` — 已有 ✅
- `yuki_game_start.json` — 已有 ✅
- `yuki_game_over.json` — 已有 ✅
- `yuki_cupid.json` — 情聖月老粉紅愛心

如果 JSON 不存在，用 `yuki_happy.json` 作為通用替代，不要 crash。

---

## 未來版本（不在本次範圍）

- 打字競速 v2.0 連線配對（Supabase Realtime）
- 邀請碼 → Z幣購買 AI 用量
- SnowRealmCore 統一帳號登入
- 皮膚商城（Z幣付費）
- VRM 3D 角色升級
- AR 雪凜（ARCore）
- SnowRealmVR 輸入介面
- 情緒採礦週報接 Insight 分析
- 冰晶 / CoinBeast 跨 App 真實聯動
- 鍵盤旅行者距離接毛行天下
