# YukiBoard 手寫改善 + 韓文 + 鍵盤按鈕

---


---

## 修改零（根本問題）：切換語言時自動下載模型

### 問題根本原因

`applyLanguageSelection()` 直接呼叫 `engine.setLanguages()`，但不確認模型是否已下載。
如果英文/日文/韓文模型未下載，`recognizeSingle` 拿不到 recognizer → `callback(emptyList())` → 候選列空白，看起來像「沒有實作」。

### `ui/panel/HandwritingPanelView.kt` — `applyLanguageSelection()`

```kotlin
// 原本：
private fun applyLanguageSelection() {
    val option = langOptions[selectedLangIndex]
    if (option.tag == "mixed") {
        engine.setLanguages(listOf("zh-Hani-TW", "en-US"))
    } else {
        engine.setLanguages(listOf(option.tag))
    }
    invalidate()
}

// 改為：切換語言時先確認模型已下載，沒有就自動下載
private fun applyLanguageSelection() {
    val option = langOptions[selectedLangIndex]
    val tags = if (option.tag == "mixed") {
        listOf("zh-Hani-TW", "en-US")
    } else {
        listOf(option.tag)
    }

    // 找出尚未下載的模型
    val notDownloaded = tags.filter { modelStatus[it] != true }

    if (notDownloaded.isEmpty()) {
        // 全部已下載 → 直接切換
        engine.setLanguages(tags)
        invalidate()
    } else {
        // 有未下載的 → 顯示下載提示，自動下載後切換
        downloadingTag = notDownloaded.first()
        invalidate()

        var remaining = notDownloaded.size
        notDownloaded.forEach { tag ->
            engine.downloadModel(tag) { success ->
                handler.post {
                    if (success) {
                        modelStatus = modelStatus.toMutableMap().apply { put(tag, true) }
                    }
                    remaining--
                    if (remaining == 0) {
                        downloadingTag = null
                        engine.setLanguages(tags)
                        invalidate()
                    }
                }
            }
        }
    }
}
```

同時，`modelStatus` 在面板建立時就要初始化（不要等到打開模型管理才查）：

在 `onAttachedToWindow()` 或 init block 加：

```kotlin
// 面板一建立就查模型狀態，讓語言按鈕能正確顯示已下載/未下載
engine.checkAllModelStatus { status ->
    handler.post {
        modelStatus = status
        invalidate()
    }
}
```


---

## 現況說明

| 項目 | 現況 |
|------|------|
| 手寫引擎 | ML Kit Digital Ink，已支援繁中/英/日/簡中 |
| 韓文 | 未加入 |
| 候選字 | 手寫面板自己畫，沒有送到 KeyboardView 候選列 |
| 鍵盤按鈕 | 無，只能從工具列 ✍ 開啟 |

---

## 修改一：加入韓文支援

### `input/handwriting/HandwritingEngine.kt` — `SUPPORTED_LANGUAGES`

```kotlin
// 原本：
val SUPPORTED_LANGUAGES = listOf(
    "zh-Hani-TW" to "繁體中文",
    "en-US" to "English",
    "ja" to "日本語",
    "zh-Hani-CN" to "简体中文"
)

// 改為：
val SUPPORTED_LANGUAGES = listOf(
    "zh-Hani-TW" to "繁體中文",
    "en-US" to "English",
    "ja" to "日本語",
    "ko-Kore" to "한국어",
    "zh-Hani-CN" to "简体中文"
)
```

### `ui/panel/HandwritingPanelView.kt` — `langOptions`

```kotlin
// 原本：
private val langOptions = listOf(
    LangOption("zh-Hani-TW", "中"),
    LangOption("en-US", "英"),
    LangOption("ja", "日"),
    LangOption("mixed", "混合")
)

// 改為：
private val langOptions = listOf(
    LangOption("zh-Hani-TW", "中"),
    LangOption("en-US", "英"),
    LangOption("ja", "日"),
    LangOption("ko-Kore", "韓"),
    LangOption("mixed", "混合")
)
```

---

## 修改二：手寫候選字串聯到 KeyboardView 候選列

目前手寫候選字只在面板內顯示，選字直接 `commitText`。
改成：辨識結果同時送到 KeyboardView 的候選列，讓使用者可以從鍵盤上方的候選列選字，保持一致的操作體驗。

### `service/YukiBoardIMEService.kt` — 手寫面板建立段

找到 `p.onCandidateSelected = { text -> ...` 這段，改為：

```kotlin
p.onCandidateSelected = { text ->
    currentInputConnection?.commitText(text, 1)
    keyboardView.updateCandidates(emptyList())  // ← 選完清除候選列
}

// ← 新增：辨識結果同步到 KeyboardView 候選列
p.onRecognitionResult = { candidates ->
    if (candidates.isNotEmpty()) {
        // 把手寫候選字顯示在鍵盤上方的候選列
        val sourceMap = candidates.associateWith {
            com.snowrealm.yukiboard.input.router.CandidateSource.DICTIONARY
        }
        keyboardView.updateCandidates(candidates, sourceMap)
    }
}
```

同時在 `closePanel()` / 手寫面板關閉時清除候選列（確認已有 `keyboardView.updateCandidates(emptyList())`）。

### `ui/panel/HandwritingPanelView.kt` — 新增 `onRecognitionResult` callback

在 class 頂部加（跟 `onCandidateSelected` 放在一起）：

```kotlin
var onRecognitionResult: ((List<String>) -> Unit)? = null
```

在 `onRecognitionResult(results: List<String>)` 這個私有函式裡，辨識到結果後通知外部：

```kotlin
private fun onRecognitionResult(results: List<String>) {
    candidates = results
    invalidate()

    // ← 新增：通知 IMEService 同步到候選列
    if (results.isNotEmpty()) {
        onRecognitionResult?.invoke(results)
    }

    if (continuousMode && results.isNotEmpty()) {
        // 連續模式：自動送出第一個候選
        clearForContinuous()
    }
    if (candidates.isNotEmpty()) {
        onCandidateSelected?.invoke(candidates[0])
    }
    // ... 原有邏輯不變
}
```

> **注意：** `onRecognitionResult` 這個名稱和私有函式名稱衝突，把 callback 改名為 `onRecognized`：

```kotlin
// HandwritingPanelView.kt：
var onRecognized: ((List<String>) -> Unit)? = null  // ← 改名避免衝突

// 私有函式裡：
onRecognized?.invoke(results)  // ← 對應改名
```

```kotlin
// YukiBoardIMEService.kt：
p.onRecognized = { candidates ->
    val sourceMap = candidates.associateWith {
        com.snowrealm.yukiboard.input.router.CandidateSource.DICTIONARY
    }
    keyboardView.updateCandidates(candidates, sourceMap)
}
```

### KeyboardView 候選列選字觸發手寫確認

當使用者從 KeyboardView 候選列點選時，也要觸發手寫面板的選字邏輯：

在 `setOnCandidateSelectListener` 的回調裡（IMEService），加一個手寫面板的判斷：

```kotlin
keyboardView.setOnCandidateSelectListener { index ->
    // ← 新增：手寫模式下候選字選取
    val hwPanel = handwritingPanel
    if (hwPanel != null) {
        val displayedCandidates = keyboardView.candidates
        val text = displayedCandidates.getOrNull(index) ?: return@setOnCandidateSelectListener
        currentInputConnection?.commitText(text, 1)
        keyboardView.updateCandidates(emptyList())
        return@setOnCandidateSelectListener
    }

    // ... 原有選字邏輯
}
```

---

## 修改三：每個鍵盤加手寫按鈕

### `ui/keyboard/KeyboardView.kt` — 新增 KEYCODE

```kotlin
const val KEYCODE_VOICE       = -12   // （語音，已有）
const val KEYCODE_HANDWRITING = -13   // ← 新增
```

### `ui/keyboard/KeyboardState.kt` — 各鍵盤底行加 ✍ 鍵

每個有語音按鈕的底行，在 `🎤` 旁邊加 `✍`，空白鍵再縮 0.5f。

#### QWERTY 底行

```kotlin
listOf(
    KeyModel.special("Ctrl",  KeyboardView.KEYCODE_CTRL,           widthWeight = 1.0f),
    KeyModel.special("Tab",   KeyboardView.KEYCODE_TAB,            widthWeight = 1.0f),
    KeyModel.special("?123",  KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.2f),
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG,    widthWeight = 1.2f),
    KeyModel.special(",",     KeyboardView.KEYCODE_COMMA, altChars = listOf("，","、","；","：","「","」")),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,          widthWeight = 1.5f),  // 縮減
    KeyModel.special(".",     KeyboardView.KEYCODE_PERIOD, altChars = listOf("。","！","？","…","～","·")),
    KeyModel.special("🎤",   KeyboardView.KEYCODE_VOICE,           widthWeight = 1.0f),
    KeyModel.special("✍",    KeyboardView.KEYCODE_HANDWRITING,     widthWeight = 1.0f),  // ← 新增
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,           widthWeight = 1.5f)
)
```

#### 注音底行

```kotlin
listOf(
    KeyModel.char("ㄦ"),
    KeyModel.special("?123",  KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
    KeyModel.special("⌫",    KeyboardView.KEYCODE_DELETE,          widthWeight = 1.2f),
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG,    widthWeight = 1.0f),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,          widthWeight = 1.5f),  // 縮減
    KeyModel.special(",",     KeyboardView.KEYCODE_COMMA, altChars = listOf("，","、","；")),
    KeyModel.special(".",     KeyboardView.KEYCODE_PERIOD, altChars = listOf("。","！","？")),
    KeyModel.special("🎤",   KeyboardView.KEYCODE_VOICE,           widthWeight = 1.0f),
    KeyModel.special("✍",    KeyboardView.KEYCODE_HANDWRITING,     widthWeight = 1.0f),  // ← 新增
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,           widthWeight = 1.2f)
)
```

#### 數字鍵盤、數字符號、更多符號

同樣在 `🎤` 後加 `✍`，空白鍵各縮 0.5f。

#### 其他語言鍵盤（拼音、倉頡、日文、韓文）

找到各底行的 `KEYCODE_SPACE` 那行，把 widthWeight **再減 0.5f**（已因語音鍵縮過一次），在 `🎤` 後加：
```kotlin
KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 1.0f),
```

### `service/YukiBoardIMEService.kt` — `handleKeyPress()` 加手寫鍵處理

```kotlin
KeyboardView.KEYCODE_HANDWRITING -> {
    // 已開啟手寫面板 → 關閉；未開啟 → 開啟
    if (activePanel == PanelType.HANDWRITING) {
        closePanel()
    } else {
        handlePanelToggle(PanelType.HANDWRITING)
    }
    return
}
```

---

## 修改四：英文/日文手寫連接 InputRouter（候選字整合）

目前手寫選字直接 `commitText`，不走 InputRouter，沒有學習效果。
但手寫是直接辨識最終字，不需要走引擎，所以 `commitText` 是對的。

改善點：選字後觸發智慧學習（讓常用字排前）：

```kotlin
p.onCandidateSelected = { text ->
    currentInputConnection?.commitText(text, 1)
    keyboardView.updateCandidates(emptyList())
    // ← 新增：通知 InputRouter 學習這個字
    inputRouter.onHandwritingCommit(text)
}
```

在 `InputRouter.kt` 新增：
```kotlin
fun onHandwritingCommit(text: String) {
    // 記錄到自適應字典
    smartInput.adaptiveDictionary.recordUsage(text)
    onTextCommitted(text)
}
```

---

## 執行順序

```
0. HandwritingPanelView.kt：
   - applyLanguageSelection() 加自動下載邏輯
   - onAttachedToWindow/init 加 checkAllModelStatus 初始化

1. HandwritingEngine.kt：SUPPORTED_LANGUAGES 加韓文 "ko-Kore"
2. HandwritingPanelView.kt：
   - langOptions 加韓文選項
   - 新增 onRecognized callback
   - onRecognitionResult 私有函式裡呼叫 onRecognized?.invoke(results)
3. YukiBoardIMEService.kt：
   - 手寫面板建立時設定 onRecognized
   - setOnCandidateSelectListener 加手寫模式判斷
   - handleKeyPress 加 KEYCODE_HANDWRITING case
4. KeyboardView.kt：加 KEYCODE_HANDWRITING = -13
5. KeyboardState.kt：各底行加 ✍ 按鍵（空白鍵縮 0.5f）
6. InputRouter.kt：新增 onHandwritingCommit()
```

---

## 驗證

```
英文/日文修復：
□ 切換到「英」→ 若未下載自動開始下載，下載完成後可辨識英文
□ 切換到「日」→ 若未下載自動開始下載，下載完成後可辨識日文
□ 面板一開啟，語言按鈕就能正確顯示已下載（✓）或未下載

韓文：
□ 手寫面板語言選項有「韓」
□ 點「韓」→ 自動下載韓文模型（第一次）
□ 韓文手寫可以辨識

候選字串聯：
□ 手寫後，辨識結果同時出現在鍵盤上方候選列
□ 點鍵盤候選列的字 → 送出（跟點面板內候選字效果一樣）
□ 關閉手寫面板 → 候選列清空

鍵盤按鈕：
□ 每個鍵盤底行有 ✍ 按鈕（在 🎤 旁邊）
□ 按 ✍ → 開啟手寫面板
□ 再按 ✍ → 關閉手寫面板
□ 空白鍵仍然可以正常使用
```
