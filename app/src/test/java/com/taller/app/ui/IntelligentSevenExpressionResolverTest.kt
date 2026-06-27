package com.taller.app.ui

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.AttentionState
import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.gpt.GptConfig
import com.taller.app.voice.ToyVoiceProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentSevenExpressionResolverTest {

    @Test
    fun speakingKeepsPriorityOverAttentionLost() {
        val expression = BimodalInteractionState.PRESENTING_QUESTION
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = true,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST)
            )

        assertEquals(IntelligentSevenExpression.SPEAKING, expression)
    }

    @Test
    fun listeningKeepsListeningExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST)
            )

        assertEquals(IntelligentSevenExpression.LISTENING, expression)
    }

    @Test
    fun waitingForFaceUsesSearchingForFaceAbsent() {
        val expression = BimodalInteractionState.WAITING_FOR_FACE
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.FACE_ABSENT)
            )

        assertEquals(IntelligentSevenExpression.SEARCHING_FACE, expression)
    }

    @Test
    fun debugDisabledKeepsNormalVisualFlow() {
        val expression = BimodalInteractionState.WAITING_FOR_FACE
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.TEMPORARILY_LOST),
                attentionVisualDebugEnabled = false
            )

        assertEquals(IntelligentSevenExpression.READY, expression)
    }

    @Test
    fun debugEnabledFaceAbsentUsesSearching() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.FACE_ABSENT),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.SEARCHING_FACE, expression)
    }

    @Test
    fun debugEnabledAttentionStableUsesReadyHappyExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.HAPPY, expression)
    }

    @Test
    fun debugEnabledTemporarilyLostUsesSoftConfusedExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.TEMPORARILY_LOST),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.CONFUSED, expression)
    }

    @Test
    fun debugEnabledAttentionLostUsesSearchingExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.SEARCHING_FACE, expression)
    }

    @Test
    fun attentionDebugLabelShowsOnlyStateFaceAndLooking() {
        val label = attentionDebugLabel(snapshot(AttentionState.ATTENTION_LOST))

        assertEquals(
            "Atencion: ATTENTION_LOST\nRostro: Si\nMirando: No",
            label
        )
    }

    @Test
    fun attentionDebugLabelDefaultsToUnknown() {
        assertEquals(
            "Atencion: UNKNOWN\nRostro: No\nMirando: No",
            attentionDebugLabel(null)
        )
    }

    @Test
    fun debugPanelOmitsTtsWhenSwitchIsOff() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = false,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "Gemini",
            ttsVoice = "Puck",
            ttsFallbackUsed = false,
            ttsStatus = "OK",
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

        assertFalse(label.contains("TTS:"))
    }

    @Test
    fun debugPanelShowsTtsWhenSwitchIsOnWithoutSensitiveText() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "Gemini",
            ttsVoice = "Puck",
            ttsFallbackUsed = false,
            ttsStatus = "OK",
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("TTS preferido: Gemini / Puck"))
        assertTrue(label.contains("TTS ultimo usado: Gemini"))
        assertTrue(label.contains("Fallback voz: No"))
        assertTrue(label.contains("Estado: OK"))
        assertFalse(label.contains("Hola, explorador"))
        assertFalse(label.contains("cache"))
        assertFalse(label.contains("C:\\"))
    }

    @Test
    fun debugPanelTts_showsPreferredAndUsedSeparately_whenFallbackOccurred() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "OpenAI",
            ttsVoice = "Puck",
            ttsFallbackUsed = true,
            ttsStatus = "OK",
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("TTS preferido: Gemini / Puck"))
        assertTrue(label.contains("TTS ultimo usado: OpenAI"))
        assertTrue(label.contains("Fallback voz: Si"))
    }

    @Test
    fun debugPanelTts_showsDashForUsedWhenNothingReproduced() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = null,
            ttsVoice = "Puck",
            ttsFallbackUsed = null,
            ttsStatus = "Sin probar",
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("TTS preferido: Gemini / Puck"))
        assertTrue(label.contains("TTS ultimo usado: —"))
        assertTrue(label.contains("Fallback voz: —"))
    }

    @Test
    fun debugPanelOmitsGptWhenSwitchIsOff() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = false,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = null,
            ttsVoice = null,
            ttsFallbackUsed = null,
            ttsStatus = "Sin probar",
            showGpt = false,
            gptConfig = gptConfig(apiKey = "credential-value"),
            lastGptUsageStatus = "sin datos"
        )

        assertFalse(label.contains("GPT:"))
    }

    @Test
    fun debugPanelShowsGptWhenSwitchIsOnWithoutApiKey() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = false,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = null,
            ttsVoice = null,
            ttsFallbackUsed = null,
            ttsStatus = "Sin probar",
            showGpt = true,
            gptConfig = gptConfig(apiKey = "credential-value", enabled = true),
            lastGptUsageStatus = "GPT usado"
        )

        assertTrue(label.contains("GPT: activado"))
        assertTrue(label.contains("Modelo: gpt-5.4-mini"))
        assertTrue(label.contains("Configurado: Si"))
        assertTrue(label.contains("Fallback local: Si"))
        assertFalse(label.contains("credential-value"))
    }

    @Test
    fun debugPanelCanShowIndependentSectionsTogether() {
        val label = intelligentDebugPanelText(
            showAttention = true,
            attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.LOCAL,
            ttsProviderUsedLabel = "Android local",
            ttsVoice = "es-PE",
            ttsFallbackUsed = true,
            ttsStatus = "OK",
            showGpt = true,
            gptConfig = gptConfig(apiKey = "", enabled = true),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("Atencion: ATTENTION_STABLE"))
        assertTrue(label.contains("TTS preferido: Android local / es-PE"))
        assertTrue(label.contains("TTS ultimo usado: Android local"))
        assertTrue(label.contains("GPT: no configurado"))
    }

    private fun snapshot(state: AttentionState): AttentionSnapshot {
        val faceDetected = state != AttentionState.FACE_ABSENT
        val lookingAtDevice = state == AttentionState.FACE_PRESENT ||
            state == AttentionState.ATTENTION_STABLE
        return AttentionSnapshot(
            state = state,
            faceDetected = faceDetected,
            lookingAtDevice = faceDetected && lookingAtDevice,
            isAttentionStable = state == AttentionState.ATTENTION_STABLE,
            isTemporarilyLost = state == AttentionState.TEMPORARILY_LOST,
            isAttentionLost = state == AttentionState.ATTENTION_LOST,
            headYawDegrees = null,
            headPitchDegrees = null,
            headRollDegrees = null,
            lastFaceDetectedAtMs = if (faceDetected) 1_000L else null,
            lastLookingAtDeviceAtMs = if (lookingAtDevice) 1_000L else null,
            lastLookAwayAtMs = if (state == AttentionState.TEMPORARILY_LOST) 1_000L else null,
            stateChangedAtMs = 1_000L,
            stableDurationMs = if (state == AttentionState.ATTENTION_STABLE) 1_500L else 0L,
            lookAwayDurationMs = if (state == AttentionState.TEMPORARILY_LOST) 1_200L else 0L,
            lostDurationMs = if (state == AttentionState.ATTENTION_LOST) 3_000L else 0L,
            consecutiveStableFrames = if (state == AttentionState.ATTENTION_STABLE) 3 else 0,
            consecutiveLostFrames = if (state == AttentionState.ATTENTION_LOST) 3 else 0
        )
    }

    private fun gptConfig(
        apiKey: String = "",
        enabled: Boolean = false
    ) = GptConfig(
        apiKey = apiKey,
        model = "gpt-5.4-mini",
        fallbackModel = "gpt-5.4-nano",
        maxOutputTokens = 220,
        timeoutMs = 12_000L,
        temperature = 0.4f,
        enabled = enabled,
        localFallbackEnabled = true,
        structuredOutputsEnabled = true
    )
}
