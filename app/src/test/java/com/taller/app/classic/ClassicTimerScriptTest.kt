package com.taller.app.classic

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicTimerScriptTest {

    private fun activity(
        intro: String? = null,
        closing: String? = null
    ) = LearningActivity(
        id = "1",
        title = "Animales",
        mode = OperationMode.CLASSIC,
        questions = emptyList(),
        generatedIntroText = intro,
        generatedClosingText = closing,
        topic = "animales"
    )

    private fun question(friendly: String? = null) = LearningQuestion(
        id = "10",
        questionText = "Que sonido hace el perro?",
        expectedAnswer = "guau",
        keywords = listOf("perro"),
        maxTimeSeconds = 10,
        maxAttempts = 1,
        childFriendlyQuestionText = friendly
    )

    @Test
    fun introUsesGeneratedWhenNeutralAndMentionsTopic() {
        val text = ClassicTimerScript.intro(
            activity(intro = "Hola, soy Seven. Hoy vamos a explorar animales con calma.")
        )

        assertEquals("Hola, soy Seven. Hoy vamos a explorar animales con calma.", text)
    }

    @Test
    fun introFallsBackWhenGeneratedContainsFeedback() {
        val text = ClassicTimerScript.intro(
            activity(intro = "Muy bien, hoy veremos animales.")
        )

        assertTrue(text.contains("animales"))
        assertFalse(text.lowercase().contains("muy bien"))
    }

    @Test
    fun questionUsesFriendlyTextWhenItDoesNotRevealAnswer() {
        val text = ClassicTimerScript.questionText(
            question(friendly = "Escucha esta preguntita: que sonido hace este animal?")
        )

        assertEquals("Escucha esta preguntita: que sonido hace este animal?", text)
    }

    @Test
    fun questionFallsBackWhenFriendlyRevealsAnswer() {
        val text = ClassicTimerScript.questionText(
            question(friendly = "El perro dice guau. Que sonido hace el perro?")
        )

        assertEquals("Que sonido hace el perro?", text)
    }

    @Test
    fun closingFallsBackWhenGeneratedMentionsWrongResult() {
        val text = ClassicTimerScript.closing(
            activity(closing = "Fallaste algunas, pero terminamos.")
        )

        assertEquals("Terminamos por ahora. ¡Hasta la próxima aventura!", text)
    }
}
