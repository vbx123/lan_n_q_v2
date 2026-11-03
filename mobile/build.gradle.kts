plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nasim.launcher.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nasim.launcher.mobile"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":common"))
    // ماسح QR جاهز من خدمات جوجل (بدون صلاحية كاميرا)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
