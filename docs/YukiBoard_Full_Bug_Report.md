# YukiBoard 全面 Bug 掃描報告

---

## Bug 1：候選列 vs 第一排按鍵互相搶觸控【最嚴重】

### 根本原因

`ACTION_DOWN` 的判斷順序：

```kotlin
// 1. 候選列攔截：y < candidateBarHeight (44dp)
val candidateHitZone = candidateBarHeight - 4f * density
if (y < candidateHitZone && candidates.isNotEmpty()) {
    candidateTouchActive = true
    return true  // ← 吃掉事件
}

// 2. 第一排按鍵的 touchTop = 0f（從 y=0 開始）
val touchTop = when (rowIndex) {
    0 -> 0f  // ← 第一排從頂端延伸
    else -> top - rh * 0.3f
}
```

**問題：**
- 候選列區域：`y = 0 ~ 40dp`
- 第一排按鍵 touchRect：`y = 0 ~ (44+rowH)`
- 兩者重疊在 `y = 0 ~ 40dp`
- 有候選字時，候選列搶走所有 `y < 40dp` 的觸控 → 第一排按鍵上半部點不到
- 沒有候選字時，候選列不攔截 → 第一排按鍵正常

**展開面板時的額外問題：**
```kotlin
if (isCandidateExpanded && y > candidateBarHeight) {
    // 展開面板攔截 y > 44dp 的所有觸控
    return true  // ← 展開面板的第一列按鍵點不到
}
```
展開後 `y > 44dp` 全被展開面板吃掉，展開面板的第一列選字沒反應。

### 修法

**`KeyboardView.kt` — `onTouchEvent ACTION_DOWN`**

把候選列攔截改成只在候選列的「可視區域」生效，不延伸到第一排按鍵：

```kotlin
// 候選列只攔截確實在候選列視覺區域內的觸控
// 原本用 candidateBarHeight - 4dp，改為精確判斷
val candidateHitZone = candidateBarHeight  // 不留 -4dp 的模糊地帶
if (y < candidateHitZone && y >= 0 && candidates.isNotEmpty()) {
    // 只有真正在候選列區域才攔截
    candidateLastTouchX = x
    candidateDownX = x
    candidateIsDragging = false
    candidateTouchActive = true
    candidateLongPressX = x
    handler.postDelayed(candidateLongPressRunnable, CANDIDATE_LONG_PRESS_DELAY)
    return true
}
```

同時把第一排按鍵的 `touchTop` 改為從 `candidateBarHeight` 開始，不往上延伸：

```kotlin
val touchTop = when (rowIndex) {
    0 -> candidateBarHeight  // ← 從候選列底部開始，不跟候選列重疊
    else -> top - rh * 0.3f
}
```

這樣候選列有候選字時可以點，第一排按鍵也可以點，互不干擾。

---

## Bug 2：展開面板第一列選字沒反應

### 根本原因

展開面板開啟時，`ACTION_DOWN` 攔截 `y > candidateBarHeight` 的所有觸控：

```kotlin
if (isCandidateExpanded && y > candidateBarHeight) {
    expandTouchStartY = y
    // ...
    return true
}
```

但 `ACTION_UP` 的選字邏輯用 `expandDownY` 計算 row：

```kotlin
val startY = candidateBarHeight + contentPadding - expandScrollOffset
val row = ((selectY - startY) / cellHeight).toInt()
```

當 `expandScrollOffset = 0`，`contentPadding = 8dp`，第一列的 row=0 對應：
`startY = 44 + 8 = 52dp`，到 `52 + 42 = 94dp`

如果 `expandTouchStartY` 和 `expandDownY` 沒有正確記錄，選字就失效。

### 修法

確認 `expandDownX` 和 `expandDownY` 在 `ACTION_DOWN` 時被正確記錄（目前程式碼有記錄但需確認沒有被後續邏輯覆蓋）。

---

## Bug 3：學習功能部分失效

### 根本原因

`onTextCommitted` 裡：

```kotlin
// 注音模式：有記錄
if (hasCjk && activeEngine is ChewingInputEngine) {
    userDict.learn(trimmed)  // ✅
}
// 其他模式：
userDict.learn(word)  // ✅
smartInput.onWordCommitted(word, currentAppPackage)  // ✅
```

但 `recordUsage`（AdaptiveDictionary）只在以下情況呼叫：
1. `onHandwritingCommit`（手寫）
2. 英文模式下（`InputRouter` 的某條路徑）

**CJK 候選字選字後，`recordUsage` 沒有被呼叫。** AdaptiveDictionary 的中文學習需要手動呼叫。

另外，`AdaptiveDictionary` 的門檻：
- CJK：`CJK_PROMOTE_THRESHOLD = 2`（打 2 次就記住）
- 英文：`PROMOTE_THRESHOLD = 3`（打 3 次才記住）

如果真的打了多次還沒記住，可能是 `persist()` 沒有在正確時機呼叫，資料沒有寫入磁碟。

### 修法

**`InputRouter.onTextCommitted`** — CJK 提交後也呼叫 `recordUsage`：

```kotlin
// 在 if (hasCjk && activeEngine is ChewingInputEngine) 區塊加：
val dict = (englishEngine as? EnglishInputEngine)?.getDictionary()
if (dict != null) {
    smartInput.adaptiveDictionary.recordUsage(trimmed, dict)
}
```

確認 `persist()` 在 `onFinishInputView` 被呼叫：

```kotlin
// YukiBoardIMEService.onFinishInputView
inputRouter.smartInput.adaptiveDictionary.persist()  // 確認有這行
```

---

## Bug 4：手寫某些區域寫不上去

### 根本原因

`writingAreaRect` 是根據面板高度計算的：

```kotlin
val writingTop = controlH + candidateH + pad  // 38 + 36 + 6 = 80dp
val writingBottom = h - pad - modelH
writingAreaRect.set(pad, writingTop, w - pad, writingBottom)
```

`ACTION_DOWN` 只在 `writingAreaRect.contains(x, y)` 時才建立 `currentPath`。如果觸控點不在這個 rect 內，`currentPath = null`，`ACTION_MOVE` 不記錄。

可能的問題：
1. 面板高度 `WRAP_CONTENT`，某些情況下 `writingBottom` 計算不正確
2. 面板最底部（靠近鍵盤邊緣）的觸控被 `panelContainer` 外層 View 攔截

### 修法

在 `ACTION_DOWN` 加 log 確認 `writingAreaRect` 的實際範圍：

```kotlin
if (writingAreaRect.contains(x, y)) {
    // 現有邏輯
} else {
    // 暫時加 log：
    android.util.Log.d("HW", "miss: x=$x y=$y rect=$writingAreaRect")
}
```

---

## Bug 5：手寫沒有接到候選字排序 / 詞庫

### 根本原因

`onRecognized` 把辨識結果送到 `keyboardView.updateCandidates`：

```kotlin
p.onRecognized = { candidates ->
    keyboardView.updateCandidates(candidates, sourceMap)
}
```

但手寫辨識的結果是 ML Kit 的原始輸出，**沒有跟 UserDictionary 或 PersonalDictionary 合併排序**。

如果使用者有自訂詞庫，手寫後也應該優先顯示使用者常用詞。

### 修法

`onRecognized` 裡，把辨識結果跟使用者詞庫合併：

```kotlin
p.onRecognized = { rawCandidates ->
    if (rawCandidates.isNotEmpty()) {
        // 用 InputRouter 的字典做二次排序
        val boosted = rawCandidates.sortedByDescending { word ->
            inputRouter.smartInput.adaptiveDictionary.getFrequency(word)
        }
        val sourceMap = boosted.associateWith {
            CandidateSource.DICTIONARY
        }
        keyboardView.updateCandidates(boosted, sourceMap)
    }
}
```

---

## 執行優先順序

```
P0（最影響使用）：
  Bug 1：候選列 vs 第一排按鍵衝突
  → KeyboardView.kt：touchTop 改為從 candidateBarHeight 開始

P1（常用功能壞掉）：
  Bug 3：學習功能 CJK 沒有呼叫 recordUsage
  → InputRouter.onTextCommitted 加 recordUsage
  → 確認 persist() 在正確時機呼叫

  Bug 5：手寫候選字沒有詞庫
  → onRecognized 合併 AdaptiveDictionary 排序

P2（影響體驗）：
  Bug 4：手寫某些區域寫不上去
  → 加 log 確認 writingAreaRect，再針對性修

  Bug 2：展開面板第一列選字
  → 確認 expandDownY 記錄正確
```
