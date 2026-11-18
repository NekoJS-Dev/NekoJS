# Explicit event module migration

## Accepted contract

The approved contract publishes event-level declarations through custom engine virtual modules and does not create automatic Java plugin/native Event Group globals. The namespace is `nekojs:event/<category>[/<owner>]` with exactly two categories: `stable` and `versioned`. Official routes are `nekojs:event/stable` and `nekojs:event/versioned`; plugin routes append the owner, for example `nekojs:event/stable/myaddon` and `nekojs:event/versioned/myaddon`. Former official routes `nekojs/api/stable/events` and `nekojs/api/versioned/events`, and former plugin routes `nekojs/plugins/<owner>/api/stable/events` and `nekojs/plugins/<owner>/api/versioned/events`, have no aliases. Do not add server/player route subdivisions. Ordinary named Binding behavior and routes are outside this event namespace migration. Script-defined dynamic event groups remain separate. Source-only Java event buses with no publication remain unexported.

## Verification seams

Use existing public registration and actual guest ESM/CommonJS imports, generated TypeScript consumers, plugin collection/owner attribution, candidate reload cleanup/failure retention, and supported-node event registration. Add a failing tracer before implementing each changed behavior. Root serializes Gradle/npm/generation while teammates edit disjoint source/test/docs scopes.

## Implementation ownership

- Root: EventGroup/EventPublication/EventExposure/ModuleEventGroup/registry, plugin Points/runtime collection and common Plugin API tests/fixtures.
- Node delegate: authored platform event declarations and node consumers.
- Runtime delegate: event global injection/schema, API module installation/routing and runtime consumer tests.
- Probe delegate: event catalog/type graph and module declaration consumers.
- Documentation delegate: canonical English authoring bundle, wiki migration and event coding conventions; event imports use the colon-prefixed custom virtual-module namespace with an optional owner suffix.

Prior uncommitted event-boundary repairs are preserved, including shared callback parsing and stable payload narrowing. Existing unrelated scratch files are not touched. No commit or push requested.

## Baseline

Before this migration, the final common run had 3022 tests, 2 existing legacy Mixin golden failures, 4 existing skips. The failure details and baseline comparison are in event-publication-fix.md and issue-69-verification.md. Five-node stable tick tests, all Probe type consumers, guard/isolation checks passed. Strict wiki checking had 45 baseline structural/digest findings after scoped digest regeneration. None of these should be suppressed as part of this migration.

## Current verification: colon event routes

The compact event module routes are implemented end to end. Official modules use `nekojs:event/stable` and `nekojs:event/versioned`; plugin modules append their loader-provided owner. Binding module IDs and event publication scopes are unchanged. ESM virtual-module ownership classifies reserved engine modules before filesystem parsing, preventing Windows `InvalidPathException` for the colon scheme.

Verification completed on 2026-11-16:

- Focused real ESM/CommonJS module tests passed after the virtual-module classification fix. They exercise plugin/official namespace isolation, imported listeners, reload rollback/replacement, root cleanup, rejection of old routes and malformed categories/owner suffixes, and reserved plugin source rejection before execution.
- Selected `:common:regenerateGoldens` tests succeeded for Probe events, dynamic registry events and the legacy tree. The only unrelated generated delta was the already documented JDK VarHandle ordinal signature; it was restored, leaving that golden unchanged.
- `./gradlew.bat --no-daemon :common:check guardLint --console=plain` ran 3036 tests: 2 failed, 4 skipped. Both failures are the known `LegacyProbeTreeTest` / `ProbeOutputCompatibilityTest` mismatch in `@mixin/index.d.ts` for `VarHandle$AccessMode.valueFromOrdinal`. No other common tests failed. Root `guardLint` passed.
- `:common:checkCommonIsolation :common:verifyAddonFixtureDependencySurface` passed independently.
- The five-node consumer matrix passed for 1.21.1, 26.1.2, 26.2.0, 26.1.2-fabric and 26.2.0-fabric. Coverage includes startup declarations, stable ticks, gameplay reload, HUD/render, PostEffect parity/lifecycle, network routing and data-sync generation boundaries.
- `npm run test:probe-types` passed, including event module imports and generated API module declarations. `npm run test:wiki` passed 8/8. Strict wiki checking retains 25 unrelated navigation/structure findings; all 22 changed topic digests were refreshed without changing draft/pending human-review status.
- Final `git diff --check` passed. Read-only route review found no demonstrated production issue after validating owner attribution against Fabric and NeoForge discovery.

### Shared-worktree evidence review (2026-11-18)

- The retained common JUnit XML reports show `EventModuleRuntimeTest` 4/4 and `NekoNodeReservedApiModuleTest` 5/5, with no skips or failures. `AuthoringExamplesEngineTest` 11/11 includes [the Python bridge regression](../common/src/test/java/com/tkisor/nekojs/authoring/AuthoringExamplesEngineTest.java#L90): a Python script imports the local JavaScript re-export of `nekojs:event/versioned`, receives one callback on the source bus, and stops receiving callbacks after listener cleanup. This closes the pending bridge check in the earlier diagnostics; direct Python import of the engine module is not part of the contract.
- `python tools/nekojs-ci-gates.py all` rechecked retained artifacts and reports: 4 gates passed, and each of the five nodes reported 17 event-surface rows and no failures. This did not rebuild the nodes or rerun their tests. The [declaration refinement](event-declaration-refinement.md#root-integration-result) records the later common and node consumer runs.
- Live-game verification was not run. Maintainer acceptance and wiki human review remain pending. No release, commit or push was performed. The earlier diagnostics below are historical and do not replace the final results.

## Earlier migration diagnostics

Behavioral reds captured: official/plugin event module paths; same group/event names across owners; real guest ESM missing new namespace; Probe native global leakage; plugin named events occupying official event module. Initial integrated model/Probe/root lifecycle45 tests had44pass and only Windows process-logger cleanup failure in new real runtime tracer. Ownership fixture fixed without skipped cleanup; followup focused40 had39pass with ESM tracer fullygreen, one newly added CJS reload fixture failure under investigation. First full diagnostic:3027 tests,48failed,4skipped; failures routed to module/example/consumer fixture owners and expected event declaration snapshots. Guard/isolation/addon-surface checks passed. The native two-loader smoke exposed a missing test snapshot constructor modIds argument; fixture-only corrected. Full five-node platformGateTest then passed (1.21.1,26.1.2,26.2.0,26.1.2-fabric,26.2.0-fabric). Broader module/domain/example followups passed101/104 initially; the remaining reserved-plugin-source tracer proved source execution before rejection (counter1), and the shared Java namespace predicate plus early Node installer gate fix is now in place. Python bridge fixture initially failed at unscoped Java event registration before Python ran; corrected official owner, true bridge check pending. Dynamic payload slice helper anchored to correct module namespace. Final common rerun underway. Canonical event golden regeneration succeeded via selected :common:regenerateGoldens tests; expected event subscriptions move to the exact `nekojs:event/stable` and `nekojs:event/versioned` modules (plugin routes append owner), and old native globals disappear. The existing unrelated JDK-specific VarHandle golden drift remains documented above and is not accepted. The sole unrelated @mixin VarHandle valueFromOrdinal delta was restored from HEAD; baseline failures are not hidden. Details in ../REGENERATE.md. Wiki22 changed topics regenerated with canonical exported digest helper, humanReview pending; strict wiki reports25 existing navigation/structure findings versus45 at unchanged9f6fe23a, with no findings in the event namespace pages; wiki tool tests8/8passed. Independent standards/spec reviews running. No final acceptance claimed. This is an authorized breaking Script API/Plugin API event migration, not a version release or live-game acceptance.
