// Fabric 节点的构建约定（26.x-fabric）。实现源码和资源来自版本树共享根；
// loom-back-compat 在本插件体内 apply——它按 MC
// 版本挑选 Loom 变体，版本号从控制器脚本 stonecutter.gradle.kts 的 `apply false` 声明读取。
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

val fabricResourceRoot = rootProject.file("src/main/resources-fabric")
val fabricTemplateRoot = rootProject.file("src/main/templates-fabric")

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
    annotationProcessor(project(":common-api-processor"))
    annotationProcessor(project(":common"))
    compileOnly(libs.jspecify)

    // 共享测试树由 stonecutter 挂进本节点；MC 节点统一用 JUnit 5
    testImplementation(libs.junit.jupiter.legacy)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 共享版本树（src/main/java）由 stonecutter 自动挂载；Fabric-only 实现也位于共享树，
// 由整文件 `//? if fabric` 守卫选择。Fabric 资源单独使用 loader-specific 根，避免
// NeoForge 节点把 Fabric 配置和 mixin 清单带进制品。
sourceSets.main {
    resources.srcDir(fabricResourceRoot)
}

// dev run 目录：server 与 client 分开。共用一个目录时两个进程会互相覆盖 logs/latest.log
// 与 nekojs/*.log（Windows 上还会撞 Files.move 轮转）。
extensions.getByName("loom").withGroovyBuilder {
    "runs" {
        "named"("server") { "runDir"("run-server") }
    }
    // accessWidenerPath 是属性(setter),非方法——setProperty 走 Groovy 属性派发
    setProperty("accessWidenerPath", fabricResourceRoot.resolve("nekojs-fabric.accesswidener"))
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
    from(fabricTemplateRoot)
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

// 共享树的 resources（src/main/resources、resources-modern/-legacy）由 stonecutter 挂进本节点。
// Fabric-only resources-fabric 根已在上面显式加入；NeoForge 的 convention plugin 不引用它，
// 因此 Fabric access widener 和 mixin 清单不会进入 NeoForge 制品。
// 共享 NeoForge 资源仍由下方禁止清单兜底，防止跨加载器资源误装。
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
    useJUnitPlatform()
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    systemProperty("file.encoding", "UTF-8")
    // Fabric now has a versioned runtime smoke gate and six JUnit tests; an empty suite must fail.
    failOnNoDiscoveredTests = true
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
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:all",
        "-Xlint:-processing",
        "-Anekojs.platform=fabric",
    ))
}
