plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.etkallem.english"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mashhadak.english.video"
        minSdk = 24
        targetSdk = 34
        versionCode = 24
        versionName = "2.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
