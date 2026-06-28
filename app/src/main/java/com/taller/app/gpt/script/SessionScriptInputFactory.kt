package com.taller.app.gpt.script

import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.QuestionEntity

/**
 * GEN01: arma el [SessionScriptInput] a partir de las entidades de la sesion.
 * Solo se incluye texto pedagogico; nada de datos de ninos.
 */
object SessionScriptInputFactory {

    fun from(activity: ActivityEntity, questions: List<QuestionEntity>): SessionScriptInput =
        SessionScriptInput(
            sessionName = activity.name.trim(),
            topic = activity.topic.trim(),
            description = activity.description.orEmpty().trim(),
            objective = activity.objective.orEmpty().trim(),
            ageLevel = activity.ageLevel.orEmpty().trim(),
            contextNotes = activity.classContextNotes.orEmpty().trim(),
            questions = questions
                .sortedBy { it.orderIndex }
                .map { q ->
                    SessionScriptQuestionInput(
                        questionId = q.id,
                        orderIndex = q.orderIndex,
                        questionText = q.questionText.trim(),
                        referenceAnswer = q.expectedAnswer.trim()
                    )
                }
        )
}
