# YukiBoard 影片背景修復任務包

> 根據 v9 原始碼分析，找到 4 個根本問題

---

## 根本原因總覽

| # | 問題 | 症狀 |
|---|------|------|
| R1 | **缺少媒體存取權限** | Android 13+ 選圖/選影片直接失敗或空白 |
| R2 | **`onSurfaceTextureDestroyed` return 不一致** | 影片 TextureView 被移除後 MediaPlayer 無法恢復，背景全黑 |
| R3 | **BG_UPDATED receiver 呼叫 `clearBackground()` 再 `reloadFromStorage()`** | 清掉後重載路徑有 race condition，有時背景消失 |
| R4 | **`startVideoPreview` 在 IO thread 建立 MediaPlayer 後切回 Main 設定 Surface** | 設定 Surface 的時機不確定，容易失敗 |

---

## 🔴 Fix R1：加入媒體存取權限（最根本問題）

Android 13（API 33）開始，`READ_EXTERNAL_STORAGE` 被拆分成：
- `READ_MEDIA_IMAGES`（圖片）
- `READ_MEDIA_VIDEO`（影片）

目前 Manifest 完全沒有這幾個權限，Android 13+ 裝置選圖/選影片會直接失敗。

### `AndroidManifest.xml`

在現有 `<uses-permission>` 區塊加入：

```xml
<!-- Android 13+ 媒體存取 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

<!-- Android 12 以下相容 -->
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

### `ui/settings/BackgroundPickerActivity.kt` — 加執行時期權限申請

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val type = intent.getStringExtra(EXTRA_PICK_TYPE) ?: "image"

    // Android 13+ 需要執行時期權限
    val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        if (type == "video") android.Manifest.permission.READ_MEDIA_VIDEO
        else android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(permission), RC_PERMISSION)
        return
    }

    launchPicker(type)
}

override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<out String>, grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == RC_PERMISSION) {
        if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchPicker(intent.getStringExtra(EXTRA_PICK_TYPE) ?: "image")
        } else {
            android.widget.Toast.makeText(this, "需要相冊存取權限", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

private fun launchPicker(type: String) {
    // ← 把原本 onCreate 裡的選取邏輯搬到這裡
    val pickIntent = Intent(Intent.ACTION_PICK).apply {
        data = if (type == "video") {
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }
    try {
        @Suppress("DEPRECATION")
        startActivityForResult(pickIntent, RC_PICK)
    } catch (_: Exception) {
        val mimeType = if (type == "video") "video/*" else "image/*"
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    this.type = mimeType
                    addCategory(Intent.CATEGORY_OPENABLE)
                },
                RC_PICK
            )
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "無法開啟相簿", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

companion object {
    const val EXTRA_PICK_TYPE = "pick_type"
    private const val RC_PICK = 7001
    private const val RC_PERMISSION = 7002  // ← 新增
}
```

---

## 🔴 Fix R2：`onSurfaceTextureDestroyed` return 值統一

**這是背景全黑的主要原因。**

`return true` = 系統釋放 SurfaceTexture（無法再用）
`return false` = 自己管理 SurfaceTexture（可以繼續用）

目前 `setVideoBackground(File)` 的版本 return `true`，
但 `resume()` 裡嘗試重用已被釋放的 SurfaceTexture → 黑屏。

### `ui/keyboard/KeyboardBackgroundView.kt`

**`setVideoBackground(File)` 的 `onSurfaceTextureDestroyed`：**

```kotlin
override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
    // return false = 我們自己管理，不讓系統釋放
    // MediaPlayer 在 releaseMediaPlayer() 裡手動釋放
    mediaPlayer?.pause()
    return false
}
```

**`setVideoBackground(Uri)` 的 `onSurfaceTextureDestroyed`（已是 false，不用改）：**
確認是 `return false`，保持一致。

---

## 🔴 Fix R3：BG_UPDATED receiver 不要呼叫 `clearBackground()`

**目前的 race condition：**

```kotlin
// 目前（錯誤）：
backgroundView?.let { bg ->
    bg.clearBackground()     // ← removeAllViews，textureView 被移除
    bg.reloadFromStorage()   // ← 重新建立 textureView，但 Surface 尚未 available
}
```

`clearBackground()` 會 `removeAllViews()`，導致 TextureView 的 SurfaceTexture 被銷毀，
`reloadFromStorage()` 建立新的 TextureView，但 `onSurfaceTextureAvailable` 是異步的，
如果在 Surface 準備好之前 MediaPlayer 就開始 prepare，就會失敗。

### `service/YukiBoardIMEService.kt` — `bgUpdateReceiver.onReceive()`

```kotlin
bgUpdateReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
        // ← 不要 clearBackground()，直接重載（reloadFromStorage 內部已有去重邏輯）
        backgroundView?.reloadFromStorage()
        keyboardView.hasCustomBackground = prefs.useCustomBackground
        keyboardView.invalidate()
        themePanel?.reloadPreviewBitmap()
        themePanel?.invalidate()
    }
}
```

---

## 🟡 Fix R4：`startVideoPreview` 改為全部在 Main thread 執行

**目前的問題：**

```kotlin
// 目前（有問題）：
activityScope.launch(Dispatchers.IO) {   // ← IO thread
    val mp = MediaPlayer().apply {
        setDataSource(filePath)           // ← 這步在 IO thread OK
    }
    withContext(Dispatchers.Main) {       // ← 切回 Main
        mp.setSurface(Surface(st))        // ← 但 st 可能在切換期間已失效
        mp.prepareAsync()
    }
}
```

`setDataSource` 是同步的，不需要 IO thread。
SurfaceTexture 在切換 thread 的過程中可能失效。

### `ui/settings/SettingsActivity.kt` — `startVideoPreview()`

```kotlin
private fun startVideoPreview(filePath: String) {
    val container = bgPreviewContainer ?: return
    val tv = android.view.TextureView(this).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    bgVideoTexture = tv

    tv.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
            // ← 全部在 Main thread，不切 IO
            try {
                val mp = android.media.MediaPlayer().apply {
                    setDataSource(filePath)
                    setSurface(android.view.Surface(st))
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e("BgPreview", "MediaPlayer error: what=$what extra=$extra")
                        bgPreviewLabel?.text = tr("影片播放失敗")
                        bgPreviewLabel?.visibility = View.VISIBLE
                        true
                    }
                    setOnPreparedListener { player ->
                        bgVideoWidth = player.videoWidth
                        bgVideoHeight = player.videoHeight
                        updateVideoPreviewTransform()
                        player.start()
                    }
                    prepareAsync()
                }
                bgVideoPlayer = mp
            } catch (e: Exception) {
                bgPreviewLabel?.text = tr("影片載入失敗")
                bgPreviewLabel?.visibility = View.VISIBLE
            }
        }
        override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
            updateVideoPreviewTransform()
        }
        override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
            val mp = bgVideoPlayer
            bgVideoPlayer = null
            try { mp?.stop() } catch (_: Exception) { }
            try { mp?.release() } catch (_: Exception) { }
            return true
        }
        override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
    }

    val overlayIdx = container.indexOfChild(bgPreviewOverlay)
    container.addView(tv, if (overlayIdx > 0) overlayIdx else 1)
}
```

---

## 🟡 Fix R5：`reloadFromStorage` 影片 URI 模式也要支援

目前 `reloadFromStorage()` 只處理 `File` 路徑，但背景是 URI 的情況（從相冊直接選）也要能處理。

確認 `KeyboardBackgroundView.getSavedBackground()` 回傳的是已複製到內部儲存的 `File`，而不是原始 URI。

**確認點：** `BackgroundPickerActivity.saveBackground()` 和 `SettingsActivity.onActivityResult()` 都有把檔案複製到 `backgrounds/` 目錄，所以 `getSavedBackground()` 拿到的是 `File`，這部分是對的，不需要改。

---

## 執行順序

```
Fix R1（最根本）：
  1. AndroidManifest.xml 加三個媒體權限
  2. BackgroundPickerActivity 加執行時期權限申請 + launchPicker() 重構
  → Build + 測試：Android 13 裝置能開啟相簿

Fix R2（背景全黑）：
  3. KeyboardBackgroundView.setVideoBackground(File) 的
     onSurfaceTextureDestroyed return true → false
  → Build + 測試：設定影片背景後切換 App 再回來，不會全黑

Fix R3（race condition）：
  4. IMEService bgUpdateReceiver 移除 clearBackground()
  → Build + 測試：在功能列選完影片後，鍵盤背景能正確更新

Fix R4（預覽播放）：
  5. SettingsActivity.startVideoPreview() 全部移到 Main thread
  → Build + 測試：設定頁的影片預覽能正常播放
```

---

## 驗證

```
選影片：
□ Android 13+ 點「選影片」→ 彈出系統相簿（不閃退、不空白）
□ Android 12 以下也能正常選影片
□ 第一次選擇時有權限申請彈窗
□ 拒絕權限 → 有提示文字，不崩潰

設定頁預覽：
□ 選完影片 → 設定頁出現影片預覽（可播放）
□ 滑動遮罩滑桿 → 預覽即時更新
□ 離開設定再進來 → 預覽仍然顯示

鍵盤背景：
□ 設定影片後，鍵盤背景有影片在播放（不是全黑）
□ 切換到其他 App 再回來 → 背景繼續播放（不全黑）
□ 從功能列主題面板選影片 → 鍵盤背景即時更新
□ 清除背景 → 背景消失，鍵盤恢復預設

ThemePanelView 預覽：
□ 設定影片後，功能列主題面板顯示影片第一幀縮圖
```
