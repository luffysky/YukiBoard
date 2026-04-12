# YukiBoard 背景縮放與旋轉修復

---

## 需求確認

| scaleMode | 行為 |
|-----------|------|
| 0（保持比例）| 縮放到**完整顯示在鍵盤內**（fit-inside，`minOf`），不裁切，多餘空間透明。X/Y 滑桿調整影片在鍵盤內的位置 |
| 1（整張塞入）| 拉伸填滿整個鍵盤（stretch） |

旋轉是在 fit-inside 之前先旋轉影片，然後再縮放到完整顯示在鍵盤內。

---

## `ui/keyboard/KeyboardBackgroundView.kt`

### 修改一：`applyOffset()`（圖片）

**`maxOf` 改 `minOf`，並修正 X/Y 位置邏輯：**

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
        val sx = vw / bmpWidth
        val sy = vh / bmpHeight
        imageMatrix.setScale(sx, sy)
    } else {
        // 保持比例，完整顯示在鍵盤內（fit-inside）
        val scale = minOf(vw / bmpWidth, vh / bmpHeight)  // ← maxOf 改 minOf
        val scaledW = bmpWidth * scale
        val scaledH = bmpHeight * scale
        // 剩餘空間，X/Y 控制影片在其中的位置（0=靠左上，1=靠右下，0.5=置中）
        val remainX = (vw - scaledW).coerceAtLeast(0f)
        val remainY = (vh - scaledH).coerceAtLeast(0f)
        val dx = remainX * offsetX
        val dy = remainY * offsetY
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)
    }

    savedMatrix.set(imageMatrix)
    iv.imageMatrix = imageMatrix
}
```

### 修改二：`applyVideoTransform()`（影片）

**正確的旋轉 + fit-inside 順序：**

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

    // 旋轉後的有效寬高（影片旋轉 90/270 時寬高對調）
    val isRotated = bgRotation == 90 || bgRotation == 270
    val effectiveW = if (isRotated) vH else vW
    val effectiveH = if (isRotated) vW else vH

    // 縮放比例
    val scale = when (scaleMode) {
        1 -> 1f  // stretch 由 postScale 個別處理
        else -> minOf(vw / effectiveW, vh / effectiveH)  // fit-inside
    }

    val matrix = Matrix()

    // Step 1：影片原點移到 View 中心（以影片中心為基準）
    matrix.setTranslate(cx - vW / 2f, cy - vH / 2f)

    // Step 2：以 View 中心旋轉
    if (bgRotation != 0) {
        matrix.postRotate(bgRotation.toFloat(), cx, cy)
    }

    // Step 3：縮放（以 View 中心為基準，確保縮放後仍置中）
    if (scaleMode == 1) {
        val sx = vw / effectiveW
        val sy = vh / effectiveH
        matrix.postScale(sx, sy, cx, cy)
    } else {
        matrix.postScale(scale, scale, cx, cy)

        // Step 4：X/Y 偏移（在 fit-inside 的剩餘空間內移動）
        val scaledW = effectiveW * scale
        val scaledH = effectiveH * scale
        val remainX = (vw - scaledW).coerceAtLeast(0f)
        val remainY = (vh - scaledH).coerceAtLeast(0f)
        // offsetX/Y = 0 靠左上，0.5 置中，1 靠右下
        // 預設 0.5 = 置中
        val dx = remainX * offsetX - remainX * 0.5f  // 相對置中的偏移
        val dy = remainY * offsetY - remainY * 0.5f
        matrix.postTranslate(dx, dy)
    }

    tv.setTransform(matrix)
}
```

### 修改三：setter 同時觸發影片和圖片更新

```kotlin
var offsetX: Float = 0.5f
    set(value) {
        field = value.coerceIn(0f, 1f)
        applyOffset()
        applyVideoTransform()  // ← 新增
    }
var offsetY: Float = 0.5f
    set(value) {
        field = value.coerceIn(0f, 1f)
        applyOffset()
        applyVideoTransform()  // ← 新增
    }
var scaleMode: Int = 0
    set(value) {
        field = value
        applyOffset()
        applyVideoTransform()  // ← 新增
    }
```

---

## 行為說明

### scaleMode = 0（保持比例，fit-inside）

```
鍵盤寬 > 鍵盤高（橫向），影片 9:16（直向）：
→ scale = minOf(vw/vW, vh/vH) = vh/vH（高度 fit）
→ 影片高度 = 鍵盤高度，寬度 < 鍵盤寬度
→ 左右有剩餘空間
→ offsetX=0.5 → 影片水平置中
→ offsetX=0 → 影片靠左
→ offsetX=1 → 影片靠右

旋轉 90° 後影片變 16:9（橫向）：
→ effectiveW 變大，effectiveH 變小
→ scale = minOf(vw/effectiveW, vh/effectiveH)
→ 影片完整顯示在鍵盤內，不裁切
```

### scaleMode = 1（整張塞入）

```
拉伸填滿鍵盤，不保持比例
X/Y 位置無效（已填滿）
```

---

## 驗證

```
圖片 fit-inside：
□ 設直向圖片（9:16）→ 圖片完整顯示在鍵盤內，左右有留白
□ offsetX 滑桿可以控制圖片左右位置
□ offsetY 滑桿可以控制圖片上下位置
□ 切換「整張塞入」→ 圖片拉伸填滿

影片 fit-inside：
□ 設直向影片 → 影片完整顯示在鍵盤內
□ X/Y 滑桿可以移動影片位置

旋轉後仍在鍵盤內：
□ 旋轉 90° → 旋轉後的影片重新 fit-inside，完整顯示
□ 旋轉後 X/Y 滑桿仍然有效
□ 四個角度（0/90/180/270）都正確

不裁切：
□ 任何情況下 scaleMode=0 時，影片/圖片邊緣不被裁切
```
