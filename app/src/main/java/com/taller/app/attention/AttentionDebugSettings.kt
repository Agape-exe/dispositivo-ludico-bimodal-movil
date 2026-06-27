package com.taller.app.attention

data class AttentionDebugSettings(
    val attentionVisualDebugEnabled: Boolean = false,
    val showTtsDebugInIntelligentMode: Boolean = false,
    val showGptDebugInIntelligentMode: Boolean = false
)
