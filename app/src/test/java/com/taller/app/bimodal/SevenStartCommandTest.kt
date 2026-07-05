package com.taller.app.bimodal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SevenStartCommandTest {

    @Test
    fun acceptsHolaSevenAndSimpleVariants() {
        assertTrue(SevenStartCommand.matches("Hola Seven"))
        assertTrue(SevenStartCommand.matches("hola seven"))
        assertTrue(SevenStartCommand.matches("HOLA SEVEN"))
        assertTrue(SevenStartCommand.matches("oye Seven"))
        assertTrue(SevenStartCommand.matches("oye seven"))
        assertTrue(SevenStartCommand.matches("hola amigo"))
    }

    @Test
    fun acceptsSevenAloneAsWholeUtterance() {
        assertTrue(SevenStartCommand.matches("Seven"))
        assertTrue(SevenStartCommand.matches("  seven  "))
    }

    @Test
    fun toleratesAccentsPunctuationAndExtraSpaces() {
        assertTrue(SevenStartCommand.matches("¡Hola, Seven!"))
        assertTrue(SevenStartCommand.matches("holá   seven"))
        assertTrue(SevenStartCommand.matches("Hola   Séven"))
    }

    @Test
    fun keepsLegacyStartPhraseForCompatibility() {
        assertTrue(SevenStartCommand.matches("Seven empieza"))
        assertTrue(SevenStartCommand.matches("seven, empieza"))
        assertTrue(SevenStartCommand.matches("Seven empecemos"))
        assertTrue(SevenStartCommand.matches("Seven comencemos"))
    }

    @Test
    fun rejectsUnrelatedPhrases() {
        assertFalse(SevenStartCommand.matches(""))
        assertFalse(SevenStartCommand.matches("quiero jugar"))
        assertFalse(SevenStartCommand.matches("el perro hace guau"))
        assertFalse(SevenStartCommand.matches("empieza ya"))
        // "se ven" no es "seven": la comparacion respeta palabras completas.
        assertFalse(SevenStartCommand.matches("se ven bonitos"))
        // "seven" dentro de una frase cualquiera no activa sin saludo.
        assertFalse(SevenStartCommand.matches("el numero seven es grande"))
        // "amigo" sin saludo tampoco activa.
        assertFalse(SevenStartCommand.matches("mi amigo vino ayer"))
    }

    @Test
    fun normalizeRemovesAccentsCaseAndSymbols() {
        org.junit.Assert.assertEquals("hola seven", SevenStartCommand.normalize("¡HOLÁ, Seven!"))
    }
}
