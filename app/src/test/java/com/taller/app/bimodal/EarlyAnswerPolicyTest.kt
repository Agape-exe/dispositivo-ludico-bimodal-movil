package com.taller.app.bimodal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyAnswerPolicyTest {

    @Test
    fun opensWindowOnlyOnRetriesWithMicAndSilence() {
        assertTrue(
            EarlyAnswerPolicy.shouldOpenEarlyWindow(
                enabled = true, attemptNumber = 2, audioGranted = true, toyVoiceSpeaking = false
            )
        )
        assertTrue(
            EarlyAnswerPolicy.shouldOpenEarlyWindow(
                enabled = true, attemptNumber = 3, audioGranted = true, toyVoiceSpeaking = false
            )
        )
    }

    @Test
    fun neverOpensWindowOnFirstAttempt() {
        assertFalse(
            EarlyAnswerPolicy.shouldOpenEarlyWindow(
                enabled = true, attemptNumber = 1, audioGranted = true, toyVoiceSpeaking = false
            )
        )
    }

    @Test
    fun neverOpensWindowWhileSevenIsSpeaking() {
        // Sin control de eco, escuchar durante el TTS capturaria la voz de Seven.
        assertFalse(
            EarlyAnswerPolicy.shouldOpenEarlyWindow(
                enabled = true, attemptNumber = 2, audioGranted = true, toyVoiceSpeaking = true
            )
        )
    }

    @Test
    fun respectsDisabledSettingAndMissingMic() {
        assertFalse(
            EarlyAnswerPolicy.shouldOpenEarlyWindow(
                enabled = false, attemptNumber = 2, audioGranted = true, toyVoiceSpeaking = false
            )
        )
        assertFalse(
            EarlyAnswerPolicy.shouldOpenEarlyWindow(
                enabled = true, attemptNumber = 2, audioGranted = false, toyVoiceSpeaking = false
            )
        )
    }

    @Test
    fun clampsWindowDurationToSafeRange() {
        assertEquals(500L, EarlyAnswerPolicy.effectiveWindowMs(100))
        assertEquals(2000L, EarlyAnswerPolicy.effectiveWindowMs(2000))
        assertEquals(4000L, EarlyAnswerPolicy.effectiveWindowMs(90_000))
    }
}
