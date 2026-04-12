# YukiBoard 每個鍵盤加語音按鈕

---

## 設計

在每個鍵盤的底行空白鍵旁邊加 `🎤` 按鈕，取代原本空間較少的位置。
按一下直接觸發語音輸入，跟工具列的 🎤 效果完全一樣。

---

## 修改一：`ui/keyboard/KeyboardView.kt` — 新增 KEYCODE

在 companion object 的 KEYCODE 定義裡加：

```kotlin
const val KEYCODE_VOICE = -12
```

---

## 修改二：`ui/keyboard/KeyboardState.kt` — 各鍵盤底行加語音鍵

每個鍵盤都在底行的 `↵` 前面加 `🎤`，同時縮減空白鍵寬度 1.0f 來騰出空間。

### QWERTY 底行（`defaultQwertyRows()`）

```kotlin
// 原本：
listOf(
    KeyModel.special("Ctrl",  KeyboardView.KEYCODE_CTRL,           widthWeight = 1.0f),
    KeyModel.special("Tab",   KeyboardView.KEYCODE_TAB,            widthWeight = 1.0f),
    KeyModel.special("?123",  KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.2f),
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG,    widthWeight = 1.2f),
    KeyModel.special(",",     KeyboardView.KEYCODE_COMMA, altChars = listOf("，","、","；","：","「","」")),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,          widthWeight = 3.0f),
    KeyModel.special(".",     KeyboardView.KEYCODE_PERIOD, altChars = listOf("。","！","？","…","～","·")),
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,          widthWeight = 1.5f)
)

// 改為：
listOf(
    KeyModel.special("Ctrl",  KeyboardView.KEYCODE_CTRL,           widthWeight = 1.0f),
    KeyModel.special("Tab",   KeyboardView.KEYCODE_TAB,            widthWeight = 1.0f),
    KeyModel.special("?123",  KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.2f),
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG,    widthWeight = 1.2f),
    KeyModel.special(",",     KeyboardView.KEYCODE_COMMA, altChars = listOf("，","、","；","：","「","」")),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,          widthWeight = 2.0f),  // 3.0 → 2.0
    KeyModel.special(".",     KeyboardView.KEYCODE_PERIOD, altChars = listOf("。","！","？","…","～","·")),
    KeyModel.special("🎤",   KeyboardView.KEYCODE_VOICE,           widthWeight = 1.0f),  // ← 新增
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,           widthWeight = 1.5f)
)
```

### 注音底行（`bopomofoRows()`）

```kotlin
// 原本：
listOf(
    KeyModel.char("ㄦ"),
    KeyModel.special("?123",  KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
    KeyModel.special("⌫",    KeyboardView.KEYCODE_DELETE,          widthWeight = 1.2f),
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG,    widthWeight = 1.0f),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,          widthWeight = 3.0f),
    KeyModel.special(",",     KeyboardView.KEYCODE_COMMA, altChars = listOf("，","、","；")),
    KeyModel.special(".",     KeyboardView.KEYCODE_PERIOD, altChars = listOf("。","！","？")),
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,           widthWeight = 1.2f)
)

// 改為：
listOf(
    KeyModel.char("ㄦ"),
    KeyModel.special("?123",  KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
    KeyModel.special("⌫",    KeyboardView.KEYCODE_DELETE,          widthWeight = 1.2f),
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG,    widthWeight = 1.0f),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,          widthWeight = 2.0f),  // 3.0 → 2.0
    KeyModel.special(",",     KeyboardView.KEYCODE_COMMA, altChars = listOf("，","、","；")),
    KeyModel.special(".",     KeyboardView.KEYCODE_PERIOD, altChars = listOf("。","！","？")),
    KeyModel.special("🎤",   KeyboardView.KEYCODE_VOICE,           widthWeight = 1.0f),  // ← 新增
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,           widthWeight = 1.2f)
)
```

### 數字鍵盤（`numpadRows()`）

```kotlin
// 原本：
listOf(
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,        widthWeight = 4f),
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,         widthWeight = 1.5f)
)

// 改為：
listOf(
    KeyModel.special("切換",  KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
    KeyModel.special(" ",     KeyboardView.KEYCODE_SPACE,        widthWeight = 3.0f),  // 4 → 3
    KeyModel.special("🎤",   KeyboardView.KEYCODE_VOICE,         widthWeight = 1.0f),  // ← 新增
    KeyModel.special("↵",    KeyboardView.KEYCODE_ENTER,         widthWeight = 1.5f)
)
```

### 數字符號鍵盤（`numberSymbolRows()`）

```kotlin
// 原本：
listOf(
    KeyModel.special("ABC",  KeyboardView.KEYCODE_SWITCH_ABC,    widthWeight = 1.3f),
    KeyModel.special("⌨",   KeyboardView.KEYCODE_SWITCH_NUMPAD, widthWeight = 1.0f),
    KeyModel.special(",",    KeyboardView.KEYCODE_COMMA),
    KeyModel.special(" ",    KeyboardView.KEYCODE_SPACE,          widthWeight = 4f),
    KeyModel.special(".",    KeyboardView.KEYCODE_PERIOD),
    KeyModel.special("↵",   KeyboardView.KEYCODE_ENTER,          widthWeight = 1.5f)
)

// 改為：
listOf(
    KeyModel.special("ABC",  KeyboardView.KEYCODE_SWITCH_ABC,    widthWeight = 1.3f),
    KeyModel.special("⌨",   KeyboardView.KEYCODE_SWITCH_NUMPAD, widthWeight = 1.0f),
    KeyModel.special(",",    KeyboardView.KEYCODE_COMMA),
    KeyModel.special(" ",    KeyboardView.KEYCODE_SPACE,          widthWeight = 3.0f),  // 4 → 3
    KeyModel.special(".",    KeyboardView.KEYCODE_PERIOD),
    KeyModel.special("🎤",  KeyboardView.KEYCODE_VOICE,          widthWeight = 1.0f),  // ← 新增
    KeyModel.special("↵",   KeyboardView.KEYCODE_ENTER,          widthWeight = 1.5f)
)
```

### 更多符號鍵盤（`moreSymbolRows()`）

```kotlin
// 原本：
listOf(
    KeyModel.special("ABC",  KeyboardView.KEYCODE_SWITCH_ABC, widthWeight = 1.5f),
    KeyModel.special(",",    KeyboardView.KEYCODE_COMMA),
    KeyModel.special(" ",    KeyboardView.KEYCODE_SPACE,       widthWeight = 5f),
    KeyModel.special(".",    KeyboardView.KEYCODE_PERIOD),
    KeyModel.special("↵",   KeyboardView.KEYCODE_ENTER,        widthWeight = 1.5f)
)

// 改為：
listOf(
    KeyModel.special("ABC",  KeyboardView.KEYCODE_SWITCH_ABC, widthWeight = 1.5f),
    KeyModel.special(",",    KeyboardView.KEYCODE_COMMA),
    KeyModel.special(" ",    KeyboardView.KEYCODE_SPACE,       widthWeight = 4.0f),  // 5 → 4
    KeyModel.special(".",    KeyboardView.KEYCODE_PERIOD),
    KeyModel.special("🎤",  KeyboardView.KEYCODE_VOICE,        widthWeight = 1.0f),  // ← 新增
    KeyModel.special("↵",   KeyboardView.KEYCODE_ENTER,        widthWeight = 1.5f)
)
```

### 其他語言鍵盤（拼音、倉頡、日文、韓文）

對每個有底行的鍵盤，找到 `KEYCODE_SPACE` 那行，把 widthWeight **減 1.0f**，在 `↵` 前面加：
```kotlin
KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 1.0f),
```

---

## 修改三：`service/YukiBoardIMEService.kt` — `handleKeyPress()` 處理語音鍵

在 `handleKeyPress()` 的 `when` 段（處理特殊鍵的地方）加：

```kotlin
KeyboardView.KEYCODE_VOICE -> {
    toggleVoiceInput()
    return
}
```

具體位置：找到 `else ->` 之前的特殊鍵判斷區，加在 `KEYCODE_SHIFT`、`KEYCODE_CTRL` 等旁邊。

---

## 驗證

```
□ QWERTY 底行有 🎤 按鈕，在 . 和 ↵ 之間
□ 注音底行有 🎤 按鈕
□ 數字鍵盤有 🎤 按鈕
□ 數字符號鍵盤有 🎤 按鈕
□ 更多符號鍵盤有 🎤 按鈕
□ 按 🎤 → 觸發語音輸入（跟工具列 🎤 一樣）
□ 語音輸入中按 🎤 → 停止語音輸入
□ 空白鍵仍然可以正常使用
□ 底行整體寬度不超出螢幕
```
