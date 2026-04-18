package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinInputEngineTest {

    @Test
    fun emptyEngineHasNoComposingOrCandidates() {
        val engine = PinyinInputEngine()

        assertEquals("", engine.composingText)
        assertTrue(engine.getCandidates().isEmpty())
    }

    @Test
    fun processCharBuildsComposingBuffer() {
        val engine = PinyinInputEngine()

        val r1 = engine.processChar("n")
        assertTrue(r1 is InputResult.ComposingUpdated)
        assertEquals("n", engine.composingText)

        engine.processChar("i")
        assertEquals("ni", engine.composingText)
    }

    @Test
    fun nonLetterCharIsIgnored() {
        val engine = PinyinInputEngine()

        val result = engine.processChar("1")

        assertEquals(InputResult.Idle, result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun singleSyllableProducesCandidatesIncludingNi() {
        val engine = PinyinInputEngine()
        engine.processChar("n")
        engine.processChar("i")

        val cands = engine.getCandidates()

        assertTrue("candidates should not be empty for 'ni'", cands.isNotEmpty())
        assertTrue("candidates should contain 你 for 'ni'", cands.contains("你"))
    }

    @Test
    fun multiSyllablePhraseNihaoProducesNihaoCandidate() {
        val engine = PinyinInputEngine()
        "nihao".forEach { engine.processChar(it.toString()) }

        val cands = engine.getCandidates()

        assertTrue("candidates should contain 你好 for 'nihao'", cands.contains("你好"))
    }

    @Test
    fun processSpaceCommitsFirstCandidate() {
        val engine = PinyinInputEngine()
        engine.processChar("n")
        engine.processChar("i")

        val result = engine.processSpace()

        assertTrue(result is InputResult.CommitText)
        val committed = (result as InputResult.CommitText).text
        assertFalse("commit should not be raw 'ni' when candidates exist", committed == "ni")
        // After commit, engine is reset
        assertEquals("", engine.composingText)
        assertTrue(engine.getCandidates().isEmpty())
    }

    @Test
    fun processSpaceOnEmptyBufferCommitsLiteralSpace() {
        val engine = PinyinInputEngine()

        val result = engine.processSpace()

        assertEquals(InputResult.CommitText(" "), result)
    }

    @Test
    fun processDeleteShrinksBufferAndCancelsCompletely() {
        val engine = PinyinInputEngine()
        engine.processChar("n")
        engine.processChar("i")

        val r1 = engine.processDelete()
        assertTrue(r1 is InputResult.ComposingUpdated)
        assertEquals("n", engine.composingText)

        val r2 = engine.processDelete()
        assertEquals(InputResult.ComposingCleared, r2)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processDeleteOnEmptyReturnsComposingCleared() {
        val engine = PinyinInputEngine()

        assertEquals(InputResult.ComposingCleared, engine.processDelete())
    }

    @Test
    fun processEnterCommitsRawPinyin() {
        val engine = PinyinInputEngine()
        engine.processChar("n")
        engine.processChar("i")

        val result = engine.processEnter()

        assertEquals(InputResult.CommitText("ni"), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun forceCommitPrefersCandidateIfPresent() {
        val engine = PinyinInputEngine()
        engine.processChar("n")
        engine.processChar("i")

        val result = engine.forceCommit()

        assertTrue(result is InputResult.CommitText)
        assertEquals("", engine.composingText)
    }

    @Test
    fun resetClearsBufferAndCandidates() {
        val engine = PinyinInputEngine()
        "nihao".forEach { engine.processChar(it.toString()) }

        engine.reset()

        assertEquals("", engine.composingText)
        assertTrue(engine.getCandidates().isEmpty())
    }
}
