package com.taller.app.gpt

import com.taller.app.BuildConfig

data class GptConfig(
    val apiKey: String,
    val model: String,
    val fallbackModel: String,
    val maxOutputTokens: Int,
    val timeoutMs: Long,
    val temperature: Float,
    val enabled: Boolean,
    val localFallbackEnabled: Boolean = true,
    val structuredOutputsEnabled: Boolean = true
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val isOperational: Boolean get() = enabled && hasApiKey

    companion object {
        const val ENDPOINT = "https://api.openai.com/v1/responses"
        const val DEFAULT_MODEL = "gpt-5.4-mini"
        const val DEFAULT_FALLBACK_MODEL = "gpt-5.4-nano"
        const val DEFAULT_MAX_OUTPUT_TOKENS = 220
        const val DEFAULT_TIMEOUT_MS = 12_000L
        const val DEFAULT_TEMPERATURE = 0.4f

        // AVISO: La API key se lee de local.properties vía BuildConfig.
        // Solo válido para prototipo local de tesis. Nunca distribuir ni subir al repositorio.
        // En producción: sustituir por backend proxy que no exponga credenciales al cliente.
        fun fromBuild(): GptConfig = GptConfig(
            apiKey = BuildConfig.OPENAI_GPT_API_KEY,
            model = GptRuntimeSettings.sanitizeModel(BuildConfig.OPENAI_GPT_MODEL, DEFAULT_MODEL),
            fallbackModel = GptRuntimeSettings.sanitizeModel(
                BuildConfig.OPENAI_GPT_FALLBACK_MODEL,
                DEFAULT_FALLBACK_MODEL
            ),
            maxOutputTokens = BuildConfig.OPENAI_GPT_MAX_OUTPUT_TOKENS.takeIf { it > 0 }
                ?: DEFAULT_MAX_OUTPUT_TOKENS,
            timeoutMs = BuildConfig.OPENAI_GPT_TIMEOUT_MS.toLong().takeIf { it > 0L }
                ?: DEFAULT_TIMEOUT_MS,
            temperature = BuildConfig.OPENAI_GPT_TEMPERATURE.takeIf { it >= 0f }
                ?: DEFAULT_TEMPERATURE,
            enabled = BuildConfig.OPENAI_GPT_ENABLED,
            localFallbackEnabled = true,
            structuredOutputsEnabled = true
        ).sanitized()

        fun fromBuild(runtimeSettings: GptRuntimeSettings): GptConfig {
            val safe = runtimeSettings.sanitized()
            return GptConfig(
                apiKey = BuildConfig.OPENAI_GPT_API_KEY,
                model = safe.model,
                fallbackModel = safe.fallbackModel,
                maxOutputTokens = safe.maxOutputTokens,
                timeoutMs = safe.timeoutMs.toLong(),
                temperature = safe.temperature,
                enabled = safe.enabled,
                localFallbackEnabled = safe.localFallbackEnabled,
                structuredOutputsEnabled = true
            ).sanitized()
        }

        private fun GptConfig.sanitized(): GptConfig = copy(
            model = GptRuntimeSettings.sanitizeModel(model, DEFAULT_MODEL),
            fallbackModel = GptRuntimeSettings.sanitizeModel(fallbackModel, DEFAULT_FALLBACK_MODEL),
            maxOutputTokens = maxOutputTokens.coerceIn(
                GptRuntimeSettings.MIN_MAX_OUTPUT_TOKENS,
                GptRuntimeSettings.MAX_MAX_OUTPUT_TOKENS
            ),
            timeoutMs = timeoutMs.coerceIn(
                GptRuntimeSettings.MIN_TIMEOUT_MS.toLong(),
                GptRuntimeSettings.MAX_TIMEOUT_MS.toLong()
            ),
            temperature = temperature.coerceIn(
                GptRuntimeSettings.MIN_TEMPERATURE,
                GptRuntimeSettings.MAX_TEMPERATURE
            ),
            structuredOutputsEnabled = true
        )
    }
}
