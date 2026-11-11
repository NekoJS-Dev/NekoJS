# Readable recipe input declarations (superseded implementation checkpoint)

This checkpoint records the earlier verified structural/domain-alias approach. The maintainer subsequently requested the original ProbeJS nominal type model instead. Current behavior is described by the [original-model follow-up](<2026-11-10-original-probejs-implementation.md>); the custom projection and its aliases below have been removed.

## Scope and interface changes

- Replaced opaque whole-field input aliases with visible container expressions and exact nominal value domains. `SingleOrArray<T>` means `T | T[]`; array-only inputs remain `Array<T>`. No Java-list conversion is invented.
- Value aliases are keyed by namespace and complete structural shape, not field name or Java host alone. The original subtree is retained for rendering. Distinct DTO requirements, literal alternatives, raw callbacks and unrelated array domains remain distinct.
- Explicit codec host leaves stay nominal instead of widening to broader class-wide adapters. Erased generic host classes retain their declared arity.
- Equivalent contiguous positional constructor prefixes coalesce into an optional-tail overload; gaps, ambiguous arities and constructor overrides prevent unsafe merging. The positional candidate precedes named options. Verified camelCase aliases improve parameter labels without changing JSON keys.
- Trailing JavaScript undefined values select the corresponding shorter constructor/defaults. Null and interior gaps are not silently removed. Named-object undefined handling is unchanged.
- Existing catalog constructor descriptors remain available. Removed whole-field aliases are a type-only migration; ordinary JavaScript calls retain their accepted inputs.

Implementation: the former `RecipeInputTypeProjection` (removed by the original-model follow-up), [constructor planning](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeConstructorOverloads.java>), [recipe emitter](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java>), [runtime argument routing](<../../common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java>). Recipe-emitted host references now participate in the existing declaration closure.

## Actual addon preview

The [unchanged-fixture test](<../../addons/nekujs/src/test/java/com/tkisor/nekujs/recipe/FixtureReadableRecipeDeclarationTest.java>) loads original Create/Mekanism resources and EnderIO programmatic registrations, then uses the canonical schema compiler, catalog and generator. It asserts 71 definitions / 50 callable profiles. It emits the [recipe preview](<../../addons/nekujs/build/reports/compatibility/recipes-readable/index.d.ts#L508-L513>) through the test workflow, not by editing generated text.

The resulting cutting interface has two candidates: an optional-tail positional constructor with visible scalar/array and value-domain types, plus named options. The fluid value domain keeps required amount; the processing output domain keeps numeric optional count/chance. Longer signatures wrap at parameter boundaries rather than hiding field types.

## Verification

The initial opaque-type regressions failed before implementation. A subsequent callback-union regression also failed before the raw-expression fix. Both wrapped and bare raw callback operands are now grouped atomically; no contributor expression is parsed or unparenthesized at a union boundary.

Strict caller and actual TypeScript language-service checks cover positive/negative DTO forms, typed hosts, required amounts, numeric count/chance, unsupported ingredient filters, exactly two candidates, visible container/domain types, and contextual output property completions. Fixture bootstrap setup now respects the existing single platform owner instead of depending on test class order.

Final combined verification passed with exit 0: `./gradlew --no-daemon guardLint :common:check :1.21.1:build :addons:nekujs:build :addons:nekujs:fixtureTest :addons:nekujs:expandedFixtureTest :addons:nekujs:compatProofTest --console=plain`, followed by bundled Node/pnpm `run test:probe-types`. This includes common isolation, original addon regressions, actual 71/50-profile preview generation, safe constructor/undefined tests, strict positive/negative caller checks, wrapped and bare raw callback alternatives, and real TypeScript signature/object-completion checks. Scoped diff hygiene passed. No production or test source changed after this frozen verification.

The [canonical recipe handbook](<../../common/src/main/resources/nekojs/authoring/handbook/recipes-and-tags.md>) and [addon guidance](<../../addons/nekujs/README.md>) describe the typing and undefined semantics. No core/addon release version bump is made. Normal common resource generation refreshes the bundled documentation manifest.

## Remaining acceptance

No game was launched and no user world or live Probe tree was modified for this implementation. Rebuild/restart the client, then run `/nekojs probe` to replace its old declarations; an existing JVM cannot load these source changes automatically. World reload/machine processing and the other previously audited generic, Bean, targeting, implicit-Context, helper and optional-dependency issues remain separate work. No maintainer gameplay acceptance is claimed.
