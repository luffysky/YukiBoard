package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM smoke tests for [CangjieInputEngine] in no-Context mode.
 *
 * In no-Context mode the built-in dictionary is empty (loaded lazily from res/raw when
 * a Context is provided). These tests therefore exercise the composing / buffer /
 * commit mechanics rather than candidate lookup correctness.
 */
class CangjieInputEngineTest {

    @Test
    fun emptyEngineHasEmptyComposing() {
        val engine = CangjieInputEngine()

        assertEquals("", engine.composingText)
        assertTrue(engine.getCandidates().isEmpty())
    }

    @Test
    fun singleLetterMapsToRadicalCharacter() {
        val engine = CangjieInputEngine()

        val result = engine.processChar("a")

        assertTrue(result is InputResult.ComposingUpdated)
        assertEquals("日", engine.composingText)
    }

    @Test
    fun twoLettersMapToConcatenatedRadicals() {
        val engine = CangjieInputEngine()

        engine.processChar("a")
        engine.processChar("b")

        assertEquals("日月", engine.composingText)
    }

    @Test
    fun uppercaseIsLowercasedBeforeMapping() {
        val engine = CangjieInputEngine()

        engine.processChar("A")

        assertEquals("日", engine.composingText)
    }

    @Test
    fun invalidLetterIsIgnored() {
        val engine = CangjieInputEngine()

        // 'x' is explicitly excluded from VALID_KEYS
        val result = engine.processChar("x")

        assertEquals(InputResult.Idle, result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun nonLetterIsIgnored() {
        val engine = CangjieInputEngine()

        val result = engine.processChar("1")

        assertEquals(InputResult.Idle, result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun maxFiveCodesBufferRefusesSixth() {
        val engine = CangjieInputEngine()
        "abcde".forEach { engine.processChar(it.toString()) }
        assertEquals(5, engine.composingText.length)

        val sixth = engine.processChar("a")

        assertEquals(InputResult.Idle, sixth)
        assertEquals(5, engine.composingText.length)
    }

    @Test
    fun processDeleteShrinksAndClears() {
        val engine = CangjieInputEngine()
        engine.processChar("a")
        engine.processChar("b")

        val r1 = engine.processDelete()
        assertTrue(r1 is InputResult.ComposingUpdated)
        assertEquals("日", engine.composingText)

        val r2 = engine.processDelete()
        assertEquals(InputResult.ComposingCleared, r2)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processDeleteOnEmptyReturnsComposingCleared() {
        val engine = CangjieInputEngine()

        assertEquals(InputResult.ComposingCleared, engine.processDelete())
    }

    @Test
    fun processEnterCommitsRawLetterCodes() {
        val engine = CangjieInputEngine()
        engine.processChar("a")
        engine.processChar("b")

        val result = engine.processEnter()

        assertEquals(InputResult.CommitText("ab"), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processEnterOnEmptyIsIdle() {
        val engine = CangjieInputEngine()

        assertEquals(InputResult.Idle, engine.processEnter())
    }

    @Test
    fun processSpaceOnEmptyCommitsLiteralSpace() {
        val engine = CangjieInputEngine()

        val result = engine.processSpace()

        assertEquals(InputResult.CommitText(" "), result)
    }

    @Test
    fun processSpaceWithEmptyDictKeepsComposing() {
        // In no-Context mode the dictionary is empty, so candidates are empty.
        // processSpace falls through to "keep composing" branch rather than committing.
        val engine = CangjieInputEngine()
        engine.processChar("a")

        val result = engine.processSpace()

        assertTrue(result is InputResult.ComposingUpdated)
        assertEquals("日", engine.composingText)
    }

    @Test
    fun resetClearsBuffer() {
        val engine = CangjieInputEngine()
        engine.processChar("a")
        engine.processChar("b")

        engine.reset()

        assertEquals("", engine.composingText)
        assertTrue(engine.getCandidates().isEmpty())
    }
}
