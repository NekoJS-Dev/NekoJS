// 节点 convention plugin 的编译环境:kotlin-dsl + 各平台插件上 classpath。
// 插件在 buildSrc 依赖里(无版本声明)后,即可被 precompiled script 的 plugins 块
// 与节点入口脚本直接引用——版本事实源收拢在本文件与根 gradle/libs.versions.toml。
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.kikugie.dev/releases")
}

dependencies {
    // 把根版本目录生成的访问器类（LibrariesForLibs）放进编译类路径——
    // convention plugin（precompiled script）里 `the<LibrariesForLibs>()` 由此解析。
    implementation(files(libs::class.java.protectionDomain.codeSource.location))
    implementation("net.neoforged:moddev-gradle:2.0.140")   // moddev + legacyforge,同 libs.versions.toml 的 moddev
    // loom-back-compat 按控制器脚本(apply false)声明的 fabric-loom 版本挑选变体,
    // 自己不硬编码 Loom 版本——声明链留在 stonecutter.gradle.kts 原处,此处只上 classpath。
    implementation("dev.kikugie.loom-back-compat:dev.kikugie.loom-back-compat.gradle.plugin:0.4.2")
}
