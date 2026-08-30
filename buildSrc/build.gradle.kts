// 节点构建约定（convention plugin）的编译环境：kotlin-dsl 加各加载器插件的 classpath。
// 插件一旦声明在这里（不带版本），precompiled script 的 plugins 块和节点入口脚本就能直接
// 引用；版本的事实源就是本文件与根 gradle/libs.versions.toml。
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
    // 把根版本目录生成的访问器类（LibrariesForLibs）放进编译类路径，convention plugin 里的
    // `the<LibrariesForLibs>()` 由此解析。
    implementation(files(libs::class.java.protectionDomain.codeSource.location))
    implementation("net.neoforged:moddev-gradle:2.0.140")   // 与 libs.versions.toml 的 moddev 保持一致
    // loom-back-compat 按 MC 版本挑选 Loom 变体，自己不硬编码 Loom 版本——版本声明留在
    // stonecutter.gradle.kts，这里只把插件放上 classpath。
    implementation("dev.kikugie.loom-back-compat:dev.kikugie.loom-back-compat.gradle.plugin:0.4.2")
}
