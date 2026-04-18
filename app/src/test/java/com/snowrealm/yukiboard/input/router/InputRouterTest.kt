package com.snowrealm.yukiboard.input.router

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InputRouterTest {

    @Test
    fun startsInEnglishModeWithEmptyCandidates() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val router = InputRouter(context)

        assertEquals(InputMode.ENGLISH, router.inputMode)
        assertTrue(router.candidates.isEmpty())
        assertEquals("", router.composingText)
    }

    @Test
    fun enabledModesContainsCoreModesByDefault() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val router = InputRouter(context)

        assertTrue(router.enabledModes.contains(InputMode.ENGLISH))
        assertTrue(router.enabledModes.contains(InputMode.BOPOMOFO))
        assertTrue(router.enabledModes.contains(InputMode.JAPANESE))
        assertTrue(router.enabledModes.contains(InputMode.KOREAN))
    }

    @Test
    fun switchToDifferentModeUpdatesInputModeAndClearsAutoBuffers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val router = InputRouter(context)
        router.autoQwertyBuffer.append("abc")
        router.autoBopomofoBuffer.add("ㄅ")

        val result = router.switchTo(InputMode.BOPOMOFO)

        assertEquals(InputResult.Idle, result)
        assertEquals(InputMode.BOPOMOFO, router.inputMode)
        assertEquals("", router.autoQwertyBuffer.toString())
        assertTrue(router.autoBopomofoBuffer.isEmpty())
    }

    @Test
    fun switchToSameModeReturnsIdleWithoutChangingMode() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val router = InputRouter(context)

        val result = router.switchTo(InputMode.ENGLISH)

        assertEquals(InputResult.Idle, result)
        assertEquals(InputMode.ENGLISH, router.inputMode)
    }

    @Test
    fun resetClearsComposingAndAutoBuffers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val router = InputRouter(context)
        router.processInput("h")
        router.autoQwertyBuffer.append("he")
        router.autoBopomofoBuffer.add("ㄏ")

        router.reset()

        assertEquals("", router.composingText)
        assertEquals("", router.autoQwertyBuffer.toString())
        assertTrue(router.autoBopomofoBuffer.isEmpty())
        assertTrue(router.candidates.isEmpty())
    }

    @Test
    fun releaseClearsAutoBuffersWithoutThrowing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val router = InputRouter(context)
        router.autoQwertyBuffer.append("test")
        router.autoBopomofoBuffer.add("ㄊ")

        try {
            router.release()
        } catch (t: Throwable) {
            fail("release should not throw, but got: ${t.message}")
        }

        assertEquals("", router.autoQwertyBuffer.toString())
        assertTrue(router.autoBopomofoBuffer.isEmpty())
    }
}
