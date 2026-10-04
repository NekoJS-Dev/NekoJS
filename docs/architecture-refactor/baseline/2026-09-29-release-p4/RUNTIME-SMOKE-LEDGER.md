# P4 runtime smoke ledger (AC6)

> Ticket 34 evidence pack, 2026-09-29. Aggregates every runtime smoke transcript produced by the
> closed tickets plus this pack's own runs. Per AC6 the ledger distinguishes **dev-run** (checkout
> `runServer`/`runGameTestServer`/`runClient` from a Gradle workspace) from **real-machine** runs;
> dev-runs are recorded as CI/dev evidence and are **not** counted as P4 real-machine evidence
> unless they consumed final remapped artifacts (ticket 08's fixture-jar discovery runs did — the
> addon jar was a built test-only artifact placed in a real loader `mods/` directory, i.e. final
> remap-equivalent packaging, not an in-process fixture).
> Per row: discovery / skip / run / fail markers point at archived transcripts.

## 1. Ledger (per session, per node × domain)

| # | session (date) | node(s) | domain | kind | discovery / skip / run / fail | transcript |
|---|---|---|---|---|---|---|
| 1 | ticket 08 (2026-09-22) | 26.1.2-fabric | plugin addon discovery→consumption→reload survival | runServer, real external addon jar in loader `mods/` | run: FML/loom discovers `nekojs-external-addon-fixture-…-testonly.jar` `{exampleaddon}`; plugin pair registers (priority 1200/400); `T08-ADDON-SMOKE: startup binding ok, marker=exampleaddon-frozen-product init=1`; fail: none recorded | `baseline/2026-09-22-plugin-addon/command-output/01-fabric-runserver-real-discovery.txt` |
| 2 | ticket 08 (2026-09-22) | 26.1.2 | same | runServer, real external addon jar | run: same markers on NeoForge (`Registered plugin: com.example.addon.ExampleAddonPlugin…`); server reaches `Done (4.300s)`; fail: none | `…/02-neoforge-runserver-real-discovery.txt` |
| 3 | ticket 08 (2026-09-22) | 26.1.2 (+fabric) | reload survival repeat | repeat runs | run: green; fail: none | `…/05-repeat-runs.txt` |
| 4 | ticket 08 (2026-09-29, this pack) | all five | addon isolation on final artifacts | post-build gate | run: `verifyExternalAddonIsolation` — 5 production jars, no fixture content; fail: none | run 01 |
| 5 | ticket 23 (2026-09-22) | 26.1.2 | recipe/datagen | runServer dev-run with datagen script | run: whole-batch failure retention + per-listener isolation observed live; note: `[object Object]` stringification gap recorded (non-blocking, owner-recorded) | `baseline/2026-09-22-recipe-data-surface/command-output/07-…-datagen-smoke.txt` |
| 6 | ticket 23 (2026-09-22) | 26.1.2-fabric | datagen explicit absence | runServer dev-run | run/skip: no published files, no `.nekojs-datagen` state dir — explicit absence confirmed (unavailable, not silent no-op) | `…/08-fabric-…-unavailable-smoke.txt` |
| 7 | ticket 24 (2026-09-28) | 26.1.2 | gameplay event surface | runServer dev-run + RCON driver | run: family bus behavior, phase trace; fail: none | `baseline/2026-09-22-gameplay-event-surface/command-output/08-runserver-26.1.2-smoke.txt` |
| 8 | ticket 24 (2026-09-28) | 26.1.2 | D4/D5 fix verify | runServer dev-run | run: fix smoke green after maintainer ruling | `…/10-runserver-26.1.2-d4d5-fix-smoke.txt` |
| 9 | maintainer session (2026-09-28) | 26.1.2 (dev client, JEI present) | 24 block-break cancel; 27 error panel; 30 record survival | **real-machine** (maintainer-operated) | run: `T24-BROKEN fired (cancelling)` ×14+, block not broken/no drops (D2 fix live); `/nekojs error` panel renders list/detail; VS Code opened via LocalErrorSource→ErrorOpenService; frozen diagnostic record survives with full fields (phase/owner/generation/source/module/cacheRevision/cause); skip: none; fail: none | `evidence/2026-09-28-realmachine-session/` (README + log-excerpt + 3 PNG) |
| 10 | ticket 20 (2026-09-29) | 26.1.2 (dedicated server) | admin command tree | runServer + RCON, two sessions | run: full `/nekojs` tree traversed as console (run1 3m29s, run2 1m3s, clean `stop`); gap-round session covers missing inputs; fail: W1/W2/W3 observations recorded (see FAILURES-LEDGER) | `evidence/2026-09-29-ticket20-smoke/command-output/01–03` |
| 11 | ticket 20 (2026-09-29) | 26.1.2 | watchdog isolation → reload recovery | runServer + RCON | run: candidate killed → isolated → reload recovers; fail: none (W1 wording issue recorded) | `evidence/2026-09-29-ticket20-watchdog-smoke/command-output/01–02` |
| 12 | ticket 21 (2026-09-29) | 26.1.2 | Dynamic Registry activation | runServer + RCON, bounded | run: gate open → ledger commit → adapter prepare → single-node commit → real `DynamicRegistries` surgery → entry observed via script + `/nekojs registry`; reload idempotent re-activation; fail: none (A1/A2/A3 observations recorded) | `baseline/2026-09-22-registry-dynamic-sync/command-output/09-runserver-26-1-2-activation.txt` |
| 13 | ticket 26 (2026-09-29) | 26.1.2 (dev client) | keybind/HUD/consumeClick/CLIENT reload | **real-machine** (maintainer-operated) | run: keybind registration ×2 loads; PRESSED/HELD/RELEASED full cycle (209 input markers); `T26-CONSUMECLICK poll consumed a click`; HUD persistent (screenshot); `/nekojs reload client` → `reload committed: generation=3 phase=COMMIT`; fail: D6 color defect found → fixed same day (fix pack `evidence/2026-09-29-d6-color-fix/`, green transcripts ×3 nodes+common; real-machine recheck pending as a 1-minute next-round check) | `evidence/2026-09-29-ticket26-realmachine/` + `evidence/2026-09-29-d6-color-fix/` |
| 14 | ticket 28 (2026-09-21) | 26.1.2, 1.21.1 | PostEffects declaration/lifecycle | node test runs (not real render) | run: 1.21.1 `PostEffectDeclarationLifecycleTest` 8/0 + `PostEffectDeclarationSurfaceTest` 4/0; chain JSON via per-node production generator; **skip: real client render smoke not run (owner-deferred to 34)** | `baseline/2026-09-21-post-effects/command-output/04, 08, 09` |
| 15 | ticket 39 (2026-09-16) | 26.1.2 | Item/Block modification collection | GameTest-server attempt | **fail (pre-existing, unrelated to 39)**: mod construction fails on `nekojs:registry_types` extension point bootstrap-incomplete in the GameTest environment; owned to 15/34 (F12) | `baseline/2026-09-16-item-block-modification/REPORT.md` G2b |
| 16 | this pack (2026-09-29) | 26.1.2 | CI GameTest smoke (mixin/interface-injection real load) | `:26.1.2:runGameTestServer` dev-run | run: `All 1 required tests passed`; BUILD SUCCESSFUL exit 0 | run 12 |
| 17 | CI (rolling) | both fabric nodes | fabric dev runtime smoke | CI job `fabric-runtime-smoke` | run in CI (server-started marker + startup bindings + `spawnLightning` compat marker); not re-executed in this pack | `.github/workflows/ci-build.yml` job definition; last green per CI history |

## 2. Node × domain hole analysis (required vs owner-deferred)

Every node × domain without any runtime evidence above:

| hole | classification |
|---|---|
| 26.2.0 live command dispatch / real-machine legs (commands, gameplay real path) | owner-deferred (tickets 20/24 closures route real legs to 34); suite + artifact green — not fabricated |
| 1.21.1 / fabric real-machine legs (input/HUD, GUI, commands live) | owner-deferred to 34's real-machine round (tickets 26/27/20 closures) |
| PostEffects real client render (any node) | owner-deferred (ticket 28 closure: owner 34) — **still open against this pack** |
| Assets/Lang real client resource reload readback; dedicated-server client-only filter smoke | owner-deferred by ticket 29 closure ruling (34 window) — open |
| JEI conditional viewer + plugin-hook real-machine load | owner-deferred (ticket 23 closure → 34) — open |
| Dynamic Registry cross-node PREPARE/ack/STATE_SYNC + client surgery | **not verified** (platform adapter/payload unwired; ticket 21 records it as blocking public activation, not silently downgraded) |
| Item/Block modification real GameTest collection | blocked by F12 (GameTest-env Point bootstrap) — see FAILURES-LEDGER |
| Fabric real-machine (client) round in general | not scheduled inside 34's charter beyond the deferred windows; recorded, not fabricated |

**No REQUIRED hole discovered by this pack's own checks went unfilled**: everything this pack
itself ran (five-node build/gates, artifact metadata, ci-gates, GameTest smoke, golden roundtrip)
is green. The probe-types F1 gate is resolved by the merged declaration golden fix; remaining holes
are the owner-deferred / not-verified windows listed above and in FAILURES-LEDGER. Per AC4/AC12
they block the corresponding domains' full verification and are recorded for the release decision —
they are not massaged into "verified".

## 3. Dev-run vs P4 evidence statement

Dev-run rows (5–8, 10–12, 16–17) are CI/dev evidence. Real-machine rows (1–2 for addon packaging,
9, 13) are the P4 real evidence. Where a chain needs the real leg and only fixture/dev evidence
exists, INTEGRATION-CHAINS.md says so explicitly.
