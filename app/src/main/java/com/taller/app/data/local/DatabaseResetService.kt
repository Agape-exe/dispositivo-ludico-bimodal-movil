package com.taller.app.data.local

import com.taller.app.data.local.dao.ActivityDao
import com.taller.app.data.local.dao.AttemptDao
import com.taller.app.data.local.dao.QuestionDao
import com.taller.app.data.local.dao.SessionDao
import com.taller.app.data.local.dao.TechnicalEventDao

/**
 * FINAL-CORE02: vaciado seguro de los datos locales de prueba antes de cargar
 * los datos oficiales.
 *
 * Borra SOLO el contenido de Room (actividades, preguntas y sus guiones,
 * sesiones, intentos y eventos tecnicos) y, opcionalmente, la cache de audio de
 * la voz de Seven (los audios pre-generados pertenecen a las actividades que se
 * borran).
 *
 * NUNCA toca:
 *  - la configuracion de la app (DataStore: voz, STT, ajustes de interaccion),
 *  - las claves/API keys (viven en local.properties → BuildConfig),
 *  - los archivos .md limitadores del docente,
 *  - los assets de la app.
 *
 * El orden de borrado respeta las claves foraneas (hijos antes que padres).
 * Cualquier error se devuelve como resultado, jamas como crash.
 */
class DatabaseResetService(
    private val deleteTechnicalEvents: suspend () -> Unit,
    private val deleteAttempts: suspend () -> Unit,
    private val deleteSessions: suspend () -> Unit,
    private val deleteQuestions: suspend () -> Unit,
    private val deleteActivities: suspend () -> Unit,
    /** Limpia la cache de audio de voz; devuelve cuantos archivos borro. */
    private val clearVoiceCache: () -> Int = { 0 },
    /**
     * Ejecutor de transaccion inyectable: en produccion envuelve el bloque en
     * una transaccion de Room; en pruebas ejecuta el bloque directamente.
     */
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { block -> block() }
) {

    constructor(
        activityDao: ActivityDao,
        questionDao: QuestionDao,
        sessionDao: SessionDao,
        attemptDao: AttemptDao,
        technicalEventDao: TechnicalEventDao,
        clearVoiceCache: () -> Int = { 0 },
        transactionRunner: suspend (suspend () -> Unit) -> Unit = { block -> block() }
    ) : this(
        deleteTechnicalEvents = { technicalEventDao.deleteAll() },
        deleteAttempts = { attemptDao.deleteAll() },
        deleteSessions = { sessionDao.deleteAll() },
        deleteQuestions = { questionDao.deleteAll() },
        deleteActivities = { activityDao.deleteAll() },
        clearVoiceCache = clearVoiceCache,
        transactionRunner = transactionRunner
    )

    sealed class Result {
        data class Success(val clearedVoiceCacheFiles: Int) : Result() {
            val message: String = "Datos de prueba eliminados correctamente."
        }

        data class Failure(val safeMessage: String) : Result()
    }

    suspend fun resetTestData(): Result {
        return try {
            transactionRunner {
                // Hijos primero para respetar las claves foraneas.
                deleteTechnicalEvents()
                deleteAttempts()
                deleteSessions()
                deleteQuestions()
                deleteActivities()
            }
            val clearedFiles = runCatching { clearVoiceCache() }.getOrDefault(0)
            Result.Success(clearedVoiceCacheFiles = clearedFiles)
        } catch (t: Throwable) {
            Result.Failure(
                safeMessage = "No se pudieron eliminar los datos de prueba. Intentalo de nuevo."
            )
        }
    }
}
