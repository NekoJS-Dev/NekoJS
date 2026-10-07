# Default dynamic event declaration gap: verified source trace

Behavior revision: `ac810fa71d061d31983220c0d9b198af5d4253ac`. This is an inspected failure chain and implementation plan, **not an executed regression test or completed fix**. It does not authorize public removal or broaden dynamic activation capability.

## Confirmed chain

1. `common/.../core/dynamic/facade/DynamicRegistryEvents.java` registers `dynamicRegistry` with the host class `DynamicRegistryEventJS.class`. `NekoScriptCatalog` copies this into `EventCatalogEntry.eventType`.
2. The actual payload's `item`, `soundEvent` and `mobEffect` operations are private `ProxyExecutable` map entries. Each accepts `(id)` or `(id, callback)`, checks that the supplied callback is executable, configures the concrete builder through `DynamicBuilderSurface`, collects a definition and returns `true`. Optional omission is not explicit-null acceptance.
3. Default `ProbeConfig` excludes `com.tkisor.nekojs.core`; `ProbeClassCollector` rejects that event class before reflection. This is intentional isolation/security scope and must not be relaxed.
4. `EventDeclarationGenerator` nevertheless emits `$DynamicRegistryEventJS` while default import filtering omits it. `common/src/test/resources/nekojs/dynamic/dynamic-registry-events.expected.d.ts` line8 freezes a dangling callback type without an import or declaration.
5. `PythonEventRenderer` resolves against available generated classes; the excluded class becomes `Any`. It therefore cannot offer the real three-member typed payload.
6. Existing builder surface entries reach both backends through the paired `type_docs` Point/Plugin Hook, but neither event renderer consumes a structural payload association. Correct standalone builder files do not prove callback payload linkage.

## Minimal existing-channel correction

Reuse the existing `type_docs` collection and `RegistryBuilderSurfaceEntry.Member` typed representation. Add an explicit declaration-only event-payload association at that catalog seam; preserve existing constructor consumers or review a deliberate public metadata change. Both event renderers resolve this association before Java-class fallback.

Derive the closed three operations from `DynamicDefinitionType` and existing builder facts, not independent handwritten member tables in two backends. Emit a script-only TS interface and Python Protocol, with resolved concrete builder callback references, optional callback omission and boolean return. Python requires explicit imports from the script-surface module. Do not emit a `java:...core` module, host constructor or Java.type promise. Do not infer identity merely from arbitrary builder simple names or `typeName="dynamic"`.

No new Point/Plugin Hook or second runtime owner is needed. `ClassFilter` stays unchanged. Scanning more core classes would not reflect private Proxy members and is not this fix.

## Proper regression seam

Before production changes, construct the real facade registry and catalog snapshot including structured surfaces; collect with `ProbeConfig.defaultConfig()` and render both complete backends in memory. The assertions must fail on the current missing payload linkage, not merely count event or builder names:

- Core classes remain excluded from collection; no core Java module/import/constructor is generated.
- The SERVER event callback references a resolvable structural payload, not dangling `$DynamicRegistryEventJS` or Python `Any`.
- Payload member names equal the runtime `getMemberKeys()` and the closed type directory exactly; no `register`, `custom`, `block`, removal or generic type entry appears.
- Each member has the correct concrete builder callback, optional omission and boolean return. Existing nullable sound range and concrete fluent returns remain intact.
- Production host-access/ClassFilter fixtures permit the Proxy operations while rejecting Java.type of the internal event class.

The existing `DynamicRegistryEventsDeclarationGoldenTest` verifies rendered shape/name and separate builder goldens, not complete default-output resolution. `DynamicRegistryDeclarationParityTest` currently supplies an empty Python availability set and only counts the event, so it tolerates the degraded payload. Extend the real default-output seam; run red, apply the minimal fix, then run both production renderers and common isolation checks. Deliberate golden regeneration follows the repository workflow, never ordinary tests.

## Separate startup scope

Ticket15's `RegistryEventJS` has different arities and builder returns, plus startup sugar/custom/Supplier capabilities. Do not reuse the dynamic1/2argument, boolean-return semantics. Its handwritten `nekojs.registry` TS producer still has a consumer; Python has no manual-declaration consumer. That producer cannot be deleted just because dynamic or builder tests pass. Startup requires its own end-to-end payload association and platform-subset checks.

## Verification status

Source inspected and existing golden read. No new regression test, implementation change, golden generation or default live Probe run was performed for this gap in this record. Ticket16 remains in-progress. Performance measurements and A22/A28 approval records are separate evidence.
