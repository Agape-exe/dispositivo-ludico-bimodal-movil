package com.taller.app.bimodal.feedback

import com.taller.app.model.LocalMediationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import kotlin.random.Random

class AnimalMediationBankTest {

    private fun newBank(seed: Long = 1L) = AnimalMediationBank(random = Random(seed))

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
    }

    private val animalKeys = listOf(
        LocalMediationKey.ANIMAL_DOG_SOUND,
        LocalMediationKey.ANIMAL_DOMESTIC,
        LocalMediationKey.ANIMAL_CAT_SOUND,
        LocalMediationKey.ANIMAL_FARM
    )

    private fun allPhraseLists(): List<List<String>> {
        val general = listOf(
            AnimalMediationBank.MISSION_START,
            AnimalMediationBank.MISSION_COMPLETED,
            AnimalMediationBank.MICRO_DIALOGUES,
            AnimalMediationBank.GENERAL_NOT_INTERPRETABLE,
            AnimalMediationBank.GENERAL_NO_RESPONSE,
            AnimalMediationBank.GENERAL_TECHNICAL_ERROR
        )
        val scenarioLists = AnimalMediationBank.SCENARIOS.values.flatten().flatMap { scenario ->
            listOf(
                scenario.intro,
                scenario.correctFeedback,
                scenario.incorrectRetryFeedback,
                scenario.incorrectNextFeedback
            )
        }
        return general + scenarioLists
    }

    // ----- Cobertura y variedad del banco ----------------------------------------

    @Test
    fun everyGeneralPhraseList_hasAtLeastTenPhrases() {
        val general = listOf(
            AnimalMediationBank.MISSION_START,
            AnimalMediationBank.MISSION_COMPLETED,
            AnimalMediationBank.MICRO_DIALOGUES,
            AnimalMediationBank.GENERAL_NOT_INTERPRETABLE,
            AnimalMediationBank.GENERAL_NO_RESPONSE,
            AnimalMediationBank.GENERAL_TECHNICAL_ERROR
        )
        for (list in general) {
            assertTrue(
                "Un conjunto general tiene ${list.size} frases, se esperaban al menos 10",
                list.size >= 10
            )
        }
    }

    @Test
    fun advancedInitialFaceGreeting_hasAtLeastTenPhrases() {
        assertTrue(
            "ADVANCED_INITIAL_FACE_GREETING tiene ${AnimalMediationBank.ADVANCED_INITIAL_FACE_GREETING.size} frases",
            AnimalMediationBank.ADVANCED_INITIAL_FACE_GREETING.size >= 10
        )
    }

    @Test
    fun getInitialFaceGreetingPhrase_returnsPhraseFromItsBank() {
        val bank = newBank()
        repeat(40) {
            val phrase = bank.getInitialFaceGreetingPhrase()
            assertTrue(
                "El saludo inicial no proviene de su banco: $phrase",
                AnimalMediationBank.ADVANCED_INITIAL_FACE_GREETING.contains(phrase)
            )
        }
    }

    @Test
    fun getInitialFaceGreetingPhrase_doesNotRepeatConsecutively() {
        val bank = newBank()
        var previous: String? = null
        repeat(200) {
            val current = bank.getInitialFaceGreetingPhrase()
            assertTrue("Saludo inicial repetido consecutivo: $current", current != previous)
            previous = current
        }
    }

    @Test
    fun advancedQuestionIntroGeneral_hasAtLeastTenPhrases() {
        assertTrue(
            "ADVANCED_QUESTION_INTRO_GENERAL tiene ${AnimalMediationBank.ADVANCED_QUESTION_INTRO_GENERAL.size} frases",
            AnimalMediationBank.ADVANCED_QUESTION_INTRO_GENERAL.size >= 10
        )
    }

    @Test
    fun advancedQuestionIntroGeneral_allPhrasesHaveQuestionPlaceholder() {
        for (phrase in AnimalMediationBank.ADVANCED_QUESTION_INTRO_GENERAL) {
            assertTrue(
                "La frase de intro genérica no tiene {question}: $phrase",
                phrase.contains("{question}")
            )
        }
    }

    @Test
    fun getGenericIntroWithQuestion_fillsPlaceholder() {
        val bank = newBank()
        val questionText = "¿Qué sonido hace el perro?"
        val result = bank.getGenericIntroWithQuestion(questionText)
        assertFalse("No debe quedar el marcador {question}", result.contains("{question}"))
        assertTrue("Debe contener el texto de la pregunta", result.contains(questionText.trim()))
    }

    @Test
    fun everyAnimalKey_hasAtLeastTenScenarios_eachWellFormed() {
        for (key in animalKeys) {
            val scenarios = AnimalMediationBank.SCENARIOS[key]
            assertNotNull("Falta lista de escenarios para $key", scenarios)
            assertTrue(
                "$key tiene ${scenarios!!.size} escenarios, se esperaban al menos 10",
                scenarios.size >= 10
            )
            for (scenario in scenarios) {
                assertTrue("${scenario.id}: sin intro", scenario.intro.isNotEmpty())
                assertTrue("${scenario.id}: sin correctFeedback", scenario.correctFeedback.isNotEmpty())
                assertTrue(
                    "${scenario.id}: sin incorrectRetryFeedback",
                    scenario.incorrectRetryFeedback.isNotEmpty()
                )
                assertTrue(
                    "${scenario.id}: sin incorrectNextFeedback",
                    scenario.incorrectNextFeedback.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun scenarioIds_areUnique() {
        val ids = AnimalMediationBank.SCENARIOS.values.flatten().map { it.id }
        assertEquals("Hay ids de escenario repetidos", ids.size, ids.toSet().size)
    }

    @Test
    fun forbiddenMisleadingPhrases_neverAppear() {
        val forbidden = listOf(
            "mencionaste una idea importante",
            "vas por buen camino",
            "estas cerca",
            "casi lo tienes",
            "vas bien"
        )
        for (list in allPhraseLists()) {
            for (phrase in list) {
                val normalized = normalize(phrase)
                for (banned in forbidden) {
                    assertFalse(
                        "Frase engañosa detectada: $phrase",
                        normalized.contains(banned)
                    )
                }
            }
        }
    }

    @Test
    fun dogRetryFeedback_neverRevealsExpectedSound_guau() {
        // En Seven el reintento puede mencionar "ladrido" (descriptor) pero no "guau" (respuesta directa).
        for (scenario in AnimalMediationBank.SCENARIOS.getValue(LocalMediationKey.ANIMAL_DOG_SOUND)) {
            for (phrase in scenario.incorrectRetryFeedback) {
                val text = normalize(phrase)
                assertFalse(
                    "El reintento de ${scenario.id} revela 'guau': $phrase",
                    text.contains("guau")
                )
            }
        }
    }

    @Test
    fun noForbiddenWord_pistaAsMainTerm_inAnyPhrase() {
        // "Pista" está excluida como término principal de vocabulario de Seven.
        // Se verifica que no aparezca en frases principales (intro, feedback).
        val scenarioPhrases = AnimalMediationBank.SCENARIOS.values.flatten().flatMap {
            it.intro + it.correctFeedback + it.incorrectRetryFeedback + it.incorrectNextFeedback
        }
        val generalPhrases = AnimalMediationBank.MISSION_START +
            AnimalMediationBank.MISSION_COMPLETED +
            AnimalMediationBank.GENERAL_NOT_INTERPRETABLE +
            AnimalMediationBank.GENERAL_NO_RESPONSE +
            AnimalMediationBank.GENERAL_TECHNICAL_ERROR
        for (phrase in scenarioPhrases + generalPhrases) {
            assertFalse(
                "Se encontró 'pista' como término en: $phrase",
                normalize(phrase).contains(" pista ")
            )
        }
    }

    // ----- Resolución por clave de mediación -------------------------------------

    @Test
    fun animalKeys_returnPhrasesFromOneOfTheirScenarios() {
        for (key in animalKeys) {
            val bank = newBank()
            val name = key.name
            val intro = bank.getQuestionIntroduction(name)
            val activeId = bank.currentScenarioId(name)
            assertNotNull("No se seleccionó escenario para $key", activeId)
            val scenario = AnimalMediationBank.SCENARIOS.getValue(key).first { it.id == activeId }
            assertTrue("La intro de $key no proviene de su escenario", scenario.intro.contains(intro))
            assertTrue(
                "El feedback correcto de $key no proviene del mismo escenario",
                scenario.correctFeedback.contains(bank.getCorrectFeedback(name))
            )
            assertTrue(
                "El feedback de reintento de $key no proviene del mismo escenario",
                scenario.incorrectRetryFeedback.contains(bank.getIncorrectRetryFeedback(name))
            )
        }
    }

    @Test
    fun noneKey_usesGeneralFallbackForContextualCategories() {
        val bank = newBank()
        val intro = bank.getQuestionIntroduction(LocalMediationKey.NONE.name)
        assertTrue(
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.QUESTION_INTRO).contains(intro)
        )
        val correct = bank.getCorrectFeedback(LocalMediationKey.NONE.name)
        assertTrue(
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.CORRECT).contains(correct)
        )
        assertEquals(null, bank.currentScenarioId(LocalMediationKey.NONE.name))
    }

    @Test
    fun blankOrUnknownKey_usesGeneralFallback() {
        for (key in listOf(null, "", "   ", "CLAVE_INEXISTENTE")) {
            val bank = newBank()
            val intro = bank.getQuestionIntroduction(key)
            assertTrue(
                "La clave '$key' debe usar fallback general para la intro",
                GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                    .getValue(GeneralTeacherFeedbackType.QUESTION_INTRO).contains(intro)
            )
            val correct = bank.getCorrectFeedback(key)
            assertTrue(
                "La clave '$key' debe usar fallback general para el feedback correcto",
                GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                    .getValue(GeneralTeacherFeedbackType.CORRECT).contains(correct)
            )
        }
    }

    @Test
    fun emptyScenarioLists_fallBackToGeneralPhrasesInsteadOfCrashing() {
        val emptyScenario = AnimalNarrativeScenario(
            id = "EMPTY_TEST",
            intro = emptyList(),
            correctFeedback = emptyList(),
            incorrectRetryFeedback = emptyList(),
            incorrectNextFeedback = emptyList()
        )
        val bank = AnimalMediationBank(
            random = Random(1L),
            scenarios = mapOf(LocalMediationKey.ANIMAL_DOG_SOUND to listOf(emptyScenario))
        )
        val key = LocalMediationKey.ANIMAL_DOG_SOUND.name

        val intro = bank.getQuestionIntroduction(key)
        assertTrue(
            "La intro de un escenario vacío debe venir del fallback general",
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.QUESTION_INTRO).contains(intro)
        )
        val correct = bank.getCorrectFeedback(key)
        assertTrue(
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.CORRECT).contains(correct)
        )
        val retry = bank.getIncorrectRetryFeedback(key)
        assertTrue(
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.INCORRECT_RETRY).contains(retry)
        )
        val next = bank.getIncorrectNextFeedback(key, isLastQuestion = false)
        assertTrue(
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.INCORRECT_NEXT).contains(next)
        )
        val nextLast = bank.getIncorrectNextFeedback(key, isLastQuestion = true)
        assertTrue(
            AnimalMediationBank.MISSION_COMPLETED.contains(nextLast)
        )
    }

    // ----- Coherencia de escenario (mini historias) ------------------------------

    @Test
    fun scenarioStaysStableBetweenIntroAndFeedbackForOneQuestion() {
        for (key in animalKeys) {
            val bank = newBank()
            val name = key.name
            bank.getQuestionIntroduction(name)
            val scenarioId = bank.currentScenarioId(name)
            assertNotNull(scenarioId)
            bank.getCorrectFeedback(name)
            assertEquals(scenarioId, bank.currentScenarioId(name))
            bank.getIncorrectRetryFeedback(name)
            assertEquals(scenarioId, bank.currentScenarioId(name))
            bank.getIncorrectNextFeedback(name, isLastQuestion = false)
            assertEquals(scenarioId, bank.currentScenarioId(name))
        }
    }

    @Test
    fun feedbackComesFromTheSameScenarioAsTheIntro() {
        for (key in animalKeys) {
            for (seed in 1L..12L) {
                val bank = newBank(seed)
                val name = key.name
                val intro = bank.getQuestionIntroduction(name)
                val scenarioId = bank.currentScenarioId(name)
                val scenario = AnimalMediationBank.SCENARIOS.getValue(key).first { it.id == scenarioId }
                assertTrue("Intro fuera del escenario activo", scenario.intro.contains(intro))
                assertTrue(
                    "Feedback correcto fuera del escenario activo ($scenarioId)",
                    scenario.correctFeedback.contains(bank.getCorrectFeedback(name))
                )
                assertTrue(
                    "Feedback de cierre incorrecto fuera del escenario activo ($scenarioId)",
                    scenario.incorrectNextFeedback.contains(
                        bank.getIncorrectNextFeedback(name, isLastQuestion = false)
                    )
                )
            }
        }
    }

    // ----- Reglas de la última pregunta ------------------------------------------

    @Test
    fun incorrectNext_onLastQuestion_neverUsesContinuationPhrase() {
        val continuation = listOf("continuemos", "continuar", "siguiente", "pasemos", "sigamos", "vamos con otra")
        for (key in animalKeys) {
            val bank = newBank()
            repeat(120) {
                val text = normalize(bank.getIncorrectNextFeedback(key.name, isLastQuestion = true))
                assertTrue("Frase vacía", text.isNotBlank())
                for (marker in continuation) {
                    assertFalse(
                        "En la última pregunta ($key) se usó continuidad: $text",
                        text.contains(marker)
                    )
                }
            }
        }
    }

    @Test
    fun correctFeedback_onLastQuestion_neverUsesContinuationPhrase() {
        val continuation = listOf("continuemos", "continuar", "siguiente", "pasemos", "sigamos", "vamos con otra")
        for (key in animalKeys) {
            val bank = newBank()
            repeat(120) {
                val text = normalize(bank.getCorrectFeedback(key.name, isLastQuestion = true))
                assertTrue("Frase vacía", text.isNotBlank())
                for (marker in continuation) {
                    assertFalse(
                        "En la última pregunta ($key) el acierto usó continuidad: $text",
                        text.contains(marker)
                    )
                }
            }
        }
    }

    @Test
    fun noLastQuestionPhrase_announcesNextQuestion() {
        for (key in animalKeys) {
            val bank = newBank()
            repeat(60) {
                val correct = normalize(bank.getCorrectFeedback(key.name, isLastQuestion = true))
                val next = normalize(bank.getIncorrectNextFeedback(key.name, isLastQuestion = true))
                assertFalse("Acierto menciona siguiente pregunta: $correct", correct.contains("siguiente pregunta"))
                assertFalse("Cierre menciona siguiente pregunta: $next", next.contains("siguiente pregunta"))
            }
        }
    }

    @Test
    fun incorrectNext_notLastQuestion_isNeverBlank() {
        val bank = newBank()
        repeat(40) {
            val text = bank.getIncorrectNextFeedback(LocalMediationKey.ANIMAL_DOG_SOUND.name, isLastQuestion = false)
            assertTrue("Frase vacía", text.isNotBlank())
        }
    }

    // ----- Selección variada -----------------------------------------------------

    @Test
    fun doesNotRepeatSameIntroductionConsecutively() {
        for (key in animalKeys) {
            val bank = newBank()
            var previous: String? = null
            repeat(200) {
                val current = bank.getQuestionIntroduction(key.name)
                assertTrue("Introducción repetida consecutiva en $key: $current", current != previous)
                previous = current
            }
        }
    }

    @Test
    fun doesNotRepeatSameCorrectFeedbackConsecutively() {
        // Con 10 escenarios distintos por clave, el feedback correcto cambia al cambiar de escenario.
        for (key in animalKeys) {
            val bank = newBank()
            var previous: String? = null
            repeat(200) {
                bank.getQuestionIntroduction(key.name)
                val current = bank.getCorrectFeedback(key.name)
                assertTrue("Feedback correcto repetido consecutivo en $key: $current", current != previous)
                previous = current
            }
        }
    }

    @Test
    fun doesNotRepeatSameGeneralPhraseConsecutively() {
        val bank = newBank()
        val producers = listOf<() -> String>(
            bank::getSessionStartPhrase,
            bank::getSessionCompletedPhrase,
            bank::getNotInterpretableFeedback,
            bank::getNoResponseFeedback,
            bank::getTechnicalErrorFeedback
        )
        for (producer in producers) {
            var previous: String? = null
            repeat(200) {
                val current = producer()
                assertTrue("Frase general repetida consecutiva: $current", current != previous)
                previous = current
            }
        }
    }

    @Test
    fun correctFeedback_doesNotAlwaysStartWithLoLograste() {
        var loLograste = 0
        var total = 0
        for (key in animalKeys) {
            for (seed in 1L..20L) {
                val bank = newBank(seed)
                repeat(10) {
                    bank.getQuestionIntroduction(key.name)
                    val text = normalize(bank.getCorrectFeedback(key.name))
                    total++
                    if (text.startsWith("lo lograste")) loLograste++
                }
            }
        }
        assertTrue(
            "Demasiados aciertos empiezan con 'lo lograste' ($loLograste de $total)",
            loLograste * 4 < total
        )
    }

    @Test
    fun generalPhrases_neverBlameChild() {
        val blame = listOf("te equivoc", "fallaste", "lo hiciste mal", "tu error", "eres malo")
        val producers = listOf(
            AnimalMediationBank.GENERAL_NOT_INTERPRETABLE,
            AnimalMediationBank.GENERAL_NO_RESPONSE,
            AnimalMediationBank.GENERAL_TECHNICAL_ERROR
        )
        for (list in producers) {
            for (phrase in list) {
                val text = normalize(phrase)
                for (marker in blame) {
                    assertFalse("Frase culpa al niño: $phrase", text.contains(marker))
                }
            }
        }
    }
}
