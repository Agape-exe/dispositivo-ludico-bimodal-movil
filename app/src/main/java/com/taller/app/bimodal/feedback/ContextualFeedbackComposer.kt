package com.taller.app.bimodal.feedback

import kotlin.random.Random

/** Tipo del feedback contextual producido por [ContextualFeedbackComposer]. */
enum class ContextualFeedbackType {
    CORRECT_CONTEXTUAL,
    RETRY_CONTEXTUAL,
    FINAL_NEUTRAL
}

/** Frase breve lista para que Seven la diga, con su tipo. */
data class ContextualFeedback(
    val text: String,
    val type: ContextualFeedbackType
)

/**
 * FINAL-CORE02: generador local de retroalimentacion contextual del modo
 * inteligente. Produce frases de 1-2 oraciones que USAN la respuesta del nino
 * cuando es seguro incluirla, para que el refuerzo o la orientacion se sientan
 * relacionados con lo que dijo (p. ej. "¡Muy bien! ¡Pavo! Ese amiguito vive en
 * la granja." o "Mmm, el rinoceronte no vive en la granja. Pensemos en un
 * amiguito de la granja.").
 *
 * Reglas:
 *  - lenguaje calido para ninos de 3 a 5 anos, sin tono de profesor;
 *  - nunca dice "incorrecto", "mal" ni "fallaste";
 *  - nunca revela la respuesta esperada si quedan intentos;
 *  - si la transcripcion no es segura de repetir (vacia, muy larga, con
 *    caracteres extranos), devuelve null y el llamador usa el banco general.
 *
 * Es logica pura sin Android ni red: no envia la transcripcion a ningun
 * servicio y no persiste nada.
 */
class ContextualFeedbackComposer(private val random: Random = Random.Default) {

    /** Refuerzo contextual para una respuesta correcta. */
    fun composeCorrect(
        questionText: String?,
        childTranscript: String?
    ): ContextualFeedback? {
        val answer = safeAnswer(childTranscript) ?: return null
        val place = extractPlace(questionText)
        val text = if (place != null) {
            pick(
                "¡Muy bien! ¡$answer! Ese amiguito vive en $place.",
                "¡Genial! $answer vive en $place. ¡Bien pensado!",
                "¡Eso es! $answer si vive en $place."
            )
        } else {
            pick(
                "¡Muy bien! ¡$answer es una gran respuesta!",
                "¡Genial! Dijiste $answer y me encanto.",
                "¡Eso es! $answer. ¡Bien pensado!"
            )
        }
        return ContextualFeedback(text, ContextualFeedbackType.CORRECT_CONTEXTUAL)
    }

    /**
     * Orientacion suave para una respuesta que no corresponde, cuando queda
     * reintento. Explica brevemente por que sin revelar la respuesta esperada.
     */
    fun composeIncorrectRetry(
        questionText: String?,
        childTranscript: String?
    ): ContextualFeedback? {
        val answer = safeAnswer(childTranscript) ?: return null
        val place = extractPlace(questionText)
        val text = if (place != null) {
            pick(
                "Mmm, $answer no vive en $place. Pensemos en un amiguito de $place.",
                "Casi. $answer vive en otro lugar. Busquemos uno que viva en $place.",
                "$answer es lindo, pero no vive en $place. Pensemos juntos otra vez."
            )
        } else {
            pick(
                "Mmm, $answer no es lo que busco esta vez. Pensemos juntos otra vez.",
                "Casi. $answer no era, pero seguro se te ocurre otra idea.",
                "Gracias por decirme $answer. Probemos con otra idea."
            )
        }
        return ContextualFeedback(text, ContextualFeedbackType.RETRY_CONTEXTUAL)
    }

    /**
     * Cierre suave cuando ya no quedan intentos: agradece sin regano y sin
     * anunciar una pregunta que quiza no exista (eso lo decide el flujo).
     */
    fun composeIncorrectFinal(
        childTranscript: String?
    ): ContextualFeedback? {
        val answer = safeAnswer(childTranscript) ?: return null
        val text = pick(
            "Gracias por decirme $answer. Lo pensamos juntos despues.",
            "$answer fue una buena idea. Gracias por intentarlo conmigo.",
            "Me gusto escuchar $answer. Sigamos explorando juntos."
        )
        return ContextualFeedback(text, ContextualFeedbackType.FINAL_NEUTRAL)
    }

    private fun pick(vararg options: String): String =
        options[random.nextInt(options.size)]

    companion object {

        private val LEADING_FILLERS = listOf(
            "es ", "un ", "una ", "unos ", "unas ", "el ", "la ", "los ", "las ", "mi "
        )

        private val SAFE_ANSWER_REGEX = Regex("^[a-záéíóúüñ]+( [a-záéíóúüñ]+){0,2}$")

        /**
         * Normaliza la transcripcion del nino a una forma corta y segura de
         * repetir en voz alta: minusculas, sin articulos iniciales, maximo tres
         * palabras y solo letras. Devuelve null si no es seguro usarla.
         */
        fun safeAnswer(transcript: String?): String? {
            var value = transcript?.trim()?.lowercase() ?: return null
            if (value.isEmpty()) return null
            var changed = true
            while (changed) {
                changed = false
                for (filler in LEADING_FILLERS) {
                    if (value.startsWith(filler) && value.length > filler.length) {
                        value = value.removePrefix(filler).trim()
                        changed = true
                    }
                }
            }
            value = value.replace(Regex("\\s+"), " ").trim()
            if (value.length !in 2..24) return null
            if (!SAFE_ANSWER_REGEX.matches(value)) return null
            return value
        }

        private val LIVES_IN_REGEX =
            Regex("viv[a-z]*\\s+en\\s+(la|el|los|las)\\s+([a-záéíóúüñ]+)", RegexOption.IGNORE_CASE)
        private val IN_PLACE_REGEX =
            Regex("\\ben\\s+(la|el)\\s+([a-záéíóúüñ]+)", RegexOption.IGNORE_CASE)
        private val OF_PLACE_REGEX =
            Regex("\\bde\\s+(la|el)\\s+([a-záéíóúüñ]+)", RegexOption.IGNORE_CASE)

        /**
         * Extrae el lugar mencionado por la pregunta ("que viva en la granja" →
         * "la granja") para dar contexto al feedback. Devuelve null si la
         * pregunta no menciona un lugar reconocible.
         */
        fun extractPlace(questionText: String?): String? {
            val question = questionText?.trim().orEmpty()
            if (question.isEmpty()) return null
            val match = LIVES_IN_REGEX.find(question)
                ?: IN_PLACE_REGEX.find(question)
                ?: OF_PLACE_REGEX.find(question)
                ?: return null
            val article = match.groupValues[1].lowercase()
            val noun = match.groupValues[2].lowercase()
            return "$article $noun"
        }
    }
}
