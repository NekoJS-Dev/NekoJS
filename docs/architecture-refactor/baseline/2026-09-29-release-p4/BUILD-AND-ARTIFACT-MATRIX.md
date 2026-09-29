# P4 build/artifact matrix (AC1–AC3)

> Ticket 34 evidence pack, 2026-09-29. Worktree `../NekoJS-mult-t34`, branch `ticket-34-release-p4`,
> base `mult@28283cc4`. All commands listed under `command-output/` were executed in this worktree on
> this base; every other result cited here is quoted from the owning ticket's archived evidence with
> its provenance. Node tiers follow ticket 04's policy wording: primary `26.1.2`, secondary `26.2.0`,
> experimental `1.21.1`, `26.1.2-fabric`, `26.2.0-fabric`.

## 1. Node tier matrix

| node | tier | build | test suite (fresh, this tree) | artifact + metadata | source-trace/spec gates |
|---|---|---|---|---|---|
| `26.1.2` | primary (NeoForge) | `:26.1.2:build` GREEN (run 01) | 490 tests / 0 fail / 58 skip (run 10) | `nekojs-neoforge-26.1.2-1.1.0-preview3.jar` (run 11: `cd893bf56d4eff8e`, 3028337 B; entries run 07) | source-roots + node-report GREEN (runs 04/05) |
| `26.2.0` | secondary (NeoForge) | `:26.2.0:build` GREEN (run 01) | 490 tests / 0 fail / 58 skip (run 10) | `nekojs-neoforge-26.2.0-1.1.0-preview3.jar` (run 11: `edad1edd45a9a47c`, 3028357 B; entries run 07) | source-roots + node-report GREEN (runs 04/05) |
| `1.21.1` | experimental (NeoForge, legacy mixins) | `:1.21.1:build` GREEN (run 01) | 341 tests / 0 fail / 14 skip (run 10) | `nekojs-neoforge-1.21.1-1.1.0-preview3.jar` (run 11: `e79dc424d862d243`, 2854727 B; entries run 07 — legacy `nekojs.mixins.json`, no modern dynamic/interface-injection set) | source-roots + node-report GREEN (runs 04/05) |
| `26.1.2-fabric` | experimental (Fabric) | `:26.1.2-fabric:build` GREEN (run 01) | 269 tests / 0 fail / 25 skip (run 10) | `nekojs-fabric-26.1.2-1.1.0-preview3.jar` (run 11: `93052b1684cadaef`, 17920849 B; full CI entry/metadata check run 06: required entries present, no NeoForge leak, `fabric-api >= 0.155.2+26.1.2`) | source-roots + node-report GREEN (runs 04/05) |
| `26.2.0-fabric` | experimental (Fabric) | `:26.2.0-fabric:build` GREEN (run 01) | 269 tests / 0 fail / 25 skip (run 10) | `nekojs-fabric-26.2-1.1.0-preview3.jar` (run 11: `8013883243743489`, 17920848 B; full CI entry/metadata check run 06: required entries present, no NeoForge leak, `fabric-api >= 0.159.0+26.2`) | source-roots + node-report GREEN (runs 04/05) |

Shared layers (root project):

| layer | check | result |
|---|---|---|
| `common` | `:common:check` incl. `checkCommonIsolation` | GREEN (run 03, BUILD SUCCESSFUL; suite 1963 / 0 / 4 skip, run 10) |
| `common-api-processor` | `:common-api-processor:test` | GREEN (run 03; 13 / 0 / 0, run 10) |
| guardLint | `guardLint` | GREEN (run 01): 341 guard blocks, 487 files, 0 over-limit exemptions, 0 warnings |
| NBT smoke | `:1.21.1/:26.1.2/:26.2.0:nbtSmokeTest` | GREEN (run 01; fabric/forge branch scripts do not register the task, per CI comment) |
| addon isolation | `verifyExternalAddonIsolation` | GREEN (run 01): 5 production jars carry no fixture content; fixture jar metadata complete |
| CI gates script | `tools/nekojs-ci-gates.py` `source-roots` ×5 then `all --out build/nekojs-gates-report.json` | GREEN (runs 04/05): ci-subset-consistency / fabric-processor-deferral / declaration-parity / node-report = pass, 0 failure rows |
| probe declarations | `npm run test:probe-types` | **RED — pre-existing** (run 02): 14 errors, all in `common/src/test/probe-ts/jsx-primitive-props.tsx` (TS2307/TS2875/TS7026; `nekojs/jsx-runtime` module resolution → missing `JSX.IntrinsicElements`). Recorded unchanged from the 2026-09-29 golden-decl-prep report §6; owner JSX chain/09; see FAILURES-LEDGER F1. |
| GameTest smoke (CI `gametest-smoke` equivalent) | `:26.1.2:runGameTestServer` | Run 12 (this tree). Result recorded in the transcript; the CI assertion is the `All N required tests passed` marker in `versions/26.1.2/run/logs/`. |
| Fabric dev runtime smoke (CI `fabric-runtime-smoke` job) | `:<node>:runServer` + CI fixture | Not re-run in this pack (checkout dev-run; AC6 forbids counting dev runs as P4 evidence). Covered by ticket 08's fabric real-discovery runServer (see RUNTIME-SMOKE-LEDGER) and by CI itself. |

## 2. Five-node suite provenance chain

- 2026-09-28 merged-tree five-node fresh suites (ticket 24 closure): 26.1.2-fabric 251/0/25, 26.2.0-fabric 251/0/25, 26.2.0 448/0/58 — `baseline/2026-09-22-gameplay-event-surface/command-output/09-node-suites-fresh.txt`.
- 2026-09-29 merged-tree five-node suites (ticket 24 fix round + ticket 27 capability round, cited by tickets 24/26 closures): 470 / 470 / 324 / 254 / 254, zero failures.
- This pack's own fresh execution on `mult@28283cc4` (run 10): 490 / 490 / 341 / 269 / 269, zero failures — counts grew with the 26/29 follow-ups merged 2026-09-29; no regression.

## 3. AC1–AC3 verdicts

- **AC1 (primary node)**: compile/check/artifact/metadata/contract/data-fixture/runtime-smoke inputs are green on `26.1.2` with two exceptions that are recorded, not massaged: (a) `npm run test:probe-types` is red (F1, pre-existing, CI-blocking until fixed); (b) the perf gate verdict is pending ticket 35 (PERF-GATE-STATUS). The maintainer-trial inputs (ticket 36) are prepared separately (`evidence/2026-09-29-ticket36-trial-prep/`) and are not consumed here. Any required gap found in this pack blocks release — see FAILURES-LEDGER for the consolidated blocking list.
- **AC2 (secondary NeoForge node)**: `26.2.0` builds, verifies artifact + metadata, and carries traceable public contracts (source-roots/node-report green; declaration goldens per node). Version differences are carried in the capability matrix (e.g. Assets `//? if >=26` guard — 1.21.1 lacks the typed binding; PostEffects JSON shape `>=26` branch) and are restated in CAPABILITY-MATRIX.
- **AC3 (experimental nodes)**: all three repeat-build green with artifact verification and declared-capability smokes (per-node catalog tests green in run 10; capability rows pinned by `golden/query/capability-matrix-*.txt` and ticket 27's `CAPABILITY-MATRIX.md`). **No full parity is claimed for them**; fabric capability differences are explicit (render/GUI seams unavailable on fabric; 1.21.1 legacy mixin path), and not-verified domains remain recorded as such (CAPABILITY-MATRIX), never silently upgraded.

## 4. No-parity-fabrication statement

Capability statements in this pack use only supported / partial / unavailable for actual capability,
and not-verified / deferred as separate evidence dimensions that block the corresponding domain's
closure (AC4 wording). No experimental node is presented as parity-complete; no dev-run is presented
as a real-machine P4 smoke (dev-run vs real-run is labeled per row in RUNTIME-SMOKE-LEDGER).
