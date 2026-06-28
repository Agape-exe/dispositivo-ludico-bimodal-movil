package com.taller.app.voice.neural

import com.taller.app.voice.VoiceErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRateLimitGateTest {

    private var nowMs = 0L
    private fun gate() = GeminiRateLimitGate(now = { nowMs })

    @Test
    fun startsWithoutCooldown() {
        val gate = gate()
        assertFalse(gate.isInCooldown())
        assertEquals(0L, gate.remainingMs())
        assertNull(gate.activeReason())
    }

    @Test
    fun rateLimitActivatesDefaultCooldownWhenNoRetryAfter() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = null)

        assertTrue(gate.isInCooldown())
        assertEquals(GeminiRateLimitGate.DEFAULT_COOLDOWN_MS, gate.remainingMs())
        assertEquals(VoiceErrorType.RATE_LIMITED, gate.activeReason())
    }

    @Test
    fun cooldownExpiresAfterDuration() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.QUOTA_EXHAUSTED, retryAfterMs = null)

        nowMs += GeminiRateLimitGate.DEFAULT_COOLDOWN_MS - 1L
        assertTrue(gate.isInCooldown())

        nowMs += 1L
        assertFalse(gate.isInCooldown())
        assertEquals(0L, gate.remainingMs())
        assertNull(gate.activeReason())
    }

    @Test
    fun retryAfterIsRespectedWithinBounds() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = 120_000L)
        assertEquals(120_000L, gate.remainingMs())
    }

    @Test
    fun retryAfterIsCappedAtMaxCooldown() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.QUOTA_EXHAUSTED, retryAfterMs = 60 * 60_000L)
        assertEquals(GeminiRateLimitGate.MAX_COOLDOWN_MS, gate.remainingMs())
    }

    @Test
    fun consecutiveRateLimitsExtendCooldownUpToMax() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED)
        assertEquals(GeminiRateLimitGate.DEFAULT_COOLDOWN_MS, gate.remainingMs())

        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED)
        assertEquals(2 * GeminiRateLimitGate.DEFAULT_COOLDOWN_MS, gate.remainingMs())

        repeat(10) { gate.registerRateLimit(VoiceErrorType.RATE_LIMITED) }
        assertEquals(GeminiRateLimitGate.MAX_COOLDOWN_MS, gate.remainingMs())
    }

    @Test
    fun successClearsCooldownAndConsecutiveCount() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED)
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED)
        gate.registerSuccess()

        assertFalse(gate.isInCooldown())
        // Tras un exito, el contador de consecutivos se reinicia: un nuevo 429
        // vuelve a empezar desde el cooldown por defecto.
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED)
        assertEquals(GeminiRateLimitGate.DEFAULT_COOLDOWN_MS, gate.remainingMs())
    }

    @Test
    fun clearResetsCooldownImmediately() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.QUOTA_EXHAUSTED)
        assertTrue(gate.isInCooldown())

        gate.clear()
        assertFalse(gate.isInCooldown())
        assertEquals(0L, gate.remainingMs())
        assertNull(gate.activeReason())
    }
}
