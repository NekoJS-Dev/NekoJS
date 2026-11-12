# Recipes and tags

Recipe scripts normally belong to SERVER. Resolve `ServerEvents.recipes` and inspect its Event Object before using operations. Prefer the current instance's `event.recipes.<namespace>.<type>` signatures over examples from another modpack.

Recipe filters accept native filter values, exact ID strings, `"*"` (all), `"-"` (none), regular expressions, boolean-returning context predicates, arrays and structured objects. Object fields such as `id`, `type`, `input`, `output`, `and`, `or` and `not` are unrelated to the authoring query language’s `all`, `any` and `some` predicates.

Fields of one object are AND. Use `{ and: [{ mod: "minecraft" }, { idContains: "chest" }] }` to require every child filter; `{ or: [a, b] }` and a plain filter array accept any child. Empty `and` matches everything; empty `or` and a plain empty array match nothing. Non-array group values retain the supplied filter's meaning. Unknown filter keys are rejected, so do not use misspellings to express an unconstrained filter.

`type` matches the **recipe serializer ID**, for example `minecraft:crafting_shaped`; `recipeType` explicitly matches the broader native recipe type, for example `minecraft:crafting`. IDs without a namespace default to `minecraft`. Migrate old `type: "minecraft:crafting"` queries to `recipeType`; serializer matching intentionally distinguishes shaped from shapeless crafting. Probe completes these fields from the real `RecipeSerializer` and `RecipeType` catalogs.

Probe presents `$RecipeFilter_` through named Expression, Conditions, Input and Output sections. These are TypeScript-only aliases, not Java classes, constructors or new runtime Bindings. Scripts keep passing the same strings, objects, arrays and predicates. Exact output restrictions remain unchanged; rebuild/restart and run the instance’s normal Probe generation to refresh existing output.

For ID, mod and group matching, JavaScript RegExp values use their actual `test` operation with `lastIndex` reset and restored on every match; Java `Pattern` and slash-pattern strings use Java `find` semantics. Slash strings support `dixmsuU`, not every JavaScript regex flag. Only the ID form interprets the exact `"*"` / `"-"` strings as all/none. Invalid patterns fail; an arbitrary object with a `test` method is not a RegExp.

A predicate receives one native, read-only context. Use `match.recipe` (or `match.getRecipe()`) and the recipe view’s `getOrCreateId()`, `getMod()`, `getType()` (serializer) and `getGroup()`. Return a real boolean; exceptions/non-booleans are reported as script failures, not silently converted to a non-match. This is not Rhino’s context, schema editor or full KubeJS RecipeMatchContext. RegExp/predicate guest values remain owned by the current script generation; do not retain them across reloads or call them on foreign threads.

```javascript
// Inside ServerEvents.recipes; these are alternative selection examples.
event.count({ type: "minecraft:crafting_shaped" })
event.count({ recipeType: "minecraft:crafting" })
event.count({ id: /^minecraft:.*planks$/ })
event.count(match => match.recipe.mod === "minecraft")
event.count({ output: { match: Item.of("minecraft:stick"), exact: true } })
```

`input` / `output` accept native Ingredient/ItemStack values and supported adapter input forms, including item/tag strings, arrays and structured stacks with components. Matching ignores stack count. A stack selector normally constrains its item and explicit component patch; `{match: stack, exact: true}` compares all native components instead. Input Ingredient matches use native representatives and acceptance predicates; exact Ingredient input uses native equality. Exact output matching requires a concrete stack. Unknown nested keys, unsupported `componentType` and invalid selectors fail closed. This is not a promise of schema-aware fluid/addon replacement matching. Modern recipe inspection remains bounded to the existing shaped/shapeless/single-item/cooking result/ingredient facades; do not infer coverage for every custom recipe serializer.

An item tag (`#namespace:path`) is not an item stack. Check which adapter accepts it. Tags, recipe schemas, resource formats, and reload support can differ by version and loader.

For complete recipe and item/block-tag files with run commands and expected effects, start with [scripting examples](scripting-examples.md).

## Optional NekuJS compatibility addon

When NekuJS is installed on 1.21.1 NeoForge, supported original addon resource and Java schemas are translated into native NekoJS recipe definitions. Use the instance declarations to discover the supported constructors and fluent methods. NekuJS is a bounded API provider, not the upstream KubeJS/Rhino engine; its provider version satisfies loader ranges but does not certify every upstream addon or coercion. Explicitly unsupported profiles and forced programmatic constructor overrides remain failures. Treat isolated codec/builder checks, successful startup, and successful world reload as separate evidence.

Native `minecraft:shaped` and `minecraft:special` schema parents are dependency inputs, not replacement recipe methods. Inherited profiles retain the child serializer ID. The special parent has a zero-argument positional constructor and a native crafting-category field defaulting to `misc`; inherited `hidden` metadata does not itself forbid runtime calls. The native shaped parent supports result, pattern, character-key ingredients, category and notification settings; empty-ingredient cleanup and cross-field validation use the native preparation lifecycle before final decode. Non-default KubeJS mirror/shrink controls are not supplied by this parent and are not advertised as supported. Unknown custom factories, processors and mappings still fail closed. Codec-specific category input literals are serialized lowercase values, not an assumption that Java enum constant names are accepted.

Verify recipe changes in a running world after a successful server reload. Editor validation does not prove recipe codec acceptance or viewer refresh.

## Native handler declarations

Native recipe handlers and schema-defined recipes are separate execution paths. Probe preserves every public executable handler overload and renders its real Java return type; it does not replace a plain RecipeJsonBuilder result with a schema-specific builder. Handler parameters use the shared native input projection, including distinct host collection identity and guest element conversions. Optional tails may be omitted; Java varargs remain single array arguments unless the runtime explicitly supports packing. Generic variables without a declared script type parameter use their JVM bounds, not invented owner reification.

Vanilla shaped metadata is identified by its registered serializer and describes result, pattern and key; shapeless describes result and ingredients. Field encoders use native item/ingredient conversion. A similarly named addon serializer does not acquire this vanilla contract. After code updates, rebuild/restart and regenerate Probe; older live declarations do not update themselves.

## Reading generated recipe inputs

NekuJS follows the original ProbeJS component type model: nominal Java types, parameterized lists, unions, and registered input aliases. A recipe result may therefore read `$List_<$ItemStack_>` instead of expanding every accepted JSON form or hiding the whole field behind `Create$ResultsInput`. Canonical input aliases live with their target class declarations; generated type names are not runtime Bindings or Java class names. These rules are shared across namespaces. If a converter has no declared input forms, Probe keeps its nominal class type instead of inventing an addon-specific DTO alias. Adapter input metadata is the common source for all class input aliases.

Converter-owned input declarations and ordinary Java method inputs have different generic contracts. Component metadata can recursively project accepted input values, while an erased Java adapter cannot infer conversion of its generic payload. The shared Probe renderer keeps this distinction; a typed host List or Map is not silently rewritten element by element.

List cardinality follows the component metadata. A list-only type stays a list input; a component that explicitly declares a single-value alternative keeps that union. Runtime list wrapping can be more permissive than the nominal declaration. Native collection aliases may also accept genuine Java collections where the native converter supports them; do not generalize that support to every collection class.

Script values use native converters before component codec validation. Supported fluid stack objects can omit amount and use the native bucket default. Ingredient forms use the native ingredient converter. Create processing outputs use the unchanged registered addon wrapper and the native item-stack converter, including supported count/chance objects. Wrapper coercion does not bypass codec bounds: for example, a chance clamped to zero can still be rejected by a positive-value codec. Time inputs support the original unit-string grammar through the registered TickDuration converter. Declarative JSON constants retain their separate codec validation path.

Equivalent contiguous positional constructor prefixes may appear as one signature with optional trailing parameters, followed by named options. The original ProbeJS constructor-key omission is not reproduced: actual constructor profiles remain authoritative. Trailing JavaScript undefined arguments are omitted before schema constructor selection; null and interior gaps are not silently removed. Constructor-specific overrides prevent coalescing. Undefined-valued named-options properties retain their existing handling.

Earlier whole-field and recipe-specific value-domain aliases, including Create$ResultsInput and Create$FluidStackInput, are replaced by canonical class input aliases. TypeScript imports must follow the newly generated declarations. Rebuild and restart the instance after code changes, then regenerate Probe through the normal workflow; editing old generated declarations cannot change runtime conversion.
