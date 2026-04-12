# YukiBoard 緊急修復任務包
# 設定閃退 / 主題同步 / 功能列背景預覽 / 效能耗電

> 基於 YukiBoard-main__3_.zip（181 個 .kt 檔）
> 這些是上架前必須修好的問題

---

## 問題診斷總結

```
問題一：設定閃退（平常）
原因：reloadFullPreviewBitmap() 在 Main thread 做 MediaMetadataRetriever
     影片檔解幀是同步 IO，會 ANR → 系統強制殺掉 Activity

問題二：影片背景時設定閃更快
原因：同上，影片解幀比圖片慢更多，加上 startVideoPreview()
     在 onCreateInternal() 被 refreshBgPreview() 呼叫，
     影片 MediaPlayer 初始化本身也在 Main thread

問題三：功能列主題 ≠ 設定頁主題（不同步）
原因：ThemePanelView 在開啟時只設一次 currentThemeId，
     使用者在設定頁換主題後，ThemePanelView 不會收到通知

問題四：功能列點照片/影片沒有預覽
原因：BackgroundPickerActivity 選完圖後只儲存檔案，
     沒有通知 ThemePanelView 重新載入預覽圖

問題五：效能耗電
主要來源：
  - FlowLight GL render thread：16ms loop 持續跑（60fps 不停）
  - KeyboardParticleView：postInvalidateOnAnimation 持續跑
  - CharacterView wandering：ObjectAnimator 循環
  - KeyboardView comboGlowAlpha > 0：postInvalidateDelayed(16) 持續
  - MediaPlayer 影片背景：硬體解碼持續消耗
```

---

## 修復一：設定不閃退（最緊急）

### 根本原因

`reloadFullPreviewBitmap()` 在 Main thread 呼叫了：
```kotlin
val retriever = android.media.MediaMetadataRetriever()
retriever.setDataSource(savedBg.absolutePath)  // IO
val frame = retriever.getFrameAtTime(0)         // 解碼，可能 > 1秒
retriever.release()
```

這是同步 IO，在 Main thread 執行超過 5 秒就 ANR → 閃退。

### 修改 `ui/settings/SettingsActivity.kt`

**1. `reloadFullPreviewBitmap()` 改成非同步：**

```kotlin
private fun reloadFullPreviewBitmap() {
    val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView
        .getSavedBackground(this)

    if (savedBg == null) {
        fullPreviewBgBitmap = null
        fullPreviewView?.invalidate()
        return
    }

    // ← 改成 coroutine IO thread，不阻塞 Main thread
    activityScope.launch(Dispatchers.IO) {
        val bmp = try {
            if (com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView
                    .isVideoFile(savedBg)) {
                // 影片：取第一幀縮圖
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(savedBg.absolutePath)
                    retriever.getFrameAtTime(
                        0,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                } finally {
                    retriever.release()
                }
            } else {
                // 圖片：低解析度快速載入
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 4
                }
                android.graphics.BitmapFactory.decodeFile(savedBg.absolutePath, opts)
            }
        } catch (_: Exception) { null }

        withContext(Dispatchers.Main) {
            fullPreviewBgBitmap = bmp
            fullPreviewView?.invalidate()
        }
    }
}
```

**2. `refreshBgPreview()` 的 IO 操作同樣改非同步：**

找到 `refreshBgPreview()` 裡這段：
```kotlin
// 原本（同步 IO）：
activityScope.launch(Dispatchers.IO) {
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
    val bmp = android.graphics.BitmapFactory.decodeFile(savedBg.absolutePath, opts)
    withContext(Dispatchers.Main) {
        bgPreviewImage?.setImageBitmap(bmp)
        ...
    }
}
```
這段已經是 IO thread ✅，不需改。確認 `refreshBgPreview()` 本身沒有在 Main thread 呼叫同步 IO。

**3. `startVideoPreview()` 確認 MediaPlayer 在 background thread：**

`startVideoPreview()` 使用 `TextureView.SurfaceTextureListener`，
MediaPlayer 的 `setDataSource` 和 `prepareAsync()` 已在 callback 裡，
`prepareAsync` 本身是非同步的 ✅，但 `setDataSource(filePath)` 如果是本機路徑，
可能阻塞 — 改成包一層：

```kotlin
override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
    // ← 移到 background thread，避免 SurfaceTexture callback 卡 Main thread
    activityScope.launch(Dispatchers.IO) {
        try {
            val mp = android.media.MediaPlayer().apply {
                setDataSource(filePath)  // IO，在 background thread
            }
            withContext(Dispatchers.Main) {
                try {
                    mp.setSurface(android.view.Surface(st))
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    mp.setOnPreparedListener { player ->
                        bgVideoWidth = player.videoWidth
                        bgVideoHeight = player.videoHeight
                        updateVideoPreviewTransform()
                        player.start()
                    }
                    mp.prepareAsync()
                    bgVideoPlayer = mp
                } catch (e: Exception) {
                    bgPreviewLabel?.text = "🎬 影片播放失敗"
                    bgPreviewLabel?.visibility = View.VISIBLE
                    try { mp.release() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                bgPreviewLabel?.text = "🎬 影片載入失敗"
                bgPreviewLabel?.visibility = View.VISIBLE
            }
        }
    }
}
```

---

## 修復二：功能列主題與設定頁同步

### 根本原因

`ThemePanelView` 的 `currentThemeId` 只在面板建立時設一次：
```kotlin
p.currentThemeId = tm.currentTheme.id  // 建立時
p.availableThemes = tm.availableThemes
```

使用者去設定頁換了主題，`ThemeManager.currentTheme` 變了，
但 `ThemePanelView` 不知道，顯示的 checkmark 還在舊主題上。

### 修改 `theme/ThemeManager.kt`

`ThemeManager` 只有一個 listener（`_onThemeChanged`），
如果 IMEService 設了，設定頁再設就會覆蓋。

改成支援多個 listener：

```kotlin
private val themeChangedListeners = mutableListOf<(KeyboardTheme) -> Unit>()

fun addOnThemeChangedListener(listener: (KeyboardTheme) -> Unit) {
    themeChangedListeners.add(listener)
}

fun removeOnThemeChangedListener(listener: (KeyboardTheme) -> Unit) {
    themeChangedListeners.remove(listener)
}

// currentTheme setter 改為通知所有 listener
set(value) {
    prefs.edit().putString(KEY_THEME_ID, value.id).apply()
    themeChangedListeners.forEach { it(value) }
}

// 保留舊的 setOnThemeChangedListener 向下相容（只設第一個）
fun setOnThemeChangedListener(listener: (KeyboardTheme) -> Unit) {
    themeChangedListeners.clear()
    themeChangedListeners.add(listener)
}
```

### 修改 `service/YukiBoardIMEService.kt`

在 ThemePanel 建立時，讓 `ThemePanelView` 訂閱主題變更：

```kotlin
p.onThemeSelected = { selected ->
    tm.currentTheme = selected
    prefs.useCustomBackground = false
    applyTheme(selected)
    p.previewBgBitmap = null
}

// ← 新增：讓 ThemePanelView 即時收到主題變更
val themeListener: (KeyboardTheme) -> Unit = { newTheme ->
    p.currentThemeId = newTheme.id
    p.theme = newTheme
    p.invalidate()
}
tm.addOnThemeChangedListener(themeListener)

// 面板關閉時移除 listener（避免 leak）
p.addOnDetachListener {
    tm.removeOnThemeChangedListener(themeListener)
}
```

### 在 `ThemePanelView.kt` 加 detach listener

```kotlin
private var onDetachCallback: (() -> Unit)? = null

fun addOnDetachListener(callback: () -> Unit) {
    onDetachCallback = callback
}

override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    onDetachCallback?.invoke()
}
```

---

## 修復三：功能列選完圖片/影片立即預覽

### 根本原因

`BackgroundPickerActivity` 選完圖後只儲存檔案，`finish()` 後
沒有通知 `ThemePanelView` 重新載入預覽圖。

IMEService 在 `onStartInputView` 才會 `reloadFromStorage()`，
但使用者不會重新打開鍵盤，所以功能列看不到新背景。

### 修改 `ui/settings/BackgroundPickerActivity.kt`

在儲存完檔案後，發送 LocalBroadcast 通知：

```kotlin
// 在 onActivityResult 儲存成功後加：
prefs.useCustomBackground = true

// 發廣播通知 IMEService 重載背景
val intent = android.content.Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
intent.setPackage(packageName)
sendBroadcast(intent)

Toast.makeText(this, "背景已設定", Toast.LENGTH_SHORT).show()
finish()
```

### 修改 `service/YukiBoardIMEService.kt`

在 `onCreate()` 或 `onCreateInputView()` 註冊廣播接收器：

```kotlin
private var bgUpdateReceiver: android.content.BroadcastReceiver? = null

// 在 onCreateInputView() 加：
bgUpdateReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
        // 背景更新了，重載鍵盤背景 + 通知 ThemePanelView
        backgroundView?.let { bg ->
            // 強制重載（清掉 currentLoadedPath 讓 reloadFromStorage 真的重載）
            bg.clearBackground()
            bg.reloadFromStorage()
        }
        keyboardView.hasCustomBackground = prefs.useCustomBackground
        keyboardView.invalidate()

        // 通知 ThemePanelView 更新預覽
        themePanel?.reloadPreviewBitmap()
        themePanel?.invalidate()
    }
}
val filter = android.content.IntentFilter("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
registerReceiver(bgUpdateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
```

在 `onDestroy()` 加：
```kotlin
bgUpdateReceiver?.let { unregisterReceiver(it) }
bgUpdateReceiver = null
```

---

## 修復四：效能 / 耗電

### 最大問題：FlowLight GL render thread 持續 60fps

```kotlin
// 現在：while (running) { render(); sleep(16) }
// 全程持續渲染，鍵盤不在螢幕上也跑
```

**修改 `animation/gles/KeyboardGLSurfaceView.kt`**

加入 `flowLightAlpha` 控制：alpha 為 0 時停止渲染：

```kotlin
private fun startRenderThread() {
    if (running) return
    running = true

    renderThread = Thread({
        initEGL()
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, surfaceWidth, surfaceHeight)

        while (running) {
            // alpha 為 0（功能關閉）時降低到 2fps 節能
            if (renderer.alpha <= 0.01f) {
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                continue
            }

            val w = surfaceWidth; val h = surfaceHeight
            GLES20.glViewport(0, 0, w, h)
            renderer.onSurfaceChanged(null, w, h)
            renderer.onDrawFrame(null)

            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) break
            try { Thread.sleep(16) } catch (_: InterruptedException) { break }
        }
        releaseEGL()
    }, "FlowLightGL").also { it.start() }
}
```

### 問題：KeyboardParticleView 持續 postInvalidateOnAnimation

粒子不在螢幕上也一直叫 `invalidate()`。

**修改 `animation/KeyboardParticleView.kt`**

在 `onDraw` 結尾改為：

```kotlin
override fun onDraw(canvas: Canvas) {
    if (!running || width == 0 || height == 0) return
    // ... 繪製邏輯不變 ...

    // 還有活著的粒子才繼續 invalidate，否則停下來
    if (particles.isNotEmpty()) {
        postInvalidateOnAnimation()
    }
    // running = false 或粒子全死，自動停止
}
```

### 問題：comboGlowAlpha > 0 持續 postInvalidateDelayed(16)

**修改 `ui/keyboard/KeyboardView.kt`**

找到：
```kotlin
if (comboParticles.isNotEmpty() || comboGlowAlpha > 0f) {
    postInvalidateDelayed(16)
}
```

改為：
```kotlin
if (comboParticles.isNotEmpty() || comboGlowAlpha > 0f) {
    postInvalidateDelayed(32)  // 30fps 夠了，降低到 32ms
}
```

### 問題：CharacterView 遊走動畫持續消耗 CPU

遊走動畫用 `ObjectAnimator` + `postDelayed` 循環，即使鍵盤在背景也跑。

**修改 `ui/keyboard/CharacterView.kt`**

在 `onVisibilityChanged` 加暫停邏輯：

```kotlin
override fun onVisibilityChanged(changedView: View, visibility: Int) {
    super.onVisibilityChanged(changedView, visibility)
    if (visibility == VISIBLE) {
        if (wanderEnabled) startWandering()
    } else {
        stopWandering()  // 不可見時停止動畫
    }
}
```

### 問題：影片背景持續解碼耗電

影片作為鍵盤背景是最耗電的功能。加一個警告：

**修改 `service/YukiBoardIMEService.kt`**

在 `onStartInputView()` 裡，偵測到影片背景時：

```kotlin
val savedBg = KeyboardBackgroundView.getSavedBackground(this)
if (savedBg != null && KeyboardBackgroundView.isVideoFile(savedBg)) {
    // 影片背景耗電，通知使用者（每次開啟鍵盤只提示一次）
    if (!prefs.videoBgWarnShown) {
        prefs.videoBgWarnShown = true
        keyboardView.showToast("提示：影片背景較耗電，可在主題設定改用圖片")
    }
}
```

在 `YukiPreferences.kt` 加：
```kotlin
var videoBgWarnShown: Boolean by BoolPref("video_bg_warn_shown", false)
```

### 問題：影片在設定頁 onPause 沒停止

**修改 `ui/settings/SettingsActivity.kt`**

加入 `onPause()`：

```kotlin
override fun onPause() {
    super.onPause()
    // 設定頁離開時停止影片預覽，節省資源
    bgVideoPlayer?.pause()
}

override fun onResume() {
    super.onResume()
    bgVideoPlayer?.start()
}
```

---

## 執行順序

```
修復一（最優先）：
  SettingsActivity.reloadFullPreviewBitmap() → 改 IO thread
  SettingsActivity.startVideoPreview() → setDataSource 移到 IO thread
  → Build 測試：設定不再閃退

修復二：
  ThemeManager 改成 multi-listener
  IMEService ThemePanel 訂閱主題變更
  ThemePanelView 加 detach listener
  → 測試：設定頁換主題，功能列即時同步

修復三：
  BackgroundPickerActivity 加 sendBroadcast
  IMEService 加 BroadcastReceiver
  → 測試：功能列選圖，立即看到預覽

修復四（效能）：
  FlowLight alpha=0 時降到 2fps
  KeyboardParticleView 粒子空時不 invalidate
  KeyboardView combo 改 32ms
  CharacterView onVisibilityChanged 加暫停
  SettingsActivity onPause 暫停影片
  YukiPreferences 加 videoBgWarnShown
```

---

## 驗證清單

```
□ 開設定不閃退（無影片背景）
□ 開設定不閃退（有影片背景）
□ 設定頁換主題 → 功能列主題同步更新
□ 功能列換圖 → 功能列縮圖立即更新
□ 功能列換影片 → 功能列縮圖立即更新（取第一幀）
□ 無連擊時 KeyboardView 不持續 invalidate
□ FlowLight 關閉時 GL thread 耗電降低
□ 影片背景時顯示耗電提示
```
