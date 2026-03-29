import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.github.triplet.play")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.ilanp13.shabbatalertdismisser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ilanp13.shabbatalertdismisser"
        minSdk = 26
        targetSdk = 35
        versionCode = 33
        versionName = "2.3.0"
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
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}

play {
    serviceAccountCredentials.set(file("../play-publisher.json"))
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
}
