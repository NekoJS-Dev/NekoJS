# Wiki-audit bugfixes — 2026-11-12

## Scope and contracts

These are the follow-up fixes authorized after the [bilingual Wiki audit](2026-11-12-wiki-audit.md). The pre-existing uncommitted Wiki audit is preserved; no unrelated local files, release version, wire contract or public event payload is changed.

### Explicit recipe AND arrays

`RecipeFilterAdapter.apply` previously passed an explicit `and` array back through its generic array branch, which constructs OR. It now parses that array’s children into `RecipeFilter.And`. The shared adapter fixes all nodes using this implementation without separate loader logic. Plain arrays and `or` remain OR; flat object fields remain AND; non-array groups, nested filters, unknown-key rejection and ignored null children retain their prior semantics. Empty AND matches everything; empty OR and an empty plain array match nothing. This intentionally corrects the erroneous broadening of recipe selection. Packs relying on the erroneous OR behavior must use explicit `or` or a plain array.

### Malformed script properties

`ScriptProperty.read` swallowed parser exceptions and returned null; `ScriptContainer.preload` then stored null over a prior successful value. The reader now propagates the original failure, retaining its nullable unparsed-result contract. At the existing preload boundary, a null result is rejected before storage. One `NEKO-1025` warning per invalid directive records the key, file, physical one-based line and original cause (or a null-result validation cause). The last successful value survives; an invalid initial value leaves the default; subsequent valid directives still win. The warning does not disable the script.

Unknown property names and directives after the opening header are still ignored. Presence-based `disable` and comma-separated list parsing are unchanged. `ScriptProperties.put(property, null)` remains an explicit storage-clear operation. Direct callers of the internal reader now receive its parser exception instead of a silently synthesized null; the repository production caller is the preload boundary.

### Canonical welcome example

The unchanged bundled example used `event.player` even though NeoForge sends its native login event with the Bean property `event.entity`. The canonical source now targets NeoForge 1.21.1/26.x using `entity`; its comment, metadata and exact matching cookbook source specify the single `entity` to `player` adaptation for Fabric. A nullish fallback would still violate current platform-specific callback member validation, so the example does not falsely claim one unmodified payload contract. The Fabric-neutral wrapper Javadoc no longer claims parity with a nonexistent native `getPlayer()` accessor. No event API or player facade was changed.

## Regression evidence

### Red: original symptoms captured before their fixes

- `./gradlew --no-daemon :common:test --tests "*ScriptPropertyParsingTest" :26.1.2:test --tests "*RecipeFilterAdapterTest" --tests "*RecipeFilterApplicationTest" --continue --console=plain` failed: both prior-value preservation tests returned 0 instead of 7; two adapter regressions exposed explicit AND becoming OR and the incorrect empty-AND identity. The seven bare-JVM application tests were skipped, not counted as runtime evidence.
- The original shipped source failed the guest NeoForge-entity example test. After aligning the new Bean fixture with the production Nashorn/ECMAScript option sequence, the actual module-pipeline `entity` case failed at the callback while the `player` positive control passed. An earlier fixture boot failure caused by Nashorn’s ES5 default is not production-bug evidence.
- For diagnosis, hypotheses were ranked as wrong shared parsing branch, faulty child-reader/filter behavior, then later storage/matching behavior. The reproductions and primary source selected the shared-branch failures described above.

### Green: focused contracts

```bash
./gradlew --no-daemon :common:test \
  --tests "*ScriptPropertyParsingTest" \
  --tests "*AuthoringExamplesEngineTest" \
  --tests "*AuthoringScriptExamplesTest" \
  --tests "*AuthoringBundleTest" \
  :26.1.2:test --tests "*RecipeFilterAdapterTest" --tests "*RecipeFilterApplicationTest" \
  :1.21.1:nativeRecipeProbeTest --tests "*RecipeFilterAdapterTest*" --console=plain
```

Passed: property parsing 9 tests; actual authoring engine 13; source/example contracts 8; bundle contracts 13; adapter contracts 10; isolated 1.21.1 native selection 1. All these executed with zero skips/failures. Seven ordinary 26.1.2 recipe-application cases remain skipped because their item-component precondition is unavailable in the bare worker.

The native tagged selection fixture initializes the existing platform loading-services seam, uses real JS input and real recipe holders, and covers both conditions, either single condition, neither, OR controls, flat/scalar AND, negation, nesting and empty groups. Its new bootstrap condition mirrors the existing native schema scanner: assert isolated metadata when the node supports it; otherwise report an assumption skip. No existing gate or assumption was weakened and no modern FML loader was fabricated. The 1.21.1 selection test executed without skips; it is not evidence of live modern-loader execution.

The common standalone test runtime originally had SLF4J API but no provider; the warning assertions correctly found zero events. A testRuntimeOnly Log4j SLF4J2 binding using the existing Log4j catalog version makes actual production logging observable. This is a concrete test-infrastructure need, not a shipping dependency or a fake/private logger injection. Exact code, line, warning count and throwable identity assertions then passed.

## Canonical documentation and generated resources

The recipe/property/error-reference and AI-support Wiki pairs describe the repaired behavior, retaining their headings and explicit anchors. The canonical bundled recipe/tag and script-type handbooks now include the matching filter/property contracts. Welcome metadata uses the existing `real-engine-with-platform-fixture` label; it distinguishes the unchanged NeoForge source and the documented adapted Fabric copy. The existing manifest generator hashes canonical bundle content during resource processing; no generated manifest is hand-edited. This is not a release/version bump.

## Broader checks and delivery boundaries

- Full `:common:check`, `guardLint`, and all five supported nodes’ `compileJava` / `compileTestJava` passed in 2m 21s: `./gradlew --no-daemon :common:check guardLint :1.21.1:compileJava :1.21.1:compileTestJava :26.1.2:compileJava :26.1.2:compileTestJava :26.2.0:compileJava :26.2.0:compileTestJava :26.1.2-fabric:compileJava :26.1.2-fabric:compileTestJava :26.2.0-fabric:compileJava :26.2.0-fabric:compileTestJava --console=plain`. This includes the common isolation/dependency gates; it is not five-node native runtime acceptance.
- Final `node scripts/wiki-docs.mjs sync`, `stamp --all`, `check --strict`: 32 bilingual topics, zero problems. `node --test scripts/wiki-docs.test.mjs`: 8 passed. The existing changed-fence syntax harness checked 36 new/changed JS/TS fences with zero failures. A baseline comparison verified all 62 original pages retain explicit anchor identities; another canonical sync was content-idempotent. `git diff --check` passed.
- No live Minecraft client/server, modern-loader runtime, multiplayer, GUI/GPU or human acceptance was run. Bean and codec fixtures are not those acceptances. No claim is made that all pre-existing Python/authoring handbook coverage gaps are fixed.
- Lead source/diff review and a bounded independent read-only review of the three parser production files and their direct contracts found no additional issues. This is not a formal exhaustive branch review.
- At verification time no Git commit had been requested; the user subsequently authorized committing this delivery with the Wiki audit and component/result-query work. Push, release and installation into a user world remain outside this delivery.
