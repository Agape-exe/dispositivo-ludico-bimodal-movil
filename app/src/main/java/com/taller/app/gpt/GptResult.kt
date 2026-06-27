package com.taller.app.gpt

sealed interface GptResult {
    data class Success(
        val text: String,
        val modelUsed: String,
        val latencyMs: Long,
        val fallbackUsed: Boolean = false
    ) : GptResult

    data class Disabled(
        val fallbackText: String
    ) : GptResult

    data class Failure(
        val errorType: GptErrorType,
        val safeMessage: String,
        val fallbackText: String,
        val modelAttempted: String
    ) : GptResult
}
