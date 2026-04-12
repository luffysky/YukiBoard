# YukiBoard 候選字 + 輸入法問題修復任務包

> 基於截圖 + 程式碼分析
> 五個問題，依序修復

---

## 問題診斷

```
問題一：AI 候選字（黃色✨）不知道怎麼選
原因：UI 上沒有任何說明，✨ 前綴讓使用者以為是裝飾
解法：移除 ✨ 前綴，改用底色區分，加長按提示

問題二：英文打完按候選字沒反應
原因：candidateManager.selectAt(index) 回傳 null
     → candidateManager 的 _candidates 可能是空的
     → candidates 是 UI 顯示的列表，但 candidateManager 內部分頁不同步
解法：英文候選選取改直接從 candidates[index] 取值 commit

問題三：輸入框打不開 / 功能列後無法打字
原因一：某些 App 的 EditorInfo 不觸發 onStartInputView
原因二：panelContainer 覆蓋 keyboardView 的 touch 區域
        panelContainer.visibility = GONE 但 removeAllViews 時序問題
        造成 keyboardView touch 被攔截
解法：closePanel() 確保 keyboardView 重獲 focus

問題四：注音候選字要最接近注音的排前面
原因：lookupCandidates 把精確查詢（含聲調）的結果排在前面，
     但「最接近注音」的直覺排序沒有實作
     例如打 ㄅㄞ 不加聲調，應該把 ㄅㄞ¹白 排第一，ㄅㄞˋ敗 排後面
解法：加聲調相似度得分，精確聲調優先，無聲調次之，其他聲調再後

問題五（截圖觀察）：候選字列的 AI 候選字視覺
截圖裡看到三個黃色 ✨ 標籤（使用體驗、按鍵手感、設計特色）
這是 AI Prediction Skill 建議的文字，不是注音候選字
這在注音輸入模式下不應該出現，會讓使用者搞混
```

---

## 修復一：AI 候選字視覺改善 + 明確可點

### 修改 `ui/keyboard/KeyboardView.kt`

**找到 AI 候選字的 displayText 計算，改掉 ✨ 前綴：**

```kotlin
// 原本：
val displayText = when {
    isSwipeCandidateMode && i == 0 -> "≈$text"
    candidateSourceMap[text] == CandidateSource.AI -> "✨$text"
    else -> text
}

// 改為（移除 ✨，改用右上角小標記）：
val displayText = when {
    isSwipeCandidateMode && i == 0 -> "≈$text"
    else -> text  // AI 候選字用底色區分，不加前綴
}
```

**AI 候選字的底色加深，讓使用者知道可以點：**

```kotlin
// 找到 AI 候選字的繪製區段，修改底色：
CandidateSource.AI -> {
    // 更明顯的底色（不只是 alpha 40，改成 70）
    keyPaint.color = 0xFFE8B84D.toInt()
    keyPaint.alpha = 70   // 原本是 40，加深讓使用者知道這是可選的
    val pill = RectF(left + 2 * density, 4 * density, right - 2 * density, candidateBarHeight - 4 * density)
    canvas.drawRoundRect(pill, 6 * density, 6 * density, keyPaint)
    keyPaint.alpha = 255
    // 右上角畫一個小 AI 標記（不用 emoji，用小圓點）
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8B84D.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(right - 6f * density, 6f * density, 3f * density, dotPaint)
}
```

**同時修改 candWidthsCache 計算（移除 ✨ 後寬度重算）：**

```kotlin
// 找到 candWidths 計算，移除 ✨：
val candWidths = candidates.mapIndexed { i, text ->
    val display = when {
        isSwipeCandidateMode && i == 0 -> "≈$text"
        else -> text   // ← 移除 "✨$text"，改用純 text
    }
    candidatePaint.measureText(display) + candPadH * 2
}
```

---

## 修復二：英文候選字按了沒反應

### 根本原因

`candidateManager.selectAt(index)` 在英文模式有時回傳 null：
```kotlin
// InputRouter.selectCandidate 英文分支：
val selected = candidateManager.selectAt(index)
if (selected != null) {
    ...
    return InputResult.CommitText(selected)
}
return InputResult.Idle  // ← 這裡！selected 是 null 就什麼都不做
```

`candidateManager._candidates` 和 UI 顯示的 `keyboardView.candidates` 可能不同步。

### 修改 `input/router/InputRouter.kt`

找到 `selectCandidate()` 的英文分支（`else ->` 那段），改為：

```kotlin
else -> {
    // 英文模式：直接用 UI 顯示的候選字，不依賴 candidateManager
    val selected = candidateManager.candidates.getOrNull(index)
        ?: candidates.getOrNull(index)  // fallback 到 InputRouter.candidates

    if (selected != null) {
        activeEngine.reset()
        candidateManager.clear()
        onTextCommitted(selected)
        smartInput.onCandidateHit()
        return InputResult.CommitText(selected)
    }
    // 最後 fallback：用 candidates 屬性
    val fallback = candidates.getOrNull(index)
    if (fallback != null) {
        activeEngine.reset()
        candidateManager.clear()
        onTextCommitted(fallback)
        smartInput.onCandidateHit()
        return InputResult.CommitText(fallback)
    }
    return InputResult.Idle
}
```

同時在 `InputRouter` 找 `candidates` 屬性定義：

```kotlin
// 確認這個屬性存在（給上面的 fallback 用）
val candidates: List<String>
    get() = candidateManager.candidates
```

---

## 修復三：功能列後無法打字 / 輸入框打不開

### 原因一：panelContainer 覆蓋問題

`panelContainer` 是 `GONE` 但有時 `removeAllViews()` 後殘留 View 還在接收 touch。

### 修改 `service/YukiBoardIMEService.kt`

**在 `closePanel()` 末尾加強確保鍵盤可用：**

```kotlin
private fun closePanel() {
    activePanel = null
    toolbarView.activePanel = null
    panelContainer.removeAllViews()
    panelContainer.visibility = View.GONE
    hidePanelControlBar()
    currentInputConnection?.finishComposingText()
    if (isPanelBehindKeyboard) {
        isPanelBehindKeyboard = false
        panelContainer.bringToFront()
    }
    // ← 新增：確保 keyboardView 重新獲得 focus 和 touch
    keyboardView.isEnabled = true
    keyboardView.requestFocus()
    keyboardView.invalidate()
    // ← 新增：確保 candidateTouchActive 不殘留
    keyboardView.resetTouchState()
}
```

**在 `KeyboardView.kt` 加 `resetTouchState()`：**

```kotlin
/**
 * 強制重置所有觸控狀態。
 * 在面板關閉後呼叫，確保鍵盤可以正常接收輸入。
 */
fun resetTouchState() {
    candidateTouchActive = false
    candidateIsDragging = false
    pressedKey = null
    isSwipingActive = false
    swipePoints.clear()
    swipeKeySequence.clear()
    isSwipeGesture = false
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    invalidate()
}
```

### 原因二：某些 App 輸入框不觸發鍵盤

有些 App（特別是 Google 系）的輸入框用客製化 View，
不會自動觸發 `onStartInputView`。

**在 `onWindowShown()` 加重新初始化：**

```kotlin
override fun onWindowShown() {
    super.onWindowShown()
    // 確保鍵盤狀態重置（某些 App 不觸發 onStartInputView）
    if (currentInputConnection != null) {
        keyboardView.resetTouchState()
        keyboardView.invalidate()
    }
}
```

### 原因三：功能列操作後 `currentInputConnection` 變 null

某些功能（GIF、翻譯、剪貼簿）在 commit 後可能讓 connection 失效。

**在 `YukiBoardIMEService` 加安全的 commit helper：**

```kotlin
/**
 * 安全提交文字，自動處理 connection 為 null 的情況。
 */
private fun safeCommit(text: String) {
    val ic = currentInputConnection
    if (ic != null) {
        ic.commitText(text, 1)
    } else {
        // 嘗試重新取得 connection
        val freshIc = currentInputConnection
        freshIc?.commitText(text, 1)
            ?: android.util.Log.w("YukiBoard", "InputConnection is null, cannot commit: $text")
    }
}
```

並把所有面板的 `currentInputConnection?.commitText(...)` 改成 `safeCommit(...)`：

```kotlin
// EmojiPanel, KaomojiPanel, ClipboardPanel, GifPanel, StickerPanel 的 commit 都改用 safeCommit
p.onEmojiSelected = { emoji -> safeCommit(emoji) }
p.onKaomojiSelected = { kaomoji -> safeCommit(kaomoji) }
p.onClipSelected = { text -> safeCommit(text) }
// 以此類推
```

---

## 修復四：注音候選字動態排列（最接近注音的排前面）

### 需求說明

```
使用者打：ㄅㄞ（不帶聲調）
期望排列：
  第1位：白（ㄅㄞˊ，最常用）
  第2位：百（ㄅㄞˇ）
  第3位：拜（ㄅㄞˋ）
  第4位：敗（ㄅㄞˋ）
  ...

使用者打：ㄅㄞˊ（帶聲調2）
期望排列：
  第1位：白（精確匹配 ㄅㄞˊ）
  第2位：擺（ㄅㄞˇ，次近）
  ...
```

### 修改 `input/engine/ChewingInputEngine.kt`

找到 `lookupCandidates()` 的 `seen.toList()` 最後那行，替換排序邏輯：

```kotlin
// 原本：
seen.addAll(singleChars)
seen.addAll(phrases)
return seen.toList()

// 改為（加入頻率 + 聲調相似度排序）：
// 使用一個評分函數決定排列順序
data class ScoredChar(val char: String, val score: Int)

fun scoreChar(ch: String, inputBopomofo: String): Int {
    var score = 0
    // 1. 精確聲調匹配 +100（打 ㄅㄞˊ 就把 ㄅㄞˊ 的字排最前）
    val charReadings = reverseIndex[ch] ?: emptySet()
    if (charReadings.contains(inputBopomofo)) score += 100
    // 2. 無聲調匹配（打 ㄅㄞ 不帶聲調）+50
    val stripped = inputBopomofo.trimEnd { it in toneMarks }
    if (charReadings.any { it.trimEnd { c -> c in toneMarks } == stripped }) score += 50
    // 3. 常用字優先（字典裡排越前面頻率越高）+10
    val exactList = dict[inputBopomofo] ?: emptyList()
    val posInExact = exactList.indexOf(ch)
    if (posInExact >= 0) score += (20 - posInExact).coerceAtLeast(0)
    // 4. 上次輸入（個人詞庫）+200（最高優先）
    // 這裡先不實作，由 SmartInputManager 的 AdaptiveDictionary 處理
    return score
}

val inputBopo = bopomofo  // 使用者輸入的注音

val scored = (singleChars + phrases).map { ch ->
    ScoredChar(ch, scoreChar(ch, inputBopo))
}

return scored
    .sortedByDescending { it.score }
    .map { it.char }
```

**注意：** `reverseIndex` 已在 `ChewingInputEngine` 中定義為 lazy 屬性，可以直接使用。

### 更簡單的版本（如果上面太複雜）

如果 reverseIndex 查詢太慢，用更輕量的方式：

```kotlin
// 在 lookupCandidates() 末尾，把精確聲調的結果確保排第一
val hasTone = inputBopomofo.last() in toneMarks

val result = mutableListOf<String>()

if (hasTone) {
    // 有聲調：精確匹配的字排最前面
    val exact = dict[inputBopomofo] ?: emptyList()
    result.addAll(exact)
    // 其他注音的字補在後面
    (singleChars + phrases).forEach { if (it !in result) result.add(it) }
} else {
    // 無聲調：按照一聲→二聲→三聲→四聲→輕聲的順序
    val toneOrder = listOf('ˉ', 'ˊ', 'ˇ', 'ˋ', '˙')
    val stripped = inputBopomofo
    for (tone in toneOrder) {
        val withTone = stripped + tone
        val chars = dict[withTone] ?: continue
        chars.forEach { if (it !in result) result.add(it) }
    }
    // 最後補入詞組
    phrases.forEach { if (it !in result) result.add(it) }
}

return result
```

---

## 修復五：IME 打不開的額外防護

### 修改 `service/YukiBoardIMEService.kt`

在 `onStartInput()` 加重置邏輯：

```kotlin
override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
    super.onStartInput(attribute, restarting)
    // 確保面板關閉狀態
    if (activePanel != null && !restarting) {
        closePanel()
    }
}
```

在 `onFinishInput()` 加重置：

```kotlin
override fun onFinishInput() {
    super.onFinishInput()
    // 重置所有輸入狀態，避免下次打開鍵盤有殘留
    if (::keyboardView.isInitialized) {
        keyboardView.resetTouchState()
    }
}
```

---

## 執行順序

```
1. KeyboardView.resetTouchState() 方法（其他修復依賴它）
2. 修復二：英文候選字 → InputRouter.selectCandidate 英文分支
3. 修復三：closePanel() + onWindowShown() + safeCommit()
4. 修復一：AI 候選字視覺（移除 ✨ 前綴，改底色）
5. 修復四：注音動態排列（用簡單版先上，複雜版再優化）
6. 修復五：onStartInput / onFinishInput 重置
每步 Build 測試
```

---

## 快速驗證

```
修復一：
□ AI 候選字（黃色）有明顯底色但沒有 ✨ 前綴
□ 點黃色候選字可以選取

修復二：
□ 打 "hello" → 候選字出現 → 點第一個 → 輸入框出現 "hello"
□ 打 "abc" → 點任意候選字 → 有反應

修復三：
□ 點 GIF 面板 → 選完 GIF → 關閉面板 → 可以繼續打字
□ 點貼圖面板 → 選完貼圖 → 可以打字
□ 在 Gmail 輸入框 → 鍵盤正常彈出

修復四：
□ 打 ㄅㄞ → 第一個候選字是「白」或「百」
□ 打 ㄅㄞˊ → 第一個候選字是「白」
□ 打 ㄉㄜ → 「的」排第一
```
