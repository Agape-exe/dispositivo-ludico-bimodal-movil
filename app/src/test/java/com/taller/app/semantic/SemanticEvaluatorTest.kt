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
}
