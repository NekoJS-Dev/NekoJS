# Cookbook: add a new adapter (新增 Adapter)

> Status: **MAINTAINER-ACCEPTED**, 2026-10-06. See the [direct confirmation](../TICKET36-AGENT-PREPARED-DRAFT.md).
> Prepared at base commit `c8173622`; checklist paths are documented workflow entries, not reconstructed per-step observations.

## Purpose

Add an adapter that bridges a difference the shared surfaces cannot express inline. Ticket 36 AC5 requires the trial to distinguish **two classes of adapter difference**:

1. **Value/type conversion** — a `JSTypeAdapter` that converts script values at the boundary (e.g. `string → ItemStack`), so dispatch-string keys and binding parameters accept plain script values.
2. **Platform capability wiring** — code that connects a platform timing/capability (NeoForge event bus, Fabric callback, era-specific API) into the shared, loader-neutral surface without pulling the platform into shared business logic.

Pure renames between MC eras are **not** adapter work: they belong to stonecutter replacements (`!mc_ids` in `stonecutter.gradle.kts`) or small guards.

## Public entry points (as they exist at HEAD)

| Entry | File | Symbols |
|---|---|---|
| Adapter extension point | `common/src/main/java/com/tkisor/nekojs/core/plugin/AdaptersPoint.java` | `AdaptersPoint.ID = "nekojs:adapters"`, `Contributor.registerAdapters(JSTypeAdapterRegistry)` |
| Adapter SPI | `common/src/main/java/com/tkisor/nekojs/api/JSTypeAdapter.java` | implement this interface |
| Adapter registry | `common/src/main/java/com/tkisor/nekojs/api/data/JSTypeAdapterRegistry.java` | `registry.register(new MyAdapter())` |
| Base facade hook | `common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` | default `registerAdapters(JSTypeAdapterRegistry)` |
| NeoForge registration example | `src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java` (`registerAdapters`) and adapters in `src/main/java/com/tkisor/nekojs/js/type_adapter/*Adapter.java` (e.g. `IdentifierAdapter`, `ItemStackAdapter` family) |
| Fabric registration example | `src/fabric/java/com/tkisor/nekojs/fabric/FabricCorePlugin.java` (`registerAdapters`, platform-neutral subset with per-domain comments) |
| Platform event wiring (NeoForge) | `src/main/java/com/tkisor/nekojs/api/event/EventBusForgeBridge.java` | `create(IEventBus)`, `bind(EventBusJS)` / `bindTransformed(...)` |
| Platform event wiring (Fabric) | `src/fabric/java/com/tkisor/nekojs/fabric/event/FabricServerEventBindings.java` (and sibling `Fabric*EventBindings`) — neutral payload classes in `com.tkisor.nekojs.wrapper.event.*`; the class Javadoc records a real timing difference (fabric `starting`/`aboutToStart` both fire in SERVER_STARTING before world load) as the pattern for documenting, not hiding, platform timing |
| Cross-era facade (version difference) | `src/main/java/com/tkisor/nekojs/platform/compat/McVersionCompat.java` — compat facade consumed cross-loader (the CI Fabric smoke asserts a real call through it: `FABRIC-CI-SMOKE: spawnLightning ok`, `.github/workflows/ci-build.yml`) |
| Node twin (whole-file override) | `src/main/java/com/tkisor/nekojs/client/render/ClientRenderEvents.java` (26.x tree) vs `versions/1.21.1/src/main/java/com/tkisor/nekojs/client/render/ClientRenderEvents.java` (1.21.1 twin) |

## Placement rules (facts source: `docs/architecture-refactor/implementation-handoff.md` §2)

| Location | Put here | Never |
|---|---|---|
| `common/src/main/java/com/tkisor/nekojs/api/` | adapter SPI / contracts | MC/loader imports, implementations |
| `common/` (any) | engine, plugin runtime | any `net.minecraft` / `net.neoforged` / `net.fabricmc` / `net.minecraftforge` / `com.mojang` import (guardLint rule 6, ADR-0007) |
| `src/main/java/` | truly shared MC-facing adapters and wrappers expressible by guards/facades | whole-file single-loader implementations |
| `src/fabric/java/` | code shared by the two fabric nodes (mounted only by the fabric convention) | NeoForge sources, second cross-loader business logic |
| `versions/<node>/src/main/` | differences too large for a facade/guard (node twins) | copies of shared business logic, a second runtime owner |

Prefer the shared tree + facade; fall back to `versions/<node>` twins only when the file is genuinely era/loader-specific (the `ClientRenderEvents` twins are the precedent). Spec backing: `docs/architecture-refactor/specs/03-platform-build-strategy.md` ("版本差异优先复用现有 compat facade 或 `versions/<node>` node override").

## Owners and facts sources

| Artifact | Facts source / owner |
|---|---|
| Adapter collection semantics | `AdaptersPoint.java` (self-contained Point: merge = `MergePolicy.append()`, accumulator = `JSTypeAdapterRegistry.Impl`) |
| Probe input aliases ($-aliases like `$ItemStack_`) | derived from registered adapters (see `AdaptersPoint.Contributor.registerAdapters` Javadoc) — adapters are the single facts source; do not hand-author aliases |
| Guard / replacement discipline | `stonecutter.gradle.kts` `guardLint` (8 rules) and ADRs `docs/adr/0007-module-boundaries.md`, `docs/adr/0008-guard-discipline.md` |
| Client render adapter lifecycle (prior art) | `docs/architecture-refactor/baseline/2026-09-21-client-input-hud/` (ticket 26) and `docs/architecture-refactor/baseline/2026-09-27-client-gui-render/` (ticket 27, incl. candidate-registration cleanup semantics) |

## Step-by-step

1. **Classify the difference.** Value conversion → type adapter (A). Platform timing/capability → wiring adapter (B). Rename-only → guards/replacements, not an adapter. Registration-type/Builder differences (ticket 36 AC5's first class) surface as facade/builder work, not as adapters.
2. **Choose placement** with the table above. Check the nearest existing example first (`IdentifierAdapter` for A; `EventBusForgeBridge` / `FabricServerEventBindings` / `McVersionCompat` for B).
3. **Type adapter (A)**: implement `com.tkisor.nekojs.api.JSTypeAdapter`, register it in the core plugin's `registerAdapters` for each loader that can supply the type (`NekoJSCorePlugin.registerAdapters` and/or `FabricCorePlugin.registerAdapters`). The probe alias and parameter conversion follow from registration — nothing else to edit.
4. **Wiring adapter (B)**: keep shared surfaces loader-neutral; the adapter lives in `src/main/java/` (NeoForge face, whole-file guard), `src/fabric/java/` (fabric face), or a node twin. Document platform timing differences at the adapter (copy the `FabricServerEventBindings` Javadoc pattern) instead of abstracting them into shared runtime — ticket 36 AC5 explicitly forbids pushing native platform timing into the shared runtime.
5. **Guards**: if the file needs `//? if neoforge {...//?}` / `//? if >=26 {...//?}` guards, keep density within the guardLint limits (≤ 20 per file; `// guard-exempt(20): reason` only with justification, ADR-0008).
6. **Update consumers**: dispatch buses that rely on the adapter (string-keyed dispatch like `EntityEvents.joinLevel('minecraft:zombie', ...)`) now accept the converted type on the nodes where it is registered; missing adapters surface as `Unsupported target type` (documented in `FabricCorePlugin`'s class Javadoc) — keep that failure explicit, no silent fallback.

## Contract / golden regeneration

- Adding a type adapter changes probe input aliases; if a probe golden covers the touched surface, regenerate with `./gradlew :common:regenerateGoldens` and review the diff (goldens live under `common/src/test/resources/nekojs/probe/`).
- `common/src/test/resources/nekojs/golden/api-manifest-core.json` moves only if `common` `api.*` contract types change — an adapter implementation in the version tree should not move it; verify with `:common:check`.
- `common/src/test/java/com/tkisor/nekojs/probe/ProbeOutputCompatibilityTest.java` guards derived probe output compatibility.

## Tests to run (real task names)

| Check | Command | What it proves here |
|---|---|---|
| Boundary/isolation | `./gradlew :common:check` (includes `checkCommonIsolation`) | No MC/loader import entered `common` |
| Guard discipline | `./gradlew guardLint` | Guard pairing/density/shape; wrapper zero-loader-import; no always-false constants |
| Node compile/tests | `./gradlew :<node>:check` for every affected node (wired to `platformGateTest` and, on fabric, `verifyFabricRuntimeArtifact`) | The adapter compiles and behaves on each node it exists for |
| Aggregated gate | `./gradlew sandboxCheck` (guardLint + every node `check` + `verifyExternalAddonIsolation`) when the change spans nodes | Cross-node consistency |
| Probe types (if declarations moved) | `npm run test:probe-types` (after `npm ci`) | TS declarations still typecheck |
| Runtime smoke when wiring is behavior-bearing | `:<fabric-node>:runServer` CI smoke path, `:26.1.2:runGameTestServer`, or Minecraft MCP live evidence | The wiring actually fires end-to-end |

## Affected nodes matrix

- An adapter registered only in `NekoJSCorePlugin` exists on the three NeoForge nodes; the fabric nodes see `Unsupported target type` until `FabricCorePlugin` registers an equivalent — that gap must stay explicit (capability vocabulary, not silent degradation).
- A `versions/<node>` twin affects only that node, but any change to the shared twin counterpart must inspect both files — the handoff and spec 03 both state that one node passing is not evidence for the others.
- Wiring adapters in `src/fabric/java/` affect both fabric nodes together.

## When no runtime/bootstrap change is needed

Both adapter classes ride existing machinery: type adapters are collected by `AdaptersPoint` each bootstrap; wiring adapters bind to already-registered buses or platform callbacks. No change to `NekoPluginBootstrap`, `NekoRuntimeRoot`, generation switching, or reload semantics should appear in the diff. Adapter products are process-level and survive ordinary reload (ticket 08 baseline, MIGRATION.md §4).

## Trial checklist (maintainer fills during the real trial — AC1)

| # | Task | Documented entry (not a recorded trial path) | Aggregate acceptance | Per-step problems |
|---|---|---|---|---|
| 1 | Classify the difference (type conversion vs platform wiring vs rename) | Existing adapter classification guidance | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 2 | Choose placement per the handoff table without guessing | Handoff placement table | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 3 | Implement + register the adapter (A) or wiring (B) | Existing adapter registration path | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 4 | Confirm probe alias / dispatch conversion appears without hand-editing | Existing generated probe path | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 5 | `guardLint` + `:common:check` clean | Existing verification commands | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 6 | `:<node>:check` on all affected nodes; note gate differences of uncovered nodes | Existing node checks | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 7 | Golden regenerations reviewed (if any) | Existing golden workflow | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 8 | Runtime/GameTest/MCP evidence when wiring is behavior-bearing | Existing runtime smoke path | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 9 | Confirm no runtime/bootstrap diff | Existing architecture boundary | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 10 | Confirm platform timing differences are documented at the adapter, not abstracted into shared runtime (AC5) | Existing adapter documentation | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
