pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "vibex-companion"
include(":companion-core")

val androidHome = System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: java.io.File(rootDir, "local.properties").takeIf { it.isFile }?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("sdk.dir=")?.trim()
    ?: java.io.File(System.getProperty("user.home"), "Library/Android/sdk").takeIf { it.isDirectory }?.absolutePath
if (!androidHome.isNullOrBlank()) {
    include(":app")
}
