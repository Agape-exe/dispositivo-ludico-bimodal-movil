package com.taller.app.voice.neural

import com.taller.app.voice.VoiceErrorType

/**
 * Compuerta de enfriamiento (cooldown) para Gemini TTS ante respuestas HTTP 429.
 *
 * Politica: **solo se respeta el tiempo de espera que el propio servidor de Google
 * indique** (campo `retryDelay` del error o header `Retry-After`). La app no impone
 * ningun bloqueo propio: si Google no pide esperar, Gemini se vuelve a intentar en la
 * siguiente frase con normalidad. Asi, un 429 suelto (pico momentaneo) solo provoca
 * el respaldo de esa frase, sin bloquear Gemini despues.
 *
 * Cuando Google si pide esperar, durante ese lapso el proveedor de Gemini omite la
 * llamada de red y la cadena usa OpenAI; al vencer, Gemini se reintenta. Un `speak`
 * exitoso o el boton "Reintentar Gemini TTS" limpian el enfriamiento. Gemini nunca se
 * desactiva de forma permanente.
 *
 * Es independiente de Android y usa un reloj inyectable para poder probarse de forma
 * aislada. El proveedor real comparte una unica instancia ([shared]) porque el limite
 * aplica a la API key de todo el proceso. No guarda audio ni texto hablado: solo una
 * marca de tiempo y el tipo de error tecnico.
 */
class GeminiRateLimitGate(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val maxCooldownMs: Long = MAX_COOLDOWN_MS
) {
    @Volatile
    private var cooldownUntilMs: Long = 0L

    @Volatile
    private var lastReason: VoiceErrorType? = null

    /** Indica si Gemini esta en enfriamiento pedido por Google y debe omitirse la red. */
    @Synchronized
    fun isInCooldown(): Boolean = now() < cooldownUntilMs

    /** Milisegundos restantes del enfriamiento (0 si no esta activo). */
    @Synchronized
    fun remainingMs(): Long = (cooldownUntilMs - now()).coerceAtLeast(0L)

    /** Tipo de error que origino el enfriamiento vigente, o null si no esta activo. */
    @Synchronized
    fun activeReason(): VoiceErrorType? = if (now() < cooldownUntilMs) lastReason else null

    /**
     * Registra un 429 de Gemini. Solo activa enfriamiento si Google indico cuanto
     * esperar ([retryAfterMs], de `Retry-After` o `retryDelay`); el valor se acota a
     * un maximo de seguridad. Si Google no lo indico, **no se bloquea Gemini**: la
     * siguiente frase volvera a intentarlo (solo se uso el respaldo en la frase del
     * 429).
     */
    @Synchronized
    fun registerRateLimit(errorType: VoiceErrorType, retryAfterMs: Long? = null) {
        val requested = retryAfterMs ?: return
        if (requested <= 0L) return
        cooldownUntilMs = now() + requested.coerceAtMost(maxCooldownMs)
        lastReason = errorType
    }

    /** Una reproduccion exitosa de Gemini limpia el enfriamiento. */
    @Synchronized
    fun registerSuccess() = reset()

    /** Limpieza manual (p. ej. boton "Reintentar Gemini TTS" en Configurar). */
    @Synchronized
    fun clear() = reset()

    private fun reset() {
        cooldownUntilMs = 0L
        lastReason = null
    }

    companion object {
        /** Tope de seguridad por si Google enviara un retryDelay anomalo y enorme. */
        const val MAX_COOLDOWN_MS = 5 * 60_000L

        /** Instancia compartida en todo el proceso (el limite es por API key). */
        val shared = GeminiRateLimitGate()
    }
}
