plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.pingwei.lengkubao"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pingwei.lengkubao"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room 配置
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
        buildConfigField("boolean", "DEBUG_MODE", "true")

        // 新增：针对Android 11+，需要添加打印机服务查询
        manifestPlaceholders.put("printerPackage", "woyou.aidlservice.jiuiv5")

        // 新增：商米扫描配置（如果需要）
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    // 新增：sourceSets配置，确保AIDL文件正确识别
    sourceSets {
        getByName("main") {
            aidl.srcDirs("src/main/aidl")
            // 如果还有其他AIDL目录，可以添加
            // aidl.srcDirs("src/main/aidl", "src/debug/aidl")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // 新增：发布版配置
            buildConfigField("boolean", "ENABLE_SCANNER_LOG", "false")
        }
        debug {
            isDebuggable = true
            buildConfigField("boolean", "ENABLE_DB_TEST", "true")
            // 新增：调试版扫描配置
            buildConfigField("boolean", "ENABLE_SCANNER_LOG", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xjvm-default=all",  // 确保接口默认方法支持
            "-Xskip-prerelease-check",
            "-Xallow-unstable-dependencies"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
        aidl = true  // 新增：启用AIDL支持
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/*.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                // 新增：商米SDK可能需要的排除项
                "**/kotlin/**",
                "**/*.kotlin_builtins",
                "**/*.kotlin_module"
            )
        }
    }
}

dependencies {


    // 基础依赖
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // ZXing二维码生成
    implementation("com.google.zxing:core:3.5.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Kotlin核心依赖
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlin.reflect)

    // 协程
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)

    // Compose
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.foundation:foundation:1.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.12.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.material:material:1.11.0")

    // Room数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // 商米SDK
    implementation(files("libs/SUNMI_CUSTOMER_API_v1.0.62_release.aar"))
    implementation("com.sunmi:printerx:1.0.17")
    implementation("com.sunmi:printerlibrary:1.0.18")

    // 新增：商米扫描SDK依赖（如果需要）
    // implementation("com.sunmi:ds1scanner:1.0.0") // 根据实际SDK名称调整

    // 测试依赖
    testImplementation(libs.junit)
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // 其他依赖
    implementation(libs.androidx.ui.test)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation.core.lint)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.camera.camera2.pipe)
    implementation(libs.generativeai)
    implementation(libs.foundation)
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}