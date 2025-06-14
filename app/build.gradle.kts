plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pruebart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.pruebart"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
}

dependencies {

    //cosas para lite rt
    implementation("com.google.ai.edge.litert:litert:1.2.0")
    implementation("com.google.ai.edge.litert:litert-support:1.2.0")

    //lo de media pipe
    implementation("com.google.mediapipe:tasks-vision:0.10.20")

    //cosas para la camara
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    //kotlin deeplearning era por si no funcionaba el tflite
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-tensorflow:0.5.2")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-onnx:0.5.2")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-visualization:0.5.2")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-dataset:0.5.2")

    implementation("androidx.activity:activity-compose:1.8.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}