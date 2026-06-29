package com.taller.app.semantic

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SemanticEvaluatorTest {

    private lateinit var evaluator: SemanticEvaluator
    private val expectedAnswer = "gato"
    private val keywords = listOf("gato", "gatito", "miau")

    @Before
    fun setUp() {
        evaluator = SemanticEvaluator()
    }

    private fun eval(input: String) = evaluator.evaluate(input, expectedAnswer, keywords)

    @Test fun emptyInput_returnsNoResponse() =
        assertEquals(SemanticResult.NO_RESPONSE, eval(""))

    @Test fun blankSpaces_returnsNoResponse() =
        assertEquals(SemanticResult.NO_RESPONSE, eval("   "))

    @Test fun exactMatch_returnsCorrect() =
        assertEquals(SemanticResult.CORRECT, eval("gato"))

    @Test fun uppercaseMatch_returnsCorrect() =
        assertEquals(SemanticResult.CORRECT, eval("GATO"))

    @Test fun accentedMatch_returnsCorrect() =
        assertEquals(SemanticResult.CORRECT, eval("gáto"))

    @Test fun phraseWithExpected_returnsCorrect() =
        assertEquals(SemanticResult.CORRECT, eval("el gato."))

    @Test fun keywordGatito_returnsCorrect() =
        assertEquals(SemanticResult.CORRECT, eval("un gatito"))

    @Test fun keywordMiau_returnsCorrect() =
        assertEquals(SemanticResult.CORRECT, eval("hace miau"))

    @Test fun wrongAnswer_returnsIncorrect() =
        assertEquals(SemanticResult.INCORRECT, eval("perro"))

    @Test fun noSe_returnsNotInterpretable() =
        assertEquals(SemanticResult.NOT_INTERPRETABLE, eval("no sé"))

    @Test fun nose_returnsNotInterpretable() =
        assertEquals(SemanticResult.NOT_INTERPRETABLE, eval("nose"))

    @Test fun mmm_returnsNotInterpretable() =
        assertEquals(SemanticResult.NOT_INTERPRETABLE, eval("mmm"))

    @Test fun singleLetter_returnsNotInterpretable() =
        assertEquals(SemanticResult.NOT_INTERPRETABLE, eval("p"))

    @Test fun symbolsOnly_returnsNotInterpretable() =
        assertEquals(SemanticResult.NOT_INTERPRETABLE, eval("???"))

    @Test fun extraSpaces_returnsCorrect() =
        assertEquals(SemanticResult.CORRECT, eval("   el    gato   "))

    // ----- Regresion: coincidencia por palabra completa, no por subcadena --------

    @Test fun expectedAsSubstringOfLongerWord_returnsIncorrect() {
        // "gato" es subcadena de "gatoplancton" pero no la palabra "gato".
        assertEquals(SemanticResult.INCORRECT, eval("gatoplancton"))
    }

    @Test fun keywordAsSubstringOfLongerWord_returnsIncorrect() {
        val e = SemanticEvaluator()
        // La palabra clave "uno" no debe coincidir dentro de "ninguno".
        assertEquals(
            SemanticResult.INCORRECT,
            e.evaluate("ninguno", expectedAnswer = "uno", keywords = listOf("uno"))
        )
    }

    @Test fun colorAsSubstring_returnsIncorrect() {
        val e = SemanticEvaluator()
        // "azul" no debe coincidir dentro de "azulejo".
        assertEquals(
            SemanticResult.INCORRECT,
            e.evaluate("azulejo", expectedAnswer = "azul", keywords = listOf("azul", "celeste"))
        )
    }

    // ----- Regresion: respuesta esperada / palabras clave en blanco --------------

    @Test fun blankExpectedAnswer_doesNotMakeEverythingCorrect() {
        val e = SemanticEvaluator()
        // Con respuesta esperada vacia y sin palabras clave utiles, una respuesta
        // cualquiera NO debe contarse como correcta (antes contains("") == true).
        assertEquals(
            SemanticResult.INCORRECT,
            e.evaluate("cualquier cosa", expectedAnswer = "", keywords = emptyList())
        )
    }

    @Test fun blankKeyword_doesNotMakeEverythingCorrect() {
        val e = SemanticEvaluator()
        assertEquals(
            SemanticResult.INCORRECT,
            e.evaluate("cualquier cosa", expectedAnswer = "perro", keywords = listOf("", "  "))
        )
    }

    @Test fun blankExpectedButValidKeyword_matchesByKeyword() {
        val e = SemanticEvaluator()
        assertEquals(
            SemanticResult.CORRECT,
            e.evaluate("es un gato", expectedAnswer = "", keywords = listOf("gato"))
        )
    }

    // ----- MED01: referencia tipo lista / ejemplos validos -----------------------

    @Test fun listReference_acceptsFirstItemWithArticle() {
        val e = SemanticEvaluator()
        // "el perro" debe aceptarse contra "Perro, gato, hamster." (regresion MED01).
        assertEquals(
            SemanticResult.CORRECT,
            e.evaluate("el perro", expectedAnswer = "Perro, gato, hamster.", keywords = emptyList())
        )
    }

    @Test fun listReference_acceptsMiddleItemWithArticle() {
        val e = SemanticEvaluator()
        assertEquals(
            SemanticResult.CORRECT,
            e.evaluate("el gato", expectedAnswer = "Perro, gato, hamster.", keywords = emptyList())
        )
    }

    @Test fun listReference_acceptsAccentedItem() {
        val e = SemanticEvaluator()
        // "hamster" debe aceptar "hámster" (tildes normalizadas).
        assertEquals(
            SemanticResult.CORRECT,
            e.evaluate("un hámster", expectedAnswer = "Perro, gato, hamster.", keywords = emptyList())
        )
    }

    @Test fun listReference_rejectsUnrelatedAnswer() {
        val e = SemanticEvaluator()
        assertEquals(
            SemanticResult.INCORRECT,
            e.evaluate("el avion", expectedAnswer = "Perro, gato, hamster.", keywords = emptyList())
        )
    }

    @Test fun listReference_acceptsItemSeparatedByConjunction() {
        val e = SemanticEvaluator()
        assertEquals(
            SemanticResult.CORRECT,
            e.evaluate("la gallina", expectedAnswer = "vaca, caballo y gallina", keywords = emptyList())
        )
    }

    @Test fun referenceLooksLikeList_detectsEnumeration() {
        val e = SemanticEvaluator()
        assertEquals(true, e.referenceLooksLikeList("Perro, gato, hamster."))
        assertEquals(true, e.referenceLooksLikeList("vaca o gallina"))
        assertEquals(false, e.referenceLooksLikeList("perro"))
        assertEquals(false, e.referenceLooksLikeList(""))
    }
}
