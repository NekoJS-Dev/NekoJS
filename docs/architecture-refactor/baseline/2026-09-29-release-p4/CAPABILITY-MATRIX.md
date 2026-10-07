# P4 five-node capability matrix (AC4)

> Ticket 34 evidence pack, 2026-09-29. This is the cross-domain aggregation view required by AC4:
> every cell states **actual capability** with supported / partial / unavailable; **not verified**
> and **deferred** are separate evidence dimensions (marked `nv:` / `def:` inside cells) that block
> the corresponding domain's full closure — they are never rewritten into a capability value.
> Binding-level rows live in the owning goldens: `src/test/resources/golden/query/capability-matrix-{neoforge,fabric}.txt`
> (ticket 25, probe-derived) and `baseline/2026-09-27-client-gui-render/CAPABILITY-MATRIX.md`
> (ticket 27 domain matrix). This table consolidates; it does not replace them.
> No silent parity: fabric gaps are explicit; experimental nodes are not recorded as primary.

## 1. Domain × node matrix

Legend: S = supported, P = partial, U = unavailable (explicit), nv = not verified (blocks domain),
def = deferred (owner-recorded window). Evidence pointer per row in §2.

| domain (owning ticket) | 26.1.2 (primary) | 26.2.0 (secondary) | 1.21.1 (exp) | 26.1.2-fabric (exp) | 26.2.0-fabric (exp) |
|---|---|---|---|---|---|
| runtime lifecycle, reload, threads, watchdog (05/06/07) | S | S | S | S (dev-run + CI smoke; real-machine client leg nv) | S (same) |
| plugin discovery, Point/Hook/Handle, builtin list (08) | S (real external addon runServer) | S (build/test; real addon run not run here, nv) | S (build/test; real addon run not run here, nv) | S (real external addon runServer) | S (artifact+CI smoke; real addon run not run here, nv) |
| language pipeline JS/CJS/ESM (11) | S | S | S | S | S |
| TS/JSX/TSX compile + source map (12) | S | S | S | S (raw-root compile) | S (raw-root compile) |
| Python transpile + diagnostics (13) | S | S | S | S | S |
| bindings, module resolution, advanced Java access (06/09) | S | S | S | S | S |
| managed API, legacy surface, declared event names (09/14) | S (TS golden) | S (TS golden) | S (TS golden) | S (TS golden, reduced surface) | S (TS golden, reduced surface) |
| event bus + gameplay families (14/24) | S (real runServer + realmachine) | S (suite) | S (suite; whole-file overrides) | P (catalog-pinned subset; capability/goal families absent, explicit) | P (same) |
| recipe/datagen/loot/tags/viewer (23) | S (runServer datagen smoke) | S (suite) | S (suite) | U (datagen publish absent, explicit absence smoke) | U (same) |
| Item/Block modification snapshot (39) | S (fixture + suite; real GameTest collection blocked by F12, nv for real-machine leg) | S (suite) | S (suite) | P | P |
| Villager Trades (22) | S | S | S | U (explicit unavailable, no silent no-op) | U (same) |
| Dynamic Registry runtime (16/21) | P (activation gate + single-node surgery real; cross-node sync nv — adapter/wire now registered, multiplayer behavior not yet proved) | P (same state, suite-level) | P | P | P |
| startup registry, typed builders (15) | S (startup-builders golden) | S | S (own golden variant) | S | S |
| client script, input/HUD (26) | S (realmachine keybind/HUD/consumeClick/reload) | S (suite) | S (suite; real leg def 34) | U (render/input seams absent, explicit) | U (same) |
| GUI/render adapters, error dashboard (27) | S (realmachine error panel + VS Code open) | S (suite) | S (suite) | U (explicit, text degradation) | U (same) |
| PostEffects (28) | S (node tests; native/first-frame nv) | S (26.2 official resource inversion/clear/F3+T and declared existing-resource-id blur observed; first-frame/all-id parity nv) | P (resources-only runtime chain; inline declared chain unavailable; node tests) | U (plugin, binding, payload and bus absent; two-node assertion 2026-10-07) | U (same) |
| network, ClientData/PData, pack sync (17/18/19) | S (fixtures + wire-frozen gate) | S | S | S (wire contract; WORLD pack status recorded) | S (same) |
| admin commands, permissions, CommandEvents (20/24) | S (real RCON full tree + watchdog chain) | S (suite; live dispatch nv) | S (suite; live dispatch nv) | S (suite; live dispatch nv) | S (suite; live dispatch nv) |
| sandbox/config/pack trust/cache/persistence (03/06/11/19) | S | S | S | S | S |
| diagnostics, telemetry, workspace, user report (30) | S (realmachine record survival + panel + IDE open) | S (suite) | S (suite) | P (text degradation; seam supported) | P (same) |
| DataMap query (25) | S (probe) | S (probe) | S (probe) | U (explicit) | U (same) |
| EntitySelectors (25) | S (probe) | S (probe) | S (probe) | S (SERVER/TEST registered; runtime query not exercised, nv) | S (same) |
| custom event declaration (14) | S | S | S | S (reduced groups; exclusions per golden) | S (same) |
| native bridge + Probe events (14/09) | S | S | S | S | S |
| Assets/Lang generation + readback (29) | S (fixture + readback tests; real client reload readback def 34) | S (Assets `>=26`) | U (typed `Assets` binding absent — `//? if >=26` guard; explicit) | P (per-node pins) | P |
| global/shared write-set in candidate tx (10) | S (common-level; real in-game stop/world-leave leg nv, N1) | S (same) | S (same) | S (same) | S (same) |

## 2. Row evidence and blockers

- **Gameplay families / commands real legs**: ticket 24 `runserver-26.1.2` + D4/D5 fix smokes and
  the 2026-09-28 maintainer real-machine session (block cancel D2, entity join); ticket 20 RCON
  full-tree + watchdog chain sessions (`evidence/2026-09-29-ticket20-{smoke,watchdog-smoke}/`).
- **Fabric partial rows**: pinned by `Ticket24FabricGameplayEventCatalogTest` (green both fabric
  nodes, runs 01/10) and `capability-matrix-fabric.txt`; the absence is a designed explicit
  unavailable/partial, not an omission.
- **Dynamic Registry `P`**: ticket 21 closure — 31 JVM dual-adapter batch-transaction cases green,
  single-node activation + real `DynamicRegistries` surgery observed via script + `/nekojs registry`
  (`baseline/2026-09-22-registry-dynamic-sync/command-output/09-...-activation.txt`); cross-node
  PREPARE/ack/STATE_SYNC and client surgery **not verified**. Adapter and transport registration
  now exist; this is no longer an "unwired" source claim. The frozen six-call/five-type wire
  contract is not changed or expanded by this closeout.
- **Assets/Lang 1.21.1 `U`**: recorded in ticket 29 evidence (`Assets` binding guarded `//? if >=26`);
  per-node capability pins added 2026-09-29 (`evidence/2026-09-29-ticket29-assets-fixture/`).
- **`def 34` windows** (owner-deferred by the closed tickets' closure records, re-routed here):
  26 fabric/1.21.1 real-machine legs; 27 render output/multiplayer smoke; 28 real client render;
  29 real client resource reload readback + dedicated-server client-only filter smoke; 23 JEI
  conditional viewer + plugin-hook real-machine load; 20 unauthorized-rejection UX confirmation.
  They are **open against this pack** (see RUNTIME-SMOKE-LEDGER §3 and FAILURES-LEDGER).

## 3. Vocabulary compliance

Every cell above expresses actual capability only (S/P/U). `nv` / `def` annotations are evidence
dimensions: an `nv` blocks that domain's "fully verified" status for this release candidate, a `def`
is an owner-recorded arrangement that likewise leaves the domain not fully verified until the
window closes. No cell was upgraded to make a row greener; no experimental node is presented as
primary parity.
