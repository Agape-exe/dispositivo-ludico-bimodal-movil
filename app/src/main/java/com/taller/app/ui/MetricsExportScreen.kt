package com.taller.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taller.app.data.local.AppDatabase
import com.taller.app.export.MetricsCsvExporter
import com.taller.app.export.MetricsExportRepository
import com.taller.app.export.MetricsJsonExporter
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
        MetricsExportRepository(db.sessionDao(), db.attemptDao(), db.technicalEventDao())
    }
    val jsonExporter = remember { MetricsJsonExporter() }
    val csvExporter = remember { MetricsCsvExporter() }

    var sessionCount by remember { mutableIntStateOf(0) }
    var attemptCount by remember { mutableIntStateOf(0) }
    var eventCount by remember { mutableIntStateOf(0) }
    var isLoadingCounts by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var exportIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingCounts = true
        sessionCount = repository.countSessions()
        attemptCount = repository.countAttempts()
        eventCount = repository.countTechnicalEvents()
        isLoadingCounts = false
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Exportar métricas",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Resumen de registros",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoadingCounts) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else {
            SummaryRow(label = "Sesiones registradas", value = sessionCount)
            SummaryRow(label = "Intentos registrados", value = attemptCount)
            SummaryRow(label = "Eventos técnicos", value = eventCount)
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Exportar datos",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                jsonLauncher.launch("interaction_metrics_$ts.json")
            },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exportar JSON (sesiones + intentos + eventos)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                sessionsCsvLauncher.launch("sessions_$ts.csv")
            },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exportar CSV — sesiones")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                attemptsCsvLauncher.launch("interaction_attempts_$ts.csv")
            },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exportar CSV — intentos por pregunta")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                eventsCsvLauncher.launch("technical_events_$ts.csv")
            },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exportar CSV — eventos técnicos")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isExporting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text("Exportando…", fontSize = 14.sp)
            }
        }

        exportStatus?.let { status ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status,
                color = if (exportIsError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp)
        Text(text = value.toString(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
