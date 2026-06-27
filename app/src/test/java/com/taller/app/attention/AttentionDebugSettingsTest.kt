package com.taller.app.attention

import org.junit.Assert.assertFalse
import org.junit.Test

class AttentionDebugSettingsTest {

    @Test
    fun attentionVisualDebugEnabled_defaultsToFalse() {
        assertFalse(AttentionDebugSettings().attentionVisualDebugEnabled)
    }
}
