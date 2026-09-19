// Fabric 节点的构建约定（26.x-fabric）。两个节点共享的 raw loader 源根是仓库级
// `src/fabric`（实施交接单 W8 已批准锚点），由本 convention 显式挂载；loom-back-compat
// 在本插件体内 apply——它按 MC 版本挑选 Loom 变体，版本号从控制器脚本
// stonecutter.gradle.kts 的 `apply false` 声明读取。
//
// 两个环境限制：libs 访问器要通过 LibrariesForLibs 取；loom / loomx 扩展在本插件的编译期
// 类型面上不存在，只能用 withGroovyBuilder 动态访问。

import groovy.lang.Closure
import org.gradle.accessors.dm.LibrariesForLibs
import java.util.concurrent.Callable
import java.util.zip.ZipFile

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
// 少数 NeoForge 专属实现没有共享语义，按类名下放并从 Fabric source set 排除，避免
// 它们因缺少外层守卫而进入 Fabric fat jar。
//
// `src/fabric` 是两个 Fabric 节点唯一共享的 raw loader root（交接单 W8 锚点，票 31 迁入）：
// java/resources 由本 convention 显式注入，templates/access widener 同根取材。该目录在
// 共享树的 source set 布局（src/main|test）之外，stonecutter **不做预处理**——版本差异
// 由共享树的 compat facade（McVersionCompat 等）或 versions/<node>/src 的节点 override 承担，
// raw root 里的文件必须同时编过 26.1.2 与 26.2。迁移前的 versions/26.1.2-fabric 借用式
// bridge（deps.fabric_source_node）已按票 32 的五层 trace 与门禁证据删除。
//
// 注意票 01 的教训仍然成立：若把 raw root 文件放回 versions/<node>/src/main，
// stonecutter 的节点本地挂载会与本注入叠成双 copy root，processResources 冷构建确定性失败
//（docs/architecture-refactor/baseline/2026-09-12-build-baseline-report.md §7.1）。
val fabricSourceRoot = rootProject.file("src/fabric")
sourceSets.main {
    java.srcDir(fabricSourceRoot.resolve("java"))
    resources.srcDir(fabricSourceRoot.resolve("resources"))
    java.exclude("**/NeoForge*.java")
}

// dev run 目录：server 与 client 分开。共用一个目录时两个进程会互相覆盖 logs/latest.log
// 与 nekojs/*.log（Windows 上还会撞 Files.move 轮转）。
extensions.getByName("loom").withGroovyBuilder {
    "runs" {
        "named"("server") { "runDir"("run-server") }
    }
    // accessWidenerPath 是属性(setter),非方法——setProperty 走 Groovy 属性派发
    setProperty("accessWidenerPath", fabricSourceRoot.resolve("resources/nekojs-fabric.accesswidener"))
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
        "fabric_api_version" to fabricApiVersion,
        "java_version" to javaRelease.toString(),
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from(fabricSourceRoot.resolve("templates"))
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
val fabricForbiddenResourceEntries = setOf(
    "META-INF/accesstransformer.cfg",
    "META-INF/neoforge.mods.toml",
    "neoforge.mods.toml",
    "nekojs.mixins.json",
    "nekojs-dynamic.mixins.json",
    "nekojs.interface_injection.json",
)
tasks.processResources {
    exclude(*fabricForbiddenResourceEntries.toTypedArray())
}

// 共享测试树的守卫面逐步放开后（适配器三件套等），JUnit Platform 必须显式启用——
// Gradle 9 默认测试框架不是 JUnit Platform，不配则 jupiter 测试静默发现不了
// （failOnNoDiscoveredTests = false 曾把这一点掩盖成"没有测试"）。
tasks.test {
    // 工单 33 gate 走独立 platformGateTest JVM（它建立进程级事件 schema，不能与普通用例同 JVM）
    useJUnitPlatform {
        excludeTags("platform-gate")
    }
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    systemProperty("file.encoding", "UTF-8")
    // Fabric now has a versioned runtime smoke gate and six JUnit tests; an empty suite must fail.
    failOnNoDiscoveredTests = true
}

// ---- 工单 33：contract/spec + event/surface 覆盖 gate（Fabric processor 延期的非 processor 替代） ----
val platformGateTest = tasks.register<Test>("platformGateTest") {
    group = "verification"
    description = "Runs the ticket-33 non-processor coverage gates (contract/spec, event/surface)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("platform-gate")
    }
    // 节点身份是 event/surface 基线的一级键（同 loader 的不同 MC 节点可有合法差异）
    systemProperty("nekojs.node", project.name)
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    systemProperty("file.encoding", "UTF-8")
}

// 工单 33：非 processor 覆盖 gate 是节点 check 的一部分（Fabric processor 延期不得变成覆盖空白）。
tasks.named("check") { dependsOn(platformGateTest) }

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

// ---- verifyFabricRuntimeArtifact：制品不得混入 NeoForge 配置或入口 -------------------
// processResources 的 exclude 只描述期望；此任务直接检查最终 fat jar，防止源集或装配逻辑
// 变化后静默把 NeoForge 资源带进 Fabric 发布物。
val fabricJar = tasks.named<Jar>("jar")
val verifyFabricRuntimeArtifact = tasks.register("verifyFabricRuntimeArtifact") {
    group = "verification"
    description = "Verifies that the Fabric runtime jar has its Fabric entrypoints and no NeoForge artifacts."
    dependsOn(fabricJar)
    inputs.file(fabricJar.flatMap { it.archiveFile })

    doLast {
        val archive = fabricJar.get().archiveFile.get().asFile
        val requiredEntries = setOf(
            "fabric.mod.json",
            "nekojs-fabric.accesswidener",
            "nekojs-fabric.mixins.json",
            "nekojs-fabric-shared.mixins.json",
            "nekojs-fabric-dynamic.mixins.json",
            "com/tkisor/nekojs/fabric/NekoJSFabricMod.class",
            "com/tkisor/nekojs/fabric/NekoJSFabricClient.class",
            "com/tkisor/nekojs/NekoJS.class",
        )
        val forbiddenPrefixes = listOf(
            "com/tkisor/nekojs/neoforge/",
            "net/neoforged/",
            "META-INF/services/net.neoforged.",
        )
        val forbiddenClassNameTokens = listOf("neoforge", "neoforged")

        ZipFile(archive).use { jar ->
            val zipEntries = jar.entries()
            val entries = generateSequence {
                if (zipEntries.hasMoreElements()) zipEntries.nextElement().name else null
            }.toSet()
            val missing = requiredEntries - entries
            val forbidden = entries.filter { entry ->
                entry in fabricForbiddenResourceEntries || forbiddenPrefixes.any(entry::startsWith)
            }
            val forbiddenClasses = entries.filter { entry ->
                entry.endsWith(".class") && forbiddenClassNameTokens.any { token ->
                    entry.contains(token, ignoreCase = true)
                }
            }
            val expectedFabricApiDependency = "\"fabric-api\": \">=$fabricApiVersion\""
            val metadata = if ("fabric.mod.json" in missing) "" else {
                jar.getInputStream(jar.getEntry("fabric.mod.json")).bufferedReader().use { it.readText() }
            }
            val invalidFabricApiDependency = expectedFabricApiDependency !in metadata

            if (missing.isNotEmpty() || forbidden.isNotEmpty() || forbiddenClasses.isNotEmpty() || invalidFabricApiDependency) {
                throw GradleException(
                    "Fabric runtime artifact ${archive.name} is invalid: " +
                        listOfNotNull(
                            missing.takeIf { it.isNotEmpty() }?.let { "missing ${it.sorted()}" },
                            forbidden.takeIf { it.isNotEmpty() }?.let { "contains NeoForge artifacts ${it.sorted()}" },
                            forbiddenClasses.takeIf { it.isNotEmpty() }?.let {
                                "contains NeoForge classes ${it.sorted()}"
                            },
                            invalidFabricApiDependency.takeIf { it }?.let {
                                "does not require fabric-api >=$fabricApiVersion"
                            },
                        ).joinToString("; ")
                )
            }
        }
    }
}

tasks.named("check") { dependsOn(verifyFabricRuntimeArtifact) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
    // 不传 -Anekojs.platform：fabric 节点尚未把 common-api-processor 挂进 annotationProcessor
    // （NeoForge 节点两样都有）。@PlatformAvailability 注解源本身在 :common 里、fabric 可见，
    // 接线是待办而非不可能——接好后按节点平台传 fabric。
}
