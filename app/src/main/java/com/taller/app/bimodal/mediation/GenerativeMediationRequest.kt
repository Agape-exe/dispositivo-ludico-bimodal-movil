package com.taller.app.bimodal.mediation

import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType
import com.taller.app.semantic.SemanticResult

/**
 * Informacion controlada que la capa de mediacion generativa puede usar para
 * producir una frase breve. Es un valor inmutable e independiente de Android.
 *
 * Privacidad: este request NUNCA contiene la transcripcion del nino, su nombre,
 * imagenes, frames, audios crudos ni datos biometricos. Solo lleva datos de la
 * actividad (texto de la pregunta, respuesta esperada, palabras clave), la
 * categoria de retroalimentacion ya calculada, el resultado semantico ya decidido
 * (solo como categoria) y banderas del flujo. Es lo unico que un proveedor en la
 * nube llegaria a recibir.
 *
 * @param type tipo de mediacion solicitada (introduccion o retroalimentacion).
 * @param questionText texto exacto de la pregunta original. No debe modificarse.
 * @param expectedAnswer respuesta esperada; contexto interno. No debe revelarse
 *        en una introduccion ni mientras [hasRemainingAttempts] sea verdadero.
 * @param keywords palabras clave de la pregunta; contexto interno.
 * @param feedbackCategory categoria de retroalimentacion ya decidida por el flujo,
 *        obligatoria para [GenerativeMediationType.CONTEXTUAL_FEEDBACK] y nula para
 *        una introduccion.
 * @param semanticResult resultado semantico ya calculado por el evaluador local,
 *        solo como categoria. La IA jamas decide este valor.
 * @param hasRemainingAttempts si todavia quedan intentos en la pregunta actual.
 * @param isLastQuestion si es la ultima pregunta de la actividad; en ese caso no se
 *        permiten frases de continuidad antes del cierre.
 * @param childAgeRange rango de edad o nivel general, sin nombre ni dato personal.
 * @param tone tono deseado de la frase (ludico, amable, infantil, breve).
 * @param locale variante de idioma de la salida.
 * @param maxLength longitud maxima permitida de la frase generada, en caracteres.
 * @param forbiddenPhrases frases que la salida no debe contener (ademas de las
 *        prohibidas por defecto del validador).
 */
data class GenerativeMediationRequest(
    val type: GenerativeMediationType,
    val questionText: String,
    val expectedAnswer: String? = null,
    val keywords: List<String> = emptyList(),
    val feedbackCategory: GeneralTeacherFeedbackType? = null,
    val semanticResult: SemanticResult? = null,
    val hasRemainingAttempts: Boolean = false,
    val isLastQuestion: Boolean = false,
    val childAgeRange: String? = null,
    val tone: String = DEFAULT_TONE,
    val locale: String = DEFAULT_LOCALE,
    val maxLength: Int = DEFAULT_MAX_LENGTH,
    val forbiddenPhrases: List<String> = emptyList()
) {
    init {
        require(maxLength > 0) { "maxLength debe ser positivo" }
        if (type == GenerativeMediationType.CONTEXTUAL_FEEDBACK) {
            require(feedbackCategory != null) {
                "CONTEXTUAL_FEEDBACK requiere una feedbackCategory ya decidida por el flujo"
            }
        }
    }

    companion object {
        const val DEFAULT_TONE = "ludico, amable, infantil, breve"
        const val DEFAULT_LOCALE = "es-PE"

        /** Tope conservador de longitud para mantener frases cortas para ninos. */
        const val DEFAULT_MAX_LENGTH = 220
    }
}
