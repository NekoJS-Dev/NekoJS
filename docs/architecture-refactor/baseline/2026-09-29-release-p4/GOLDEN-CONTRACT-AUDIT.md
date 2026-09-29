# P4 contract / golden audit (AC5)

> Ticket 34 evidence pack, 2026-09-29. Inventory of every golden family with its guard test and
> latest green run. Spot-check regeneration roundtrip performed fresh on this tree (run 09).
> Ordinary tests never rewrote a baseline: the only regenerate-enabled invocation is recorded as
> run 09 and left the tree content-identical (restored via `git checkout`; EOL-only delta).

## 1. Family inventory

| # | family (files) | guard test (gate) | latest green run on this tree | old/new diff, reason, review record |
|---|---|---|---|---|
| 1 | `src/test/resources/golden/events-declared/<node>.<side>-events.d.ts` — 15 files (5 nodes × startup/server/client), two authoring batches (batch 1 commit `7985420d3`; batch 2 golden-decl-ext) | `DeclaredEventSurfaceGoldenTest` (wired into `platformGateTest` → node `check`) | runs 01 (`:<node>:build` → check → platformGateTest, five nodes) + run 09 (explicit `:26.1.2:platformGateTest`) | batch 1 diff `evidence/2026-09-29-golden-decl-prep/events-declared-goldens.diff` (+`regeneration-noop-proof.txt`); batch 2 `evidence/2026-09-29-golden-decl-ext/` (README, regenerate transcript, per-node honesty, patches); maintainer TS-first ruling 2026-09-29 (tickets 23/27 closure records) |
| 2 | `common/src/test/resources/nekojs/golden/api-manifest-core.json` | `ApiManifestGoldenTest` (`:common:check`) | run 03 (BUILD SUCCESSFUL; suite 1963/0/4, run 10) | managed-surface regeneration flow `baseline/2026-09-12-managed-surface/REGENERATE.md`; batch-1 diff `evidence/2026-09-29-golden-decl-prep/api-manifest-core.diff` |
| 3 | probe expected d.ts — `legacy-bindings`, `legacy-events`, `probe-events`, `dynamic-builders`, `dynamic-registry-events` (+ `legacy-tree/` ambient set) | probe declaration tests under `:common:check` | run 03 | batch-1 diff `evidence/2026-09-29-golden-decl-prep/probe-expected-dts.diff`; parity diff `declaration-parity.diff` |
| 4 | `common/src/test/resources/nekojs/platform-gates/declaration-parity.txt` | `ManagedDeclarationCoverageGateTest` + `tools/nekojs-ci-gates.py` declaration-parity check | run 05 (ci-gates `declaration-parity` = pass) | same batch-1 record; ticket 33 baseline `2026-09-19-ci-processor-gate/` |
| 5 | platform-gates rows — `src/test/resources/nekojs/platform-gates/event-surface-domains.txt`, `spec-coverage-{neoforge,fabric}.txt` | node `platformGateTest` + ci-gates node-report (source-trace / spec-coverage / event-surface rows) | runs 04/05 (all five nodes: e.g. 26.2.0-fabric spec-coverage 83 rows / 0 failures, event-surface 17 rows / 0 failures) | ticket 33 closure + baseline; KeyBindEvents rows covered here per ticket 26 AC7 note |
| 6 | capability rows — `src/test/resources/golden/query/capability-matrix-{neoforge,fabric}.txt` | `QueryToolCapabilityMatrixTest` | run 01 (`:26.1.2:build` et al. include the query suite; counts in run 10) | ticket 25 baseline `2026-09-12-query-tools/` (probe-derived rows; PARTIAL unless runtime-probed) |
| 7 | query declaration goldens — `datamap-binding.d.txt`, `entityselectors-binding.d.txt` | `DataMapQueryBindingTest`, `EntitySelectorsQueryBindingTest` | run 01/10 | ticket 25 baseline |
| 8 | registry goldens — `golden/registry/startup-builders.d.ts` (+ 1.21.1 variant) | `RegistryBuilderSurfaceGoldenTest` | run 01/10 | ticket 15 baseline `2026-09-15-registry-startup/` (MIGRATION + REPORT) |
| 9 | per-node gameplay API pins — `versions/<n>/src/test/resources/golden/block-events-api.txt` (3 NeoForge nodes) | per-node tests (block-events API golden) | run 01/10 | ticket 24 baseline `2026-09-22-gameplay-event-surface/` |
| 10 | inspector six-profiles golden — `common/src/test/resources/nekojs/inspector/six-profiles-golden.txt` | `Ticket45InspectorOutputTest` (`:common:check`) | run 03 | JSX UI chain (ticket 45 closed); ticket 43 AC2 golden regeneration remains a maintainer-review item per its in-review state |

Domain families whose "old/new diff, reason, impact, review record" live in the owning tickets'
MIGRATION/REPORT (managed, legacy, plugin, registry, packet, diagnostic, language, functional):
`2026-09-12-managed-surface`, `2026-09-15-event-surface`, `2026-09-22-plugin-addon`,
`2026-09-15-registry-startup` + `2026-09-16-registry-dynamic-local` + `2026-09-22-registry-dynamic-sync`,
`2026-09-15-network-sync`, `2026-09-22-diagnostics`, `2026-09-18/19-language-*`,
`2026-09-22-{recipe-data,gameplay}-surface`, `2026-09-21-{assets-lang,client-input-hud,post-effects,villager-trades}`,
`2026-09-27-client-gui-render`. Each contains the old/new diff and review notes cited by the closed
ticket; this pack re-verifies their guards through the fresh green runs above rather than re-deriving.

## 2. Zero hand-patching spot-check (fresh, this tree)

Run 09: `:26.1.2:platformGateTest -Dnekojs.golden.regenerate=true` → BUILD SUCCESSFUL (exit 0).
`git diff src/test/resources/golden/events-declared/` → **zero content hunks**; the only delta was
EOL normalization (generator writes LF; working copy CRLF), proven by an empty
`git diff --ignore-cr-at-eol --stat`. Files restored via `git checkout`. Conclusion: the checked-in
events-declared goldens for 26.1.2 are byte-equivalent to what regeneration produces — no
hand-patched output. This matches the archived no-op proof from golden-decl-prep but was re-executed
on the current base `mult@28283cc4`.

## 3. Known red in the declaration chain

`npm run test:probe-types` is red on this tree (run 02; 14 errors in `jsx-primitive-props.tsx`,
module resolution of `nekojs/jsx-runtime` → missing `JSX.IntrinsicElements`). Pre-existing before
ticket 34 (recorded in golden-decl-prep §6 and the 2026-09-29 inreview digest); owner JSX chain/09.
This is a CI gate failure and is carried as FAILURES-LEDGER F1 — the golden families above stay
green in their JUnit guards, but the standalone declaration typecheck gate is not green.
