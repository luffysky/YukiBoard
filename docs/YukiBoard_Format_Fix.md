# YukiBoard 背景格式支援 + 黑色區域修復

---

## 問題總覽

| # | 問題 | 位置 |
|---|------|------|
| F1 | 圖片只支援 jpg/png/webp，缺 gif/bmp/heic 等 | `KeyboardBackgroundView` + `BackgroundPickerActivity` + `SettingsActivity` |
| F2 | 影片只支援 mp4/webm/3gp，存檔時把 mkv/avi/mov 強制改名 mp4 → 播放失敗 | `BackgroundPickerActivity` + `SettingsActivity` |
| F3 | 圖片用 `inSampleSize=2` 縮小，解析度降低 | `KeyboardBackgroundView` |
| F4 | 鍵盤縮窄時右側黑色 → 背景跟著縮，背景應延伸全螢幕 | `YukiBoardIMEService` |

---

## 修復 F1+F2：格式支援全面擴充

### `ui/keyboard/KeyboardBackgroundView.kt` — companion object

```kotlin
fun getSavedBackground(context: Context): File? {
    val dir = getBackgroundDir(context)
    // 影片優先
    val video = dir.listFiles()?.firstOrNull { isVideoFile(it) }
    if (video != null) return video
    // 圖片
    return dir.listFiles()?.firstOrNull { isImageFile(it) }
}

fun isVideoFile(file: File): Boolean {
    return file.extension.lowercase() in listOf(
        "mp4", "m4v", "webm", "3gp", "mkv", "mov", "avi", "ts", "flv"
    )
}

fun isImageFile(file: File): Boolean {
    return file.extension.lowercase() in listOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
    )
}
```

> `isImageFile` 取代原本 `!isVideoFile` 的判斷，到處呼叫的地方也要一起改。

### `ui/settings/BackgroundPickerActivity.kt` — `saveBackground()`

**不再強制轉換格式，改用 mime type 取得正確副檔名：**

```kotlin
private fun saveBackground(uri: Uri) {
    val prefs = YukiPreferences(this)
    val bgDir = KeyboardBackgroundView.getBackgroundDir(this)
    bgDir.listFiles()?.forEach { it.delete() }

    val pickType = intent.getStringExtra(EXTRA_PICK_TYPE) ?: "image"

    // 從 mime type 取得副檔名，保留原始格式
    val mime = contentResolver.getType(uri) ?: ""
    val ext = when {
        // 影片
        mime.contains("webm")           -> "webm"
        mime.contains("3gp")            -> "3gp"
        mime.contains("x-matroska")     -> "mkv"
        mime.contains("quicktime")      -> "mov"
        mime.contains("x-msvideo")      -> "avi"
        mime.contains("mp2t")           -> "ts"
        mime.contains("video")          -> "mp4"   // 其他影片 fallback
        // 圖片
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("webp")           -> "webp"
        mime.contains("gif")            -> "gif"
        mime.contains("bmp")            -> "bmp"
        mime.contains("heic") || mime.contains("heif") -> "heic"
        mime.contains("avif")           -> "avif"
        mime.contains("png")            -> "png"
        else -> if (pickType == "video") "mp4" else "jpg"
    }

    val dest = java.io.File(bgDir, "keyboard_bg.$ext")
    try {
        contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (!dest.exists() || dest.length() == 0L) {
            Toast.makeText(this, "背景檔案複製失敗", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.useCustomBackground = true
        val intent = android.content.Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        Toast.makeText(this, "背景已設定", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(this, "背景設定失敗: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

### `ui/settings/SettingsActivity.kt` — `onActivityResult()`

同樣改為保留原始格式：

```kotlin
val ext = when {
    // 影片
    mime.contains("webm")           -> "webm"
    mime.contains("3gp")            -> "3gp"
    mime.contains("x-matroska")     -> "mkv"
    mime.contains("quicktime")      -> "mov"
    mime.contains("x-msvideo")      -> "avi"
    mime.contains("mp2t")           -> "ts"
    mime.contains("video")          -> "mp4"
    // 圖片
    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
    mime.contains("webp")           -> "webp"
    mime.contains("gif")            -> "gif"
    mime.contains("bmp")            -> "bmp"
    mime.contains("heic") || mime.contains("heif") -> "heic"
    mime.contains("avif")           -> "avif"
    mime.contains("png")            -> "png"
    else -> if (requestCode == REQUEST_PICK_BG_VIDEO) "mp4" else "jpg"
}
```

> 注意：`val mime = contentResolver.getType(uri) ?: ""` 要在 ext 判斷前取得。

---

## 修復 F3：移除 inSampleSize 限制

### `ui/keyboard/KeyboardBackgroundView.kt` — `setImageBackground(file)`

```kotlin
bgScope.launch(Dispatchers.IO) {
    // 移除 inSampleSize=2，讓圖片以原始解析度載入
    val bmp = BitmapFactory.decodeFile(file.absolutePath)
    withContext(Dispatchers.Main) {
        if (bmp != null) {
            iv.setImageBitmap(bmp)
            initCenterCropMatrix(iv, bmp.width, bmp.height)
        }
    }
}
```

> 如果擔心記憶體，可以改成根據螢幕尺寸動態計算 inSampleSize，但不要硬碼 2。

---

## 修復 F4：背景延伸到全螢幕寬度

### 問題

`backgroundView` 在 `keyboardContainer` 裡，當鍵盤寬度縮小時背景也跟著縮，旁邊露出黑色。

### `service/YukiBoardIMEService.kt` — `onCreateInputView()`

**把 `backgroundView` 從 `keyboardContainer` 移出，改為直接加進 `rootContainer` 底層：**

找到這段：
```kotlin
keyboardContainer.addView(bgView, FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
))
backgroundView = bgView
```

改為把背景加在 `rootContainer` 的絕對底層，並且尺寸覆蓋整個 rootContainer：

```kotlin
// 背景不加進 keyboardContainer，改加進一個全螢幕的 FrameLayout
// 用 WindowManager 或直接把 rootContainer 改成 FrameLayout

// 最簡單方案：讓 bgView 的 layoutParams 使用螢幕完整寬度
val screenW = resources.displayMetrics.widthPixels
keyboardContainer.addView(bgView, FrameLayout.LayoutParams(
    screenW,  // ← 用螢幕完整寬度，而不是 MATCH_PARENT
    FrameLayout.LayoutParams.MATCH_PARENT
))
backgroundView = bgView
```

> 這樣即使 keyboardContainer 被縮窄，背景的寬度仍是螢幕完整寬度，不會露出黑色。
> bgView 的內容會超出 keyboardContainer 邊界顯示，需要確認 `keyboardContainer.clipChildren = false`（v12 已有設定）。

---

## 執行順序

```
1. KeyboardBackgroundView.kt：
   - getSavedBackground：擴充影片/圖片格式清單
   - isVideoFile：加 mkv/mov/avi/ts/flv/m4v
   - 新增 isImageFile：加 gif/bmp/heic/avif
   - setImageBackground：移除 inSampleSize=2

2. BackgroundPickerActivity.kt：
   - saveBackground：改用完整 mime → ext 對應表

3. SettingsActivity.kt：
   - onActivityResult：同樣改用完整 mime → ext 對應表

4. YukiBoardIMEService.kt：
   - bgView 的 LayoutParams 寬度改為 screenW
```

---

## 關於 GIF 動畫

`BitmapFactory.decodeFile` 只解碼第一幀，GIF 不會動。

要支援 GIF 動畫背景，需要：
- Android API 28+ 用 `AnimatedImageDrawable`
- 或引入 Glide/Coil 等圖片庫

目前先支援 GIF 靜態第一幀，動畫 GIF 未來另行加入。

---

## 驗證

```
格式：
□ jpg/png/webp 圖片背景正常
□ gif 圖片背景（第一幀）正常
□ mp4 影片背景正常
□ mkv 影片背景正常
□ mov 影片背景正常
□ 選任何影片不再出現「影片播放失敗」（只要系統支援該編解碼器）

黑色區域：
□ 鍵盤縮窄（< 100% 寬度）時，背景延伸到全螢幕，不露黑色
```
