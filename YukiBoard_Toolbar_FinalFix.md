# YukiBoard 工具列透明 — 最終修法

## 根本原因確認

前兩次修法（`setWillNotDraw(false)` + 移除 `super.onDraw`）是對的邏輯，
但在某些 Android 版本 + 硬體加速 + `clipChildren=false` 的組合下，
`canvas.drawRect` 畫的背景無法正確合成到螢幕上。

**最可靠的解法：不靠 `onDraw` 畫背景，改用 `setBackgroundColor`**，
讓 Android 的 View 繪製機制直接處理背景，不經過 canvas 合成。

---

## 修改 `ui/panel/ToolbarView.kt`

### 第一步：`theme` setter 加 `updateBackground()`

```kotlin
var theme: KeyboardTheme = KeyboardTheme.default()
    set(value) {
        field = value
        updateBackground()   // ← 新增
        invalidate()
    }
```

### 第二步：`setTransparent()` 也要更新背景

```kotlin
fun setTransparent(transparent: Boolean) {
    isTransparent = transparent
    updateBackground()   // ← 新增
    invalidate()
}
```

### 第三步：新增 `updateBackground()` 函式

加在 `setTransparent` 下面：

```kotlin
private fun updateBackground() {
    if (isTransparent) {
        setBackgroundColor(0x88000000.toInt())
    } else {
        setBackgroundColor(theme.effectiveToolbarColor())
    }
}
```

### 第四步：`init` block 更新

```kotlin
init {
    setWillNotDraw(false)
    updateBackground()   // ← 新增：建構時立刻設背景色
}
```

### 第五步：`onDraw` 移除 `canvas.drawRect` 背景那行

```kotlin
override fun onDraw(canvas: Canvas) {
    // 背景由 setBackgroundColor 處理，不在這裡畫
    // (以下移除這段)
    // if (isTransparent) {
    //     bgPaint.color = 0x88000000.toInt()
    // } else {
    //     bgPaint.color = theme.effectiveToolbarColor()
    // }
    // canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // 計算按鈕佈局（含捲動偏移）
    btnRects.clear()
    var x = edgePad - scrollX
    // ... 其餘不變
```

---

## 完整修改後的關鍵段落

```kotlin
class ToolbarView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val barHeight = (40 * density).toInt()

    init {
        setWillNotDraw(false)
        updateBackground()
    }

    // ... Paint 宣告等不變 ...

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) {
            field = value
            updateBackground()
            invalidate()
        }

    // ...

    private var isTransparent = false
    fun setTransparent(transparent: Boolean) {
        isTransparent = transparent
        updateBackground()
        invalidate()
    }

    private fun updateBackground() {
        if (isTransparent) {
            setBackgroundColor(0x88000000.toInt())
        } else {
            setBackgroundColor(theme.effectiveToolbarColor())
        }
    }

    // ...

    override fun onDraw(canvas: Canvas) {
        // 背景由 setBackgroundColor 處理，這裡只畫 icon 和按鈕

        btnRects.clear()
        var x = edgePad - scrollX
        // ... icon 繪製邏輯完全不變 ...
    }
}
```

---

## 驗證

```
□ 工具列有正確的背景色（snow_light 主題下是淡藍色 #FFE2EAF8）
□ 工具列顯示所有按鈕圖示（🐾🎤🌐😀顔🖼 等）
□ sakura 主題下工具列是粉色
□ 透明鍵盤模式下工具列是半透明黑色
□ 切換主題後工具列顏色即時更新
```
