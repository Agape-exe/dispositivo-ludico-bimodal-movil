package com.taller.app.bimodal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCaptureEventMapperTest {

    @Test
    fun transcribedWithText_mapsToSpeechCaptured() {
        val event = SpeechCaptureEventMapper.toEvent(
            SpeechCaptureOutcome.Transcribed("el cielo es azul")
        )

        assertTrue(event is BimodalInteractionEvent.SpeechCaptured)
        assertEquals(
            "el cielo es azul",
            (event as BimodalInteractionEvent.SpeechCaptured).transcription
        )
    }

    @Test
    fun transcribedWithSurroundingSpaces_isTrimmed() {
        val event = SpeechCaptureEventMapper.toEvent(
            SpeechCaptureOutcome.Transcribed("   gato   ")
        )

        assertEquals(
            "gato",
            (event as BimodalInteractionEvent.SpeechCaptured).transcription
        )
    }

    @Test
    fun transcribedBlank_mapsToNoResponse() {
        val event = SpeechCaptureEventMapper.toEvent(
            SpeechCaptureOutcome.Transcribed("    ")
        )

        assertEquals(BimodalInteractionEvent.NoResponse, event)
    }

    @Test
    fun noSpeech_mapsToNoResponse() {
        val event = SpeechCaptureEventMapper.toEvent(SpeechCaptureOutcome.NoSpeech)

        assertEquals(BimodalInteractionEvent.NoResponse, event)
    }

    @Test
    fun failed_mapsToSpeechFailedWithReason() {
        val event = SpeechCaptureEventMapper.toEvent(
            SpeechCaptureOutcome.Failed("Error de red.")
        )

        assertTrue(event is BimodalInteractionEvent.SpeechFailed)
        assertEquals("Error de red.", (event as BimodalInteractionEvent.SpeechFailed).reason)
    }

    @Test
    fun failedOutcome_neverMapsToIncorrectAnswer() {
        // Un fallo tecnico no debe interpretarse como respuesta incorrecta.
        val event = SpeechCaptureEventMapper.toEvent(
            SpeechCaptureOutcome.Failed("El reconocedor está ocupado.")
        )

        assertTrue(event is BimodalInteractionEvent.SpeechFailed)
    }
}
