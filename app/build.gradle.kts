import java.net.URL
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.strss.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.strss.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = false
            // signed with the debug key so the APK installs directly
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
}

// Downloads the Urbanist font (SIL OFL) once if it is not already in res/font
val downloadFont by tasks.registering {
    val out = file("src/main/res/font/urbanist.ttf")
    outputs.file(out)
    doLast {
        if (!out.exists()) {
            out.parentFile.mkdirs()
            URL("https://github.com/google/fonts/raw/main/ofl/urbanist/Urbanist%5Bwght%5D.ttf")
                .openStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadFont) }
