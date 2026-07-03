package com.taller.app.semantic

import java.text.Normalizer

/**
 * MED02: equivalencias controladas del habla infantil, centralizadas y testeables.
 *
 * Es logica pura (sin Android, sin red) que decide si dos palabras representan el
 * MISMO concepto para un nino de 3 a 5 anos, tolerando variaciones seguras:
 *  - plural/singular simple ("ojos" / "ojo"),
 *  - diminutivos comunes ("perrito" -> "perro", "gatito" -> "gato"),
 *  - sinonimos infantiles muy frecuentes ("platano" / "banana", "bus" / "autobus").
 *
 * NO inventa categorias amplias: solo reconoce equivalencias cuando ambas palabras
 * caen en el mismo grupo definido aqui o son variantes de numero/diminutivo de la
 * misma base. La referencia de la docente sigue mandando: si una palabra no es
 * equivalente a la referencia o sus palabras clave, no se acepta.
 */
object ChildAnswerEquivalences {

    /** Articulos y conectores que no aportan al concepto y se descartan. */
    private val STOPWORDS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "con", "y", "o", "u", "es", "son", "hace", "dice",
        "mi", "su", "lo", "al", "a", "en"
    )

    /**
     * Grupos de sinonimos infantiles frecuentes. Cada palabra ya esta normalizada
     * (minusculas, sin tildes). Solo se reconocen equivalencias DENTRO de un grupo,
     * nunca entre grupos distintos. Mantener acotado y seguro.
     */
    private val EQUIVALENCE_GROUPS: List<Set<String>> = listOf(
        // Frutas / alimentos con nombres regionales.
        setOf("platano", "banana", "guineo"),
        // Transporte: nombres equivalentes del habla cotidiana.
        setOf("bus", "autobus", "omnibus", "colectivo"),
        setOf("carro", "auto", "coche", "automovil"),
        setOf("bici", "bicicleta"),
        setOf("moto", "motocicleta"),
        setOf("avion", "aeroplano"),
        // Formas redondas: pelota / circulo / esfera para ninos pequenos.
        setOf("circulo", "redondo", "redonda", "redondel", "esfera")
    )

    private val GROUP_INDEX: Map<String, Set<String>> = buildMap {
        EQUIVALENCE_GROUPS.forEach { group ->
            group.forEach { word -> put(word, group) }
        }
    }

    /** Minusculas, sin tildes, solo letras y numeros. */
    fun normalizeToken(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    /** Separa una frase normalizada en palabras de contenido (sin articulos). */
    fun contentTokens(text: String): List<String> {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in STOPWORDS }
    }

    /**
     * Variantes simples de numero (singular/plural) de una palabra ya normalizada.
     * No pretende cubrir toda la gramatica espanola: solo los casos regulares mas
     * comunes en respuestas infantiles ("ojo"/"ojos", "flor"/"flores").
     */
    fun numberVariants(token: String): Set<String> {
        if (token.length < 2) return setOf(token)
        val variants = mutableSetOf(token)
        if (token.endsWith("es") && token.length > 4) variants += token.dropLast(2)
        if (token.endsWith("s") && token.length > 3) variants += token.dropLast(1)
        variants += token + "s"
        if (!token.endsWith("s")) variants += token + "es"
        return variants
    }

    /**
     * Base sin diminutivo para los sufijos mas comunes (-ito/-ita/-itos/-itas).
     * Devuelve null si la palabra no parece un diminutivo regular. Otros diminutivos
     * (-cito, -illo) quedan pendientes por ser menos seguros de revertir.
     */
    fun strippedDiminutive(token: String): String? = when {
        token.endsWith("itos") && token.length > 5 -> token.dropLast(4) + "os"
        token.endsWith("itas") && token.length > 5 -> token.dropLast(4) + "as"
        token.endsWith("ito") && token.length > 4 -> token.dropLast(3) + "o"
        token.endsWith("ita") && token.length > 4 -> token.dropLast(3) + "a"
        else -> null
    }

    /**
     * Conjunto de formas equivalentes de una palabra: la propia, su base sin
     * diminutivo, los miembros de su grupo de sinonimos y las variantes de numero
     * de todas ellas. Sirve para comparar dos palabras por interseccion.
     */
    fun expand(raw: String): Set<String> {
        val token = normalizeToken(raw)
        if (token.isBlank()) return emptySet()

        val roots = mutableSetOf(token)
        strippedDiminutive(token)?.let { roots += it }

        val withSynonyms = roots.toMutableSet()
        roots.forEach { root -> GROUP_INDEX[root]?.let { withSynonyms += it } }

        val forms = mutableSetOf<String>()
        withSynonyms.forEach { forms += numberVariants(it) }
        return forms
    }

    /** True si [a] y [b] representan el mismo concepto infantil seguro. */
    fun areEquivalent(a: String, b: String): Boolean {
        val expandedA = expand(a)
        if (expandedA.isEmpty()) return false
        return expand(b).any { it in expandedA }
    }

    /**
     * True si alguna palabra de contenido de [answer] es equivalente a alguna de las
     * [options] (respuesta de referencia o palabras clave). Comparacion por concepto,
     * tolerando plural/singular, diminutivos y sinonimos infantiles seguros.
     */
    fun answerMatchesAnyOption(answer: String, options: List<String>): Boolean {
        val answerForms = contentTokens(answer)
            .flatMap { expand(it) }
            .toSet()
        if (answerForms.isEmpty()) return false
        return options.any { option ->
            contentTokens(option).any { optionToken ->
                expand(optionToken).any { it in answerForms }
            }
        }
    }

    /**
     * True si [text] menciona alguna variante obvia de [reference] (plural/singular,
     * diminutivo o sinonimo del mismo grupo). Lo usan los validadores de pistas para
     * evitar que una pista delate la respuesta aunque no la escriba textualmente.
     */
    fun textMentionsVariantOf(text: String, reference: String): Boolean {
        val textForms = contentTokens(text)
            .flatMap { expand(it) }
            .toSet()
        if (textForms.isEmpty()) return false
        return contentTokens(reference).any { refToken ->
            // Solo conceptos con sustancia: evita falsos positivos con palabras muy cortas.
            normalizeToken(refToken).length >= 3 &&
                expand(refToken).any { it in textForms }
        }
    }
}
