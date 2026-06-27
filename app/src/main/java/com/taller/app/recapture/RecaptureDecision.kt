package com.taller.app.recapture

sealed class RecaptureDecision {
    data object Idle : RecaptureDecision()
    data class Suppress(val reason: RecaptureReason) : RecaptureDecision()
    data class WaitMore(val msRemaining: Long) : RecaptureDecision()
    data class WaitCooldown(val msRemaining: Long) : RecaptureDecision()
    data class Execute(
        val attemptInQuestion: Int,
        val attemptInSession: Int,
        val reason: RecaptureReason
    ) : RecaptureDecision()
    data class CloseGracefully(val reason: RecaptureReason) : RecaptureDecision()
    data class Cancel(val reason: RecaptureReason) : RecaptureDecision()
}
