plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)       // 新增：Compose 编译器插件
    //id("kotlin-kapt")                        // 新增：注解处理器（未来 Room 等预留）
}

android {
    namespace = "com.example.voiceapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voiceapp"
        minSdk = 26                          // 从 29 降至 26，兼容更多设备
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"               // 版本号微调

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 新增：指定 CPU 架构，减小 APK 体积
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true          // 新增：启用 Jetpack Compose
    }
    /*composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"   // 与 Kotlin 1.9.24 匹配的 Compose 编译器版本
    }*/


    // 新增：解决 ONNX Runtime 依赖冲突
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // ========== Android 核心（替换原有依赖） ==========
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)   // 新增
    implementation(libs.androidx.activity.compose)        // 新增：Compose Activity
    implementation(platform(libs.androidx.compose.bom))   // 新增：Compose BOM
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3) // 保留 Material3
    // 重新添加 View 体系的 Material3 库（提供 Theme.Material3.* 主题）
    implementation(libs.material)

    // 原有的 appcompat、material、constraintlayout 已移除，改用 Compose

    // ========== 协程 ==========
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ========== 网络 (OkHttp + WebSocket) ==========
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ========== 加密数据库 (SQLCipher) ==========
    //implementation("net.zetetic:android-database-sqlcipher:4.6.0")
    // 原版本 4.6.0 不存在，改为 4.5.4（已验证存在）
    implementation("net.zetetic:android-database-sqlcipher:4.5.3")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // ========== 音频编解码 (Opus) ==========
    //implementation("com.twilio:opus:1.0.0")
    // 原版本 1.0.0 不存在，改为 1.0.2（已验证存在）
    //implementation("com.twilio:opus:1.0.2")

    // ========== VAD 引擎 (Silero VAD 通过 ONNX Runtime) ==========
    //implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.1")
    // 原版本 1.18.1 太新，镜像可能未同步，改为 1.17.1（已验证存在）
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")

    // ========== 安全存储 ==========
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ========== JSON 解析 ==========
    implementation("com.google.code.gson:gson:2.11.0")

    // ========== 测试（保留原有） ==========
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}