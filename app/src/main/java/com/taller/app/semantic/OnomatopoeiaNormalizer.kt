package com.taller.app.semantic

import java.text.Normalizer

/**
 * MED01-FIX01: tolerancia local y controlada para sonidos/onomatopeyas infantiles.
 *
 * El reconocimiento de voz transcribe mal las onomatopeyas de niños pequeños
 * ("guau" suele llegar como "wow", "wau" o "woof"). Esta utilidad reconoce esas
 * variantes SOLO cuando el contexto lo justifica:
 *  - la respuesta de referencia ya es una onomatopeya conocida ("guau", "miau"), o
 *  - la pregunta pide el sonido del animal correspondiente.
 *
 * Nunca acepta de forma global: "miau" jamás equivale a "guau", "wow" jamás equivale
 * a "miau", y una palabra cualquiera ("mesa") no se acepta como sonido. Es lógica
 * pura, sin Android y sin red: solo compara texto ya transcrito.
 */
object OnomatopoeiaNormalizer {

    /**
     * Resultado del análisis de una respuesta frente a la onomatopeya esperada.
     *
     * @property matchedAlias la respuesta es una variante válida del sonido esperado.
     * @property aliasReason motivo corto y seguro para el reporte técnico, o null.
     * @property contradictorySound la respuesta es claramente OTRO sonido conocido
     *   (por ejemplo "miau" cuando se pedía "guau"): caso cerrado, no dudoso.
     */
    data class Match(
        val normalizedAnswer: String,
        val normalizedExpected: String,
        val matchedAlias: Boolean,
        val aliasReason: String?,
        val contradictorySound: Boolean
    )

    private data class SoundGroup(
        val canonical: String,
        val aliases: Set<String>,
        val animalMarkers: Set<String>
    )

    private val GROUPS = listOf(
        SoundGroup(
            canonical = "guau",
            aliases = setOf(
                "guau", "guao", "guaf", "guauf", "wau", "waw", "wow", "wao", "woof", "wof"
            ),
            animalMarkers = setOf("perro", "perrito", "perros", "perrita", "cachorro", "cachorrito")
        ),
        SoundGroup(
            canonical = "miau",
            aliases = setOf(
                "miau", "miaw", "miao", "meow", "meaw", "mau", "meu", "niau"
            ),
            animalMarkers = setOf("gato", "gatito", "gatos", "gatita", "minino", "michi")
        ),
        // MED02: sonidos seguros adicionales. Solo variantes claras y poco ambiguas.
        SoundGroup(
            canonical = "muu",
            aliases = setOf("mu", "muu", "muuu", "moo", "muh", "muuh"),
            animalMarkers = setOf("vaca", "vaquita", "vacas", "ternero", "becerro")
        ),
        SoundGroup(
            canonical = "cuac",
            aliases = setOf("cuac", "cua", "cuacuac", "cuak", "quack", "cuc"),
            animalMarkers = setOf("pato", "patito", "patos", "pata", "patos")
        ),
        SoundGroup(
            canonical = "bee",
            aliases = setOf("be", "bee", "beee", "mee", "meee", "baa"),
            animalMarkers = setOf("oveja", "ovejita", "ovejas", "borrego", "cordero", "corderito")
        ),
        // FINAL-FLOW01: gallina/pollo con variantes claras y poco ambiguas. Se evita
        // "coco" (fruta) y otras formas que el STT confunde con palabras reales.
        SoundGroup(
            canonical = "pio pio",
            aliases = setOf(
                "pio", "piopio", "pi", "pipi", "clo", "cloclo", "cocoroco", "kokoroko", "coroco"
            ),
            animalMarkers = setOf(
                "gallina", "gallinita", "gallinas", "pollo", "pollito", "pollitos", "gallo"
            )
        )
        // Pendientes (menos seguros de aislar por STT): cerdo (oink/oinc). Se anaden
        // cuando haya datos reales que confirmen las transcripciones tipicas.
    )

    /** Conectores y artículos que se descartan para aislar el sonido. */
    private val STOPWORDS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "hace", "dice", "es", "son", "suena", "sonido", "y", "de", "del", "que"
    )

    private val SOUND_QUESTION_MARKERS = listOf(
        "que sonido hace", "que sonido", "como hace", "que dice", "sonido del",
        "sonido de la", "sonido de", "hace el perro", "hace el gato", "como suena"
    )

    /** Minúsculas, sin tildes, sin signos y con espacios simples. */
    fun normalizeBasic(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun soundTokens(text: String): Set<String> =
        normalizeBasic(text).split(" ")
            .filter { it.isNotBlank() && it !in STOPWORDS }
            .toSet()

    /** La pregunta pide el sonido de un animal. */
    fun isSoundQuestion(questionText: String): Boolean {
        val normalized = normalizeBasic(questionText)
        return SOUND_QUESTION_MARKERS.any { normalized.contains(it) }
    }

    /** La respuesta de referencia es una onomatopeya conocida ("guau", "miau"...). */
    fun expectedLooksLikeOnomatopoeia(expectedAnswer: String): Boolean {
        val tokens = soundTokens(expectedAnswer)
        return tokens.isNotEmpty() && GROUPS.any { group -> tokens.any { it in group.aliases } }
    }

    /** Heurística: respuesta corta probable de ser un sonido (1-2 tokens cortos). */
    fun answerLooksLikeShortSound(answer: String): Boolean {
        val tokens = soundTokens(answer)
        if (tokens.isEmpty() || tokens.size > 2) return false
        return tokens.all { it.length in 2..6 }
    }

    /**
     * Analiza [answer] frente a la onomatopeya esperada usando el contexto de
     * [expectedAnswer] y [questionText]. Solo activa el grupo de sonido si el
     * contexto lo justifica; de lo contrario no aplica ningún alias.
     */
    fun match(answer: String, expectedAnswer: String, questionText: String): Match {
        val normalizedAnswer = normalizeBasic(answer)
        val normalizedExpected = normalizeBasic(expectedAnswer)
        val answerTokens = soundTokens(answer)
        val expectedTokens = soundTokens(expectedAnswer)
        val normalizedQuestion = normalizeBasic(questionText)

        val target = GROUPS.firstOrNull { group ->
            expectedTokens.any { it in group.aliases } ||
                group.animalMarkers.any { marker -> normalizedQuestion.contains(marker) }
        } ?: return Match(
            normalizedAnswer = normalizedAnswer,
            normalizedExpected = normalizedExpected,
            matchedAlias = false,
            aliasReason = null,
            contradictorySound = false
        )

        val answerInTarget = answerTokens.isNotEmpty() && answerTokens.all { it in target.aliases }
        if (answerInTarget) {
            val spoken = answerTokens.joinToString(" ")
            return Match(
                normalizedAnswer = normalizedAnswer,
                normalizedExpected = normalizedExpected,
                matchedAlias = true,
                aliasReason = "alias de sonido: $spoken -> ${target.canonical}",
                contradictorySound = false
            )
        }

        val belongsToOtherSound = GROUPS.any { group ->
            group.canonical != target.canonical &&
                answerTokens.isNotEmpty() && answerTokens.all { it in group.aliases }
        }
        return Match(
            normalizedAnswer = normalizedAnswer,
            normalizedExpected = normalizedExpected,
            matchedAlias = false,
            aliasReason = null,
            contradictorySound = belongsToOtherSound
        )
    }
}
