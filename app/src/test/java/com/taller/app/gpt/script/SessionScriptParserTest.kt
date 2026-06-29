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
    fun parse_jsonWithSurroundingText_extractsObject() {
        val wrapped = "Claro, aquí tienes el guion:\n" + validJson() + "\n¡Espero que te sirva!"
        val script = SessionScriptParser.parse(wrapped, input())
        assertEquals(2, script.questions.size)
        assertEquals("Hola explorador", script.intro)
    }

    @Test
    fun parse_acceptsFieldAliases() {
        val aliased = """
            {
              "sessionIntro": "Hola desde alias",
              "sessionClosing": "Chau alias",
              "tone": "calido",
              "warnings": "",
              "items": [
                {
                  "orderIndex": 1,
                  "spokenQuestion": "Dime un animalito",
                  "hints": ["viven en casa", "cuatro patas", "hacen ruido"],
                  "positiveFeedback": "Genial",
                  "supportiveFeedback": "Sigamos",
                  "retryPrompt": "Otra vez",
                  "suggestedReference": "perro"
                },
                {
                  "orderIndex": 2,
                  "childFriendlyQuestion": "Que come la vaca",
                  "hint1": "es verde", "hint2": "del campo", "hint3": "lo mastican",
                  "positiveFeedbackText": "Muy bien",
                  "supportiveFeedbackText": "Casi",
                  "retryPromptText": "Probemos"
                }
              ]
            }
        """.trimIndent()

        val script = SessionScriptParser.parse(aliased, input())
        assertEquals("Hola desde alias", script.intro)
        assertEquals("Chau alias", script.closing)
        assertEquals("Dime un animalito", script.questions[0].childFriendlyQuestionText)
        assertEquals("viven en casa", script.questions[0].hintLevel1)
        assertEquals("Genial", script.questions[0].positiveFeedbackText)
        assertEquals("Que come la vaca", script.questions[1].childFriendlyQuestionText)
        assertEquals("es verde", script.questions[1].hintLevel1)
    }

    @Test
    fun parse_invalidJson_throws() {
        assertThrows(SessionScriptParseException::class.java) {
            SessionScriptParser.parse("no soy json", input())
        }
    }

    @Test
    fun parse_truncatedJson_throws() {
        val truncated = """
            {
              "intro": "Hola",
              "closing": "Adios",
              "questions": [
                { "orderIndex": 1, "childFriendlyQuestionText": "x", "hintLevel1
        """.trimIndent()
        assertThrows(SessionScriptParseException::class.java) {
            SessionScriptParser.parse(truncated, input())
        }
    }

    @Test
    fun parse_missingQuestion_isTolerantWithBlankFields() {
        // GEN01-FIX01: una pregunta sin guion del modelo NO descarta el resto; se
        // devuelve con campos en blanco para que la recuperación los complete.
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

        val script = SessionScriptParser.parse(incomplete, input())
        assertEquals(2, script.questions.size)
        assertEquals("x", script.questions[0].childFriendlyQuestionText)
        // La segunda pregunta no vino en el guion: queda en blanco, no lanza.
        assertEquals("", script.questions[1].childFriendlyQuestionText)
        assertEquals(11L, script.questions[1].questionId)
    }
}
