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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.launch

private enum class TeacherView { LIST, FORM }

@Composable
fun TeacherActivitiesScreen(
    onBack: () -> Unit,
    onNavigateToQuestions: (Long) -> Unit,
    onNavigateToScript: (Long) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).activityDao() }
    val scope = rememberCoroutineScope()
    val activities by dao.getAllOrderedByUpdated().collectAsState(initial = emptyList())

    var view by remember { mutableStateOf(TeacherView.LIST) }
    var editingActivity by remember { mutableStateOf<ActivityEntity?>(null) }

    BackHandler {
        if (view == TeacherView.FORM) view = TeacherView.LIST else onBack()
    }

    when (view) {
        TeacherView.LIST -> ActivityListView(
            activities = activities,
            onBack = onBack,
            onCreate = {
                editingActivity = null
                view = TeacherView.FORM
            },
            onEdit = {
                editingActivity = it
                view = TeacherView.FORM
            },
            onManageQuestions = { onNavigateToQuestions(it.id) },
            onManageScript = { onNavigateToScript(it.id) },
            onDeactivate = { activity ->
                scope.launch { dao.setActive(activity.id, false) }
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
    onEdit: (ActivityEntity) -> Unit,
    onManageQuestions: (ActivityEntity) -> Unit,
    onManageScript: (ActivityEntity) -> Unit,
    onDeactivate: (ActivityEntity) -> Unit
) {
    var activityToDeactivate by remember { mutableStateOf<ActivityEntity?>(null) }

    activityToDeactivate?.let { activity ->
        AlertDialog(
            onDismissRequest = { activityToDeactivate = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = { Text("Desactivar sesión", color = TeacherTitleColor) },
            text = {
                Text(
                    "¿Deseas desactivar \"${activity.name}\"? " +
                        "La sesión dejará de aparecer en las listas. " +
                        "El historial de registros se conserva."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeactivate(activity)
                        activityToDeactivate = null
                    }
                ) {
                    Text("Desactivar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { activityToDeactivate = null }) {
                    Text("Cancelar", color = TeacherPrimaryPurple)
                }
            }
        )
    }

    TeacherPanelContainer(modifier = Modifier.navigationBarsPadding()) {
        Spacer(modifier = Modifier.height(38.dp))
        SectionTitle(
            text = "Gestión de\nSesiones",
            centered = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(22.dp))
        PastelActionButton(
            text = "+ Nueva sesión",
            onClick = onCreate,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(22.dp))

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay sesiones guardadas.\nCrea la primera sesión para comenzar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TeacherSecondaryTextColor,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(activities, key = { it.id }) { activity ->
                    TeacherActivityCard(
                        activity = activity,
                        onEdit = { onEdit(activity) },
                        onManageQuestions = { onManageQuestions(activity) },
                        onManageScript = { onManageScript(activity) },
                        onDeactivate = { activityToDeactivate = activity }
                    )
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }

        BottomBackButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 18.dp)
                .width(150.dp)
        )
    }
}

@Composable
private fun TeacherActivityCard(
    activity: ActivityEntity,
    onEdit: () -> Unit,
    onManageQuestions: () -> Unit,
    onManageScript: () -> Unit,
    onDeactivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TeacherCardLavender),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TeacherTextColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tema: ${activity.topic}",
                style = MaterialTheme.typography.bodyMedium,
                color = TeacherSecondaryTextColor
            )
            if (!activity.ageLevel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Edad: ${activity.ageLevel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TeacherSecondaryTextColor
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = TeacherPrimaryPurple
                    ),
                    border = null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Editar", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onManageQuestions,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TeacherSmallPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Preguntas", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onManageScript,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TeacherPrimaryPurple,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guion de Seven", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDeactivate,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desactivar sesión", fontWeight = FontWeight.SemiBold)
            }
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
    var objective by remember(key) { mutableStateOf(existing?.objective ?: "") }
    var description by remember(key) { mutableStateOf(existing?.description ?: "") }
    var ageLevel by remember(key) { mutableStateOf(existing?.ageLevel ?: "") }
    var classContextNotes by remember(key) { mutableStateOf(existing?.classContextNotes ?: "") }

    var nameError by remember(key) { mutableStateOf(false) }
    var topicError by remember(key) { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = name.isBlank()
        topicError = topic.isBlank()
        return !nameError && !topicError
    }

    TeacherPanelContainer(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(34.dp))
        TeacherFormHeader(
            title = if (existing == null) "Nueva sesión" else "Editar sesión",
            onCancel = onCancel
        )
        Spacer(modifier = Modifier.height(26.dp))

        PastelTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = "Nombre de la sesión *",
            isError = nameError,
            supportingText = if (nameError) "El nombre es obligatorio" else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = topic,
            onValueChange = { topic = it; topicError = false },
            label = "Tema *",
            isError = topicError,
            supportingText = if (topicError) "El tema es obligatorio" else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = objective,
            onValueChange = { objective = it },
            label = "Objetivo"
        )
        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "Datos de la clase",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TeacherPrimaryPurple
        )
        Spacer(modifier = Modifier.height(14.dp))
        PastelTextField(
            value = ageLevel,
            onValueChange = { ageLevel = it },
            label = "Rango de edad de los niños",
            supportingText = "Ej: 3 años, 4–5 años, 3 a 5 años"
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = classContextNotes,
            onValueChange = { classContextNotes = it },
            label = "Contexto / notas del grupo",
            supportingText = "Ej: grupo A, nivel inicial, observaciones relevantes",
            singleLine = false,
            minLines = 3
        )
        Spacer(modifier = Modifier.height(28.dp))

        PastelActionButton(
            text = if (existing == null) "Crear sesión" else "Guardar cambios",
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
                            classContextNotes = classContextNotes.trim().ifEmpty { null },
                            operationMode = existing?.operationMode ?: "",
                            maxTimeSeconds = existing?.maxTimeSeconds ?: 60,
                            maxAttempts = existing?.maxAttempts ?: 3,
                            isActive = existing?.isActive ?: true,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                            // Conserva el guion de Seven ya generado al editar la sesión.
                            generatedIntroText = existing?.generatedIntroText,
                            generatedClosingText = existing?.generatedClosingText,
                            generatedToneNotes = existing?.generatedToneNotes,
                            generatedPedagogicalWarnings = existing?.generatedPedagogicalWarnings,
                            scriptStatus = existing?.scriptStatus ?: "NOT_GENERATED",
                            scriptUpdatedAt = existing?.scriptUpdatedAt
                        )
                    )
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}
