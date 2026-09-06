plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mindquest.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mindquest.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // Point at your MindQuest API deployment; 10.0.2.2 reaches the host from the emulator.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
    }

    // Sideload signing: CI writes the keystore to this path from a repo secret. Without it
    // (i.e. any local build) we fall back to the debug key so nothing breaks.
    val sideloadStore = rootProject.file("sideload.keystore")
    val sideloadPassword = System.getenv("SIDELOAD_KEYSTORE_PASSWORD")
    val hasSideloadKey = sideloadStore.exists() && !sideloadPassword.isNullOrBlank()

    signingConfigs {
        if (hasSideloadKey) {
            create("sideload") {
                storeFile = sideloadStore
                storePassword = sideloadPassword
                keyAlias = System.getenv("SIDELOAD_KEY_ALIAS") ?: "mindquest"
                keyPassword = System.getenv("SIDELOAD_KEY_PASSWORD") ?: sideloadPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        // APK built by GitHub Actions and installed by hand. Installs alongside the
        // Android-Studio build (`.ci` suffix) so switching over never risks existing data.
        create("sideload") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".ci"
            versionNameSuffix = "-ci"
            isMinifyEnabled = false
            signingConfig = if (hasSideloadKey) {
                signingConfigs.getByName("sideload")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Optional fingerprint / face unlock in front of the PIN (MQ-24)
    implementation("androidx.biometric:biometric:1.1.0")

    // Room — local on-device database (offline source of truth)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // On-device OCR (offline, bundled Latin model) — Phase 3 documents
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Reminder notifications for inbox notes (survives reboot)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.13.1")

    // Sarvam AI (Phase 4) + JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
