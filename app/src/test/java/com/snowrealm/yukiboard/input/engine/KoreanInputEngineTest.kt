package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanInputEngineTest {

    @Test
    fun emptyEngineHasEmptyComposing() {
        val engine = KoreanInputEngine()

        assertEquals("", engine.composingText)
    }

    @Test
    fun singleConsonantKeyShowsLoneJamo() {
        val engine = KoreanInputEngine()

        // 'r' maps to ㄱ in Dubeolsik
        val result = engine.processChar("r")

        assertTrue(result is InputResult.ComposingUpdated)
        assertEquals("ㄱ", engine.composingText)
    }

    @Test
    fun consonantPlusVowelFormsSyllable() {
        val engine = KoreanInputEngine()

        engine.processChar("r") // ㄱ
        engine.processChar("k") // ㅏ

        // Should form 가 (chosung ㄱ + jungsung ㅏ)
        assertEquals("가", engine.composingText)
    }

    @Test
    fun consonantVowelConsonantFormsJongsungSyllable() {
        val engine = KoreanInputEngine()

        engine.processChar("r") // ㄱ
        engine.processChar("k") // ㅏ
        engine.processChar("s") // ㄴ jongsung

        // Should form 간
        assertEquals("간", engine.composingText)
    }

    @Test
    fun compoundVowelOfWa() {
        val engine = KoreanInputEngine()

        // ㄱ + ㅗ + ㅏ should form compound ㅘ → 과
        engine.processChar("r") // ㄱ
        engine.processChar("h") // ㅗ
        engine.processChar("k") // ㅏ (compound with ㅗ → ㅘ)

        assertEquals("과", engine.composingText)
    }

    @Test
    fun processDeleteRetreatsThroughSyllableStates() {
        val engine = KoreanInputEngine()
        engine.processChar("r") // ㄱ
        engine.processChar("k") // ㅏ → 가
        engine.processChar("s") // ㄴ → 간

        // First delete drops jongsung ㄴ → 가
        val r1 = engine.processDelete()
        assertTrue(r1 is InputResult.ComposingUpdated)
        assertEquals("가", engine.composingText)

        // Next delete drops jungsung ㅏ → ㄱ
        engine.processDelete()
        assertEquals("ㄱ", engine.composingText)
    }

    @Test
    fun processDeleteOnEmptyReturnsIdle() {
        val engine = KoreanInputEngine()

        assertEquals(InputResult.Idle, engine.processDelete())
    }

    @Test
    fun processSpaceFlushesComposingWithTrailingSpace() {
        val engine = KoreanInputEngine()
        engine.processChar("r") // ㄱ
        engine.processChar("k") // ㅏ

        val result = engine.processSpace()

        assertEquals(InputResult.CommitText("가 "), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun processEnterFlushesComposingWithoutTrailingSpace() {
        val engine = KoreanInputEngine()
        engine.processChar("r") // ㄱ
        engine.processChar("k") // ㅏ

        val result = engine.processEnter()

        assertEquals(InputResult.CommitText("가"), result)
        assertEquals("", engine.composingText)
    }

    @Test
    fun nonKoreanKeyPassesThroughAsCommit() {
        val engine = KoreanInputEngine()

        // '1' is not mapped and not jamo → should commit through
        val result = engine.processChar("1")

        assertTrue(result is InputResult.CommitText)
        assertEquals("1", (result as InputResult.CommitText).text)
    }

    @Test
    fun resetClearsComposing() {
        val engine = KoreanInputEngine()
        engine.processChar("r")
        engine.processChar("k")

        engine.reset()

        assertEquals("", engine.composingText)
    }
}
