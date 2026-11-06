plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
dependencies { implementation("androidx.core:core-ktx:1.15.0") }
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.mayuu.phantomrecover"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mayuu.phantomrecover"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }
}