plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.auebnavigator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.auebnavigator"
        minSdk = 29
        targetSdk = 34
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
}

dependencies {
    // CameraX core library using the camera2 implementation
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // Google ML Kit for Object Detection
    implementation("com.google.mlkit:object-detection:17.0.0")

    // Google ML Kit for Text Recognition (OCR - Latin)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // Google ML Kit Translation API
    implementation("com.google.mlkit:translate:17.0.1")

    // AndroidX & Material Libraries
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Testing Libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Η βιβλιοθήκη για να τεστάρουμε τα Intents (αλλαγές οθόνης)
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
}