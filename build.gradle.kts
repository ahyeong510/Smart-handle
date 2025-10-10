plugins {
    id("com.android.application") version "8.13.0"
    kotlin("android") version "1.9.24"
}

android {
    namespace = "com.example.smart_handle"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smart_handle"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // ★ 여기 추가: Java 컴파일 타깃 17로 통일
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ★ 여기 추가: Kotlin 타깃 17로 통일
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

// ★ 여기 추가: Kotlin JVM Toolchain 17 고정
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.01"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.0")
}
