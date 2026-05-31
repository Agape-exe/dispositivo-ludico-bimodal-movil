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
            "\"${localSecret("ELEVENLABS_API_KEY")}\""
        )
        buildConfigField(
            "String",
            "ELEVENLABS_VOICE_ID",
            "\"${localSecret("ELEVENLABS_VOICE_ID")}\""
        )
        buildConfigField(
            "String",
            "AZURE_SPEECH_KEY",
            "\"${localSecret("AZURE_SPEECH_KEY")}\""
        )
        buildConfigField(
            "String",
            "AZURE_SPEECH_REGION",
            "\"${localSecret("AZURE_SPEECH_REGION")}\""
        )
        buildConfigField(
            "String",
            "AZURE_SPEECH_VOICE",
            "\"${localSecret("AZURE_SPEECH_VOICE")}\""
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