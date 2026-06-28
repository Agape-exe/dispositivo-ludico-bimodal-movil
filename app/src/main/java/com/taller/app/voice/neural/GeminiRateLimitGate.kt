package com.taller.app.voice.neural

import com.taller.app.voice.VoiceErrorType

/**
 * Compuerta de enfriamiento (cooldown) para Gemini TTS ante respuestas HTTP 429
 * (limite de cuota o rate limit del plan).
 *
 * Cuando Gemini responde 429, no tiene sentido seguir golpeando el endpoint en cada
 * frase: cada intento gasta una llamada y vuelve a fallar, agregando latencia y
 * presionando aun mas el limite. Esta compuerta marca un periodo de enfriamiento
 * durante el cual el proveedor de Gemini omite la llamada de red y deja que la cadena
 * de respaldo use OpenAI directamente. Al vencer el enfriamiento, Gemini se vuelve a
 * intentar con normalidad; nunca se desactiva de forma permanente.
 *
 * Es independiente de Android y usa un reloj inyectable para poder probarse de forma
 * aislada. El proveedor real comparte una unica instancia ([shared]) porque el limite
 * de cuota aplica a la API key de todo el proceso, no a una pantalla concreta.
 *
 * No guarda audio, texto hablado ni datos sensibles: solo marcas de tiempo y el tipo
 * de error tecnico del limite.
 */
class GeminiRateLimitGate(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val defaultCooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val maxCooldownMs: Long = MAX_COOLDOWN_MS
) {
    @Volatile
    private var cooldownUntilMs: Long = 0L

    @Volatile
    private var lastReason: VoiceErrorType? = null

    @Volatile
    private var consecutiveHits: Int = 0

    /** Indica si Gemini esta en enfriamiento y debe omitirse la llamada de red. */
    @Synchronized
    fun isInCooldown(): Boolean = now() < cooldownUntilMs

    /** Milisegundos restantes del enfriamiento (0 si no esta activo). */
    @Synchronized
    fun remainingMs(): Long = (cooldownUntilMs - now()).coerceAtLeast(0L)

    /** Tipo de error que origino el enfriamiento vigente, o null si no esta activo. */
    @Synchronized
    fun activeReason(): VoiceErrorType? = if (now() < cooldownUntilMs) lastReason else null

    /**
     * Registra un 429 de Gemini y activa (o extiende) el enfriamiento.
     *
     * Si el servidor indico un tiempo de espera ([retryAfterMs]) se respeta, acotado
     * entre el minimo por defecto y el maximo. Si no lo indico, el enfriamiento crece
     * con cada 429 consecutivo (backoff lineal) hasta el maximo, para no insistir
     * sobre un limite que sigue activo.
     */
    @Synchronized
    fun registerRateLimit(errorType: VoiceErrorType, retryAfterMs: Long? = null) {
        consecutiveHits += 1
        val base = retryAfterMs ?: (defaultCooldownMs * consecutiveHits)
        val cooldown = base.coerceIn(defaultCooldownMs, maxCooldownMs)
        cooldownUntilMs = now() + cooldown
        lastReason = errorType
    }

    /** Una reproduccion exitosa de Gemini limpia el enfriamiento. */
    @Synchronized
    fun registerSuccess() = reset()

    /** Limpieza manual (p. ej. boton "Reintentar Gemini TTS" en Configurar). */
    @Synchronized
    fun clear() = reset()

    private fun reset() {
        consecutiveHits = 0
        cooldownUntilMs = 0L
        lastReason = null
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 60_000L
        const val MAX_COOLDOWN_MS = 5 * 60_000L

        /** Instancia compartida en todo el proceso (la cuota es por API key). */
        val shared = GeminiRateLimitGate()
    }
}
