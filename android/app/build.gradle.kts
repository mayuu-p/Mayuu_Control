plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
dependencies { implementation("androidx.core:core-ktx:1.15.0") }
android {
    namespace = "com.mayuu.phantomrecover"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mayuu.phantomrecover"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
}