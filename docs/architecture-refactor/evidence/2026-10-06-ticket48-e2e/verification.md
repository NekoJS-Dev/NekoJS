# Final scoped verification

## Behavior and contract changes

- Ordinary candidate runtime/syntax errors now reject transactional reload at EXECUTION/script-execution. Unreadable candidate entries reject at PREPARATION/script-preload. Candidate failure releases candidate resources and retains the active Context/generation/listeners/state. Initial active loading and explicit kill attribution are unchanged; no new public API or owner is introduced.
- NeoForge 1.21.1 uses Minecraft's existing ICU module rather than embedding a duplicate. Modern NeoForge still requires one intact ICU Jar-in-Jar. The node-specific gate checks ownership, class/data completeness and module identity without flattening ICU.
- The canonical converted form gives native fields full inner width and 20-unit height, separates actions, and uses opaque RGB plus explicit dark labels. Script events, native color grammar and source conversion limitations are unchanged.
- Ticket 36 uses the maintainer's literal personal-trial acceptance. Ticket 39 AC14 uses the separate explicit deleted-symbol confirmation. No release approval or other-node native UI parity is inferred.

## Passed

Commands ran from the repository root with `gradlew.bat`, without golden updates:

- Focused reload generation/thread/global-state and UI authoring regressions; common isolation and NeoForge 26.2 build passed after the candidate and geometry fixes.
- `:common:test --tests '*TypeScriptUiAuthoringDocsTest' :common:checkCommonIsolation` passed after the RGB/contrast regression went red and was corrected.
- `:1.21.1:clean :1.21.1:build :26.2.0:verifyNeoForgeRuntimeArtifact :26.1.2:verifyNeoForgeRuntimeArtifact` passed (52 tasks). Legacy official jar then completed actual world baseline/active/rejection/restore/relog; no manual ZIP stripping.
- Final `:common:check :1.21.1:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build verifyExternalAddonIsolation guardLint` passed. NeoForge 26.1.2 main compilation/jar participated in isolation checks; its full build is not included in this passing statement.
- `node docs/architecture-refactor/evidence/2026-10-06-ticket48-e2e/verify-combined-session.mjs` passed: 201 earlier after-fix records.
- The same verifier with `docs/architecture-refactor/evidence/2026-10-06-ticket48-e2e/combined-final-session.log` passed: 764 records, exact keyed-list node/text changes, resource fallback/revisions, failure retention and cleanup=1.
- Authored-source/documentation `git diff --check` passed. After explicitly staging raw logs, the cached check reported five upstream ModDiscoverer blank-message lines ending in a space (one per log). Those literal records are intentionally preserved; the non-log cached diff remains clean. TSX line-ending normalization warnings are separate.
- The maintainer confirmed actual focused-control narration in the separate unmuted NeoForge 26.2 retest. Both owned clients exited normally afterward.

## Failed, not hidden

`:26.1.2:build` was rerun and failed at `compileTestJava`. The active node uses raw canonical test source; [FabricEventBusBridgeTest](../../../..//src/test/java/com/tkisor/nekojs/fabric/event/FabricEventBusBridgeTest.java) has an uncommented Fabric-only body and references `FabricEventBusBridge` at lines 25/37/50/60/78. That file is identical to HEAD, outside this patch; it was not excluded or rewritten to obtain green output. Main compilation/jar and its ICU artifact gate passed independently. A complete five-node build is therefore **not** claimed.

## Not run / limits

- No new GameTest server/remote CI run or publication was performed in this final pass.
- No real-client JSX UI support is claimed for other NeoForge/Fabric nodes.
- Legacy smoke was integrated, not independent-process network proof. Old stacks remain captured-component snapshots; fresh/restored/relogged observations are reported separately.
- Six root profile probes are not six physical window sizes. Resource reload recreated CLIENT roots/generations; space-provider widths do not prove glyph appearance. Final 764-session counters are operation counts, not a release performance threshold.

Raw game logs and scoped helpers are linked in [combined proof](README.md) and [legacy proof](../2026-10-06-ticket39-1211/README.md). Unrelated `.memsearch`, `.normify-gen`, `.workbuddy`, `.zcodeignore` and generated local build state are excluded from submission.
