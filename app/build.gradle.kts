plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.dormradar.app"
    compileSdk = 35

    // 构建目录挪到纯 ASCII 路径（2026-10-07）：仓库路径含中文，Gradle 会把 classpath 规范化成
    // 中文路径写进测试 worker 的 argfile，而 JDK17 launcher 以 GBK 解析 → 单测全部
    // ClassNotFoundException（subst 盘符也挡不住规范化，实测复现，详见 docs/08 §7）。
    // build 目录搬出中文树后，单测 classpath 全 ASCII，问题根除。副作用：APK/测试报告
    // 输出位置变为 D:\dormradar-build（发版脚本用 -ApkPath 显式传参，不受影响）。
    layout.buildDirectory = File("D:/dormradar-build")

    defaultConfig {
        applicationId = "com.dormradar.app"
        minSdk = 26          // 覆盖近 8 年设备，BLE API 稳定
        targetSdk = 35
        versionCode = 15
        versionName = "0.6.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    // Compose（BOM 统一版本）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // 依赖注入
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // 数据层（阶段 3 起使用，先占位）
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // 协程
    implementation(libs.coroutines.android)

    // 测试
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
