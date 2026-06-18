package com.taller.app.bimodal.feedback

import com.taller.app.bimodal.BimodalInteractionState
import kotlin.random.Random

/**
 * Generador de retroalimentacion general tipo profesor para el modo bimodal.
 *
 * A partir de un [GeneralTeacherFeedbackContext] decide la categoria adecuada y
 * elige una frase de un banco predefinido amplio. La seleccion es variada y
 * segura: nunca repite la misma frase de forma consecutiva dentro de una misma
 * categoria cuando hay mas de una disponible.
 *
 * Es independiente de Android y de la capa de voz: solo produce texto. La UI lo
 * envia a la capa comun de voz ([com.taller.app.voice.ToyVoiceFallback]), que
 * aplica el proveedor seleccionado (Azure) con respaldo a la voz local.
 *
 * Mantiene estado mutable (el ultimo indice elegido por categoria), por lo que
 * debe existir una instancia por sesion. El generador de aleatoriedad y el banco
 * de frases son inyectables para facilitar las pruebas.
 *
 * No usa IA generativa, no agrega campos a las preguntas y no produce
 * explicaciones especificas del contenido.
 */
class GeneralTeacherFeedbackGenerator(
    private val random: Random = Random.Default,
    private val phrases: Map<GeneralTeacherFeedbackType, List<String>> = DEFAULT_PHRASES
) {

    /** Ultimo indice de frase elegido por categoria, para evitar repeticiones seguidas. */
    private val lastIndexByType = HashMap<GeneralTeacherFeedbackType, Int>()

    /**
     * Decide la categoria de retroalimentacion para el contexto dado, o null si el
     * estado no requiere una frase (estados de transito o de espera tecnica).
     *
     * La variante "_RETRY" / "_NEXT" se decide con [GeneralTeacherFeedbackContext.canRetry]:
     * si el orquestador permite reintentar se anima a intentar de nuevo; si no,
     * se anima a continuar sin revelar la respuesta esperada.
     *
     * En la ultima pregunta ([GeneralTeacherFeedbackContext.isLastQuestion]) el
     * desenlace SI produce retroalimentacion: el nino debe escuchar el feedback de
     * su respuesta antes del cierre de la sesion. La regla de "no anunciar un avance
     * que no ocurrira" (no decir "continuemos", "pasemos a la siguiente") se aplica al
     * elegir la frase concreta —en el banco local de mediacion, que filtra las frases
     * de continuidad en la ultima pregunta—, no suprimiendo la categoria aqui. El
     * cierre lo aporta el estado SESSION_COMPLETED con su propia categoria, despues de
     * que la retroalimentacion termina de reproducirse.
     */
    fun feedbackTypeFor(context: GeneralTeacherFeedbackContext): GeneralTeacherFeedbackType? =
        when (context.state) {
            BimodalInteractionState.WAITING_FOR_FACE ->
                if (context.questionIndex == 0) GeneralTeacherFeedbackType.SESSION_START
                else GeneralTeacherFeedbackType.QUESTION_INTRO
            BimodalInteractionState.PRESENTING_QUESTION ->
                GeneralTeacherFeedbackType.QUESTION_INTRO
            BimodalInteractionState.FEEDBACK_CORRECT ->
                GeneralTeacherFeedbackType.CORRECT
            BimodalInteractionState.FEEDBACK_INCORRECT ->
                if (context.canRetry) GeneralTeacherFeedbackType.INCORRECT_RETRY
                else GeneralTeacherFeedbackType.INCORRECT_NEXT
            BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ->
                if (context.canRetry) GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY
                else GeneralTeacherFeedbackType.NOT_INTERPRETABLE_NEXT
            BimodalInteractionState.FEEDBACK_NO_RESPONSE ->
                if (context.canRetry) GeneralTeacherFeedbackType.NO_RESPONSE_RETRY
                else GeneralTeacherFeedbackType.NO_RESPONSE_NEXT
            BimodalInteractionState.TIME_EXPIRED ->
                if (context.canRetry) GeneralTeacherFeedbackType.TIME_EXPIRED_RETRY
                else GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT
            BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR ->
                if (context.canRetry) GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY
                else GeneralTeacherFeedbackType.TECHNICAL_ERROR_NEXT
            BimodalInteractionState.SESSION_COMPLETED ->
                GeneralTeacherFeedbackType.SESSION_COMPLETED
            else -> null
        }

    /**
     * Genera el mensaje de retroalimentacion para el contexto, o null si el estado
     * no requiere voz. Mide el tiempo de seleccion (practicamente inmediato).
     */
    fun generate(context: GeneralTeacherFeedbackContext): GeneralTeacherFeedbackMessage? {
        val type = feedbackTypeFor(context) ?: return null
        return message(type)
    }

    /**
     * Elige una frase para la categoria indicada, midiendo el tiempo de generacion.
     * Util para puntos del flujo que ya conocen la categoria (p. ej. la
     * presentacion de la pregunta usa [GeneralTeacherFeedbackType.QUESTION_INTRO]).
     */
    fun message(type: GeneralTeacherFeedbackType): GeneralTeacherFeedbackMessage {
        val start = System.nanoTime()
        val text = pick(type)
        val elapsed = System.nanoTime() - start
        return GeneralTeacherFeedbackMessage(type, text, elapsed)
    }

    /**
     * Selecciona el indice de frase de la categoria evitando repetir el ultimo
     * usado: elige de forma uniforme entre las demas opciones, sin sesgo. Con una
     * sola frase disponible la devuelve siempre.
     */
    private fun pick(type: GeneralTeacherFeedbackType): String {
        val options = phrases[type]
            ?: error("No hay frases definidas para la categoria $type")
        require(options.isNotEmpty()) { "La categoria $type no tiene frases" }

        if (options.size == 1) {
            lastIndexByType[type] = 0
            return options[0]
        }

        val lastIndex = lastIndexByType[type]
        val index = if (lastIndex == null) {
            random.nextInt(options.size)
        } else {
            // Elige uniformemente entre las (size - 1) opciones distintas de la
            // ultima, garantizando que no se repita la frase consecutiva.
            val candidate = random.nextInt(options.size - 1)
            if (candidate >= lastIndex) candidate + 1 else candidate
        }
        lastIndexByType[type] = index
        return options[index]
    }

    companion object {

        /**
         * Banco amplio de frases por categoria, en espanol, breves y adecuadas para
         * ninos. No incluye frases que sugieran un acierto parcial ("vas bien",
         * "estas cerca", "casi lo tienes") porque el flujo no modela esa categoria.
         */
        val DEFAULT_PHRASES: Map<GeneralTeacherFeedbackType, List<String>> = mapOf(
            GeneralTeacherFeedbackType.CORRECT to listOf(
                "Muy bien, esa respuesta es adecuada.",
                "Buen trabajo, lo hiciste bien.",
                "Excelente, continuemos.",
                "Muy bien, sigamos con la siguiente.",
                "Lo hiciste bien, vamos a continuar.",
                "Buen intento, esa respuesta funciona.",
                "Perfecto, podemos seguir.",
                "Muy bien, vamos por otra.",
                "Respuesta válida, sigamos.",
                "Lo lograste, continuemos.",
                "Bien hecho, pasemos a la siguiente.",
                "Muy bien, estás participando muy bien."
            ),
            GeneralTeacherFeedbackType.INCORRECT_RETRY to listOf(
                "Aún no es la respuesta esperada. Intentemos otra vez.",
                "Probemos nuevamente con calma.",
                "Intentemos una vez más.",
                "Escucha otra vez la pregunta y vuelve a responder.",
                "Todavía no coincide. Vamos a intentarlo otra vez.",
                "No te preocupes, puedes probar nuevamente.",
                "Vamos a repetirlo con atención.",
                "Inténtalo otra vez, despacio y claro.",
                "Pensemos un poquito más y probemos otra vez.",
                "Aún podemos intentarlo nuevamente.",
                "Vamos de nuevo, tú puedes.",
                "Probemos una respuesta diferente."
            ),
            GeneralTeacherFeedbackType.INCORRECT_NEXT to listOf(
                "No te preocupes, vamos a continuar.",
                "Está bien, pasemos a la siguiente pregunta.",
                "Sigamos avanzando con otra pregunta.",
                "Vamos con la siguiente.",
                "No pasa nada, continuemos.",
                "Seguimos con otra pregunta.",
                "Muy bien por intentarlo, ahora continuemos.",
                "Vamos a seguir practicando."
            ),
            GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY to listOf(
                "No pude entenderte bien. Repitamos con calma.",
                "Creo que no escuché claramente. Inténtalo otra vez.",
                "Puedes responder otra vez, despacio y claro.",
                "Repitamos la respuesta con voz clara.",
                "No logré interpretar tu respuesta. Probemos de nuevo.",
                "Intentemos otra vez para escucharte mejor.",
                "Habla un poquito más claro y probemos nuevamente.",
                "Vamos a repetirlo con calma.",
                "No entendí bien esa respuesta. Intenta otra vez.",
                "Probemos de nuevo, estoy escuchando."
            ),
            GeneralTeacherFeedbackType.NOT_INTERPRETABLE_NEXT to listOf(
                "No pude entender bien la respuesta, pero vamos a continuar.",
                "Sigamos con la siguiente pregunta.",
                "Continuemos con otra pregunta.",
                "Vamos a seguir avanzando.",
                "No hay problema, pasemos a la siguiente.",
                "Seguimos practicando con otra pregunta."
            ),
            GeneralTeacherFeedbackType.NO_RESPONSE_RETRY to listOf(
                "No escuché una respuesta. Intentemos nuevamente.",
                "Parece que no respondiste. Probemos otra vez.",
                "Cuando estés listo, responde con voz clara.",
                "No escuché tu voz. Intentémoslo otra vez.",
                "Vamos a probar nuevamente.",
                "Responde cuando estés listo.",
                "Intentemos otra vez, te estoy escuchando.",
                "No se detectó respuesta. Probemos de nuevo.",
                "Puedes intentarlo otra vez.",
                "Vamos de nuevo con calma."
            ),
            GeneralTeacherFeedbackType.NO_RESPONSE_NEXT to listOf(
                "No escuché respuesta, vamos a continuar.",
                "Pasemos a la siguiente pregunta.",
                "No hay problema, seguimos avanzando.",
                "Continuemos con otra pregunta.",
                "Vamos a seguir practicando.",
                "Sigamos con la siguiente."
            ),
            GeneralTeacherFeedbackType.TIME_EXPIRED_RETRY to listOf(
                "Se terminó el tiempo. Intentemos nuevamente.",
                "El tiempo acabó, pero puedes probar otra vez.",
                "Vamos a repetirlo un poco más rápido.",
                "Se acabó el tiempo. Intentemos de nuevo.",
                "Probemos otra vez antes de continuar.",
                "El tiempo terminó. Vamos de nuevo.",
                "Intentemos responder con más calma esta vez.",
                "Vamos a intentarlo una vez más."
            ),
            GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT to listOf(
                "Se terminó el tiempo. Vamos a continuar.",
                "El tiempo acabó. Pasemos a la siguiente.",
                "No hay problema, sigamos con otra pregunta.",
                "Continuemos con la siguiente.",
                "Vamos a seguir avanzando.",
                "Pasemos a otra pregunta."
            ),
            GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY to listOf(
                "Hubo un pequeño problema. Intentemos otra vez.",
                "Algo no funcionó bien. Probemos nuevamente.",
                "Vamos a repetirlo para continuar.",
                "Tuve un problema para procesar eso. Intentemos otra vez.",
                "Probemos nuevamente.",
                "Intentemos continuar con calma."
            ),
            GeneralTeacherFeedbackType.TECHNICAL_ERROR_NEXT to listOf(
                "Hubo un pequeño problema, vamos a continuar.",
                "Sigamos con la siguiente pregunta.",
                "No hay problema, continuemos.",
                "Vamos a avanzar con otra pregunta.",
                "Continuemos con la actividad.",
                "Sigamos practicando."
            ),
            GeneralTeacherFeedbackType.SESSION_START to listOf(
                "Vamos a empezar.",
                "Estoy listo para jugar y aprender contigo.",
                "Empecemos con la actividad.",
                "Vamos a practicar juntos.",
                "Prepárate, vamos a comenzar.",
                "Comencemos con atención."
            ),
            GeneralTeacherFeedbackType.QUESTION_INTRO to listOf(
                "Escucha con atención.",
                "Ahora va la pregunta.",
                "Presta atención a esta pregunta.",
                "Vamos con una pregunta.",
                "Escucha bien antes de responder.",
                "Ahora responde cuando estés listo.",
                "Piensa un momento antes de responder.",
                "Aquí viene la pregunta."
            ),
            GeneralTeacherFeedbackType.SESSION_COMPLETED to listOf(
                "Terminamos la actividad. Gracias por participar.",
                "Muy bien, completamos la actividad.",
                "Hemos terminado. Buen trabajo.",
                "Gracias por participar, lo hiciste con esfuerzo.",
                "Actividad finalizada. Buen trabajo.",
                "Terminamos por ahora. Gracias por responder.",
                "Muy bien, llegamos al final.",
                "Buen trabajo, terminamos la sesión."
            )
        )
    }
}
