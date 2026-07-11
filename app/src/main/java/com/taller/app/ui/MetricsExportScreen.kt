package com.taller.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taller.app.data.local.AppDatabase
import com.taller.app.export.ExportSessionDto
import com.taller.app.export.MetricsCsvExporter
import com.taller.app.export.MetricsExportRepository
import com.taller.app.export.MetricsJsonExporter
import com.taller.app.export.MetricsReportMapper
import com.taller.app.export.PdfMetricsExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MetricsExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getInstance(context) }
    val repository = remember {
        MetricsExportRepository(
            db.sessionDao(),
            db.attemptDao(),
            db.technicalEventDao(),
            db.activityDao(),
            db.questionDao()
        )
    }
    val jsonExporter = remember { MetricsJsonExporter() }
    val csvExporter = remember { MetricsCsvExporter() }
    val pdfExporter = remember { PdfMetricsExporter() }

    var sessionCount by remember { mutableIntStateOf(0) }
    var attemptCount by remember { mutableIntStateOf(0) }
    var eventCount by remember { mutableIntStateOf(0) }
    var isLoadingCounts by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var exportIsError by remember { mutableStateOf(false) }
    var recentSessions by remember { mutableStateOf(emptyList<ExportSessionDto>()) }
    var pendingPdfSessionId by remember { mutableStateOf<Long?>(null) }

    // FINAL-FLOW01: filtros basicos de la lista de sesiones recientes. Solo afectan
    // la vista; las exportaciones JSON/CSV/PDF general siguen incluyendo todo.
    var sessionModeFilter by remember { mutableStateOf(SessionModeFilter.ALL) }
    var sessionSearchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoadingCounts = true
        sessionCount = repository.countSessions()
        attemptCount = repository.countAttempts()
        eventCount = repository.countTechnicalEvents()
        recentSessions = repository.buildExportSessions()
            .asReversed()
            .take(30)
        isLoadingCounts = false
    }

    val filteredSessions = remember(recentSessions, sessionModeFilter, sessionSearchText) {
        val query = sessionSearchText.trim().lowercase()
        recentSessions.filter { session ->
            val modeOk = when (sessionModeFilter) {
                SessionModeFilter.ALL -> true
                SessionModeFilter.INTELLIGENT -> session.operationMode != "CLASSIC"
                SessionModeFilter.CLASSIC -> session.operationMode == "CLASSIC"
            }
            val textOk = query.isEmpty() ||
                session.sessionId.toString().contains(query) ||
                session.activityName.orEmpty().lowercase().contains(query) ||
                session.activityTopic.orEmpty().lowercase().contains(query)
            modeOk && textOk
        }.take(10)
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportStatus = null
            try {
                val sessions = repository.buildExportSessions()
                val content = jsonExporter.export(sessions)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("No se pudo abrir el archivo de destino")
                }
                exportStatus = "JSON exportado correctamente (${sessions.size} sesiones)"
                exportIsError = false
            } catch (e: Exception) {
                exportStatus = "Error al exportar JSON: ${e.message}"
                exportIsError = true
            } finally {
                isExporting = false
            }
        }
    }

    val sessionsCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportStatus = null
            try {
                val sessions = repository.buildExportSessions()
                val content = csvExporter.exportSessionsCsv(sessions)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("No se pudo abrir el archivo de destino")
                }
                exportStatus = "CSV de sesiones exportado (${sessions.size} filas)"
                exportIsError = false
            } catch (e: Exception) {
                exportStatus = "Error al exportar CSV de sesiones: ${e.message}"
                exportIsError = true
            } finally {
                isExporting = false
            }
        }
    }

    val attemptsCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportStatus = null
            try {
                val sessions = repository.buildExportSessions()
                val content = csvExporter.exportAttemptsCsv(sessions)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("No se pudo abrir el archivo de destino")
                }
                val rowCount = sessions.sumOf { it.attempts.size }
                exportStatus = "CSV de intentos exportado ($rowCount filas)"
                exportIsError = false
            } catch (e: Exception) {
                exportStatus = "Error al exportar CSV de intentos: ${e.message}"
                exportIsError = true
            } finally {
                isExporting = false
            }
        }
    }

    val eventsCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportStatus = null
            try {
                val sessions = repository.buildExportSessions()
                val content = csvExporter.exportEventsCsv(sessions)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("No se pudo abrir el archivo de destino")
                }
                val rowCount = sessions.sumOf { it.technicalEvents.size }
                exportStatus = "CSV de eventos exportado ($rowCount filas)"
                exportIsError = false
            } catch (e: Exception) {
                exportStatus = "Error al exportar CSV de eventos: ${e.message}"
                exportIsError = true
            } finally {
                isExporting = false
            }
        }
    }

    val summaryPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportStatus = null
            try {
                val sessions = repository.buildExportSessions()
                val report = MetricsReportMapper.mapAll(sessions)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        pdfExporter.writeSessionsSummary(report, out)
                    } ?: error("No se pudo abrir el archivo de destino")
                }
                exportStatus = "PDF general exportado (${sessions.size} sesiones)"
                exportIsError = false
            } catch (e: Exception) {
                exportStatus = "Error al exportar PDF general: ${e.message}"
                exportIsError = true
            } finally {
                isExporting = false
            }
        }
    }

    val sessionPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val sessionId = pendingPdfSessionId
        pendingPdfSessionId = null
        if (uri == null || sessionId == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportStatus = null
            try {
                val session = repository.buildExportSessions()
                    .firstOrNull { it.sessionId == sessionId }
                    ?: error("No se encontro la sesion $sessionId")
                val report = MetricsReportMapper.mapSession(session)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        pdfExporter.writeSessionReport(report, out)
                    } ?: error("No se pudo abrir el archivo de destino")
                }
                exportStatus = "PDF de sesion $sessionId exportado correctamente"
                exportIsError = false
            } catch (e: Exception) {
                exportStatus = "Error al exportar PDF de sesion: ${e.message}"
                exportIsError = true
            } finally {
                isExporting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RecordsWhite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Registros",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = RecordsPink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Exportación del datalogger",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = RecordsSubtitle,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(34.dp))

        RecordsSummarySection(
            isLoading = isLoadingCounts,
            sessionCount = sessionCount,
            attemptCount = attemptCount,
            eventCount = eventCount
        )

        Spacer(modifier = Modifier.height(24.dp))
        SessionFiltersSection(
            modeFilter = sessionModeFilter,
            searchText = sessionSearchText,
            onModeFilterChange = { sessionModeFilter = it },
            onSearchTextChange = { sessionSearchText = it.take(40) }
        )

        Spacer(modifier = Modifier.height(12.dp))
        RecentSessionsSection(
            isLoading = isLoadingCounts,
            sessions = filteredSessions,
            modeFilter = sessionModeFilter,
            isExporting = isExporting,
            onExportPdf = { session ->
                pendingPdfSessionId = session.sessionId
                sessionPdfLauncher.launch("reporte_sesion_${session.sessionId}.pdf")
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = RecordsDivider, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Exportar datos",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RecordsPink,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExportButton(
            text = "Exportar JSON (sesiones + intentos + eventos)",
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                jsonLauncher.launch("interaction_metrics_$ts.json")
            },
            enabled = !isExporting
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExportButton(
            text = "Exportar CSV - Sesiones",
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                sessionsCsvLauncher.launch("sessions_$ts.csv")
            },
            enabled = !isExporting
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExportButton(
            text = "Exportar CSV - Intentos por pregunta",
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                attemptsCsvLauncher.launch("interaction_attempts_$ts.csv")
            },
            enabled = !isExporting
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExportButton(
            text = "Exportar CSV - Eventos técnicos",
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                eventsCsvLauncher.launch("technical_events_$ts.csv")
            },
            enabled = !isExporting
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExportButton(
            text = "Exportar PDF - Resumen de sesiones",
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                summaryPdfLauncher.launch("reporte_metricas_$ts.pdf")
            },
            enabled = !isExporting
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (isExporting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = RecordsPink)
                Text("Exportando...", fontSize = 14.sp, color = RecordsText)
            }
        }

        exportStatus?.let { status ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status,
                color = if (exportIsError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        BottomBackButton(onClick = onBack)

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RecordsSummarySection(
    isLoading: Boolean,
    sessionCount: Int,
    attemptCount: Int,
    eventCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Resumen de registros",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RecordsPink
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            CircularProgressIndicator(
                color = RecordsPink,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            )
        } else {
            RecordsSummaryRow(label = "Sesiones registradas", value = sessionCount)
            RecordsSummaryRow(label = "Intentos registrados", value = attemptCount)
            RecordsSummaryRow(label = "Eventos técnicos", value = eventCount)
        }
    }
}

@Composable
private fun RecordsSummaryRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = RecordsText
        )
        Text(
            text = value.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = RecordsText
        )
    }
}

/** FINAL-FLOW01: filtro de modo para la lista de sesiones recientes. */
private enum class SessionModeFilter(val label: String) {
    ALL("Todas"),
    INTELLIGENT("Inteligente"),
    CLASSIC("Temporizador")
}

@Composable
private fun SessionFiltersSection(
    modeFilter: SessionModeFilter,
    searchText: String,
    onModeFilterChange: (SessionModeFilter) -> Unit,
    onSearchTextChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Filtrar sesiones",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = RecordsPink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SessionModeFilter.entries.forEach { option ->
                val selected = option == modeFilter
                if (selected) {
                    Button(
                        onClick = { onModeFilterChange(option) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecordsBackPink,
                            contentColor = Color.White
                        )
                    ) {
                        Text(option.label, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onModeFilterChange(option) },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(option.label, fontSize = 13.sp, color = RecordsText)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text("Buscar por ID, nombre o tema") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecentSessionsSection(
    isLoading: Boolean,
    sessions: List<ExportSessionDto>,
    modeFilter: SessionModeFilter,
    isExporting: Boolean,
    onExportPdf: (ExportSessionDto) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sesiones recientes",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RecordsPink
        )

        Spacer(modifier = Modifier.height(14.dp))

        when {
            isLoading -> CircularProgressIndicator(
                color = RecordsPink,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            )
            sessions.isEmpty() -> Text(
                text = if (modeFilter == SessionModeFilter.CLASSIC) {
                    "El modo temporizador no registra métricas. Solo presenta preguntas con tiempo."
                } else {
                    "No hay sesiones que coincidan con el filtro."
                },
                fontSize = 14.sp,
                color = RecordsText
            )
            else -> sessions.forEach { session ->
                val isClassicHistory = session.operationMode == "CLASSIC"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = buildString {
                            append("ID ${session.sessionId} - ${session.activityName ?: "Sesion sin nombre"}")
                            if (isClassicHistory) append(" (histórico)")
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RecordsText
                    )
                    Text(
                        text = "Estado: ${session.finalState ?: "En curso"} | " +
                            "Modo: ${MetricsReportMapper.modeLabel(session.operationMode)} | " +
                            "Preguntas: ${session.summary.completedQuestions}/${session.summary.totalQuestions}",
                        fontSize = 13.sp,
                        color = RecordsSubtitle
                    )
                    if (isClassicHistory) {
                        Text(
                            text = "Registro histórico anterior a la simplificación del temporizador.",
                            fontSize = 13.sp,
                            color = RecordsSubtitle
                        )
                    } else {
                        Text(
                            text = "Respondidas: ${session.attempts.count { it.transcription != null }} | " +
                                "Sin respuesta: ${session.summary.noResponseCount} | " +
                                "Correctas: ${session.summary.correctCount ?: 0} | " +
                                "Incorrectas: ${session.summary.incorrectCount ?: 0}",
                            fontSize = 13.sp,
                            color = RecordsSubtitle
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportButton(
                        text = "Exportar PDF de esta sesion",
                        onClick = { onExportPdf(session) },
                        enabled = !isExporting
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RecordsButtonPink,
            contentColor = RecordsText,
            disabledContainerColor = RecordsButtonPink.copy(alpha = 0.45f),
            disabledContentColor = RecordsText.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BottomBackButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RecordsBackPink,
            contentColor = Color.White
        ),
        modifier = Modifier
            .widthIn(min = 136.dp)
            .height(48.dp)
    ) {
        Text(
            text = "Volver",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val RecordsWhite = Color(0xFFFFFFFF)
private val RecordsPink = Color(0xFFFF6FA3)
private val RecordsSubtitle = Color(0xFF3F3A4A)
private val RecordsText = Color(0xFF2E2535)
private val RecordsDivider = Color(0xFFF2A7C5)
private val RecordsButtonPink = Color(0xFFF2B7D0)
private val RecordsBackPink = Color(0xFFFF75A8)
