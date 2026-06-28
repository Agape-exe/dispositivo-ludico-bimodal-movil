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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    var view by remember { mutableStateOf(QuestionView.LIST) }
    var editingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }

    LaunchedEffect(activityId) {
        val found = activityDao.getById(activityId)
        if (found == null) activityNotFound = true else activity = found
    }

    BackHandler {
        if (view == QuestionView.FORM) view = QuestionView.LIST else onBack()
    }

    when {
        activityNotFound -> MissingActivityView(onBack)
        activity == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = TeacherPrimaryPurple)
        }

        view == QuestionView.LIST -> QuestionListView(
            activity = activity!!,
            questions = questions,
            onBack = onBack,
            onCreate = {
                editingQuestion = null
                view = QuestionView.FORM
            },
            onEdit = {
                editingQuestion = it
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
private fun MissingActivityView(onBack: () -> Unit) {
    TeacherPanelContainer {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "La actividad no existe o fue eliminada.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                BottomBackButton(onClick = onBack, modifier = Modifier.width(150.dp))
            }
        }
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

    questionToDelete?.let { question ->
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = { Text("Eliminar pregunta", color = TeacherTitleColor) },
            text = { Text("¿Deseas eliminar esta pregunta? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(question)
                        questionToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) {
                    Text("Cancelar", color = TeacherPrimaryPurple)
                }
            }
        )
    }

    TeacherPanelContainer(modifier = Modifier.navigationBarsPadding()) {
        Spacer(modifier = Modifier.height(38.dp))
        SectionTitle(
            text = "Preguntas de la sesión",
            centered = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = activity.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TeacherSecondaryTextColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        PastelActionButton(
            text = "+ Nueva pregunta",
            onClick = onCreate,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(22.dp))

        if (questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Esta actividad todavía no tiene preguntas.\nAgrega la primera para comenzar.",
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
                items(questions, key = { it.id }) { question ->
                    TeacherQuestionCard(
                        question = question,
                        onEdit = { onEdit(question) },
                        onDelete = { questionToDelete = question }
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
private fun TeacherQuestionCard(
    question: QuestionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TeacherCardLavender),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${question.orderIndex}. ${question.questionText}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TeacherTextColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Respuesta de referencia:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TeacherPrimaryPurple
            )
            Text(
                text = question.expectedAnswer,
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
                    onClick = onDelete,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TeacherSmallPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.SemiBold)
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

    TeacherPanelContainer(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(34.dp))
        TeacherFormHeader(
            title = if (existing == null) "Nueva Pregunta" else "Editar Pregunta",
            onCancel = onCancel
        )
        Spacer(modifier = Modifier.height(26.dp))

        PastelTextField(
            value = orderIndex,
            onValueChange = { orderIndex = it; orderIndexError = false },
            label = "Orden de la pregunta *",
            isError = orderIndexError,
            supportingText = if (orderIndexError) "Debe ser un número mayor o igual a 1" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = questionText,
            onValueChange = { questionText = it; questionTextError = false },
            label = "Texto de la pregunta *",
            isError = questionTextError,
            supportingText = if (questionTextError) "El texto de la pregunta es obligatorio" else null,
            singleLine = false,
            minLines = 3
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = expectedAnswer,
            onValueChange = { expectedAnswer = it; expectedAnswerError = false },
            label = "Respuesta de referencia *",
            isError = expectedAnswerError,
            supportingText = if (expectedAnswerError) {
                "La respuesta de referencia es obligatoria"
            } else {
                "Respuesta óptima sugerida. Seven puede aceptar equivalentes."
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = keywords,
            onValueChange = { keywords = it; keywordsError = false },
            label = "Palabras o frases adicionales *",
            isError = keywordsError,
            supportingText = if (keywordsError) {
                "Ingresa al menos una palabra o frase adicional"
            } else {
                "Otras formas válidas de responder. Sepáralas con comas."
            },
            placeholder = "ej: guau, ladra, ladrido",
            singleLine = false,
            minLines = 2
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = maxTimeSeconds,
            onValueChange = { maxTimeSeconds = it; timeError = false },
            label = "Tiempo máximo (s) *",
            isError = timeError,
            supportingText = if (timeError) "Debe ser un número mayor que 0" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(12.dp))
        PastelTextField(
            value = maxAttempts,
            onValueChange = { maxAttempts = it; attemptsError = false },
            label = "Intentos máximos *",
            isError = attemptsError,
            supportingText = if (attemptsError) "Debe ser un número mayor que 0" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "Configuración adicional",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TeacherPrimaryPurple
        )
        Spacer(modifier = Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = mediationDropdownExpanded,
            onExpandedChange = { mediationDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = mediationKey.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Mediación local") },
                supportingText = { Text("Asocia frases lúdicas predefinidas del juguete.") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = mediationDropdownExpanded
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TeacherPrimaryPurple,
                    unfocusedBorderColor = TeacherPrimaryPurple,
                    focusedLabelColor = TeacherPrimaryPurple,
                    unfocusedLabelColor = TeacherTitleColor,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
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
        Spacer(modifier = Modifier.height(28.dp))

        PastelActionButton(
            text = if (existing == null) "Guardar pregunta" else "Editar pregunta",
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
                            mediationKey = if (mediationKey == LocalMediationKey.NONE) {
                                null
                            } else {
                                mediationKey.name
                            }
                        )
                    )
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}
