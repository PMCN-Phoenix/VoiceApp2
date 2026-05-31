pluginManagement {
    repositories {
        // 官方仓库放在最前面，保证插件优先从官方源解析
        google()
        mavenCentral()
        gradlePluginPortal()

        // 阿里云镜像作为备用（官方仓库连接慢时可自动切换）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 官方仓库（必须优先级最高）
        google()
        mavenCentral()

        // 阿里云备用镜像
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VoiceApp"
include(":app")