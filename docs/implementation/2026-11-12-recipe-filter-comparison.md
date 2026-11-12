# RecipeFilter: KubeJS and NekoJS comparison (2026-11-12)

> Baseline investigation: the native findings below describe the pre-repair `97a513bb` sources and the originally supplied generated files. They are not a statement of the repaired runtime. See [the repair and verification report](2026-11-12-recipe-filter-fix.md) for the intentional migration and current evidence.

## Scope and conclusion

Read-only investigation of the explicitly authorized local KubeJS checkout and existing declarations. This report is the only authored artifact; no production/generated edits, builds, staging, or commits. Results are source-derived, not live Minecraft execution.

The difference is not merely Rhino versus Graal, nor an obligation to duplicate Java interfaces. KubeJS has a broad filter DSL and a recipe-context predicate. NekoJS has its own holder/registry predicate and narrower string/array/object DSL. Native explicit conjunction/prefix helpers and strict typo checks are design choices; missing regex/callback and richer input/output matching, plus serializer-vs-recipe-type matching, are genuine original-KubeJS-script compatibility gaps.

## Actual registration and predicate context

Default wrappers are RecipeFilter::wrap, ReplacementMatch::wrap and ReplacementMatchInfo::wrap, not a conversion inferred from Probe's Ingredient/ItemStack names: [BuiltinKubeJSPlugin](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/BuiltinKubeJSPlugin.java#L545-L562>).

Upstream RecipeFilter extends Predicate<RecipeMatchContext> ([KubeJS RecipeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java#L20-L25>)). The callback receives a context, not the recipe directly: it carries Rhino context, registries, ops and recipe ([RecipeMatchContext](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeMatchContext.java#L10-L14>)); inherited accessors are registries(), ops(), recipe() ([RecipeLikeContext](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/RecipeLikeContext.java#L7-L12>)). Recipe streams construct that context before test ([RecipesKubeEvent](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/RecipesKubeEvent.java#L425-L428>)). NekoJS tests RecipeHolder plus HolderLookup.Provider ([NekoJS RecipeFilter](<../../src/main/java/com/tkisor/nekojs/api/recipe/RecipeFilter.java#L35-L37>)). The Java shape need not be copied to support equivalent script filters.

No readTypeWrapper symbol was found in upstream Java sources: the actual method here is wrap. No Probe customization source was located inside this checkout (no Probe-named files either). The alias below is observed directly, but its authoring/customization source is NOT established; do not attribute it solely to reflection.

## Upstream conversion and boolean semantics

Null, ConstantFilter.TRUE and "*" mean all; ConstantFilter.FALSE and "-" mean none. There is no JavaScript boolean true/false conversion branch. Other strings are recipe IDs or slash regex strings; NativeRegExp/Pattern are also accepted. BaseFunction becomes a context predicate ([KubeJS RecipeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java#L25-L43>)). Exact ID compares the complete recipe ID ([IDFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/IDFilter.java#L8-L15>)); regex uses Java Matcher.find(), not full-string matches ([RegexIDFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RegexIDFilter.java#L33-L35>); [RegExpKJS](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/util/RegExpKJS.java#L19-L59>)).

Empty list is false; lists of more than one element recursively OR and simplify constants. Singleton caveat: upstream does NOT recursively wrap its sole element; it calls cx.optionalMapOf(list.getFirst()), and null/empty map yields true. Rhino optionalMapOf source was not present, so precise singleton primitive/regex outcomes are not live-verified ([KubeJS RecipeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java#L45-L69>); [ListJS](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/util/ListJS.java#L16-L45>)).

A map ANDs built-in keys or, not, id, type, group, mod, input, output. There is no explicit and key. Empty map is true. The or value recursively wraps, and not negates. Unknown-only maps reach a parse error, but mixed recognized+unknown maps silently keep recognized conditions. Null-valued fields are skipped. NeoForge RecipeFilterParseEvent can contribute additional conditions before the empty-condition check ([KubeJS RecipeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java#L65-L137>); [RecipeFilterParseEvent](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilterParseEvent.java#L9-L17>)). Only KubeRuntimeException is caught, warned and changed to false; do not claim every parse/type error is caught ([KubeJS RecipeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java#L138-L140>)).

The predicates themselves use empty-AND=true and empty-OR=false ([AndFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/AndFilter.java#L9-L18>); [OrFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/OrFilter.java#L9-L18>)).

NekoJS strings always become ById; arrays always recursively OR, including singletons. Object conditions AND together. It adds explicit and arrays and idStartsWith/idEndsWith/idContains. Unknown keys always throw; unsupported scalar values throw. Empty object is all, empty array none; null returns null and null array children are skipped ([NekoJS RecipeFilterAdapter](<../../src/main/java/com/tkisor/nekojs/js/type_adapter/RecipeFilterAdapter.java#L29-L84>); [NekoJS RecipeFilterAdapter](<../../src/main/java/com/tkisor/nekojs/js/type_adapter/RecipeFilterAdapter.java#L87-L164>)). This is deliberately stricter typo handling, not evidence that arbitrary unknown keys should be supported.

## Critical type semantic mismatch

Upstream type means RECIPE_SERIALIZER, compared through recipe.kjs$getTypeKey() ([TypeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/TypeFilter.java#L9-L17>); [RecipeHolderKJS](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/core/RecipeHolderKJS.java#L55-L57>)). NekoJS uses RECIPE_TYPE and holder.value().getType() ([NekoJS RecipeFilter](<../../src/main/java/com/tkisor/nekojs/api/recipe/RecipeFilter.java#L152-L155>)). Thus crafting_shaped/crafting_shapeless are distinct upstream serializer filters, whereas minecraft:crafting is their shared NekoJS recipe type. This is a behavior gap even if declarations both look string-like.

## input/output are ReplacementMatchInfo

Both upstream fields use ReplacementMatchInfo.wrap ([KubeJS RecipeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java#L107-L128>)). Existing info passes through; Map/NativeJavaMap invokes record conversion with match, exact, componentType; other values use ReplacementMatch with exact=false and no component restriction ([ReplacementMatchInfo](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/match/ReplacementMatchInfo.java#L12-L27>)). Existing ReplacementMatch passes through; otherwise IngredientWrapper is used ([ReplacementMatch](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/match/ReplacementMatch.java#L14-L21>)). Ingredient conversion supports existing ingredients/item-like values, tags, regex, strings and lists ([IngredientWrapper](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/wrapper/IngredientWrapper.java#L88-L150>)). A raw map at this position is reserved for match-info record conversion, not automatically ingredient JSON.

InputFilter/OutputFilter call recipe.hasInput/hasOutput ([InputFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/InputFilter.java#L5-L9>); [OutputFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/OutputFilter.java#L8-L15>)). KubeRecipe scans all schema input/output component values ([KubeRecipe](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/KubeRecipe.java#L363-L398>)); matching restricts componentType and forwards exact ([RecipeComponentValue](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/component/RecipeComponentValue.java#L37-L38>); [IngredientComponent](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/component/IngredientComponent.java#L27-L29>)). Raw holders have vanilla Ingredient/result-item ItemMatch fallback ([RecipeHolderKJS](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/core/RecipeHolderKJS.java#L61-L86>)); do not claim every unknown recipe exposes every component.

NekoJS calls asString for input/output ([NekoJS RecipeFilterAdapter](<../../src/main/java/com/tkisor/nekojs/js/type_adapter/RecipeFilterAdapter.java#L124-L129>)); predicates support only item ID or item tag, inspect vanilla ingredients for inputs and a result item ID for outputs ([NekoJS RecipeFilter](<../../src/main/java/com/tkisor/nekojs/api/recipe/RecipeFilter.java#L62-L103>)). Ingredient/ItemStack objects, regex ingredient matches, match-info maps, exact/component restrictions, and schema-aware non-item components are not implemented by this path.

## Actual generated KubeJS alias and fidelity

Read directly from [provided generated declaration](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/dev/latvian/mods/kubejs/recipe/filter/index.d.ts#L23-L32>):

```ts
export type $RecipeFilter_ = RegExp | "*" | "-" | $RecipeFilter[] | {
  or?: $RecipeFilter_[], not?: $RecipeFilter_,
  id?: SpecialTypes.RecipeId, type?: RegistryTypes.RecipeSerializer,
  group?: string, mod?: SpecialTypes.ModId,
  input?: $Ingredient_, output?: $ItemStack_,
} | ((cx: $RecipeMatchContext) => boolean);
```

This is not an exact runtime specification. It omits arbitrary valid top-level ID strings, slash regex strings, null, and uses $RecipeFilter[] rather than recursive $RecipeFilter_[] for top-level arrays. Runtime id accepts regex, while declared id is RecipeId; runtime or accepts any recursively wrapped value, not only arrays ([KubeJS RecipeFilter](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java#L25-L86>)). Callback, RegExp and sentinels ARE real runtime branches, not merely reflected interface conveniences.

The nested ItemStack alias includes {id: RegistryTypes.Item, count?: number} ([generated ItemStack alias](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/net/minecraft/world/item/index.d.ts#L2124>)); Ingredient includes ItemStack ([generated Ingredient alias](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/net/minecraft/world/item/crafting/index.d.ts#L294>)). Consequently input/output promise raw item-stack maps, but runtime maps go into ReplacementMatchInfo record conversion, not ItemWrapper/ingredient JSON conversion. Those shapes are NOT supported as the item-stack maps the alias suggests. Precise exception/default behavior depends on Rhino RecordTypeInfo, not examined here. This is an upstream declaration/runtime mismatch, not a reason to imitate an unsupported branch. Conversely runtime supports richer match-info maps/existing matches, and output ingredient-like matching (including tags) which the filter alias underdescribes.

## Concrete scripts

Use FILTER inside `ServerEvents.recipes(event => { event.remove(FILTER); });`. These destructive examples were NOT executed. IDs only match entries present in a pack.

| FILTER | KubeJS checkout | Current NekoJS |
| --- | --- | --- |
| `{id:"minecraft:stick"}` | Exact recipe ID | Same intent |
| `{mod:"minecraft",output:"minecraft:stick"}` | AND, replacement output match | AND, result item-ID match |
| `[{mod:"minecraft"},{output:"minecraft:stick"}]` | OR | OR |
| `{or:[{mod:"minecraft"},{mod:"example"}],not:{output:"minecraft:stick"}}` | OR mods AND NOT output | Same boolean intent for simple item output |
| `{}` / `[]` | All / none | All / none |
| `"*"` | All | Invalid ById, no match |
| `/minecraft:.*stick/` | Recipe-ID regex find | No regex branch; may reject/misinterpret object, not regex matching |
| `{id:/stick/}` | Regex ID find | asString conversion fails |
| `cx => false` | Valid context predicate, none | Not declared; adapter has no canExecute branch and the native SAM has different parameters. Actual host-conversion precedence was not executed, so do not claim equivalent callback behavior or a particular failure result. |
| `{type:"minecraft:crafting_shaped"}` | Shaped serializer recipes | Compares recipe type instead, normally none |
| `{type:"minecraft:crafting"}` | Serializer named crafting, not both crafting forms | Shared crafting recipe type |
| `{input:Ingredient.of("#minecraft:planks")}` | Existing ingredient replacement match | asString fails |
| `{input:{match:"#minecraft:planks",exact:true}}` | Match-info record with exact semantics | asString fails |
| `{and:[{mod:"minecraft"},{output:"minecraft:stick"}]}` | Unknown built-in key absent extension | Native conjunction |
| `{idStartsWith:"minecraft:wood"}` | Unknown built-in key absent extension | Native prefix filter |
| `{mod:"minecraft",typo:1}` | Ignores typo, keeps mod | Rejects unknown key |

## Verification and remaining gaps

Inspected source and existing generated declarations using read/glob/grep; no build, tests or game mutation commands. A broad filesystem glob timed out and a grep pattern needed correction; neither changed files. Probe customization source and Rhino optionalMapOf/RecordTypeInfo source were not located, so singleton non-map list and raw-map exact failure outcomes remain qualified. NekoJS generated aliases/generator are independently examined by the lead and may extend this same report.

## NekoJS declaration tracing and generator ownership

The user-provided [server event declaration](<../../addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/tkisor/nekojs/wrapper/event/server/index.d.ts#L2>) imports the filter from the native recipe API package; it is a consumer, not the definition. [remove and replacements](<../../addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/tkisor/nekojs/wrapper/event/server/index.d.ts#L227-L229>) take the imported alias. The actual [native recipe alias](<../../addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/tkisor/nekojs/api/recipe/index.d.ts#L169>) is:

```ts
export type $RecipeFilter_ = string | $RecipeFilter_[] | { not?: $RecipeFilter_, and?: $RecipeFilter_, or?: $RecipeFilter_, output?: (RegistryTypes.Item | `#${RegistryTypes.ItemTag}`), input?: (RegistryTypes.Item | `#${RegistryTypes.ItemTag}`), mod?: string, group?: string, id?: string, idStartsWith?: string, idEndsWith?: string, idContains?: string, type?: string };
```

Runtime shape metadata comes from [RecipeFilterAdapter.inputShapes](<../../src/main/java/com/tkisor/nekojs/js/type_adapter/RecipeFilterAdapter.java#L37-L56>): strings, recursive arrays, object keys and item/tag registry forms. There is no explicit regex, sentinel, predicate or existing-host self shape at the top level. A TypeScript string accepts stars syntactically, but [apply constructs ById](<../../src/main/java/com/tkisor/nekojs/js/type_adapter/RecipeFilterAdapter.java#L65-L79>); that does not grant wildcard semantics.

[AdapterAliasGenerator](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/AdapterAliasGenerator.java#L124-L156>) renders the registered shapes. [IndexFileGenerator](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/IndexFileGenerator.java#L228-L255>) emits an adapter alias first and continues rather than unioning its generic functional-interface alias. This is not a missing import/re-export in the event declaration: a narrower canonical adapter contract takes priority over the SAM alias.

Callback support needs an actual integration test, not an unconditional declaration-only addition. [Native RecipeFilter.test](<../../src/main/java/com/tkisor/nekojs/api/recipe/RecipeFilter.java#L35-L37>) takes (RecipeHolder, HolderLookup.Provider), not a single upstream context. [NekoSharedHostAccess](<../../common/src/main/java/com/tkisor/nekojs/core/NekoSharedHostAccess.java#L29-L65>) enables generic implementations plus explicit adapters. [Adapter acceptance and apply](<../../src/main/java/com/tkisor/nekojs/js/type_adapter/RecipeFilterAdapter.java#L59-L92>) have neither canExecute handling nor RegExp recognition. Actual precedence/misclassification for guest function objects was not executed here; no exact false/match-all/exception outcome is claimed. Even a successful native SAM path would not provide the KubeJS callback context.

The NekuJS [RecipeMatchContext compatibility seam](<../../addons/nekujs/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeMatchContext.java#L5-L8>) explicitly states that no recipe-filter engine is installed and only carries cx. It does not supply the upstream recipe/registries/ops context.

## Recommended repair order (not implemented here)

1. Add real selection regressions and reconcile serializer-versus-type matching, testing shaped and shapeless independently. Decide explicitly whether native type-ID semantics need a separately named input or migration. Current native Javadoc already uses crafting_shaped despite its implementation, so this merits priority over completion widening.
2. Define and implement any supported sentinels, regex and predicate behavior in the existing native parser. A real callback context must be chosen; do not import another Rhino recipe engine. Preserve native AND/prefix extensions and typo rejection outside an explicit migration.
3. Reuse the existing ingredient/item/component match seams for supported richer input/output filters. Do not advertise upstream replacement-info objects until their semantics actually exist.
4. Update canonical inputShapes and registry references, and merge adapter/SAM shapes only where runtime acceptance is verified. Recipe-ID/mod completion improvements are distinct from adding runtime support.
5. Regenerate with normal Probe; verify TypeScript input aliases and actual recipe selection. Do not hand-patch either supplied declaration or treat byte-for-byte upstream output as acceptance.

No production/generated files or upstream checkout files were modified. No builds, destructive recipe calls, live-game acceptance or compatibility implementation were performed.
