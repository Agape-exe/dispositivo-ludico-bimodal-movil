package com.taller.app.gpt.judge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAnswerJudgeParserTest {

    private fun input(
        attemptsRemaining: Boolean = true,
        referenceAnswer: String = "perro"
    ) = OpenAnswerJudgeInput(
        questionText = "Menciona un animal domestico",
        childFriendlyQuestionText = null,
        referenceAnswer = referenceAnswer,
        childAnswer = "el gato",
        ageRange = "3-5",
        topic = "animales",
        classContext = null,
        currentAttempt = 1,
        attemptsRemaining = attemptsRemaining,
        availableHint = null,
        localResult = "INCORRECT"
    )

    @Test
    fun parsesValidCorrectVerdict() {
        val json = """
            {
              "decision": "CORRECT",
              "confidence": 0.92,
              "reason": "ejemplo valido de animal domestico",
              "acceptedAsEquivalent": true,
              "shouldRetry": false,
              "feedbackType": "POSITIVE",
              "safeHintLevel": 0,
              "revealsAnswer": false,
              "normalizedChildAnswer": "gato",
              "normalizedExpectedConcept": "animal domestico"
            }
        """.trimIndent()
        val verdict = OpenAnswerJudgeParser.parse(json, input())
        assertEquals(JudgeDecision.CORRECT, verdict.decision)
        assertTrue(verdict.acceptedAsEquivalent)
        assertEquals(0.92, verdict.confidence, 0.001)
        assertEquals("gato", verdict.normalizedChildAnswer)
    }

    @Test
    fun parsesIncorrectVerdict() {
        val json = """
            { "decision": "INCORRECT", "confidence": 0.8, "reason": "no responde",
              "acceptedAsEquivalent": false, "shouldRetry": true, "feedbackType": "RETRY",
              "safeHintLevel": 1, "revealsAnswer": false,
              "normalizedChildAnswer": "miau", "normalizedExpectedConcept": "guau" }
        """.trimIndent()
        val verdict = OpenAnswerJudgeParser.parse(json, input(referenceAnswer = "guau"))
        assertEquals(JudgeDecision.INCORRECT, verdict.decision)
    }

    @Test
    fun clampsConfidenceOutOfRange() {
        val json = """{ "decision": "UNCERTAIN", "confidence": 5.0 }"""
        val verdict = OpenAnswerJudgeParser.parse(json, input())
        assertEquals(1.0, verdict.confidence, 0.001)
    }

    @Test
    fun hidesAnswerWhenAttemptsRemainAndModelRevealsIt() {
        // El modelo marca revealsAnswer=true con intentos restantes: la app lo oculta.
        val json = """
            { "decision": "INCORRECT", "confidence": 0.7, "reason": "la respuesta es perro",
              "revealsAnswer": true }
        """.trimIndent()
        val verdict = OpenAnswerJudgeParser.parse(json, input(attemptsRemaining = true, referenceAnswer = "perro"))
        assertFalse(verdict.revealsAnswer)
        assertFalse(verdict.reason.contains("perro"))
    }

    @Test
    fun redactsReasonThatLeaksAnswer() {
        val json = """
            { "decision": "INCORRECT", "confidence": 0.6, "reason": "deberia decir perro" }
        """.trimIndent()
        val verdict = OpenAnswerJudgeParser.parse(json, input(attemptsRemaining = true, referenceAnswer = "perro"))
        assertFalse(verdict.reason.contains("perro"))
    }

    @Test
    fun throwsOnInvalidJson() {
        assertThrows(OpenAnswerJudgeParseException::class.java) {
            OpenAnswerJudgeParser.parse("no soy json", input())
        }
    }

    @Test
    fun throwsOnMissingDecision() {
        assertThrows(OpenAnswerJudgeParseException::class.java) {
            OpenAnswerJudgeParser.parse("""{ "confidence": 0.5 }""", input())
        }
    }

    @Test
    fun parsesAcceptanceTypeWhenCorrect() {
        val json = """{ "decision": "CORRECT", "confidence": 0.8, "acceptedAsEquivalent": true,
            "acceptanceType": "EQUIVALENT" }"""
        val verdict = OpenAnswerJudgeParser.parse(json, input())
        assertEquals(JudgeAcceptanceType.EQUIVALENT, verdict.acceptanceType)
    }

    @Test
    fun ignoresAcceptanceTypeWhenIncorrect() {
        // Si no acepto, no se conserva un tipo de aceptacion.
        val json = """{ "decision": "INCORRECT", "confidence": 0.8, "acceptanceType": "LITERAL" }"""
        val verdict = OpenAnswerJudgeParser.parse(json, input())
        assertEquals(JudgeAcceptanceType.NONE, verdict.acceptanceType)
    }

    @Test
    fun unknownAcceptanceTypeBecomesNone() {
        val json = """{ "decision": "CORRECT", "confidence": 0.8, "acceptanceType": "loquesea" }"""
        val verdict = OpenAnswerJudgeParser.parse(json, input())
        assertEquals(JudgeAcceptanceType.NONE, verdict.acceptanceType)
    }

    @Test
    fun parsesFencedJson() {
        val json = "```json\n{ \"decision\": \"CORRECT\", \"confidence\": 0.5 }\n```"
        val verdict = OpenAnswerJudgeParser.parse(json, input())
        assertEquals(JudgeDecision.CORRECT, verdict.decision)
    }
}
