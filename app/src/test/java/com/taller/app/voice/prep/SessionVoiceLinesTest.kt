package com.taller.app.voice.prep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionVoiceLinesTest {

    private fun question(
        id: Long,
        order: Int,
        childFriendly: String? = null,
        raw: String = "Pregunta cruda $id",
        hint1: String? = null,
        positive: String? = null,
        retry: String? = null
    ) = QuestionVoiceSource(
        questionId = id,
        orderIndex = order,
        childFriendlyQuestionText = childFriendly,
        rawQuestionText = raw,
        hint1 = hint1,
        hint2 = null,
        hint3 = null,
        positiveFeedbackText = positive,
        supportiveFeedbackText = null,
        retryPromptText = retry
    )

    @Test
    fun collectIncludesScriptAndGenericBank() {
        val source = SessionVoiceSource(
            introText = "Hola, soy Seven",
            closingText = "Hasta pronto",
            questions = listOf(
                question(
                    id = 1,
                    order = 1,
                    childFriendly = "Dime un animal que viva en casa",
                    hint1 = "Hace guau",
                    positive = "Muy bien",
                    retry = "Probemos otra vez"
                )
            )
        )

        val lines = SessionVoiceLines.collect(source)

        assertTrue(lines.any { it.role == VoiceLineRole.INTRO && it.text == "Hola, soy Seven" })
        assertTrue(lines.any { it.role == VoiceLineRole.CLOSING && it.text == "Hasta pronto" })
        assertTrue(lines.any { it.role == VoiceLineRole.QUESTION && it.text == "Dime un animal que viva en casa" })
        assertTrue(lines.any { it.role == VoiceLineRole.HINT_1 })
        assertTrue(lines.any { it.role == VoiceLineRole.POSITIVE_FEEDBACK })
        assertTrue(lines.any { it.role == VoiceLineRole.RETRY_PROMPT })
        assertTrue(lines.any { it.role == VoiceLineRole.GENERIC })
    }

    @Test
    fun questionUsesChildFriendlyWhenPresentElseRaw() {
        val source = SessionVoiceSource(
            introText = null,
            closingText = null,
            questions = listOf(
                question(id = 1, order = 1, childFriendly = "Amigable", raw = "Cruda"),
                question(id = 2, order = 2, childFriendly = null, raw = "Solo cruda")
            )
        )

        val questionLines = SessionVoiceLines.collect(source, includeGeneric = false)
            .filter { it.role == VoiceLineRole.QUESTION }
            .map { it.text }

        assertTrue(questionLines.contains("Amigable"))
        assertTrue(questionLines.contains("Solo cruda"))
        assertFalse(questionLines.contains("Cruda"))
    }

    @Test
    fun blankAndInvalidTextsAreSkipped() {
        val source = SessionVoiceSource(
            introText = "   ",
            closingText = null,
            questions = listOf(
                question(id = 1, order = 1, childFriendly = "Pregunta válida", hint1 = "")
            )
        )

        val lines = SessionVoiceLines.collect(source, includeGeneric = false)

        assertFalse(lines.any { it.role == VoiceLineRole.INTRO })
        assertFalse(lines.any { it.role == VoiceLineRole.HINT_1 })
        assertEquals(1, lines.size)
        assertEquals(VoiceLineRole.QUESTION, lines.first().role)
    }

    @Test
    fun duplicateTextsAreCollapsedOnce() {
        val source = SessionVoiceSource(
            introText = "Muy bien",
            closingText = null,
            questions = listOf(
                // Mismo texto que el intro, con espacios distintos: debe deduplicarse.
                question(id = 1, order = 1, childFriendly = "Muy bien", positive = "  Muy   bien ")
            )
        )

        val lines = SessionVoiceLines.collect(source, includeGeneric = false)
        val normalized = lines.mapNotNull { SessionVoiceLines.dedupeKey(it.text) }

        // La normalizacion coincide con la de la clave de cache: recorta y colapsa
        // espacios, conservando mayusculas/acentos.
        assertEquals(normalized.size, normalized.toSet().size)
        assertEquals(1, normalized.count { it == "Muy bien" })
    }

    @Test
    fun collectIncludesTimerSafeLinesForCachePreparation() {
        val source = SessionVoiceSource(
            introText = null,
            closingText = null,
            activityTitle = "Animales",
            activityTopic = "animales",
            questions = listOf(
                question(
                    id = 1,
                    order = 1,
                    childFriendly = "El perro dice guau. Que sonido hace?",
                    raw = "Que sonido hace el perro?"
                ).copy(
                    expectedAnswer = "guau",
                    keywords = listOf("perro")
                )
            )
        )

        val lines = SessionVoiceLines.collect(source, includeGeneric = false)
        val intro = "¡Hola! Soy Seven. Hoy vamos a explorar sobre animales. Te haré unas preguntitas y puedes responder con calma."
        val closing = "Terminamos por ahora. ¡Hasta la próxima aventura!"

        assertTrue(lines.any { it.role == VoiceLineRole.INTRO && it.text == intro })
        assertTrue(lines.any { it.role == VoiceLineRole.CLOSING && it.text == closing })
        assertTrue(lines.any { it.role == VoiceLineRole.QUESTION && it.text == "Que sonido hace el perro?" })
    }
}
