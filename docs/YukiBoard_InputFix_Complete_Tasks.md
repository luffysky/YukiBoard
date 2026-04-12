# YukiBoard — 注音輸入全面改版 + 拼音容錯任務包

> 合併：YukiBoard_BopomofoFix_Tasks + YukiBoard_BopomofoAdjacency_Full + 拼音新增
> 基於 YukiBoard-main__4_.zip

---

## 目錄

1. [注音鍵盤完整 8 方向鄰近表](#一注音鍵盤完整-8-方向鄰近表)
2. [拼音鍵盤完整 8 方向鄰近表](#二拼音鍵盤完整-8-方向鄰近表)
3. [空白鍵先聲調再選字](#三空白鍵先聲調再選字)
4. [英文點候選字直接輸出](#四英文點候選字直接輸出)
5. [多音節聲調排序](#五多音節聲調排序)
6. [展開候選選字後自動關閉](#六展開候選選字後自動關閉)
7. [注音打錯容錯預測](#七注音打錯容錯預測)
8. [拼音打錯容錯預測](#八拼音打錯容錯預測)
9. [執行順序與驗證](#九執行順序與驗證)

---

## 一、注音鍵盤完整 8 方向鄰近表

根據大千式鍵盤實際座標計算，每個符號包含上下左右 + 四個斜角。

### 替換 `input/swipe/BopomofoSwipeDecoder.kt` 的 ADJACENT

找到 `companion object` 裡的 `private val ADJACENT`，整段替換：

```kotlin
// 大千式鍵盤完整 8 方向鄰近表（含斜角）
// 供滑動輸入容錯使用，含聲調鍵（手指滑動時會經過）
private val ADJACENT: Map<String, List<String>> = mapOf(
    // Row 0（數字列）
    "ㄅ" to listOf("ㄉ", "ˇ", "ㄆ", "ㄊ"),
    "ㄉ" to listOf("ㄅ", "ˇ", "ˋ", "ㄆ", "ㄊ", "ㄍ"),
    "ˇ"  to listOf("ㄉ", "ˋ", "ㄊ", "ㄍ"),
    "ˋ"  to listOf("ˇ", "ㄓ", "ˊ", "ㄍ", "ㄐ", "ㄔ"),
    "ㄓ" to listOf("ˋ", "ˊ", "˙", "ㄐ", "ㄔ", "ㄗ"),
    "ˊ"  to listOf("ㄓ", "˙", "ㄔ", "ㄗ", "ㄧ"),
    "˙"  to listOf("ˊ", "ㄚ", "ㄗ", "ㄧ", "ㄛ"),
    "ㄚ" to listOf("˙", "ㄞ", "ㄧ", "ㄛ", "ㄟ"),
    "ㄞ" to listOf("ㄚ", "ㄢ", "ㄛ", "ㄟ", "ㄣ"),
    "ㄢ" to listOf("ㄞ", "ㄟ", "ㄣ"),
    // Row 1（QWERTY 列）
    "ㄆ" to listOf("ㄅ", "ㄉ", "ˇ", "ㄊ", "ㄇ", "ㄋ"),
    "ㄊ" to listOf("ㄅ", "ㄉ", "ˇ", "ˋ", "ㄆ", "ㄍ", "ㄇ", "ㄋ", "ㄎ"),
    "ㄍ" to listOf("ㄉ", "ˇ", "ˋ", "ㄊ", "ㄐ", "ㄋ", "ㄎ", "ㄑ"),
    "ㄐ" to listOf("ˇ", "ˋ", "ㄓ", "ㄍ", "ㄔ", "ㄎ", "ㄑ", "ㄕ"),
    "ㄔ" to listOf("ˋ", "ㄓ", "ˊ", "ㄐ", "ㄗ", "ㄑ", "ㄕ", "ㄘ"),
    "ㄗ" to listOf("ㄓ", "ˊ", "˙", "ㄔ", "ㄧ", "ㄕ", "ㄘ", "ㄨ"),
    "ㄧ" to listOf("ˊ", "˙", "ㄚ", "ㄗ", "ㄛ", "ㄘ", "ㄨ", "ㄜ"),
    "ㄛ" to listOf("˙", "ㄚ", "ㄞ", "ㄧ", "ㄟ", "ㄨ", "ㄜ", "ㄠ"),
    "ㄟ" to listOf("ㄚ", "ㄞ", "ㄢ", "ㄛ", "ㄣ", "ㄜ", "ㄠ", "ㄤ"),
    "ㄣ" to listOf("ㄞ", "ㄢ", "ㄟ", "ㄠ", "ㄤ"),
    // Row 2（ASDF 列）
    "ㄇ" to listOf("ㄆ", "ㄊ", "ㄋ", "ㄈ", "ㄌ"),
    "ㄋ" to listOf("ㄆ", "ㄊ", "ㄍ", "ㄇ", "ㄎ", "ㄈ", "ㄌ", "ㄏ"),
    "ㄎ" to listOf("ㄊ", "ㄍ", "ㄐ", "ㄋ", "ㄑ", "ㄌ", "ㄏ", "ㄒ"),
    "ㄑ" to listOf("ㄍ", "ㄐ", "ㄔ", "ㄎ", "ㄕ", "ㄏ", "ㄒ", "ㄖ"),
    "ㄕ" to listOf("ㄐ", "ㄔ", "ㄗ", "ㄑ", "ㄘ", "ㄒ", "ㄖ", "ㄙ"),
    "ㄘ" to listOf("ㄔ", "ㄗ", "ㄧ", "ㄕ", "ㄨ", "ㄖ", "ㄙ", "ㄩ"),
    "ㄨ" to listOf("ㄗ", "ㄧ", "ㄛ", "ㄘ", "ㄜ", "ㄙ", "ㄩ", "ㄝ"),
    "ㄜ" to listOf("ㄧ", "ㄛ", "ㄟ", "ㄨ", "ㄠ", "ㄩ", "ㄝ", "ㄡ"),
    "ㄠ" to listOf("ㄛ", "ㄟ", "ㄣ", "ㄜ", "ㄤ", "ㄝ", "ㄡ", "ㄥ"),
    "ㄤ" to listOf("ㄟ", "ㄣ", "ㄠ", "ㄡ", "ㄥ"),
    // Row 3（ZXCV 列）
    "ㄈ" to listOf("ㄇ", "ㄋ", "ㄌ", "ㄦ"),
    "ㄌ" to listOf("ㄇ", "ㄋ", "ㄎ", "ㄈ", "ㄏ", "ㄦ"),
    "ㄏ" to listOf("ㄋ", "ㄎ", "ㄑ", "ㄌ", "ㄒ"),
    "ㄒ" to listOf("ㄎ", "ㄑ", "ㄕ", "ㄏ", "ㄖ"),
    "ㄖ" to listOf("ㄑ", "ㄕ", "ㄘ", "ㄒ", "ㄙ"),
    "ㄙ" to listOf("ㄕ", "ㄘ", "ㄨ", "ㄖ", "ㄩ"),
    "ㄩ" to listOf("ㄘ", "ㄨ", "ㄜ", "ㄙ", "ㄝ"),
    "ㄝ" to listOf("ㄨ", "ㄜ", "ㄠ", "ㄩ", "ㄡ"),
    "ㄡ" to listOf("ㄜ", "ㄠ", "ㄤ", "ㄝ", "ㄥ"),
    "ㄥ" to listOf("ㄠ", "ㄤ", "ㄡ"),
    // Row 4
    "ㄦ" to listOf("ㄈ", "ㄌ"),
)
```

### 替換 `input/engine/ChewingInputEngine.kt` 的 BOPOMOFO_ADJACENT

注音容錯專用（不含聲調鍵，預測時只替換注音符號）：

```kotlin
// 注音容錯用（只含注音符號鄰居，去掉聲調鍵）
private val BOPOMOFO_ADJACENT: Map<String, List<String>> = mapOf(
    "ㄅ" to listOf("ㄉ", "ㄆ", "ㄊ"),
    "ㄉ" to listOf("ㄅ", "ㄆ", "ㄊ", "ㄍ"),
    "ㄓ" to listOf("ㄐ", "ㄔ", "ㄗ"),
    "ㄚ" to listOf("ㄞ", "ㄧ", "ㄛ", "ㄟ"),
    "ㄞ" to listOf("ㄚ", "ㄢ", "ㄛ", "ㄟ", "ㄣ"),
    "ㄢ" to listOf("ㄞ", "ㄟ", "ㄣ"),
    "ㄆ" to listOf("ㄅ", "ㄉ", "ㄊ", "ㄇ", "ㄋ"),
    "ㄊ" to listOf("ㄅ", "ㄉ", "ㄆ", "ㄍ", "ㄇ", "ㄋ", "ㄎ"),
    "ㄍ" to listOf("ㄉ", "ㄊ", "ㄐ", "ㄋ", "ㄎ", "ㄑ"),
    "ㄐ" to listOf("ㄓ", "ㄍ", "ㄔ", "ㄎ", "ㄑ", "ㄕ"),
    "ㄔ" to listOf("ㄓ", "ㄐ", "ㄗ", "ㄑ", "ㄕ", "ㄘ"),
    "ㄗ" to listOf("ㄓ", "ㄔ", "ㄧ", "ㄕ", "ㄘ", "ㄨ"),
    "ㄧ" to listOf("ㄚ", "ㄗ", "ㄛ", "ㄘ", "ㄨ", "ㄜ"),
    "ㄛ" to listOf("ㄚ", "ㄞ", "ㄧ", "ㄟ", "ㄨ", "ㄜ", "ㄠ"),
    "ㄟ" to listOf("ㄚ", "ㄞ", "ㄢ", "ㄛ", "ㄣ", "ㄜ", "ㄠ", "ㄤ"),
    "ㄣ" to listOf("ㄞ", "ㄢ", "ㄟ", "ㄠ", "ㄤ"),
    "ㄇ" to listOf("ㄆ", "ㄊ", "ㄋ", "ㄈ", "ㄌ"),
    "ㄋ" to listOf("ㄆ", "ㄊ", "ㄍ", "ㄇ", "ㄎ", "ㄈ", "ㄌ", "ㄏ"),
    "ㄎ" to listOf("ㄊ", "ㄍ", "ㄐ", "ㄋ", "ㄑ", "ㄌ", "ㄏ", "ㄒ"),
    "ㄑ" to listOf("ㄍ", "ㄐ", "ㄔ", "ㄎ", "ㄕ", "ㄏ", "ㄒ", "ㄖ"),
    "ㄕ" to listOf("ㄐ", "ㄔ", "ㄗ", "ㄑ", "ㄘ", "ㄒ", "ㄖ", "ㄙ"),
    "ㄘ" to listOf("ㄔ", "ㄗ", "ㄧ", "ㄕ", "ㄨ", "ㄖ", "ㄙ", "ㄩ"),
    "ㄨ" to listOf("ㄗ", "ㄧ", "ㄛ", "ㄘ", "ㄜ", "ㄙ", "ㄩ", "ㄝ"),
    "ㄜ" to listOf("ㄧ", "ㄛ", "ㄟ", "ㄨ", "ㄠ", "ㄩ", "ㄝ", "ㄡ"),
    "ㄠ" to listOf("ㄛ", "ㄟ", "ㄣ", "ㄜ", "ㄤ", "ㄝ", "ㄡ", "ㄥ"),
    "ㄤ" to listOf("ㄟ", "ㄣ", "ㄠ", "ㄡ", "ㄥ"),
    "ㄈ" to listOf("ㄇ", "ㄋ", "ㄌ", "ㄦ"),
    "ㄌ" to listOf("ㄇ", "ㄋ", "ㄎ", "ㄈ", "ㄏ", "ㄦ"),
    "ㄏ" to listOf("ㄋ", "ㄎ", "ㄑ", "ㄌ", "ㄒ"),
    "ㄒ" to listOf("ㄎ", "ㄑ", "ㄕ", "ㄏ", "ㄖ"),
    "ㄖ" to listOf("ㄑ", "ㄕ", "ㄘ", "ㄒ", "ㄙ"),
    "ㄙ" to listOf("ㄕ", "ㄘ", "ㄨ", "ㄖ", "ㄩ"),
    "ㄩ" to listOf("ㄘ", "ㄨ", "ㄜ", "ㄙ", "ㄝ"),
    "ㄝ" to listOf("ㄨ", "ㄜ", "ㄠ", "ㄩ", "ㄡ"),
    "ㄡ" to listOf("ㄜ", "ㄠ", "ㄤ", "ㄝ", "ㄥ"),
    "ㄥ" to listOf("ㄠ", "ㄤ", "ㄡ"),
    "ㄦ" to listOf("ㄈ", "ㄌ"),
)
```

---

## 二、拼音鍵盤完整 8 方向鄰近表

根據 QWERTY 鍵盤實際座標計算（含鍵盤標準偏移：Row2 右偏 0.5，Row3 右偏 1.0）。

### 新增到 `input/engine/PinyinInputEngine.kt` companion object

```kotlin
// QWERTY 鍵盤完整鄰近表（含斜角，距離 ≤ 1.5 鍵寬）
// 根據鍵盤實際偏移計算（ASDF 排相對 QWERTY 排偏右 0.5 鍵）
private val PINYIN_ADJACENT: Map<Char, List<Char>> = mapOf(
    'a' to listOf('s', 'q', 'w', 'z'),       // row1 col0
    'b' to listOf('v', 'n', 'g', 'h'),        // row2 col4
    'c' to listOf('x', 'v', 'd', 'f'),        // row2 col2
    'd' to listOf('s', 'f', 'e', 'r', 'x', 'c'),  // row1 col2
    'e' to listOf('w', 'r', 's', 'd'),        // row0 col2
    'f' to listOf('d', 'g', 'r', 't', 'c', 'v'),  // row1 col3
    'g' to listOf('f', 'h', 't', 'y', 'v', 'b'),  // row1 col4
    'h' to listOf('g', 'j', 'y', 'u', 'b', 'n'),  // row1 col5
    'i' to listOf('u', 'o', 'j', 'k'),        // row0 col7
    'j' to listOf('h', 'k', 'u', 'i', 'n', 'm'),  // row1 col6
    'k' to listOf('j', 'l', 'i', 'o', 'm'),   // row1 col7
    'l' to listOf('k', 'o', 'p'),             // row1 col8
    'm' to listOf('n', 'j', 'k'),             // row2 col6
    'n' to listOf('b', 'm', 'h', 'j'),        // row2 col5
    'o' to listOf('i', 'p', 'k', 'l'),        // row0 col8
    'p' to listOf('o', 'l'),                  // row0 col9
    'q' to listOf('w', 'a'),                  // row0 col0
    'r' to listOf('e', 't', 'd', 'f'),        // row0 col3
    's' to listOf('a', 'd', 'w', 'e', 'z', 'x'),  // row1 col1
    't' to listOf('r', 'y', 'f', 'g'),        // row0 col4
    'u' to listOf('y', 'i', 'h', 'j'),        // row0 col6
    'v' to listOf('c', 'b', 'f', 'g'),        // row2 col3
    'w' to listOf('q', 'e', 'a', 's'),        // row0 col1
    'x' to listOf('z', 'c', 's', 'd'),        // row2 col1
    'y' to listOf('t', 'u', 'g', 'h'),        // row0 col5
    'z' to listOf('x', 'a', 's'),             // row2 col0
)
```

---

## 三、空白鍵先聲調再選字

### 修改 `input/engine/ChewingInputEngine.kt` 的 `processSpace()`

```kotlin
override fun processSpace(): InputResult {
    if (bopomofoBuffer.isEmpty() && selectedBuffer.isEmpty()) {
        return InputResult.CommitText(" ")
    }
    if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
        return selectCandidateAt(0)
    }
    // buffer 有注音 → 查字典進入選字模式（候選字已按一聲→二聲排序）
    updateCandidatesFromBuffer()
    if (currentCandidates.isNotEmpty()) {
        isSelectingCandidate = true
        return InputResult.ComposingUpdated(composingText)
    }
    // 查無候選 → 嘗試容錯預測
    val fuzzyResults = fuzzyPredictFromBuffer(bopomofoBuffer.toString())
    if (fuzzyResults.isNotEmpty()) {
        currentCandidates = fuzzyResults
        isSelectingCandidate = true
        return InputResult.ComposingUpdated(composingText)
    }
    val text = composingText
    reset()
    return InputResult.CommitText(text)
}
```

---

## 四、英文點候選字直接輸出

### 確認 `input/router/InputRouter.kt` 英文選字分支

```kotlin
else -> {
    val selected = candidateManager.candidates.getOrNull(index)
        ?: candidates.getOrNull(index)
    if (selected != null) {
        activeEngine.reset()
        candidateManager.clear()
        onTextCommitted(selected)
        smartInput.onCandidateHit()
        return InputResult.CommitText(selected)  // 直接 commit，不加空格
    }
    return InputResult.Idle
}
```

---

## 五、多音節聲調排序

### 修改 `input/engine/ChewingInputEngine.kt`

在 `updateCandidatesFromBuffer()` 的多音節分支：

```kotlin
if (syllables.size >= 2) {
    val rawResults = lookupMultiSyllable(syllables)
    currentCandidates = if (rawResults.isNotEmpty()) {
        sortByToneMatch(rawResults, syllables)
    } else {
        fuzzyPredictFromBuffer(bopomofoBuffer.toString())
    }
}
```

新增 `sortByToneMatch()` 函式：

```kotlin
private fun sortByToneMatch(candidates: List<String>, syllables: List<String>): List<String> {
    if (syllables.isEmpty()) return candidates
    val strippedSyllables = syllables.map { it.trimEnd { c -> c in toneMarks } }

    data class Scored(val word: String, val score: Int)

    val scored = candidates.map { word ->
        var score = 0
        if (word.length == syllables.size) score += 2
        word.forEachIndexed { i, ch ->
            if (i >= syllables.size) return@forEachIndexed
            val inputStripped = strippedSyllables[i]
            val inputTone = syllables[i].lastOrNull()?.takeIf { it in toneMarks }
            val charReadings = reverseIndex[ch.toString()] ?: return@forEachIndexed
            for (reading in charReadings) {
                val readingStripped = reading.trimEnd { it in toneMarks }
                val readingTone = reading.lastOrNull()?.takeIf { it in toneMarks }
                if (readingStripped == inputStripped) {
                    score += 1
                    if (inputTone == null || readingTone == inputTone) { score += 2; break }
                }
            }
        }
        Scored(word, score)
    }
    return scored.sortedByDescending { it.score }.map { it.word }
}
```

---

## 六、展開候選選字後自動關閉

### 修改 `ui/keyboard/KeyboardView.kt`

找到展開面板選字區段（約 line 1948），改一行：

```kotlin
if (index in allCandidates.indices) {
    onCandidateSpeakListener?.invoke(allCandidates[index])
    onCandidateSelectListener?.invoke(index)
    isCandidateExpanded = false      // ← 原本是「保持展開」，改為關閉
    expandScrollOffset = 0f
    invalidate()
    performClick()
    return true
}
```

---

## 七、注音打錯容錯預測

### 在 `input/engine/ChewingInputEngine.kt` 新增

```kotlin
/**
 * 注音打錯容錯預測。
 * 當 buffer 查無候選時呼叫，嘗試鄰近符號替換找出可能的正確輸入。
 */
private fun fuzzyPredictFromBuffer(bopomofo: String): List<String> {
    if (bopomofo.isEmpty()) return emptyList()
    val results = linkedSetOf<String>()
    val syllables = segmentSyllables(bopomofo)
    val lastSyllable = syllables.lastOrNull() ?: return emptyList()
    val lastSylStripped = lastSyllable.trimEnd { it in toneMarks }

    // 策略一：最後一個音節的每個符號換成鄰近符號
    for (i in lastSylStripped.indices) {
        val ch = lastSylStripped[i].toString()
        val neighbors = BOPOMOFO_ADJACENT[ch] ?: continue
        for (neighbor in neighbors) {
            val altSyllable = lastSylStripped.substring(0, i) +
                neighbor + lastSylStripped.substring(i + 1)
            val altBopomofo = if (syllables.size > 1) {
                syllables.dropLast(1).joinToString("") + altSyllable
            } else altSyllable
            val altSyls = segmentSyllables(altBopomofo)
            val cands = if (altSyls.size >= 2) lookupMultiSyllable(altSyls)
                        else lookupCandidates(altBopomofo)
            results.addAll(cands.take(3))
        }
    }

    // 策略二：刪掉最後一個音節重查
    if (bopomofo.length > 1) {
        val shorter = bopomofo.dropLast(1)
        val shorterSyls = segmentSyllables(shorter)
        val shorterCands = if (shorterSyls.size >= 2) lookupMultiSyllable(shorterSyls)
                           else lookupCandidates(shorter)
        results.addAll(shorterCands.take(5))
    }

    // 策略三：只用聲母查詞
    val initialOnly = syllables.joinToString("") { syl ->
        syl.trimEnd { it in toneMarks }.firstOrNull()?.toString() ?: ""
    }
    if (initialOnly.length >= 2) {
        phraseDict[initialOnly]?.let { results.addAll(it.take(5)) }
    }

    return results.toList()
}
```

同時在 `updateCandidatesFromBuffer()` 加入觸發：

```kotlin
// 單音節查無結果時觸發
if (regular.isEmpty()) {
    currentCandidates = fuzzyPredictFromBuffer(bopomofo)
    isSelectingCandidate = false
    return
}
```

---

## 八、拼音打錯容錯預測

### 修改 `input/engine/PinyinInputEngine.kt`

新增 `fuzzyPredictPinyin()` 函式（加在 `fuzzyMatch()` 下方）：

```kotlin
/**
 * 拼音打錯容錯預測。
 * 當 pinyinBuffer 查無候選時，用鍵盤鄰近鍵替換找出可能的正確拼音。
 *
 * 策略：
 * 1. 對最後一個音節的每個字母，替換成 QWERTY 鄰近鍵
 * 2. 刪掉最後 1-2 個字母重查
 * 3. 現有 fuzzyMatch（zh/z、n/l 等模糊拼音）
 */
private fun fuzzyPredictPinyin(): List<String> {
    val input = pinyinBuffer.toString()
    if (input.isEmpty()) return emptyList()

    val results = mutableListOf<String>()
    val seenCandidates = mutableSetOf<String>()

    fun addIfNew(candidates: List<String>, limit: Int = 4) {
        candidates.take(limit).forEach { if (seenCandidates.add(it)) results.add(it) }
    }

    // 策略一：替換輸入的每個字母為 QWERTY 鄰近鍵
    for (i in input.indices) {
        val ch = input[i]
        val neighbors = PINYIN_ADJACENT[ch] ?: continue
        for (neighbor in neighbors) {
            val altInput = input.substring(0, i) + neighbor + input.substring(i + 1)
            val altSyllables = segmentPinyin(altInput)
            if (altSyllables.isNotEmpty()) {
                val isValid = altSyllables.all { isValidSyllable(it) }
                if (isValid) {
                    val altFull = altSyllables.joinToString("")
                    // 直接查字典
                    PINYIN_DICT[altFull]?.let { addIfNew(it) }
                    // 查詞組
                    if (altSyllables.size >= 2) {
                        val key = altSyllables.take(2).joinToString("")
                        PHRASE_DICT[key]?.let { addIfNew(it) }
                    }
                    // 查第一個音節的字
                    PINYIN_DICT[altSyllables[0]]?.let { addIfNew(it, 2) }
                }
            }
        }
    }

    // 策略二：刪掉最後 1 個字母
    if (input.length >= 2) {
        val shorter = input.dropLast(1)
        val shorterSyls = segmentPinyin(shorter)
        if (shorterSyls.isNotEmpty()) {
            PINYIN_DICT[shorter]?.let { addIfNew(it) }
            if (shorterSyls.size >= 2) {
                PHRASE_DICT[shorterSyls.take(2).joinToString("")]?.let { addIfNew(it) }
            }
            PINYIN_DICT[shorterSyls[0]]?.let { addIfNew(it, 3) }
        }
    }

    // 策略三：刪掉最後 2 個字母
    if (input.length >= 3) {
        val shorter2 = input.dropLast(2)
        val syl2 = segmentPinyin(shorter2)
        if (syl2.isNotEmpty()) {
            PINYIN_DICT[syl2[0]]?.let { addIfNew(it, 3) }
        }
    }

    // 策略四：現有的模糊拼音（zh↔z, n↔l, ang↔an 等）
    if (syllables.isNotEmpty()) {
        fuzzyMatch(syllables[0]).forEach { fuzzySyl ->
            PINYIN_DICT[fuzzySyl]?.let { addIfNew(it, 3) }
        }
    }

    return results.distinct()
}
```

### 在 `updateCandidates()` 整合容錯

找到 `updateCandidates()` 的末尾 `currentCandidates = candidates.distinct().take(30)`，改為：

```kotlin
val finalCandidates = candidates.distinct()
currentCandidates = if (finalCandidates.isEmpty()) {
    // 查無結果 → 容錯預測
    fuzzyPredictPinyin()
} else {
    finalCandidates.take(30)
}
```

### 在 `processSpace()` 也整合容錯

```kotlin
override fun processSpace(): InputResult {
    if (pinyinBuffer.isEmpty()) return InputResult.CommitText(" ")

    if (currentCandidates.isNotEmpty()) {
        val selected = currentCandidates[0]
        return commitCandidate(selected)
    }

    // 容錯：嘗試用鄰近鍵預測
    val fuzzy = fuzzyPredictPinyin()
    if (fuzzy.isNotEmpty()) {
        currentCandidates = fuzzy
        return InputResult.ComposingUpdated(composingText)
    }

    val text = pinyinBuffer.toString()
    reset()
    return InputResult.CommitText(text)
}
```

---

## 九、執行順序與驗證

### 執行順序

```
Step 1：替換鄰近表
  BopomofoSwipeDecoder.kt → 替換 ADJACENT（含聲調鍵）
  ChewingInputEngine.kt → 替換 BOPOMOFO_ADJACENT（純注音）
  PinyinInputEngine.kt → 新增 PINYIN_ADJACENT

Step 2：最簡單改動
  KeyboardView.kt → 展開面板選字後關閉（一行）

Step 3：英文直接 commit
  InputRouter.kt → 英文選字分支確認不加空格

Step 4：注音改動
  ChewingInputEngine.kt → processSpace() 改版
  ChewingInputEngine.kt → sortByToneMatch() 新增
  ChewingInputEngine.kt → fuzzyPredictFromBuffer() 新增
  ChewingInputEngine.kt → updateCandidatesFromBuffer() 整合容錯

Step 5：拼音改動
  PinyinInputEngine.kt → fuzzyPredictPinyin() 新增
  PinyinInputEngine.kt → updateCandidates() 整合容錯
  PinyinInputEngine.kt → processSpace() 整合容錯

每步 Build 測試
```

### 驗證清單

```
注音：
□ 打 ㄅㄨ + 空白 → 出現「不補捕布步…」（一聲排最前）
□ 打 ㄅㄨˊㄧㄠˋ → 「不要」排在最前面
□ 打 ㄅㄨㄞ（不存在）→ 出現「杯貝備北…」（容錯）
□ 打 ㄋㄧㄏ（截短）→ 出現「你好」等

拼音：
□ 打 nuhao（u打成n旁邊的鍵）→ 出現「你好」（容錯）
□ 打 heo（hello少兩個字母）→ 出現相近候選
□ 打 nihao → 出現「你好」
□ 打 zhongguo → 出現「中國」

英文：
□ 點候選字 → 直接輸入，不需要按空白

展開面板：
□ 點 ▼ → 點任意字 → 面板自動收起
```

---

## 附：兩種鄰近表的差異

| 用途 | 檔案 | 包含聲調鍵 | 說明 |
|------|------|-----------|------|
| 滑動輸入 | `BopomofoSwipeDecoder.ADJACENT` | 是 | 手指滑動會路過聲調鍵 |
| 注音容錯 | `ChewingInputEngine.BOPOMOFO_ADJACENT` | 否 | 只替換注音符號本身 |
| 拼音容錯 | `PinyinInputEngine.PINYIN_ADJACENT` | 不適用 | QWERTY 字母鍵鄰近 |
