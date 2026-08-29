// NeoForge 分支共享构建脚本：stonecutter 对每个版本节点（versions/1.21.1、versions/26.1.2、
// versions/26.2.0）各求值一次。per-node 参数来自 versions/<node>/gradle.properties
//（stonecutter property() 读取；deps.loader=neoforge 驱动 `//? if neoforge` 常量）。
//
// 节点源码构成：分支共享 src/main/java（版本守卫 + loader 守卫单副本）+ 节点专属
// versions/<node>/src/main/{java,resources}（per-node compat 与不可守卫配对副本）。
// fabric 节点（26.1.2-fabric）用 fabric.gradle.kts，同一棵共享树。

import groovy.lang.Closure
import org.slf4j.event.Level
import java.util.concurrent.Callable

plugins {
    id("java-library")
    alias(libs.plugins.moddev)
}

// ---- per-node 参数 -----------------------------------------------------------

val mcVersion = property("deps.minecraft") as String
val neoVersion = property("deps.neo") as String
val jeiFileId = property("deps.jei") as String
val platformTag = property("deps.platform_tag") as String
val mcRange = property("deps.mc_range") as String
val neoRange = property("deps.neo_range") as String
val javaRelease = (property("deps.java") as String).toInt()
// AT / iface JSON / mods.toml 模板均已迁进沙箱共享或 era 层，不再需要 per-node 路径参数

val modern = !mcVersion.startsWith("1.")   // 26.x vs 1.21.1

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modName = property("mod_name") as String
val modLicense = property("mod_license") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String

version = modVersion

// 节点（1.21.1/26.1.2/26.2.0）在 settings 里先于 common 注册，求值时 :common 尚未配置；
// 跨项目读 sourceSets 前必须显式声明求值依赖（主仓靠 include 顺序隐式成立）
evaluationDependsOn(":common-api")
evaluationDependsOn(":common")

// MDG mods 块的 lambda receiver 不是 project，sourceSet 需在顶层捕获
val mainSources = sourceSets.main.get()
val commonSources = project(":common").sourceSets.main.get()
val commonApiSources = project(":common-api").sourceSets.main.get()

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

// ---- 源码集：只读引用主仓源码树 ------------------------------------------------

// ---- B2 收尾：根分支的 Java 源码与 resources 已全部住在沙箱里 -----------------------
// 分支共享 src/main/java（248 文件）= 已合并配对（守卫/replacements）+ neoforge-shared
// 直搬 + 版本专属文件的整文件守卫；节点专属目录 versions/<node>/src/main/{java,resources}
// 放 per-node compat 与「不可安全守卫」配对的 1.21.1 副本。
// resources 同样分层：src/main/resources（AT/mixins.json 守卫合并）+
// src/main/resources-modern（仅 26.x 有的 pack.mcmeta / dynamic mixins / iface JSON——
// 按 era 挂载，避免给 1.21.1 产出空文件而破坏 jar entry 对齐）。
// AT 与 iface JSON 被 MDG 按**路径**消费，所以要喂 stonecutter 处理后的副本
// （stonecutter.process(file, out)：active 节点返回原文件，其余返回预处理产物）。

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

// ---- 生成器已退役（B2 第一块成果）---------------------------------------------
// 原先这里移植了主仓 platforms/neoforge-1.21.1 的 generateLegacySharedSources
// （读 scripts/gen-1211-{rules,masters}，把 26-shared master 机械改名生成 1.21.1 源）。
// 27 个 master 全部合并为分支共享 src 的单副本后，改名交给 stonecutter replacements，
// 生成器、规则文件、清单文件在此形态下全部不再需要——主仓 B2 迁移时可直接删除。

// ---- 依赖块在 neoForge{}（runs 声明）之后——见下文注释 -------------------------

// ---- MDG ---------------------------------------------------------------------

neoForge {
    version = neoVersion

    // MDG 按路径消费 AT：喂 stonecutter 处理过的副本（active 节点即原文件）
    accessTransformers.from(
        stonecutter.process(
            rootProject.file("src/main/resources/META-INF/accesstransformer.cfg"),
            "processed/accesstransformer.cfg",
        )
    )

    if (modern) {
        // interface injection 只有 26.x 有（1.21.1 侧主仓也没挂），无版本差异，直接用 era 层原文件
        interfaceInjectionData {
            from(files(rootProject.file("src/main/resources-modern/nekojs.interface_injection.json")))
        }
    }

    mods {
        create(modId) {
            sourceSet(mainSources)
            sourceSet(commonSources)
            sourceSet(commonApiSources)
        }
    }

    // runs：对齐主仓 neoforge-26-shared.gradle / 1.21.1 build.gradle
    //（1.21.1 的 data run 用 data()，26.x 用 clientData()——26.x 的 datagen 分侧了）
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
    annotationProcessor(project(":common-api"))
    compileOnly(libs.jspecify)

    // JUnit 与主仓平台层同款（5.x 迁移期刻意分歧，BOM 6.0.0 只给 common 系）
    testImplementation(libs.junit.jupiter.legacy)
    testRuntimeOnly(libs.junit.platform.launcher)

    if (!modern) {
        // 主仓 1.21.1 的 dev-run ICU4J 补丁：MDG server legacy classpath 不收项目
        // runtimeClasspath 的传递库；26.x 走 clientData 时代不再需要
        "additionalRuntimeClasspath"("com.ibm.icu:icu4j:73.2")
    }
}

// stonecutter.process 把处理后的 AT 落在 build/generated/stonecutter/main/... 里，
// 而该目录是 stonecutterGenerate 的输出、又是 MDG createMinecraftArtifacts 的输入
// （AT 按路径消费）→ Gradle 报 implicit dependency。显式声明依赖即可。
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
        stonecutter.process(
            rootProject.file("src/main/templates/META-INF/neoforge.mods.toml"),
            "processed/neoforge.mods.toml",
        )
    ) { into("META-INF") }
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main { resources.srcDir(generateModMetadata) }
neoForge.ideSyncTask(generateModMetadata)

// ---- fat-jar：内嵌引擎产物 + common 运行时（Graal 排除）--------------------------
// 移植自主仓 gradle/neoforge-common.gradle 的 embeddedCommonRuntime + jar 合并块。

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
    // 必须用 from(Closure)（执行期求值，等价主仓 Groovy 的 from { ... }）：
    // KTS 的 embeddedCommonRuntime.map{...} 会解析到 Iterable.map（配置期立即迭代），
    // 在任务创建期触发跨项目 :common:runtimeClasspath 的无锁解析——IDEA sync / gradlew
    // tasks 直接炸 "Resolution ... attempted without an exclusive lock"
    from(object : Closure<Any>(null) {
        fun doCall(): List<Any> = embeddedCommonRuntime
            .toCollection(mutableListOf())
            .map { dep -> if (dep.isDirectory) dep else zipTree(dep) }
    })
}

// ---- 编译约定（对齐主仓根 allprojects + neoforge-26-shared.gradle）---------------

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:all",
        "-Xlint:-processing",
        "-Anekojs.platform=$platformTag",
    ))
}

// 诊断辅助：打印本节点 main 的编译 classpath（用于 vanilla-only 隔离编译探针，见 tools/）
tasks.register("dumpCompileClasspath") {
    val cp = sourceSets.main.map { it.compileClasspath.asPath }
    doLast { println(cp.get()) }
}

// ---- 测试任务（locale 固定对齐主仓根 allprojects 的 Test 约定）----------------------

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

// ---- verifyDevModSourceSets：ModDev mod source-set 注册门禁（移植主仓） -------------
// 主仓 gradle/neoforge-common.gradle 的同名任务：断言 mods{} 里注册了本平台 + :common +
// :common-api 三个 source set，且各自的关键 class 已编译出来（开发运行需要它们可见）。

val verifyDevModSourceSets = tasks.register("verifyDevModSourceSets") {
    group = "verification"
    description = "Verifies NeoForge ModDev mod source-set registration for this node."
    dependsOn(tasks.named("classes"))
    dependsOn(project(":common").tasks.named("classes"))
    dependsOn(project(":common-api").tasks.named("classes"))

    val requiredSets = mapOf(
        ":${project.name}" to mainSources,
        ":common" to commonSources,
        ":common-api" to commonApiSources,
    )
    val requiredClasses = listOf(
        Triple(":${project.name}", mainSources, "com/tkisor/nekojs/NekoJSMod.class"),
        Triple(":common", commonSources, "com/tkisor/nekojs/NekoJS.class"),
        Triple(":common-api", commonApiSources, "com/tkisor/nekojs/api/data/NekoId.class"),
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
