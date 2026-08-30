// buildSrc 有自己的 settings：插件管理与主构建互不共享，但导入根版本目录
// （gradle/libs.versions.toml），让 convention plugin 里的依赖坐标保持单一事实源。
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
    versionCatalogs {
        create("libs") { from(files(rootDir.parentFile.resolve("gradle/libs.versions.toml"))) }
    }
}

rootProject.name = "buildSrc"
