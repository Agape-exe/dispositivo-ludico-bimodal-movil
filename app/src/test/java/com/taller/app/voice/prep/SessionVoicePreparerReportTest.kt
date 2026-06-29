package com.taller.app.voice.prep

import com.taller.app.voice.ToyVoiceProviderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionVoicePreparerReportTest {

    private class FakeSynthesizer(
        private val results: (String) -> VoiceLinePrepResult
    ) : VoiceLineSynthesizer {
        override fun isCached(text: String): Boolean = false
        override suspend fun prepare(text: String): VoiceLinePrepResult = results(text)
    }

    private fun line(role: VoiceLineRole, text: String) = VoiceLine(role, text)

    private fun gemini(fromCache: Boolean = true) = VoiceLinePrepResult.Prepared(
        provider = ToyVoiceProviderType.GEMINI_TTS,
        fromCache = fromCache,
        model = "gemini-3.1-flash-tts-preview",
        voice = "Puck",
        cacheKeyShort = "abc123"
    )

    private fun openAi() = VoiceLinePrepResult.Prepared(
        provider = ToyVoiceProviderType.OPENAI_TTS,
        fromCache = true,
        model = "gpt-4o-mini-tts",
        voice = "marin",
        cacheKeyShort = "def456"
    )

    @Test
    fun allGeminiYieldsReadyIdeal() = runBlocking {
        val synth = FakeSynthesizer { gemini() }
        val outcome = SessionVoicePreparer(synth).prepare(
            listOf(line(VoiceLineRole.INTRO, "Hola"), line(VoiceLineRole.CLOSING, "Adiós"))
        )
        val report = outcome.report!!
        assertEquals(VoicePrepRecommendedStatus.READY_IDEAL, report.recommendedStatus)
        assertEquals(2, report.targetProviderReady)
        assertEquals(0, report.providerMismatch)
        assertEquals(VoicePrepStatus.READY, outcome.status)
        assertTrue(report.items.all { it.isTargetProvider })
        assertEquals("INTRO", report.items.first().lineType)
        assertEquals("Gemini", report.items.first().provider)
    }

    @Test
    fun anyOpenAiYieldsReviewFallbackAndPartial() = runBlocking {
        val synth = FakeSynthesizer { text ->
            if (text == "Adiós") openAi() else gemini()
        }
        val outcome = SessionVoicePreparer(synth).prepare(
            listOf(line(VoiceLineRole.INTRO, "Hola"), line(VoiceLineRole.CLOSING, "Adiós"))
        )
        val report = outcome.report!!
        assertEquals(VoicePrepRecommendedStatus.REVIEW_FALLBACK, report.recommendedStatus)
        assertEquals(1, report.providerMismatch)
        assertEquals(1, report.targetProviderReady)
        // Mezcla de proveedores => no se marca como READY ideal.
        assertEquals(VoicePrepStatus.PARTIAL, outcome.status)
        val mismatchItem = report.items.first { !it.isTargetProvider }
        assertEquals("PROVIDER_MISMATCH", mismatchItem.status)
        assertEquals("OpenAI", mismatchItem.provider)
    }

    @Test
    fun failuresYieldIncomplete() = runBlocking {
        val synth = FakeSynthesizer { text ->
            if (text == "Adiós") VoiceLinePrepResult.Failed("Sin conexión") else gemini()
        }
        val outcome = SessionVoicePreparer(synth).prepare(
            listOf(line(VoiceLineRole.INTRO, "Hola"), line(VoiceLineRole.CLOSING, "Adiós"))
        )
        val report = outcome.report!!
        assertEquals(VoicePrepRecommendedStatus.INCOMPLETE, report.recommendedStatus)
        assertEquals(1, report.failed)
        assertEquals(VoicePrepStatus.PARTIAL, outcome.status)
        val failedItem = report.items.first { it.status == "FAILED" }
        assertEquals("Sin conexión", failedItem.error)
    }

    @Test
    fun reportItemsCarryShortHashAndNoLongText() = runBlocking {
        val longText = "Esta es una frase larga de Seven ".repeat(10)
        val synth = FakeSynthesizer { gemini() }
        val outcome = SessionVoicePreparer(synth).prepare(listOf(line(VoiceLineRole.QUESTION, longText)))
        val item = outcome.report!!.items.single()
        // El hash es corto y el reporte no contiene el texto completo.
        assertTrue(item.textHashShort.length <= 16)
        assertFalse(item.toString().contains(longText))
    }
}
