plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lightdesk.core.data"
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

    testOptions {
        unitTests {
            // 仓储默认使用 LogcatLogger；JVM 单测里 android.util.Log 未实现，
            // 返回默认值可避免 “Method d in android.util.Log not mocked”。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
