package com.taller.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsCsvExporterTest {

    private val exporter = MetricsCsvExporter()

    // -------------------------------------------------------------------------
    // BOM
    // -------------------------------------------------------------------------

    @Test
    fun exportAttempts_iniciaCONBOM() {
        val csv = exporter.exportAttemptsCsv(emptyList())
        assertTrue("Debe iniciar con BOM UTF-8", csv.startsWith("﻿"))
    }

    @Test
    fun exportEvents_iniciaCONBOM() {
        val csv = exporter.exportEventsCsv(emptyList())
        assertTrue("Debe iniciar con BOM UTF-8", csv.startsWith("﻿"))
    }

    @Test
    fun exportSessions_iniciaCONBOM() {
        val csv = exporter.exportSessionsCsv(emptyList())
        assertTrue("Debe iniciar con BOM UTF-8", csv.startsWith("﻿"))
    }

    // -------------------------------------------------------------------------
    // Separador punto y coma
    // -------------------------------------------------------------------------

    @Test
    fun encabezadoIntentos_usaPuntoYComaComoSeparador() {
        val csv = exporter.exportAttemptsCsv(emptyList())
        val header = headerLine(csv)
        assertTrue(header.contains(";"))
        assertFalse("No debe usar coma como separador", header.contains(","))
    }

    @Test
    fun encabezadoEventos_usaPuntoYComaComoSeparador() {
        val csv = exporter.exportEventsCsv(emptyList())
        val header = headerLine(csv)
        assertTrue(header.contains(";"))
        assertFalse(header.contains(","))
    }

    @Test
    fun encabezadoSesiones_usaPuntoYComaComoSeparador() {
        val csv = exporter.exportSessionsCsv(emptyList())
        val header = headerLine(csv)
        assertTrue(header.contains(";"))
        assertFalse(header.contains(","))
    }

    // -------------------------------------------------------------------------
    // Estructura básica — intentos
    // -------------------------------------------------------------------------

    @Test
    fun exportAttempts_empty_soloEncabezado() {
        val csv = exporter.exportAttemptsCsv(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertTrue(headerLine(csv).startsWith("session_id"))
    }

    @Test
    fun encabezadoIntentos_contieneColumnasRequeridas() {
        val cols = headerCols(exporter.exportAttemptsCsv(emptyList()))
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
        assertEquals(2, csv.trim().lines().size)
    }

    @Test
    fun dosIntentos_generaDosFila() {
        val session = bimodalSession().copy(
            attempts = listOf(bimodalAttempt(), bimodalAttempt().copy(attemptId = 2L))
        )
        val csv = exporter.exportAttemptsCsv(listOf(session))
        assertEquals(3, csv.trim().lines().size)
    }

    // -------------------------------------------------------------------------
    // Modo bimodal — intentos
    // -------------------------------------------------------------------------

    @Test
    fun bimodal_semanticResultPresente() {
        val csv = exporter.exportAttemptsCsv(listOf(bimodalSession()))
        val cols = headerCols(exporter.exportAttemptsCsv(emptyList()))
        val idx = cols.indexOf("semantic_result")
        val dataCol = dataRowCols(csv)[idx]
        assertEquals("CORRECT", dataCol)
    }

    @Test
    fun bimodal_classicResultVacio() {
        val csv = exporter.exportAttemptsCsv(listOf(bimodalSession()))
        val cols = headerCols(exporter.exportAttemptsCsv(emptyList()))
        val idx = cols.indexOf("classic_result")
        val dataCol = dataRowCols(csv)[idx]
        assertEquals("", dataCol)
    }

    @Test
    fun bimodal_latenciasPresentes() {
        val csv = exporter.exportAttemptsCsv(listOf(bimodalSession()))
        assertTrue(csv.contains("150"))
        assertTrue(csv.contains("950"))
    }

    // -------------------------------------------------------------------------
    // Modo clásico — intentos
    // -------------------------------------------------------------------------

    @Test
    fun clasico_semanticResultVacio() {
        val csv = exporter.exportAttemptsCsv(listOf(classicSession()))
        val cols = headerCols(exporter.exportAttemptsCsv(emptyList()))
        val idx = cols.indexOf("semantic_result")
        val dataCol = dataRowCols(csv)[idx]
        assertEquals("", dataCol)
    }

    @Test
    fun clasico_classicResultPresente() {
        val csv = exporter.exportAttemptsCsv(listOf(classicSession()))
        val cols = headerCols(exporter.exportAttemptsCsv(emptyList()))
        val idx = cols.indexOf("classic_result")
        val dataCol = dataRowCols(csv)[idx]
        assertEquals("ANSWERED", dataCol)
    }

    @Test
    fun clasico_noTieneSemanticResultComoCorrectoOIncorrecto() {
        val csv = exporter.exportAttemptsCsv(listOf(classicSession()))
        val cols = headerCols(exporter.exportAttemptsCsv(emptyList()))
        val idx = cols.indexOf("semantic_result")
        val dataCol = dataRowCols(csv)[idx]
        assertFalse(dataCol.equals("CORRECT", ignoreCase = true))
        assertFalse(dataCol.equals("INCORRECT", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Escaping CSV (punto y coma, comillas, saltos de línea)
    // -------------------------------------------------------------------------

    @Test
    fun transcriptConPuntoYComa_seEncierraEnComillas() {
        val attempt = bimodalAttempt().copy(transcription = "uno; dos")
        val session = bimodalSession().copy(attempts = listOf(attempt))
        val csv = exporter.exportAttemptsCsv(listOf(session))
        assertTrue(csv.contains("\"uno; dos\""))
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

    @Test
    fun transcriptConComa_noRequiereEscape() {
        val attempt = bimodalAttempt().copy(transcription = "uno, dos")
        val session = bimodalSession().copy(attempts = listOf(attempt))
        val csv = exporter.exportAttemptsCsv(listOf(session))
        assertTrue(csv.contains("uno, dos"))
    }

    // -------------------------------------------------------------------------
    // Consistencia de columnas — intentos
    // -------------------------------------------------------------------------

    @Test
    fun todasLasFilasIntentostienenMismaCantidadDeColumnas() {
        val sessions = listOf(bimodalSession(), classicSession())
        val csv = exporter.exportAttemptsCsv(sessions)
        val expectedCols = MetricsCsvExporter.ATTEMPTS_HEADER.size
        csv.trim().lines().forEach { line ->
            assertEquals(
                "Columnas incorrectas en: $line",
                expectedCols,
                countSemicolonColumns(line)
            )
        }
    }

    // -------------------------------------------------------------------------
    // CSV de sesiones
    // -------------------------------------------------------------------------

    @Test
    fun exportSessions_empty_soloEncabezado() {
        val csv = exporter.exportSessionsCsv(emptyList())
        assertEquals(1, csv.trim().lines().size)
        assertTrue(headerLine(csv).startsWith("session_id"))
    }

    @Test
    fun encabezadoSesiones_contieneColumnasRequeridas() {
        val cols = headerCols(exporter.exportSessionsCsv(emptyList()))
        assertTrue(cols.contains("session_id"))
        assertTrue(cols.contains("activity_id"))
        assertTrue(cols.contains("operation_mode"))
        assertTrue(cols.contains("total_attempts"))
        assertTrue(cols.contains("correct_count"))
        assertTrue(cols.contains("timeout_count"))
    }

    @Test
    fun exportSessions_unaSesion_generaFila() {
        val csv = exporter.exportSessionsCsv(listOf(bimodalSession()))
        assertEquals(2, csv.trim().lines().size)
    }

    @Test
    fun exportSessions_bimodal_correctCountPresente() {
        val csv = exporter.exportSessionsCsv(listOf(bimodalSession()))
        val cols = headerCols(exporter.exportSessionsCsv(emptyList()))
        val idx = cols.indexOf("correct_count")
        val dataCol = dataRowCols(csv)[idx]
        assertEquals("1", dataCol)
    }

    @Test
    fun exportSessions_clasico_correctCountVacio() {
        val csv = exporter.exportSessionsCsv(listOf(classicSession()))
        val cols = headerCols(exporter.exportSessionsCsv(emptyList()))
        val idxCorrect = cols.indexOf("correct_count")
        val idxIncorrect = cols.indexOf("incorrect_count")
        val dataRow = dataRowCols(csv)
        assertEquals("", dataRow[idxCorrect])
        assertEquals("", dataRow[idxIncorrect])
    }

    @Test
    fun todasLasFilasSesionestieneMismaCantidadDeColumnas() {
        val sessions = listOf(bimodalSession(), classicSession())
        val csv = exporter.exportSessionsCsv(sessions)
        val expectedCols = MetricsCsvExporter.SESSIONS_HEADER.size
        csv.trim().lines().forEach { line ->
            assertEquals(
                "Columnas incorrectas en: $line",
                expectedCols,
                countSemicolonColumns(line)
            )
        }
    }

    // -------------------------------------------------------------------------
    // CSV de eventos técnicos
    // -------------------------------------------------------------------------

    @Test
    fun exportEvents_empty_soloEncabezado() {
        val csv = exporter.exportEventsCsv(emptyList())
        assertEquals(1, csv.trim().lines().size)
        assertTrue(headerLine(csv).startsWith("session_id"))
    }

    @Test
    fun encabezadoEventos_contieneColumnasRequeridas() {
        val cols = headerCols(exporter.exportEventsCsv(emptyList()))
        assertTrue(cols.contains("session_id"))
        assertTrue(cols.contains("event_id"))
        assertTrue(cols.contains("event_type"))
        assertTrue(cols.contains("timestamp_ms"))
        assertTrue(cols.contains("latency_ms"))
    }

    @Test
    fun eventos_generaFilaPorEvento() {
        val session = bimodalSession().copy(technicalEvents = listOf(event1(), event2()))
        val csv = exporter.exportEventsCsv(listOf(session))
        assertEquals(3, csv.trim().lines().size)
    }

    @Test
    fun eventoConLatencia_apareceEnCsv() {
        val session = bimodalSession().copy(technicalEvents = listOf(event2()))
        val csv = exporter.exportEventsCsv(listOf(session))
        assertTrue(csv.contains("320"))
    }

    @Test
    fun eventoSinLatencia_ultimoCampoVacio() {
        val session = bimodalSession().copy(technicalEvents = listOf(event1()))
        val csv = exporter.exportEventsCsv(listOf(session))
        val dataRow = csv.trim().lines()[1]
        assertTrue(dataRow.endsWith(";"))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun headerLine(csv: String): String =
        csv.trim().lines().first().removePrefix("﻿")

    private fun headerCols(csv: String): List<String> =
        headerLine(csv).split(";")

    private fun dataRowCols(csv: String): List<String> =
        csv.trim().lines()[1].split(";")

    private fun countSemicolonColumns(line: String): Int {
        val clean = line.removePrefix("﻿")
        var count = 1
        var inQuotes = false
        for (ch in clean) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ';' && !inQuotes -> count++
            }
        }
        return count
    }

    // -------------------------------------------------------------------------
    // Fixtures
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
        eventId = 1L, questionId = 10L, attemptId = 1L,
        operationMode = "BIMODAL_INTELLIGENT", eventType = "STT_STARTED",
        message = null, timestampMs = 1150L, latencyMs = null
    )

    private fun event2() = ExportTechnicalEventDto(
        eventId = 2L, questionId = 10L, attemptId = 1L,
        operationMode = "BIMODAL_INTELLIGENT", eventType = "STT_FINAL",
        message = null, timestampMs = 1800L, latencyMs = 320L
    )
}
