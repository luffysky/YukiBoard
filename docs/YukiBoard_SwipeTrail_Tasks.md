# YukiBoard 滑動輸入路徑視覺 任務包

> 目標：做出像 iOS / Samsung 鍵盤那樣的滑動路徑視覺效果
> 包含：漸層路徑、起點光點、按鍵亮起、手指離開後路徑淡出動畫
> 基於現有 KeyboardView.kt 架構

---

## 效果說明

### iOS QuickPath 的路徑設計
- 細白線 + 半透明
- 路徑寬度一致（不隨速度變化）
- 手指離開後路徑迅速淡出（約 300ms）
- 起點有小圓點

### Samsung 的路徑設計
- 藍色/主題色漸層
- 路徑有發光效果（雙層：外層模糊 + 內層清晰）
- 經過的按鍵有短暫高亮
- 路徑淡出較慢（約 500ms）

### YukiBoard 設計方向
- 主色：淡紫色 #B39DDB（跟 AI 候選字一致的品牌色）
- 外層模糊發光 + 內層清晰路徑（參考 Samsung）
- 起點光圈動畫
- 路徑經過的按鍵短暫縮放高亮
- 手指離開後路徑淡出（400ms）
- 受主題系統控制（可以跟鍵盤主題同色）

---

## 修改 KeyboardView.kt

### 步驟一：加入滑動路徑相關屬性

在 `class KeyboardView` 的屬性區（`// ─── Paint ───` 附近）加入：

```kotlin
// ─── 滑動路徑視覺 ───────────────────────────────────────────
// 路徑點序列
private val swipePoints = mutableListOf<PointF>()
// 是否正在滑動中
private var isSwipingActive = false
// 路徑淡出動畫進度（1f=完全顯示，0f=完全隱藏）
private var swipeAlpha = 0f
// 路徑淡出用的 ValueAnimator
private var swipeFadeAnimator: android.animation.ValueAnimator? = null
// 最後一次滑動經過的按鍵（用於高亮）
private var swipeHighlightKey: KeyModel? = null
private var swipeHighlightAlpha = 0f
private var highlightFadeAnimator: android.animation.ValueAnimator? = null

// 外層發光 Paint（模糊）
private val swipeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = 18f * density
    // BlurMaskFilter 讓路徑有發光感
    maskFilter = BlurMaskFilter(12f * density, BlurMaskFilter.Blur.NORMAL)
}

// 內層清晰路徑 Paint
private val swipeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = 5f * density
}

// 起點圓點 Paint
private val swipeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}

// 按鍵高亮 Paint
private val swipeKeyHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}

// 路徑用 Path 物件（避免每次 onDraw 重建）
private val swipePath = Path()

// 滑動路徑主題色（預設淡紫，theme 改變時更新）
private var swipeColor = 0xFFB39DDB.toInt()
```

### 步驟二：在 `applyTheme()` 或 theme setter 裡更新滑動色

找到 `var theme: KeyboardTheme` 的 setter（`set(value)`），在裡面加：

```kotlin
// 更新滑動路徑顏色（跟 AI 候選字同色，或用主題強調色）
swipeColor = value.candidateSelectedColorInt.let { base ->
    // 讓顏色更亮一點
    android.graphics.Color.argb(
        255,
        (android.graphics.Color.red(base) * 1.2f).toInt().coerceAtMost(255),
        (android.graphics.Color.green(base) * 1.2f).toInt().coerceAtMost(255),
        (android.graphics.Color.blue(base) * 1.2f).toInt().coerceAtMost(255)
    )
}
// fallback：如果主題色太深，用預設紫色
if (android.graphics.Color.red(swipeColor) + android.graphics.Color.green(swipeColor) +
    android.graphics.Color.blue(swipeColor) < 200) {
    swipeColor = 0xFFB39DDB.toInt()
}
```

### 步驟三：加入滑動路徑控制方法

```kotlin
/**
 * 開始滑動：清空路徑，記錄起點。
 * 由外部（SwipeGestureDetector 或 onTouchEvent）呼叫。
 */
fun onSwipeStart(x: Float, y: Float) {
    swipeFadeAnimator?.cancel()
    swipePoints.clear()
    swipePoints.add(PointF(x, y))
    isSwipingActive = true
    swipeAlpha = 1f

    // 亮起起點按鍵
    val startKey = keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
    if (startKey != null) flashKey(startKey)

    invalidate()
}

/**
 * 滑動中：加入新點，更新路徑。
 * 每次 ACTION_MOVE 呼叫。
 */
fun onSwipeMove(x: Float, y: Float) {
    if (!isSwipingActive) return

    val last = swipePoints.lastOrNull()
    // 間隔取樣：距上一點 > 4dp 才加（避免點過密）
    if (last == null || Math.hypot((x - last.x).toDouble(), (y - last.y).toDouble()) > 4f * density) {
        swipePoints.add(PointF(x, y))

        // 偵測是否滑到新的按鍵，亮起按鍵
        val currentKey = keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
        if (currentKey != null && currentKey != swipeHighlightKey) {
            flashKey(currentKey)
        }

        invalidate()
    }
}

/**
 * 滑動結束：觸發路徑淡出動畫。
 */
fun onSwipeEnd() {
    isSwipingActive = false

    // 400ms 淡出動畫
    swipeFadeAnimator?.cancel()
    swipeFadeAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 400
        interpolator = android.view.animation.DecelerateInterpolator()
        addUpdateListener { anim ->
            swipeAlpha = anim.animatedValue as Float
            invalidate()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                swipePoints.clear()
                invalidate()
            }
        })
        start()
    }
}

/**
 * 按鍵短暫高亮閃爍（滑過時）。
 */
private fun flashKey(key: KeyModel) {
    swipeHighlightKey = key
    swipeHighlightAlpha = 0.6f

    highlightFadeAnimator?.cancel()
    highlightFadeAnimator = android.animation.ValueAnimator.ofFloat(0.6f, 0f).apply {
        duration = 200
        addUpdateListener { anim ->
            swipeHighlightAlpha = anim.animatedValue as Float
            invalidate()
        }
        start()
    }
}
```

### 步驟四：在 onDraw 末尾加入滑動路徑繪製

在 `override fun onDraw(canvas: Canvas)` 的最後（`drawExpandedCandidatePanel` 之後）加入：

```kotlin
// 滑動路徑（繪製在所有元素最上層）
if (swipePoints.size >= 2 || (swipePoints.size == 1 && isSwipingActive)) {
    drawSwipeTrail(canvas)
}
```

新增 `drawSwipeTrail()` 方法：

```kotlin
private fun drawSwipeTrail(canvas: Canvas) {
    if (swipePoints.isEmpty()) return
    val alpha = (swipeAlpha * 255).toInt().coerceIn(0, 255)
    if (alpha == 0) return

    // ── 構建路徑 ──
    swipePath.reset()
    if (swipePoints.size == 1) {
        // 只有一個點：只畫起點圓
        val p = swipePoints[0]
        swipeDotPaint.color = swipeColor
        swipeDotPaint.alpha = alpha
        canvas.drawCircle(p.x, p.y, 10f * density, swipeDotPaint)
        return
    }

    // 使用貝茲曲線讓路徑更平滑（不用直線連接）
    swipePath.moveTo(swipePoints[0].x, swipePoints[0].y)
    for (i in 1 until swipePoints.size - 1) {
        val curr = swipePoints[i]
        val next = swipePoints[i + 1]
        // 控制點用相鄰兩點的中點
        val midX = (curr.x + next.x) / 2f
        val midY = (curr.y + next.y) / 2f
        swipePath.quadTo(curr.x, curr.y, midX, midY)
    }
    swipePath.lineTo(swipePoints.last().x, swipePoints.last().y)

    // ── 外層發光（模糊，較粗，半透明）──
    swipeGlowPaint.color = swipeColor
    swipeGlowPaint.alpha = (alpha * 0.35f).toInt()
    canvas.drawPath(swipePath, swipeGlowPaint)

    // ── 內層清晰路徑 ──
    // 漸層：起點較透明，終點較亮（表現「流動感」）
    swipeLinePaint.color = swipeColor
    swipeLinePaint.alpha = alpha
    canvas.drawPath(swipePath, swipeLinePaint)

    // ── 起點光圈 ──
    val start = swipePoints[0]
    // 外圈（半透明）
    swipeDotPaint.color = swipeColor
    swipeDotPaint.alpha = (alpha * 0.4f).toInt()
    canvas.drawCircle(start.x, start.y, 14f * density, swipeDotPaint)
    // 內圈（實心）
    swipeDotPaint.alpha = (alpha * 0.9f).toInt()
    canvas.drawCircle(start.x, start.y, 6f * density, swipeDotPaint)

    // ── 按鍵高亮 ──
    val highlightKey = swipeHighlightKey
    if (highlightKey != null && swipeHighlightAlpha > 0f) {
        val keyEntry = keyEntries.firstOrNull { it.key == highlightKey }
        if (keyEntry != null) {
            swipeKeyHighlightPaint.color = swipeColor
            swipeKeyHighlightPaint.alpha = (swipeHighlightAlpha * 255).toInt()
            val rect = keyEntry.visualRect
            canvas.drawRoundRect(
                rect, 8f * density, 8f * density,
                swipeKeyHighlightPaint
            )
        }
    }
}
```

### 步驟五：在 onTouchEvent 整合滑動路徑

**注意：** 這裡整合的是「路徑視覺」，不是「滑動輸入邏輯」（輸入邏輯在 SwipeGestureDetector）。

在 `ACTION_DOWN` 裡（按鍵區域，不是候選列）加入：

```kotlin
MotionEvent.ACTION_DOWN -> {
    // ... 現有邏輯 ...

    // 如果點的是鍵盤區域（不是候選列），記錄起點
    if (y >= candidateBarHeight && prefs.swipeInputEnabled) {
        onSwipeStart(x, y)
    }
}
```

在 `ACTION_MOVE` 裡（滑動鍵盤區域）加入：

```kotlin
MotionEvent.ACTION_MOVE -> {
    // ... 現有邏輯 ...

    // 更新滑動路徑
    if (y >= candidateBarHeight && prefs.swipeInputEnabled) {
        onSwipeMove(x, y)
    }
}
```

在 `ACTION_UP` 和 `ACTION_CANCEL` 裡加入：

```kotlin
MotionEvent.ACTION_UP -> {
    // ... 現有邏輯 ...
    onSwipeEnd()
}

MotionEvent.ACTION_CANCEL -> {
    // ... 現有邏輯 ...
    onSwipeEnd()
    swipePoints.clear()
    swipeAlpha = 0f
    invalidate()
}
```

---

## 注意事項

### BlurMaskFilter 與硬體加速

`BlurMaskFilter` 在硬體加速開啟時需要 `setLayerType(LAYER_TYPE_SOFTWARE, null)`，
否則發光效果不顯示（會自動 fallback 到無模糊版）。

在 `KeyboardView` 的 `init {}` 或建構子裡：

```kotlin
// 開啟軟體渲染層，讓 BlurMaskFilter 生效
// 只在鍵盤 View 上，不影響整個 Activity 效能
setLayerType(LAYER_TYPE_SOFTWARE, null)
```

**但這會有效能影響！** 如果覺得卡頓，可以改成：
```kotlin
// 替代方案：不用 BlurMaskFilter，用雙層路徑模擬發光
// 外層：粗線 + 透明 → 視覺上像模糊
swipeGlowPaint.maskFilter = null  // 不用 BlurMaskFilter
swipeGlowPaint.strokeWidth = 22f * density  // 外層更粗
swipeGlowPaint.alpha = (alpha * 0.25f).toInt()  // 更透明
// 這樣不需要 software layer，效能更好
```

建議先用雙層路徑方案，如果視覺效果不夠再加 BlurMaskFilter。

### 清除資源

在 `onDetachedFromWindow()` 裡取消動畫：

```kotlin
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    swipeFadeAnimator?.cancel()
    highlightFadeAnimator?.cancel()
    // ... 現有邏輯 ...
}
```

---

## YukiPreferences 新增開關

```kotlin
var swipeTrailEnabled: Boolean by BoolPref("swipe_trail", true)
var swipeTrailStyle: String by StringPref("swipe_trail_style", "glow")
// "glow" = 發光版, "simple" = 細線版（效能優先）
```

在 `drawSwipeTrail()` 開頭加判斷：
```kotlin
private fun drawSwipeTrail(canvas: Canvas) {
    if (!prefs.swipeTrailEnabled) return
    // ...
}
```

---

## 執行順序

```
1. 在 KeyboardView.kt 加入所有屬性（swipePoints, Paint 等）
2. 在 applyTheme / theme setter 更新 swipeColor
3. 加入 onSwipeStart / onSwipeMove / onSwipeEnd / flashKey 方法
4. 加入 drawSwipeTrail() 方法
5. 在 onDraw 末尾呼叫 drawSwipeTrail()
6. 在 onTouchEvent 的 DOWN/MOVE/UP/CANCEL 整合
7. 在 onDetachedFromWindow 取消動畫
8. YukiPreferences 加開關
9. Build 驗證
```

---

## 效果預期

| 動作 | 視覺效果 |
|------|---------|
| 手指按下 | 起點亮起光圈（雙層圓點）|
| 手指滑動 | 紫色發光路徑跟隨（貝茲曲線，流暢）|
| 滑過按鍵 | 按鍵短暫高亮（200ms 淡出）|
| 手指離開 | 路徑 400ms 淡出消失 |
| 主題切換 | 路徑顏色跟著主題變 |
