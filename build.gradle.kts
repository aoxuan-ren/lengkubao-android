plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// 新版Gradle清理任务，兼容Kotlin DSL
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}