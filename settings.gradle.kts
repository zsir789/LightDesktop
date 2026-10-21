pluginManagement {
    repositories {
        // 官方 Google Maven 在部分网络环境不可达，优先走阿里云镜像；保留官方源兜底。
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "LightDesk"

// :app 为壳工程：负责应用装配、导航、主题与生命周期
include(":app")

// 核心能力模块（与业务无关，可被任意功能模块复用）
include(":core:common")
include(":core:model")
include(":core:data")
include(":core:designsystem")

// 功能模块（彼此解耦，仅依赖 core 层）
// v1.1.0：memo / todo 合并为 notes，floatingball 下线，新增 home
include(":feature:home")
include(":feature:notes")
include(":feature:timetable")
include(":feature:weather")
include(":feature:stats")
