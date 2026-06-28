package com.taller.app.voice.prep

import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.QuestionEntity

/**
 * Adapta las entidades de Room (sesion + preguntas con guion de GEN01) a la fuente
 * decoplada [SessionVoiceSource] que consume [SessionVoiceLines].
 */
object SessionVoiceSourceFactory {

    fun from(activity: ActivityEntity, questions: List<QuestionEntity>): SessionVoiceSource =
        SessionVoiceSource(
            introText = activity.generatedIntroText,
            closingText = activity.generatedClosingText,
            questions = questions.sortedBy { it.orderIndex }.map { q ->
                QuestionVoiceSource(
                    questionId = q.id,
                    orderIndex = q.orderIndex,
                    childFriendlyQuestionText = q.childFriendlyQuestionText,
                    rawQuestionText = q.questionText,
                    hint1 = q.hintLevel1,
                    hint2 = q.hintLevel2,
                    hint3 = q.hintLevel3,
                    positiveFeedbackText = q.positiveFeedbackText,
                    supportiveFeedbackText = q.supportiveFeedbackText,
                    retryPromptText = q.retryPromptText
                )
            }
        )
}
