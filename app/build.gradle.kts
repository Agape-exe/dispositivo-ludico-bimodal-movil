import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Credenciales del proveedor neural. Se leen de una fuente local NO versionada
// (local.properties o variable de entorno) y se inyectan en BuildConfig.
// Nunca se hardcodean valores reales ni se suben al repositorio.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localSecret(name: String): String =
    (localProperties.getProperty(name) ?: System.getenv(name) ?: "").trim()

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.taller.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.taller.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "ELEVENLABS_API_KEY",
            buildConfigString(localSecret("ELEVENLABS_API_KEY"))
        )
        buildConfigField(
            "String",
            "ELEVENLABS_VOICE_ID",
            buildConfigString(localSecret("ELEVENLABS_VOICE_ID"))
        )
        buildConfigField(
            "String",
            "AZURE_SPEECH_KEY",
            buildConfigString(localSecret("AZURE_SPEECH_KEY"))
        )
        buildConfigField(
            "String",
            "AZURE_SPEECH_REGION",
            buildConfigString(localSecret("AZURE_SPEECH_REGION"))
        )
        buildConfigField(
            "String",
            "AZURE_SPEECH_VOICE",
            buildConfigString(localSecret("AZURE_SPEECH_VOICE"))
        )
        buildConfigField(
            "String",
            "OPENAI_API_KEY",
            buildConfigString(localSecret("OPENAI_API_KEY"))
        )
        buildConfigField(
            "String",
            "OPENAI_TTS_MODEL",
            buildConfigString(localSecret("OPENAI_TTS_MODEL").ifBlank { "gpt-4o-mini-tts" })
        )
        buildConfigField(
            "String",
            "OPENAI_TTS_VOICE",
            buildConfigString(localSecret("OPENAI_TTS_VOICE").ifBlank { "marin" })
        )
        buildConfigField(
            "String",
            "OPENAI_TTS_INSTRUCTIONS",
            buildConfigString(localSecret("OPENAI_TTS_INSTRUCTIONS").ifBlank { "Habla en espanol latino con una voz calida, clara, amable y expresiva, como un companero de juego para ninos. Manten un ritmo natural, no demasiado rapido, con tono curioso y alegre. Evita sonar como profesor serio o como robot." })
        )

        // Mediacion ludica generativa (opcional). Desactivada por defecto: la app
        // funciona sin credenciales y usa el banco local de frases. Si se quiere
        // activar, definir en local.properties (no versionado):
        // GENERATIVE_MEDIATION_ENABLED=true, GENERATIVE_MEDIATION_API_KEY,
        // GENERATIVE_MEDIATION_ENDPOINT y, opcionalmente, GENERATIVE_MEDIATION_MODEL.
        buildConfigField(
            "boolean",
            "GENERATIVE_MEDIATION_ENABLED",
            localSecret("GENERATIVE_MEDIATION_ENABLED").ifBlank { "false" }
        )
        buildConfigField(
            "String",
            "GENERATIVE_MEDIATION_API_KEY",
            buildConfigString(localSecret("GENERATIVE_MEDIATION_API_KEY"))
        )
        buildConfigField(
            "String",
            "GENERATIVE_MEDIATION_ENDPOINT",
            buildConfigString(localSecret("GENERATIVE_MEDIATION_ENDPOINT"))
        )
        buildConfigField(
            "String",
            "GENERATIVE_MEDIATION_MODEL",
            buildConfigString(localSecret("GENERATIVE_MEDIATION_MODEL").ifBlank { "gpt-4o-mini" })
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // CameraX — base para futura vista de cámara con análisis de imagen
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // ML Kit Face Detection — detección de rostros on-device
    implementation(libs.mlkit.face.detection)
    // Room — persistencia local
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // DataStore — preferencias de configuración local
    implementation(libs.androidx.datastore.preferences)
    // OkHttp — cliente HTTP para el proveedor de voz neural
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
