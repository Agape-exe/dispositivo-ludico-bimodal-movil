package com.taller.app.ui

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.AttentionState
import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.gpt.GptConfig
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.VoiceContext
import com.taller.app.voice.VoiceErrorType
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
    fun attentionOffKeepsPureVisualFlow() {
        // Sin atencion funcional, la cara sigue el flujo aunque haya snapshot de atencion.
        val expression = BimodalInteractionState.WAITING_FOR_FACE
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.TEMPORARILY_LOST),
                attentionDrivenVisualsEnabled = false
            )

        assertEquals(IntelligentSevenExpression.READY, expression)
    }

    @Test
    fun functionalAttentionRefinesLowPriorityStates() {
        // Con atencion funcional activa, un estado de baja prioridad (esperar rostro)
        // se matiza segun la atencion real observada.
        val absent = BimodalInteractionState.WAITING_FOR_FACE
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.FACE_ABSENT),
                attentionDrivenVisualsEnabled = true
            )
        assertEquals(IntelligentSevenExpression.SEARCHING_FACE, absent)

        val stable = BimodalInteractionState.WAITING_FOR_FACE
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
                attentionDrivenVisualsEnabled = true
            )
        assertEquals(IntelligentSevenExpression.HAPPY, stable)
    }

    @Test
    fun functionalAttentionDoesNotOverrideListening() {
        // La prioridad del flujo manda: escuchar no se pisa por la atencion.
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
                attentionDrivenVisualsEnabled = true
            )

        assertEquals(IntelligentSevenExpression.LISTENING, expression)
    }

    @Test
    fun functionalAttentionDoesNotOverrideSpeaking() {
        val expression = BimodalInteractionState.PRESENTING_QUESTION
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = true,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
                attentionDrivenVisualsEnabled = true
            )

        assertEquals(IntelligentSevenExpression.SPEAKING, expression)
    }

    @Test
    fun debugPanelToggleDoesNotAffectExpression() {
        // El panel de depuracion ya no es un parametro de la expresion: la misma
        // entrada (atencion funcional apagada) produce siempre la expresion del flujo,
        // sin importar si el overlay tecnico esta visible o no.
        val expression = BimodalInteractionState.WAITING_FOR_RESPONSE
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
                attentionDrivenVisualsEnabled = false
            )

        assertEquals(IntelligentSevenExpression.LISTENING, expression)
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
    fun sttDebugLabel_showsProviderLanguageAndFlagsWithoutTranscript() {
        val label = sttDebugLabel(
            available = true,
            language = "es-PE",
            lastStateLabel = "Escuchando",
            receivedPartial = true,
            receivedFinal = false,
            lastErrorShort = null
        )
        assertTrue(label.contains("STT: Android SpeechRecognizer"))
        assertTrue(label.contains("Proveedor del sistema: Google si esta disponible"))
        assertTrue(label.contains("Disponible: Si"))
        assertTrue(label.contains("Idioma: es-PE"))
        assertTrue(label.contains("Parcial: Si / Final: No"))
        assertTrue(label.contains("Error STT: —"))
    }

    @Test
    fun sttDebugLabel_showsShortErrorWhenPresent() {
        val label = sttDebugLabel(
            available = false,
            language = "es-PE",
            lastStateLabel = "Error",
            receivedPartial = false,
            receivedFinal = false,
            lastErrorShort = "No se detecto voz dentro del tiempo esperado."
        )
        assertTrue(label.contains("Disponible: No"))
        assertTrue(label.contains("Error STT: No se detecto voz"))
    }

    @Test
    fun debugPanelShowsSttOnlyWhenAttentionPanelIsOn() {
        val stt = sttDebugLabel(
            available = true,
            language = "es-PE",
            lastStateLabel = "Listo para escuchar",
            receivedPartial = false,
            receivedFinal = false,
            lastErrorShort = null
        )
        val withPanel = intelligentDebugPanelText(
            showAttention = true,
            attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
            sttDebug = stt,
            showTts = false,
            ttsProviderConfigured = ToyVoiceProviderType.LOCAL,
            ttsProviderUsedLabel = null,
            ttsVoice = null,
            ttsFallbackUsed = null,
            ttsStatus = "Sin probar",
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )
        assertTrue(withPanel.contains("STT: Android SpeechRecognizer"))

        val withoutPanel = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            sttDebug = stt,
            showTts = false,
            ttsProviderConfigured = ToyVoiceProviderType.LOCAL,
            ttsProviderUsedLabel = null,
            ttsVoice = null,
            ttsFallbackUsed = null,
            ttsStatus = "Sin probar",
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )
        assertFalse(withoutPanel.contains("STT: Android SpeechRecognizer"))
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

    // ----- AUD01.1: contexto, motivo y estado del debug TTS ----------------------

    @Test
    fun ttsContextLabel_mapsEachVoiceContextToSafeShortLabel() {
        assertEquals("INTRO", ttsContextLabel(VoiceContext.GREETING))
        assertEquals("QUESTION", ttsContextLabel(VoiceContext.QUESTION))
        assertEquals("FEEDBACK", ttsContextLabel(VoiceContext.FEEDBACK_CORRECT))
        assertEquals("FEEDBACK", ttsContextLabel(VoiceContext.FEEDBACK_INCORRECT))
        assertEquals("FEEDBACK", ttsContextLabel(VoiceContext.FEEDBACK_RETRY))
        assertEquals("FEEDBACK", ttsContextLabel(VoiceContext.NOT_INTERPRETABLE))
        assertEquals("RECAPTURE", ttsContextLabel(VoiceContext.RECAPTURE))
        assertEquals("CLOSING", ttsContextLabel(VoiceContext.CLOSING))
        assertEquals("UNKNOWN", ttsContextLabel(VoiceContext.UNKNOWN))
        assertEquals("UNKNOWN", ttsContextLabel(null))
    }

    @Test
    fun ttsFallbackReason_isNoneWhenPreferredProviderUsedWithoutError() {
        assertEquals("NONE", ttsFallbackReasonLabel(providerUsedIsPreferred = true, errorType = null))
    }

    @Test
    fun ttsFallbackReason_mapsGeminiErrorsToSafeCodes() {
        assertEquals(
            "GEMINI_TIMEOUT",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.TIMEOUT)
        )
        assertEquals(
            "GEMINI_HTTP_ERROR",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.HTTP_429)
        )
        assertEquals(
            "GEMINI_HTTP_ERROR",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.HTTP_ERROR)
        )
        assertEquals(
            "GEMINI_EMPTY_AUDIO",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.RESPONSE_WITHOUT_AUDIO)
        )
        assertEquals(
            "GEMINI_INVALID_AUDIO",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.INVALID_AUDIO)
        )
        assertEquals(
            "TEXT_VALIDATION_FAILED",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.INVALID_TTS_TEXT)
        )
        assertEquals(
            "PROVIDER_NOT_CONFIGURED",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.NOT_CONFIGURED)
        )
        assertEquals(
            "GEMINI_RATE_LIMIT",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.RATE_LIMITED)
        )
        assertEquals(
            "GEMINI_QUOTA_EXHAUSTED",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.QUOTA_EXHAUSTED)
        )
        assertEquals(
            "UNKNOWN",
            ttsFallbackReasonLabel(providerUsedIsPreferred = false, errorType = VoiceErrorType.UNKNOWN)
        )
    }

    @Test
    fun debugPanelTts_showsGeminiCooldownWhenActive() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "OpenAI",
            ttsVoice = "Puck",
            ttsFallbackUsed = true,
            ttsStatus = "FALLBACK_USED",
            ttsContextLabel = "QUESTION",
            ttsFallbackReason = "GEMINI_RATE_LIMIT",
            ttsLatencyMs = 30L,
            geminiCooldownRemainingMs = 48_000L,
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("Gemini cooldown: activo (48 s)"))
        assertTrue(label.contains("Motivo fallback: GEMINI_RATE_LIMIT"))
    }

    @Test
    fun debugPanelTts_showsNoCooldownWhenInactive() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "Gemini",
            ttsVoice = "Puck",
            ttsFallbackUsed = false,
            ttsStatus = "OK",
            ttsContextLabel = "QUESTION",
            ttsFallbackReason = "NONE",
            ttsLatencyMs = 820L,
            geminiCooldownRemainingMs = 0L,
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("Gemini cooldown: no activo"))
    }

    @Test
    fun debugPanelTts_showsContextReasonAndLatencyOnFallback() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "OpenAI",
            ttsVoice = "Puck",
            ttsFallbackUsed = true,
            ttsStatus = "FALLBACK_USED",
            ttsContextLabel = "QUESTION",
            ttsFallbackReason = "GEMINI_HTTP_ERROR",
            ttsLatencyMs = 2400L,
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("TTS preferido: Gemini / Puck"))
        assertTrue(label.contains("TTS ultimo usado: OpenAI"))
        assertTrue(label.contains("Contexto: QUESTION"))
        assertTrue(label.contains("Fallback voz: Si"))
        assertTrue(label.contains("Motivo fallback: GEMINI_HTTP_ERROR"))
        assertTrue(label.contains("Latencia: 2400 ms"))
        assertTrue(label.contains("Estado: FALLBACK_USED"))
    }

    @Test
    fun debugPanelTts_showsShortHistoryWithoutSpokenTextOrApiKey() {
        val label = intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "Gemini",
            ttsVoice = "Puck",
            ttsFallbackUsed = false,
            ttsStatus = "OK",
            ttsContextLabel = "FEEDBACK",
            ttsFallbackReason = "NONE",
            ttsLatencyMs = 900L,
            ttsHistory = listOf(
                "INTRO → Gemini / OK / 820 ms",
                "QUESTION → OpenAI / FALLBACK_USED / GEMINI_HTTP_ERROR / 2400 ms",
                "FEEDBACK → Gemini / OK / 900 ms"
            ),
            showGpt = false,
            gptConfig = gptConfig(apiKey = "credential-value"),
            lastGptUsageStatus = "sin datos"
        )

        assertTrue(label.contains("Ultimas voces:"))
        assertTrue(label.contains("1. INTRO → Gemini / OK / 820 ms"))
        assertTrue(label.contains("2. QUESTION → OpenAI / FALLBACK_USED / GEMINI_HTTP_ERROR / 2400 ms"))
        assertTrue(label.contains("3. FEEDBACK → Gemini / OK / 900 ms"))
        // Privacidad: el historial nunca contiene el texto hablado ni la API key.
        assertFalse(label.contains("Hola"))
        assertFalse(label.contains("credential-value"))
    }

    @Test
    fun debugPanelTts_defaultsAreSafeWhenNoVoiceYet() {
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

        assertTrue(label.contains("TTS ultimo usado: —"))
        assertTrue(label.contains("Contexto: —"))
        assertTrue(label.contains("Motivo fallback: NONE"))
        assertTrue(label.contains("Latencia: —"))
        assertFalse(label.contains("Ultimas voces:"))
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
