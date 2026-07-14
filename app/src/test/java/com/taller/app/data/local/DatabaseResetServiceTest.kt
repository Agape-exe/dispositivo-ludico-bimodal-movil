package com.taller.app.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseResetServiceTest {

    private class FakeStore {
        var technicalEvents = 3
        var attempts = 5
        var sessions = 2
        var questions = 4
        var activities = 1
        val deletionOrder = mutableListOf<String>()

        fun isEmpty(): Boolean =
            technicalEvents == 0 && attempts == 0 && sessions == 0 &&
                questions == 0 && activities == 0
    }

    private fun serviceFor(
        store: FakeStore,
        clearVoiceCache: () -> Int = { 0 },
        failOnSessions: Boolean = false
    ) = DatabaseResetService(
        deleteTechnicalEvents = {
            store.technicalEvents = 0
            store.deletionOrder.add("technical_events")
        },
        deleteAttempts = {
            store.attempts = 0
            store.deletionOrder.add("attempts")
        },
        deleteSessions = {
            if (failOnSessions) error("disco lleno")
            store.sessions = 0
            store.deletionOrder.add("sessions")
        },
        deleteQuestions = {
            store.questions = 0
            store.deletionOrder.add("questions")
        },
        deleteActivities = {
            store.activities = 0
            store.deletionOrder.add("activities")
        },
        clearVoiceCache = clearVoiceCache
    )

    @Test
    fun borraTodasLasEntidadesDePrueba() = runBlocking {
        val store = FakeStore()

        val result = serviceFor(store).resetTestData()

        assertTrue(result is DatabaseResetService.Result.Success)
        assertTrue(store.isEmpty())
        assertEquals(
            "Datos de prueba eliminados correctamente.",
            (result as DatabaseResetService.Result.Success).message
        )
    }

    @Test
    fun borraHijosAntesQuePadres() = runBlocking {
        val store = FakeStore()

        serviceFor(store).resetTestData()

        assertEquals(
            listOf("technical_events", "attempts", "sessions", "questions", "activities"),
            store.deletionOrder
        )
    }

    @Test
    fun reportaLosAudiosDeCacheEliminados() = runBlocking {
        val store = FakeStore()

        val result = serviceFor(store, clearVoiceCache = { 7 }).resetTestData()

        assertEquals(7, (result as DatabaseResetService.Result.Success).clearedVoiceCacheFiles)
    }

    @Test
    fun unErrorDeBorradoDevuelveFalloSeguroSinCrashear() = runBlocking {
        val store = FakeStore()

        val result = serviceFor(store, failOnSessions = true).resetTestData()

        assertTrue(result is DatabaseResetService.Result.Failure)
        assertTrue(
            (result as DatabaseResetService.Result.Failure).safeMessage.isNotBlank()
        )
    }

    @Test
    fun unErrorDeCacheDeVozNoImpideElExito() = runBlocking {
        val store = FakeStore()

        val result = serviceFor(store, clearVoiceCache = { error("cache bloqueada") })
            .resetTestData()

        assertTrue(result is DatabaseResetService.Result.Success)
        assertEquals(0, (result as DatabaseResetService.Result.Success).clearedVoiceCacheFiles)
        assertTrue(store.isEmpty())
    }
}
