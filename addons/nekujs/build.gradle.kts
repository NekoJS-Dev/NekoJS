import org.gradle.api.tasks.SourceSetContainer
import java.util.Properties

plugins {
    `java-library`
    id("net.neoforged.moddev")
}

// Ordinary subprojects do not automatically load their local gradle.properties.
val addonProperties = Properties().apply {
    file("gradle.properties").inputStream().use { load(it) }
}
fun addonProperty(name: String): String = requireNotNull(addonProperties.getProperty(name)) {
    "Missing NekuJS property: $name"
}
val modId = addonProperty("mod_id")
val modVersion = addonProperty("mod_version")
val minecraftVersion = addonProperty("deps.minecraft")
val neoVersion = addonProperty("deps.neo")

version = modVersion
group = addonProperty("mod_group_id")

java.toolchain.languageVersion = JavaLanguageVersion.of(21)
base.archivesName.set("nekujs-neoforge")
tasks.jar {
    archiveVersion.set("$minecraftVersion-$modVersion")
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://www.cursemaven.com") }
        filter { includeGroup("curse.maven") }
    }
    // The core exposes its relocated Graal runtime through this repository.
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter { includeGroup("maven.modrinth") }
    }
    // Resolve Gson compile-time annotations from Central rather than a loader mirror.
    exclusiveContent {
        forRepository { mavenCentral() }
        filter { includeGroup("com.google.errorprone") }
    }
}

evaluationDependsOn(":1.21.1")
evaluationDependsOn(":common")

val addonSources = sourceSets.main.get()
val runtimeSources = project(":1.21.1").extensions.getByType<SourceSetContainer>().named("main").get()
val commonSources = project(":common").extensions.getByType<SourceSetContainer>().named("main").get()
val auditSources = sourceSets.create("audit")
val auditTestSources = sourceSets.create("auditTest")
val compatProofSources = sourceSets.create("compatProof")
val compatProofTestSources = sourceSets.create("compatProofTest")
val fixtureDirectory = providers.gradleProperty("nekujsFixturesDir")
    .map { rootProject.file(it) }.orElse(file("1211nekujs"))
val testClientMods = fileTree(fixtureDirectory) { include("*.jar") }
val syncTestClientMods = tasks.register<Sync>("syncTestClientMods") {
    description = "Synchronizes fixture mods into the isolated test client's managed mods directory."
    from(testClientMods)
    into(layout.buildDirectory.dir("run/testClient/mods"))
}

neoForge {
    version = neoVersion
    mods {
        create(modId) {
            sourceSet(addonSources)
        }
        create("nekojs") {
            sourceSet(runtimeSources)
            sourceSet(commonSources)
        }
    }
    runs {
        create("client") {
            client()
            gameDirectory.set(layout.buildDirectory.dir("run/client"))
            jvmArguments.add("-Dfml.earlydisplay=false")
        }
        create("testClient") {
            client()
            ideName.set("NekuJS Test Client (1.21.1)")
            gameDirectory.set(layout.buildDirectory.dir("run/testClient"))
            jvmArguments.add("-Dfml.earlydisplay=false")
            // NeoForge discovers fixture JARs through the isolated game directory.
            taskBefore(syncTestClientMods)
        }
        create("server") {
            server()
            gameDirectory.set(layout.buildDirectory.dir("run/server"))
            programArgument("--nogui")
        }
    }
}

dependencies {
    // Development dependencies remain separate mods; the addon jar contains only its own output.
    implementation(project(":1.21.1"))
    implementation(project(":common"))
    "additionalRuntimeClasspath"("com.ibm.icu:icu4j:73.2")

    add(auditSources.implementationConfigurationName, libs.asm)
    add(auditSources.implementationConfigurationName, "org.ow2.asm:asm-commons:${libs.versions.asm.get()}")
    add(auditSources.implementationConfigurationName, libs.gson)
    testImplementation(libs.junit.jupiter.legacy)
    testRuntimeOnly(libs.junit.platform.launcher)
    add(auditTestSources.implementationConfigurationName, libs.junit.jupiter.legacy)
    add(auditTestSources.runtimeOnlyConfigurationName, libs.junit.platform.launcher)
}

// Experimental ABI classes are deliberately excluded from the shipping main source set.
compatProofSources.compileClasspath += addonSources.compileClasspath
compatProofSources.runtimeClasspath += addonSources.runtimeClasspath
compatProofTestSources.compileClasspath += compatProofSources.output + compatProofSources.compileClasspath + sourceSets.test.get().compileClasspath
compatProofTestSources.runtimeClasspath += compatProofSources.output + compatProofSources.runtimeClasspath + sourceSets.test.get().runtimeClasspath

auditTestSources.compileClasspath += auditSources.output + auditSources.compileClasspath
auditTestSources.runtimeClasspath += auditSources.output + auditSources.runtimeClasspath
val auditTest = tasks.register<Test>("auditTest") {
    description = "Tests the standalone binary auditor without compiling Minecraft sources."
    group = "verification"
    testClassesDirs = auditTestSources.output.classesDirs
    classpath = auditTestSources.runtimeClasspath
    useJUnitPlatform()
}
tasks.check { dependsOn(auditTest) }

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath + auditSources.output + auditSources.compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + auditSources.output + auditSources.runtimeClasspath
}

val compatibilityReport = layout.buildDirectory.file("reports/compatibility/fixtures.json")
tasks.register<JavaExec>("auditCompatibility") {
    group = "verification"
    description = "Audits addon jar compatibility without compiling the Minecraft runtime."
    dependsOn(tasks.named(auditSources.classesTaskName))
    classpath = auditSources.runtimeClasspath
    mainClass.set("com.tkisor.nekujs.audit.AddonCompatibilityAudit")
    args(fixtureDirectory.get().absolutePath, compatibilityReport.get().asFile.absolutePath)
    inputs.dir(fixtureDirectory)
    outputs.file(compatibilityReport)
}

val metadataProperties = mapOf(
    "mod_id" to modId,
    "mod_version" to modVersion,
    "mod_name" to addonProperty("mod_name"),
    "mod_license" to addonProperty("mod_license"),
    "mod_authors" to addonProperty("mod_authors"),
    "mod_description" to addonProperty("mod_description"),
    "minecraft_version_range" to addonProperty("deps.mc_range"),
    "neo_version_range" to addonProperty("deps.neo_range"),
    "nekojs_version_range" to addonProperty("nekojs_version_range"),
)

tasks.processResources {
    inputs.properties(metadataProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(metadataProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.test {
    // Contract suites initialize process-singleton platform and Minecraft services independently.
    forkEvery = 1
    useJUnitPlatform { excludeTags("fixtures", "expanded-profile") }
    dependsOn(tasks.jar)
    inputs.file(tasks.jar.flatMap { it.archiveFile })
    doFirst {
        systemProperty("nekujs.test.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    }
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    systemProperty("file.encoding", "UTF-8")
}

// External binaries are opt-in verification inputs, never normal test or distribution dependencies.
tasks.register<Test>("fixtureTest") {
    group = "verification"
    description = "Checks the original six-mod fixture contracts independently of added mods."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("fixtures") }
    systemProperty("nekujs.neo.version", neoVersion)
    inputs.dir(fixtureDirectory)
    systemProperty("nekujs.fixtures.dir", fixtureDirectory.get().absolutePath)
}
tasks.register<Test>("expandedFixtureTest") {
    group = "verification"
    description = "Checks required dependency versions and native contracts from the added mod profile."
    dependsOn(tasks.jar)
    inputs.file(tasks.jar.flatMap { it.archiveFile })
    doFirst { systemProperty("nekujs.test.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath) }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("expanded-profile") }
    systemProperty("nekujs.neo.version", neoVersion)
    systemProperty("nekujs.fixtures.dir", fixtureDirectory.get().absolutePath)
    inputs.dir(fixtureDirectory)
}

tasks.register<Test>("compatProofTest") {
    group = "verification"
    description = "Exercises an experimental native ABI against unchanged Additions classes, not game startup."
    testClassesDirs = compatProofTestSources.output.classesDirs
    classpath = compatProofTestSources.runtimeClasspath
    useJUnitPlatform()
    inputs.dir(fixtureDirectory)
    systemProperty("nekujs.fixtures.dir", fixtureDirectory.get().absolutePath)
}
