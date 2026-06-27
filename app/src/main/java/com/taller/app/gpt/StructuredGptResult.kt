package com.taller.app.gpt

sealed interface StructuredGptResult {
    val response: SevenResponse
    val validation: ValidationResult
    val modelUsed: String
    val latencyMs: Long
    val fallbackUsed: Boolean
    val errorType: GptErrorType?
    val safeMessage: String?
    val rawTextLength: Int

    data class Success(
        override val response: SevenResponse,
        override val validation: ValidationResult,
        override val modelUsed: String,
        override val latencyMs: Long,
        override val fallbackUsed: Boolean,
        override val rawTextLength: Int
    ) : StructuredGptResult {
        override val errorType: GptErrorType? = null
        override val safeMessage: String? = null
    }

    data class Fallback(
        override val response: SevenResponse,
        override val validation: ValidationResult,
        override val modelUsed: String,
        override val latencyMs: Long,
        override val fallbackUsed: Boolean,
        override val errorType: GptErrorType,
        override val safeMessage: String,
        override val rawTextLength: Int = 0
    ) : StructuredGptResult
}
