package com.taller.app.classic

import com.taller.app.model.LocalMediationKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FixedTimerNeutralPhraseBankTest {

    private lateinit var bank: FixedTimerNeutralPhraseBank

    @Before
    fun setUp() {
        bank = FixedTimerNeutralPhraseBank()
    }

    // ----- Frases no vacías -----------------------------------------------------

    @Test
    fun allEntryPoints_returnNonBlankPhrases() {
        assertTrue(bank.getSessionStart().isNotBlank())
        assertTrue(bank.getRoundPrompt(1, "¿qué animal es?", isLast = false).isNotBlank())
        assertTrue(bank.getRoundPrompt(4, "¿qué animal es?", isLast = true).isNotBlank())
        assertTrue(bank.getAnswerReceived(isLast = false).isNotBlank())
        assertTrue(bank.getAnswerReceived(isLast = true).isNotBlank())
        assertTrue(bank.getTimeExpired(hadPartialResponse = true).isNotBlank())
        assertTrue(bank.getTimeExpired(hadPartialResponse = false).isNotBlank())
        assertTrue(bank.getTimeExpired(hadPartialResponse = false, isLast = true).isNotBlank())
        assertTrue(bank.getSessionCompleted().isNotBlank())
    }

    // ----- ROUND_QUESTION_PROMPT combina transición y pregunta ------------------

    @Test
    fun roundPrompt_combinesRoundNumberAndQuestion() {
        val question = "¿qué sonido hace el perro?"
        val phrase = bank.getRoundPrompt(3, question, isLast = false)
        assertTrue("Debe incluir el número de ronda", phrase.contains("3"))
        assertTrue("Debe incluir el texto de la pregunta", phrase.contains(question))
    }

    @Test
    fun roundPrompt_doesNotLeavePlaceholders() {
        repeat(30) {
            val phrase = bank.getRoundPrompt(2, "¿qué animal vive en el agua?", isLast = false)
            assertFalse("No debe quedar el marcador {n}", phrase.contains("{n}"))
            assertFalse("No debe quedar el marcador {question}", phrase.contains("{question}"))
        }
    }

    @Test
    fun lastRoundPrompt_doesNotLeavePlaceholders() {
        repeat(30) {
            val phrase = bank.getRoundPrompt(4, "¿qué animal vive en la granja?", isLast = true)
            assertFalse(phrase.contains("{n}"))
            assertFalse(phrase.contains("{question}"))
            assertTrue(phrase.contains("¿qué animal vive en la granja?"))
        }
    }

    // ----- mediationKey produce lead-in temático --------------------------------

    @Test
    fun roundPrompt_withDogMediation_usesDogLeadIn() {
        // Con la clave de perro, alguna de las plantitas temáticas debe aparecer.
        val phrases = (1..40).map {
            bank.getRoundPrompt(1, "¿qué sonido hace el perro?", isLast = false, LocalMediationKey.ANIMAL_DOG_SOUND)
        }
        assertTrue(
            "La mediación de perro debe usar un lead-in de perrito",
            phrases.any { it.contains("perrito") || it.contains("perro moviendo") }
        )
    }

    @Test
    fun roundPrompt_withFarmMediation_usesFarmLeadIn() {
        val phrases = (1..40).map {
            bank.getRoundPrompt(2, "menciona un animal de granja", isLast = false, LocalMediationKey.ANIMAL_FARM)
        }
        assertTrue(phrases.any { it.contains("granja") })
    }

    // ----- Última ronda no anuncia otra pregunta --------------------------------

    @Test
    fun lastRoundPrompt_doesNotAnnounceAnotherQuestion() {
        val forbidden = listOf("siguiente pregunta", "otra pregunta", "pasemos", "vamos con otra")
        repeat(40) {
            val phrase = bank.getRoundPrompt(4, "¿qué animal es?", isLast = true).lowercase()
            for (word in forbidden) {
                assertFalse("La última ronda no debe decir '$word'", phrase.contains(word))
            }
        }
    }

    @Test
    fun lastAnswerReceived_doesNotAnnounceAnotherQuestion() {
        val forbidden = listOf("siguiente", "otra pregunta", "pasemos a otra", "próxima", "viene otra")
        repeat(40) {
            val phrase = bank.getAnswerReceived(isLast = true).lowercase()
            for (word in forbidden) {
                assertFalse("LAST_ANSWER_RECEIVED no debe decir '$word'", phrase.contains(word))
            }
        }
    }

    @Test
    fun lastTimeExpired_doesNotAnnounceAnotherQuestion() {
        val forbidden = listOf("siguiente", "otra ronda", "próxima", "intentemos")
        repeat(40) {
            val phrase = bank.getTimeExpired(hadPartialResponse = false, isLast = true).lowercase()
            for (word in forbidden) {
                assertFalse("Tiempo agotado en última ronda no debe decir '$word'", phrase.contains(word))
            }
        }
    }

    // ----- Sin lenguaje evaluativo ----------------------------------------------

    @Test
    fun answerReceivedPhrases_doNotContainFeedbackWords() {
        val forbidden = listOf("correcto", "incorrecto", "exacto", "respuesta correcta", "muy bien, esa era")
        repeat(60) {
            val regular = bank.getAnswerReceived(isLast = false).lowercase()
            val last = bank.getAnswerReceived(isLast = true).lowercase()
            for (word in forbidden) {
                assertFalse("ANSWER_RECEIVED no debe contener '$word'", regular.contains(word))
                assertFalse("LAST_ANSWER_RECEIVED no debe contener '$word'", last.contains(word))
            }
        }
    }

    @Test
    fun roundPrompts_doNotContainFeedbackWords() {
        val forbidden = listOf("correcto", "incorrecto", "exacto")
        repeat(40) {
            val regular = bank.getRoundPrompt(1, "¿qué animal es?", isLast = false).lowercase()
            val last = bank.getRoundPrompt(4, "¿qué animal es?", isLast = true).lowercase()
            for (word in forbidden) {
                assertFalse(regular.contains(word))
                assertFalse(last.contains(word))
            }
        }
    }

    // ----- Anti-repetición ------------------------------------------------------

    @Test
    fun sessionStart_doesNotRepeatImmediately() {
        assertFalse(bank.getSessionStart() == bank.getSessionStart())
    }

    @Test
    fun answerReceived_doesNotRepeatImmediately() {
        assertFalse(bank.getAnswerReceived() == bank.getAnswerReceived())
    }

    @Test
    fun lastAnswerReceived_doesNotRepeatImmediately() {
        assertFalse(bank.getAnswerReceived(isLast = true) == bank.getAnswerReceived(isLast = true))
    }

    @Test
    fun sessionCompleted_doesNotRepeatImmediately() {
        assertFalse(bank.getSessionCompleted() == bank.getSessionCompleted())
    }

    @Test
    fun roundPrompt_doesNotRepeatTemplateImmediately() {
        val first = bank.getRoundPrompt(1, "PREGUNTA_FIJA", isLast = false)
        val second = bank.getRoundPrompt(1, "PREGUNTA_FIJA", isLast = false)
        assertFalse("No debe repetir la misma plantilla de ronda consecutivamente", first == second)
    }
}
