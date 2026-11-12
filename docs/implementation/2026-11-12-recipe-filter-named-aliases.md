# Readable RecipeFilter input declarations

## Contract

This follow-up changes declaration organization only. The previously repaired runtime, accepted filter inputs, serializer/type migration, callback conversion precedence and component-match restrictions are unchanged.

The canonical input shape now names four reusable sections: `$RecipeFilterExpression_`, `$RecipeFilterConditions_`, `$RecipeFilterInput_` and `$RecipeFilterOutput_`. The primary alias lists native values, expressions, context predicates, recursive arrays and conditions on separate lines. Conditions list common identity/type selectors before input/output and logical/prefix extensions. Helper aliases are TypeScript-only declarations, not Java classes or new runtime Bindings.

## Implementation

- `AdapterInputShape.named(suffix, shape)` is a transparent presentation wrapper, carried through the existing adapter catalog without a parallel declaration source.
- The TypeScript adapter generator qualifies helper names with their owning target, forwards generic parameters, deduplicates repeated sections and rejects conflicting definitions. It keeps standalone/ownerless shapes inline.
- One declaration-group emitter is used by the real package generator and canonical consumer fixture. Helpers reserve local module symbols before imported names are allocated; actual local declaration collisions fail rather than being overwritten. Helper names are not added to every Java host import.
- Only adapters opting into named parts get multiline groups. Existing unnamed aliases retain their prior bytes. Opaque multiline raw/template type text takes the conservative inline path so formatting cannot change literal contents. No new TypeScript parser is introduced.
- Python unwraps named sections; the named-versus-unwrapped fixture produces byte-identical Python output. The existing dependency collector traverses named parts and callable parameters/results. Recipe-field structural classification also unwraps the presentation layer.
- The RecipeFilter adapter changes only its `inputShapes()` metadata. Existing runtime matching code is not modified.

## Regression evidence

Before production changes, the new canonical-generator readability case failed against the existing inline declaration. Subsequent tests exercise the actual package generator, generic/self references, helper deduplication and conflicts, registry/host dependency closure, real cross-package symbol collisions, unchanged ordinary/opaque type output, Python byte parity, all prior strict input cases and mutual assignment between the previous expanded filter type and the new named form.

The strict consumer obtains its aliases from the real canonical adapters, its context declarations from the actual reflector/renderer and its registry completion types from real native registry snapshots. No generated live-instance declaration is manually edited.

## Verification

All final checks passed.

| Check | Result |
|---|---|
| `:common:check` and `guardLint` | PASS; 2,997 tests, 0 failures, 4 existing skips |
| Five named-shape backend tests | PASS; real symbol collisions, generic recursion, opaque literal bytes, ownerless rendering and Python parity |
| Declaration tests on all five nodes | PASS; 3 cases per node, 0 failures/skips |
| All five `compileTestJava` tasks | PASS, including production compilation |
| `:1.21.1:nativeRecipeProbeTest` | PASS; 27 tests, 0 failures, 0 skips |
| `pnpm run test:probe-recipe-filter` | PASS; helper imports, previous positive/negative cases and mutual old/new assignability |
| `pnpm run test:probe-types` | PASS; complete existing TypeScript consumer chain |
| `:1.21.1:jar` and `:addons:nekujs:build` | PASS |
| Wiki sync/stamp/strict check | PASS; 32 bilingual topics, 0 problems |
| `git diff --check` | PASS |

The final combined Gradle gate completed successfully in 3m16s (90 actionable tasks):

```sh
./gradlew --no-daemon :common:check guardLint :1.21.1:test --tests "*RecipeFilterDeclarationTest" :1.21.1:nativeRecipeProbeTest :26.1.2:test --tests "*RecipeFilterDeclarationTest" :26.2.0:test --tests "*RecipeFilterDeclarationTest" :26.1.2-fabric:test --tests "*RecipeFilterDeclarationTest" :26.2.0-fabric:test --tests "*RecipeFilterDeclarationTest" :1.21.1:compileTestJava :26.1.2:compileTestJava :26.2.0:compileTestJava :26.1.2-fabric:compileTestJava :26.2.0-fabric:compileTestJava :1.21.1:jar :addons:nekujs:build --continue --console=plain
pnpm run test:probe-recipe-filter
pnpm run test:probe-types
node scripts/wiki-docs.mjs check --strict
```

The [native-test generated preview](../../versions/1.21.1/build/probe-ts-recipe-filter/declarations.d.ts#L583-L626) shows the compact main alias and four named sections. The ordinary readability test additionally exercises the real package IndexFileGenerator, not just the preview writer. Complete declaration groups use the shared emitter; old flat-only groups keep their original formatting. No golden was updated to hide a failure. Java metadata visitors need to unwrap the additive NamedValue variant as the existing backends/collector do. Full five-node build/GameTest and live-world acceptance were not run locally.

## Deployment and remaining boundaries

Rebuild/restart the instance and run its normal Probe generation to update existing instance output. The native-test generated declaration is a preview, not a replacement for the full live package. This follow-up does not add runtime capabilities, broaden exact output matching, refresh a running JVM or claim live-client/world acceptance. Changes are committed only on explicit maintainer request; no push or deployment is implied. Human live-client/world acceptance remains pending.
