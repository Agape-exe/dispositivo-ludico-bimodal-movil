package com.taller.app.classic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FixedTimerNeutralPhraseBankTest {

    private lateinit var bank: FixedTimerNeutralPhraseBank

    @Before
    fun setUp() {
        bank = FixedTimerNeutralPhraseBank()
    }

    @Test
    fun sessionStart_returnsNonBlankPhrase() {
        val phrase = bank.getSessionStart()
        assertTrue(phrase.isNotBlank())
    }

    @Test
    fun questionTransition_notLast_returnsNonBlankPhrase() {
        val phrase = bank.getQuestionTransition(isLast = false)
        assertTrue(phrase.isNotBlank())
    }

    @Test
    fun questionTransition_isLast_returnsLastQuestionPhrase() {
        val phrase = bank.getQuestionTransition(isLast = true)
        assertTrue(phrase.isNotBlank())
    }

    @Test
    fun answerReceived_returnsNonBlankPhrase() {
        val phrase = bank.getAnswerReceived()
        assertTrue(phrase.isNotBlank())
    }

    @Test
    fun timeExpiredWithResponse_returnsNonBlankPhrase() {
        val phrase = bank.getTimeExpired(hadPartialResponse = true)
        assertTrue(phrase.isNotBlank())
    }

    @Test
    fun timeExpiredNoResponse_returnsNonBlankPhrase() {
        val phrase = bank.getTimeExpired(hadPartialResponse = false)
        assertTrue(phrase.isNotBlank())
    }

    @Test
    fun sessionCompleted_returnsNonBlankPhrase() {
        val phrase = bank.getSessionCompleted()
        assertTrue(phrase.isNotBlank())
    }

    @Test
    fun answerReceived_doesNotRepeatImmediately() {
        // Con más de dos frases, la segunda llamada no debe devolver la misma frase.
        val first = bank.getAnswerReceived()
        val second = bank.getAnswerReceived()
        assertFalse(
            "El banco no debe repetir la misma frase consecutivamente",
            first == second
        )
    }

    @Test
    fun sessionStart_doesNotRepeatImmediately() {
        val first = bank.getSessionStart()
        val second = bank.getSessionStart()
        assertFalse(first == second)
    }

    @Test
    fun timeExpiredNoResponse_doesNotRepeatImmediately() {
        val first = bank.getTimeExpired(false)
        val second = bank.getTimeExpired(false)
        assertFalse(first == second)
    }

    @Test
    fun sessionCompleted_doesNotRepeatImmediately() {
        val first = bank.getSessionCompleted()
        val second = bank.getSessionCompleted()
        assertFalse(first == second)
    }

    @Test
    fun lastQuestionPhrase_doesNotContainFeedbackWords() {
        // Las frases no deben incluir palabras de feedback semántico.
        val forbidden = listOf("correcto", "incorrecto", "exacto", "muy bien, esa era", "respuesta correcta")
        repeat(30) {
            val phrase = bank.getQuestionTransition(isLast = true).lowercase()
            for (word in forbidden) {
                assertFalse(
                    "La frase de última pregunta no debe contener '$word'",
                    phrase.contains(word)
                )
            }
        }
    }

    @Test
    fun answerReceivedPhrases_doNotContainFeedbackWords() {
        val forbidden = listOf("correcto", "incorrecto", "exacto", "respuesta correcta", "muy bien, esa era")
        repeat(40) {
            val phrase = bank.getAnswerReceived().lowercase()
            for (word in forbidden) {
                assertFalse(
                    "Frase ANSWER_RECEIVED no debe contener '$word'",
                    phrase.contains(word)
                )
            }
        }
    }

    @Test
    fun allCategories_returnNonBlankPhrases() {
        assertNotNull(bank.getSessionStart())
        assertNotNull(bank.getQuestionTransition(false))
        assertNotNull(bank.getQuestionTransition(true))
        assertNotNull(bank.getAnswerReceived())
        assertNotNull(bank.getTimeExpired(true))
        assertNotNull(bank.getTimeExpired(false))
        assertNotNull(bank.getSessionCompleted())
    }
}
