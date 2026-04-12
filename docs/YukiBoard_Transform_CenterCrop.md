# YukiBoard 背景縮放修復（最終正確版）

## 需求確認

**你要的是 center-crop（填滿鍵盤，裁切超出的部分），跟設定頁預覽一樣。**
X/Y 滑桿控制裁切哪個部分（往哪邊偏移）。
旋轉後仍然 center-crop 填滿鍵盤。

---

## `ui/keyboard/KeyboardBackgroundView.kt`

### setter 三個（加 applyVideoTransform）

```kotlin
var offsetX: Float = 0.5f
    set(value) {
        field = value.coerceIn(0f, 1f)
        applyOffset()
        applyVideoTransform()
    }
var offsetY: Float = 0.5f
    set(value) {
        field = value.coerceIn(0f, 1f)
        applyOffset()
        applyVideoTransform()
    }
var scaleMode: Int = 0
    set(value) {
        field = value
        applyOffset()
        applyVideoTransform()
    }
```

### `applyOffset()` — 跟設定頁預覽一致的 center-crop，加旋轉

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
    val cx = vw / 2f
    val cy = vh / 2f

    if (scaleMode == 1) {
        // 整張塞入：拉伸填滿
        imageMatrix.setScale(vw / bmpWidth, vh / bmpHeight)
        if (bgRotation != 0) {
            imageMatrix.postRotate(bgRotation.toFloat(), cx, cy)
        }
    } else {
        // center-crop：填滿鍵盤，裁切超出部分（跟設定頁預覽一致）
        val scale = maxOf(vw / bmpWidth, vh / bmpHeight)
        val scaledW = bmpWidth * scale
        val scaledH = bmpHeight * scale
        val maxDx = (scaledW - vw).coerceAtLeast(0f)
        val maxDy = (scaledH - vh).coerceAtLeast(0f)
        // 先縮放並置中
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(-maxDx * offsetX, -maxDy * offsetY)
        // 旋轉（以 View 中心）
        if (bgRotation != 0) {
            imageMatrix.postRotate(bgRotation.toFloat(), cx, cy)
        }
    }

    savedMatrix.set(imageMatrix)
    iv.imageMatrix = imageMatrix
}
```

### `applyVideoTransform()` — center-crop，加旋轉

```kotlin
private fun applyVideoTransform() {
    val tv = textureView ?: return
    val vw = tv.width.toFloat()
    val vh = tv.height.toFloat()
    if (vw <= 0 || vh <= 0 || videoWidth <= 0 || videoHeight <= 0) return

    val vW = videoWidth.toFloat()
    val vH = videoHeight.toFloat()
    val cx = vw / 2f
    val cy = vh / 2f

    val isRotated = bgRotation == 90 || bgRotation == 270
    // 旋轉後的有效寬高（用來算 center-crop 的 scale）
    val effectiveW = if (isRotated) vH else vW
    val effectiveH = if (isRotated) vW else vH

    val matrix = Matrix()

    if (scaleMode == 1) {
        // 整張塞入
        matrix.setTranslate(cx - vW / 2f, cy - vH / 2f)
        if (bgRotation != 0) matrix.postRotate(bgRotation.toFloat(), cx, cy)
        matrix.postScale(vw / effectiveW, vh / effectiveH, cx, cy)
    } else {
        // center-crop：填滿鍵盤
        val scale = maxOf(vw / effectiveW, vh / effectiveH)
        val scaledW = effectiveW * scale
        val scaledH = effectiveH * scale
        val maxDx = (scaledW - vw).coerceAtLeast(0f)
        val maxDy = (scaledH - vh).coerceAtLeast(0f)

        // 1. 移到 View 中心
        matrix.setTranslate(cx - vW / 2f, cy - vH / 2f)
        // 2. 旋轉
        if (bgRotation != 0) matrix.postRotate(bgRotation.toFloat(), cx, cy)
        // 3. 縮放（以 View 中心）
        matrix.postScale(scale, scale, cx, cy)
        // 4. 偏移裁切
        matrix.postTranslate(-maxDx * offsetX, -maxDy * offsetY)
    }

    tv.setTransform(matrix)
}
```

---

## 同步修改：設定頁預覽也要套用旋轉

設定頁的 `buildFullKeyboardPreview` 的 `onDraw` 背景繪製，也要加旋轉：

### `ui/settings/SettingsActivity.kt` — `buildFullKeyboardPreview()` 的 `onDraw`

找到這段：
```kotlin
val scale = maxOf(w / bmp.width, h / bmp.height)
val sw = bmp.width * scale
val sh = bmp.height * scale
val maxDx = (sw - w).coerceAtLeast(0f)
val maxDy = (sh - h).coerceAtLeast(0f)
bgMatrix.setScale(scale, scale)
bgMatrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
```

**改為：**
```kotlin
val scale = maxOf(w / bmp.width, h / bmp.height)
val sw = bmp.width * scale
val sh = bmp.height * scale
val maxDx = (sw - w).coerceAtLeast(0f)
val maxDy = (sh - h).coerceAtLeast(0f)
bgMatrix.setScale(scale, scale)
bgMatrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
// 旋轉（以 View 中心）
if (prefs.bgRotation != 0) {
    bgMatrix.postRotate(prefs.bgRotation.toFloat(), w / 2f, h / 2f)
}
```

---

## 驗證

```
圖片（center-crop）：
□ 直向圖設定後，圖片填滿整個鍵盤（跟設定頁預覽一樣）
□ X 滑桿往右 → 圖片往左偏移（裁切右側）
□ X 滑桿往左 → 圖片往右偏移（裁切左側）
□ X=0.5, Y=0.5 → 圖片置中（跟設定頁預覽一樣）

旋轉：
□ 旋轉 90° → 圖片旋轉後仍然填滿鍵盤
□ 設定頁預覽和實際鍵盤顯示一致

影片：
□ 影片填滿鍵盤，不留白
□ 旋轉後仍填滿
```
