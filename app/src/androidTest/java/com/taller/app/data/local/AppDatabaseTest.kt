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
