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
        versionCode = 27
        versionName = "2.6.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
