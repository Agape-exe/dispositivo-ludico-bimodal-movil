package com.taller.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun MainScreen(
    onNavigateToFaceDetection: () -> Unit,
    onNavigateToSpeechTest: () -> Unit,
    onNavigateToSemanticTest: () -> Unit
) {
    val context = LocalContext.current

    fun isCameraGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun isAudioGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(isCameraGranted()) }
    var audioGranted by remember { mutableStateOf(isAudioGranted()) }
    var showSettingsHint by remember { mutableStateOf(false) }

    // Re-checks permissions every time the screen resumes (e.g., returning from system settings)
    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = isCameraGranted()
                audioGranted = isAudioGranted()
                if (cameraGranted && audioGranted) showSettingsHint = false
                Log.d("MainScreen", "ON_RESUME - cámara: $cameraGranted, micrófono: $audioGranted")
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        cameraGranted = permissions[Manifest.permission.CAMERA] == true
        audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        showSettingsHint = !cameraGranted || !audioGranted
        Log.d("MainScreen", "Permisos - cámara: $cameraGranted, micrófono: $audioGranted")
    }

    val allGranted = cameraGranted && audioGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TallerApp - Prototipo móvil bimodal",
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PermissionStatusRow(label = "Cámara", granted = cameraGranted)
        PermissionStatusRow(label = "Micrófono", granted = audioGranted)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                showSettingsHint = false
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            },
            enabled = !allGranted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(text = if (allGranted) "Permisos concedidos" else "Solicitar permisos")
        }

        if (showSettingsHint) {
            Text(
                text = when {
                    !cameraGranted && !audioGranted ->
                        "Cámara y micrófono denegados. Ve a Configuración para concederlos."
                    !cameraGranted ->
                        "Permiso de cámara denegado. Ve a Configuración para concederlo."
                    else ->
                        "Permiso de micrófono denegado. Ve a Configuración para concederlo."
                },
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("Ir a Configuración")
            }
        }

        Button(
            onClick = onNavigateToFaceDetection,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Prueba de detección facial")
        }

        Button(
            onClick = onNavigateToSpeechTest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Prueba de voz STT")
        }

        Button(
            onClick = onNavigateToSemanticTest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Prueba de procesamiento semántico")
        }
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp)
        Text(
            text = if (granted) "Concedido" else "Pendiente",
            color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontSize = 14.sp
        )
    }
}
