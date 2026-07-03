package com.taller.app.gpt.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScriptRecoveryTest {

    private fun input() = SessionScriptInput(
        sessionName = "Animales",
        topic = "Animales domésticos",
        description = "",
        objective = "",
        ageLevel = "3 a 5 años",
        contextNotes = "",
        questions = listOf(
            SessionScriptQuestionInput(1L, 1, "Menciona un animal doméstico.", "perro"),
            SessionScriptQuestionInput(2L, 2, "¿Qué come la vaca?", "pasto")
        )
    )

    private fun question(
        order: Int,
        id: Long,
        childFriendly: String = "Pregunta amigable $order",
        hint1: String = "Pista uno $order",
        positive: String = "Muy bien",
        supportive: String = "Sigamos pensando",
        retry: String = "Otra vez"
    ) = QuestionScript(
        questionId = id,
        orderIndex = order,
        childFriendlyQuestionText = childFriendly,
        hintLevel1 = hint1,
        hintLevel2 = "Pista dos $order",
        hintLevel3 = "Pista tres $order",
        positiveFeedbackText = positive,
        supportiveFeedbackText = supportive,
        retryPromptText = retry,
        answerReferenceWarning = "",
        suggestedReferenceAnswer = "ref $order"
    )

    private fun modelScript(vararg questions: QuestionScript) = SessionScript(
        intro = "Hola explorador",
        closing = "Adios explorador",
        toneNotes = "calido",
        pedagogicalWarnings = "",
        questions = questions.toList()
    )

    @Test
    fun fullyValidModel_keepsEverything() {
        val input = input()
        val local = SessionScriptLocalFallback.build(input)
        val model = modelScript(question(1, 1L), question(2, 2L))

        val result = SessionScriptRecovery.recover(model, local, input)

        assertEquals(0, result.repairedFields)
        assertTrue(result.usedModelContent)
        assertEquals("Hola explorador", result.script.intro)
        assertEquals("Pregunta amigable 1", result.script.questions[0].childFriendlyQuestionText)
    }

    @Test
    fun blankFieldsAreCompletedFromLocal() {
        val input = input()
        val local = SessionScriptLocalFallback.build(input)
        val model = modelScript(
            question(1, 1L, positive = ""), // feedback positivo vacío
            question(2, 2L)
        )

        val result = SessionScriptRecovery.recover(model, local, input)

        assertTrue(result.repairedFields >= 1)
        assertTrue(result.usedModelContent)
        assertTrue(result.script.questions[0].positiveFeedbackText.isNotBlank())
    }

    @Test
    fun hintRevealingAnswerIsReplaced() {
        val input = input()
        val local = SessionScriptLocalFallback.build(input)
        val model = modelScript(
            question(1, 1L, hint1 = "Es un perro"), // revela la respuesta de referencia
            question(2, 2L)
        )

        val result = SessionScriptRecovery.recover(model, local, input)

        assertTrue(result.repairedFields >= 1)
        assertFalse(result.script.questions[0].hintLevel1.lowercase().contains("perro"))
    }

    @Test
    fun hintWithVariantIsReplacedButRestKept() {
        // Una sola pista delata la respuesta por variante ("perritos"): se repara solo
        // esa pista y se conserva el resto del guion del modelo (reemplazo parcial).
        val input = input()
        val local = SessionScriptLocalFallback.build(input)
        val model = modelScript(
            question(1, 1L, hint1 = "Hay muchos perritos en casa"),
            question(2, 2L)
        )

        val result = SessionScriptRecovery.recover(model, local, input)

        assertTrue(result.usedModelContent)
        assertFalse(
            SessionScriptValidator.revealsAnswer(result.script.questions[0].hintLevel1, "perro")
        )
        // El resto del guion del modelo se conserva.
        assertEquals("Pregunta amigable 1", result.script.questions[0].childFriendlyQuestionText)
        assertEquals("Pista dos 1", result.script.questions[0].hintLevel2)
    }

    @Test
    fun emptyModelUsesLocalEntirely() {
        val input = input()
        val local = SessionScriptLocalFallback.build(input)
        val blankQuestion = QuestionScript(
            questionId = 1L, orderIndex = 1,
            childFriendlyQuestionText = "", hintLevel1 = "", hintLevel2 = "", hintLevel3 = "",
            positiveFeedbackText = "", supportiveFeedbackText = "", retryPromptText = "",
            answerReferenceWarning = "", suggestedReferenceAnswer = ""
        )
        val model = SessionScript(
            intro = "", closing = "", toneNotes = "", pedagogicalWarnings = "",
            questions = listOf(blankQuestion, blankQuestion.copy(questionId = 2L, orderIndex = 2))
        )

        val result = SessionScriptRecovery.recover(model, local, input)

        assertFalse("El modelo no aportó nada utilizable", result.usedModelContent)
        assertEquals(0, result.modelFieldsKept)
        assertTrue(result.script.intro.isNotBlank())
    }
}
