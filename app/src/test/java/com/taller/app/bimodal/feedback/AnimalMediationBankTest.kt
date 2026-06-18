package com.taller.app.bimodal.feedback

import com.taller.app.model.LocalMediationKey
import org.junit.Assert.assertFalse
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

    /** Todos los conjuntos de frases pre-aprobados del banco. */
    private fun allPhraseLists(): List<List<String>> {
        val general = listOf(
            AnimalMediationBank.MISSION_START,
            AnimalMediationBank.MISSION_COMPLETED,
            AnimalMediationBank.MICRO_DIALOGUES,
            AnimalMediationBank.GENERAL_NOT_INTERPRETABLE,
            AnimalMediationBank.GENERAL_NO_RESPONSE,
            AnimalMediationBank.GENERAL_TECHNICAL_ERROR
        )
        val keyed = listOf(
            AnimalMediationBank.INTRODUCTIONS,
            AnimalMediationBank.CORRECT_FEEDBACK,
            AnimalMediationBank.INCORRECT_RETRY,
            AnimalMediationBank.INCORRECT_NEXT
        ).flatMap { it.values }
        return general + keyed
    }

    // ----- Cobertura y variedad del banco --------------------------------------

    @Test
    fun everyPhraseList_hasAtLeastTenPhrases() {
        for (list in allPhraseLists()) {
            assertTrue(
                "Un conjunto tiene ${list.size} frases, se esperaban al menos 10",
                list.size >= 10
            )
        }
    }

    @Test
    fun everyAnimalKey_hasIntroductionsAndAllFeedbacks() {
        for (key in animalKeys) {
            assertTrue("Falta INTRODUCTIONS para $key", AnimalMediationBank.INTRODUCTIONS.containsKey(key))
            assertTrue("Falta CORRECT para $key", AnimalMediationBank.CORRECT_FEEDBACK.containsKey(key))
            assertTrue("Falta INCORRECT_RETRY para $key", AnimalMediationBank.INCORRECT_RETRY.containsKey(key))
            assertTrue("Falta INCORRECT_NEXT para $key", AnimalMediationBank.INCORRECT_NEXT.containsKey(key))
        }
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

    // ----- Resolucion por clave de mediacion -----------------------------------

    @Test
    fun animalKeys_returnTheirOwnIntroductionsAndFeedbacks() {
        for (key in animalKeys) {
            val bank = newBank()
            val name = key.name
            assertTrue(
                "La introduccion de $key no proviene de su banco",
                AnimalMediationBank.INTRODUCTIONS.getValue(key).contains(bank.getQuestionIntroduction(name))
            )
            assertTrue(
                "El feedback correcto de $key no proviene de su banco",
                AnimalMediationBank.CORRECT_FEEDBACK.getValue(key).contains(bank.getCorrectFeedback(name))
            )
            assertTrue(
                "El feedback de reintento de $key no proviene de su banco",
                AnimalMediationBank.INCORRECT_RETRY.getValue(key).contains(bank.getIncorrectRetryFeedback(name))
            )
        }
    }

    @Test
    fun noneKey_usesGeneralFallbackForContextualCategories() {
        val bank = newBank()
        // NONE no tiene frases propias de intro/correcto/reintento: deben provenir del
        // banco general tipo profesor, no de los conjuntos especificos de animales.
        val intro = bank.getQuestionIntroduction(LocalMediationKey.NONE.name)
        assertTrue(
            "La intro general no deberia ser una intro de animales",
            AnimalMediationBank.INTRODUCTIONS.values.none { it.contains(intro) }
        )
        assertTrue(
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.QUESTION_INTRO).contains(intro)
        )

        val correct = bank.getCorrectFeedback(LocalMediationKey.NONE.name)
        assertTrue(
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES
                .getValue(GeneralTeacherFeedbackType.CORRECT).contains(correct)
        )
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

    // ----- Reglas de la ultima pregunta ----------------------------------------

    @Test
    fun incorrectNext_onLastQuestion_neverUsesContinuationPhrase() {
        val continuation = listOf("continuemos", "continuar", "siguiente", "pasemos", "sigamos", "vamos con otra")
        for (key in animalKeys) {
            val bank = newBank()
            repeat(80) {
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
    fun incorrectNext_notLastQuestion_mayMentionCorrectAnswer() {
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
    fun generalPhrases_neverBlameChild() {
        // "tu culpa" aparece solo en reconfortantes ("No fue tu culpa"), por lo que no
        // se incluye como marcador de reproche.
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
