package com.taller.app.logger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidVoiceResponseRuleTest {

    @Test
    fun transcripcionNormal_cuenta() {
        assertTrue(ValidVoiceResponseRule.isValid("un pavo"))
        assertTrue(ValidVoiceResponseRule.isValid("la vaca dice muu"))
        assertTrue(ValidVoiceResponseRule.isValid("no"))
        assertTrue(ValidVoiceResponseRule.isValid("  sí  "))
    }

    @Test
    fun vaciaONula_noCuenta() {
        assertFalse(ValidVoiceResponseRule.isValid(null))
        assertFalse(ValidVoiceResponseRule.isValid(""))
        assertFalse(ValidVoiceResponseRule.isValid("   "))
    }

    @Test
    fun soloRuidoOSimbolos_noCuenta() {
        assertFalse(ValidVoiceResponseRule.isValid("..."))
        assertFalse(ValidVoiceResponseRule.isValid("123"))
        assertFalse(ValidVoiceResponseRule.isValid("!?"))
        assertFalse(ValidVoiceResponseRule.isValid("a"))
    }
}
