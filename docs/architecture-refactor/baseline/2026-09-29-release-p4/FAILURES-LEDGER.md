# P4 failures ledger (AC12)

> Ticket 34 evidence pack, 2026-09-29. Every recorded open finding, each with node / input /
> expected / actual / owner and a release-impact statement. Nothing is massaged, downgraded, or
> auto-EOL'd; a single failure does not demote a node. "Blocking" is stated against the release
> candidate (per AC12, failures block release until fixed or explicitly re-ruled by the maintainer;
> owner-deferred windows block the corresponding domain's full verification, per AC4).

## Resolved release-gate findings

| id | node | input | expected | actual | owner | resolution |
|---|---|---|---|---|---|---|
| F1 | common (CI: all) | `npm run test:probe-types` (`common/src/test/probe-ts/jsx-primitive-props.tsx`) | exit 0 | PASS after merging `probe-types-fix` (`bce6d8ee6aaca103487cb1e0a0d1e13b3bb9dd8a`); fresh-checkout declaration fixture resolves `nekojs/jsx-runtime` and `tsc` exits 0 | JSX chain / 09 | Resolved 2026-10-03. Also passed `:common:test --tests com.tkisor.nekojs.probe.JsxRuntimeProbeDeclarationGoldenTest --rerun`; evidence: `evidence/2026-09-29-probe-types-fix/`.
| F-perf | 26.1.2 | perf re-test per ticket 04 policy (startup ≤ 41173 ms, reload ≤ 285.3 ms mean, 5 formal samples each) | policy-verdict recorded | startup mean 16512.2 ms and reload mean 278.0 ms, both PASS | ticket 35 | Resolved by ticket 35 evidence; see `PERF-GATE-STATUS.md` §2.

## Historical blocking snapshot at pack close (2026-09-29)

The following rows preserve the original pack-close inputs for auditability. Current status is
recorded in the resolved section above; these rows are not current release blockers.

| id | node | input | expected | actual | owner | release impact |
|---|---|---|---|---|---|---|
| F1 | common (CI: all) | `npm run test:probe-types` (`common/src/test/probe-ts/jsx-primitive-props.tsx`) | exit 0 | exit 2 — 14 errors (TS2307/TS2875/TS7026; `nekojs/jsx-runtime` unresolved → no `JSX.IntrinsicElements`); reproduced fresh in run 02 | JSX chain / 09 (pre-existing before 34; recorded in golden-decl-prep §6 and the 2026-09-29 inreview digest) | **BLOCKING for CI greenness**: the CI step `Typecheck Probe declarations` fails, so the release workflow cannot pass as-is. Not a regression of this pack's scope; requires the jsx-runtime declaration resolution fix (or a maintainer re-rule) before release. |
| F-perf | 26.1.2 | perf re-test per ticket 04 policy (startup ≤ 41173 ms, reload ≤ 285.3 ms mean, 5 formal samples each) | policy-verdict recorded | **pending ticket 35** (`baseline/2026-09-29-release-perf/` not present at pack close) | ticket 35 (in-progress, parallel worktree) | **BLOCKING until 35 lands**: ticket 04 marks startup/reload as release-blocking dimensions; this pack records the gate as pending-35 (PERF-GATE-STATUS). |

## Open findings (recorded, non-blocking unless stated)

| id | node | input | expected | actual | owner | release impact |
|---|---|---|---|---|---|---|
| W1 | 26.1.2 | `/nekojs reload <file>` after watchdog killed the active runtime (RCON session) | FILE success report reflects "active was killed" | `doReloadScriptFile` reports success with only a residual-error hint; `NekoRuntimeRoot.reloadFile` rechecks `isActiveFailed` at entry, not at exit | ticket 20 (wording fix ruled by maintainer 2026-09-29, effective with merged fix pack) | non-blocking (user-facing wording; state machine correct, isolation effective). If the ruled fix is not yet observable in a future smoke, re-open. |
| W2 | 26.1.2 | watchdog 3 s runaway spin during candidate/FILE evaluation | — | server thread blocked (`Can't keep up! … 62 ticks behind` ×3) — consistent with owner-thread synchronous evaluation model | ticket 07 domain (observation) | non-blocking (by-design ownership semantics, recorded). |
| W3 | all | empty `server_scripts` dir | discovery counts read against authored scripts only | mod auto-scaffolds `src/main.js` example script, skewing discovery counts (2/3/2 basis) | maintainer decision (observation) | non-blocking; affects count interpretation only. |
| A1 | 26.1.2 | same declaration at startup | single attributed transaction | two transactions per startup (reload-tx + `server-registry-ready` collection with `<unknown>` owner); idempotent re-claim, counts unchanged | ticket 21 (observation) | non-blocking (accounting noise, not correctness). |
| A2 | 26.1.2 | `collectInitial` INFO line with gate on | wording matches state | stale "inert only — activation is gated" printed while engine is bound and activated | ticket 21 (wording defect) | non-blocking. |
| A3 | 26.1.2 | `/nekojs reload server` during empty-server pause (`pause-when-empty-seconds`) | immediate activation | `ServerTickEvent.Post` does not fire while paused: activation deferred to next non-paused tick; stop-while-paused drops via close boundary and re-activates on next boot | ticket 21 → 34 real-machine round awareness | non-blocking (documented behavior; real-machine round must not misread it as a hang). |
| F12 | 26.1.2 | GameTest server + `run/nekojs/server_scripts` item/block modification script | scripts collect | mod construction fails: `extension point 'nekojs:registry_types' has not finished yet (bootstrap incomplete)` in GameTest environment | ticket 15/34 owners (pre-existing, unrelated to 39) | blocks the real GameTest collection leg for chain 3 / row 8 (item-block modification real leg). Plain CI GameTest smoke (no nekojs scripts) is green (run 12). Non-blocking for release unless the item-block real leg is required for it. |
| D6-followup | 26.1.2 | script-side ARGB int color literals (`0xFF……`) | colors render as authored | rendered white on real machine (Graal JS number → Java int saturation); fix merged with green transcripts on common + 26.1.2 + 1.21.1 (`evidence/2026-09-29-d6-color-fix/`) | ticket 26 (fixed; real-machine recheck = "next round 1-minute check") | non-blocking pending the 1-minute real recheck in the next real-machine round. |
| Python-pyi | all | Python declaration leg for the events-declared families | parity with TS goldens | TS-first ruling (2026-09-29): TS golden is authoritative; Python `.pyi` follow-up recorded to 09/34 | 09/34 record | non-blocking (ruled deferral), domain not fully closed for the Python leg. |
| Binding-scope | all | `Assets` typed binding + `generatedLangs()` declarations | declaration coverage | outside the events-declared golden family (event-family charter); scope decision recorded to 09/34 (ticket 29 AC6 boundary ruling) | 09/34 record | non-blocking (ruled boundary), binding-face declaration coverage remains an open record. |
| Fabric-realmachine | 26.1.2-fabric, 26.2.0-fabric | client-side real-machine round (GUI/render/input; unauthorized-rejection UX; PlayerEvents real path) | real-machine legs | not run; dev-run + CI + explicit-unavailable pins only | 34 real-machine round / 36 (per tickets 20/24/26/27 closures) | blocks full verification of those domains on fabric/1.21.1; recorded, not fabricated. |
| PostEffects-render | 26.x | real client post-effect render smoke | visual confirmation | node tests green; real render smoke not run (ticket 28 closure → 34) | 34 real-machine round | blocks full verification of the PostEffects domain. |
| Assets-readback | 26.x | real client resource reload readback; dedicated-server client-only filter | live readback observed | fixture/readback tests green; real legs not run (ticket 29 closure ruling → 34 window) | 34 real-machine round | blocks full verification of the assets real-readback leg. |
| JEI-hook | 26.1.2 | JEI conditional viewer + plugin-hook real load | real load | conditional; real load deferred to 34 (ticket 23 closure) | 34 real-machine round | non-blocking for the recipe domain's declared scope; recorded window. |
| DynReg-sync | all | cross-node PREPARE/ack/STATE_SYNC + client registry surgery | real sync | not verified — platform adapter/network payload unwired; ticket 17 wire gate frozen (6 calls/5 types) | platform wiring successor (ticket 21 record) | public activation stays blocked (capability `P`, matrix row); not silently downgraded. |
| N1 | 26.1.2 | real in-game server stop / world leave / CLIENT real for global shared state | manager-side semantics observed in-game | simulated via platform hooks at common layer; real leg not run | 34 real-machine round / minecraft-mcp | blocks the real leg of integration chain 3 (fixture-only leg stated in INTEGRATION-CHAINS). |
| JSX-UI-inreview | 26.2.0 | tickets 41/43/44 remain in-review (outside ticket 34's blocker set; 48 blocked on them) | maintainer rulings | live Screen interaction, golden regeneration review, texture pipeline remain open per the 2026-09-29 digest | 41/43/44 + 48 chain | outside this pack's blocker scope; recorded because the release-readiness verdict must not imply the JSX UI chain is closed. |

## Statement

No failure was downgraded or excluded to make the original pack green; no gate was weakened. F1 and
F-perf were the two release blockers at the 2026-09-29 pack close and are resolved in the section
above. Remaining rows either block a specific domain's full verification (explicitly carried in
CAPABILITY-MATRIX as nv/def) or are recorded observations with a ruled owner.
