# YukiBoard — AUTO 模式：注音鍵盤上自動判斷要打英文還是注音

---

## 技術背景

### 這個功能的技術名稱
**Dual-script ambiguity resolution（雙符號意圖消歧）**

注音鍵盤上每個按鍵有兩種身份：
```
按下 Q 鍵 → 可以是 ㄆ（注音意圖）或 q（英文意圖）
按下 W 鍵 → 可以是 ㄊ（注音意圖）或 w（英文意圖）
```

系統要在使用者**按下的同時**，用三個訊號判斷意圖是哪個。

### 三個判斷訊號

```
訊號一：字典命中率（最強訊號）
  同時把按下的鍵當注音 和 當英文，各查一次字典
  例：按了 h-e-l-l-o
    注音解讀：ㄘ-ㄍ-ㄠ-ㄠ-ㄛ → 查注音字典 → 查無
    英文解讀：h-e-l-l-o → 查英文字典 → 找到 hello
    → 英文概率壓倒性勝出

訊號二：上下文連續性（中強訊號）
  剛剛 commit 了中文 → 後續偏注音
  剛剛 commit 了英文 → 後續偏英文
  上下文是英文 App（Twitter, Gmail）→ 偏英文

訊號三：音節合法性（補充訊號）
  ㄘㄍ 這個注音組合不構成合法音節 → 注音意圖降分
  he 是合法英文前綴 → 英文意圖加分
```

### 現況的問題

`autoDetectAndSwitch()` 現在只看第一個字元的 Unicode：
```kotlin
char in '\u3105'..'\u312F' -> BOPOMOFO  // 注音符號
char.isLetter() -> ENGLISH
```

注音鍵盤的按鍵 label 是 `ㄅㄆㄇ...`，永遠是 Unicode 注音範圍，
所以在注音鍵盤上永遠切 BOPOMOFO，AUTO 模式完全無效。

---

## 修改一：`InputRouter.kt` — 重寫 `autoDetectAndSwitch()`

### 新增完整對照表（加在 companion object 或 class 頂部）

```kotlin
// 注音符號 → 對應 QWERTY 小寫字母（大千式標準配置）
private val BOPOMOFO_TO_QWERTY: Map<String, String> = mapOf(
    "ㄆ" to "q", "ㄊ" to "w", "ㄍ" to "e", "ㄐ" to "r", "ㄔ" to "t",
    "ㄗ" to "y", "ㄧ" to "u", "ㄛ" to "i", "ㄟ" to "o", "ㄣ" to "p",
    "ㄇ" to "a", "ㄋ" to "s", "ㄎ" to "d", "ㄑ" to "f", "ㄕ" to "g",
    "ㄘ" to "h", "ㄨ" to "j", "ㄜ" to "k", "ㄠ" to "l",
    "ㄈ" to "z", "ㄌ" to "x", "ㄏ" to "c", "ㄒ" to "v", "ㄖ" to "b",
    "ㄙ" to "n", "ㄩ" to "m",
)
```

### 新增「累積英文緩衝區」狀態

在 `InputRouter` class 頂部加：

```kotlin
// AUTO 模式：累積的按鍵雙重解釋緩衝
private val autoQwertyBuffer = StringBuilder()   // 英文解讀（按鍵的 QWERTY 含義）
private val autoBopomofoBuffer = mutableListOf<String>()  // 注音解讀（按鍵的注音含義）
private var autoLastCommitWasEnglish = false     // 上次 commit 是英文嗎
private var autoEnglishScore = 0                 // 累積英文意圖分數
private var autoBopomofoScore = 0                // 累積注音意圖分數
```

### 完整重寫 `autoDetectAndSwitch()`

```kotlin
private fun autoDetectAndSwitch(label: String) {
    val ch = label.firstOrNull() ?: return

    // ── 取得這個鍵的雙重解讀 ──
    val isBopomofoKey = ch in '\u3105'..'\u312F' || ch in setOf('ˉ','ˊ','ˇ','ˋ','˙')
    val qwertyChar = if (isBopomofoKey) {
        BOPOMOFO_TO_QWERTY[label]?.firstOrNull()
    } else if (ch.isLetter()) {
        ch.lowercaseChar()
    } else null

    // 注音鍵盤上按了無法映射到英文的鍵（如聲調鍵、ㄅ、ㄦ 等）
    // → 明確是注音，直接切注音
    if (qwertyChar == null && isBopomofoKey) {
        if (currentMode != InputMode.BOPOMOFO) switchToClean(InputMode.BOPOMOFO)
        return
    }

    // ── 累積雙重解讀 ──
    if (qwertyChar != null) autoQwertyBuffer.append(qwertyChar)
    if (isBopomofoKey) autoBopomofoBuffer.add(label)
    else if (qwertyChar != null) autoBopomofoBuffer.add(
        // 英文字母在注音鍵盤上的含義
        when (qwertyChar) {
            'q' -> "ㄆ"; 'w' -> "ㄊ"; 'e' -> "ㄍ"; 'r' -> "ㄐ"; 't' -> "ㄔ"
            'y' -> "ㄗ"; 'u' -> "ㄧ"; 'i' -> "ㄛ"; 'o' -> "ㄟ"; 'p' -> "ㄣ"
            'a' -> "ㄇ"; 's' -> "ㄋ"; 'd' -> "ㄎ"; 'f' -> "ㄑ"; 'g' -> "ㄕ"
            'h' -> "ㄘ"; 'j' -> "ㄨ"; 'k' -> "ㄜ"; 'l' -> "ㄠ"
            'z' -> "ㄈ"; 'x' -> "ㄌ"; 'c' -> "ㄏ"; 'v' -> "ㄒ"; 'b' -> "ㄖ"
            'n' -> "ㄙ"; 'm' -> "ㄩ"
            else -> label
        }
    )

    val englishSoFar = autoQwertyBuffer.toString()
    val bopomofoSoFar = autoBopomofoBuffer

    // ── 評分邏輯 ──
    var englishScore = 0
    var bopomofoScore = 0

    // 訊號一：字典命中
    if (englishSoFar.length >= 2) {
        // 英文前綴命中
        val engCandidates = englishEngine.let {
            (it as? EnglishInputEngine)?.let { eng ->
                // 直接查字典前綴
                eng.getCandidatesForPrefix(englishSoFar)
            }
        } ?: emptyList()
        if (engCandidates.isNotEmpty()) englishScore += engCandidates.size * 10 + 30
    }

    if (bopomofoSoFar.isNotEmpty()) {
        // 注音音節合法性
        val bopoCandidates = bopomofoEngine.let {
            (it as? ChewingInputEngine)?.getCandidatesForBopomofo(
                bopomofoSoFar.joinToString("")
            )
        } ?: emptyList()
        if (bopoCandidates.isNotEmpty()) bopomofoScore += bopoCandidates.size * 10 + 30
    }

    // 訊號二：上下文連續性
    if (autoLastCommitWasEnglish) englishScore += 20
    else bopomofoScore += 20

    // 訊號三：音節合法性
    // 英文：字母序列長度合理
    if (englishSoFar.length in 2..15) englishScore += 10
    // 注音：前兩個符號能組成合法聲母+韻母/介音
    if (bopomofoSoFar.size >= 2) {
        val twoSymbol = bopomofoSoFar.take(2).joinToString("")
        if (isBopomofoSyllableStart(twoSymbol)) bopomofoScore += 15
    }

    // ── 決策 ──
    // 只有在分數差距明顯時才切換，避免在前 1-2 個鍵就亂切
    val threshold = if (autoQwertyBuffer.length <= 2) 40 else 20

    if (englishScore - bopomofoScore >= threshold) {
        if (currentMode != InputMode.ENGLISH) {
            // 切英文：把 autoQwertyBuffer 重新 feed 給英文引擎
            switchToClean(InputMode.ENGLISH)
            for (c in autoQwertyBuffer.toString().dropLast(1)) {
                englishEngine.processChar(c.toString())
            }
        }
    } else if (bopomofoScore - englishScore >= threshold) {
        if (currentMode != InputMode.BOPOMOFO) {
            switchToClean(InputMode.BOPOMOFO)
            for (sym in autoBopomofoBuffer.dropLast(1)) {
                bopomofoEngine.processChar(sym)
            }
        }
    }
    // 分數接近 → 繼續收集更多按鍵再決定
}

/** 切換模式並清空所有 composing 狀態 */
private fun switchToClean(mode: InputMode) {
    activeEngine.reset()
    candidateManager.clear()
    currentMode = mode
    activeEngine = engineForMode(mode)
}

/** 前兩個注音符號是否構成合法音節開頭 */
private fun isBopomofoSyllableStart(twoSymbol: String): Boolean {
    // 合法的聲母+韻母/介音組合（從字典前綴判斷）
    return (bopomofoEngine as? ChewingInputEngine)
        ?.getCandidatesForBopomofo(twoSymbol)?.isNotEmpty() == true
}
```

---

## 修改二：`ChewingInputEngine.kt` — 新增 public 查詢方法

AUTO 模式需要在不改變引擎狀態的前提下查字典。

```kotlin
/**
 * 不改變引擎狀態，只查詢指定注音是否有候選字（供 AUTO 模式評分用）
 */
fun getCandidatesForBopomofo(bopomofo: String): List<String> {
    if (bopomofo.isEmpty()) return emptyList()
    val syllables = segmentSyllables(bopomofo)
    return if (syllables.size >= 2) lookupMultiSyllable(syllables)
           else lookupCandidates(bopomofo)
}
```

---

## 修改三：`EnglishInputEngine.kt` — 新增 public 查詢方法

```kotlin
/**
 * 不改變引擎狀態，只查詢英文前綴是否有候選詞（供 AUTO 模式評分用）
 */
fun getCandidatesForPrefix(prefix: String): List<String> {
    if (prefix.isEmpty()) return emptyList()
    return dictionary.suggest(prefix, 5)
}
```

---

## 修改四：AUTO 模式的 commit 後清理

在 `InputRouter.kt` 的 `onTextCommitted()` 呼叫位置，加入 AUTO 狀態重置：

```kotlin
private fun onTextCommitted(text: String) {
    contextBeforeCursor = (contextBeforeCursor + text).takeLast(200)
    smartInput.onTextCommitted(text, contextBeforeCursor)

    // AUTO 模式：commit 後重置雙重評分緩衝，準備下一個詞
    if (currentMode == InputMode.AUTO ||
        currentMode == InputMode.BOPOMOFO ||
        currentMode == InputMode.ENGLISH) {
        autoQwertyBuffer.clear()
        autoBopomofoBuffer.clear()
        // 記錄這次 commit 的語言，影響下一個詞的初始偏向
        autoLastCommitWasEnglish = text.all { it.code < 128 }
    }
}
```

---

## 修改五：`YukiBoardIMEService.kt` — AUTO 模式的候選字顯示

AUTO 模式應該同時顯示注音候選和英文候選，讓使用者看到系統正在判斷什麼：

```kotlin
// 在 handleKeyPress 裡，AUTO 模式時顯示雙語候選
if (inputRouter.inputMode == InputMode.AUTO) {
    val bopoCandidates = inputRouter.bopomofoEngine
        .let { (it as? ChewingInputEngine)?.getCandidatesForBopomofo(
            inputRouter.autoBopomofoBuffer.joinToString("")) }
        ?: emptyList()
    val engCandidates = inputRouter.englishEngine
        .let { (it as? EnglishInputEngine)?.getCandidatesForPrefix(
            inputRouter.autoQwertyBuffer.toString()) }
        ?: emptyList()

    // 根據分數決定哪邊在前面
    val combined = if (inputRouter.autoEnglishScore >= inputRouter.autoBopomofoScore) {
        engCandidates + bopoCandidates
    } else {
        bopoCandidates + engCandidates
    }
    keyboardView.updateCandidates(combined.distinct().take(10))
}
```

---

## 修改六：設定頁 — 讓使用者可以開啟 AUTO 模式

### `YukiPreferences.kt` 加：

```kotlin
var autoInputMode: Boolean
    get() = prefs.getBoolean("auto_input_mode", false)
    set(value) = prefs.edit().putBoolean("auto_input_mode", value).apply()
```

### `YukiBoardIMEService.kt` 啟動時判斷：

```kotlin
// 在 onCreateInputView() 決定初始模式
val initialMode = if (prefs.autoInputMode) {
    InputMode.AUTO
} else {
    InputMode.BOPOMOFO  // 預設
}
inputRouter.switchTo(initialMode)
```

---

## 執行順序

```
1. ChewingInputEngine.kt → 新增 getCandidatesForBopomofo()（不改狀態）
2. EnglishInputEngine.kt → 新增 getCandidatesForPrefix()（不改狀態）
3. InputRouter.kt → 新增 BOPOMOFO_TO_QWERTY 對照表
4. InputRouter.kt → 新增 autoQwertyBuffer 等狀態變數
5. InputRouter.kt → 重寫 autoDetectAndSwitch()
6. InputRouter.kt → onTextCommitted() 加 AUTO 狀態重置
7. YukiBoardIMEService.kt → AUTO 模式候選字顯示
8. YukiPreferences.kt → autoInputMode 設定
9. SettingsActivity.kt → 加開關 UI
Build 測試
```

---

## 驗證

```
AUTO 模式開啟後（注音鍵盤不切換）：
□ 打 hello → 系統判斷英文 → 候選出現 hello
□ 打 ㄋㄧˇㄏㄠˇ → 系統判斷注音 → 候選出現「你好」
□ 打 nihao → 注音解讀 ㄙㄛㄘㄇㄟ 查無，英文解讀命中 → 切英文
□ 打 ok → 英文字典有，注音 ㄟㄜ 沒有合法詞 → 英文
□ 打 ㄅ → 只有注音鍵，直接注音模式
□ 連續打中文後突然打 OK → 識別出英文
□ 連續打英文後突然打注音 → 識別出注音
```

---

## 補充：為什麼這是「技術上難的問題」

注音鍵盤的特殊性在於：**每個按鍵同時是兩種語言的輸入**，而且無法靠單一字元判斷（`ㄆ` 和 `q` 是同一個鍵）。

華碩的實作可能還加了：
- **打字速度**：快速連打 → 偏英文；慢慢打帶停頓 → 偏注音
- **App 感知**：Twitter/Instagram → 偏英文；LINE → 偏注音
- **使用者歷史**：這個 App 過去用什麼語言打字最多

這些都可以之後疊加進去，但字典命中率是最核心、最有效的訊號，先把這個做好就能覆蓋 90% 的使用情境。
