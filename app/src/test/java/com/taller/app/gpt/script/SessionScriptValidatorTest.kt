package com.taller.app.gpt.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScriptValidatorTest {

    private fun input() = SessionScriptInput(
        sessionName = "Animales",
        topic = "Animales domésticos",
        description = "",
        objective = "",
        ageLevel = "3 a 5 años",
        contextNotes = "",
        questions = listOf(
            SessionScriptQuestionInput(
                questionId = 1L,
                orderIndex = 1,
                questionText = "Menciona un animal doméstico.",
                referenceAnswer = "perro"
            )
        )
    )

    private fun question(
        childFriendly: String = "Dime un animalito que viva con las personas",
        hint1: String = "Algunos viven en casa",
        positive: String = "Muy bien",
        supportive: String = "Casi, sigamos"
    ) = QuestionScript(
        questionId = 1L,
        orderIndex = 1,
        childFriendlyQuestionText = childFriendly,
        hintLevel1 = hint1,
        hintLevel2 = "Tiene cuatro patas",
        hintLevel3 = "Le gusta jugar",
        positiveFeedbackText = positive,
        supportiveFeedbackText = supportive,
        retryPromptText = "Probemos otra vez",
        answerReferenceWarning = "",
        suggestedReferenceAnswer = "perro, gato"
    )

    private fun script(q: QuestionScript = question()) = SessionScript(
        intro = "Hola explorador",
        closing = "Adios explorador",
        toneNotes = "calido",
        pedagogicalWarnings = "",
        questions = listOf(q)
    )

    @Test
    fun validScript_passes() {
        val result = SessionScriptValidator.validate(script(), input())
        assertTrue(result.issues.toString(), result.isValid)
    }

    @Test
    fun missingIntro_fails() {
        val result = SessionScriptValidator.validate(script().copy(intro = ""), input())
        assertFalse(result.isValid)
    }

    @Test
    fun hintRevealingAnswer_fails() {
        val result = SessionScriptValidator.validate(
            script(question(hint1 = "Es un perro")),
            input()
        )
        assertFalse(result.isValid)
    }

    @Test
    fun emptyFeedback_fails() {
        val result = SessionScriptValidator.validate(
            script(question(positive = "")),
            input()
        )
        assertFalse(result.isValid)
    }

    @Test
    fun aiMention_fails() {
        val result = SessionScriptValidator.validate(
            script(question(childFriendly = "Soy una IA y te pregunto algo")),
            input()
        )
        assertFalse(result.isValid)
    }

    @Test
    fun tooLongText_fails() {
        val longText = "a".repeat(SessionScriptValidator.MAX_SPOKEN_CHARS + 10)
        val result = SessionScriptValidator.validate(
            script(question(childFriendly = longText)),
            input()
        )
        assertFalse(result.isValid)
    }
}
