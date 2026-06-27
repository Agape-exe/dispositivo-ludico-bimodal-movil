package com.taller.app.recapture

object RecapturePolicy {
    const val ATTENTION_LOST_BEFORE_RECAPTURE_MS = 3_000L
    const val RECAPTURE_COOLDOWN_MS = 15_000L
    const val MAX_RECAPTURES_PER_QUESTION = 2
    const val MAX_RECAPTURES_PER_SESSION = 5
    const val MAX_SILENCE_AFTER_FINAL_MS = 20_000L
    const val POST_FEEDBACK_EXTRA_COOLDOWN_MS = 2_000L
    const val POSITIVE_RETURN_COOLDOWN_MS = 30_000L
    const val SUPPRESS_WHILE_SPEAKING = true
    const val SUPPRESS_WHILE_LISTENING = true
}
