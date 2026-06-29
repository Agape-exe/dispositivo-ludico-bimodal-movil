package com.taller.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLimiterRepositoryTest {

    @Test
    fun sanitizesDangerousDisplayName() {
        val safe = MarkdownLimiterRepository.sanitizeDisplayName("../reglas raras!!.md")
        assertFalse(safe.contains(".."))
        assertFalse(safe.contains("/"))
        assertTrue(safe.endsWith(".md"))
    }

    @Test
    fun addsMdExtensionWhenMissing() {
        assertEquals("reglas.md", MarkdownLimiterRepository.sanitizeDisplayName("reglas"))
    }

    @Test
    fun exposesDocumentLimits() {
        assertEquals(100 * 1024, MarkdownLimiterRepository.MAX_FILE_SIZE_BYTES)
        assertEquals(4_000, MarkdownLimiterRepository.DEFAULT_RULES_CHARACTER_LIMIT)
    }
}
