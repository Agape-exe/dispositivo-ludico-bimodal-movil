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

    // ----- MED01-FIX01: tolerancia de sonidos / onomatopeyas ---------------------

    private val dogQuestion = "¿Qué sonido hace el perro?"
    private val catQuestion = "¿Qué sonido hace el gato?"

    private fun evalSound(answer: String, expected: String, question: String) =
        SemanticEvaluator().evaluate(answer, expectedAnswer = expected, keywords = emptyList(), questionText = question)

    @Test fun dogSound_guau_isCorrect() =
        assertEquals(SemanticResult.CORRECT, evalSound("guau", "guau", dogQuestion))

    @Test fun dogSound_guauGuau_isCorrect() =
        assertEquals(SemanticResult.CORRECT, evalSound("guau guau", "guau", dogQuestion))

    @Test fun dogSound_wow_isCorrect() =
        assertEquals(SemanticResult.CORRECT, evalSound("wow", "guau", dogQuestion))

    @Test fun dogSound_wowWow_isCorrect() =
        assertEquals(SemanticResult.CORRECT, evalSound("wow wow", "guau", dogQuestion))

    @Test fun dogSound_wau_isCorrect() =
        assertEquals(SemanticResult.CORRECT, evalSound("wau", "guau", dogQuestion))

    @Test fun dogSound_woof_isCorrect() =
        assertEquals(SemanticResult.CORRECT, evalSound("woof", "guau", dogQuestion))

    @Test fun dogSound_miau_isIncorrect() =
        assertEquals(SemanticResult.INCORRECT, evalSound("miau", "guau", dogQuestion))

    @Test fun catSound_meow_isCorrect() =
        assertEquals(SemanticResult.CORRECT, evalSound("meow", "miau", catQuestion))

    @Test fun catSound_wow_isIncorrect() =
        assertEquals(SemanticResult.INCORRECT, evalSound("wow", "miau", catQuestion))

    @Test fun dogSound_unrelatedWord_isIncorrect() =
        assertEquals(SemanticResult.INCORRECT, evalSound("mesa", "guau", dogQuestion))

    @Test
    fun dogSound_wow_reportsAliasMetadata() {
        val detailed = SemanticEvaluator().evaluateDetailed(
            transcription = "wow",
            expectedAnswer = "guau",
            keywords = emptyList(),
            questionText = dogQuestion
        )
        assertEquals(SemanticResult.CORRECT, detailed.result)
        assertEquals(true, detailed.aliasApplied)
        assertEquals(true, detailed.aliasReason?.contains("guau"))
    }

    @Test
    fun soundAlias_notAppliedWithoutContext() {
        // Sin contexto de sonido ni referencia onomatopeyica, "wow" no se acepta.
        assertEquals(
            SemanticResult.INCORRECT,
            SemanticEvaluator().evaluate("wow", expectedAnswer = "azul", keywords = emptyList(), questionText = "¿De qué color es el cielo?")
        )
    }

    // ----- MED02: equivalencias infantiles (plural/singular, diminutivos, sinonimos)

    private fun e() = SemanticEvaluator()

    @Test fun diminutive_perrito_matchesPerro() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("el perrito", "perro", emptyList()))

    @Test fun diminutive_gatito_matchesGato() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("un gatito", "gato", emptyList()))

    @Test fun pluralAnswer_matchesSingularReference() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("ojos", "ojo", emptyList()))

    @Test fun singularAnswer_matchesPluralReference() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("ojo", "ojos", emptyList()))

    @Test fun synonym_banana_matchesPlatano() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("una banana", "plátano", emptyList()))

    @Test fun synonym_autobus_matchesBus() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("el autobús", "bus", emptyList()))

    @Test fun synonym_redonda_matchesCirculo() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("redonda", "círculo", emptyList()))

    @Test fun equivalence_reportsEquivalenceType() {
        val detailed = e().evaluateDetailed("el perrito", "perro", emptyList())
        assertEquals(SemanticResult.CORRECT, detailed.result)
        assertEquals(LocalAcceptanceType.EQUIVALENCE, detailed.acceptanceType)
    }

    @Test fun reference_reportsReferenceType() {
        val detailed = e().evaluateDetailed("perro", "perro", emptyList())
        assertEquals(LocalAcceptanceType.REFERENCE, detailed.acceptanceType)
    }

    // ----- MED02: animales (listas de referencia) --------------------------------

    private val pets = "perro, gato, conejo, hámster, pez"
    private val farm = "vaca, gallina, cerdo, pato, caballo, oveja"

    @Test fun pet_perro_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("perro", pets, emptyList()))
    @Test fun pet_gato_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("el gato", pets, emptyList()))
    @Test fun pet_conejo_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("un conejo", pets, emptyList()))
    @Test fun pet_mesa_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("mesa", pets, emptyList()))

    @Test fun farm_vaca_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("la vaca", farm, emptyList()))
    @Test fun farm_gallina_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("gallina", farm, emptyList()))
    @Test fun farm_pato_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("un pato", farm, emptyList()))
    @Test fun farm_lapiz_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("lápiz", farm, emptyList()))

    // ----- MED02: sonidos de animales --------------------------------------------

    private fun sound(answer: String, expected: String, question: String) =
        e().evaluate(answer, expected, emptyList(), question)

    private val cowQuestion = "¿Qué sonido hace la vaca?"
    private val duckQuestion = "¿Qué sonido hace el pato?"

    @Test fun cowSound_muu_isCorrect() = assertEquals(SemanticResult.CORRECT, sound("muu", "muu", cowQuestion))
    @Test fun cowSound_mu_isCorrect() = assertEquals(SemanticResult.CORRECT, sound("mu", "muu", cowQuestion))
    @Test fun cowSound_guau_isIncorrect() = assertEquals(SemanticResult.INCORRECT, sound("guau", "muu", cowQuestion))

    @Test fun duckSound_cuac_isCorrect() = assertEquals(SemanticResult.CORRECT, sound("cuac", "cuac", duckQuestion))
    @Test fun duckSound_quack_isCorrect() = assertEquals(SemanticResult.CORRECT, sound("quack", "cuac", duckQuestion))
    @Test fun duckSound_cuaCua_isCorrect() = assertEquals(SemanticResult.CORRECT, sound("cua cua", "cuac", duckQuestion))
    @Test fun duckSound_miau_isIncorrect() = assertEquals(SemanticResult.INCORRECT, sound("miau", "cuac", duckQuestion))

    // ----- MED02: colores --------------------------------------------------------

    private val colors = "rojo, azul, amarillo, verde, rosado, morado"

    @Test fun color_rojo_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("rojo", colors, emptyList()))
    @Test fun color_azul_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("azul", colors, emptyList()))
    @Test fun color_verde_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("verde", colors, emptyList()))
    @Test fun color_perro_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("perro", colors, emptyList()))

    @Test fun skyColor_azul_isCorrect() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("azul", "azul, celeste", emptyList()))
    @Test fun skyColor_celeste_isCorrect() =
        assertEquals(SemanticResult.CORRECT, e().evaluate("celeste", "azul, celeste", emptyList()))
    @Test fun skyColor_gris_isNotAutoAccepted() =
        // "gris" no se acepta localmente sin contexto: queda para que decida el juez.
        assertEquals(SemanticResult.INCORRECT, e().evaluate("gris", "azul, celeste", emptyList()))

    // ----- MED02: formas ---------------------------------------------------------

    private val shapes = "círculo, cuadrado, triángulo, rectángulo"

    @Test fun shape_circulo_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("círculo", shapes, emptyList()))
    @Test fun shape_cuadrado_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("cuadrado", shapes, emptyList()))
    @Test fun shape_triangulo_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("triángulo", shapes, emptyList()))
    @Test fun shape_gato_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("gato", shapes, emptyList()))

    @Test fun ballShape_circulo_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("círculo", "círculo", emptyList()))
    @Test fun ballShape_redonda_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("redonda", "círculo", emptyList()))
    @Test fun ballShape_esfera_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("esfera", "círculo", emptyList()))
    @Test fun ballShape_cuadrado_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("cuadrado", "círculo", emptyList()))

    // ----- MED02: partes del cuerpo ----------------------------------------------

    private val body = "mano, pie, cabeza, ojo, nariz, boca"

    @Test fun body_mano_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("mano", body, emptyList()))
    @Test fun body_pie_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("el pie", body, emptyList()))
    @Test fun body_cabeza_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("cabeza", body, emptyList()))
    @Test fun body_avion_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("avión", body, emptyList()))

    @Test fun seeWith_ojos_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("ojos", "ojos", emptyList()))
    @Test fun seeWith_ojo_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("ojo", "ojos", emptyList()))
    @Test fun seeWith_conLosOjos_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("con los ojos", "ojos", emptyList()))
    @Test fun seeWith_orejas_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("orejas", "ojos", emptyList()))

    // ----- MED02: alimentos ------------------------------------------------------

    private val fruits = "manzana, plátano, banana, uva, pera, naranja, fresa"

    @Test fun fruit_manzana_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("manzana", fruits, emptyList()))
    @Test fun fruit_platano_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("plátano", fruits, emptyList()))
    @Test fun fruit_banana_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("banana", fruits, emptyList()))
    @Test fun fruit_uvas_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("uvas", fruits, emptyList()))
    @Test fun fruit_zapato_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("zapato", fruits, emptyList()))

    private val edible = "pan, arroz, sopa, fruta, manzana"

    @Test fun edible_pan_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("pan", edible, emptyList()))
    @Test fun edible_arroz_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("arroz", edible, emptyList()))
    @Test fun edible_sopa_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("sopa", edible, emptyList()))
    @Test fun edible_piedra_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("piedra", edible, emptyList()))

    // ----- MED02: transportes ----------------------------------------------------

    private val transports = "carro, bus, autobús, bicicleta, avión, tren, moto"

    @Test fun transport_carro_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("carro", transports, emptyList()))
    @Test fun transport_bus_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("el bus", transports, emptyList()))
    @Test fun transport_bicicleta_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("bicicleta", transports, emptyList()))
    @Test fun transport_avion_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("avión", transports, emptyList()))
    @Test fun transport_tren_isCorrect() = assertEquals(SemanticResult.CORRECT, e().evaluate("tren", transports, emptyList()))
    @Test fun transport_perro_isIncorrect() = assertEquals(SemanticResult.INCORRECT, e().evaluate("perro", transports, emptyList()))
}
