package com.taller.app.voice.prep

import com.taller.app.voice.ToyVoiceTextValidator

/** Guion de una pregunta como fuente de lineas de voz (decoplado de Room). */
data class QuestionVoiceSource(
    val questionId: Long,
    val orderIndex: Int,
    val childFriendlyQuestionText: String?,
    val rawQuestionText: String,
    val hint1: String?,
    val hint2: String?,
    val hint3: String?,
    val positiveFeedbackText: String?,
    val supportiveFeedbackText: String?,
    val retryPromptText: String?
)

/** Guion completo de una sesion como fuente de lineas de voz (decoplado de Room). */
data class SessionVoiceSource(
    val introText: String?,
    val closingText: String?,
    val questions: List<QuestionVoiceSource>
)

/**
 * Construye la lista FINITA de lineas que Seven podria decir en una sesion, lista
 * para pre-generar y cachear.
 *
 * Reglas:
 * - Incluye el banco generico (una sola vez) mas el guion de la sesion: intro,
 *   cierre, pregunta amigable, pistas, feedbacks y reintento de cada pregunta.
 * - Para la pregunta usa el texto amigable del guion si existe; si no, cae al
 *   texto original como respaldo (la pregunta original nunca se pierde).
 * - Descarta textos vacios, marcadores o tecnicos (segun [ToyVoiceTextValidator]).
 * - Deduplica por texto normalizado igual que la clave de cache: dos lineas con el
 *   mismo texto se sintetizan una sola vez. Conserva el primer rol encontrado.
 *
 * Es codigo puro (sin Android) para poder probarse de forma aislada.
 */
object SessionVoiceLines {

    fun collect(
        source: SessionVoiceSource,
        includeGeneric: Boolean = true
    ): List<VoiceLine> {
        val raw = buildList {
            if (includeGeneric) addAll(SevenGenericVoiceBank.lines())
            addLine(VoiceLineRole.INTRO, source.introText)
            addLine(VoiceLineRole.CLOSING, source.closingText)
            source.questions.sortedBy { it.orderIndex }.forEach { q ->
                val questionText = q.childFriendlyQuestionText?.takeIf { it.isNotBlank() }
                    ?: q.rawQuestionText
                addLine(VoiceLineRole.QUESTION, questionText, q.questionId)
                addLine(VoiceLineRole.HINT_1, q.hint1, q.questionId)
                addLine(VoiceLineRole.HINT_2, q.hint2, q.questionId)
                addLine(VoiceLineRole.HINT_3, q.hint3, q.questionId)
                addLine(VoiceLineRole.POSITIVE_FEEDBACK, q.positiveFeedbackText, q.questionId)
                addLine(VoiceLineRole.SUPPORTIVE_FEEDBACK, q.supportiveFeedbackText, q.questionId)
                addLine(VoiceLineRole.RETRY_PROMPT, q.retryPromptText, q.questionId)
            }
        }
        return dedupe(raw)
    }

    /**
     * Texto normalizado usado para deduplicar. Coincide con la normalizacion de la
     * clave de cache ([com.taller.app.voice.neural.VoiceCacheKey]): valida, recorta
     * y colapsa espacios. Devuelve null si el texto no es valido para voz.
     */
    fun dedupeKey(text: String?): String? {
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) return null
        return validation.normalizedText.trim().replace(Regex("\\s+"), " ")
    }

    private fun MutableList<VoiceLine>.addLine(
        role: VoiceLineRole,
        text: String?,
        questionId: Long? = null
    ) {
        if (dedupeKey(text) == null) return
        add(VoiceLine(role, text!!.trim(), questionId))
    }

    private fun dedupe(lines: List<VoiceLine>): List<VoiceLine> {
        val seen = HashSet<String>()
        return lines.filter { line ->
            val key = dedupeKey(line.text) ?: return@filter false
            seen.add(key)
        }
    }
}
