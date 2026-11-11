# Native recipe handler declarations and vanilla shaped metadata

## Scope

The native crafting_shaped signature differs from original KubeJS for both legitimate interface reasons and declaration defects. The NekoJS 1.21.1 handler really accepts an optional group and returns RecipeJsonBuilder. Those contracts are retained; no original recipe subclass or KubeJS-only function is invented. This change fixes the independent handler declaration path and native shaped metadata rather than changing output strings to resemble another runtime.

## Shared metadata and rendering

[Handler metadata](<../../common/src/main/java/com/tkisor/nekojs/api/catalog/RecipeHandlerMethodEntry.java>) now carries neutral parameter and actual return types. Existing constructor overloads remain available to earlier contributors. [Collection](<../../common/src/main/java/com/tkisor/nekojs/api/catalog/RecipeNamespaceCatalogEntry.java>) preserves every visible runtime overload instead of keeping only the largest arity. It uses the same eligibility and required-arity helpers as execution.

The [recipe emitter](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java>) uses shared native input projection and output projection for handlers. Its old collection-specific rendering and guessed import suffixes are removed. Legacy string descriptors are parsed into the neutral model, not rendered through a second type policy. Return-type and parameter imports are collected through those same rendering callbacks. Reflection conversion is shared through [JavaGenericTypes](<../../common/src/main/java/com/tkisor/nekojs/core/reflect/JavaGenericTypes.java>) rather than making the catalog depend on Probe implementation classes.

Both shaped overloads remain: pattern plus key map, and an inline ingredient grid. Optional tails are omitted only where execution supports omission; nontrailing Optional parameters do not make required arguments optional. Java varargs retain one array argument, not invented spread support. Unbound generic variables use JVM bounds; inherited owner specialization is not falsely advertised.

## Runtime conversion and metadata accuracy

The [existing native dispatch helper](<../../common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java>) previously used only erased Class conversion. Guest List/Collection/Iterable and Map values now preserve reflected element types by traversing those supported containers and invoking existing root-owned Graal adapters at the leaves. Host collections and arbitrary raw generic adapters retain identity/erasure. No fresh context, private TypeLiteral mutation, new DSL, mod-name branch or new runtime owner is added. Dispatch order is unchanged; conversion mismatches can try another overload, while adapter and handler execution failures still propagate. Null non-Optional values retain the native invocation path rather than silently falling back.

[Native schema discovery](<../../src/main/java/com/tkisor/nekojs/api/recipe/definition/MinecraftRecipeSchemaScanner.java>) recognizes exact registered vanilla serializer identity. Shaped fields are result, pattern and key; shapeless fields are result and ingredients. Whole-field codecs carry nominal collection metadata and use existing item/ingredient/string conversion. A similarly named foreign serializer does not acquire a vanilla schema. Existing unrelated discovery fallbacks are outside this correction.

## Verification

Captured declaration RED: four failing native handler assertions. Captured discovery RED: two common codec propagation failures; the initial node test was not reached after the common failure. Runtime tests exposed erased generic conversion and arity/visibility defects; the varargs fallback assertion was corrected to reflect the existing raw fallback contract, not used to invent stricter namespace behavior.

Focused repaired handler execution, declaration and strict-fixture generation passed. The complete common suite records 2934 tests with zero failures and four existing skips; the full TypeScript suite including native handlers passed. Initial node runs skipped every scanner case because the fixture lacked real loading services; those runs are not counted as native integration coverage. A fresh genuine 1.21.1 run executed all six scanner/integration tests with zero skips/failures, produced the actual declaration preview and validated normal/inline shaped JSON through the vanilla serializer. Bootstrap is now isolated in the mandatory nativeRecipeProbeTest verification task instead of changing ordinary test workers. Modern bare-JVM runs report the absence of an active FML Loader explicitly rather than fabricate one. The final full matrix passed in 5m 8s (123 actionable tasks), including common isolation checks, guard lint, all five platform builds, mandatory isolated native recipe checks, and all three addon fixture/proof tasks. No golden was hand-patched. The test suite uses consuming handler bodies, exact JSON conversion, host identity, Optional tails, generic erasure, failure propagation and strict positive/negative TypeScript calls, not declaration snapshots alone.

### Reproduction commands

```bash
./gradlew --no-daemon :common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :addons:nekujs:build :addons:nekujs:fixtureTest :addons:nekujs:expandedFixtureTest :addons:nekujs:compatProofTest --console=plain
./gradlew --no-daemon :1.21.1:nativeRecipeProbeTest --console=plain
pnpm run test:probe-types
```

The node build/check tasks depend on the isolated nativeRecipeProbeTest. Modern native integration tests remain explicitly skipped without an active FML Loader; the 1.21.1 integration is mandatory and must not skip.

The generated [1.21.1 preview](<../../versions/1.21.1/build/reports/native-recipe-probe/index.d.ts>) and [vanilla JSON validation status](<../../versions/1.21.1/build/reports/native-recipe-probe/runtime-status.txt>) are separate build artifacts, not modifications to the user live declarations.

## Delivery constraints

No live user Probe tree or world is overwritten. Rebuild/restart and regenerate Probe through the normal command to refresh the instance declarations. The native optional group, keys parameter name and actual base-builder result are intentional differences from KubeJS, not bugs to hide. There is no claim of exhaustive vanilla JSON schema coverage, gameplay acceptance, or new generic owner reification.
