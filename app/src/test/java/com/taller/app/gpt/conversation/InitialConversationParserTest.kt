package com.taller.app.gpt.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialConversationParserTest {

    @Test
    fun parsesAllowedAnswer() {
        val verdict = InitialConversationParser.parse(
            "{\"answer\":\"Me llamo Seven.\",\"allowed\":true,\"reason\":\"SAFE_CHILD_CONTEXT\"}"
        )
        assertTrue(verdict.allowed)
        assertEquals("Me llamo Seven.", verdict.answer)
        assertEquals("SAFE_CHILD_CONTEXT", verdict.reason)
    }

    @Test
    fun toleratesMarkdownFences() {
        val verdict = InitialConversationParser.parse(
            "```json\n{\"answer\":\"Hola.\",\"allowed\":true,\"reason\":\"SAFE_CHILD_CONTEXT\"}\n```"
        )
        assertTrue(verdict.allowed)
        assertEquals("Hola.", verdict.answer)
    }

    @Test
    fun treatsAllowedButEmptyAnswerAsNotUsable() {
        val verdict = InitialConversationParser.parse(
            "{\"answer\":\"  \",\"allowed\":true,\"reason\":\"SAFE_CHILD_CONTEXT\"}"
        )
        assertFalse(verdict.allowed)
        assertEquals("", verdict.answer)
    }

    @Test
    fun clearsAnswerWhenNotAllowed() {
        val verdict = InitialConversationParser.parse(
            "{\"answer\":\"algo raro\",\"allowed\":false,\"reason\":\"OUT_OF_SCOPE\"}"
        )
        assertFalse(verdict.allowed)
        assertEquals("", verdict.answer)
        assertEquals("OUT_OF_SCOPE", verdict.reason)
    }

    @Test
    fun clampsVeryLongAnswer() {
        val longAnswer = "a".repeat(500)
        val verdict = InitialConversationParser.parse(
            "{\"answer\":\"$longAnswer\",\"allowed\":true,\"reason\":\"x\"}"
        )
        assertTrue(verdict.answer.length <= 180)
    }

    @Test
    fun throwsOnInvalidJson() {
        assertThrows(InitialConversationParseException::class.java) {
            InitialConversationParser.parse("no es json")
        }
    }
}
