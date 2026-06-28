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
    fun rateLimitWithoutRetryAfter_doesNotBlockGemini() {
        val gate = gate()
        // Google no indico cuanto esperar: la app no impone bloqueo propio.
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = null)

        assertFalse(gate.isInCooldown())
        assertEquals(0L, gate.remainingMs())
        assertNull(gate.activeReason())
    }

    @Test
    fun rateLimitWithRetryAfter_blocksOnlyForRequestedTime() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = 17_000L)

        assertTrue(gate.isInCooldown())
        assertEquals(17_000L, gate.remainingMs())
        assertEquals(VoiceErrorType.RATE_LIMITED, gate.activeReason())
    }

    @Test
    fun cooldownExpiresExactlyAfterRequestedTime() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.QUOTA_EXHAUSTED, retryAfterMs = 17_000L)

        nowMs += 16_999L
        assertTrue(gate.isInCooldown())

        nowMs += 1L
        assertFalse(gate.isInCooldown())
        assertEquals(0L, gate.remainingMs())
        assertNull(gate.activeReason())
    }

    @Test
    fun nonPositiveRetryAfter_doesNotBlock() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = 0L)
        assertFalse(gate.isInCooldown())
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = -5_000L)
        assertFalse(gate.isInCooldown())
    }

    @Test
    fun retryAfterIsCappedAtMaxCooldown() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.QUOTA_EXHAUSTED, retryAfterMs = 60 * 60_000L)
        assertEquals(GeminiRateLimitGate.MAX_COOLDOWN_MS, gate.remainingMs())
    }

    @Test
    fun noBackoffEscalation_consecutive429sUseOnlyEachRequestedTime() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = 10_000L)
        assertEquals(10_000L, gate.remainingMs())
        // Un segundo 429 con el mismo retryDelay no se acumula ni escala.
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = 10_000L)
        assertEquals(10_000L, gate.remainingMs())
    }

    @Test
    fun successClearsCooldown() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.RATE_LIMITED, retryAfterMs = 30_000L)
        gate.registerSuccess()
        assertFalse(gate.isInCooldown())
        assertEquals(0L, gate.remainingMs())
    }

    @Test
    fun clearResetsCooldownImmediately() {
        val gate = gate()
        gate.registerRateLimit(VoiceErrorType.QUOTA_EXHAUSTED, retryAfterMs = 30_000L)
        assertTrue(gate.isInCooldown())

        gate.clear()
        assertFalse(gate.isInCooldown())
        assertEquals(0L, gate.remainingMs())
        assertNull(gate.activeReason())
    }
}
