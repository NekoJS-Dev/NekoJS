# Cookbook: add a new event (新增事件)

> Status: **DRAFT — UNVALIDATED**. Prepared for ticket 36 (`docs/architecture-refactor/implementation-tickets/36-release-maintainer-trials.md`, AC12) maintainer trials.
> Every path/symbol below was read at base commit `c8173622` (branch `ticket-36-trial-prep-a`). The real trial must confirm each step and correct this document.

## Purpose

Add a new event that pack scripts subscribe to as `EventGroupName.eventName(event => { ... })` — either a new bus member in an existing builtin event group (the common case, e.g. a new `ServerEvents` member) or a new event group. This cookbook traces the entry, the platform wiring, the catalog/declaration derivation, the cross-node coverage fixture, and the tests that must pass. It covers the three event tiers only where they differ (builtin group; ScriptEvents dynamic declaration is out of scope — see the ticket 14 baseline).

## Public entry points (as they exist at HEAD)

| Entry | File | Symbols |
|---|---|---|
| Builtin group declaration (NeoForge face) | `src/main/java/com/tkisor/nekojs/bindings/event/ServerEvents.java` (whole-file `//? if neoforge` guard) | `EventGroup GROUP = EventGroup.of("ServerEvents")`; members like `EventBusJS<ServerTickEvent.Pre, Void> TICK_PRE = GROUP.server("tickPre", ServerTickEvent.Pre.class)`; dispatch variant `GROUP.server("tags", TagEventJS.class, TAG_REGISTRY_KEY)`; platform wiring `EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS).bind(TICK_PRE)...` |
| Event group API (engine, loader-free) | `common/src/main/java/com/tkisor/nekojs/api/event/EventGroup.java` | `EventGroup.of(String)`, `server/client/startup(name, type)`, dispatch overloads with `DispatchKey`, `freeze()` |
| Group registration API | `common/src/main/java/com/tkisor/nekojs/api/event/EventGroupRegistry.java` | `registry.register(GROUP)`; same-name groups from different plugins merge (see `FabricCorePlugin.registerEvents` comments) |
| NeoForge registration hook | `src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java` | `registerEvents(EventGroupRegistry)` / `registerClientEvents(EventGroupRegistry)` (`@RegisterNekoJSPlugin(priority = NekoJSPlugin.CORE_PRIORITY)`) |
| Fabric registration hook | `src/fabric/java/com/tkisor/nekojs/fabric/FabricCorePlugin.java` | `registerEvents` / `registerClientEvents`; groups come from `src/fabric/java/com/tkisor/nekojs/fabric/event/Fabric*EventBindings.java` with neutral payloads in `com.tkisor.nekojs.wrapper.event.*` |
| Base facade hooks (plugin authors) | `common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` | default `registerEvents(EventGroupRegistry)` / `registerClientEvents(EventGroupRegistry)` — facade projections of `EventsPoint` / `ClientEventsPoint` |
| Platform bridge (NeoForge) | `src/main/java/com/tkisor/nekojs/api/event/EventBusForgeBridge.java` | `EventBusForgeBridge.create(IEventBus)`, `.bind(bus)` / `.bindTransformed(...)` |

Naming conventions for script authors: group member names are lower camel case (`tickPre`), the group is the namespace, selector/filter arguments come before the callback, and event-specific operations live on the event object (see `ServerEvents.java` and its Javadocs for live examples).

## Owners and facts sources

| Artifact | Facts source / owner |
|---|---|
| Members of a builtin domain group | The group interface file itself (`ServerEvents.java` etc.) — single facts source for names, sides, dispatch keys, payload types |
| Catalog entries (script-visible event list) | `NekoScriptCatalog.events` (`common/src/main/java/com/tkisor/nekojs/api/catalog/`) — derived; TS declarations via `common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/EventDeclarationGenerator.java`, Python via the parallel renderer. No second registration. |
| Cross-node coverage ledger | `src/test/resources/nekojs/platform-gates/event-surface-domains.txt` — read-only baseline; header names the owner as "Managed Surface/Probe owner (coverage ledger + contract fixture); build convention owner (wiring)". Event-surface changes update the fixture only after the implementation changes, with a record in the ticket 33 report. |
| Gate test | `src/test/java/com/tkisor/nekojs/platform/EventSurfaceDomainGateTest.java` (tagged `platform-gate`, run by the per-node `platformGateTest` task) |
| Baseline mechanics (ticket 14) | `docs/architecture-refactor/baseline/2026-09-15-event-surface/` (`REPORT.md`, `MIGRATION.md`, `examples/`) |

## Step-by-step (example: add `ServerEvents.example`)

1. **Declare the member** in `src/main/java/com/tkisor/nekojs/bindings/event/ServerEvents.java`:
   - Non-dispatch: `EventBusJS<PayloadType, Void> EXAMPLE = GROUP.server("example", PayloadType.class);`
   - Dispatch (targeted, like `TAGS`): define a `DispatchKey<PayloadType, KeyType>` constant (see `TAG_REGISTRY_KEY`) and use `GROUP.server("example", PayloadType.class, EXAMPLE_KEY)`.
   - Choose the side with `server(...)` / `client(...)` / `startup(...)`; `client` members are registered through `registerClientEvents`.
2. **Pick the payload type**. Prefer a platform-neutral wrapper under `com.tkisor.nekojs.wrapper.event.*` (see `LootTableEventJS`, `TagEventJS`) so both loaders can post it; a raw platform type is acceptable only when both eras share the type (the `ServerTickEvent` comment in `ServerEvents.java` documents that case). Put callback data and event-specific operations on the event object.
3. **Wire the platform side** — declaration alone never fires the bus:
   - NeoForge: add `.bind(EXAMPLE)` to `ServerEvents.FORGE_BRIDGE` for a plain platform event, or post from the domain owner adapter at its legal commit point when the domain owns timing (precedent: `VillagerTradeDomainOwner` posts `TRADE_DECLARATION` / `TRADE_RELOAD`, see the Javadocs on those members).
   - Fabric: implement the callback in the matching `src/fabric/java/com/tkisor/nekojs/fabric/event/Fabric*EventBindings.java` group (same group name + same bus name merge across plugins), or explicitly record the Fabric capability gap — no silent no-op.
4. **Update the coverage fixture** `src/test/resources/nekojs/platform-gates/event-surface-domains.txt`: add the bus to the `buses=...` list for exactly the nodes that register it. Source of truth for the list is the gate's own JSON output (`versions/<node>/build/nekojs-gates/event-surface-<node>.json` after `:<node>:platformGateTest`). A new member without a fixture row fails the gate as `member-drift` ("运行时有该 bus 但基线未记录"). Leave unported nodes as `not-verified` — the vocabulary is only `present` / `not-verified`.
5. **Examples / docs**: if the member needs a script example, follow `examples/` files in the ticket 14 baseline and the wiki event docs; the probe/TS/Python declarations regenerate from the catalog at runtime — nothing to hand-edit.

## Contract / golden regeneration

- `src/test/resources/nekojs/platform-gates/event-surface-domains.txt` — hand-updated from the gate JSON (step 4); record the change in the ticket 33 report (`docs/architecture-refactor/baseline/2026-09-19-ci-processor-gate/REPORT.md`), per the fixture header.
- Builtin event groups are **catalog-observed, not contract-frozen**: adding a member does **not** touch `common/src/test/resources/nekojs/golden/api-manifest-core.json` (the group lives in the version tree, not `common` `api.*`; see ticket 14 `REPORT.md` §3 tier table). `ApiManifestGoldenTest` should stay green unchanged — if it moves, something is wrong.
- Probe goldens (`common/src/test/resources/nekojs/probe/probe-events.expected.d.ts` etc.) only move if you touch `ProbeEvents` or shared declaration generators; regeneration entry is `./gradlew :common:regenerateGoldens`.

## Tests to run (real task names)

| Check | Command | What it proves here |
|---|---|---|
| Node gate | `./gradlew :<node>:platformGateTest` (also wired into `:<node>:check`) | Registered members match the fixture per node |
| Ownership / no duplicate buses | root-tree `EventSurfaceOwnershipTest` (`src/test/java/com/tkisor/nekojs/bindings/event/EventSurfaceOwnershipTest.java`) via `:<active-node>:test` | Every bus once, no domain copy, catalog `(group, name)` unique |
| Catalog snapshot (gameplay domains) | `Ticket24GameplayEventCatalogTest` (root tree) and `Ticket24FabricGameplayEventCatalogTest` (fabric nodes) | Catalog entries for gameplay groups reflect the real registration path |
| Catalog semantics | `common/src/test/java/com/tkisor/nekojs/api/catalog/NekoScriptCatalogEventsTest.java` via `:common:test` (part of `:common:check`) | Catalog derivation rules unchanged |
| Engine isolation | `./gradlew :common:check` + `guardLint` | No MC/loader leak into `common` (should be untouched by this change) |
| Runtime proof when timing matters | `:<node>:runGameTestServer` and/or Minecraft MCP live client (see `docs/agents/minecraft-mcp.md`) | The bus actually fires with the payload shape scripts see |

On Windows use `gradlew.bat` / `./gradlew.bat`.

## Affected nodes matrix

- New `ServerEvents` member with neutral payload: NeoForge nodes (`1.21.1`, `26.1.2`, `26.2.0`) get it via the version tree; fabric nodes (`26.1.2-fabric`, `26.2.0-fabric`) only after the fabric twin bridge lands the same bus name — until then the fixture records fabric as `not-verified` for that bus/domain, and script behavior must state the capability explicitly.
- Payloads that differ per era may need `//? if >=26 { ... //?}` guards or stonecutter replacements (`stonecutter.gradle.kts` `!mc_ids` group) — run `guardLint` after touching guards.
- A change to shared group files covers the full supported matrix; a single-node pass is not evidence for the others.

## When no runtime/bootstrap change is needed

Adding a member to an already-registered group (or a new group registered from an existing plugin's `registerEvents`/`registerClientEvents`) touches **no** runtime/bootstrap code: `EventsPoint`/`ClientEventsPoint` collection, group merge, `freeze()` timing, generation/reload semantics and `NekoRuntimeRoot` ownership are all unchanged. Ordinary `/nekojs reload` does not re-bootstrap Plugin Runtime. The diff should be: group file + platform wiring + gate fixture (+ example/docs). If the trial finds itself editing `NekoPluginBootstrap`, `NekoRuntimeRoot`, or registry internals, that is a failure record per ticket 36 AC10, not a step.

## Trial checklist (maintainer fills during the real trial — AC1)

| # | Task | Entry used | Result (pass/fail + evidence) | Problems hit |
|---|---|---|---|---|
| 1 | Locate the group file and conventions without reading internals |  |  |  |
| 2 | Declare the member (side, name, payload, dispatch key if any) |  |  |  |
| 3 | Wire NeoForge firing (`FORGE_BRIDGE` bind or domain-owner post) |  |  |  |
| 4 | Wire or explicitly gap Fabric |  |  |  |
| 5 | Update `event-surface-domains.txt` from gate JSON; ticket 33 report record |  |  |  |
| 6 | Run `:<node>:platformGateTest` on affected nodes |  |  |  |
| 7 | Run ownership/catalog tests |  |  |  |
| 8 | In-game or GameTest evidence that the bus fires |  |  |  |
| 9 | Confirm no runtime/bootstrap diff |  |  |  |
| 10 | Subscribing from a script (`ServerEvents.example(event => {})`) with only public declarations |  |  |  |

Anything that required guessing an owner, editing generated output by hand, or reading internal implementation to complete: record verbatim under Problems — that is the trial's real output.
