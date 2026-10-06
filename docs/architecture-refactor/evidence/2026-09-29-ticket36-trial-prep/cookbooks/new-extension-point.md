# Cookbook: add a new extension point (新增扩展点)

> Status: **MAINTAINER-ACCEPTED**, 2026-10-06. See the [direct confirmation](../TICKET36-AGENT-PREPARED-DRAFT.md).
> Prepared at base commit `c8173622`; checklist paths are documented workflow entries, not reconstructed per-step observations.

## Purpose

Add a new **collection channel** to the engine: a named mount point that collects contributions from plugins at bootstrap (e.g. a new `nekojs:xxx` point with its accumulator and frozen product). This is the ADR-0001/0002/0003 Point / Contributor / Hook / Handle model. Two distinct scenarios are covered:

- **S1 — builtin channel on `NekoJSPlugin`** (a facade hook every plugin can override). This is the "four-part change" with a pairing gate.
- **S2 — custom point from a plugin** (including an external addon). No engine change at all — this is the normal third-party path.

Vocabulary (from `CONTEXT.md`): 扩展点 (Extension Point) is the named mount; 贡献面 (Contributor) is the interface plugins implement; 插件钩子 (Plugin Hook) is the facade method on `NekoJSPlugin`; 扩展点句柄 (Extension Handle) is the registration return used to fetch the frozen product after bootstrap.

## Public entry points (as they exist at HEAD)

| Entry | File | Symbols |
|---|---|---|
| Point definition model | `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginExtensionPoint.java` | builder `NekoPluginExtensionPoint.<P,A,R>builder(id, pluginType)` with `.merge(MergePolicy...)`, `.initializer(...)`, `.collector(...)`, `.finish(...)`, `dependsOn(point)/dependsOnId(id)`, `dependsOnOptional(...)`; `id()`, `pluginType()` |
| Merge policies | `common/src/main/java/com/tkisor/nekojs/core/plugin/MergePolicy.java` | `append` / `firstWin` / `overrideWarn` / `failFast` (ADR-0001 four standard policies) |
| Provider entry (custom points) | `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginExtensionProvider.java` — `registerPluginExtensionPoints(NekoPluginExtensionRegistry registry)` | registration returns `NekoPluginExtensionHandle` (`common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginExtensionHandle.java`) |
| Accumulator sealing | `common/src/main/java/com/tkisor/nekojs/core/plugin/Sealable.java` | optional; sealed after finisher, late collection throws |
| Builtin points manifest | `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoBuiltinPointsPlugin.java` | `registerPluginExtensionPoints` registers all builtin `*Point.POINT`s — "新增内置扩展点 = 一个自包含 Point 文件 + 本清单一行" |
| Template Point file | `common/src/main/java/com/tkisor/nekojs/core/plugin/AdaptersPoint.java` | self-contained: `ID`, `Contributor` interface with default hook, `POINT` builder chain |
| Facade base interface | `common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` | default hooks (e.g. `registerAdapters(JSTypeAdapterRegistry)`); the class Javadoc states the facade-projection contract |
| Pairing gate | `common/src/test/java/com/tkisor/nekojs/core/plugin/PluginHookPairingTest.java` | `CHANNELS` pairing table; five invariants |
| Bootstrap/runtime (read-only context) | `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginBootstrap.java`, `NekoPluginRuntime.java`, `common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java` | ordering (Kahn topological, builtin-first), freeze, Handle publication — you consume this machinery, never extend it per channel |
| External addon prior art | `common/src/addonFixture/java/com/example/addon/` (real external addon fixture incl. a custom point with the nested `ExampleAddonPlugin.GreetingAccumulator` as the `Sealable` example); `docs/architecture-refactor/baseline/2026-09-22-plugin-addon/MIGRATION.md` §2 (custom point contract) | `Ticket08ExternalAddonChainTest` (`common/src/test/java/com/tkisor/nekojs/core/plugin/`) drives discovery → contribution → freeze → consumption → reload survival |

Model references: `docs/adr/0001-extension-point-model-v2.md`, `docs/adr/0002-extension-point-dependency-semantics.md`, `docs/adr/0003-builtin-extension-point-registration.md`, `docs/adr/0010-plugin-authoring-model.md`.

## Owners and facts sources

| Artifact | Facts source / owner |
|---|---|
| A channel's semantics (merge policy, accumulator, finisher, dependencies) | the self-contained `XxxPoint.java` — the **only** facts source (ADR-0001); the `NekoJSPlugin` hook is a facade projection |
| The builtin-channel list | `NekoBuiltinPointsPlugin` (explicit manifest, constructed directly by bootstrap before third-party providers — no `@RegisterNekoJSPlugin` scan) |
| Pairing protocol | `PluginHookPairingTest.CHANNELS` — mechanical guard; missing any of the four parts turns it red |
| Dependency semantics | ADR-0002: timing deps declared via `dependsOn`, data deps read via `NekoPluginExtensionContext.result(...)` (free, but out-of-order read throws with the fix hint), optional timing deps via `dependsOnOptional*` |

## Step-by-step

### S1 — add a builtin collection channel (four-part change)

1. **Create the Point file** `common/src/main/java/com/tkisor/nekojs/core/plugin/XxxPoint.java` following `AdaptersPoint.java`: `public static final String ID = "nekojs:xxx";`, a `Contributor` interface extending `NekoJSPlugin` with a default hook `registerXxx(XxxRegister registry)`, and the `POINT` built with an explicit `MergePolicy` (`append` for list-shaped channels), an `initializer` creating a fresh accumulator, a `collector` referencing the hook, and a `finish` that snapshots an immutable product. The collector lambda must be defined in this file (pairing invariant 5).
2. **Add the facade hook** on `common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` as a `default` method mirroring the Contributor hook (name starts with `register`), with Javadoc that states which Point it projects.
3. **Register the Point**: one line in `NekoBuiltinPointsPlugin.registerPluginExtensionPoints` (`registry.register(XxxPoint.POINT);`).
4. **Add the pairing row** in `PluginHookPairingTest.CHANNELS`: `new Channel(XxxPoint.ID, "registerXxx", "XxxPoint", List.of("XxxRegister"))`.
5. **Decide freeze/seal semantics**: implement `Sealable` on the accumulator if late collection must fail (see `ExampleAddonPlugin.GreetingAccumulator` in the addon fixture); otherwise document that late writes only mutate the accumulator and never the published product.
6. **Consumer**: read the product either via the `NekoPluginExtensionHandle` returned at registration or via `NekoPluginRuntime.extensionProduct(pointId, type)` — same frozen object (ticket 08 asserts identity with `assertSame`).

### S2 — add a custom point from a plugin / external addon

1. Implement `NekoPluginExtensionProvider.registerPluginExtensionPoints` on your plugin; `registry.register(point)` returns the handle.
2. Build the point with your own namespaced id (`modid:name` is the recommended convention, per `NekoPluginExtensionPoint`'s parameter docs); builtin `nekojs:*` ids are reserved and duplicates throw at registration.
3. Use `dependsOnId("nekojs:bindings")` etc. for ordering; read upstream products with `context.result(...)` only after the upstream point finished.
4. Reference contract for authors: `docs/architecture-refactor/baseline/2026-09-22-plugin-addon/MIGRATION.md` (discovery inputs per loader, three freeze layers, reload semantics) and the wiki plugin-development pages.
5. No engine repo change is needed or allowed for this scenario — if the trial finds itself editing engine files to ship a third-party point, that is a failure record.

## Contract / golden regeneration

- Adding a default hook to `NekoJSPlugin` changes a `common` `api.*` contract type: run `:common:test --tests com.tkisor.nekojs.core.api.ApiManifestGoldenTest` and expect drift in `common/src/test/resources/nekojs/golden/api-manifest-core.json`; regenerate deliberately with
  `./gradlew :common:test -Dnekojs.golden.regenerate=true --tests com.tkisor.nekojs.core.api.ApiManifestGoldenTest`
  then review the diff as a breaking-change review (the test's failure message documents this exact flow). **TODO(trial): confirm the manifest input actually includes `NekoJSPlugin` member symbols; if not, drop this step.**
- S2 touches no goldens.

## Tests to run (real task names)

| Check | Command | What it proves here |
|---|---|---|
| Pairing protocol | `:common:check` — `PluginHookPairingTest` (5 invariants: builtin list ↔ pairing table, pluginType = `NekoJSPlugin.class`, hook declared + default, `register*` set = paired ∪ `registerApiSurface` exemption, collector defined in the Point file) | The four parts were changed together |
| Isolation | `:common:check` (includes `checkCommonIsolation`) | The new Point file stays MC/loader-free |
| Processor | `:common-api-processor:test` when contract-adjacent types change | Processor unaffected |
| Manifest golden | `ApiManifestGoldenTest` (within `:common:check`) | Contract surface change is deliberate, reviewed |
| Node propagation | `:<node>:check` on representative nodes | The channel collects on real plugin discovery inputs |
| External-addon semantics (if the channel should serve addons) | `Ticket08ExternalAddonChainTest` + `Ticket08LoaderDiscoveryTest` pattern; extend with a fixture consumer for the new channel | Discovery → contribution → freeze → Handle → reload survival |

## Affected nodes matrix

- The Point model lives in `common`, so a builtin channel reaches all five nodes through the `:common` dependency; each node's plugin discovery (NeoForge annotation scan / Fabric entrypoints) feeds the same collection path — no per-node wiring.
- The pairing gate is node-independent (`:common`). No gate differences are expected across nodes for the channel itself; consumer surfaces (bindings/events exposed to scripts) still follow each node's capability.

## When no runtime/bootstrap change is needed

Both scenarios ride the existing bootstrap machinery — ordering (Kahn topological, builtin-first), freeze windows, `Sealable` sealing, Handle publication, process-level products surviving ordinary reload (ticket 08 MIGRATION.md §4). The diff for S1 is exactly four files (Point, `NekoJSPlugin`, `NekoBuiltinPointsPlugin`, `PluginHookPairingTest`) plus consumers/tests; S2 is plugin-side only. Editing `NekoPluginBootstrap` / `NekoPluginRuntime` / `NekoRuntimeRoot` to add a channel is a failure record (ticket 36 AC7: reuse Point/Contributor/Hook/dependency/freeze/Handle semantics; no second lifecycle, no static result table, no second facts source). Direct callback hooks (no collection) do not get a Point at all — keep their dispatch separate.

## Trial checklist (maintainer fills during the real trial — AC1)

| # | Task | Documented entry (not a recorded trial path) | Aggregate acceptance | Per-step problems |
|---|---|---|---|---|
| 1 | Find the Point model docs + template without reading internals | Existing Point/Contributor cookbook | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 2 | Write the self-contained Point file (id, merge policy, accumulator, finisher) | Public Point model | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 3 | Add facade hook + builtin manifest line + pairing row (four-part change) | Public plugin and pairing path | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 4 | `:common:check` green incl. `PluginHookPairingTest` | Existing common check | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 5 | Manifest golden drift regenerated + reviewed (if applicable) | Existing golden workflow | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 6 | Consume the product via Handle / `extensionProduct` and verify freeze semantics | Existing Handle contract | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 7 | Reload survival: ordinary `/nekojs reload` does not re-collect or drop the product | Existing reload contract | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 8 | (S2) custom point from an external addon with zero engine edits | Existing addon extension path | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 9 | Confirm no runtime/bootstrap diff | Existing architecture boundary | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
| 10 | Confirm no second facts source / static result table was needed (AC7) | Existing ownership rules | Aggregate maintainer acceptance; no per-step output supplied | Not individually recorded |
