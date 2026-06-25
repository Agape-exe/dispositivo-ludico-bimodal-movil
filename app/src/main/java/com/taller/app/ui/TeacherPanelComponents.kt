package com.taller.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val TeacherTitleColor = Color(0xFF9B7CC6)
internal val TeacherTextColor = Color(0xFF3F3A4A)
internal val TeacherSecondaryTextColor = Color(0xFF5B5266)
internal val TeacherPrimaryPurple = Color(0xFF6B4AA0)
internal val TeacherDarkPurple = Color(0xFF6E45A3)
internal val TeacherCardLavender = Color(0xFFD6D3EA)
internal val TeacherPastelPink = Color(0xFFF8D8F7)
internal val TeacherSmallPurple = Color(0xFF8D75B8)

@Composable
internal fun TeacherPanelContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 32.dp),
        content = content
    )
}

@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Text(
        text = text,
        color = TeacherTitleColor,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = modifier
    )
}

@Composable
internal fun TeacherFormHeader(
    title: String,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(
            text = title,
            modifier = Modifier.weight(1f)
        )
        PastelActionButton(
            text = "Cancelar",
            onClick = onCancel,
            modifier = Modifier.heightIn(min = 44.dp),
            contentPaddingHorizontal = 18
        )
    }
}

@Composable
internal fun PastelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { message -> { Text(message) } },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TeacherPrimaryPurple,
            unfocusedBorderColor = TeacherPrimaryPurple,
            focusedLabelColor = TeacherPrimaryPurple,
            unfocusedLabelColor = TeacherTitleColor,
            cursorColor = TeacherPrimaryPurple,
            focusedTextColor = TeacherTextColor,
            unfocusedTextColor = TeacherTextColor,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun PastelActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingHorizontal: Int = 28
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TeacherPastelPink,
            contentColor = TeacherPrimaryPurple
        ),
        contentPadding = PaddingValues(
            horizontal = contentPaddingHorizontal.dp,
            vertical = 10.dp
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun BottomBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TeacherDarkPurple,
            contentColor = Color.White
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Text(
            text = "Volver",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
