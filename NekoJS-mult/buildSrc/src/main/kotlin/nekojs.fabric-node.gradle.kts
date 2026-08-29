// fabric 节点 convention plugin（26.1.2-fabric）。从 fabric.gradle.kts 原体迁入
//（DEVEX-ROADMAP T2）。loom-back-compat 在本插件体内 apply——它从控制器脚本
//（stonecutter.gradle.kts，apply false）声明的 fabric-loom 版本挑选变体，声明链不变。
// 与原体的适配：libs 经 LibrariesForLibs 取用；loom / loomx 扩展不在本插件编译期
// 类型面上，经 withGroovyBuilder 动态访问（方法/属性名与 loom 稳定 API 对齐）。

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

// 节点在 settings 里先于 common 注册，求值时 :common 尚未配置（求值顺序陷阱①）
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

// night-config：引擎的 engine.toml/probe.toml 读写依赖它。NeoForge 运行时自带（:common
// 里只声明 compileOnly），fabric 不提供——fabric 侧必须自己带：bundled 配置既进 dev
// classpath 也进分发 jar。
val bundled = configurations.create("bundled")
configurations.named("implementation").get().extendsFrom(bundled)

// GraalMC 的 curse 文件按加载器分 build：8762962 是 NeoForge 构建（catalog 默认，
// 无 fabric.mod.json），fabric 节点定向解析到 8762963（fabric 构建，GraalMC 25.1.3.7；
// 须自带 TRegex 注册，见 libs.versions.toml 的 graal 注释）。
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

    // 共享 src/test（共享测试树）由 stonecutter 挂进本节点；junit 与主仓平台层同款
    testImplementation(libs.junit.jupiter.legacy)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 共享版本树（src/main/java）由 stonecutter 自动挂载；加载器差异用 `//? if neoforge`
// 整文件守卫与节点目录表达，跨加载器中立契约（BlockEvents 等）直接住共享树。

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

// ICU4J：GraalJS（truffle）静态初始化需要；fabric loader / MC dev 环境都不提供。
// 实测 loom 会把用户声明的 icu4j 依赖从 dev run 类路径剥离（识别为 MC manifest 库去重），
// 故不走依赖通道——直接把类提取进主 sourceSet 输出目录：dev run 类路径必含源集输出，
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

// ---- fat-jar：内嵌引擎产物 + common 运行时（Graal 排除）——与 neo/forge 节点同构 ----
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
    // 求值顺序陷阱③：from(Closure) 执行期求值（配置期解析会撞 :common 的无锁解析）
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
    // fabric 骨架暂无 @PlatformAvailability 注解源；平台标签（fl26 等）随 LoaderBridge 一起定
}
