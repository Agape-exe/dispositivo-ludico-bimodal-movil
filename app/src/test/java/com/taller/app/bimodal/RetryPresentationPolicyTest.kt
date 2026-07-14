package com.taller.app.bimodal

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPresentationPolicyTest {

    @Test
    fun ordenDelReintento_escuchaSiempreAlFinal() {
        val order = RetryPresentationPolicy.STEP_ORDER
        assertEquals(RetryPresentationPolicy.Step.FEEDBACK, order.first())
        assertEquals(RetryPresentationPolicy.Step.OPEN_LISTENING, order.last())
        assertTrue(
            order.indexOf(RetryPresentationPolicy.Step.ANNOUNCE_REPEAT) <
                order.indexOf(RetryPresentationPolicy.Step.QUESTION)
        )
        assertTrue(
            order.indexOf(RetryPresentationPolicy.Step.QUESTION) <
                order.indexOf(RetryPresentationPolicy.Step.OPEN_LISTENING)
        )
    }

    @Test
    fun avisos_noSonUnIntentaloDeNuevoAislado() {
        for (phrase in RetryPresentationPolicy.ANNOUNCE_PHRASES) {
            val text = phrase.lowercase()
            assertFalse("Aviso ambiguo: $phrase", text.contains("intentalo de nuevo"))
            assertFalse("Aviso ambiguo: $phrase", text.contains("inténtalo de nuevo"))
            // Todos los avisos dejan claro que la pregunta se repite.
            assertTrue(
                "El aviso no anuncia la repeticion: $phrase",
                text.contains("repito") || text.contains("escucha")
            )
        }
    }

    @Test
    fun segmentosDeVoz_avisoPrimeroPreguntaDespues() {
        val segments = RetryPresentationPolicy.buildRetrySpeechSegments(
            questionText = "Dime un animal que viva en la granja.",
            random = Random(3)
        )

        assertEquals(2, segments.size)
        assertTrue(RetryPresentationPolicy.ANNOUNCE_PHRASES.contains(segments[0]))
        assertEquals("Dime un animal que viva en la granja.", segments[1])
    }

    @Test
    fun politicaDeVentanaTemprana_nuncaEscuchaMientrasSevenHabla() {
        assertFalse(
            EarlyAnswerPolicy.shouldOpenEarlyWindow(
                enabled = true,
                attemptNumber = 2,
                audioGranted = true,
                toyVoiceSpeaking = true
            )
        )
    }

    @Test
    fun ventanaTemprana_desactivadaPorDefectoEnConfiguracion() {
        assertFalse(com.taller.app.settings.AppSettings.defaults().earlyAnswerCaptureEnabled)
    }
}
