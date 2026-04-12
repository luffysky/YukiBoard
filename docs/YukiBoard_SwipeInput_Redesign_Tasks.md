# YukiBoard — 滑動輸入改版：改用停頓偵測取代路徑偵測

---

## 問題根本原因

### 現在的設計：記錄路徑上「經過的所有按鍵」

```
使用者滑動：ㄔ → ㄈ → ㄢ（打「吃飯」）
路徑上實際經過：ㄔ ㄖ ㄙ ㄨ ㄘ ㄕ ㄘ ㄖ ㄙ ㄈ ㄌ ㄏ ㄒ ㄖ ㄙ ㄩ ㄝ ㄡ ㄥ ㄠ ㄤ ㄣ ㄟ ㄢ
全部塞進 swipeKeySequence → 解碼出一堆無關的注音 → 查不到「吃飯」
```

核心問題是：**把「路徑上路過的鍵」當作「使用者想打的鍵」**。

### 使用者真正的意圖

```
「吃飯」的注音滑動：
  → ㄔ（停一下，明確選中這個鍵）
  → 滑向 ㄈ（停一下）
  → 滑向 ㄢ（停一下，放手）

使用者的意圖是「我在這三個鍵上停過」，不是「我路過了哪些鍵」
```

---

## 解法：改用停頓偵測（Dwell）作為主要機制

**停頓 = 手指在某個鍵上的速度降低或停留超過閾值**

```
新邏輯：
  手指在 ㄔ 上按下 → 記錄 ㄔ
  移動途中經過 ㄖㄙㄨ... → 不記錄（速度快，路過）
  手指在 ㄈ 上速度降低 → 記錄 ㄈ（停頓確認）
  移動途中經過 ㄌㄏ... → 不記錄
  手指在 ㄢ 上放開 → 記錄 ㄢ（放手確認）

最終序列：ㄔ ㄈ ㄢ → 查到「吃飯」✅
```

---

## 修改一：KeyboardView.kt — 速度感知的停頓偵測

### 新增速度追蹤狀態變數

在現有的 `// ─── 滑動停頓偵測 ───` 區塊附近加入：

```kotlin
// ─── 滑動速度感知停頓偵測 ───
private val swipeVelocityPoints = ArrayDeque<Pair<Long, PointF>>()  // 時間戳+座標
private val VELOCITY_WINDOW_MS = 80L         // 計算速度的時間窗口
private val DWELL_VELOCITY_THRESHOLD = 200f  // px/s，低於此速度算停頓
private val CONFIRMED_SWIPE_KEYS = mutableListOf<KeyModel>()  // 停頓確認的鍵
private var lastConfirmedKey: KeyModel? = null
```

### 修改 `onSwipeMove()` 函式

找到 `fun onSwipeMove(x: Float, y: Float)` 整段替換：

```kotlin
fun onSwipeMove(x: Float, y: Float) {
    if (!isSwipingActive) return

    val now = System.currentTimeMillis()
    val newPoint = PointF(x, y)

    // 累積速度計算點
    swipeVelocityPoints.addLast(now to newPoint)
    // 只保留最近 VELOCITY_WINDOW_MS 的點
    while (swipeVelocityPoints.isNotEmpty() &&
        now - swipeVelocityPoints.first().first > VELOCITY_WINDOW_MS) {
        swipeVelocityPoints.removeFirst()
    }

    // 計算當前速度（px/s）
    val currentVelocity = if (swipeVelocityPoints.size >= 2) {
        val oldest = swipeVelocityPoints.first()
        val newest = swipeVelocityPoints.last()
        val dt = (newest.first - oldest.first).toFloat() / 1000f
        val dx = newest.second.x - oldest.second.x
        val dy = newest.second.y - oldest.second.y
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dt > 0) dist / dt else Float.MAX_VALUE
    } else Float.MAX_VALUE

    // 更新繪製路徑
    val last = swipePoints.lastOrNull()
    if (last == null || Math.hypot((x - last.x).toDouble(), (y - last.y).toDouble()) > 4.0 * density) {
        swipePoints.add(newPoint)
        invalidate()
    }

    // 取得當前所在按鍵
    val currentKey = keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
        ?: return

    if (currentKey.isSpecial) return

    // 停頓偵測：速度低於閾值 且 換了按鍵 → 確認這個按鍵
    val isSlowingDown = currentVelocity < DWELL_VELOCITY_THRESHOLD

    if (currentKey != lastConfirmedKey) {
        if (isSlowingDown) {
            // 速度已降低，立即確認這個鍵
            confirmSwipeKey(currentKey)
        } else {
            // 速度還快，用原本的 dwell timer 等待停頓
            swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
            if (currentKey != swipeDwellKey) {
                swipeDwellKey = currentKey
                swipeDwellHandler.postDelayed(swipeDwellRunnable, swipeDwellThresholdMs)
            }
        }
    }
}
```

### 新增 `confirmSwipeKey()` 函式

```kotlin
private fun confirmSwipeKey(key: KeyModel) {
    if (key == lastConfirmedKey) return
    lastConfirmedKey = key

    // 加入確認序列
    CONFIRMED_SWIPE_KEYS.add(key)

    // 視覺回饋：按鍵閃爍 + 震動
    swipeHighlightKey = key
    swipeHighlightAlpha = 1.0f
    highlightFadeAnimator?.cancel()
    highlightFadeAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 0f).apply {
        duration = 250
        addUpdateListener { anim ->
            swipeHighlightAlpha = anim.animatedValue as Float
            invalidate()
        }
        start()
    }
    if (hapticEnabled) vibrateShort()
}
```

### 修改 `onSwipeEnd()` 開頭

在 `onSwipeEnd()` 裡，放手時確認最後一個按鍵：

```kotlin
fun onSwipeEnd() {
    if (!isSwipingActive && swipePoints.isEmpty()) return
    isSwipingActive = false
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)

    // 放手的位置也算一個確認點（最後一個鍵）
    val lastPoint = swipePoints.lastOrNull()
    if (lastPoint != null) {
        val lastKey = keyEntries.firstOrNull {
            it.touchRect.contains(lastPoint.x, lastPoint.y)
        }?.key
        if (lastKey != null && !lastKey.isSpecial && lastKey != lastConfirmedKey) {
            confirmSwipeKey(lastKey)
        }
    }

    // 用停頓確認的鍵序列，而不是路徑上的所有鍵
    // 回傳給 IMEService 解碼
    // ... （見下方修改 swipeKeySequence 的地方）
}
```

### 修改 `resetTouchState()` 清理新狀態

```kotlin
fun resetTouchState() {
    candidateTouchActive = false
    candidateIsDragging = false
    pressedKey = null
    isSwipingActive = false
    isSwipeGesture = false
    swipePoints.clear()
    swipeKeySequence.clear()
    CONFIRMED_SWIPE_KEYS.clear()      // ← 新增
    lastConfirmedKey = null            // ← 新增
    swipeVelocityPoints.clear()        // ← 新增
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
    swipeDwellKey = null
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    invalidate()
}
```

---

## 修改二：把 swipeKeySequence 換成 CONFIRMED_SWIPE_KEYS

現在 `swipeKeySequence` 記錄的是「路徑上所有鍵」，改成用 `CONFIRMED_SWIPE_KEYS`。

找到 ACTION_UP 裡的：

```kotlin
// 原本：
if (isSwipeGesture && swipeKeySequence.size >= 2) {
    onSwipeInputListener?.invoke(swipeKeySequence.toList())
    swipeKeySequence.clear()
    isSwipeGesture = false
    onSwipeEnd()
    performClick()
    return true
}

// 改為：
if (isSwipeGesture && CONFIRMED_SWIPE_KEYS.size >= 2) {
    onSwipeInputListener?.invoke(CONFIRMED_SWIPE_KEYS.toList())
    CONFIRMED_SWIPE_KEYS.clear()
    lastConfirmedKey = null
    swipeKeySequence.clear()
    isSwipeGesture = false
    onSwipeEnd()
    performClick()
    return true
}
```

同時在 ACTION_DOWN 的 `onSwipeStart()` 初始化加：

```kotlin
CONFIRMED_SWIPE_KEYS.clear()
lastConfirmedKey = null
swipeVelocityPoints.clear()
// 起始鍵直接加入確認序列
if (hit != null && !hit.key.isSpecial) {
    CONFIRMED_SWIPE_KEYS.add(hit.key)
    lastConfirmedKey = hit.key
}
```

---

## 修改三：swipeDwellRunnable 改呼叫 confirmSwipeKey

```kotlin
private val swipeDwellRunnable = Runnable {
    swipeDwellKey?.let { confirmSwipeKey(it) }  // ← 改用 confirmSwipeKey
    swipeDwellKey = null
}
```

---

## 修改四：調整閾值（關鍵數字）

```kotlin
private val swipeDwellThresholdMs = 180L        // 原本 300ms，降到 180ms
                                                  // 停頓偵測更靈敏
private val DWELL_VELOCITY_THRESHOLD = 300f      // px/s，可以調高讓更容易觸發
private val SWIPE_DISTANCE_THRESHOLD = 10f * density  // 觸發滑動的門檻維持低
```

---

## 修改五（可選）：雙模式支援

有些使用者習慣快速掃過（Gboard 風格），有些習慣停頓確認（倉頡風格）。
可以加一個設定切換：

### 在 `YukiPreferences.kt` 加：

```kotlin
var swipeInputMode: Int
    get() = prefs.getInt("swipe_input_mode", 0)
    set(value) = prefs.edit().putInt("swipe_input_mode", value).apply()
// 0 = 停頓模式（新設計，適合注音）
// 1 = 路徑模式（原設計，適合英文）
```

### 在 `KeyboardView.kt` 的 `onSwipeMove()` 加判斷：

```kotlin
// 如果是路徑模式（英文），維持原本的 swipeKeySequence 邏輯
// 如果是停頓模式（注音），用 CONFIRMED_SWIPE_KEYS 邏輯
```

---

## 效果說明

```
改版前（打「吃飯」ㄔ→ㄈ→ㄢ）：
  記錄路徑：ㄔ ㄖ ㄙ ㄘ ㄕ ㄈ ㄌ ㄏ ㄒ ㄖ ㄙ ㄩ ㄝ ㄡ ㄤ ㄣ ㄟ ㄢ
  → 18 個符號，解碼失敗

改版後（打「吃飯」ㄔ→ㄈ→ㄢ）：
  確認序列：ㄔ（起點）ㄈ（停頓）ㄢ（放手）
  → 3 個符號，直接查到「吃飯」✅

改版後（打「你好」ㄋ→ㄧ→ㄏ→ㄠ）：
  確認序列：ㄋ ㄧ ㄏ ㄠ
  → 4 個符號，查到「你好」✅
```

---

## 執行順序

```
1. KeyboardView.kt → 新增速度追蹤狀態變數
2. KeyboardView.kt → 修改 onSwipeMove() 加速度感知
3. KeyboardView.kt → 新增 confirmSwipeKey()
4. KeyboardView.kt → 修改 swipeDwellRunnable 呼叫 confirmSwipeKey
5. KeyboardView.kt → 修改 onSwipeEnd() 確認最後一個鍵
6. KeyboardView.kt → ACTION_UP 改用 CONFIRMED_SWIPE_KEYS
7. KeyboardView.kt → ACTION_DOWN 初始化新狀態
8. KeyboardView.kt → resetTouchState() 清理新狀態
9. 調整閾值 swipeDwellThresholdMs = 180
Build 測試
```

---

## 驗證

```
□ 打「吃飯」：從 ㄔ 滑到 ㄈ 滑到 ㄢ → 候選出現「吃飯」
□ 打「你好」：從 ㄋ 滑到 ㄧ 到 ㄏ 到 ㄠ → 候選出現「你好」
□ 快速路過不記錄：滑過 ㄖ 不停頓 → ㄖ 不出現在確認序列
□ 起始鍵一定被記錄（手指按下的那個鍵）
□ 放手的鍵一定被記錄（最後停的那個鍵）
□ 停頓中間鍵被記錄（速度降低或停留超過 180ms）
```
