pluginManagement {
    repositories {
        google()
        mavenCentral()
        // 阿里云镜像加速（保留）
        maven(url = "https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 阿里云镜像加速（保留）
        maven(url = "https://maven.aliyun.com/repository/public")
        // 商米官方Maven仓库（下载打印/扫码SDK必备）
        maven(url = "https://artifactory.sunmi.com/public/android/releases")
        // JitPack仓库（兼容第三方开源依赖）
        maven(url = "https://jitpack.io")
        // 本地libs目录，加载SUNMI_CUSTOMER_API_v1.0.62_release.aar等本地依赖
        flatDir {
            dirs(rootProject.projectDir.resolve("app/libs"))
        }
    }
}

rootProject.name = "LengKuBao"
include(":app")