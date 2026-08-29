// fabric branch 共享构建脚本（B3 spike：Loom 与 MDG 在同一构建里共存）。
//
// 目标（计划 B3 第 ③ 步的第一格）：
//   1. Fabric Loom 与 ModDevGradle 在一个 Gradle 构建里各管各的节点，互不干扰；
//   2. :common 引擎（Java 21 字节码 + GraalJS 运行时）能以 fat-jar 形态嵌进 fabric jar；
//   3. fabric loader 真机能加载并进入 mod 入口。
// 尚未做：LoaderBridge 的 fabric 实现（事件/网络/注册），因此入口只做加载验证。

import groovy.lang.Closure
import java.util.concurrent.Callable

plugins {
    id("java-library")
    // 26.1+ 起 Mojang 去掉了混淆，stock fabric-loom 的 officialMojangMappings() 直接失败
    //（实测：Failed to find official mojang mappings for 26.1.2）。loom-back-compat 是
    // stonecutter 官方 fabric 模板同款的兼容层：按 MC 版本挑选正确的 Loom 变体，并提供
    // loomx.applyMojangMappings()。
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

val mcVersion = property("deps.minecraft") as String
val loaderVersion = property("deps.loader") as String
val fabricApiVersion = property("deps.fabric_api") as String
val javaRelease = (property("deps.java") as String).toInt()

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modName = property("mod_name") as String
val modDescription = property("mod_description") as String

version = modVersion

evaluationDependsOn(":common-api")
evaluationDependsOn(":common")

java.toolchain.languageVersion = JavaLanguageVersion.of(javaRelease)

base { archivesName = "nekojs-fabric" }
tasks.withType<Jar>().configureEach { archiveVersion.set("$mcVersion-$modVersion") }

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    exclusiveContent {
        forRepository { maven { url = uri("https://cursemaven.com") } }
        filter { includeGroup("curse.maven") }
    }
}

// night-config：引擎的 engine.toml/probe.toml 读写依赖它。NeoForge 运行时自带（主仓 :common
// 里只声明 compileOnly），**fabric 不提供**——实测 runServer 报
// NoClassDefFoundError: com/electronwill/nightconfig/core/file/CommentedFileConfig。
// 所以 fabric 侧必须自己带：bundled 配置既进 dev classpath 也进分发 jar。
val bundled: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(bundled)

// GraalMC 的 curse 文件按加载器分 build：8456810 是 NeoForge 构建（catalog 默认，
// 无 fabric.mod.json），fabric 节点定向解析到 8456812（fabric 构建，"支持所有版本"
// 指 MC 版本维度）。
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "curse.maven" && requested.name == "graal-1504336") {
            useVersion("8456812")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common"))
    bundled(libs.night.config.core)
    bundled(libs.night.config.toml)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.jspecify)
}

// 共享版本树（src/main/java）由 stonecutter 自动挂载；加载器差异用 `//? if neoforge`
// 整文件守卫与 `//? if fabric` 表达，跨加载器中立契约（BlockEvents 等）直接住共享树。

// fabric.mod.json 模板展开（Loom 不做变量替换，沿用 ProcessResources 约定）
// 属性必须在顶层读：任务配置 lambda 里裸调 property() 会解析到任务的动态属性查找
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_version" to modVersion,
        "mod_description" to modDescription,
        "minecraft_version" to mcVersion,
        "loader_version" to loaderVersion,
        "java_version" to javaRelease.toString(),
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main { resources.srcDir(generateModMetadata) }

// ICU4J：GraalJS（truffle）静态初始化需要；fabric loader / MC dev 环境都不提供。
// 实测 loom 会把用户声明的 icu4j 依赖从 dev run 类路径剥离（识别为 MC manifest 库去重；
// 探针对照：commons-text 原始坐标可进、icu4j 经 bundled/runtimeOnly 恒不进），故不走
// 依赖通道——直接把类提取进主 sourceSet 输出目录：dev run 类路径必含源集输出，
// 分发 jar 亦随之携带（同时覆盖 dev 与生产，无需 bundled 嵌包）。
val icuClassesDir = layout.buildDirectory.dir("generated/icuClasses")
val extractIcuClasses = tasks.register<Sync>("extractIcuClasses") {
    val icuJar = configurations.detachedConfiguration(
        dependencies.create("com.ibm.icu:icu4j:" + libs.icu4j.get().version)
    ).resolve().single()
    from(zipTree(icuJar)) {
        exclude("META-INF/**")
        exclude("module-info.class")
    }
    into(icuClassesDir)
}
sourceSets.main {
    output.dir(mapOf("builtBy" to extractIcuClasses), icuClassesDir)
}

// 共享树的 resources（src/main/resources、resources-modern/-legacy、templates）由 stonecutter
// 挂进本节点，里面是 NeoForge 专属的 AT/mixins/mods.toml；fabric jar 不应携带它们。
// stonecutter 在 afterEvaluate 追加 srcDir，但 CopySpec 的 exclude 过滤器在执行期生效，仍然有效。
tasks.processResources {
    exclude("META-INF/accesstransformer.cfg")
    exclude("META-INF/neoforge.mods.toml")
    exclude("neoforge.mods.toml")
    exclude("nekojs.mixins.json")
    exclude("nekojs-dynamic.mixins.json")
    exclude("nekojs.interface_injection.json")
}

// LoaderBridge 移植前共享测试树全部被 `//? if neoforge` 守卫成空文件：测试源码存在但没有
// 可发现的测试类，Gradle 9 的 failOnNoDiscoveredTests 会因此失败——显式放行。
tasks.test { failOnNoDiscoveredTests = false }

// ---- fat-jar：内嵌引擎产物 + common 运行时（Graal 排除）——与 NeoForge 侧同构 ----------
// Loom 会在 remapJar 阶段重映射 jar；引擎与 Graal 不引用 MC 类，重映射对其为恒等变换。

val embeddedCommonRuntime = files(
    Callable {
        project(":common").configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .filter { artifact ->
                !(artifact.moduleVersion.id.group == "curse.maven" && artifact.name == "graal-1504336")
            }
            .map { artifact -> artifact.file }
    }
)

tasks.jar {
    dependsOn(project(":common").tasks.named("jar"))
    dependsOn(project(":common-api").tasks.named("jar"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")
    from(project(":common").sourceSets.main.get().output)
    from(project(":common-api").sourceSets.main.get().output)
    from(object : Closure<Any>(null) {
        fun doCall(): List<Any> = embeddedCommonRuntime
            .toCollection(mutableListOf())
            .map { dep -> if (dep.isDirectory) dep else zipTree(dep) }
    })
    // fabric 侧自带的运行时库（night-config）——同样执行期求值，避免配置期解析
    from(object : Closure<Any>(null) {
        fun doCall(): List<Any> = bundled.resolve().map { dep -> zipTree(dep) }
    })
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
    // fabric 骨架暂无 @PlatformAvailability 注解源；平台标签（fl26 等）随 LoaderBridge 一起定
}
