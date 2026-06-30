package com.taller.app.classic

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import java.text.Normalizer

private const val MAX_NEUTRAL_LINE_CHARS = 220

private val bannedTimerTerms = listOf(
    "correcto",
    "incorrecto",
    "muy bien",
    "acertaste",
    "eso esta mal",
    "eso está mal",
    "fallaste",
    "te equivocaste",
    "la respuesta era",
    "intenta corregir",
    "casi",
    "pista"
)

object ClassicTimerScript {

    fun intro(activity: LearningActivity): String {
        val generated = activity.generatedIntroText
            ?.takeIf { isNeutralLine(it) && mentionsTopicOrTitle(it, activity) }
        return generated ?: buildString {
            append("¡Hola! Soy Seven. Hoy vamos a explorar sobre ")
            append((activity.topic.ifBlank { activity.title }).trim())
            append(". Te haré unas preguntitas y puedes responder con calma.")
        }
    }

    fun closing(activity: LearningActivity): String {
        val generated = activity.generatedClosingText?.takeIf { isNeutralLine(it) }
        return generated ?: "Terminamos por ahora. ¡Hasta la próxima aventura!"
    }

    fun questionText(question: LearningQuestion): String {
        val friendly = question.childFriendlyQuestionText
            ?.takeIf { isSafeQuestion(it, question) }
        return friendly ?: question.questionText
    }

    fun isNeutralLine(text: String): Boolean {
        val normalized = normalize(text)
        return text.trim().length <= MAX_NEUTRAL_LINE_CHARS &&
            bannedTimerTerms.none { normalized.contains(normalize(it)) }
    }

    fun isSafeQuestion(text: String, question: LearningQuestion): Boolean {
        if (!isNeutralLine(text)) return false
        val normalized = normalize(text)
        val answerOptions = question.expectedAnswer
            .split(Regex("(?i)[,;/\\n]| y | o | u | e "))
            .map { normalize(it) }
            .filter { it.length > 2 }
        val keywordOptions = question.keywords
            .map { normalize(it) }
            .filter { it.length > 2 }
        return (answerOptions + keywordOptions).none { option ->
            " $normalized ".contains(" $option ")
        }
    }

    private fun mentionsTopicOrTitle(text: String, activity: LearningActivity): Boolean {
        val normalized = normalize(text)
        val topic = normalize(activity.topic)
        val title = normalize(activity.title)
        return topic.isBlank() || normalized.contains(topic) || normalized.contains(title)
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9ñ\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
