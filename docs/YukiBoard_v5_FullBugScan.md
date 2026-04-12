# YukiBoard v5 全專案完整 Bug 掃描報告

> 掃描範圍：207 個 .kt 檔案
> 分類：🔴 嚴重（崩潰）/ 🟡 中等（異常行為）/ 🔵 小問題（潛在風險）

---

## 🔴 嚴重 Bug（優先修）

### S-1：InputRouter.lastCommittedWord!! 三處崩潰
**檔案：** `input/router/InputRouter.kt` line 544, 601, 675

```kotlin
// 危險：
val continuations = generateContinuationCandidates(lastCommittedWord!!)
val predictions = (activeEngine as ChewingInputEngine).getNextCharPredictions(lastCommittedWord!!)
```
`lastCommittedWord` 在某些路徑為 null → 直接崩潰。

**修法：**
```kotlin
val word = lastCommittedWord ?: return@let
val continuations = generateContinuationCandidates(word)

val predictions = lastCommittedWord?.let {
    (activeEngine as? ChewingInputEngine)?.getNextCharPredictions(it)
} ?: emptyList()
```

---

### S-2：ClipboardPanelView.encryptedData!! 崩潰
**檔案：** `ui/panel/ClipboardPanelView.kt` line 226

```kotlin
val plain = encryptHelper.decrypt(item.encryptedData!!)  // encryptedData 是 nullable
```

**修法：**
```kotlin
val data = item.encryptedData ?: return
val plain = encryptHelper.decrypt(data) ?: return
```

---

### S-3：ClipboardPanelView.longPressRunnable!! 崩潰
**檔案：** `ui/panel/ClipboardPanelView.kt` line 901

```kotlin
handler.postDelayed(longPressRunnable!!, longPressTimeout)
```
`longPressRunnable` 可能是 null。

**修法：**
```kotlin
longPressRunnable?.let { handler.postDelayed(it, longPressTimeout) }
```

---

### S-4：ClawPanelView.client!! 雙重崩潰
**檔案：** `ui/panel/ClawPanelView.kt` line 200, 220

```kotlin
client!!.streamSkill(prompt, systemPrompt)
client!!.executeSkill(skillId, input)
```

**修法：**
```kotlin
val c = client ?: run { showError("AI 未連線"); return@launch }
c.streamSkill(prompt, systemPrompt)
```

---

### S-5：IMEService floatingPanelManager 強制解包
**檔案：** `service/YukiBoardIMEService.kt` line 249-250

```kotlin
floatingPanelManager?.registerPanel(weatherFloatingPanel!!)
floatingPanelManager?.registerPanel(clawResultFloatingPanel!!)
```
`floatingPanelManager?` 用了 safe call，但 `!!` 讓 `weatherFloatingPanel` / `clawResultFloatingPanel` 仍可崩潰。

**修法：**
```kotlin
val weather = weatherFloatingPanel ?: return
val claw = clawResultFloatingPanel ?: return
floatingPanelManager?.registerPanel(weather)
floatingPanelManager?.registerPanel(claw)
```

---

## 🟡 中等 Bug（影響體驗）

### M-1：最上排按不到
**檔案：** `ui/keyboard/KeyboardView.kt` line 1278

**原因：** 第一排 `touchRect.top = candidateBarHeight (40dp)`，手指按第一排上半部時 y < 40dp，被候選列攔截。

**修法：**
```kotlin
// 第一排往上延伸
val touchTop = if (rowIndex == 0) 0f else top
val touchRect = RectF(x, touchTop, x + keyWidth, top + rh)
```
同時調整候選列攔截範圍：
```kotlin
// 候選列攔截縮小 8dp，把邊界讓給第一排
val candidateHitZone = candidateBarHeight - 8f * density
if (y < candidateHitZone && candidates.isNotEmpty()) {
    candidateTouchActive = true
    return true
}
```

---

### M-2：AUTO 模式切換時候選字閃爍消失
**檔案：** `input/router/InputRouter.kt` line ~455

**原因：** `candidateManager.clear()` 在先，新候選在後，中間有空白。

**修法：**
```kotlin
// 切換到英文引擎時，先 feed 輸入再更新，不手動 clear
activeEngine.reset()
activeEngine = englishEngine
for (c in autoQwertyBuffer.toString().dropLast(1)) {
    englishEngine.processChar(c.toString())
}
updateCandidates()  // 內部會自然更新 candidateManager，不需要手動 clear
```

---

### M-3：ZCoinManager.topUp / syncBalance 沒有 @Synchronized
**檔案：** `currency/ZCoinManager.kt` line 61, 73

`spend()` 有 `@Synchronized`，但 `topUp()` 和 `syncBalance()` 沒有，多執行緒同時操作餘額會造成 race condition。

**修法：**
```kotlin
@Synchronized
fun topUp(amount: Int, orderId: String) { ... }

@Synchronized
fun syncBalance(serverBalance: Int) { ... }
```

---

### M-4：KeyboardView onDetachedFromWindow 只清了 swipeDwellHandler
**檔案：** `ui/keyboard/KeyboardView.kt` line 2213

```kotlin
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
    // ← 沒有清 handler（longPressRunnable, deleteRepeatRunnable）
}
```

**修法：**
```kotlin
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    handler.removeCallbacksAndMessages(null)
}
```

---

### M-5：IMEService onDestroy 缺少清理項目
**檔案：** `service/YukiBoardIMEService.kt` line 927

`onDestroy` 沒有清理：
- `floatingPanelManager`
- `weatherFloatingPanel`
- `clawResultFloatingPanel`
- `characterView`（VRM 的 Filament 資源）
- `glSurfaceView`

**修法：**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    // ... 原有清理 ...
    floatingPanelManager?.destroy()
    weatherFloatingPanel?.destroy()
    clawResultFloatingPanel?.destroy()
    characterView?.let { removeView(it) }
    glSurfaceView?.onDestroy()
    // 清空 panel 參照
    emojiPanel = null
    kaomojiPanel = null
    clipboardPanel = null
    gifPanel = null
    themePanel = null
    serviceScope.cancel()
}
```

---

### M-6：VoiceRecognitionOverlay postDelayed({ dismiss() }, 3000) 無法取消
**檔案：** `ui/speech/VoiceRecognitionOverlay.kt` line 226

```kotlin
postDelayed({ dismiss() }, 3000)  // 匿名 lambda，無法 removeCallbacks
```
如果 3 秒內 View 被移除，dismiss 還是會被呼叫。

**修法：**
```kotlin
private val autoDismissRunnable = Runnable { dismiss() }

// 使用時：
removeCallbacks(autoDismissRunnable)
postDelayed(autoDismissRunnable, 3000)

// onDetachedFromWindow：
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    removeCallbacks(autoDismissRunnable)
    removeCallbacks(countdownRunnable)
}
```

---

### M-7：FasterWhisperSpeechEngine / WhisperSpeechEngine launch 外層沒有 try-catch
**檔案：** `speech/FasterWhisperSpeechEngine.kt` line 91
**檔案：** `speech/WhisperSpeechEngine.kt` line 88

launch 內層有 try-catch，但外層（啟動錄音之前）如果 `AudioRecord` 初始化失敗，exception 會逃逸。

**修法：**
```kotlin
recordingJob = scope.launch {
    try {
        // 所有邏輯包在這裡
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(tag, "Recording error", e)
        withContext(Dispatchers.Main) { onError(ERROR_AUDIO_INIT) }
    }
}
```

---

### M-8：AndroidTtsEngine.selectedVoiceName!! 崩潰
**檔案：** `speech/AndroidTtsEngine.kt` line 79, 171

```kotlin
applyVoiceByName(selectedVoiceName!!)
```

**修法：**
```kotlin
selectedVoiceName?.let { applyVoiceByName(it) }
```

---

### M-9：CalculatorPanelView.operand1!! 崩潰
**檔案：** `ui/panel/CalculatorPanelView.kt` line 115

```kotlin
val expr = formatNumber(operand1!!) + " $operator"
```
`operand1` 在某些狀態可能為 null（例如剛初始化、按了運算子後又按了 operator）。

**修法：**
```kotlin
val expr = operand1?.let { formatNumber(it) + " $operator" } ?: "0 $operator"
```

---

### M-10：CrosswordGame.currentPuzzle!! 崩潰
**檔案：** `game/CrosswordGame.kt` line 100

```kotlin
val word = currentPuzzle!!.word
```

**修法：**
```kotlin
val word = currentPuzzle?.word ?: return
```

---

## 🔵 小問題（潛在風險）

### B-1：IMEService gkv.postDelayed({ showGameMenu() }, 3000) 匿名 lambda
**檔案：** `service/YukiBoardIMEService.kt` line 2264, 2270

匿名 lambda 無法被 removeCallbacks 取消，遊戲結束後 View 被移除仍會執行。
改成具名 Runnable 即可。

---

### B-2：CharacterView postDelayed wanderToNext
**檔案：** `ui/keyboard/CharacterView.kt` line 226

```kotlin
postDelayed({ wanderToNext() }, 1500L + Random.nextLong(2500L))
```
雖然有 `wanderEnabled` 和 `onDetachedFromWindow` 保護，但 lambda 本身無法被精確取消。
改成具名 Runnable 更穩。

---

### B-3：VrmCharacterAnimation 沒有 scope cancel
**檔案：** `animation/VrmCharacterAnimation.kt`

如果有 coroutine scope，確認 `stop()` 時有 `scope.cancel()`。

---

### B-4：OAuthCallbackActivity scope 沒有 try-catch
**檔案：** `ui/auth/OAuthCallbackActivity.kt` line 44

```kotlin
scope.launch {
    // OAuth callback 處理，網路操作，沒有 try-catch
}
```
網路失敗時 exception 逃逸，Activity 可能無響應。

---

### B-5：SettingsActivity 匿名 CoroutineScope
**檔案：** `ui/settings/SettingsActivity.kt` line 1902

```kotlin
kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
    // 沒有任何生命週期綁定，Activity 銷毀後仍繼續執行
}
```
改成 `lifecycleScope.launch(Dispatchers.IO)` 讓 lifecycle 自動取消。

---

## 統計

| 等級 | 數量 | 主要風險 |
|------|------|---------|
| 🔴 嚴重 | 5 | 崩潰（NullPointerException）|
| 🟡 中等 | 10 | 體驗異常、記憶體洩漏、資料競爭 |
| 🔵 小問題 | 5 | 潛在不穩定 |

---

## 修復優先順序

```
第一批（上架前必修，避免崩潰）：
  S-1 InputRouter lastCommittedWord!! × 3
  S-2 ClipboardPanelView encryptedData!!
  S-3 ClipboardPanelView longPressRunnable!!
  S-4 ClawPanelView client!! × 2
  S-5 IMEService floatingPanel!!
  M-8 AndroidTtsEngine selectedVoiceName!!
  M-9 CalculatorPanelView operand1!!
  M-10 CrosswordGame currentPuzzle!!

第二批（體驗修復）：
  M-1 最上排按不到
  M-2 AUTO 模式候選字閃爍
  M-3 ZCoinManager 無 @Synchronized

第三批（記憶體穩定性）：
  M-4 KeyboardView onDetachedFromWindow
  M-5 IMEService onDestroy 補清理
  M-6 VoiceOverlay postDelayed
  M-7 WhisperEngine try-catch
  B-4 OAuthCallbackActivity try-catch
  B-5 SettingsActivity lifecycleScope
```
