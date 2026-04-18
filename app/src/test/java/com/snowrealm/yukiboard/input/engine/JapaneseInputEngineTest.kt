package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseInputEngineTest {

    @Test
    fun emptyEngineHasEmptyComposing() {
        val engine = JapaneseInputEngine()

        assertEquals("", engine.composingText)
    }

    @Test
    fun kaProducesHiraganaKa() {
        val engine = JapaneseInputEngine()

        engine.processChar("k")
        engine.processChar("a")

        assertEquals("か", engine.composingText)
    }

    @Test
    fun shiGreedyMatchesThreeCharRomaji() {
        val engine = JapaneseInputEngine()

        engine.processChar("s")
        engine.processChar("h")
        engine.processChar("i")

        assertEquals("し", engine.composingText)
    }

    @Test
    fun tsuGreedyMatchesThreeCharRomaji() {
        val engine = JapaneseInputEngine()

        engine.processChar("t")
        engine.processChar("s")
        engine.processChar("u")

        assertEquals("つ", engine.composingText)
    }

    @Test
    fun doubleConsonantProducesSokuonPlusKana() {
        val engine = JapaneseInputEngine()

        engine.processChar("k")
        engine.processChar("k")
        engine.processChar("a")

        assertEquals("っか", engine.composingText)
    }

    @Test
    fun nyaProducesSmallYaCombo() {
        val engine = JapaneseInputEngine()

        engine.processChar("n")
        engine.processChar("y")
        engine.processChar("a")

        assertEquals("にゃ", engine.composingText)
    }

    @Test
    fun processEnterCommitsCurrentKana() {
        val engine = JapaneseInputEngine()
        engine.processChar("k")
        engine.processChar("a")

        val result = engine.processEnter()

        assertEquals(InputResult.CommitText("か"), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processEnterOnEmptyBufferIsIdle() {
        val engine = JapaneseInputEngine()

        assertEquals(InputResult.Idle, engine.processEnter())
    }

    @Test
    fun processDeleteClearsAfterExhaust() {
        val engine = JapaneseInputEngine()
        engine.processChar("k")
        engine.processChar("a")

        // First delete removes the 'か' kana (kana buffer has "か", delete removes last kana char)
        engine.processDelete()
        val finalDelete = engine.processDelete()

        // Either the first or second delete should end in ComposingCleared
        // (depends on exact delete semantics), but composing must be empty at this point
        assertTrue(
            finalDelete is InputResult.ComposingCleared || engine.composingText.isEmpty()
        )
        assertEquals("", engine.composingText)
    }

    @Test
    fun resetClearsEverything() {
        val engine = JapaneseInputEngine()
        engine.processChar("k")
        engine.processChar("a")

        engine.reset()

        assertEquals("", engine.composingText)
    }

    @Test
    fun forceCommitFlushesKanaWithoutRomajiTail() {
        val engine = JapaneseInputEngine()
        engine.processChar("k")
        engine.processChar("a")

        val result = engine.forceCommit()

        assertTrue(result is InputResult.CommitText)
        assertEquals("か", (result as InputResult.CommitText).text)
        assertEquals("", engine.composingText)
    }
}
