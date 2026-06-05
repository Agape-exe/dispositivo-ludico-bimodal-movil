package com.taller.app.bimodal.mediation

/**
 * Tipos de mediacion ludica generativa que puede producir la capa opcional de IA.
 *
 * La mediacion solo genera frases breves a partir de informacion ya controlada por
 * el sistema; nunca decide el resultado de la respuesta del nino ni cambia la
 * intencion de la pregunta. La evaluacion sigue dependiendo exclusivamente del
 * evaluador semantico local.
 */
enum class GenerativeMediationType {
    /**
     * Mini introduccion (1 o 2 frases) que precede a la pregunta para captar la
     * atencion del nino. No reemplaza ni reformula la pregunta original: el flujo
     * reproduce la pregunta original inmediatamente despues de la introduccion.
     */
    QUESTION_INTRODUCTION,

    /**
     * Frase posterior a la respuesta del nino, segun la categoria de
     * retroalimentacion ya decidida por el flujo (correcta, reintento, avance,
     * etc.). No revela la respuesta esperada cuando aun quedan intentos.
     */
    CONTEXTUAL_FEEDBACK
}
