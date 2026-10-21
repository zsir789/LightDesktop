plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lightdesk.core.designsystem"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":core:common"))
    // v1.2.0：主题配色枚举 ThemePalette 定义在 core:model，设计系统直接暴露给各 feature
    api(project(":core:model"))

    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.material.icons.core)
    api(libs.compose.material.icons.extended)
    api(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
}
