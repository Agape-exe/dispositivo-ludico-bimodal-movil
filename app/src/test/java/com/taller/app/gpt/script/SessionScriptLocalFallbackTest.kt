package com.taller.app.gpt.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScriptLocalFallbackTest {

    private fun input() = SessionScriptInput(
        sessionName = "Animales",
        topic = "Animales domésticos",
        description = "",
        objective = "",
        ageLevel = "3 a 5 años",
        contextNotes = "",
        questions = listOf(
            SessionScriptQuestionInput(1L, 1, "Menciona un animal doméstico.", "perro"),
            SessionScriptQuestionInput(2L, 2, "¿Qué come una vaca?", "pasto")
        )
    )

    @Test
    fun build_producesValidScript() {
        val input = input()
        val script = SessionScriptLocalFallback.build(input)

        assertTrue(script.intro.isNotBlank())
        assertTrue(script.closing.isNotBlank())
        assertEquals(2, script.questions.size)

        val result = SessionScriptValidator.validate(script, input)
        assertTrue(result.issues.toString(), result.isValid)
    }

    @Test
    fun build_keepsOriginalReferenceAsSuggestion() {
        val script = SessionScriptLocalFallback.build(input())
        assertEquals("perro", script.questions[0].suggestedReferenceAnswer)
        assertEquals("pasto", script.questions[1].suggestedReferenceAnswer)
    }

    @Test
    fun build_blankTopic_stillValid() {
        val input = input().copy(topic = "")
        val script = SessionScriptLocalFallback.build(input)
        val result = SessionScriptValidator.validate(script, input)
        assertTrue(result.issues.toString(), result.isValid)
    }
}
