package com.snowrealm.yukiboard.input.smart

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartInputManagerTest {

    @Test
    fun startsWithFeatureFlagsEnabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val manager = SmartInputManager(context)

        assertTrue(manager.smartCorrectionEnabled)
        assertTrue(manager.adaptiveDictEnabled)
        assertTrue(manager.phrasePredictEnabled)
        assertTrue(manager.habitTrackEnabled)
    }

    @Test
    fun sentencePredictionsAreEmptyWhenPhrasePredictionDisabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val manager = SmartInputManager(context)
        manager.phrasePredictEnabled = false

        assertEquals(emptyList<String>(), manager.getSentencePredictions("hello", "w"))
    }

    @Test
    fun phrasePredictionApisReturnEmptyWhenFeatureDisabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val manager = SmartInputManager(context)
        manager.phrasePredictEnabled = false

        assertEquals(emptyList<String>(), manager.getPhrasePredictionsWithPrefix("he"))
        assertEquals(emptyList<String>(), manager.predictWithContext(listOf("hello")))
        assertEquals(emptyList<String>(), manager.getContextSuggestions("com.test.app"))
    }

    @Test
    fun resetContextDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val manager = SmartInputManager(context)

        try {
            manager.updateSentenceContext("hello world")
            manager.resetContext()
        } catch (t: Throwable) {
            fail("resetContext should not throw, but got: ${t.message}")
        }
    }

    @Test
    fun applyCloudSnapshotWithMinimalPayloadDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val manager = SmartInputManager(context)
        val snapshot = buildJsonObject {
            putJsonObject("adaptive_dict") {}
            putJsonObject("personal_dict") {}
            putJsonObject("phrase_predict") {}
            putJsonObject("sentence_predict") {}
            putJsonObject("style_profile") {}
            putJsonObject("typing_habits") {}
            putJsonObject("correction_learn") {}
        }

        try {
            manager.applyCloudSnapshot(snapshot)
        } catch (t: Throwable) {
            fail("applyCloudSnapshot should not throw, but got: ${t.message}")
        }
    }

    @Test
    fun persistAndMaintenanceDoNotThrow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val manager = SmartInputManager(context)

        try {
            manager.persist()
            manager.maintenance()
        } catch (t: Throwable) {
            fail("persist/maintenance should not throw, but got: ${t.message}")
        }
    }
}
