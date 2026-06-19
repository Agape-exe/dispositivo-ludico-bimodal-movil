package com.taller.app.logger

import com.taller.app.bimodal.BimodalLatencySample
import com.taller.app.bimodal.BimodalSessionSummary
import com.taller.app.data.local.dao.AttemptDao
import com.taller.app.data.local.dao.SessionDao
import com.taller.app.data.local.dao.TechnicalEventDao
import com.taller.app.data.local.entity.AttemptEntity
import com.taller.app.data.local.entity.SessionEntity
import com.taller.app.data.local.entity.TechnicalEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InteractionDataLoggerTest {

    // -------------------------------------------------------------------------
    // Fake DAOs en memoria
    // -------------------------------------------------------------------------

    private class FakeSessionDao : SessionDao {
        val sessions = mutableListOf<SessionEntity>()
        private var nextId = 1L

        override suspend fun insert(session: SessionEntity): Long {
            val id = nextId++
            sessions.add(session.copy(id = id))
            return id
        }

        override suspend fun update(session: SessionEntity) {
            val i = sessions.indexOfFirst { it.id == session.id }
            if (i >= 0) sessions[i] = session
        }

        override suspend fun getById(id: Long): SessionEntity? =
            sessions.firstOrNull { it.id == id }

        override fun getRecent(limit: Int): Flow<List<SessionEntity>> =
            flowOf(sessions.takeLast(limit))

        override suspend fun updateEnd(
            id: Long, endedAt: Long, durationSeconds: Long,
            finalStatus: String, completed: Boolean
        ) {
            val i = sessions.indexOfFirst { it.id == id }
            if (i >= 0) sessions[i] = sessions[i].copy(
                endedAt = endedAt, durationSeconds = durationSeconds,
                finalStatus = finalStatus, completed = completed
            )
        }

        override suspend fun updateFinal(
            id: Long, endedAt: Long, durationSeconds: Long, totalDurationMs: Long,
            finalStatus: String, completed: Boolean, completedQuestions: Int,
            totalAttempts: Int, correctCount: Int?, incorrectCount: Int?,
            noResponseCount: Int, notInterpretableCount: Int?,
            timeoutCount: Int, technicalErrorCount: Int
        ) {
            val i = sessions.indexOfFirst { it.id == id }
            if (i >= 0) sessions[i] = sessions[i].copy(
                endedAt = endedAt, durationSeconds = durationSeconds,
                totalDurationMs = totalDurationMs, finalStatus = finalStatus,
                completed = completed, completedQuestions = completedQuestions,
                totalAttempts = totalAttempts, correctCount = correctCount,
                incorrectCount = incorrectCount, noResponseCount = noResponseCount,
                notInterpretableCount = notInterpretableCount,
                timeoutCount = timeoutCount, technicalErrorCount = technicalErrorCount
            )
        }
    }

    private class FakeAttemptDao : AttemptDao {
        val attempts = mutableListOf<AttemptEntity>()
        private var nextId = 1L

        override suspend fun insert(attempt: AttemptEntity): Long {
            val id = nextId++
            attempts.add(attempt.copy(id = id))
            return id
        }

        override fun getBySessionId(sessionId: Long): Flow<List<AttemptEntity>> =
            flowOf(attempts.filter { it.sessionId == sessionId })

        override suspend fun getBySessionIdOnce(sessionId: Long): List<AttemptEntity> =
            attempts.filter { it.sessionId == sessionId }

        override suspend fun updateFinished(
            attemptId: Long, finalState: String, wasFinal: Boolean,
            transcript: String?, semanticResult: String?, classicResult: String?,
            responseReceivedAtMs: Long?, finishedAtMs: Long, realResponseTimeMs: Long?,
            advancedFeedbackType: String?, usedStt: Boolean,
            sttStart: Long?, sttFinalAt: Long?, semStart: Long?, semEnd: Long?,
            logicAt: Long?, feedbackAt: Long?,
            responseLatency: Long?, feedbackLatency: Long?, pipelineLatency: Long?
        ) {
            val i = attempts.indexOfFirst { it.id == attemptId }
            if (i >= 0) attempts[i] = attempts[i].copy(
                finalAttemptState = finalState, wasFinalAttempt = wasFinal,
                transcription = transcript, semanticResult = semanticResult,
                classicResult = classicResult, responseReceivedAtMs = responseReceivedAtMs,
                finishedAtMs = finishedAtMs, realResponseTimeMs = realResponseTimeMs,
                advancedFeedbackType = advancedFeedbackType, usedSpeechToText = usedStt,
                sttStartAtMs = sttStart, sttFinalAtMs = sttFinalAt,
                semanticStartAtMs = semStart, semanticEndAtMs = semEnd,
                logicalResponseAtMs = logicAt, feedbackStartAtMs = feedbackAt,
                totalResponseLatencyMs = responseLatency,
                responseToFeedbackLatencyMs = feedbackLatency,
                fullPipelineLatencyMs = pipelineLatency
            )
        }
    }

    private class FakeTechnicalEventDao : TechnicalEventDao {
        val events = mutableListOf<TechnicalEventEntity>()
        private var nextId = 1L

        override suspend fun insert(event: TechnicalEventEntity): Long {
            val id = nextId++
            events.add(event.copy(id = id))
            return id
        }

        override fun getBySessionId(sessionId: Long): Flow<List<TechnicalEventEntity>> =
            flowOf(events.filter { it.sessionId == sessionId })

        override suspend fun getBySessionIdOnce(sessionId: Long): List<TechnicalEventEntity> =
            events.filter { it.sessionId == sessionId }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private lateinit var sessionDao: FakeSessionDao
    private lateinit var attemptDao: FakeAttemptDao
    private lateinit var eventDao: FakeTechnicalEventDao
    private lateinit var logger: InteractionDataLogger

    private val fixedNow = 1_000L

    @Before
    fun setUp() {
        sessionDao = FakeSessionDao()
        attemptDao = FakeAttemptDao()
        eventDao = FakeTechnicalEventDao()
        logger = InteractionDataLogger(sessionDao, attemptDao, eventDao) { fixedNow }
    }

    // -------------------------------------------------------------------------
    // startSession
    // -------------------------------------------------------------------------

    @Test
    fun startSession_validId_creaEnRoom() = runBlocking {
        val sid = logger.startSession(42L, "Actividad Test", "ADVANCED", 5)

        assertEquals(1L, sid)
        val stored = sessionDao.sessions.first()
        assertEquals(42L, stored.activityId)
        assertEquals("Actividad Test", stored.activityName)
        assertEquals("ADVANCED", stored.operationMode)
        assertEquals(5, stored.totalQuestions)
        assertFalse(stored.completed)
    }

    @Test
    fun startSession_idCero_retornaMinusOne() = runBlocking {
        val sid = logger.startSession(0L, "Muestra", "ADVANCED", 3)

        assertEquals(-1L, sid)
        assertTrue(sessionDao.sessions.isEmpty())
    }

    @Test
    fun startSession_idNegativo_retornaMinusOne() = runBlocking {
        val sid = logger.startSession(-5L, "Muestra", "CLASSIC", 2)

        assertEquals(-1L, sid)
        assertTrue(sessionDao.sessions.isEmpty())
    }

    // -------------------------------------------------------------------------
    // finishBimodalSession
    // -------------------------------------------------------------------------

    @Test
    fun finishBimodalSession_actualizaConteosBimodales() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 3)
        val summary = BimodalSessionSummary(
            correct = 2, incorrect = 1, notInterpretable = 0,
            noResponse = 0, timeExpired = 1, sttErrors = 0, technicalErrors = 0,
            resolvedQuestions = 3
        )

        logger.finishBimodalSession(
            sessionId = sid,
            finalState = "SESSION_COMPLETED",
            startedAtMs = 0L,
            completedQuestions = 3,
            summary = summary
        )

        val stored = sessionDao.sessions.first()
        assertTrue(stored.completed)
        assertEquals("SESSION_COMPLETED", stored.finalStatus)
        assertEquals(3, stored.completedQuestions)
        assertEquals(4, stored.totalAttempts)   // 2+1+0+0+1+0+0
        assertEquals(2, stored.correctCount)
        assertEquals(1, stored.incorrectCount)
        assertEquals(0, stored.noResponseCount)
        assertEquals(1, stored.timeoutCount)
    }

    @Test
    fun finishBimodalSession_idInvalido_noHaceNada() = runBlocking {
        logger.finishBimodalSession(
            sessionId = -1L,
            finalState = "SESSION_COMPLETED",
            startedAtMs = 0L,
            completedQuestions = 1,
            summary = BimodalSessionSummary()
        )

        assertTrue(sessionDao.sessions.isEmpty())
    }

    // -------------------------------------------------------------------------
    // finishClassicSession
    // -------------------------------------------------------------------------

    @Test
    fun finishClassicSession_dejaNulosCamposSemanticos() = runBlocking {
        val sid = logger.startSession(2L, "Clasica", "CLASSIC", 4)

        logger.finishClassicSession(
            sessionId = sid,
            finalState = "SESSION_COMPLETED",
            startedAtMs = 0L,
            completedQuestions = 4,
            totalAttempts = 4,
            noResponseCount = 1,
            timeoutCount = 2
        )

        val stored = sessionDao.sessions.first()
        assertTrue(stored.completed)
        assertEquals(4, stored.totalAttempts)
        assertNull(stored.correctCount)
        assertNull(stored.incorrectCount)
        assertNull(stored.notInterpretableCount)
        assertEquals(1, stored.noResponseCount)
        assertEquals(2, stored.timeoutCount)
    }

    // -------------------------------------------------------------------------
    // logAttemptStarted
    // -------------------------------------------------------------------------

    @Test
    fun logAttemptStarted_idsValidos_creaIntento() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 2)

        val aid = logger.logAttemptStarted(
            sessionId = sid,
            questionId = 10L,
            questionOrder = 0,
            attemptNumber = 1,
            operationMode = "ADVANCED",
            questionText = "¿De qué color es el cielo?",
            maxTimeMs = 30_000L,
            usedSemanticEvaluation = true,
            usedSpeechToText = true
        )

        assertEquals(1L, aid)
        val stored = attemptDao.attempts.first()
        assertEquals(sid, stored.sessionId)
        assertEquals(10L, stored.questionId)
        assertEquals(0, stored.questionOrder)
        assertEquals(1, stored.attemptNumber)
        assertEquals("ADVANCED", stored.operationMode)
        assertEquals("¿De qué color es el cielo?", stored.questionText)
        assertEquals(30_000L, stored.maxTimeMs)
        assertTrue(stored.usedSemanticEvaluation)
        assertTrue(stored.usedSpeechToText)
    }

    @Test
    fun logAttemptStarted_sessionIdInvalido_retornaMinusOne() = runBlocking {
        val aid = logger.logAttemptStarted(
            sessionId = 0L, questionId = 5L, questionOrder = 0,
            attemptNumber = 1, operationMode = "ADVANCED"
        )

        assertEquals(-1L, aid)
        assertTrue(attemptDao.attempts.isEmpty())
    }

    @Test
    fun logAttemptStarted_questionIdInvalido_retornaMinusOne() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 1)

        val aid = logger.logAttemptStarted(
            sessionId = sid, questionId = 0L, questionOrder = 0,
            attemptNumber = 1, operationMode = "ADVANCED"
        )

        assertEquals(-1L, aid)
        assertTrue(attemptDao.attempts.isEmpty())
    }

    // -------------------------------------------------------------------------
    // finishBimodalAttempt
    // -------------------------------------------------------------------------

    @Test
    fun finishBimodalAttempt_guarda_resultado_y_latencias() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 1)
        val aid = logger.logAttemptStarted(sid, 10L, 0, 1, "ADVANCED")

        val sample = BimodalLatencySample(
            sttStartAtMs = 1_000L,
            sttFinalAtMs = 1_800L,
            semanticStartAtMs = 1_820L,
            semanticEndAtMs = 2_100L,
            logicalResponseAtMs = 2_120L,
            feedbackStartAtMs = 2_150L
        )

        logger.finishBimodalAttempt(
            attemptId = aid,
            finalAttemptState = "FEEDBACK_CORRECT",
            wasFinalAttempt = true,
            transcript = "azul",
            semanticResult = "CORRECT",
            advancedFeedbackType = "CORRECT",
            usedStt = true,
            latencySample = sample
        )

        val stored = attemptDao.attempts.first()
        assertEquals("FEEDBACK_CORRECT", stored.finalAttemptState)
        assertTrue(stored.wasFinalAttempt)
        assertEquals("azul", stored.transcription)
        assertEquals("CORRECT", stored.semanticResult)
        assertNull(stored.classicResult)
        assertEquals("CORRECT", stored.advancedFeedbackType)
        assertTrue(stored.usedSpeechToText)
        assertEquals(1_000L, stored.sttStartAtMs)
        assertEquals(1_800L, stored.sttFinalAtMs)
        assertEquals(2_120L, stored.logicalResponseAtMs)
        // totalResponseLatencyMs = logicalResponseAt - sttFinalAt = 320
        assertEquals(320L, stored.totalResponseLatencyMs)
    }

    @Test
    fun finishBimodalAttempt_idInvalido_noHaceNada() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 1)
        logger.logAttemptStarted(sid, 10L, 0, 1, "ADVANCED")

        logger.finishBimodalAttempt(
            attemptId = -1L,
            finalAttemptState = "FEEDBACK_CORRECT",
            wasFinalAttempt = true
        )

        val stored = attemptDao.attempts.first()
        assertEquals("UNKNOWN", stored.finalAttemptState)  // no se actualizó
    }

    // -------------------------------------------------------------------------
    // finishClassicAttempt
    // -------------------------------------------------------------------------

    @Test
    fun finishClassicAttempt_sinCamposSemanticos() = runBlocking {
        val sid = logger.startSession(2L, "Clasica", "CLASSIC", 3)
        val aid = logger.logAttemptStarted(
            sid, 20L, 0, 1, "CLASSIC",
            usedSemanticEvaluation = false
        )

        logger.finishClassicAttempt(
            attemptId = aid,
            finalAttemptState = "ANSWER_RECEIVED",
            classicResult = "ANSWERED",
            transcript = "cuatro",
            usedStt = true
        )

        val stored = attemptDao.attempts.first()
        assertEquals("ANSWER_RECEIVED", stored.finalAttemptState)
        assertEquals("ANSWERED", stored.classicResult)
        assertEquals("cuatro", stored.transcription)
        assertNull(stored.semanticResult)
        assertTrue(stored.usedSpeechToText)
        assertNull(stored.totalResponseLatencyMs)
    }

    @Test
    fun finishClassicAttempt_timeout_classicResultCorrecto() = runBlocking {
        val sid = logger.startSession(2L, "Clasica", "CLASSIC", 3)
        val aid = logger.logAttemptStarted(sid, 21L, 1, 1, "CLASSIC")

        logger.finishClassicAttempt(
            attemptId = aid,
            finalAttemptState = "TIME_EXPIRED",
            classicResult = "TIMEOUT_NO_RESPONSE",
            usedStt = false
        )

        val stored = attemptDao.attempts.first()
        assertEquals("TIME_EXPIRED", stored.finalAttemptState)
        assertEquals("TIMEOUT_NO_RESPONSE", stored.classicResult)
        assertNull(stored.transcription)
        assertFalse(stored.usedSpeechToText)
    }

    // -------------------------------------------------------------------------
    // logTechnicalEvent
    // -------------------------------------------------------------------------

    @Test
    fun logTechnicalEvent_almacenaEvento() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 1)

        logger.logTechnicalEvent(
            sessionId = sid,
            questionId = 5L,
            attemptId = 10L,
            operationMode = "ADVANCED",
            eventType = "STT_STARTED"
        )

        assertEquals(1, eventDao.events.size)
        val ev = eventDao.events.first()
        assertEquals(sid, ev.sessionId)
        assertEquals(5L, ev.questionId)
        assertEquals(10L, ev.attemptId)
        assertEquals("ADVANCED", ev.operationMode)
        assertEquals("STT_STARTED", ev.eventType)
    }

    @Test
    fun logTechnicalEvent_sessionInvalida_noAlmacena() = runBlocking {
        logger.logTechnicalEvent(
            sessionId = -1L,
            operationMode = "CLASSIC",
            eventType = "STT_ERROR"
        )

        assertTrue(eventDao.events.isEmpty())
    }

    @Test
    fun logTechnicalEvent_questionIdCero_guardaNulo() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 1)

        logger.logTechnicalEvent(
            sessionId = sid,
            questionId = 0L,
            operationMode = "ADVANCED",
            eventType = "SESSION_START"
        )

        val ev = eventDao.events.first()
        assertNull(ev.questionId)
    }

    @Test
    fun logTechnicalEvent_conLatencia_almacenaLatencia() = runBlocking {
        val sid = logger.startSession(1L, "Test", "ADVANCED", 1)

        logger.logTechnicalEvent(
            sessionId = sid,
            operationMode = "ADVANCED",
            eventType = "STT_FINAL",
            latencyMs = 320L
        )

        val ev = eventDao.events.first()
        assertEquals(320L, ev.latencyMs)
    }
}
