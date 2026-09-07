// Explicit: inside a Kotlin build script `java` is Gradle's JavaPluginExtension, so the
// fully-qualified `java.net.URI` does not resolve.
import java.net.URI

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
        // Architecture selection lives in the `splits` block below. AGP rejects the build
        // if ndk.abiFilters is set as well.
    }

    // Sideload signing: CI writes the keystore to this path from a repo secret. Without it
    // (i.e. any local build) we fall back to the debug key so nothing breaks.
    val sideloadStore = rootProject.file("sideload.keystore")
    // Env var in CI; a Gradle property locally, because Android Studio launched from a
    // desktop icon does not inherit the shell environment.
    val sideloadPassword = System.getenv("SIDELOAD_KEYSTORE_PASSWORD")
        ?: providers.gradleProperty("sideloadKeystorePassword").orNull
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
    // One APK per CPU architecture instead of one fat APK carrying both. ONNX Runtime and
    // ML Kit each ship a large .so per ABI, so a single-architecture APK drops roughly a
    // quarter of the download — which matters when the only way to install is a phone
    // pulling it over mobile data.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
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

    // On-device sentence embeddings (MQ-25) — runs the MiniLM ONNX graph locally
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

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

// ---------------------------------------------------------------------------
// MQ-25: fetch the MiniLM sentence-embedding model into assets at build time.
//
// The model is ~23 MB of binary, so it is downloaded rather than committed — this repo is
// public and a binary that size does not belong in its history. The download is cached
// (skipped when the files already exist) and is deliberately NON-FATAL: a build with no
// network still produces a working app, it just falls back to hashing embeddings. That is
// why the app checks for these assets at runtime instead of assuming them.
// ---------------------------------------------------------------------------
val embeddingAssets = mapOf(
    "minilm.onnx" to "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx",
    "minilm_vocab.txt" to "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/vocab.txt",
)

val fetchEmbeddingModel by tasks.registering {
    description = "Downloads the MiniLM ONNX model and vocab into src/main/assets."
    val assetsDir = file("src/main/assets")
    outputs.dir(assetsDir)
    doLast {
        assetsDir.mkdirs()
        embeddingAssets.forEach { (name, url) ->
            val target = File(assetsDir, name)
            if (target.exists() && target.length() > 0) {
                logger.lifecycle("MQ-25: $name already present (${target.length() / 1024} KB)")
                return@forEach
            }
            try {
                logger.lifecycle("MQ-25: downloading $name …")
                val tmp = File(assetsDir, "$name.part")
                URI(url).toURL().openStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (tmp.length() == 0L) error("empty download")
                tmp.renameTo(target)
                logger.lifecycle("MQ-25: $name ready (${target.length() / 1024} KB)")
            } catch (e: Exception) {
                File(assetsDir, "$name.part").delete()
                logger.warn("MQ-25: could not fetch $name (${e.message}). Build continues; the app will use hashing embeddings.")
            }
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchEmbeddingModel) }
