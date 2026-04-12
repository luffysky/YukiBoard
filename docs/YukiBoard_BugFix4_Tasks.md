# YukiBoard 四個問題修復 任務包

> 基於最新版 YukiBoard-main__2_.zip
> 四個問題獨立修復，依序執行

---

## 修復一：圖片/影片預覽顯示在功能列主題面板

### 問題
ThemePanelView 的「鍵盤預覽」區塊只畫主題色按鍵縮圖，
沒有顯示使用者設定的自訂背景圖片/影片縮圖。

### 修改檔案：`ui/panel/ThemePanelView.kt`

找到 `drawKeyboardPreview()` 方法，在繪製鍵盤底色的地方加入背景圖預覽。

**步驟 1：確認 `previewBgBitmap` 在正確時機被載入**

在 `ThemePanelView` 的 `init {}` 或每次顯示時呼叫 `reloadPreviewBitmap()`：

```kotlin
// 在 ThemePanelView 加入：
override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    reloadPreviewBitmap()  // 每次面板顯示時重新載入背景預覽
}
```

**步驟 2：在 `drawKeyboardPreview()` 裡繪製背景圖**

找到 `drawKeyboardPreview()` 方法，在畫鍵盤背景色之後、畫按鍵之前，加入背景圖繪製：

```kotlin
private fun drawKeyboardPreview(canvas: Canvas, startY: Float): Float {
    val previewH = 80f * density
    val previewW = width - pad * 2
    val rect = RectF(pad, startY, pad + previewW, startY + previewH)

    // ── 1. 鍵盤底色 ──
    cardPaint.color = theme.backgroundColorInt
    canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

    // ── 2. 自訂背景圖（如果有的話）──
    val bgBmp = previewBgBitmap
    if (bgBmp != null && !bgBmp.isRecycled) {
        canvas.save()
        canvas.clipRoundRect(rect, cornerR, cornerR)   // 裁剪成圓角

        // 計算 center-crop 的 Matrix
        val scaleX = previewW / bgBmp.width
        val scaleY = previewH / bgBmp.height
        val scale = maxOf(scaleX, scaleY)
        val offsetX = (previewW - bgBmp.width * scale) / 2f
        val offsetY = (previewH - bgBmp.height * scale) / 2f
        previewBgMatrix.reset()
        previewBgMatrix.setScale(scale, scale)
        previewBgMatrix.postTranslate(pad + offsetX, startY + offsetY)

        // 半透明遮罩（讓按鍵看得清楚）
        bgPaint.alpha = 200
        canvas.drawBitmap(bgBmp, previewBgMatrix, bgPaint)
        bgPaint.alpha = 255

        // 深色遮罩（跟 KeyboardBackgroundView 的 overlayAlpha 一致）
        val overlayPaint = Paint().apply {
            color = android.graphics.Color.argb(
                (0.35f * 255).toInt(), 0, 0, 0
            )
        }
        canvas.drawRoundRect(rect, cornerR, cornerR, overlayPaint)
        canvas.restore()
    }

    // ── 3. 候選字列 ──
    val candH = 16f * density
    cardPaint.color = theme.candidateBarColorInt
    canvas.drawRoundRect(
        RectF(rect.left, rect.top, rect.right, rect.top + candH),
        cornerR, 0f, cardPaint
    )

    // ── 4. 幾排示意按鍵 ──
    val keyRows = 3
    val keysPerRow = 5
    val keyMargin = 3f * density
    val keyAreaTop = rect.top + candH + keyMargin
    val keyAreaH = previewH - candH - keyMargin * 2
    val keyH = (keyAreaH - keyMargin * (keyRows - 1)) / keyRows
    val keyW = (previewW - keyMargin * (keysPerRow + 1)) / keysPerRow

    for (row in 0 until keyRows) {
        for (col in 0 until keysPerRow) {
            val kx = rect.left + keyMargin + col * (keyW + keyMargin)
            val ky = keyAreaTop + row * (keyH + keyMargin)
            val keyRect = RectF(kx, ky, kx + keyW, ky + keyH)
            previewKeyPaint.color = if (col == keysPerRow - 1 && row == keyRows - 1)
                theme.specialKeyColorInt else theme.keyColorInt
            canvas.drawRoundRect(keyRect, 3f * density, 3f * density, previewKeyPaint)
        }
    }

    return startY + previewH + pad
}
```

**步驟 3：`canvas.clipRoundRect` 相容性處理**

`clipRoundRect` 在 API 26+ 才有，用以下方式相容舊版本：

```kotlin
// 改用 clipPath
val clipPath = Path().apply {
    addRoundRect(rect, cornerR, cornerR, Path.Direction.CW)
}
canvas.save()
canvas.clipPath(clipPath)
// ... 繪製背景圖
canvas.restore()
```

---

## 修復二：圖片換第二次鍵盤沒有跟著換

### 根本原因

`KeyboardBackgroundView.reloadFromStorage()` 有判斷：
```kotlin
// 圖片已載入就只套用 offset，不重新載入圖片
if (currentType == BackgroundType.IMAGE && !isVideoFile(savedBg) && imageView != null) {
    applyOffset()
    return true  // ← 換了新圖片也不重載！
}
```

只要 `currentType == IMAGE` 且 `imageView != null`，就直接 return，**完全不載入新圖片**。

### 修改檔案：`ui/keyboard/KeyboardBackgroundView.kt`

找到 `reloadFromStorage()` 方法，修改判斷邏輯：

```kotlin
fun reloadFromStorage(): Boolean {
    val savedBg = getSavedBackground(context) ?: run {
        if (currentType != BackgroundType.NONE) clearBackground()
        return false
    }

    // ── 影片：如果是同一個檔案才跳過重載 ──
    if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg) && mediaPlayer != null) {
        // 確認是同一個影片檔案
        if (currentLoadedPath == savedBg.absolutePath) {
            applyVideoTransform()
            return true
        }
        // 不同影片：繼續往下重載
    }

    // ── 圖片：如果是同一個檔案才跳過重載 ──
    if (currentType == BackgroundType.IMAGE && !isVideoFile(savedBg) && imageView != null) {
        // 確認是同一個圖片檔案
        if (currentLoadedPath == savedBg.absolutePath) {
            applyOffset()
            return true
        }
        // 不同圖片：繼續往下重載
    }

    if (isVideoFile(savedBg)) {
        setVideoBackground(savedBg)
    } else {
        setImageBackground(savedBg)
    }
    return true
}
```

同時在 `setImageBackground(file: File)` 和 `setVideoBackground(file: File)` 裡記錄路徑：

```kotlin
// 在 class 頂部加入屬性
private var currentLoadedPath: String? = null

// 在 setImageBackground(file: File) 開頭加
fun setImageBackground(file: File) {
    currentLoadedPath = file.absolutePath  // ← 加這行
    clearBackground()
    currentType = BackgroundType.IMAGE
    // ... 其餘不變
}

// 在 setVideoBackground(file: File) 開頭加
fun setVideoBackground(file: File) {
    currentLoadedPath = file.absolutePath  // ← 加這行
    // ... 其餘不變
}

// 在 clearBackground() 加
fun clearBackground() {
    currentLoadedPath = null  // ← 加這行
    currentType = BackgroundType.NONE
    // ... 其餘不變
}
```

---

## 修復三：語音測試沒聲音

### 根本原因

`AndroidTtsEngine` 使用 `USAGE_MEDIA` + `STREAM_MUSIC`，
但在 IME context 或 SettingsActivity 裡，
`STREAM_MUSIC` 有時候會被系統靜音或搶不到 AudioFocus。

### 修改檔案：`speech/AndroidTtsEngine.kt`

找到 `setAudioAttributes` 的地方，改用更適合 TTS 的音頻屬性：

```kotlin
// 原本：
tts?.setAudioAttributes(
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
)

// 改為：
tts?.setAudioAttributes(
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)  // 強制可聽見
        .build()
)
```

同時在 `speak()` 方法裡改 Bundle params：

```kotlin
// 原本：
val params = Bundle().apply {
    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
}

// 改為：
val params = Bundle().apply {
    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
    // STREAM_NOTIFICATION 不受靜音模式和 AudioFocus 影響
    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)  // 確保音量 100%
}
```

同時在 SettingsActivity 的測試按鈕加音量檢查提示：

```kotlin
// 在 ttsSettingsSection() 的測試按鈕點擊裡，engine 初始化成功後加：
val audioMgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager

// 如果通知音量是 0，提示使用者
val notifVol = audioMgr.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
if (notifVol == 0) {
    Toast.makeText(
        this@SettingsActivity,
        "通知音量為 0，請至系統設定調高音量後再測試",
        Toast.LENGTH_LONG
    ).show()
}
```

---

## 修復四：滑動停頓判斷（按鍵停頓自動確認音節）

### 功能說明

iOS 注音滑動的體驗：滑到某個按鍵上**停頓超過 300ms**，
系統自動確認這個音節，然後繼續滑下一個音節。
這樣不需要手指離開螢幕就能輸入多個音節的詞組。

### 修改檔案：`ui/keyboard/KeyboardView.kt`

**步驟 1：加入停頓偵測屬性**

在現有的 swipe 相關屬性區加入：

```kotlin
// ─── 滑動停頓偵測 ───────────────────────────────────────────
private var swipeDwellKey: KeyModel? = null      // 目前停頓的按鍵
private var swipeDwellStartMs = 0L              // 開始停頓的時間
private val swipeDwellThresholdMs = 300L        // 停頓多久算確認（ms）
private val swipeDwellHandler = android.os.Handler(android.os.Looper.getMainLooper())
private val swipeDwellRunnable = Runnable {
    // 停頓超過閾值：確認這個音節
    swipeDwellKey?.let { key ->
        onSwipeDwellConfirmed(key)
    }
}
```

**步驟 2：在 `onSwipeMove()` 加入停頓偵測**

找到 `onSwipeMove(x: Float, y: Float)` 方法：

```kotlin
fun onSwipeMove(x: Float, y: Float) {
    if (!isSwipingActive) return

    val last = swipePoints.lastOrNull()
    val dist = if (last != null)
        Math.hypot((x - last.x).toDouble(), (y - last.y).toDouble())
    else Double.MAX_VALUE

    if (dist >= 4f * density) {
        swipePoints.add(PointF(x, y))

        // 找到目前所在的按鍵
        val currentKey = keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
        if (currentKey != null && currentKey != swipeHighlightKey) {
            flashKey(currentKey)

            // ── 停頓偵測：換了新按鍵就重新計時 ──
            swipeDwellHandler.removeCallbacks(swipeDwellRunnable)

            if (currentKey != swipeDwellKey) {
                swipeDwellKey = currentKey
                swipeDwellStartMs = System.currentTimeMillis()
                // 300ms 後觸發停頓確認
                swipeDwellHandler.postDelayed(swipeDwellRunnable, swipeDwellThresholdMs)
            }
        }

        invalidate()
    }
}
```

**步驟 3：加入 `onSwipeDwellConfirmed()` 方法**

```kotlin
/**
 * 停頓確認：使用者在某個注音按鍵上停頓超過閾值。
 * 效果：音節分隔（等同按了一下再繼續滑）。
 * 視覺：亮起確認動畫 + 短震動。
 */
private fun onSwipeDwellConfirmed(key: KeyModel) {
    // 只在注音模式生效
    if (currentMode.inputMode != com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO) return

    // 視覺回饋：按鍵亮起較強的閃爍
    flashKeyStrong(key)

    // 震動（短促）
    if (hapticEnabled) {
        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
            as? android.os.Vibrator
        vibrator?.vibrate(30)
    }

    // 通知 IMEService：這個音節確認了，請先查候選字
    onSwipeSyllableConfirmed?.invoke(key.label)

    // 重置停頓計時
    swipeDwellKey = null
}

// 音節確認回呼（由 IMEService 設定）
var onSwipeSyllableConfirmed: ((syllable: String) -> Unit)? = null
```

**步驟 4：加入 `flashKeyStrong()` 視覺效果**

```kotlin
private fun flashKeyStrong(key: KeyModel) {
    swipeHighlightKey = key
    swipeHighlightAlpha = 1.0f  // 比普通 flashKey 更亮

    highlightFadeAnimator?.cancel()
    highlightFadeAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 0f).apply {
        duration = 350
        interpolator = android.view.animation.OvershootInterpolator()
        addUpdateListener { anim ->
            swipeHighlightAlpha = anim.animatedValue as Float
            invalidate()
        }
        start()
    }
}
```

**步驟 5：在 `onSwipeEnd()` 清理**

```kotlin
fun onSwipeEnd() {
    isSwipingActive = false
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)  // ← 加這行
    swipeDwellKey = null                                    // ← 加這行
    // ... 其餘 fadeOut 動畫邏輯不變
}
```

**步驟 6：在 `onDetachedFromWindow()` 清理**

```kotlin
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)  // ← 加這行
    swipeFadeAnimator?.cancel()
    highlightFadeAnimator?.cancel()
    // ... 其他現有清理邏輯
}
```

---

## 執行順序

```
修復二（最優先）：KeyboardBackgroundView 加 currentLoadedPath
   → 換圖馬上生效，不需要重開鍵盤

修復一：ThemePanelView drawKeyboardPreview 加背景圖預覽
   → 功能列看得到背景縮圖

修復三：AndroidTtsEngine 改 STREAM_NOTIFICATION
   → 語音測試有聲音

修復四：KeyboardView 停頓偵測
   → 滑動輸入更精準

每個修復完成後 Build 驗證一次。
```

---

## 快速驗證方式

修復二：
1. 設定頁選一張背景圖 A
2. 再選一張背景圖 B
3. 切換到 LINE，確認背景是 B（不是 A）

修復一：
1. 設定完背景後進功能列
2. 點主題，確認鍵盤預覽縮圖有顯示背景圖

修復三：
1. 設定頁語音輸出測試按鈕
2. 確保通知音量不是 0
3. 點測試，應該聽到聲音

修復四：
1. 切換到注音模式
2. 滑動時在某個按鍵停頓 0.3 秒
3. 應該看到按鍵閃爍 + 感受到震動
