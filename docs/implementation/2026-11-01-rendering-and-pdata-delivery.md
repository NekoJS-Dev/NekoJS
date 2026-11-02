# Rendering and Fabric PData delivery

## Requested scope

Fix the Fabric PData container disagreement and Minecraft 26.2 world line/box submission; implement real callback-last post-effect builders and explicit HUD/world registration options in Probe. The earlier wiki audit remains a historical documentation-only record. This delivery changes runtime behavior and synchronizes the English bundled handbook and bilingual wiki. No version bump, commit, push, or automatic saved-data migration is included.

## Changes and contracts

- [Fabric persistence diagnosis and regressions](2026-11-01-fabric-pdata-fix.md): the mixin now owns the complete `NeoForgeData` container, matching both bridge access paths. Canonical payload selection happens once. Preserve unrelated fields and historical nested data; do not guess whether a nested key is corruption or legitimate user data. Stop the server and back up saved data before any separately approved manual migration.
- [World rendering diagnosis and regressions](2026-11-01-world-render-fix.md): version facades replace reflective no-op discovery. Minecraft 26.2 uses an owned staged draw with scoped current-camera rotation and deterministic cleanup. One box emits all twelve edges in one submission. Existing 1.21.1 context behavior remains. A temporary stage has allocation/upload costs (at least 262,144-byte GPU allocation granularity); no shared vanilla stage is borrowed or closed.
- [Rendering authoring interfaces](2026-11-01-render-interfaces.md): real node-specific `event.register(id, builder => { ... })`, sealed builders, strict legacy object compatibility, strict HUD/world options and preserved shorthands. Runtime validation and TypeScript shapes share canonical schemas; opt-in callable event metadata preserves the ordinary event emitter path.
- Unknown options, wrong types, nonfinite/fractional/out-of-range priorities now reject registration rather than being ignored/truncated. Modern blur radius `[0,64]` and integer rounds `[1,8]` reject invalid values instead of clamping. Node-specific shader fields remain distinct. New diagnostic identifiers are `NEKO-4060`, `NEKO-4061`, `NEKO-4062`, and `NEKO-4063` (invalid declaration input metadata; existing authored-class diagnostic `NEKO-4033` is preserved).
- Bundled [client-rendering handbook](../../common/src/main/resources/nekojs/authoring/handbook/client-rendering.md), index/Skill routing, PData compatibility guidance, and the bilingual [client rendering](../../wiki/client-rendering_us.md), [data](../../wiki/data-and-state_us.md), [Probe](../../wiki/probe-type-generation_us.md), style/platform/event/diagnostic pages describe the current contracts. The existing manifest generator owns bundle hashes.

## Verification record

Initial red runs and isolated orchestration results are detailed in the linked diagnosis reports. They are not reused as final green evidence. The first integrated common run compiled production successfully, then failed one of eight tests in the new inherited-input fixture (`NoSuchElementException` at `ScriptInputShapeDeclarationTest.java:129`); the node phase did not execute. The inherited fixture was corrected to inspect the real superclass contract, and the full common/TypeScript runs passed. A later actual-Graal 1.21.1 run exposed deterministic overload ambiguity: the function was converted to the legacy Map before the callback ran (`needs chainJson or program; callback entered=false`). This was a runtime defect, not another syntax-fixture failure. Finite script dispatch now selects callable inputs before map conversion while retaining the typed Java overloads. Final declaration review also found proxy methods reappearing through interface heritage despite hidden overrides. Five common regressions first failed in three cases; the shared reflector now omits only nonempty interface contracts whose concrete instance methods are all hidden, retaining marker and partially visible interfaces. Independent review then identified abstract hidden declarations incorrectly qualifying as concrete implementations; two new regressions failed with expected interface count 1 versus 0. Retaining abstract resolutions fixed both; all seven heritage cases pass. Node regressions check the actual payload and builder reflection/rendering as well as callback entry, sealing and candidate poisoning.

The first full five-node build passed production compilation and platform gates but failed ordinary node tests with `Java heap space`. The original declaration-parity test reproduced the failure on 26.2 with one worker and the default test heap, ruling out concurrent node execution as the sole cause. Stack traces identified whole-tree Gson copies and full-string hashing; removing those peaks passed the original 26.2 test, but the integrated rerun still failed older nodes with a larger declaration closure. Further isolated traces located retained rendering/closure IR, simultaneous emitted/member-copy trees, and full-string publication. Rendering now joins all package work, transfers emission records from the producer to the index once, and releases both completed render and closure caches. The index adopts its exclusively owned class records and resource arrays; Binding views still deep-copy their records, including nested parameter data. Compact UTF-8 hashing and temporary-file publication are streamed. Existing content is compared byte-for-byte, cleanup runs on failure, and the pointer switches only after a verified complete file. Ordering, identities and original parity assertions remain unchanged; no heap increase, class-graph shrink, exclusion, or assertion weakening was used. The unchanged original older-node two-generation parity test passed in isolation. Hash/byte-identity, retry, failed UTF-8 cleanup and producer-transfer regressions accompany the fix; the final integrated rerun below is separate evidence.

The integrated run also exposed two stale contract fixtures, not additional runtime failures: raw Java method enumeration included hidden proxy protocol hooks, and the recipe phase trace searched the old unchecked event post name. The first now asserts the exact actual TypeReflector-visible declaration operations, both registration overloads and absence of proxy heritage; the second follows checked posts while retaining all pre-/post-commit and exactly-once assertions. Their minimized node rerun passed before the final matrix.

| Check | Current result |
|---|---|
| Focused common schema/Probe tests | Passed in the subsequent full common run; inherited fixture now asserts the superclass contract rather than inventing local IR members |
| Actual-node world/builder/registration regressions | Passed after both syntax-fixture and real overload defects were fixed: 1.21.1 and 26.2 each ran 7 real-Graal builder + 2 registration cases; 26.2 additionally ran 5 world pipeline cases. All 23 passed, no errors/skips; both NeoForge nodes compiled |
| Both Fabric bridge/save-shape regressions | Passed: 6 caller + 7 save-shape cases per node, 26 tests total, no failures/errors/skips; both nodes compiled |
| Full common isolation/processor and TypeScript checks | Passed: `:common:check :common-api-processor:test :common:assemble guardLint` and `npm run test:probe-types`; common 2,672 tests across 365 suites, 0 failures/errors, 4 skips |
| Supported-node build/source/guard checks | Passed: all five node `build` tasks, all three NeoForge `nbtSmokeTest` tasks, five fresh source-root probes, and all four CI gates; node reports contain 472 / 715 / 715 / 438 / 438 tests respectively, 0 failures. Gradle completed in 2m 18s; the following TypeScript checks also completed successfully |
| Wiki strict structure, examples, anchors and whitespace | Passed: 30 topics/0 issues; 8 tooling tests; 60 pages/280 JS-family syntax blocks; generation idempotent, all 52 baseline numeric-anchor sequences stable; 234 local evidence targets checked; diff whitespace clean |
| Bundled artifact/resource hash comparison | Passed: both JARs contain identical 33 source-matching payloads, bundle ID `2bc8d49fce1125062df19c8a884ba2caca1a29232ddde2101b99f045e031fa17`; standalone class isolation and Java 21 bytecode checks passed |

The three NeoForge `platformGateTest` declaration-only regenerations passed after the final common run. Reviewed changes are limited to six snapshots: CLIENT and STARTUP for 1.21.1, 26.1.2 and 26.2.0. Each replaces the incorrect single `Object` subscription for `hudRender`/`worldRender` with two callback-last registration signatures, exact layer/options unions, actual context/graphics imports and boolean returns. No SERVER/TEST snapshot or unrelated event declaration changed. These are intentional corrections to match the existing runtime registration contract, not golden updates to mask a test failure. Full node builds then check these snapshots without regeneration.

The source-matrix CI driver passed all five active-node selections and restored active `26.1.2`. One overlapping diagnostic run was canceled and discarded after discovering that the driver snapshots/restores shared sources; the fresh serial rerun above is the causal evidence. No production edits were made during that overlap.

Independent read-only standards/spec reviews found a caught accessor-arity poison bypass; both builders were corrected and a real-Graal regression was added. Final review found no outstanding concrete issue, but does not replace executed tests.

## Final verification commands

The final combined Gradle and TypeScript run exited 0. The Gradle daemon used Zulu 25, while the common test toolchain remained Java 21. No test heap increase, exclusions, or golden regeneration flags were used.

```sh
./gradlew.bat "-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25" :common:check :common-api-processor:test :common:assemble guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest --console=plain
npm run test:probe-types
```

After the Gradle build finished, the serial CI probe run also exited 0. Its environment exported the same Zulu 25 `JAVA_HOME` and placed its `bin` first on `PATH`. Each source-root report was verified newer than the probe job start, rather than accepting old files.

```sh
for node in 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric; do
  python tools/nekojs-ci-gates.py source-roots --node "$node" || exit $?
done
python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json
node scripts/wiki-docs.mjs sync
node scripts/wiki-docs.mjs stamp --all
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
git diff --check
```

The final artifact audit rechecked both rebuilt common JARs against canonical source payloads, their manifest hashes, standalone class isolation and Java 21 bytecode; all passed. The wiki remains draft/pending human review: automated checks do not constitute maintainer approval.

## Remaining acceptance

No live Minecraft client/server, applied-Mixin persistence, actual world restart/multiplayer sync, GPU/depth/alpha or maintainer acceptance is claimed. A CPU recording fixture cannot prove real vertex/index buffers, numerical matrix math, target selection, or GPU visuals. Late rendering inherits vanilla depth state, which may already have been cleared by the always-on-top pass. Older rendering paths retain their existing transform behavior. Fabric registration restrictions remain; a compatible helper does not add a Fabric post-effect/world-render event. Python callable-registration rendering is not implemented by this change.
