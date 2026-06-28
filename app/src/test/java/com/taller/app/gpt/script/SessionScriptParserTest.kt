package com.taller.app.gpt.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionScriptParserTest {

    private fun input() = SessionScriptInput(
        sessionName = "Animales",
        topic = "Animales domésticos",
        description = "",
        objective = "",
        ageLevel = "3 a 5 años",
        contextNotes = "",
        questions = listOf(
            SessionScriptQuestionInput(
                questionId = 10L,
                orderIndex = 1,
                questionText = "Menciona un animal doméstico.",
                referenceAnswer = "perro"
            ),
            SessionScriptQuestionInput(
                questionId = 11L,
                orderIndex = 2,
                questionText = "¿Qué come una vaca?",
                referenceAnswer = "pasto"
            )
        )
    )

    private fun validJson() = """
        {
          "intro": "Hola explorador",
          "closing": "Adios explorador",
          "toneNotes": "calido",
          "pedagogicalWarnings": "",
          "questions": [
            {
              "orderIndex": 1,
              "childFriendlyQuestionText": "Dime un animalito que viva con las personas",
              "hintLevel1": "Algunos viven en casa",
              "hintLevel2": "Tiene cuatro patas",
              "hintLevel3": "Le gusta jugar",
              "positiveFeedbackText": "Muy bien",
              "supportiveFeedbackText": "Casi, sigamos pensando",
              "retryPromptText": "Probemos otra vez",
              "answerReferenceWarning": "La pregunta admite varias respuestas",
              "suggestedReferenceAnswer": "perro, gato, conejo"
            },
            {
              "orderIndex": 2,
              "childFriendlyQuestionText": "Que crees que come una vaquita",
              "hintLevel1": "Es algo verde",
              "hintLevel2": "Crece en el campo",
              "hintLevel3": "Las vacas lo mastican",
              "positiveFeedbackText": "Excelente",
              "supportiveFeedbackText": "Sigamos pensando juntos",
              "retryPromptText": "Inténtalo otra vez",
              "answerReferenceWarning": "",
              "suggestedReferenceAnswer": "pasto"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parse_validJson_mapsQuestionsById() {
        val script = SessionScriptParser.parse(validJson(), input())

        assertEquals("Hola explorador", script.intro)
        assertEquals(2, script.questions.size)
        assertEquals(10L, script.questions[0].questionId)
        assertEquals(11L, script.questions[1].questionId)
        assertEquals("perro, gato, conejo", script.questions[0].suggestedReferenceAnswer)
    }

    @Test
    fun parse_fencedJson_isAccepted() {
        val fenced = "```json\n" + validJson() + "\n```"
        val script = SessionScriptParser.parse(fenced, input())
        assertEquals(2, script.questions.size)
    }

    @Test
    fun parse_invalidJson_throws() {
        assertThrows(SessionScriptParseException::class.java) {
            SessionScriptParser.parse("no soy json", input())
        }
    }

    @Test
    fun parse_missingQuestion_throws() {
        val incomplete = """
            {
              "intro": "Hola",
              "closing": "Adios",
              "toneNotes": "",
              "pedagogicalWarnings": "",
              "questions": [
                {
                  "orderIndex": 1,
                  "childFriendlyQuestionText": "x",
                  "hintLevel1": "a", "hintLevel2": "b", "hintLevel3": "c",
                  "positiveFeedbackText": "p", "supportiveFeedbackText": "s",
                  "retryPromptText": "r", "answerReferenceWarning": "",
                  "suggestedReferenceAnswer": "perro"
                }
              ]
            }
        """.trimIndent()

        assertThrows(SessionScriptParseException::class.java) {
            SessionScriptParser.parse(incomplete, input())
        }
    }
}
