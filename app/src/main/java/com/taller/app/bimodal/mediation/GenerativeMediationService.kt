package com.taller.app.bimodal.mediation

/**
 * Punto de entrada de la mediacion ludica generativa para el flujo bimodal.
 *
 * Coordina las tres capas garantizando que siempre haya una frase segura que
 * reproducir:
 *
 * 1. Si la mediacion generativa esta activada y configurada, pide una frase al
 *    [GenerativeMediationProvider] (nube) y la pasa por el
 *    [GenerativeMediationSafetyValidator].
 * 2. Si la frase generada es valida, se usa ([MediationSource.GENERATIVE]).
 * 3. En cualquier otro caso (desactivada, sin credenciales, error del proveedor,
 *    timeout o frase invalida) se usa el banco local de frases
 *    ([LocalMediationFallbackProvider]) como respaldo ([MediationSource.FALLBACK]
 *    o [MediationSource.LOCAL]).
 *
 * Nunca lanza: cualquier fallo se resuelve con el respaldo local. No decide el
 * resultado de la respuesta del nino ni cambia la pregunta original.
 *
 * @param config configuracion vigente de la mediacion.
 * @param cloudProvider proveedor generativo (desacoplado).
 * @param localProvider respaldo local de frases pre-aprobadas.
 * @param validator validador de seguridad de contenido.
 */
class GenerativeMediationService(
    private val config: GenerativeMediationConfig,
    private val cloudProvider: GenerativeMediationProvider,
    private val localProvider: LocalMediationFallbackProvider,
    private val validator: GenerativeMediationSafetyValidator = GenerativeMediationSafetyValidator()
) {

    suspend fun mediate(request: GenerativeMediationRequest): GenerativeMediationResponse {
        // Mediacion desactivada o sin credenciales: banco local directo, sin nube.
        if (!config.isOperational) {
            return local(request, source = MediationSource.LOCAL, fallbackReason = null, latencyMs = 0L)
        }

        val result = runCatching { cloudProvider.generateMediation(request) }
            .getOrElse { GenerativeMediationResult.Unavailable("Excepcion del proveedor.") }

        return when (result) {
            is GenerativeMediationResult.Generated -> {
                when (val validation = validator.validate(result.text, request)) {
                    is MediationValidation.Valid ->
                        GenerativeMediationResponse(
                            type = request.type,
                            text = result.text,
                            source = MediationSource.GENERATIVE,
                            latencyMs = result.latencyMs,
                            fallbackReason = null
                        )
                    is MediationValidation.Rejected ->
                        local(
                            request,
                            source = MediationSource.FALLBACK,
                            fallbackReason = "Validacion: ${validation.reason}",
                            latencyMs = result.latencyMs
                        )
                }
            }
            is GenerativeMediationResult.Unavailable ->
                local(
                    request,
                    source = MediationSource.FALLBACK,
                    fallbackReason = result.reason,
                    latencyMs = 0L
                )
        }
    }

    private fun local(
        request: GenerativeMediationRequest,
        source: MediationSource,
        fallbackReason: String?,
        latencyMs: Long
    ): GenerativeMediationResponse = GenerativeMediationResponse(
        type = request.type,
        text = localProvider.mediate(request),
        source = source,
        latencyMs = latencyMs,
        fallbackReason = fallbackReason
    )
}
