# Removal of normal script execution controls

## Scope and migration

The engine no longer exposes or enforces scriptEvaluationTimeoutSeconds, scriptStatementLimit or scriptRunawayTimeoutSeconds for normal startup/server/client/test script execution. The configuration model and its callers no longer carry these values. Writable canonical engine configuration removes the retired keys, including their comments, through the existing config loader. The legacy engine-config fallback remains read-only. All other configuration, including the pending default-off MixinJS opt-in, is retained.

The normal sandbox factory no longer installs Graal ResourceLimits or a statement predicate. Both production watchdog implementations and automatic arm/disarm scheduling are removed. Entry evaluation still pumps ready Node timers and polls a pending evaluation future to make progress, but there is no total evaluation deadline or automatic Context termination. Interruption and original evaluation failures continue to propagate through existing recording and generation handling.

## Explicitly retained behavior

- HTTP and authoring request/reply waits are independent operation policies and remain unchanged.
- Lifecycle close preemption, callback dead-context rejection, failed-active isolation, candidate rollback and explicit recovery remain in place. Generic cancellation/resource-exhaustion classification does not install an execution budget.
- Diagnostic phase WATCHDOG and owner label Runaway Watchdog remain stable identifiers for existing diagnostic consumers; these labels no longer imply an installed automatic watchdog.
- The private offline Mixin declaration evaluator retains its separate fixed statement budget. It is not the normal script execution environment and does not read the retired settings.

There is no replacement hidden deadline, cumulative budget, global guard thread or fallback watchdog. A non-terminating top-level await may keep loading pending; synchronous infinite guest code may block the owner thread until externally interrupted or the process ends. Host-call blocking is not made safe by this removal.

## Regression strategy

Configuration migration verifies removal of old positive, negative and invalid-typed values while preserving retained settings and read-only bytes. Tests that only assert removed watchdog behavior are retired; tests covering cancellation, failed-active isolation, dependency publication, transactional rollback, cleanup and recovery retain those assertions using explicit test-owned Context cancellation and intrinsically finite fallback work. Test timeouts are harness containment, not production replacements.

The internal SandboxConfig constructor interface is intentionally reduced from the removed-budget shapes to 8, 11 and 12 retained-setting arguments; no ignored obsolete control parameters remain. Existing callers are migrated together.

## Verification

- Common main compilation passed. Compiled main outputs contain no watchdog classes. Active normal-runtime references to the three old controls are limited to the three configuration cleanup entries; the private offline declaration budget remains separate.
- Focused migration/execution/cancellation/lifecycle checks passed (109 tests) with no relaxed assertions. Initial fixture failures exposed borrowed Context wrappers without cancellation authority and shared temporary script contamination; the fixtures now cancel the actual creator-owned Context, perform terminal cleanup, and isolate their script inputs.
- Wiki sync/stamp/strict checks passed (31 bilingual topics, zero problems); eight wiki-tool tests passed. Obsolete active budget diagnostics are retired; the still-live output cap NEKO-2004 is retained.
- The first full matrix was stopped after thread dumps identified additional budget-dependent infinite-loop fixtures in GlobalStateExamplesTest and the shared client GUI/render lifecycle tests. Those fixtures now retain rollback/cleanup/recovery assertions through ordinary failure or explicit creator-owned cancellation. A full scan leaves only the intentionally bounded private Mixin declaration test loop. No production guard was restored to make the tests finish.
- Full common isolation/five-platform/addon matrix passed in 2m41s on retry: `./gradlew --no-daemon :common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :addons:nekujs:build :addons:nekujs:fixtureTest :addons:nekujs:expandedFixtureTest :addons:nekujs:compatProofTest --continue --console=plain`. No gate exclusions or expected lifecycle assertions were removed to obtain this result.
- Full Probe TypeScript/completion checks passed: `pnpm run test:probe-types`.
- Final common report: 2,951 tests, zero failures, four existing skips. The test-count change reflects retired automatic-budget tests and retained/new generic cancellation/evaluation checks, not dropped lifecycle expectations.

No new game-world or human acceptance is claimed.
