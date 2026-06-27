package com.taller.app.recapture

import com.taller.app.voice.ToyVoiceTextValidator
import java.text.Normalizer

enum class RecapturePhraseKind {
    FIRST_RECAPTURE,
    SECOND_RECAPTURE,
    FINAL_RECAPTURE,
    POSITIVE_RETURN
}

class RecapturePhraseBank {
    private var lastPhrase: String? = null

    fun phraseFor(kind: RecapturePhraseKind): String {
        val candidates = phrases.getValue(kind)
            .filter { it != lastPhrase }
            .ifEmpty { phrases.getValue(kind) }
        val selected = candidates.firstOrNull(::isSafeRecapturePhrase) ?: SAFE_MINIMAL_PHRASE
        lastPhrase = selected
        return selected
    }

    fun firstForAttempt(attemptNumber: Int): String =
        phraseFor(
            when {
                attemptNumber <= 1 -> RecapturePhraseKind.FIRST_RECAPTURE
                attemptNumber == 2 -> RecapturePhraseKind.SECOND_RECAPTURE
                else -> RecapturePhraseKind.FINAL_RECAPTURE
            }
        )

    companion object {
        const val SAFE_MINIMAL_PHRASE = "¡Sigamos explorando juntos!"

        val prohibitedTerms = listOf(
            "cámara",
            "camara",
            "rostro",
            "cara",
            "te veo",
            "no te veo",
            "mirar",
            "mirando",
            "vigilancia",
            "detección",
            "deteccion",
            "micrófono",
            "microfono",
            "radar",
            "detecta",
            "encontrando"
        )

        private val phrases = mapOf(
            RecapturePhraseKind.FIRST_RECAPTURE to listOf(
                "¡Ey, explorador! La misión sigue esperando.",
                "¿Seguimos la aventura, compañero?",
                "Seven está listo cuando tú lo estés.",
                "¡Volvamos a la misión espacial!"
            ),
            RecapturePhraseKind.SECOND_RECAPTURE to listOf(
                "La aventura se está poniendo interesante. ¿Seguimos?",
                "¡Tu ayuda es importante para esta misión!",
                "Cuando estés listo, seguimos explorando.",
                "¡La misión necesita a su explorador!"
            ),
            RecapturePhraseKind.FINAL_RECAPTURE to listOf(
                "Está bien, explorador. Guardaré tu lugar para cuando vuelvas.",
                "La misión puede esperar. Aquí estaré cuando estés listo.",
                "Tomemos una pausa. La aventura sigue después."
            ),
            RecapturePhraseKind.POSITIVE_RETURN to listOf(
                "¡Qué bueno que volviste! Sigamos.",
                "¡Ahí estás, explorador! Continuemos.",
                "¡Perfecto! La misión continúa.",
                "¡Equipo completo otra vez!"
            )
        )

        fun allLocalPhrases(): List<String> = phrases.values.flatten() + SAFE_MINIMAL_PHRASE

        fun isSafeRecapturePhrase(text: String): Boolean {
            val normalized = normalizeForRecapture(text)
            val hasProhibitedTerm = prohibitedTerms.any { term ->
                normalized.contains(normalizeForRecapture(term))
            }
            return !hasProhibitedTerm && ToyVoiceTextValidator.validate(text).isValid
        }

        private fun normalizeForRecapture(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{Mn}+"), "")
        }
    }
}
