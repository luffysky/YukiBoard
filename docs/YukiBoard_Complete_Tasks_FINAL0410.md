# YukiBoard 最終完整任務包 v2

> 整合所有文件 + 鍵盤 UI 對齊 + 按鍵難按修復

---

## 總覽

| # | 類型 | 項目 | 檔案 |
|---|------|------|------|
| B1 | 🔴 | 英文/注音打字重複送出 | `YukiBoardIMEService.kt` |
| B2 | 🔴 | 數字/符號按不出來 | `YukiBoardIMEService.kt` |
| B3 | 🔴 | 注音選字後候選消失 | `ChewingInputEngine.kt` `InputRouter.kt` |
| B4 | 🟡 | 換算面板 Tab 按不了 | `UnitConverterPanelView.kt` |
| B5 | 🟡 | 候選字要點幾次才反應 | `KeyboardView.kt` |
| B6 | 🟡 | 候選字下拉展開難按 | `KeyboardView.kt` |
| B7 | 🟡 | HandwritingPanelView 崩潰 | `HandwritingPanelView.kt` |
| B8 | 🟡 | 解密面板佔整個畫面 | `DecryptPanelView.kt` |
| B9 | 🟡 | 面板無法輸入（解密/詞庫） | `DecryptPanelView.kt` `YukiBoardIMEService.kt` |
| B10 | 🟡 | 按鍵有時很難按 | `KeyboardView.kt` |
| U1 | 🎨 | 鍵盤 UI 對齊（每排縫隙一致） | `KeyboardView.kt` `KeyboardState.kt` |
| F1 | 🎵 | 真正音階音效（AudioTrack） | 新增 `KeyboardSoundEngine.kt` |
| F2 | 🔤 | 大小寫轉換（長按 Shift） | `KeyboardView.kt` `YukiBoardIMEService.kt` |
| F3 | 🎤 | 語音辨識三引擎改善 | 三個 Speech 引擎 |
| F4 | 🎤 | 語音後處理（LLM 清理） | 新增 `VoicePostProcessor.kt` |
| F5 | 🔢 | 候選字常見字優先 | `InputRouter.kt` |
| F6 | 🔀 | AUTO 模式中英混合候選 | `InputRouter.kt` |
| F7 | 🔍 | 候選字長按放大鏡 | `KeyboardView.kt` |

---

## 🔴 B1：英文/注音打字重複送出

### `service/YukiBoardIMEService.kt` — `applyInputResult()` 的 `CommitText` 分支

```kotlin
is InputResult.CommitText -> {
    // ... 情緒冷靜期不變 ...
    ic.beginBatchEdit()
    ic.setComposingText("", 0)   // ← 0 = 丟棄 composing，不 commit
    ic.commitText(result.text, 1)
    ic.endBatchEdit()
    // ... 其餘不變
}
```

---

## 🔴 B2：數字/符號按不出來

### `service/YukiBoardIMEService.kt` — `handleKeyPress()` 數字/符號路徑

找到 `if (ch != null && !ch.isLetter() && !isBopomofo)` 整段替換：

```kotlin
if (ch != null && !ch.isLetter() && !isBopomofo) {
    if (inputRouter.composingText.isNotEmpty()) {
        val spaceResult = inputRouter.processSpace()
        if (spaceResult is InputResult.CommitText) {
            val trimmed = spaceResult.text.trimEnd()
            if (trimmed.isNotEmpty()) {
                ic.beginBatchEdit()
                ic.setComposingText("", 0)
                ic.commitText(trimmed, 1)
                ic.endBatchEdit()
            }
        } else {
            val commitResult = inputRouter.getActiveEngine().forceCommit()
            if (commitResult is InputResult.CommitText) {
                ic.beginBatchEdit()
                ic.setComposingText("", 0)
                ic.commitText(commitResult.text, 1)
                ic.endBatchEdit()
            } else {
                ic.setComposingText("", 0)
                ic.finishComposingText()
            }
        }
        inputRouter.reset()
        keyboardView.updateComposingText("")
        keyboardView.updateCandidates(emptyList())
    }
    ic.commitText(label, 1)
}
```

---

## 🔴 B3：注音選字後候選消失

### `input/engine/ChewingInputEngine.kt` — `selectCandidateAt()`

```kotlin
fun selectCandidateAt(index: Int): InputResult {
    if (index !in currentCandidates.indices) return InputResult.Idle
    val selected = currentCandidates[index]
    lastComposingBopomofo = bopomofoBuffer.toString()
    selectedBuffer.append(selected)
    bopomofoBuffer.clear()
    isSelectingCandidate = false
    lastCommittedChar = selected.last().toString()
    currentCandidates = getNextCharPredictions(lastCommittedChar)
    return InputResult.ComposingUpdated(composingText)
}
```

### `input/router/InputRouter.kt` — `updateCandidates()` 的 `if (raw.isEmpty())` 段

```kotlin
if (raw.isEmpty()) {
    val lastChar = composing.lastOrNull()?.toString() ?: ""
    val continuations = if (lastChar.isNotEmpty() && lastChar.any { it.code > 0x2E80 }) {
        generateContinuationCandidates(lastChar)
    } else emptyList()

    if (continuations.isNotEmpty()) {
        for (c in continuations) historySourceMap[c] = CandidateSource.PREDICTION
        for (h in candidateManager.historyCandidates) historySourceMap[h] = CandidateSource.HISTORY
        candidateSourceMap = historySourceMap
        candidateManager.update(
            continuations + candidateManager.historyCandidates.filter { it !in continuations }
        )
        return
    }
    for (h in candidateManager.historyCandidates) historySourceMap[h] = CandidateSource.HISTORY
    candidateSourceMap = historySourceMap
    candidateManager.update(emptyList())
    return
}
```

---

## 🟡 B4：換算面板 Tab 按不了

### `ui/panel/UnitConverterPanelView.kt`

```kotlin
class UnitConverterPanelView(context: Context) : View(context) {
    init { setWillNotDraw(false) }
    // ...
}
```

同時修單位選擇邏輯：

```kotlin
unitRects.firstOrNull { it.first.contains(x, y) }?.let { (_, idx) ->
    when {
        idx == fromIndex -> { }
        idx == toIndex -> { val tmp = fromIndex; fromIndex = toIndex; toIndex = tmp }
        else -> toIndex = idx
    }
    invalidate(); return true
}
```

---

## 🟡 B5：候選字要點幾次才反應

### `ui/keyboard/KeyboardView.kt`

**修1：選字用 DOWN 位置**
```kotlin
// 找到：val idx = hitTestCandidateIndex(x)
// 改為：
val idx = hitTestCandidateIndex(if (candidateIsDragging) x else candidateDownX)
```

**修2：`ACTION_CANCEL` 補清狀態**
```kotlin
MotionEvent.ACTION_CANCEL -> {
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    handler.removeCallbacks(candidateLongPressRunnable)  // ← 新增
    pressedKey = null
    isLongPressTriggered = false
    candidateTouchActive = false           // ← 新增
    candidateLongPressActive = false       // ← 新增
    candidateLongPressIndex = -1           // ← 新增
    // ... 其餘不變
```

---

## 🟡 B6：候選字下拉展開難按

### `ui/keyboard/KeyboardView.kt`

**class 頂部新增兩個變數：**
```kotlin
private var expandDownX = 0f
private var expandDownY = 0f
```

**`ACTION_DOWN` 展開面板加兩行：**
```kotlin
if (isCandidateExpanded && y > candidateBarHeight) {
    expandTouchStartY = y; expandLastTouchY = y
    expandDownX = x    // ← 新增
    expandDownY = y    // ← 新增
    expandIsScrolling = false; return true
}
```

**`ACTION_UP` 格子選字用 DOWN 位置：**
```kotlin
// 找到：
val col = (x / cellWidth).toInt().coerceIn(0, EXPAND_GRID_COLS - 1)
val row = ((y - startY) / cellHeight).toInt()
// 改為：
val selectX = if (expandIsScrolling) x else expandDownX
val selectY = if (expandIsScrolling) y else expandDownY
val col = (selectX / cellWidth).toInt().coerceIn(0, EXPAND_GRID_COLS - 1)
val row = ((selectY - startY) / cellHeight).toInt()
```

**展開箭頭加大觸控區：**
```kotlin
// 找到：
private val expandArrowWidth by lazy { 28f * density }
// 改為：
private val expandArrowWidth by lazy { 44f * density }
```

---

## 🟡 B7：HandwritingPanelView 崩潰

### `ui/panel/HandwritingPanelView.kt`

```kotlin
// line 457：pressedBtn!! → pressedBtn?.let { handleButtonUp(it, x, y) }
// line 464：drawPaths.add(currentPath!!) → currentPath?.let { drawPaths.add(it) }
```

---

## 🟡 B8：解密面板佔整個畫面

### `ui/panel/DecryptPanelView.kt`

```kotlin
override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val w = MeasureSpec.getSize(widthMeasureSpec)
    val maxH = (260 * resources.displayMetrics.density).toInt()
    setMeasuredDimension(w, maxH)
}
```

---

## 🟡 B9：面板無法輸入

### `ui/panel/DecryptPanelView.kt`

新增：
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

### `service/YukiBoardIMEService.kt`

在 `PanelType.DECRYPT` 建立後加 `p.enterInputMode()`，確認 `PanelType.USER_DICT` 也有呼叫。

---

## 🟡 B10：按鍵有時很難按

### `ui/keyboard/KeyboardView.kt` — `resetTouchState()`

確認 `resetTouchState()` 裡有 `isLongPressTriggered = false`，`ACTION_UP` 最開頭也有清除：

```kotlin
MotionEvent.ACTION_UP -> {
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(deleteRepeatRunnable)
    pressedKey = null
    isLongPressTriggered = false    // ← 確認存在
    // ...
```

---

## 🎨 U1：鍵盤 UI 對齊（每排縫隙一致）

### 根本原因

目前 `drawKeysInRegion` 用所有排的最大 `widthWeight` 作為 `totalUnits`：
```
數字行:  10.0
QWERTY: 10.0
ASDF:    9.0  ← 比最大值少，右側有多餘空白
ZXCV:   10.0
底行:   10.9  ← 最大值，其他排按鍵都比底行更寬
```

結果：每排左右縫隙不一致，按鍵寬度也不一致。

### 正確做法

**每排用自己的 `totalUnits` 計算鍵寬，然後把這排置中**。

這樣每排的按鍵尺寸一致（相同 widthWeight 的鍵寬相同），左右縫隙相等。

### `ui/keyboard/KeyboardView.kt` — `drawKeysInRegion()`

找到這段：
```kotlin
val totalUnits = rows.maxOfOrNull { row ->
    row.sumOf { it.widthWeight.toDouble() }.toFloat()
} ?: 10f

rows.forEachIndexed { rowIndex, row ->
    val top = candidateBarHeight + rowIndex * rh
    var x = left
    val unitWidth = regionWidth / totalUnits
```

**改為：**
```kotlin
// 以最大 widthWeight 總和為基準，計算統一的 unitWidth
val maxRowUnits = rows.maxOfOrNull { row ->
    row.sumOf { it.widthWeight.toDouble() }.toFloat()
} ?: 10f
val unitWidth = regionWidth / maxRowUnits

rows.forEachIndexed { rowIndex, row ->
    val top = candidateBarHeight + rowIndex * rh

    // 計算這排的實際寬度，置中排列
    val rowTotalUnits = row.sumOf { it.widthWeight.toDouble() }.toFloat()
    val rowActualWidth = rowTotalUnits * unitWidth
    val rowOffset = (regionWidth - rowActualWidth) / 2f   // 左右對稱縫隙
    var x = left + rowOffset
```

同時刪除原本在 `rows.forEachIndexed` 裡的 `val unitWidth = regionWidth / totalUnits`（已移到外面）。

### `ui/keyboard/KeyboardState.kt` — 調整底行 widthWeight

底行目前總計 10.9，比其他排多，是縫隙不一致的主因。調整讓底行總計也是 10.0：

```kotlin
// 底行目前：Ctrl(1.0) Tab(1.0) ?123(1.2) 切換(1.2) ,(1.0) space(3.0) .(1.0) ↵(1.5) = 10.9
// 調整方案：縮小 Ctrl Tab 到 0.9，space 維持 3.0，其他不變
listOf(
    KeyModel.special("Ctrl", KeyboardView.KEYCODE_CTRL, widthWeight = 0.9f),   // 1.0 → 0.9
    KeyModel.special("Tab",  KeyboardView.KEYCODE_TAB,  widthWeight = 0.9f),   // 1.0 → 0.9
    KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.1f), // 1.2 → 1.1
    KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG,    widthWeight = 1.1f), // 1.2 → 1.1
    KeyModel.special(",",    KeyboardView.KEYCODE_COMMA, altChars = listOf("，","、","；","：","「","」")),
    KeyModel.special(" ",    KeyboardView.KEYCODE_SPACE, widthWeight = 3.0f),
    KeyModel.special(".",    KeyboardView.KEYCODE_PERIOD, altChars = listOf("。","！","？","…","～","·")),
    KeyModel.special("↵",   KeyboardView.KEYCODE_ENTER, widthWeight = 1.5f)
    // 總計：0.9+0.9+1.1+1.1+1.0+3.0+1.0+1.5 = 10.5
)
```

> **注意：** widthWeight 調整後，Claude Code 需要反覆試，讓底行和其他排視覺上一致。目標是所有排的 `rowOffset` 接近 0（每排幾乎佔滿整個寬度，縫隙相等）。

### 同時確認 touchRect 也用置中後的 x

上面的修改已包含：`var x = left + rowOffset`，`touchRect` 和 `rect` 都用這個 `x`，所以觸控和繪製都會自動對齊。

---

## 🎵 F1：真正音階音效（AudioTrack 正弦波）

### 新增 `service/KeyboardSoundEngine.kt`

```kotlin
package com.snowrealm.yukiboard.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.*

class KeyboardSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sampleRate = 44100
    var volumeScale = 0.45f
    var defaultStyle = ToneStyle.PIANO

    private fun hz(semitone: Int) = 440f * 2f.pow(semitone / 12f)

    private val C3=hz(-21); private val G3=hz(-14); private val A3=hz(-12)
    private val B3=hz(-10); private val GS3=hz(-13)
    private val C4=hz(-9);  private val D4=hz(-7);  private val E4=hz(-5)
    private val F4=hz(-4);  private val G4=hz(-2);  private val A4=hz(0)
    private val B4=hz(2);   private val C5=hz(3);   private val D5=hz(5)
    private val E5=hz(7);   private val F5=hz(8);   private val G5=hz(10)
    private val A5=hz(12);  private val B5=hz(14);  private val C6=hz(15)
    private val D6=hz(17);  private val E6=hz(19)

    private val keyNotes: Map<String, Float> by lazy { buildMap {
        put("1",C5); put("2",D5); put("3",E5); put("4",F5); put("5",G5)
        put("6",A5); put("7",B5); put("8",C6); put("9",D6); put("0",E6)
        put("q",C4); put("w",D4); put("e",E4); put("r",F4); put("t",G4)
        put("y",A4); put("u",B4); put("i",C5); put("o",D5); put("p",E4)
        put("a",A3); put("s",B3); put("d",C4); put("f",D4); put("g",E4)
        put("h",F4); put("j",G4); put("k",A4); put("l",B4)
        put("z",G3); put("x",A3); put("c",B3); put("v",C4)
        put("b",D4); put("n",E4); put("m",G3)
        put("ㄅ",C4); put("ㄆ",D4); put("ㄇ",E4); put("ㄈ",F4)
        put("ㄉ",G4); put("ㄊ",A4); put("ㄋ",B4); put("ㄌ",C5)
        put("ㄍ",D5); put("ㄎ",E5); put("ㄏ",F5)
        put("ㄐ",G5); put("ㄑ",A5); put("ㄒ",B5)
        put("ㄓ",C4); put("ㄔ",D4); put("ㄕ",E4); put("ㄖ",F4)
        put("ㄗ",G4); put("ㄘ",A4); put("ㄙ",B4)
        put("ㄧ",D5); put("ㄨ",E5); put("ㄩ",G5)
        put("ㄚ",A5); put("ㄛ",B5); put("ㄜ",C5); put("ㄝ",D5)
        put("ㄞ",E5); put("ㄟ",F5); put("ㄠ",G5); put("ㄡ",A5)
        put("ㄢ",B5); put("ㄣ",C6); put("ㄤ",D6); put("ㄥ",E6); put("ㄦ",G5)
        put("ˊ",E5); put("ˇ",C5); put("ˋ",A4); put("˙",G4)
        put(" ",C4); put("⌫",GS3); put("\n",G4)
    }}

    enum class ToneStyle { PIANO, MARIMBA, SOFT }

    fun playKeyTone(label: String, durationMs: Int = 85) {
        val freq = keyNotes[label] ?: keyNotes[label.lowercase()] ?: C4
        scope.launch { try { playTone(freq, durationMs, defaultStyle) } catch (_: Exception) {} }
    }

    private suspend fun playTone(freq: Float, durationMs: Int, style: ToneStyle) {
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val attackSamples = sampleRate * when (style) { ToneStyle.PIANO->5; ToneStyle.MARIMBA->3; ToneStyle.SOFT->20 } / 1000
        val decayRate = when (style) { ToneStyle.PIANO->6f; ToneStyle.MARIMBA->9f; ToneStyle.SOFT->1.5f }

        for (i in samples.indices) {
            val s = sin(2.0 * PI * freq * i / sampleRate).toFloat()
            val harmonic = when (style) {
                ToneStyle.PIANO -> s + sin(2.0*PI*freq*2*i/sampleRate).toFloat()*0.3f +
                                       sin(2.0*PI*freq*3*i/sampleRate).toFloat()*0.1f
                ToneStyle.MARIMBA -> s + sin(2.0*PI*freq*2*i/sampleRate).toFloat()*0.5f
                ToneStyle.SOFT -> s
            }
            val env = if (i < attackSamples) i.toFloat()/attackSamples
                      else exp(-decayRate * (i-attackSamples).toFloat()/(numSamples-attackSamples))
            samples[i] = (harmonic * env * volumeScale * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufSize = AudioTrack.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(numSamples*2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufSize).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(samples, 0, samples.size)
        track.play()
        delay(durationMs.toLong() + 15)
        track.stop(); track.release()
    }

    fun destroy() { scope.cancel() }
}
```

### `service/YukiBoardIMEService.kt` 整合

```kotlin
// 成員變數
private var keySoundEngine: KeyboardSoundEngine? = null

// onCreateInputView() 裡
keySoundEngine = KeyboardSoundEngine()

// handleKeyPress() 開頭
if (prefs.keySoundEnabled) {
    keySoundEngine?.defaultStyle = when (prefs.keySoundStyle) {
        "marimba" -> KeyboardSoundEngine.ToneStyle.MARIMBA
        "soft"    -> KeyboardSoundEngine.ToneStyle.SOFT
        else      -> KeyboardSoundEngine.ToneStyle.PIANO
    }
    keySoundEngine?.playKeyTone(label)
}

// onDestroy()
keySoundEngine?.destroy()
```

### `ui/settings/YukiPreferences.kt`
```kotlin
var keySoundStyle: String by StringPref("key_sound_style", "piano")
var keySoundVolume: Float by FloatPref("key_sound_volume", 0.45f)
```

### `ui/settings/SettingsActivity.kt` — 按鍵音效開關下方加：
```kotlin
radioRow("音色", listOf("piano" to "🎹 鋼琴", "marimba" to "🪘 木琴", "soft" to "🎻 柔和"),
    prefs.keySoundStyle) { prefs.keySoundStyle = it }
sliderRow("音效音量", (prefs.keySoundVolume * 100).toInt(), 10, 100) { vol ->
    prefs.keySoundVolume = vol / 100f
    keySoundEngine?.volumeScale = vol / 100f
}
```

---

## 🔤 F2：大小寫轉換（長按 Shift）

### `ui/keyboard/KeyboardView.kt`

**class 頂部新增：**
```kotlin
enum class CaseConvertType { UPPER, LOWER, INVERT }
var onCaseConvert: ((CaseConvertType) -> Unit)? = null
private var caseMenuVisible = false
private var caseMenuAnchor = RectF()
private val caseMenuItems = listOf(
    "AA 全大寫" to CaseConvertType.UPPER,
    "aa 全小寫" to CaseConvertType.LOWER,
    "Aa 反轉"   to CaseConvertType.INVERT
)
```

**`longPressRunnable` 加 Shift case：**
```kotlin
key.code == KEYCODE_SHIFT -> {
    isLongPressTriggered = true; vibrateShort()
    caseMenuAnchor = rect; caseMenuVisible = true; invalidate()
}
```

**`onDraw()` 末尾加：**
```kotlin
if (caseMenuVisible) drawCaseMenu(canvas)
```

**新增 `drawCaseMenu()`：**
```kotlin
private fun drawCaseMenu(canvas: Canvas) {
    val itemH = 36f * density; val itemW = 96f * density; val pad = 8f * density
    val totalH = itemH * caseMenuItems.size + pad * 2
    val left = (caseMenuAnchor.centerX() - itemW / 2f).coerceIn(0f, width - itemW)
    val top = caseMenuAnchor.top - totalH - 4f * density
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.keyColorInt; setShadowLayer(8f*density, 0f, 2f*density, 0x44000000)
    }
    canvas.drawRoundRect(RectF(left-pad, top-pad, left+itemW+pad, top+totalH+pad),
        12f*density, 12f*density, bgPaint)
    caseMenuItems.forEachIndexed { i, (label, _) ->
        val itemTop = top + pad + i * itemH
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f*density; color = theme.keyLabelColorInt; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, left+itemW/2f, itemTop+itemH/2f+tp.textSize*0.35f, tp)
    }
}
```

**`ACTION_UP` 最開頭（在 altPopupActive 之前）加：**
```kotlin
if (caseMenuVisible) {
    val itemH = 36f*density; val itemW = 96f*density; val pad = 8f*density
    val totalH = itemH*caseMenuItems.size + pad*2
    val menuLeft = (caseMenuAnchor.centerX()-itemW/2f).coerceIn(0f, width-itemW)
    val menuTop = caseMenuAnchor.top - totalH - 4f*density
    caseMenuItems.forEachIndexed { i, (_, type) ->
        val itemTop = menuTop + pad + i * itemH
        if (RectF(menuLeft, itemTop, menuLeft+itemW, itemTop+itemH).contains(x, y)) {
            onCaseConvert?.invoke(type)
            caseMenuVisible = false; isLongPressTriggered = false
            invalidate(); performClick(); return true
        }
    }
    caseMenuVisible = false; isLongPressTriggered = false; invalidate(); return true
}
```

**`ACTION_CANCEL` 加：**
```kotlin
caseMenuVisible = false
```

### `service/YukiBoardIMEService.kt`

```kotlin
keyboardView.onCaseConvert = { type ->
    val ic = currentInputConnection ?: return@onCaseConvert
    val selected = ic.getSelectedText(0)?.toString()
    val target = if (!selected.isNullOrEmpty()) selected
    else {
        val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val lastSpace = before.lastIndexOf(' ')
        if (lastSpace >= 0) before.substring(lastSpace + 1) else before
    }
    if (target.isEmpty()) return@onCaseConvert
    val converted = when (type) {
        KeyboardView.CaseConvertType.UPPER  -> target.uppercase()
        KeyboardView.CaseConvertType.LOWER  -> target.lowercase()
        KeyboardView.CaseConvertType.INVERT -> target.map {
            if (it.isUpperCase()) it.lowercase() else it.uppercase()
        }.joinToString("")
    }
    if (converted == target) return@onCaseConvert
    ic.beginBatchEdit()
    if (!selected.isNullOrEmpty()) ic.commitText(converted, 1)
    else { ic.deleteSurroundingText(target.length, 0); ic.commitText(converted, 1) }
    ic.endBatchEdit()
}
```

---

## 🎤 F3：語音辨識三引擎改善

### `speech/AndroidSpeechEngine.kt`

intent 靜音偵測延長：
```kotlin
putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
```

`onResults` 選最高 confidence：
```kotlin
override fun onResults(results: Bundle?) {
    isListening = false
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
    val best = if (scores != null && matches != null) {
        matches.getOrNull(scores.indices.maxByOrNull { scores[it] } ?: 0) ?: ""
    } else matches?.firstOrNull() ?: ""
    onFinalResult(best)
}
```

### `speech/WhisperSpeechEngine.kt`

```kotlin
private val partialIntervalMs = 2500L  // 原 1500L
```

`transcribe()` 加完整語言參數和 temperature：
```kotlin
.addFormDataPart("temperature", "0")
// language 判斷：
when {
    language == "zh" || language.startsWith("zh-") -> {
        bodyBuilder.addFormDataPart("language", "zh")
        bodyBuilder.addFormDataPart("prompt", "${contextHint}以下是繁體中文對話，請輸出繁體中文，不要簡體字。")
    }
    language == "en" || language.startsWith("en-") -> {
        bodyBuilder.addFormDataPart("language", "en")
        if (contextHint.isNotEmpty()) bodyBuilder.addFormDataPart("prompt", contextHint)
    }
    language.isNotBlank() -> bodyBuilder.addFormDataPart("language", language.substringBefore("-"))
}
```

### `speech/FasterWhisperSpeechEngine.kt`

`transcribe()` bodyBuilder 加：
```kotlin
bodyBuilder.addFormDataPart("temperature", "0")
```

---

## 🎤 F4：語音後處理

### 新增 `speech/VoicePostProcessor.kt`

```kotlin
package com.snowrealm.yukiboard.speech

import com.snowrealm.yukiboard.claw.ClawClient
import kotlinx.coroutines.*

class VoicePostProcessor(private val clawClient: ClawClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun process(raw: String, language: String, onResult: (String) -> Unit) {
        if (raw.isBlank() || raw.length <= 4) { onResult(raw); return }
        scope.launch {
            try {
                val result = StringBuilder()
                clawClient.streamChat(
                    messages = listOf(mapOf("role" to "user", "content" to buildPrompt(raw, language))),
                    onToken = { result.append(it) },
                    onDone  = { val c = result.toString().trim()
                                withContext(Dispatchers.Main) { onResult(if (c.isNotBlank()) c else raw) } },
                    onError = { withContext(Dispatchers.Main) { onResult(raw) } }
                )
            } catch (_: Exception) { withContext(Dispatchers.Main) { onResult(raw) } }
        }
    }

    private fun buildPrompt(raw: String, language: String) =
        if (language.startsWith("zh")) """
你是語音辨識後處理助手。清理以下語音辨識文字：
1. 移除填充詞（嗯、那個、就是、然後、對對對）
2. 移除明顯重複
3. 說話者改變主意時只保留最後意圖
4. 統一繁體中文
5. 保持原有語氣，不過度潤飾
6. 只輸出處理後文字

原始文字：$raw""".trimIndent()
        else """Clean up this voice transcription:
1. Remove filler words (um, uh, like, you know)
2. Remove repetitions; keep final intent if speaker corrects themselves
3. Keep original tone; output cleaned text only

Raw: $raw""".trimIndent()

    fun destroy() { scope.cancel() }
}
```

### `service/YukiBoardIMEService.kt` 整合

```kotlin
private var voicePostProcessor: VoicePostProcessor? = null
// onCreateInputView()：
voicePostProcessor = VoicePostProcessor(clawClient)
// onFinalResult 回調改為：
onFinalResult = { result ->
    if (voiceFinalHandled) return@startListening
    voiceFinalHandled = true; keyboardView.voiceListeningLocale = null
    if (result.isNotEmpty() && prefs.voicePostProcessEnabled) {
        voiceOverlay?.showFinal("整理中…")
        val lang = if (inputRouter.inputMode == InputMode.ENGLISH) "en" else "zh-TW"
        voicePostProcessor?.process(result, lang) { voiceOverlay?.showFinal(it) }
    } else if (result.isNotEmpty()) voiceOverlay?.showFinal(result)
    else { voiceOverlay?.dismiss(); characterView?.playIdle() }
}
// onDestroy()：
voicePostProcessor?.destroy()
```

### `ui/settings/YukiPreferences.kt`
```kotlin
var voicePostProcessEnabled: Boolean by BoolPref("voice_post_process", true)
```

### `ui/settings/SettingsActivity.kt`
```kotlin
switchRow("語音智慧整理", "自動移除填充詞（消耗少量 AI 額度）",
    prefs.voicePostProcessEnabled) { prefs.voicePostProcessEnabled = it }
```

---

## 🔢 F5：候選字常見字優先排序

### `input/router/InputRouter.kt` — `updateCandidates()` 排序段

在 `boosted` 排序的 score 計算裡加入常用字加分：

```kotlin
val boosted = raw.sortedWith(compareByDescending<String> { word ->
    var score = 0f
    val freq = smartInput.adaptiveDictionary.getFrequency(word)
    if (freq > 0) score += freq + 50

    // ← 新增：基礎常用字加分
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

在 `InputRouter` companion object 加頻率表：

```kotlin
private val COMMON_CHAR_FREQUENCY = mapOf(
    "的" to 40f, "是" to 38f, "在" to 35f, "了" to 34f, "我" to 33f,
    "他" to 32f, "她" to 32f, "你" to 32f, "們" to 30f, "不" to 30f,
    "有" to 29f, "這" to 28f, "那" to 28f, "也" to 27f, "就" to 26f,
    "都" to 25f, "說" to 25f, "要" to 24f, "會" to 24f, "來" to 23f,
    "去" to 23f, "可以" to 28f, "沒有" to 27f, "知道" to 25f,
    "覺得" to 24f, "喜歡" to 23f, "謝謝" to 22f, "好的" to 22f,
    "嗯" to 18f, "哈哈" to 17f, "OK" to 20f, "ok" to 20f,
)
```

---

## 🔀 F6：AUTO 模式中英混合候選

### `input/router/InputRouter.kt` — `updateCandidates()`

在 AUTO 模式下合併兩種引擎的候選（找到 AUTO 模式那段，加在 `candidateManager.update` 之前）：

```kotlin
if (inputMode == InputMode.AUTO && autoQwertyBuffer.isNotEmpty()) {
    val bopoCands = (bopomofoEngine as? ChewingInputEngine)
        ?.getCandidatesForBopomofo(autoBopomofoBuffer.joinToString(""))
        ?: emptyList()
    val engCands = (englishEngine as? EnglishInputEngine)
        ?.getCandidatesForPrefix(autoQwertyBuffer.toString())
        ?: emptyList()

    // 依分數決定誰排前面
    val combined = if (autoEnglishScore >= autoBopomofoScore) {
        engCands + bopoCands
    } else {
        bopoCands + engCands
    }

    val sourceMap = mutableMapOf<String, CandidateSource>()
    engCands.forEach { sourceMap[it] = CandidateSource.PREDICTION }
    bopoCands.forEach { if (it !in sourceMap) sourceMap[it] = CandidateSource.DICTIONARY }
    candidateSourceMap = sourceMap
    candidateManager.update(combined.distinct().take(15))
    return
}
```

---

## 🔍 F7：候選字長按放大鏡

### `ui/keyboard/KeyboardView.kt`

**新增狀態變數（class 頂部）：**
```kotlin
private var candidateLongPressIndex = -1
private var candidateLongPressActive = false
private val CANDIDATE_LONG_PRESS_DELAY = 400L
private val candidateLongPressRunnable = Runnable {
    val idx = hitTestCandidateIndex(candidateLongPressX)
    if (idx >= 0 && idx < candidates.size) {
        candidateLongPressIndex = idx
        candidateLongPressActive = true
        invalidate()
    }
}
```

> 注意：`candidateLongPressX` 和 `CANDIDATE_LONG_PRESS_DELAY` 可能已存在，若已存在不重複宣告。

**`onDraw()` 末尾加（在最後一行之前）：**
```kotlin
if (candidateLongPressActive && candidateLongPressIndex >= 0) {
    drawCandidateMagnifier(canvas, candidateLongPressIndex)
}
```

**新增 `drawCandidateMagnifier()`：**
```kotlin
private fun drawCandidateMagnifier(canvas: Canvas, index: Int) {
    val text = candidates.getOrNull(index) ?: return
    val bubbleW = 80f * density
    val bubbleH = 48f * density
    val cornerR = 10f * density

    // 計算候選字水平中心
    var accum = candFixedLeftCache - candidateScrollX
    for (i in 0 until index) accum += (candWidthsCache.getOrNull(i) ?: 0f) + candGapCache
    val candCx = accum + (candWidthsCache.getOrNull(index) ?: 0f) / 2f
    val bubbleLeft = (candCx - bubbleW / 2f).coerceIn(0f, width - bubbleW)
    val bubbleTop = -bubbleH - 6f * density  // 往上超出候選列（需 clipChildren=false）

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyColorInt }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.candidateSelectedColorInt
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    val rect = RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH)
    canvas.drawRoundRect(rect, cornerR, cornerR, bgPaint)
    canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)

    val tp = Paint(candidatePaint).apply {
        textSize = 22f * density
        textAlign = Paint.Align.CENTER
        color = theme.candidateSelectedColorInt
    }
    canvas.drawText(text, rect.centerX(), rect.centerY() + tp.textSize * 0.35f, tp)

    // 小尾巴
    val tail = android.graphics.Path()
    tail.moveTo(rect.centerX() - 5f * density, rect.bottom)
    tail.lineTo(rect.centerX(), rect.bottom + 5f * density)
    tail.lineTo(rect.centerX() + 5f * density, rect.bottom)
    tail.close()
    canvas.drawPath(tail, bgPaint)
}
```

**`ACTION_UP` 的候選字選字段：在 `candidateTouchActive` 處理開頭加：**
```kotlin
if (candidateTouchActive && candidates.isNotEmpty()) {
    handler.removeCallbacks(candidateLongPressRunnable)
    candidateTouchActive = false

    if (candidateLongPressActive) {
        // 長按結束：只取消放大鏡，不選字
        candidateLongPressActive = false
        candidateLongPressIndex = -1
        invalidate()
        performClick()
        return true
    }
    // ... 原有選字邏輯繼續 ...
```

---

## 執行順序

```
【第一批】輸入正確性
  B1  applyInputResult → setComposingText("", 0)
  B2  數字路徑 → setComposingText("", 0)
  B3  ChewingInputEngine + InputRouter

【第二批】觸控 / UI
  B4  UnitConverterPanelView setWillNotDraw
  B5  hitTestCandidateIndex 用 candidateDownX + ACTION_CANCEL
  B6  expandDownX/Y + 44dp 箭頭
  B7  HandwritingPanelView null-safe
  B8  DecryptPanelView onMeasure
  B9  DecryptPanelView enterInputMode
  B10 resetTouchState + ACTION_UP isLongPressTriggered
  U1  drawKeysInRegion 置中 + KeyboardState 底行調整

【第三批】新功能
  F1  KeyboardSoundEngine.kt + 整合 + 設定頁
  F2  CaseConvertType + drawCaseMenu + onCaseConvert
  F3  三語音引擎改善
  F4  VoicePostProcessor.kt + 整合 + 設定開關
  F5  InputRouter COMMON_CHAR_FREQUENCY 常用字加分
  F6  AUTO 模式中英混合候選
  F7  候選字長按放大鏡
```

---

## 驗證清單

```
輸入：
□ 打 hello 空白送出不重複（LINE 測試）
□ 注音打到一半按數字，注音先送出，數字正常出現
□ 注音連選 5 個字，候選列不消失
□ 注音打「ㄉ」→「的」排第一
□ 注音打「ㄕ」→「是」排前面

觸控：
□ 換算面板 Tab 第一下反應
□ 候選字第一下就選到
□ 展開箭頭邊緣也能按到
□ 展開面板格子第一下就選到
□ 解密面板不佔整個畫面
□ 解密面板可以輸入密碼
□ 每個按鍵第一下有反應
□ 長按候選字 → 放大鏡出現
□ 放開手指 → 放大鏡消失（不選字）
□ 短按 → 正常選字

UI 對齊：
□ QWERTY / ASDF / ZXCV 各排左右縫隙一致
□ 每排高度相同
□ 空格鍵佔 3 格寬度正常
□ 左邊縫隙 = 右邊縫隙
□ 每個鍵盤模式（注音/英文/數字）都整齊

AUTO 模式：
□ 打英文 → 候選列同時有英文和注音候選
□ 打注音 → 候選列同時有中文和英文建議

音效：
□ 數字行高音，ZXCV 低音
□ 三種音色切換
□ 音量可調

大小寫：
□ 長按 Shift 出現 menu
□ 全大 / 全小 / 反轉 正確

語音：
□ 說話停頓 2-3 秒不截斷
□ 說中文輸出繁體
□ 語音整理後填充詞消失
```
