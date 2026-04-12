# YukiBoard — 英文/日文/韓文打錯容錯任務包

> 三種語言各用不同的容錯策略，但核心邏輯相同：
> 找出「按到旁邊的鍵」或「發音相近的符號」，從字典查出正確候選

---

## 三種語言的容錯策略差異

```
英文：QWERTY 物理鍵盤鄰近鍵
  打 helo → 替換 l→k 找 hello
  現有 autocorrect 已用編輯距離，但鄰近鍵沒有加分
  → 改善：替換時優先試鄰近鍵，分數更高

日文：兩層容錯
  層一：羅馬字層（QWERTY 鄰近鍵）
    打 teh → 找 the → て
  層二：假名層（清音↔濁音、語音相似）
    找不到漢字時，嘗試語音相近的假名查字典
    か→が，し→じ，ち→ち，は→ば/ぱ

韓文：두벌식 Jamo 鄰近鍵
  QWERTY 的每個鍵對應一個 Jamo
  按到旁邊的鍵 = 打出旁邊的 Jamo
  直接用 QWERTY 鄰近表映射到 Jamo 鄰近表
```

---

## 一、英文容錯改善

### 現況

`EnglishDictionary.autocorrect()` 已有編輯距離算法，
但替換一個字母的分數統一是 +150，不管是不是鄰近鍵。

打 `helo` 想打 `hello`：
- 現在：替換 `l→l` 插入 = `hello`（+100 插入分）
- 改善後：如果替換的字母是鄰近鍵，額外 +50 分，排序更靠前

### 修改 `input/dictionary/EnglishDictionary.kt`

**更新 `ADJACENT_KEYS` 為完整 8 方向版（替換現有的）：**

```kotlin
private val ADJACENT_KEYS = mapOf(
    'q' to setOf('w', 'a', 's'),
    'w' to setOf('q', 'e', 'a', 's'),
    'e' to setOf('w', 'r', 's', 'd'),
    'r' to setOf('e', 't', 'd', 'f'),
    't' to setOf('r', 'y', 'f', 'g'),
    'y' to setOf('t', 'u', 'g', 'h'),
    'u' to setOf('y', 'i', 'h', 'j'),
    'i' to setOf('u', 'o', 'j', 'k'),
    'o' to setOf('i', 'p', 'k', 'l'),
    'p' to setOf('o', 'l'),
    'a' to setOf('q', 'w', 's', 'z'),
    's' to setOf('a', 'w', 'e', 'd', 'z', 'x'),
    'd' to setOf('s', 'e', 'r', 'f', 'x', 'c'),
    'f' to setOf('d', 'r', 't', 'g', 'c', 'v'),
    'g' to setOf('f', 't', 'y', 'h', 'v', 'b'),
    'h' to setOf('g', 'y', 'u', 'j', 'b', 'n'),
    'j' to setOf('h', 'u', 'i', 'k', 'n', 'm'),
    'k' to setOf('j', 'i', 'o', 'l', 'm'),
    'l' to setOf('k', 'o', 'p'),
    'z' to setOf('a', 's', 'x'),
    'x' to setOf('z', 's', 'd', 'c'),
    'c' to setOf('x', 'd', 'f', 'v'),
    'v' to setOf('c', 'f', 'g', 'b'),
    'b' to setOf('v', 'g', 'h', 'n'),
    'n' to setOf('b', 'h', 'j', 'm'),
    'm' to setOf('n', 'j', 'k')
)
```

**在 `addEditDistance1Candidates()` 的替換分支，鄰近鍵加分：**

```kotlin
// 替換一字元
for (i in word.indices) {
    for (c in 'a'..'z') {
        if (c == word[i]) continue
        val attempt = word.replaceRange(i, i + 1, c.toString())
        if (isWord(attempt)) {
            // 鄰近鍵替換比隨機替換更可能是打錯：+50 加分
            val isAdjacent = ADJACENT_KEYS[word[i]]?.contains(c) == true
            val score = frequencyOf(attempt) + if (isAdjacent) 200 else 150
            out[attempt] = maxOf(out[attempt] ?: 0, score)
        }
    }
}
```

**新增 `fuzzyCorrectWithAdjacency()` 函式（在 autocorrect 之外，專門針對鄰近鍵）：**

```kotlin
/**
 * 鄰近鍵容錯：對每個位置只嘗試鄰近鍵（不是全部 26 個字母）。
 * 比 autocorrect 快，且優先找「打錯旁邊鍵」的情況。
 */
fun fuzzyCorrectWithAdjacency(input: String, maxResults: Int = 5): List<String> {
    if (input.length < 2) return emptyList()
    val lower = input.lowercase()
    val scored = mutableMapOf<String, Int>()

    for (i in lower.indices) {
        val neighbors = ADJACENT_KEYS[lower[i]] ?: continue
        for (neighbor in neighbors) {
            val attempt = lower.replaceRange(i, i + 1, neighbor.toString())
            if (isWord(attempt)) {
                scored[attempt] = maxOf(
                    scored[attempt] ?: 0,
                    frequencyOf(attempt) + 300  // 鄰近鍵分數高於一般編輯距離
                )
            }
            // 同時試插入鄰近鍵
            val inserted = lower.substring(0, i) + neighbor + lower.substring(i)
            if (isWord(inserted)) {
                scored[inserted] = maxOf(scored[inserted] ?: 0, frequencyOf(inserted) + 200)
            }
        }
    }

    return scored.entries
        .sortedByDescending { it.value }
        .take(maxResults)
        .map { it.key }
}
```

**在 `EnglishInputEngine.updateCandidateCache()` 整合：**

找到這段，在 `autocorrect` 之前先試鄰近鍵容錯：

```kotlin
private fun updateCandidateCache() {
    val prefix = buffer.toString()
    if (prefix.isEmpty()) { cachedCandidates = emptyList(); return }

    var results = dictionary.suggest(prefix, if (prefix.length == 1) 5 else 9)

    if (results.isEmpty() && prefix.length >= 2) {
        // 先試鄰近鍵容錯（快且準）
        results = dictionary.fuzzyCorrectWithAdjacency(prefix, 5)
    }

    if (results.isEmpty() && prefix.length >= 2) {
        // 再試完整編輯距離（慢但全面）
        results = dictionary.autocorrect(prefix, 5)
    }

    cachedCandidates = results
}
```

---

## 二、日文容錯

### 兩層容錯設計

```
輸入：teh（想打 the）
層一：羅馬字鄰近鍵 → 試 the，有 → て（正確）

輸入：kanji（想打 かんじ）
層一：正確拼出 → 查字典 → 找到「漢字」等詞

輸入：situ（想打 shitu = しつ = 質）
層一：羅馬字容錯 → 試 shitu → し+つ → 找到「質」等詞
```

### 修改 `input/engine/JapaneseInputEngine.kt`

**新增 QWERTY 鄰近表（加在 companion object）：**

```kotlin
private val ROMAJI_ADJACENT: Map<Char, List<Char>> = mapOf(
    'q' to listOf('w', 'a'),
    'w' to listOf('q', 'e', 'a', 's'),
    'e' to listOf('w', 'r', 's', 'd'),
    'r' to listOf('e', 't', 'd', 'f'),
    't' to listOf('r', 'y', 'f', 'g'),
    'y' to listOf('t', 'u', 'g', 'h'),
    'u' to listOf('y', 'i', 'h', 'j'),
    'i' to listOf('u', 'o', 'j', 'k'),
    'o' to listOf('i', 'p', 'k', 'l'),
    'p' to listOf('o', 'l'),
    'a' to listOf('q', 'w', 's', 'z'),
    's' to listOf('a', 'w', 'e', 'd', 'z', 'x'),
    'd' to listOf('s', 'e', 'r', 'f', 'x', 'c'),
    'f' to listOf('d', 'r', 't', 'g', 'c', 'v'),
    'g' to listOf('f', 't', 'y', 'h', 'v', 'b'),
    'h' to listOf('g', 'y', 'u', 'j', 'b', 'n'),
    'j' to listOf('h', 'u', 'i', 'k', 'n', 'm'),
    'k' to listOf('j', 'i', 'o', 'l', 'm'),
    'l' to listOf('k', 'o', 'p'),
    'z' to listOf('a', 's', 'x'),
    'x' to listOf('z', 's', 'd', 'c'),
    'c' to listOf('x', 'd', 'f', 'v'),
    'v' to listOf('c', 'f', 'g', 'b'),
    'b' to listOf('v', 'g', 'h', 'n'),
    'n' to listOf('b', 'h', 'j', 'm'),
    'm' to listOf('n', 'j', 'k')
)
```

**新增假名語音相似表（加在 companion object）：**

```kotlin
// 假名語音相似表（清音↔濁音、半濁音、母音列相鄰、常見拗音）
private val KANA_SIMILAR: Map<String, List<String>> = mapOf(
    "あ" to listOf("い", "か", "さ"),
    "い" to listOf("あ", "う", "き"),
    "う" to listOf("い", "え", "く"),
    "え" to listOf("う", "お", "け"),
    "お" to listOf("え", "こ", "そ"),
    "か" to listOf("が", "き", "こ"),
    "が" to listOf("か", "ぎ", "ご"),
    "き" to listOf("ぎ", "か", "く"),
    "きゃ" to listOf("きゅ", "ぎゃ", "ちゃ"),
    "きゅ" to listOf("きゃ", "きょ"),
    "きょ" to listOf("きゅ", "ぎょ"),
    "ぎ" to listOf("き", "が", "ぐ"),
    "く" to listOf("ぐ", "き", "け"),
    "ぐ" to listOf("く", "ぎ", "げ"),
    "け" to listOf("げ", "く", "こ"),
    "げ" to listOf("け", "ぐ", "ご"),
    "こ" to listOf("ご", "か", "け"),
    "ご" to listOf("こ", "が", "げ"),
    "さ" to listOf("ざ", "し", "す"),
    "ざ" to listOf("さ", "じ", "ず"),
    "し" to listOf("じ", "さ", "す"),
    "しゃ" to listOf("じゃ", "ちゃ", "しゅ"),
    "しゅ" to listOf("しゃ", "しょ", "じゅ"),
    "しょ" to listOf("しゅ", "じょ", "ちょ"),
    "じ" to listOf("し", "ざ", "ず"),
    "す" to listOf("ず", "し", "せ"),
    "ず" to listOf("す", "じ", "ぜ"),
    "せ" to listOf("ぜ", "す", "そ"),
    "ぜ" to listOf("せ", "ず", "ぞ"),
    "そ" to listOf("ぞ", "さ", "せ"),
    "ぞ" to listOf("そ", "ざ", "ぜ"),
    "た" to listOf("だ", "て", "と"),
    "だ" to listOf("た", "で", "ど"),
    "ち" to listOf("じ", "て", "つ"),
    "ちゃ" to listOf("じゃ", "しゃ", "ちゅ"),
    "ちゅ" to listOf("ちゃ", "ちょ"),
    "ちょ" to listOf("ちゅ", "じょ", "しょ"),
    "つ" to listOf("づ", "ち", "て"),
    "づ" to listOf("つ", "ど"),
    "て" to listOf("で", "た", "と"),
    "で" to listOf("て", "だ", "ど"),
    "と" to listOf("ど", "た", "て"),
    "ど" to listOf("と", "だ", "で"),
    "な" to listOf("に", "ぬ"),
    "に" to listOf("な", "ぬ"),
    "にゃ" to listOf("にゅ"),
    "にゅ" to listOf("にゃ", "にょ"),
    "にょ" to listOf("にゅ"),
    "ぬ" to listOf("な", "に", "ね"),
    "ね" to listOf("な", "ぬ"),
    "の" to listOf("な", "ね"),
    "は" to listOf("ば", "ぱ", "ひ"),
    "ば" to listOf("は", "び", "ぱ"),
    "ぱ" to listOf("は", "ば", "ぴ"),
    "ひ" to listOf("び", "ぴ", "は"),
    "ひゃ" to listOf("びゃ", "ぴゃ"),
    "び" to listOf("ひ", "ぱ", "ぎ"),
    "ぴ" to listOf("ひ", "ぱ", "び"),
    "ふ" to listOf("ぶ", "ぷ", "は"),
    "ぶ" to listOf("ふ", "ぷ"),
    "ぷ" to listOf("ふ", "ぶ"),
    "へ" to listOf("べ", "ぺ"),
    "べ" to listOf("へ", "ぺ"),
    "ぺ" to listOf("へ", "べ"),
    "ほ" to listOf("ぼ", "ぽ"),
    "ぼ" to listOf("ほ", "ぽ"),
    "ぽ" to listOf("ほ", "ぼ"),
    "ま" to listOf("み", "む"),
    "み" to listOf("ま", "む"),
    "みゃ" to listOf("みゅ"),
    "む" to listOf("ま", "み", "め"),
    "め" to listOf("ま", "む"),
    "も" to listOf("ま", "め"),
    "ら" to listOf("り", "る"),
    "り" to listOf("ら", "る"),
    "りゃ" to listOf("りゅ"),
    "りゅ" to listOf("りゃ", "りょ"),
    "りょ" to listOf("りゅ"),
    "る" to listOf("ら", "り", "れ"),
    "れ" to listOf("ら", "る"),
    "ろ" to listOf("ら", "れ"),
)
```

**新增 `fuzzyLookupCandidates()` 函式：**

```kotlin
/**
 * 日文容錯查詢。
 * 當 lookupCandidates(reading) 查無結果時呼叫。
 *
 * 策略一：羅馬字鄰近鍵替換 → 重新轉假名 → 查字典
 * 策略二：已轉的假名做語音相似替換 → 查字典
 */
private fun fuzzyLookupCandidates(originalRomaji: String, reading: String): List<String> {
    val results = mutableListOf<String>()
    val seen = mutableSetOf<String>()

    fun addIfNew(candidates: List<String>) {
        candidates.forEach { if (seen.add(it)) results.add(it) }
    }

    // 策略一：羅馬字層容錯（鄰近鍵替換）
    for (i in originalRomaji.indices) {
        val ch = originalRomaji[i]
        val neighbors = ROMAJI_ADJACENT[ch] ?: continue
        for (neighbor in neighbors) {
            val altRomaji = originalRomaji.substring(0, i) +
                neighbor + originalRomaji.substring(i + 1)
            // 轉換成假名
            val altEngine = JapaneseInputEngine()
            for (c in altRomaji) altEngine.processChar(c.toString())
            val altReading = altEngine.kanaBuffer.toString()
            if (altReading.isNotEmpty() && altReading != reading) {
                addIfNew(lookupCandidates(altReading).take(3))
            }
        }
    }

    // 策略二：假名層容錯（語音相似）
    // 對 reading 的每個假名嘗試相似替換
    var pos = 0
    while (pos < reading.length) {
        // 先嘗試 2 字元拗音（しゃ、ちゅ 等）
        val twoChar = if (pos + 1 < reading.length) reading.substring(pos, pos + 2) else null
        val similarTwo = twoChar?.let { KANA_SIMILAR[it] }
        if (similarTwo != null) {
            for (alt in similarTwo) {
                val altReading = reading.substring(0, pos) + alt + reading.substring(pos + 2)
                addIfNew(lookupCandidates(altReading).take(3))
            }
            pos += 2
            continue
        }
        // 單假名替換
        val oneChar = reading[pos].toString()
        val similar = KANA_SIMILAR[oneChar]
        if (similar != null) {
            for (alt in similar) {
                val altReading = reading.substring(0, pos) + alt + reading.substring(pos + 1)
                addIfNew(lookupCandidates(altReading).take(3))
            }
        }
        pos++
    }

    // 策略三：截短最後一個音節重查
    if (reading.length > 1) {
        val shorter = reading.dropLast(1)
        addIfNew(lookupCandidates(shorter).take(4))
    }

    return results.distinct()
}
```

**在 `processSpace()` 整合容錯：**

找到 `lookupCandidates(reading)` 返回空的分支：

```kotlin
// 原本：
if (candidates.isEmpty()) {
    val text = if (katakanaMode) hiraganaToKatakana(reading) else reading
    kanaBuffer.clear()
    return InputResult.CommitText(text)
}

// 改為：
if (candidates.isEmpty()) {
    // 嘗試容錯查詢
    val fuzzyCandidates = fuzzyLookupCandidates(
        originalRomaji = romajiHistory,  // 見下方說明
        reading = reading
    )
    if (fuzzyCandidates.isNotEmpty()) {
        this.candidates = fuzzyCandidates
        candidateIndex = -1
        isConverting = true
        return InputResult.ComposingUpdated(composingText)
    }
    // 真的找不到 → 輸出假名
    val text = if (katakanaMode) hiraganaToKatakana(reading) else reading
    kanaBuffer.clear()
    return InputResult.CommitText(text)
}
```

**需要保存羅馬字歷史（加一個新的狀態變數）：**

```kotlin
// 在 class 頂部加
private val romajiHistory = StringBuilder()

// 在 processChar() 的 romajiBuffer.append(ch) 之前加
romajiHistory.append(ch)  // 記錄所有輸入過的羅馬字

// 在 reset() 加
romajiHistory.clear()
```

---

## 三、韓文容錯

### 두벌식 Jamo 鄰近表

根據 QWERTY 鍵盤物理位置 + 두벌식 鍵位映射計算。

### 修改 `input/engine/KoreanInputEngine.kt`

**新增 Jamo 鄰近表（加在 companion object）：**

```kotlin
// 두벌식 Jamo 鍵盤鄰近表
// 根據 QWERTY 物理位置 + 두벌식 鍵位映射計算
// 按了旁邊的鍵就輸出旁邊的 Jamo
private val JAMO_ADJACENT: Map<Char, List<Char>> = mapOf(
    'ㄱ' to listOf('ㄷ', 'ㅅ', 'ㅇ', 'ㄹ'),    // r鍵 → 鄰近 e,t,d,f
    'ㄴ' to listOf('ㅁ', 'ㅇ', 'ㅈ', 'ㄷ', 'ㅋ', 'ㅌ'), // s鍵
    'ㄷ' to listOf('ㅈ', 'ㄱ', 'ㄴ', 'ㅇ'),     // e鍵
    'ㄹ' to listOf('ㅇ', 'ㅎ', 'ㄱ', 'ㅅ', 'ㅊ', 'ㅍ'), // f鍵
    'ㅁ' to listOf('ㄴ', 'ㅂ', 'ㅈ', 'ㅋ'),     // a鍵
    'ㅂ' to listOf('ㅈ', 'ㅁ'),                // q鍵
    'ㅅ' to listOf('ㄱ', 'ㅛ', 'ㄹ', 'ㅎ'),     // t鍵
    'ㅇ' to listOf('ㄴ', 'ㄹ', 'ㄷ', 'ㄱ', 'ㅌ', 'ㅊ'), // d鍵
    'ㅈ' to listOf('ㅂ', 'ㄷ', 'ㅁ', 'ㄴ'),     // w鍵
    'ㅊ' to listOf('ㅌ', 'ㅍ', 'ㅇ', 'ㄹ'),     // c鍵
    'ㅋ' to listOf('ㅌ', 'ㅁ', 'ㄴ'),           // z鍵
    'ㅌ' to listOf('ㅋ', 'ㅊ', 'ㄴ', 'ㅇ'),     // x鍵
    'ㅍ' to listOf('ㅊ', 'ㅠ', 'ㄹ', 'ㅎ'),     // v鍵
    'ㅎ' to listOf('ㄹ', 'ㅗ', 'ㅅ', 'ㅛ', 'ㅍ', 'ㅠ'), // g鍵
    // 母音
    'ㅏ' to listOf('ㅓ', 'ㅣ', 'ㅑ', 'ㅐ', 'ㅡ'), // k鍵
    'ㅐ' to listOf('ㅑ', 'ㅔ', 'ㅏ', 'ㅣ'),     // o鍵
    'ㅑ' to listOf('ㅕ', 'ㅐ', 'ㅓ', 'ㅏ'),     // i鍵
    'ㅓ' to listOf('ㅗ', 'ㅏ', 'ㅕ', 'ㅑ', 'ㅜ', 'ㅡ'), // j鍵
    'ㅔ' to listOf('ㅐ', 'ㅣ'),                // p鍵
    'ㅕ' to listOf('ㅛ', 'ㅑ', 'ㅗ', 'ㅓ'),     // u鍵
    'ㅗ' to listOf('ㅎ', 'ㅓ', 'ㅛ', 'ㅕ', 'ㅠ', 'ㅜ'), // h鍵
    'ㅛ' to listOf('ㅅ', 'ㅕ', 'ㅎ', 'ㅗ'),     // y鍵
    'ㅜ' to listOf('ㅠ', 'ㅡ', 'ㅗ', 'ㅓ'),     // n鍵
    'ㅠ' to listOf('ㅍ', 'ㅜ', 'ㅎ', 'ㅗ'),     // b鍵
    'ㅡ' to listOf('ㅜ', 'ㅓ', 'ㅏ'),           // m鍵
    'ㅣ' to listOf('ㅏ', 'ㅐ', 'ㅔ'),           // l鍵
)
```

**新增 `fuzzyCorrectKorean()` 函式：**

```kotlin
/**
 * 韓文容錯：嘗試把輸入中的每個 Jamo 替換成鄰近 Jamo，
 * 查詞庫找到匹配的詞。
 */
fun fuzzyCorrectKorean(input: String, maxResults: Int = 5): List<String> {
    if (input.isEmpty()) return emptyList()
    val results = mutableSetOf<String>()

    // 分解輸入字串的每個字的 Jamo
    for (charIdx in input.indices) {
        val ch = input[charIdx]
        if (ch < '\uAC00' || ch > '\uD7A3') continue  // 不是韓文音節

        // 分解音節
        val code = ch - '\uAC00'
        val chosungIdx = code / (JUNGSUNG_COUNT * JONGSUNG_COUNT)
        val jungsungIdx = (code % (JUNGSUNG_COUNT * JONGSUNG_COUNT)) / JONGSUNG_COUNT
        val jongsungIdx = code % JONGSUNG_COUNT

        val chosung = CHOSUNG_TABLE[chosungIdx]
        val jungsung = JUNGSUNG_TABLE[jungsungIdx]
        val jongsung = if (jongsungIdx > 0) JONGSUNG_TABLE[jongsungIdx] else null

        // 嘗試替換初聲
        JAMO_ADJACENT[chosung]?.forEach { altChosung ->
            val altIdx = CHOSUNG_TABLE.indexOf(altChosung)
            if (altIdx >= 0) {
                val altCode = altIdx * JUNGSUNG_COUNT * JONGSUNG_COUNT +
                    jungsungIdx * JONGSUNG_COUNT + jongsungIdx
                val altChar = ('\uAC00' + altCode)
                val altWord = input.substring(0, charIdx) + altChar + input.substring(charIdx + 1)
                if (KOREAN_DICTIONARY.any { it.startsWith(altWord) }) {
                    KOREAN_DICTIONARY.filter { it.startsWith(altWord) }.take(3)
                        .forEach { results.add(it) }
                }
            }
        }

        // 嘗試替換中聲
        JAMO_ADJACENT[jungsung]?.forEach { altJungsung ->
            val altIdx = JUNGSUNG_TABLE.indexOf(altJungsung)
            if (altIdx >= 0) {
                val altCode = chosungIdx * JUNGSUNG_COUNT * JONGSUNG_COUNT +
                    altIdx * JONGSUNG_COUNT + jongsungIdx
                val altChar = ('\uAC00' + altCode)
                val altWord = input.substring(0, charIdx) + altChar + input.substring(charIdx + 1)
                if (KOREAN_DICTIONARY.any { it.startsWith(altWord) }) {
                    KOREAN_DICTIONARY.filter { it.startsWith(altWord) }.take(3)
                        .forEach { results.add(it) }
                }
            }
        }
    }

    return results.toList().take(maxResults)
}
```

**在 `getCandidates()` 整合容錯：**

```kotlin
override fun getCandidates(): List<String> {
    val current = composingText
    if (current.isEmpty()) return emptyList()

    // 精確前綴匹配
    val exact = KOREAN_DICTIONARY.filter { it.startsWith(current) && it != current }.take(8)
    if (exact.isNotEmpty()) return exact

    // 查無結果 → 嘗試 Jamo 鄰近鍵容錯
    return fuzzyCorrectKorean(current, 5)
}
```

---

## 四、三種語言容錯的本質差異

| 語言 | 容錯層次 | 策略 | 難度 |
|------|---------|------|------|
| 英文 | 字母層 | QWERTY 鄰近鍵 + 編輯距離加權 | 最簡單，已有基礎 |
| 拼音 | 字母層 | QWERTY 鄰近鍵替換，重新查拼音字典 | 簡單（見前一任務包）|
| 日文 | 羅馬字層 + 假名層 | 兩層容錯，先試羅馬字再試假名 | 最複雜 |
| 韓文 | Jamo 層 | QWERTY→Jamo 映射，直接替換音節 | 中等 |
| 注音 | 符號層 | 鍵盤物理位置（見前一任務包）| 中等 |

---

## 五、執行順序

```
1. 英文（最簡單）：
   EnglishDictionary.kt → 更新 ADJACENT_KEYS + addEditDistance1Candidates 鄰近加分
   EnglishDictionary.kt → 新增 fuzzyCorrectWithAdjacency()
   EnglishInputEngine.kt → updateCandidateCache() 先試 fuzzyCorrectWithAdjacency

2. 韓文（中等）：
   KoreanInputEngine.kt → 新增 JAMO_ADJACENT
   KoreanInputEngine.kt → 新增 fuzzyCorrectKorean()
   KoreanInputEngine.kt → getCandidates() 整合容錯

3. 日文（最複雜）：
   JapaneseInputEngine.kt → 新增 ROMAJI_ADJACENT + KANA_SIMILAR
   JapaneseInputEngine.kt → 新增 romajiHistory 狀態變數
   JapaneseInputEngine.kt → 新增 fuzzyLookupCandidates()
   JapaneseInputEngine.kt → processSpace() 整合容錯

每步 Build 測試
```

---

## 六、驗證清單

```
英文：
□ 打 helo → 候選出現 hello（鄰近鍵 l 多了一個）
□ 打 teh → 候選出現 the（t-e 交換）
□ 打 wrold → 候選出現 world
□ 打 frend → 候選出現 friend

日文：
□ 打 kinja → 候選出現 きんじゃ相關詞（n→m 鄰近）
□ 打 suto → 候選出現 すと / 捨都等（清音相近）
□ 打 hisasiburi → 候選出現 久しぶり（s→sh 模糊）

韓文：
□ 打 안녀（想打 안녕）→ 候選出現 안녕 / 안녕하세요
□ 母音打錯（ㅓ→ㅏ）→ 容錯候選出現
```
