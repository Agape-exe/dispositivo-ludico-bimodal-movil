package com.taller.app.gpt.judge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAnswerJudgePromptTest {

    private fun input() = OpenAnswerJudgeInput(
        questionText = "Menciona un animal domestico",
        childFriendlyQuestionText = null,
        referenceAnswer = "perro",
        childAnswer = "gato",
        ageRange = "3-5",
        topic = "animales",
        classContext = null,
        currentAttempt = 1,
        attemptsRemaining = true,
        availableHint = null,
        localResult = "INCORRECT"
    )

    @Test
    fun includesAdditionalTeacherRulesInUserMessage() {
        val prompt = OpenAnswerJudgePrompt.build(
            input = input(),
            additionalRules = "No uses pistas que revelen respuestas."
        )
        assertTrue(prompt.userMessage.contains("Reglas adicionales configuradas"))
        assertTrue(prompt.userMessage.contains("No uses pistas"))
        assertTrue(prompt.systemInstruction.contains("No reveles la respuesta correcta"))
    }

    @Test
    fun limitsAdditionalTeacherRules() {
        val longRules = "a".repeat(4_500)
        val prompt = OpenAnswerJudgePrompt.build(input(), additionalRules = longRules)
        assertFalse(prompt.userMessage.contains("a".repeat(4_500)))
    }
}
