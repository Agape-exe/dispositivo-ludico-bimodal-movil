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
 * ## Mini historias coherentes por escenario
 *
 * Cada clave de mediacion de animales tiene varios [AnimalNarrativeScenario]. Al
 * iniciar una pregunta se selecciona un escenario y se RECUERDA hasta que la
 * pregunta termina o se vuelve a presentar (reintento): la introduccion y la
 * retroalimentacion de esa pregunta se eligen siempre dentro del mismo escenario,
 * de modo que la historia tenga sentido (no se mezcla la intro "el gatito perdio su
 * voz" con un feedback de "el gatito de la ventana").
 *
 * Reglas que respetan todos los conjuntos de frases:
 *  - Nunca se revela la respuesta esperada mientras quedan intentos.
 *  - Ante error tecnico, silencio o respuesta no interpretable nunca se culpa al nino.
 *  - No se usan frases de acierto parcial ("estas cerca", "casi lo tienes", etc.).
 *  - En la ultima pregunta, se evitan frases de continuidad ("continuemos",
 *    "pasemos a la siguiente", "vamos con otra").
 *  - Los inicios de feedback son variados para no sonar repetitivos ("Lo lograste"
 *    convive con "Eso era", "Encontraste la pista", "Ahora si", etc.).
 *
 * Para claves no reconocidas o vacias ([LocalMediationKey.NONE]) la introduccion y
 * la retroalimentacion contextual se delegan a [GeneralTeacherFeedbackGenerator].
 * Tambien se recurre al respaldo general si, por cualquier motivo, un escenario
 * tuviera una lista de frases vacia: el flujo nunca se queda sin frase.
 *
 * Mantiene estado mutable (el escenario activo por clave y el ultimo indice elegido
 * por conjunto) para no repetir y para mantener la coherencia narrativa, por lo que
 * debe existir una instancia por sesion. El generador de aleatoriedad es inyectable
 * para facilitar las pruebas.
 */
class AnimalMediationBank(
    private val random: Random = Random.Default,
    private val generalFallback: GeneralTeacherFeedbackGenerator = GeneralTeacherFeedbackGenerator(),
    private val scenarios: Map<LocalMediationKey, List<AnimalNarrativeScenario>> = SCENARIOS
) {

    private val lastIndexByGroup = HashMap<String, Int>()

    /** Escenario actualmente activo por clave de mediacion (coherencia intro/feedback). */
    private val activeScenarioByKey = HashMap<LocalMediationKey, AnimalNarrativeScenario>()

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
     * Identificador del escenario activo para la clave dada, o null si no hay uno
     * seleccionado todavia (o la clave no tiene escenarios). Util para diagnostico y
     * pruebas: permite verificar que el feedback proviene del mismo escenario que la
     * introduccion.
     */
    fun currentScenarioId(mediationKey: String?): String? =
        activeScenarioByKey[LocalMediationKey.fromKey(mediationKey)]?.id

    /**
     Mini escena narrativa antes de una pregunta segun su clave de mediacion.
     */
    fun getQuestionIntroduction(mediationKey: String?): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val scenario = selectScenario(key)
            ?: return generalFallback.message(GeneralTeacherFeedbackType.QUESTION_INTRO).text
        return pickFromScenario("INTRO", scenario, scenario.intro) {
            generalFallback.message(GeneralTeacherFeedbackType.QUESTION_INTRO).text
        }
    }

    /**
     * Retroalimentacion para una respuesta correcta segun la clave de mediacion,
     * dentro del escenario activo de la pregunta.
     */
    fun getCorrectFeedback(mediationKey: String?, isLastQuestion: Boolean = false): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val scenario = activeScenario(key)
            ?: return generalFallback.message(GeneralTeacherFeedbackType.CORRECT).text
        return pickContextual(
            prefix = "CORRECT",
            scenario = scenario,
            options = scenario.correctFeedback,
            isLastQuestion = isLastQuestion,
            general = { generalFallback.message(GeneralTeacherFeedbackType.CORRECT).text }
        )
    }

    /**
     * Retroalimentacion para una respuesta incorrecta cuando aun quedan intentos,
     * dentro del escenario activo. Nunca revela la respuesta esperada: solo invita a
     * pensar de nuevo.
     */
    fun getIncorrectRetryFeedback(mediationKey: String?): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val scenario = activeScenario(key)
            ?: return generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_RETRY).text
        return pickFromScenario("RETRY", scenario, scenario.incorrectRetryFeedback) {
            generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_RETRY).text
        }
    }

    /**
     * Retroalimentacion para una respuesta incorrecta sin intentos restantes, dentro
     * del escenario activo. Aqui si puede mencionarse la respuesta correcta de forma
     * amable.
     *
     * En la ultima pregunta ([isLastQuestion]) se excluyen frases con continuidad.
     */
    fun getIncorrectNextFeedback(mediationKey: String?, isLastQuestion: Boolean): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val scenario = activeScenario(key)
            ?: return if (isLastQuestion) getSessionCompletedPhrase()
            else generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_NEXT).text
        return pickContextual(
            prefix = "NEXT",
            scenario = scenario,
            options = scenario.incorrectNextFeedback,
            isLastQuestion = isLastQuestion,
            general = {
                if (isLastQuestion) getSessionCompletedPhrase()
                else generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_NEXT).text
            }
        )
    }

    // ----- Seleccion de escenario ---------------------------------------------

    /**
     * Selecciona un escenario nuevo para la clave (evitando repetir el escenario
     * anterior cuando hay alternativas) y lo recuerda como activo. Devuelve null si
     * la clave no tiene escenarios (claves generales o no reconocidas).
     */
    private fun selectScenario(key: LocalMediationKey): AnimalNarrativeScenario? {
        val keyScenarios = scenarios[key]?.takeIf { it.isNotEmpty() } ?: return null
        val index = pickIndex("SCENARIO_${key.name}", keyScenarios.size)
        val scenario = keyScenarios[index]
        activeScenarioByKey[key] = scenario
        return scenario
    }

    /**
     * Escenario activo de la clave; si todavia no hay uno (p. ej. el feedback se pide
     * sin que se haya presentado la intro), selecciona uno bajo demanda.
     */
    private fun activeScenario(key: LocalMediationKey): AnimalNarrativeScenario? =
        activeScenarioByKey[key] ?: selectScenario(key)

    /**
     * Elige una frase del escenario, aislando el estado anti-repeticion por escenario
     * (con el id en la clave de grupo) y recurriendo al respaldo general si la lista
     * estuviera vacia. Garantiza que nunca se devuelva texto en blanco.
     */
    private fun pickFromScenario(
        prefix: String,
        scenario: AnimalNarrativeScenario,
        options: List<String>,
        general: () -> String
    ): String {
        if (options.isEmpty()) return general()
        return pick("${prefix}_${scenario.id}", options)
    }

    /**
     * Igual que [pickFromScenario], pero aplica la regla de la ultima pregunta:
     * excluye las frases de continuidad y, si no quedara ninguna, usa el respaldo.
     */
    private fun pickContextual(
        prefix: String,
        scenario: AnimalNarrativeScenario,
        options: List<String>,
        isLastQuestion: Boolean,
        general: () -> String
    ): String {
        if (options.isEmpty()) return general()
        if (!isLastQuestion) return pick("${prefix}_${scenario.id}", options)
        val neutral = options.filterNot(::hasContinuation)
        return if (neutral.isEmpty()) general()
        else pick("${prefix}_LAST_${scenario.id}", neutral)
    }

    /** Elige un indice de [size] opciones evitando repetir el ultimo del grupo. */
    private fun pickIndex(group: String, size: Int): Int {
        require(size > 0) { "El conjunto $group no tiene opciones" }
        if (size == 1) {
            lastIndexByGroup[group] = 0
            return 0
        }
        val lastIndex = lastIndexByGroup[group]
        val index = if (lastIndex == null) {
            random.nextInt(size)
        } else {
            val candidate = random.nextInt(size - 1)
            if (candidate >= lastIndex) candidate + 1 else candidate
        }
        lastIndexByGroup[group] = index
        return index
    }

    private fun pick(group: String, options: List<String>): String {
        require(options.isNotEmpty()) { "El conjunto $group no tiene frases" }
        return options[pickIndex(group, options.size)]
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

        // ----- Escenarios narrativos por clave de mediacion ------------------------
        // Cada escenario es una mini historia coherente: la intro plantea una
        // situacion y todos sus feedbacks pertenecen a esa misma situacion. Los
        // inicios de feedback se distribuyen para no sonar repetitivos.

        private val DOG_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "DOG_LOST_BARK",
                intro = listOf(
                    "Primera misión: el perrito de la aventura se quedó sin su ladrido y no sabe cómo avisar a su familia. Ayúdalo a recordarlo. ¿Qué sonido hace el perro?",
                    "Mi perrito de juguete olvidó cómo ladrar y está un poco confundido. Recordemos su sonido juntos. ¿Qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Eso era! El perrito recuperó su ladrido y ya puede avisar a su familia: guau.",
                    "¡Lo resolvimos juntos! El perrito volvió a ladrar contento: guau.",
                    "¡Qué buena ayuda! Gracias a ti el perrito recordó su guau.",
                    "¡El animalito ya está feliz! El perrito volvió a hacer guau."
                ),
                incorrectRetryFeedback = listOf(
                    "El perrito todavía no encuentra su ladrido. Pensemos otra vez, sin prisa.",
                    "Mmm, su sonido sigue escondido. Imagina al perrito avisando a su familia e inténtalo de nuevo.",
                    "Aún no aparece su ladrido. Cerremos los ojitos y probemos una vez más."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. El perrito ladra y hace guau; ya recordó su sonido.",
                    "Gracias por ayudarlo. El sonido que buscaba el perrito era guau.",
                    "Lo intentaste con ganas. Al final el perrito recordó su guau."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_AT_GATE",
                intro = listOf(
                    "Escucha... un perrito llegó a la reja moviendo la colita y quiere saludarnos. ¿Qué sonido hace el perro?",
                    "Veo un perrito feliz esperando en la puerta para darnos la bienvenida. ¿Qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Ahora sí! Era el perrito de la reja saludándonos con su guau.",
                    "¡Qué buen oído! El perrito de la puerta hacía guau.",
                    "¡Me ayudaste mucho! Ese era el saludo del perrito: guau.",
                    "¡Respuesta encontrada! El perrito de la reja decía guau."
                ),
                incorrectRetryFeedback = listOf(
                    "El perrito de la reja sigue esperando. Escuchémoslo otra vez. ¿Qué sonido hace?",
                    "Volvamos a la puerta y pongamos atención al perrito. Inténtalo de nuevo.",
                    "Todavía no es ese. Imagina al perrito saludando y probemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Está bien, lo intentaste. El perrito de la reja saluda con un guau.",
                    "No pasa nada. El sonido del perrito de la puerta era guau.",
                    "Gracias por intentarlo. El perrito saludaba haciendo guau."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_PLAY_PARK",
                intro = listOf(
                    "Un perrito está corriendo en el parque detrás de su pelota y quiere llamarnos a jugar. ¿Qué sonido hace el perro?",
                    "Imagina un perrito muy juguetón en el parque que nos quiere invitar a correr. ¿Qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Ese era el sonido! El perrito del parque nos llamó a jugar: guau.",
                    "¡Encontraste la pista! El perrito juguetón hacía guau.",
                    "¡El animalito ya está feliz! El perrito siguió jugando y haciendo guau.",
                    "¡Qué buena ayuda! El perrito del parque te respondió con un guau."
                ),
                incorrectRetryFeedback = listOf(
                    "El perrito del parque todavía espera para jugar. Probemos otra vez.",
                    "Aún no es ese sonido. Imagina al perrito con su pelota e inténtalo de nuevo.",
                    "Pensemos otra vez en el perrito juguetón. ¿Qué sonido hará?"
                ),
                incorrectNextFeedback = listOf(
                    "Buen esfuerzo. El perrito del parque nos llamaba con un guau.",
                    "Lo intentaste con ánimo. El sonido del perrito juguetón era guau.",
                    "Está bien. Al final el perrito siguió jugando y haciendo guau."
                )
            )
        )

        private val DOMESTIC_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "DOMESTIC_NEW_HOME",
                intro = listOf(
                    "Segunda misión: una familia preparó una camita y un plato porque va a recibir una mascota nueva. Menciona un animal doméstico.",
                    "Imagina que tocan la puerta y llega una mascota para vivir con una familia. Menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Eso era! Ese animalito puede vivir feliz con una familia en casa.",
                    "¡Qué buena ayuda! Esa mascota encontró su nuevo hogar.",
                    "¡Encontraste la pista! Ese animal sí puede ser una mascota de casa.",
                    "¡Me ayudaste mucho! Esa mascota ya tiene su camita lista."
                ),
                incorrectRetryFeedback = listOf(
                    "Pensemos otra vez en un animal que una familia pueda cuidar en casa.",
                    "Aún no es ese. Imagina una mascota durmiendo en su camita e inténtalo de nuevo.",
                    "Probemos una vez más con un animal que viva cerca de las personas."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. Un animal doméstico puede ser el perro, el gato o el conejo.",
                    "Está bien. Algunas mascotas de casa son el perro, el gato o el hámster.",
                    "Gracias por intentarlo. La tortuga, el pez o el conejo también viven en casa."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOMESTIC_PET_FRIEND",
                intro = listOf(
                    "Busquemos un amiguito animal que acompaña a las personas y recibe muchos mimos. Menciona un animal doméstico.",
                    "Hay animalitos que viven con nosotros y nos hacen compañía cada día. Menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Ahora sí! Ese animalito puede ser un gran compañero en casa.",
                    "¡Me ayudaste mucho! Esa mascota acompaña muy bien a una familia.",
                    "¡Ese era! Ese animal puede vivir cerquita de las personas.",
                    "¡Qué buena idea! Ese amiguito animal nos hace muy buena compañía."
                ),
                incorrectRetryFeedback = listOf(
                    "Pensemos en un animalito que nos haga compañía en casa. Inténtalo otra vez.",
                    "Aún no es ese. Recuerda alguna mascota que hayas visto y probemos de nuevo.",
                    "Sigue buscando un amiguito animal que viva con una familia."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. Un buen compañero de casa puede ser el perro o el gato.",
                    "Está bien. El conejo, el hámster o el pez también acompañan a una familia.",
                    "Gracias por participar. Muchas mascotas, como el gato, viven cerca de nosotros."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOMESTIC_HOUSE_VISIT",
                intro = listOf(
                    "Entramos a una casita imaginaria y vemos a una mascota esperando en su rincón. Menciona un animal doméstico.",
                    "Abrimos la puerta de una casa y dentro vive un animalito muy querido. Menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Respuesta encontrada! Esa mascota vive muy bien dentro de una casa.",
                    "¡Qué buena idea! Ese animal puede acompañar a la familia de la casa.",
                    "¡Lo resolvimos juntos! Ese animalito sí puede ser una mascota.",
                    "¡Eso era! Esa mascota encaja perfecto en la casita imaginaria."
                ),
                incorrectRetryFeedback = listOf(
                    "Miremos de nuevo dentro de la casa. ¿Qué mascota podría vivir ahí?",
                    "Aún no es ese. Imagina el rincón de una mascota en casa e inténtalo otra vez.",
                    "Pensemos una vez más en un animal que viva dentro de una casa."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. En una casa pueden vivir un perro, un gato o un conejo.",
                    "Está bien. Una mascota de casa puede ser el gato, el pez o el hámster.",
                    "Gracias por intentarlo. Muchos animalitos, como el perro, viven en casa."
                )
            )
        )

        private val CAT_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "CAT_LOST_VOICE",
                intro = listOf(
                    "Tercera misión: el gatito de la aventura perdió su miau y no encuentra su voz. Ayúdame a recordarlo. ¿Qué sonido hace el gato?",
                    "Mi gatito de juguete se quedó sin su vocecita y está triste. Recuperemos su sonido juntos. ¿Qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Qué buena ayuda! El gatito recuperó su miau y ya tiene su voz.",
                    "¡Lo resolvimos juntos! El gatito volvió a maullar: miau.",
                    "¡El animalito ya está feliz! Ayudaste al gatito a recordar su miau.",
                    "¡Eso era! El gatito encontró su voz otra vez: miau."
                ),
                incorrectRetryFeedback = listOf(
                    "El gatito todavía no encuentra su sonido. Pensemos otra vez, con calma.",
                    "Su vocecita sigue escondida. Imagina al gatito buscando su voz e inténtalo de nuevo.",
                    "Aún no aparece su sonido. Probemos una vez más, sin apuro."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. El gatito maúlla y hace miau; ya recuperó su voz.",
                    "Gracias por ayudarlo. El sonido que el gatito buscaba era miau.",
                    "Lo intentaste con cariño. Al final el gatito recordó su miau."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_WINDOW",
                intro = listOf(
                    "Shhh... escuché un sonido suave cerca de la ventana. Creo que hay un gatito. ¿Qué sonido hace el gato?",
                    "Veo unos bigotes asomándose por la ventana. Parece un gatito curioso. ¿Qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Ahora sí! Era el gatito de la ventana haciendo miau.",
                    "¡Qué buen oído! Escuchaste al gatito de la ventana: hacía miau.",
                    "¡Encontraste la pista! El gatito de la ventana decía miau.",
                    "¡Eso era! El sonido junto a la ventana era el miau del gatito."
                ),
                incorrectRetryFeedback = listOf(
                    "Escuchemos otra vez al gatito de la ventana. ¿Qué sonido hace?",
                    "El gatito sigue junto a la ventana. Pongamos atención e inténtalo de nuevo.",
                    "Todavía no es ese. Acerquémonos despacito a la ventana y probemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Está bien, lo intentaste. El gatito de la ventana hace miau.",
                    "No pasa nada. Ese sonido cerca de la ventana era un miau.",
                    "Gracias por intentarlo. El gatito de la ventana maullaba: miau."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_HUNGRY",
                intro = listOf(
                    "Un gatito se acercó despacito a su plato porque tiene hambre y quiere pedir comida. ¿Qué sonido hace el gato?",
                    "Imagina un gatito suave frotándose en tus piernas para pedir su comida. ¿Qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Ese era el sonido! El gatito pidió su comida con un miau.",
                    "¡Me ayudaste mucho! El gatito hambriento hacía miau.",
                    "¡Respuesta encontrada! Así pedía comida el gatito: miau.",
                    "¡Qué buena ayuda! El gatito recibió su comida después de su miau."
                ),
                incorrectRetryFeedback = listOf(
                    "El gatito todavía tiene hambre y espera. ¿Qué sonido hará para pedir comida?",
                    "Aún no es ese. Imagina al gatito junto a su plato e inténtalo otra vez.",
                    "Pensemos de nuevo en el gatito pidiendo su comida. Probemos una vez más."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. El gatito pedía su comida haciendo miau.",
                    "Lo intentaste con ganas. El sonido del gatito hambriento era miau.",
                    "Está bien. Al final el gatito pidió comida con un miau."
                )
            )
        )

        private val FARM_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "FARM_OPEN_GATE",
                intro = listOf(
                    "Última misión: abrimos la tranquera de la granja y muchos animales nos esperan adentro. Menciona un animal de la granja.",
                    "Llegamos a la granja imaginaria y se escuchan muchos animales tras la cerca. Menciona un animal de la granja."
                ),
                correctFeedback = listOf(
                    "¡Ese era! Ese animalito vive muy bien en nuestra granja.",
                    "¡Qué buena ayuda! La granja ya tiene a ese animal en su lugar.",
                    "¡Encontraste la pista! Ese animal sí lo vemos en la granja.",
                    "¡El animalito ya está feliz! Ese animal encontró su lugar en la granja."
                ),
                incorrectRetryFeedback = listOf(
                    "Miremos de nuevo dentro de la granja. ¿Qué animal podría vivir ahí?",
                    "Aún no es ese. Imagina los corrales llenos de animales e inténtalo otra vez.",
                    "Pensemos una vez más en un animal que viva en la granja."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. En la granja viven la vaca, la gallina y el caballo.",
                    "Está bien. Algunos animales de granja son la oveja, el cerdo y el pato.",
                    "Gracias por intentarlo. La cabra, la vaca o la gallina también viven en la granja."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_MILK_EGGS",
                intro = listOf(
                    "En la granja hay animales que nos dan leche o ponen huevos cada mañana. Menciona un animal de la granja.",
                    "Imagina una granja con corrales, pasto y animales que nos dan alimento. Menciona un animal de la granja."
                ),
                correctFeedback = listOf(
                    "¡Ahora sí! Ese animal trabaja muy bien en nuestra granja.",
                    "¡Me ayudaste mucho! Ese animalito pertenece a la granja.",
                    "¡Respuesta encontrada! Ese animal lo encontramos en el campo o la granja.",
                    "¡Eso era! Ese animal de la granja nos da su alimento cada día."
                ),
                incorrectRetryFeedback = listOf(
                    "Pensemos en un animal que viva en la granja y nos dé alimento. Inténtalo otra vez.",
                    "Aún no es ese. Imagina el corral por la mañana y probemos de nuevo.",
                    "Sigue pensando en un animal de la granja. ¿Cuál podría ser?"
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. En la granja, la vaca da leche y la gallina pone huevos.",
                    "Está bien. Animales de granja son la vaca, la gallina, la oveja y el cerdo.",
                    "Gracias por participar. El caballo, el pato y la cabra también viven en la granja."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_FIELD_WALK",
                intro = listOf(
                    "Caminamos por el campo de la granja y vemos animales grandes y pequeños por todos lados. Menciona un animal de la granja.",
                    "El último reto nos lleva a recorrer la granja entre el pasto y los corrales. Menciona un animal de la granja."
                ),
                correctFeedback = listOf(
                    "¡Eso era! Ese animalito lo encontramos paseando por la granja.",
                    "¡Qué buen oído de explorador! Ese animal vive en la granja.",
                    "¡Lo resolvimos juntos! Ese animal pertenece a nuestra granja imaginaria.",
                    "¡Encontraste la pista! Ese animal pasea tranquilo por la granja."
                ),
                incorrectRetryFeedback = listOf(
                    "Sigamos caminando por la granja. ¿Qué animal podríamos encontrar?",
                    "Aún no es ese. Imagina el campo lleno de animales e inténtalo otra vez.",
                    "Pensemos una vez más en un animal que pasee por la granja."
                ),
                incorrectNextFeedback = listOf(
                    "Buen intento. Por la granja pasean la vaca, el caballo y la oveja.",
                    "Está bien. En el campo viven gallinas, cerdos, patos y cabras.",
                    "Gracias por intentarlo. La vaca, la gallina y el caballo viven en la granja."
                )
            )
        )

        /** Escenarios narrativos disponibles por clave de mediacion de animales. */
        val SCENARIOS: Map<LocalMediationKey, List<AnimalNarrativeScenario>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to DOG_SCENARIOS,
            LocalMediationKey.ANIMAL_DOMESTIC to DOMESTIC_SCENARIOS,
            LocalMediationKey.ANIMAL_CAT_SOUND to CAT_SCENARIOS,
            LocalMediationKey.ANIMAL_FARM to FARM_SCENARIOS
        )
    }
}
