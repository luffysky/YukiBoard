# YukiBoard 背景區域修復

---

## 問題

`bgView` 是 `MATCH_PARENT × MATCH_PARENT`，放進 `frame` 之後，
因為 Android IME window 預設從螢幕頂端算起，`MATCH_PARENT` 就變成整個裝置高度。

---

## 需求

| 模式 | 背景行為 |
|------|---------|
| 一般模式 | 填滿「工具列以下的鍵盤區域」，不延伸到工具列上方 |
| 懸浮鍵盤模式 | 背景透明（不顯示背景） |

---

## 修法：bgView 高度跟著 keyboardContainer

不需要 outerFrame 這個架構，回歸最簡單的做法：

**bgView 留在 keyboardContainer 裡，寬度用 `MATCH_PARENT`（不設 screenW），高度 `MATCH_PARENT`。**

鍵盤縮窄時，keyboardContainer 縮窄，bgView 也跟著縮，旁邊是透明（系統讓底層 App 透出來）。這就是懸浮模式的正確行為。

一般模式（不縮窄）時，bgView 就是填滿整個 keyboardContainer，也就是工具列以下的鍵盤區域。

---

## `service/YukiBoardIMEService.kt` 修改

### 1. 完全移除 outerFrame 架構

移除成員變數：
```kotlin
// 刪掉這行：
private var outerFrame: FrameLayout? = null
```

### 2. `onCreateInputView()` — bgView 改回加進 keyboardContainer

找到這段（目前加進 frame 的）：
```kotlin
// 建立外層 FrameLayout，讓背景可以全螢幕
val frame = FrameLayout(this).also { outerFrame = it }
frame.addView(bgView, FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
))
frame.addView(rootContainer, FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.WRAP_CONTENT
).apply {
    gravity = android.view.Gravity.BOTTOM
})
return frame
```

**改回：**
```kotlin
keyboardContainer.addView(bgView, FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
))
backgroundView = bgView
// ... （接著原本的 keyboardContainer.addView(keyboardView...) 等）

return rootContainer
```

### 3. 移除三處 `outerFrame = null`

把之前加的三處 `outerFrame = null` 全部刪掉。

### 4. 懸浮模式：隱藏背景

在 `wrapInFloatingMode()` 函式裡，加一行隱藏背景：

```kotlin
private fun wrapInFloatingMode(content: View): View {
    // 懸浮模式不顯示背景
    backgroundView?.visibility = View.GONE
    // ... 其餘不變
}
```

在懸浮模式結束回到一般模式時（`wrapper.onToggleDock` 或其他退出浮動的地方），恢復背景：

```kotlin
backgroundView?.visibility = View.VISIBLE
```

---

## 驗證

```
一般模式：
□ 選背景圖 → 填滿工具列以下的鍵盤區域
□ 鍵盤縮窄（< 100%）→ 背景跟著縮，旁邊透明看到底層 App
□ 鍵盤 100% → 背景填滿整個鍵盤區域

懸浮模式：
□ 啟用懸浮鍵盤 → 背景不顯示（透明）
□ 關閉懸浮鍵盤回一般模式 → 背景恢復顯示
```
