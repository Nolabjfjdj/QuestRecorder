plugins {
    id("com.android.application")
}
android {
    namespace = "fr.nolabjfjdj.questrecorder"
    compileSdk = 35
    defaultConfig {
        applicationId = "fr.nolabjfjdj.questrecorder"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
dependencies {
    implementation("androidx.core:core:1.15.0")
}
