// NekoJS —— 多 MC 版本 × 多加载器单仓。
//
// 版本图（stonecutter 0.9.7）：四个节点共用一棵源码树 src/，版本差异由守卫与
// replacements 表达，加载器差异由 `//? if neoforge` / `//? if fabric` 守卫表达。
//   1.21.1 / 26.1.2 / 26.2.0      —— NeoForge 节点，入口 build.gradle.kts
//   26.1.2-fabric / 26.2.0-fabric —— Fabric 节点，入口 fabric.gradle.kts。id 带后缀避免
//                                    与 NeoForge 节点撞名；两节点共享 raw loader root
//                                    src/fabric（fabric convention 显式挂载，票 31 迁入；
//                                    版本差异由 compat facade / versions/<node> override 承担）
//
// 不支持 Forge 1.20.1：它的 API 与共享树差了一个时代，守卫和 replacements 桥接不了，
// 移植等于维护第二套代码库。
//
// 引擎两模块（common / common-api-processor）是普通子项目，不参与版本化。对外契约类型住
// common 的 com.tkisor.nekojs.api.* 包下，"零 MC/Loader/Graal import"这条纪律由 guardLint
// 按包前缀强制（ADR-0007），不再由独立模块承载。

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
        version("26.2.0-fabric", "26.2.0").buildscript = "fabric.gradle.kts"
    }
}

include("common-api-processor", "common")
