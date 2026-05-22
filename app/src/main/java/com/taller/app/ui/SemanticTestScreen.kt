package com.taller.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taller.app.semantic.SemanticEvaluator
import com.taller.app.semantic.SemanticResult

private const val FIXED_QUESTION = "¿Qué animal dice miau?"
private const val EXPECTED_ANSWER = "gato"
private val KEYWORDS = listOf("gato", "gatito", "miau")

@Composable
fun SemanticTestScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val evaluator = remember { SemanticEvaluator() }
    var inputText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SemanticResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) {
            Text("← Volver")
        }

        Text(
            text = "Prueba de procesamiento semántico",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Ingresa una respuesta simulada para validar cómo el sistema la clasifica frente a una respuesta esperada y palabras clave.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Tarjeta de pregunta fija
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pregunta fija",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = FIXED_QUESTION,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Tarjeta de respuesta esperada y palabras clave
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Respuesta esperada",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"$EXPECTED_ANSWER\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Palabras clave",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = KEYWORDS.joinToString(separator = ", ") { "\"$it\"" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Campo de texto
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Respuesta simulada del niño") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2
        )

        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    result = evaluator.evaluate(
                        transcription = inputText,
                        expectedAnswer = EXPECTED_ANSWER,
                        keywords = KEYWORDS
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Evaluar respuesta")
            }
            OutlinedButton(
                onClick = {
                    inputText = ""
                    result = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Limpiar")
            }
        }

        // Tarjeta de resultado
        result?.let { semanticResult ->
            ResultCard(semanticResult)
        }
    }
}

@Composable
private fun ResultCard(result: SemanticResult) {
    val (label, explanation, containerColor, contentColor) = when (result) {
        SemanticResult.CORRECT -> ResultStyle(
            label = "Correcta",
            explanation = "La respuesta coincide con la respuesta esperada o contiene una palabra clave.",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        SemanticResult.INCORRECT -> ResultStyle(
            label = "Incorrecta",
            explanation = "No se encontró coincidencia suficiente con la respuesta esperada ni con las palabras clave.",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        SemanticResult.NO_RESPONSE -> ResultStyle(
            label = "Sin respuesta",
            explanation = "No se ingresó ninguna respuesta.",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SemanticResult.NOT_INTERPRETABLE -> ResultStyle(
            label = "No interpretable",
            explanation = "La respuesta no pudo ser clasificada con certeza.",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Resultado",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

private data class ResultStyle(
    val label: String,
    val explanation: String,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color
)
