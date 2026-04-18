package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishInputEngineTest {

    @Test
    fun emptyEngineExposesEmptyComposing() {
        val engine = EnglishInputEngine()

        assertEquals("", engine.composingText)
        assertTrue(engine.getCandidates().isEmpty())
    }

    @Test
    fun processCharAppendsToComposingBuffer() {
        val engine = EnglishInputEngine()

        val r1 = engine.processChar("t")
        assertTrue(r1 is InputResult.ComposingUpdated)
        assertEquals("t", engine.composingText)

        engine.processChar("h")
        assertEquals("th", engine.composingText)
    }

    @Test
    fun knownPrefixProducesSuggestCandidates() {
        val engine = EnglishInputEngine()
        engine.processChar("t")
        engine.processChar("h")

        val cands = engine.getCandidates()

        assertTrue("candidates for 'th' should include 'the'", cands.contains("the"))
    }

    @Test
    fun processSpaceOnKnownWordCommitsWordWithTrailingSpace() {
        val engine = EnglishInputEngine()
        engine.processChar("t")
        engine.processChar("h")
        engine.processChar("e")

        val result = engine.processSpace()

        assertEquals(InputResult.CommitText("the "), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processSpaceOnEmptyBufferCommitsLiteralSpace() {
        val engine = EnglishInputEngine()

        val result = engine.processSpace()

        assertEquals(InputResult.CommitText(" "), result)
    }

    @Test
    fun processEnterCommitsRawBufferWithoutTrailingSpace() {
        val engine = EnglishInputEngine()
        engine.processChar("h")
        engine.processChar("i")

        val result = engine.processEnter()

        assertEquals(InputResult.CommitText("hi"), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processEnterOnEmptyBufferIsIdle() {
        val engine = EnglishInputEngine()

        assertEquals(InputResult.Idle, engine.processEnter())
    }

    @Test
    fun processDeleteShrinksBufferAndClearsOnExhaust() {
        val engine = EnglishInputEngine()
        engine.processChar("a")
        engine.processChar("b")

        val r1 = engine.processDelete()
        assertTrue(r1 is InputResult.ComposingUpdated)
        assertEquals("a", engine.composingText)

        val r2 = engine.processDelete()
        assertEquals(InputResult.ComposingCleared, r2)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processDeleteOnEmptyBufferReturnsComposingCleared() {
        val engine = EnglishInputEngine()

        assertEquals(InputResult.ComposingCleared, engine.processDelete())
    }

    @Test
    fun forceCommitFlushesBufferWithoutSpace() {
        val engine = EnglishInputEngine()
        engine.processChar("o")
        engine.processChar("k")

        val result = engine.forceCommit()

        assertEquals(InputResult.CommitText("ok"), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun resetClearsEverything() {
        val engine = EnglishInputEngine()
        engine.processChar("h")
        engine.processChar("i")

        engine.reset()

        assertEquals("", engine.composingText)
        assertTrue(engine.getCandidates().isEmpty())
    }

    @Test
    fun getCandidatesForPrefixDoesNotDisturbInternalBuffer() {
        val engine = EnglishInputEngine()
        engine.processChar("t")

        val probe = engine.getCandidatesForPrefix("wh")

        assertTrue(probe.isNotEmpty())
        // internal buffer untouched
        assertEquals("t", engine.composingText)
    }
}
