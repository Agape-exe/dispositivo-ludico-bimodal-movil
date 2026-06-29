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
         * Banco amplio de frases de Seven por categoria, en espanol, breves y
         * adecuadas para ninos de 3 a 5 anos. Es el respaldo generico del modo
         * inteligente: Seven, el alien explorador, acompana con calidez y sin sonar
         * a examen.
         *
         * Reglas (VOZ01):
         *  - No culpa al nino: nunca dice "incorrecto", "mal", "fallaste",
         *    "te equivocaste" ni "no sabes".
         *  - No suena a adulto rigido: nada de "presta atencion", "concentrate",
         *    "evaluacion" ni "respuesta valida".
         *  - No revela la respuesta cuando quedan intentos.
         *  - No sugiere acierto parcial ("vas bien", "estas cerca", "casi lo
         *    tienes") porque el flujo no modela esa categoria.
         *  - Frases cortas (<=120 caracteres) y con la voz de Seven sin abusar de
         *    "mi antenita" / "mi nave" en todas.
         */
        val DEFAULT_PHRASES: Map<GeneralTeacherFeedbackType, List<String>> = mapOf(
            GeneralTeacherFeedbackType.CORRECT to listOf(
                "¡Muy bien! Mi nave aprendió algo nuevo contigo.",
                "¡Lo lograste! Seven está feliz.",
                "¡Excelente ayuda, explorador!",
                "¡Perfecto! Mi antenita guardó tu respuesta.",
                "¡Qué bien! Aprendí algo de la Tierra.",
                "¡Lo lograste! Gracias por ayudarme.",
                "¡Muy bien! Seguimos descubriendo juntos.",
                "¡Excelente! Mi nave dio un saltito de alegría.",
                "¡Perfecto, amiguito! Eso me sirve mucho.",
                "¡Qué bien lo hiciste! Seven aprende contigo.",
                "¡Lo lograste! Mi antenita está contenta.",
                "¡Muy bien! Esa ayuda es genial."
            ),
            GeneralTeacherFeedbackType.INCORRECT_RETRY to listOf(
                "Sigamos pensando juntitos. Probemos otra vez.",
                "Mi antenita cree que podemos intentarlo de nuevo.",
                "Vamos otra vez, con calma. Yo te acompaño.",
                "Probemos de nuevo, mi pequeño explorador.",
                "Intentémoslo otra vez, tú y yo.",
                "Seven quiere escucharte una vez más.",
                "Vamos a intentarlo de nuevo, sin apuro.",
                "Pensemos un poquito más y probemos otra vez.",
                "No pasa nada, podemos intentarlo nuevamente.",
                "Mi nave espera otro intento. ¡Vamos!",
                "Probemos con otra idea, exploradorcito.",
                "Lo intentamos otra vez, juntos."
            ),
            GeneralTeacherFeedbackType.INCORRECT_NEXT to listOf(
                "Gracias por intentarlo. Sigamos explorando.",
                "Gracias por participar. Vamos con otro reto.",
                "No pasa nada, continuemos la aventura.",
                "Seguimos descubriendo cosas de la Tierra.",
                "Gracias por tu ayuda. Vamos con otra pregunta.",
                "Está bien, sigamos jugando juntos.",
                "Continuemos con calma, explorador.",
                "Vamos a seguir descubriendo cositas nuevas."
            ),
            GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY to listOf(
                "Mi antenita no te escuchó bien. Probemos otra vez.",
                "Creo que la señal llegó bajita. Repítelo, por favor.",
                "Dilo despacito otra vez, te escucho.",
                "No te entendí bien. Intentémoslo de nuevo.",
                "Mi nave necesita escucharte una vez más.",
                "Repitamos con voz clarita, exploradorcito.",
                "Probemos de nuevo, estoy escuchando.",
                "La señal llegó borrosa. Intenta otra vez.",
                "No logré entenderte. Repítelo, por favor.",
                "Dime otra vez, despacito y claro."
            ),
            GeneralTeacherFeedbackType.NOT_INTERPRETABLE_NEXT to listOf(
                "No te escuché muy bien, pero sigamos explorando.",
                "Vamos con otro reto de la Tierra.",
                "Continuemos la aventura, explorador.",
                "No pasa nada, seguimos descubriendo.",
                "Sigamos con otra pregunta juntos.",
                "Vamos a seguir jugando, amiguito."
            ),
            GeneralTeacherFeedbackType.NO_RESPONSE_RETRY to listOf(
                "No escuché tu voz. Probemos otra vez.",
                "Mi antenita espera tu respuesta. Intenta de nuevo.",
                "Cuando quieras, respóndeme con tu voz.",
                "Seven sigue escuchando. Intentémoslo otra vez.",
                "No llegó tu señal. Vamos a intentarlo de nuevo.",
                "Te escucho, exploradorcito. Responde cuando quieras.",
                "Mi nave no captó tu voz. Probemos otra vez.",
                "Cuéntame tu respuesta, te escucho de nuevo.",
                "Vamos otra vez, con calma. Estoy atento.",
                "Puedes intentarlo otra vez, yo espero."
            ),
            GeneralTeacherFeedbackType.NO_RESPONSE_NEXT to listOf(
                "No escuché respuesta, pero sigamos explorando.",
                "Vamos con otra pregunta de la Tierra.",
                "No pasa nada, continuemos la aventura.",
                "Seguimos descubriendo juntos, explorador.",
                "Sigamos con otro reto, amiguito.",
                "Vamos a seguir jugando con calma."
            ),
            GeneralTeacherFeedbackType.TIME_EXPIRED_RETRY to listOf(
                "Se acabó el tiempito. Probemos otra vez.",
                "Mi reloj espacial sonó. Intentémoslo de nuevo.",
                "Vamos otra vez, un poquito más rápido.",
                "El tiempo voló. Probemos una vez más.",
                "Intentémoslo de nuevo, con calma.",
                "Mi nave te da otro intento. ¡Vamos!",
                "Se terminó el tiempito, pero podemos intentarlo otra vez.",
                "Probemos de nuevo antes de seguir."
            ),
            GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT to listOf(
                "Se acabó el tiempito. Sigamos explorando.",
                "Vamos a continuar la aventura.",
                "Seguimos con otra pregunta de la Tierra.",
                "No pasa nada, continuemos juntos.",
                "Sigamos descubriendo más cositas.",
                "Continuemos con otro reto, explorador."
            ),
            GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY to listOf(
                "Uy, mi nave hizo un ruidito raro. Probemos otra vez.",
                "Mi antenita se confundió un poquito. Intentémoslo de nuevo.",
                "Algo pasó en mi nave, pero seguimos tranquilos.",
                "Mi sistema espacial tartamudeó. Vamos otra vez.",
                "Hubo un saltito en mi nave. Probemos de nuevo.",
                "Mi computadora se mareó un momento. Intentémoslo otra vez."
            ),
            GeneralTeacherFeedbackType.TECHNICAL_ERROR_NEXT to listOf(
                "Mi nave tuvo un ruidito, pero sigamos explorando.",
                "Algo pasó en mi nave. Continuemos la aventura.",
                "No pasa nada, seguimos con otra pregunta.",
                "Mi antenita ya está mejor. Sigamos jugando.",
                "Vamos a continuar, mi nave sigue lista.",
                "Seguimos descubriendo, exploradorcito."
            ),
            GeneralTeacherFeedbackType.SESSION_START to listOf(
                "¡Hola! Soy Seven. Vamos a descubrir cosas juntos.",
                "Mi antenita está lista para aprender contigo.",
                "¡Empecemos esta aventura espacial!",
                "Vine de muy lejos para conocer la Tierra contigo.",
                "¡Hola, explorador! Comencemos a jugar.",
                "Mi nave está lista. ¡Vamos a empezar!"
            ),
            GeneralTeacherFeedbackType.QUESTION_INTRO to listOf(
                "Aquí viene un reto de la Tierra.",
                "Tengo una preguntita para ti.",
                "Mi antenita quiere descubrir algo.",
                "Vamos con un reto pequeñito.",
                "Seven tiene una duda curiosa.",
                "Escucha esta preguntita y dime.",
                "Aquí va otra aventura para tu voz.",
                "Mi nave quiere aprender esto contigo."
            ),
            GeneralTeacherFeedbackType.SESSION_COMPLETED to listOf(
                "Gracias por ayudarme a conocer la Tierra.",
                "Terminamos por hoy. ¡Hasta la próxima aventura!",
                "Mi nave guardó una estrellita gracias a ti.",
                "Seven aprendió mucho contigo. ¡Gracias!",
                "Completamos la misión. ¡Buen trabajo, explorador!",
                "Llegamos al final. Gracias por jugar conmigo.",
                "Terminamos la aventura. Seven está feliz.",
                "Gracias, exploradorcito. La misión quedó completa."
            )
        )
    }
}
