package com.taller.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToyVoiceFallbackTest {

    @Test
    fun defaultVoiceSettings_usesGemini() {
        assertEquals(ToyVoiceProviderType.GEMINI_TTS, ToyVoiceSettings().provider)
    }

    @Test
    fun fallbackOrder_forGeminiUsesOpenAiAzureAndLocal() {
        assertEquals(
            listOf(
                ToyVoiceProviderType.GEMINI_TTS,
                ToyVoiceProviderType.OPENAI_TTS,
                ToyVoiceProviderType.AZURE_NEURAL,
                ToyVoiceProviderType.LOCAL
            ),
            ToyVoiceProviderFallbackOrder.forPreferred(ToyVoiceProviderType.GEMINI_TTS)
        )
    }

    @Test
    fun fallbackOrder_forOpenAiUsesAzureAndLocal() {
        assertEquals(
            listOf(
                ToyVoiceProviderType.OPENAI_TTS,
                ToyVoiceProviderType.AZURE_NEURAL,
                ToyVoiceProviderType.LOCAL
            ),
            ToyVoiceProviderFallbackOrder.forPreferred(ToyVoiceProviderType.OPENAI_TTS)
        )
    }

    @Test
    fun fallbackOrder_forAzureUsesLocal() {
        assertEquals(
            listOf(ToyVoiceProviderType.AZURE_NEURAL, ToyVoiceProviderType.LOCAL),
            ToyVoiceProviderFallbackOrder.forPreferred(ToyVoiceProviderType.AZURE_NEURAL)
        )
    }

    @Test
    fun fallbackOrder_forLocalUsesOnlyLocal() {
        assertEquals(
            listOf(ToyVoiceProviderType.LOCAL),
            ToyVoiceProviderFallbackOrder.forPreferred(ToyVoiceProviderType.LOCAL)
        )
    }

    @Test
    fun speakWithFallback_usesAzureWhenOpenAiFails() = runBlocking {
        val outcome = ToyVoiceFallback.speakWithFallback(
            text = "Hola",
            providerRequested = ToyVoiceProviderType.OPENAI_TTS,
            providers = listOf(
                ToyVoiceProviderType.OPENAI_TTS to FakeProvider(
                    VoicePlaybackResult.Error(VoiceErrorType.NOT_CONFIGURED, "sin openai")
                ),
                ToyVoiceProviderType.AZURE_NEURAL to FakeProvider(VoicePlaybackResult.Success()),
                ToyVoiceProviderType.LOCAL to FakeProvider(VoicePlaybackResult.Success())
            )
        )

        assertTrue(outcome is VoiceOutcome.Completed)
        assertEquals(ToyVoiceProviderType.OPENAI_TTS, outcome.providerRequested)
        assertEquals(ToyVoiceProviderType.AZURE_NEURAL, outcome.providerUsed)
        assertTrue(outcome.fallbackUsed)
        assertEquals("sin openai", outcome.errorMessage)
    }

    @Test
    fun speakWithFallback_usesOpenAiWithoutFallbackWhenItSucceeds() = runBlocking {
        val outcome = ToyVoiceFallback.speakWithFallback(
            text = "Hola",
            providerRequested = ToyVoiceProviderType.OPENAI_TTS,
            providers = listOf(
                ToyVoiceProviderType.OPENAI_TTS to FakeProvider(VoicePlaybackResult.Success()),
                ToyVoiceProviderType.AZURE_NEURAL to FakeProvider(VoicePlaybackResult.Success())
            )
        )

        assertTrue(outcome is VoiceOutcome.Completed)
        assertEquals(ToyVoiceProviderType.OPENAI_TTS, outcome.providerUsed)
        assertFalse(outcome.fallbackUsed)
    }

    @Test
    fun speakWithFallback_geminiFailureFallsBackToOpenAi() = runBlocking {
        val outcome = ToyVoiceFallback.speakWithFallback(
            text = "Hola",
            providerRequested = ToyVoiceProviderType.GEMINI_TTS,
            providers = listOf(
                ToyVoiceProviderType.GEMINI_TTS to FakeProvider(
                    VoicePlaybackResult.Error(VoiceErrorType.HTTP_ERROR, "sin gemini")
                ),
                ToyVoiceProviderType.OPENAI_TTS to FakeProvider(VoicePlaybackResult.Success()),
                ToyVoiceProviderType.AZURE_NEURAL to FakeProvider(VoicePlaybackResult.Success()),
                ToyVoiceProviderType.LOCAL to FakeProvider(VoicePlaybackResult.Success())
            )
        )

        assertTrue(outcome is VoiceOutcome.Completed)
        assertEquals(ToyVoiceProviderType.GEMINI_TTS, outcome.providerRequested)
        assertEquals(ToyVoiceProviderType.OPENAI_TTS, outcome.providerUsed)
        assertTrue(outcome.fallbackUsed)
        assertEquals("sin gemini", outcome.errorMessage)
    }

    @Test
    fun sevenVoiceService_reportsRequestedUsedAndFallback() = runBlocking {
        val service = SevenVoiceService(
            geminiProvider = FakeProvider(
                VoicePlaybackResult.Error(VoiceErrorType.HTTP_ERROR, "sin gemini")
            ),
            openAiProvider = FakeProvider(VoicePlaybackResult.Success()),
            azureProvider = FakeProvider(VoicePlaybackResult.Success()),
            localProvider = FakeProvider(VoicePlaybackResult.Success()),
            preferredProvider = { ToyVoiceProviderType.GEMINI_TTS }
        )

        val outcome = service.speak("Hola")

        assertTrue(outcome is VoiceOutcome.Completed)
        assertEquals(ToyVoiceProviderType.GEMINI_TTS, outcome.providerRequested)
        assertEquals(ToyVoiceProviderType.OPENAI_TTS, outcome.providerUsed)
        assertTrue(outcome.fallbackUsed)
    }

    @Test
    fun speakWithFallback_nullText_skipsProviders() = runBlocking {
        val provider = CountingProvider(VoicePlaybackResult.Success())

        val outcome = ToyVoiceFallback.speakWithFallback(
            text = null,
            providerRequested = ToyVoiceProviderType.OPENAI_TTS,
            providers = listOf(ToyVoiceProviderType.OPENAI_TTS to provider)
        )

        assertTrue(outcome is VoiceOutcome.SkippedInvalidText)
        assertEquals(0, provider.calls)
        assertEquals(ToyVoiceProviderType.OPENAI_TTS, outcome.providerRequested)
        assertEquals(null, outcome.providerUsed)
        assertFalse(outcome.fallbackUsed)
    }

    @Test
    fun speakWithFallback_placeholderText_skipsProviders() = runBlocking {
        val provider = CountingProvider(VoicePlaybackResult.Success())

        val outcome = ToyVoiceFallback.speakWithFallback(
            text = "No hay texto para reproducir",
            providerRequested = ToyVoiceProviderType.OPENAI_TTS,
            providers = listOf(ToyVoiceProviderType.OPENAI_TTS to provider)
        )

        assertTrue(outcome is VoiceOutcome.SkippedInvalidText)
        assertEquals(0, provider.calls)
    }

    @Test
    fun speakWithFallback_invalidGeminiTextDoesNotUseFallback() = runBlocking {
        val gemini = CountingProvider(VoicePlaybackResult.Success())
        val openAi = CountingProvider(VoicePlaybackResult.Success())

        val outcome = ToyVoiceFallback.speakWithFallback(
            text = "No hay texto para reproducir",
            providerRequested = ToyVoiceProviderType.GEMINI_TTS,
            providers = listOf(
                ToyVoiceProviderType.GEMINI_TTS to gemini,
                ToyVoiceProviderType.OPENAI_TTS to openAi
            )
        )

        assertTrue(outcome is VoiceOutcome.SkippedInvalidText)
        assertEquals(ToyVoiceProviderType.GEMINI_TTS, outcome.providerRequested)
        assertEquals(null, outcome.providerUsed)
        assertFalse(outcome.fallbackUsed)
        assertEquals(0, gemini.calls)
        assertEquals(0, openAi.calls)
    }

    private class FakeProvider(
        private val result: VoicePlaybackResult
    ) : ToyVoiceProvider {
        override suspend fun speak(text: String, onPlaybackStart: () -> Unit): VoicePlaybackResult = result
        override fun isConfigured(): Boolean = true
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class CountingProvider(
        private val result: VoicePlaybackResult
    ) : ToyVoiceProvider {
        var calls = 0
            private set

        override suspend fun speak(text: String, onPlaybackStart: () -> Unit): VoicePlaybackResult {
            calls += 1
            return result
        }

        override fun isConfigured(): Boolean = true
        override fun stop() = Unit
        override fun release() = Unit
    }
}
