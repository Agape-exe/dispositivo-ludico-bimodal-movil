package com.taller.app.gpt.script

/**
 * GEN01-FIX01: combina el guion propuesto por el modelo con el guion local de
 * respaldo campo por campo, para no descartar todo el trabajo por un único campo
 * recuperable.
 *
 * Para cada texto hablado:
 *  - si el modelo dio un valor utilizable (no vacío, no demasiado largo, sin frases
 *    no permitidas y, en pistas, sin revelar la respuesta), se conserva ese valor;
 *  - si no, se reemplaza SOLO ese campo con el del guion local y se cuenta como
 *    reparado.
 *
 * El resultado siempre es un guion completo y válido para que la docente lo revise.
 * No hace red, no registra contenido sensible y no reemplaza la pregunta original.
 */
object SessionScriptRecovery {

    data class Result(
        val script: SessionScript,
        /** Campos que hubo que completar con el guion local. */
        val repairedFields: Int,
        /** Campos conservados tal como los propuso el modelo. */
        val modelFieldsKept: Int
    ) {
        /** El modelo aportó algo utilizable (no todo vino del respaldo local). */
        val usedModelContent: Boolean get() = modelFieldsKept > 0
    }

    fun recover(
        model: SessionScript,
        local: SessionScript,
        input: SessionScriptInput
    ): Result {
        var repaired = 0
        var kept = 0

        fun pick(modelText: String, localText: String, reveals: (String) -> Boolean = { false }): String {
            val usable = modelText.isNotBlank() &&
                !SessionScriptValidator.isTooLong(modelText) &&
                !SessionScriptValidator.hasForbidden(modelText) &&
                !reveals(modelText)
            return if (usable) {
                kept++
                modelText.trim()
            } else {
                repaired++
                localText
            }
        }

        val intro = pick(model.intro, local.intro)
        val closing = pick(model.closing, local.closing)
        // toneNotes y pedagogicalWarnings son notas opcionales para la docente: se
        // conservan si son seguras, sin contar como reparación si vienen vacías.
        val toneNotes = optionalNote(model.toneNotes, local.toneNotes)
        val warnings = optionalNote(model.pedagogicalWarnings, "")

        val referenceByOrder = input.questions.associateBy({ it.orderIndex }, { it.referenceAnswer })
        val localByOrder = local.questions.associateBy { it.orderIndex }

        val mergedQuestions = model.questions.map { mq ->
            val localQ = localByOrder[mq.orderIndex]
                ?: return@map mq // sin par local: se conserva tal cual (caso atípico)
            val reference = referenceByOrder[mq.orderIndex].orEmpty()
            val revealsRef: (String) -> Boolean = { text ->
                reference.isNotBlank() && SessionScriptValidator.revealsAnswer(text, reference)
            }
            QuestionScript(
                questionId = mq.questionId,
                orderIndex = mq.orderIndex,
                childFriendlyQuestionText = pick(mq.childFriendlyQuestionText, localQ.childFriendlyQuestionText),
                hintLevel1 = pick(mq.hintLevel1, localQ.hintLevel1, revealsRef),
                hintLevel2 = pick(mq.hintLevel2, localQ.hintLevel2, revealsRef),
                hintLevel3 = pick(mq.hintLevel3, localQ.hintLevel3, revealsRef),
                positiveFeedbackText = pick(mq.positiveFeedbackText, localQ.positiveFeedbackText),
                supportiveFeedbackText = pick(mq.supportiveFeedbackText, localQ.supportiveFeedbackText),
                retryPromptText = pick(mq.retryPromptText, localQ.retryPromptText),
                // Notas de referencia: metadatos para la docente, no se hablan.
                answerReferenceWarning = if (SessionScriptValidator.hasForbidden(mq.answerReferenceWarning)) {
                    ""
                } else {
                    mq.answerReferenceWarning.trim()
                },
                suggestedReferenceAnswer = mq.suggestedReferenceAnswer.trim()
                    .ifEmpty { localQ.suggestedReferenceAnswer }
            )
        }

        val script = SessionScript(
            intro = intro,
            closing = closing,
            toneNotes = toneNotes,
            pedagogicalWarnings = warnings,
            questions = mergedQuestions
        )
        return Result(script = script, repairedFields = repaired, modelFieldsKept = kept)
    }

    /** Conserva una nota opcional si es segura; si está vacía o no permitida, usa el respaldo. */
    private fun optionalNote(modelText: String, localText: String): String {
        if (modelText.isBlank()) return localText
        if (SessionScriptValidator.hasForbidden(modelText) || SessionScriptValidator.isTooLong(modelText)) {
            return localText
        }
        return modelText.trim()
    }
}
