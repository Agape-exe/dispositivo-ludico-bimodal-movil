package com.taller.app.gpt.script

/**
 * GEN01: guion local basico y determinista, generado sin servicio externo.
 * Garantiza que la docente siempre pueda preparar y revisar un guion aunque GPT
 * no este disponible. Funciona con cualquier tema (no depende de bancos especificos).
 */
object SessionScriptLocalFallback {

    fun build(input: SessionScriptInput): SessionScript {
        val topic = input.topic.trim().ifEmpty { "lo que vamos a descubrir" }

        val questions = input.questions.map { q ->
            QuestionScript(
                questionId = q.questionId,
                orderIndex = q.orderIndex,
                childFriendlyQuestionText = friendlyQuestion(topic, q.questionText),
                hintLevel1 = "Piensa con calma, tú puedes.",
                hintLevel2 = "Recuerda lo que estuvimos explorando sobre $topic.",
                hintLevel3 = "Dime lo que se te ocurra, no hay prisa.",
                positiveFeedbackText = "¡Muy bien, explorador! Aprendí algo nuevo contigo.",
                supportiveFeedbackText = "Casi, sigamos pensando juntos. Estoy aprendiendo contigo.",
                retryPromptText = "¿Lo intentamos otra vez? Tú puedes.",
                answerReferenceWarning = "",
                // No reemplaza la referencia original; solo la conserva como sugerencia.
                suggestedReferenceAnswer = q.referenceAnswer.trim()
            )
        }

        return SessionScript(
            intro = "¡Hola, explorador! Soy Seven y hoy vamos a descubrir cosas sobre $topic. ¿Me acompañas?",
            closing = "¡Gracias por explorar conmigo! Aprendí mucho contigo hoy.",
            toneNotes = "Tono cálido, cercano y sin prisa, adecuado para niños pequeños.",
            pedagogicalWarnings = "",
            questions = questions
        )
    }

    private fun friendlyQuestion(topic: String, original: String): String {
        val clean = original.trim()
        val leadIn = "Seven está explorando sobre $topic. ¿Me ayudas? "
        val combined = leadIn + clean
        // Mantiene la frase dentro de un largo razonable para niños pequeños.
        return if (combined.length <= SessionScriptValidator.MAX_SPOKEN_CHARS) combined else clean
    }
}
