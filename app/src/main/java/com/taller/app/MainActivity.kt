package com.taller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.taller.app.ui.BimodalInteractionScreen
import com.taller.app.ui.ClassicTimerInteractionScreen
import com.taller.app.ui.ClassicSevenFaceScreen
import com.taller.app.ui.FaceDetectionScreen
import com.taller.app.ui.HomeScreen
import com.taller.app.ui.IntelligentSevenFaceScreen
import com.taller.app.ui.MetricsExportScreen
import com.taller.app.ui.Screen
import com.taller.app.ui.SemanticTestScreen
import com.taller.app.ui.SessionScriptScreen
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
                var currentScreenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
                var selectedActivityId by rememberSaveable { mutableStateOf(0L) }
                val currentScreen = runCatching { Screen.valueOf(currentScreenName) }
                    .getOrDefault(Screen.HOME)

                fun navigateTo(screen: Screen) {
                    currentScreenName = screen.name
                }

                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        onNavigateToTeacherPanel = { navigateTo(Screen.TEACHER_ACTIVITIES) },
                        onNavigateToRecords = { navigateTo(Screen.METRICS_EXPORT) },
                        onNavigateToSmartMode = {
                            selectedActivityId = 0L
                            navigateTo(Screen.BIMODAL_INTERACTION)
                        },
                        onNavigateToTimerMode = {
                            selectedActivityId = 0L
                            navigateTo(Screen.CLASSIC_TIMER_INTERACTION)
                        },
                        onNavigateToSettings = { navigateTo(Screen.MAIN) }
                    )
                    Screen.MAIN -> SettingsScreen(
                        onBack = { navigateTo(Screen.HOME) },
                        onNavigateToFaceDetection = { navigateTo(Screen.FACE_DETECTION) },
                        onNavigateToSpeechTest = { navigateTo(Screen.SPEECH_TEST) },
                        onNavigateToSemanticTest = { navigateTo(Screen.SEMANTIC_TEST) },
                        onNavigateToToyVoiceSettings = { navigateTo(Screen.TOY_VOICE_SETTINGS) }
                    )
                    Screen.FACE_DETECTION -> FaceDetectionScreen(
                        onBack = { navigateTo(Screen.MAIN) }
                    )
                    Screen.SPEECH_TEST -> SpeechTestScreen(
                        onBack = { navigateTo(Screen.MAIN) }
                    )
                    Screen.SEMANTIC_TEST -> SemanticTestScreen(
                        onBack = { navigateTo(Screen.MAIN) }
                    )
                    Screen.TEACHER_ACTIVITIES -> TeacherActivitiesScreen(
                        onBack = { navigateTo(Screen.HOME) },
                        onNavigateToQuestions = { activityId ->
                            selectedActivityId = activityId
                            navigateTo(Screen.TEACHER_QUESTIONS)
                        },
                        onNavigateToScript = { activityId ->
                            selectedActivityId = activityId
                            navigateTo(Screen.SESSION_SCRIPT)
                        }
                    )
                    Screen.TEACHER_QUESTIONS -> TeacherQuestionsScreen(
                        activityId = selectedActivityId,
                        onBack = { navigateTo(Screen.TEACHER_ACTIVITIES) }
                    )
                    Screen.SESSION_SCRIPT -> SessionScriptScreen(
                        activityId = selectedActivityId,
                        onBack = { navigateTo(Screen.TEACHER_ACTIVITIES) }
                    )
                    Screen.TOY_VOICE_SETTINGS -> ToyVoiceSettingsScreen(
                        onBack = { navigateTo(Screen.MAIN) }
                    )
                    Screen.BIMODAL_INTERACTION -> BimodalInteractionScreen(
                        onStartActivity = { activityId ->
                            selectedActivityId = activityId
                            navigateTo(Screen.INTELLIGENT_SEVEN_FACE)
                        },
                        onBack = { navigateTo(Screen.HOME) }
                    )
                    Screen.INTELLIGENT_SEVEN_FACE -> IntelligentSevenFaceScreen(
                        activityId = selectedActivityId,
                        onChangeActivity = {
                            selectedActivityId = 0L
                            navigateTo(Screen.BIMODAL_INTERACTION)
                        },
                        onBack = { navigateTo(Screen.HOME) }
                    )
                    Screen.CLASSIC_TIMER_INTERACTION -> ClassicTimerInteractionScreen(
                        onStartActivity = { activityId ->
                            selectedActivityId = activityId
                            navigateTo(Screen.CLASSIC_SEVEN_FACE)
                        },
                        onBack = { navigateTo(Screen.HOME) }
                    )
                    Screen.CLASSIC_SEVEN_FACE -> ClassicSevenFaceScreen(
                        activityId = selectedActivityId,
                        onChangeActivity = {
                            selectedActivityId = 0L
                            navigateTo(Screen.CLASSIC_TIMER_INTERACTION)
                        },
                        onBack = { navigateTo(Screen.HOME) }
                    )
                    Screen.METRICS_EXPORT -> MetricsExportScreen(
                        onBack = { navigateTo(Screen.HOME) }
                    )
                }
            }
        }
    }
}
