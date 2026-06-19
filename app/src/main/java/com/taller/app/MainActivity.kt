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
import com.taller.app.ui.MainScreen
import com.taller.app.ui.Screen
import com.taller.app.ui.SemanticTestScreen
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
                var currentScreen by remember { mutableStateOf(Screen.MAIN) }
                var selectedActivityId by remember { mutableStateOf(0L) }

                when (currentScreen) {
                    Screen.MAIN -> MainScreen(
                        onNavigateToFaceDetection = { currentScreen = Screen.FACE_DETECTION },
                        onNavigateToSpeechTest = { currentScreen = Screen.SPEECH_TEST },
                        onNavigateToSemanticTest = { currentScreen = Screen.SEMANTIC_TEST },
                        onNavigateToTeacherActivities = { currentScreen = Screen.TEACHER_ACTIVITIES },
                        onNavigateToToyVoiceSettings = { currentScreen = Screen.TOY_VOICE_SETTINGS },
                        onNavigateToBimodal = {
                            selectedActivityId = 0L
                            currentScreen = Screen.BIMODAL_INTERACTION
                        },
                        onNavigateToClassicTimer = {
                            selectedActivityId = 0L
                            currentScreen = Screen.CLASSIC_TIMER_INTERACTION
                        }
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
                        onBack = { currentScreen = Screen.MAIN },
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
                        onBack = { currentScreen = Screen.MAIN }
                    )
                    Screen.CLASSIC_TIMER_INTERACTION -> ClassicTimerInteractionScreen(
                        activityId = selectedActivityId,
                        onBack = { currentScreen = Screen.MAIN }
                    )
                }
            }
        }
    }
}
