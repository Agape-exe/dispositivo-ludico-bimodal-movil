package com.taller.app.bimodal.mediation

import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackGenerator
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType

/**
 * Respaldo de mediacion basado en el banco local de frases pre-aprobadas
 * ([GeneralTeacherFeedbackGenerator]). Produce siempre una frase segura, sin red
 * ni IA, por lo que es el respaldo definitivo cuando la mediacion generativa no
 * esta disponible o su salida no pasa la validacion.
 *
 * Reutiliza una instancia del generador para conservar su seleccion anti-repeticion
 * por categoria.
 *
 * @param generator generador local de frases tipo profesor.
 */
class LocalMediationFallbackProvider(
    private val generator: GeneralTeacherFeedbackGenerator
) {

    /**
     * Devuelve una frase local segura para la mediacion solicitada.
     *
     * Para una introduccion usa la categoria [GeneralTeacherFeedbackType.QUESTION_INTRO];
     * para una retroalimentacion usa la categoria ya decidida por el flujo
     * ([GenerativeMediationRequest.feedbackCategory]).
     */
    fun mediate(request: GenerativeMediationRequest): String {
        val type = when (request.type) {
            GenerativeMediationType.QUESTION_INTRODUCTION ->
                GeneralTeacherFeedbackType.QUESTION_INTRO
            GenerativeMediationType.CONTEXTUAL_FEEDBACK ->
                requireNotNull(request.feedbackCategory) {
                    "CONTEXTUAL_FEEDBACK requiere una feedbackCategory"
                }
        }
        return generator.message(type).text
    }
}
