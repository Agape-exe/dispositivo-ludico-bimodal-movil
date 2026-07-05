package com.taller.app.bimodal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialConversationBankTest {

    /** Palabras sueltas que nunca deben aparecer (comparadas como palabra completa). */
    private val forbiddenWords = setOf(
        "mal", "incorrecto", "fallaste", "error", "castigo", "tonto",
        "robot", "maquina", "modelo", "asistente"
    )

    /** Frases prohibidas (comparadas como subcadena). */
    private val forbiddenPhrases = listOf("no puedes", "inteligencia artificial")

    @Test
    fun staticAndSampleAnswersAreNonBlankShortAndSafe() {
        (InitialConversationBank.staticPhrases() + InitialConversationBank.sampleLocalAnswers())
            .forEach { phrase ->
                assertTrue("Frase vacia en el banco", phrase.isNotBlank())
                assertTrue("Frase demasiado larga: $phrase", phrase.length <= 180)
                val lower = phrase.lowercase()
                // Palabras completas: evita falsos positivos como "animales" -> "mal".
                val words = lower.split(Regex("[^a-záéíóúñ]+")).filter { it.isNotBlank() }
                forbiddenWords.forEach { word ->
                    assertFalse(
                        "Palabra no permitida '$word' en: $phrase",
                        words.contains(word)
                    )
                }
                forbiddenPhrases.forEach { forbidden ->
                    assertFalse(
                        "Frase no permitida '$forbidden' en: $phrase",
                        lower.contains(forbidden)
                    )
                }
            }
    }

    @Test
    fun localAnswer_respondsToNameQuestions() {
        val expected = "Me llamo Seven. Soy un explorador curioso que quiere aprender contigo."
        assertEquals(expected, InitialConversationBank.localAnswer("¿Cómo te llamas?", "animales"))
        assertEquals(expected, InitialConversationBank.localAnswer("cual es tu nombre", "animales"))
        assertEquals(expected, InitialConversationBank.localAnswer("¿Quién eres?", "animales"))
    }

    @Test
    fun localAnswer_respondsToWhatAreYou() {
        assertEquals(
            "Soy Seven, un pequeño explorador que está conociendo la Tierra.",
            InitialConversationBank.localAnswer("¿qué eres?", "colores")
        )
    }

    @Test
    fun localAnswer_respondsToRobotQuestionSeparately() {
        // "eres un robot" no debe confundirse con "que eres".
        assertEquals(
            "Soy Seven, tu amigo explorador para esta aventura.",
            InitialConversationBank.localAnswer("¿eres un robot?", "formas")
        )
    }

    @Test
    fun localAnswer_explainsActivityWithTopic() {
        val answer = InitialConversationBank.localAnswer("¿qué vamos a hacer?", "los animales")
        assertTrue(answer!!.contains("los animales"))
        assertTrue(answer.contains("preguntitas"))
    }

    @Test
    fun localAnswer_usesGenericTopicWhenBlank() {
        val answer = InitialConversationBank.localAnswer("¿de qué trata?", "  ")
        assertTrue(answer!!.contains("cosas divertidas"))
    }

    @Test
    fun localAnswer_returnsNullForUnknownQuestion() {
        // Preguntas fuera del banco se delegan al juez conversacional (null local).
        assertNull(InitialConversationBank.localAnswer("por que el cielo es azul", "colores"))
        assertNull(InitialConversationBank.localAnswer("", "colores"))
    }

    @Test
    fun invitationAndTransitionComeFromTheLocalBank() {
        assertTrue(InitialConversationBank.invitePhrase().isNotBlank())
        assertTrue(InitialConversationBank.transitionPhrase().isNotBlank())
    }
}
