# YukiBoard v7 全專案 Bug 掃描任務包

> 掃描範圍：208 檔案 / 52000+ 行 Kotlin 原始碼
> 掃描日期：2026-04-09
> 分類：崩潰(C) / 功能缺失(F) / Coroutine(CR) / 執行緒安全(T) / 記憶體洩漏(M) / 邏輯(L)

---

## ✅ v6 已確認修好（不需要再做）

```
✅ ZCoinManager @Synchronized（topUp / syncBalance / spend）
✅ 影片背景 pause() / resume()
✅ CharacterView pauseAnimation() / resumeAnimation()
✅ 候選列攔截 candidateHitZone = candidateBarHeight - 8dp
✅ PanelInputTarget 介面 + activePanelInput 統一路由
✅ DecryptPanelView pressedBtn?.takeIf + enterInputMode + onMeasure 260dp
✅ isLongPressTriggered 清除（resetTouchState + ACTION_UP）
✅ touchRect 每排延伸 30% + expandPx 16dp
✅ beginBatchEdit 保護 + onStartInputView 清 composing
✅ clipChildren = false（rootContainer + keyboardContainer）
✅ FasterWhisper 靜音偵測
✅ StickerPanelView / ClawResultFloatingPanel try-catch
✅ KeyboardView toast / CharacterView playIdle 具名 Runnable
✅ GL 流光 wait/notify 省電 + VRM idle 降 15fps
✅ 候選字常用字加分 + 長按放大鏡
```

---

# 🔴 第一批：崩潰風險（6 項）

---

## C1：ClipboardEncryptionHelper 硬轉型崩潰

**`ui/panel/ClipboardEncryptionHelper.kt` line 56**

```kotlin
// 原本：
(keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

// 改為：
val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
    ?: throw IllegalStateException("KeyStore entry is not SecretKeyEntry")
entry.secretKey
```

---

## C2：EmotionStickerEngine 空集合 !! 崩潰

**`input/smart/EmotionStickerEngine.kt` line 48**

```kotlin
// 原本：
val best = scores.maxByOrNull { it.value }!!

// 改為：
val best = scores.maxByOrNull { it.value } ?: return null
```

---

## C3：SettingsActivity item.second!! 崩潰

**`ui/settings/SettingsActivity.kt` line 2287**

```kotlin
// 原本：
val selected = item.second!!

// 改為：
val selected = item.second ?: return
```

---

## C4：KeyboardGLSurfaceView EGL config 崩潰

**`animation/gles/KeyboardGLSurfaceView.kt` line 158**

```kotlin
// 原本：
eglConfig = configs[0]!!

// 改為：
eglConfig = configs[0] ?: run {
    android.util.Log.e("FlowLightGL", "No EGL config found")
    return
}
```

---

## C5：MusicTypingGame rows==1 越界

**`game/MusicTypingGame.kt` line ~122**

`rows==1` 時 `judgeRow = rows - 2 = -1`，`coerceIn(1, rows-1)` = `coerceIn(1, 0)` → 越界。

```kotlin
// 加 guard：
if (rows < 3) return  // 遊戲需要至少 3 行才能運作
```

---

## C6：RhythmGame 空列表越界

**`game/RhythmGame.kt` line 74, 77**

```kotlin
// 原本：
builtInSongs[0]
songNotes.last()

// 改為：
val song = builtInSongs.firstOrNull() ?: return
val lastNote = songNotes.lastOrNull() ?: return
```

---

# 🔴 第二批：功能缺失（2 項）

---

## F1：7 款遊戲 onGameEvent 未接線

**`service/YukiBoardIMEService.kt` line 2243**

`when (game)` 只接了部分遊戲的 `onGameEvent`，缺 7 種遊戲：
- WordleGame
- WordChainGame
- CrosswordGame
- MusicTypingGame
- YukiHouseGame
- TypingFarmGame
- SpeedTypingBattleGame

這些遊戲的 PERFECT / MISS / GAME_OVER / VICTORY 事件全部靜默丟失。

```kotlin
// 在 when (game) 加入缺失的分支，或改用統一接線：
game.onGameEvent = { event -> handleGameEvent(event) }
// 在所有 when 分支之前統一設定，省略分支判斷
```

---

## F2：詞庫匯入/匯出 Toast 可能在背景執行緒

**`service/YukiBoardIMEService.kt` line 1389-1404**

`onExportRequest` / `onImportRequest` 的 callback 內直接呼叫 `Toast.makeText`，如果 callback 是從背景 coroutine 觸發，會拋 `RuntimeException: Can't create handler inside thread that has not called Looper.prepare()`。

```kotlin
// 所有 Toast 呼叫改包 Handler：
android.os.Handler(android.os.Looper.getMainLooper()).post {
    Toast.makeText(this, "...", Toast.LENGTH_SHORT).show()
}
```

---

# 🟡 第三批：Coroutine 安全（9 項）

---

## CR1：翻譯 engine launch 無 try-catch

**`service/YukiBoardIMEService.kt` line 1173**

網路異常時 callback 永遠不回傳，翻譯面板 UI 卡死。

```kotlin
serviceScope.launch {
    try {
        val result = engine.translate(src, fromLang, toLang)
        callback(result)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        callback("翻譯失敗: ${e.message}")
    }
}
```

---

## CR2：GIF search launch 無 try-catch

**`service/YukiBoardIMEService.kt` line 1191**

同上，GIF 面板永遠顯示 loading。

```kotlin
serviceScope.launch {
    try {
        val results = if (query == null) gifClient.fetchTrending() else gifClient.search(query)
        callback(results)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        callback(emptyList())
    }
}
```

---

## CR3：RIME 字典 init launch

**`service/YukiBoardIMEService.kt` line 181**

```kotlin
serviceScope.launch {
    try { rimeDictionary?.init() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w("IME", "RIME init failed: ${e.message}") }
}
```

---

## CR4：chewing warmUp launch

**`service/YukiBoardIMEService.kt` line 195**

```kotlin
serviceScope.launch(Dispatchers.IO) {
    try { inputRouter.warmUpChewing() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w("IME", "Chewing warmup failed: ${e.message}") }
}
```

---

## CR5：被動防詐 launch

**`service/YukiBoardIMEService.kt` line 866**

```kotlin
serviceScope.launch(Dispatchers.Default) {
    try {
        // ... analyzeByRules() ... scanUrls() ...
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w("Fraud", "Passive scan failed: ${e.message}") }
}
```

---

## CR6：GifPanelView preview load

**`ui/panel/GifPanelView.kt` line 276**

失敗後 `loadingUrls` 不移除，該 URL 永遠無法重試。

```kotlin
scope.launch {
    try {
        val bmp = imageCache.getPreviewBitmap(url)
        // ...
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        loadingUrls.remove(url)
        Log.w("GifPanel", "Preview load failed: ${e.message}")
    }
}
```

---

## CR7：StickerRepository prefetch

**`sticker/StickerRepository.kt` line 48**

```kotlin
launch(Dispatchers.IO) {
    try {
        remote.forEach { pack -> imageCache.getBitmap(...) }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w("Sticker", "Prefetch failed: ${e.message}") }
}
```

---

## CR8：StickerPackDownloadManager downloadAllPacks

**`sticker/StickerPackDownloadManager.kt` line 72**

```kotlin
scope.launch {
    try {
        repo.syncInBackground()
        delay(500)
        repo.packs.forEach { downloadPack(it) }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w("StickerDL", "Download failed: ${e.message}") }
}
```

---

## CR9：SettingsActivity modelManager.manualUpdate

**`ui/settings/SettingsActivity.kt` line 196**

異常時按鈕永遠卡在「檢查中...」。

```kotlin
activityScope.launch {
    try {
        modelManager.manualUpdate(...)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        withContext(Dispatchers.Main) {
            btn.text = "檢查失敗"
            btn.isEnabled = true
        }
    }
}
```

---

# 🟡 第四批：執行緒安全（12 項）

---

## T1：FlowLightRenderer 缺 @Volatile

**`animation/gles/FlowLightRenderer.kt` line 30-34**

`alpha`、`colorR`、`colorG`、`colorB` 主執行緒寫、GL 渲染緒讀。

```kotlin
@Volatile var alpha: Float = 0f
@Volatile private var colorR = 0.4f
@Volatile private var colorG = 0.6f
@Volatile private var colorB = 1.0f
```

---

## T2：KeyboardGLSurfaceView surfaceWidth/Height 缺 @Volatile

**`animation/gles/KeyboardGLSurfaceView.kt` line 37-38**

主執行緒寫、渲染緒讀。

```kotlin
@Volatile private var surfaceWidth = 0
@Volatile private var surfaceHeight = 0
```

---

## T3：KeyboardGLSurfaceView startRenderThread 競態

**`animation/gles/KeyboardGLSurfaceView.kt` line 95-97**

`onResume` + `onSurfaceTextureAvailable` 同時呼叫可能啟動兩個渲染緒。

```kotlin
@Synchronized
private fun startRenderThread() {
    if (running) return
    running = true
    // ...
}
```

---

## T4：ShakeDetector 缺 @Volatile

**`sensor/ShakeDetector.kt` line 22-23**

```kotlin
@Volatile var onShakeListener: (() -> Unit)? = null
@Volatile var enabled: Boolean = false
```

---

## T5：GyroscopeCursorController 缺 @Volatile

**`sensor/GyroscopeCursorController.kt` line 28-29**

```kotlin
@Volatile private var active = false
@Volatile private var enabled = false
```

---

## T6：WhisperSpeechEngine isListening + audioRecord 競態

**`speech/WhisperSpeechEngine.kt` line 25, 176-182**

```kotlin
@Volatile override var isListening: Boolean = false

// releaseAudioRecord() 加 @Synchronized：
@Synchronized
private fun releaseAudioRecord() { ... }
```

---

## T7：FasterWhisperSpeechEngine 同上

**`speech/FasterWhisperSpeechEngine.kt` line 31, 180-184**

```kotlin
@Volatile override var isListening: Boolean = false

@Synchronized
private fun releaseAudioRecord() { ... }
```

---

## T8：AndroidTtsEngine pendingCallbacks 未同步

**`speech/AndroidTtsEngine.kt` line 23**

TTS 回調緒 + 主執行緒同時存取 MutableMap。

```kotlin
// 改用 ConcurrentHashMap：
private val pendingCallbacks = java.util.concurrent.ConcurrentHashMap<String, (() -> Unit)?>()
```

---

## T9：AdaptiveDictionary entries 未同步

**`input/smart/AdaptiveDictionary.kt` line 23**

```kotlin
// 改用 ConcurrentHashMap：
private val entries = java.util.concurrent.ConcurrentHashMap<String, WordEntry>()
```

---

## T10：PersonalDictionary entries 未同步

**`input/smart/PersonalDictionary.kt` line 22**

```kotlin
private val entries = java.util.concurrent.ConcurrentHashMap<String, PersonalWord>()
```

---

## T11：BluetoothHidKeyboard 缺 @Volatile

**`remote/BluetoothHidKeyboard.kt` line 94-95**

```kotlin
@Volatile private var hidDevice: BluetoothHidDevice? = null
@Volatile private var hostDevice: BluetoothDevice? = null
```

---

## T12：WifiKeyboardServer 缺 @Volatile

**`remote/WifiKeyboardServer.kt` line 54, 61**

```kotlin
@Volatile var stateListener: ((ConnectionState) -> Unit)? = null
@Volatile var connectionPin: String = ""
```

---

# 🟡 第五批：記憶體洩漏（8 項）

---

## M1：StickerPanelView CoroutineScope 永不 cancel

**`ui/panel/StickerPanelView.kt` line 120**

`onDestroyView()` 存在但從未被呼叫。

### IMEService 關閉面板時加呼叫：

```kotlin
// closePanel() 或 onDestroy() 裡：
stickerPanel?.onDestroyView()
stickerPanel = null
```

---

## M2：StickerPanelView callback 持有 View 強引用

**`ui/panel/StickerPanelView.kt` line 64, 75**

`onPacksUpdated` / `onProgressUpdated` / `onAllCompleted` detach 時未清除。

```kotlin
// 新增 onDetachedFromWindow：
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    repository?.onPacksUpdated = null
    downloadManager?.onProgressUpdated = null
    downloadManager?.onAllCompleted = null
}
```

---

## M3：StickerRepository.onPacksUpdated destroy 未置 null

**`sticker/StickerRepository.kt` line 31**

```kotlin
fun destroy() {
    scope.cancel()
    onPacksUpdated = null  // ← 新增
}
```

---

## M4：StickerPackDownloadManager callbacks destroy 未置 null

**`sticker/StickerPackDownloadManager.kt` line 33**

```kotlin
fun destroy() {
    scope.cancel()
    onProgressUpdated = null   // ← 新增
    onAllCompleted = null      // ← 新增
}
```

---

## M5：VrmCharacterAnimation 匿名 postDelayed

**`animation/VrmCharacterAnimation.kt` line 99**

`postDelayed({ clearExpressions(); onEnd() }, 1000)` — `stop()` 無法 removeCallbacks。

```kotlin
// 改為具名 Runnable：
private var pendingOnEnd: Runnable? = null

// play() 裡：
pendingOnEnd?.let { view.removeCallbacks(it) }
pendingOnEnd = Runnable {
    view.clearExpressions()
    onEnd()
}.also { view.postDelayed(it, 1000) }

// stop() 裡：
pendingOnEnd?.let { view.removeCallbacks(it) }
pendingOnEnd = null
```

---

## M6：VrmCharacterView 匿名 postDelayed re-arm

**`animation/VrmCharacterView.kt` line 144**

`postDelayed({ choreographer?.postFrameCallback(this) }, 64)` — destroy 後仍觸發。

```kotlin
// 改為具名 Runnable：
private val lowFpsRearmRunnable = Runnable {
    if (!isDestroyed) choreographer?.postFrameCallback(frameCallback)
}

// frameCallback 裡：
if (hasActiveAnimation()) {
    choreographer?.postFrameCallback(this)
} else {
    removeCallbacks(lowFpsRearmRunnable)
    postDelayed(lowFpsRearmRunnable, 64)
}

// destroy() 裡加：
removeCallbacks(lowFpsRearmRunnable)
```

---

## M7：IMEService 匿名 postDelayed fraud-detect

**`service/YukiBoardIMEService.kt` line 890**

```kotlin
// 改為具名 Runnable + onDestroy 清除：
private val fraudDetectRunnable = Runnable {
    (panelContainer.getChildAt(0) as? ClawPanelView)?.triggerSkill("fraud_detect")
}

// 使用處：
charAnchor.removeCallbacks(fraudDetectRunnable)
charAnchor.postDelayed(fraudDetectRunnable, 300)

// onDestroy：
characterView?.removeCallbacks(fraudDetectRunnable)
```

---

## M8：SettingsActivity MediaPlayer 洩漏

**`ui/settings/SettingsActivity.kt` line 986**

MediaPlayer 在 IO 緒建立，surface 提前銷毀時 mp 洩漏。

```kotlin
// 改為主緒建立 + onDestroy 保底釋放：
override fun onDestroy() {
    super.onDestroy()
    bgVideoPlayer?.release()
    bgVideoPlayer = null
    activityScope.cancel()
}
```

---

# 🟢 第六批：邏輯 / 顯示（12 項）

---

## L1：ClipboardPanelView indexOf -1 汙染映射

**`ui/panel/ClipboardPanelView.kt` line 710, 757**

`clipHistory.indexOf(item)` 回傳 -1 時 rect-to-index 映射錯誤。

```kotlin
val globalIdx = clipHistory.indexOf(item)
if (globalIdx < 0) continue  // ← 加 guard
```

---

## L2：InputRouter indexOf -1 靜默切模式

**`input/router/InputRouter.kt` line 163**

```kotlin
val currentIndex = enabledModes.indexOf(currentMode)
if (currentIndex < 0) return  // ← 加 guard
```

---

## L3：IMEService indexOfChild -1 插入位置錯

**`service/YukiBoardIMEService.kt` line 1529**

```kotlin
val toolbarIndex = rootContainer.indexOfChild(toolbarView)
if (toolbarIndex < 0) return  // ← 加 guard
```

---

## L4：GameKeyboardView 除以零

**`game/GameKeyboardView.kt` line 180**

```kotlin
val totalUnits = row.sumOf { it.widthWeight.toDouble() }.toFloat()
if (totalUnits <= 0f) return  // ← 加 guard
```

---

## L5：GeminiProvider SSE 空 catch

**`claw/provider/GeminiProvider.kt` line 116**

```kotlin
} catch (e: Exception) {
    Log.w("Gemini", "SSE parse error: ${e.message}")
}
```

---

## L6：GroqProvider SSE 空 catch

**`claw/provider/GroqProvider.kt` line 105**

同上加 Log。

---

## L7：SnowRealmProvider SSE 空 catch

**`claw/provider/SnowRealmProvider.kt` line 126**

同上加 Log。

---

## L8：FraudAIAnalyzer 空 catch

**`security/FraudAIAnalyzer.kt` line 548**

AI 分析失敗完全靜默，使用者不知道。

```kotlin
} catch (e: Exception) {
    Log.e("FraudAI", "Analysis failed: ${e.message}")
    // 通知 UI 分析失敗
}
```

---

## L9：ClipboardPanelView 加解密空 catch

**`ui/panel/ClipboardPanelView.kt` line 412**

```kotlin
} catch (e: Exception) {
    Log.w("Clipboard", "Crypto operation failed: ${e.message}")
}
```

---

## L10：YukiPreferences write 空 catch

**`ui/settings/YukiPreferences.kt` line 248**

UI 顯示新值但未持久化。

```kotlin
} catch (e: Exception) {
    Log.e("Prefs", "Write failed: ${e.message}")
}
```

---

## L11：SettingsActivity 多處 (btn as TextView) 硬轉型

**`ui/settings/SettingsActivity.kt` line 2285, 2289, 2390, 2395, 2406, 2415, 2463**

```kotlin
// 全部改為安全轉型：
(btn as? TextView)?.text = "..."
```

---

## L12：InputRouter 字體載入空 catch

**`input/router/InputRouter.kt` line 509**

```kotlin
} catch (e: Exception) {
    Log.w("InputRouter", "Font load failed: ${e.message}")
}
```

---

# 執行順序

```
第一批（崩潰）：
  C1  ClipboardEncryptionHelper as? 安全轉型
  C2  EmotionStickerEngine ?: return null
  C3  SettingsActivity ?: return
  C4  KeyboardGLSurfaceView ?: return
  C5  MusicTypingGame rows guard
  C6  RhythmGame firstOrNull / lastOrNull
  Build → 測試

第二批（功能缺失）：
  F1  when(game) 補齊 7 種遊戲 onGameEvent
  F2  Toast 包 Handler(mainLooper).post
  Build → 測試

第三批（Coroutine）：
  CR1-CR9  全部加 try-catch + CancellationException rethrow
  Build → 測試

第四批（執行緒安全）：
  T1-T2   @Volatile（FlowLightRenderer + GLSurfaceView）
  T3      @Synchronized startRenderThread
  T4-T5   @Volatile（ShakeDetector + GyroscopeController）
  T6-T7   @Volatile + @Synchronized（Whisper + FasterWhisper）
  T8-T10  ConcurrentHashMap（TTS + Dictionary）
  T11-T12 @Volatile（Bluetooth + WiFi）
  Build → 測試

第五批（記憶體洩漏）：
  M1  StickerPanelView scope cancel 呼叫
  M2  StickerPanelView onDetachedFromWindow 清 callback
  M3-M4  Repository/DownloadManager destroy 置 null
  M5-M7  VrmAnim/VrmView/IMEService 具名 Runnable
  M8  SettingsActivity MediaPlayer 保底釋放
  Build → 測試

第六批（邏輯）：
  L1-L3   indexOf guard
  L4      除以零 guard
  L5-L12  空 catch 加 Log + 安全轉型
  Build → 測試
```

---

# 驗證清單

```
崩潰防護：
□ 剪貼簿加密正常（KeyStore 異常不崩潰）
□ 情緒貼圖空集合不崩潰
□ 設定頁操作不崩潰
□ GL 流光初始化失敗不崩潰
□ 節奏遊戲 / 音樂打字遊戲正常啟動

功能完整：
□ 7 種遊戲事件（分數、勝負）正常回報
□ 詞庫匯入/匯出 Toast 正常顯示

Coroutine：
□ 翻譯面板網路斷線 → 顯示錯誤訊息（不卡死）
□ GIF 面板網路斷線 → 顯示空結果（不永遠 loading）

執行緒安全：
□ 快速切換流光顏色 / 透明度不閃退
□ 搖手機復原正常觸發
□ 藍牙鍵盤配對 → 傳送按鍵正常

記憶體：
□ 開關貼圖面板 10 次 → 記憶體不持續增長
□ LeakCanary 無 StickerPanelView 洩漏報告
```

---

## 統計

| 分類 | 數量 | 嚴重度 |
|------|------|--------|
| 崩潰風險 (C) | 6 | 🔴 高 |
| 功能缺失 (F) | 2 | 🔴 高 |
| Coroutine (CR) | 9 | 🟡 中 |
| 執行緒安全 (T) | 12 | 🟡 中 |
| 記憶體洩漏 (M) | 8 | 🟡 中 |
| 邏輯/顯示 (L) | 12 | 🟢 低 |
| **合計** | **49** | |
