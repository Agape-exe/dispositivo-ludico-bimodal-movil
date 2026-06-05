package com.taller.app.bimodal.feedback

import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.semantic.SemanticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import kotlin.random.Random

class GeneralTeacherFeedbackGeneratorTest {

    private fun newGenerator(seed: Long = 1L) =
        GeneralTeacherFeedbackGenerator(random = Random(seed))

    /** Minusculas y sin acentos, para comparaciones robustas de contenido. */
    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
    }

    private fun contextFor(
        state: BimodalInteractionState,
        canRetry: Boolean = false,
        questionIndex: Int = 0,
        currentAttempt: Int = 1,
        maxAttempts: Int = 3,
        semanticResult: SemanticResult? = null,
        expectedAnswer: String? = null
    ) = GeneralTeacherFeedbackContext(
        state = state,
        questionIndex = questionIndex,
        currentAttempt = currentAttempt,
        maxAttempts = maxAttempts,
        canRetry = canRetry,
        semanticResult = semanticResult,
        expectedAnswer = expectedAnswer
    )

    // ----- Cobertura del banco --------------------------------------------------

    @Test
    fun everyCategory_returnsNonEmptyMessage() {
        val generator = newGenerator()
        for (type in GeneralTeacherFeedbackType.values()) {
            val message = generator.message(type)
            assertEquals(type, message.type)
            assertTrue("La categoria $type devolvio texto vacio", message.text.isNotBlank())
            assertTrue(message.generationLatencyNanos >= 0)
        }
    }

    @Test
    fun everyCategory_hasAtLeastMinimumVariety() {
        // Garantiza variedad suficiente para evitar repeticiones monotonas.
        val expectedMinimum = mapOf(
            GeneralTeacherFeedbackType.CORRECT to 12,
            GeneralTeacherFeedbackType.INCORRECT_RETRY to 12,
            GeneralTeacherFeedbackType.INCORRECT_NEXT to 8,
            GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY to 10,
            GeneralTeacherFeedbackType.NOT_INTERPRETABLE_NEXT to 6,
            GeneralTeacherFeedbackType.NO_RESPONSE_RETRY to 10,
            GeneralTeacherFeedbackType.NO_RESPONSE_NEXT to 6,
            GeneralTeacherFeedbackType.TIME_EXPIRED_RETRY to 8,
            GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT to 6,
            GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY to 6,
            GeneralTeacherFeedbackType.TECHNICAL_ERROR_NEXT to 6,
            GeneralTeacherFeedbackType.SESSION_START to 6,
            GeneralTeacherFeedbackType.QUESTION_INTRO to 8,
            GeneralTeacherFeedbackType.SESSION_COMPLETED to 8
        )
        for ((type, minimum) in expectedMinimum) {
            val count = GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES.getValue(type).size
            assertTrue(
                "La categoria $type tiene $count frases, se esperaban al menos $minimum",
                count >= minimum
            )
        }
    }

    // ----- Mapeo de estado a categoria -----------------------------------------

    @Test
    fun mapping_usesRetryVariantWhenAttemptsRemain() {
        val generator = newGenerator()
        assertEquals(
            GeneralTeacherFeedbackType.INCORRECT_RETRY,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.FEEDBACK_INCORRECT, canRetry = true))
        )
        assertEquals(
            GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE, canRetry = true))
        )
        assertEquals(
            GeneralTeacherFeedbackType.NO_RESPONSE_RETRY,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.FEEDBACK_NO_RESPONSE, canRetry = true))
        )
        assertEquals(
            GeneralTeacherFeedbackType.TIME_EXPIRED_RETRY,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.TIME_EXPIRED, canRetry = true))
        )
        assertEquals(
            GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR, canRetry = true))
        )
    }

    @Test
    fun mapping_usesNextVariantWhenNoAttemptsRemain() {
        val generator = newGenerator()
        assertEquals(
            GeneralTeacherFeedbackType.INCORRECT_NEXT,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.FEEDBACK_INCORRECT, canRetry = false))
        )
        assertEquals(
            GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.TIME_EXPIRED, canRetry = false))
        )
    }

    @Test
    fun mapping_sessionStartOnlyForFirstQuestion() {
        val generator = newGenerator()
        assertEquals(
            GeneralTeacherFeedbackType.SESSION_START,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.WAITING_FOR_FACE, questionIndex = 0))
        )
        assertEquals(
            GeneralTeacherFeedbackType.QUESTION_INTRO,
            generator.feedbackTypeFor(contextFor(BimodalInteractionState.WAITING_FOR_FACE, questionIndex = 2))
        )
    }

    @Test
    fun mapping_returnsNullForTransitionalStates() {
        val generator = newGenerator()
        assertNull(generator.feedbackTypeFor(contextFor(BimodalInteractionState.LISTENING)))
        assertNull(generator.feedbackTypeFor(contextFor(BimodalInteractionState.EVALUATING)))
        assertNull(generator.feedbackTypeFor(contextFor(BimodalInteractionState.IDLE)))
        assertNull(generator.generate(contextFor(BimodalInteractionState.TRANSCRIBING)))
    }

    // ----- Contenido por categoria ---------------------------------------------

    @Test
    fun correct_returnsPositivePhrases() {
        val generator = newGenerator()
        val positiveMarkers = listOf("bien", "excelente", "perfecto", "lograste", "valida", "funciona")
        repeat(60) {
            val text = normalize(generator.message(GeneralTeacherFeedbackType.CORRECT).text)
            assertTrue(
                "Frase correcta sin tono positivo: $text",
                positiveMarkers.any { text.contains(it) }
            )
        }
    }

    @Test
    fun incorrectRetry_doesNotRevealExpectedAnswer() {
        val generator = newGenerator()
        val expected = "azul"
        repeat(100) {
            val message = generator.generate(
                contextFor(
                    BimodalInteractionState.FEEDBACK_INCORRECT,
                    canRetry = true,
                    semanticResult = SemanticResult.INCORRECT,
                    expectedAnswer = expected
                )
            )
            assertNotNull(message)
            assertFalse(
                "La frase de reintento revelo la respuesta esperada: ${message!!.text}",
                normalize(message.text).contains(normalize(expected))
            )
        }
    }

    @Test
    fun incorrectNext_doesNotHumiliate() {
        val generator = newGenerator()
        val humiliating = listOf("tonto", "mal hecho", "no sabes", "siempre te equivocas", "eres malo")
        repeat(60) {
            val text = normalize(generator.message(GeneralTeacherFeedbackType.INCORRECT_NEXT).text)
            assertTrue("Frase vacia", text.isNotBlank())
            assertTrue(
                "Frase humillante detectada: $text",
                humiliating.none { text.contains(it) }
            )
        }
    }

    @Test
    fun noResponseRetry_asksToRepeat() {
        val generator = newGenerator()
        val markers = listOf("intent", "prob", "responde", "nuevamente", "otra vez", "de nuevo")
        repeat(60) {
            val text = normalize(generator.message(GeneralTeacherFeedbackType.NO_RESPONSE_RETRY).text)
            assertTrue(
                "Frase de sin-respuesta no invita a repetir: $text",
                markers.any { text.contains(it) }
            )
        }
    }

    @Test
    fun notInterpretableRetry_asksToSpeakClearlyOrRepeat() {
        val generator = newGenerator()
        val markers = listOf(
            "claro", "repet", "repit", "entend", "escuch", "otra vez", "de nuevo", "nuevamente", "probemos", "intent"
        )
        repeat(60) {
            val text = normalize(generator.message(GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY).text)
            assertTrue(
                "Frase no-interpretable no pide hablar claro ni repetir: $text",
                markers.any { text.contains(it) }
            )
        }
    }

    @Test
    fun timeExpiredNext_indicatesAdvancing() {
        val generator = newGenerator()
        val markers = listOf("continu", "sig", "avanz", "pasemos", "otra pregunta")
        repeat(40) {
            val text = normalize(generator.message(GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT).text)
            assertTrue(
                "Frase de tiempo agotado no indica avance: $text",
                markers.any { text.contains(it) }
            )
        }
    }

    @Test
    fun technicalErrorRetry_doesNotBlameChild() {
        val generator = newGenerator()
        val blame = listOf("te equivoc", "tu culpa", "fallaste", "lo hiciste mal", "tu error")
        repeat(40) {
            val text = normalize(generator.message(GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY).text)
            assertTrue("Frase vacia", text.isNotBlank())
            assertTrue(
                "Frase de error tecnico culpa al nino: $text",
                blame.none { text.contains(it) }
            )
        }
    }

    // ----- Seleccion variada y frases prohibidas -------------------------------

    @Test
    fun doesNotRepeatSamePhraseConsecutively_whenMultipleAvailable() {
        val generator = newGenerator()
        for (type in GeneralTeacherFeedbackType.values()) {
            val available = GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES.getValue(type).size
            if (available < 2) continue
            var previous: String? = null
            repeat(200) {
                val current = generator.message(type).text
                assertTrue(
                    "La categoria $type repitio la frase consecutiva: $current",
                    current != previous
                )
                previous = current
            }
        }
    }

    @Test
    fun forbiddenMisleadingPhrases_neverAppear() {
        val forbidden = listOf(
            "mencionaste una idea importante",
            "vas por buen camino",
            "estas cerca",
            "casi lo tienes",
            "vas bien"
        )
        for ((type, phrases) in GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES) {
            for (phrase in phrases) {
                val normalized = normalize(phrase)
                for (banned in forbidden) {
                    assertFalse(
                        "La categoria $type contiene una frase enganosa: $phrase",
                        normalized.contains(banned)
                    )
                }
            }
        }
    }
}
