plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.havenapp.main"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.havenapp.main"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.0.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePath = System.getenv("HAVEN_KEYSTORE_PATH")
    val keystorePassword = System.getenv("HAVEN_KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("HAVEN_KEY_ALIAS")
    val keyPassword = System.getenv("HAVEN_KEY_PASSWORD")
    val hasKeystore = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()
        && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()

    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // TFLite-Modell-Dateien dürfen nicht komprimiert werden
    androidResources {
        noCompress += "tflite"
    }
}

// Object detection model setup (MediaPipe Tasks Vision):
// Das EfficientDet Lite 0 Modell muss manuell nach src/main/assets/ kopiert werden.
// Das gleiche efficientdet_lite0.tflite Modell funktioniert mit MediaPipe Tasks Vision.
// Download (~4 MB):
//   curl -L -o app/src/main/assets/efficientdet_lite0.tflite \
//     "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite"
// Ohne das Modell läuft die App mit reiner Bewegungserkennung (graceful degradation).

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.coroutines.android)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.process)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // MediaPipe Tasks Vision (on-device object detection, Android 16 / 16 KB compliant)
    implementation(libs.mediapipe.tasks.vision)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.video)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // OkHttp
    implementation(libs.okhttp)

    // DataStore
    implementation(libs.datastore.preferences)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}
