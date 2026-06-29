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
 * estable.
 */
object SevenGenericVoiceBank {

    /** Lista finita y estable de frases genericas a pre-generar una sola vez. */
    val phrases: List<String> = listOf(
        // Saludo general
        "Hola, soy Seven. Vamos a jugar y aprender juntos.",
        "Mi antenita está lista para aprender contigo.",
        "¡Qué alegría verte! Empecemos esta aventura.",
        // Transicion entre preguntas
        "Vamos con otra aventura pequeñita.",
        "Sigamos explorando juntos.",
        "Continuemos con calma.",
        "Tengo otro reto de la Tierra para ti.",
        // Te escucho / turno del nino
        "Te escucho con mis orejitas espaciales.",
        "Ahora es tu turno, dime con calma.",
        "Cuando quieras, dime tu respuesta.",
        // Pensamiento / espera
        "Estoy pensando con mi antenita.",
        "Dame un momentito, estoy revisando.",
        "Déjame ordenar mis estrellas.",
        // Volver a la aventura (suave, sin ordenes duras)
        "Aquí estoy, sigamos juntos.",
        "Volvamos a la aventura, explorador.",
        // Apoyo cuando no responde
        "No te preocupes, tómate tu tiempo.",
        "Cuando estés listo, te escucho.",
        "Sin apuro, aquí te espero.",
        // Cierre neutro
        "Gracias por jugar conmigo.",
        "Lo pasamos muy bien. ¡Hasta pronto!",
        "Gracias por acompañarme en la aventura.",
        // Error suave / intentemos otra vez
        "Uy, mi nave hizo un ruidito. Probemos otra vez.",
        "Mi antenita se confundió un poquito.",
        "Algo pasó en mi nave, pero seguimos tranquilos.",
        // Frase de calma
        "Respira tranquilo, todo está bien.",
        "Estamos bien, sigamos jugando."
    )

    /** Frases genericas convertidas a [VoiceLine] con rol [VoiceLineRole.GENERIC]. */
    fun lines(): List<VoiceLine> = phrases.map { VoiceLine(VoiceLineRole.GENERIC, it) }
}
