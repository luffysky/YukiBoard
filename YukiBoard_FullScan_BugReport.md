# YukiBoard 全專案 Bug 掃描報告

> 基於 YukiBoard-main__2_.zip，174 個 .kt 檔案
> 已排除本次另有任務包的四個問題（圖片預覽/換圖/語音/滑動停頓）
> 依嚴重程度排序：🔴 必修 → 🟡 建議修 → 🔵 優化項

---

## 🔴 嚴重（會 crash 或資料錯誤）

---

### S-1｜ZCoinManager 餘額 race condition
**檔案：** `currency/ZCoinManager.kt:41-49`

```kotlin
// 問題：canAfford 和 balance -= amount 不是 atomic
fun canAfford(amount: Int): Boolean = balance >= amount  // 讀一次
fun spend(...): Boolean {
    if (!canAfford(amount)) return false
    balance -= amount  // 又讀一次，中間可能被其他 coroutine 改
}
```

**情境：** 兩個地方同時觸發 spend（例如 AI 補字和遊戲同時），餘額可能變負數。

**修法：**
```kotlin
@Synchronized
fun spend(amount: Int, description: String): Boolean {
    if (amount <= 0 || balance < amount) return false
    balance -= amount
    // ... 其餘不變
    return true
}
```

---

### S-2｜AI Provider `flow {}` 裡的 `.execute()` 沒有在 IO thread

**檔案：** `claw/provider/OpenAIProvider.kt:75`、`AnthropicProvider.kt:72`、`GeminiProvider.kt:78`、`GroqProvider.kt:75`、`SnowRealmProvider.kt:91`

```kotlin
// 問題：flow {} builder 本身不切換 dispatcher
// .execute() 是同步阻塞，如果在 Main thread 呼叫會 ANR
override suspend fun complete(...): Flow<String> = flow {
    // ...
    val response = client.newCall(request).execute()  // ← 阻塞 IO，無保護
}
```

`flow {}` 預設在呼叫端的 dispatcher 執行，呼叫端若在 Main thread 就會 ANR。

**修法：**
```kotlin
override suspend fun complete(...): Flow<String> = flow {
    // ...
    val response = withContext(Dispatchers.IO) {
        client.newCall(request).execute()
    }
}
```
或在 ClawClient 呼叫時加 `.flowOn(Dispatchers.IO)`：
```kotlin
clawClient.executeSkill(...).flowOn(Dispatchers.IO).collect { ... }
```

---

### S-3｜TokenBudgetManager `prefs.edit().clear()` 會刪掉所有設定

**檔案：** `claw/TokenBudgetManager.kt:43`

```kotlin
private fun resetDailyIfNeeded() {
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    if (storedDate != today) {
        prefs.edit().clear().putString("quota_date", today).apply()
        // ↑ clear() 會清掉這個 prefs 檔裡的所有 key
    }
}
```

如果 TokenBudgetManager 跟其他設定共用同一個 SharedPreferences 檔案（名稱若是 `yukiboard_prefs`），每天重置會刪掉不相關的設定。

**確認：** 檢查 TokenBudgetManager 用的 prefs 名稱，確保它是獨立的 `token_budget` 檔案，不是共用的。若不確定，改為只重置相關 key：

```kotlin
prefs.edit()
    .remove("tokens_used_today")
    .remove("calls_today")
    .putString("quota_date", today)
    .apply()
```

---

### S-4｜`google_cloud_tts_key` 明文存 SharedPreferences

**檔案：** `ui/settings/YukiPreferences.kt:110`

```kotlin
var googleCloudTtsApiKey: String
    get() = prefs.getString("google_cloud_tts_key", "") ?: ""
    set(value) = prefs.edit().putString("google_cloud_tts_key", value).apply()
```

AI Provider key 已用 `EncryptedSharedPreferences`，但 TTS key 沒有加密，root 後可直接讀取。

**修法：** 用同 `AIProviderManager` 的 `EncryptedSharedPreferences` 存，或移到同一個加密 prefs 檔案。

---

## 🟡 中度（功能異常或潛在 crash）

---

### M-1｜`KeyboardParticleView.particles` 非 thread-safe

**檔案：** `animation/KeyboardParticleView.kt:36`

```kotlin
private val particles = mutableListOf<Particle>()  // 普通 mutableList

override fun onDraw(canvas: Canvas) {
    while (particles.size < maxParticles) {
        particles.add(spawnParticle())  // onDraw 在 Main thread
    }
}

fun setTheme(theme: KeyboardTheme) {
    particles.clear()  // 如果從非 Main thread 呼叫 → ConcurrentModificationException
}
```

若 `setTheme()` 從非 Main thread 呼叫，會跟 `onDraw` 衝突。

**修法：**
```kotlin
// 方案 A：強制在 Main thread 呼叫
fun setTheme(theme: KeyboardTheme) {
    post { particles.clear() }  // post 到 Main thread queue
}

// 方案 B：改用 CopyOnWriteArrayList（效能稍差但安全）
private val particles = CopyOnWriteArrayList<Particle>()
```

---

### M-2｜`StickerImageCache` 和 `GifImageCache` 磁碟快取無大小限制

**檔案：** `sticker/StickerImageCache.kt:24`、`gif/GifImageCache.kt:24`

```kotlin
private val diskCacheDir = File(context.cacheDir, "stickers").also { it.mkdirs() }
// 只有 clearAll() 才清，從不主動修剪
```

貼圖和 GIF 快取會無限增長，長期使用後佔滿 storage。

**修法：** 在每次寫入後，若總大小超過閾值就清掉最舊的檔案：
```kotlin
private val MAX_DISK_BYTES = 50L * 1024 * 1024  // 50MB

private fun trimDiskCacheIfNeeded() {
    val files = diskCacheDir.listFiles() ?: return
    var total = files.sumOf { it.length() }
    if (total <= MAX_DISK_BYTES) return
    files.sortedBy { it.lastModified() }.forEach { file ->
        if (total <= MAX_DISK_BYTES * 0.8) return
        total -= file.length()
        file.delete()
    }
}
// 在 getBitmap() 寫入磁碟後呼叫 trimDiskCacheIfNeeded()
```

---

### M-3｜`SettingsActivity` 用 deprecated `startActivityForResult` / `onActivityResult`

**檔案：** `ui/settings/SettingsActivity.kt:488-499`、`BackgroundPickerActivity.kt:34`

Android 13+ 不建議使用，且有部分裝置行為異常。

**修法：** 改用 `ActivityResultLauncher`：
```kotlin
// 在 Activity 頂部宣告
private val pickImageLauncher = registerForActivityResult(
    ActivityResultContracts.GetContent()
) { uri ->
    uri?.let { handleImagePicked(it) }
}

// 觸發時
pickImageLauncher.launch("image/*")
```

---

### M-4｜`SettingsActivity` 用 deprecated `resources.updateConfiguration`

**檔案：** `ui/settings/SettingsActivity.kt:1428`

```kotlin
resources.updateConfiguration(config, resources.displayMetrics)
// API 25 deprecated，Android 13+ 可能無效
```

**修法：** 改用 `AppCompatDelegate.setApplicationLocales()` 或直接重建 Activity。

---

### M-5｜`ClipboardPanelView` 未移除 ClipboardManager listener

**檔案：** `ui/panel/ClipboardPanelView.kt`

`ClipboardManager` 若有呼叫 `addPrimaryClipChangedListener`，需要在 `onDetachedFromWindow()` 呼叫 `removePrimaryClipChangedListener`，否則會 leak。

**確認：** 檢查有沒有呼叫 `addPrimaryClipChangedListener`，若有則在 `onDetachedFromWindow()` 移除。

---

### M-6｜`GifPanelView` 搜尋無 debounce

**檔案：** `ui/panel/GifPanelView.kt:283`、`409`

```kotlin
// 每次按鍵都觸發 search()，無防抖
fun search(query: String) { ... }
// 呼叫處
search(inputBuffer.toString())  // 每個字元輸入都呼叫
```

每打一個字就觸發 Giphy API，浪費配額且可能被 rate limit。

**修法：**
```kotlin
private var searchJob: Job? = null

fun searchDebounced(query: String) {
    searchJob?.cancel()
    searchJob = scope.launch {
        delay(400)  // 400ms debounce
        search(query)
    }
}
```

---

### M-7｜`SnowRealmAuthClient` JWT token 存 email / displayName 未加密

**檔案：** `auth/SnowRealmAuthClient.kt:129-130`

```kotlin
// JWT 有加密，但 email 和 displayName 用普通 SharedPreferences
.putString(KEY_EMAIL, user.email)         // ← 未加密
.putString(KEY_DISPLAY_NAME, user.displayName)  // ← 未加密
```

root 後可直接讀取使用者 email。

**修法：** 統一全部存進 EncryptedSharedPreferences，或至少 email 加密。

---

### M-8｜`RemoteKeyboardPanelView` 引用的 `RemoteKeyboardService` 只是 interface，無實作類

**檔案：** `remote/RemoteKeyboardService.kt`、`ui/panel/RemoteKeyboardPanelView.kt`

`RemoteKeyboardService` 是 interface，`WifiKeyboardServer` 和 `BluetoothHidKeyboard` 是實作，但 Panel 實際啟動的邏輯需確認是否有正確實例化並傳入。若沒有，點遠端鍵盤功能會 NPE。

---

## 🔵 優化（不影響功能，但影響品質）

---

### O-1｜`VrmCharacterView` scope 未在 `onDetachedFromWindow` cancel

**檔案：** `animation/VrmCharacterView.kt:129`

```kotlin
private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
// 需確認 onDetachedFromWindow 有 scope.cancel()
```

若 VRM 角色 View 被移除但 scope 未取消，coroutine 會繼續跑造成 leak。

**修法：**
```kotlin
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    scope.cancel()
}
```

---

### O-2｜`StickerRepository` scope 在 destroy() 才 cancel，但 syncInBackground() 可能在之後才被呼叫

**檔案：** `sticker/StickerRepository.kt`

確認 `syncInBackground()` 只在 IMEService 初始化時呼叫一次，destroy 後不會再呼叫，否則會在已 cancel 的 scope 上 launch。

---

### O-3｜所有 `fun search()` 系列函式缺乏輸入長度限制

**檔案：** `gif/GiphyApiClient.kt`、`translate/TranslateEngine.kt`

使用者若貼上超長文字觸發搜尋，會把整段文字送到 API，浪費 token 且可能超過 API 限制。

**修法：** 在呼叫前加 `query.take(100)` 截斷。

---

### O-4｜`TypingHabitTracker` / `PhrasePredictor` 每次 commit 都寫 SharedPreferences

**檔案：** `input/smart/TypingHabitTracker.kt`、`input/smart/PhrasePredictor.kt`

每輸入一個詞就立即寫 SharedPreferences，頻繁 I/O 會讓鍵盤輸入有輕微延遲。

**修法：** 改成 dirty flag + 定時批次寫入（每 30 秒或 App 背景時才寫）：
```kotlin
private var isDirty = false

fun recordCommit(word: String) {
    // ... 更新記憶體中的資料
    isDirty = true
}

fun flush() {
    if (!isDirty) return
    isDirty = false
    // 寫 SharedPreferences
}
// 在 YukiBoardIMEService.onFinishInput() 呼叫 flush()
```

---

### O-5｜`KeyboardView.onDraw` 每次重設 Paint 屬性（可改為只在 theme 變更時設定）

**現況：** Paint 物件已是 class 屬性（正確），但 `color` 和 `alpha` 在每次 onDraw 設定，頻率較高。

整體影響不大（60fps 可維持），但在動畫密集時（粒子 + 滑動路徑同時）可酌情優化。

---

### O-6｜`WeatherFloatingPanel` 持有 `context` 可能是 Activity context

**檔案：** `weather/WeatherFloatingPanel.kt:23`

FloatingPanel 通常由 IMEService 建立，傳入的是 Service context，這是正確的。若未來有從 Activity 建立的路徑需注意不要傳 Activity context，否則 Panel 存活期間 Activity 無法被 GC。

---

---

## 🟡 補充（第二輪掃描）

---

### M-9｜`BluetoothHidKeyboard.sendText()` 傳長文字時 postDelayed 無法取消

**檔案：** `remote/BluetoothHidKeyboard.kt:254`

```kotlin
// 每個字元 postDelayed(35ms * index)
// 傳 200 字 = 7 秒的 postDelayed queue
// destroy() 呼叫 stop() 但沒有 handler.removeCallbacksAndMessages(null)
text.forEachIndexed { index, char ->
    handler.postDelayed({
        hid.sendReport(...)
    }, index * 35L)
}

override fun destroy() {
    stop()
    // ← 沒有 handler.removeCallbacksAndMessages(null)
}
```

destroy 後 handler queue 還在跑，嘗試對已斷線的 hidDevice 送資料會 crash。

**修法：**
```kotlin
override fun destroy() {
    handler.removeCallbacksAndMessages(null)  // ← 加這行
    stop()
    stateListener = null
}
```

---

### M-10｜`YukiSpeechBubble.scheduleHide()` 沒有 removeCallbacks

**檔案：** `yuki/YukiSpeechBubble.kt:136`

```kotlin
private fun scheduleHide() {
    postDelayed({
        animate().alpha(0f)...
    }, DISPLAY_DURATION_MS)
    // 沒有在 onDetachedFromWindow 取消
}
```

如果 View 在 2.5 秒內被移除，Runnable 仍會執行，對已 detach 的 View 呼叫 `animate()` 會 crash 或 leak。

**修法：**
```kotlin
private val hideRunnable = Runnable {
    animate().alpha(0f)
        .setDuration(FADE_DURATION_MS)
        .withEndAction { hideNow() }
        .start()
}

private fun scheduleHide() {
    removeCallbacks(hideRunnable)  // 避免重複排程
    postDelayed(hideRunnable, DISPLAY_DURATION_MS)
}

override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    removeCallbacks(hideRunnable)
    animate().cancel()
}
```

---

### M-11｜`InviteCodeActivity` 的 `lottieView.postDelayed({ finish() }, 2000)` 無 cancel

**檔案：** `ui/auth/InviteCodeActivity.kt:194`

使用者如果在 2 秒內按返回鍵退出，`finish()` 仍會在 2 秒後被呼叫（對已 finish 的 Activity 再次 finish），雖然不會 crash，但行為不正確。

**修法：**
```kotlin
private val finishRunnable = Runnable { finish() }

// 原本：lottieView.postDelayed({ finish() }, 2000)
// 改為：
lottieView.postDelayed(finishRunnable, 2000)

override fun onDestroy() {
    super.onDestroy()
    lottieView.removeCallbacks(finishRunnable)
    activityScope.cancel()  // ← 同時補上 scope cancel（目前沒有）
}
```

---

### M-12｜兩個 `CharacterView.kt` 功能重疊，容易混淆

**檔案：** `ui/keyboard/CharacterView.kt` vs `ui/character/CharacterView.kt`

兩個 class 同名，一個功能完整（含 VRM、Lottie、遊走動畫），另一個是簡化版（只有 Lottie）。

如果不同地方 import 到不同版本，功能會靜默不正確（編譯不報錯但行為錯誤）。

**建議：** 統一使用 `ui/keyboard/CharacterView.kt`，刪除或重命名 `ui/character/CharacterView.kt`，例如改名為 `SimpleCharacterView`。

---

## 已確認不是 Bug 的項目

- **ClipboardPanelView** — 只有 `clipManager.primaryClip`（主動讀取），沒有呼叫 `addPrimaryClipChangedListener`，不需要 removeListener ✅
- **S-2 AI Provider** — 所有 Provider (`OpenAI`/`Anthropic`/`Gemini`/`Groq`/`SnowRealm`) 都已有 `.flowOn(Dispatchers.IO)` ✅
- **M-8 RemoteKeyboard** — `RemoteKeyboardPanelView` 已有 `onBtToggle`/`onWifiToggle` 回呼，在 `YukiBoardIMEService` 中正確設定 ✅
- **O-1 VrmCharacterView** — `onDetachedFromWindow()` 已呼叫 `destroy()` → `scope.cancel()` ✅
- **TokenBudgetManager prefs** — 使用獨立的 `yukiboard_token_budget` 檔案，不跟其他設定混用，`clear()` 安全 ✅

---

## 總覽（含第二輪）

| 編號 | 嚴重度 | 問題 | 檔案 |
|------|--------|------|------|
| S-1 | 🔴 | ZCoinManager spend race condition | `ZCoinManager.kt` |
| S-2 | ~~🔴~~ | ~~AI Provider flow 無 IO~~ | 已確認有 flowOn ✅ |
| S-3 | 🔴 | TokenBudgetManager clear() ← 已確認獨立 prefs | 低風險 |
| S-4 | 🔴 | google_cloud_tts_key 明文存儲 | `YukiPreferences.kt` |
| M-1 | 🟡 | ParticleView particles 非 thread-safe | `KeyboardParticleView.kt` |
| M-2 | 🟡 | 貼圖/GIF 磁碟快取無大小限制 | `StickerImageCache.kt`、`GifImageCache.kt` |
| M-3 | 🟡 | startActivityForResult deprecated | `SettingsActivity.kt` |
| M-4 | 🟡 | updateConfiguration deprecated | `SettingsActivity.kt` |
| M-5 | ~~🟡~~ | ~~ClipboardManager listener 未移除~~ | 已確認無 listener ✅ |
| M-6 | 🟡 | GIF 搜尋無 debounce | `GifPanelView.kt` |
| M-7 | 🟡 | email/displayName 未加密存儲 | `SnowRealmAuthClient.kt` |
| M-8 | ~~🟡~~ | ~~RemoteKeyboard 無實作~~ | 已確認有回呼 ✅ |
| M-9 | 🟡 | BluetoothHidKeyboard sendText postDelayed 無 cancel | `BluetoothHidKeyboard.kt` |
| M-10 | 🟡 | YukiSpeechBubble scheduleHide 無 removeCallbacks | `YukiSpeechBubble.kt` |
| M-11 | 🟡 | InviteCodeActivity postDelayed 無 cancel + scope 未 cancel | `InviteCodeActivity.kt` |
| M-12 | 🟡 | 兩個 CharacterView.kt 同名容易混淆 | `ui/keyboard/` vs `ui/character/` |
| O-1 | ~~🔵~~ | ~~VrmCharacterView scope 未 cancel~~ | 已確認有 destroy ✅ |
| O-2 | 🔵 | StickerRepository scope 時序問題 | `StickerRepository.kt` |
| O-3 | 🔵 | 搜尋輸入無長度限制 | `GiphyApiClient.kt` 等 |
| O-4 | 🔵 | Habit/Phrase 每次 commit 寫 prefs | `TypingHabitTracker.kt` 等 |
| O-5 | 🔵 | KeyboardView onDraw Paint 頻繁設定 | `KeyboardView.kt` |
| O-6 | 🔵 | WeatherFloatingPanel context 類型注意 | `WeatherFloatingPanel.kt` |

**真正需要修的：🔴 2個，🟡 8個，🔵 5個，共 15 個。**
