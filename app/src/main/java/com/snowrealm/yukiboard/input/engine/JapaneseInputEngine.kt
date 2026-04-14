package com.snowrealm.yukiboard.input.engine

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.input.router.InputResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 日本語輸入引擎。
 *
 * 功能：
 * - ローマ字→ひらがな即時変換（全標準音節 + 拗音 + 促音 + 撥音）
 * - ひらがな⇔カタカナ切替
 * - 漢字候補表示（スペースキーで変換、辞書 ~5000 語）
 * - 候補選択（selectCandidateAt）
 *
 * 設計：
 * - romajiBuffer: 未確定のローマ字（例: "k"）
 * - kanaBuffer: 確定済みの仮名（例: "かき"）
 * - composingText = kanaBuffer + romajiBufferの表示
 * - Space で漢字変換モードへ遷移、候補リストを生成
 * - Enter で仮名のまま確定
 */
class JapaneseInputEngine(context: Context? = null) : InputEngine {

    private val kanjiDictionary: Map<String, List<String>> = loadKanjiDictionary(context)

    // ── State ──────────────────────────────────────────────

    /** Accumulated kana that has been fully converted from romaji. */
    private val kanaBuffer = StringBuilder()

    /** Pending romaji characters that have not yet formed a complete kana. */
    private val romajiBuffer = StringBuilder()

    /** Full romaji input history for fuzzy correction. */
    private val romajiHistory = StringBuilder()

    /** Whether we are currently in kanji-conversion mode (space was pressed). */
    private var isConverting = false

    /** Current candidate list during conversion. */
    private var candidates: List<String> = emptyList()

    /** Index of the currently highlighted candidate (-1 = none). */
    private var candidateIndex = -1

    /** When true, output katakana instead of hiragana. */
    var katakanaMode = false
        private set

    override val composingText: String
        get() {
            if (isConverting && candidates.isNotEmpty()) {
                val idx = if (candidateIndex >= 0) candidateIndex else 0
                return candidates.getOrElse(idx) { kanaBuffer.toString() }
            }
            val kana = kanaBuffer.toString()
            val pending = romajiBuffer.toString()
            val display = kana + pending
            return if (katakanaMode) hiraganaToKatakana(display) else display
        }

    // ── Public API ─────────────────────────────────────────

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle

        // If we were in conversion mode, commit the selected candidate first
        if (isConverting) {
            commitConversion()
        }

        val ch = char.lowercase()
        // Only process ASCII letters and a few special romaji-relevant chars
        if (ch.length == 1 && ch[0] in 'a'..'z') {
            romajiHistory.append(ch)
            romajiBuffer.append(ch)
            processRomaji()
        } else {
            // Non-letter: flush any pending romaji as-is, then commit this char
            flushRomajiAsIs()
            kanaBuffer.append(ch)
        }

        return InputResult.ComposingUpdated(composingText)
    }

    override fun processDelete(): InputResult {
        if (isConverting) {
            // Cancel conversion, go back to kana editing
            isConverting = false
            candidates = emptyList()
            candidateIndex = -1
            return InputResult.ComposingUpdated(composingText)
        }

        if (romajiBuffer.isNotEmpty()) {
            romajiBuffer.deleteCharAt(romajiBuffer.lastIndex)
        } else if (kanaBuffer.isNotEmpty()) {
            kanaBuffer.deleteCharAt(kanaBuffer.lastIndex)
        }

        return if (kanaBuffer.isEmpty() && romajiBuffer.isEmpty()) {
            InputResult.ComposingCleared
        } else {
            InputResult.ComposingUpdated(composingText)
        }
    }

    override fun processSpace(): InputResult {
        if (kanaBuffer.isEmpty() && romajiBuffer.isEmpty()) {
            return InputResult.CommitText(" ")
        }

        // Flush any remaining romaji
        resolveTrailingN()
        flushRomajiAsIs()

        if (!isConverting) {
            // Enter conversion mode
            val reading = kanaBuffer.toString()
            candidates = lookupCandidates(reading)
            if (candidates.isEmpty()) {
                // Try fuzzy correction before giving up
                val fuzzyCandidates = fuzzyLookupCandidates(romajiHistory.toString(), reading)
                if (fuzzyCandidates.isNotEmpty()) {
                    candidates = fuzzyCandidates
                    candidateIndex = -1
                    isConverting = true
                    return InputResult.ComposingUpdated(composingText)
                }
                // No kanji found — commit the kana as-is
                val text = if (katakanaMode) hiraganaToKatakana(reading) else reading
                kanaBuffer.clear()
                return InputResult.CommitText(text)
            }
            isConverting = true
            candidateIndex = 0
            return InputResult.ComposingUpdated(composingText)
        } else {
            // Already converting — cycle to next candidate
            if (candidates.isNotEmpty()) {
                candidateIndex = (candidateIndex + 1) % candidates.size
            }
            return InputResult.ComposingUpdated(composingText)
        }
    }

    override fun processEnter(): InputResult {
        if (isConverting && candidates.isNotEmpty()) {
            val idx = if (candidateIndex >= 0) candidateIndex else 0
            val text = candidates.getOrElse(idx) { kanaBuffer.toString() }
            resetState()
            return InputResult.CommitText(text)
        }

        if (kanaBuffer.isEmpty() && romajiBuffer.isEmpty()) {
            return InputResult.Idle
        }

        resolveTrailingN()
        flushRomajiAsIs()
        val kana = kanaBuffer.toString()
        val text = if (katakanaMode) hiraganaToKatakana(kana) else kana
        resetState()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (kanaBuffer.isEmpty() && romajiBuffer.isEmpty() && !isConverting) {
            return InputResult.Idle
        }

        if (isConverting && candidates.isNotEmpty()) {
            val idx = if (candidateIndex >= 0) candidateIndex else 0
            val text = candidates.getOrElse(idx) { kanaBuffer.toString() }
            resetState()
            return InputResult.CommitText(text)
        }

        resolveTrailingN()
        flushRomajiAsIs()
        val kana = kanaBuffer.toString()
        val text = if (katakanaMode) hiraganaToKatakana(kana) else kana
        resetState()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> {
        // 変換モード中はそのまま candidates を返す
        if (isConverting) return candidates

        // 入力中：仮名バッファから即時候補を生成
        val reading = kanaBuffer.toString()
        if (reading.isEmpty()) return emptyList()

        val live = mutableListOf<String>()
        // 完全一致
        kanjiDictionary[reading]?.let { live.addAll(it.take(6)) }
        // 部分一致（最後の2-4文字で辞書引き）
        if (live.size < 5 && reading.length >= 2) {
            for (len in minOf(reading.length, 4) downTo 2) {
                val tail = reading.substring(reading.length - len)
                kanjiDictionary[tail]?.forEach { k ->
                    val compound = reading.substring(0, reading.length - len) + k
                    if (compound !in live && live.size < 8) live.add(compound)
                }
                if (live.size >= 5) break
            }
        }
        return live
    }

    override fun reset() {
        resetState()
    }

    /**
     * Select a candidate by index and commit it.
     */
    fun selectCandidateAt(index: Int): InputResult {
        // 変換モード：从 candidates 选字
        if (isConverting) {
            val text = candidates.getOrNull(index) ?: return InputResult.Idle
            resetState()
            return InputResult.CommitText(text)
        }
        // 入力中：从即時候補（getCandidates）选字
        val liveCandidates = getCandidates()
        val text = liveCandidates.getOrNull(index) ?: return InputResult.Idle
        resetState()
        return InputResult.CommitText(text)
    }

    /**
     * Toggle between hiragana and katakana output mode.
     */
    fun toggleKatakana() {
        katakanaMode = !katakanaMode
    }

    /**
     * Explicitly set kana mode.
     */
    fun setKatakanaMode(enabled: Boolean) {
        katakanaMode = enabled
    }

    // ── Romaji → Kana Conversion ───────────────────────────

    /**
     * Attempt to convert the romaji buffer into kana.
     * Greedy: try longest match first (up to 4 chars), then shorter.
     */
    private fun processRomaji() {
        while (romajiBuffer.isNotEmpty()) {
            val buf = romajiBuffer.toString()
            var matched = false

            // Try 4, 3, 2, 1 char prefixes
            val maxLen = minOf(buf.length, 4)
            for (len in maxLen downTo 1) {
                val prefix = buf.substring(0, len)
                val kana = ROMAJI_TO_KANA[prefix]
                if (kana != null) {
                    kanaBuffer.append(kana)
                    romajiBuffer.delete(0, len)
                    matched = true
                    break
                }
            }

            if (matched) continue

            // Double consonant → っ (sokuon)
            if (buf.length >= 2 && buf[0] == buf[1]
                && buf[0] != 'a' && buf[0] != 'i' && buf[0] != 'u'
                && buf[0] != 'e' && buf[0] != 'o' && buf[0] != 'n'
            ) {
                kanaBuffer.append('っ')
                romajiBuffer.deleteCharAt(0)
                continue
            }

            // 'n' followed by a consonant (not 'y', not 'n', not vowel) → ん
            if (buf.length >= 2 && buf[0] == 'n') {
                val next = buf[1]
                if (next != 'a' && next != 'i' && next != 'u' && next != 'e' && next != 'o'
                    && next != 'y' && next != 'n'
                ) {
                    kanaBuffer.append('ん')
                    romajiBuffer.deleteCharAt(0)
                    continue
                }
            }

            // No match possible yet — check if any table entry starts with current buffer.
            // If not, flush the first char as-is to avoid stuck state.
            val couldMatch = ROMAJI_TO_KANA.keys.any { it.startsWith(buf.take(1)) || buf.take(1) == "n" }
            if (!couldMatch) {
                kanaBuffer.append(buf[0])
                romajiBuffer.deleteCharAt(0)
                continue
            }

            // Otherwise, wait for more input
            break
        }
    }

    /** If romaji buffer ends with 'n', treat it as ん. */
    private fun resolveTrailingN() {
        if (romajiBuffer.isNotEmpty() && romajiBuffer.toString() == "n") {
            kanaBuffer.append('ん')
            romajiBuffer.clear()
        }
    }

    /** Flush remaining romaji buffer content directly into kana buffer. */
    private fun flushRomajiAsIs() {
        if (romajiBuffer.isNotEmpty()) {
            kanaBuffer.append(romajiBuffer)
            romajiBuffer.clear()
        }
    }

    /** Commit the currently selected conversion candidate and reset. */
    private fun commitConversion() {
        if (candidates.isNotEmpty() && candidateIndex >= 0) {
            // Discard — the caller should have already committed via selectCandidateAt.
            // Here we just clear conversion state so new input starts fresh.
        }
        isConverting = false
        candidates = emptyList()
        candidateIndex = -1
        kanaBuffer.clear()
    }

    private fun resetState() {
        kanaBuffer.clear()
        romajiBuffer.clear()
        romajiHistory.clear()
        isConverting = false
        candidates = emptyList()
        candidateIndex = -1
    }

    // ── Kana Utilities ─────────────────────────────────────

    private fun hiraganaToKatakana(text: String): String {
        return buildString(text.length) {
            for (ch in text) {
                if (ch in '\u3041'..'\u3096') {
                    append(ch + 0x60) // Hiragana → Katakana offset
                } else {
                    append(ch)
                }
            }
        }
    }

    // ── Kanji Lookup ───────────────────────────────────────

    /**
     * Look up kanji/word candidates for the given hiragana reading.
     * Tries exact match first, then progressively shorter prefixes,
     * and always includes the original kana as the last candidate.
     */
    private fun lookupCandidates(reading: String): List<String> {
        val results = mutableListOf<String>()

        // Exact match
        kanjiDictionary[reading]?.let { results.addAll(it) }

        // If no exact match, try substring matches for compound words
        if (results.isEmpty() && reading.length > 1) {
            // Try splitting into known segments
            val segmented = segmentAndLookup(reading)
            if (segmented.isNotEmpty()) {
                results.addAll(segmented)
            }
        }

        // Always include the hiragana and katakana forms
        val hiragana = reading
        val katakana = hiraganaToKatakana(reading)
        if (hiragana !in results) results.add(hiragana)
        if (katakana !in results) results.add(katakana)

        return results
    }

    /**
     * Simple greedy segmentation: try to match longest known readings from left,
     * combine their first kanji candidates.
     */
    private fun segmentAndLookup(reading: String): List<String> {
        val parts = mutableListOf<List<String>>()
        var pos = 0
        while (pos < reading.length) {
            var found = false
            val maxLen = minOf(reading.length - pos, 8)
            for (len in maxLen downTo 1) {
                val segment = reading.substring(pos, pos + len)
                val entry = kanjiDictionary[segment]
                if (entry != null) {
                    parts.add(entry)
                    pos += len
                    found = true
                    break
                }
            }
            if (!found) {
                // Single kana, no kanji match
                parts.add(listOf(reading[pos].toString()))
                pos++
            }
        }

        if (parts.size <= 1) return emptyList()

        // Generate a few combinations from the top candidates of each segment
        val results = mutableListOf<String>()
        // First: all top-1 candidates combined
        results.add(parts.joinToString("") { it.first() })
        // Then a few more variations using second candidates where available
        for (i in parts.indices) {
            if (parts[i].size > 1) {
                val variant = parts.mapIndexed { idx, list ->
                    if (idx == i) list[1] else list.first()
                }.joinToString("")
                if (variant !in results) results.add(variant)
            }
            if (results.size >= 6) break
        }
        return results
    }

    // ── Fuzzy Correction ──────────────────────────────────

    /**
     * 日文容錯查詢。
     * 當 lookupCandidates(reading) 查無結果時呼叫。
     *
     * 策略一：羅馬字鄰近鍵替換 → 重新轉假名 → 查字典
     * 策略二：已轉的假名做語音相似替換 → 查字典
     * 策略三：截短最後一個音節重查
     */
    private fun fuzzyLookupCandidates(originalRomaji: String, reading: String): List<String> {
        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun addIfNew(newCandidates: List<String>) {
            newCandidates.forEach { if (seen.add(it)) results.add(it) }
        }

        // 策略一：羅馬字層容錯（鄰近鍵替換）
        for (i in originalRomaji.indices) {
            val ch = originalRomaji[i]
            val neighbors = ROMAJI_ADJACENT[ch] ?: continue
            for (neighbor in neighbors) {
                val altRomaji = originalRomaji.substring(0, i) +
                    neighbor + originalRomaji.substring(i + 1)
                // 轉換成假名
                val altKana = romajiToKana(altRomaji)
                if (altKana.isNotEmpty() && altKana != reading) {
                    addIfNew(lookupCandidates(altKana).take(3))
                }
            }
            if (results.size >= 10) break
        }

        // 策略二：假名層容錯（語音相似）
        var pos = 0
        while (pos < reading.length && results.size < 15) {
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

    /** Convert romaji string to hiragana without altering engine state. */
    private fun romajiToKana(romaji: String): String {
        val buf = StringBuilder()
        val pending = StringBuilder()
        for (c in romaji) {
            pending.append(c)
            // Try longest match first
            var matched = false
            for (len in minOf(pending.length, 4) downTo 1) {
                val sub = pending.substring(pending.length - len)
                val kana = ROMAJI_TO_KANA[sub]
                if (kana != null) {
                    // Remove matched portion from pending, add kana
                    pending.delete(pending.length - len, pending.length)
                    buf.append(pending).append(kana)
                    pending.clear()
                    matched = true
                    break
                }
            }
            // Handle double consonant → っ
            if (!matched && pending.length >= 2) {
                val last = pending[pending.length - 1]
                val prev = pending[pending.length - 2]
                if (last == prev && last != 'a' && last != 'i' && last != 'u' && last != 'e' && last != 'o' && last != 'n') {
                    buf.append(pending.substring(0, pending.length - 2)).append("っ")
                    pending.delete(0, pending.length - 1)
                }
            }
        }
        // Handle trailing 'n' → ん
        if (pending.toString() == "n") {
            buf.append("ん")
            pending.clear()
        }
        buf.append(pending)
        return buf.toString()
    }

    // ════════════════════════════════════════════════════════
    //  STATIC DATA
    // ════════════════════════════════════════════════════════

    companion object {

        // ── QWERTY Adjacency (for romaji fuzzy correction) ──
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

        // ── Kana phonetic similarity (for kana-layer fuzzy) ──
        private val KANA_SIMILAR: Map<String, List<String>> = mapOf(
            "か" to listOf("が", "き", "こ"),
            "が" to listOf("か", "ぎ", "ご"),
            "き" to listOf("ぎ", "か", "く"),
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
            "つ" to listOf("づ", "ち", "て"),
            "づ" to listOf("つ", "ど"),
            "て" to listOf("で", "た", "と"),
            "で" to listOf("て", "だ", "ど"),
            "と" to listOf("ど", "た", "て"),
            "ど" to listOf("と", "だ", "で"),
            "は" to listOf("ば", "ぱ", "ひ"),
            "ば" to listOf("は", "び", "ぱ"),
            "ぱ" to listOf("は", "ば", "ぴ"),
            "ひ" to listOf("び", "ぴ", "は"),
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
        )

        // ── Romaji → Hiragana Table ────────────────────────

        private val ROMAJI_TO_KANA: Map<String, String> = buildMap(250) {

            // --- Vowels ---
            put("a", "あ"); put("i", "い"); put("u", "う"); put("e", "え"); put("o", "お")

            // --- K-row ---
            put("ka", "か"); put("ki", "き"); put("ku", "く"); put("ke", "け"); put("ko", "こ")
            put("kya", "きゃ"); put("kyi", "きぃ"); put("kyu", "きゅ"); put("kye", "きぇ"); put("kyo", "きょ")

            // --- S-row ---
            put("sa", "さ"); put("si", "し"); put("shi", "し"); put("su", "す"); put("se", "せ"); put("so", "そ")
            put("sha", "しゃ"); put("shu", "しゅ"); put("she", "しぇ"); put("sho", "しょ")
            put("sya", "しゃ"); put("syu", "しゅ"); put("syo", "しょ")

            // --- T-row ---
            put("ta", "た"); put("ti", "ち"); put("chi", "ち"); put("tu", "つ"); put("tsu", "つ")
            put("te", "て"); put("to", "と")
            put("cha", "ちゃ"); put("chu", "ちゅ"); put("che", "ちぇ"); put("cho", "ちょ")
            put("tya", "ちゃ"); put("tyu", "ちゅ"); put("tyo", "ちょ")
            put("tha", "てゃ"); put("thi", "てぃ"); put("thu", "てゅ"); put("the", "てぇ"); put("tho", "てょ")

            // --- N-row ---
            put("na", "な"); put("ni", "に"); put("nu", "ぬ"); put("ne", "ね"); put("no", "の")
            put("nya", "にゃ"); put("nyi", "にぃ"); put("nyu", "にゅ"); put("nye", "にぇ"); put("nyo", "にょ")
            put("nn", "ん"); put("xn", "ん")

            // --- H-row ---
            put("ha", "は"); put("hi", "ひ"); put("hu", "ふ"); put("fu", "ふ"); put("he", "へ"); put("ho", "ほ")
            put("hya", "ひゃ"); put("hyi", "ひぃ"); put("hyu", "ひゅ"); put("hye", "ひぇ"); put("hyo", "ひょ")
            put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ")

            // --- M-row ---
            put("ma", "ま"); put("mi", "み"); put("mu", "む"); put("me", "め"); put("mo", "も")
            put("mya", "みゃ"); put("myi", "みぃ"); put("myu", "みゅ"); put("mye", "みぇ"); put("myo", "みょ")

            // --- Y-row ---
            put("ya", "や"); put("yu", "ゆ"); put("yo", "よ")

            // --- R-row ---
            put("ra", "ら"); put("ri", "り"); put("ru", "る"); put("re", "れ"); put("ro", "ろ")
            put("rya", "りゃ"); put("ryi", "りぃ"); put("ryu", "りゅ"); put("rye", "りぇ"); put("ryo", "りょ")

            // --- W-row ---
            put("wa", "わ"); put("wi", "ゐ"); put("we", "ゑ"); put("wo", "を")

            // --- G-row (Dakuten) ---
            put("ga", "が"); put("gi", "ぎ"); put("gu", "ぐ"); put("ge", "げ"); put("go", "ご")
            put("gya", "ぎゃ"); put("gyi", "ぎぃ"); put("gyu", "ぎゅ"); put("gye", "ぎぇ"); put("gyo", "ぎょ")

            // --- Z-row (Dakuten) ---
            put("za", "ざ"); put("zi", "じ"); put("ji", "じ"); put("zu", "ず"); put("ze", "ぜ"); put("zo", "ぞ")
            put("ja", "じゃ"); put("ju", "じゅ"); put("je", "じぇ"); put("jo", "じょ")
            put("jya", "じゃ"); put("jyu", "じゅ"); put("jyo", "じょ")
            put("zya", "じゃ"); put("zyu", "じゅ"); put("zyo", "じょ")

            // --- D-row (Dakuten) ---
            put("da", "だ"); put("di", "ぢ"); put("du", "づ"); put("de", "で"); put("do", "ど")
            put("dya", "ぢゃ"); put("dyu", "ぢゅ"); put("dyo", "ぢょ")
            put("dha", "でゃ"); put("dhi", "でぃ"); put("dhu", "でゅ"); put("dhe", "でぇ"); put("dho", "でょ")

            // --- B-row (Dakuten) ---
            put("ba", "ば"); put("bi", "び"); put("bu", "ぶ"); put("be", "べ"); put("bo", "ぼ")
            put("bya", "びゃ"); put("byi", "びぃ"); put("byu", "びゅ"); put("bye", "びぇ"); put("byo", "びょ")

            // --- P-row (Handakuten) ---
            put("pa", "ぱ"); put("pi", "ぴ"); put("pu", "ぷ"); put("pe", "ぺ"); put("po", "ぽ")
            put("pya", "ぴゃ"); put("pyi", "ぴぃ"); put("pyu", "ぴゅ"); put("pye", "ぴぇ"); put("pyo", "ぴょ")

            // --- Small kana (x- / l- prefix) ---
            put("xa", "ぁ"); put("xi", "ぃ"); put("xu", "ぅ"); put("xe", "ぇ"); put("xo", "ぉ")
            put("la", "ぁ"); put("li", "ぃ"); put("lu", "ぅ"); put("le", "ぇ"); put("lo", "ぉ")
            put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ")
            put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ")
            put("xtsu", "っ"); put("xtu", "っ"); put("ltsu", "っ"); put("ltu", "っ")
            put("xwa", "ゎ"); put("lwa", "ゎ")
            put("xka", "ゕ"); put("xke", "ゖ")

            // --- Special ---
            put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ")

            // --- Punctuation commonly typed in romaji mode ---
            put("-", "ー")
        }

        /**
         * 從 res/raw/ja_kanji_dict.txt 載入漢字辭書。
         * 格式：reading\tkanji1 kanji2 kanji3 ...
         */
        fun loadKanjiDictionary(context: Context?): Map<String, List<String>> {
            if (context != null) {
                try {
                    val map = mutableMapOf<String, List<String>>()
                    val input = context.resources.openRawResource(R.raw.ja_kanji_dict)
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        reader.forEachLine { line ->
                            if (line.isNotBlank() && !line.startsWith('#')) {
                                val parts = line.split('\t', limit = 2)
                                if (parts.size == 2) {
                                    val reading = parts[0].trim()
                                    val words = parts[1].trim().split(' ')
                                        .filter { it.isNotBlank() }
                                    if (reading.isNotEmpty() && words.isNotEmpty()) {
                                        map[reading] = words
                                    }
                                }
                            }
                        }
                    }
                    if (map.size > 100) return map
                } catch (_: Exception) { /* fall through to built-in */ }
            }
            return emptyMap()
        }
    }
}
