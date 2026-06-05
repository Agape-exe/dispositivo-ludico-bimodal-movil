package com.taller.app.bimodal.mediation

import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType
import java.text.Normalizer

/** Motivo por el que el validador rechaza una frase generada. */
enum class MediationRejectionReason {
    /** Texto vacio o solo espacios. */
    EMPTY,

    /** Supera la longitud maxima permitida por la solicitud. */
    TOO_LONG,

    /** Contiene una frase prohibida (lista por defecto o de la solicitud). */
    FORBIDDEN_PHRASE,

    /** Se refiere a si misma como IA, modelo, sistema o a la evaluacion semantica. */
    MENTIONS_AI_OR_SYSTEM,

    /** Afirma acierto ("correcto"/"exacto") en una categoria que no es CORRECT. */
    UNEXPECTED_CORRECTNESS_CLAIM,

    /** Incluye una invitacion a continuar en la ultima pregunta. */
    CONTINUATION_ON_LAST_QUESTION,

    /** Introduce una pregunta distinta de la original. */
    CHANGES_ORIGINAL_QUESTION,

    /** Parece incluir datos personales (p. ej. un correo electronico). */
    PERSONAL_DATA,

    /** Contiene lenguaje no adecuado para ninos. */
    INAPPROPRIATE_LANGUAGE,

    /** No parece estar en espanol. */
    NOT_SPANISH
}

/** Resultado de validar una frase de mediacion. */
sealed interface MediationValidation {
    /** La frase es segura para reproducirse. */
    data object Valid : MediationValidation

    /** La frase debe descartarse; el flujo debe recurrir al banco local. */
    data class Rejected(val reason: MediationRejectionReason, val detail: String) : MediationValidation

    val isValid: Boolean get() = this is Valid
}

/**
 * Validador local que revisa cualquier frase generada por la capa de IA antes de
 * reproducirla. Es independiente de Android y deterministico, por lo que se prueba
 * de forma aislada.
 *
 * Ninguna frase llega a la voz del juguete sin pasar por aqui. Si una frase se
 * rechaza, el flujo usa el banco local de frases pre-aprobadas como respaldo.
 *
 * El validador no contacta servicios externos ni decide el resultado de la
 * respuesta del nino: solo aplica reglas de seguridad de contenido.
 */
class GenerativeMediationSafetyValidator {

    fun validate(text: String, request: GenerativeMediationRequest): MediationValidation {
        if (text.isBlank()) {
            return reject(MediationRejectionReason.EMPTY, "La frase generada esta vacia.")
        }
        if (text.trim().length > request.maxLength) {
            return reject(
                MediationRejectionReason.TOO_LONG,
                "La frase supera el maximo de ${request.maxLength} caracteres."
            )
        }

        val normalized = normalize(text)

        if (!looksSpanish(text, normalized)) {
            return reject(MediationRejectionReason.NOT_SPANISH, "La frase no parece estar en espanol.")
        }

        if (containsPersonalData(text)) {
            return reject(MediationRejectionReason.PERSONAL_DATA, "La frase parece incluir datos personales.")
        }

        if (containsAny(normalized, INAPPROPRIATE_WORDS, wholeWord = true)) {
            return reject(
                MediationRejectionReason.INAPPROPRIATE_LANGUAGE,
                "La frase contiene lenguaje no adecuado para ninos."
            )
        }

        // Frases prohibidas: lista por defecto + las que aporte la solicitud.
        val forbidden = DEFAULT_FORBIDDEN_PHRASES + request.forbiddenPhrases.map { normalize(it) }
        forbidden.firstOrNull { it.isNotBlank() && normalized.contains(it) }?.let {
            return reject(MediationRejectionReason.FORBIDDEN_PHRASE, "Contiene una frase prohibida.")
        }

        // Auto-referencias a IA, modelo, sistema o a la evaluacion.
        if (containsAny(normalized, AI_OR_SYSTEM_PHRASES, wholeWord = false) ||
            containsAny(normalized, AI_OR_SYSTEM_TOKENS, wholeWord = true)
        ) {
            return reject(
                MediationRejectionReason.MENTIONS_AI_OR_SYSTEM,
                "La frase se refiere a la IA, a un modelo o al sistema."
            )
        }

        // "correcto"/"exacto" solo se permiten en la categoria CORRECT.
        val isCorrectCategory = request.feedbackCategory == GeneralTeacherFeedbackType.CORRECT
        if (!isCorrectCategory && containsAny(normalized, CORRECTNESS_TOKENS, wholeWord = true)) {
            return reject(
                MediationRejectionReason.UNEXPECTED_CORRECTNESS_CLAIM,
                "Afirma acierto en una categoria que no es CORRECT."
            )
        }

        // En la ultima pregunta no se permiten invitaciones a continuar.
        if (request.isLastQuestion && containsAny(normalized, CONTINUATION_PHRASES, wholeWord = false)) {
            return reject(
                MediationRejectionReason.CONTINUATION_ON_LAST_QUESTION,
                "Incluye una invitacion a continuar en la ultima pregunta."
            )
        }

        // No se permiten preguntas nuevas: si la frase contiene una pregunta, la
        // unica admitida es la original (mismo texto). Esto evita que la IA cambie
        // la pregunta o abra conversacion libre.
        if (hasQuestionMark(text)) {
            val normalizedOriginal = normalize(request.questionText)
            if (normalizedOriginal.isBlank() || !normalized.contains(normalizedOriginal)) {
                return reject(
                    MediationRejectionReason.CHANGES_ORIGINAL_QUESTION,
                    "Introduce una pregunta distinta de la original."
                )
            }
        }

        return MediationValidation.Valid
    }

    private fun reject(reason: MediationRejectionReason, detail: String) =
        MediationValidation.Rejected(reason, detail)

    /** Indica si el texto crudo contiene algun signo de interrogacion. */
    private fun hasQuestionMark(text: String): Boolean = text.contains('?') || text.contains('¿')

    /**
     * Heuristica conservadora de idioma: rechaza solo cuando hay senales claras de
     * que la frase no esta en espanol (palabras funcionales inglesas como palabra
     * completa). No exige caracteres especiales del espanol para no rechazar frases
     * validas simples.
     */
    private fun looksSpanish(rawText: String, normalized: String): Boolean {
        if (rawText.none { it.isLetter() }) return false
        return !containsAny(normalized, ENGLISH_MARKERS, wholeWord = true)
    }

    /** Detecta datos personales evidentes (correos electronicos). */
    private fun containsPersonalData(text: String): Boolean = text.contains('@')

    private fun containsAny(haystack: String, needles: Set<String>, wholeWord: Boolean): Boolean =
        needles.any { needle ->
            if (needle.isBlank()) false
            else if (wholeWord) " $haystack ".contains(" $needle ")
            else haystack.contains(needle)
        }

    /**
     * Normaliza igual que el evaluador semantico: minusculas, sin acentos, sin
     * puntuacion y con espacios simples. Asi las comparaciones son robustas frente a
     * tildes y signos.
     */
    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val lowercase = withoutAccents.lowercase()
        val onlyAlphanumericAndSpace = lowercase.replace(Regex("[^a-z0-9\\s]"), " ")
        return onlyAlphanumericAndSpace.trim().replace(Regex("\\s+"), " ")
    }

    companion object {
        /** Frases enganosas o tecnicas prohibidas (ya normalizadas). */
        val DEFAULT_FORBIDDEN_PHRASES: List<String> = listOf(
            "estas cerca",
            "casi lo tienes",
            "vas por buen camino",
            "vas bien",
            "mencionaste una idea importante",
            "tu respuesta es parcialmente correcta",
            "parcialmente correcta",
            "segun tu respuesta",
            "como inteligencia artificial",
            "soy una ia",
            "soy una inteligencia artificial",
            "inteligencia artificial",
            "evaluacion semantica",
            "el sistema detecto"
        )

        /** Subcadenas que delatan auto-referencia a IA/modelo/sistema. */
        private val AI_OR_SYSTEM_PHRASES: Set<String> = setOf(
            "evaluacion semantica",
            "inteligencia artificial"
        )

        /** Palabras completas que delatan auto-referencia tecnica. */
        private val AI_OR_SYSTEM_TOKENS: Set<String> = setOf(
            "ia", "modelo", "sistema"
        )

        /** Afirmaciones de acierto, solo permitidas en CORRECT (palabra completa). */
        private val CORRECTNESS_TOKENS: Set<String> = setOf(
            "correcto", "correcta", "exacto", "exacta"
        )

        /** Invitaciones a continuar, prohibidas en la ultima pregunta. */
        private val CONTINUATION_PHRASES: Set<String> = setOf(
            "continuemos",
            "continuar",
            "siguiente pregunta",
            "otra pregunta",
            "vamos con otra",
            "pasemos a la siguiente",
            "pasemos a otra",
            "sigamos con",
            "seguimos con",
            "vamos a seguir",
            "avancemos"
        )

        /** Lenguaje no adecuado para ninos (palabra completa). */
        private val INAPPROPRIATE_WORDS: Set<String> = setOf(
            "tonto", "estupido", "idiota", "maldito", "imbecil", "feo", "burro"
        )

        /** Marcadores inequivocos de ingles (palabra completa). */
        private val ENGLISH_MARKERS: Set<String> = setOf(
            "the", "you", "your", "what", "how", "hello", "please", "sorry", "dog", "cat", "with", "and"
        )
    }
}
