# Golden declaration gap (tickets 23 AC1 / 24 AC8 / 27 AC7) — review pack

**Maintainer ruling:** 2026-09-29 「现在备 diff 供审」— prepare old/new diffs + impact statement for one batch review.
**Branch:** `golden-decl-prep` (worktree `../NekoJS-mult-tgold`), based on `mult@973defbc`. Review artifact only — nothing merges to `mult` from this task.
**Date:** 2026-09-28/29.

---

## 1. Root cause: why the recorded greps return zero hits

The gap evidence (ticket 23 `baseline/2026-09-22-recipe-data-surface/` AC1 note, ticket 24
`baseline/2026-09-22-gameplay-event-surface/command-output/04-declaration-coverage.txt`, ticket 27
`baseline/2026-09-27-client-gui-render/command-output/10-declaration-coverage.txt`) greps
`api-manifest-core.json`, the probe `*.expected.d.ts` goldens and `declaration-parity.txt` for the
recipe/data, gameplay and client GUI/render symbols and finds 0 hits. The cause is **(a) generator
input coverage — by architecture**, not stale goldens and not missing metadata:

| Artifact | Guard test | Derivation input | Why the three domains can never appear |
|---|---|---|---|
| `common/src/test/resources/nekojs/golden/api-manifest-core.json` | `ApiManifestGoldenTest` | `CoreManagedApiBootstrap.buildContract()` (`common/src/main/java/com/tkisor/nekojs/core/api/CoreManagedApiBootstrap.java:336-372`): reflection over the 7 portable facades (ID/Platform/Text/JsonIO/NBT/Registry/Performance), their data types, and common-side `ScriptEventRegistrationEvent` | The contract deliberately carries **no events** (`NormativeApiContract(..., symbols, List.of(), List.of())`, line 365-367; pinned by `CoreContractReflectionTest` "events 由 EventContractReflector 运行时反射，契约不携带"). The event families are MC-facing classes in the preprocessed version tree (`src/main/java/com/tkisor/nekojs/bindings/event/ServerEvents.java` imports `net.neoforged.*`/`net.minecraft.*`); `common` must stay free of MC/loader deps (ADR-0007), so they can never enter this reflection input. |
| probe `probe-events.expected.d.ts`, `legacy-events/-bindings.expected.d.ts`, `dynamic-*.expected.d.ts` | `ProbeEventsSurfaceGoldenTest`, `LegacyProbeCompatibilityTest`, `DynamicRegistryEventsDeclarationGoldenTest` | Test-local synthetic surfaces only: `ProbeEvents.GROUP` (4 probe-only buses, ticket 14 froze it as probe-extension-only), `LegacyProbeCompatibilityTest$SampleEvent`, `DynamicRegistryEvents.GROUP` | The rendering chain itself is generic (`NekoScriptCatalog.events` → `EventDeclarationGenerator`), but **no golden test ever fed it the real game event families** — they are unreachable from `common` tests for the same ADR-0007 reason. |
| `common/src/test/resources/nekojs/platform-gates/declaration-parity.txt` | `ManagedDeclarationCoverageGateTest` | Same portable-core contract via `JsApiSurfaceResolver` + `ManagedApiDeclarationGenerator` (test javadoc, lines 36-41; header of the fixture: `globals=ID,JsonIO,NBT,Performance,Platform,Registry,Text`, `members=141`) | Identical input boundary: the parity baseline compares contract symbols to the TS render of those same symbols. Event groups are not contract symbols. |

The one chain that does see the real families — `EventContractReflector.extractEvents(IPluginRuntime.eventGroups())`
(`common/src/main/java/com/tkisor/nekojs/core/api/EventContractReflector.java:36`) — is consumed only by
`NekoPluginRuntime.installManagedCallbackSchemas` → `ManagedCallbackSchemaRegistry.installContractEvents`
(`common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginRuntime.java:161`) for callback-schema
validation. It never feeds a declaration or manifest golden.

Rules out:
- **(b) missing metadata** — no. Ticket 24's `Ticket24GameplayEventCatalogTest` (shared tree) already
  derives name/payload/side/dispatch/cancel from the real registration entry; the catalog and TS
  renderer consume the same `EventGroup` instances without extra metadata.
- **(c) stale goldens** — no, and this is now proven, not asserted: this branch re-ran the explicit
  regeneration workflows and reproduced every existing common golden byte-identically
  (`regeneration-noop-proof.txt`; the three `.diff` files for those artifacts are empty by design).

**Conclusion:** the gap was a wiring gap — no declaration golden derivation anywhere in the repo
consumed the real, node-reachable registration surface for these domains. The bounded canonical fix
is to wire the existing generators to that surface at the level where it is reachable (per node,
inside the existing `platformGateTest` gate JVM), not to redesign the common-side generators.

## 2. Canonical change on this branch (generator coverage fix, no hand-edited goldens)

1. **`src/test/java/com/tkisor/nekojs/platform/EventRegistrationSurfaces.java` (new)** — the
   ticket-33 registration-driving logic (contributor discovery: `@RegisterNekoJSPlugin` classpath
   scan ∪ fabric `BUILTIN_PLUGINS`; `registerEvents`/`registerClientEvents` invocation into fresh
   registries; node/loader identity) extracted from `EventSurfaceDomainGateTest` so the golden test
   drives the exact same input instead of a drifting copy.
2. **`EventSurfaceDomainGateTest`** — refactored to delegate to the helper. Pure extraction: same
   discovery input, same per-plugin fresh registries, same merge semantics, same failure strings.
   The only intentional drops are the now-dead private `corePluginClass` helper and the
   always-false `isMissingHook` branch (verified: its only call site ran after `error != null`,
   where the map was never empty).
3. **`src/test/java/com/tkisor/nekojs/platform/DeclaredEventSurfaceGoldenTest.java` (new, tagged
   `platform-gate`)** — freezes the TS declarations of exactly the three ticket domains:
   - ticket 23 recipe/data: `ServerEvents`, `RecipeViewerEvents`
   - ticket 24 gameplay: `BlockEvents`, `ItemEvents`, `LevelEvents`, `PlayerEvents`,
     `CommandEvents`, `CapabilityEvents`, `GoalEvents`, `EntityEvents`
   - ticket 27 client GUI/render: `ClientEvents`
   Derivation = real node registration (`EventRegistrationSurfaces`) → `NekoScriptCatalog.events`
   → `EventDeclarationGenerator` (the production probe chain). Cross-node presence is **reused from
   the ticket-33 baseline** `event-surface-domains.txt` (a group frozen for a node = its `present`
   row; `not-verified` groups like fabric `CapabilityEvents` are out of that node's golden). A
   loader-aware `Platform` stub (mirroring `NeoForgePlatform`/`FabricPlatform.defaultScanPackages`)
   makes the import filtering deterministic regardless of JVM init order. Goldens are read-only in
   normal runs; per-side member-count and namespace-presence assertions prevent empty-shell goldens.
4. **`src/test/resources/golden/events-declared/<node>.<side>-events.d.ts` (new goldens, 15
   files)** — one standalone `d.ts` per node (5) per script side (startup/server/client), produced
   by the explicit regeneration switch — never hand-written.
5. **Gradle wiring** (`buildSrc/.../nekojs.neoforge-node.gradle.kts`, `nekojs.fabric-node.gradle.kts`,
   `platformGateTest` blocks): pass through `-Dnekojs.golden.regenerate` (default `"false"`, same
   convention as `:common:test`) and inject `nekojs.test.sharedTree` = shared tree root for
   write-back (node project dirs move with the active node).
6. **`docs/architecture-refactor/baseline/2026-09-12-managed-surface/REGENERATE.md`** — the new
   golden family is registered in the canonical baseline table (required by that document: it is
   the single registry of goldens and their regeneration workflow).

## 3. Artifact-by-artifact old/new diff summary

| Artifact | Old → New | Counts |
|---|---|---|
| `api-manifest-core.json` | **unchanged** (no-op proof) | 0 lines; regenerated via `ApiManifestGoldenTest` + explicit switch |
| probe `*.expected.d.ts` (+ `probe-ts/generated/index.d.ts`, `legacy-tree/**`) | **unchanged** (no-op proof) | 0 content lines; `git diff --numstat` empty after `:common:regenerateGoldens` (only LF/CRLF re-touch, reverted) |
| `declaration-parity.txt` | **unchanged** (no-op proof) | gate green against frozen fixture; no regen switch exists for it, none needed |
| `golden/events-declared/*.d.ts` | **new** (`events-declared-goldens.diff`, 6394 insertions / 15 files) | per node — startup/server/client declared members: 1.21.1 156/120/34; 26.1.2 157/121/34; 26.2.0 157/121/34; 26.1.2-fabric 90/77/12; 26.2.0-fabric 90/77/12 |
| wiring + tests (`wiring-and-tests.diff`, 871 diff lines) | 2 convention-plugin blocks (+6 lines each), gate refactor (-105/+30), 2 new test classes (~330 + ~150 lines) | see diff |

Gap-symbol spot checks in the new goldens (the exact greps that returned 0 hits in the ticket
evidence): `ServerEvents.recipes/generateData/lootTables/tags` present on all three NeoForge nodes
(server + startup files; fabric honestly shows only its reduced surface — no `generateData`/
`lootTables`/`tags`, matching the ticket-33 baseline rows); `ClientEvents.hudRender/worldRender/
screenRender/hud` present in the three NeoForge client files (absent on fabric — the render surface
is not ported there, exactly what ticket 27's capability matrix records); all eight gameplay
families render as namespaces on every node that registers them.

## 4. Blast radius

- `EventSurfaceDomainGateTest` refactor: behavior-preserving extraction; the gate re-ran green on
  all five nodes as part of `platformGateTest` (below). No baseline file it owns was touched.
- No unrelated domains entered any golden: the golden scope is the 11 groups of tickets 23/24/27
  only (`NetworkEvents`, `KeyBindEvents`, `RegistryEvents`, `DynamicRegistryEvents`, `ScriptEvents`,
  `ProbeEvents` are excluded). The shared workflow does not regenerate anything else — the common
  regeneration tasks were run only to produce the no-op proofs and their output was reverted.
- Normal `test` tasks are untouched (the new test is `platform-gate`-tagged and runs only in the
  dedicated gate JVM, preserving the ticket-33 JVM-isolation rationale).
- No engine/`common` source changed. No public contract changed. No existing golden changed.

## 5. Regeneration commands (the workflow to re-run after any event-surface change)

```bash
# new goldens (per node, explicit switch; review diff per REGENERATE.md §3):
./gradlew.bat :26.1.2:platformGateTest -Dnekojs.golden.regenerate=true --console=plain
# same for :1.21.1, :26.2.0, :26.1.2-fabric, :26.2.0-fabric
# existing common goldens (no-op unless the portable-core contract changed):
./gradlew.bat :common:regenerateGoldens --console=plain
./gradlew.bat :common:test --tests "com.tkisor.nekojs.core.api.ApiManifestGoldenTest" -Dnekojs.golden.regenerate=true --console=plain
```

## 6. Verification results

| Check | Result |
|---|---|
| `:<node>:platformGateTest` read-only, all 5 nodes (includes refactored `EventSurfaceDomainGateTest`, `PlatformSpecContractGateTest`, new `DeclaredEventSurfaceGoldenTest`) | **pass** ×5 (BUILD SUCCESSFUL each) |
| Golden regeneration, all 5 nodes (explicit switch) | **pass** ×5; read-only re-run afterwards **pass** ×5 |
| `:common:test --tests ManagedDeclarationCoverageGateTest` | **pass** |
| `:common:regenerateGoldens` | **pass**; zero content diff (no-op proof) |
| `:common:test --tests ApiManifestGoldenTest -Dnekojs.golden.regenerate=true` | golden rewritten identical (no-op); task "fails" on the pre-existing `regenerateSwitchIsOffForNonTrueValues` guard, which by design fails whenever the switch is on — quirk of the documented command, not introduced here |
| `:common:check` | **pass** (BUILD SUCCESSFUL in 1m 2s; includes isolation checks and the golden gates in read-only mode) |
| `npm run test:probe-types` | **failed with pre-existing errors** (14 × TS7026 in `jsx-primitive-props.tsx`, all about missing `JSX.IntrinsicElements`; last green runs predate the ticket-40 JSX fixture). Zero `common/` files changed on this branch, so the break is inherited from `mult@973defbc`. Owner: JSX ticket chain (40/42-47) / managed-surface 09 — proposed follow-up below. |
| `npm install` | pass (fresh `node_modules`, tsc 5.8.3) |

## 7. What the maintainer is asked to approve

1. The **root-cause statement** (§1): the 0-hit evidence is an input-set property of the common
   declaration derivations (portable-core only, ADR-0007); regenerating them is a proven no-op.
2. The **canonical coverage fix** (§2): a new per-node declaration golden family
   (`golden/events-declared/`) derived from real registration through the production probe chain,
   guarded by `DeclaredEventSurfaceGoldenTest` inside the existing `platformGateTest` gate, with an
   explicit regeneration switch — closing ticket 23 AC1 / 24 AC8 / 27 AC7's declaration gap at the
   node level where it is architecturally reachable.
3. The **new golden content** (`events-declared-goldens.diff`): 15 files, 6394 lines, incl. the
   honest per-node differences (fabric's reduced `ServerEvents`/`ClientEvents` surface).
4. The **behavior-preserving extraction** in `EventSurfaceDomainGateTest` + the two-line-per-plugin
   Gradle wiring.

## 8. Out of scope / proposals (not implemented)

- **Python per-node `.pyi` goldens**: the same `EventCatalogEntry` input drives
  `PythonEventRenderer`; adding per-node `.pyi` goldens is mechanical but doubles the frozen
  surface. Proposed as follow-up under the managed-surface owner if TS goldens are accepted.
- **`npm run test:probe-types` pre-existing JSX failure**: needs a `JSX.IntrinsicElements`
  declaration in the probe TS output (or a fixture-scoped tsconfig). Recorded here; owner should be
  agreed (JSX tickets vs 09) — not fixed in this branch to keep the review single-topic.
- **Common-side event declarations** (making `api-manifest-core.json`/parity cover event families):
  architecturally impossible without breaking ADR-0007 (`common` must stay MC/loader-free). Not
  proposed; the node-level golden is the correct home.

## Files in this pack

- `REPORT.md` — this document
- `events-declared-goldens.diff` — the 15 new goldens (6394 insertions)
- `wiring-and-tests.diff` — buildSrc wiring, gate refactor, two new test classes
- `api-manifest-core.diff`, `probe-expected-dts.diff`, `declaration-parity.diff` — empty by design (no-op proofs)
- `regeneration-noop-proof.txt` — captured commands and outputs for the no-op proofs
