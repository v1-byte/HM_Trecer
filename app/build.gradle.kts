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
        versionCode = 4
        versionName = "1.2.1"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/hmtracker-release.jks")
            storePassword = "hmtracker2026"
            keyAlias = "hmtracker"
            keyPassword = "hmtracker2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
}
