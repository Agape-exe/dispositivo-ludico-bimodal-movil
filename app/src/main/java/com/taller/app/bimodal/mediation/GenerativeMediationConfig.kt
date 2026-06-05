package com.taller.app.bimodal.mediation

import com.taller.app.BuildConfig

/**
 * Configuracion de la capa opcional de mediacion ludica generativa.
 *
 * La app debe funcionar sin credenciales: si la mediacion esta desactivada o le
 * faltan credenciales, el flujo usa el banco local de frases. Las credenciales se
 * leen de una fuente local no versionada (local.properties o variable de entorno)
 * a traves de [BuildConfig], igual que el proveedor de voz neural. Nunca se
 * hardcodean ni se suben al repositorio.
 *
 * @param enabled si la mediacion generativa esta activada.
 * @param apiKey clave del servicio generativo (vacia si no se configuro).
 * @param endpoint URL del servicio generativo (vacia si no se configuro).
 * @param model identificador opcional del modelo a usar.
 * @param requestTimeoutMs tope de espera de la llamada generativa, en milisegundos.
 */
data class GenerativeMediationConfig(
    val enabled: Boolean,
    val apiKey: String,
    val endpoint: String,
    val model: String,
    val requestTimeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    /** Hay credenciales suficientes para intentar una llamada real. */
    val hasCredentials: Boolean get() = apiKey.isNotBlank() && endpoint.isNotBlank()

    /** La mediacion generativa puede intentarse (activada y con credenciales). */
    val isOperational: Boolean get() = enabled && hasCredentials

    companion object {
        /** Tope de espera por defecto: corto para no demorar la voz del juguete. */
        const val DEFAULT_TIMEOUT_MS = 2_500L

        fun fromBuild(enabledOverride: Boolean? = null): GenerativeMediationConfig =
            GenerativeMediationConfig(
                enabled = enabledOverride ?: BuildConfig.GENERATIVE_MEDIATION_ENABLED,
                apiKey = BuildConfig.GENERATIVE_MEDIATION_API_KEY,
                endpoint = BuildConfig.GENERATIVE_MEDIATION_ENDPOINT,
                model = BuildConfig.GENERATIVE_MEDIATION_MODEL
            )
    }
}
