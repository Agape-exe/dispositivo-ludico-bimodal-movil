package com.taller.app.bimodal.feedback

import com.taller.app.model.LocalMediationKey
import kotlin.random.Random

/**
 * Banco local avanzado de frases de mediacion ludica para la actividad de animales
 * ("Los animales y sus sonidos").
 *
 * A partir de la clave de mediacion local registrada por el docente
 * ([LocalMediationKey]) entrega frases calidas, humanizadas y entretenidas para
 * ninos pequenos. No usa IA generativa, no llama a ninguna API externa y no
 * depende de internet: solo elige que frase decir entre conjuntos pre-aprobados.
 *
 * El banco no decide si una respuesta es correcta; eso sigue siendo trabajo del
 * SemanticEvaluator. Aqui solo se selecciona la frase adecuada para el desenlace
 * que el flujo ya determino.
 *
 * Reglas que respetan estos conjuntos de frases:
 *  - Nunca se revela la respuesta esperada mientras quedan intentos (las frases de
 *    reintento solo invitan a pensar de nuevo).
 *  - Con la respuesta correcta nunca se dice algo distinto a un acierto real.
 *  - Ante error tecnico, silencio o respuesta no interpretable nunca se culpa al
 *    nino.
 *  - No se usan frases de acierto parcial ("estas cerca", "casi lo tienes", "vas
 *    por buen camino", "mencionaste una idea importante").
 *  - En la ultima pregunta, [getIncorrectNextFeedback] evita frases de continuidad
 *    ("continuemos", "pasemos a la siguiente", "vamos con otra").
 *
 * Para claves no reconocidas o vacias ([LocalMediationKey.NONE]) la introduccion y
 * la retroalimentacion contextual se delegan a [GeneralTeacherFeedbackGenerator]
 * (frases generales seguras); las categorias generales de sesion, silencio, error y
 * cierre siempre usan los bancos amplios de esta clase.
 *
 * Mantiene estado mutable (el ultimo indice elegido por conjunto) para no repetir
 * la misma frase de forma consecutiva, por lo que debe existir una instancia por
 * sesion. El generador de aleatoriedad es inyectable para facilitar las pruebas.
 */
class AnimalMediationBank(
    private val random: Random = Random.Default,
    private val generalFallback: GeneralTeacherFeedbackGenerator = GeneralTeacherFeedbackGenerator()
) {

    /** Ultimo indice de frase elegido por conjunto, para evitar repeticiones seguidas. */
    private val lastIndexByGroup = HashMap<String, Int>()

    /** Frase de apertura de la sesion. */
    fun getSessionStartPhrase(): String = pick("GENERAL_SESSION_START", GENERAL_SESSION_START)

    /** Frase de cierre cuando la actividad termina. */
    fun getSessionCompletedPhrase(): String =
        pick("GENERAL_SESSION_COMPLETED", GENERAL_SESSION_COMPLETED)

    /** Frase para cuando la respuesta no se pudo interpretar. Nunca culpa al nino. */
    fun getNotInterpretableFeedback(): String =
        pick("GENERAL_NOT_INTERPRETABLE", GENERAL_NOT_INTERPRETABLE)

    /** Frase para cuando no se detecto respuesta o silencio. Nunca culpa al nino. */
    fun getNoResponseFeedback(): String = pick("GENERAL_NO_RESPONSE", GENERAL_NO_RESPONSE)

    /** Frase para un error tecnico recuperable. Nunca culpa al nino. */
    fun getTechnicalErrorFeedback(): String =
        pick("GENERAL_TECHNICAL_ERROR", GENERAL_TECHNICAL_ERROR)

    /**
     * Introduccion previa a una pregunta segun su clave de mediacion. Las frases de
     * animales ya incluyen el enunciado de la pregunta; para [LocalMediationKey.NONE]
     * o claves no reconocidas devuelve una introduccion general (que no incluye la
     * pregunta: la concatena el llamador).
     */
    fun getQuestionIntroduction(mediationKey: String?): String =
        forKey(mediationKey, INTRODUCTIONS, "INTRO") {
            generalFallback.message(GeneralTeacherFeedbackType.QUESTION_INTRO).text
        }

    /** Retroalimentacion para una respuesta correcta segun la clave de mediacion. */
    fun getCorrectFeedback(mediationKey: String?): String =
        forKey(mediationKey, CORRECT_FEEDBACK, "CORRECT") {
            generalFallback.message(GeneralTeacherFeedbackType.CORRECT).text
        }

    /**
     * Retroalimentacion para una respuesta incorrecta cuando aun quedan intentos.
     * Nunca revela la respuesta esperada: solo invita a pensar de nuevo.
     */
    fun getIncorrectRetryFeedback(mediationKey: String?): String =
        forKey(mediationKey, INCORRECT_RETRY, "RETRY") {
            generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_RETRY).text
        }

    /**
     * Retroalimentacion para una respuesta incorrecta sin intentos restantes. Aqui
     * si puede mencionarse la respuesta correcta de forma amable.
     *
     * En la ultima pregunta ([isLastQuestion]) se excluyen las frases con continuidad
     * ("continuemos", "pasemos a la siguiente", "sigamos") para no anunciar un avance
     * que no ocurrira; si la clave es general se usa el cierre suave.
     */
    fun getIncorrectNextFeedback(mediationKey: String?, isLastQuestion: Boolean): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val list = INCORRECT_NEXT[key]
            ?: return if (isLastQuestion) getSessionCompletedPhrase()
            else generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_NEXT).text

        if (!isLastQuestion) return pick("NEXT_${key.name}", list)

        // Ultima pregunta: nunca una frase de continuidad antes del cierre.
        val neutral = list.filterNot(::hasContinuation)
        return if (neutral.isEmpty()) getSessionCompletedPhrase()
        else pick("NEXT_LAST_${key.name}", neutral)
    }

    /**
     * Resuelve la frase de un grupo asociado a una clave de mediacion concreta; si la
     * clave es general o no tiene frases propias, usa [generalText].
     */
    private inline fun forKey(
        mediationKey: String?,
        groups: Map<LocalMediationKey, List<String>>,
        prefix: String,
        generalText: () -> String
    ): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val list = groups[key] ?: return generalText()
        return pick("${prefix}_${key.name}", list)
    }

    /**
     * Selecciona una frase del conjunto evitando repetir la ultima usada: elige de
     * forma uniforme entre las demas opciones. Con una sola frase la devuelve siempre.
     */
    private fun pick(group: String, options: List<String>): String {
        require(options.isNotEmpty()) { "El conjunto $group no tiene frases" }

        if (options.size == 1) {
            lastIndexByGroup[group] = 0
            return options[0]
        }

        val lastIndex = lastIndexByGroup[group]
        val index = if (lastIndex == null) {
            random.nextInt(options.size)
        } else {
            // Elige uniformemente entre las (size - 1) opciones distintas de la ultima.
            val candidate = random.nextInt(options.size - 1)
            if (candidate >= lastIndex) candidate + 1 else candidate
        }
        lastIndexByGroup[group] = index
        return options[index]
    }

    companion object {

        /** Marcas de continuidad a evitar en la ultima pregunta. */
        private val CONTINUATION_MARKERS = listOf(
            "continuemos", "continuar", "siguiente", "vamos con otra",
            "pasemos", "sigamos", "vamos a continuar"
        )

        /** Indica si una frase contiene alguna marca de continuidad. */
        private fun hasContinuation(phrase: String): Boolean {
            val lower = phrase.lowercase()
            return CONTINUATION_MARKERS.any { lower.contains(it) }
        }

        val GENERAL_SESSION_START: List<String> = listOf(
            "Hola, hoy vamos a jugar aprendiendo sobre los animales.",
            "Vamos a empezar una actividad divertida con animales y sonidos.",
            "Hoy escucharemos, pensaremos y responderemos sobre animales.",
            "Me alegra verte. Vamos a jugar con preguntas de animales.",
            "Prepárate, porque hoy conoceremos animales domésticos y de granja.",
            "Vamos a imaginar que visitamos un lugar lleno de animalitos.",
            "Hoy jugaremos a reconocer animales y algunos sonidos que hacen.",
            "Escucha con atención, porque los animales tienen mucho que enseñarnos.",
            "Empecemos con una aventura pequeña sobre animales.",
            "Vamos a aprender jugando. Hoy hablaremos de animales que quizá ya conoces."
        )

        val GENERAL_SESSION_COMPLETED: List<String> = listOf(
            "Terminamos la actividad. Gracias por jugar y aprender conmigo.",
            "Muy bien, completamos las preguntas sobre animales.",
            "Hemos terminado por hoy. Me gustó mucho jugar contigo.",
            "Gracias por participar. Aprendimos juntos sobre los animales.",
            "La actividad terminó. Lo hiciste con mucho esfuerzo.",
            "Muy bien, llegamos al final de nuestra aventura con animales.",
            "Terminamos el juego de animales. Gracias por responder.",
            "Excelente, completamos todas las preguntas de hoy.",
            "Ya terminamos. Me encantó acompañarte en esta actividad.",
            "Gracias por jugar conmigo. Hoy recordamos animales y sonidos."
        )

        val GENERAL_NOT_INTERPRETABLE: List<String> = listOf(
            "No pude escucharte bien. Vamos a repetirlo con voz clara.",
            "Creo que no entendí bien tu respuesta. Inténtalo otra vez despacito.",
            "No logré reconocer lo que dijiste. Probemos nuevamente.",
            "Vamos a intentarlo otra vez con calma y voz clara.",
            "Te escuché un poquito bajo. Repitamos una vez más.",
            "Creo que tu voz llegó un poco mezclada. Vamos a probar de nuevo.",
            "No entendí bien esa respuesta. Dila otra vez con tranquilidad.",
            "Vamos a repetirlo, pero esta vez un poquito más claro.",
            "No pude reconocer bien tus palabras. Intentemos otra vez.",
            "Te escuché, pero no pude entenderlo bien. Probemos nuevamente."
        )

        val GENERAL_NO_RESPONSE: List<String> = listOf(
            "No escuché una respuesta. Cuando estés listo, puedes responder con voz clara.",
            "Parece que no respondiste todavía. Intentemos una vez más.",
            "Estoy esperando tu respuesta. Respira tranquilo y probemos de nuevo.",
            "No escuché nada esta vez. Vamos a repetir la pregunta.",
            "Puedes responder cuando estés listo. Intentemos otra vez.",
            "No escuché tu voz. Vamos a probar nuevamente.",
            "Parece que hubo un silencio. No pasa nada, repitamos.",
            "Cuando estés listo, dime tu respuesta con voz clara.",
            "Esta vez no escuché respuesta. Intentémoslo con calma.",
            "Vamos a intentarlo otra vez. Yo te espero."
        )

        val GENERAL_TECHNICAL_ERROR: List<String> = listOf(
            "Parece que tuve un pequeño problema para escucharte. Vamos a intentarlo otra vez.",
            "Algo falló por un momento, pero no pasa nada. Probemos nuevamente.",
            "Tu respuesta es importante, pero tuve un problema técnico. Intentemos otra vez.",
            "Creo que mi oído de juguete se confundió un poquito. Vamos de nuevo.",
            "Tu voz no llegó bien esta vez. Repitamos con calma.",
            "Hubo una pequeña falla, pero seguimos jugando.",
            "No fue tu culpa. Tuve un problemita para escuchar.",
            "Vamos a repetirlo, porque esta vez no pude procesarlo bien.",
            "Mi sistema se distrajo un poquito. Intentemos nuevamente.",
            "Sigamos con calma. Voy a escucharte otra vez."
        )

        // ----- Frases especificas por clave de mediacion local ---------------------

        val INTRODUCTIONS: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "Te cuento algo: los perros son mascotas muy juguetonas y a veces ladran cuando están felices. Ahora dime, ¿qué sonido hace el perro?",
                "Imaginemos que vemos un perrito moviendo la colita porque quiere jugar. Dime, ¿qué sonido hace el perro?",
                "Hoy empezamos con un animal muy conocido. Tiene patitas, cola y muchas veces cuida la casa. ¿Qué sonido hace el perro?",
                "Piensa en un perrito cuando ve llegar a su dueño. Se emociona mucho y hace un sonido. ¿Qué sonido hace el perro?",
                "En muchas casas hay perritos que saludan con mucha alegría. Escucha bien: ¿qué sonido hace el perro?",
                "Vamos a imaginar un parque con un perrito corriendo detrás de una pelota. Ahora dime, ¿qué sonido hace el perro?",
                "Hay un animal que a veces usa collar, mueve la cola y ladra. ¿Qué sonido hace el perro?",
                "Te cuento que algunos perros ladran cuando quieren avisar algo. Ahora dime, ¿qué sonido hace el perro?",
                "Imagina que tocamos una puerta y un perrito nos escucha desde adentro. ¿Qué sonido hace el perro?",
                "Vamos a jugar con sonidos. Primero pensemos en un perrito feliz. ¿Qué sonido hace el perro?"
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "Hay animales que pueden vivir cerca de nosotros y acompañarnos en casa. Ahora dime, menciona un animal doméstico.",
                "Pensemos en los animalitos que algunas familias cuidan en casa. Dime un animal doméstico.",
                "Imagina una casa con una mascota cariñosa. Ahora dime, menciona un animal doméstico.",
                "Algunas mascotas viven con las personas y reciben mucho cariño. Dime un animal doméstico.",
                "Vamos a pensar en animales que podemos cuidar en casa. Menciona un animal doméstico.",
                "Hay animalitos que comen, duermen y juegan cerca de las personas. Dime uno que sea doméstico.",
                "Imagina que visitamos una casa donde hay una mascota. ¿Qué animal doméstico podrías mencionar?",
                "Algunas personas tienen animales en casa y los cuidan todos los días. Menciona un animal doméstico.",
                "Vamos a pensar en una mascota que pueda vivir con una familia. Dime un animal doméstico.",
                "Hay animales que no viven en la selva ni en la granja, sino cerca de nosotros en casa. Menciona uno."
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "Ahora pensemos en un gatito. Los gatos caminan suavecito y hacen un sonido especial. Dime, ¿qué sonido hace el gato?",
                "Imagina un gato pequeño buscando a su mamá. Escucha bien: ¿qué sonido hace el gato?",
                "Los gatitos pueden ser muy curiosos y a veces hacen un sonido tierno. ¿Qué sonido hace el gato?",
                "Sigamos jugando con sonidos de animales. Ahora toca el gato: ¿qué sonido hace?",
                "Piensa en un gatito cuando quiere que lo acaricien. Dime, ¿qué sonido hace el gato?",
                "Imagina un gato sentado en la ventana mirando todo con curiosidad. ¿Qué sonido hace el gato?",
                "Hay un animal que tiene bigotes, camina suavecito y maúlla. ¿Qué sonido hace el gato?",
                "Vamos a pensar en un gatito que quiere leche. Dime, ¿qué sonido hace el gato?",
                "Los gatos a veces se acercan despacito y hacen un sonido suave. ¿Qué sonido hace el gato?",
                "Ahora imaginemos un gatito jugando con una bolita de lana. ¿Qué sonido hace el gato?"
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "Imaginemos que visitamos una granja con muchos animales. Algunos caminan, otros corren y otros hacen sonidos fuertes. Ahora dime, menciona un animal que encontremos en la granja.",
                "En la granja viven muchos animales que podemos conocer y cuidar. Dime un animal que encontremos en la granja.",
                "Vamos a pasear con la imaginación por una granja. Mira a tu alrededor y dime un animal que podríamos encontrar.",
                "En una granja podemos ver animales grandes y pequeños. Ahora menciona un animal de la granja.",
                "Pensemos en una granja con corrales, pasto y muchos sonidos. Dime un animal que viva allí.",
                "Imagina que abrimos la puerta de una granja y escuchamos muchos animales. Menciona uno que podamos encontrar.",
                "En la granja hay animales que dan leche, ponen huevos o ayudan en el campo. Dime uno.",
                "Vamos a visitar una granja imaginaria. Hay corrales, pasto y animalitos. ¿Qué animal encontramos allí?",
                "Piensa en los animales que suelen vivir en el campo. Menciona uno que encontremos en la granja.",
                "Ahora viajemos con la imaginación a una granja. Dime un animal que viva en ese lugar."
            )
        )

        val CORRECT_FEEDBACK: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "¡Exacto! El perro hace guau. Los perritos ladran así cuando quieren saludar o jugar.",
                "¡Muy bien! El sonido del perro es guau. Lo hiciste muy bien.",
                "¡Eso es! El perro ladra y hace guau.",
                "¡Excelente! Un perro puede decir guau cuando está contento o quiere llamar la atención.",
                "¡Bien hecho! Reconociste el sonido del perro.",
                "¡Muy buena respuesta! El perro hace guau cuando ladra.",
                "¡Sí! El perrito dice guau. Lo respondiste muy bien.",
                "¡Correcto! Cuando un perro ladra, muchas veces escuchamos guau.",
                "¡Genial! Ese es el sonido que hace un perro.",
                "¡Lo lograste! El perro hace guau, como un perrito saludando."
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "¡Muy bien! Ese es un animal doméstico porque puede vivir cerca de las personas.",
                "¡Exacto! Ese animal puede ser una mascota o vivir en casa con una familia.",
                "¡Bien hecho! Mencionaste un animal doméstico.",
                "¡Excelente! Ese animal puede acompañar a las personas en casa.",
                "¡Muy buena respuesta! Ese animal sí puede ser doméstico.",
                "¡Sí! Ese animal puede ser cuidado por una familia.",
                "¡Lo hiciste muy bien! Ese animal puede vivir cerca de las personas.",
                "¡Correcto! Ese es un ejemplo de animal doméstico.",
                "¡Genial! Pensaste en un animal que puede estar en casa.",
                "¡Muy bien pensado! Ese animal puede ser una mascota."
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "¡Exacto! El gato hace miau. Los gatitos maúllan cuando quieren llamar nuestra atención.",
                "¡Muy bien! El sonido del gato es miau.",
                "¡Eso es! El gato maúlla y hace miau.",
                "¡Excelente! Reconociste el sonido del gato.",
                "¡Bien hecho! Un gatito puede decir miau cuando quiere algo.",
                "¡Correcto! El gato hace miau con su vocecita suave.",
                "¡Genial! Ese es el sonido de un gatito.",
                "¡Muy buena respuesta! El gato maúlla y dice miau.",
                "¡Lo lograste! El gatito hace miau.",
                "¡Sí! El gato dice miau cuando maúlla."
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "¡Muy bien! Ese animal puede vivir en la granja.",
                "¡Exacto! En la granja podemos encontrar ese animal.",
                "¡Bien hecho! Mencionaste un animal que podemos ver en una granja.",
                "¡Excelente! Ese animal forma parte de la vida en la granja.",
                "¡Muy buena respuesta! En una granja hay muchos animales como ese.",
                "¡Sí! Ese animal puede estar en una granja.",
                "¡Correcto! Ese es un buen ejemplo de animal de granja.",
                "¡Genial! Pensaste en un animal que vive en el campo o en la granja.",
                "¡Lo hiciste muy bien! Ese animal puede vivir en una granja.",
                "¡Muy bien pensado! Ese animal sí pertenece al ambiente de la granja."
            )
        )

        val INCORRECT_RETRY: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "No te preocupes, intentemos otra vez. Imagina a un perrito llamando a su dueño.",
                "Pensemos con calma. ¿Qué sonido escuchas cuando un perro ladra?",
                "Vamos a probar nuevamente. Recuerda el sonido que hace un perrito.",
                "Intentemos otra vez. Piensa en un perro pequeño jugando en el parque.",
                "No pasa nada, podemos repetir. Escucha la pregunta otra vez: ¿qué sonido hace el perro?",
                "Vamos a imaginar al perrito de nuevo. ¿Qué sonido haría para saludar?",
                "Probemos otra vez. Piensa en el sonido de un perro cuando ladra.",
                "Puedes intentarlo nuevamente. Recuerda cómo suena un perrito.",
                "Vamos con calma. Imagina que un perro quiere llamar tu atención.",
                "Intentemos una vez más. ¿Qué sonido hace un perrito cuando ladra?"
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "Pensemos otra vez. Un animal doméstico es uno que puede vivir con las personas.",
                "Intentemos nuevamente. Piensa en una mascota que pueda estar en una casa.",
                "No te preocupes, probemos otra vez. Recuerda un animal que una familia pueda cuidar.",
                "Vamos a repetir con calma. Puede ser un animal como una mascota.",
                "Piensa en un animal que tal vez hayas visto en una casa. Intenta responder otra vez.",
                "Vamos a pensar en mascotas. ¿Qué animal podría vivir con una familia?",
                "Probemos otra vez. Imagina un animalito que alguien cuide en su casa.",
                "No pasa nada. Piensa en un animal que pueda dormir, comer y jugar en una casa.",
                "Intentemos una vez más. Puede ser un animal que las personas quieran y cuiden.",
                "Vamos con calma. Recuerda algún animal que hayas visto como mascota."
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "Intentemos otra vez. Imagina a un gatito pequeño llamando con su voz.",
                "Pensemos con calma. ¿Qué sonido hace un gato cuando maúlla?",
                "Vamos a probar nuevamente. Recuerda el sonido de un gatito.",
                "No pasa nada, repetimos. Piensa en un gato diciendo su sonido.",
                "Escuchemos la pregunta otra vez: ¿qué sonido hace el gato?",
                "Vamos con calma. Imagina a un gatito queriendo llamar tu atención.",
                "Probemos una vez más. ¿Cómo suena un gato cuando maúlla?",
                "Piensa en un gatito pequeño. ¿Qué sonido haría?",
                "Intentémoslo otra vez. Recuerda cómo suena un gato.",
                "No te preocupes. Vamos a imaginar al gatito de nuevo."
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "Pensemos otra vez. En una granja hay animales que viven en corrales o en el campo.",
                "Intentemos nuevamente. Imagina una granja con pasto, corrales y muchos animales.",
                "No te preocupes, probemos otra vez. Piensa en un animal que viva en una granja.",
                "Vamos a repetir con calma. Puede ser un animal que dé leche, ponga huevos o viva en un corral.",
                "Intenta otra vez. Recuerda algún animal que hayas visto en dibujos de granja.",
                "Vamos con calma. Imagina una granja y mira qué animal aparece primero en tu mente.",
                "Probemos otra vez. Piensa en animales que caminan por el campo.",
                "No pasa nada. Recuerda un animal que podrías encontrar en una granja.",
                "Intentemos nuevamente. Puede ser un animal grande o pequeño que viva en la granja.",
                "Respira tranquilo y piensa en un animal de corral o de campo."
            )
        )

        val INCORRECT_NEXT: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "Está bien, lo intentaste. El perro hace guau. Sigamos con la siguiente.",
                "Buen intento. El sonido del perro es guau. Vamos a continuar.",
                "No te preocupes. Los perros ladran y hacen guau. Continuemos.",
                "Gracias por intentarlo. El perro dice guau cuando ladra.",
                "Está bien. Ahora ya recordamos que el perro hace guau.",
                "Muy bien por participar. El sonido del perro es guau.",
                "Lo intentaste con ganas. El perro hace guau cuando ladra.",
                "No pasa nada. Aprendimos que el perrito dice guau.",
                "Gracias por responder. El perro ladra y suena como guau.",
                "Está bien, seguimos aprendiendo. El perro hace guau."
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "Está bien, lo intentaste. Algunos animales domésticos son el perro, el gato o el conejo.",
                "Buen intento. Un animal doméstico puede ser un perro, un gato o un hámster.",
                "No te preocupes. Los animales domésticos son los que pueden vivir cerca de las personas.",
                "Gracias por intentarlo. Por ejemplo, el perro y el gato son animales domésticos.",
                "Muy bien por participar. Ahora recordamos que una mascota puede ser un animal doméstico.",
                "Está bien. Un animal doméstico puede ser el perro, el gato o la tortuga.",
                "Gracias por responder. Los animales domésticos pueden vivir con una familia.",
                "Lo intentaste. Algunos ejemplos son el conejo, el pez o el pájaro.",
                "No pasa nada. Seguimos aprendiendo sobre animales domésticos.",
                "Está bien, aprendimos juntos. Un animal doméstico puede ser una mascota."
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "Está bien, lo intentaste. El gato hace miau. Continuemos.",
                "Buen intento. El sonido del gato es miau.",
                "No te preocupes. Los gatos maúllan y hacen miau.",
                "Gracias por intentarlo. Ahora recordamos que el gato dice miau.",
                "Está bien. El gatito hace miau cuando maúlla.",
                "Muy bien por participar. El sonido del gato es miau.",
                "Lo intentaste con calma. El gato dice miau.",
                "No pasa nada. Aprendimos que el gatito hace miau.",
                "Gracias por responder. El gato maúlla con un miau.",
                "Está bien, seguimos aprendiendo. El gato hace miau."
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "Está bien, lo intentaste. En la granja podemos encontrar vacas, gallinas, cerdos y caballos.",
                "Buen intento. Algunos animales de la granja son la vaca, la oveja y el pato.",
                "No te preocupes. En una granja viven animales como gallinas, caballos y cabras.",
                "Gracias por participar. Ahora recordamos algunos animales de la granja.",
                "Está bien. En la granja podemos encontrar muchos animales, como la vaca y la gallina.",
                "Muy bien por intentarlo. Algunos animales de granja son el cerdo, el caballo y la oveja.",
                "Gracias por responder. En una granja también podemos encontrar patos, cabras y pollitos.",
                "No pasa nada. Seguimos aprendiendo sobre los animales de la granja.",
                "Está bien, aprendimos juntos. Una granja puede tener vacas, gallinas y caballos.",
                "Buen esfuerzo. Ahora recordamos que en la granja viven varios animales."
            )
        )
    }
}
