# NekuJS recipe translation boundary

## Status and evidence

This began as a design investigation of the six local fixture JARs, without redistribution. The Create extension contains 21 schema graph documents, including four bases; the Mekanism extension contains 41, including nine bases. The bounded implementation now compiles these resources through native NekoJS definitions; see the [addon contract](<../../addons/nekujs/README.md>) for current behavior and the [implementation evidence](<../nekujs-implementation-plan.md>) for executed checks. Graph counts are not counts of usable recipe serializers.

The translation must compile into NekoJS's existing schema contribution, runtime and declaration path. Do not build another recipe engine or invoke legacy RecipeScriptContext/RecipeComponent.wrap as a substitute evaluator. Minecraft codecs and registry access remain addon/version-facing; common contracts remain Minecraft/loader-free.

## Exact reference behavior

Reference sources are under D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/schema/RecipeSchemaData.java and JsonRecipeSchemaLoader.java. Recognized schema fields include parent, override_type, recipe_factory, keys, constructors, functions, override_keys, hidden, mappings, unique, post_processors and merge. Key metadata includes optional, default_optional, alternative_names, excluded, function_names and always_write. Constructors may override key values.

Parent resolution must reject missing parents and cycles. Merged keys preserve order and replace matching names; required/optional ordering remains meaningful. Functions inherit and child definitions override even without a merge.functions flag. In this exact decoder, merge.functions, Mekanism reaction's builder_names and shaped's factory are unknown fields, not additional behavior to invent.

Upstream optional defaults initialize logical values but are omitted from JSON until explicitly set or always_write applies. NekoJS currently writes defaults immediately. Existing native definitions must retain their current behavior through explicit compatibility defaults when adding this distinction.

## Required native capabilities

- Semantic value descriptors and encoder bindings: constrained primitives/ticks, counted/optional item and fluid values, chemical codecs, union/list values and counted ingredient spreading. JSON passthrough is not equivalent conversion. Reuse the native ItemStackAdapter, IngredientResolver and FluidResolver where their semantics match.
- Declarative no-argument set functions, aliases, excluded constructor fields, explicit constructor overrides, omitted/default/always-write policy, and inherited function resolution.
- One canonical definition feeding runtime, callback/catalog metadata and TypeScript declarations; no addon-only declaration patches.
- Constructor validation and encoding before publication. RecipeJsonBuilder currently registers at allocation, so a failed conversion can otherwise leave an empty recipe.
- Terminal, enumerable unsupported-profile diagnostics. Unsupported known schemas must not become successful generic/raw fallback recipes.
- A deliberate unique-ID policy. Native RecipeTypeDefinition.unique has no common runtime consumer. Upstream single-key unique behavior is component-specific; the multi-key branch iterates all keys. This implementation preserves native NekoJS generated IDs and explicit id() rather than claiming upstream automatic-ID parity merely by copying unique metadata.

## Finite profile closure

Create processing/item-application families need list/either, ProcessingOutput.CODEC_NEW, Create's flat sized-fluid codec, heat-condition enum, counted item spreading, heated/superheated and keepHeldItem functions. processing_time=100 is omitted in the processing base but always written in processing_with_time. Fluid and item union arms must preserve their own encodings and counts.

Mekanism machine families need flat sized ingredients/fluids, real required/optional ChemicalStack and ChemicalStackIngredient codecs, positive/non-negative longs and bounds, and required versus default ticks. Preserve per_tick_usage=false with always_write. Sawing has explicit 4/3/2-argument constructors and chance bounds; reaction has 6/5/4/3-argument constructors and optional empty outputs; rotary has direction-dependent optional fields. JSON field names remain literal snake_case. Fluent setter names follow the reference normalization of selected canonical/alternative names or explicit function_names into camelCase, after replacing colon and slash separators.

Initially reject Create mechanical_crafting/sequenced_assembly and Mekanism paxel/mek_data/banner_shield/clear_configuration/bin_insert/bin_extract until their complete inherited capabilities exist. Mechanical crafting, paxel and mek_data depend on shaped inheritance; the four special types require hidden/category semantics. Sequenced assembly requires nested recipes to become synthetic, not separately published. A prospective accepted closure contains at most 54 graph documents plus eight rejected documents, not 54 callable serializers. No such support is implemented by this document.

Shaped inheritance includes result/pattern/key, optional-ingredient cleanup, excluded mirror/shrink state, category and notification functions. The key-pattern postprocessor removes empty keys and replaces matching symbols with spaces. Special schemas are hidden with an explicit empty constructor. These features must not silently disappear during translation.

## Implementation and test sequence

1. Extend the existing canonical native definition with compatibility-preserving defaults; implement actual missing semantics at its runtime owner.
2. Add an addon-local compiler taking graph documents plus frozen semantic components, returning accepted native definitions and source/resource/JSON-path rejection diagnostics. Validate the full inherited closure before registration through RecipeSchemaRegister.
3. Bind actual target codecs and native conveniences without adding an evaluator or global current-server slot.
4. Account for every fixture resource as accepted or explicitly rejected. Test inheritance, merges, defaults, constructors, aliases/functions, constraints, failure atomicity, unique/manual IDs and registry-layer precedence.
5. Decode emitted JSON through real target codecs/serializers; snapshots alone do not prove acceptance. Test runtime and generated declarations from the same definitions. Run common:check, relevant addon/1.21.1 gates and canonical TypeScript gates; record real gameplay/reload evidence separately.
