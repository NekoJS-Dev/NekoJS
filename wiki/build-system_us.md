<!-- wiki-page: build-system; locale: us -->

> **English** · [中文](build-system_cn)

<a id="wiki-section-1"></a>
# Build system

This page explains how the NekoJS repository builds itself: how several Minecraft versions and loaders share one codebase, and how to compile, check and release. Read it if you contribute to NekoJS or handle releases; plugin authors don't need it.

<a id="wiki-section-2"></a>
## Quick start

```bash
./gradlew :26.1.2:build                      # Build one node
./gradlew guardLint                          # Check guard usage and module boundaries; takes seconds
./gradlew :common:check                      # Engine-layer tests and isolation checks
./gradlew sandboxCheck                       # Before committing: guardLint + every node's check
./gradlew switchVersion -Pnode=26.2.0        # Switch the node your IDE compiles directly
```

Build outputs go to `versions/<node>/build/libs/`.

Requirements:

- Gradle itself needs JDK 17 or newer; 25 is recommended. The repository doesn't pin a local JDK path. Set `org.gradle.java.home` in your user-level `~/.gradle/gradle.properties`, or set `JAVA_HOME`.
- The JDK used for compiling comes from each node's `deps.java` (21 for 1.21.1, 25 for 26.x) and is fetched automatically through Gradle toolchains.

<a id="wiki-section-3"></a>
## Nodes

NekoJS uses [Stonecutter](https://stonecutter.kikugie.dev/) to build several versions from one repository. Each combination of Minecraft version and loader is called a **node**. `settings.gradle.kts` defines five:

```kotlin
stonecutter {
    kotlinController = true
    create(rootProject) {
        versions("1.21.1", "26.1.2", "26.2.0")                                // NeoForge
        version("26.1.2-fabric", "26.1.2").buildscript = "fabric.gradle.kts"  // Fabric
        version("26.2.0-fabric", "26.2.0").buildscript = "fabric.gradle.kts"
    }
}
```

| Node | Loader | Java |
|---|---|---|
| `1.21.1` | NeoForge 21.1 | 21 |
| `26.1.2` | NeoForge 26.1.2 | 25 |
| `26.2.0` | NeoForge 26.2.0 | 25 |
| `26.1.2-fabric` | Fabric | 25 |
| `26.2.0-fabric` | Fabric | 25 |

Gradle task paths use the node ID, such as `:26.2.0-fabric:build`. A node ID isn't always the Minecraft version: `26.2.0-fabric` depends on `deps.minecraft=26.2`.

Besides the five nodes there are two ordinary, unversioned subprojects, `common` and `common-api-processor`, plus the separately shipped `:addons:nekujs`.

<a id="wiki-section-4"></a>
### The active node

The node your IDE compiles and runs directly is the active node, set by the `stonecutter active "…"` line in `stonecutter.gradle.kts`. The active node compiles the shared `src/` directly; the other nodes compile copies preprocessed by Stonecutter.

To change it, run `./gradlew switchVersion -Pnode=<node>` and reload the Gradle project in your IDE. Don't edit that line by hand.

<a id="wiki-section-5"></a>
## Source organization

| Location | Contents |
|---|---|
| `src/main/` | One copy of the source shared by every node, written for 26.x |
| `src/main/resources-modern` / `resources-legacy` | AT, mixin configs and other resources for 26.x and 1.21.1 respectively |
| `src/fabric/` | Fabric-only code, resources and templates, mounted directly by the Fabric convention without Stonecutter preprocessing |
| `versions/<node>/gradle.properties` | Node parameters |
| `versions/<node>/src/` | Node-only sources |

Node-only files override shared ones. If `versions/<node>/src/` has a file at the same relative path as one in the shared tree, that node compiles its own copy instead of the shared one. This also holds for the active node; the `nekojs.node-sources` convention implements it.

Node-only sources serve two purposes:

- Version facade implementations, such as each version's `McPlatformCompat` and `McClientCompat` under `platform/compat/`.
- "Twin files" whose version differences are too large to be worth guarding. The shared tree holds the 26.x version, and `versions/1.21.1/src/` holds a 1.21.1 file with the same name. Files in a node directory are **not** guard-evaluated, so they must be plain, already-evaluated 1.21.1 code. The cost is keeping both copies in sync.

<a id="wiki-section-6"></a>
### Node parameters

Keys in `versions/<node>/gradle.properties` all start with `deps.`. For example, the 26.1.2 node:

```properties
deps.minecraft=26.1.2
deps.neo=26.1.2.71
deps.jei=7920926
deps.platform_tag=nf26
deps.mc_range=[26.1.2,26.2)
deps.neo_range=[26,)
deps.java=25
deps.platform=neoforge
```

Two keys are easy to mix up:

| Key | Meaning | Example |
|---|---|---|
| `deps.platform` | Loader constant; the source of the `neoforge` / `fabric` values used in guards | `neoforge` |
| `deps.loader_version` | Loader version; used as a dependency coordinate by Fabric nodes only | `0.19.3` |

<a id="wiki-section-7"></a>
## Build scripts

Both entry scripts are a single line. All build logic lives in convention plugins in `buildSrc`:

| Entry | Convention plugin | Responsible for |
|---|---|---|
| `build.gradle.kts` | `nekojs.neoforge-node` | ModDev (MDG) setup and run configs, AT and `neoforge.mods.toml`, era-split resources, fat jar, `verifyDevModSourceSets`, `verifyNeoForgeRuntimeArtifact` |
| `fabric.gradle.kts` | `nekojs.fabric-node` | Loom, ICU4J handling, bundled night-config, Fabric fat jar, `verifyFabricRuntimeArtifact` |
| Applied by both conventions | `nekojs.node-sources` | Node-only files take precedence over the shared tree, `src/fabric` and Stonecutter-generated sources |

Each node produces a fat jar that embeds the `common` runtime classes. Graal is excluded; players install the separate Graal mod. NeoForge 1.21.1 also builds a dedicated-server jar with a `-server` classifier that carries the ICU dependency servers need.

When you change a convention plugin, keep in mind:

1. Nodes are registered in settings before `:common`, so the conventions need `evaluationDependsOn(":common")`.
2. Stonecutter's preprocessed output is MDG's input, so add an explicit `dependsOn("stonecutterGenerate")`.
3. The fat jar must add `:common`'s runtimeClasspath with `from(Closure)` so it resolves at execution time. Resolving it at configuration time breaks IDE sync and `gradlew tasks`.
4. The Stonecutter extension isn't on buildSrc's compile classpath, so access it through reflection; access Loom extensions dynamically with `withGroovyBuilder`.
5. Dependency coordinates live only in the root `gradle/libs.versions.toml`; buildSrc imports it through `buildSrc/settings.gradle.kts`.

<a id="wiki-section-8"></a>
## Guards and renames

Code in the shared tree is written for 26.x. When you need to tell versions or loaders apart, there are three options, in order of preference:

1. **Version facade**: call a facade such as `McPlatformCompat` from the shared tree and put each version's implementation in the node directories. New code uses this by default.
2. **Node twin file**: only for files with almost no shared logic.
3. **Stonecutter guard**: `//? if >=26 { … //?}` or `//? if neoforge { … //?}`. Use it only when the first two don't fit.

Plain renames need no guard. The replacements in `stonecutter.gradle.kts` handle them:

- The `!mc_ids` group is on by default. It handles renames such as `ResourceLocation` ↔ `Identifier` and `GuiGraphics` ↔ `GuiGraphicsExtractor`. A name qualifies only if it never appears in one of the versions.
- The `mc_legacy_api` group is off by default and enabled per verified file with `//~ mc_legacy_api`. It holds renames where the call shape differs, such as `displayClientMessage` ↔ `sendSystemMessage`.

> Replacement rules must be literal `replace(...)` calls. Rules generated in a loop silently stop working, with no compile error.

<a id="wiki-section-9"></a>
### What guardLint checks

CI enforces `./gradlew guardLint`. It fails when:

- The number of `//? if` markers and closing markers doesn't match.
- A guard sits inside a Java text block (`"""`).
- The first line of a guard branch starts with `/*`.
- A single file has more than 20 `//? if` markers. If you really need more, add a plain Java comment `// guard-exempt(20): reason` to the file; it will appear in the exemption list.
- `common` (including `com.tkisor.nekojs.api.*`) imports a Minecraft or loader package (`net.minecraft`, `net.neoforged`, `net.fabricmc`, `net.minecraftforge`, `com.mojang`). Graal is allowed.
- The `wrapper` package has a loader import outside a guard. A wrapper wrapped entirely in a loader guard doesn't count.
- A guard uses a loader constant that is false on every node, so the branch never compiles.

A run of guarded lines longer than 8 only produces a warning.

Guard rules check only `src/**/*.java`; module boundary rules check `common/src/main/java`.

<a id="wiki-section-10"></a>
## Checks and tests

| Task | What it does |
|---|---|
| `guardLint` | The guard and boundary checks above |
| `:common:check` | Engine-layer tests plus isolation checks such as `checkCommonIsolation`. Running only `:common:test` is not a substitute |
| `:<node>:check` | Node tests, `platformGateTest` (spec and event coverage) and artifact verification; NeoForge nodes also run `nativeRecipeProbeTest` |
| `:<node>:build` | Compiles, packages, and runs the check above |
| `sandboxCheck` | `guardLint` + every node's `check` + `verifyExternalAddonIsolation` (makes sure the test addon fixture didn't end up in a production jar) |
| `:<NeoForge node>:nbtSmokeTest` | JVM tests for NBT handling |

All of these are JVM tests and artifact checks; none of them start the game. The Fabric `runServer` smoke test in CI only confirms that the server starts and startup scripts run. It is not full in-game acceptance.

<a id="wiki-section-11"></a>
## Separate addon: NekuJS

`:addons:nekujs` is a compatibility layer that lets some KubeJS addons run on NekoJS, on NeoForge 1.21.1 only. It is packaged separately as `nekujs-neoforge-1.21.1-<version>.jar` and is never embedded in a NekoJS node jar.

```bash
./gradlew :addons:nekujs:build
```

`build` runs only the contract tests that need no third-party jars. `fixtureTest`, `expandedFixtureTest`, `compatProofTest` and `runTestClient` need local third-party jars, by default in `addons/nekujs/1211nekujs`; point elsewhere with `-PnekujsFixturesDir=/path/to/fixtures`. These third-party jars are not shipped.

<a id="wiki-section-12"></a>
## Adding a Minecraft version

1. Copy the `versions/<node>/` folder of a similar node and update `deps.minecraft`, `deps.neo` (or `deps.fabric_api` for Fabric), `deps.jei`, `deps.mc_range`, `deps.neo_range` and `deps.java` in `gradle.properties`.
2. Add the node to the `stonecutter` block in `settings.gradle.kts`.
3. Update the places with hard-coded node lists: the build command in `.github/workflows/ci-build.yml` (plus the smoke matrix for Fabric nodes), and `tools/nekojs-ci-gates.py`.
4. Run `./gradlew :<new node>:build` and `./gradlew sandboxCheck`.

<a id="wiki-section-13"></a>
## CI and releases

The main flow in `.github/workflows/ci-build.yml`:

1. `:common:check` and `:common-api-processor:test`
2. `guardLint`
3. `nbtSmokeTest` on the three NeoForge nodes
4. `npm run test:probe-types` (TypeScript type checks on generated declarations)
5. `build` on all five nodes
6. Extra checks: native recipe probe, JSX declarations, per-node source override selection, and the build gate report (`tools/nekojs-ci-gates.py`)
7. For each Fabric node: verify the downloaded jar, then run a development server smoke test

A separate job runs `:common:check` again on JDK 21 to catch hidden dependencies on the Gradle daemon's JDK version.

**Releases** are triggered by the commit title:

| Commit title | Channel |
|---|---|
| `update <version>` | release |
| `update beta <version>` | beta |
| `update alpha <version>` | alpha |

A release creates a GitHub Release and publishes to CurseForge, once the build and the Fabric smoke tests pass. Fabric jars are currently CI artifacts only and are not attached to releases.

<a id="wiki-section-14"></a>
## Known limits

- Fabric's loader API coverage and test scope are intentionally smaller than NeoForge's.
- Forge 1.20.1 is not supported. It is an era apart from the shared tree, beyond what guards and renames can bridge, so its skeleton was removed. It can be recovered from git history if needed.
- Cleanroom 1.12.2 is outside this repository's build.

<a id="wiki-section-15"></a>
## Next steps

- [Project architecture](project-architecture_us): how the modules fit together.
- [Plugin development](plugin-development_us): write a NekoJS plugin mod.

<!-- wiki-nav -->

---

[Previous: Probe type generation](probe-type-generation_us) · [Contents](Home)
