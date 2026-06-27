package com.taller.app.recapture

import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptPrompt
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.SevenBlockedReason
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.SevenIntent
import com.taller.app.gpt.SevenLocalEvaluation
import com.taller.app.gpt.SevenResponse
import com.taller.app.gpt.SevenResponseType
import com.taller.app.gpt.SevenResponseValidator
import com.taller.app.gpt.SevenSafetyLevel
import com.taller.app.gpt.StructuredGptResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapturePhraseGeneratorTest {
    @Test
    fun localBankContainsNoProhibitedTerms() {
        RecapturePhraseBank.allLocalPhrases().forEach { phrase ->
            assertTrue(phrase, RecapturePhraseBank.isSafeRecapturePhrase(phrase))
        }
    }

    @Test
    fun recapturePayloadContainsNoQuestionOrSensitiveData() {
        val input = RecapturePhraseGenerator.recaptureInput(
            topic = "Animales",
            attemptNumber = 1
        )

        val json = input.toJsonString()

        assertTrue(json.contains("recapture_attention"))
        assertFalse(json.contains("¿Cuál es la respuesta?"))
        assertFalse(json.contains("faceDetected"))
        assertFalse(json.contains("lookingAtDevice"))
        assertFalse(json.contains("yaw"))
        assertFalse(json.contains("pitch"))
        assertFalse(json.contains("roll"))
        assertFalse(json.contains("transcription"))
        assertTrue(json.contains("no_camera_mention"))
    }

    @Test
    fun disabledGptUsesLocalFallback() = runBlocking {
        val generator = RecapturePhraseGenerator(
            gptClient = FakeGptClient(enabled = false, configured = true),
            localBank = RecapturePhraseBank()
        )

        val result = generator.generate(topic = "Animales", attemptNumber = 1)

        assertEquals(RecapturePhraseSource.LOCAL, result.source)
        assertTrue(result.fallbackUsed)
    }

    @Test
    fun unsafeGptPhraseUsesLocalFallback() = runBlocking {
        val generator = RecapturePhraseGenerator(
            gptClient = FakeGptClient(
                responseText = "Te veo con la cámara, explorador."
            ),
            localBank = RecapturePhraseBank()
        )

        val result = generator.generate(topic = "Animales", attemptNumber = 1)

        assertEquals(RecapturePhraseSource.LOCAL, result.source)
        assertTrue(result.fallbackUsed)
        assertTrue(RecapturePhraseBank.isSafeRecapturePhrase(result.text))
    }

    @Test
    fun safeGptPhraseIsUsed() = runBlocking {
        val generator = RecapturePhraseGenerator(
            gptClient = FakeGptClient(responseText = "¡Ey, explorador! La misión sigue esperando."),
            localBank = RecapturePhraseBank()
        )

        val result = generator.generate(topic = "Animales", attemptNumber = 1)

        assertEquals(RecapturePhraseSource.GPT, result.source)
        assertFalse(result.fallbackUsed)
    }

    private class FakeGptClient(
        private val enabled: Boolean = true,
        private val configured: Boolean = true,
        private val responseText: String = "¡Ey, explorador! La misión sigue esperando."
    ) : GptClient {
        override suspend fun generate(prompt: GptPrompt): GptResult = GptResult.Disabled("")

        override suspend fun generateStructured(input: SevenInputContract): StructuredGptResult {
            val response = SevenResponse(
                intent = SevenIntent.RECAPTURE_ATTENTION,
                responseType = SevenResponseType.RECAPTURE,
                visibleText = responseText,
                safetyLevel = SevenSafetyLevel.SAFE,
                fallbackUsed = false,
                canGiveHint = false,
                canGiveFinalAnswer = false,
                shouldAskRepeat = false,
                shouldRecaptureAttention = true,
                topic = input.topic,
                localEvaluation = SevenLocalEvaluation.NOT_APPLICABLE,
                attemptsRemaining = 0,
                maxWords = input.maxWords,
                blockedReason = SevenBlockedReason.NONE,
                safeForTts = true,
                validationNotes = "test"
            )
            return StructuredGptResult.Success(
                response = response,
                validation = SevenResponseValidator.validate(response, input),
                modelUsed = "test",
                latencyMs = 5L,
                fallbackUsed = false,
                rawTextLength = responseText.length
            )
        }

        override fun isEnabled(): Boolean = enabled

        override fun isConfigured(): Boolean = configured
    }
}
