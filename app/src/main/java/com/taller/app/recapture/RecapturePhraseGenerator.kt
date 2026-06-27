package com.taller.app.recapture

import com.taller.app.gpt.GptClient
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.StructuredGptResult
import com.taller.app.voice.ToyVoiceTextValidator
import kotlinx.coroutines.withTimeoutOrNull

enum class RecapturePhraseSource {
    GPT,
    LOCAL
}

data class RecapturePhraseResult(
    val text: String,
    val source: RecapturePhraseSource,
    val latencyMs: Long? = null,
    val fallbackUsed: Boolean = false,
    val errorType: String? = null
)

class RecapturePhraseGenerator(
    private val gptClient: GptClient?,
    private val localBank: RecapturePhraseBank,
    private val timeoutMs: Long = 2_500L
) {
    suspend fun generate(
        topic: String?,
        attemptNumber: Int,
        maxAttempts: Int = RecapturePolicy.MAX_RECAPTURES_PER_QUESTION
    ): RecapturePhraseResult {
        val fallback = { errorType: String? ->
            RecapturePhraseResult(
                text = localBank.firstForAttempt(attemptNumber),
                source = RecapturePhraseSource.LOCAL,
                fallbackUsed = true,
                errorType = errorType
            )
        }

        val client = gptClient
        if (client == null || !client.isEnabled() || !client.isConfigured()) {
            return fallback("GPT_DISABLED_OR_NOT_CONFIGURED")
        }

        val input = recaptureInput(topic, attemptNumber, maxAttempts)
        val result = withTimeoutOrNull(timeoutMs) {
            runCatching { client.generateStructured(input) }.getOrNull()
        } ?: return fallback("GPT_TIMEOUT_OR_FAILURE")

        val text = result.response.visibleText
        val safe = result.validation.isValid &&
            ToyVoiceTextValidator.validate(text).isValid &&
            RecapturePhraseBank.isSafeRecapturePhrase(text)
        if (!safe) {
            return fallback(result.errorType?.name ?: "GPT_UNSAFE_TEXT")
        }

        return RecapturePhraseResult(
            text = text,
            source = RecapturePhraseSource.GPT,
            latencyMs = result.latencyMs,
            fallbackUsed = result.fallbackUsed || result is StructuredGptResult.Fallback,
            errorType = result.errorType?.name
        )
    }

    companion object {
        fun recaptureInput(
            topic: String?,
            attemptNumber: Int,
            maxAttempts: Int = RecapturePolicy.MAX_RECAPTURES_PER_QUESTION
        ): SevenInputContract = SevenInputContract(
            intent = "recapture_attention",
            topic = topic?.takeIf { it.isNotBlank() } ?: "exploración",
            questionText = "",
            localEvaluation = "not_applicable",
            attemptsRemaining = 0,
            expectedResponseType = "recapture",
            canGiveHint = false,
            canGiveFinalAnswer = false,
            maxWords = 18,
            allowedHint = "",
            restrictions = listOf(
                "no_personal_data",
                "no_ai_mention",
                "no_camera_mention",
                "no_face_mention",
                "no_answer_reveal",
                "spanish_latin_only",
                "no_markdown",
                "no_emoji",
                "no_lists"
            ),
            language = "es-419",
            tone = "friendly_curious_alien",
            contextTag = "recapture_attention",
            answerTokens = emptyList(),
            recaptureAttemptNumber = attemptNumber,
            recaptureMaxAttempts = maxAttempts,
            recaptureKind = "attention_lost"
        ).also {
            require(!it.toJsonString().contains("faceDetected"))
            require(attemptNumber in 1..maxAttempts.coerceAtLeast(1))
        }
    }
}
