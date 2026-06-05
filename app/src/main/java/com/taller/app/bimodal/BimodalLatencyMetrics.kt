package com.taller.app.bimodal

/**
 * Umbral tecnico de referencia del project charter: el tiempo de respuesta
 * promedio del sistema debe mantenerse por debajo de 1.5 segundos.
 */
const val LATENCY_TARGET_MS: Long = 1500L

/**
 * Medicion interna de latencia de un ciclo de respuesta del flujo bimodal.
 *
 * Registra instantes (epoch millis) de los hitos del procesamiento de una
 * respuesta. Las latencias derivadas se calculan solo cuando los hitos necesarios
 * estan disponibles y nunca son negativas (un hito posterior anterior al previo se
 * descarta). Es un valor inmutable y sin dependencias de Android, para poder
 * validarse con pruebas unitarias.
 *
 * No guarda transcripciones, audios ni datos del nino: solo marcas de tiempo.
 */
data class BimodalLatencySample(
    /** Inicio de la escucha (captura de audio). */
    val sttStartAtMs: Long? = null,
    /** Llegada de la transcripcion final o del fin definitivo de la captura. */
    val sttFinalAtMs: Long? = null,
    /** Inicio de la evaluacion semantica. */
    val semanticStartAtMs: Long? = null,
    /** Fin de la evaluacion semantica. */
    val semanticEndAtMs: Long? = null,
    /** Momento en que el orquestador decide el resultado/accion logica. */
    val logicalResponseAtMs: Long? = null,
    /** Inicio de la retroalimentacion visual/auditiva. */
    val feedbackStartAtMs: Long? = null
) {
    /**
     * Latencia principal del charter: desde que la transcripcion esta disponible
     * hasta la respuesta logica del sistema.
     */
    val totalResponseLatencyMs: Long?
        get() = nonNegativeDiff(sttFinalAtMs, logicalResponseAtMs)

    /** Latencia complementaria: desde la transcripcion hasta el inicio del feedback. */
    val responseToFeedbackLatencyMs: Long?
        get() = nonNegativeDiff(sttFinalAtMs, feedbackStartAtMs)

    /** Latencia de la evaluacion semantica. */
    val semanticLatencyMs: Long?
        get() = nonNegativeDiff(semanticStartAtMs, semanticEndAtMs)

    /** Latencia de la tuberia completa: desde el inicio de la escucha hasta el feedback. */
    val fullPipelineLatencyMs: Long?
        get() = nonNegativeDiff(sttStartAtMs, feedbackStartAtMs)

    /**
     * Una medicion es valida (computable) cuando tiene latencia de respuesta
     * logica; solo estas se promedian y cuentan para el objetivo tecnico.
     */
    val isValid: Boolean
        get() = totalResponseLatencyMs != null

    private companion object {
        fun nonNegativeDiff(start: Long?, end: Long?): Long? =
            if (start != null && end != null && end >= start) end - start else null
    }
}

/**
 * Resumen agregado de las latencias validas de una sesion.
 *
 * @property validSamples cantidad de mediciones validas consideradas.
 * @property lastResponseLatencyMs ultima latencia de respuesta logica.
 * @property lastFeedbackLatencyMs ultima latencia hasta el inicio del feedback.
 * @property lastPipelineLatencyMs ultima latencia total del pipeline (escucha->feedback).
 * @property averageResponseLatencyMs promedio de latencias de respuesta logica.
 * @property averageFeedbackLatencyMs promedio de latencias hasta el inicio del feedback.
 * @property targetMs umbral tecnico de referencia.
 */
data class BimodalLatencyStats(
    val validSamples: Int = 0,
    val lastResponseLatencyMs: Long? = null,
    val lastFeedbackLatencyMs: Long? = null,
    val lastPipelineLatencyMs: Long? = null,
    val averageResponseLatencyMs: Long? = null,
    val averageFeedbackLatencyMs: Long? = null,
    val targetMs: Long = LATENCY_TARGET_MS
) {
    /** Indica si el promedio de la sesion cumple el objetivo tecnico (< umbral). */
    val meetsTarget: Boolean
        get() = averageResponseLatencyMs != null && averageResponseLatencyMs < targetMs

    /** Hay al menos una medicion valida para evaluar el cumplimiento del objetivo. */
    val hasData: Boolean
        get() = validSamples > 0
}

/**
 * Calcula el resumen de latencias a partir de las mediciones acumuladas. Solo
 * promedia las mediciones validas (con latencia de respuesta logica) y reporta el
 * cumplimiento del umbral indicado. Es una funcion pura, facil de probar.
 */
fun computeLatencyStats(
    samples: List<BimodalLatencySample>,
    targetMs: Long = LATENCY_TARGET_MS
): BimodalLatencyStats {
    val valid = samples.filter { it.isValid }
    if (valid.isEmpty()) return BimodalLatencyStats(targetMs = targetMs)

    val responseLatencies = valid.mapNotNull { it.totalResponseLatencyMs }
    val average = responseLatencies.sum() / responseLatencies.size
    val feedbackLatencies = valid.mapNotNull { it.responseToFeedbackLatencyMs }
    val averageFeedback =
        if (feedbackLatencies.isEmpty()) null else feedbackLatencies.sum() / feedbackLatencies.size
    val last = valid.last()
    return BimodalLatencyStats(
        validSamples = valid.size,
        lastResponseLatencyMs = last.totalResponseLatencyMs,
        lastFeedbackLatencyMs = last.responseToFeedbackLatencyMs,
        lastPipelineLatencyMs = last.fullPipelineLatencyMs,
        averageResponseLatencyMs = average,
        averageFeedbackLatencyMs = averageFeedback,
        targetMs = targetMs
    )
}

/**
 * Formatea un valor de latencia (ms) para mostrarlo en pantalla o registrarlo.
 * Devuelve el numero exacto seguido de " ms", o un guion si no hay dato. Es una
 * funcion pura para poder validarla con pruebas unitarias.
 */
fun latencyMsLabel(valueMs: Long?): String = valueMs?.let { "$it ms" } ?: "—"

/**
 * Acumulador mutable de latencias para una sesion del flujo bimodal.
 *
 * Construye una medicion por ciclo de respuesta marcando sus hitos y la consolida
 * con [commit] (que la descarta si no es valida, evitando contar ciclos sin datos
 * utiles). Cada hito se marca una sola vez por ciclo, de modo que callbacks
 * duplicados (p. ej. transcripcion final y detencion) no alteran la medicion.
 *
 * @param targetMs umbral tecnico de referencia.
 * @param now proveedor de tiempo (epoch millis), inyectable para pruebas.
 */
class BimodalLatencyTracker(
    private val targetMs: Long = LATENCY_TARGET_MS,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val samples = mutableListOf<BimodalLatencySample>()
    private var current = BimodalLatencySample()

    /** Inicia un nuevo ciclo de medicion al comenzar la escucha. */
    fun beginCapture() {
        current = BimodalLatencySample(sttStartAtMs = now())
    }

    /** Marca la transcripcion final o el fin definitivo de la captura (una sola vez). */
    fun markSttFinal() {
        if (current.sttFinalAtMs == null) current = current.copy(sttFinalAtMs = now())
    }

    fun markSemanticStart() {
        if (current.semanticStartAtMs == null) current = current.copy(semanticStartAtMs = now())
    }

    fun markSemanticEnd() {
        current = current.copy(semanticEndAtMs = now())
    }

    /** Marca el instante de la respuesta logica del orquestador (una sola vez). */
    fun markLogicalResponse() {
        if (current.logicalResponseAtMs == null) current = current.copy(logicalResponseAtMs = now())
    }

    /** Marca el inicio de la retroalimentacion (una sola vez). */
    fun markFeedbackStart() {
        if (current.feedbackStartAtMs == null) current = current.copy(feedbackStartAtMs = now())
    }

    /** Medicion en curso (para inspeccion/diagnostico). */
    fun current(): BimodalLatencySample = current

    /**
     * Consolida la medicion en curso: la agrega solo si es valida, reinicia el
     * ciclo y devuelve el resumen actualizado. Llamar mas de una vez por ciclo no
     * duplica la medicion, porque el ciclo se reinicia tras consolidar.
     */
    fun commit(): BimodalLatencyStats {
        if (current.isValid) samples.add(current)
        current = BimodalLatencySample()
        return stats()
    }

    fun stats(): BimodalLatencyStats = computeLatencyStats(samples, targetMs)

    fun reset() {
        samples.clear()
        current = BimodalLatencySample()
    }
}
