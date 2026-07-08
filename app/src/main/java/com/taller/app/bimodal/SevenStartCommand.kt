package com.taller.app.bimodal

import java.text.Normalizer

/**
 * FINAL-FLOW01 / FINAL-FLOW01-FIX01: reconoce la frase de activacion del modo
 * inteligente de forma confiable para ninos pequenos.
 *
 * La activacion principal es natural: "Hola Seven" y variantes simples ("oye
 * Seven", "Seven" solo, "hola amigo"). Ademas tolera los errores tipicos del
 * reconocimiento de voz infantil ("seben", "se ven", "ola" por "hola"), sin
 * distinguir mayusculas, tildes ni espacios extra. La frase antigua "Seven
 * empieza" (y variantes) se mantiene por compatibilidad.
 *
 * Es logica pura sin Android: solo analiza texto ya transcrito dentro del
 * mecanismo de escucha existente. No implementa hotword continuo. La pantalla
 * ademas ofrece un boton manual de inicio para cuando el STT falla.
 */
object SevenStartCommand {

    /** Texto que la interfaz muestra como instruccion principal. */
    const val PRIMARY_HINT = "Di: Hola Seven"

    /** Saludos (y sus errores comunes de STT) que, junto al nombre, activan. */
    private val GREETING_WORDS = setOf("hola", "ola", "oye", "hey", "holi")

    /**
     * Variantes de "Seven" tal como suele transcribirlas el STT con ninos pequenos.
     * "se ven" / "se ben" se manejan aparte porque llegan como dos palabras.
     */
    private val SEVEN_WORDS = setOf(
        "seven", "seben", "seuen", "sefen", "seiven", "sven", "sebem", "sebven", "seaven"
    )

    /** Verbos de inicio que, junto al nombre, activan aunque la frase sea larga. */
    private val START_VERBS = setOf(
        "empieza", "empezar", "empecemos", "empezamos", "comencemos", "comienza",
        "comenzar", "empiece", "arranca", "vamos"
    )

    /** Frases antiguas completas que se conservan por compatibilidad. */
    private val LEGACY_PHRASES = listOf(
        "seven empieza",
        "seven empezar",
        "seven comencemos",
        "seven empecemos"
    )

    /** Minusculas, sin tildes, sin signos y con espacios simples. */
    fun normalize(text: String): String {
        val withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return withoutMarks
            .lowercase()
            .replace("[^a-z0-9ñ ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Une los errores de STT que parten "seven" en dos palabras ("se ven", "se
     * ben") en un unico token "seven", para poder analizarlo como el nombre. No
     * altera otras apariciones de "se": solo cuando va seguido de "ven"/"ben".
     */
    private fun collapseSevenTokens(normalized: String): String =
        normalized.replace("\\bse (ven|ben|ban|fen)\\b".toRegex(), "seven")

    private fun isSevenWord(word: String): Boolean = word in SEVEN_WORDS

    /** True si [text] es una frase de activacion valida. Alias de [matches]. */
    fun isStartCommand(text: String): Boolean = matches(text)

    /** True si [text] es una frase de activacion valida. */
    fun matches(text: String): Boolean {
        val normalized = collapseSevenTokens(normalize(text))
        if (normalized.isEmpty()) return false

        // Compatibilidad: "Seven empieza" y variantes siguen funcionando.
        if (LEGACY_PHRASES.any { normalized.contains(it) }) return true

        val words = normalized.split(" ").filter { it.isNotBlank() }
        val hasGreeting = words.any { it in GREETING_WORDS }
        val hasSeven = words.any { isSevenWord(it) }
        val hasStartVerb = words.any { it in START_VERBS }

        // "Seven" (o variante) como enunciado corto y directo: 1-2 palabras.
        if (hasSeven && words.size <= 2 && !hasOtherContentWords(words)) return true

        // Saludo + Seven: "hola seven", "oye seben", "hola se ven"...
        if (hasGreeting && hasSeven) return true

        // "Hola amigo" como variante simple de saludo directo al juguete.
        if (hasGreeting && words.contains("amigo")) return true

        // Frase larga con "seven" solo activa si trae un verbo de inicio claro.
        if (hasSeven && hasStartVerb) return true

        return false
    }

    /**
     * En un enunciado corto de 2 palabras, indica si la palabra que acompana a
     * "seven" es contenido ajeno (por ejemplo "seven bonitos"), lo que impide
     * tratarlo como activacion directa. Un saludo delante no cuenta como contenido
     * ajeno porque ese caso ya lo cubre la regla de saludo + Seven.
     */
    private fun hasOtherContentWords(words: List<String>): Boolean =
        words.any { !isSevenWord(it) && it !in GREETING_WORDS }
}
