package com.taller.app.bimodal.feedback

import com.taller.app.model.LocalMediationKey
import kotlin.random.Random

/**
 * Banco local de frases narrativas de Seven, el alien explorador, para la
 * actividad de animales en modo bimodal inteligente.
 *
 * Seven es un alien curioso y amigable que llegó a la Tierra para aprender
 * cosas nuevas con ayuda de los niños. No actúa como evaluador ni como
 * profesor: cada sesión es una mini aventura donde Seven descubre animales,
 * sonidos y objetos terrestres.
 *
 * ## Mini historias coherentes por escenario
 *
 * Cada clave de mediación de animales tiene diez [AnimalNarrativeScenario]. Al
 * iniciar una pregunta se selecciona un escenario y se recuerda hasta que la
 * pregunta termina: la introducción y la retroalimentación de esa pregunta se
 * eligen siempre dentro del mismo escenario, de modo que la historia tenga
 * sentido narrativo.
 *
 * Reglas que respetan todos los conjuntos de frases:
 *  - Nunca se culpa al niño por el resultado.
 *  - No se usan frases de acierto parcial.
 *  - No se usa la palabra "pista" como término principal.
 *  - En la última pregunta se evitan frases de continuidad.
 *  - Seven usa vocabulario de exploración espacial:
 *    reto, ronda, misión, señal, exploración, registro.
 *
 * Para claves no reconocidas o sin escenario ([LocalMediationKey.NONE]) la
 * introducción y la retroalimentación contextual se delegan a
 * [GeneralTeacherFeedbackGenerator]. El banco también ofrece
 * [getGenericIntroWithQuestion] para componer una intro de Seven con el texto
 * de la pregunta embebido, usada por la pantalla cuando la clave es NONE.
 */
class AnimalMediationBank(
    private val random: Random = Random.Default,
    private val generalFallback: GeneralTeacherFeedbackGenerator = GeneralTeacherFeedbackGenerator(),
    private val scenarios: Map<LocalMediationKey, List<AnimalNarrativeScenario>> = SCENARIOS
) {

    private val lastIndexByGroup = HashMap<String, Int>()

    private val activeScenarioByKey = HashMap<LocalMediationKey, AnimalNarrativeScenario>()

    fun getSessionStartPhrase(): String = pick("MISSION_START", MISSION_START)

    fun getSessionCompletedPhrase(): String = pick("MISSION_COMPLETED", MISSION_COMPLETED)

    fun getNotInterpretableFeedback(): String =
        pick("GENERAL_NOT_INTERPRETABLE", GENERAL_NOT_INTERPRETABLE)

    fun getNoResponseFeedback(): String = pick("GENERAL_NO_RESPONSE", GENERAL_NO_RESPONSE)

    fun getTechnicalErrorFeedback(): String =
        pick("GENERAL_TECHNICAL_ERROR", GENERAL_TECHNICAL_ERROR)

    fun getMicroDialogue(probabilityPercent: Int = 35): String? {
        if (random.nextInt(100) >= probabilityPercent) return null
        return pick("MICRO_DIALOGUE", MICRO_DIALOGUES)
    }

    /**
     * Intro genérica de Seven con el texto de la pregunta embebido, para usar
     * cuando la clave de mediación es NONE (sin escenario específico de animal).
     * Rellena el marcador {question} con [questionText].
     */
    fun getGenericIntroWithQuestion(questionText: String): String {
        val template = pick("GENERIC_INTRO_QUESTION", ADVANCED_QUESTION_INTRO_GENERAL)
        return template.replace("{question}", questionText.trim())
    }

    fun currentScenarioId(mediationKey: String?): String? =
        activeScenarioByKey[LocalMediationKey.fromKey(mediationKey)]?.id

    fun getQuestionIntroduction(mediationKey: String?): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val scenario = selectScenario(key)
            ?: return generalFallback.message(GeneralTeacherFeedbackType.QUESTION_INTRO).text
        return pickFromScenario("INTRO", scenario, scenario.intro) {
            generalFallback.message(GeneralTeacherFeedbackType.QUESTION_INTRO).text
        }
    }

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

    fun getIncorrectRetryFeedback(mediationKey: String?): String {
        val key = LocalMediationKey.fromKey(mediationKey)
        val scenario = activeScenario(key)
            ?: return generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_RETRY).text
        return pickFromScenario("RETRY", scenario, scenario.incorrectRetryFeedback) {
            generalFallback.message(GeneralTeacherFeedbackType.INCORRECT_RETRY).text
        }
    }

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

    // ----- Selección de escenario ------------------------------------------------

    private fun selectScenario(key: LocalMediationKey): AnimalNarrativeScenario? {
        val keyScenarios = scenarios[key]?.takeIf { it.isNotEmpty() } ?: return null
        val index = pickIndex("SCENARIO_${key.name}", keyScenarios.size)
        val scenario = keyScenarios[index]
        activeScenarioByKey[key] = scenario
        return scenario
    }

    private fun activeScenario(key: LocalMediationKey): AnimalNarrativeScenario? =
        activeScenarioByKey[key] ?: selectScenario(key)

    private fun pickFromScenario(
        prefix: String,
        scenario: AnimalNarrativeScenario,
        options: List<String>,
        general: () -> String
    ): String {
        if (options.isEmpty()) return general()
        return pick("${prefix}_${scenario.id}", options)
    }

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

        // ----- Apertura de misión (ADVANCED_SESSION_START) -----------------------

        val MISSION_START: List<String> = listOf(
            "¡Hola! Soy Seven, un alien explorador. Hoy necesito tu ayuda para aprender sobre los animales de la Tierra.",
            "¡Activando misión terrestre! Seven llegó del espacio para descubrir animalitos contigo.",
            "¡Hola, explorador de la Tierra! Mi nave detectó muchos sonidos de animales y necesito tu ayuda.",
            "Me contaron que en la Tierra viven criaturas muy curiosas. ¿Me ayudas a conocerlas?",
            "¡Seven reportándose desde su nave! Hoy quiero aprender sobre los animalitos que viven aquí.",
            "Mi radar espacial encontró una misión animal. Necesito una voz terrestre que me ayude.",
            "¡Qué emoción! Seven llegó a investigar animales, sonidos y lugares de la Tierra.",
            "Mi computadora espacial dice que tú conoces mejor este planeta. ¿Me ayudas en la misión?",
            "¡Bienvenido a la exploración de Seven! Hoy descubriremos animalitos terrestres.",
            "Seven tiene una misión especial: aprender de los animales con ayuda de un niño experto de la Tierra."
        )

        // ----- Cierre de misión (ADVANCED_SESSION_COMPLETED) --------------------

        val MISSION_COMPLETED: List<String> = listOf(
            "¡Misión completada! Seven aprendió mucho sobre los animales gracias a tu ayuda.",
            "¡Exploración terminada! Mi nave guardó nuevos datos de los animalitos de la Tierra.",
            "Seven está muy feliz. Hoy descubrimos criaturas terrestres juntos.",
            "¡Gracias por ayudarme! La misión animal quedó completada.",
            "Mi radar espacial terminó la exploración. Seven conoce más animales que antes.",
            "¡Misión guardada en la memoria de Seven! Gracias por enseñarme cosas de la Tierra.",
            "Hoy mi nave aprendió bastante sobre animales. Seven volverá con más retos pronto.",
            "¡Exploración finalizada! Tus respuestas ayudaron mucho a Seven.",
            "La misión llegó a su fin. Seven se despide con una sonrisa espacial.",
            "¡Gracias, explorador! Seven completó esta aventura animal."
        )

        // ----- Microdiálogos opcionales de Seven ---------------------------------

        val MICRO_DIALOGUES: List<String> = listOf(
            "Espera... Seven captó algo en su radar.",
            "Vamos despacito, como buenos exploradores espaciales.",
            "Activa tus orejitas de astronauta.",
            "A ver, a ver... Seven está pensando.",
            "Hay algo interesante en el mapa terrestre de Seven.",
            "Esta misión está muy emocionante.",
            "Seven necesita tu ayuda especial en esta parte.",
            "Vamos a imaginarlo juntos desde la nave.",
            "Shhh... Seven está escuchando con su antena.",
            "¡Qué emoción! Seven está descubriendo algo nuevo."
        )

        // ----- Intro genérica con pregunta (ADVANCED_QUESTION_INTRO_GENERAL) -----
        // Usa {question} como marcador que se reemplaza con el texto real.

        val ADVANCED_QUESTION_INTRO_GENERAL: List<String> = listOf(
            "Tengo un reto terrestre para ti: {question}",
            "Mi radar encontró una duda curiosa: {question}",
            "Ayúdame con este descubrimiento: {question}",
            "Seven necesita aprender esto: {question}",
            "Escucha este reto de exploración: {question}",
            "Mi nave quiere registrar una respuesta: {question}",
            "Tengo una misión pequeña para tu voz: {question}",
            "Vamos con un reto de la Tierra: {question}",
            "Mi antena detectó esta pregunta: {question}",
            "Seven está pensando en algo curioso: {question}"
        )

        // ----- No interpretable (ADVANCED_NOT_INTERPRETABLE) --------------------

        val GENERAL_NOT_INTERPRETABLE: List<String> = listOf(
            "Mi traductor espacial hizo chispitas y no entendió bien. ¿Puedes repetirlo?",
            "La señal llegó un poco borrosa a mi nave. Intentemos otra vez.",
            "Seven escuchó algo, pero su antena se confundió. Repítelo, por favor.",
            "Mi radar de voz no pudo ordenar esa señal. ¿Me ayudas repitiendo?",
            "Creo que mi traductor alienígena se mareó. Vamos otra vez.",
            "La señal de tu voz llegó incompleta. Intentemos nuevamente.",
            "Seven quiere entenderte bien. Dilo una vez más con tu voz.",
            "Mi nave captó sonidos, pero no logró convertirlos en respuesta.",
            "La antena espacial necesita otra señal más clara.",
            "Ups, Seven no logró entender esa respuesta. Probemos de nuevo."
        )

        // ----- Sin respuesta (ADVANCED_NO_RESPONSE) ------------------------------

        val GENERAL_NO_RESPONSE: List<String> = listOf(
            "No recibí ninguna señal de voz. Seven esperará otro intento.",
            "Mi antena no escuchó respuesta esta vez. Intentemos de nuevo.",
            "Parece que la señal no llegó a mi nave. Vamos otra vez.",
            "Seven se quedó esperando tu voz. Puedes ayudarme intentándolo otra vez.",
            "No llegó respuesta al radar espacial. Probemos nuevamente.",
            "Mi nave no detectó sonido. Hagamos otro intento.",
            "Esta vez el espacio se quedó en silencio. Seven seguirá atento.",
            "No escuché tu señal, pero todavía podemos intentarlo.",
            "La antena de Seven no recibió respuesta. Vamos con calma otra vez.",
            "No pasó nada, explorador. Seven esperará una nueva señal."
        )

        // ----- Error técnico (ADVANCED_TECHNICAL_ERROR) --------------------------

        val GENERAL_TECHNICAL_ERROR: List<String> = listOf(
            "Ups, mi nave tuvo una pequeña falla técnica. Seven se está recuperando.",
            "Mi sistema espacial hizo un ruidito extraño. Intentemos continuar.",
            "Hubo un problema en los controles de Seven, no fue culpa tuya.",
            "Mi antena tuvo una falla técnica. Seven intentará seguir con la misión.",
            "Algo se movió en mi nave y no pude procesar bien la señal.",
            "Seven detectó un error técnico, pero seguimos con calma.",
            "Mi computadora espacial se confundió un momento. Vamos a continuar.",
            "Hubo una interferencia en la nave. No te preocupes, seguimos.",
            "Mi sistema necesita un segundo para ordenarse. Seven sigue aquí.",
            "La nave tuvo una pequeña interferencia. Tu ayuda sigue siendo importante."
        )

        // ----- Escenarios narrativos de Seven por clave de mediación -------------
        // Cada escenario es una mini historia coherente: la intro plantea una
        // situación espacial y todos sus feedbacks pertenecen a esa misma historia.

        private val DOG_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "DOG_S01",
                intro = listOf(
                    "Me contaron que en la Tierra hay un animalito peludo que cuida la casa y mueve la cola. Seven quiere saber: ¿qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Guau detectado! Mi nave acaba de registrar el sonido del perrito terrestre."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi antena escuchó algo, pero todavía no encontró el ladrido del perrito. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no logró guardar el sonido del perrito esta vez, pero seguirá explorando."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S02",
                intro = listOf(
                    "Seven vio en su mapa un amigo de cuatro patas que acompaña a las personas. Ayúdame: ¿qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Ladrido registrado! Seven ya sabe cómo suena ese amigo de cuatro patas."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi radar sigue buscando el ladrido correcto. Probemos una vez más."
                ),
                incorrectNextFeedback = listOf(
                    "El sonido del perrito quedó pendiente en mi mapa espacial."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S03",
                intro = listOf(
                    "Mi nave escuchó que algunos perritos dicen algo cuando están felices o alertas. Dime: ¿qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Eso sonó como un perrito! Mi antena está dando saltitos espaciales."
                ),
                incorrectRetryFeedback = listOf(
                    "Seven no captó todavía el sonido del perrito. ¿Me ayudas de nuevo?"
                ),
                incorrectNextFeedback = listOf(
                    "Mi antena no logró reconocer el ladrido, pero la misión continúa."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S04",
                intro = listOf(
                    "En la Tierra hay un animal que puede ser muy buen amigo de los niños. Seven quiere aprender: ¿qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Guau guardado! Seven aprendió un nuevo sonido terrestre."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi traductor alienígena no encontró el sonido esperado. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven guardará este reto para practicarlo después."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S05",
                intro = listOf(
                    "Me dijeron que los perros a veces hacen ruido cuando saludan o cuidan su casa. Ayúdame: ¿qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Señal perruna recibida! Mi nave ya reconoce ese sonido."
                ),
                incorrectRetryFeedback = listOf(
                    "La señal llegó un poquito confundida. Seven necesita escuchar otro intento."
                ),
                incorrectNextFeedback = listOf(
                    "No logramos completar el sonido del perro, pero gracias por ayudar a Seven."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S06",
                intro = listOf(
                    "Seven encontró huellitas en su pantalla espacial. Parecen de un perro. ¿Qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Huellitas y guau conectados! Seven acaba de entenderlo."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi nave encontró las huellitas, pero no el sonido. Probemos de nuevo."
                ),
                incorrectNextFeedback = listOf(
                    "El sonido de esas huellitas quedó sin registrar por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S07",
                intro = listOf(
                    "Mi radar dice que el perro es un animal muy conocido en la Tierra. Seven pregunta: ¿qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Ladrido confirmado! Seven está aprendiendo muy rápido contigo."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi radar no confirmó el ladrido todavía. Vamos con otro intento."
                ),
                incorrectNextFeedback = listOf(
                    "El radar de Seven no confirmó ese sonido, pero seguimos con la misión."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S08",
                intro = listOf(
                    "Seven está armando una colección de sonidos terrestres. Empecemos con uno famoso: ¿qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Sonido famoso guardado! El perro ya está en mi colección espacial."
                ),
                incorrectRetryFeedback = listOf(
                    "La colección aún no tiene ese sonido. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "La colección de Seven dejó el sonido del perro incompleto por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S09",
                intro = listOf(
                    "Mi nave escuchó un 'guau' perdido en la Tierra, pero Seven no sabe de quién es. ¿Qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Misterio resuelto! Ese 'guau' era del perro."
                ),
                incorrectRetryFeedback = listOf(
                    "El misterio sigue abierto. Seven necesita otra respuesta."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no resolvió este misterio, pero buscará más señales después."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOG_S10",
                intro = listOf(
                    "Seven quiere saludar a un perrito terrestre, pero primero debe conocer su sonido. ¿Qué sonido hace el perro?"
                ),
                correctFeedback = listOf(
                    "¡Ahora Seven puede saludar al perrito con un guau espacial!"
                ),
                incorrectRetryFeedback = listOf(
                    "Seven todavía no sabe cómo saludarlo. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no pudo aprender ese saludo perruno esta vez."
                )
            )
        )

        private val DOMESTIC_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "DOM_S01",
                intro = listOf(
                    "Me contaron que algunos animalitos viven cerca de las familias y reciben mucho cariño. Seven quiere saber: menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Animal de casa registrado! Seven ya conoce un compañero terrestre."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi radar no encontró un animal de casa en esa señal. Probemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no logró registrar un animal doméstico esta vez, pero seguirá aprendiendo."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S02",
                intro = listOf(
                    "Seven está investigando animalitos que pueden vivir con las personas. Ayúdame: menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Compañero terrestre guardado! Mi nave ya tiene un nuevo dato."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi computadora espacial necesita otro ejemplo de animal doméstico."
                ),
                incorrectNextFeedback = listOf(
                    "El registro de animales domésticos quedó pendiente por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S03",
                intro = listOf(
                    "En mi planeta no tenemos mascotas como en la Tierra. Seven quiere aprender: menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Mascota terrestre aprendida! Seven está muy curioso por conocer más."
                ),
                incorrectRetryFeedback = listOf(
                    "Seven todavía no reconoció una mascota terrestre. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no logró guardar esa mascota, pero continuará la exploración."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S04",
                intro = listOf(
                    "Mi nave vio casas terrestres y cree que algunos animalitos viven allí. Dime un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Animalito de casa detectado! Mi mapa terrestre creció un poquito."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi mapa no encontró ese animalito de casa. Probemos de nuevo."
                ),
                incorrectNextFeedback = listOf(
                    "El mapa de Seven quedó sin ese animal doméstico por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S05",
                intro = listOf(
                    "Seven quiere saber qué animalitos acompañan a las personas en la Tierra. Menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Buen dato terrestre! Seven ya sabe de un animal que puede acompañar a las personas."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi antena necesita otro dato sobre animales que viven cerca de las personas."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no pudo completar esta parte, pero gracias por intentarlo."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S06",
                intro = listOf(
                    "Estoy creando una lista espacial de mascotas de la Tierra. Ayúdame con una: menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Mascota añadida a la lista espacial de Seven!"
                ),
                incorrectRetryFeedback = listOf(
                    "La lista espacial todavía necesita una mascota válida. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "La lista de mascotas quedó incompleta por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S07",
                intro = listOf(
                    "Mi radar encontró platos pequeños, camitas y juguetes. Creo que son para mascotas. Menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Registro de mascota completado! Seven entendió ese dato."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi radar de mascotas se confundió. Ayúdame con otro intento."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no pudo cerrar el registro de mascota esta vez."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S08",
                intro = listOf(
                    "Seven escuchó que algunos niños tienen animalitos en casa. Quiero aprender uno: menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Animal doméstico aprendido! Seven está guardando ese dato con cuidado."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi nave no reconoció ese animal como doméstico. Probemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "El dato quedó pendiente en mi memoria espacial."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S09",
                intro = listOf(
                    "En la Tierra, algunas criaturas viven con las familias. Seven pregunta: menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Criatura familiar registrada! Mi nave está muy contenta."
                ),
                incorrectRetryFeedback = listOf(
                    "La nave necesita otro ejemplo de criatura que viva con familias."
                ),
                incorrectNextFeedback = listOf(
                    "Seven dejará esta criatura familiar para otra exploración."
                )
            ),
            AnimalNarrativeScenario(
                id = "DOM_S10",
                intro = listOf(
                    "Seven quiere completar su álbum de animales cercanos a las personas. Menciona un animal doméstico."
                ),
                correctFeedback = listOf(
                    "¡Álbum actualizado! Seven agregó un animal doméstico."
                ),
                incorrectRetryFeedback = listOf(
                    "El álbum aún no puede guardar esa respuesta. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "El álbum de Seven quedó sin completar esta página."
                )
            )
        )

        private val CAT_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "CAT_S01",
                intro = listOf(
                    "Me contaron que hay un animalito con bigotes que camina suavecito. Seven quiere saber: ¿qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Miau registrado! Seven ya conoce el sonido del gato terrestre."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi antena escuchó algo, pero no encontró el miau. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no logró guardar el sonido del gato esta vez."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S02",
                intro = listOf(
                    "Seven vio un animal con cola y bigotes en su pantalla espacial. Ayúdame: ¿qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Sonido gatuno detectado! Mi nave acaba de aprender un miau."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi radar gatuno sigue confundido. Probemos de nuevo."
                ),
                incorrectNextFeedback = listOf(
                    "El radar de Seven no pudo registrar el sonido del gato."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S03",
                intro = listOf(
                    "Mi nave escuchó que los gatos hacen un sonido muy suave. Dime: ¿qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Miau confirmado! Seven está aprendiendo sonidos suaves de la Tierra."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi traductor no encontró el miau todavía. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "El miau quedó pendiente en la memoria de Seven."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S04",
                intro = listOf(
                    "Seven quiere saludar a un gatito, pero no sabe cómo suena. ¿Qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Ahora Seven puede saludar al gatito con un miau espacial!"
                ),
                incorrectRetryFeedback = listOf(
                    "Seven aún no sabe cómo saludar al gatito. Ayúdame otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no aprendió el saludo del gatito esta vez."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S05",
                intro = listOf(
                    "Me dijeron que los gatos son silenciosos, pero a veces hacen un sonido especial. ¿Qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Ese sonido especial quedó guardado! Seven ya conoce el miau."
                ),
                incorrectRetryFeedback = listOf(
                    "La señal llegó borrosa y Seven no reconoció el sonido especial."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no pudo guardar el sonido especial del gato por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S06",
                intro = listOf(
                    "Seven encontró unas patitas suaves en su mapa terrestre. Creo que son de un gato. ¿Qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Patitas suaves y miau conectados! Mi nave lo entendió."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi mapa encontró las patitas, pero no el sonido. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "El sonido de esas patitas quedó sin registrar por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S07",
                intro = listOf(
                    "Mi computadora espacial dice que los gatos pueden vivir en casas. Seven pregunta: ¿qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Miau aprendido! Seven ya sabe un poquito más de los gatos."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi computadora no escuchó el miau esperado. Probemos de nuevo."
                ),
                incorrectNextFeedback = listOf(
                    "Seven dejará el sonido del gato para practicarlo después."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S08",
                intro = listOf(
                    "Seven está creando una colección de sonidos animales. Ahora necesita uno de gato: ¿qué sonido hace?"
                ),
                correctFeedback = listOf(
                    "¡Colección actualizada! El miau ya está en la nave."
                ),
                incorrectRetryFeedback = listOf(
                    "La colección todavía no puede guardar ese sonido. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "La colección quedó sin el sonido del gato por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S09",
                intro = listOf(
                    "La nave de Seven detectó bigotes, orejas y una cola. Falta el sonido. ¿Qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Miau detectado! El dibujo del gato ya tiene sonido."
                ),
                incorrectRetryFeedback = listOf(
                    "El dibujo sigue sin sonido. Seven necesita otro intento."
                ),
                incorrectNextFeedback = listOf(
                    "El dibujo del gato quedó sin sonido en esta misión."
                )
            ),
            AnimalNarrativeScenario(
                id = "CAT_S10",
                intro = listOf(
                    "Seven cree que un gatito se escondió cerca de su nave. Para encontrarlo necesita saber: ¿qué sonido hace el gato?"
                ),
                correctFeedback = listOf(
                    "¡Miau encontrado! Seven pudo ubicar al gatito imaginario."
                ),
                incorrectRetryFeedback = listOf(
                    "El gatito imaginario sigue escondido. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "El gatito imaginario seguirá escondido por ahora."
                )
            )
        )

        private val FARM_SCENARIOS = listOf(
            AnimalNarrativeScenario(
                id = "FARM_S01",
                intro = listOf(
                    "Me han contado que en la granja hay muchos animalitos que ayudan con comida. Seven quiere aprender uno: menciona un animal de la granja."
                ),
                correctFeedback = listOf(
                    "¡Animal de granja registrado! La nave de Seven ya conoce un habitante de ese lugar."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi mapa de la granja no encontró ese animal todavía. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no logró completar el mapa de la granja esta vez."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S02",
                intro = listOf(
                    "Seven escuchó que en las granjas viven animales muy importantes para las personas. Ayúdame: menciona un animal de la granja."
                ),
                correctFeedback = listOf(
                    "¡Habitante de granja guardado! Seven está entendiendo ese lugar terrestre."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi radar de granja necesita otro animal. Probemos de nuevo."
                ),
                incorrectNextFeedback = listOf(
                    "El radar de granja quedó incompleto por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S03",
                intro = listOf(
                    "Mi nave vio un lugar con pasto, corrales y muchos sonidos. Creo que se llama granja. Menciona un animal que viva allí."
                ),
                correctFeedback = listOf(
                    "¡Registro de corral completado! Seven aprendió un animal de granja."
                ),
                incorrectRetryFeedback = listOf(
                    "El corral de mi mapa sigue vacío. Ayúdame con otro intento."
                ),
                incorrectNextFeedback = listOf(
                    "El corral espacial quedó sin animal esta vez."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S04",
                intro = listOf(
                    "Seven quiere conocer los animales que viven lejos de la ciudad, en un lugar llamado granja. Dime uno."
                ),
                correctFeedback = listOf(
                    "¡Animal rural aprendido! Seven guardó ese dato terrestre."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi nave no ubicó ese animal en la granja. Intentemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "Seven no pudo ubicar ese animal en la granja por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S05",
                intro = listOf(
                    "Me dijeron que en la granja algunos animales dan leche, huevos o lana. Seven quiere conocer uno."
                ),
                correctFeedback = listOf(
                    "¡Dato de granja guardado! Seven está aprendiendo de los animales que ayudan a las personas."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi computadora de granja sigue buscando un animal de ese lugar."
                ),
                incorrectNextFeedback = listOf(
                    "La computadora de granja quedó sin completar este registro."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S06",
                intro = listOf(
                    "Seven encontró un dibujo de una granja en su nave, pero faltan los animales. Ayúdame nombrando uno."
                ),
                correctFeedback = listOf(
                    "¡Dibujo completado un poquito más! Seven agregó un animal a la granja."
                ),
                incorrectRetryFeedback = listOf(
                    "El dibujo de la granja todavía necesita un animal. Probemos otra vez."
                ),
                incorrectNextFeedback = listOf(
                    "El dibujo de la granja quedó incompleto por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S07",
                intro = listOf(
                    "Mi radar detectó sonidos de vacas, gallinas y otros animales. Seven pregunta: menciona un animal de la granja."
                ),
                correctFeedback = listOf(
                    "¡Sonido de granja conectado con su animal! Seven entendió un nuevo dato."
                ),
                incorrectRetryFeedback = listOf(
                    "Mi radar escuchó sonidos, pero no encontró el animal esperado."
                ),
                incorrectNextFeedback = listOf(
                    "Seven dejará esos sonidos de granja para otra misión."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S08",
                intro = listOf(
                    "Seven quiere visitar una granja imaginaria de la Tierra, pero primero debe conocer sus animales. Menciona uno."
                ),
                correctFeedback = listOf(
                    "¡Entrada a la granja desbloqueada! Seven ya conoce un animal de allí."
                ),
                incorrectRetryFeedback = listOf(
                    "La entrada a la granja sigue cerrada. Necesito otro intento."
                ),
                incorrectNextFeedback = listOf(
                    "La entrada a la granja quedó cerrada por ahora."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S09",
                intro = listOf(
                    "En mi planeta no hay granjas como en la Tierra. Seven necesita tu ayuda: dime un animal de granja."
                ),
                correctFeedback = listOf(
                    "¡Exploración de granja iniciada! Seven ya aprendió un animal nuevo."
                ),
                incorrectRetryFeedback = listOf(
                    "Seven todavía no entiende qué animal vive en la granja. Probemos de nuevo."
                ),
                incorrectNextFeedback = listOf(
                    "La exploración de granja quedó pendiente."
                )
            ),
            AnimalNarrativeScenario(
                id = "FARM_S10",
                intro = listOf(
                    "Mi nave quiere llenar una cajita de datos sobre la granja. Ayúdame con un animal que viva allí."
                ),
                correctFeedback = listOf(
                    "¡Cajita de datos actualizada! Seven guardó un animal de granja."
                ),
                incorrectRetryFeedback = listOf(
                    "La cajita de datos sigue esperando un animal de granja."
                ),
                incorrectNextFeedback = listOf(
                    "La cajita de datos quedó incompleta esta vez."
                )
            )
        )

        val SCENARIOS: Map<LocalMediationKey, List<AnimalNarrativeScenario>> = mapOf(
            LocalMediationKey.ANIMAL_DOG_SOUND to DOG_SCENARIOS,
            LocalMediationKey.ANIMAL_DOMESTIC to DOMESTIC_SCENARIOS,
            LocalMediationKey.ANIMAL_CAT_SOUND to CAT_SCENARIOS,
            LocalMediationKey.ANIMAL_FARM to FARM_SCENARIOS
        )
    }
}
