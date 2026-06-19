package com.taller.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsCsvExporterTest {

    private val exporter = MetricsCsvExporter()

    // -------------------------------------------------------------------------
    // Estructura básica — intentos
    // -------------------------------------------------------------------------

    @Test
    fun exportEmpty_soloEncabezado() {
        val csv = exporter.exportAttemptsCsv(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("session_id"))
    }

    @Test
    fun encabezadoIntentos_contieneColumnasRequeridas() {
        val csv = exporter.exportAttemptsCsv(emptyList())
        val header = csv.trim().lines().first()
        val cols = header.split(",")
        assertTrue(cols.contains("session_id"))
        assertTrue(cols.contains("attempt_id"))
        assertTrue(cols.contains("semantic_result"))
        assertTrue(cols.contains("classic_result"))
        assertTrue(cols.contains("operation_mode"))
        assertTrue(cols.contains("transcript"))
        assertTrue(cols.contains("final_attempt_state"))
        assertTrue(cols.contains("total_response_latency_ms"))
    }

    @Test
    fun unSesionUnIntento_generaFilaDeDatos() {
        val csv = exporter.exportAttemptsCsv(listOf(bimodalSession()))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
    }

    @Test
    fun dosIntentos_generaDosFila() {
        val session = bimodalSession().copy(attempts = listOf(bimodalAttempt(), bimodalAttempt().copy(attemptId = 2L)))
        val csv = exporter.exportAttemptsCsv(listOf(session))
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
    }

    // -------------------------------------------------------------------------
    // Modo bimodal
    // -------------------------------------------------------------------------

    @Test
    fun bimodal_semanticResultPresente() {
        val csv = exporter.exportAttemptsCsv(listOf(bimodalSession()))
        val dataRow = csv.trim().lines()[1]
        val cols = dataRow.split(",")
        val header = exporter.exportAttemptsCsv(emptyList()).trim().lines()[0].split(",")
        val idx = header.indexOf("semantic_result")
        assertEquals("CORRECT", cols[idx])
    }

    @Test
    fun bimodal_classicResultVacio() {
        val csv = exporter.exportAttemptsCsv(listOf(bimodalSession()))
        val dataRow = csv.trim().lines()[1]
        val cols = dataRow.split(",")
        val header = exporter.exportAttemptsCsv(emptyList()).trim().lines()[0].split(",")
        val idx = header.indexOf("classic_result")
        assertEquals("", cols[idx])
    }

    @Test
    fun bimodal_latenciasPresentes() {
        val csv = exporter.exportAttemptsCsv(listOf(bimodalSession()))
        assertTrue(csv.contains("150"))
        assertTrue(csv.contains("950"))
    }

    // -------------------------------------------------------------------------
    // Modo clásico
    // -------------------------------------------------------------------------

    @Test
    fun clasico_semanticResultVacio() {
        val csv = exporter.exportAttemptsCsv(listOf(classicSession()))
        val dataRow = csv.trim().lines()[1]
        val cols = dataRow.split(",")
        val header = exporter.exportAttemptsCsv(emptyList()).trim().lines()[0].split(",")
        val idx = header.indexOf("semantic_result")
        assertEquals("", cols[idx])
    }

    @Test
    fun clasico_classicResultPresente() {
        val csv = exporter.exportAttemptsCsv(listOf(classicSession()))
        val dataRow = csv.trim().lines()[1]
        val cols = dataRow.split(",")
        val header = exporter.exportAttemptsCsv(emptyList()).trim().lines()[0].split(",")
        val idx = header.indexOf("classic_result")
        assertEquals("ANSWERED", cols[idx])
    }

    @Test
    fun clasico_noTieneSemanticResultComoCorrectoOIncorrecto() {
        val csv = exporter.exportAttemptsCsv(listOf(classicSession()))
        val dataRow = csv.trim().lines()[1]
        val cols = dataRow.split(",")
        val header = exporter.exportAttemptsCsv(emptyList()).trim().lines()[0].split(",")
        val idx = header.indexOf("semantic_result")
        assertFalse(cols[idx].equals("CORRECT", ignoreCase = true))
        assertFalse(cols[idx].equals("INCORRECT", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Escaping CSV
    // -------------------------------------------------------------------------

    @Test
    fun transcriptConComa_seEncierraEnComillas() {
        val attempt = bimodalAttempt().copy(transcription = "uno, dos")
        val session = bimodalSession().copy(attempts = listOf(attempt))
        val csv = exporter.exportAttemptsCsv(listOf(session))
        assertTrue(csv.contains("\"uno, dos\""))
    }

    @Test
    fun transcriptConComillaDoble_seEscapa() {
        val attempt = bimodalAttempt().copy(transcription = "dijo \"hola\"")
        val session = bimodalSession().copy(attempts = listOf(attempt))
        val csv = exporter.exportAttemptsCsv(listOf(session))
        assertTrue(csv.contains("\"dijo \"\"hola\"\"\""))
    }

    @Test
    fun transcriptConSaltoDeLinea_seEncierraEnComillas() {
        val attempt = bimodalAttempt().copy(transcription = "linea1\nlinea2")
        val session = bimodalSession().copy(attempts = listOf(attempt))
        val csv = exporter.exportAttemptsCsv(listOf(session))
        assertTrue(csv.contains("\"linea1\nlinea2\""))
    }

    // -------------------------------------------------------------------------
    // Consistencia de columnas
    // -------------------------------------------------------------------------

    @Test
    fun todasLasFilasTienenMismasCantidadDeColumnas() {
        val sessions = listOf(bimodalSession(), classicSession())
        val csv = exporter.exportAttemptsCsv(sessions)
        val lines = csv.trim().lines()
        val headerCount = MetricsCsvExporter.ATTEMPTS_HEADER.size
        lines.forEach { line ->
            val count = countCsvColumns(line)
            assertEquals("Fila con columnas incorrectas: $line", headerCount, count)
        }
    }

    // -------------------------------------------------------------------------
    // CSV de eventos técnicos
    // -------------------------------------------------------------------------

    @Test
    fun eventosEmpty_soloEncabezado() {
        val csv = exporter.exportEventsCsv(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("session_id"))
    }

    @Test
    fun encabezadoEventos_contieneColumnasRequeridas() {
        val csv = exporter.exportEventsCsv(emptyList())
        val header = csv.trim().lines().first()
        val cols = header.split(",")
        assertTrue(cols.contains("session_id"))
        assertTrue(cols.contains("event_id"))
        assertTrue(cols.contains("event_type"))
        assertTrue(cols.contains("timestamp_ms"))
        assertTrue(cols.contains("latency_ms"))
    }

    @Test
    fun eventos_filasPorEvento() {
        val session = bimodalSession().copy(technicalEvents = listOf(event1(), event2()))
        val csv = exporter.exportEventsCsv(listOf(session))
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
    }

    @Test
    fun eventoConLatencia_aparece() {
        val session = bimodalSession().copy(technicalEvents = listOf(event2()))
        val csv = exporter.exportEventsCsv(listOf(session))
        assertTrue(csv.contains("320"))
    }

    @Test
    fun eventoSinLatencia_campoVacio() {
        val session = bimodalSession().copy(technicalEvents = listOf(event1()))
        val csv = exporter.exportEventsCsv(listOf(session))
        val dataRow = csv.trim().lines()[1]
        assertTrue(dataRow.endsWith(","))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun bimodalSession() = ExportSessionDto(
        sessionId = 1L,
        activityId = 1L,
        activityName = "Actividad test",
        operationMode = "BIMODAL_INTELLIGENT",
        startedAtMs = 1000L,
        finishedAtMs = 5000L,
        finalState = "SESSION_COMPLETED",
        totalDurationMs = 4000L,
        summary = ExportSessionSummaryDto(
            totalQuestions = 1, completedQuestions = 1, totalAttempts = 1,
            correctCount = 1, incorrectCount = 0, noResponseCount = 0,
            notInterpretableCount = 0, timeoutCount = 0, technicalErrorCount = 0
        ),
        attempts = listOf(bimodalAttempt()),
        technicalEvents = emptyList()
    )

    private fun bimodalAttempt() = ExportAttemptDto(
        attemptId = 1L,
        questionId = 10L,
        questionOrder = 0,
        attemptNumber = 1,
        operationMode = "BIMODAL_INTELLIGENT",
        questionText = "¿Qué color?",
        maxTimeMs = 30000L,
        startedAtMs = 1100L,
        responseReceivedAtMs = 2000L,
        finishedAtMs = 2100L,
        realResponseTimeMs = 900L,
        transcription = "azul",
        semanticResult = "CORRECT",
        classicResult = null,
        finalAttemptState = "FEEDBACK_CORRECT",
        usedSemanticEvaluation = true,
        usedSpeechToText = true,
        wasFinalAttempt = true,
        advancedFeedbackType = "CORRECT",
        sttStartAtMs = 1150L,
        sttFinalAtMs = 1800L,
        semanticStartAtMs = 1820L,
        semanticEndAtMs = 1900L,
        logicalResponseAtMs = 1950L,
        feedbackStartAtMs = 2000L,
        totalResponseLatencyMs = 150L,
        responseToFeedbackLatencyMs = 50L,
        fullPipelineLatencyMs = 950L
    )

    private fun classicSession() = ExportSessionDto(
        sessionId = 2L,
        activityId = 2L,
        activityName = "Actividad clásica",
        operationMode = "CLASSIC",
        startedAtMs = 2000L,
        finishedAtMs = 6000L,
        finalState = "SESSION_COMPLETED",
        totalDurationMs = 4000L,
        summary = ExportSessionSummaryDto(
            totalQuestions = 1, completedQuestions = 1, totalAttempts = 1,
            correctCount = null, incorrectCount = null, noResponseCount = 0,
            notInterpretableCount = null, timeoutCount = 0, technicalErrorCount = 0
        ),
        attempts = listOf(classicAttempt()),
        technicalEvents = emptyList()
    )

    private fun classicAttempt() = ExportAttemptDto(
        attemptId = 2L,
        questionId = 20L,
        questionOrder = 0,
        attemptNumber = 1,
        operationMode = "CLASSIC",
        questionText = "¿Cuánto es 2+2?",
        maxTimeMs = 15000L,
        startedAtMs = 2100L,
        responseReceivedAtMs = 3000L,
        finishedAtMs = 3100L,
        realResponseTimeMs = 900L,
        transcription = "cuatro",
        semanticResult = null,
        classicResult = "ANSWERED",
        finalAttemptState = "ANSWER_RECEIVED",
        usedSemanticEvaluation = false,
        usedSpeechToText = true,
        wasFinalAttempt = true,
        advancedFeedbackType = null,
        sttStartAtMs = null,
        sttFinalAtMs = null,
        semanticStartAtMs = null,
        semanticEndAtMs = null,
        logicalResponseAtMs = null,
        feedbackStartAtMs = null,
        totalResponseLatencyMs = null,
        responseToFeedbackLatencyMs = null,
        fullPipelineLatencyMs = null
    )

    private fun event1() = ExportTechnicalEventDto(
        eventId = 1L,
        questionId = 10L,
        attemptId = 1L,
        operationMode = "BIMODAL_INTELLIGENT",
        eventType = "STT_STARTED",
        message = null,
        timestampMs = 1150L,
        latencyMs = null
    )

    private fun event2() = ExportTechnicalEventDto(
        eventId = 2L,
        questionId = 10L,
        attemptId = 1L,
        operationMode = "BIMODAL_INTELLIGENT",
        eventType = "STT_FINAL",
        message = null,
        timestampMs = 1800L,
        latencyMs = 320L
    )

    private fun countCsvColumns(line: String): Int {
        var count = 1
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> count++
            }
        }
        return count
    }
}
