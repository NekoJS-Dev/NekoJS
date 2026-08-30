// NeoForge 节点的构建约定（对 1.21.1 / 26.1.2 / 26.2.0 各求值一次）。节点入口
// build.gradle.kts 只有一行 plugins 声明，节点间差异全部来自 versions/<node>/gradle.properties
// 的 deps.* 键。
//
// 节点的源码由两部分组成：所有节点共用的 src/main/java，加上本节点专属的
// versions/<node>/src/main/{java,resources}（放版本 compat 实现和差异过大不便用守卫表达的
// 孪生文件）。
//
// 两个环境限制值得先知道：
//   1. stonecutter 扩展不在 buildSrc 的编译类路径上，所以 process(File, path) 只能反射调用
//      （见下面的 stonecutterProcessed）；
//   2. 版本目录经 buildSrc/settings.gradle.kts 导入，libs 访问器要通过 LibrariesForLibs 取。

import groovy.lang.Closure
import org.gradle.accessors.dm.LibrariesForLibs
import org.slf4j.event.Level
import java.util.concurrent.Callable

plugins {
    id("java-library")
    id("net.neoforged.moddev")
}

// ---- per-node 参数 -----------------------------------------------------------

val libs = the<LibrariesForLibs>()

val mcVersion = property("deps.minecraft") as String
val neoVersion = property("deps.neo") as String
val jeiFileId = property("deps.jei") as String
val platformTag = property("deps.platform_tag") as String
val mcRange = property("deps.mc_range") as String
val neoRange = property("deps.neo_range") as String
val javaRelease = (property("deps.java") as String).toInt()

val modern = !mcVersion.startsWith("1.")   // 26.x vs 1.21.1

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modName = property("mod_name") as String
val modLicense = property("mod_license") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String

version = modVersion

// 节点在 settings 里先于 common 注册，求值时 :common 还没配置完；跨项目读 sourceSets
// 之前必须显式声明求值依赖。
evaluationDependsOn(":common")

// MDG mods 块的 lambda receiver 不是 project，sourceSet 需在顶层捕获
val mainSources = sourceSets.main.get()
val commonSources = project(":common").sourceSets.main.get()

java.toolchain.languageVersion = JavaLanguageVersion.of(javaRelease)

base { archivesName = "nekojs-neoforge" }
tasks.withType<Jar>().configureEach { archiveVersion.set("$mcVersion-$modVersion") }

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven { url = uri("https://cursemaven.com") } }
        filter { includeGroup("curse.maven") }
    }
}

// ---- stonecutter 预处理桥 -------------------------------------------------------
// 反射调用 stonecutter 扩展的 process(File, String)：active 节点返回原文件，其余节点返回
// 预处理产物。MDG 按路径消费 AT 和 mods.toml 模板，必须喂处理后的副本。

private fun Project.stonecutterProcessed(input: File, path: String): Any =
    extensions.getByName("stonecutter")
        .let { sc -> sc.javaClass.methods.first { it.name == "process" && it.parameterCount == 2 }.invoke(sc, input, path) }

// ---- 源码集 --------------------------------------------------------------------

sourceSets.main {
    resources.srcDir(layout.projectDirectory.dir("src/generated/resources"))
    resources.srcDir(rootProject.file(if (modern) "src/main/resources-modern" else "src/main/resources-legacy"))
}

// ---- 测试源集（共享 src/test/java + 节点本地），26.x-only 测试为整文件守卫 -------------
//   （26.x-only 测试不能挂给 1.21.1——被测类不在其 main 源集里；loader 同理走守卫）

sourceSets.test.configure {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

// ---- MDG ---------------------------------------------------------------------

neoForge {
    version = neoVersion

    // MDG 按路径消费 AT：喂 stonecutter 处理过的副本（active 节点即原文件）
    accessTransformers.from(
        stonecutterProcessed(
            rootProject.file("src/main/resources/META-INF/accesstransformer.cfg"),
            "processed/accesstransformer.cfg",
        )
    )

    if (modern) {
        // interface injection 只有 26.x 支持，且无版本内差异，直接用 26.x 资源层的原文件
        interfaceInjectionData {
            from(files(rootProject.file("src/main/resources-modern/nekojs.interface_injection.json")))
        }
    }

    mods {
        create(modId) {
            sourceSet(mainSources)
            sourceSet(commonSources)
        }
    }

    // 1.21.1 的 data run 用 data()，26.x 用 clientData()——26.x 起 datagen 按端分侧了
    runs {
        create("client") {
            client()
            jvmArguments.add("-Dfml.earlydisplay=false")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        create("data") {
            if (modern) clientData() else data()
            programArguments.addAll(
                "--mod", modId, "--all",
                "--output", layout.projectDirectory.dir("src/generated/resources").asFile.absolutePath,
                "--existing", layout.projectDirectory.dir("src/main/resources").asFile.absolutePath,
            )
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = Level.DEBUG
        }
    }
}

// ---- 依赖 ---------------------------------------------------------------------
// 必须位于 neoForge{}（runs 声明）之后：additionalRuntimeClasspath configuration
// 由 RunModel 在 runs 声明时才创建（顺序反了会 UnknownConfigurationException）

dependencies {
    implementation(project(":common"))
    implementation("curse.maven:jei-238222:$jeiFileId")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":common-api-processor"))
    // 处理器在自己的 classloader 里加载 @PlatformAvailability，所以契约类型也要上处理器路径。
    // 契约类型现在住 :common，代价是处理器路径上多出 common 与 Graal（不影响编译/运行 classpath）。
    annotationProcessor(project(":common"))
    compileOnly(libs.jspecify)

    // MC 节点用 JUnit 5：NeoForge 测试环境不兼容 JUnit 6（引擎模块走 BOM 6.0.0）
    testImplementation(libs.junit.jupiter.legacy)
    testRuntimeOnly(libs.junit.platform.launcher)

    if (!modern) {
        // 1.21.1 的开发运行需要显式补 ICU4J：MDG 的 server legacy classpath 不收项目
        // runtimeClasspath 的传递依赖。26.x 走 clientData，不再需要。
        "additionalRuntimeClasspath"("com.ibm.icu:icu4j:73.2")
    }
}

// stonecutter.process 的输出落在 build/generated/stonecutter/main/ 下，这个目录既是
// stonecutterGenerate 的输出、又是 MDG createMinecraftArtifacts 的输入（AT 按路径消费），
// 所以要显式声明依赖，否则是隐式依赖。
tasks.matching { it.name == "createMinecraftArtifacts" }.configureEach {
    dependsOn(tasks.named("stonecutterGenerate"))
}

// ---- neoforge.mods.toml 模板展开 ------------------------------------------------

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to mcVersion,
        "minecraft_version_range" to mcRange,
        "neo_version" to neoVersion,
        "neo_version_range" to neoRange,
        "loader_version_range" to "[4,)",
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
        "mod_authors" to modAuthors,
        "mod_description" to modDescription,
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    // 模板同样走 stonecutter.process（26.x 多一段 dynamic mixins 声明）
    from(
        stonecutterProcessed(
            rootProject.file("src/main/templates/META-INF/neoforge.mods.toml"),
            "processed/neoforge.mods.toml",
        )
    ) { into("META-INF") }
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main { resources.srcDir(generateModMetadata) }
neoForge.ideSyncTask(generateModMetadata)

// ---- fat-jar：内嵌引擎产物 + common 运行时（Graal 排除）--------------------------
// 与 fabric 节点的装配逻辑保持同构。

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
    // 必须用 from(Closure)（执行期求值）：KTS 的 map{} 会在配置期立即迭代，触发
    // :common:runtimeClasspath 的无锁解析，IDEA sync 和 gradlew tasks 会直接失败。
    from(object : Closure<Any>(null) {
        fun doCall(): List<Any> = embeddedCommonRuntime
            .toCollection(mutableListOf())
            .map { dep -> if (dep.isDirectory) dep else zipTree(dep) }
    })
}

// ---- 编译约定 -------------------------------------------------------------------

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:all",
        "-Xlint:-processing",
        "-Anekojs.platform=$platformTag",
    ))
}

// 诊断辅助：打印本节点 main 的编译 classpath（用于 vanilla-only 隔离编译探针）
tasks.register("dumpCompileClasspath") {
    val cp = sourceSets.main.map { it.compileClasspath.asPath }
    doLast { println(cp.get()) }
}

// ---- 测试任务（locale / 时区固定，避免依赖机器环境）--------------------------------

tasks.test {
    useJUnitPlatform()
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    systemProperty("file.encoding", "UTF-8")
}

tasks.register<Test>("nbtSmokeTest") {
    group = "verification"
    description = "Runs the native NeoForge portable binary NBT smoke tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("nbt-smoke")
    }
}

// ---- verifyDevModSourceSets：ModDev mod source-set 注册门禁 ----------------------
// 断言 mods{} 里注册了本平台与 :common 两个 source set，且各自的关键 class 已编译出来
// （开发运行需要它们可见）。契约类型（api.*）随 :common 一起进来，用 NekoId 抽查。

val verifyDevModSourceSets = tasks.register("verifyDevModSourceSets") {
    group = "verification"
    description = "Verifies NeoForge ModDev mod source-set registration for this node."
    dependsOn(tasks.named("classes"))
    dependsOn(project(":common").tasks.named("classes"))

    val requiredSets = mapOf(
        ":${project.name}" to mainSources,
        ":common" to commonSources,
    )
    val requiredClasses = listOf(
        Triple(":${project.name}", mainSources, "com/tkisor/nekojs/NekoJSMod.class"),
        Triple(":common", commonSources, "com/tkisor/nekojs/NekoJS.class"),
        Triple(":common", commonSources, "com/tkisor/nekojs/api/data/NekoId.class"),
    )
    val configuredSets = neoForge.mods.named(modId).map { it.modSourceSets.get() }

    doLast {
        val registered = configuredSets.get()
        requiredSets.forEach { (path, sourceSet) ->
            if (sourceSet !in registered) {
                throw GradleException(
                    "ModDev model for mod '$modId' in ${project.path} does not include $path source set '${sourceSet.name}'."
                )
            }
        }
        requiredClasses.forEach { (path, sourceSet, classPath) ->
            val present = sourceSet.output.classesDirs.files.any { dir -> File(dir, classPath).isFile }
            if (!present) {
                throw GradleException("$path source set '${sourceSet.name}' is missing compiled class '$classPath'.")
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDevModSourceSets)
}
