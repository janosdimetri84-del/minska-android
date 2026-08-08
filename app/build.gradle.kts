plugins {
    id("com.android.application")
}

android {
    namespace = "se.minska.test"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.minska.test"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
}
