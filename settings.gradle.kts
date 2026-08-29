// NekoJS —— 多版本 × 多加载器单仓（独立主仓形态）。
//
// 版本图（stonecutter 0.9.7）：
//   根分支（共享 src/ 版本树，loader 常量区分加载器）：
//     1.21.1 / 26.1.2 / 26.2.0      —— NeoForge 节点，根 build.gradle.kts
//     26.1.2-fabric                 —— Fabric 节点，id 带后缀避免撞名，`to` 右侧是
//                                       干净逻辑版本（守卫按它解析），fabric.gradle.kts
//   forge 分支（独立树）：
//     1.20.1                        —— Forge 47 骨架，forge/build.gradle.kts。
//       1.20.1 的 API 距离共享树太远（B4 端口未开始），共享树编不了，故留独立分支；
//       端口完成后再并入根分支（与 fabric 同法）。
//
// 引擎三模块（common/common-api/common-api-processor）为普通子项目，不参与版本化。

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "NekoJS"

stonecutter {
    kotlinController = true
    create(rootProject) {
        versions("1.21.1", "26.1.2", "26.2.0")
        version("26.1.2-fabric", "26.1.2").buildscript = "fabric.gradle.kts"
        branch("forge") {
            versions("1.20.1")
        }
    }
}

include("common-api", "common-api-processor", "common")
