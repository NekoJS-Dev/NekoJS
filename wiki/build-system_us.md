<!-- wiki-page: build-system; locale: us -->

> **English** · [中文](build-system_cn)

<a id="wiki-section-1"></a>
# Build system

> This page covers the NekoJS repository's own build: organizing multiple versions and loaders, compiling each platform, guard discipline, and CI. **Plugin authors do not need to read it** unless interested; contributors and release maintainers do. See [ADR-0007](https://github.com/NekoJS-Dev/NekoJS/blob/master/docs/adr/0007-module-boundaries.md) and [ADR-0008](https://github.com/NekoJS-Dev/NekoJS/blob/master/docs/adr/0008-guard-discipline.md) for module placement and guard rules.

<a id="wiki-section-2"></a>
## Repository layout

A Stonecutter multi-version, multi-loader **single repository**: one shared version tree, per-node parameter directories, and two convention plugins. The version graph in settings.gradle.kts is:

```kotlin
stonecutter {
    versions("1.21.1", "26.1.2", "26.2.0")                    // NeoForge nodes (root branch, sharing src/)
    version("26.1.2-fabric", "26.1.2").buildscript = "fabric.gradle.kts"
    version("26.2.0-fabric", "26.2.0").buildscript = "fabric.gradle.kts"
}
```

- **Shared `src/` tree**: all nodes use one copy. Version differences use `//? if >=26` guards / replacements / node-specific paired files. Loader differences use whole-file `//? if neoforge` guards; the Fabric side uses else branches plus the raw loader root `src/fabric`, explicitly mounted by the Fabric convention without Stonecutter preprocessing (moved there in ticket 31).
- **`versions/<node>/`**: node parameters (`deps.*` in `gradle.properties`) plus node-specific sources for differences too large for convenient guarding, such as GUI / `PostEffectManager` implementations from before the 26.x split.
- **Build logic stays out of node scripts**: entry scripts only declare conventions; all build logic lives in buildSrc.

<a id="wiki-section-3"></a>
## Entry scripts and convention plugins

Each of the two entry scripts contains one declaration line:

| Entry | Convention plugin | Responsibilities |
|---|---|---|
| `build.gradle.kts` | `buildSrc/.../nekojs.neoforge-node.gradle.kts` | MDG (`neoForge` block/runs), supplying AT and mods.toml, era-specific resource layers, fat-jar, `verifyDevModSourceSets`, compile/test conventions |
| `fabric.gradle.kts` | `nekojs.fabric-node.gradle.kts` | loom-back-compat (applied inside the plugin, reading the Loom version from the controller's `apply false` declaration), ICU4J class extraction, bundled night-config, Fabric fat-jar, `verifyFabricRuntimeArtifact` |

Three established rules, based on past build failures, are enforced in the plugin implementation and documented beside the code:

1. Nodes are registered in settings before `:common`, so entry scripts/plugins must use `evaluationDependsOn`.
2. Stonecutter preprocessing is the output of `stonecutterGenerate` and the input to MDG, so an explicit `dependsOn` is required; neoforge-node already declares it.
3. Adding `:common` runtimeClasspath to a fat-jar must use `from(Closure)` for execution-time evaluation. Configuration-time resolution can attempt resolution without the required lock.

**When extending a convention plugin**: the Stonecutter extension is not on buildSrc's compile classpath (`stonecutter.process` uses a reflection bridge); loom/loomx extensions are accessed dynamically with `withGroovyBuilder`. The version catalog imports the root `gradle/libs.versions.toml` through `buildSrc/settings.gradle.kts`, keeping dependency coordinates in one source of truth.

<a id="wiki-section-4"></a>
## Node parameters (versions/&lt;node&gt;/gradle.properties)

Each node has a file using the `deps.*` prefix. Two keys are easy to confuse:

| Key | Meaning | Example |
|---|---|---|
| `deps.platform` | Guard constant, the source of truth for `constants.match`; either `neoforge` or `fabric` | `neoforge` |
| `deps.loader_version` | Loader version; used as a dependency coordinate only by Fabric nodes | `0.19.3` |
| `deps.minecraft` / `deps.neo` / `deps.java` | Version-axis parameters | `26.1.2` / `26.1.2.71` / `25` |

<a id="wiki-section-5"></a>
## Guards and renaming (required reading for shared-tree changes)

Distinguish enforced task checks from writing recommendations.

**guardLint enforces its checks** (`stonecutter.gradle.kts`, 8 rules, `./gradlew guardLint`, mandatory in CI). Six checks are hard failures: paired guards (`//? if` count must equal closing-guard count), guards outside Java text blocks, no `/*` at the start of a guard branch, **at most 20 `//? if` guards per file**, module boundaries (no MC/Loader imports in `com.tkisor.nekojs.api.*` or the rest of common), and constants that are always false (each guard constant must be true in at least one node). Graal dependencies are allowed in common. Zero loader imports in wrappers is an additional hard failure (rule 7, currently `wrapperLoaderImportHardFail = true`); wrappers guarded as entire loader-specific files are explicit platform surfaces and are only listed informationally.

The **only** way to exceed 20 guards is a normal Java comment such as `// guard-exempt(20): reason`, without the `//?` prefix reserved for Stonecutter directives. A stated reason grants an exemption and lists it on every run; without a reason, the task fails.

Scan scopes differ: guard rules inspect only `src/**/*.java` (the shared tree); module rules inspect `com/tkisor/nekojs/api/**` under `common/src/main/java` (L1, selected by package prefix) and the full tree (L2). Always-false constants are checked against `deps.platform` in `versions/*/gradle.properties`.

**Warning only**: consecutive guard regions longer than 8 lines (rule 5, a proxy for method-level density).

**The following are recommendations, not task checks**:
- **New code defaults to no guards** (26.x baseline, revised ADR-0008): use version facades (`McClientCompat` / `McPlatformCompat` under `platform/compat/`, with three version implementations, deliberately distinct class names to avoid drift, and `META-INF/services` registration) or node-specific paired files. This is the recommended approach under the density limit, not a gate: a file with exactly 20 guards still passes guardLint.
- **Whole-file splits** are recommended only when almost no logic is shared. Use a pure 26.x shared-tree file and an evaluated counterpart under `versions/1.21.1/src`.

**Replacements are configuration, not discipline**: `!mc_ids` handles pure renaming such as `ResourceLocation` ↔ `Identifier` and is enabled globally by default. `mc_legacy_api` is disabled by default and enabled locally with file-level `//~ mc_legacy_api`, only when that file does not contain the token on one side.

**sandboxCheck** aggregates guardLint and every node's `check`; run it locally before submitting.

<a id="wiki-section-6"></a>
## Everyday commands

```bash
./gradlew guardLint                          # Guard and boundary lint (seconds)
./gradlew :26.1.2:build                      # Single-node build (active node compiles directly in the IDE)
./gradlew :26.1.2-fabric:verifyFabricRuntimeArtifact # Verify Fabric fat-jar entry points and loader isolation
./gradlew :26.2.0-fabric:build              # Verify the 26.2 Fabric source bridge
./gradlew sandboxCheck                       # All-node gate before submitting
./gradlew switchVersion -Pnode=26.2.0        # Change active node, then resync the IDE
./gradlew :common:check                      # Engine checks
```

The active node is the node compiled/run directly in the IDE, selected by `stonecutter active "…"` in `stonecutter.gradle.kts`.

<a id="wiki-section-7"></a>
## Add a Minecraft version (three steps)

1. For `versions/<新版本>/gradle.properties`, copy the adjacent node and change `deps.minecraft` / `deps.neo` / `deps.jei` (Curse file id) / `deps.mc_range` / `deps.neo_range` / `deps.java`.
2. Add the node name to the version graph in `settings.gradle.kts`.
3. Add one entry to the build matrix in `.github/workflows/ci-build.yml`; the list is handwritten because there are few nodes.

<a id="wiki-section-8"></a>
## CI

`.github/workflows/ci-build.yml` uses a handwritten node matrix: `:common:check` → `guardLint` → NeoForge `nbtSmokeTest` → `npm run test:probe-types` → node `build` tasks, including Fabric 26.1.2/26.2.0 → each Fabric matrix leg verifies the downloaded Fabric jar and then runs a development-server smoke test. A commit title of `update <版本>` triggers release publication. GitHub Release and CurseForge tasks depend on both Fabric smoke legs; Fabric files remain explicitly excluded until publication is enabled separately. Runtime smoke uses versioned fixtures and stops after seeing startup/server-started markers. CI obtains JDKs through `actions/setup-java`; the repository no longer fixes `org.gradle.java.home`. Local users configure it in user-level `~/.gradle/gradle.properties` or export `JAVA_HOME`.

<a id="wiki-section-9"></a>
## Known boundaries

- Fabric tests still cover a subset: the 6 classes / 26 cases run so far cover pdata and three neutral adapters; the other shared tests remain NeoForge-guarded. CI builds 26.1.2-fabric and 26.2.0-fabric, verifies each downloaded Fabric jar, and runs a development-server smoke test in each matrix leg. Both legs must pass before the release job can run. The two Fabric nodes share the raw loader root `src/fabric` (moved in ticket 31 and explicitly mounted by the Fabric convention). `deps.fabric_source_node` remains transitional bridge metadata pending removal in ticket 32. `verifyFabricRuntimeArtifact` is already a release-artifact gate under `check`.
- Forge 1.20.1 is unsupported. Its API differs from the shared tree by an entire era, beyond what guards and replacements can bridge. Supporting it would effectively mean maintaining a second codebase, so its skeleton has been removed; it can be recovered from git history if needed.
- Cleanroom 1.12.2 is not built in this repository; a separate legacy branch maintains it.

- [Project architecture](project-architecture_us): the design behind the modules.

<!-- wiki-nav -->

---

[Previous: Probe type generation](probe-type-generation_us) · [Contents](Home)
