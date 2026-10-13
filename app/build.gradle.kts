plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.etkallem.english"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mashhadak.english.study4"
        minSdk = 24
        targetSdk = 34
        versionCode = 25
        versionName = "2.5.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
