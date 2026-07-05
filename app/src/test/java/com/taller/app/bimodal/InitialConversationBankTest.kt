package com.taller.app.bimodal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialConversationBankTest {

    /** Palabras que nunca deben aparecer en frases dichas a un nino pequeno. */
    private val forbiddenWords = listOf(
        "mal", "incorrecto", "fallaste", "error", "castigo", "tonto", "no puedes"
    )

    @Test
    fun allPhrasesAreNonBlankAndShort() {
        InitialConversationBank.allPhrases().forEach { phrase ->
            assertTrue("Frase vacia en el banco", phrase.isNotBlank())
            assertTrue("Frase demasiado larga: $phrase", phrase.length <= 120)
        }
    }

    @Test
    fun allPhrasesAreSafeForChildren() {
        InitialConversationBank.allPhrases().forEach { phrase ->
            val lower = phrase.lowercase()
            forbiddenWords.forEach { word ->
                assertFalse(
                    "Palabra no permitida '$word' en: $phrase",
                    lower.contains(word)
                )
            }
        }
    }

    @Test
    fun replyPhrasesRotateByTurnWithoutCrashing() {
        val first = InitialConversationBank.replyPhrase(1)
        val second = InitialConversationBank.replyPhrase(2)
        val third = InitialConversationBank.replyPhrase(3)
        // Tres turnos seguidos usan frases distintas del banco.
        assertEquals(3, setOf(first, second, third).size)
        // Turnos fuera de rango no rompen: rotan de forma segura.
        assertTrue(InitialConversationBank.replyPhrase(0).isNotBlank())
        assertTrue(InitialConversationBank.replyPhrase(99).isNotBlank())
    }

    @Test
    fun invitationAndTransitionComeFromTheLocalBank() {
        assertTrue(InitialConversationBank.allPhrases().contains(InitialConversationBank.invitePhrase()))
        assertTrue(InitialConversationBank.allPhrases().contains(InitialConversationBank.transitionPhrase()))
    }
}
