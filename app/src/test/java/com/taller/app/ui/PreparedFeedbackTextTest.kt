package com.taller.app.ui

import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType
import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreparedFeedbackTextTest {

    private fun question(
        childFriendly: String? = "Pregunta amigable",
        positive: String? = "Muy bien",
        supportive: String? = "Sigamos pensando",
        retry: String? = "Probemos otra vez"
    ) = LearningQuestion(
        id = "1",
        questionText = "¿Pregunta original?",
        expectedAnswer = "respuesta",
        keywords = listOf("respuesta"),
        maxTimeSeconds = 30,
        maxAttempts = 2,
        childFriendlyQuestionText = childFriendly,
        positiveFeedbackText = positive,
        supportiveFeedbackText = supportive,
        retryPromptText = retry
    )

    private fun activity(
        intro: String? = "Hola, soy Seven",
        closing: String? = "Hasta pronto"
    ) = LearningActivity(
        id = "1",
        title = "Sesión",
        mode = OperationMode.ADVANCED,
        questions = emptyList(),
        generatedIntroText = intro,
        generatedClosingText = closing,
        voicePrepReady = true
    )

    @Test
    fun correctUsesPositiveFeedback() {
        assertEquals(
            "Muy bien",
            preparedFeedbackTextFor(GeneralTeacherFeedbackType.CORRECT, question(), activity())
        )
    }

    @Test
    fun retryCategoriesUseRetryPromptThenSupportive() {
        assertEquals(
            "Probemos otra vez",
            preparedFeedbackTextFor(GeneralTeacherFeedbackType.INCORRECT_RETRY, question(), activity())
        )
        // Si no hay reintento preparado, cae al feedback de apoyo.
        assertEquals(
            "Sigamos pensando",
            preparedFeedbackTextFor(
                GeneralTeacherFeedbackType.NO_RESPONSE_RETRY,
                question(retry = null),
                activity()
            )
        )
    }

    @Test
    fun nextCategoriesUseSupportiveFeedback() {
        assertEquals(
            "Sigamos pensando",
            preparedFeedbackTextFor(GeneralTeacherFeedbackType.INCORRECT_NEXT, question(), activity())
        )
    }

    @Test
    fun sessionCompletedUsesClosingAndStartUsesIntro() {
        assertEquals(
            "Hasta pronto",
            preparedFeedbackTextFor(GeneralTeacherFeedbackType.SESSION_COMPLETED, question(), activity())
        )
        assertEquals(
            "Hola, soy Seven",
            preparedFeedbackTextFor(GeneralTeacherFeedbackType.SESSION_START, question(), activity())
        )
    }

    @Test
    fun questionIntroHasNoPreparedEquivalent() {
        assertNull(
            preparedFeedbackTextFor(GeneralTeacherFeedbackType.QUESTION_INTRO, question(), activity())
        )
    }

    @Test
    fun blankPreparedTextIsTreatedAsAbsent() {
        assertNull(
            preparedFeedbackTextFor(
                GeneralTeacherFeedbackType.CORRECT,
                question(positive = "   "),
                activity()
            )
        )
    }
}
