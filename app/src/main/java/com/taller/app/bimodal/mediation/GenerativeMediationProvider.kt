package com.taller.app.bimodal.mediation

/**
 * Resultado crudo de un proveedor de mediacion generativa, antes de validarse.
 */
sealed interface GenerativeMediationResult {
    /**
     * El proveedor produjo un texto candidato. Aun debe pasar por
     * [GenerativeMediationSafetyValidator] antes de reproducirse.
     *
     * @param text frase candidata sin validar.
     * @param latencyMs tiempo que tomo producirla, en milisegundos.
     */
    data class Generated(val text: String, val latencyMs: Long) : GenerativeMediationResult

    /**
     * El proveedor no pudo generar (sin credenciales, sin red, timeout o error).
     * El servicio debe recurrir al banco local de frases.
     *
     * @param reason motivo legible, sin datos sensibles, para diagnostico.
     */
    data class Unavailable(val reason: String) : GenerativeMediationResult
}

/**
 * Abstraccion de una fuente de mediacion ludica generativa.
 *
 * Una implementacion puede contactar un servicio en la nube o resolverse en local.
 * El contrato es desacoplado a proposito para poder conectar un proveedor real mas
 * adelante sin tocar el flujo: solo recibe informacion controlada de la actividad
 * (ver [GenerativeMediationRequest]) y nunca la transcripcion del nino.
 */
interface GenerativeMediationProvider {

    /** Indica si el proveedor tiene la configuracion minima para operar. */
    fun isConfigured(): Boolean

    /**
     * Genera una frase candidata para la mediacion solicitada. Nunca lanza: ante
     * cualquier problema devuelve [GenerativeMediationResult.Unavailable].
     */
    suspend fun generateMediation(request: GenerativeMediationRequest): GenerativeMediationResult
}
