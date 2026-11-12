plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hmtracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hmtracker"
        minSdk = 28
        targetSdk = 34
        versionCode = 6
        versionName = "1.4.0"
    }

    // Kredensial signing TIDAK ditulis di sini. Dibaca dari environment variable
    // (di GitHub Actions diisi dari Secrets). Jika tidak ada, build release tetap jalan tanpa tanda tangan.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("HM_KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("HM_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("HM_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("HM_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("HM_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // CameraX: preview langsung di aplikasi, flash, zoom, dan resolusi HD
    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    testImplementation("junit:junit:4.13.2")
}
