# Script API stability implementation

## Scope and ownership

The lead implemented production code and tests. DeepSeek Flash agents provided read-only research. Existing unrelated authoring, JSX, Probe output annotation and reload HUD changes were preserved. No version bump or release publication was performed.

## Implemented behavior and contracts

- Binding and event hooks support module-only stable/version publication. Existing global overloads retain their behavior. Module identity includes mod owner, category, export name and Script Type; targets and duplicate names are validated before publication.
- Existing Points remain the collection source. Fabric discovery passes entrypoint owners; NeoForge discovery resolves an unambiguous mod from scan metadata. Ambiguous multi-mod jars retain legacy identity and cannot silently acquire an inferred module owner.
- Context-local, frozen namespaces support named native ESM imports and CommonJS, including plugin subpaths. Reserved routes cannot be replaced by Node modules. Public value types constrain runtime methods, overloads, Bean properties and nested views as well as declarations.
- Event views use the original buses and generation listener lifecycle. Shared buses are deduplicated for prepare/cleanup. Stable event publication requires an explicit allowlist; cancellable projection without control mapping is rejected. Module schemas do not overwrite global schema identities.
- The stable module publishes ID, the explicit profile-dependent Native bridge, CLIENT-only I18n, and SERVER-only ServerEvents.tickPre/tickPost. The version module publishes Item and applicable existing event groups. Existing globals and domain module routes remain intact.
- ID.resource enforces the documented strict resource grammar without normalization. ID.of and its nekojs default remain compatible. Native.ids converts actual node identifier types in both directions, rejects lookalikes, and validates strict grammar. Native has no class-loading entry point.
- Stable ordinary types reject direct, nested and generic native leaks; explicit native bridges retain precise profile signatures without relaxing managed-contract validation.
- Runtime, catalog, Probe declarations and authoring queries consume the actual module publications. Literal require overloads match ESM; unknown routes return unknown. EnvironmentScope now applies the existing pack Minecraft version-range semantics and rejects missing restricted loader/version data.
- Probe emits deterministic per-profile module observations. ApiModuleBaseline compares stable removals/signature/access/scope contractions separately from version/native-bridge drift. Observations do not claim verified managed contracts.

## Intentional generated changes

The existing golden regeneration workflow produced the additive ID.resource manifest entries (two overloads), and Probe tree module declarations/imports. The declaration parity baseline increases from 141 to 142 members and 150 to 152 signatures. Direct builtin-hook test fixtures now supply the same nekojs owner as runtime bootstrap; assertions and platform gates were not weakened.

## Verification

Passed:

- `& ./gradlew.bat :common:check guardLint --console=plain`, including isolation, API golden, Hook/Point pairing, lifecycle and module regressions.
- `& ./gradlew.bat :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`, including all node tests and platform gates.
- `npm run test:probe-types`, including the new strict ESM/CommonJS API-module consumer.
- Focused native ESM/CJS tests: same frozen namespace, no incidental globals, owner subpaths, unavailable named imports and reserved-route protection.
- `git diff --check` and focused final review of collection, owner routing, public views, lifecycle, common isolation and generated changes.

Final repeats also passed:

- `:common:check guardLint` after the final generic-native-leak and export-name regressions.
- `python tools/nekojs-ci-gates.py source-roots --node <node>` for all five nodes.
- `python tools/verify-active-node-sources.py --target build/active-source-matrix`: every active-node choice passed all five node selections.
- All five node `:test` tasks with `--tests "*NativeIdBridgeTest"`: actual native type roundtrips, strict grammar, null and JavaScript-like lookalike rejection.

At that checkpoint, the complete five-node build passed before the additional native-ID test was added; that new test then passed on every node. The class-loading and stable-tick follow-ups below record the subsequent production changes.

Earlier failed runs diagnosed real fixture integration and expected additive baselines, then were rerun after correction. PowerShell requires quoting the golden regeneration system-property argument; a malformed invocation was corrected. The initial source-roots invocation omitted its required --node argument; no gate was changed to bypass it.

## Class-loading follow-up

At the maintainer's request, Native.loadClass was removed from the public bridge. Native now exposes only its ID conversion view; the existing Java interop entry points were not changed. The handbook was corrected and a regression checks both the reflected public surface and actual guest access. Follow-up verification passed: `& ./gradlew.bat guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`, including the new class-loading absence regression on all five nodes. `git diff --check` passed. No live game smoke was run for this API removal.

## Stable server tick follow-up

The stable ServerEvents group publishes tickPre/tickPost, restricted to exact SERVER scripts on both loaders. Publication is now declared beside each source event in its EventGroup definition; core plugins register the source group and the registry consumes its declarations with the current owner. The immutable StableServerTickEvent exposes only its read-only tick number, copied directly from the native event server's getTickCount(). There is no root-owned clock, ThreadLocal projection scope, extra tick listener or pre/post pairing state. The temporary zero-based-counter implementation and its wrapper classes were removed.

This intentionally replaces the original design's zero-based proposal: native counters advance before pre in the inspected supported Minecraft sources, and both loader phases observe that same value. The first ordinary native tick is 1; Minecraft owns new-session initialization and integer overflow. Reloading scripts does not alter the native server count. Each retained view is just an immutable numeric snapshot, with no native server reference.

NeoForge uses its existing tick bus bindings; Fabric retains its original transformed pre event and post dispatch, including timer/drain ordering. Raw/version subscribers still receive the original payload on the same bus. Module scope is intersected with source scope in runtime and catalog; legacy STARTUP visibility remains unchanged. No native unwrap or cancellation controls are implied.

Native source audit: NeoForge 21.1.227, 26.1.2.71 and 26.2.0.57 increment tickCount before their pre hook and call the post hook after the tick; Fabric lifecycle events 4.1.1+df84eb3d4c and 4.1.4+29b6eb019e inject start before tickChildren and end at tickServer tail.

Regressions exercise actual plugin registration and native payloads, arbitrary source counts (37 and 91) without prior phase state, retained read-only snapshots, original payload identity, successful/failed generation handoff and listener cleanup, exact module scope and typed ESM/CommonJS consumers. The read-only regression checks that attempted assignment leaves tick unchanged; non-strict JavaScript may ignore a read-only write instead of throwing.

Native-payload regression runs in the existing isolated `platformGateTest` JVM because the ordinary suite can partially initialize vanilla registries without a loader. It installs scoped empty NeoForge loader metadata solely for native server class initialization, restores it afterwards, and exercises the real getter without starting a server or world. Common lifecycle and strict Probe consumer checks, all five native platform gates, all five source-roots checks and `git diff --check` passed. Final `& ./gradlew.bat guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain` passed, including ordinary tests, isolated platform gates and runtime artifact verification. `:common:check` and the complete `npm run test:probe-types` suite also passed for the native-count implementation. No live Minecraft acceptance is claimed.

## Event-owned publication follow-up

At the maintainer's request, EventGroup now owns immutable module-publication declarations. Typed event-definition overloads accept EventPublication callbacks with independent versioned/stable targets and exact per-target script scopes. Group-wide expose declarations remain available. Registry registration supplies the owner, validates public views and conflicts, and combines disjoint declarations into one module/group export without duplicating source buses. Per-event scopes survive source-group merging; wildcard selections resolve against their original contributor before merging. Static GROUP construction has no registration side effects.

Both loader tick definitions now declare their stable projections locally. The NeoForge core plugin only registers ServerEvents.GROUP. Fabric's lifecycle and recipe/trade contributors each declare their own version publication and are merged by the registry; its legacy publication loop excludes that already-declarative group. Other event groups retain their existing explicit registry publication. The configured registry overload remains a module-only extension for externally defined groups. Native tick values and script-facing module names are unchanged.

Regression source was added for both targets, owner reuse, per-event scope, source merging/inheritance, declaration freezing, conflict atomicity and shared-bus projection. Per the maintainer's explicit instruction, no tests, compilation, generators or runtime smoke were run for this follow-up. Earlier passed checks above apply to earlier revisions, not this refactor. Source/diff review and `git diff --check` passed; this revision remains unverified by compilation or execution.

## Block event source consolidation follow-up

The maintainer requested that the NeoForge block event declarations and native bridge live in BlockEvents itself. The main NeoForge source and the evaluated 1.21.1 source now each own their complete group and FORGE_BRIDGE; the empty NeoForgeBlockEvents bootstrap method and both old adapter classes were removed. The core plugin registers BlockEvents.GROUP directly. Random/block-entity tick mixins reference the same family constants in their new owner.

Fabric has a same-qualified-name BlockEvents source in its existing loader source tree, preserving its neutral broken/modification buses and callback extensions without importing NeoForge types. Existing source-selection rules choose the loader/node implementation; no new configuration or source generator was added. Native payload conversion, cancellation, dispatch keys, server-side interaction filters and modification-domain posting are unchanged. The 1.21.1 source retains BreakEvent and omits the 26.x-only modification API.

Existing regression source now reads the consolidated bridge paths, initializes the complete group directly, and requires exactly one declaration in each of the three mutually exclusive BlockEvents source sites. No golden surface was regenerated or edited. Per the existing no-tests instruction, tests, compilation, source generation and live smoke were not run for this follow-up. Earlier passed checks do not verify this revision. Source/diff review and `git diff --check` passed; compilation and runtime behavior remain unverified.

## Remaining gaps and explicit deferrals

- Stable server ticks have automated native-value projection, reload, scope and declaration coverage; live Minecraft verification remains unperformed.
- No stable NativeEvents: its current listener ledger lacks the required candidate rollback/per-script cleanup and callback thread-gate guarantees. Its existing global behavior remains unchanged.
- No Native.unwrap: there is no audited callback-scoped native-backed stable view to unwrap. Plain objects are never promoted to native handles.
- Stable projected cancellable events remain unsupported until an explicit control mapping is designed and tested.
- No live Minecraft/client-server smoke or reload/restart acceptance was run. No maintainer acceptance conclusion is claimed.
- No previous-release module observation was available for a real release-to-release comparison; comparison behavior is covered by regression tests. No CI release policy or version bump was added.
