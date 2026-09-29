# P4 five-node release validation pack (ticket 34)

Date: 2026-09-29. Worktree `../NekoJS-mult-t34`, branch `ticket-34-release-p4`, base `mult@28283cc4`.
Executor: ticket-34 validation subagent (GLM-5.3). This pack is the release-judgment input required
by ticket 34: it aggregates the closed tickets' evidence and adds fresh verification runs executed
on this tree. It does not replace the per-domain tickets' acceptance and changes no production code,
no golden, and no gate.

## 1. Inputs consumed

- All 17 blockers of ticket 34 are **closed** (33, 04, 39, 08, 18, 20, 23, 24, 25, 30, 15, 21, 22,
  26, 27, 28, 29 — status lines verified 2026-09-29).
- Baselines `2026-09-12` … `2026-09-27` and evidence sessions `2026-09-28/29` (incl. the
  maintainer real-machine sessions, smoke/fix packs, golden-decl prep/ext, inreview digest).
- Maintainer rulings 2026-09-28/29 recorded in the closed tickets: TS-first declarations, zero
  deletion with ready evidence, hidden-reject command permission semantics (brigadier/vanilla
  convention), ticket 29 AC6 boundary (event-family charter; binding face → 09/34).
- Ticket 04's maintainer-confirmed perf release policy; ticket 35 in progress (perf section below).

## 2. Fresh verification executed in this pack (transcripts under `command-output/`)

| run | command / check | result |
|---|---|---|
| 01 | five-node `build` + `guardLint` + 3× `nbtSmokeTest` + `verifyExternalAddonIsolation` | GREEN — BUILD SUCCESSFUL (1m48s, exit 0); guardLint 341 blocks / 487 files / 0 warnings; 5 production jars carry no fixture content |
| 02 | `npm run test:probe-types` | **RED — pre-existing** (14 errors, `jsx-primitive-props.tsx`); FAILURES-LEDGER F1 |
| 03 | `:common:check` (incl. `checkCommonIsolation`) + `:common-api-processor:test` | GREEN (suite 1963/0/4; processor 13/0) |
| 04 | `tools/nekojs-ci-gates.py source-roots` ×5 nodes | GREEN (exit 0 each) |
| 05 | `tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json` | GREEN — 4 checks (ci-subset-consistency, fabric-processor-deferral, declaration-parity, node-report), 0 failure rows |
| 06 | Fabric packaged-artifact verification (CI rule set: required/forbidden entries, metadata, fabric-api minimum) on both fabric jars | GREEN |
| 07 | NeoForge artifact metadata entries (3 nodes) | GREEN |
| 08 | `architecture-audit.py` — automated full production inventory (1084 files) + handoff §2 rules | PASS all rules (0 violations) |
| 09 | `:26.1.2:platformGateTest -Dnekojs.golden.regenerate=true` + diff | Content no-op — goldens are exactly regeneration output (EOL-only delta; restored) |
| 10 | fresh JUnit counts (this tree) | common 1963/0/4; 26.1.2 490/0/58; 26.2.0 490/0/58; 1.21.1 341/0/14; fabrics 269/0/25 ×2 — zero failures |
| 11 | artifact inventory (sha256-16, sizes, 5 jars) | recorded |
| 12 | `:26.1.2:runGameTestServer` (CI gametest smoke) | GREEN — `All 1 required tests passed`, exit 0 |

## 3. Per-AC status (ticket 34's 12 ACs)

| AC | status | where |
|---|---|---|
| AC1 primary node full inputs; any required gap blocks release | **satisfied-with-recorded-gaps** — all build/check/artifact/contract/fixture/smoke inputs green on 26.1.2 except: probe-types red (F1, CI-blocking) and perf pending-35 (F-perf). Both are carried as blocking, not massaged. | BUILD-AND-ARTIFACT-MATRIX, FAILURES-LEDGER |
| AC2 secondary NeoForge node buildable/verifiable/contracts traceable; differences in matrix + notes | satisfied — 26.2.0 green across runs 01/04/05/10; differences carried (Assets `>=26`, PostEffects JSON branch, capability rows) | BUILD-AND-ARTIFACT-MATRIX §1/§3, CAPABILITY-MATRIX |
| AC3 experimental nodes repeat-build + artifact + declared-capability smoke; no silent parity promise | satisfied — 1.21.1 + both fabric nodes green; explicit unavailable/partial recorded; no parity fabricated | BUILD-AND-ARTIFACT-MATRIX §3/§4, CAPABILITY-MATRIX |
| AC4 supported/partial/unavailable only for capability; not-verified/deferred as separate blocking dimensions | satisfied — domain × node matrix with nv/def annotations kept separate and blocking | CAPABILITY-MATRIX |
| AC5 contract/golden families have old/new diff, reason, impact, review; ordinary tests did not rewrite baselines | satisfied — 10 families inventoried with guard + latest green run; fresh regeneration roundtrip is a content no-op; one known red in the standalone typecheck gate (F1) recorded | GOLDEN-CONTRACT-AUDIT |
| AC6 runtime smoke uses final artifacts/real scenarios; per-node discovery/skip/run/fail; dev-run not passed off as P4 | satisfied-with-open-windows — ledger distinguishes real-machine vs dev-run vs deferred; required holes identified: none beyond the owner-deferred/not-verified windows listed (which remain open and blocking their domains) | RUNTIME-SMOKE-LEDGER |
| AC7 final physical-architecture audit: automated full inventory, per-file exceptions, common purity, no duplicate business logic, deletion-ledger consistency; no forced physical migration | satisfied — 1084 files inventoried; all rules PASS; allowed patterns per-file with owner/reason; deletion ledger consistent | ARCHITECTURE-AUDIT |
| AC8 coverage ledger rows complete (path/owner/gate/evidence/deletion condition) | satisfied — all 23 functional rows mapped; no domain omitted | COVERAGE-LEDGER |
| AC9 four cross-domain real chains; fixtures cannot substitute | satisfied-with-one-weak-chain — chains 1, 2, 4 real; chain 3 (global/shared → domain plans) fixture-only for its real legs, stated and routed to the real-machine round (N1/F12) | INTEGRATION-CHAINS |
| AC10 perf policy exists; P4 records gate status per policy without touching numbers | satisfied-as-pending — policy cited; verdict **pending ticket 35** (`baseline/2026-09-29-release-perf/` absent at close); no number touched | PERF-GATE-STATUS |
| AC11 Point/Contributor/Hook/explicit depends/freeze/Handle not rebuilt or regressed; single source of truth for channels | satisfied — see §4 below | this README §4 |
| AC12 all failures recorded per node/input/expected/actual/owner and block release; no auto-downgrade/EOL | satisfied — consolidated ledger; 2 release-blocking items (F1, F-perf) + domain-blocking windows | FAILURES-LEDGER |

## 4. Point / Hook / Handle regression check (AC11)

- **No second framework**: ticket 08 closed with zero new Point/lifecycle framework — the external
  addon fixture expresses dependency via the existing V2 builder + `dependsOnId("nekojs:bindings")`
  and consumes prior frozen results through `context.result` (ticket AC record).
- **Chain regression green**: `Ticket08ExternalAddonChainTest` 11/0 (collection order, freeze-once,
  scoped attribution; red→green archived in `baseline/2026-09-22-plugin-addon/command-output/07`),
  re-covered fresh by this tree's `:common:check` (run 03; suite 1963/0/4).
- **Pairing gate green**: `PluginHookPairingTest` inside the same `:common:check` (run 03).
- **Single source of truth for new channels**: declaration-parity gate pass (run 05) + events-declared
  goldens as the authoritative declaration leg (TS-first ruling) + `verifyExternalAddonIsolation`
  clean on all 5 jars (run 01). The dynamic-registry channel's capability stays not-verified until
  platform wiring (no second registration path was opened — ticket 21's frozen wire-gate record).
- Ticket 21's 31-case JVM dual-adapter batch-transaction suite covers the prepare/ack/commit ledger
  semantics referenced alongside the plugin channel (cited for completeness; its real cross-node leg
  stays not-verified).

## 5. Release-readiness verdict (input for the maintainer)

**Conditional-go, with two hard blockers and a set of open verification windows:**

1. **F1 (probe-types red)** — the CI declaration-typecheck gate fails on this tree; the release
   workflow cannot go green until the jsx-runtime declaration resolution is fixed (or re-ruled).
2. **F-perf (pending 35)** — ticket 04's blocking dimensions have no recorded verdict yet; consume
   ticket 35's report when it lands (PERF-GATE-STATUS has the consumer instruction).

Everything else this pack could execute is green (five-node build/artifact/gates, architecture
audit, golden integrity, fresh suites, GameTest smoke). The owner-deferred real-machine windows
(fabric/1.21.1 real legs, PostEffects render, assets real readback, JEI hook load, chain-3 real
legs, Python .pyi leg, binding-scope record) remain open and block **full** verification of their
domains — they are recorded, never fabricated, and should ride the already-chartered real-machine
round (34 window / ticket 36 trials). Human acceptance of this pack is the maintainer's conclusion,
not this document.

## 6. Pack file index

- `BUILD-AND-ARTIFACT-MATRIX.md` — AC1–AC3 (+no-parity statement)
- `CAPABILITY-MATRIX.md` — AC4
- `GOLDEN-CONTRACT-AUDIT.md` — AC5
- `RUNTIME-SMOKE-LEDGER.md` — AC6
- `ARCHITECTURE-AUDIT.md` + `architecture-audit.py` + `command-output/08` — AC7
- `COVERAGE-LEDGER.md` — AC8
- `INTEGRATION-CHAINS.md` — AC9
- `PERF-GATE-STATUS.md` — AC10
- this README §4 — AC11
- `FAILURES-LEDGER.md` — AC12
- `command-output/01–12` — fresh transcripts (`.txt`)
