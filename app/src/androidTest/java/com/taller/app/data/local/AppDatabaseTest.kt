package com.taller.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.taller.app.data.local.dao.ActivityDao
import com.taller.app.data.local.dao.AttemptDao
import com.taller.app.data.local.dao.QuestionDao
import com.taller.app.data.local.dao.SessionDao
import com.taller.app.data.local.dao.TechnicalEventDao
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.AttemptEntity
import com.taller.app.data.local.entity.QuestionEntity
import com.taller.app.data.local.entity.SessionEntity
import com.taller.app.data.local.entity.TechnicalEventEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var activityDao: ActivityDao
    private lateinit var questionDao: QuestionDao
    private lateinit var sessionDao: SessionDao
    private lateinit var attemptDao: AttemptDao
    private lateinit var technicalEventDao: TechnicalEventDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activityDao = db.activityDao()
        questionDao = db.questionDao()
        sessionDao = db.sessionDao()
        attemptDao = db.attemptDao()
        technicalEventDao = db.technicalEventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrieveActivity() = runBlocking {
        val now = System.currentTimeMillis()
        val activity = ActivityEntity(
            name = "Animales del campo",
            topic = "Naturaleza",
            operationMode = "CLASSIC",
            maxAttempts = 3,
            maxTimeSeconds = 60,
            createdAt = now,
            updatedAt = now
        )
        val id = activityDao.insert(activity)
        val retrieved = activityDao.getById(id)

        assertNotNull(retrieved)
        assertEquals("Animales del campo", retrieved!!.name)
        assertEquals("Naturaleza", retrieved.topic)
        assertEquals("CLASSIC", retrieved.operationMode)
        assertEquals(3, retrieved.maxAttempts)
    }

    @Test
    fun insertAndRetrieveQuestionsOrdered() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Test actividad",
                topic = "Test",
                operationMode = "CLASSIC",
                maxAttempts = 3,
                maxTimeSeconds = 60,
                createdAt = now,
                updatedAt = now
            )
        )
        questionDao.insertAll(
            listOf(
                QuestionEntity(
                    activityId = activityId,
                    questionText = "¿Qué animal dice miau?",
                    expectedAnswer = "gato",
                    keywords = "gato,gatito,miau",
                    orderIndex = 1,
                    maxAttempts = 3,
                    maxTimeSeconds = 30,
                    createdAt = now,
                    updatedAt = now
                ),
                QuestionEntity(
                    activityId = activityId,
                    questionText = "¿Qué animal dice guau?",
                    expectedAnswer = "perro",
                    keywords = "perro,can",
                    orderIndex = 2,
                    maxAttempts = 3,
                    maxTimeSeconds = 30,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )

        val questions = questionDao.getByActivityIdOnce(activityId)

        assertEquals(2, questions.size)
        assertEquals(1, questions[0].orderIndex)
        assertEquals(2, questions[1].orderIndex)
        assertEquals("gato", questions[0].expectedAnswer)
        assertEquals("perro", questions[1].expectedAnswer)
    }

    @Test
    fun insertAndRetrieveSession() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Test actividad",
                topic = "Test",
                operationMode = "ADVANCED",
                maxAttempts = 2,
                maxTimeSeconds = 90,
                createdAt = now,
                updatedAt = now
            )
        )
        val sessionId = sessionDao.insert(
            SessionEntity(
                activityId = activityId,
                operationMode = "ADVANCED",
                startedAt = now,
                completed = false
            )
        )
        val session = sessionDao.getById(sessionId)

        assertNotNull(session)
        assertEquals(activityId, session!!.activityId)
        assertEquals("ADVANCED", session.operationMode)
        assertFalse(session.completed)
    }

    @Test
    fun insertAttemptAndTechnicalEvent() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Test actividad",
                topic = "Test",
                operationMode = "CLASSIC",
                maxAttempts = 3,
                maxTimeSeconds = 60,
                createdAt = now,
                updatedAt = now
            )
        )
        val questionId = questionDao.insert(
            QuestionEntity(
                activityId = activityId,
                questionText = "¿Qué animal dice miau?",
                expectedAnswer = "gato",
                keywords = "gato,gatito",
                orderIndex = 1,
                maxAttempts = 3,
                maxTimeSeconds = 30,
                createdAt = now,
                updatedAt = now
            )
        )
        val sessionId = sessionDao.insert(
            SessionEntity(
                activityId = activityId,
                operationMode = "CLASSIC",
                startedAt = now,
                completed = false
            )
        )
        attemptDao.insert(
            AttemptEntity(
                sessionId = sessionId,
                questionId = questionId,
                attemptNumber = 1,
                transcription = "gato",
                semanticResult = "CORRECT",
                responseTimeMs = 1500,
                sttLatencyMs = 320,
                semanticLatencyMs = 45,
                createdAt = now
            )
        )
        technicalEventDao.insert(
            TechnicalEventEntity(
                sessionId = sessionId,
                questionId = questionId,
                eventType = "STT_RESULT",
                message = "Transcripción recibida",
                timestamp = now
            )
        )

        val attempts = attemptDao.getBySessionIdOnce(sessionId)
        assertEquals(1, attempts.size)
        assertEquals("gato", attempts[0].transcription)
        assertEquals("CORRECT", attempts[0].semanticResult)
        assertEquals(1500L, attempts[0].responseTimeMs)

        val events = technicalEventDao.getBySessionIdOnce(sessionId)
        assertEquals(1, events.size)
        assertEquals("STT_RESULT", events[0].eventType)
        assertEquals(sessionId, events[0].sessionId)
    }

    @Test
    fun updateQuestion() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Actividad test",
                topic = "Test",
                operationMode = "CLASSIC",
                maxAttempts = 3,
                maxTimeSeconds = 60,
                createdAt = now,
                updatedAt = now
            )
        )
        val questionId = questionDao.insert(
            QuestionEntity(
                activityId = activityId,
                questionText = "Texto original",
                expectedAnswer = "respuesta original",
                keywords = "original",
                orderIndex = 1,
                maxAttempts = 3,
                maxTimeSeconds = 30,
                createdAt = now,
                updatedAt = now
            )
        )

        questionDao.update(
            QuestionEntity(
                id = questionId,
                activityId = activityId,
                questionText = "Texto actualizado",
                expectedAnswer = "respuesta actualizada",
                keywords = "nueva,clave",
                orderIndex = 1,
                maxAttempts = 2,
                maxTimeSeconds = 45,
                createdAt = now,
                updatedAt = now + 1000
            )
        )

        val questions = questionDao.getByActivityIdOnce(activityId)
        assertEquals(1, questions.size)
        assertEquals("Texto actualizado", questions[0].questionText)
        assertEquals("respuesta actualizada", questions[0].expectedAnswer)
        assertEquals(2, questions[0].maxAttempts)
        assertEquals(45, questions[0].maxTimeSeconds)
        assertEquals(now, questions[0].createdAt)
    }

    @Test
    fun questionWithMediationKeyIsPersistedAndRetrieved() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Actividad con mediación",
                topic = "Animales",
                operationMode = "CLASSIC",
                maxAttempts = 2,
                maxTimeSeconds = 10,
                createdAt = now,
                updatedAt = now
            )
        )
        val questionId = questionDao.insert(
            QuestionEntity(
                activityId = activityId,
                questionText = "¿Qué sonido hace el perro?",
                expectedAnswer = "guau",
                keywords = "guau,guau guau,ladra,ladrido",
                orderIndex = 1,
                maxAttempts = 2,
                maxTimeSeconds = 10,
                createdAt = now,
                updatedAt = now,
                mediationKey = "ANIMAL_DOG_SOUND"
            )
        )

        val questions = questionDao.getByActivityIdOnce(activityId)
        assertEquals(1, questions.size)
        assertEquals("ANIMAL_DOG_SOUND", questions[0].mediationKey)
        assertEquals(questionId, questions[0].id)
    }

    @Test
    fun questionWithoutMediationKeyDefaultsToNull() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Actividad sin mediación",
                topic = "Test",
                operationMode = "CLASSIC",
                maxAttempts = 3,
                maxTimeSeconds = 30,
                createdAt = now,
                updatedAt = now
            )
        )
        questionDao.insert(
            QuestionEntity(
                activityId = activityId,
                questionText = "¿Pregunta sin mediación?",
                expectedAnswer = "respuesta",
                keywords = "respuesta",
                orderIndex = 1,
                maxAttempts = 3,
                maxTimeSeconds = 30,
                createdAt = now,
                updatedAt = now
            )
        )

        val questions = questionDao.getByActivityIdOnce(activityId)
        assertEquals(1, questions.size)
        assertEquals(null, questions[0].mediationKey)
    }

    @Test
    fun updateQuestionMediationKey() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Actividad edición mediación",
                topic = "Test",
                operationMode = "CLASSIC",
                maxAttempts = 2,
                maxTimeSeconds = 10,
                createdAt = now,
                updatedAt = now
            )
        )
        val questionId = questionDao.insert(
            QuestionEntity(
                activityId = activityId,
                questionText = "Menciona un animal doméstico",
                expectedAnswer = "perro",
                keywords = "perro,gato,conejo",
                orderIndex = 1,
                maxAttempts = 2,
                maxTimeSeconds = 10,
                createdAt = now,
                updatedAt = now,
                mediationKey = null
            )
        )

        questionDao.update(
            QuestionEntity(
                id = questionId,
                activityId = activityId,
                questionText = "Menciona un animal doméstico",
                expectedAnswer = "perro",
                keywords = "perro,gato,conejo,hámster",
                orderIndex = 1,
                maxAttempts = 2,
                maxTimeSeconds = 10,
                createdAt = now,
                updatedAt = now + 1000,
                mediationKey = "ANIMAL_DOMESTIC"
            )
        )

        val questions = questionDao.getByActivityIdOnce(activityId)
        assertEquals(1, questions.size)
        assertEquals("ANIMAL_DOMESTIC", questions[0].mediationKey)
        assertEquals("perro,gato,conejo,hámster", questions[0].keywords)
    }

    @Test
    fun multipleMediationKeysAreStoredCorrectly() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Actividad animales",
                topic = "Animales",
                operationMode = "CLASSIC",
                maxAttempts = 2,
                maxTimeSeconds = 10,
                createdAt = now,
                updatedAt = now
            )
        )
        questionDao.insertAll(
            listOf(
                QuestionEntity(
                    activityId = activityId,
                    questionText = "¿Qué sonido hace el perro?",
                    expectedAnswer = "guau",
                    keywords = "guau,ladra",
                    orderIndex = 1,
                    maxAttempts = 2,
                    maxTimeSeconds = 10,
                    createdAt = now,
                    updatedAt = now,
                    mediationKey = "ANIMAL_DOG_SOUND"
                ),
                QuestionEntity(
                    activityId = activityId,
                    questionText = "¿Qué sonido hace el gato?",
                    expectedAnswer = "miau",
                    keywords = "miau,maúlla",
                    orderIndex = 2,
                    maxAttempts = 2,
                    maxTimeSeconds = 10,
                    createdAt = now,
                    updatedAt = now,
                    mediationKey = "ANIMAL_CAT_SOUND"
                ),
                QuestionEntity(
                    activityId = activityId,
                    questionText = "Menciona un animal de granja",
                    expectedAnswer = "vaca",
                    keywords = "vaca,gallina,cerdo,caballo",
                    orderIndex = 3,
                    maxAttempts = 2,
                    maxTimeSeconds = 10,
                    createdAt = now,
                    updatedAt = now,
                    mediationKey = "ANIMAL_FARM"
                )
            )
        )

        val questions = questionDao.getByActivityIdOnce(activityId)
        assertEquals(3, questions.size)
        assertEquals("ANIMAL_DOG_SOUND", questions[0].mediationKey)
        assertEquals("ANIMAL_CAT_SOUND", questions[1].mediationKey)
        assertEquals("ANIMAL_FARM", questions[2].mediationKey)
    }

    @Test
    fun deleteQuestion() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Actividad test",
                topic = "Test",
                operationMode = "CLASSIC",
                maxAttempts = 3,
                maxTimeSeconds = 60,
                createdAt = now,
                updatedAt = now
            )
        )
        val questionId = questionDao.insert(
            QuestionEntity(
                activityId = activityId,
                questionText = "¿Pregunta a eliminar?",
                expectedAnswer = "respuesta",
                keywords = "clave",
                orderIndex = 1,
                maxAttempts = 3,
                maxTimeSeconds = 30,
                createdAt = now,
                updatedAt = now
            )
        )

        questionDao.deleteById(questionId)

        val questions = questionDao.getByActivityIdOnce(activityId)
        assertEquals(0, questions.size)
    }

    @Test
    fun updateScriptPersistsSessionAndQuestionFields() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Animales domésticos",
                topic = "Animales",
                operationMode = "ADVANCED",
                maxAttempts = 3,
                maxTimeSeconds = 60,
                createdAt = now,
                updatedAt = now
            )
        )
        val questionId = questionDao.insert(
            QuestionEntity(
                activityId = activityId,
                questionText = "Menciona un animal doméstico",
                expectedAnswer = "perro",
                keywords = "perro,gato",
                orderIndex = 1,
                maxAttempts = 3,
                maxTimeSeconds = 30,
                createdAt = now,
                updatedAt = now
            )
        )

        activityDao.updateScript(
            id = activityId,
            introText = "Hola, soy Seven",
            closingText = "Gracias por explorar",
            toneNotes = "cálido",
            pedagogicalWarnings = null,
            scriptStatus = "REVIEWED",
            scriptUpdatedAt = now + 100
        )
        questionDao.updateScript(
            id = questionId,
            childFriendlyQuestionText = "Dime un animalito que viva con las personas",
            hintLevel1 = "Algunos viven en casa",
            hintLevel2 = "Tiene cuatro patas",
            hintLevel3 = "Le gusta jugar",
            positiveFeedbackText = "Muy bien",
            supportiveFeedbackText = "Casi, sigamos pensando",
            retryPromptText = "Probemos otra vez",
            answerReferenceWarning = "La pregunta admite varias respuestas",
            suggestedReferenceAnswer = "perro, gato, conejo",
            scriptReviewed = true,
            scriptUpdatedAt = now + 100
        )

        val activity = activityDao.getById(activityId)
        assertNotNull(activity)
        assertEquals("Hola, soy Seven", activity!!.generatedIntroText)
        assertEquals("REVIEWED", activity.scriptStatus)

        val question = questionDao.getByActivityIdOnce(activityId).first()
        assertEquals("Dime un animalito que viva con las personas", question.childFriendlyQuestionText)
        assertEquals("perro, gato, conejo", question.suggestedReferenceAnswer)
        assertEquals(true, question.scriptReviewed)
        // No se altera la pregunta ni la referencia original.
        assertEquals("Menciona un animal doméstico", question.questionText)
        assertEquals("perro", question.expectedAnswer)
    }

    @Test
    fun voicePrepDefaultsAndUpdatePersist() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Sesión con voz",
                topic = "Animales",
                operationMode = "ADVANCED",
                maxAttempts = 3,
                maxTimeSeconds = 60,
                createdAt = now,
                updatedAt = now
            )
        )

        // Por defecto la voz no esta preparada.
        val initial = activityDao.getById(activityId)
        assertNotNull(initial)
        assertEquals("NOT_PREPARED", initial!!.voicePrepStatus)
        assertEquals(0, initial.voicePrepTotalCount)

        activityDao.updateVoicePrep(
            id = activityId,
            status = "READY",
            updatedAt = now + 500,
            provider = "GEMINI_TTS",
            voice = "Puck",
            readyCount = 12,
            totalCount = 12,
            lastError = null
        )

        val updated = activityDao.getById(activityId)
        assertNotNull(updated)
        assertEquals("READY", updated!!.voicePrepStatus)
        assertEquals("GEMINI_TTS", updated.voicePrepProvider)
        assertEquals("Puck", updated.voicePrepVoice)
        assertEquals(12, updated.voicePrepReadyCount)
        assertEquals(12, updated.voicePrepTotalCount)
        assertEquals(null, updated.voicePrepLastError)
        // No se alteran los datos previos de la sesion.
        assertEquals("Sesión con voz", updated.name)
    }

    @Test
    fun technicalEventWithoutQuestionIsAllowed() = runBlocking {
        val now = System.currentTimeMillis()
        val activityId = activityDao.insert(
            ActivityEntity(
                name = "Test actividad",
                topic = "Test",
                operationMode = "CLASSIC",
                maxAttempts = 3,
                maxTimeSeconds = 60,
                createdAt = now,
                updatedAt = now
            )
        )
        val sessionId = sessionDao.insert(
            SessionEntity(
                activityId = activityId,
                operationMode = "CLASSIC",
                startedAt = now,
                completed = false
            )
        )
        technicalEventDao.insert(
            TechnicalEventEntity(
                sessionId = sessionId,
                questionId = null,
                eventType = "SESSION_START",
                message = "Sesión iniciada",
                timestamp = now
            )
        )

        val events = technicalEventDao.getBySessionIdOnce(sessionId)
        assertEquals(1, events.size)
        assertEquals("SESSION_START", events[0].eventType)
        assertEquals(null, events[0].questionId)
    }
}
