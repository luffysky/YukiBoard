# YukiBoard — 面板輸入重設計 + 相簿選擇修復

---

## 問題根本原因診斷

### 問題一：相簿選不到
`BackgroundPickerActivity` 用 `ACTION_OPEN_DOCUMENT`（系統檔案管理器），
改成 `ACTION_PICK` 或 `ACTION_GET_CONTENT` 就能讓相簿 App 出現並有預覽。

### 問題二：解密/詞庫面板完全無法輸入
`DecryptPanelView` 和 `UserDictionaryPanelView` 建立後**沒有呼叫 `enterInputMode()`**，
所以 `isInInputMode` 永遠是 false，`handleKeyPress` 攔截邏輯永遠跳過它們。

### 問題三：GIF/翻譯面板無法輸入
`enterInputMode()` 有被呼叫，但面板開啟時使用者還沒點搜尋框，
`isInputMode` 是 false，按鍵盤沒有攔截效果。
真正的問題是：**使用者點了搜尋框，`isInputMode` 才變 true，
但這個狀態變化 IMEService 不知道**，所以第一下輸入沒效果。

### 問題四：輸入框（灰色）無法輸入
Canvas 繪製的輸入框是假的，點了之後 `isInputMode = true`，
但 IMEService 不會主動通知面板「你現在要接收輸入」，
需要面板有辦法通知 IMEService「我進入輸入模式了，把鍵盤輸入轉給我」。

---

## 修復一：相簿選擇

### 修改 `ui/settings/BackgroundPickerActivity.kt`

**把 `ACTION_OPEN_DOCUMENT` 改成 `ACTION_GET_CONTENT` + Chooser：**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val type = intent.getStringExtra(EXTRA_PICK_TYPE) ?: "image"
    val mimeType = if (type == "video") "video/*" else "image/*"

    // ── 改用 ACTION_GET_CONTENT，讓相簿 App 可以出現 ──
    val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
        this.type = mimeType
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)  // 只要本機檔案
    }

    // ── 同時提供 ACTION_PICK 作為替代（部分手機需要）──
    val pickIntent = Intent(Intent.ACTION_PICK).apply {
        this.type = mimeType
    }

    // ── 用 Chooser 讓使用者選擇相簿或檔案管理器 ──
    val chooser = Intent.createChooser(
        getContentIntent,
        if (type == "video") "選擇影片背景" else "選擇圖片背景"
    ).apply {
        // 把 ACTION_PICK 加進選項
        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickIntent))
    }

    try {
        @Suppress("DEPRECATION")
        startActivityForResult(chooser, RC_PICK)
    } catch (_: Exception) {
        // fallback：直接用檔案管理器
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    this.type = mimeType
                },
                RC_PICK
            )
        } catch (_: Exception) {
            Toast.makeText(this, "無法開啟選擇器", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
```

`onActivityResult` 不需要改，URI 處理邏輯相同。

---

## 修復二：解密面板輸入

### 問題
`DecryptPanelView` 沒有 `enterInputMode()` 函式，
建立後也沒有進入輸入模式，`isInInputMode` 永遠 false。

### 修改 `ui/panel/DecryptPanelView.kt`

**新增 `enterInputMode()` 函式（加在 `exitInputMode()` 上方）：**

```kotlin
/**
 * 進入輸入模式（密碼欄）。
 * 由 Service 在面板建立後呼叫，讓鍵盤輸入導向密碼欄。
 */
fun enterInputMode() {
    isInputMode = true
    activeField = "password"
    inputBuffer.clear()
    if (password.isNotEmpty()) inputBuffer.append(password)
    composingHint = ""
    invalidate()
}
```

### 修改 `service/YukiBoardIMEService.kt`

在 `PanelType.DECRYPT` 建立 panel 後加 `enterInputMode()`：

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

---

## 修復三：詞庫面板輸入

### 問題
`UserDictionaryPanelView` 建立後沒有 `enterInputMode()`，
`isInInputMode` 永遠 false。

### 確認 `UserDictionaryPanelView` 有 `enterInputMode()` 函式

如果沒有，加：

```kotlin
fun enterInputMode() {
    isInputMode = true
    inputBuffer.clear()
    invalidate()
}
```

### 修改 `service/YukiBoardIMEService.kt`

在 `PanelType.USER_DICT` 建立 panel 後加：

```kotlin
PanelType.USER_DICT -> {
    val p = UserDictionaryPanelView(this).also { userDictPanel = it }
    // ... 原有設定 ...
    p.enterInputMode()    // ← 新增
    p
}
```

---

## 修復四：GIF/翻譯面板輸入狀態同步

### 問題
使用者點了 GIF 搜尋框，面板內部 `isInputMode = true`，
但 IMEService 不知道，下一個按鍵的 `handleKeyPress` 判斷 `gp.isInInputMode`
已經是 true 了，**理論上應該能工作**。

**真正的問題是**：`onRequestInputMode` callback 沒有設定，
導致面板無法通知 IMEService「我要進入輸入模式」。

### 確認 `TranslatePanelView` 有 `onRequestInputMode` callback

```kotlin
var onRequestInputMode: ((Boolean) -> Unit)? = null
```

### 修改 `service/YukiBoardIMEService.kt`

在翻譯和 GIF 面板建立時，設定 `onRequestInputMode` callback：

```kotlin
PanelType.TRANSLATE -> {
    val p = TranslatePanelView(this).also { translatePanel = it }
    p.theme = theme
    // ... 原有設定 ...

    // ← 新增：讓面板可以通知 IME 輸入焦點
    p.onRequestInputMode = { entering ->
        if (entering) {
            // 面板要進入輸入模式：確保鍵盤輸入會路由到面板
            keyboardView.updateCandidates(emptyList())
            keyboardView.updateComposingText("")
        }
    }
    p.enterInputMode()
    p
}

PanelType.GIF -> {
    val p = GifPanelView(this).also { gifPanel = it }
    p.theme = theme
    // ... 原有設定 ...

    // ← 新增
    p.onRequestInputMode = { entering ->
        if (entering) {
            keyboardView.updateCandidates(emptyList())
            keyboardView.updateComposingText("")
        }
    }
    p.enterInputMode()
    p
}
```

---

## 修復五：統一面板輸入路由（根本解法）

現在的問題是每個面板都要個別處理，而且 `handleKeyPress` 有四段幾乎一樣的重複程式碼。

### 新增 `PanelInputTarget` 介面

在 `ui/panel/` 下新增介面：

```kotlin
// ui/panel/PanelInputTarget.kt
package com.snowrealm.yukiboard.ui.panel

/**
 * 可接收鍵盤輸入的面板介面。
 * 所有支援輸入的面板都實作此介面，讓 IMEService 統一路由。
 */
interface PanelInputTarget {
    val isInInputMode: Boolean

    fun onCharInput(text: String)
    fun onDeleteInput()
    fun onSpaceInput()
    fun onEnterInput()
    fun onComposedTextInput(text: String)
    fun updateComposingHint(hint: String)
    fun exitInputMode()
    fun enterInputMode()
}
```

### 讓四個面板都實作 PanelInputTarget

```kotlin
// GifPanelView
class GifPanelView(context: Context) : View(context), PanelInputTarget { ... }

// TranslatePanelView
class TranslatePanelView(context: Context) : View(context), PanelInputTarget { ... }

// UserDictionaryPanelView
class UserDictionaryPanelView(context: Context) : View(context), PanelInputTarget { ... }

// DecryptPanelView
class DecryptPanelView(context: Context) : View(context), PanelInputTarget { ... }
```

### 修改 `service/YukiBoardIMEService.kt`

用統一的 `activePanelInput` 變數取代四個個別 panel 參照：

```kotlin
// 新增
private var activePanelInput: PanelInputTarget? = null

// 面板建立時設定：
PanelType.GIF -> {
    val p = GifPanelView(this).also { gifPanel = it }
    // ... 設定 ...
    activePanelInput = p    // ← 設定統一路由目標
    p.enterInputMode()
    p
}
PanelType.TRANSLATE -> {
    val p = TranslatePanelView(this).also { translatePanel = it }
    // ...
    activePanelInput = p
    p.enterInputMode()
    p
}
PanelType.USER_DICT -> {
    val p = UserDictionaryPanelView(this).also { userDictPanel = it }
    // ...
    activePanelInput = p
    p.enterInputMode()
    p
}
PanelType.DECRYPT -> {
    val p = DecryptPanelView(this).also { decryptPanel = it }
    // ...
    activePanelInput = p
    p.enterInputMode()
    p
}

// closePanel 時清除：
private fun closePanel() {
    activePanelInput = null
    // ... 原有清理 ...
}
```

### 簡化 `handleKeyPress` 面板輸入路由

把四段重複程式碼合併成一段：

```kotlin
private fun handleKeyPress(keyCode: Int, label: String) {
    // ...

    // ── 統一面板輸入路由 ──
    val panelInput = activePanelInput
    if (panelInput != null && panelInput.isInInputMode) {
        val isCjk = inputRouter.inputMode != InputMode.ENGLISH
        when (keyCode) {
            KeyboardView.KEYCODE_DELETE -> {
                if (isCjk && inputRouter.composingText.isNotEmpty()) {
                    inputRouter.processDelete()
                    keyboardView.updateComposingText(inputRouter.composingText)
                    keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
                } else {
                    panelInput.onDeleteInput()
                }
            }
            KeyboardView.KEYCODE_ENTER -> {
                if (isCjk && inputRouter.composingText.isNotEmpty()) {
                    panelInput.onComposedTextInput(inputRouter.composingText)
                    inputRouter.reset()
                    keyboardView.updateComposingText("")
                    keyboardView.updateCandidates(emptyList())
                }
                panelInput.onEnterInput()
            }
            KeyboardView.KEYCODE_SPACE -> {
                if (isCjk && inputRouter.composingText.isNotEmpty()) {
                    val result = inputRouter.processSpace()
                    if (result is InputResult.CommitText) {
                        panelInput.onComposedTextInput(result.text)
                    }
                    keyboardView.updateComposingText(inputRouter.composingText)
                    keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
                } else {
                    panelInput.onSpaceInput()
                }
            }
            KeyboardView.KEYCODE_SWITCH_LANG -> handleSwitchLang()
            KeyboardView.KEYCODE_SHIFT, KeyboardView.KEYCODE_CTRL -> {}
            else -> if (label.isNotEmpty() && keyCode >= 0) {
                if (isCjk) {
                    val result = inputRouter.processInput(label)
                    if (result is InputResult.CommitText) {
                        panelInput.onComposedTextInput(result.text)
                    }
                    keyboardView.updateComposingText(inputRouter.composingText)
                    keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
                    panelInput.updateComposingHint(inputRouter.composingText)
                } else {
                    panelInput.onCharInput(label)
                }
            }
        }
        return
    }

    // 以下是原本的一般鍵盤輸入邏輯（不變）...
}
```

---

## 執行順序

```
1. 修復一：BackgroundPickerActivity（ACTION_GET_CONTENT + Chooser）
   → 最快，改一個函式，立刻能用相簿

2. 修復二：DecryptPanelView.enterInputMode()
3. 修復三：UserDictionaryPanelView.enterInputMode()
4. 修復二/三在 IMEService 呼叫
   → 解密和詞庫立刻能打字

5. 修復五：新增 PanelInputTarget 介面
   → 四個面板 implement 介面
   → IMEService 加 activePanelInput
   → handleKeyPress 合併四段為一段
   → 清掉舊的四段重複程式碼

Build 測試每步
```

---

## 驗證

```
相簿選擇：
□ 設定 → 背景 → 選圖片 → 出現相簿 App 且有預覽
□ 選影片 → 出現相簿 App 的影片清單
□ 選完後鍵盤背景更新

解密面板：
□ 開解密面板 → 直接打字 → 密碼欄出現文字
□ 注音輸入 → 候選字選字 → 進入密碼欄
□ Enter 確認 → 離開輸入模式

詞庫面板：
□ 開詞庫面板 → 直接打字 → 搜尋框出現文字
□ Enter → 搜尋執行

GIF 面板：
□ 開 GIF → 打字 → 搜尋框更新
□ Enter → 開始搜尋 GIF

翻譯面板：
□ 開翻譯 → 直接打字 → 輸入框出現文字
□ 注音輸入 + 選字 → 文字進入翻譯框
```
