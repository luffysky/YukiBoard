package com.snowrealm.yukiboard.input.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM smoke tests for [EnglishDictionary] in no-Context mode.
 *
 * When constructed without a Context the dictionary loads only the [BASIC_WORDS]
 * fallback (~120 high-frequency words). These tests confirm core Trie ops work
 * against that fallback; asset-backed behaviour is out of scope for S11-3.
 */
class EnglishDictionaryTest {

    @Test
    fun isWordReturnsTrueForBasicEntries() {
        val dict = EnglishDictionary()

        assertTrue(dict.isWord("the"))
        assertTrue(dict.isWord("and"))
        assertTrue(dict.isWord("you"))
    }

    @Test
    fun isWordReturnsFalseForUnknown() {
        val dict = EnglishDictionary()

        assertFalse(dict.isWord("zzzzz"))
        assertFalse(dict.isWord(""))
    }

    @Test
    fun isWordIsCaseInsensitive() {
        val dict = EnglishDictionary()

        assertTrue(dict.isWord("The"))
        assertTrue(dict.isWord("THE"))
    }

    @Test
    fun suggestOnKnownPrefixReturnsResults() {
        val dict = EnglishDictionary()

        val results = dict.suggest("th", 9)

        assertTrue("suggest('th') should not be empty", results.isNotEmpty())
        assertTrue("suggest('th') should contain 'the'", results.contains("the"))
    }

    @Test
    fun suggestRespectsMaxResults() {
        val dict = EnglishDictionary()

        val results = dict.suggest("th", 2)

        assertTrue(results.size <= 2)
    }

    @Test
    fun suggestOnEmptyPrefixReturnsEmpty() {
        val dict = EnglishDictionary()

        assertTrue(dict.suggest("", 5).isEmpty())
        assertTrue(dict.suggest("   ", 5).isEmpty())
    }

    @Test
    fun suggestOnUnknownPrefixReturnsEmpty() {
        val dict = EnglishDictionary()

        assertTrue(dict.suggest("zqx", 5).isEmpty())
    }

    @Test
    fun frequencyOfKnownWordIsPositive() {
        val dict = EnglishDictionary()

        assertTrue(dict.frequencyOf("the") > 0)
    }

    @Test
    fun frequencyOfUnknownWordIsZero() {
        val dict = EnglishDictionary()

        assertEquals(0, dict.frequencyOf("zqxwfv"))
    }

    @Test
    fun autocorrectFindsKnownWordOnAdjacentSwap() {
        val dict = EnglishDictionary()

        // "teh" is a known swap error for "the": swapping positions 1 and 2 yields "the"
        val results = dict.autocorrect("teh", 5)

        assertTrue("autocorrect('teh') should contain 'the'", results.contains("the"))
    }

    @Test
    fun autocorrectOnSingleCharReturnsEmpty() {
        val dict = EnglishDictionary()

        assertTrue(dict.autocorrect("a", 5).isEmpty())
    }

    @Test
    fun fuzzyCorrectWithAdjacencyRepairsMistypedKey() {
        val dict = EnglishDictionary()

        // 'd' is adjacent to 'e' on QWERTY → "thd" should correct to "the"
        val results = dict.fuzzyCorrectWithAdjacency("thd", 5)

        assertTrue(
            "fuzzy adjacent correction for 'thd' should include 'the'",
            results.contains("the")
        )
    }

    @Test
    fun fuzzyCorrectWithAdjacencyOnSingleCharIsEmpty() {
        val dict = EnglishDictionary()

        assertTrue(dict.fuzzyCorrectWithAdjacency("a", 5).isEmpty())
    }

    @Test
    fun insertCustomWordIsFoundByIsWord() {
        val dict = EnglishDictionary()
        dict.insert("yukiboard", 9999)

        assertTrue(dict.isWord("yukiboard"))
        assertTrue(dict.frequencyOf("yukiboard") > 0)
    }
}
