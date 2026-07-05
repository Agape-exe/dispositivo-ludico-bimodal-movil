package com.taller.app.settings

data class AppSettings(
    val classicResponseTimeSeconds: Int = DEFAULT_CLASSIC_RESPONSE_TIME_SECONDS,
    val intelligentMaxRecaptures: Int = DEFAULT_INTELLIGENT_MAX_RECAPTURES,
    /**
     * FINAL-FLOW01: la camara/atencion del modo inteligente es opcional. Apagada por
     * defecto: la sesion no exige camara, no cuenta perdidas de atencion y nunca se
     * interrumpe por falta de recaptura.
     */
    val intelligentAttentionEnabled: Boolean = DEFAULT_INTELLIGENT_ATTENTION_ENABLED,
    /**
     * FINAL-FLOW01: la recaptura por voz solo actua si la atencion esta activada Y
     * esta opcion avanzada tambien. Apagada por defecto: con atencion activa se
     * observa/mide sin interrumpir la sesion.
     */
    val intelligentRecaptureEnabled: Boolean = DEFAULT_INTELLIGENT_RECAPTURE_ENABLED,
    /** FINAL-FLOW01: conversacion inicial breve y opcional antes de las preguntas. */
    val initialConversationEnabled: Boolean = DEFAULT_INITIAL_CONVERSATION_ENABLED,
    val initialConversationMaxChildTurns: Int = DEFAULT_INITIAL_CONVERSATION_MAX_TURNS,
    val initialConversationMaxDurationSeconds: Int = DEFAULT_INITIAL_CONVERSATION_MAX_DURATION_SECONDS,
    /**
     * FINAL-FLOW01: ventana temprana de escucha en reintentos, para no perder la
     * respuesta del nino que se adelanta al enunciado completo de la pregunta.
     */
    val earlyAnswerCaptureEnabled: Boolean = DEFAULT_EARLY_ANSWER_CAPTURE_ENABLED,
    val earlyAnswerWindowMs: Int = DEFAULT_EARLY_ANSWER_WINDOW_MS,
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
        initialConversationMaxChildTurns = initialConversationMaxChildTurns.coerceIn(
            MIN_INITIAL_CONVERSATION_TURNS,
            MAX_INITIAL_CONVERSATION_TURNS
        ),
        initialConversationMaxDurationSeconds = initialConversationMaxDurationSeconds.coerceIn(
            MIN_INITIAL_CONVERSATION_DURATION_SECONDS,
            MAX_INITIAL_CONVERSATION_DURATION_SECONDS
        ),
        earlyAnswerWindowMs = earlyAnswerWindowMs.coerceIn(
            MIN_EARLY_ANSWER_WINDOW_MS,
            MAX_EARLY_ANSWER_WINDOW_MS
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

        const val DEFAULT_INTELLIGENT_ATTENTION_ENABLED = false
        const val DEFAULT_INTELLIGENT_RECAPTURE_ENABLED = false

        const val DEFAULT_INITIAL_CONVERSATION_ENABLED = false
        const val DEFAULT_INITIAL_CONVERSATION_MAX_TURNS = 1
        const val MIN_INITIAL_CONVERSATION_TURNS = 0
        const val MAX_INITIAL_CONVERSATION_TURNS = 3
        const val DEFAULT_INITIAL_CONVERSATION_MAX_DURATION_SECONDS = 60
        const val MIN_INITIAL_CONVERSATION_DURATION_SECONDS = 15
        const val MAX_INITIAL_CONVERSATION_DURATION_SECONDS = 120

        const val DEFAULT_EARLY_ANSWER_CAPTURE_ENABLED = true
        const val DEFAULT_EARLY_ANSWER_WINDOW_MS = 2000
        const val MIN_EARLY_ANSWER_WINDOW_MS = 500
        const val MAX_EARLY_ANSWER_WINDOW_MS = 4000

        fun defaults(): AppSettings = AppSettings()

        fun isValidClassicResponseTime(value: Int): Boolean =
            value in MIN_CLASSIC_RESPONSE_TIME_SECONDS..MAX_CLASSIC_RESPONSE_TIME_SECONDS

        fun isValidIntelligentMaxRecaptures(value: Int): Boolean =
            value in MIN_INTELLIGENT_MAX_RECAPTURES..MAX_INTELLIGENT_MAX_RECAPTURES

        fun isValidInitialConversationTurns(value: Int): Boolean =
            value in MIN_INITIAL_CONVERSATION_TURNS..MAX_INITIAL_CONVERSATION_TURNS

        fun isValidInitialConversationDurationSeconds(value: Int): Boolean =
            value in MIN_INITIAL_CONVERSATION_DURATION_SECONDS..MAX_INITIAL_CONVERSATION_DURATION_SECONDS

        fun isValidEarlyAnswerWindowMs(value: Int): Boolean =
            value in MIN_EARLY_ANSWER_WINDOW_MS..MAX_EARLY_ANSWER_WINDOW_MS
    }
}
