# YukiBoard 功能打架分析報告

> 不是 crash，是功能互相干擾、狀態衝突、資源搶奪
> 依嚴重程度：🔴 使用者會明顯感覺到 → 🟡 偶爾出問題 → 🔵 邊界情況

---

## 🔴 嚴重衝突

---

### F-1｜語音輸入中按任何鍵，TTS 和語音辨識同時搶麥克風

**情境：**
```
使用者觸發 TTS 朗讀候選字（onCandidateSpeakListener）
→ 同時按下語音輸入按鈕
→ TTS 佔著 STREAM_MUSIC
→ 語音辨識同時啟動麥克風
→ TTS 聲音混進語音辨識 → 辨識結果亂掉
```

**程式碼：**
```kotlin
// ttsSpeak() 沒有停止語音辨識
private fun ttsSpeak(text: String, ...) {
    ttsEngine?.speak(text, ...)   // ← 直接說
}

// startVoiceInput() 沒有停止 TTS
speechEngine?.startListening(...)  // ← 直接聽
```

**修法：**
```kotlin
private fun ttsSpeak(text: String, locale: String? = null, onDone: (() -> Unit)? = null) {
    // TTS 開始前，先停止語音輸入
    if (speechEngine?.isListening == true) stopVoiceInput()
    ttsEngine?.speak(text, locale, prefs.ttsSpeed, onDone)
}

private fun startVoiceInput() {
    // 語音輸入開始前，先停止 TTS
    ttsEngine?.stop()
    // ... 其餘不變
}
```

---

### F-2｜AI 補字結果貼到錯誤的上下文（stale context race）

**情境：**
```
使用者打「好」→ 觸發 AI 補字（600ms debounce）
→ 在 600ms 內繼續打「好棒」
→ AI 跑完，拿現在的 inputRouter.candidates（已是「好棒」的候選）
→ 把「好」的 AI 補字附加到「好棒」的候選列
→ 候選列出現不相關的補字
```

**程式碼：**
```kotlin
aiCandidateJob = serviceScope.launch {
    delay(AI_CANDIDATE_DEBOUNCE_MS)
    // 這裡用「現在」的上下文去問 AI
    val input = ClawInput(text = composing.ifEmpty { context.takeLast(30) })
    val output = client.executeSkill("ai_prediction", input)

    if (output.suggestions.isNotEmpty()) {
        // 但合併到「現在」的 candidates，不是「當時」的
        val currentCandidates = inputRouter.candidates.toMutableList()  // ← 已是新的
        currentCandidates.addAll(...)
        keyboardView.updateCandidates(currentCandidates, ...)
    }
}
```

**修法：** 在 launch 時記錄當時的 composing，結果回來後比對是否還相同：
```kotlin
aiCandidateJob = serviceScope.launch {
    val capturedComposing = inputRouter.composingText  // ← 記錄當時
    val capturedContext = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
    delay(AI_CANDIDATE_DEBOUNCE_MS)

    // 600ms 後確認上下文沒變
    if (inputRouter.composingText != capturedComposing) return@launch  // ← 已過時，放棄

    val output = client.executeSkill("ai_prediction", input)
    // ... 其餘不變
}
```

---

### F-3｜遊戲模式下 Shake-to-Undo 仍然有效

**情境：**
```
使用者在玩蛇形遊戲（SnakeGame）
→ 手機晃了一下（手指快速滑動常見）
→ ShakeDetector 觸發
→ 發送 Ctrl+Z 到當前 InputConnection
→ 遊戲目標 App（如 LINE）收到 Undo 指令
→ 使用者剛打的字被撤銷
```

**程式碼：**
```kotlin
shakeDetector.setOnShakeListener {
    if (prefs.shakeToUndoEnabled) {
        val ic = currentInputConnection ?: return@setOnShakeListener
        sendCtrlKey(ic, KeyEvent.KEYCODE_Z)  // ← 遊戲模式沒有攔截
    }
}
```

**修法：**
```kotlin
shakeDetector.setOnShakeListener {
    if (prefs.shakeToUndoEnabled && !isInGameMode) {  // ← 加判斷
        val ic = currentInputConnection ?: return@setOnShakeListener
        sendCtrlKey(ic, KeyEvent.KEYCODE_Z)
    }
}
```

---

### F-4｜AI Token 額度被多個 Skill 同時扣（race condition）

**情境：**
```
使用者快速觸發兩個 Skill（例如 AI 補字自動觸發 + 手動按 WriterSkill）
→ 兩個 coroutine 同時執行 executeSkill()
→ InviteCodeManager.consumeTokens() 和 TokenBudgetManager.recordUsage()
  都是讀-改-寫，非 atomic
→ 兩個 call 都通過額度檢查
→ 實際扣了兩次但只算一次
→ 或 daily_token_used 計數不正確
```

**程式碼：**
```kotlin
// InviteCodeManager.consumeTokens() 是非 atomic 讀-改-寫
fun consumeTokens(count: Int) {
    val current = prefs.getInt("daily_token_used", 0)  // 讀
    prefs.edit().putInt("daily_token_used", current + count).apply()  // 寫
}
// 兩個 coroutine 同時讀，都讀到舊值，都寫入
```

**修法：**
```kotlin
// 方案 A：用 synchronized
@Synchronized
fun consumeTokens(count: Int) { ... }

// 方案 B：Kotlin Mutex（更適合 coroutine 環境）
private val tokenMutex = Mutex()

suspend fun consumeTokensAsync(count: Int) {
    tokenMutex.withLock {
        val current = prefs.getInt("daily_token_used", 0)
        prefs.edit().putInt("daily_token_used", current + count).apply()
    }
}
```

---

## 🟡 中度衝突

---

### F-5｜注音 composing 進行中切換翻譯面板輸入模式

**情境：**
```
注音模式下打「ㄅㄞ」（還沒選字）
→ 使用者點翻譯面板的「輸入」按鈕
→ translatePanel.isInInputMode = true
→ 下一個按鍵被翻譯面板攔截
→ 但 inputRouter.composingText 還是「ㄅㄞ」
→ composing 顯示在候選列，但輸入去了翻譯框
→ 兩邊狀態不同步
```

**確認：** `showPanel()` 只呼叫了 `translatePanel?.exitInputMode()`，但沒有 `inputRouter.reset()`。

**修法：** 在 `showPanel()` 或面板進入輸入模式時，先清掉 composing：
```kotlin
private fun showPanel(panel: PanelType) {
    // 切換面板時強制提交或清除 composing
    val ic = currentInputConnection
    val composing = inputRouter.composingText
    if (composing.isNotEmpty()) {
        ic?.commitText(composing, 1)
        inputRouter.reset()
        keyboardView.updateComposingText("")
        keyboardView.updateCandidates(emptyList())
    }
    translatePanel?.exitInputMode()
    gifPanel?.exitInputMode()
    panelContainer.removeAllViews()
    // ...
}
```

---

### F-6｜滑動輸入 + 注音 composing 未清除就選字

**情境：**
```
注音模式打「ㄅ」（composing 非空）
→ 手指滑動做滑動輸入
→ swipeInputPending = true，顯示滑動候選字
→ 使用者點了滑動候選字
→ commitText 寫入，swipeInputPending = false
→ 但 inputRouter.composingText 還是「ㄅ」
→ 候選列清掉了，但 composing 還顯示在輸入框
```

**程式碼：**
```kotlin
// onCandidateSelectListener 的滑動路徑
if (swipeInputPending && index in swipeInputCandidates.indices) {
    val text = swipeInputCandidates[index]
    ic?.commitText(text, 1)
    swipeInputPending = false
    swipeInputCandidates = emptyList()
    keyboardView.updateComposingText("")  // 只清畫面
    keyboardView.updateCandidates(emptyList())
    // ← 沒有 inputRouter.reset()
    return@setOnCandidateSelectListener
}
```

**修法：** 加 `inputRouter.reset()` 和 `ic?.finishComposingText()`：
```kotlin
if (swipeInputPending && index in swipeInputCandidates.indices) {
    val text = swipeInputCandidates[index]
    val ic = currentInputConnection
    ic?.finishComposingText()        // ← 清掉 inputConnection 的 composing
    inputRouter.reset()              // ← 清掉 inputRouter 狀態
    ic?.commitText(text, 1)
    swipeInputPending = false
    swipeInputCandidates = emptyList()
    keyboardView.updateComposingText("")
    keyboardView.updateCandidates(emptyList())
    return@setOnCandidateSelectListener
}
```

---

### F-7｜GIF 搜尋模式下注音輸入 DELETE 行為不一致

**情境：**
```
GIF 面板進入輸入模式，使用者用注音打「ㄅ」
→ 按 DELETE
→ handleKeyPress 裡 GIF 模式判斷：
  if (isCjk && inputRouter.composingText.isNotEmpty()) {
      inputRouter.processDelete()   // 清注音
  } else {
      gp.onDeleteInput()           // 清搜尋框
  }
→ 第一次按清注音（正確）
→ 第二次按（composing 空了）清搜尋框（正確）
```

這個邏輯基本正確，但有一個邊界：

**問題：** 注音 composing 非空時候選字列顯示注音候選，但選字後詞組進了搜尋框，不是進 InputConnection。這個路徑在 `GIF_PANEL + 選字` 的 `onCandidateSelectListener` 裡有處理，但如果使用者直接按空白鍵確認注音，`handleKeyPress → SPACE → gp.onComposedTextInput(result.text)` 路徑是否每次都正確清除 composing 需確認。

---

### F-8｜陀螺儀游標模式 + 注音輸入 composing 中同時移動游標

**情境：**
```
注音模式打「ㄅㄞ」（composing 非空）
→ 長按空白鍵啟動陀螺儀游標
→ 陀螺儀發送 DPAD_LEFT
→ 游標往左移動
→ composing 文字還在輸入框
→ 游標移動可能把游標定位到 composing 文字中間
→ 之後的輸入行為不可預測
```

**原因：** `gyroController.activate()` 和注音 composing 沒有互斥邏輯。

**修法：**
```kotlin
gyroController.setOnCursorMoveListener { direction ->
    // 游標模式下如果有 composing，先提交
    if (inputRouter.composingText.isNotEmpty()) {
        val ic = currentInputConnection ?: return@setOnCursorMoveListener
        val result = inputRouter.getActiveEngine().forceCommit()
        if (result is InputResult.CommitText) ic.commitText(result.text, 1)
        inputRouter.reset()
        keyboardView.updateComposingText("")
        keyboardView.updateCandidates(emptyList())
    }
    // 移動游標
    val keyCode = when (direction) { ... }
    sendKey(ic, keyCode)
}
```

---

### F-9｜Claw Panel Skill + AI 補字 job 同時呼叫 ClawClient

**情境：**
```
使用者開啟 Claw Panel，按下 WriterSkill
→ 同時 AI 補字 debounce 時間到
→ 兩個 coroutine 同時呼叫 executeSkill()
→ 兩次都通過 isAvailable() 和 budget 檢查
→ 兩次都扣 token（F-4 已提）
→ 兩個 updateCandidates 互蓋
```

Claw Panel 的結果送到 `clawResultFloatingPanel`，AI 補字送到 `keyboardView.updateCandidates`，路徑不同，UI 不互蓋。但 token 扣款衝突（F-4）。

**不需要額外修，F-4 修完即可。**

---

### F-10｜兩個 CharacterView 的動畫互搶

**情境：**
```
ui/keyboard/CharacterView（完整版）正在跑「遊走動畫」
→ 某段程式碼 import 了 ui/character/CharacterView（簡化版）
→ 兩個 View 都被 add 到同一個 container
→ 動畫衝突，或只有一個正常顯示
```

根本原因：M-12 的同名問題，import 錯誤時編譯不報錯。

**修法：** 刪除或重命名 `ui/character/CharacterView`（M-12 修完即解決）。

---

## 🔵 邊界衝突（低頻）

---

### F-11｜Theme 切換時 Particle View 的 clear() 可能在 onDraw 中執行

**情境：**
```
ThemeManager 在 Main thread 切換主題
→ applyTheme() → keyboardView.invalidate() → onDraw()
→ 同時 particleView.setTheme() → particles.clear()
```

兩個操作都在 Main thread，理論上不會 race，但 `setTheme()` 的 `particles.clear()` 發生在 `invalidate()` 前後的時序可能導致 `onDraw` 在 `clear()` 後立刻補了新粒子，主題色還是舊的。

**影響：** 切換主題的瞬間粒子顏色短暫不正確。視覺小問題，不影響功能。

---

### F-12｜語音辨識 partial result 和按鍵 composing 互蓋

**情境：**
```
語音辨識進行中（isListening = true）
→ partial result 持續更新 composingText
→ 使用者同時按了一個鍵（handleKeyPress 沒有 isListening 攔截）
→ 按鍵也更新 composingText
→ 語音的 partial 覆蓋了按鍵的輸入，或反過來
```

**確認：** `handleKeyPress` 開頭沒有 `if (speechEngine?.isListening == true) return` 的判斷。

**修法：**
```kotlin
private fun handleKeyPress(keyCode: Int, label: String) {
    // 語音輸入中：除了語音按鈕本身，其他按鍵先停止語音
    if (speechEngine?.isListening == true) {
        if (keyCode == KeyboardView.KEYCODE_VOICE) return  // 語音按鈕自己處理
        stopVoiceInput()
        // 繼續往下執行按鍵邏輯
    }
    // ... 其餘不變
}
```

---

### F-13｜Remote Keyboard 傳字 + 本地輸入 InputConnection 衝突

**情境：**
```
WiFi/藍牙遠端鍵盤模式已連線
→ 遠端 send 按鍵事件
→ 同時本地使用者也按了鍵
→ 兩個輸入同時操作同一個 InputConnection
→ 字元順序不定
```

**影響：** 遠端鍵盤功能本身是小眾功能，且兩邊同時操作的機率低。但沒有 Mutex 保護。

---

## 確認沒有衝突的項目

- **Panel 互斥** — `handlePanelToggle` 有 `activePanel` 單一來源管理，`showPanel` 會先 `removeAllViews`，同時只有一個 panel 在 container 裡 ✅
- **多個面板的內嵌/懸浮互斥** — `PanelCoordinator` 有完整邏輯 ✅
- **語言切換時 composing 殘留** — `handleSwitchLang` 有 `forceCommit` + `finishComposingText` + `inputRouter.reset()` ✅
- **AI Provider 切換後 Skill 用舊 Provider** — `getSkill()` 每次動態取 `providerManager.getCurrentProvider()` ✅
- **遊戲模式 + 候選字更新互蓋** — `isInGameMode = true` 時 `keyboardView` 被換成 `gameKeyboardView`，`updateCandidates` 指向不同 View ✅

---

## 修復優先順序

```
立刻修（影響日常使用）：
  F-1  語音輸入 + TTS 互搶     → 2行 if 判斷
  F-2  AI 補字 stale context   → 記錄 capturedComposing 比對
  F-3  遊戲中 Shake-Undo       → 1行 !isInGameMode 判斷
  F-6  滑動 + 注音未清 composing → 加 inputRouter.reset()

Token 正確性（有錢的問題）：
  F-4  Token 扣款 race condition → Mutex or synchronized

使用體驗：
  F-5  注音 + 翻譯面板輸入模式  → showPanel() 加 composing 清除
  F-8  陀螺儀 + 注音 composing  → 移動游標前先 forceCommit
  F-12 語音輸入中按鍵互蓋       → handleKeyPress 加 isListening 攔截
```
