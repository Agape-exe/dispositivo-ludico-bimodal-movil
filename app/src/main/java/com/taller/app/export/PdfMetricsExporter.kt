package com.taller.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class PdfMetricsExporter(
    private val locale: Locale = Locale.getDefault()
) {

    fun writeSessionReport(session: MetricsSessionReport, output: OutputStream) {
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document)
            writer.title("Reporte de metricas de sesion")
            writer.subtitle(session.activityName)
            writer.keyValue("Identificador", session.sessionId.toString())
            writer.keyValue("Tema", session.topic)
            writer.keyValue("Modo", session.modeLabel)
            writer.keyValue("Inicio", formatDate(session.startedAtMs))
            writer.keyValue("Fin", formatDate(session.finishedAtMs))
            writer.keyValue("Duracion", formatDuration(session.durationMs))
            writer.keyValue("Estado final", session.finalState)
            writer.section("Resumen")
            writer.summaryGrid(summaryRows(session))
            writer.section("Detalle por pregunta")
            writer.attemptTable(
                session.details,
                session.isIntelligent,
                session.attentionTrackingEnabled
            )
            writer.section("Privacidad")
            writer.paragraph(
                "El reporte contiene texto transcrito y metricas de interaccion. " +
                    "No incluye audio crudo, imagenes ni datos biometricos."
            )
            writer.finish()
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    fun writeSessionsSummary(report: MetricsReport, output: OutputStream) {
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document)
            writer.title("Resumen de sesiones registradas")
            writer.keyValue("Sesiones incluidas", report.sessions.size.toString())
            writer.keyValue("Generado", formatDate(System.currentTimeMillis()))
            writer.section("Listado")
            writer.sessionsTable(report.sessions)
            writer.section("Privacidad")
            writer.paragraph(
                "El reporte contiene texto transcrito y metricas de interaccion. " +
                    "No incluye audio crudo, imagenes ni datos biometricos."
            )
            writer.finish()
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun summaryRows(session: MetricsSessionReport): List<Pair<String, String>> {
        val base = mutableListOf(
            "Total de preguntas" to session.totalQuestions.toString(),
            "Correctas" to nullableCount(session.correctCount),
            "Incorrectas" to nullableCount(session.incorrectCount),
            "Nulas / sin respuesta" to session.noResponseCount.toString(),
            "No interpretables" to nullableCount(session.notInterpretableCount),
            "Timeouts" to session.timeoutCount.toString(),
            "Tiempo promedio de respuesta" to formatDuration(session.averageResponseTimeMs),
            "Tiempo por pregunta" to formatDuration(session.configuredTimePerQuestionMs)
        )
        if (session.isIntelligent) {
            // FINAL-FLOW01: con la atencion por camara desactivada, el reporte lo
            // dice de forma explicita ("Desactivada" / "No aplica") en lugar de
            // mostrar ceros o guiones que se confundan con "sin distracciones".
            if (session.attentionTrackingEnabled) {
                base += "Atencion por camara" to "Activada"
                base += "Distracciones / perdida de atencion" to
                    session.attentionLossCount.toString()
            } else {
                base += "Atencion por camara" to "Desactivada"
                base += "Distracciones / perdida de atencion" to "No aplica"
            }
            base += "Recapturas de atencion" to if (session.recaptureTrackingEnabled) {
                session.recaptureCount.toString()
            } else {
                "No aplica"
            }
            base += "Intentos totales" to session.totalAttempts.toString()
            base += "Promedio de intentos por pregunta" to averageAttempts(session)
            base += "Motivo de cierre" to session.closeReason
        }
        return base
    }

    private fun averageAttempts(session: MetricsSessionReport): String {
        if (session.totalQuestions <= 0) return MetricsReportMapper.NOT_RECORDED
        return String.format(locale, "%.1f", session.totalAttempts.toFloat() / session.totalQuestions)
    }

    private fun nullableCount(value: Int?): String = value?.toString() ?: MetricsReportMapper.NOT_RECORDED

    private fun formatDate(value: Long?): String =
        value?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(it)) }
            ?: MetricsReportMapper.NOT_RECORDED

    private fun formatDuration(valueMs: Long?): String =
        valueMs?.let { String.format(locale, "%.1f s", it / 1000.0) }
            ?: MetricsReportMapper.NOT_RECORDED

    private inner class PdfWriter(
        private val document: PdfDocument
    ) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 36f
        private val footerY = pageHeight - 24f
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = margin

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 33, 33)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
        }
        private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 33, 33)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
        }
        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 255, 255)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 45, 45)
            textSize = 9f
        }
        private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(80, 80, 80)
            textSize = 8f
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 210, 210)
            strokeWidth = 1f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 238, 238)
            style = Paint.Style.FILL
        }
        private val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(86, 86, 86)
            style = Paint.Style.FILL
        }

        init {
            newPage()
        }

        fun title(text: String) {
            drawWrapped(text, margin, y, pageWidth - margin * 2, titlePaint, 24f)
            y += 30f
        }

        fun subtitle(text: String) {
            drawWrapped(text, margin, y, pageWidth - margin * 2, sectionPaint, 16f)
            y += 20f
        }

        fun keyValue(label: String, value: String) {
            ensureSpace(16f)
            canvas.drawText("$label:", margin, y, sectionPaint)
            drawWrapped(value, margin + 118f, y, pageWidth - margin * 2 - 118f, bodyPaint, 12f)
            y += 16f
        }

        fun section(text: String) {
            y += 10f
            ensureSpace(24f)
            canvas.drawText(text, margin, y, sectionPaint)
            y += 10f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 14f
        }

        fun paragraph(text: String) {
            ensureSpace(36f)
            val lines = wrap(text, pageWidth - margin * 2, bodyPaint)
            lines.forEach { line ->
                ensureSpace(12f)
                canvas.drawText(line, margin, y, bodyPaint)
                y += 12f
            }
        }

        fun summaryGrid(rows: List<Pair<String, String>>) {
            val labelWidth = 210f
            rows.forEach { (label, value) ->
                ensureSpace(18f)
                canvas.drawText(label, margin, y, bodyPaint)
                canvas.drawText(value, margin + labelWidth, y, bodyPaint)
                y += 15f
            }
        }

        fun attemptTable(
            details: List<MetricsQuestionAttemptReport>,
            intelligent: Boolean,
            attentionApplicable: Boolean = true
        ) {
            if (details.isEmpty()) {
                paragraph("No hay intentos registrados para esta sesion.")
                return
            }
            val widths = if (intelligent) {
                floatArrayOf(24f, 122f, 90f, 70f, 52f, 44f, 42f)
            } else {
                floatArrayOf(24f, 142f, 102f, 74f, 56f, 58f)
            }
            val headers = if (intelligent) {
                listOf("N", "Pregunta", "Respuesta", "Resultado", "Tiempo", "Intento", "Atencion")
            } else {
                listOf("N", "Pregunta", "Respuesta", "Resultado", "Tiempo", "Timeout")
            }
            tableHeader(headers, widths)
            details.forEach { row ->
                val cells = if (intelligent) {
                    listOf(
                        row.questionOrder.toString(),
                        "${row.questionText}\nRef: ${row.expectedAnswer}",
                        row.capturedAnswer,
                        row.result,
                        formatDuration(row.responseTimeMs),
                        row.attemptNumber.toString(),
                        if (attentionApplicable) attentionText(row) else "No aplica"
                    )
                } else {
                    listOf(
                        row.questionOrder.toString(),
                        "${row.questionText}\nRef: ${row.expectedAnswer}",
                        row.capturedAnswer,
                        row.result,
                        formatDuration(row.responseTimeMs),
                        if (row.timeout) "Si" else "No"
                    )
                }
                tableRow(cells, widths)
            }
        }

        fun sessionsTable(sessions: List<MetricsSessionReport>) {
            if (sessions.isEmpty()) {
                paragraph("No hay sesiones registradas.")
                return
            }
            val widths = floatArrayOf(30f, 58f, 70f, 120f, 54f, 72f, 42f, 42f, 42f, 56f)
            val headers = listOf("ID", "Fecha", "Modo", "Nombre / tema", "Dur.", "Estado", "OK", "Mal", "Nulas", "Prom.")
            tableHeader(headers, widths)
            sessions.forEach { session ->
                val cells = listOf(
                    session.sessionId.toString(),
                    formatDate(session.startedAtMs).take(16),
                    session.modeLabel,
                    "${session.activityName}\n${session.topic}",
                    formatDuration(session.durationMs),
                    session.finalState,
                    nullableCount(session.correctCount),
                    nullableCount(session.incorrectCount),
                    session.noResponseCount.toString(),
                    formatDuration(session.averageResponseTimeMs)
                )
                tableRow(cells, widths)
            }
        }

        fun finish() {
            finishPage()
        }

        private fun tableHeader(headers: List<String>, widths: FloatArray) {
            ensureSpace(26f)
            val rowHeight = 18f
            var x = margin
            canvas.drawRect(margin, y - 11f, pageWidth - margin, y + rowHeight - 11f, headerFill)
            headers.forEachIndexed { index, header ->
                canvas.drawText(header, x + 3f, y, headerPaint)
                x += widths[index]
            }
            y += rowHeight
        }

        private fun tableRow(cells: List<String>, widths: FloatArray) {
            val wrapped = cells.mapIndexed { index, cell ->
                cell.lines().flatMap { wrap(it, widths[index] - 6f, smallPaint) }.ifEmpty { listOf("") }
            }
            val rowHeight = max(22f, wrapped.maxOf { it.size } * 10f + 8f)
            ensureSpace(rowHeight + 4f)
            var x = margin
            canvas.drawRect(margin, y - 10f, pageWidth - margin, y + rowHeight - 10f, fillPaint)
            wrapped.forEachIndexed { index, lines ->
                var cellY = y
                lines.take(5).forEach { line ->
                    canvas.drawText(line, x + 3f, cellY, smallPaint)
                    cellY += 10f
                }
                x += widths[index]
            }
            y += rowHeight
            canvas.drawLine(margin, y - 8f, pageWidth - margin, y - 8f, linePaint)
        }

        private fun attentionText(row: MetricsQuestionAttemptReport): String = when {
            row.attentionLost && row.recaptured -> "Perdida / recaptura"
            row.attentionLost -> "Perdida"
            row.recaptured -> "Recaptura"
            else -> "--"
        }

        private fun drawWrapped(text: String, x: Float, startY: Float, width: Float, paint: Paint, lineHeight: Float) {
            var lineY = startY
            wrap(text, width, paint).forEach { line ->
                canvas.drawText(line, x, lineY, paint)
                lineY += lineHeight
            }
        }

        private fun wrap(text: String, width: Float, paint: Paint): List<String> {
            val clean = text.replace("\r", " ").replace("\t", " ")
            val lines = mutableListOf<String>()
            clean.split('\n').forEach { paragraph ->
                var current = ""
                paragraph.split(' ').filter { it.isNotBlank() }.forEach { word ->
                    val candidate = if (current.isBlank()) word else "$current $word"
                    if (paint.measureText(candidate) <= width) {
                        current = candidate
                    } else {
                        if (current.isNotBlank()) lines += current
                        current = truncateLongWord(word, width, paint)
                    }
                }
                if (current.isNotBlank()) lines += current
            }
            return lines.ifEmpty { listOf("") }
        }

        private fun truncateLongWord(word: String, width: Float, paint: Paint): String {
            if (paint.measureText(word) <= width) return word
            var result = word
            while (result.length > 4 && paint.measureText("$result...") > width) {
                result = result.dropLast(1)
            }
            return "$result..."
        }

        private fun ensureSpace(required: Float) {
            if (y + required > footerY - 10f) {
                finishPage()
                newPage()
            }
        }

        private fun newPage() {
            pageNumber += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = requireNotNull(page).canvas
            y = margin
        }

        private fun finishPage() {
            val current = page ?: return
            canvas.drawText("Pagina $pageNumber", pageWidth - margin - 58f, footerY, smallPaint)
            document.finishPage(current)
            page = null
        }
    }
}
