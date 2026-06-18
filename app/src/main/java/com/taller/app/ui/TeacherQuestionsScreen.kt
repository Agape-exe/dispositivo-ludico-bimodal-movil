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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.QuestionEntity
import com.taller.app.model.LocalMediationKey
import kotlinx.coroutines.launch

private enum class QuestionView { LIST, FORM }

@Composable
fun TeacherQuestionsScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val questionDao = remember { db.questionDao() }
    val activityDao = remember { db.activityDao() }
    val scope = rememberCoroutineScope()

    val questions by questionDao.getByActivityId(activityId).collectAsState(initial = emptyList())
    var activity by remember { mutableStateOf<ActivityEntity?>(null) }
    var activityNotFound by remember { mutableStateOf(false) }

    LaunchedEffect(activityId) {
        val found = activityDao.getById(activityId)
        if (found == null) activityNotFound = true else activity = found
    }

    var view by remember { mutableStateOf(QuestionView.LIST) }
    var editingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }

    BackHandler {
        if (view == QuestionView.FORM) view = QuestionView.LIST else onBack()
    }

    when {
        activityNotFound -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "La actividad no existe o fue eliminada.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Volver") }
                }
            }
        }
        activity == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        view == QuestionView.LIST -> QuestionListView(
            activity = activity!!,
            questions = questions,
            onBack = onBack,
            onCreate = {
                editingQuestion = null
                view = QuestionView.FORM
            },
            onEdit = { question ->
                editingQuestion = question
                view = QuestionView.FORM
            },
            onDelete = { question ->
                scope.launch { questionDao.deleteById(question.id) }
            }
        )
        else -> QuestionFormView(
            activityId = activityId,
            existing = editingQuestion,
            onSave = { entity ->
                scope.launch {
                    if (entity.id == 0L) questionDao.insert(entity) else questionDao.update(entity)
                    view = QuestionView.LIST
                }
            },
            onCancel = { view = QuestionView.LIST }
        )
    }
}

@Composable
private fun QuestionListView(
    activity: ActivityEntity,
    questions: List<QuestionEntity>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (QuestionEntity) -> Unit,
    onDelete: (QuestionEntity) -> Unit
) {
    var questionToDelete by remember { mutableStateOf<QuestionEntity?>(null) }

    if (questionToDelete != null) {
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            title = { Text("Eliminar pregunta") },
            text = { Text("¿Deseas eliminar esta pregunta? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(questionToDelete!!)
                    questionToDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Preguntas",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onBack) {
                Text("Volver")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Nueva pregunta")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Esta actividad todavía no tiene preguntas.\nAgrega la primera con el botón de arriba.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(questions, key = { it.id }) { question ->
                    QuestionCard(
                        question = question,
                        onEdit = { onEdit(question) },
                        onDelete = { questionToDelete = question }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: QuestionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val mediationKey = LocalMediationKey.fromKey(question.mediationKey)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${question.orderIndex}. ${question.questionText}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Respuesta: ${question.expectedAnswer}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Palabras clave: ${question.keywords}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tiempo: ${question.maxTimeSeconds}s · Intentos: ${question.maxAttempts}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (mediationKey != LocalMediationKey.NONE) {
                Text(
                    text = "Mediación: ${mediationKey.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Editar")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionFormView(
    activityId: Long,
    existing: QuestionEntity?,
    onSave: (QuestionEntity) -> Unit,
    onCancel: () -> Unit
) {
    val key = existing?.id

    var orderIndex by remember(key) { mutableStateOf(existing?.orderIndex?.toString() ?: "1") }
    var questionText by remember(key) { mutableStateOf(existing?.questionText ?: "") }
    var expectedAnswer by remember(key) { mutableStateOf(existing?.expectedAnswer ?: "") }
    var keywords by remember(key) { mutableStateOf(existing?.keywords ?: "") }
    var maxTimeSeconds by remember(key) { mutableStateOf(existing?.maxTimeSeconds?.toString() ?: "10") }
    var maxAttempts by remember(key) { mutableStateOf(existing?.maxAttempts?.toString() ?: "2") }
    var mediationKey by remember(key) { mutableStateOf(LocalMediationKey.fromKey(existing?.mediationKey)) }
    var mediationDropdownExpanded by remember { mutableStateOf(false) }

    var questionTextError by remember(key) { mutableStateOf(false) }
    var expectedAnswerError by remember(key) { mutableStateOf(false) }
    var keywordsError by remember(key) { mutableStateOf(false) }
    var orderIndexError by remember(key) { mutableStateOf(false) }
    var timeError by remember(key) { mutableStateOf(false) }
    var attemptsError by remember(key) { mutableStateOf(false) }

    fun validate(): Boolean {
        questionTextError = questionText.isBlank()
        expectedAnswerError = expectedAnswer.isBlank()
        keywordsError = keywords.isBlank()
        orderIndexError = orderIndex.toIntOrNull()?.let { it < 1 } ?: true
        timeError = maxTimeSeconds.toIntOrNull()?.let { it <= 0 } ?: true
        attemptsError = maxAttempts.toIntOrNull()?.let { it <= 0 } ?: true
        return !questionTextError && !expectedAnswerError && !keywordsError &&
            !orderIndexError && !timeError && !attemptsError
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
                text = if (existing == null) "Nueva pregunta" else "Editar pregunta",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Datos de la pregunta",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = orderIndex,
            onValueChange = { orderIndex = it; orderIndexError = false },
            label = { Text("Orden de la pregunta *") },
            isError = orderIndexError,
            supportingText = if (orderIndexError) {
                { Text("Debe ser un número mayor o igual a 1") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = questionText,
            onValueChange = { questionText = it; questionTextError = false },
            label = { Text("Texto de la pregunta *") },
            isError = questionTextError,
            supportingText = if (questionTextError) {
                { Text("El texto de la pregunta es obligatorio") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = expectedAnswer,
            onValueChange = { expectedAnswer = it; expectedAnswerError = false },
            label = { Text("Respuesta esperada principal *") },
            isError = expectedAnswerError,
            supportingText = if (expectedAnswerError) {
                { Text("La respuesta esperada es obligatoria") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = keywords,
            onValueChange = { keywords = it; keywordsError = false },
            label = { Text("Respuestas aceptadas / palabras clave *") },
            isError = keywordsError,
            supportingText = if (keywordsError) {
                { Text("Ingresa al menos una palabra clave") }
            } else {
                { Text("Separa las respuestas válidas con comas.") }
            },
            placeholder = { Text("ej: guau, guau guau, ladra, ladrido") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = maxTimeSeconds,
            onValueChange = { maxTimeSeconds = it; timeError = false },
            label = { Text("Tiempo máximo de respuesta (segundos) *") },
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
            label = { Text("Intentos máximos *") },
            isError = attemptsError,
            supportingText = if (attemptsError) {
                { Text("Debe ser un número mayor que 0") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Mediación local",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = mediationDropdownExpanded,
            onExpandedChange = { mediationDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = mediationKey.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Clave de mediación local") },
                supportingText = { Text("Permite asociar esta pregunta con frases lúdicas predefinidas del juguete.") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mediationDropdownExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = mediationDropdownExpanded,
                onDismissRequest = { mediationDropdownExpanded = false }
            ) {
                LocalMediationKey.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            mediationKey = option
                            mediationDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (validate()) {
                    val now = System.currentTimeMillis()
                    onSave(
                        QuestionEntity(
                            id = existing?.id ?: 0L,
                            activityId = activityId,
                            questionText = questionText.trim(),
                            expectedAnswer = expectedAnswer.trim(),
                            keywords = keywords.trim(),
                            orderIndex = orderIndex.toInt(),
                            maxTimeSeconds = maxTimeSeconds.toInt(),
                            maxAttempts = maxAttempts.toInt(),
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                            mediationKey = if (mediationKey == LocalMediationKey.NONE) null else mediationKey.name
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existing == null) "Guardar pregunta" else "Actualizar pregunta")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
