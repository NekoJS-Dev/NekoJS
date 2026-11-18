# Event declaration refinement

## Approved contract

- Plain `EventGroup.server/client/startup`, including keyed overloads, default to versioned publication. Configured overloads publish only selected targets; empty callbacks fail. Unconfigured `add` stays private.
- `stable()` and `versioned()` return the same Scope with optional `view(Class, Function)` and exact `scriptTypes(...)`; remove `stable(Class, Function)` without adding synonyms.
- Stable may be an independent portable source posted directly. Sibling views receive the original payload and share its source bus/lifecycle. Views do not convert dispatch keys. Converted cancellable sources remain rejected in both categories.
- Exact publication scope intersects source availability: SERVER-only scope differs from the SERVER source range (SERVER, STARTUP, TEST). Preserve existing colon routes, Binding contracts and route-migration edits.

## Root acceptance checklist

- [x] Focused publication regressions cover defaults/keyed defaults, explicit-only targets, empty callback rejection and private `add`.
- [x] Stable-only direct posting, both categories' views, original-source conversion, cancellation/key constraints and exact scope pass focused runtime tests and affected node checks.
- [x] Relevant common/node checks were run and reviewed; the two existing JDK/Mixin golden failures are recorded below. No new golden changes were needed for this declaration-only refinement.
- [ ] Maintainer acceptance: pending a real maintainer conclusion.

## Documentation result

Updated the canonical authoring handbook and Java examples, event conventions, bilingual event extension/migration guidance, architecture summary and NEKO-4057 guidance. Refreshed only event-extensions, project-architecture and error-reference wiki digests with the canonical digest helper; draft/pending review status remains unchanged. Existing route migration, script examples and Binding paths are preserved. No Java, Gradle or generated goldens were changed/run by the documentation delegate.

- `npm run test:wiki`: passed, 8/8 tests.
- `npm run check:wiki`: failed with exactly the same 25 structural/navigation problems as the pre-edit baseline (34 topics); no scoped-topic or digest failures. Existing problem topics: script-basics, data-and-state, event-reference, ai-support, jsx-client-ui, client-rendering, plugin-development, jsx-client-ui-advanced, ai-support-protocol.
- `git diff --check`: passed. Scoped diff and new handbook link/anchor reviewed. Java snippets not compiled by this delegate.

## Root integration result

- Production `:common:compileJava` passed. The first focused test compile found a missing ScriptType import in the migrated extension-point fixture; root added it. The repeated focused `:common:test` run passed for EventGroupPublicationTest, ModuleEventExposureTest, NekoScriptCatalogEventsTest, NekoPluginExtensionPointTest, EventModuleRuntimeTest and NekoRuntimeRootReloadResultTest.
- Five-node consumer matrix passed on 1.21.1, 26.1.2, 26.2.0, 26.1.2-fabric and 26.2.0-fabric. Selected families: StartupRegistryDefaultDeclarationsTest, StableServerEventsTest, gameplay reload, client HUD/render lifecycle, PostEffect lifecycle/parity, network routing and data-sync generation boundaries.
- `./gradlew.bat --no-daemon :common:checkCommonIsolation :common:verifyAddonFixtureDependencySurface guardLint :addons:nekujs:compatProofTest :common:check --continue --console=plain` ran 3044 common tests: 2 failed and 4 skipped. Both failures are the unchanged LegacyProbeTreeTest and ProbeOutputCompatibilityTest JDK reflection mismatch for VarHandle$AccessMode.valueFromOrdinal in the Mixin golden. No other common tests failed. Common isolation, addon fixture dependency surface and root guardLint passed. The addon compatibility proof passed 2/2 tests with no skips.
- The source-only fixture audit covers common tests, authored node sources and addon proof. Production common shortcut callers preserve their previous versioned publications; explicit configured publications retain exact source/type scopes and custom bus identity.
- Read-only standards/contract review found no demonstrated new regression. Existing host-only payload conversion and cancellable-projection restrictions remain unchanged. No module routes, Binding behavior, dependencies or lifecycle owners were changed in this refinement.
- Generated outputs were verified by normal tests; no new golden regeneration or JDK golden acceptance was needed. The unrelated Mixin golden file remains unchanged. Final source/doc whitespace check passed. No game runtime/human acceptance, release, commit or push was performed.
- `npm run test:probe-types` passed, including generated event module/API declarations and completion consumers.
- Root audit note: private native source helper in addon compatProof NativeEventProofScope migrated to `add(name, SERVER, EventBusJS.of(KubeEvent.class))`; legacy addon bridge already uses private `add` (reported by root; not delegate-owned verification).

### Shared-worktree evidence review (2026-11-18)

The retained common JUnit XML from 2026-11-17 UTC shows `EventGroupPublicationTest` 15/15 and `ModuleEventExposureTest` 13/13, with no skips or failures. The tests cover plain and keyed defaults, configured targets, private buses, exact scope, portable stable payloads, independent views of the original payload, and source-bus cleanup. The retained `LegacyProbeTreeTest` and `ProbeOutputCompatibilityTest` reports still show one failure each; the common gate is not green. The [migration review](event-module-migration.md#shared-worktree-evidence-review-2026-11-18) records the read-only CI report aggregation. The existing five-node matrix and Probe consumer runs were not repeated; live-game verification was not run and maintainer acceptance remains pending.
