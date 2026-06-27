package com.taller.app.attention

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionDebugSettingsTest {

    @Test
    fun attentionVisualDebugEnabled_defaultsToFalse() {
        assertFalse(AttentionDebugSettings().attentionVisualDebugEnabled)
    }

    @Test
    fun ttsAndGptDebugFlags_defaultToFalse() {
        val settings = AttentionDebugSettings()

        assertFalse(settings.showTtsDebugInIntelligentMode)
        assertFalse(settings.showGptDebugInIntelligentMode)
    }

    @Test
    fun ttsDebugFlagCanBeChangedInSettingsCopy() {
        val settings = AttentionDebugSettings()
            .copy(showTtsDebugInIntelligentMode = true)

        assertTrue(settings.showTtsDebugInIntelligentMode)
    }

    @Test
    fun gptDebugFlagCanBeChangedInSettingsCopy() {
        val settings = AttentionDebugSettings()
            .copy(showGptDebugInIntelligentMode = true)

        assertTrue(settings.showGptDebugInIntelligentMode)
    }
}
