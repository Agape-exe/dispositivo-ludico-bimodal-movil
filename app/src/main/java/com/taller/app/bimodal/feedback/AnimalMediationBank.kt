package com.taller.app.bimodal.feedback

import com.taller.app.model.LocalMediationKey
import kotlin.random.Random

/**
 * Banco local de frases narrativas para la actividad de animales
 * ("Rescatemos los sonidos de los animales").
 *
 * Las frases tienen tono teatral y de aventura: el juguete es un personaje que
 * juega con el nino, no un evaluador. No usa IA generativa, no llama a ninguna
 * API externa y no depende de internet.
 *
 * Reglas que respetan todos los conjuntos de frases:
 *  - Nunca se revela la respuesta esperada mientras quedan intentos.
 *  - Ante error tecnico, silencio o respuesta no interpretable nunca se culpa al nino.
 *  - No se usan frases de acierto parcial ("estas cerca", "casi lo tienes", etc.).
 *  - En la ultima pregunta, se evitan frases de continuidad ("continuemos",
 *    "pasemos a la siguiente", "vamos con otra").
 *  - No se usan frases como "evaluacion", "sistema", "respuesta correcta/incorrecta".
 *
 * Para claves no reconocidas o vacias ([LocalMediationKey.NONE]) la introduccion y
 * la retroalimentacion contextual se delegan a [GeneralTeacherFeedbackGenerator].
 *
 * Mantiene estado mutable (el ultimo indice elegido por conjunto) para no repetir
 * la misma frase de forma consecutiva, por lo que debe existir una instancia por
 * sesion. El generador de aleatoriedad es inyectable para facilitar las pruebas.
 */
class AnimalMediationBank(
    private val random: Random = Random.Default,
    private val generalFallback: GeneralTeacherFeedbackGenerator = GeneralTeacherFeedbackGenerator()
) {

    private val lastIndexByGroup = HashMap<String, Int>()

    /** Frase de apertura de mision al iniciar la sesion. */
    fun getSessionStartPhrase(): String = pick("MISSION_START", MISSION_START)

    /** Frase de cierre al completar la actividad. */
    fun getSessionCompletedPhrase(): String = pick("MISSION_COMPLETED", MISSION_COMPLETED)

    /** Frase para cuando la respuesta no se pudo interpretar. Nunca culpa al nino. */
    fun getNotInterpretableFeedback(): String =
        pick("GENERAL_NOT_INTERPRETABLE", GENERAL_NOT_INTERPRETABLE)

    /** Frase para cuando no se detecto respuesta o silencio. Nunca culpa al nino. */
    fun getNoResponseFeedback(): String = pick("GENERAL_NO_RESPONSE", GENERAL_NO_RESPONSE)

    /** Frase para un error tecnico recuperable. Nunca culpa al nino. */
    fun getTechnicalErrorFeedback(): String =
        pick("GENERAL_TECHNICAL_ERROR", GENERAL_TECHNICAL_ERROR)

    /**
     * Devuelve un microdiálogo breve de forma ocasional (probabilidad configurable)
     * para intercalar antes de la intro de una pregunta. Devuelve null cuando no
     * corresponde usarlo, para no alargar la interaccion.
     */
    fun getMicroDialogue(probabilityPercent: Int = 35): String? {
        if (random.nextInt(100) >= probabilityPercent) return null
        return pick("MICRO_DIALOGUE", MICRO_DIALOGUES)
    }

    /**
     * Mini escena narrativa antes de una pregunta segun su clave de mediacion. Las
     * escenas de animales ya incluyen el enunciado de la pregunta; para
     * [LocalMediationKey.NONE] o claves no reconocidas devuelve una introduccion
     * general (la pregunta se concatena aparte en el llamador).
     */
    fun getQuestionIntroduction(mediationKey: String?): String =
        forKey(mediationKey, INTRODUCTIONS, "INTRO") {
            generalFallback.message(GeneralTeacherFeedbackType.QUESTION_INTRO).text
        }

    /**
     * Retroalimentacion para una respuesta correcta segun la clave de mediacion.
     * En la ultima pregunta ([isLastQuestion]) se excluyen las frases con continuidad.
     */
    fun getCorrectFeedback(mediationKey: String?, isLastQuestion: Boolean = false): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val list = CORRECT_FEEDBACK[key]
            ?: return generalFallback.message(GeneralTeacherFeedbackType.CORRECT).text

        if (!isLastQuestion) return pick("CORRECT_${key.name}", list)

        val neutral = list.filterNot(::hasContinuation)
        return if (neutral.isEmpty()) pick("CORRECT_${key.name}", list)
        else pick("CORRECT_LAST_${key.name}", neutral)
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
     * En la ultima pregunta ([isLastQuestion]) se excluyen frases con continuidad.
     */
    fun getIncorrectNextFeedback(mediationKey: String?, isLastQuestion: Boolean): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val list = INCORRECT_NEXT[key]
            ?: return if (isLastQuestion) getSessionCompletedPhrase()
            else generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_NEXT).text

        if (!isLastQuestion) return pick("NEXT_${key.name}", list)

        val neutral = list.filterNot(::hasContinuation)
        return if (neutral.isEmpty()) getSessionCompletedPhrase()
        else pick("NEXT_LAST_${key.name}", neutral)
    }

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
            val candidate = random.nextInt(options.size - 1)
            if (candidate >= lastIndex) candidate + 1 else candidate
        }
        lastIndexByGroup[group] = index
        return options[index]
    }

    companion object {

        private val CONTINUATION_MARKERS = listOf(
            "continuemos", "continuar", "siguiente", "vamos con otra",
            "pasemos", "sigamos", "vamos a continuar"
        )

        private fun hasContinuation(phrase: String): Boolean {
            val lower = phrase.lowercase()
            return CONTINUATION_MARKERS.any { lower.contains(it) }
        }

        // ----- Apertura de mision --------------------------------------------------

        val MISSION_START: List<String> = listOf(
            "¡Hola! Hoy tengo una misión especial. Se me mezclaron algunos sonidos de animales y necesito tu ayuda para recordarlos.",
            "¡Qué bueno que estás aquí! Mi granja imaginaria está un poquito desordenada y necesito a alguien que sepa de animales.",
            "Hoy vamos a ser exploradores de animales. Yo te haré algunas preguntas y tú me ayudarás a encontrarlos.",
            "Tengo un pequeño problema: escuché varios sonidos de animales, pero algunos se me olvidaron. ¿Me ayudas?",
            "Bienvenido a nuestra misión animal. Vamos a buscar sonidos, mascotas y animales de la granja.",
            "Hoy necesito tus orejitas de explorador. Vamos a descubrir animales juntos.",
            "Prepárate, porque vamos a entrar a una aventura de animales. Yo pregunto y tú me ayudas.",
            "Mi memoria de juguete se confundió con algunos animalitos. Vamos a ordenarlos juntos.",
            "Tenemos una misión: reconocer animales y sonidos. Sé que puedes ayudarme.",
            "Hoy la granja necesita nuestra ayuda. Vamos a descubrir qué animal es cada uno."
        )

        // ----- Cierre de mision ----------------------------------------------------

        val MISSION_COMPLETED: List<String> = listOf(
            "¡Misión cumplida! Gracias por ayudarme a ordenar los animales.",
            "Terminamos nuestra aventura animal. Me encantó jugar contigo.",
            "La granja imaginaria está feliz otra vez. Gracias por ayudarme.",
            "Completamos todas las pistas de animales. Lo hiciste con mucho esfuerzo.",
            "Nuestra misión terminó. Hoy reconocimos sonidos, mascotas y animales de granja.",
            "Gracias por ser mi ayudante en esta aventura.",
            "Los animales ya están en su lugar. Terminamos por hoy.",
            "Muy bien, explorador. Completamos la misión de animales.",
            "Hoy hicimos un gran trabajo en nuestra aventura animal.",
            "Gracias por jugar conmigo. Nuestra misión de animales terminó."
        )

        // ----- Microdiálogos opcionales --------------------------------------------

        val MICRO_DIALOGUES: List<String> = listOf(
            "Espera... creo que escuché algo.",
            "Vamos despacito, como buenos exploradores.",
            "Usa tus orejitas de detective.",
            "A ver, a ver... pensemos juntos.",
            "Me parece que hay un animal cerca.",
            "Esta misión está interesante.",
            "Necesito tu ayuda en esta parte.",
            "Vamos a imaginarlo juntos.",
            "Shhh... prestemos atención.",
            "Listo, vamos con la siguiente pista."
        )

        // ----- Frases generales (no interpretable, sin respuesta, error técnico) ---

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

        // ----- Mini escenas antes de cada pregunta ---------------------------------

        val INTRODUCTIONS: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "Primera misión: escuché unas patitas corriendo por el patio. Creo que es un perrito feliz. ¿Qué sonido hace el perro?",
                "Imagina que abrimos la puerta y un perrito viene moviendo la colita. Quiere saludarnos. ¿Qué sonido hace el perro?",
                "Veo una pelota rodando y un perrito detrás de ella. Está muy emocionado. ¿Qué sonido hace el perro?",
                "En nuestra misión apareció un perrito guardián. Quiere avisarnos algo. ¿Qué sonido hace el perro?",
                "Creo que hay un perro cerca de la casa. Se acercó muy contento. ¿Qué sonido hace el perro?",
                "Un perrito está jugando en el parque y quiere llamar nuestra atención. ¿Qué sonido hace el perro?",
                "Misión perrito: encontramos huellitas pequeñas en el camino. Ahora dime, ¿qué sonido hace el perro?",
                "Imagina a un perro saludando a su familia cuando llega a casa. ¿Qué sonido hace el perro?",
                "Nuestro primer animal tiene cola, orejas y muchas ganas de jugar. ¿Qué sonido hace el perro?",
                "El perrito de la misión quiere decirnos algo con su ladrido. ¿Qué sonido hace el perro?"
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "Segunda misión: entramos a una casita imaginaria. Allí vive una mascota muy querida. Menciona un animal doméstico.",
                "Ahora busquemos un animal que pueda vivir cerca de las personas. Menciona un animal doméstico.",
                "En esta parte de la aventura necesitamos encontrar una mascota. ¿Qué animal doméstico puedes mencionar?",
                "Imagina una casa con una camita pequeña, comida y agua para una mascota. Menciona un animal doméstico.",
                "La misión ahora es pensar en un animal que una familia pueda cuidar en casa. Dime uno.",
                "Hay animalitos que acompañan a las personas y reciben mucho cariño. Menciona un animal doméstico.",
                "Abrimos una puerta imaginaria y vemos una mascota esperando. ¿Qué animal doméstico puede ser?",
                "Ahora toca buscar un animal de casa, uno que pueda ser mascota. Menciona uno.",
                "Nuestro juguete necesita ordenar las mascotas. Ayúdame: menciona un animal doméstico.",
                "Pensemos en un animal que pueda vivir con una familia. ¿Cuál puede ser?"
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "Tercera misión: escuché un sonido suavecito cerca de la ventana. Creo que hay un gatito. ¿Qué sonido hace el gato?",
                "Shhh... caminemos despacio. Hay un gatito curioso mirándonos. ¿Qué sonido hace el gato?",
                "Imagina un gato pequeño jugando con una bolita de lana. De pronto quiere hablarnos. ¿Qué sonido hace el gato?",
                "Ahora apareció un animal con bigotes y patitas suaves. ¿Qué sonido hace el gato?",
                "El gatito de nuestra misión quiere llamar a su mamá. ¿Qué sonido hace el gato?",
                "Veo unos bigotes asomándose detrás de una silla. Creo que es un gato. ¿Qué sonido hace?",
                "Nuestro siguiente animal camina suavecito y a veces se acurruca. ¿Qué sonido hace el gato?",
                "Imagina que un gatito quiere pedir comida. ¿Qué sonido hace el gato?",
                "En esta parte de la aventura encontramos un gatito curioso. Dime, ¿qué sonido hace?",
                "El gatito se acercó despacito y quiere saludarnos. ¿Qué sonido hace el gato?"
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "Última misión: llegamos a la granja imaginaria. Hay pasto, corrales y muchos animales. Menciona un animal que encontremos en la granja.",
                "Ahora abrimos la tranquera de la granja. Veo varios animales caminando por ahí. Menciona uno.",
                "En esta parte final visitamos una granja llena de sonidos y movimiento. Dime un animal que encontremos allí.",
                "Imagina una granja con gallinas, corrales y mucho pasto. Menciona un animal de la granja.",
                "La última pista nos lleva al campo. Allí viven muchos animales. Dime uno que encontremos en la granja.",
                "Escucha la aventura: llegamos a un lugar con animales grandes y pequeños. Menciona un animal de granja.",
                "La granja nos espera. Hay animales que dan leche, ponen huevos o viven en corrales. Dime uno.",
                "Caminamos por la granja imaginaria y vemos muchos animalitos. ¿Cuál podemos encontrar?",
                "Último reto de explorador: piensa en una granja y menciona un animal que viva allí.",
                "Llegamos al final de la aventura animal. Ayúdame con un animal que encontremos en la granja."
            )
        )

        // ----- Feedback correcto ---------------------------------------------------

        val CORRECT_FEEDBACK: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "¡Sííí! ¡Era el perrito! El perro hace guau. Lo encontraste muy bien.",
                "¡Exacto! El perrito dice guau cuando ladra. Misión cumplida.",
                "¡Muy bien! Tu respuesta ayudó al perrito a recuperar su sonido.",
                "¡Eso es! El perro hace guau. Nuestro perrito imaginario está feliz.",
                "¡Excelente! Reconociste el sonido del perro.",
                "¡Lo lograste! El perrito ya puede ladrar otra vez: guau.",
                "¡Qué buena respuesta! El sonido del perro es guau.",
                "¡Genial! El perro ladra y hace guau. Sigamos con nuestra aventura.",
                "¡Correcto! Ese era el sonido que estábamos buscando.",
                "¡Muy bien, explorador! Encontraste el sonido del perro."
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "¡Muy bien! Ese animal puede ser doméstico. Lo encontraste.",
                "¡Exacto! Ese animal puede vivir cerca de las personas.",
                "¡Genial! Esa mascota sí pertenece al grupo de animales domésticos.",
                "¡Excelente respuesta! Ese animal puede acompañar a una familia.",
                "¡Lo lograste! Encontraste un animal doméstico.",
                "¡Muy bien pensado! Ese animal puede ser cuidado en casa.",
                "¡Sí! Ese animal puede ser una mascota.",
                "¡Qué buena respuesta! Ese animal puede vivir cerca de nosotros.",
                "¡Misión cumplida! Encontramos un animal doméstico.",
                "¡Bien hecho! Ese animal sí puede estar en una casa."
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "¡Sííí! ¡Era el gatito! El gato hace miau.",
                "¡Exacto! El gatito maúlla y dice miau.",
                "¡Muy bien! Encontraste el sonido del gato.",
                "¡Excelente! El gato hace miau con su vocecita suave.",
                "¡Lo lograste! Nuestro gatito imaginario ya tiene su sonido.",
                "¡Genial! El sonido del gato es miau.",
                "¡Correcto! El gato maúlla cuando quiere llamar la atención.",
                "¡Muy buena respuesta! Ese era el sonido del gatito.",
                "¡Bien hecho! El gato dice miau.",
                "¡Misión gatito cumplida! El gato hace miau."
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "¡Muy bien! Ese animal puede vivir en la granja.",
                "¡Exacto! En la granja podemos encontrar ese animal.",
                "¡Genial! Encontraste un animal de la granja.",
                "¡Excelente respuesta! Ese animal pertenece a nuestra granja imaginaria.",
                "¡Lo lograste! La granja ya tiene otro animal en su lugar.",
                "¡Muy bien pensado! Ese animal puede estar en una granja.",
                "¡Misión cumplida! Reconociste un animal de granja.",
                "¡Qué buena respuesta! Ese animal puede vivir en el campo o en la granja.",
                "¡Correcto! Ese es un animal que podemos encontrar en la granja.",
                "¡Bien hecho! Nuestra granja imaginaria está más completa."
            )
        )

        // ----- Feedback incorrecto con reintento -----------------------------------

        val INCORRECT_RETRY: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "Mmm, mi radar de perritos no está seguro. Intentemos otra vez.",
                "Pensemos en un perrito cuando ladra para saludar. Probemos nuevamente.",
                "No pasa nada. Cerremos los ojitos un momento e imaginemos a un perro.",
                "Vamos otra vez. ¿Qué sonido haría un perrito emocionado?",
                "Creo que el perrito quiere que lo escuchemos mejor. Intentemos de nuevo.",
                "Probemos otra pista: este animal ladra cuando quiere avisar algo.",
                "El perrito todavía está escondiendo su sonido. Vamos a intentarlo otra vez.",
                "No te preocupes, las misiones se resuelven con calma. Pensemos otra vez.",
                "Escuchemos con la imaginación a un perro en el patio. Intenta responder.",
                "Vamos a darle otra oportunidad al perrito. ¿Qué sonido hace?"
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "Pensemos otra vez. Buscamos un animal que pueda vivir con las personas.",
                "No pasa nada. Imagina una mascota dentro de una casa.",
                "Probemos de nuevo. ¿Qué animal podría cuidar una familia?",
                "La pista es: puede vivir cerca de nosotros y recibir cariño.",
                "Vamos con calma. Piensa en un animal que hayas visto como mascota.",
                "Creo que necesitamos otra pista: algunos animales domésticos duermen en casa.",
                "Intentemos otra vez. Puede ser un animal pequeño o grande, pero cercano a las personas.",
                "Respira tranquilo. Imagina una casa con una mascota.",
                "A ver, a ver... ¿qué animal puede acompañar a una familia?",
                "La misión sigue. Pensemos en una mascota conocida."
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "Mmm, el gatito habló muy bajito. Intentemos otra vez.",
                "Pensemos en un gatito pequeño llamando a su mamá.",
                "Vamos con calma. ¿Qué sonido hace un gato cuando maúlla?",
                "El gatito está escondido, pero podemos recordar su sonido.",
                "Probemos nuevamente. Imagina a un gato pidiendo comida.",
                "No pasa nada. Escuchemos al gatito con la imaginación.",
                "Creo que el gatito quiere que lo intentemos otra vez.",
                "Respira tranquilo. Piensa en un gato haciendo su sonido.",
                "Vamos a repetir la misión del gatito.",
                "El gatito está cerquita. ¿Qué sonido hace?"
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "Pensemos otra vez. En una granja hay animales que viven en corrales o en el campo.",
                "No pasa nada. Imagina una granja con pasto, animales y corrales.",
                "Probemos nuevamente. ¿Qué animal podrías ver en una granja?",
                "La pista es: puede ser un animal que dé leche, ponga huevos o viva en un corral.",
                "Vamos con calma. Recuerda algún animal que hayas visto en una granja.",
                "La granja tiene muchos animales. Intentemos encontrar uno.",
                "Respira tranquilo. Imagina que caminas por una granja.",
                "A ver, a ver... ¿qué animal vive en el campo o en un corral?",
                "No te preocupes. La misión final todavía puede resolverse.",
                "Pensemos juntos en animales grandes o pequeños de una granja."
            )
        )

        // ----- Feedback incorrecto sin intentos restantes -------------------------

        val INCORRECT_NEXT: Map<LocalMediationKey, List<String>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
                "Buen intento. El perro hace guau. El perrito ya recuperó su sonido.",
                "Está bien, lo intentaste. El sonido que buscábamos era guau.",
                "No pasa nada. Los perros ladran y hacen guau.",
                "Gracias por ayudarme. Ahora recordamos que el perro dice guau.",
                "El perrito nos dejó una pista: cuando ladra, hace guau.",
                "Muy bien por participar. El sonido del perro es guau.",
                "Lo intentaste con ganas. El perrito hace guau cuando ladra.",
                "Aprendimos algo juntos: el perro dice guau.",
                "Está bien. Guardemos el sonido guau para el perrito.",
                "Gracias por intentarlo. Nuestro perrito imaginario ya tiene su sonido."
            ),
            LocalMediationKey.ANIMAL_DOMESTIC to listOf(
                "Está bien, lo intentaste. Algunos animales domésticos son el perro, el gato o el conejo.",
                "Buen intento. Un animal doméstico puede ser un perro, un gato o un hámster.",
                "No te preocupes. Los animales domésticos son los que pueden vivir cerca de las personas.",
                "Gracias por intentarlo. Por ejemplo, el perro y el gato son animales domésticos.",
                "Aprendimos juntos que una mascota puede ser un animal doméstico.",
                "Está bien. El conejo, la tortuga o el pez también pueden ser animales domésticos.",
                "Lo intentaste con ganas. Los animales domésticos pueden vivir con una familia.",
                "Guardemos esta idea: algunos animales domésticos viven en casa.",
                "Muy bien por participar. Encontramos ejemplos como perro, gato y conejo.",
                "La misión nos enseñó algo: una mascota puede ser un animal doméstico."
            ),
            LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
                "Está bien, lo intentaste. El gato hace miau.",
                "Buen intento. El sonido del gato es miau.",
                "No te preocupes. Los gatos maúllan y hacen miau.",
                "Gracias por ayudarme. Ahora recordamos que el gato dice miau.",
                "El gatito nos dejó una pista: cuando maúlla, hace miau.",
                "Muy bien por participar. El gato hace miau con su vocecita.",
                "Lo intentaste con calma. El sonido que buscábamos era miau.",
                "Aprendimos juntos que el gato dice miau.",
                "Guardemos este sonido para el gatito: miau.",
                "El gatito ya recuperó su sonido. Hace miau."
            ),
            LocalMediationKey.ANIMAL_FARM to listOf(
                "Está bien, lo intentaste. En la granja podemos encontrar vacas, gallinas, cerdos y caballos.",
                "Buen intento. Algunos animales de la granja son la vaca, la oveja y el pato.",
                "No te preocupes. En una granja viven animales como gallinas, caballos y cabras.",
                "Gracias por participar. Ahora recordamos algunos animales de la granja.",
                "Está bien. En la granja podemos encontrar muchos animales, como la vaca y la gallina.",
                "Muy bien por intentarlo. Algunos animales de granja son el cerdo, el caballo y la oveja.",
                "Gracias por responder. En una granja también podemos encontrar patos, cabras y pollitos.",
                "No pasa nada. Aprendimos juntos sobre los animales de la granja.",
                "Buen esfuerzo. Una granja puede tener vacas, gallinas y caballos.",
                "La granja nos enseñó algo hoy: allí viven muchos animales diferentes."
            )
        )
    }
}
