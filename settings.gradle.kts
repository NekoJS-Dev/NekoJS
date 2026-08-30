// NekoJS —— 多 MC 版本 × 多加载器单仓。
//
// 版本图（stonecutter 0.9.7）：四个节点共用一棵源码树 src/，版本差异由守卫与
// replacements 表达，加载器差异由 `//? if neoforge` / `//? if fabric` 守卫表达。
//   1.21.1 / 26.1.2 / 26.2.0      —— NeoForge 节点，入口 build.gradle.kts
//   26.1.2-fabric                 —— Fabric 节点，入口 fabric.gradle.kts。id 带后缀避免
//                                    与 NeoForge 的 26.1.2 撞名，`to` 右侧是守卫解析用的
//                                    干净逻辑版本
//
// 不支持 Forge 1.20.1：它的 API 与共享树差了一个时代，守卫和 replacements 桥接不了，
// 移植等于维护第二套代码库。
//
// 引擎三模块（common / common-api / common-api-processor）是普通子项目，不参与版本化。

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
    }
}

include("common-api", "common-api-processor", "common")
