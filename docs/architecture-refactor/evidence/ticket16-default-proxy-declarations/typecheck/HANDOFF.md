# Default dynamic Proxy declaration repair: local handoff

Status: scoped implementation complete, UNCOMMITTED. No commit, push, branch, reset, full matrix build or Minecraft benchmark was performed by this subagent. Starting shared-main revision: `678012149aca48a7329e3fee297bb0dfdec466ee`. Parent owns integration, documentation, full isolation/build/runtime validation and performance work.

## Exact write scope

Production:

- `common/src/main/java/com/tkisor/nekojs/api/catalog/RegistryBuilderSurfaceEntry.java`
- `common/src/main/java/com/tkisor/nekojs/core/dynamic/plan/DynamicBuilderSurfaces.java` (declaration producer only)
- `common/src/main/java/com/tkisor/nekojs/probe/EventPayloadDeclarations.java` (NEW)
- `common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/EventDeclarationGenerator.java`
- `common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/TypeScriptProbeBackend.java`
- `common/src/main/java/com/tkisor/nekojs/probe/backend/python/PythonEventRenderer.java`
- `common/src/main/java/com/tkisor/nekojs/probe/backend/python/PythonProbeBackend.java`

Test: `common/src/test/java/com/tkisor/nekojs/probe/DynamicRegistryDefaultDeclarationsTest.java` (NEW).

All other writes are ignored local logs/generated verification files under `build/ticket15-16-proxy-fix/`. Compiler/module/node/script/sandbox/lifecycle production, benchmark, version tree, shared documents, public runtime methods, saved/wire contracts, version and unrelated untracked files/PNGs were untouched. Parent's untracked `ticket37-reload-confirmation` directory was observed and preserved.

## Changed declaration contract

Existing `type_docs` builder entries now have an OPTIONAL `EventPayload` record holding an explicit event-host identity, a script-only payload name and existing typed `Member` facts. The existing six-argument `RegistryBuilderSurfaceEntry` constructor remains available and defaults to no association. No Point/Plugin Hook, runtime owner, runtime interface, singleton or host permission was introduced.

The declaration-only `DynamicBuilderSurfaces` producer derives the three payload operations from `DynamicDefinitionType` and the same concrete builder facts already registered by the existing version-tree plugin through `registerRegistryBuilderSurface`. No change to that plugin is needed. Names/typeName alone do NOT infer an association (negative test).

Both complete backends consume the explicit association before Java-class fallback. Default SERVER callbacks now refer to the declared `DynamicRegistryEvent` script-only TS interface / Python Protocol, not dangling `$DynamicRegistryEventJS` or Python `Any`. The payload is exactly `item`, `soundEvent`, `mobEffect`, each `(id[,callback]) -> boolean/bool`, with the matching concrete builder, optional omission, nullable sound range and concrete fluent return preserved. There is no generic register/custom/block/removal/create member or host constructor.

Python imports the three concrete builders from `nekojs._registry_builders`, not an excluded core Java module. TS event files carry a relative triple-slash reference to the existing global `@registry-builders/index.d.ts`: current default script-dir jsconfig otherwise omits that directory. This avoids a project-wide editor/config change or any alteration of builder goldens.

The old metadata-free generator/renderer overloads remain compatible and unchanged for their callers; their old shape-only goldens remain untouched. They are not claimed as complete default-output resolution proof. Startup `RegistryEventJS` and its genuine create overloads are entirely untouched and unexpanded; dynamic payload has no create operation, so no startup/dynamic arity semantics are conflated.

## Test-first proof

1. Before any production edit, the new real default-catalog/collector/both-complete-backend test compiled and failed the missing structural SERVER callback assertion: `default-red.txt`, internal Gradle exit1, one behavioral failure (not compilation red).
2. Minimal metadata + both default consumers made that test pass: `default-green.txt`, exit0,12s.
3. The strengthened fixture now uses actual `NekoPluginBootstrap`/`registerTypeDocs`/catalog propagation, unchanged `ProbeConfig.defaultConfig()`, public `ProbeCoordinator.collectClasses` and shared `TypeReflector` IR; no private reflection or relaxed package filtering. An allowed public `ScriptType` binding supplies nonempty Python IR, not forced core classes.
4. A second real default-output assertion exposed the missing builder reference: `builder-reference-red.txt` and retained XML, three tests/one failure, exit1. The conditional relative reference fixes it.
5. Final focused command with opt-in actual output capture:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:NEKO_PROXY_DECLARATION_CAPTURE='D:\mcmodDemo\NekoJS-mult\build\ticket15-16-proxy-fix\generated'
.\gradlew.bat :common:test --tests '*DynamicRegistryDefaultDeclarationsTest' --tests '*DynamicRegistryDeclarationParityTest' --tests '*DynamicRegistryEventsDeclarationGoldenTest' --tests '*RegistryBuilderDeclarationParityTest' --tests '*ApiManifestGoldenTest' --tests '*PluginHookPairingTest' --tests '*PythonProbeBackendIntegrationTest' --tests '*ProbeClassCollectorTest' --tests '*NekoPluginBootstrapV2Test' --tests '*PythonDeclarationDeterminismParityTest' --console=plain
```

Result: `final-captured-focused.txt`, exit0,12s,17tasks/3executed/14up-to-date. Actual XML: **52 tests,9 suites,0 skipped/failures/errors**. No class named `ProbeClassCollectorTest` matched that one include pattern; collector behavior is directly executed/asserted by the new default regression. Actual suites: ApiManifestGolden5, dynamic parity6, bootstrapV2 9, PluginHookpairing6, Pythonbackendintegration15, registry-builderparity4, newdefault3, dynamicgolden2, Pythondeterminism2. Ordinary goldens were not regenerated or changed.

Security regression uses the actual default `ClassFilter(SandboxConfig.defaultConfig())` and production `NekoSharedHostAccess` in a real GraalJS Context (not `allowAllAccess`). All three entries return true with omitted and typed callbacks. `Java.type('com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEventJS')`, explicit null callback and forbidden block operation throw. Default collection excludes ALL `com.tkisor.nekojs.core.*` types; complete outputs contain no corresponding host module/import or constructor. This is production policy-seam proof, not a full Minecraft sandbox-factory boot.

## Actual generated-output checks

Bundled Node + existing TypeScript, no install:

```powershell
& 'C:\Users\11515\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe' node_modules\typescript\lib\tsc.js -p build\ticket15-16-proxy-fix\tsconfig.json --noEmit
& 'C:\Users\11515\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe' node_modules\typescript\lib\tsc.js -p common\src\test\probe-ts\tsconfig.json --noEmit
```

Both exit0. First config uses strict=true, skipLibCheck=false and includes ONLY actual captured SERVER events + `caller.ts`, not an explicit builder include. It verifies inferred concrete builders, omission and boolean returns, fluent/nullable sound semantics and consumed negative @ts-expect-error cases for block/null/item sound-only property. Existing Probe fixture also passes.

Bundled Python:

```powershell
& 'C:\Users\11515\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe' build\ticket15-16-proxy-fix\check_generated_python.py
```

Exit0:25 actual `.pyi` files parse; actual script-surface imports resolve to3 declared builder Protocols; payload AST has exactly3 typed Callable/default-ellipsis/bool methods. **Pyright not run** (not installed; no dependency install attempted). AST verification is not relabeled as static Python typechecking.

Generated SHA256:

- TS SERVER events: `9CB20A4381ADFA6EAE4D7815E93C6A68028EBC3D11C248C5FF94A6060F62BBFE`
- TS builders: `495AE7166CA730EA9F3CA3DDCA0748EC594A51A1DD01290161EDDE9925D021CF`
- Python SERVER events: `957C7FCF33D535C0AF28B8D8BA051665940F2B1908B794A13F1A374FDB53D988`
- Python builders: `FB73829DFC49DB6DE921875EBC5C373E3EB65240F2BFBE07272F7C8C0ABB6907`

`git diff --check` passed. No current main API/build/runtime/performance result is inferred from older artifacts. Lead should run `:common:check`/guardLint and applicable matrix/runtime revalidation after integrating changes; this subagent deliberately ran only targeted checks.

## Remaining boundaries

Only this dynamic default declaration linkage gap is repaired. Ticket15 startup payload/manual declarations and real create overload contracts still have separate consumers/windows; no public removal. Ticket16 activation, actual MobEffect/multiplayer, all-node installed-artifact and publication acceptance are not claimed. Lead retains all performance/lifecycle ownership.

Execution tools expose required `sandbox_permissions`/nonempty `justification` fields in this route; calls used the existing current `danger-full-access` mode, with no approval prompt, approval receipt or scope escalation. Some redundant read-only revision calls occurred during schema handling; they are not verification evidence and do not alter the implementation result.
