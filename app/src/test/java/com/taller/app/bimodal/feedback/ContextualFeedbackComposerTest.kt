package com.taller.app.bimodal.feedback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualFeedbackComposerTest {

    private val question = "Dime un animal que viva en la granja."
    private val forbiddenWords = listOf("incorrecto", "mal", "fallaste")

    private fun composer(seed: Int = 1) = ContextualFeedbackComposer(Random(seed))

    private fun allSeeds(block: (ContextualFeedbackComposer) -> ContextualFeedback?): List<ContextualFeedback> =
        (0 until 20).mapNotNull { block(composer(it)) }

    @Test
    fun correcto_pavoEnGranja_mencionaPavoYGranja() {
        val feedback = composer().composeCorrect(question, "un pavo")

        assertNotNull(feedback)
        val text = feedback!!.text.lowercase()
        assertTrue(text.contains("pavo"))
        assertTrue(text.contains("la granja"))
        assertEquals(ContextualFeedbackType.CORRECT_CONTEXTUAL, feedback.type)
    }

    @Test
    fun incorrecto_rinoceronteEnGranja_explicaSuaveYMencionaLaRespuesta() {
        val feedback = composer().composeIncorrectRetry(question, "un rinoceronte")

        assertNotNull(feedback)
        val text = feedback!!.text.lowercase()
        assertTrue(text.contains("rinoceronte"))
        assertTrue(text.contains("granja"))
        assertEquals(ContextualFeedbackType.RETRY_CONTEXTUAL, feedback.type)
    }

    @Test
    fun feedback_esBreve_maximoDosOraciones() {
        val all = allSeeds { it.composeCorrect(question, "pavo") } +
            allSeeds { it.composeIncorrectRetry(question, "rinoceronte") } +
            allSeeds { it.composeIncorrectFinal("rinoceronte") }
        for (feedback in all) {
            val sentences = feedback.text.count { it == '.' || it == '!' || it == '?' }
            assertTrue(
                "Demasiado largo: ${feedback.text}",
                feedback.text.length <= 120 && sentences <= 3
            )
        }
    }

    @Test
    fun feedback_nuncaUsaPalabrasProhibidas() {
        val all = allSeeds { it.composeCorrect(question, "pavo") } +
            allSeeds { it.composeIncorrectRetry(question, "rinoceronte") } +
            allSeeds { it.composeIncorrectFinal("rinoceronte") }
        for (feedback in all) {
            val text = feedback.text.lowercase()
            for (word in forbiddenWords) {
                assertFalse("Contiene '$word': ${feedback.text}", text.contains(word))
            }
        }
    }

    @Test
    fun reintento_noRevelaLaRespuestaEsperada() {
        // La respuesta esperada nunca entra al compositor de reintentos: solo la
        // pregunta y lo que dijo el nino.
        val all = allSeeds { it.composeIncorrectRetry(question, "rinoceronte") }
        for (feedback in all) {
            assertFalse(feedback.text.lowercase().contains("vaca"))
        }
    }

    @Test
    fun transcripcionInsegura_devuelveNullParaUsarElBanco() {
        val c = composer()
        assertNull(c.composeCorrect(question, null))
        assertNull(c.composeCorrect(question, "   "))
        assertNull(c.composeCorrect(question, "a"))
        assertNull(c.composeCorrect(question, "pavo123"))
        assertNull(c.composeCorrect(question, "una frase demasiado larga que no es segura de repetir en voz alta"))
        assertNull(c.composeIncorrectRetry(question, "<script>"))
        assertNull(c.composeIncorrectFinal(""))
    }

    @Test
    fun safeAnswer_quitaArticulosIniciales() {
        assertEquals("pavo", ContextualFeedbackComposer.safeAnswer("un pavo"))
        assertEquals("vaca", ContextualFeedbackComposer.safeAnswer("Es una vaca"))
        assertEquals("gato", ContextualFeedbackComposer.safeAnswer("el gato"))
    }

    @Test
    fun extractPlace_reconoceElLugarDeLaPregunta() {
        assertEquals(
            "la granja",
            ContextualFeedbackComposer.extractPlace("Dime un animal que viva en la granja.")
        )
        assertEquals(
            "el mar",
            ContextualFeedbackComposer.extractPlace("Dime un animal que vive en el mar")
        )
        assertNull(ContextualFeedbackComposer.extractPlace("¿Qué sonido hace el?"))
        assertNull(ContextualFeedbackComposer.extractPlace(null))
    }

    @Test
    fun sinLugar_igualRefuerzaConLaRespuesta() {
        val feedback = composer().composeCorrect("¿Qué animal dice muu?", "vaca")

        assertNotNull(feedback)
        assertTrue(feedback!!.text.lowercase().contains("vaca"))
    }

    @Test
    fun cierreSinIntentos_esNeutralYAgradece() {
        val feedback = composer().composeIncorrectFinal("rinoceronte")

        assertNotNull(feedback)
        assertEquals(ContextualFeedbackType.FINAL_NEUTRAL, feedback!!.type)
        assertFalse(feedback.text.lowercase().contains("intenta"))
    }
}
