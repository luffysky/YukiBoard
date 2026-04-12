# YukiBoard v5 全專案 Bug 修復任務包

---

## Bug 1：最上排按不到（🔴 嚴重）

### 根本原因

```
候選列高度 = 40dp（candidateBarHeight）
第一排 touchRect.top = candidateBarHeight = 40dp

使用者手指按第一排時，手指觸碰點通常在按鍵中間，
但手指是有面積的，按鍵頂部的觸碰 y 座標 < 40dp
→ 被候選列攔截（conditions: candidates.isNotEmpty()）
→ 變成「滑動候選列」而不是「按按鍵」
```

### 修改 `ui/keyboard/KeyboardView.kt`

找到 `drawKeyRows()` 裡建立 `touchRect` 的地方（約 line 1278）：

```kotlin
// 原本：
rows.forEachIndexed { rowIndex, row ->
    val top = candidateBarHeight + rowIndex * rh
    ...
    val touchRect = RectF(x, top, x + keyWidth, top + rh)

// 改為：第一排的 touchRect 往上延伸到候選列頂部
rows.forEachIndexed { rowIndex, row ->
    val top = candidateBarHeight + rowIndex * rh
    ...
    // 第一排往上延伸，確保候選列邊界附近的觸碰也能命中按鍵
    val touchTop = if (rowIndex == 0) 0f else top
    val touchRect = RectF(x, touchTop, x + keyWidth, top + rh)
```

這樣第一排的 `touchRect` 從 y=0 開始，完整蓋住候選列高度，
手指在候選列底部按下時也能命中第一排的按鍵。

### 同時修改候選列攔截條件

因為第一排現在從 y=0 開始，需要讓候選列的攔截更精準：

```kotlin
// 原本：
if (y < candidateBarHeight && candidates.isNotEmpty()) {
    candidateTouchActive = true
    return true
}

// 改為：候選列攔截只在候選列的中間區域生效（不含最底部 8dp）
val candidateHitZone = candidateBarHeight - 8f * density
if (y < candidateHitZone && candidates.isNotEmpty()) {
    candidateTouchActive = true
    return true
}
// candidateBarHeight - 8dp 到 candidateBarHeight 的範圍：兩個都能響應
// 候選列底部 8dp 讓給第一排按鍵的上緣
```

---

## Bug 2：詞庫面板待修復（🟡 中等）

### 現況

`PhrasePanelView` 在 `onTouchEvent` 裡：
```kotlin
if (!isDragging && pressedPhrase != null) {
    onInsertPhrase?.invoke(pressedPhrase!!)  // ← !! 在 null check 後，邏輯上安全
}
```

`pressedPhrase != null` 已經確認不為 null，但 `!!` 仍然是冗餘的，
且在多執行緒情況下（雖然這裡都在 Main thread）理論上有 TOCTOU 風險。

### 修改 `ui/panel/PhrasePanelView.kt`

```kotlin
// 原本：
if (!isDragging && pressedPhrase != null) {
    onInsertPhrase?.invoke(pressedPhrase!!)

// 改為：
pressedPhrase?.takeIf { !isDragging }?.let { phrase ->
    onInsertPhrase?.invoke(phrase)
}
```

### 詞庫面板另一個問題：Tab 切換後 scrollY 不重置

找到 Tab 點擊後的處理，確認有清掉 `scrollY`：

```kotlin
tabRects.firstOrNull { it.rect.contains(event.x, event.y) }?.let {
    selectedCategoryIndex = it.index
    scrollY = 0f      // ← 確認這行存在，切 Tab 後捲動位置重置
    invalidate()
    return true
}
```

---

## Bug 3：AUTO 模式切換後候選字消失（🟡 中等）

### 問題

`autoDetectAndSwitch()` 在切換引擎時呼叫了 `candidateManager.clear()`：

```kotlin
if (engScore - bopoScore >= threshold && activeEngine !is EnglishInputEngine) {
    activeEngine.reset()
    candidateManager.clear()   // ← 清空候選字
    activeEngine = englishEngine
    ...
}
```

切換時機通常是使用者正在打字，清掉候選字會讓畫面閃一下。

### 修改 `input/router/InputRouter.kt`

在 AUTO 模式切換引擎時，**先建立新候選字，再切換**，不要先清空：

```kotlin
if (engScore - bopoScore >= threshold && activeEngine !is EnglishInputEngine) {
    activeEngine.reset()
    // 不要立刻 clear，先切換引擎並取得新候選
    activeEngine = englishEngine
    for (c in autoQwertyBuffer.toString().dropLast(1)) {
        englishEngine.processChar(c.toString())
    }
    // 切換後更新候選（有了新候選才清掉舊的）
    updateCandidates()
    // updateCandidates() 內部會 candidateManager.update(...)，不需要手動 clear
} else if (bopoScore - engScore >= threshold && activeEngine !is ChewingInputEngine) {
    activeEngine.reset()
    activeEngine = bopomofoEngine
    for (sym in autoBopomofoBuffer.dropLast(1)) {
        bopomofoEngine.processChar(sym)
    }
    updateCandidates()
}
```

---

## Bug 4：BluetoothHidKeyboard postDelayed 洩漏（🟡 中等）

### 問題

`BluetoothHidKeyboard.kt` 有幾個 `handler.postDelayed(...)` 是用匿名 lambda，
無法用 `removeCallbacks` 取消，會在 destroy 後繼續執行。

### 修改 `remote/BluetoothHidKeyboard.kt`

找到以下三個 postDelayed（約 line 235, 254, 265），
把 lambda 改成有名字的 Runnable，並在 destroy/disconnect 時取消：

```kotlin
// 在 class 頂部加：
private val reconnectRunnable = Runnable { attemptReconnect() }
private val connectTimeoutRunnable = Runnable { handleConnectTimeout() }
private val retryRunnable = Runnable { retryLastKey() }

// 原本的三個 handler.postDelayed { ... } 改成：
handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
handler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS)
handler.postDelayed(retryRunnable, RETRY_DELAY_MS)

// 在 destroy() 函式加：
fun destroy() {
    handler.removeCallbacks(reconnectRunnable)
    handler.removeCallbacks(connectTimeoutRunnable)
    handler.removeCallbacks(retryRunnable)
    handler.removeCallbacksAndMessages(null)  // 原本就有這行，保留
    // ... 其餘 cleanup
}
```

---

## Bug 5：ClipboardPanelView encryptedData!! 崩潰（🔴 嚴重）

### 問題

```kotlin
// ClipboardPanelView.kt 約 line 226
val plain = encryptHelper.decrypt(item.encryptedData!!)
```

`encryptedData` 是 nullable，`!!` 會在 null 時拋 `NullPointerException`，
導致剪貼簿面板崩潰。

### 修改 `ui/panel/ClipboardPanelView.kt`

```kotlin
// 原本：
val plain = encryptHelper.decrypt(item.encryptedData!!)

// 改為：
val plain = item.encryptedData?.let { encryptHelper.decrypt(it) }
if (plain == null) {
    // 解密失敗或 encryptedData 為 null，顯示佔位符
    showToast("無法解密此項目")
    return
}
```

---

## Bug 6：InputRouter lastCommittedWord!! 崩潰（🔴 嚴重）

### 問題

```kotlin
// InputRouter.kt 約 line 544, 601, 675
val continuations = generateContinuationCandidates(lastCommittedWord!!)
val predictions = (activeEngine as ChewingInputEngine).getNextCharPredictions(lastCommittedWord!!)
```

`lastCommittedWord` 在某些路徑下可能是 null。

### 修改 `input/router/InputRouter.kt`

三處都改成安全呼叫：

```kotlin
// 原本：
val continuations = generateContinuationCandidates(lastCommittedWord!!)

// 改為：
val word = lastCommittedWord ?: return@let   // 或 return emptyList()
val continuations = generateContinuationCandidates(word)

// 同樣處理 line 601 和 675
val predictions = lastCommittedWord?.let {
    (activeEngine as? ChewingInputEngine)?.getNextCharPredictions(it)
} ?: emptyList()
```

---

## Bug 7：WeatherFloatingPanel coroutine 沒有 try-catch（🟡 中等）

### 問題

```kotlin
// WeatherFloatingPanel.kt 約 line 104
scope.launch {
    // 網路請求，沒有 try-catch
    val data = weatherManager.fetch()
    ...
}
```

網路請求失敗（無網路、API 錯誤）會拋出 Exception，
`scope.launch` 裡沒有 try-catch 會讓 coroutine 崩潰並輸出 uncaught exception。

### 修改 `weather/WeatherFloatingPanel.kt`

```kotlin
scope.launch {
    try {
        val data = weatherManager.fetch()
        withContext(Dispatchers.Main) {
            updateUI(data)
        }
    } catch (e: CancellationException) {
        throw e  // 讓 cancellation 正常傳播
    } catch (e: Exception) {
        android.util.Log.w("WeatherPanel", "Fetch failed: ${e.message}")
        withContext(Dispatchers.Main) {
            showErrorState()
        }
    }
}
```

---

## Bug 8：SecretMessageManager 密碼加密（🔵 小問題）

### 確認現況

`SecretMessageManager` 的 `encryptWithPassword` 和 `decryptWithPassword` 
用的是 AES 加密。確認密碼 key derivation 有用 PBKDF2 而不是直接用密碼當 key：

```kotlin
// 確認有類似這樣的 key derivation：
val keySpec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
val key = factory.generateSecret(keySpec)
```

如果沒有，直接用密碼字串當 AES key 是安全漏洞，需要加 PBKDF2。

---

## 執行順序

```
1. Bug 5 + 6（崩潰風險最高，優先修）
   ClipboardPanelView.kt → encryptedData?.let
   InputRouter.kt → lastCommittedWord?.let（3處）

2. Bug 1（使用者體驗最差）
   KeyboardView.kt → 第一排 touchTop = 0f
   KeyboardView.kt → 候選列攔截改 candidateBarHeight - 8dp

3. Bug 3（AUTO 模式候選字閃爍）
   InputRouter.kt → autoDetectAndSwitch 先更新候選再切換

4. Bug 4（記憶體洩漏）
   BluetoothHidKeyboard.kt → 具名 Runnable + destroy 時 removeCallbacks

5. Bug 2（詞庫面板小修）
   PhrasePanelView.kt → pressedPhrase?.takeIf

6. Bug 7（天氣面板）
   WeatherFloatingPanel.kt → 加 try-catch

7. Bug 8（確認 SecretMessageManager 安全性）

每步 Build 測試
```

---

## 快速驗證

```
Bug 1：
□ 注音模式打幾個字（有候選字）→ 按最上排的注音鍵 → 有反應
□ 按 ˇ ˋ ˊ ˙ 等聲調鍵 → 有反應

Bug 2：
□ 開詞庫面板 → 點任意短語 → 輸入框出現該短語
□ 切分類 Tab → 上下捲動位置重置為頂部

Bug 3：
□ AUTO 模式打中文後打英文 → 候選字不閃爍/不消失，平滑切換

Bug 5：
□ 剪貼簿面板有加密項目 → 點開查看 → 不崩潰

Bug 6：
□ 連續選字後繼續打字 → 不崩潰
```
