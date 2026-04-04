package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult

/**
 * Korean (Hangul) input engine implementing the standard 2-set Dubeolsik (두벌식) keyboard layout.
 *
 * Handles real-time Jamo composition into Hangul syllable blocks per Unicode standard:
 *   syllable = 0xAC00 + (chosung × 21 + jungsung) × 28 + jongsung
 *
 * Composition state machine:
 *   EMPTY → consonant → CHOSUNG → vowel → SYLLABLE → consonant → SYLLABLE_JONG
 *   SYLLABLE_JONG → vowel → split jongsung as new chosung, form new syllable
 *
 * Supports compound vowels (ㅘ, ㅙ, ㅚ, ㅝ, ㅞ, ㅟ, ㅢ) and compound jongsung
 * (ㄳ, ㄵ, ㄶ, ㄺ, ㄻ, ㄼ, ㄽ, ㄾ, ㄿ, ㅀ, ㅄ).
 */
class KoreanInputEngine : InputEngine {

    // ──────────────────────────────────────────────────────────────
    // Tables
    // ──────────────────────────────────────────────────────────────

    companion object {
        private const val HANGUL_BASE = 0xAC00
        private const val CHOSUNG_COUNT = 19
        private const val JUNGSUNG_COUNT = 21
        private const val JONGSUNG_COUNT = 28

        /** 초성 (initial consonants) in Unicode order */
        val CHOSUNG_TABLE = charArrayOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
        )

        /** 중성 (medial vowels) in Unicode order */
        val JUNGSUNG_TABLE = charArrayOf(
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ',
            'ㅣ'
        )

        /** 종성 (final consonants) in Unicode order — index 0 = no jongsung */
        val JONGSUNG_TABLE = charArrayOf(
            '\u0000', // 0: none
            'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ',
            'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ',
            'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
        )

        // ── Key-to-Jamo mapping (Dubeolsik 2-set) ──

        /** Maps a Latin key character to its corresponding Jamo */
        private val KEY_TO_JAMO = mapOf(
            // Consonants
            'r' to 'ㄱ', 'R' to 'ㄲ',
            's' to 'ㄴ',
            'e' to 'ㄷ', 'E' to 'ㄸ',
            'f' to 'ㄹ',
            'a' to 'ㅁ',
            'q' to 'ㅂ', 'Q' to 'ㅃ',
            't' to 'ㅅ', 'T' to 'ㅆ',
            'd' to 'ㅇ',
            'w' to 'ㅈ', 'W' to 'ㅉ',
            'c' to 'ㅊ',
            'z' to 'ㅋ',
            'x' to 'ㅌ',
            'v' to 'ㅍ',
            'g' to 'ㅎ',
            // Vowels
            'k' to 'ㅏ',
            'o' to 'ㅐ',
            'i' to 'ㅑ',
            'O' to 'ㅒ',
            'j' to 'ㅓ',
            'p' to 'ㅔ',
            'u' to 'ㅕ',
            'P' to 'ㅖ',
            'h' to 'ㅗ',
            'y' to 'ㅛ',
            'n' to 'ㅜ',
            'b' to 'ㅠ',
            'm' to 'ㅡ',
            'l' to 'ㅣ'
        )

        private val CONSONANTS = setOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
        )

        private val VOWELS = setOf(
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ',
            'ㅣ'
        )

        // ── Compound vowel combinations ──
        // Maps (base vowel, added vowel) → compound vowel
        private val COMPOUND_VOWELS = mapOf(
            ('ㅗ' to 'ㅏ') to 'ㅘ',
            ('ㅗ' to 'ㅐ') to 'ㅙ',
            ('ㅗ' to 'ㅣ') to 'ㅚ',
            ('ㅜ' to 'ㅓ') to 'ㅝ',
            ('ㅜ' to 'ㅔ') to 'ㅞ',
            ('ㅜ' to 'ㅣ') to 'ㅟ',
            ('ㅡ' to 'ㅣ') to 'ㅢ'
        )

        // Decompose compound vowel → (base, added)
        private val COMPOUND_VOWEL_DECOMPOSE = mapOf(
            'ㅘ' to ('ㅗ' to 'ㅏ'),
            'ㅙ' to ('ㅗ' to 'ㅐ'),
            'ㅚ' to ('ㅗ' to 'ㅣ'),
            'ㅝ' to ('ㅜ' to 'ㅓ'),
            'ㅞ' to ('ㅜ' to 'ㅔ'),
            'ㅟ' to ('ㅜ' to 'ㅣ'),
            'ㅢ' to ('ㅡ' to 'ㅣ')
        )

        // ── Compound jongsung combinations ──
        // Maps (first consonant, second consonant) → compound jongsung
        private val COMPOUND_JONGSUNG = mapOf(
            ('ㄱ' to 'ㅅ') to 'ㄳ',
            ('ㄴ' to 'ㅈ') to 'ㄵ',
            ('ㄴ' to 'ㅎ') to 'ㄶ',
            ('ㄹ' to 'ㄱ') to 'ㄺ',
            ('ㄹ' to 'ㅁ') to 'ㄻ',
            ('ㄹ' to 'ㅂ') to 'ㄼ',
            ('ㄹ' to 'ㅅ') to 'ㄽ',
            ('ㄹ' to 'ㅌ') to 'ㄾ',
            ('ㄹ' to 'ㅍ') to 'ㄿ',
            ('ㄹ' to 'ㅎ') to 'ㅀ',
            ('ㅂ' to 'ㅅ') to 'ㅄ'
        )

        // Decompose compound jongsung → (first, second)
        private val COMPOUND_JONGSUNG_DECOMPOSE = mapOf(
            'ㄳ' to ('ㄱ' to 'ㅅ'),
            'ㄵ' to ('ㄴ' to 'ㅈ'),
            'ㄶ' to ('ㄴ' to 'ㅎ'),
            'ㄺ' to ('ㄹ' to 'ㄱ'),
            'ㄻ' to ('ㄹ' to 'ㅁ'),
            'ㄼ' to ('ㄹ' to 'ㅂ'),
            'ㄽ' to ('ㄹ' to 'ㅅ'),
            'ㄾ' to ('ㄹ' to 'ㅌ'),
            'ㄿ' to ('ㄹ' to 'ㅍ'),
            'ㅀ' to ('ㄹ' to 'ㅎ'),
            'ㅄ' to ('ㅂ' to 'ㅅ')
        )

        // Consonants that can serve as jongsung (maps jamo char → jongsung index)
        // Not all chosung consonants are valid as jongsung; double consonants ㄸ, ㅃ, ㅉ cannot.
        private val JAMO_TO_JONGSUNG_INDEX: Map<Char, Int> = buildMap {
            for (i in JONGSUNG_TABLE.indices) {
                if (JONGSUNG_TABLE[i] != '\u0000') {
                    put(JONGSUNG_TABLE[i], i)
                }
            }
        }

        private val JAMO_TO_CHOSUNG_INDEX: Map<Char, Int> = buildMap {
            for (i in CHOSUNG_TABLE.indices) {
                put(CHOSUNG_TABLE[i], i)
            }
        }

        private val JAMO_TO_JUNGSUNG_INDEX: Map<Char, Int> = buildMap {
            for (i in JUNGSUNG_TABLE.indices) {
                put(JUNGSUNG_TABLE[i], i)
            }
        }

        private fun isConsonant(jamo: Char): Boolean = jamo in CONSONANTS
        private fun isVowel(jamo: Char): Boolean = jamo in VOWELS
    }

    // ──────────────────────────────────────────────────────────────
    // Composition state
    // ──────────────────────────────────────────────────────────────

    private enum class State {
        EMPTY,          // No composing jamo
        CHOSUNG,        // Has initial consonant only (displayed as standalone jamo)
        SYLLABLE,       // Has chosung + jungsung (formed syllable, no jongsung)
        SYLLABLE_JONG   // Has chosung + jungsung + jongsung
    }

    private var state = State.EMPTY

    /** Already committed text accumulated during this composing session */
    private var committedBuffer = StringBuilder()

    // Current syllable components (jamo characters, not indices)
    private var chosung: Char = '\u0000'
    private var jungsung: Char = '\u0000'
    private var jongsung: Char = '\u0000' // '\u0000' means no jongsung

    override val composingText: String
        get() = buildComposingText()

    // ──────────────────────────────────────────────────────────────
    // InputEngine implementation
    // ──────────────────────────────────────────────────────────────

    override fun processChar(char: String): InputResult {
        if (char.length != 1) {
            // Not a single character; commit current and pass through
            val committed = forceCommitInternal()
            return if (committed.isNotEmpty()) {
                InputResult.CommitText(committed + char)
            } else {
                InputResult.CommitText(char)
            }
        }

        val key = char[0]
        val jamo = KEY_TO_JAMO[key]

        if (jamo == null) {
            // Not a Korean key; commit current composing and pass through the character
            val committed = forceCommitInternal()
            return if (committed.isNotEmpty()) {
                InputResult.CommitText(committed + key)
            } else {
                InputResult.CommitText(key.toString())
            }
        }

        return if (isConsonant(jamo)) {
            processConsonant(jamo)
        } else {
            processVowel(jamo)
        }
    }

    override fun processDelete(): InputResult {
        return when (state) {
            State.EMPTY -> {
                InputResult.Idle
            }
            State.CHOSUNG -> {
                // Remove the lone chosung
                chosung = '\u0000'
                state = State.EMPTY
                if (committedBuffer.isNotEmpty()) {
                    InputResult.ComposingUpdated(composingText)
                } else {
                    InputResult.ComposingCleared
                }
            }
            State.SYLLABLE -> {
                // Remove jungsung; check if it was a compound vowel
                val decomposed = COMPOUND_VOWEL_DECOMPOSE[jungsung]
                if (decomposed != null) {
                    // Compound vowel: revert to base vowel
                    jungsung = decomposed.first
                    // Still in SYLLABLE state with base vowel
                    return InputResult.ComposingUpdated(composingText)
                }
                // Simple vowel: remove it
                jungsung = '\u0000'
                if (chosung != '\u0000') {
                    state = State.CHOSUNG
                    InputResult.ComposingUpdated(composingText)
                } else {
                    // Was a lone vowel with no chosung
                    state = State.EMPTY
                    if (committedBuffer.isNotEmpty()) {
                        InputResult.ComposingUpdated(composingText)
                    } else {
                        InputResult.ComposingCleared
                    }
                }
            }
            State.SYLLABLE_JONG -> {
                // Remove jongsung; check if it was a compound jongsung
                val decomposed = COMPOUND_JONGSUNG_DECOMPOSE[jongsung]
                if (decomposed != null) {
                    // Compound jongsung: revert to first component
                    jongsung = decomposed.first
                    // Stay in SYLLABLE_JONG
                    return InputResult.ComposingUpdated(composingText)
                }
                // Simple jongsung: remove it, revert to SYLLABLE
                jongsung = '\u0000'
                state = State.SYLLABLE
                InputResult.ComposingUpdated(composingText)
            }
        }
    }

    override fun processSpace(): InputResult {
        val committed = forceCommitInternal()
        return if (committed.isNotEmpty()) {
            InputResult.CommitText(committed + " ")
        } else {
            InputResult.CommitText(" ")
        }
    }

    override fun processEnter(): InputResult {
        val committed = forceCommitInternal()
        return if (committed.isNotEmpty()) {
            InputResult.CommitText(committed)
        } else {
            InputResult.Idle
        }
    }

    override fun forceCommit(): InputResult {
        val committed = forceCommitInternal()
        return if (committed.isNotEmpty()) {
            InputResult.CommitText(committed)
        } else {
            InputResult.ComposingCleared
        }
    }

    override fun getCandidates(): List<String> = emptyList()

    override fun reset() {
        state = State.EMPTY
        chosung = '\u0000'
        jungsung = '\u0000'
        jongsung = '\u0000'
        committedBuffer.clear()
    }

    // ──────────────────────────────────────────────────────────────
    // Core composition logic
    // ──────────────────────────────────────────────────────────────

    private fun processConsonant(jamo: Char): InputResult {
        return when (state) {
            State.EMPTY -> {
                chosung = jamo
                state = State.CHOSUNG
                InputResult.ComposingUpdated(composingText)
            }
            State.CHOSUNG -> {
                // Another consonant after chosung: commit the previous chosung as standalone,
                // start new chosung. Exception: doubled consonants are handled by the key map
                // (e.g., 'R' → ㄲ), so two consecutive consonants are always separate.
                committedBuffer.append(chosung)
                chosung = jamo
                // Stay in CHOSUNG
                InputResult.ComposingUpdated(composingText)
            }
            State.SYLLABLE -> {
                // Consonant after chosung+jungsung → becomes jongsung if valid
                val jongIndex = JAMO_TO_JONGSUNG_INDEX[jamo]
                if (jongIndex != null) {
                    jongsung = jamo
                    state = State.SYLLABLE_JONG
                    InputResult.ComposingUpdated(composingText)
                } else {
                    // This consonant can't be jongsung (e.g., ㄸ, ㅃ, ㅉ)
                    // Commit current syllable, start new chosung
                    committedBuffer.append(buildSyllable())
                    resetCurrentSyllable()
                    chosung = jamo
                    state = State.CHOSUNG
                    InputResult.ComposingUpdated(composingText)
                }
            }
            State.SYLLABLE_JONG -> {
                // Already have jongsung. Try to form compound jongsung.
                val compound = COMPOUND_JONGSUNG[jongsung to jamo]
                if (compound != null) {
                    jongsung = compound
                    // Stay in SYLLABLE_JONG
                    InputResult.ComposingUpdated(composingText)
                } else {
                    // Can't compound. Commit current syllable, start new chosung.
                    committedBuffer.append(buildSyllable())
                    resetCurrentSyllable()
                    chosung = jamo
                    state = State.CHOSUNG
                    InputResult.ComposingUpdated(composingText)
                }
            }
        }
    }

    private fun processVowel(jamo: Char): InputResult {
        return when (state) {
            State.EMPTY -> {
                // Vowel with no chosung: treat as standalone vowel character
                // In Korean IME, a lone vowel is just committed or shown as composing.
                // We use ㅇ-less display: just the vowel jamo itself.
                // Actually, set chosung to implicit none and form with jungsung only.
                // For proper behavior: show the vowel as composing, commit on next input.
                jungsung = jamo
                state = State.SYLLABLE
                // Use placeholder ㅇ? No — standard IME shows lone vowel as jamo.
                // We'll track this specially: chosung = '\u0000' means no chosung.
                InputResult.ComposingUpdated(composingText)
            }
            State.CHOSUNG -> {
                // Chosung + vowel → form syllable
                jungsung = jamo
                state = State.SYLLABLE
                InputResult.ComposingUpdated(composingText)
            }
            State.SYLLABLE -> {
                // Already have chosung + jungsung. Try compound vowel.
                val compound = COMPOUND_VOWELS[jungsung to jamo]
                if (compound != null) {
                    jungsung = compound
                    // Stay in SYLLABLE
                    InputResult.ComposingUpdated(composingText)
                } else {
                    // Can't compound. Commit current syllable, start new vowel-only state.
                    committedBuffer.append(currentDisplayChar())
                    resetCurrentSyllable()
                    jungsung = jamo
                    state = State.SYLLABLE
                    InputResult.ComposingUpdated(composingText)
                }
            }
            State.SYLLABLE_JONG -> {
                // Has jongsung, and a new vowel comes in.
                // Split: the jongsung (or second part of compound jongsung) becomes
                // the chosung of a new syllable with this vowel.
                val decomposed = COMPOUND_JONGSUNG_DECOMPOSE[jongsung]
                if (decomposed != null) {
                    // Compound jongsung: first part stays, second part becomes new chosung
                    val stayJong = decomposed.first
                    val newCho = decomposed.second

                    // Commit current syllable with only the first part of compound jongsung
                    jongsung = stayJong
                    committedBuffer.append(buildSyllable())

                    // Start new syllable
                    resetCurrentSyllable()
                    chosung = newCho
                    jungsung = jamo
                    state = State.SYLLABLE
                } else {
                    // Simple jongsung: it moves to become the new chosung
                    val newCho = jongsung

                    // Commit current syllable without jongsung
                    jongsung = '\u0000'
                    committedBuffer.append(buildSyllable())

                    // Start new syllable
                    resetCurrentSyllable()
                    chosung = newCho
                    jungsung = jamo
                    state = State.SYLLABLE
                }
                InputResult.ComposingUpdated(composingText)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Syllable building helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Builds a complete Hangul syllable character from current chosung/jungsung/jongsung.
     * Returns the syllable character, or a jamo character if incomplete.
     */
    private fun buildSyllable(): Char {
        val choIndex = JAMO_TO_CHOSUNG_INDEX[chosung]
        val jungIndex = JAMO_TO_JUNGSUNG_INDEX[jungsung]

        if (choIndex == null || jungIndex == null) {
            // Shouldn't happen in normal flow; fallback
            return chosung.takeIf { it != '\u0000' } ?: jungsung
        }

        val jongIndex = if (jongsung != '\u0000') {
            JAMO_TO_JONGSUNG_INDEX[jongsung] ?: 0
        } else {
            0
        }

        val code = HANGUL_BASE + (choIndex * JUNGSUNG_COUNT + jungIndex) * JONGSUNG_COUNT + jongIndex
        return code.toChar()
    }

    /**
     * Returns the character to display for the current composing state.
     */
    private fun currentDisplayChar(): String {
        return when (state) {
            State.EMPTY -> ""
            State.CHOSUNG -> chosung.toString()
            State.SYLLABLE -> {
                if (chosung == '\u0000') {
                    // Lone vowel — display as jamo
                    jungsung.toString()
                } else {
                    buildSyllable().toString()
                }
            }
            State.SYLLABLE_JONG -> buildSyllable().toString()
        }
    }

    /**
     * Builds the full composing text: committed buffer + current composing character.
     */
    private fun buildComposingText(): String {
        val current = currentDisplayChar()
        return if (committedBuffer.isNotEmpty()) {
            committedBuffer.toString() + current
        } else {
            current
        }
    }

    /**
     * Force-commits all composing text and resets state. Returns the committed string.
     */
    private fun forceCommitInternal(): String {
        val text = composingText
        reset()
        return text
    }

    private fun resetCurrentSyllable() {
        chosung = '\u0000'
        jungsung = '\u0000'
        jongsung = '\u0000'
    }
}
