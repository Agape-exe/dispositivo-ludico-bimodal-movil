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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.launch

private enum class TeacherView { LIST, FORM }

@Composable
fun TeacherActivitiesScreen(onBack: () -> Unit, onNavigateToQuestions: (Long) -> Unit) {
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
            onManageQuestions = { onNavigateToQuestions(it.id) }
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
    onManageQuestions: (ActivityEntity) -> Unit
) {
    TeacherPanelContainer(modifier = Modifier.navigationBarsPadding()) {
        Spacer(modifier = Modifier.height(38.dp))
        SectionTitle(
            text = "Gestión de\nActividades",
            centered = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(22.dp))
        PastelActionButton(
            text = "+ Nueva actividad",
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
                    text = "No hay actividades guardadas.\nCrea la primera actividad para comenzar.",
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
                        onManageQuestions = { onManageQuestions(activity) }
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
    onManageQuestions: () -> Unit
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

    TeacherPanelContainer(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(34.dp))
        TeacherFormHeader(
            title = if (existing == null) "Nueva actividad" else "Editar actividad",
            onCancel = onCancel
        )
        Spacer(modifier = Modifier.height(26.dp))

        PastelTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = "Nombre *",
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
            value = objective,
            onValueChange = { objective = it },
            label = "Objetivo"
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 4
        )
        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "Configuración adicional",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TeacherPrimaryPurple
        )
        Spacer(modifier = Modifier.height(14.dp))
        PastelTextField(
            value = ageLevel,
            onValueChange = { ageLevel = it },
            label = "Edad / Nivel"
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Modo de operación *",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (modeError) MaterialTheme.colorScheme.error else TeacherPrimaryPurple
        )
        Column(modifier = Modifier.selectableGroup()) {
            listOf("CLASSIC" to "Clásico", "ADVANCED" to "Avanzado").forEach { (value, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = operationMode == value,
                            onClick = {
                                operationMode = value
                                modeError = false
                            },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 3.dp)
                ) {
                    RadioButton(
                        selected = operationMode == value,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = TeacherPrimaryPurple)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = TeacherTextColor)
                }
            }
        }
        if (modeError) {
            Text(
                text = "Selecciona un modo de operación",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = maxTimeSeconds,
            onValueChange = { maxTimeSeconds = it; timeError = false },
            label = "Tiempo máximo por pregunta (s) *",
            isError = timeError,
            supportingText = if (timeError) "Debe ser un número mayor que 0" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = maxAttempts,
            onValueChange = { maxAttempts = it; attemptsError = false },
            label = "Número máximo de intentos *",
            isError = attemptsError,
            supportingText = if (attemptsError) "Debe ser un número mayor que 0" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(28.dp))

        PastelActionButton(
            text = if (existing == null) "Crear actividad" else "Editar actividad",
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
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}
