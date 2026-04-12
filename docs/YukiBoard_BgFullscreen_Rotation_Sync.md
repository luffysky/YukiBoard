# YukiBoard 背景全螢幕 + 旋轉 + 同步修復（合併任務包）

---

## 修改檔案總覽

| 檔案 | 修改內容 |
|------|---------|
| `service/YukiBoardIMEService.kt` | outerFrame 架構、bgRotation 套用、onRotationChanged callback |
| `ui/keyboard/KeyboardBackgroundView.kt` | lastModified 同步、rotation 成員、applyVideoTransform 旋轉 |
| `ui/settings/YukiPreferences.kt` | 新增 bgRotation |
| `ui/settings/SettingsActivity.kt` | 旋轉按鈕 UI |
| `ui/panel/ThemePanelView.kt` | 旋轉按鈕 + onRotationChanged callback |

---

# Part 1：背景全螢幕（outerFrame 架構）

## 根本原因

`bgView` 在 `keyboardContainer` 裡，`keyboardContainer` 是 `rootContainer` 的子 View。`applyKeyboardWidth` 縮的是 `rootContainer`，所有子 View 包含 `keyboardContainer` 都跟著縮。`FrameLayout` 裁切子 View，所以 `bgView` 設了 `screenW` 也沒用。

## 正確架構

```
outerFrame (FrameLayout, MATCH_PARENT)  ← onCreateInputView 回傳這個
  ├── bgView (MATCH_PARENT)             ← 全螢幕背景，不隨鍵盤縮窄
  └── rootContainer (LinearLayout)      ← 縮窄的鍵盤本體
        ├── toolbarView
        ├── keyboardContainer
        └── ...
```

## `service/YukiBoardIMEService.kt`

### 1. 成員變數

找到：
```kotlin
private lateinit var rootContainer: LinearLayout
```
下面加：
```kotlin
private var outerFrame: FrameLayout? = null
```

### 2. `onCreateInputView()` — bgView 移出 keyboardContainer

找到這段：
```kotlin
// 背景用螢幕完整寬度，避免鍵盤縮窄時露出黑色
val screenW = resources.displayMetrics.widthPixels
keyboardContainer.addView(bgView, FrameLayout.LayoutParams(
    screenW,
    FrameLayout.LayoutParams.MATCH_PARENT
))
backgroundView = bgView
```

**改為：**
```kotlin
backgroundView = bgView
// bgView 稍後加進 outerFrame，不加進 keyboardContainer
```

### 3. `onCreateInputView()` — return 前建立 outerFrame

找到：
```kotlin
return rootContainer
```

**改為：**
```kotlin
// 建立外層 FrameLayout，讓背景可以全螢幕
val frame = FrameLayout(this).also { outerFrame = it }

// bgView 加進 outerFrame 最底層
frame.addView(bgView, FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
))

// rootContainer（工具列+鍵盤）疊在上面
frame.addView(rootContainer, FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.WRAP_CONTENT
).apply {
    gravity = Gravity.BOTTOM
})

return frame
```

### 4. `applyKeyboardWidth()` 確認縮的是 rootContainer

```kotlin
applyKeyboardWidth(rootContainer)  // ← 縮 rootContainer，不縮 outerFrame，正確
```

### 5. 三處 `setInputView(onCreateInputView())` 前加清理

```kotlin
outerFrame = null  // ← 清掉舊的參考
backgroundView?.destroy()
backgroundView = null
setInputView(onCreateInputView())
```

---

# Part 2：影片同步修復（lastModified）

## 根本原因

新舊影片都存成同一個檔名 `keyboard_bg.mp4`，`reloadFromStorage()` 的 de-dup 邏輯只比對路徑：

```
原本播 A.mp4 → currentLoadedPath = ".../keyboard_bg.mp4"
選 B 影片 → 覆寫成同一個 keyboard_bg.mp4
sendBroadcast → reloadFromStorage()
→ currentLoadedPath == savedBg.absolutePath → TRUE → 跳過重載！
```

## `ui/keyboard/KeyboardBackgroundView.kt`

### 1. class 頂部加成員變數

```kotlin
private var currentLoadedLastModified: Long = 0L
```

### 2. `setVideoBackground(File)` 記錄修改時間

```kotlin
fun setVideoBackground(file: File) {
    clearBackground()
    currentType = BackgroundType.VIDEO
    currentLoadedPath = file.absolutePath
    currentLoadedLastModified = file.lastModified()  // ← 新增
    // ... 其餘不變
}
```

### 3. `setImageBackground(File)` 同樣記錄

```kotlin
fun setImageBackground(file: File) {
    clearBackground()
    currentType = BackgroundType.IMAGE
    currentLoadedPath = file.absolutePath
    currentLoadedLastModified = file.lastModified()  // ← 新增
    // ... 其餘不變
}
```

### 4. `reloadFromStorage()` de-dup 加 lastModified 比對

```kotlin
// 影片 de-dup：
if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg)
    && currentLoadedPath == savedBg.absolutePath
    && currentLoadedLastModified == savedBg.lastModified()  // ← 新增
    && mediaPlayer != null) {
    applyVideoTransform()
    return true
}

// 圖片 de-dup：
if (currentType == BackgroundType.IMAGE && isImageFile(savedBg) && imageView != null
    && currentLoadedPath == savedBg.absolutePath
    && currentLoadedLastModified == savedBg.lastModified()) {  // ← 新增
    applyOffset()
    return true
}
```

### 5. `clearBackground()` 重置

```kotlin
fun clearBackground() {
    currentType = BackgroundType.NONE
    currentLoadedPath = null
    currentLoadedLastModified = 0L  // ← 新增
    releaseMediaPlayer()
    removeAllViews()
    imageView = null
    textureView = null
    overlayView = null
}
```

---

# Part 3：旋轉功能

## `ui/settings/YukiPreferences.kt`

```kotlin
var bgRotation: Int by IntPref("bg_rotation", 0)  // 0, 90, 180, 270
```

## `ui/keyboard/KeyboardBackgroundView.kt`

### 1. 加 rotation 成員變數

```kotlin
var rotation: Int = 0  // 0, 90, 180, 270
    set(value) {
        field = value
        applyVideoTransform()
        applyOffset()
    }
```

### 2. 修改 `applyVideoTransform()`

```kotlin
private fun applyVideoTransform() {
    val tv = textureView ?: return
    val vw = tv.width.toFloat()
    val vh = tv.height.toFloat()
    if (vw <= 0 || vh <= 0 || videoWidth <= 0 || videoHeight <= 0) return

    val matrix = Matrix()
    val isRotated90or270 = rotation == 90 || rotation == 270

    // 旋轉後的有效寬高
    val effectiveW = if (isRotated90or270) videoHeight.toFloat() else videoWidth.toFloat()
    val effectiveH = if (isRotated90or270) videoWidth.toFloat() else videoHeight.toFloat()

    if (scaleMode == 1) {
        val sx = vw / effectiveW
        val sy = vh / effectiveH
        matrix.setScale(sx, sy)
    } else {
        val scale = maxOf(vw / effectiveW, vh / effectiveH)
        val scaledW = effectiveW * scale
        val scaledH = effectiveH * scale
        val maxDx = (scaledW - vw).coerceAtLeast(0f)
        val maxDy = (scaledH - vh).coerceAtLeast(0f)
        val dx = -maxDx * offsetX
        val dy = -maxDy * offsetY
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
    }

    // 套用旋轉
    if (rotation != 0) {
        val cx = vw / 2f
        val cy = vh / 2f
        val rotMatrix = Matrix()
        rotMatrix.setRotate(rotation.toFloat(), cx, cy)
        if (isRotated90or270) {
            val extraScale = if (vw / vh > 1f) vw / vh else vh / vw
            rotMatrix.postScale(extraScale, extraScale, cx, cy)
        }
        matrix.postConcat(rotMatrix)
    }

    tv.setTransform(matrix)
}
```

## `service/YukiBoardIMEService.kt`

### `onStartInputView` 套用 bgRotation

```kotlin
backgroundView?.scaleMode = prefs.bgScaleMode
backgroundView?.offsetX = prefs.bgOffsetX
backgroundView?.offsetY = prefs.bgOffsetY
backgroundView?.rotation = prefs.bgRotation  // ← 新增
```

### ThemePanelView callback 設定

```kotlin
p.onRotationChanged = { deg ->
    prefs.bgRotation = deg
    backgroundView?.rotation = deg
}
```

## `ui/settings/SettingsActivity.kt`

在比例切換按鈕（保持比例/整張塞入）下方加旋轉按鈕列：

```kotlin
// 用成員變數持有 4 個按鈕，方便互相更新樣式
val rotBtns = mutableListOf<android.widget.Button>()

val rotationRow = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(dp(12), dp(8), dp(12), dp(4))
}
rotationRow.addView(TextView(this).apply {
    text = tr("影片旋轉:")
    textSize = 12f
    setTextColor(0xFF7FD8FF.toInt())
    gravity = android.view.Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.MATCH_PARENT
    )
})

listOf(0 to "0°", 90 to "90°", 180 to "180°", 270 to "270°").forEach { (deg, label) ->
    val btn = android.widget.Button(this).apply {
        text = label
        textSize = 11f
        setPadding(dp(6), 0, dp(6), 0)
        layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(4) }
        setOnClickListener {
            prefs.bgRotation = deg
            rotBtns.forEachIndexed { i, b ->
                val d = listOf(0, 90, 180, 270)[i]
                b.setTextColor(if (prefs.bgRotation == d) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
                b.setBackgroundColor(if (prefs.bgRotation == d) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            }
            updateVideoPreviewTransform()
            fullPreviewView?.invalidate()
            sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
        }
    }
    // 初始樣式
    val selected = prefs.bgRotation == deg
    btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
    btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
    rotBtns.add(btn)
    rotationRow.addView(btn)
}
card.addView(rotationRow)
```

## `ui/panel/ThemePanelView.kt`

### 新增 callback

```kotlin
var onRotationChanged: ((Int) -> Unit)? = null
```

在現有比例切換按鈕的繪製區域旁，加旋轉按鈕（0° / 90° / 180° / 270°），觸控時呼叫：

```kotlin
onRotationChanged?.invoke(newDeg)
prefs.bgRotation = newDeg
invalidate()
```

---

# 執行順序

```
Step 1：YukiBoardIMEService.kt
  a. 加 outerFrame 成員變數
  b. onCreateInputView：bgView 移出 keyboardContainer
  c. onCreateInputView：return 改為回傳 outerFrame
  d. 三處 setInputView 前加 outerFrame = null
  e. onStartInputView：加 backgroundView?.rotation = prefs.bgRotation
  f. ThemePanelView callback：加 onRotationChanged

Step 2：KeyboardBackgroundView.kt
  a. 加 currentLoadedLastModified 成員變數
  b. 加 rotation 成員變數
  c. setVideoBackground(File)：記錄 lastModified
  d. setImageBackground(File)：記錄 lastModified
  e. reloadFromStorage()：de-dup 加 lastModified 比對（影片 + 圖片）
  f. clearBackground()：重置 lastModified
  g. applyVideoTransform()：加旋轉邏輯

Step 3：YukiPreferences.kt
  - 加 bgRotation: Int

Step 4：SettingsActivity.kt
  - 加旋轉按鈕列（含 syncBgSliders 也要同步旋轉按鈕樣式）

Step 5：ThemePanelView.kt
  - 加 onRotationChanged callback
  - 加旋轉按鈕繪製和觸控
```

---

# 驗證清單

```
全螢幕背景：
□ 鍵盤寬度設 80% → 背景延伸到全螢幕，不露黑色
□ 鍵盤寬度 100% → 正常

影片同步：
□ 設定頁選 A 影片 → 鍵盤播 A
□ 功能列選 B 影片 → 鍵盤立即切換播 B，設定頁預覽也更新
□ 反向：功能列選 A，設定頁再選 B → 立即切換

旋轉：
□ 選直拍影片 → 旋轉 90° → 橫向填滿鍵盤
□ 四個角度都正常
□ 設定頁和功能列都能操作旋轉
□ 重開 app 旋轉設定保留
```
