package com.taller.app.bimodal

import org.junit.Assert.assertEquals
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
    fun acceptsCommonSttMisspellings() {
        // El STT infantil confunde "seven" con "seben" o lo parte en "se ven",
        // y "hola" con "ola". La activacion debe tolerar estos errores.
        assertTrue(SevenStartCommand.matches("hola seben"))
        assertTrue(SevenStartCommand.matches("hola se ven"))
        assertTrue(SevenStartCommand.matches("ola seven"))
        assertTrue(SevenStartCommand.matches("ola seben"))
        assertTrue(SevenStartCommand.matches("oye seben"))
        assertTrue(SevenStartCommand.matches("seben"))
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
    fun acceptsLongPhraseOnlyWithStartVerb() {
        // Frase larga con "seven" + verbo de inicio: activa.
        assertTrue(SevenStartCommand.matches("ahora seven vamos"))
        // Frase larga con "seven" pero sin verbo de inicio ni saludo: no activa.
        assertFalse(SevenStartCommand.matches("el numero seven es grande"))
    }

    @Test
    fun rejectsUnrelatedPhrases() {
        assertFalse(SevenStartCommand.matches(""))
        assertFalse(SevenStartCommand.matches("quiero jugar"))
        assertFalse(SevenStartCommand.matches("el perro hace guau"))
        assertFalse(SevenStartCommand.matches("empieza ya"))
        // "se ven bonitos" no es "hola seven": aunque contenga "se ven", el resto
        // es contenido ajeno y no hay saludo ni verbo de inicio.
        assertFalse(SevenStartCommand.matches("se ven bonitos"))
        // "seven" dentro de una frase cualquiera no activa sin saludo/verbo.
        assertFalse(SevenStartCommand.matches("el numero seven es grande"))
        // "hola mama" tiene saludo pero no nombra a Seven.
        assertFalse(SevenStartCommand.matches("hola mama"))
        // "amigo" sin saludo tampoco activa.
        assertFalse(SevenStartCommand.matches("mi amigo vino ayer"))
    }

    @Test
    fun isStartCommandIsAliasOfMatches() {
        assertTrue(SevenStartCommand.isStartCommand("Hola Seven"))
        assertFalse(SevenStartCommand.isStartCommand("hola mama"))
    }

    @Test
    fun normalizeRemovesAccentsCaseAndSymbols() {
        assertEquals("hola seven", SevenStartCommand.normalize("¡HOLÁ, Seven!"))
    }
}
