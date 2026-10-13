plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.etkallem.english"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.etkallem.english.dialogues"
        minSdk = 24
        targetSdk = 34
        versionCode = 23
        versionName = "2.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
