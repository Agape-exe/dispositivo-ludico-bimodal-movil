package com.taller.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onNavigateToTeacherPanel: () -> Unit,
    onNavigateToRecords: () -> Unit,
    onNavigateToSmartMode: () -> Unit,
    onNavigateToTimerMode: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFFEDE8F5))
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF9B7CC6),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Bienvenida,",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9B7CC6),
            textAlign = TextAlign.Center
        )

        Text(
            text = "¿qué haremos hoy?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF3F3A4A),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HomeMenuCard(
                text = "Panel Docente",
                icon = Icons.Rounded.School,
                backgroundColor = Color(0xFFB8B3DF),
                textColor = Color(0xFF6B519E),
                iconColor = Color(0xFF7B65B6),
                onClick = onNavigateToTeacherPanel,
                modifier = Modifier.weight(1f)
            )
            HomeMenuCard(
                text = "Registros",
                icon = Icons.Rounded.Assessment,
                backgroundColor = Color(0xFFF5D6F4),
                textColor = Color(0xFFF062A8),
                iconColor = Color(0xFFF062A8),
                onClick = onNavigateToRecords,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HomeMenuCard(
                text = "Modo Inteligente",
                icon = Icons.Rounded.SmartToy,
                backgroundColor = Color(0xFF96CEC6),
                textColor = Color(0xFF6B519E),
                iconColor = Color(0xFF1A9CA0),
                onClick = onNavigateToSmartMode,
                modifier = Modifier.weight(1f)
            )
            HomeMenuCard(
                text = "Temporizador Fijo",
                icon = Icons.Rounded.Timer,
                backgroundColor = Color(0xFFFFF0A6),
                textColor = Color(0xFF4A3D38),
                iconColor = Color(0xFFF2A900),
                onClick = onNavigateToTimerMode,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            onClick = onNavigateToSettings,
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFD6D3EA),
            modifier = Modifier
                .widthIn(min = 160.dp, max = 220.dp)
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = Color(0xFF1E1E1E),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Configurar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B519E)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun HomeMenuCard(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    textColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
        }
    }
}
