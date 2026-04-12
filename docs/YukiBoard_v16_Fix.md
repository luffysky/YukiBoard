# YukiBoard v16 修復任務包

## 問題總覽

| # | 問題 | 位置 |
|---|------|------|
| P1 | `applyOffset` 用 `minOf`（fit-inside），但三個區域預覽都用 `maxOf`（center-crop） | `KeyboardBackgroundView` |
| P2 | `applyOffset` 沒有旋轉邏輯，圖片不旋轉 | `KeyboardBackgroundView` |
| P3 | 設定頁背景預覽（`updateBgPreviewMatrix`）沒有套用旋轉 | `SettingsActivity` |
| P4 | 設定頁鍵盤預覽（`buildFullKeyboardPreview`）沒有套用旋轉 | `SettingsActivity` |
| P5 | ThemePanelView 改設定後只呼叫 `onBackgroundSettingsChanged`，IMEService 不發廣播，SettingsActivity 收不到 | `YukiBoardIMEService` |

---

## P1 + P2：`KeyboardBackgroundView.applyOffset()` 改成 center-crop + 旋轉

**目標：與設定頁預覽、功能列預覽的邏輯完全一致（都是 `maxOf` + 旋轉以內容中心為原點）**

```kotlin
private fun applyOffset() {
    val iv = imageView ?: return
    val vw = iv.width.toFloat()
    val vh = iv.height.toFloat()

    if (vw <= 0 || vh <= 0 || bmpWidth <= 0 || bmpHeight <= 0) {
        iv.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                iv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                applyOffset()
            }
        })
        return
    }

    imageMatrix.reset()

    if (scaleMode == 1) {
        // 整張塞入：拉伸填滿
        imageMatrix.setScale(vw / bmpWidth, vh / bmpHeight)
    } else {
        // center-crop：填滿鍵盤，offsetX/Y 控制裁切哪一側
        val scale = maxOf(vw / bmpWidth, vh / bmpHeight)
        val scaledW = bmpWidth * scale
        val scaledH = bmpHeight * scale
        val maxDx = (scaledW - vw).coerceAtLeast(0f)
        val maxDy = (scaledH - vh).coerceAtLeast(0f)
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(-maxDx * offsetX, -maxDy * offsetY)
    }

    // 旋轉：以圖片內容的中心點旋轉（縮放後的內容中心）
    if (bgRotation != 0) {
        // 縮放後內容在 View 中的中心點
        val scale = if (scaleMode == 1) 1f else maxOf(vw / bmpWidth, vh / bmpHeight)
        val scaledW = if (scaleMode == 1) vw else (bmpWidth * scale).coerceAtMost(vw)
        val scaledH = if (scaleMode == 1) vh else (bmpHeight * scale).coerceAtMost(vh)
        val maxDx = if (scaleMode == 1) 0f else (bmpWidth * scale - vw).coerceAtLeast(0f)
        val maxDy = if (scaleMode == 1) 0f else (bmpHeight * scale - vh).coerceAtLeast(0f)
        // 內容中心 = View 中心（因為 center-crop 以 View 中心裁切）
        val cx = vw / 2f
        val cy = vh / 2f
        imageMatrix.postRotate(bgRotation.toFloat(), cx, cy)
    }

    savedMatrix.set(imageMatrix)
    iv.imageMatrix = imageMatrix
}
```

---

## P3：`SettingsActivity.updateBgPreviewMatrix()` 加旋轉

找到這段：
```kotlin
matrix.setScale(scale, scale)
matrix.postTranslate(dx, dy)
```

**在後面加：**
```kotlin
matrix.setScale(scale, scale)
matrix.postTranslate(dx, dy)
// 旋轉：以 View 中心旋轉
if (prefs.bgRotation != 0) {
    matrix.postRotate(prefs.bgRotation.toFloat(), vw / 2f, vh / 2f)
}
```

`scaleMode == 1` 的分支也加：
```kotlin
matrix.setScale(vw / bmpW, vh / bmpH)
if (prefs.bgRotation != 0) {
    matrix.postRotate(prefs.bgRotation.toFloat(), vw / 2f, vh / 2f)
}
```

---

## P4：`SettingsActivity.buildFullKeyboardPreview()` 的 `onDraw` 加旋轉

找到這段：
```kotlin
bgMatrix.setScale(scale, scale)
bgMatrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
```

**在後面加：**
```kotlin
bgMatrix.setScale(scale, scale)
bgMatrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
if (prefs.bgRotation != 0) {
    bgMatrix.postRotate(prefs.bgRotation.toFloat(), w / 2f, h / 2f)
}
```

`scaleMode == 1` 的分支也加：
```kotlin
bgMatrix.setScale(w / bmp.width, h / bmp.height)
if (prefs.bgRotation != 0) {
    bgMatrix.postRotate(prefs.bgRotation.toFloat(), w / 2f, h / 2f)
}
```

---

## P5：IMEService `onBackgroundSettingsChanged` 加 sendBroadcast

找到：
```kotlin
p.onBackgroundSettingsChanged = {
    backgroundView?.let { bg ->
        bg.scaleMode = prefs.bgScaleMode
        bg.offsetX = prefs.bgOffsetX
        bg.offsetY = prefs.bgOffsetY
        bg.bgRotation = prefs.bgRotation
        bg.reloadFromStorage()
    }
    keyboardView.invalidate()
}
```

**改為：**
```kotlin
p.onBackgroundSettingsChanged = {
    backgroundView?.let { bg ->
        bg.scaleMode = prefs.bgScaleMode
        bg.offsetX = prefs.bgOffsetX
        bg.offsetY = prefs.bgOffsetY
        bg.bgRotation = prefs.bgRotation
        bg.reloadFromStorage()
    }
    keyboardView.invalidate()
    // 通知 SettingsActivity 同步 UI
    val intent = android.content.Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
    intent.setPackage(packageName)
    sendBroadcast(intent)
}
```

---

## 執行順序

```
1. KeyboardBackgroundView.applyOffset()：minOf → maxOf，加旋轉
2. SettingsActivity.updateBgPreviewMatrix()：加旋轉
3. SettingsActivity.buildFullKeyboardPreview() onDraw：加旋轉
4. YukiBoardIMEService.onBackgroundSettingsChanged：加 sendBroadcast
```

---

## 驗證

```
三個區域顯示一致：
□ 設定頁「鍵盤背景/Background」預覽
□ 設定頁「鍵盤預覽」
□ 功能列主題面板預覽
□ 實際鍵盤背景
→ 四個地方顯示完全一樣

旋轉：
□ 旋轉按鈕切換 → 四個地方同步更新
□ 旋轉以圖片/影片內容中心點為原點（不是 View 角落）

同步：
□ 功能列改旋轉/比例/XY → 設定頁立即更新
□ 設定頁改旋轉/比例/XY → 功能列立即更新
□ 任一方改背景圖/影片 → 另一方立即更新
```
