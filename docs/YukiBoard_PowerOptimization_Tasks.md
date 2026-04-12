# YukiBoard v5 省電 + 效能優化任務包

> 影片背景正常運作的前提下最大化省電

---

## 耗電來源診斷

```
🔴 最耗電：
  1. VrmCharacterView — Choreographer 60fps 持續渲染，鍵盤收起時仍在跑
  2. KeyboardGLSurfaceView（流光）— 獨立 Thread 60fps，有做 alpha=0 時降 2fps
  3. MediaPlayer 影片背景 — 沒有 pause/resume API，鍵盤收起後仍在解碼

🟡 中度耗電：
  4. KeyboardView invalidate() — 43 個呼叫點，部分不必要
  5. stickerRepository.syncInBackground() — 每次 Service 建立就執行
  6. WeatherFloatingPanel — 持續輪詢天氣

🔵 輕微：
  7. AI 補字 debounce — 600ms 呼叫 AI，已有 cancel，OK
  8. ShakeDetector / GyroController — onFinishInput 有 stop，OK
```

---

## 優化一：影片背景 pause/resume（最重要）

### 問題
`KeyboardBackgroundView` 沒有 `pause()` / `resume()` API，
鍵盤收起後 MediaPlayer 還在持續解碼影片（最耗電的操作之一）。

### 修改 `ui/keyboard/KeyboardBackgroundView.kt`

新增 pause/resume：

```kotlin
/**
 * 鍵盤收起時暫停影片（節省 CPU + GPU）
 */
fun pause() {
    mediaPlayer?.let { mp ->
        if (mp.isPlaying) {
            mp.pause()
        }
    }
}

/**
 * 鍵盤顯示時繼續影片
 */
fun resume() {
    mediaPlayer?.let { mp ->
        if (!mp.isPlaying) {
            try { mp.start() } catch (_: Exception) {}
        }
    }
}
```

### 修改 `service/YukiBoardIMEService.kt`

在 `onFinishInputView()` 加 pause，在 `onStartInputView()` 加 resume：

```kotlin
override fun onFinishInputView(finishingInput: Boolean) {
    super.onFinishInputView(finishingInput)
    // ... 原有邏輯 ...
    glSurfaceView?.onPause()
    backgroundView?.pause()    // ← 新增：影片背景暫停
    gameLoop?.pause()
}

override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    // ... 原有邏輯 ...
    glSurfaceView?.onResume()
    backgroundView?.resume()   // ← 新增：影片背景恢復
}
```

---

## 優化二：VrmCharacterView 鍵盤收起時停止渲染

### 問題
`CharacterView` 沒有 `onPause()` / `onResume()`，
`VrmCharacterView` 的 Choreographer 60fps 在鍵盤收起後繼續跑。

### 修改 `ui/keyboard/CharacterView.kt`

新增 pause/resume：

```kotlin
fun pauseAnimation() {
    vrmView?.stopRendering()      // 停 Choreographer
    lottieView?.pauseAnimation()  // 停 Lottie
    wanderEnabled = false
    stopWandering()
}

fun resumeAnimation() {
    vrmView?.startRendering()
    lottieView?.resumeAnimation()
    wanderEnabled = true
    post { startWandering() }
}
```

同時讓 `VrmCharacterView.startRendering()` 和 `stopRendering()` 從 private 改為 internal：

```kotlin
internal fun startRendering() {
    choreographer?.postFrameCallback(frameCallback)
}

internal fun stopRendering() {
    choreographer?.removeFrameCallback(frameCallback)
}
```

### 修改 `service/YukiBoardIMEService.kt`

```kotlin
override fun onFinishInputView(finishingInput: Boolean) {
    // ... 原有邏輯 ...
    glSurfaceView?.onPause()
    backgroundView?.pause()
    characterView?.pauseAnimation()    // ← 新增
    gameLoop?.pause()
}

override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    // ... 原有邏輯 ...
    glSurfaceView?.onResume()
    backgroundView?.resume()
    if (prefs.characterAnimEnabled) {
        characterView?.resumeAnimation()   // ← 新增
    }
}
```

---

## 優化三：流光效果 alpha=0 時完全停止 Thread

### 現況
`KeyboardGLSurfaceView` 已有 alpha=0 時降到 500ms 輪詢（2fps），
但 Thread 仍在跑，還是有 CPU 喚醒開銷。

### 修改 `animation/gles/KeyboardGLSurfaceView.kt`

在 `startRenderThread()` 的 while loop 裡，alpha=0 時完全暫停並用 wait：

```kotlin
// 原本：
while (running) {
    if (renderer.alpha <= 0.01f) {
        try { Thread.sleep(500) } catch (_: InterruptedException) { break }
        continue
    }
    // ... 渲染 ...
    try { Thread.sleep(16) } catch (_: InterruptedException) { break }
}

// 改為：
while (running) {
    if (renderer.alpha <= 0.01f) {
        // alpha=0：完全暫停渲染，等待外部喚醒
        synchronized(this) {
            try { (this as Object).wait(2000) }  // 最多等 2 秒自動醒
            catch (_: InterruptedException) { break }
        }
        continue
    }
    // ... 渲染 ...
    try { Thread.sleep(16) } catch (_: InterruptedException) { break }
}
```

同時在 `setFlowAlpha()` 裡，alpha 從 0 變成非 0 時喚醒 Thread：

```kotlin
fun setFlowAlpha(alpha: Float) {
    val wasZero = renderer.alpha <= 0.01f
    renderer.alpha = alpha.coerceIn(0f, 1f)
    // 從 0 變非 0：喚醒渲染 Thread
    if (wasZero && alpha > 0.01f) {
        synchronized(this) { (this as Object).notifyAll() }
    }
}
```

---

## 優化四：影片背景降低影格率

### 問題
MediaPlayer 預設以影片原始 fps 播放（通常 30fps 或 60fps），
但鍵盤背景不需要這麼高的幀率，24fps 已經非常流暢。

### 方法：在 MediaPlayer 準備好後設定 playback params

```kotlin
// 在 setOnPreparedListener 裡加：
setOnPreparedListener { player ->
    this@KeyboardBackgroundView.videoWidth = player.videoWidth
    this@KeyboardBackgroundView.videoHeight = player.videoHeight
    applyVideoTransform()

    // ← 新增：設定播放速度，間接降低解碼頻率
    // 保持原速播放，但確保不超過 30fps
    try {
        val params = player.playbackParams.apply {
            // speed = 1.0 正常速，audioFallbackMode 確保音訊同步
            speed = 1.0f
        }
        player.playbackParams = params
    } catch (_: Exception) {}

    player.start()
}
```

**更有效的方法：讓使用者上傳時壓縮成低 fps 版本（24fps、低解析度）**

在 `BackgroundPickerActivity` 上傳影片時，加一個壓縮提示：

```kotlin
// 顯示提示給使用者
Toast.makeText(
    this,
    "建議使用 30 秒以內、720p 以下的影片以節省電量",
    Toast.LENGTH_LONG
).show()
```

---

## 優化五：stickerRepository 延遲同步

### 問題
`stickerRepository?.syncInBackground()` 在每次 Service 建立時就執行，
即使使用者沒有開貼圖面板。

### 修改 `service/YukiBoardIMEService.kt`

```kotlin
// 原本（Service 啟動就跑）：
stickerRepository = StickerRepository(applicationContext)
stickerRepository?.syncInBackground()

// 改為（延遲到第一次開貼圖面板才同步）：
stickerRepository = StickerRepository(applicationContext)
// syncInBackground() 移到貼圖面板第一次開啟時呼叫
```

在開啟貼圖面板的地方（約 `PanelType.STICKER`）加：

```kotlin
PanelType.STICKER -> {
    val p = StickerPanelView(this).also { stickerPanel = it }
    p.theme = theme
    p.repository = stickerRepository
    // ← 新增：第一次開才同步
    if (!stickerSyncDone) {
        stickerRepository?.syncInBackground()
        stickerSyncDone = true
    }
    p
}
```

新增 flag：
```kotlin
private var stickerSyncDone = false
```

---

## 優化六：KeyboardView 減少不必要的 invalidate

### 問題
`invalidate()` 觸發整個 KeyboardView 重繪（包含所有按鍵），
有 43 個呼叫點，部分是在只有候選字變化時，不需要重繪按鍵區。

### 改善策略：用 `invalidate(Rect)` 只重繪需要的區域

```kotlin
// 只有候選列變化時，只重繪候選列區域
private fun invalidateCandidateBar() {
    invalidate(0, 0, width, candidateBarHeight.toInt())
}

// 在 updateCandidates() 裡改用：
fun updateCandidates(list: List<String>, ...) {
    // ... 邏輯 ...
    invalidateCandidateBar()  // 而不是 invalidate()
}

// 在 updateComposingText() 裡：
fun updateComposingText(text: String) {
    if (composingText == text) return
    composingText = text
    invalidateCandidateBar()  // 而不是 invalidate()
}
```

---

## 優化七：VrmCharacterView needsRender 優化

### 現況
`VrmCharacterView` 有 `needsRender` flag，但 `render()` 裡沒有完全利用它。

```kotlin
private fun render() {
    if (!isReady || isDestroyed || !needsRender) return
    // ... 渲染 ...
}
```

### 優化：沒有動畫時降低 fps

```kotlin
private val frameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
        if (isDestroyed) return

        val dt = if (lastFrameTimeNanos == 0L) 1f / 60f
                 else (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
        lastFrameTimeNanos = frameTimeNanos

        update(dt)
        render()

        // ← 新增：如果沒有任何動畫在跑，降到 15fps（每 4 幀才重繪一次）
        if (hasActiveAnimation()) {
            choreographer?.postFrameCallback(this)  // 60fps
        } else {
            // 無動畫：1秒後才重繪（基本上靜止）
            postDelayed({ choreographer?.postFrameCallback(this) }, 64)  // ~15fps
        }
    }
}

private fun hasActiveAnimation(): Boolean {
    return isAnimating ||
        isBlinking ||
        proceduralAction != null ||
        targetExpressionWeights.any { it.value > 0.01f } ||
        currentExpressionWeights.any { it.value > 0.01f }
}
```

---

## 優化八：影片背景解析度限制

### 在 `BackgroundPickerActivity.kt` 上傳影片前做解析度提示

```kotlin
// 使用者選了影片後，檢查解析度和時長
private fun validateVideoFile(file: File): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0

        when {
            width > 1280 || height > 720 -> {
                showWarning("影片解析度較高（${width}x${height}），可能較耗電\n建議使用 720p 以下")
                true  // 仍允許，只是警告
            }
            durationMs > 60_000 -> {
                showWarning("影片超過 60 秒，建議使用較短的循環片段")
                true
            }
            else -> true
        }
    } catch (_: Exception) {
        true
    } finally {
        retriever.release()
    }
}
```

---

## 省電效果預估

| 優化項目 | 預估省電 | 難度 |
|---------|---------|------|
| 影片背景 pause/resume | ⭐⭐⭐⭐⭐ 最大 | 簡單 |
| VRM 鍵盤收起時停止 | ⭐⭐⭐⭐ | 簡單 |
| 流光 alpha=0 完全停 Thread | ⭐⭐⭐ | 中等 |
| VRM 無動畫時降 fps | ⭐⭐⭐ | 中等 |
| Sticker 延遲同步 | ⭐⭐ | 簡單 |
| invalidate 區域化 | ⭐⭐ | 中等 |

---

## 執行順序

```
1. 影片背景 pause/resume（KeyboardBackgroundView + IMEService）← 最重要
2. VRM pause/resume（CharacterView + IMEService）
3. 流光 Thread wait/notify
4. Sticker 延遲同步
5. VRM 無動畫時降 fps
6. invalidate 區域化（最複雜，最後做）

每步 Build 測試，用 Android Studio 的 Energy Profiler 確認耗電降低
```

---

## 驗證方式

```
用 Android Studio Profiler → Energy 頁面：

1. 開啟鍵盤、打幾個字 → 看 CPU 使用率
2. 收起鍵盤（不關 App）→ 看 CPU 是否降低
3. 再打開鍵盤 → 看是否正常恢復

預期結果：
  □ 鍵盤收起時 CPU 接近 0%（沒有持續渲染）
  □ 有影片背景時，收起鍵盤後影片停止解碼
  □ 鍵盤顯示時，影片自動恢復播放
  □ 流光效果關閉時，GL Thread 不再喚醒 CPU
```
