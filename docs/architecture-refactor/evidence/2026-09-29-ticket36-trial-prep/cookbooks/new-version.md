# Cookbook: add a new version node (新增版本)

> Status: **DRAFT — UNVALIDATED**. Prepared for ticket 36 (`docs/architecture-refactor/implementation-tickets/36-release-maintainer-trials.md`, AC12) maintainer trials.
> Every path/symbol below was read at base commit `c8173622` (branch `ticket-36-trial-prep-a`). The real trial must confirm each step and correct this document.

## Purpose

Add a new build node (MC version × loader) to the stonecutter version graph and prove it builds, tests, gates, and names its artifacts correctly — **without** changing the supported five-node matrix. Per ticket 36 AC13, the trial runs on a throwaway branch/fixture with a trial node id (e.g. a `-t36trial` suffix) and is never merged into the final matrix; support levels are governed separately by `docs/architecture-refactor/specs/02-support-matrix.md`.

## Public entry points (as they exist at HEAD)

| Entry | File | Symbols / facts |
|---|---|---|
| Node graph — **single facts source** | `settings.gradle.kts` (stonecutter block) | `versions("1.21.1", "26.1.2", "26.2.0")` (NeoForge, entry `build.gradle.kts`); `version("26.1.2-fabric", "26.1.2").buildscript = "fabric.gradle.kts"` (id carries the `-fabric` suffix to avoid clashing with the NeoForge node) |
| Per-node properties | `versions/<node>/gradle.properties` | NeoForge keys: `deps.minecraft`, `deps.neo`, `deps.jei`, `deps.platform_tag`, `deps.mc_range`, `deps.neo_range`, `deps.java`, `deps.platform=neoforge`. Fabric keys: `deps.minecraft`, `deps.platform=fabric`, `deps.loader_version`, `deps.fabric_api`, `deps.java` |
| Convention plugins | `buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts`, `buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts` | NeoForge: ModDevGradle, `archivesName = "nekojs-neoforge"`, `archiveVersion = "$mcVersion-$modVersion"`, mods.toml template expansion from `src/main/templates/META-INF/neoforge.mods.toml`, `resources-modern`/`resources-legacy` split, `nbtSmokeTest` task. Fabric: Loom, `archivesName = "nekojs-fabric"`, access widener, `verifyFabricRuntimeArtifact`, `platformGateTest` |
| Entry buildscripts | root `build.gradle.kts` (NeoForge plugin `nekojs.neoforge-node`), root `fabric.gradle.kts` (plugin `nekojs.fabric-node`) | node buildscripts are referenced from the settings DSL; `versions/<node>/` currently holds only `gradle.properties` + `src/` |
| Controller | `stonecutter.gradle.kts` | `stonecutter active "26.1.2"`; `switchVersion` task (`gradlew switchVersion -Pnode=<node>`); replacements groups `!mc_ids` / `mc_legacy_api`; `guardLint` (8 rules); `sandboxCheck`; `verifyExternalAddonIsolation` |
| Shared catalog | `gradle/libs.versions.toml` | deliberately does **not** hold per-node MC/loader versions — those live in `versions/<node>/gradle.properties` (file header states this) |
| CI | `.github/workflows/ci-build.yml` | node lists appear in: "Build version nodes" step (`:1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build` — the step comment says keep in sync with `settings.gradle.kts`), the ticket 33 gates loop (`for node in 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric; do python3 tools/nekojs-ci-gates.py source-roots --node "$node"`), the Fabric smoke matrix, and `gametest-smoke` (`:26.1.2:runGameTestServer`) |
| Ticket 33 gates | `tools/nekojs-ci-gates.py` | `parse_nodes` reads the node graph **only** from `settings.gradle.kts` ("节点图唯一事实源 = settings.gradle.kts 的 stonecutter DSL"); commands `source-roots --node <node>`, `subsets`, `processor`, `declaration`, `nodes`, `all --out build/nekojs-gates-report.json` |
| Build strategy spec | `docs/architecture-refactor/specs/03-platform-build-strategy.md` | node identity/coordinates/naming must not be normalized ad hoc ("保留 `26.2.0` 节点身份、Minecraft 26.2 坐标语义和 Fabric 26.2 制品命名") |
| Ticket 33 baseline | `docs/architecture-refactor/baseline/2026-09-19-ci-processor-gate/` (`REPORT.md`, `MIGRATION.md`) | gates' owner/input/output/failure vocabulary |

## Owners and facts sources

| Artifact | Facts source / owner |
|---|---|
| Node set & ids | `settings.gradle.kts` stonecutter DSL — CI lists and `tools/nekojs-ci-gates.py` are derived; update CI lists when nodes change (the workflow comment says so explicitly) |
| Node coordinates (MC/NeoForge/loader/Fabric API versions, JDK) | `versions/<node>/gradle.properties` `deps.*` keys |
| Build behavior | the two `buildSrc` convention plugins (one per loader); a new node of an existing loader reuses them unchanged |
| Platform axis of guards | `deps.platform` per node feeds `constants.match(...)` in `stonecutter.gradle.kts` (`neoforge`/`fabric` guard constants) — guardLint rule 8 uses the same values to catch always-false constants |
| Cross-node event coverage ledger | `src/test/resources/nekojs/platform-gates/event-surface-domains.txt` — rows keyed `Domain | <node>`; a new node needs its own rows |

## Step-by-step (trial node, throwaway branch only)

1. **Branch first**: do the whole trial on a scratch branch off the current base; never commit the trial node to a shared branch (AC13).
2. **Register the node** in `settings.gradle.kts`:
   - NeoForge: append to `versions("1.21.1", "26.1.2", "26.2.0", "<trial-node>")` (entry `build.gradle.kts` implicit).
   - Fabric: add `version("<trial-node>-fabric", "<mc>").buildscript = "fabric.gradle.kts"` (id needs the `-fabric` suffix; MC coordinate semantics follow the pattern of the existing rows).
   Use a clearly-trial id (e.g. `26.3.0-t36trial`) so it can never be mistaken for a support-matrix member.
3. **Create `versions/<trial-node>/gradle.properties`** by copying the nearest existing node of the same loader and adjusting `deps.minecraft`, `deps.neo` (NeoForge) or `deps.loader_version`/`deps.fabric_api` (Fabric), plus `deps.mc_range`/`deps.neo_range` (they feed the expanded `neoforge.mods.toml`). `deps.java` fixes the toolchain.
4. **Node-local sources**: create `versions/<trial-node>/src/main/{java,resources}` only for files this era must override (twin pattern); otherwise leave the tree absent/empty. **TODO(trial): verify whether stonecutter tolerates a node directory with no `src/` and whether `versions/<node>/` needs any stub file.**
5. **Era differences**: prefer existing compat facades (`src/main/java/com/tkisor/nekojs/platform/compat/McVersionCompat.java`) or node overrides; extend the replacements tables in `stonecutter.gradle.kts` only for proven-equivalent renames (the file's comments define the `!mc_ids` admission bar). Run `guardLint` after any guard/replacement edit.
6. **Update CI lists** on the trial branch: the "Build version nodes" step, the ticket 33 gates `for node in ...` loop, and (for a fabric trial node) the `fabric-runtime-smoke` matrix + its `case "$node"` version mapping. The `gate_subsets` check in `tools/nekojs-ci-gates.py` compares these lists against the node graph, so they must move together.
7. **Expect two known gate interactions** (record outcomes, do not work around them by weakening gates):
   - `verifyExternalAddonIsolation` (registered in `stonecutter.gradle.kts`) asserts "expected to verify exactly 5 production jars (ticket 08 AC1)" — a sixth trial node changes the count and fails the gate **by design**; on the trial branch record this as the expected failure with its message rather than editing the count.
   - `EventSurfaceDomainGateTest` fails with `missing-binding` ("基线未登记本节点") for every fixture domain until you add `<trial-node> = present | buses=...` rows sourced from the gate's JSON output (`versions/<trial-node>/build/nekojs-gates/event-surface-<trial-node>.json`).
8. **Build and gate** (Windows: `./gradlew.bat`):
   - `./gradlew :<trial-node>:build` (compile + tests + artifact verify chain)
   - `./gradlew guardLint`
   - `python tools/nekojs-ci-gates.py source-roots --node <trial-node>` (writes `versions/<trial-node>/build/nekojs-gates/source-roots-<trial-node>.json`)
   - `python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json`
   - NeoForge nodes additionally have `nbtSmokeTest` (CI runs it for the three NeoForge nodes; fabric does not register the task — a gate difference to state, not to fix).
9. **Check artifact naming**: `versions/<trial-node>/build/libs/nekojs-neoforge-<mc>-<modVersion>.jar` (NeoForge) or `nekojs-fabric-...` (Fabric) — set by the convention plugins' `archivesName`/`archiveVersion`.
10. **Record and discard**: keep the verification log (commands, outputs, gate reports) as trial evidence, then drop the branch. Nothing merges into the five-node matrix.

## Contract / golden regeneration

- No declaration/contract golden regenerates merely because a node exists. The gate fixtures that are node-keyed are `src/test/resources/nekojs/platform-gates/event-surface-domains.txt` (step 7) and the source-roots JSON emitted per node (generated artifact, not committed).
- If era-difference work touches shared declarations, the relevant golden flows from the other cookbooks apply (`:common:regenerateGoldens`, `-Dnekojs.golden.regenerate=true` for `ApiManifestGoldenTest`).

## Tests to run (real task names)

| Check | Command | Notes |
|---|---|---|
| Node build + tests | `./gradlew :<trial-node>:build` | includes `check`, `platformGateTest`, and on fabric `verifyFabricRuntimeArtifact` |
| Guard lint | `./gradlew guardLint` | guard shape/density; wrapper loader imports; always-false constants vs `deps.platform` set |
| Source-roots probe | `python tools/nekojs-ci-gates.py source-roots --node <trial-node>` | required input for `gate_nodes` |
| Gate aggregate | `python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json` | same command CI runs |
| NBT smoke (NeoForge only) | `./gradlew :<trial-node>:nbtSmokeTest` | task does not exist on fabric nodes — state the gate difference |
| Aggregated check | `./gradlew sandboxCheck` | guardLint + every node `check` + addon-fixture isolation (expect the designed 5-jar failure on the trial branch) |
| Runtime smoke (fabric) | CI's `:<trial-node>:runServer` smoke flow with the shared fixture `src/fabric/test/resources/fabric-runtime-smoke/fabric_ci_smoke.js` | only if the trial node is fabric |
| GameTest | `:26.1.2:runGameTestServer` still passes (unchanged node) | proves the trial didn't disturb existing nodes |

## Affected nodes matrix

- The trial node itself is **not** part of the supported matrix (AC13): it must never change support levels, `docs/architecture-refactor/specs/02-support-matrix.md`, release/publish steps in CI, or the CurseForge/Modrinth publish steps.
- Existing five nodes must keep passing unchanged (`sandboxCheck` / CI) — the trial branch's diff to shared files (settings, CI lists, fixture) is exactly the reviewable surface.
- Gates that are node-count-sensitive by design (`verifyExternalAddonIsolation`, `EventSurfaceDomainGateTest` fixture coverage) respond as described in step 7.

## When no runtime/bootstrap change is needed

A new node is a build-graph concept: convention plugins, the version tree, and the shared engine cover it unchanged. The expected diff is `settings.gradle.kts` + `versions/<trial-node>/**` + CI lists + gate fixture rows (+ compat work only if the MC era actually demands it). No change to `common` runtime, `NekoRuntimeRoot`, plugin bootstrap, or any Script/Plugin API contract should appear. If the trial needs engine edits to bring a node up, record them as findings with owners and release impact (ticket 36 final AC).

## Trial checklist (maintainer fills during the real trial — AC1)

| # | Task | Entry used | Result (pass/fail + evidence) | Problems hit |
|---|---|---|---|---|
| 1 | Register trial node in `settings.gradle.kts` (correct id, buildscript) |  |  |  |
| 2 | Author `versions/<trial-node>/gradle.properties` (all `deps.*` keys) |  |  |  |
| 3 | `:<trial-node>:build` green |  |  |  |
| 4 | `guardLint` green |  |  |  |
| 5 | `platformGateTest` + fixture rows for the trial node |  |  |  |
| 6 | `tools/nekojs-ci-gates.py source-roots --node` + `all` outcomes |  |  |  |
| 7 | Artifact name/version correct (`nekojs-neoforge-<mc>-<modVersion>` / `nekojs-fabric-...`) |  |  |  |
| 8 | CI list updates and `gate_subsets` consistency |  |  |  |
| 9 | Designed gate failures recorded (`verifyExternalAddonIsolation` 5-jar count; others) |  |  |  |
| 10 | Existing five nodes unaffected (CI or `sandboxCheck` minus the designed failure) |  |  |  |
| 11 | Node identity/coordinates/naming kept explicit; no support-matrix change (AC13) |  |  |  |
| 12 | Confirm no runtime/bootstrap/engine diff |  |  |  |
