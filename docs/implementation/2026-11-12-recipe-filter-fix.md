# RecipeFilter compatibility repair — 2026-11-12

## Scope and intentional migration

The repair follows the source comparison in [the investigation](2026-11-12-recipe-filter-comparison.md). It changes the native runtime and canonical Probe inputs, not the user-supplied generated declarations or the upstream checkout. No Rhino recipe engine, parallel runtime owner, new production dependency or release-version bump is introduced.

- `type` selects `BuiltInRegistries.RECIPE_SERIALIZER`, distinguishing shaped and shapeless crafting. Use `recipeType` for the previous broader native recipe-type selection. Both normalize missing namespaces to `minecraft`.
- ID filters accept `*` / `-`, native JS RegExp values, Java Pattern and slash-pattern strings; ID/mod/group regex parsing shares one implementation. Native JS `test` preserves the caller’s `lastIndex` while evaluating every candidate from zero. Slash strings use Java `find` and `dixmsuU`, not an approximation advertised as full JavaScript regex.
- A function receives one native, read-only `RecipeMatchContext`. `recipe` is the Bean property of `getRecipe()`; no conflicting `recipe()` method is exposed. The recipe view supplies identity, mod, serializer and group. Only actual boolean returns are accepted. Event selection catches invalid recipe decoding separately and allows filter/callback exceptions to reach the owning script.
- Input/output selectors reuse native Ingredient, ItemStack and component codecs. Count is ignored; normal stack matching constrains the item and explicit component patch, while exact concrete-stack matching compares all native components. Unknown nested keys and unsupported match-info fields fail. Plain ID/tag branches preserve their existing delayed-registry behavior.
- Probe metadata declares native self values, regexes, typed context callbacks, recursive groups and supported item-match forms. Exact output declarations require stack inputs rather than promising unsupported Ingredient equality. Serializer and native-type completion aliases are collected from the actual distinct registries on both platforms.
- Typed callback shapes use the existing adapter-shape catalog and existing TypeScript/Python renderers. No generic SAM-alias merge or handwritten generated filter union is used.

## Evidence and diagnostics

Six regressions were first run against unchanged production sources: two real-native serializer/context cases, two real-native ingredient/component cases, and two canonical declaration cases. All six failed. These were behavioral RED results, not missing-production-class compilation failures.

Subsequent checks caught and corrected integration/fixture problems without weakening the original assertions:

1. A `recipe()` method masked the new Bean property; remove that unreleased alias and verify both `match.recipe` and `match.getRecipe()` with the real production HostAccess/options.
2. Native 1.21 custom-name component codecs require a different serialized shape from modern versions. The regression derives its guest JSON from the current node’s native patch codec and first verifies direct ItemStack conversion; it still selects one named output and rejects another of the same item.
3. A permanent strict platform proxy leaked into the native schema scanner and Probe renderer. The two new consumers now own a shared, classpath-visible test-only platform scope, preserve any pre-existing platform, and release only what they installed. Common’s private test helper is not a node test dependency.
4. Native loading-service fixtures are NeoForge-only. Only native fixture-dependent declaration methods are guarded; pure alias assertions remain compilable on Fabric.
5. Real host invocation with recipe-context property reads exposed Graal’s generic two-argument SAM conversion taking precedence over the default LOWEST adapter. The resulting callback received a RecipeHolder, not RecipeMatchContext. RecipeFilter now explicitly uses the existing HIGH conversion precedence; constant true/false callbacks alone were not treated as evidence of the correct argument.
6. Real catalog tests revealed missing production `RecipeSerializer`/`RecipeType` exports that an artificial registry fixture would have hidden. The strict consumer now uses actual platform registry snapshots and the real special-types renderer.

Coverage includes real decoded holders, shaped versus shapeless serializer selection, namespace normalization, wildcard/negative controls, deterministic global/sticky JS regexes, native context callbacks, non-boolean/throwing callbacks, real host event argument conversion, native component subset/exact matching, count independence, host-value copies, recursive item inputs and invalid keys. The real event callback test reads context Bean properties, so a generic two-argument SAM cannot pass merely by returning a constant.

## Verification

Final verification passed. Failed intermediate compilation/fixture runs described above are diagnostics, not counted as passing runs.

| Check | Final result |
|---|---|
| `:common:check` and `guardLint` | PASS; common report: 2,991 tests, 0 failures, 4 existing skips |
| `:1.21.1:nativeRecipeProbeTest` | PASS; 27 tests, 0 failures, **0 skips** across 7 test classes |
| Canonical declaration tests on all five nodes | PASS; 2 ordinary cases per node, no failures/skips |
| All five `compileTestJava` tasks, including production compilation | PASS |
| Focused 1.21.1 adapter/catalog/filter tests and 26.1.2 adapter tests | PASS; the older ordinary `RecipeFilterApplicationTest` reports 7 registry-availability skips, not 7 executed passes |
| `:1.21.1:jar` and `:addons:nekujs:build` | PASS |
| `pnpm run test:probe-recipe-filter` | PASS; actual newly generated aliases, reflected read-only context and real registry completions; strict/noEmit/skipLibCheck:false |
| `pnpm run test:probe-types` | PASS; complete existing declaration-consumer chain |
| Wiki `sync`, `stamp --all`, `check --strict` | PASS; 32 bilingual topics, 0 problems |
| `node --test scripts/wiki-docs.test.mjs` | PASS; 8 tests, 0 skips/failures |
| `git diff --check` | PASS |

The final combined Gradle run completed successfully in 3m32s (90 actionable tasks). Its applicable task list was:

```sh
./gradlew --no-daemon :common:check guardLint :1.21.1:test --tests "*RecipeFilter*" --tests "*RegistryTypeCatalogsTest" :1.21.1:nativeRecipeProbeTest :26.1.2:test --tests "*RecipeFilterDeclarationTest" --tests "*RecipeFilterAdapterTest" :26.2.0:test --tests "*RecipeFilterDeclarationTest" :26.1.2-fabric:test --tests "*RecipeFilterDeclarationTest" :26.2.0-fabric:test --tests "*RecipeFilterDeclarationTest" :1.21.1:compileTestJava :26.1.2:compileTestJava :26.2.0:compileTestJava :26.1.2-fabric:compileTestJava :26.2.0-fabric:compileTestJava :1.21.1:jar :addons:nekujs:build --continue --console=plain
pnpm run test:probe-recipe-filter
pnpm run test:probe-types
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
```

Bundled Node/pnpm were used on Windows without changing package-manager configuration. CI now explicitly runs the native gate and its strict consumer after the existing version builds, using the workflow’s existing Node/npm setup. The ordinary application-test skips are not used as runtime evidence: real host selection and native codec cases execute in the mandatory zero-skip 1.21.1 gate. Modern/Fabric live-world selection and full five-node `build`/GameTest execution were not performed locally. Both new fixture users and the older item-component test now use the same scoped platform fixture; no assertions or isolation gates were weakened. Gradle/native-access deprecation warnings remain warnings, not failures.

## Compatibility limits and deployment

- This is not upstream KubeJS RecipeMatchContext, Rhino `cx`/`ops`, mutable recipe schemas or schema-aware fluid/addon ReplacementMatchInfo.
- Guest regex/predicate values, including scripted Ingredient predicates, remain owned by the current script generation and execution thread. The closed-context tests cover normalized native values and DTOs, not arbitrary guest callbacks.
- Native ingredient overlap is based on available representatives/concrete witnesses and both acceptance predicates. Exact Ingredient input uses native equality. Exact output requires a concrete stack.
- Modern result/ingredient inspection retains the existing shaped/shapeless/single-item/cooking facade boundary; arbitrary custom serializers are not certified.
- No live client/world acceptance is claimed. The previously supplied generated declarations remain untouched. Use the rebuilt engine/addon, restart the instance and invoke its normal Probe generation to refresh that instance’s declarations; generated test consumers are separate evidence.
- Changes are committed only on explicit maintainer request; no push or deployment is implied. Human live-client/world acceptance remains pending.
