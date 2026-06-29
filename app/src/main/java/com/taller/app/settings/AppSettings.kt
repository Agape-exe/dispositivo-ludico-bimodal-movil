package com.taller.app.settings

data class AppSettings(
    val classicResponseTimeSeconds: Int = DEFAULT_CLASSIC_RESPONSE_TIME_SECONDS,
    val intelligentMaxRecaptures: Int = DEFAULT_INTELLIGENT_MAX_RECAPTURES,
    val updatedAt: Long = 0L
) {
    fun sanitized(nowMs: Long = updatedAt): AppSettings = copy(
        classicResponseTimeSeconds = classicResponseTimeSeconds.coerceIn(
            MIN_CLASSIC_RESPONSE_TIME_SECONDS,
            MAX_CLASSIC_RESPONSE_TIME_SECONDS
        ),
        intelligentMaxRecaptures = intelligentMaxRecaptures.coerceIn(
            MIN_INTELLIGENT_MAX_RECAPTURES,
            MAX_INTELLIGENT_MAX_RECAPTURES
        ),
        updatedAt = nowMs
    )

    companion object {
        const val DEFAULT_CLASSIC_RESPONSE_TIME_SECONDS = 10
        const val MIN_CLASSIC_RESPONSE_TIME_SECONDS = 5
        const val MAX_CLASSIC_RESPONSE_TIME_SECONDS = 120

        const val DEFAULT_INTELLIGENT_MAX_RECAPTURES = 5
        const val MIN_INTELLIGENT_MAX_RECAPTURES = 0
        const val MAX_INTELLIGENT_MAX_RECAPTURES = 10

        fun defaults(): AppSettings = AppSettings()

        fun isValidClassicResponseTime(value: Int): Boolean =
            value in MIN_CLASSIC_RESPONSE_TIME_SECONDS..MAX_CLASSIC_RESPONSE_TIME_SECONDS

        fun isValidIntelligentMaxRecaptures(value: Int): Boolean =
            value in MIN_INTELLIGENT_MAX_RECAPTURES..MAX_INTELLIGENT_MAX_RECAPTURES
    }
}
