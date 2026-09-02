// Fabric 节点的构建约定（26.1.2-fabric）。loom-back-compat 在本插件体内 apply——它按 MC
// 版本挑选 Loom 变体，版本号从控制器脚本 stonecutter.gradle.kts 的 `apply false` 声明读取。
//
// 两个环境限制：libs 访问器要通过 LibrariesForLibs 取；loom / loomx 扩展在本插件的编译期
// 类型面上不存在，只能用 withGroovyBuilder 动态访问。

import groovy.lang.Closure
import org.gradle.accessors.dm.LibrariesForLibs
import java.util.concurrent.Callable

plugins {
    id("java-library")
}

val libs = the<LibrariesForLibs>()

pluginManager.apply("dev.kikugie.loom-back-compat")

val mcVersion = property("deps.minecraft") as String
val loaderVersion = property("deps.loader_version") as String
val fabricApiVersion = property("deps.fabric_api") as String
val javaRelease = (property("deps.java") as String).toInt()

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modName = property("mod_name") as String
val modDescription = property("mod_description") as String

version = modVersion

// 节点在 settings 里先于 common 注册，求值时 :common 还没配置完
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

// night-config：引擎的 engine.toml/probe.toml 读写依赖它。NeoForge 运行时自带（:common
// 里只声明 compileOnly），fabric 不提供——fabric 侧必须自己带：bundled 配置既进 dev
// classpath 也进分发 jar。
val bundled = configurations.create("bundled")
configurations.named("implementation").get().extendsFrom(bundled)

// GraalMC 的 curse 文件按加载器分构建：版本目录里的默认值是 NeoForge 构建（不含
// fabric.mod.json），fabric 节点定向解析到 8762963。版本下限见 libs.versions.toml 的 graal 注释。
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "curse.maven" && requested.name == "graal-1504336") {
            useVersion("8762963")
        }
    }
}

dependencies {
    // loom 的 minecraft / modImplementation 配置由 loom-back-compat(体内 apply)创建,
    // 无类型访问器——按名添加。
    add("minecraft", "com.mojang:minecraft:$mcVersion")
    extensions.getByName("loomx").withGroovyBuilder { "applyMojangMappings"() }
    add("modImplementation", "net.fabricmc:fabric-loader:$loaderVersion")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common"))
    bundled(libs.night.config.core)
    bundled(libs.night.config.toml)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.jspecify)

    // 共享测试树由 stonecutter 挂进本节点；MC 节点统一用 JUnit 5
    testImplementation(libs.junit.jupiter.legacy)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 共享版本树（src/main/java）由 stonecutter 自动挂载；加载器差异用 `//? if neoforge`
// 整文件守卫和节点目录表达，跨加载器中立的部分（BlockEvents 等）直接住共享树。

// dev run 目录：server 与 client 分开。共用一个目录时两个进程会互相覆盖 logs/latest.log
// 与 nekojs/*.log（Windows 上还会撞 Files.move 轮转）。
extensions.getByName("loom").withGroovyBuilder {
    "runs" {
        "named"("server") { "runDir"("run-server") }
    }
    // accessWidenerPath 是属性(setter),非方法——setProperty 走 Groovy 属性派发
    setProperty("accessWidenerPath", file("src/main/resources/nekojs-fabric.accesswidener"))
}

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

// ICU4J：GraalJS（truffle）的静态初始化需要它，而 fabric loader 和 MC 开发环境都不提供。
// 不能走依赖通道：loom 会把用户声明的 icu4j 从 dev run 类路径剥离（它按 MC manifest 库
// 去重）。改为把类直接提取进主 sourceSet 的输出目录——dev run 类路径必含源集输出，分发
// jar 也随之携带，一举覆盖开发与生产两侧。
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
// 挂进本节点。其中 AT / mods.toml / interface_injection 是 NeoForge 机制，fabric jar 不应携带；
// nekojs.mixins.json / nekojs-dynamic.mixins.json 里既有 NeoForge 专属 mixin 也有对两加载器
// 中立的条目——中立的那批由节点自己的 nekojs-fabric-shared/-dynamic.mixins.json 按条目激活
// （清单见 docs/fabric-port-status.md），整文件 exclude 的目的是不把 NeoForge 专属条目带进来。
// stonecutter 在 afterEvaluate 追加 srcDir，但 CopySpec 的 exclude 过滤器在执行期生效，仍然有效。
tasks.processResources {
    exclude("META-INF/accesstransformer.cfg")
    exclude("META-INF/neoforge.mods.toml")
    exclude("neoforge.mods.toml")
    exclude("nekojs.mixins.json")
    exclude("nekojs-dynamic.mixins.json")
    exclude("nekojs.interface_injection.json")
}

// 共享测试树的守卫面逐步放开后（适配器三件套等），JUnit Platform 必须显式启用——
// Gradle 9 默认测试框架不是 JUnit Platform，不配则 jupiter 测试静默发现不了
// （failOnNoDiscoveredTests = false 曾把这一点掩盖成"没有测试"）。
tasks.test {
    useJUnitPlatform()
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    systemProperty("file.encoding", "UTF-8")
    failOnNoDiscoveredTests = false
}

// ---- fat-jar：内嵌引擎产物 + common 运行时（Graal 排除）——与 NeoForge 节点同构 ----
// Loom 会在 remapJar 阶段重映射 jar；引擎与 Graal 不引用 MC 类，重映射对它们是恒等变换。

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
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")
    from(project(":common").sourceSets.main.get().output)
    // 必须 from(Closure) 执行期求值：配置期解析会撞 :common 的无锁解析
    from(object : Closure<Any>(null) {
        fun doCall(): List<Any> = embeddedCommonRuntime
            .toCollection(mutableListOf())
            .map { dep -> if (dep.isDirectory) dep else zipTree(dep) }
    })
    // fabric 侧自带的运行时库（night-config）——同样执行期求值
    from(object : Closure<Any>(null) {
        fun doCall(): List<Any> = bundled.resolve().map { dep -> zipTree(dep) }
    })
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
    // 不传 -Anekojs.platform：fabric 节点尚未把 common-api-processor 挂进 annotationProcessor
    // （NeoForge 节点两样都有）。@PlatformAvailability 注解源本身在 :common 里、fabric 可见，
    // 接线是待办而非不可能——接好后按节点平台传 fabric。
}
