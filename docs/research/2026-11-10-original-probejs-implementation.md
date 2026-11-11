# Generic NekoJS Probe input projection following ProbeJS 8.0.3

## Objective and primary sources

Optimize NekoJS Probe itself, not a declaration patch for one addon. The [primary-source investigation](<2026-11-10-original-probejs-recipe-model.md>) checks local source against the installed original ProbeJS 8.0.3 artifact. The implementation independently uses NekoJS neutral type IR and existing metadata channels; it does not copy the GPL emitter, import a second evaluator, or change licensing.

## Shared core implementation

- [Shared IR renderer](<../../common/src/main/java/com/tkisor/nekojs/probe/ir/TypeScriptClassRenderer.java>) owns both input use-contexts. Native Java parameters preserve erased adapter payload identity; converter-owned declared inputs recursively project ordinary generic arguments following the original model. Holder and SAM payloads remain nominal. Existing reflection wildcard/contextualization is retained.
- [Type alias registry](<../../common/src/main/java/com/tkisor/nekojs/probe/types/TypeAliasRegistry.java>) is the single source of builtin collection input names, shared by references and the [package emitter](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/IndexFileGenerator.java>). No synthetic List adapter is registered.
- [Recipe emitter](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java>) delegates to that shared renderer. Nominal component metadata comes through the neutral codec/catalog contracts; exact structural shapes remain the fallback. There is no private recipe projection algorithm, SingleOrArray helper, whole-field/domain-alias layer, or addon-name branch in Probe.
- Actual constructor profiles, safe optional-tail coalescing and named options remain authoritative. The original constructor-key/noFunctions omission is deliberately not copied. List-only metadata remains list-only; can_write_self explicitly adds the scalar alternative.
- Module emission and forced dependency collection use the same applicability guard. Builder dependencies are collected only when declarations use the builder, so an empty namespace does not create an unavailable phantom root.

## Truthful adapter metadata, not guessed addon inputs

Registered input shapes are consumed through the existing generic adapter metadata channel for every target type. Opaque converters without declared input forms retain nominal class types, as in the original model. The temporary ProcessingOutput/Create-factory input-shape special case was removed completely. Runtime acceptance of an object does not authorize Probe to invent a DTO from an addon name.

Native generic behavior is empirically different from indiscriminate recursive alias widening. Guest List/Collection/Iterable arrays and Map record values can convert elements through adapters; genuine host collections retain identity. Consuming an incompatible host List fails. A registered raw Box adapter does not infer or convert its T payload, including when nested in List or Map. The [production HostAccess tests](<../../common/src/test/java/com/tkisor/nekojs/probe/ir/TypeScriptCollectionInputTest.java>) exercise those distinctions rather than assuming Rhino behavior.

The [shared renderer tests](<../../common/src/test/java/com/tkisor/nekojs/probe/ir/TypeScriptDeclaredInputModelTest.java>) use unrelated generic Box/Date types, SAMs and holder types. The [nominal model tests](<../../common/src/test/java/com/tkisor/nekojs/probe/OriginalRecipeTypeModelTest.java>) additionally exercise foundry, tooling and native_custom namespaces through the same producer. Converter-owned metadata remains a contract supplied by its author, not proof that every arbitrary custom converter recursively converts payloads.

## Separate runtime compatibility work

Necessary NekuJS conversion adaptations remain outside Probe. Standard inputs delegate to native converters before component codec validation. Unchanged registered addon wrappers perform their original conversion; shared ItemWrapper structured conversion delegates to the native item-stack converter. Fluid defaults, supported ingredient forms, and TickDuration unit strings use real native conversions. Wrapper coercion does not bypass codec bounds: a chance clamped to zero still fails a positive-value codec. JSON constants retain their separate codec path.

Heat/Mekanism custom codecs keep existing bounded conversion paths when registry-aware legacy context is unavailable. No new global registry service, fake class, production initialization fallback or runtime reset is introduced. Test-only NeoForge serializer bootstrap uses genuine codecs and is idempotent.

## Verification and generated artifacts

Passed complete Gradle gate:

~~~bash
./gradlew --no-daemon guardLint :common:check :1.21.1:build :addons:nekujs:build :addons:nekujs:fixtureTest :addons:nekujs:expandedFixtureTest :addons:nekujs:compatProofTest --console=plain
~~~

The common report records 2911 tests, zero failures and four skips; both supported Probe backends and existing declaration/availability regressions are included. Actual unchanged Create/Mekanism resources and EnderIO registrations cover 71 definitions / 50 callable profiles. Managed original-wrapper conversion and three-recipe JSON pipeline/bounds checks pass. The [generated preview](<../../addons/nekujs/build/reports/compatibility/recipes-readable/index.d.ts>) contains their canonical signatures, with opaque ProcessingOutput remaining nominal rather than receiving fabricated metadata.

The complete pnpm run test:probe-types gate passed, including strict declarations, generic adapters, input shapes, recipe and Mixin language-service completion checks. The full Gradle gate was then rerun after the final bundled-handbook update and passed again. The scoped independent core review reported no concrete P0/P1 findings; native defaults and reflection wildcard handling remain unchanged. Both built client/server artifacts were inspected: they contain the shared declared-input renderer and the exact current bundled handbook, and no obsolete RecipeInputTypeProjection class. Scoped diff whitespace validation passed.

Goldens were regenerated through :common:regenerateGoldens, not hand-edited. A byte comparison against the preserved 58-file tree finds exactly one changed file: [recipe declarations](<../../common/src/test/resources/nekojs/probe/legacy-tree/@side-only/server/events/recipes/index.d.ts>), with its unused builder import removed. Dependency counts and completeness were not weakened to hide missing classes. Other pre-existing working-tree golden changes are preserved.

## Migration and remaining limits

Earlier whole-field/custom-domain TypeScript imports must follow current canonical declarations. Opaque legacy wrappers can accept runtime forms that their original public type metadata does not describe; Probe does not guess those forms. Unsupported native container conversions are not widened merely to resemble Rhino declarations.

Rebuild/restart and run /nekojs probe to replace an old live tree. This workflow does not modify a user world or live Probe output, and does not claim gameplay/world-reload acceptance or a completely error-free historical live declaration graph. Previously audited unrelated targeted-event, implicit-Context, generic/Bean, optional-dependency and helper gaps remain outside this change. No release version bump is made.

The [canonical handbook](<../../common/src/main/resources/nekojs/authoring/handbook/recipes-and-tags.md>) and [addon README](<../../addons/nekujs/README.md>) describe the shared model and bounded native differences.
