package com.taller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.taller.app.ui.FaceDetectionScreen
import com.taller.app.ui.MainScreen
import com.taller.app.ui.Screen
import com.taller.app.ui.SemanticTestScreen
import com.taller.app.ui.SpeechTestScreen
import com.taller.app.ui.TeacherActivitiesScreen
import com.taller.app.ui.theme.TallerAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TallerAppTheme {
                var currentScreen by remember { mutableStateOf(Screen.MAIN) }

                when (currentScreen) {
                    Screen.MAIN -> MainScreen(
                        onNavigateToFaceDetection = { currentScreen = Screen.FACE_DETECTION },
                        onNavigateToSpeechTest = { currentScreen = Screen.SPEECH_TEST },
                        onNavigateToSemanticTest = { currentScreen = Screen.SEMANTIC_TEST },
                        onNavigateToTeacherActivities = { currentScreen = Screen.TEACHER_ACTIVITIES }
                    )
                    Screen.FACE_DETECTION -> FaceDetectionScreen(
                        onBack = { currentScreen = Screen.MAIN }
                    )
                    Screen.SPEECH_TEST -> SpeechTestScreen(
                        onBack = { currentScreen = Screen.MAIN }
                    )
                    Screen.SEMANTIC_TEST -> SemanticTestScreen(
                        onBack = { currentScreen = Screen.MAIN }
                    )
                    Screen.TEACHER_ACTIVITIES -> TeacherActivitiesScreen(
                        onBack = { currentScreen = Screen.MAIN }
                    )
                }
            }
        }
    }
}
