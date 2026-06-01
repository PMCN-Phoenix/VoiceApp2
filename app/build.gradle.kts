plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    //id("kotlin-kapt")
}

// 导入 java.util.Properties
import java.util.Properties

// 读取 local.properties 中的百度密钥
val localProperties = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localProperties.load(localFile.inputStream())
}
val baiduAppId: String = localProperties.getProperty("BAIDU_APP_ID") ?: ""
val baiduApiKey: String = localProperties.getProperty("BAIDU_API_KEY") ?: ""
val baiduSecretKey: String = localProperties.getProperty("BAIDU_SECRET_KEY") ?: ""

android {
    namespace = "com.example.voiceapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voiceapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        buildConfigField("String", "BAIDU_APP_ID", "\"$baiduAppId\"")
        buildConfigField("String", "BAIDU_API_KEY", "\"$baiduApiKey\"")
        buildConfigField("String", "BAIDU_SECRET_KEY", "\"$baiduSecretKey\"")
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
        compose = true
        buildConfig = true   // 新增：启用 BuildConfig 生成
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("net.zetetic:android-database-sqlcipher:4.5.3")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}