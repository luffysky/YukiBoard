# 影片背景修復（最小改動）

## 只改一個檔案：`ui/keyboard/KeyboardBackgroundView.kt`

### 改動 1：第 276 行附近（`setVideoBackground(File)` 裡）

```kotlin
// 原本：
override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
    // return false = 自己管理 SurfaceTexture，不讓系統釋放
    // IME 視窗隱藏/顯示時不需重建 MediaPlayer
    mediaPlayer?.pause()
    return false
}

// 改為：
override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
    releaseMediaPlayer()
    return true
}
```

### 改動 2：第 330 行附近（`setVideoBackground(Uri)` 裡）

```kotlin
// 原本：
override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
    mediaPlayer?.pause()
    return false
}

// 改為：
override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
    releaseMediaPlayer()
    return true
}
```

### 改動 3：`reloadFromStorage()` 裡移除 `resume()` 呼叫

找到這段：
```kotlin
if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg) && mediaPlayer != null
    && currentLoadedPath == savedBg.absolutePath) {
    applyVideoTransform()
    resume()   // ← 刪掉這行
    return true
}
```

改為：
```kotlin
if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg) && mediaPlayer != null
    && currentLoadedPath == savedBg.absolutePath) {
    applyVideoTransform()
    return true
}
```

### 改動 4：`pause()` 和 `resume()` 函式清空

```kotlin
fun pause() {
    // SurfaceTexture 由系統管理（return true），不需要手動暫停
}

fun resume() {
    // SurfaceTexture 重建由 onSurfaceTextureAvailable 自動處理
}
```

---

就這樣，不要改其他東西。
