package com.taller.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TeacherView { LIST, FORM }

@Composable
fun TeacherActivitiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.activityDao() }
    val scope = rememberCoroutineScope()

    val activities by dao.getAllOrderedByUpdated().collectAsState(initial = emptyList())

    var view by remember { mutableStateOf(TeacherView.LIST) }
    var editingActivity by remember { mutableStateOf<ActivityEntity?>(null) }

    BackHandler {
        if (view == TeacherView.FORM) {
            view = TeacherView.LIST
        } else {
            onBack()
        }
    }

    when (view) {
        TeacherView.LIST -> ActivityListView(
            activities = activities,
            onBack = onBack,
            onCreate = {
                editingActivity = null
                view = TeacherView.FORM
            },
            onEdit = { activity ->
                editingActivity = activity
                view = TeacherView.FORM
            }
        )
        TeacherView.FORM -> ActivityFormView(
            existing = editingActivity,
            onSave = { entity ->
                scope.launch {
                    if (entity.id == 0L) dao.insert(entity) else dao.update(entity)
                    view = TeacherView.LIST
                }
            },
            onCancel = { view = TeacherView.LIST }
        )
    }
}

@Composable
private fun ActivityListView(
    activities: List<ActivityEntity>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (ActivityEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gestión de actividades",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) {
                Text("Volver")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Nueva actividad")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay actividades guardadas.\nCrea la primera actividad con el botón de arriba.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activities, key = { it.id }) { activity ->
                    ActivityCard(activity = activity, onClick = { onEdit(activity) })
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(activity: ActivityEntity, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val modeLabel = if (activity.operationMode == "ADVANCED") "Avanzado" else "Clásico"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tema: ${activity.topic}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Modo: $modeLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Actualizado: ${dateFormat.format(Date(activity.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ActivityFormView(
    existing: ActivityEntity?,
    onSave: (ActivityEntity) -> Unit,
    onCancel: () -> Unit
) {
    val key = existing?.id

    var name by remember(key) { mutableStateOf(existing?.name ?: "") }
    var topic by remember(key) { mutableStateOf(existing?.topic ?: "") }
    var description by remember(key) { mutableStateOf(existing?.description ?: "") }
    var objective by remember(key) { mutableStateOf(existing?.objective ?: "") }
    var ageLevel by remember(key) { mutableStateOf(existing?.ageLevel ?: "") }
    var operationMode by remember(key) { mutableStateOf(existing?.operationMode ?: "") }
    var maxTimeSeconds by remember(key) { mutableStateOf(existing?.maxTimeSeconds?.toString() ?: "60") }
    var maxAttempts by remember(key) { mutableStateOf(existing?.maxAttempts?.toString() ?: "3") }

    var nameError by remember(key) { mutableStateOf(false) }
    var topicError by remember(key) { mutableStateOf(false) }
    var modeError by remember(key) { mutableStateOf(false) }
    var timeError by remember(key) { mutableStateOf(false) }
    var attemptsError by remember(key) { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = name.isBlank()
        topicError = topic.isBlank()
        modeError = operationMode.isEmpty()
        timeError = maxTimeSeconds.toIntOrNull()?.let { it <= 0 } ?: true
        attemptsError = maxAttempts.toIntOrNull()?.let { it <= 0 } ?: true
        return !nameError && !topicError && !modeError && !timeError && !attemptsError
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (existing == null) "Nueva actividad" else "Editar actividad",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text("Nombre *") },
            isError = nameError,
            supportingText = if (nameError) {
                { Text("El nombre es obligatorio") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it; topicError = false },
            label = { Text("Tema *") },
            isError = topicError,
            supportingText = if (topicError) {
                { Text("El tema es obligatorio") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = objective,
            onValueChange = { objective = it },
            label = { Text("Objetivo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = ageLevel,
            onValueChange = { ageLevel = it },
            label = { Text("Edad / Nivel") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Modo de operación *",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (modeError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )

        Column(modifier = Modifier.selectableGroup()) {
            listOf("CLASSIC" to "Clásico", "ADVANCED" to "Avanzado").forEach { (value, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = operationMode == value,
                            onClick = { operationMode = value; modeError = false },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(selected = operationMode == value, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (modeError) {
            Text(
                text = "Selecciona un modo de operación",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = maxTimeSeconds,
            onValueChange = { maxTimeSeconds = it; timeError = false },
            label = { Text("Tiempo máximo por pregunta (segundos) *") },
            isError = timeError,
            supportingText = if (timeError) {
                { Text("Debe ser un número mayor que 0") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = maxAttempts,
            onValueChange = { maxAttempts = it; attemptsError = false },
            label = { Text("Número máximo de intentos *") },
            isError = attemptsError,
            supportingText = if (attemptsError) {
                { Text("Debe ser un número mayor que 0") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (validate()) {
                    val now = System.currentTimeMillis()
                    onSave(
                        ActivityEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            topic = topic.trim(),
                            description = description.trim().ifEmpty { null },
                            objective = objective.trim().ifEmpty { null },
                            ageLevel = ageLevel.trim().ifEmpty { null },
                            operationMode = operationMode,
                            maxTimeSeconds = maxTimeSeconds.toInt(),
                            maxAttempts = maxAttempts.toInt(),
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existing == null) "Guardar actividad" else "Actualizar actividad")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
