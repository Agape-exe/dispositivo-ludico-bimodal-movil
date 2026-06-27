package com.taller.app.gpt

data class SevenResponse(
    val intent: SevenIntent,
    val responseType: SevenResponseType,
    val visibleText: String,
    val safetyLevel: SevenSafetyLevel,
    val fallbackUsed: Boolean,
    val canGiveHint: Boolean,
    val canGiveFinalAnswer: Boolean,
    val shouldAskRepeat: Boolean,
    val shouldRecaptureAttention: Boolean,
    val topic: String,
    val localEvaluation: SevenLocalEvaluation,
    val attemptsRemaining: Int,
    val maxWords: Int,
    val blockedReason: SevenBlockedReason,
    val safeForTts: Boolean,
    val validationNotes: String
)

enum class SevenIntent(val wireValue: String) {
    GREETING("greeting"),
    MISSION_START("mission_start"),
    PRESENT_QUESTION("present_question"),
    FEEDBACK_CORRECT("feedback_correct"),
    FEEDBACK_INCORRECT("feedback_incorrect"),
    NOT_INTERPRETABLE("not_interpretable"),
    RETRY("retry"),
    RECAPTURE_ATTENTION("recapture_attention"),
    CLOSING("closing"),
    FALLBACK("fallback");

    companion object {
        fun parse(value: String): SevenIntent =
            entries.firstOrNull { it.wireValue == value }
                ?: throw SevenParseException("Invalid intent")
    }
}

enum class SevenResponseType(val wireValue: String) {
    GREET("greet"),
    PRESENT("present"),
    PRAISE("praise"),
    HINT("hint"),
    ASK_REPEAT("ask_repeat"),
    ENCOURAGE("encourage"),
    RECAPTURE("recapture"),
    CLOSE("close"),
    FALLBACK("fallback");

    companion object {
        fun parse(value: String): SevenResponseType =
            entries.firstOrNull { it.wireValue == value }
                ?: throw SevenParseException("Invalid responseType")
    }
}

enum class SevenSafetyLevel(val wireValue: String) {
    SAFE("safe"),
    CORRECTED("corrected"),
    BLOCKED("blocked");

    companion object {
        fun parse(value: String): SevenSafetyLevel =
            entries.firstOrNull { it.wireValue == value }
                ?: throw SevenParseException("Invalid safetyLevel")
    }
}

enum class SevenLocalEvaluation(val wireValue: String) {
    CORRECT("correct"),
    INCORRECT("incorrect"),
    NOT_INTERPRETABLE("not_interpretable"),
    NOT_APPLICABLE("not_applicable");

    companion object {
        fun parse(value: String): SevenLocalEvaluation =
            entries.firstOrNull { it.wireValue == value }
                ?: throw SevenParseException("Invalid localEvaluation")
    }
}

enum class SevenBlockedReason(val wireValue: String) {
    NONE("none"),
    UNSAFE_CONTENT("unsafe_content"),
    PRIVACY_RISK("privacy_risk"),
    GIVES_ANSWER_NOT_ALLOWED("gives_answer_not_allowed"),
    MENTIONS_INTERNAL_SYSTEM("mentions_internal_system"),
    TOO_LONG("too_long"),
    OFF_TOPIC("off_topic"),
    INVALID_CONTEXT("invalid_context"),
    UNKNOWN("unknown");

    companion object {
        fun parse(value: String): SevenBlockedReason =
            entries.firstOrNull { it.wireValue == value }
                ?: throw SevenParseException("Invalid blockedReason")
    }
}
