package com.taller.app.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnomatopoeiaNormalizerTest {

    private val dogQuestion = "¿Qué sonido hace el perro?"
    private val catQuestion = "¿Qué sonido hace el gato?"

    @Test
    fun normalizeBasic_lowercasesStripsAccentsAndPunctuation() {
        assertEquals("guau guau", OnomatopoeiaNormalizer.normalizeBasic("¡Guau, Guau!"))
        assertEquals("miau", OnomatopoeiaNormalizer.normalizeBasic("  Miáu.  "))
    }

    @Test
    fun match_dogAliasesWithDogContext() {
        for (alias in listOf("wow", "wow wow", "wau", "wao", "woof")) {
            val match = OnomatopoeiaNormalizer.match(alias, "guau", dogQuestion)
            assertTrue("'$alias' debería ser alias de guau", match.matchedAlias)
            assertFalse(match.contradictorySound)
        }
    }

    @Test
    fun match_catAliasesWithCatContext() {
        for (alias in listOf("meow", "miaw", "mau")) {
            val match = OnomatopoeiaNormalizer.match(alias, "miau", catQuestion)
            assertTrue("'$alias' debería ser alias de miau", match.matchedAlias)
        }
    }

    @Test
    fun match_doesNotAcceptOppositeSound() {
        val dog = OnomatopoeiaNormalizer.match("miau", "guau", dogQuestion)
        assertFalse(dog.matchedAlias)
        assertTrue(dog.contradictorySound)

        val cat = OnomatopoeiaNormalizer.match("wow", "miau", catQuestion)
        assertFalse(cat.matchedAlias)
        assertTrue(cat.contradictorySound)
    }

    @Test
    fun match_doesNotAcceptUnrelatedWord() {
        val match = OnomatopoeiaNormalizer.match("mesa", "guau", dogQuestion)
        assertFalse(match.matchedAlias)
        assertFalse(match.contradictorySound)
    }

    @Test
    fun match_doesNotApplyWithoutContext() {
        // Sin referencia onomatopeyica ni pregunta de sonido, no se activa ningún grupo.
        val match = OnomatopoeiaNormalizer.match("wow", "azul", "¿De qué color es el cielo?")
        assertFalse(match.matchedAlias)
        assertFalse(match.contradictorySound)
    }

    @Test
    fun contextByQuestionMarkerAlone() {
        // La referencia no es onomatopeya, pero la pregunta pide el sonido del perro.
        val match = OnomatopoeiaNormalizer.match("woof", "ladra", dogQuestion)
        assertTrue(match.matchedAlias)
    }

    @Test
    fun helpers_detectSoundContextAndShortSounds() {
        assertTrue(OnomatopoeiaNormalizer.isSoundQuestion(dogQuestion))
        assertFalse(OnomatopoeiaNormalizer.isSoundQuestion("Menciona un animal doméstico"))
        assertTrue(OnomatopoeiaNormalizer.expectedLooksLikeOnomatopoeia("guau"))
        assertFalse(OnomatopoeiaNormalizer.expectedLooksLikeOnomatopoeia("perro"))
        assertTrue(OnomatopoeiaNormalizer.answerLooksLikeShortSound("grr"))
        assertFalse(OnomatopoeiaNormalizer.answerLooksLikeShortSound("el escritorio de madera grande"))
    }
}
