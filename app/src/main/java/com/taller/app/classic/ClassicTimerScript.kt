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
        return intro(
            title = activity.title,
            topic = activity.topic,
            generatedIntroText = activity.generatedIntroText
        )
    }

    fun intro(
        title: String,
        topic: String,
        generatedIntroText: String?
    ): String {
        val generated = generatedIntroText
            ?.takeIf { isNeutralLine(it) && mentionsTopicOrTitle(it, title, topic) }
        return generated ?: buildString {
            append("¡Hola! Soy Seven. Hoy vamos a explorar sobre ")
            append((topic.ifBlank { title }).trim())
            append(". Te haré unas preguntitas y puedes responder con calma.")
        }
    }

    fun closing(activity: LearningActivity): String {
        return closing(activity.generatedClosingText)
    }

    fun closing(generatedClosingText: String?): String {
        val generated = generatedClosingText?.takeIf { isNeutralLine(it) }
        return generated ?: "Terminamos por ahora. ¡Hasta la próxima aventura!"
    }

    fun questionText(question: LearningQuestion): String {
        return questionText(
            rawQuestionText = question.questionText,
            childFriendlyQuestionText = question.childFriendlyQuestionText,
            expectedAnswer = question.expectedAnswer,
            keywords = question.keywords
        )
    }

    fun questionText(
        rawQuestionText: String,
        childFriendlyQuestionText: String?,
        expectedAnswer: String,
        keywords: List<String>
    ): String {
        val friendly = childFriendlyQuestionText
            ?.takeIf { isSafeQuestion(it, expectedAnswer, keywords) }
        return friendly ?: rawQuestionText
    }

    fun isNeutralLine(text: String): Boolean {
        val normalized = normalize(text)
        return text.trim().length <= MAX_NEUTRAL_LINE_CHARS &&
            bannedTimerTerms.none { normalized.contains(normalize(it)) }
    }

    fun isSafeQuestion(text: String, question: LearningQuestion): Boolean {
        return isSafeQuestion(text, question.expectedAnswer, question.keywords)
    }

    fun isSafeQuestion(text: String, expectedAnswer: String, keywords: List<String>): Boolean {
        if (!isNeutralLine(text)) return false
        val normalized = normalize(text)
        val answerOptions = expectedAnswer
            .split(Regex("(?i)[,;/\\n]| y | o | u | e "))
            .map { normalize(it) }
            .filter { it.length > 2 }
        val keywordOptions = keywords
            .map { normalize(it) }
            .filter { it.length > 2 }
        return (answerOptions + keywordOptions).none { option ->
            " $normalized ".contains(" $option ")
        }
    }

    private fun mentionsTopicOrTitle(text: String, activity: LearningActivity): Boolean =
        mentionsTopicOrTitle(text, activity.title, activity.topic)

    private fun mentionsTopicOrTitle(text: String, title: String, topic: String): Boolean {
        val normalized = normalize(text)
        val normalizedTopic = normalize(topic)
        val normalizedTitle = normalize(title)
        return normalizedTopic.isBlank() || normalized.contains(normalizedTopic) || normalized.contains(normalizedTitle)
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
