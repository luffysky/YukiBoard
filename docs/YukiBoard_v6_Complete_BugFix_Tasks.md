# YukiBoard v6 完整整合修復任務包

> 整合兩份文件：YukiBoard_v6_ComprehensiveFix_Tasks + YukiBoard_v6_Final_BugFix_Tasks
> 完整掃描 208 檔案 / 52017 行，去除重複，按優先度排序

---

## ✅ v6 已確認修好（不需要再做）

```
✅ ZCoinManager @Synchronized（topUp / syncBalance / spend）
✅ 影片背景 pause() / resume()
✅ CharacterView pauseAnimation() / resumeAnimation()
✅ 候選列攔截 candidateHitZone = candidateBarHeight - 8dp
✅ 速度感知停頓偵測（confirmSwipeKey + DWELL_VELOCITY_THRESHOLD）
✅ 英文 fuzzyCorrectWithAdjacency
✅ 日文 KANA_SIMILAR + romajiHistory + fuzzyLookupCandidates
✅ 韓文 JAMO_ADJACENT + fuzzyCorrectKorean
✅ 注音 BOPOMOFO_ADJACENT + fuzzyPredictFromBuffer
✅ PanelInputTarget 介面 + activePanelInput 統一路由
✅ 倉頡引擎完整實作（非 stub）
```

---

# 🔴 第一批：崩潰 / 完全無法用（必修）

---

## B1：DecryptPanelView pressedBtn!! 崩潰

**`ui/panel/DecryptPanelView.kt` line 276**

```kotlin
// 原本：
if (!isDragging && pressedBtn != null) {
    handleClick(pressedBtn!!)
}

// 改為：
pressedBtn?.takeIf { !isDragging }?.let { handleClick(it) }
```

---

## B2：解密面板無法輸入（enterInputMode 缺失）

**根本原因：** `DecryptPanelView` 沒有 `enterInputMode()` 函式，建立時沒呼叫，`isInInputMode` 永遠 false。

### `ui/panel/DecryptPanelView.kt` 新增：

```kotlin
override fun enterInputMode() {
    isInputMode = true
    activeField = "password"
    inputBuffer.clear()
    if (password.isNotEmpty()) inputBuffer.append(password)
    composingHint = ""
    invalidate()
}
```

### `service/YukiBoardIMEService.kt` — `PanelType.DECRYPT` 建立後加：

```kotlin
PanelType.DECRYPT -> {
    val p = DecryptPanelView(this).also { decryptPanel = it }
    p.theme = theme
    p.onEncryptedResult = { encrypted ->
        currentInputConnection?.commitText(encrypted, 1)
    }
    p.enterInputMode()    // ← 新增
    p
}
```

同時確認 `PanelType.USER_DICT` 也有呼叫 `enterInputMode()`。

---

## B3：按鍵需要點 3-4 下才反應

**根本原因：** `resetTouchState()` 沒有清除 `isLongPressTriggered`，radial menu 或 alt popup 使用後 flag 殘留。

### `ui/keyboard/KeyboardView.kt` — `resetTouchState()` 加一行：

```kotlin
fun resetTouchState() {
    candidateTouchActive = false
    candidateIsDragging = false
    pressedKey = null
    isLongPressTriggered = false    // ← 新增
    isSwipingActive = false
    swipePoints.clear()
    swipeKeySequence.clear()
    confirmedSwipeKeys.clear()
    lastConfirmedKey = null
    swipeVelocityPoints.clear()
    isSwipeGesture = false
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
    swipeDwellKey = null
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    invalidate()
}
```

### `ACTION_UP` 開頭加一行：

```kotlin
MotionEvent.ACTION_UP -> {
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    pressedKey = null
    isLongPressTriggered = false    // ← 新增
    // ... 其餘不變
}
```

---

## B4：第一排 / 第二排（及所有排）按不到

**根本原因：** 只有 `rowIndex == 0` 往上延伸，其他排 `touchTop = top`，排與排交界處加上 `keyPaddingV` 縮小 visualRect 後容易落空。

### `ui/keyboard/KeyboardView.kt` — `drawKeyRows()` 裡 touchRect 計算：

```kotlin
// 原本：
val touchTop = if (rowIndex == 0) 0f else top
val touchRect = RectF(x, touchTop, x + keyWidth, top + rh)

// 改為：每排都往上延伸 30%，消除所有排間縫隙
val touchTop = when (rowIndex) {
    0 -> 0f                     // 第一排延伸到頂
    else -> top - rh * 0.3f    // 其他排往上延伸 30%
}
val touchRect = RectF(x, touchTop, x + keyWidth, top + rh)
```

### fallback expandPx 從 8dp 加大到 16dp：

```kotlin
// 原本：
val expandPx = 8f * density

// 改為：
val expandPx = 16f * density
```

---

# 🟡 第二批：輸入正確性

---

## B5：輸入框混入前次 composing 文字

**根本原因：** `finishComposingText()` 在某些 App（LINE）有延遲，沒有 beginBatchEdit 保護。

### `service/YukiBoardIMEService.kt` — `applyInputResult()` 的 CommitText 分支：

```kotlin
is InputResult.CommitText -> {
    // ... 情緒冷靜期檢查不變 ...

    // 改為 batchEdit 保護
    ic.beginBatchEdit()
    ic.finishComposingText()
    ic.commitText(result.text, 1)
    ic.endBatchEdit()

    // ... characterView?.playHappy() 等不變
}
```

### `onStartInputView()` 開頭加清除：

```kotlin
override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    // 先清除上一個 session 的 composing 殘留
    currentInputConnection?.let { ic ->
        ic.beginBatchEdit()
        ic.finishComposingText()
        ic.endBatchEdit()
    }
    inputRouter.reset()
    keyboardView.updateComposingText("")
    keyboardView.updateCandidates(emptyList())
    // ... 其餘不變
}
```

---

# 🟡 第三批：顯示問題

---

## B6：解密面板佔整面

**根本原因：** `DecryptPanelView` 是 custom View，沒有 `onMeasure()`，`WRAP_CONTENT` 無效。

### `ui/panel/DecryptPanelView.kt` 新增 `onMeasure()`：

```kotlin
override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val w = MeasureSpec.getSize(widthMeasureSpec)
    val maxH = (260 * resources.displayMetrics.density).toInt()
    setMeasuredDimension(w, maxH)
}
```

### 同時讓內容更緊湊：

```kotlin
// drawFieldCard 裡：
val cardH = 44f * density    // 原本 52f

// onDraw 裡的 btnH：
val btnH = 32f * density     // 原本 36f
```

---

## B7：雪凜氣泡框被截斷

**根本原因：** `keyboardContainer` 和 `rootContainer` 預設 `clipChildren = true`，氣泡超出邊界被截掉。

### `service/YukiBoardIMEService.kt` — `createInputView()` 裡：

```kotlin
rootContainer = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    clipChildren = false      // ← 新增
    clipToPadding = false     // ← 新增
}

keyboardContainer = FrameLayout(this).apply {
    clipChildren = false      // ← 新增
    clipToPadding = false     // ← 新增
}
```

---

# 🟡 第四批：語音 + Coroutine 安全

---

## B8：FasterWhisperSpeechEngine 缺靜音偵測

WhisperSpeechEngine 已有，FasterWhisper 沒有。

### `speech/FasterWhisperSpeechEngine.kt` — 錄音 while loop 加入：

在 `recordingJob = scope.launch {` 內，`var lastPartialTime` 那行旁邊加：

```kotlin
var silenceStartTime = 0L
```

在 `if (read > 0) {` 區塊，PCM 寫入後加：

```kotlin
val maxAmplitude = (0 until read)
    .maxOfOrNull { kotlin.math.abs(buffer[it].toInt()) } ?: 0
val now = System.currentTimeMillis()
if (maxAmplitude < SILENCE_AMPLITUDE_THRESHOLD) {
    if (silenceStartTime == 0L) silenceStartTime = now
    else if (now - silenceStartTime >= SILENCE_THRESHOLD_MS
             && pcmStream.size() > sampleRate * 2) {
        android.util.Log.i(tag, "silence detected, auto-stopping")
        isListening = false
        break
    }
} else {
    silenceStartTime = 0L
}
```

在 companion object 加常數：

```kotlin
private const val SILENCE_AMPLITUDE_THRESHOLD = 500
private const val SILENCE_THRESHOLD_MS = 3000L
```

---

## B9：StickerPanelView launch 無 try-catch

### `ui/panel/StickerPanelView.kt`：

```kotlin
scope.launch {
    try {
        val bmp = repository?.getStickerBitmap(url)
        loadedBitmaps[url] = bmp
        loadingUrls.remove(url)
        post { invalidate() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loadingUrls.remove(url)
        android.util.Log.w("StickerPanel", "load failed: ${e.message}")
    }
}
```

---

## B10：ClawResultFloatingPanel typewriter 無 try-catch

### `ui/floating/ClawResultFloatingPanel.kt`：

```kotlin
private fun playTypewriterEffect(text: String) {
    scope.coroutineContext.cancelChildren()
    scope.launch {
        try {
            resultText?.text = ""
            for (i in text.indices) {
                if (!isActive) break
                resultText?.text = text.substring(0, i + 1)
                delay(25)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            resultText?.text = text
        }
    }
}
```

---

# 🔵 第五批：記憶體洩漏（匿名 postDelayed）

---

## B11：KeyboardView toast postDelayed 匿名 lambda

**`ui/keyboard/KeyboardView.kt` line 352, 364**

```kotlin
// 在 class 頂部加：
private var pendingToastClear: Runnable? = null

// showToast() 改為：
fun showToast(msg: String) {
    modeToastText = msg
    modeToastEndTime = System.currentTimeMillis() + modeToastDuration
    invalidate()
    pendingToastClear?.let { handler.removeCallbacks(it) }
    pendingToastClear = Runnable {
        if (modeToastText == msg) {
            modeToastText = null
            invalidate()
        }
    }.also { handler.postDelayed(it, modeToastDuration) }
}

// showModeToast() 同理（把匿名 lambda 換成具名 Runnable）

// onDetachedFromWindow 加：
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    pendingToastClear?.let { handler.removeCallbacks(it) }  // ← 新增
}
```

---

## B12：CharacterView playIdle 匿名 lambda

**`ui/keyboard/CharacterView.kt` line 120**

```kotlin
// 在 class 加：
private val playIdleRunnable = Runnable { playIdle() }

// 原本：
vv.postDelayed({ playIdle() }, 2000)
// 改為：
vv.removeCallbacks(playIdleRunnable)
vv.postDelayed(playIdleRunnable, 2000)

// cleanupCurrentMode() 裡加：
vrmView?.removeCallbacks(playIdleRunnable)
```

---

# 🟢 第六批：功能升級

---

## U1：候選字常見字排前面

**現況：** `updateCandidates()` 有 `adaptiveDictionary` 加分，但沒有基礎常用字頻率。

### `input/engine/ChewingInputEngine.kt` — 新增 public 方法：

```kotlin
fun getCommonCharScore(ch: Char): Int = COMMON_CHAR_FREQ[ch] ?: 0
```

### `input/router/InputRouter.kt` — `updateCandidates()` 的 `boosted` 排序加：

```kotlin
val boosted = raw.sortedWith(compareByDescending<String> { word ->
    var score = 0f
    val freq = smartInput.adaptiveDictionary.getFrequency(word)
    if (freq > 0) score += freq + 50

    // ← 新增：基礎常用字加分
    if (word.length == 1) {
        val commonScore = (bopomofoEngine as? ChewingInputEngine)
            ?.getCommonCharScore(word.first()) ?: 0
        score += commonScore * 0.4f
    }

    // ← 新增：常用詞組加分（從 companion object 查）
    val commonFreq = COMMON_CHAR_FREQUENCY[word]
    if (commonFreq != null) score += commonFreq

    val ctxScore = contextPredictions[word]
    if (ctxScore != null) score += ctxScore * 5
    val ngramScore = nextCharBoost[word]
    if (ngramScore != null) score += ngramScore * 3
    if (smartInput.personalDictionary.isKnown(word)) score += 30
    if (userDict.contains(word)) score += 20
    score
}.thenBy { raw.indexOf(it) })
```

### 同時在 `InputRouter` companion object 新增頻率表：

```kotlin
private val COMMON_CHAR_FREQUENCY = mapOf(
    "的" to 40f, "是" to 38f, "在" to 35f, "了" to 34f, "我" to 33f,
    "他" to 32f, "她" to 32f, "你" to 32f, "們" to 30f, "不" to 30f,
    "有" to 29f, "這" to 28f, "那" to 28f, "也" to 27f, "就" to 26f,
    "都" to 25f, "說" to 25f, "要" to 24f, "會" to 24f, "來" to 23f,
    "去" to 23f, "可以" to 28f, "沒有" to 27f, "知道" to 25f,
    "覺得" to 24f, "喜歡" to 23f, "謝謝" to 22f, "好的" to 22f,
    "OK" to 20f, "ok" to 20f, "嗯" to 18f, "哈哈" to 17f,
)
```

---

## U2：AUTO 模式候選字中英混合

**現況：** AUTO 模式只顯示當前引擎的候選，沒有同時顯示中英。

### `input/router/InputRouter.kt` — `updateCandidates()` 裡，在 `if (raw.size > 1)` 之前加 AUTO 分支：

```kotlin
// AUTO 模式：同時顯示注音和英文候選
if (inputMode == InputMode.AUTO && autoQwertyBuffer.isNotEmpty()) {
    val engCands = (englishEngine as? EnglishInputEngine)
        ?.getCandidatesForPrefix(autoQwertyBuffer.toString())
        ?: emptyList()
    val bopoCands = (bopomofoEngine as? ChewingInputEngine)
        ?.getCandidatesForBopomofo(autoBopomofoBuffer.joinToString(""))
        ?: emptyList()

    val combined = if (autoEnglishScore >= autoBopomofoScore)
        engCands + bopoCands
    else
        bopoCands + engCands

    val sourceMap = mutableMapOf<String, CandidateSource>()
    engCands.forEach { sourceMap[it] = CandidateSource.PREDICTION }
    bopoCands.forEach { if (it !in sourceMap) sourceMap[it] = CandidateSource.DICTIONARY }
    candidateSourceMap = sourceMap
    candidateManager.update(combined.distinct().take(12))
    return
}
```

---

## U3：候選字長按放大鏡

**前置條件：** B7（clipChildren = false）需先完成。

### `ui/keyboard/KeyboardView.kt` — 新增狀態變數：

```kotlin
private var candidateLongPressIndex = -1
private var candidateLongPressActive = false
private var candidateLongPressX = 0f
private val CANDIDATE_LONG_PRESS_DELAY = 400L
private val candidateLongPressRunnable = Runnable {
    val idx = hitTestCandidateIndex(candidateLongPressX)
    if (idx >= 0 && idx < candidates.size) {
        candidateLongPressIndex = idx
        candidateLongPressActive = true
        if (hapticEnabled) vibrateShort()
        invalidate()
    }
}
```

### `ACTION_DOWN` 候選列區域加長按計時：

```kotlin
if (y < candidateHitZone && candidates.isNotEmpty()) {
    candidateLastTouchX = x
    candidateDownX = x
    candidateIsDragging = false
    candidateTouchActive = true
    candidateLongPressX = x                                              // ← 新增
    handler.postDelayed(candidateLongPressRunnable, CANDIDATE_LONG_PRESS_DELAY)  // ← 新增
    return true
}
```

### `ACTION_MOVE` 候選列拖動時取消長按：

```kotlin
if (candidateTouchActive && candidates.isNotEmpty()) {
    handler.removeCallbacks(candidateLongPressRunnable)   // ← 新增
    // ... 原有拖動邏輯
}
```

### `ACTION_UP` 候選列選字前處理長按：

```kotlin
if (candidateTouchActive && candidates.isNotEmpty()) {
    handler.removeCallbacks(candidateLongPressRunnable)   // ← 新增
    candidateTouchActive = false

    if (candidateLongPressActive) {                       // ← 新增整段
        candidateLongPressActive = false
        candidateLongPressIndex = -1
        invalidate()
        performClick()
        return true  // 長按：不選字，只取消放大鏡
    }
    // ... 原有選字邏輯不變
}
```

### `drawCandidateBar()` 末尾 `canvas.restore()` 後加：

```kotlin
canvas.restore()
if (candidateLongPressActive && candidateLongPressIndex >= 0) {
    drawCandidateMagnifier(canvas, candidateLongPressIndex)
}
```

### 新增繪製函式：

```kotlin
private fun drawCandidateMagnifier(canvas: Canvas, index: Int) {
    val text = candidates.getOrNull(index) ?: return
    val bubbleW = 80f * density
    val bubbleH = 48f * density
    val cornerR = 10f * density

    var accum = candFixedLeftCache - candidateScrollX
    for (i in 0 until index) accum += (candWidthsCache.getOrNull(i) ?: 0f) + candGapCache
    val candCx = accum + (candWidthsCache.getOrNull(index) ?: 0f) / 2f
    val bubbleLeft = (candCx - bubbleW / 2f).coerceIn(0f, width - bubbleW)
    val bubbleTop = -bubbleH - 8f * density  // 往上顯示（需要 clipChildren=false）

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyColorInt }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.candidateSelectedColorInt
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    val rect = RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH)
    canvas.drawRoundRect(rect, cornerR, cornerR, bgPaint)
    canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)

    val magnifyPaint = Paint(candidatePaint).apply {
        textSize = 22f * density
        textAlign = Paint.Align.CENTER
        color = theme.candidateSelectedColorInt
        isFakeBoldText = true
    }
    canvas.drawText(text, rect.centerX(), rect.centerY() + magnifyPaint.textSize * 0.35f, magnifyPaint)

    val tail = android.graphics.Path()
    tail.moveTo(rect.centerX() - 6f * density, rect.bottom)
    tail.lineTo(rect.centerX(), rect.bottom + 6f * density)
    tail.lineTo(rect.centerX() + 6f * density, rect.bottom)
    tail.close()
    canvas.drawPath(tail, bgPaint)
}
```

---

## 執行順序

```
第一批（崩潰優先）：
  B1  DecryptPanelView pressedBtn?.let
  B2  enterInputMode() + IMEService 呼叫
  B3  isLongPressTriggered 清除（resetTouchState + ACTION_UP）
  B4  touchRect 每排延伸 + expandPx 16dp
  Build → 測試每個按鍵第一下就反應

第二批（輸入正確性）：
  B5  beginBatchEdit + onStartInputView 清 composing
  Build → 測試不混前文

第三批（顯示）：
  B6  DecryptPanelView onMeasure 限高 260dp + 緊湊化
  B7  clipChildren = false（rootContainer + keyboardContainer）
  Build → 測試氣泡不截斷 + 解密面板正常

第四批（語音 + Coroutine）：
  B8  FasterWhisper 靜音偵測
  B9  StickerPanelView try-catch
  B10 ClawResultFloatingPanel try-catch
  Build

第五批（記憶體）：
  B11 KeyboardView toast Runnable 具名化
  B12 CharacterView playIdle Runnable 具名化
  Build

第六批（功能升級）：
  U1  ChewingInputEngine.getCommonCharScore() + InputRouter 常用字加分
  U2  AUTO 模式中英混合候選
  U3  候選字長按放大鏡（需 B7 先完成）
  Build
```

---

## 驗證清單

```
鍵盤觸控：
□ 注音每排（聲調行、ˇˋ等、ㄆㄊ等、ㄇㄋ等）第一下有反應
□ QWERTY 每排（數字行、qwer、asdf、zxcv）第一下有反應
□ radial menu 使用後繼續打字正常
□ alt popup 使用後繼續打字正常

輸入正確性：
□ 打一段文字送出 → 新輸入框不混前文
□ 快速連打不出現多餘 composing

解密面板：
□ 開解密面板 → 鍵盤仍可見（面板約 260dp）
□ 直接打字 → 密碼欄有文字

顯示：
□ 雪凜氣泡完整不截斷
□ 長按候選字 400ms → 放大鏡泡泡出現
□ 放開手 → 泡泡消失（不觸發選字）

語音：
□ Whisper 說完靜音 3 秒自動停
□ FasterWhisper 說完靜音 3 秒自動停

候選字：
□ 注音打「ㄉ」→「的」排第一
□ AUTO 模式同時有中文和英文候選
```
