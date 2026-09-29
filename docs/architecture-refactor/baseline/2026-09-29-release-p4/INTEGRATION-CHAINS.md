# P4 cross-domain real integration chains (AC9)

> Ticket 34 evidence pack, 2026-09-29. The four named chains, each endpoint cited to **real**
> (non-fixture-only) evidence where it exists; where a leg is fixture-only, that is stated and the
> chain is routed to the real-machine round instead of being claimed.

## Chain 1 — diagnostics record → GUI / workspace (30 → 27)

| leg | evidence | real? |
|---|---|---|
| frozen diagnostic record created with full fields (phase=EXECUTION/RESOLVE_LINK, owner, generation, source, module, cacheRevision, cause) | `evidence/2026-09-28-realmachine-session/log-excerpt.txt` (`script-diagnostic id=nekojs:rt/…` lines, incl. a candidate-phase RESOLVE_LINK record) | **real** (maintainer single-player session, dev client + JEI) |
| record survives and is listed in `/nekojs error` panel; detail/copy/locate act | same session: panel rendering confirmed by maintainer + screenshot `2026-09-28_21.48.59.png` | **real** |
| locate → VS Code open via `LocalErrorSource → ErrorOpenService` | same session, maintainer-confirmed actual VS Code window open | **real** |
| fabric/text-degraded variant | capability matrix (27): text degradation, seam supported | design-level; real fabric client leg not run (deferred) |

Verdict: chain closed on real evidence end-to-end on 26.1.2 (record → panel → external IDE).

## Chain 2 — event candidate → runtime commit (14 / 26 / 08 / 21)

| leg | evidence | real? |
|---|---|---|
| CLIENT reload: `/nekojs reload client` → scheduled → keybind members re-registered → `reload committed: generation=3 phase=COMMIT`, HUD stable across generation switch | `evidence/2026-09-29-ticket26-realmachine/` (log excerpt + screenshot; maintainer session) | **real** |
| SERVER reload with real external addon: plugin bootstrap not re-run, frozen product still consumed after reload | ticket 08 runServer sessions (RUNTIME-SMOKE-LEDGER rows 1–3): `T08-ADDON-SMOKE: startup binding ok, marker=exampleaddon-frozen-product init=1` across reloads, repeat-run green | **real** (real loader discovery of a built addon jar) |
| Dynamic Registry activation: candidate ledger → adapter prepare → single-node commit → real `DynamicRegistries` surgery → observable via script + `/nekojs registry`; idempotent re-activation on reload | `baseline/2026-09-22-registry-dynamic-sync/command-output/09-runserver-26-1-2-activation.txt` (RCON-driven bounded session) | **real** (headless dedicated server; dev-run classification noted in the smoke ledger) |
| candidate-phase failure retention (link-error record) feeding chain 1 | 09-28 session RESOLVE_LINK record | **real** |

Verdict: chain closed on real evidence for CLIENT (real client), SERVER addon (real loader), and
registry activation (dedicated server). Fixture-level cases (14's family fixtures, 21's 31 JVM
dual-adapter cases) extend coverage but are not counted as the real leg.

## Chain 3 — global/shared write-set → domain plans (10 / 39)

| leg | evidence | real? |
|---|---|---|
| type-keyed global + explicit shared + candidate top-level write-set joint commit; root-owned state survives reload/stop-cycle and is released by root close | ticket 10: `NekoRuntimeRoot`-level test `rootOwnedStateSurvivesReloadAndStopCycle…` (real `NekoRuntimeRoot`, platform hooks simulated via `clearWorldPackListeners` + rediscover) | **fixture/simulation-level** (common layer); real in-game server-stop/world-leave leg = N1, **not run** |
| Item/Block modification candidate plan + snapshot ownership + restore | ticket 39 fixtures + suites; real GameTest collection attempt failed on F12 (pre-existing GameTest-env Point bootstrap issue, owner 15/34) | **fixture-level**; real leg blocked by F12 |

Verdict: this chain is the weakest of the four — **fixture-only for its real-machine legs**. It is
recorded as such (not claimed) and routed to the real-machine round: N1 (minecraft-mcp `/nekojs
reload` + stop/reopen world smoke) and F12 unblocking (both listed in FAILURES-LEDGER). Per AC9 the
domain contract fixtures do **not** substitute for this chain's final real leg.

## Chain 4 — build trace → capability matrix (33 → 31/32 + this pack)

| leg | evidence | real? |
|---|---|---|
| per-node source-trace facts from the build graph (settings DSL → init-script probe → `source-roots-<node>.json`) | run 04 (five nodes, exit 0) — the same mechanism CI runs | **real** (automated, this tree) |
| gate aggregation: ci-subset-consistency, fabric-processor-deferral, declaration-parity, node-report (source-trace/spec/event rows) | run 05 + `build/nekojs-gates-report.json`: 4 checks, 0 failure rows | **real** |
| capability rows derived from runtime probes, pinned per loader | `golden/query/capability-matrix-{neoforge,fabric}.txt` guarded by `QueryToolCapabilityMatrixTest` (green, runs 01/10); ticket 27 `CAPABILITY-MATRIX.md`; this pack's `CAPABILITY-MATRIX.md` | **real** (probe-derived, test-guarded) |
| fabric artifact identity/trace | runs 06/07 (entries, metadata, no NeoForge leak) + tickets 31/32 baselines | **real** |

Verdict: chain closed on real automated evidence; the matrix rows in this pack consume those
outputs directly.
