package com.taller.app.semantic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildAnswerEquivalencesTest {

    // ----- Plural / singular -----------------------------------------------------

    @Test fun singularMatchesPlural() = assertTrue(ChildAnswerEquivalences.areEquivalent("ojo", "ojos"))

    @Test fun pluralMatchesSingular() = assertTrue(ChildAnswerEquivalences.areEquivalent("manos", "mano"))

    @Test fun unrelatedWordsAreNotEquivalent() =
        assertFalse(ChildAnswerEquivalences.areEquivalent("mesa", "perro"))

    // ----- Diminutivos -----------------------------------------------------------

    @Test fun diminutiveMatchesBase() = assertTrue(ChildAnswerEquivalences.areEquivalent("perrito", "perro"))

    @Test fun diminutiveCatMatchesBase() = assertTrue(ChildAnswerEquivalences.areEquivalent("gatito", "gato"))

    @Test fun pluralDiminutiveMatchesBase() =
        assertTrue(ChildAnswerEquivalences.areEquivalent("perritos", "perro"))

    @Test fun diminutiveDoesNotMatchOtherConcept() =
        assertFalse(ChildAnswerEquivalences.areEquivalent("gatito", "perro"))

    // ----- Sinonimos infantiles --------------------------------------------------

    @Test fun bananaMatchesPlatano() = assertTrue(ChildAnswerEquivalences.areEquivalent("banana", "platano"))

    @Test fun busMatchesAutobus() = assertTrue(ChildAnswerEquivalences.areEquivalent("bus", "autobús"))

    @Test fun redondaMatchesCirculo() = assertTrue(ChildAnswerEquivalences.areEquivalent("redonda", "círculo"))

    @Test fun esferaMatchesCirculo() = assertTrue(ChildAnswerEquivalences.areEquivalent("esfera", "circulo"))

    @Test fun differentTransportNotEquivalent() =
        assertFalse(ChildAnswerEquivalences.areEquivalent("bus", "carro"))

    // ----- answerMatchesAnyOption ------------------------------------------------

    @Test fun answerWithArticleMatchesOption() =
        assertTrue(ChildAnswerEquivalences.answerMatchesAnyOption("el perrito", listOf("perro", "gato")))

    @Test fun answerDoesNotMatchUnrelatedOptions() =
        assertFalse(ChildAnswerEquivalences.answerMatchesAnyOption("mesa", listOf("perro", "gato")))

    // ----- textMentionsVariantOf (validacion de pistas) --------------------------

    @Test fun hintMentioningDiminutiveLeaksAnswer() =
        assertTrue(ChildAnswerEquivalences.textMentionsVariantOf("piensa en un perrito", "perro"))

    @Test fun hintMentioningPluralLeaksAnswer() =
        assertTrue(ChildAnswerEquivalences.textMentionsVariantOf("hay muchos perros", "perro"))

    @Test fun generalHintDoesNotLeakAnswer() =
        assertFalse(
            ChildAnswerEquivalences.textMentionsVariantOf(
                "es un animalito que mueve la cola",
                "perro"
            )
        )
}
