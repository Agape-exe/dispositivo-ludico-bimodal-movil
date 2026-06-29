package com.taller.app.voice

import com.taller.app.bimodal.feedback.AnimalMediationBank
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackGenerator
import com.taller.app.recapture.RecapturePhraseBank
import com.taller.app.voice.prep.SevenGenericVoiceBank
import com.taller.app.voice.prep.VoiceLineRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/**
 * Reglas de tono y seguridad (VOZ01) sobre los bancos genericos de frases de Seven.
 *
 * Verifica que las frases que Seven puede decir a un nino de 3 a 5 anos:
 *  - no lo culpan ("incorrecto", "mal", "fallaste", "te equivocaste", "no sabes"),
 *  - no suenan a adulto rigido ("presta atencion", "concentrate", "evaluacion",
 *    "respuesta valida", "soy una ia"),
 *  - son cortas (banco generico cacheable),
 *  - el banco generico es enumerable y sin duplicados para la pre-generacion de voz.
 */
class SevenGenericPhraseToneTest {

    /** Minusculas y sin acentos, para comparaciones robustas. */
    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
    }

    /** Palabras prohibidas como palabra completa (evita falsos positivos como "animal"). */
    private val forbiddenWholeWords = listOf(
        "incorrecto", "incorrecta", "mal", "malo", "mala",
        "fallaste", "equivocaste", "concentrate", "examen"
    )

    /** Expresiones prohibidas que se buscan como subcadena (multipalabra). */
    private val forbiddenFragments = listOf(
        "presta atencion", "no sabes", "te equivocaste", "respuesta valida",
        "soy una ia", "evaluacion", "respuesta esperada"
    )

    private fun assertSafeTone(label: String, phrases: Collection<String>) {
        for (phrase in phrases) {
            val normalized = normalize(phrase)
            for (word in forbiddenWholeWords) {
                assertTrue(
                    "[$label] usa la palabra prohibida '$word': $phrase",
                    !Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(normalized)
                )
            }
            for (fragment in forbiddenFragments) {
                assertTrue(
                    "[$label] contiene la expresion prohibida '$fragment': $phrase",
                    !normalized.contains(fragment)
                )
            }
        }
    }

    /** Todas las frases del banco narrativo de animales, enumeradas. */
    private fun allAnimalPhrases(): List<String> = buildList {
        addAll(AnimalMediationBank.MISSION_START)
        addAll(AnimalMediationBank.ADVANCED_INITIAL_FACE_GREETING)
        addAll(AnimalMediationBank.MISSION_COMPLETED)
        addAll(AnimalMediationBank.MICRO_DIALOGUES)
        addAll(AnimalMediationBank.ADVANCED_QUESTION_INTRO_GENERAL)
        addAll(AnimalMediationBank.GENERAL_NOT_INTERPRETABLE)
        addAll(AnimalMediationBank.GENERAL_NO_RESPONSE)
        addAll(AnimalMediationBank.GENERAL_TECHNICAL_ERROR)
        addAll(AnimalMediationBank.GENERIC_MISSION_START)
        addAll(AnimalMediationBank.GENERIC_MISSION_COMPLETED)
        AnimalMediationBank.SCENARIOS.values.flatten().forEach { scenario ->
            addAll(scenario.intro)
            addAll(scenario.correctFeedback)
            addAll(scenario.incorrectRetryFeedback)
            addAll(scenario.incorrectNextFeedback)
        }
    }

    // ----- Tono seguro por banco ------------------------------------------------

    @Test
    fun genericVoiceBank_hasSafeTone() {
        assertSafeTone("SevenGenericVoiceBank", SevenGenericVoiceBank.phrases)
    }

    @Test
    fun teacherFeedback_hasSafeTone() {
        GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES.forEach { (type, phrases) ->
            assertSafeTone("GeneralTeacherFeedback.$type", phrases)
        }
    }

    @Test
    fun animalBank_hasSafeTone() {
        assertSafeTone("AnimalMediationBank", allAnimalPhrases())
    }

    @Test
    fun recaptureBank_hasSafeTone() {
        assertSafeTone("RecapturePhraseBank", RecapturePhraseBank.allLocalPhrases())
    }

    // ----- Longitud razonable de las frases genericas cortas --------------------

    @Test
    fun shortGenericPhrases_stayWithinLengthLimit() {
        val maxLength = 120
        val shortBanks = buildList {
            SevenGenericVoiceBank.phrases.forEach { add("SevenGenericVoiceBank" to it) }
            GeneralTeacherFeedbackGenerator.DEFAULT_PHRASES.forEach { (type, phrases) ->
                phrases.forEach { add("GeneralTeacherFeedback.$type" to it) }
            }
        }
        for ((label, phrase) in shortBanks) {
            assertTrue(
                "[$label] frase demasiado larga (${phrase.length} > $maxLength): $phrase",
                phrase.length <= maxLength
            )
        }
    }

    // ----- Cacheabilidad: banco generico enumerable y sin duplicados ------------

    @Test
    fun genericVoiceBank_isEnumerableWithoutDuplicates() {
        val phrases = SevenGenericVoiceBank.phrases
        assertTrue("El banco generico debe tener variedad suficiente", phrases.size >= 20)
        val normalizedKeys = phrases.map { it.trim().replace(Regex("\\s+"), " ").lowercase() }
        assertEquals(
            "El banco generico no debe tener frases duplicadas",
            normalizedKeys.size,
            normalizedKeys.toSet().size
        )
    }

    @Test
    fun genericVoiceBank_linesCoverEveryPhraseAsGeneric() {
        val lines = SevenGenericVoiceBank.lines()
        assertEquals(SevenGenericVoiceBank.phrases.size, lines.size)
        assertTrue(
            "Todas las lineas del banco generico deben tener rol GENERIC",
            lines.all { it.role == VoiceLineRole.GENERIC }
        )
        // Cada frase del banco debe estar incluida como linea pre-generable.
        assertTrue(
            SevenGenericVoiceBank.phrases.all { phrase ->
                lines.any { it.text == phrase }
            }
        )
    }
}
