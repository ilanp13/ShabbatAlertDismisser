import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.github.triplet.play")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.ilanp13.shabbatalertdismisser.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ilanp13.shabbatalertdismisser"
        minSdk = 26
        targetSdk = 35
        versionCode = 69
        versionName = "2.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", ""))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
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
}

play {
    serviceAccountCredentials.set(file("../play-publisher.json"))
    track.set("wear:internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
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
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
