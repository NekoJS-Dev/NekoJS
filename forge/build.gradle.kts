// forge branch 共享构建脚本（对 branch 内每个节点求值；当前只有 1.20.1）。
//
// 1.20.1 无 NeoForge——用 ModDevGradle 的 legacyforge 变体（Forge ≤1.20.1 官方新工具链，
// 替代 ForgeGradle）。D7 决策（2026-08-26 用户拍板）：1.20.1 线要求 Java 21 运行时，
// :common 保持 Java 21 字节码直接内嵌（与 NeoForge 节点同构的 fat-jar），不走 --release 17。
// 平台源码仍为节点本地骨架（B4 回植未开始）。

import groovy.lang.Closure
import org.slf4j.event.Level
import java.util.concurrent.Callable

plugins {
    id("java-library")
    id("net.neoforged.moddev.legacyforge") version "2.0.140"
}

val mcVersion = property("deps.minecraft") as String
val forgeVersion = property("deps.forge") as String
val mcRange = property("deps.mc_range") as String
val loaderRange = property("deps.loader_range") as String
val javaRelease = (property("deps.java") as String).toInt()

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modName = property("mod_name") as String
val modLicense = property("mod_license") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String

version = modVersion

// 引擎层来自根构建的 :common 副本；节点在 :forge 下求值先于 common，需显式声明
evaluationDependsOn(":common-api")
evaluationDependsOn(":common")

val mainSources = sourceSets.main.get()
val commonSources = project(":common").sourceSets.main.get()
val commonApiSources = project(":common-api").sourceSets.main.get()

java.toolchain.languageVersion = JavaLanguageVersion.of(javaRelease)

base { archivesName = "nekojs-forge" }
tasks.withType<Jar>().configureEach { archiveVersion.set("$mcVersion-$modVersion") }

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven { url = uri("https://cursemaven.com") } }
        filter { includeGroup("curse.maven") }
    }
}

legacyForge {
    version = forgeVersion

    mods {
        create(modId) {
            sourceSet(mainSources)
            sourceSet(commonSources)
            sourceSet(commonApiSources)
        }
    }

    runs {
        create("server") {
            server()
            programArgument("--nogui")
        }
        create("gameTestServer") {
            type = "gameTestServer"
            // Forge 1.20.1 的命名空间是 forge.*（非 neoforge.*）
            systemProperty("forge.enabledGameTestNamespaces", modId)
        }
        configureEach {
            logLevel = Level.DEBUG
        }
    }
}

dependencies {
    implementation(project(":common"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":common-api-processor"))
    annotationProcessor(project(":common-api"))
    compileOnly(libs.jspecify)
}

// META-INF/mods.toml（Forge 47 仍是 mods.toml，非 neoforge.mods.toml）
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version_range" to mcRange,
        "loader_version_range" to loaderRange,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
        "mod_authors" to modAuthors,
        "mod_description" to modDescription,
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main { resources.srcDir(generateModMetadata) }

// ---- fat-jar：内嵌引擎产物 + common 运行时（Graal 排除）——与根分支同构 ----------------

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
    // 同根分支：from(Closure) 执行期求值（Iterable.map 会在配置期触发无锁跨项目解析）
    from(object : Closure<Any>(null) {
        fun doCall(): List<Any> = embeddedCommonRuntime
            .toCollection(mutableListOf())
            .map { dep -> if (dep.isDirectory) dep else zipTree(dep) }
    })
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
    // 1.20.1 骨架无 @PlatformAvailability 注解源，-Anekojs.platform 暂不传
    //（B4 端口时随主仓处理器标签集一起定 fg120 之类的新标签）
}
