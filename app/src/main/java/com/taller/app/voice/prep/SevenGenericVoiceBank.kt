package com.taller.app.voice.prep

/**
 * Banco generico de frases de Seven, reutilizable entre todas las sesiones.
 *
 * Son frases neutras que no dependen del tema de la actividad: saludos, espera,
 * "te escucho", recaptura de atencion, apoyo cuando no responde, transiciones,
 * cierre neutro, error suave y calma. Estan pensadas para ninos de 3 a 5 anos:
 * calidas, variadas, sin culpar, sin sonar a examen y sin revelar respuestas.
 *
 * Es un conjunto FINITO y deterministico para que pueda cachearse una sola vez y
 * reproducirse desde la cache durante las sesiones reales. Comparte la linea
 * editorial del banco tipo profesor
 * ([com.taller.app.bimodal.feedback.GeneralTeacherFeedbackGenerator]); aqui se
 * mantiene una seleccion fija y sin estado para que la lista a sintetizar sea
 * estable. La revision fina de estas frases corresponde a VOZ01.
 */
object SevenGenericVoiceBank {

    /** Lista finita y estable de frases genericas a pre-generar una sola vez. */
    val phrases: List<String> = listOf(
        // Saludo general
        "Hola, soy Seven. Vamos a jugar y aprender juntos.",
        "Que alegria verte. Empecemos.",
        // Transicion entre preguntas
        "Muy bien, sigamos.",
        "Vamos con otra.",
        "Continuemos con calma.",
        // Te escucho
        "Te escucho.",
        "Cuando quieras, dime tu respuesta.",
        // Pensamiento / espera
        "Estoy pensando.",
        "Dame un momentito.",
        // Recaptura de atencion
        "Aqui estoy, mirame.",
        "Volvamos a mirar juntos.",
        // Apoyo cuando no responde
        "No te preocupes, tomate tu tiempo.",
        "Cuando estes listo, te escucho.",
        // Cierre neutro
        "Gracias por jugar conmigo.",
        "Lo hicimos muy bien. Hasta pronto.",
        // Error suave / intentemos otra vez
        "Hubo un pequeno problema. Intentemos otra vez.",
        "Probemos de nuevo, sin apuro.",
        // Frase de calma
        "Respira tranquilo, todo esta bien.",
        "Estamos bien, sigamos jugando."
    )

    /** Frases genericas convertidas a [VoiceLine] con rol [VoiceLineRole.GENERIC]. */
    fun lines(): List<VoiceLine> = phrases.map { VoiceLine(VoiceLineRole.GENERIC, it) }
}
