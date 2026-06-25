package com.taller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.taller.app.ui.BimodalInteractionScreen
import com.taller.app.ui.ClassicTimerInteractionScreen
import com.taller.app.ui.FaceDetectionScreen
import com.taller.app.ui.HomeScreen
import com.taller.app.ui.MetricsExportScreen
import com.taller.app.ui.Screen
import com.taller.app.ui.SemanticTestScreen
import com.taller.app.ui.SettingsScreen
import com.taller.app.ui.SpeechTestScreen
import com.taller.app.ui.TeacherActivitiesScreen
import com.taller.app.ui.TeacherQuestionsScreen
import com.taller.app.ui.ToyVoiceSettingsScreen
import com.taller.app.ui.theme.TallerAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TallerAppTheme {
                var currentScreen by remember { mutableStateOf(Screen.HOME) }
                var selectedActivityId by remember { mutableStateOf(0L) }

                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        onNavigateToTeacherPanel = { currentScreen = Screen.TEACHER_ACTIVITIES },
                        onNavigateToRecords = { currentScreen = Screen.METRICS_EXPORT },
                        onNavigateToSmartMode = {
                            selectedActivityId = 0L
                            currentScreen = Screen.BIMODAL_INTERACTION
                        },
                        onNavigateToTimerMode = {
                            selectedActivityId = 0L
                            currentScreen = Screen.CLASSIC_TIMER_INTERACTION
                        },
                        onNavigateToSettings = { currentScreen = Screen.MAIN }
                    )
                    Screen.MAIN -> SettingsScreen(
                        onBack = { currentScreen = Screen.HOME },
                        onNavigateToFaceDetection = { currentScreen = Screen.FACE_DETECTION },
                        onNavigateToSpeechTest = { currentScreen = Screen.SPEECH_TEST },
                        onNavigateToSemanticTest = { currentScreen = Screen.SEMANTIC_TEST },
                        onNavigateToToyVoiceSettings = { currentScreen = Screen.TOY_VOICE_SETTINGS }
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
                        onBack = { currentScreen = Screen.HOME },
                        onNavigateToQuestions = { activityId ->
                            selectedActivityId = activityId
                            currentScreen = Screen.TEACHER_QUESTIONS
                        }
                    )
                    Screen.TEACHER_QUESTIONS -> TeacherQuestionsScreen(
                        activityId = selectedActivityId,
                        onBack = { currentScreen = Screen.TEACHER_ACTIVITIES }
                    )
                    Screen.TOY_VOICE_SETTINGS -> ToyVoiceSettingsScreen(
                        onBack = { currentScreen = Screen.MAIN }
                    )
                    Screen.BIMODAL_INTERACTION -> BimodalInteractionScreen(
                        activityId = selectedActivityId,
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.CLASSIC_TIMER_INTERACTION -> ClassicTimerInteractionScreen(
                        activityId = selectedActivityId,
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.METRICS_EXPORT -> MetricsExportScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                }
            }
        }
    }
}
