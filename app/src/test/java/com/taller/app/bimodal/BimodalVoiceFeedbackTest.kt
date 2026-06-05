package com.taller.app.bimodal

import com.taller.app.voice.ToySpeechPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BimodalVoiceFeedbackTest {

    @Test
    fun correctAnswer_returnsCorrectNeutral() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_CORRECT,
            canRetry = false
        )
        assertEquals(ToySpeechPhrase.CORRECT_NEUTRAL, phrase)
    }

    @Test
    fun incorrectWithRetry_returnsIncorrectRetry() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_INCORRECT,
            canRetry = true
        )
        assertEquals(ToySpeechPhrase.INCORRECT_RETRY, phrase)
    }

    @Test
    fun incorrectWithoutRetry_returnsIncorrectNext() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_INCORRECT,
            canRetry = false
        )
        assertEquals(ToySpeechPhrase.INCORRECT_NEXT, phrase)
    }

    @Test
    fun lastQuestionCorrect_returnsCorrectFinalWithoutContinuity() {
        // En la ultima pregunta el acierto no debe invitar a continuar.
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_CORRECT,
            canRetry = false,
            questionIndex = 2,
            isLastQuestion = true
        )
        assertEquals(ToySpeechPhrase.CORRECT_FINAL, phrase)
    }

    @Test
    fun lastQuestionIncorrectWithoutRetry_returnsIncorrectFinalNotNext() {
        // En la ultima pregunta una incorrecta sin reintento no debe decir
        // "vamos con la siguiente pregunta".
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_INCORRECT,
            canRetry = false,
            questionIndex = 2,
            isLastQuestion = true
        )
        assertEquals(ToySpeechPhrase.INCORRECT_FINAL, phrase)
    }

    @Test
    fun lastQuestionIncorrectWithRetry_stillReturnsRetry() {
        // Aunque sea la ultima pregunta, si quedan intentos se reintenta.
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_INCORRECT,
            canRetry = true,
            questionIndex = 2,
            isLastQuestion = true
        )
        assertEquals(ToySpeechPhrase.INCORRECT_RETRY, phrase)
    }

    @Test
    fun notInterpretableWithoutRetry_returnsNotInterpretableFinal() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE,
            canRetry = false
        )
        assertEquals(ToySpeechPhrase.NOT_INTERPRETABLE_FINAL, phrase)
    }

    @Test
    fun noResponseWithoutRetry_returnsNoResponseFinal() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_NO_RESPONSE,
            canRetry = false
        )
        assertEquals(ToySpeechPhrase.NO_RESPONSE_FINAL, phrase)
    }

    @Test
    fun notInterpretable_returnsNotInterpretableRetry() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE,
            canRetry = true
        )
        assertEquals(ToySpeechPhrase.NOT_INTERPRETABLE_RETRY, phrase)
    }

    @Test
    fun noResponse_returnsNoResponseRetry() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_NO_RESPONSE,
            canRetry = true
        )
        assertEquals(ToySpeechPhrase.NO_RESPONSE_RETRY, phrase)
    }

    @Test
    fun timeExpired_returnsTimeExpired() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.TIME_EXPIRED
        )
        assertEquals(ToySpeechPhrase.TIME_EXPIRED, phrase)
    }

    @Test
    fun sessionCompleted_returnsActivityFinished() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.SESSION_COMPLETED
        )
        assertEquals(ToySpeechPhrase.ACTIVITY_FINISHED, phrase)
    }

    @Test
    fun technicalError_returnsTechnicalError() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR,
            canRetry = true
        )
        assertEquals(ToySpeechPhrase.TECHNICAL_ERROR, phrase)
    }

    @Test
    fun waitingForFaceFirstQuestion_returnsActivityStart() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.WAITING_FOR_FACE,
            questionIndex = 0
        )
        assertEquals(ToySpeechPhrase.ACTIVITY_START, phrase)
    }

    @Test
    fun waitingForFaceSubsequentQuestion_returnsNextQuestion() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.WAITING_FOR_FACE,
            questionIndex = 1
        )
        assertEquals(ToySpeechPhrase.NEXT_QUESTION, phrase)
    }

    @Test
    fun listeningState_returnsNull() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.LISTENING
        )
        assertNull(phrase)
    }

    @Test
    fun presentingQuestionState_returnsNull() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.PRESENTING_QUESTION
        )
        assertNull(phrase)
    }

    @Test
    fun idleState_returnsNull() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.IDLE
        )
        assertNull(phrase)
    }

    @Test
    fun evaluatingState_returnsNull() {
        val phrase = BimodalVoiceFeedback.phraseFor(
            state = BimodalInteractionState.EVALUATING
        )
        assertNull(phrase)
    }
}
