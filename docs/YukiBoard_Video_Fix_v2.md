# YukiBoard 影片背景修復（完整版）

> 基於 v10 原始碼全面掃描，找出 5 個仍然存在的問題

---

## 問題總覽

| # | 問題 | 症狀 |
|---|------|------|
| P1 | `resume()` 沒有檢查 `textureView?.isAvailable` | 每次鍵盤重新顯示就全黑 |
| P2 | `onStartInputView` 先呼叫 `resume()`，緊接著 `reloadFromStorage()` 又呼叫 `resume()` | 重複操作，第二次 resume 時 Surface 還沒 ready |
| P3 | `setInputView(onCreateInputView())` 被呼叫前，舊的 `backgroundView` 沒有先 `destroy()` | MediaPlayer 資源洩漏、舊播放器繼續佔資源 |
| P4 | `onStartInputView` 裡 `resume()` 的呼叫時機在 `reloadFromStorage()` 之前 | 順序錯誤：resume 時背景還沒被確認存在 |
| P5 | `reloadFromStorage()` 的去重邏輯：`mediaPlayer != null` 才算「同一個影片」，但 resume 後 mediaPlayer 可能是 null | 明明是同一個影片，卻重新建立 TextureView |

---

## 🔴 Fix P1 + P2 + P4：重寫 `KeyboardBackgroundView.resume()`

### `ui/keyboard/KeyboardBackgroundView.kt` — `resume()`

**核心問題：** `resume()` 看到 `mediaPlayer != null` 就直接 `start()`，但沒確認 TextureView 的 Surface 是否仍然有效。在 IME 環境裡，IME window 隱藏再顯示時，TextureView 的 `isAvailable` 會變 `false`（Surface 失效），此時 `start()` 輸出無畫面 → 全黑。

**解法：** 在 `resume()` 裡優先檢查 `textureView?.isAvailable`，Surface 失效就重建整個 TextureView + MediaPlayer：

```kotlin
fun resume() {
    val tv = textureView
    val mp = mediaPlayer
    val path = currentLoadedPath

    when {
        // Surface 可用且 MediaPlayer 存在 → 直接播放
        tv != null && tv.isAvailable && mp != null -> {
            if (!mp.isPlaying) {
                try {
                    mp.start()
                } catch (_: Exception) {
                    // 狀態異常 → 重建
                    if (path != null) {
                        releaseMediaPlayer()
                        setVideoBackground(File(path))
                    }
                }
            }
        }

        // Surface 不可用（IME window 重新顯示，Surface 失效）→ 重建 TextureView
        tv != null && !tv.isAvailable && path != null -> {
            releaseMediaPlayer()
            removeView(tv)
            textureView = null
            setVideoBackground(File(path))
        }

        // MediaPlayer 不存在但有路徑 → 重建
        mp == null && path != null && currentType == BackgroundType.VIDEO -> {
            setVideoBackground(File(path))
        }
    }
}
```

---

## 🔴 Fix P2 + P4：`onStartInputView` 移除第一次多餘的 `resume()`

### `service/YukiBoardIMEService.kt` — `onStartInputView()`

找到這段（約 line 839）：

```kotlin
glSurfaceView?.onResume()
backgroundView?.resume()   // ← 移除這行
if (prefs.characterAnimEnabled) {
    characterView?.resumeAnimation()
}
```

**改為：**

```kotlin
glSurfaceView?.onResume()
// backgroundView 的 resume 由下面的 reloadFromStorage() 統一處理，不需要在這裡呼叫
if (prefs.characterAnimEnabled) {
    characterView?.resumeAnimation()
}
```

`reloadFromStorage()` 在後面（line 876）被呼叫，它的去重邏輯最後也會呼叫 `resume()`，讓一個入口統一處理就好。

---

## 🔴 Fix P3：`setInputView(onCreateInputView())` 前先 destroy 舊的 backgroundView

### `service/YukiBoardIMEService.kt`

有三個地方呼叫 `setInputView(onCreateInputView())`，每個前面都要先 destroy：

**1. floating toggle（約 line 590）：**

```kotlin
keyboardView.setOnFloatingToggleListener {
    prefs.floatingModeEnabled = !prefs.floatingModeEnabled
    closePanel()
    backgroundView?.destroy()   // ← 新增
    backgroundView = null       // ← 新增
    setInputView(onCreateInputView())
}
```

**2. keyboard height changed（約 line 1611）：**

```kotlin
p.onKeyboardHeightChanged = {
    closePanel()
    backgroundView?.destroy()   // ← 新增
    backgroundView = null       // ← 新增
    setInputView(onCreateInputView())
}
```

**3. floating exit（約 line 2614）：**

```kotlin
wrapper.onToggleDock = {
    prefs.floatingModeEnabled = false
    prefs.floatingX = -1
    prefs.floatingY = -1
    closePanel()
    backgroundView?.destroy()   // ← 新增
    backgroundView = null       // ← 新增
    setInputView(onCreateInputView())
}
```

---

## 🟡 Fix P5：`reloadFromStorage()` 的去重條件加上 `textureView != null`

### `ui/keyboard/KeyboardBackgroundView.kt` — `reloadFromStorage()`

```kotlin
// 原本：
if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg) && mediaPlayer != null
    && currentLoadedPath == savedBg.absolutePath) {
    applyVideoTransform()
    resume()
    return true
}

// 改為：條件加上 textureView != null（確保 View 還在）
if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg)
    && currentLoadedPath == savedBg.absolutePath
    && textureView != null) {
    applyVideoTransform()
    resume()
    return true
}
```

---

## 🟡 Fix 補充：`setVideoBackground(File)` 和 `setVideoBackground(Uri)` 的 `onSurfaceTextureDestroyed`

**兩個版本要統一行為：**

File 版本（`return false` ← 正確）：
```kotlin
override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
    mediaPlayer?.pause()
    return false  // ✅ 不讓系統釋放，IME resume 時 Surface 仍有效
}
```

但 `return false` 有一個副作用：**如果 View 被 `removeView()` 移出 hierarchy，SurfaceTexture 雖然不被釋放，但 TextureView.isAvailable 會變 false。** 這正是 P1 要處理的情況，Fix P1 的新 `resume()` 已處理這個 case。

所以兩個版本統一用 `return false`，讓 Fix P1 的 `resume()` 統一判斷。

---

## 執行順序

```
Step 1：修 KeyboardBackgroundView.resume()（P1，最關鍵）
  → Build + 測試：鍵盤顯示/隱藏 5 次，影片背景不會變黑

Step 2：修 onStartInputView 移除多餘的 backgroundView?.resume()（P2/P4）
  → Build + 測試：鍵盤顯示不會閃一次黑再顯示影片

Step 3：修三處 setInputView 前 destroy backgroundView（P3）
  → Build + 測試：切換浮動模式、調整鍵盤高度後，影片仍在播放

Step 4：修 reloadFromStorage 去重條件（P5）
  → Build + 測試：同一個影片不會重複重建 TextureView
```

---

## 驗證清單

```
基本播放：
□ 設定影片背景 → 鍵盤顯示時影片在播放
□ 影片循環播放（不會停在最後一幀）
□ 影片無聲（不影響其他音源）

黑屏問題：
□ 開啟鍵盤 → 打幾個字 → 關閉鍵盤 → 重新開啟 → 影片仍在播放（不黑屏）
□ 重複開關鍵盤 10 次，沒有出現全黑
□ 切換 App 再回來 → 影片仍在播放

重建場景：
□ 切換浮動/嵌入模式 → 影片背景正常
□ 在設定頁調整鍵盤高度 → 回到鍵盤影片仍在播放
□ 浮動模式切回嵌入模式 → 影片正常

設定頁預覽：
□ 選完影片 → 設定頁立即顯示影片預覽（可播放）
□ 離開設定頁再進來 → 預覽仍然可以播放
```
