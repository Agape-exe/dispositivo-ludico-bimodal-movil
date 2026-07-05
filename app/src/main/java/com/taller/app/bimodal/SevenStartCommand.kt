package com.taller.app.bimodal

import java.text.Normalizer

/**
 * FINAL-FLOW01: reconoce la frase de activacion del modo inteligente.
 *
 * La activacion principal es natural: "Hola Seven" y variantes simples ("oye
 * Seven", "Seven" solo, "hola amigo"), sin distinguir mayusculas, tildes ni
 * espacios extra. La frase antigua "Seven empieza" (y sus variantes) se mantiene
 * por compatibilidad, aunque ya no se muestra como principal.
 *
 * Es logica pura sin Android: solo analiza texto ya transcrito dentro del
 * mecanismo de escucha existente. No implementa hotword continuo.
 */
object SevenStartCommand {

    /** Texto que la interfaz muestra como instruccion principal. */
    const val PRIMARY_HINT = "Di: Hola Seven"

    /** Saludos que, junto al nombre, activan a Seven ("hola seven", "oye seven"). */
    private val GREETING_WORDS = setOf("hola", "oye", "hey", "ola")

    /** Frases antiguas que se conservan por compatibilidad. */
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

    /** True si [text] es una frase de activacion valida. */
    fun matches(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return false
        val words = normalized.split(" ").filter { it.isNotBlank() }

        // Compatibilidad: "Seven empieza" y variantes siguen funcionando.
        if (LEGACY_PHRASES.any { normalized.contains(it) }) return true

        // "Seven" solo, como palabra completa y sin nada mas alrededor.
        if (words.size == 1 && words.first() == "seven") return true

        // Saludo + Seven: "hola seven", "oye seven", "hola hola seven"...
        if (words.contains("seven") && words.any { it in GREETING_WORDS }) return true

        // "Hola amigo" como variante simple de saludo directo al juguete.
        if (words.contains("amigo") && words.any { it in GREETING_WORDS }) return true

        return false
    }
}
