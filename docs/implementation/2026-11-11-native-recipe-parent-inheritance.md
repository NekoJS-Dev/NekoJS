# Native recipe parent inheritance and wiki audit

## Authorized scope

Audit the bilingual wiki against the current implementation, then repair the confirmed native-parent inheritance gap behind four original Mekanism addon declarations. No generated live instance declarations are hand-edited. The fix belongs to shared native parent resolution and canonical NekoJS construction, not four recipe-name exceptions or a Rhino/second recipe runtime.

## Captured evidence

The original installed kubejs-mekanism 2101.1.7 build 18 resources contain only parent aliases: mek_data inherits shaped; bin_extract, bin_insert and clear_configuration inherit special. The actual live declaration tree emitted all four as never, and latest.log reported NEKO-5242 at each resource's parent path. The reproducible build-report script checks those exact declaration and log pairs and reports all four blocked (exit 1).

Original upstream special is not an empty schema: category uses the native CraftingBookCategory codec with default misc, and its only positional constructor is zero-argument. Hidden is inherited metadata upstream, not a runtime invocation gate. Original shaped includes a factory and component/cleanup semantics; importing a name or inventing an empty parent would not be a valid fix. Upstream source versions differ, so the implemented native capability is explicitly bounded rather than claiming universal factory equivalence.

Primary-source references: [upstream 2101 shaped parent](https://raw.githubusercontent.com/KubeJS-Mods/KubeJS/2101/src/main/resources/data/minecraft/kubejs/recipe_schema/shaped.json) and [special parent](https://raw.githubusercontent.com/KubeJS-Mods/KubeJS/2101/src/main/resources/data/minecraft/kubejs/recipe_schema/special.json).

## Design

- Supply trusted in-memory native parent dependencies through the graph's existing resolution path. Resolve arbitrary child IDs, preserve child serializer identities and explicit overrides, and do not publish synthetic dependency roots. Explicitly owned parent documents take precedence.
- Keep abstract base dependencies, unknown parents, cycles, unknown custom factories/processors/mappings and invalid component codecs fail-closed. Accept both known factory-property spellings with conflict checks; never silently ignore a custom factory.
- Correct hidden metadata so it no longer incorrectly denies runtime construction. This is a deliberate compatibility correction, not a bypass of unsupported semantics.
- Reuse existing ItemStack and Ingredient encoders; add strict string, native crafting-category and single-character ingredient-map bindings. Native shaped cleanup removes empty ingredient mappings, rewrites their pattern symbols to spaces, validates remaining symbols and retains the actual child type.
- Use the canonical RecipeExecutionFactory and borrowed RecipeBuilderSession beforeEncode lifecycle. A default validation hook checks detached encoded fields before native builder allocation; later fluent changes are checked again at final encoding. Existing execution factories retain their default behavior.
- Native mirror/shrink defaults are supported. Non-default KubeJS mirror/shrink controls are not advertised by the native parent, and injected unsupported controls fail explicitly rather than being silently serialized as effective features.

## Regression workflow

The pre-fix focused Gradle run executes the real graph and original-addon pipeline: 21 graph tests with four failures, plus the new original-Mekanism pipeline test failing at unsupportedReason. This is behavior-level RED, not a compilation error. The integration test reads unchanged original jar resources, uses real native ItemStack/Ingredient adapters and Graal host access, preserves all child type IDs, verifies category/default/zero-argument construction, and checks shaped JSON with the genuine vanilla serializer. It does not claim a running-game or actual loaded Mekanism serializer acceptance result.

The repaired focused run passes all 21 graph tests and the original-addon pipeline through prepareRecipesForDecode, the same lifecycle used by the native RecipeManager. Native shaped codec validation covers host and guest inputs after cleanup; invalid constructor input does not allocate another recipe. A readonly review caught lowercase category literals being overridden by nominal Java enum names. Bindings now retain codec-specific structural literal metadata, including propagation through supported containers.

The first full matrix passes common isolation and 2952 common tests (zero failures, four existing skips), guard lint, five platform builds, native 1.21.1 checks and addon build/proof tasks. Overall Gradle still reports failure because existing fixture profile-count assertions and a repeated test-helper platform initialization require test-only corrections. The original-resource audit accounts for seven newly callable profiles: the four reported Mekanism aliases, create:mechanical_crafting, mekanismtools:paxel and mekanismtools:banner_shield. All seven inherit the same two native parents; the three extra child resources are not themselves explicit hidden declarations. The base fixture has 48 callable profiles out of 62 original documents, with thirteen abstract bases and unsupported nested sequenced assembly still rejected. Expanded coverage is 57 callable profiles out of 71. Final verification passed: the complete normal matrix finished in 3m 52s (123 tasks: 17 executed, 106 up-to-date), including all fixture, expanded-fixture and compatibility-proof gates. The full Probe TypeScript and language-service completion suite also passed. The final original-addon pipeline runs with zero skips/failures in the full fixture worker, and writes a generated declaration preview and runtime-status report under addons/nekujs/build/reports/native-recipe-parents. The reused fixture initialization is now success-only, synchronized and once per JVM; no production initialization guard or test skip was introduced.

Remaining acceptance limits: no new live-world or loaded Mekanism-serializer gameplay acceptance was performed. Genuine vanilla shaped decoding and native category codecs are distinct from that acceptance. Non-default KubeJS mirror/shrink controls remain unavailable, and unknown factories/processors/mappings remain rejected. Ingredient coercion continues through the native converter, which may accept runtime forms beyond nominal hints; this change does not claim to exhaustively redefine that adapter domain. Update both NekoJS and NekuJS, restart the instance and export Probe again. Existing live generated declarations and worlds were not overwritten.

## Wiki audit

All 31 bilingual topics were assigned and reviewed against relevant sources. Targeted corrections cover recipe/Probe typing and diagnostics, module publications, Node shim paths and restrictions, parser/type-checking behavior, platform/build scope, plugin lifecycle/event exposure, annotations, JSX availability and bounded Mixin evidence. The FAQ no longer incorrectly assumes a named Create recipe universally lacks a schema. The error reference adds existing schema-import codes. Existing dirty work was preserved.

The canonical wiki sync workflow regenerates anchors/navigation. Source citations use repository blob URLs, not broken relative links into a separate GitHub wiki. Structural validation reports 31 bilingual topics and zero problems. Translation stamps remain draft with human review pending; no publication or maintainer acceptance is fabricated.
## Final commands

~~~bash
./gradlew --no-daemon :common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :addons:nekujs:build :addons:nekujs:fixtureTest :addons:nekujs:expandedFixtureTest :addons:nekujs:compatProofTest --console=plain
pnpm run test:probe-types
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
~~~

All commands passed. The wiki checker reports 31 bilingual topics and zero problems; all eight wiki-tool tests pass. Draft translation/human-review status is retained, and no GitHub push or wiki publication was performed.
