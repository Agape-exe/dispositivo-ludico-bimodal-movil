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

    // ----- MED02: sonidos seguros adicionales (vaca, pato, oveja) ----------------

    private val cowQuestion = "¿Qué sonido hace la vaca?"
    private val duckQuestion = "¿Qué sonido hace el pato?"
    private val sheepQuestion = "¿Qué sonido hace la oveja?"

    @Test
    fun match_cowAliasesWithCowContext() {
        for (alias in listOf("mu", "muu", "muuu", "moo")) {
            val match = OnomatopoeiaNormalizer.match(alias, "muu", cowQuestion)
            assertTrue("'$alias' debería ser alias de muu", match.matchedAlias)
        }
    }

    @Test
    fun match_duckAliasesWithDuckContext() {
        for (alias in listOf("cuac", "cua", "cua cua", "quack")) {
            val match = OnomatopoeiaNormalizer.match(alias, "cuac", duckQuestion)
            assertTrue("'$alias' debería ser alias de cuac", match.matchedAlias)
        }
    }

    @Test
    fun match_sheepAliasesWithSheepContext() {
        for (alias in listOf("be", "bee", "beee")) {
            val match = OnomatopoeiaNormalizer.match(alias, "bee", sheepQuestion)
            assertTrue("'$alias' debería ser alias de bee", match.matchedAlias)
        }
    }

    @Test
    fun match_cowDoesNotAcceptDogSound() {
        val match = OnomatopoeiaNormalizer.match("guau", "muu", cowQuestion)
        assertFalse(match.matchedAlias)
        assertTrue(match.contradictorySound)
    }

    @Test
    fun match_duckDoesNotAcceptCatSound() {
        val match = OnomatopoeiaNormalizer.match("miau", "cuac", duckQuestion)
        assertFalse(match.matchedAlias)
        assertTrue(match.contradictorySound)
    }

    // ----- FINAL-FLOW01: gallina/pollo -------------------------------------------

    private val henQuestion = "¿Qué sonido hace la gallina?"
    private val chickQuestion = "¿Qué sonido hace el pollito?"

    @Test
    fun match_henAliasesWithHenContext() {
        for (alias in listOf("pio", "pio pio", "clo clo", "cocoroco")) {
            val match = OnomatopoeiaNormalizer.match(alias, "pio pio", henQuestion)
            assertTrue("'$alias' debería ser alias de pio pio", match.matchedAlias)
        }
    }

    @Test
    fun match_chickContextByQuestionMarkerAlone() {
        // La referencia no es onomatopeya, pero la pregunta pide el sonido del pollito.
        val match = OnomatopoeiaNormalizer.match("pio pio", "canta", chickQuestion)
        assertTrue(match.matchedAlias)
    }

    @Test
    fun match_henDoesNotAcceptDogOrCatSound() {
        val dog = OnomatopoeiaNormalizer.match("guau", "pio pio", henQuestion)
        assertFalse(dog.matchedAlias)
        assertTrue(dog.contradictorySound)

        val cat = OnomatopoeiaNormalizer.match("miau", "pio pio", henQuestion)
        assertFalse(cat.matchedAlias)
        assertTrue(cat.contradictorySound)
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
