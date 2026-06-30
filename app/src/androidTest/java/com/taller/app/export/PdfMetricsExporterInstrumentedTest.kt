package com.taller.app.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfMetricsExporterInstrumentedTest {

    private val exporter = PdfMetricsExporter()

    @Test
    fun sessionReport_generatesNonEmptyPdf() {
        val out = ByteArrayOutputStream()

        exporter.writeSessionReport(reportSession(), out)

        val bytes = out.toByteArray()
        assertTrue(bytes.size > 1_000)
        assertTrue(bytes.take(4).toByteArray().decodeToString() == "%PDF")
    }

    @Test
    fun sessionsSummary_generatesNonEmptyPdf() {
        val out = ByteArrayOutputStream()

        exporter.writeSessionsSummary(MetricsReport(listOf(reportSession())), out)

        val bytes = out.toByteArray()
        assertTrue(bytes.size > 1_000)
        assertTrue(bytes.take(4).toByteArray().decodeToString() == "%PDF")
    }

    @Test
    fun sessionReport_doesNotEmbedTechnicalPrivacyStringsInPlainBytes() {
        val out = ByteArrayOutputStream()

        exporter.writeSessionReport(reportSession(), out)

        val raw = out.toByteArray().decodeToString()
        listOf(
            "API key",
            "prompt",
            "payload",
            "local.properties",
            "CACHE_HIT",
            "NETWORK_SYNTHESIS",
            "hash",
            "audio path",
            "camera frame"
        ).forEach { forbidden ->
            assertFalse("No debe contener $forbidden", raw.contains(forbidden, ignoreCase = true))
        }
    }

    private fun reportSession() = MetricsSessionReport(
        sessionId = 7L,
        activityName = "Sesion de animales",
        topic = "La granja",
        modeLabel = "Inteligente",
        modeCode = "BIMODAL_INTELLIGENT",
        startedAtMs = 1_000L,
        finishedAtMs = 8_000L,
        durationMs = 7_000L,
        finalState = "Completada",
        totalQuestions = 2,
        correctCount = 1,
        incorrectCount = 1,
        noResponseCount = 0,
        notInterpretableCount = 0,
        timeoutCount = 0,
        totalAttempts = 2,
        averageResponseTimeMs = 1_200L,
        attentionLossCount = 1,
        recaptureCount = 1,
        closeReason = "Completada",
        configuredTimePerQuestionMs = 30_000L,
        details = List(35) { index ->
            MetricsQuestionAttemptReport(
                questionOrder = index + 1,
                questionText = "Pregunta larga para validar salto de linea numero ${index + 1}",
                expectedAnswer = "respuesta esperada",
                capturedAnswer = "respuesta capturada",
                attemptNumber = 1,
                result = if (index % 2 == 0) "Correcta" else "Incorrecta",
                responseTimeMs = 1_000L + index,
                timeout = false,
                timestampMs = 2_000L + index,
                evaluationLayer = "Local",
                attentionLost = index == 0,
                recaptured = index == 0
            )
        }
    )
}
