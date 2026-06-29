package com.taller.app.voice.prep

import com.taller.app.voice.ToyVoiceProviderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionVoicePreparerTest {

    /** Sintetizador falso: decide el resultado por texto y cuenta las llamadas. */
    private class FakeSynthesizer(
        private val results: (String) -> VoiceLinePrepResult
    ) : VoiceLineSynthesizer {
        val calls = mutableListOf<String>()
        override fun isCached(text: String): Boolean = false
        override suspend fun prepare(text: String): VoiceLinePrepResult {
            calls.add(text)
            return results(text)
        }
    }

    private fun line(text: String) = VoiceLine(VoiceLineRole.GENERIC, text)

    private val prepared = VoiceLinePrepResult.Prepared(ToyVoiceProviderType.GEMINI_TTS, fromCache = false)

    @Test
    fun allPreparedYieldsReady() = runBlocking {
        val synth = FakeSynthesizer { prepared }
        val outcome = SessionVoicePreparer(synth).prepare(
            listOf(line("Uno"), line("Dos"), line("Tres"))
        )

        assertEquals(VoicePrepStatus.READY, outcome.status)
        assertEquals(3, outcome.totalCount)
        assertEquals(3, outcome.readyCount)
        assertEquals(0, outcome.failedCount)
        assertEquals(ToyVoiceProviderType.GEMINI_TTS, outcome.providerUsed)
    }

    @Test
    fun someFailuresYieldPartial() = runBlocking {
        val synth = FakeSynthesizer { text ->
            if (text == "Dos") VoiceLinePrepResult.Failed("Sin conexión") else prepared
        }
        val outcome = SessionVoicePreparer(synth).prepare(
            listOf(line("Uno"), line("Dos"), line("Tres"))
        )

        assertEquals(VoicePrepStatus.PARTIAL, outcome.status)
        assertEquals(2, outcome.readyCount)
        assertEquals(1, outcome.failedCount)
        assertEquals(1, outcome.missingCount)
        assertEquals("Sin conexión", outcome.lastError)
    }

    @Test
    fun allFailuresYieldFailed() = runBlocking {
        val synth = FakeSynthesizer { VoiceLinePrepResult.Failed("Error seguro") }
        val outcome = SessionVoicePreparer(synth).prepare(listOf(line("Uno"), line("Dos")))

        assertEquals(VoicePrepStatus.FAILED, outcome.status)
        assertEquals(0, outcome.readyCount)
        assertEquals(2, outcome.failedCount)
    }

    @Test
    fun emptyListYieldsFailed() = runBlocking {
        val synth = FakeSynthesizer { prepared }
        val outcome = SessionVoicePreparer(synth).prepare(emptyList())

        assertEquals(VoicePrepStatus.FAILED, outcome.status)
        assertEquals(0, outcome.totalCount)
        assertTrue(synth.calls.isEmpty())
    }

    @Test
    fun duplicateTextsAreSynthesizedOnce() = runBlocking {
        val synth = FakeSynthesizer { prepared }
        val outcome = SessionVoicePreparer(synth).prepare(
            listOf(line("Hola"), line("  Hola "), line("Adiós"))
        )

        // "Hola" y "  Hola " comparten clave normalizada: una sola sintesis.
        assertEquals(2, outcome.totalCount)
        assertEquals(2, synth.calls.size)
        assertEquals(VoicePrepStatus.READY, outcome.status)
    }

    @Test
    fun progressIsReportedForEachLine() = runBlocking {
        val synth = FakeSynthesizer { prepared }
        val snapshots = mutableListOf<VoicePrepProgress>()
        SessionVoicePreparer(synth).prepare(listOf(line("A"), line("B"))) { snapshots.add(it) }

        // Emite el estado inicial y uno por cada linea preparada.
        assertEquals(VoicePrepProgress(2, 0, 0), snapshots.first())
        assertEquals(VoicePrepProgress(2, 2, 0), snapshots.last())
        assertTrue(snapshots.last().isDone)
    }
}
