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

    /** Minusculas y sin acentos, para comparaciones robustas de contenido. */
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

    /** Todas las frases predefinidas del banco (generales + de cada escenario). */
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

    // ----- Cobertura y variedad del banco --------------------------------------

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
    fun everyAnimalKey_hasAtLeastThreeScenarios_eachWellFormed() {
        for (key in animalKeys) {
            val scenarios = AnimalMediationBank.SCENARIOS[key]
            assertNotNull("Falta lista de escenarios para $key", scenarios)
            assertTrue(
                "$key tiene ${scenarios!!.size} escenarios, se esperaban al menos 3",
                scenarios.size >= 3
            )
            for (scenario in scenarios) {
                assertTrue("${scenario.id}: <2 intros", scenario.intro.size >= 2)
                assertTrue("${scenario.id}: <3 correctFeedback", scenario.correctFeedback.size >= 3)
                assertTrue(
                    "${scenario.id}: <3 incorrectRetryFeedback",
                    scenario.incorrectRetryFeedback.size >= 3
                )
                assertTrue(
                    "${scenario.id}: <3 incorrectNextFeedback",
                    scenario.incorrectNextFeedback.size >= 3
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
                        "Frase enganosa detectada: $phrase",
                        normalized.contains(banned)
                    )
                }
            }
        }
    }

    @Test
    fun retryFeedback_neverRevealsTheExpectedSound() {
        // El reintento jamas debe revelar la respuesta esperada de los sonidos.
        val revealed = listOf("guau", "miau")
        val soundKeys = listOf(
            LocalMediationKey.ANIMAL_DOG_SOUND,
            LocalMediationKey.ANIMAL_CAT_SOUND
        )
        for (key in soundKeys) {
            for (scenario in AnimalMediationBank.SCENARIOS.getValue(key)) {
                for (phrase in scenario.incorrectRetryFeedback) {
                    val text = normalize(phrase)
                    for (word in revealed) {
                        assertFalse(
                            "El reintento de ${scenario.id} revela la respuesta: $phrase",
                            text.contains(word)
                        )
                    }
                }
            }
        }
    }

    // ----- Resolucion por clave de mediacion -----------------------------------

    @Test
    fun animalKeys_returnPhrasesFromOneOfTheirScenarios() {
        for (key in animalKeys) {
            val bank = newBank()
            val name = key.name
            val intro = bank.getQuestionIntroduction(name)
            val activeId = bank.currentScenarioId(name)
            assertNotNull("No se selecciono escenario para $key", activeId)
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
        // NONE no selecciona ningun escenario de animales.
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
        // Un escenario con todas sus listas vacias jamas debe romper el flujo: cada
        // categoria recurre al banco general tipo profesor (o al cierre, segun el caso).
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
            "La intro de un escenario vacio debe venir del fallback general",
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
        // En la ultima pregunta el respaldo es la frase de cierre.
        val nextLast = bank.getIncorrectNextFeedback(key, isLastQuestion = true)
        assertTrue(
            AnimalMediationBank.MISSION_COMPLETED.contains(nextLast)
        )
    }

    // ----- Coherencia de escenario (mini historias) ----------------------------

    @Test
    fun scenarioStaysStableBetweenIntroAndFeedbackForOneQuestion() {
        for (key in animalKeys) {
            val bank = newBank()
            val name = key.name
            bank.getQuestionIntroduction(name)
            val scenarioId = bank.currentScenarioId(name)
            assertNotNull(scenarioId)
            // Todas las categorias de feedback de esta pregunta deben mantener el id.
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
            // Varias semillas para cubrir distintos escenarios elegidos.
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

    @Test
    fun catLostVoiceScenario_neverMixesWithCatWindowFeedback() {
        // Si la intro pertenece a CAT_LOST_VOICE, su feedback no puede hablar de la
        // ventana; y si pertenece a CAT_WINDOW, su feedback no puede hablar de recuperar
        // la voz. Recorremos muchas semillas para forzar ambos escenarios.
        val key = LocalMediationKey.ANIMAL_CAT_SOUND.name
        for (seed in 1L..60L) {
            val bank = newBank(seed)
            bank.getQuestionIntroduction(key)
            val scenarioId = bank.currentScenarioId(key)
            val correct = normalize(bank.getCorrectFeedback(key))
            val next = normalize(bank.getIncorrectNextFeedback(key, isLastQuestion = false))
            when (scenarioId) {
                "CAT_LOST_VOICE" -> {
                    assertFalse("LOST_VOICE mezclado con ventana: $correct", correct.contains("ventana"))
                    assertFalse("LOST_VOICE mezclado con ventana: $next", next.contains("ventana"))
                }
                "CAT_WINDOW" -> {
                    assertFalse("WINDOW mezclado con recuperar voz: $correct", correct.contains("recupero su voz") || correct.contains("recupero su miau"))
                }
            }
        }
    }

    // ----- Reglas de la ultima pregunta ----------------------------------------

    @Test
    fun incorrectNext_onLastQuestion_neverUsesContinuationPhrase() {
        val continuation = listOf("continuemos", "continuar", "siguiente", "pasemos", "sigamos", "vamos con otra")
        for (key in animalKeys) {
            val bank = newBank()
            repeat(120) {
                val text = normalize(bank.getIncorrectNextFeedback(key.name, isLastQuestion = true))
                assertTrue("Frase vacia", text.isNotBlank())
                for (marker in continuation) {
                    assertFalse(
                        "En la ultima pregunta ($key) se uso continuidad: $text",
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
                assertTrue("Frase vacia", text.isNotBlank())
                for (marker in continuation) {
                    assertFalse(
                        "En la ultima pregunta ($key) el acierto uso continuidad: $text",
                        text.contains(marker)
                    )
                }
            }
        }
    }

    @Test
    fun noLastQuestionPhrase_announcesNextQuestion() {
        // Ninguna frase reproducible en la ultima pregunta debe decir "siguiente pregunta".
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
            assertTrue("Frase vacia", text.isNotBlank())
        }
    }

    // ----- Seleccion variada ---------------------------------------------------

    @Test
    fun doesNotRepeatSameIntroductionConsecutively() {
        for (key in animalKeys) {
            val bank = newBank()
            var previous: String? = null
            repeat(200) {
                val current = bank.getQuestionIntroduction(key.name)
                assertTrue("Introduccion repetida consecutiva en $key: $current", current != previous)
                previous = current
            }
        }
    }

    @Test
    fun doesNotRepeatSameCorrectFeedbackConsecutivelyWithinAScenario() {
        // Forzamos un unico escenario por clave para comprobar la anti-repeticion del
        // feedback dentro del mismo escenario (lo que escucha el nino entre intentos).
        for (key in animalKeys) {
            val onlyFirst = mapOf(key to listOf(AnimalMediationBank.SCENARIOS.getValue(key).first()))
            val bank = AnimalMediationBank(random = Random(7L), scenarios = onlyFirst)
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
        // La variedad de inicios reduce la repeticion percibida: a lo largo de muchas
        // respuestas correctas, "lo lograste" no debe dominar los comienzos.
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
                    assertFalse("Frase culpa al nino: $phrase", text.contains(marker))
                }
            }
        }
    }
}
