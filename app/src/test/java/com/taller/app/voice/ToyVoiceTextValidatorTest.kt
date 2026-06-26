package com.taller.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ToyVoiceTextValidatorTest {

    @Test
    fun nullText_isInvalid() {
        val result = ToyVoiceTextValidator.validate(null)

        assertFalse(result.isValid)
        assertEquals(InvalidToyVoiceTextReason.EMPTY_TEXT, result.reason)
    }

    @Test
    fun emptyText_isInvalid() {
        val result = ToyVoiceTextValidator.validate("")

        assertFalse(result.isValid)
        assertEquals(InvalidToyVoiceTextReason.EMPTY_TEXT, result.reason)
    }

    @Test
    fun blankText_isInvalid() {
        val result = ToyVoiceTextValidator.validate(" \n\t ")

        assertFalse(result.isValid)
        assertEquals(InvalidToyVoiceTextReason.EMPTY_TEXT, result.reason)
    }

    @Test
    fun symbolOnlyText_isInvalid() {
        val result = ToyVoiceTextValidator.validate("... -- !!!")

        assertFalse(result.isValid)
        assertEquals(InvalidToyVoiceTextReason.NO_USEFUL_CONTENT, result.reason)
    }

    @Test
    fun placeholderPrompt_isInvalid() {
        val result = ToyVoiceTextValidator.validate("Dime que quieres que diga")

        assertFalse(result.isValid)
        assertEquals(InvalidToyVoiceTextReason.PLACEHOLDER_TEXT, result.reason)
    }

    @Test
    fun emptySpeechPlaceholder_isInvalid() {
        val result = ToyVoiceTextValidator.validate("No tengo nada que decir")

        assertFalse(result.isValid)
        assertEquals(InvalidToyVoiceTextReason.PLACEHOLDER_TEXT, result.reason)
    }

    @Test
    fun validSevenPhrase_isValidAndTrimmed() {
        val result = ToyVoiceTextValidator.validate("  Hola! Soy Seven, tu amigo explorador.  ")

        assertTrue(result.isValid)
        assertEquals("Hola! Soy Seven, tu amigo explorador.", result.normalizedText)
        assertNull(result.reason)
    }
}
