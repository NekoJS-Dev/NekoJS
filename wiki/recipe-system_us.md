<!-- wiki-page: recipe-system; locale: us -->

> **English** · [中文](recipe-system_cn)

<a id="wiki-section-1"></a>
# Recipe system

Recipe modification is one of the most commonly used NekoJS features. Use the `ServerEvents.recipes` event to add, remove, and modify vanilla and mod recipes.

<a id="wiki-section-2"></a>
## Basic usage

```javascript
ServerEvents.recipes(event => {
  // Remove
  event.remove({ output: 'minecraft:stick' })

  // Add (vanilla-style)
  event.shaped('4x minecraft:stick', ['C'], { C: 'minecraft:cobblestone' })
    .id('nekojs:cobble_to_sticks')

  // Add (any type, schema-driven)
  event.recipes.minecraft.smelting({
    ingredient: Ingredient.of('minecraft:iron_ore'),
    result: Item.of('minecraft:iron_ingot')
  })

  // Replace inputs
  event.replaceInput({ type: 'minecraft:crafting_shaped' }, '#minecraft:planks', 'minecraft:oak_planks')
})
```

`event` is a `RecipeEventJS` object. All methods below belong to it.

---

<a id="wiki-section-3"></a>
## Filters (RecipeFilter)

Filters accept native instances, exact recipe IDs, `"*"` (all), `"-"` (none), regular expressions, boolean-returning context predicates and nested arrays/objects. Object members combine with AND; plain arrays combine with OR. `{}` matches everything; unknown keys are still rejected (`NEKO-5003`) instead of silently broadening destructive selection. Explicit `{ and: [a, b] }` requires every child, while `{ or: [a, b] }` accepts any child. Empty AND is true; empty OR and plain empty arrays are false. A non-array group value retains the supplied filter’s meaning.

| Key | Value | Meaning |
|---|---|---|
| `mod` | mod id | Recipes from this mod |
| `type` | Serializer id | Match the serializer, distinguishing shaped/shapeless; completed from its registry |
| `recipeType` | Native type id | Explicitly match the broader type, e.g. `minecraft:crafting` |
| `output` | Item/ingredient input or `{match, exact?}` | Match the output, including native stacks and components |
| `input` | Ingredient/item input or `{match, exact?}` | Match native ingredient overlap or a concrete stack witness |
| `group` | String | Recipe group |
| `id` | Recipe id or regex | Exact id (default namespace `minecraft:`) or regex |
| `idStartsWith` / `idEndsWith` / `idContains` | String | Match substrings of the id |
| `and` | Filter or filter array | Every array element must match; a single filter keeps its meaning |
| `or` | Array of filters | OR |
| `not` | Filter | NOT |

**Migration:** `type` now selects the serializer, not the broad native `RecipeType`. Replace old `{type: "minecraft:crafting"}` with `{recipeType: "minecraft:crafting"}`; use `{type: "minecraft:crafting_shaped"}` for shaped crafting only. Missing namespaces default to `minecraft:`. `id`, `mod` and `group` also accept JS RegExp, Java Pattern and slash-pattern strings. JS regex matching resets/restores `lastIndex`; slash strings use Java `find` and `dixmsuU` flags.

Predicate example: `event.count(match => match.recipe.mod === "minecraft")`. The native read-only context exposes `recipe` / `getRecipe()`; its recipe view has `getOrCreateId()`, `getMod()`, `getType()` (serializer) and `getGroup()`. This is not a Rhino context or mutable schema/builder. Return a real boolean; callback exceptions propagate. Do not retain guest regex/predicate values across script generations or threads.

`input` / `output` accept supported Ingredient/ItemStack adapter forms, arrays and component-bearing stack objects. Count is ignored. Default stack matching constrains the item and explicit component patch; `{match: stack, exact: true}` compares full native components. Exact Ingredient input uses native equality; exact output requires a concrete stack. Unknown nested keys and unsupported `componentType` fail. Full KubeJS fluid/addon-schema replacement matching is not implied. Modern recipe inspection remains bounded by the existing shaped/shapeless/single-item/cooking facades. Probe declarations come from actual adapters and registries; restart with the new build and regenerate old output instead of hand-editing declarations as a runtime fix.

```javascript
event.remove({ mod: 'minecraft', output: 'minecraft:stick' })   // Stick recipes from minecraft
event.remove({ type: 'minecraft:crafting_shaped', output: '#minecraft:planks' })
event.remove({ not: { mod: 'create' } })                          // Everything except create
```

---

<a id="wiki-section-4"></a>
## Removing / replacing

| Method | Description |
|---|---|
| `event.remove(filter)` | Remove all matching recipes |
| `event.removeById(id)` | Remove one recipe by id |
| `event.replaceInput(filter, match: Ingredient, replacement: Ingredient)` | Recursively replace input ingredients |
| `event.replaceOutput(filter, match: Ingredient, replacement: ItemStack)` | Recursively replace outputs |

```javascript
// Replace every input using minecraft:oak_planks with minecraft:birch_planks
event.replaceInput({}, 'minecraft:oak_planks', 'minecraft:birch_planks')

// Remove all mod recipes that output a diamond sword
event.remove({ output: 'minecraft:diamond_sword' })
```

---

<a id="wiki-section-5"></a>
## Ingredient syntax (Ingredient)

Anywhere a parameter is typed as `Ingredient` (the ingredient array in `event.shapeless`, the second argument of direct `event.blasting`, the `match` argument of `replaceInput`, and so on), the following forms are accepted:

| Form | Meaning | Editor completion |
|---|---|---|
| `'minecraft:stone'` | A single item id | ✅ Lists all registered items |
| `'#minecraft:planks'` | An item tag | ✅ Lists all loaded tags |
| `'@create'` | All items in a namespace | ✅ Lists loaded mod ids plus namespaces present in the registries |
| `'*'` | Any item | ✅ |
| `'/wool$/'` | Match ids with a regular expression | ❌ (regular expressions cannot be exhaustively enumerated) |
| `Item.of('minecraft:stone')` / `ItemStack` | Host object | ✅ |
| `['minecraft:stone', '#minecraft:planks']` | Array = match any entry | ✅ |
| `{ item, tag, mod, regex, wildcard, filter, any, all, not }` | Object form | ✅ (the `item` / `tag` / `mod` fields) |

Completion comes from the `$Ingredient_` alias generated by probe: item ids use the `RegistryTypes.Item` literal union, while tags and namespaces use template literal types such as <code>\`#${RegistryTypes.ItemTag}\`</code> and <code>\`@${RegistryTypes.Namespace}\`</code>. `RegistryTypes.ItemTag` is exported from tags bound to the registries when probe runs. `RegistryTypes.Namespace` is the union of the loader's mod list and namespaces of registry entries (so `@mymod` also receives completion when your script registers `mymod:cool_gem`). Therefore, **run `/nekojs probe` again after adding mods, registering content in scripts, or changing datapack tags**; otherwise new ids/tags will be marked as errors.

> This version tree uses item tags; it does not provide a verified Cleanroom/OreDictionary contract. NeoForge fluid ingredient completion uses the corresponding `Fluid` metadata; the fluid APIs are not implied on Fabric.

---

<a id="wiki-section-6"></a>
## Adding recipes

<a id="wiki-section-7"></a>
### Method 1: `event.<type>(...)` shortcuts

`event` provides direct methods for common vanilla types:

| Method | Signature |
|---|---|
| `event.shaped(result, pattern, keys)` | Shaped crafting. `result` is an ItemStack/id; `pattern` is a string array; `keys` is `{ character: Ingredient }` |
| `event.shapeless(result, ingredients)` | Shapeless crafting. `ingredients` is an array |
| `event.smelting(result, ingredient)` / `event.smelting(result, ingredient, xp, cookTime)` | Furnace |
| `event.blasting(result, ingredient)` / `event.blasting(result, ingredient, xp, cookTime)` | Blast furnace (the same overloads as smelting) |
| `event.smoking(result, ingredient)` / `event.campfireCooking(result, ingredient)` | Smoker / campfire (only the two-argument form, without xp/cookTime) |

```javascript
event.shaped('minecraft:diamond_sword', [
  'D',
  'D',
  'S'
], {
  D: 'minecraft:diamond',
  S: 'minecraft:stick'
}).id('nekojs:diamond_sword_alt')

event.shapeless('minecraft:dirt', ['minecraft:sand', 'minecraft:gravel'])
```

<a id="wiki-section-8"></a>
### Method 2: `event.recipes.<namespace>.<type>(...)`

For a callable profile, resolution tries a registered handler, then a schema, then raw JSON. A known profile carrying an `unsupportedReason` is checked first and rejected instead of silently using a handler or raw fallback. **Do not copy the direct-helper argument order to every namespace method.** The built-in handler supplies `crafting_shaped`/`crafting_shapeless`; cooking and smithing depend on the effective schema and installed node. Inspect its generated overloads or use named fields when that schema supports them. See [runtime resolution](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java#L64-L77).

<a id="wiki-section-9"></a>
### Native handler and schema contracts

Native handler declarations retain **every visible overload** and the actual Java return type, including its declared generic structure; they are not renamed to a similarly named schema builder. Unbound Java type variables are approximated by their upper bounds, not invented as undeclared TypeScript variables. Only a trailing suffix of `Optional` parameters can be omitted; an interior optional slot remains required and accepts null/undefined. Java varargs on this reflected route still require one array argument, not spread arguments. See [handler metadata](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/api/catalog/RecipeNamespaceCatalogEntry.java#L66-L94), [arity/conversion](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L70-L96), and [Optional suffix rules](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L183-L188).

A schema uses its own declared constructors and keeps the **full named-options object overload** when fields exist, alongside positional forms. Named options preserve required/defaulted fields and canonical-or-alias exclusivity; they are not a reduced sample of convenient fields. Chained setters retain the same whole-field input contract. The generated options type may be named to avoid repeating a long object. See [schema overload emission](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java#L238-L255) and [Probe type conventions](probe-type-generation_us#wiki-section-16).

Place this complete example in `server_scripts/namespace-recipes.js`:

```javascript
ServerEvents.recipes(event => {
  event.recipes.minecraft.crafting_shaped(
    Item.of('minecraft:stick', 2), ['B'],
    { B: Ingredient.of('minecraft:bamboo') }
  ).id('nekojs_example:bamboo_to_sticks')

  event.recipes.minecraft.smelting({
    ingredient: Ingredient.of('minecraft:gold_ore'),
    result: Item.of('minecraft:gold_ingot'),
    experience: 1.0,
    cookingtime: 200
  }).id('nekojs_example:smelt_gold')
})
```

Recipe objects, ingredient maps, filters, and schema JSON are structured data, not a universal builder-configuration convention. A third-party mod id does not prove a particular schema or constructor exists. Use the installed mod's documented serializer/schema; do not assume `create.mixing(result, inputs)` or a misspelled `smithingtransform` is supported. Fluent `.id(...)` is current supported API; no callback-builder replacement is implied. See [Script API style](script-api-style_us).

> Generated ids are implementation-derived `nekojs:<prefix>_<8-hex hash>` values with collision suffixes; use explicit unique ids for stable pack behavior.

<a id="wiki-section-10"></a>
### Method 3: `event.custom(...)` / `event.builder(...)` - raw JSON

For an installed serializer, supply its complete recipe data. `RecipeJsonValue` converts nested native Ingredient/ItemStack values with the current event's codecs, as below; plain JSON must already match the node/mod format. This does not infer missing fields, install a serializer, or certify arbitrary addon support:

```javascript
// custom: must include type
event.custom({
  type: 'minecraft:smelting',
  ingredient: Ingredient.of('minecraft:cobblestone'),
  result: Item.of('minecraft:stone'),
  experience: 0.1,
  cookingtime: 200
}).id('nekojs:cobble_to_stone')

// builder: construct any type through chained calls
event.builder('minecraft:crafting_shapeless')
  .id('nekojs:custom_button')
  .property('category', 'redstone')
  .property('ingredients', [Ingredient.of('minecraft:stone')])
  .output('result', Item.of('minecraft:stone_button'))
  .validate()
```

---

<a id="wiki-section-11"></a>
## RecipeJsonBuilder (chained calls)

`event.builder(type)`, the direct vanilla helpers, and valid `event.custom(...)` calls return `RecipeJsonBuilder`; a custom object missing `type` returns `null`. Schema calls return a schema-aware fluent wrapper, while namespace handlers retain their declared Java return type. Only chain methods advertised for that actual return type.

| Method | Description |
|---|---|
| `.id(newId)` | Set the recipe id (reindexes the recipe) |
| `.group(group)` | Set the recipe group |
| `.validate()` | Decode against the native recipe codec now; report invalid serializer data |
| `.json() → object` | Return the underlying JSON |
| `.merge(jsonObject\|value)` | Merge JSON |
| `.property(key, value)` | Set any property |
| `.jsonProperty(key, JsonElement\|Number\|String\|Boolean)` | Set a JSON property |
| `.input(key, Ingredient)` | Encode an Ingredient at the named top-level JSON key |
| `.output(key, ItemStack)` | Set an output field |
| `.fluid(key, FluidStack)` | Set a fluid field |
| `.fluidIngredient(key, FluidIngredient)` | Set a fluid ingredient field |
| `.sizedFluidIngredient(key, SizedFluidIngredient)` | Set a sized fluid ingredient field |
| `.setPath(path, value)` / `.setPaths(obj)` | Set values by JSON path |
| `.removePath(path)` / `.removePaths(array)` | Remove by path |
| `.removeProperty(key)` | Remove a property |

---

<a id="wiki-section-12"></a>
## Querying / iterating

| Method | Description |
|---|---|
| `event.all() → RecipeEntryJS[]` | All recipes |
| `event.get(id) → RecipeEntryJS` | Get by id |
| `event.find(filter) → RecipeEntryJS[]` | Find by filter |
| `event.forEach(filter?, cb)` | Iterate |
| `event.modify(filter, cb)` | Iterate and modify |
| `event.count()` / `event.count(filter) → int` | Count |
| `event.exists(id)` / `event.exists(filter) → boolean` | Check existence |
| `event.ids() → string[]` | All recipe ids |
| `event.setJson(id, json)` / `event.getJson(id)` | Read/write raw JSON |
| `event.dump(filter?) → string` / `event.print(filter?)` | Convert to a JSON string / print to the log |

`RecipeEntryJS` exposes `.id`, `.json`, `.setPath(path, value)`, `.removePath(path)`, and other members.

```javascript
event.forEach({ output: 'minecraft:iron_ingot' }, recipe => {
  console.info(`Found recipe: ${recipe.id}`)
  recipe.setPath('experience', 0.5)   // Change experience
})
```

---

<a id="wiki-section-13"></a>
## Ingredient actions (Ingredient Actions)

> **NeoForge 26.x** only. Customize the crafting remainder when a recipe consumes ingredients:
> damage a tool instead of removing it, retain a catalyst, or replace an ingredient with another item. Actions are **script-only state**
> (not written into recipe JSON). They are rebuilt automatically when `/reload` or `/nekojs reload server` reruns the scripts. Remove
> an action from the script, and it stops applying after the next reload.

Three actions are available on `RecipeJsonBuilder` (for example, the direct `event.shaped(...)` / `event.shapeless(...)` result); do not infer them for every namespace handler return type. The `mymod:` outputs below are placeholders: replace them with items installed in your instance. Pass the damage amount explicitly; there is no one-argument overload:

```js
ServerEvents.recipes(event => {
  // An iron pickaxe used as a crafting tool returns to the grid with 1 less durability
  // (disappears when exhausted; unbreakable items are retained)
  event.shaped('mymod:super_pick', ['PPP', ' S ', ' S '], {
      P: 'minecraft:iron_pickaxe', S: 'minecraft:stick'
    })
    .damageIngredient('minecraft:iron_pickaxe', 1)

  // Keep the diamond catalyst unchanged (only other ingredients are consumed each time)
  event.shaped('mymod:magic_block', ['DAD', 'AAA', 'DAD'], {
      D: 'minecraft:diamond_block', A: '#c:ingots',
    })
    .keepIngredient('minecraft:diamond_block')

  // Replace an ingredient (the replacement enters the grid/inventory when the output is taken)
  event.shapeless('mymod:swap', ['minecraft:diamond'])
    .replaceIngredient('minecraft:diamond', 'minecraft:emerald')
})
```

The match argument uses the same forms as [ingredient syntax](#wiki-section-5): item ids, arrays, `'#tag'`, or Ingredient objects.
A recipe can have multiple actions, but only the **first** matching action applies to each slot. Unmatched slots retain vanilla remainder behavior
(empty buckets, experience bottles, and so on). Rename with `.id(...)` **before** calling an action: actions are registered against the recipe id at call time.

| Action | Semantics | Limits |
|---|---|---|
| `damageIngredient(filter, amount)` | Remainder = a copy of the ingredient, with damage increased by amount | Pass `amount` explicitly (use `1` for one durability); at maximum damage → disappears; non-damageable → retained |
| `keepIngredient(filter)` | Remainder = a copy of the ingredient, without damage | Equivalent to a catalyst |
| `replaceIngredient(filter, with)` | Remainder = a copy of `with` | `with` accepts an item id or ItemStack |

---

<a id="wiki-section-14"></a>
## Data-driven schemas

Automatic discovery scans registered serializers using node-specific known vanilla contracts and codec inspection/heuristics. A serializer being present does **not** guarantee a complete field schema, correct positional constructor, or support for every addon component. Complex or opaque codecs can produce limited discovery results; inspect the effective catalog and generated declarations rather than assuming a mod-wide compatibility guarantee. See [the native scanner](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/src/main/java/com/tkisor/nekojs/api/recipe/definition/MinecraftRecipeSchemaScanner.java#L33-L74) and [codec inspection/fallbacks](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/src/main/java/com/tkisor/nekojs/api/recipe/definition/MinecraftRecipeSchemaScanner.java#L117-L159).

With the optional NekuJS addon, supported compatibility resources and programmatic schemas are compiled into the **same native NekoJS schema registry**. They reuse native conversion and Probe declarations; this is not an upstream Rhino/KubeJS runtime or a second recipe engine. Compatibility remains bounded by the imported plugins, component codecs, schema inheritance and factory operations actually implemented. See [bridge publication](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/addons/nekujs/src/main/java/com/tkisor/nekujs/bridge/LegacyRuntimeBridge.java#L75-L107) and [native argument conversion](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L70-L143).

**Installing the optional compatibility addon.** NekuJS is a separate JAR, not bundled into NekoJS, and currently targets **Minecraft 1.21.1 + NeoForge 21.1 + Java 21**. Install it alongside a compatible NekoJS JAR and the supported original extension mods and their own dependencies. Do **not** install upstream KubeJS or Rhino beside it: NekuJS already supplies their bounded provider identities/packages. These identities satisfy loader ranges; they do not establish a complete upstream runtime. Restart the instance when changing these mods or their owned schema resources, then regenerate Probe; script reload alone does not rebuild the Plugin Runtime. See the [addon installation contract](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/addons/nekujs/README.md#runtime-contract).

The checked-in fixture contracts describe these **specific catalogs**, not all recipes in a mod or a claim that every serializer has passed live gameplay:

| Original extension fixture | Declared native profile scope |
|---|---|
| KubeJS Create 2101.3.1-build.18 + KubeJS Mekanism 2101.1.7-build.18 | 62 owned schema documents: 48 callable profiles, 13 non-callable bases, and explicit rejection of `create:sequenced_assembly` |
| The same pair + KubeJS EnderIO 1.21.1-0.13.0 | Nine additional programmatic schemas; 71 definitions total, 57 callable |

Source contracts: [resource graph fixture](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/addons/nekujs/src/test/java/com/tkisor/nekujs/recipe/schema/FixtureRecipeSchemaGraphCompilerTest.java#L26-L50) and [combined declaration fixture](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/addons/nekujs/src/test/java/com/tkisor/nekujs/recipe/FixtureReadableRecipeDeclarationTest.java#L43-L80). Nested recipe construction, forced programmatic constructor overrides, and unimplemented factory/processor/mapping capabilities remain explicit failures; neither counts nor successful Probe generation certify world recipe decoding. NekuJS content-builder definitions are currently captured metadata, not native registry-object installation.

NekuJS supplies shared native parent dependencies for `minecraft:shaped` and `minecraft:special`; these do not publish synthetic methods over vanilla handlers, and children retain their own serializer IDs. The special parent preserves its native category codec, `misc` default and zero-positional-argument constructor. Upstream `hidden` is descriptive metadata, not a runtime call prohibition. The shaped parent supplies result, pattern, single-character ingredient keys, category and notification fields, with empty-ingredient cleanup and cross-field validation in the native encode lifecycle.

Consequently, the original Mekanism addon's `mek_data` inherits shaped, while `bin_extract`, `bin_insert` and `clear_configuration` inherit special without recipe-name exceptions. The native parent supports vanilla default mirror/shrink behavior; it does not advertise non-default KubeJS controls such as `noMirror` or `noShrink`. Unknown factories, processors and mappings remain explicit failures. Restart the updated instance and regenerate Probe before evaluating the new definitions; old declarations do not update themselves.

To define a schema for a **custom/unknown type** (enabling positional arguments and field validation), write a JSON file:

**Path**: `nekojs/data/<namespace>/nekojs/recipe_types/<type>.json`

**Example** (`data_driven_shapeless.json`):

```json
{
  "type": "minecraft:crafting_shapeless",
  "id_prefix": "data_driven_shapeless",
  "constructors": [
    ["result", "ingredients"],
    ["result", "ingredients", "group"]
  ],
  "fields": {
    "result":      { "path": "result",     "kind": "item_stack" },
    "ingredients": { "path": "ingredients","kind": "ingredient", "array": true },
    "group":       { "path": "group",      "kind": "string", "optional": true }
  }
}
```

<a id="wiki-section-15"></a>
### Top-level fields

| Key | Required? | Default | Meaning |
|---|---|---|---|
| `type` | Optional | `<namespace>:<name>` | Target recipe type id |
| `id_prefix` | Optional | `<namespace>_<name>` | Prefix for generated ids |
| `constructors` | Optional | All fields in declaration order | Positional constructors: an array of arrays of field names |
| `fields` | **Required** | None | Mapping of field names to field definitions |
| `unique` | Optional | `[]` | Retained field-name metadata; does not replace native generated-id policy or `.id(...)` |

<a id="wiki-section-16"></a>
### Field definitions

| Key | Default | Meaning |
|---|---|---|
| `path` | Field name | JSON path where the value is written |
| `kind` | `json` | Type (see below) |
| `array` | `false` | Whether to wrap in a JSON array |
| `optional` | `false` | A missing field is required when false **and no default exists** |
| `default` | — | Default JSON value |
| `role` | Inferred from the name | `INPUT` / `OUTPUT` / `OTHER` (controls key recognition by `replaceInput`/`replaceOutput`) |

<a id="wiki-section-17"></a>
### What happens with an invalid definition

`RecipeTypeDefinitionJsonLoader.parse` throws immediately for these cases (loading the JSON fails): missing `fields` or an empty `fields` object; a field definition that is not an object; an entry in `constructors` that is not an array; a constructor referring to a field name absent from `fields`; or an unknown `kind` value (`valueOf` in `RecipeFieldKind.parse` throws).

`role` is an exception: an invalid value **does not produce an error**. It silently falls back to inference from the field name (`RecipeFieldRoles.roleOfName`). A misspelled `role` therefore causes `replaceInput` to fail to recognize the key, rather than causing startup to fail.

<a id="wiki-section-18"></a>
### `kind` values

`json`, `string`, `int`, `number`, `boolean`, `ingredient`, `item_stack`, `fluid_stack`, `fluid_ingredient`, `sized_fluid_ingredient` (case-insensitive; kebab-case is accepted).

<a id="wiki-section-19"></a>
### Schema priority

NekoJS merges recipe schemas from three sources in priority order:

1. **Automatic discovery** (lowest): reflection of Minecraft codecs.
2. **Plugin overrides** (middle): plugins register through `registerRecipeSchemas`.
3. **Data-driven JSON** (highest): your `recipe_types/*.json` files.

A higher-priority schema can override a lower-priority schema. For runtime queries, use [Global bindings - RecipeSchema](global-bindings_us).

---

<a id="wiki-section-20"></a>
## Fallback for unknown types

A field schema is not required to submit complete recipe JSON. For an unrecognized namespace/type with no handler or schema, the raw fallback accepts one object and supplies its `type`. This is **not** an escape hatch for a known rejected profile, and successful registration still depends on the installed serializer accepting the JSON:

```javascript
event.recipes.mymod.unknown_type({
  some_field: 'value',
  another: [1, 2, 3]
})
```

A raw fallback accepts arbitrary member names, but a member existing does not prove a valid recipe serializer or authoring contract. It performs no recipe field validation:

- There must be **exactly one** object argument with members; incompatible argument shapes return `null`, so immediately chaining `.id(...)` can fail. Error handling depends on the entry point: [FallbackNamespaceProxy](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/FallbackNamespaceProxy.java#L41-L61) catches conversion/registration exceptions, logs at debug level and returns `null`; the raw branch of [RecipeNamespaceProxy](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java#L195-L209) does not catch those exceptions. Neither behavior turns a handler execution error or a known rejected profile into a successful fallback.
- Incorrect field names/types are detected only when the game loads the recipe.

Schemas provide positional arguments, required-field validation, key recognition for `replaceInput`/`replaceOutput`, and probe-generated completion. If you only need to insert JSON, the fallback or `event.custom({...})` is enough.

<a id="wiki-section-21"></a>
### Diagnosing a recipe member typed as `never`

A recipe member such as `some_profile: never` means the **effective catalog carries a known rejection** (`unsupportedReason`). The generator does not advertise a callable handler/schema alternative for that name, and the runtime rejects it with `NEKO-5211` rather than using the unknown-type fallback. This is different from an options field such as `input?: never`, which deliberately makes canonical and alias keys mutually exclusive. See [declaration rejection](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java#L188-L208) and [runtime rejection](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java#L64-L77).

Inspect the startup schema-compilation diagnostic for the affected resource, JSON path and recipe id, then regenerate Probe after changing mods/resources or installing a fix. For example, `NEKO-5242` identifies a parent-schema resolution failure in the compatibility graph; it is not a line-length limit or evidence that the entire mod/native serializer is unsupported. Supported parent definitions can resolve that feature gap; other unsupported components/factories need their own implementation. Do not hide the problem with a TypeScript cast or assume increasing the scan allowlist fixes it. See [parent resolution](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/addons/nekujs/src/main/java/com/tkisor/nekujs/recipe/schema/RecipeSchemaGraphCompiler.java#L75-L95). A separate `event.custom` route still requires the actual installed serializer's valid JSON and does not reproduce a rejected profile's constructors, defaults or actions.

---

<a id="wiki-section-22"></a>
## Platform differences

| Platform | Recipe reload |
|---|---|
| **Cleanroom 1.12.2** | Separate legacy branch; this repository does not verify recipe reload or viewer refresh behavior. |
| **Fabric 26.x** | Supports rerunning recipe scripts through `/nekojs reload server`; the current node implementation determines the API. |
| **NeoForge 26.x / 1.21.1** | Supported. `/nekojs reload server` rebuilds the working set from cached `baseJsons`, reruns recipe scripts, and replaces `RecipeManager` (server-side crafting takes effect immediately); the JEI/REI panel may need a client refresh. |

<a id="wiki-section-23"></a>
## Next steps

- [Global bindings](global-bindings_us) - Reference for `Item`/`Ingredient` and other bindings.
- [Event reference](event-reference_us#wiki-section-3) - Events beyond `ServerEvents.recipes`.
- [Event extensions](event-extensions_us) - Schema/namespace registration for plugin developers.

<!-- wiki-nav -->

---

[Previous: Versioned script packs](script-packs_us) · [Contents](Home) · [Next: Registering new content](registering-new-content_us)
