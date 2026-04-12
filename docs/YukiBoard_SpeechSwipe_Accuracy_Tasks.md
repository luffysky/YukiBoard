# YukiBoard — 語音辨識準確度 + 滑動輸入準確度改善

---

## 診斷結果

### 語音辨識（Whisper）問題

```
問題一：每 2.5 秒才送一次 partial，說話中間沒有即時回饋
問題二：只傳一個 prompt「以下是繁體中文語音內容。」太少資訊
         → Whisper 無法根據上下文判斷用詞
問題三：靜音偵測：錄到底才送，使用者不知道什麼時候停止
問題四：model 寫死 "whisper-1"，沒有 faster-whisper 選項
問題五：多語混說（中英混）時沒有 prompt 引導
```

### 滑動輸入問題

```
注音滑動：
問題一：SWIPE_DISTANCE_THRESHOLD = 20dp，太高，小手機很難觸發
問題二：滑動路徑記錄到聲調鍵（ˊˇˋ˙），會干擾解碼
問題三：alternatives 只產生 6 個鄰近方案，覆蓋率不夠
問題四：segmentSyllables 切音節邏輯在滑動路徑上失準
         （滑動時會經過多餘的鍵，切出錯誤的音節邊界）

英文滑動：
問題一：swipeMatchScore 的 lengthFit 用 seq.size * 0.7 估算詞長
         → 長詞（7字母以上）分數很低，打 "through" 出不來
問題二：collectWords 收集 2000 個詞，太多，大量無關候選浪費時間
問題三：首字母完全錯的詞就淘汰，但滑動起點常常就是最不準的
```

---

## 修復一：Whisper 語音辨識準確度

### 修改 `speech/WhisperSpeechEngine.kt`

**Step 1：加入上下文 prompt（最有效）**

Whisper 的 `prompt` 參數可以傳入最多 224 個 token 的上下文，
它會根據這個修正辨識結果（用詞、標點、名詞）。

在 `transcribe()` 函式，把 `language: String` 改成也接 `contextPrompt: String`：

```kotlin
private fun transcribe(
    wavData: ByteArray,
    language: String,
    contextPrompt: String = ""
): String? {
    val bodyBuilder = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file", "audio.wav",
            wavData.toRequestBody("audio/wav".toMediaType())
        )
        .addFormDataPart("model", model)
        .addFormDataPart("response_format", "json")

    if (language == "zh") {
        bodyBuilder.addFormDataPart("language", "zh")
        // ← 改善：prompt 加入更多繁體中文引導 + 上下文
        val prompt = buildString {
            append("以下是繁體中文，使用台灣用語，不使用簡體字。")
            if (contextPrompt.isNotBlank()) {
                append("上下文：「")
                append(contextPrompt.takeLast(100))  // 最後 100 字
                append("」。")
            }
        }
        bodyBuilder.addFormDataPart("prompt", prompt)
    } else {
        // 英文或其他語言也傳入上下文
        if (contextPrompt.isNotBlank()) {
            bodyBuilder.addFormDataPart("prompt", contextPrompt.takeLast(100))
        }
    }

    // ... 其餘不變
}
```

**Step 2：在 startListening 取得輸入框上下文**

`startListening` 加入 `contextText` 參數：

```kotlin
override fun startListening(
    locale: String,
    contextText: String = "",           // ← 新增
    onPartialResult: (String) -> Unit,
    onFinalResult: (String) -> Unit,
    onError: (Int) -> Unit
) {
    // ...
    recordingJob = scope.launch {
        // ...
        val finalText = transcribe(finalWav, whisperLocale, contextText)
        // ...
    }
}
```

**Step 3：IMEService 傳入上下文**

在 `YukiBoardIMEService.kt` 找到 `speechEngine?.startListening(...)` 這段，加入上下文：

```kotlin
val contextText = currentInputConnection
    ?.getTextBeforeCursor(200, 0)
    ?.toString() ?: ""

speechEngine?.startListening(
    locale = localeHint,
    contextText = contextText,   // ← 加這個
    onPartialResult = { partial ->
        currentInputConnection?.setComposingText(partial, 1)
    },
    // ... 其餘不變
)
```

**Step 4：靜音偵測 + 自動停止**

在 `recordingJob` 的 while loop 裡加入靜音偵測：

```kotlin
var silenceFrames = 0
val SILENCE_THRESHOLD = 200  // 音量閾值
val MAX_SILENCE_FRAMES = 25  // 約 1.5 秒靜音後自動停

while (isActive && isListening) {
    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
    if (read > 0) {
        // 計算這段音頻的 RMS 音量
        var sum = 0L
        for (i in 0 until read) sum += buffer[i] * buffer[i]
        val rms = Math.sqrt(sum.toDouble() / read).toInt()

        if (rms < SILENCE_THRESHOLD) {
            silenceFrames++
            if (silenceFrames >= MAX_SILENCE_FRAMES) {
                // 靜音太久，自動停止錄音
                isListening = false
                break
            }
        } else {
            silenceFrames = 0  // 有聲音就重置
        }

        // PCM 寫入
        for (i in 0 until read) {
            pcmStream.write(buffer[i].toInt() and 0xFF)
            pcmStream.write((buffer[i].toInt() shr 8) and 0xFF)
        }
        // ... 其餘不變
    }
}
```

**Step 5：partial 改成 1.5 秒（原本 2.5 秒太慢）**

```kotlin
private val partialIntervalMs = 1500L   // 原本 2500，改 1500
```

**Step 6：SpeechInputEngine 介面補 contextText**

修改 `speech/SpeechInputEngine.kt`：

```kotlin
interface SpeechInputEngine {
    val isListening: Boolean

    fun startListening(
        locale: String,
        contextText: String = "",         // ← 新增（有預設值，向下相容）
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Int) -> Unit
    )
    // ... 其餘不變
}
```

AndroidSpeechEngine 也加上 `contextText` 參數（忽略即可，只是讓介面一致）：

```kotlin
// AndroidSpeechEngine.startListening() 加 contextText 參數但不使用
// Android 原生 SpeechRecognizer 已有自己的上下文機制
```

---

## 修復二：注音滑動準確度

### 修改 `ui/keyboard/KeyboardView.kt`

**降低滑動觸發門檻（20dp → 12dp）：**

```kotlin
// 原本：
private val SWIPE_DISTANCE_THRESHOLD = 20f * density  // 20dp

// 改為：
private val SWIPE_DISTANCE_THRESHOLD = 12f * density  // 12dp，更容易觸發
```

### 修改 `input/router/InputRouter.kt`

**swipeSuggest 加入滑動路徑清理邏輯：**

注音滑動時，路徑裡常常混入多餘的鍵（路過的聲調鍵、重複的按鍵）。
加入更強的清理：

```kotlin
fun swipeSuggest(keyLabels: List<String>): List<String> {
    if (keyLabels.size < 2) return emptyList()

    val isBopomofo = keyLabels.any { it.length == 1 && it[0] in '\u3105'..'\u312F' }

    return if (isBopomofo) {
        val symbols = keyLabels.filter { l ->
            if (l.length != 1) return@filter false
            val ch = l[0]
            // 只保留注音符號，丟掉聲調鍵（滑動時不需要聲調）
            ch in '\u3105'..'\u312F'
        }
        // 去連續重複
        val deduped = mutableListOf<String>()
        for (s in symbols) {
            if (deduped.lastOrNull() != s) deduped.add(s)
        }
        if (deduped.isEmpty()) return emptyList()
        swipeRanker.rankBopomofo(deduped)
    } else {
        val chars = keyLabels.mapNotNull { it.firstOrNull()?.lowercaseChar() }
            .filter { it in 'a'..'z' }  // 只保留字母
        swipeRanker.rankEnglish(chars)
    }
}
```

### 修改 `input/swipe/SwipeCandidateRanker.kt`

**擴大 alternatives 到 10 個 + 加入雙位置替換：**

```kotlin
fun rankBopomofo(bopomofoSymbols: List<String>, maxResults: Int = 12): List<String> {
    if (chewingEngine == null || bopomofoSymbols.isEmpty()) return emptyList()

    val scored = mutableMapOf<String, Float>()

    // 1. 主序列（最高權重）
    val primary = chewingEngine.lookupByBopomofoString(bopomofoSymbols.joinToString(""))
    primary.forEachIndexed { i, word ->
        scored[word] = (scored[word] ?: 0f) + (20f - i * 0.5f).coerceAtLeast(5f)
    }

    // 2. 單位置鄰近替換（擴大到 10 個）
    val alternatives = decoder.alternatives(bopomofoSymbols, 10)  // 原本 4，改 10
    for (altSeq in alternatives) {
        val altResults = chewingEngine.lookupByBopomofoString(altSeq.joinToString(""))
        altResults.take(8).forEachIndexed { i, word ->
            scored[word] = (scored[word] ?: 0f) + (10f - i).coerceAtLeast(1f)
        }
    }

    // 3. ← 新增：雙位置替換（兩個位置同時錯的情況）
    if (bopomofoSymbols.size >= 2) {
        for (i in bopomofoSymbols.indices) {
            val neighbors1 = decoder.getAdjacentKeys(bopomofoSymbols[i])
            for (n1 in neighbors1.take(2)) {
                if (n1.firstOrNull()?.let { it !in '\u3105'..'\u312F' } == true) continue
                val alt1 = bopomofoSymbols.toMutableList()
                alt1[i] = n1
                // 再對另一個位置做替換
                for (j in bopomofoSymbols.indices) {
                    if (j == i) continue
                    val neighbors2 = decoder.getAdjacentKeys(bopomofoSymbols[j])
                    for (n2 in neighbors2.take(2)) {
                        if (n2.firstOrNull()?.let { it !in '\u3105'..'\u312F' } == true) continue
                        val alt2 = alt1.toMutableList()
                        alt2[j] = n2
                        val r = chewingEngine.lookupByBopomofoString(alt2.joinToString(""))
                        r.take(3).forEachIndexed { k, word ->
                            scored[word] = (scored[word] ?: 0f) + (3f - k).coerceAtLeast(0.5f)
                        }
                    }
                }
            }
        }
    }

    // 4. 前綴截短查詢（使用者沒滑完就放開）
    // 例如滑了 ㄋㄧㄏ 但想打「你好」，把最後一個音節去掉重查
    if (bopomofoSymbols.size >= 3) {
        val shortened = bopomofoSymbols.dropLast(1)
        val shortenedResult = chewingEngine.lookupByBopomofoString(shortened.joinToString(""))
        shortenedResult.take(5).forEachIndexed { i, word ->
            scored[word] = (scored[word] ?: 0f) + (4f - i).coerceAtLeast(0.5f)
        }
    }

    // 5. 自適應字典加分
    for ((word, score) in scored.toMap()) {
        val freq = smartInput.adaptiveDictionary.getFrequency(word)
        if (freq > 0) scored[word] = score + freq * 2f + 10f
    }

    return scored.entries
        .sortedByDescending { it.value }
        .take(maxResults)
        .map { it.key }
}
```

---

## 修復三：英文滑動準確度

### 修改 `input/dictionary/EnglishDictionary.kt`

**改善 swipeMatchScore 的 lengthFit 計算：**

原本的 `seq.size * 0.7` 估算詞長太保守，長詞嚴重吃虧。

```kotlin
private fun swipeMatchScore(word: String, seq: List<Char>, seqSet: Set<Char>): Double {
    var seqIdx = 0
    var matched = 0
    var adjacentUsed = 0
    var skipped = 0   // ← 新增：記錄跳過多少路徑上的鍵

    for (ch in word) {
        var found = false
        val startIdx = seqIdx
        var tempIdx = seqIdx
        while (tempIdx < seq.size) {
            if (seq[tempIdx] == ch) {
                skipped += (tempIdx - seqIdx)   // 記錄跳過的鍵
                seqIdx = tempIdx + 1
                matched++
                found = true
                break
            }
            val adj = ADJACENT_KEYS[ch]
            if (adj != null && seq[tempIdx] in adj) {
                skipped += (tempIdx - seqIdx)
                seqIdx = tempIdx + 1
                matched++
                adjacentUsed++
                found = true
                break
            }
            tempIdx++
        }
        if (!found) {
            if (ch in seqSet) {
                matched++
                adjacentUsed++
            } else {
                val adj = ADJACENT_KEYS[ch]
                if (adj != null && adj.any { it in seqSet }) {
                    adjacentUsed += 2
                } else {
                    return 0.0
                }
            }
        }
    }

    val matchRatio = matched.toDouble() / word.length

    // ← 改善：lengthFit 用更寬鬆的範圍
    // 一般滑動路徑長度 ≈ 詞長 * 1.2 ~ 2.5（會經過一些多餘的鍵）
    val expectedSeqLen = word.length * 1.8   // 期望路徑長度
    val lengthFit = 1.0 - (Math.abs(seq.size - expectedSeqLen) / expectedSeqLen)
        .coerceIn(0.0, 1.0) * 0.5   // 最多扣 50%

    val adjacentPenalty = adjacentUsed * 0.08   // 降低鄰近鍵懲罰
    val skipPenalty = (skipped.toDouble() / seq.size).coerceIn(0.0, 0.3)   // 跳過太多懲罰

    return (matchRatio * 0.55 + lengthFit * 0.35 + 0.1 - adjacentPenalty - skipPenalty)
        .coerceAtLeast(0.0)
}
```

**改善首字母容錯（滑動起點常常不準）：**

```kotlin
fun swipeSuggest(keySequence: List<Char>, maxResults: Int = 8): List<String> {
    if (keySequence.size < 2) return emptyList()
    val seq = keySequence.map { it.lowercaseChar() }
    val firstChar = seq.first()
    val lastChar = seq.last()
    val seqSet = seq.toSet()

    // ← 改善：首字母鄰近範圍擴大（起點最不準）
    val possibleFirsts = (ADJACENT_KEYS[firstChar] ?: emptySet()) + firstChar +
        (ADJACENT_KEYS[firstChar]?.flatMap { ADJACENT_KEYS[it] ?: emptySet() }?.toSet() ?: emptySet())

    // 尾字母正常
    val possibleLasts = (ADJACENT_KEYS[lastChar] ?: emptySet()) + lastChar

    val candidates = mutableListOf<Triple<String, Int, Double>>()

    for (startChar in possibleFirsts) {
        val startNode = root.children[startChar] ?: continue
        val allWords = mutableListOf<Pair<String, Int>>()
        collectWords(startNode, allWords, 800)   // 原本 2000，降到 800 提高效能

        for ((word, freq) in allWords) {
            if (word.length < 2) continue
            if (word.last() !in possibleLasts) continue
            // ← 改善：長度限制更寬鬆
            if (word.length > seq.size + 3 || word.length < (seq.size * 0.25).toInt()) continue

            val score = swipeMatchScore(word, seq, seqSet)
            if (score > 0.0) {
                candidates.add(Triple(word, freq, score))
            }
        }
    }

    return candidates
        .sortedWith(compareByDescending<Triple<String, Int, Double>> { it.third }
            .thenByDescending { it.second })
        .take(maxResults)
        .map { it.first }
}
```

### 修改 `input/swipe/SwipeCandidateRanker.kt`

**英文滑動的 rankEnglish 也加入前綴截短容錯：**

```kotlin
fun rankEnglish(chars: List<Char>, maxResults: Int = 12): List<String> {
    if (chars.size < 2) return emptyList()

    val scored = mutableMapOf<String, Float>()

    // 1. 完整序列
    val swipeSuggestions = smartInput.swipeSuggestEnglish(chars)
    swipeSuggestions.forEachIndexed { i, word ->
        scored[word] = (scored[word] ?: 0f) + (15f - i * 0.5f).coerceAtLeast(3f)
    }

    // 2. ← 新增：去掉最後 1-2 個字母重查（使用者沒滑完就放開）
    if (chars.size >= 4) {
        val shorter = chars.dropLast(1)
        smartInput.swipeSuggestEnglish(shorter).take(4).forEachIndexed { i, word ->
            scored[word] = (scored[word] ?: 0f) + (6f - i).coerceAtLeast(1f)
        }
    }

    // 3. ← 新增：去掉最前面 1 個字母重查（起點滑偏）
    if (chars.size >= 4) {
        val shifted = chars.drop(1)
        smartInput.swipeSuggestEnglish(shifted).take(4).forEachIndexed { i, word ->
            scored[word] = (scored[word] ?: 0f) + (5f - i).coerceAtLeast(1f)
        }
    }

    // 4. 自適應字典加分
    for ((word, score) in scored.toMap()) {
        val freq = smartInput.adaptiveDictionary.getFrequency(word)
        if (freq > 0) scored[word] = score + freq * 2f + 5f
    }

    return scored.entries
        .sortedByDescending { it.value }
        .take(maxResults)
        .map { it.key }
}
```

---

## 修復四：學習機制（用過就記住）

現在的 `AdaptiveDictionary` 已有 `getFrequency(word)` 和學習機制，
但滑動選字後沒有觸發學習。

### 修改 `service/YukiBoardIMEService.kt`

在 `onCandidateSelectListener` 的滑動路徑加入學習：

```kotlin
if (swipeInputPending && index in swipeInputCandidates.indices) {
    val text = swipeInputCandidates[index]
    val ic = currentInputConnection
    ic?.finishComposingText()
    inputRouter.reset()
    ic?.commitText(text, 1)
    swipeInputPending = false
    swipeInputCandidates = emptyList()
    keyboardView.updateComposingText("")
    keyboardView.updateCandidates(emptyList())

    // ← 新增：讓系統記住使用者選了這個詞
    inputRouter.smartInput.adaptiveDictionary.recordUsage(text)
    inputRouter.smartInput.onWordCommitted(text, currentInputEditorInfo?.packageName)

    return@setOnCandidateSelectListener
}
```

---

## 執行順序

```
1. 語音辨識：
   SpeechInputEngine.kt → 介面加 contextText 參數
   WhisperSpeechEngine.kt → 加上下文 prompt + 靜音偵測 + partial 改 1500ms
   AndroidSpeechEngine.kt → 介面對齊（contextText 加上但不使用）
   YukiBoardIMEService.kt → startListening 傳入 getTextBeforeCursor 上下文

2. 注音滑動：
   KeyboardView.kt → SWIPE_DISTANCE_THRESHOLD 改 12dp
   InputRouter.kt → swipeSuggest 過濾掉聲調鍵
   SwipeCandidateRanker.kt → alternatives 改 10 個 + 雙位置替換 + 前綴截短

3. 英文滑動：
   EnglishDictionary.kt → swipeMatchScore lengthFit 改善 + 首字母擴大容錯
   EnglishDictionary.kt → collectWords 降到 800 個（效能）
   SwipeCandidateRanker.kt → rankEnglish 加前綴/後綴截短容錯

4. 學習機制：
   YukiBoardIMEService.kt → 滑動選字後觸發 adaptiveDictionary.recordUsage
```

---

## 驗證

```
語音辨識：
□ 說「你好世界」→ 辨識出「你好世界」不是「你好 WORLD」
□ 靜音 1.5 秒後自動停止（不需要再按按鈕）
□ 說「幫我寫信給王小明」→ 不會認成「王小命」（上下文有「寫信」）

注音滑動：
□ 從 ㄋ 滑到 ㄧ 再到 ㄏ 到 ㄠ → 出現「你好」
□ 輕輕滑動（不需要很大力）就觸發
□ 稍微偏到旁邊的鍵也能辨識出正確的詞

英文滑動：
□ 從 t 滑過 h-r-o-u-g-h → 出現 "through"
□ 從 h 滑到 e-l-l-o → 出現 "hello"
□ 第二次選同一個詞 → 排名上升（學習生效）
```
