# YukiBoard 全專案 Bug & 升級掃描報告

> 基於 YukiBoard-main 157 個 .kt 檔案完整審查。
> 最後更新：2026-04-04

---

## 🔴 嚴重 Bug（會 crash 或功能壞掉）

### S-1 MemoryFlipGame 快速點擊 crash
**檔案：** `game/MemoryFlipGame.kt` 第 75-76 行
```kotlin
val (r1, c1) = firstFlip!!   // ← crash
val (r2, c2) = secondFlip!!  // ← crash
```
**修復：**
```kotlin
val flip1 = firstFlip ?: return
val flip2 = secondFlip ?: return
val (r1, c1) = flip1
val (r2, c2) = flip2
```

### S-2 SettingsActivity 無限 recreate
**檔案：** `ui/settings/SettingsActivity.kt`，`buildLanguageCard()`
RadioGroup 初始化觸發 `setOnCheckedChangeListener` → `recreate()` 無限迴圈。
**修復：** 加 `isInitializing` flag，初始化後才允許觸發。

### S-3 注音輸入卡頓（主執行緒凍結）
**檔案：** `input/engine/ChewingInputEngine.kt`
`prefixCache`、`reverseIndex`、`nextCharIndex` 三個 lazy 在主執行緒初始化，第一次按注音鍵凍結 UI 1-3 秒。
**修復：** 加 `warmUp()` 在 `YukiBoardIMEService.onCreateInputView()` 用 IO 執行緒提前預熱。

### S-4 Anthropic Streaming 收不到回應
**檔案：** `claw/provider/AnthropicProvider.kt`
外層多餘判斷 `deltaType == "content_block_delta"` 永遠不成立，導致所有 Streaming 回應被吞掉。
**修復：** 移除外層判斷，直接取 `delta["type"] == "text_delta"`。

### S-5 savedBg!! crash
**檔案：** `service/YukiBoardIMEService.kt` 第 266 行
```kotlin
if (isVideoFile(savedBg!!))  // savedBg 已有 null check 但 !! 仍不安全
```
**修復：** `savedBg?.let { bg -> ... }`

---

## 🟡 中等 Bug（體驗差但不一定 crash）

### M-1 ZCoinManager 餘額存在普通 SharedPreferences（可被竄改）
**檔案：** `currency/ZCoinManager.kt`
Z 幣餘額用普通 `SharedPreferences` 存，Root 用戶可直接修改。
**修復：** 改用 `EncryptedSharedPreferences`（跟 InviteCodeManager、AIProviderManager 一樣的做法）：
```kotlin
private val prefs = EncryptedSharedPreferences.create(
    context, "yukiboard_zcoin", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### M-2 GameFloatingPanel 捲動沒有下限
**檔案：** `game/GameFloatingPanel.kt` 第 141 行
`scrollY = (scrollY + dy).coerceAtMost(0f)` 只限制上限，遊戲少時可以無限下捲到空白。
**修復：**
```kotlin
val totalContentHeight = (games.size + cardsPerRow - 1) / cardsPerRow * (cardSize + cardPadding)
val maxScrollUp = -(totalContentHeight - (height - startY)).coerceAtLeast(0f)
scrollY = (scrollY + dy).coerceIn(maxScrollUp, 0f)
```

### M-3 VoiceGame 是空實作但可被選到
**檔案：** `game/VoiceGame.kt`、`game/GameState.kt`
點進去只顯示「即將推出」，但出現在遊戲選單正常卡片中。
**修復：** `GameFloatingPanel.kt` 過濾或顯示 🔒 標記並禁止點擊。

### M-4 GameLoopRunner 未確認生命週期綁定
**檔案：** `game/GameKeyboardView.kt`、`game/GameEngine.kt`
`GameLoopRunner` 用 `Handler` 跑迴圈，需確認 `GameKeyboardView.onDetachedFromWindow()` 裡有 `gameLoopRunner.stop()`，否則 View 銷毀後 Handler 繼續跑。
**修復：** 確認 `onDetachedFromWindow()` 呼叫 `gameLoopRunner.stop()`。

### M-5 多處裸 CoroutineScope 記憶體洩漏
**檔案：** 以下四個位置
```
ui/settings/SettingsActivity.kt:528,538   → lifecycleScope.launch
ui/auth/LoginActivity.kt:372,410,498      → lifecycleScope.launch
ui/currency/ZCoinWalletActivity.kt:113    → lifecycleScope.launch
ui/keyboard/KeyboardBackgroundView.kt:101,129  → 加 class scope，onDetachedFromWindow cancel
```

### M-6 InputRouter / SmartCorrectionEngine !! crash
**檔案：** `input/router/InputRouter.kt:247`、`input/smart/SmartCorrectionEngine.kt:114`
```kotlin
scored[key]!! + 3f   // 有 containsKey 但仍有競態風險
candidates[word]!! + 5.0f
```
**修復：** `(scored[key] ?: 0f) + 3f`

### M-7 TranslateEngine !! crash
**檔案：** `translate/TranslateEngine.kt:92`
```kotlin
return currentTranslator!!
```
**修復：** `return currentTranslator ?: createTranslator(from, to)`

### M-8 LoginActivity pendingCodeId!! crash
**檔案：** `ui/auth/LoginActivity.kt:414`
```kotlin
authClient.register(email, password, name, pendingCodeId!!, code)
```
**修復：**
```kotlin
val codeId = pendingCodeId ?: run { showError("請先完成邀請碼驗證"); return@launch }
authClient.register(email, password, name, codeId, code)
```

### M-9 StickerFloatingPanel 分頁篩選 TODO 未接
**檔案：** `ui/floating/StickerFloatingPanel.kt:109`
Tab 點擊沒有實際過濾效果。
**修復：** 在 `StickerPanelView` 加 `filterByCategory(category: String?)` 並在 Tab 點擊中呼叫。

### M-10 SettingsActivity data!! crash
**檔案：** `ui/settings/SettingsActivity.kt:1024`
```kotlin
val uri = data.data!!  // onActivityResult 中，data 有可能是 null
```
**修復：** `val uri = data?.data ?: return`

### M-11 VrmCharacterView 雙重 !! crash
**檔案：** `animation/VrmCharacterView.kt:209,666`
```kotlin
displayHelper?.attach(renderer!!, ...)
eng.destroyCameraComponent(camera!!.entity)
```
**修復：**
```kotlin
val r = renderer ?: return
displayHelper?.attach(r, display)

camera?.let { c -> eng.destroyCameraComponent(c.entity) }
```

### M-12 SpellingDuelGame 詞庫硬編碼且過少
**檔案：** `game/SpellingDuelGame.kt`
詞庫是程式碼裡少量固定單字，很快重複。
**修復：** 移到 `res/raw/spelling_words.txt`，`init` 時從檔案載入。

### M-13 WifiKeyboardServer 用 HTTP 明文傳輸（無加密）
**檔案：** `remote/WifiKeyboardServer.kt`
遠端鍵盤走明文 HTTP，同網路下可被竊聽截取輸入內容（含密碼）。
**修復短期：** UI 顯示警告「此功能僅建議在私人網路使用，不適合輸入密碼」。
**修復長期：** 改用 HTTPS 或 WebSocket over TLS。

---

## 🟢 輕微問題（低機率觸發或小細節）

### L-1 Deprecated API（三處）
**檔案：** `ui/settings/SettingsActivity.kt`
- `startActivityForResult` + `onActivityResult` → 改用 `ActivityResultLauncher`
- `resources.updateConfiguration` → 改用 `createConfigurationContext`

```kotlin
// 現在
startActivityForResult(intent, REQUEST_PICK_BG_IMAGE)

// 升級後
val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { handleBgImage(it) }
}
pickImageLauncher.launch(arrayOf("image/*"))
```

### L-2 KeyboardView.kt bgPaint 位置錯誤
**檔案：** `ui/keyboard/KeyboardView.kt:603`
```kotlin
private val bgPaint = Paint()  // ← 在 class 內宣告但在 onDraw 下方
```
`bgPaint` 宣告在 class 內（正確），但位置在其他 Paint 宣告下面 400 行，容易讓人誤以為是在 `onDraw` 內建立。整理一下位置即可。

### L-3 TokenBudgetManager 不是真正的額度管控
**檔案：** `claw/TokenBudgetManager.kt`
文件說「僅統計，不扣額度」，但命名是 `BudgetManager` 容易誤解。
**建議：** 改名 `TokenUsageTracker` 或在 CLAUDE.md 加說明。

### L-4 RemoteKeyboardService 背景執行沒有 Foreground Notification
**檔案：** `remote/RemoteKeyboardService.kt`
Android 8+ 背景 Service 需要呼叫 `startForeground()` 否則會被系統 kill。
**修復：** 加入持久通知「遠端鍵盤連線中」。

### L-5 ScoreManager combo 顏色命名不符
**檔案：** `game/ScoreManager.kt`
`getComboColor()` 10+ combo 回傳橘色但叫「彩虹」，語意不符。

---

## 🔧 升級機會（不是 Bug，但值得改善）

### U-1 SettingsActivity 全部改用 Jetpack Compose
目前設定頁面是純程式碼建構 View，約 1200 行難以維護。用 Compose 可以大幅簡化，而且新增的六群組開關可以更容易管理。

### U-2 SharedPreferences → DataStore
**影響：** `YukiPreferences`、`ThemeManager`、`ScoreManager`、`TypingHabitTracker`、`AdaptiveDictionary`、`PhrasePredictor`、`SnippetManager`、`UserDictionary`

DataStore 是官方建議的 SharedPreferences 替代方案，支援 Kotlin Flow、type-safe、非同步寫入。

### U-3 PhrasePredictor / AdaptiveDictionary → Room DB
`PhrasePredictor` 和 `AdaptiveDictionary` 用 SharedPreferences 存 bigram/trigram，資料量大時（幾百個 key）效能會下降。改用 Room DB 更適合。

### U-4 WeatherManager 快取改用 Room DB
目前天氣快取是 in-memory（`var cachedData: WeatherData?`），App 重啟後快取消失，重新啟動時一定要等網路。改用 Room 存到磁碟，重啟後仍可用舊快取。

### U-5 ClawClient.skills 每次建立新 Provider 實例
**檔案：** `claw/ClawClient.kt`
```kotlin
private val skills: Map<String, ClawSkill> by lazy {
    val provider = providerManager.getCurrentProvider()  // ← 只在初始化時取一次
    listOf(AIPredictionSkill(provider), ...).associateBy { it.id }
}
```
用戶切換 Provider 後 skills 裡面的 provider 不會更新。
**修復：** Skill 執行時動態取 provider，不在初始化時固定：
```kotlin
override suspend fun execute(input: ClawInput): ClawOutput {
    val provider = providerManager.getCurrentProvider()  // 每次執行時取
    ...
}
```

### U-6 TypingHabitTracker 缺少資料上限
**檔案：** `input/smart/TypingHabitTracker.kt`
打字習慣追蹤持續累積，沒有清理過期資料的機制，SharedPreferences 會越來越大。
**修復：** 加入 `purgeOldData()` 定期清理 30 天前的資料。

### U-7 GifImageCache 沒有磁碟快取大小限制
**檔案：** `gif/GifImageCache.kt`
GIF 快取只有 LRU 記憶體快取，沒有磁碟快取大小上限，長期使用可能佔用大量儲存空間。

---

## 📋 給 Claude Code 的修復指令

```
請依照優先順序修復 YukiBoard 全專案掃描發現的問題：

第一輪（嚴重，立刻修）：
- S-1：MemoryFlipGame firstFlip!! / secondFlip!! → ?: return
- S-2：SettingsActivity buildLanguageCard isInitializing flag
- S-3：ChewingInputEngine warmUp() 預熱
- S-4：AnthropicProvider Streaming 移除多餘外層判斷
- S-5：IMEService savedBg?.let { bg -> ... }

第二輪（中等，影響體驗）：
- M-1：ZCoinManager 改用 EncryptedSharedPreferences
- M-2：GameFloatingPanel scrollY 加下限
- M-3：VoiceGame 卡片顯示 🔒 禁止點擊
- M-4：GameKeyboardView onDetachedFromWindow 確認 gameLoopRunner.stop()
- M-5：四個檔案裸 CoroutineScope 改 lifecycleScope
- M-6：InputRouter / SmartCorrectionEngine !! → ?: 0f
- M-7：TranslateEngine !! → ?: createTranslator()
- M-8：LoginActivity pendingCodeId ?: return
- M-9：StickerFloatingPanel filterByCategory 串接
- M-10：SettingsActivity data?.data ?: return
- M-11：VrmCharacterView renderer!! / camera!! 修復
- M-12：SpellingDuelGame 詞庫移到 res/raw/spelling_words.txt
- M-13：WifiKeyboardServer UI 加警告提示

第三輪（升級，優先度由高到低）：
- U-5：ClawClient skills 的 provider 改為每次執行時動態取
- U-6：TypingHabitTracker 加 purgeOldData()
- L-4：RemoteKeyboardService 加 startForeground() Notification
- L-1：SettingsActivity Deprecated API 升級到 ActivityResultLauncher
```
