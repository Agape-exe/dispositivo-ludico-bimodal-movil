package com.taller.app.gpt

import com.taller.app.BuildConfig

data class GptRuntimeSettings(
    val enabled: Boolean = BuildConfig.OPENAI_GPT_ENABLED,
    val model: String = defaultModel(),
    val fallbackModel: String = defaultFallbackModel(),
    val maxOutputTokens: Int = defaultMaxOutputTokens(),
    val timeoutMs: Int = defaultTimeoutMs(),
    val temperature: Float = defaultTemperature(),
    val localFallbackEnabled: Boolean = true,
    val structuredOutputsEnabled: Boolean = true
) {
    fun sanitized(): GptRuntimeSettings = copy(
        model = sanitizeModel(model, DEFAULT_MODEL),
        fallbackModel = sanitizeModel(fallbackModel, DEFAULT_FALLBACK_MODEL),
        maxOutputTokens = maxOutputTokens.coerceIn(MIN_MAX_OUTPUT_TOKENS, MAX_MAX_OUTPUT_TOKENS),
        timeoutMs = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
        temperature = temperature.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE),
        structuredOutputsEnabled = true
    )

    companion object {
        const val DEFAULT_MODEL = "gpt-5.4-mini"
        const val DEFAULT_FALLBACK_MODEL = "gpt-5.4-nano"
        const val DEFAULT_MAX_OUTPUT_TOKENS = 220
        const val DEFAULT_TIMEOUT_MS = 12_000
        const val DEFAULT_TEMPERATURE = 0.4f

        const val MIN_MAX_OUTPUT_TOKENS = 80
        const val MAX_MAX_OUTPUT_TOKENS = 400
        const val MIN_TIMEOUT_MS = 3_000
        const val MAX_TIMEOUT_MS = 20_000
        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 1.0f

        val ALLOWED_MODELS = listOf("gpt-5.4-mini", "gpt-5.4-nano", "gpt-5.4")

        fun defaults(): GptRuntimeSettings = GptRuntimeSettings().sanitized()

        fun sanitizeModel(value: String, fallback: String): String =
            value.trim().takeIf { it in ALLOWED_MODELS } ?: fallback

        private fun defaultModel(): String =
            sanitizeModel(BuildConfig.OPENAI_GPT_MODEL, DEFAULT_MODEL)

        private fun defaultFallbackModel(): String =
            sanitizeModel(BuildConfig.OPENAI_GPT_FALLBACK_MODEL, DEFAULT_FALLBACK_MODEL)

        private fun defaultMaxOutputTokens(): Int =
            BuildConfig.OPENAI_GPT_MAX_OUTPUT_TOKENS.takeIf { it > 0 } ?: DEFAULT_MAX_OUTPUT_TOKENS

        private fun defaultTimeoutMs(): Int =
            BuildConfig.OPENAI_GPT_TIMEOUT_MS.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS

        private fun defaultTemperature(): Float =
            BuildConfig.OPENAI_GPT_TEMPERATURE.takeIf { it >= MIN_TEMPERATURE } ?: DEFAULT_TEMPERATURE
    }
}
