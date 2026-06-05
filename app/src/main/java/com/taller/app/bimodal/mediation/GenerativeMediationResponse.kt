package com.taller.app.bimodal.mediation

/**
 * Origen efectivo de la frase de mediacion que termino usandose.
 *
 * Permite mostrar en la interfaz tecnica que capa atendio la mediacion sin exponer
 * datos del nino.
 */
enum class MediationSource {
    /** La frase fue generada por la capa generativa y paso la validacion de seguridad. */
    GENERATIVE,

    /**
     * Se intento la mediacion generativa pero no fue posible usarla (sin
     * credenciales en tiempo de ejecucion, fallo de conexion, timeout o frase
     * invalida): se uso el banco local de frases como respaldo.
     */
    FALLBACK,

    /**
     * La mediacion generativa esta desactivada o no configurada: se uso el banco
     * local de frases directamente, sin intentar la nube.
     */
    LOCAL
}

/**
 * Frase de mediacion ya resuelta y lista para reproducirse: el texto seguro, su
 * origen, la latencia del intento generativo y, si aplica, el motivo por el que se
 * recurrio al banco local.
 *
 * El texto siempre es seguro de reproducir: si provino de la nube ya paso por el
 * validador de seguridad; si provino del banco local pertenece a un conjunto de
 * frases pre-aprobadas.
 *
 * @param type tipo de mediacion producida.
 * @param text frase a reproducir.
 * @param source capa que atendio finalmente la mediacion.
 * @param latencyMs latencia del intento generativo en milisegundos (0 cuando no se
 *        intento la nube por estar desactivada).
 * @param fallbackReason motivo legible del respaldo al banco local, si lo hubo.
 */
data class GenerativeMediationResponse(
    val type: GenerativeMediationType,
    val text: String,
    val source: MediationSource,
    val latencyMs: Long = 0L,
    val fallbackReason: String? = null
)
