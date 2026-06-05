package com.taller.app.bimodal.mediation

import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType
import com.taller.app.semantic.SemanticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeMediationSafetyValidatorTest {

    private val validator = GenerativeMediationSafetyValidator()

    private fun introRequest(
        questionText: String = "¿Cómo hace el perro?",
        isLastQuestion: Boolean = false,
        maxLength: Int = GenerativeMediationRequest.DEFAULT_MAX_LENGTH,
        forbiddenPhrases: List<String> = emptyList()
    ) = GenerativeMediationRequest(
        type = GenerativeMediationType.QUESTION_INTRODUCTION,
        questionText = questionText,
        expectedAnswer = "guau",
        keywords = listOf("guau", "ladra"),
        isLastQuestion = isLastQuestion,
        maxLength = maxLength,
        forbiddenPhrases = forbiddenPhrases
    )

    private fun feedbackRequest(
        category: GeneralTeacherFeedbackType,
        questionText: String = "¿Cómo hace el perro?",
        isLastQuestion: Boolean = false,
        hasRemainingAttempts: Boolean = false
    ) = GenerativeMediationRequest(
        type = GenerativeMediationType.CONTEXTUAL_FEEDBACK,
        questionText = questionText,
        expectedAnswer = "guau",
        keywords = listOf("guau", "ladra"),
        feedbackCategory = category,
        semanticResult = SemanticResult.INCORRECT,
        hasRemainingAttempts = hasRemainingAttempts,
        isLastQuestion = isLastQuestion
    )

    private fun rejectionReason(validation: MediationValidation): MediationRejectionReason {
        assertTrue("Se esperaba un rechazo, pero fue: $validation", validation is MediationValidation.Rejected)
        return (validation as MediationValidation.Rejected).reason
    }

    // ----- Rechazos basicos -----------------------------------------------------

    @Test
    fun rejectsEmptyText() {
        assertEquals(
            MediationRejectionReason.EMPTY,
            rejectionReason(validator.validate("", introRequest()))
        )
        assertEquals(
            MediationRejectionReason.EMPTY,
            rejectionReason(validator.validate("    ", introRequest()))
        )
    }

    @Test
    fun rejectsTooLongText() {
        val longText = "Los perritos son muy juguetones y mueven la colita. ".repeat(20)
        assertEquals(
            MediationRejectionReason.TOO_LONG,
            rejectionReason(validator.validate(longText, introRequest(maxLength = 80)))
        )
    }

    @Test
    fun rejectsForbiddenPhrases() {
        assertEquals(
            MediationRejectionReason.FORBIDDEN_PHRASE,
            rejectionReason(
                validator.validate(
                    "Muy bien, vas por buen camino.",
                    feedbackRequest(GeneralTeacherFeedbackType.INCORRECT_RETRY)
                )
            )
        )
        assertEquals(
            MediationRejectionReason.FORBIDDEN_PHRASE,
            rejectionReason(
                validator.validate(
                    "Casi lo tienes, intenta otra vez.",
                    feedbackRequest(GeneralTeacherFeedbackType.INCORRECT_RETRY)
                )
            )
        )
    }

    @Test
    fun rejectsCallerProvidedForbiddenPhrase() {
        val validation = validator.validate(
            "Pensemos en un gatito callejero.",
            introRequest(forbiddenPhrases = listOf("gatito callejero"))
        )
        assertEquals(MediationRejectionReason.FORBIDDEN_PHRASE, rejectionReason(validation))
    }

    @Test
    fun rejectsAiOrSystemSelfReference() {
        assertEquals(
            MediationRejectionReason.MENTIONS_AI_OR_SYSTEM,
            rejectionReason(
                validator.validate(
                    "Soy un modelo que te acompana.",
                    feedbackRequest(GeneralTeacherFeedbackType.INCORRECT_RETRY)
                )
            )
        )
        // "familia" contiene la subcadena "ia" pero no debe activar el rechazo.
        assertTrue(
            validator.validate(
                "Pensemos juntos en familia.",
                introRequest()
            ).isValid
        )
    }

    // ----- Afirmaciones de acierto ----------------------------------------------

    @Test
    fun rejectsCorrectnessClaimWhenCategoryIsNotCorrect() {
        assertEquals(
            MediationRejectionReason.UNEXPECTED_CORRECTNESS_CLAIM,
            rejectionReason(
                validator.validate(
                    "Eso es correcto, sigamos.",
                    feedbackRequest(GeneralTeacherFeedbackType.INCORRECT_NEXT)
                )
            )
        )
        assertEquals(
            MediationRejectionReason.UNEXPECTED_CORRECTNESS_CLAIM,
            rejectionReason(
                validator.validate(
                    "¡Exacto!",
                    feedbackRequest(GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY)
                )
            )
        )
    }

    @Test
    fun allowsCorrectnessClaimInCorrectCategory() {
        val validation = validator.validate(
            "¡Muy bien! Esa respuesta es correcta, el perro hace guau.",
            feedbackRequest(GeneralTeacherFeedbackType.CORRECT)
        )
        assertTrue("Un feedback correcto valido fue rechazado: $validation", validation.isValid)
    }

    @Test
    fun doesNotConfuseIncorrectWithCorrect() {
        // "incorrecta" contiene "correcta" como subcadena; la comparacion por palabra
        // completa no debe activar el rechazo de afirmacion de acierto.
        val validation = validator.validate(
            "Esa respuesta no es la que buscamos, intentemos otra vez.",
            feedbackRequest(GeneralTeacherFeedbackType.INCORRECT_RETRY)
        )
        assertTrue("Frase valida marcada como afirmacion de acierto: $validation", validation.isValid)
    }

    // ----- Continuidad en la ultima pregunta ------------------------------------

    @Test
    fun rejectsContinuationOnLastQuestion() {
        assertEquals(
            MediationRejectionReason.CONTINUATION_ON_LAST_QUESTION,
            rejectionReason(
                validator.validate(
                    "Muy bien, continuemos con la siguiente pregunta.",
                    feedbackRequest(GeneralTeacherFeedbackType.CORRECT, isLastQuestion = true)
                )
            )
        )
    }

    @Test
    fun allowsNonContinuationOnLastQuestion() {
        val validation = validator.validate(
            "Muy bien, esa respuesta es correcta.",
            feedbackRequest(GeneralTeacherFeedbackType.CORRECT, isLastQuestion = true)
        )
        assertTrue("Un cierre sin continuidad fue rechazado: $validation", validation.isValid)
    }

    // ----- Cambio de la pregunta original ---------------------------------------

    @Test
    fun rejectsIntroductionThatChangesOriginalQuestion() {
        assertEquals(
            MediationRejectionReason.CHANGES_ORIGINAL_QUESTION,
            rejectionReason(
                validator.validate(
                    "¿Qué mascota tienes en casa?",
                    introRequest(questionText = "¿Cómo hace el perro?")
                )
            )
        )
    }

    @Test
    fun acceptsIntroductionThatKeepsOriginalQuestion() {
        val validation = validator.validate(
            "Te cuento algo: los perritos mueven la colita. Ahora dime, ¿cómo hace el perro?",
            introRequest(questionText = "¿Cómo hace el perro?")
        )
        assertTrue("Una introduccion que conserva la pregunta fue rechazada: $validation", validation.isValid)
    }

    // ----- Aceptaciones ---------------------------------------------------------

    @Test
    fun acceptsShortValidIntroduction() {
        val validation = validator.validate(
            "Te cuento algo: los perritos son muy juguetones y a veces ladran cuando están felices.",
            introRequest()
        )
        assertTrue("Una introduccion breve y valida fue rechazada: $validation", validation.isValid)
    }

    @Test
    fun acceptsValidCorrectFeedback() {
        val validation = validator.validate(
            "¡Exacto! El perro hace guau cuando quiere saludar.",
            feedbackRequest(GeneralTeacherFeedbackType.CORRECT)
        )
        assertTrue("Un feedback correcto valido fue rechazado: $validation", validation.isValid)
    }

    // ----- Idioma y datos personales --------------------------------------------

    @Test
    fun rejectsNonSpanishText() {
        assertEquals(
            MediationRejectionReason.NOT_SPANISH,
            rejectionReason(
                validator.validate(
                    "Hello, how are you my friend?",
                    introRequest()
                )
            )
        )
    }

    @Test
    fun rejectsPersonalDataLikeEmail() {
        assertEquals(
            MediationRejectionReason.PERSONAL_DATA,
            rejectionReason(
                validator.validate(
                    "Escríbeme a juan@correo.com para jugar.",
                    introRequest()
                )
            )
        )
    }

    @Test
    fun rejectsInappropriateLanguage() {
        val validation = validator.validate(
            "No seas tonto, intenta otra vez.",
            feedbackRequest(GeneralTeacherFeedbackType.INCORRECT_RETRY)
        )
        assertEquals(MediationRejectionReason.INAPPROPRIATE_LANGUAGE, rejectionReason(validation))
    }

    @Test
    fun defaultForbiddenPhrasesAreAllNormalized() {
        // Garantiza que la lista por defecto no contenga acentos ni mayusculas que
        // impidan la comparacion normalizada.
        for (phrase in GenerativeMediationSafetyValidator.DEFAULT_FORBIDDEN_PHRASES) {
            assertEquals(
                "La frase prohibida por defecto no esta normalizada: $phrase",
                phrase,
                phrase.lowercase()
            )
            assertFalse("La frase prohibida por defecto tiene espacios extra: $phrase", phrase != phrase.trim())
        }
    }
}
