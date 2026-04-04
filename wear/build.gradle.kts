plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ilanp13.shabbatalertdismisser.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ilanp13.shabbatalertdismisser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha29")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
