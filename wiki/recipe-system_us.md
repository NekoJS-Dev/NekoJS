<!-- wiki-page: recipe-system; locale: us -->

> **English** · [中文](recipe-system_cn)

<a id="wiki-section-1"></a>
# Recipe system

Recipe scripts go in `server_scripts/`, inside a `ServerEvents.recipes` handler. Save the file, run `/nekojs reload`, then try the recipe in a crafting table or furnace to confirm it works.

<a id="wiki-section-2"></a>
## Quick start

Create `nekojs/server_scripts/recipes.js`:

```javascript
ServerEvents.recipes(event => {
  // Remove every recipe that makes sticks
  event.remove({ output: 'minecraft:stick' })

  // 1 cobblestone makes 4 sticks
  event.shaped('4x minecraft:stick', ['C'], { C: 'minecraft:cobblestone' })
    .id('mypack:cobble_to_sticks')

  // Smelt sand into glass: 0.1 XP, 200 ticks
  event.smelting('minecraft:glass', 'minecraft:sand', 0.1, 200)
    .id('mypack:sand_to_glass')

  // In every shaped recipe, use birch planks instead of oak planks
  event.replaceInput({ type: 'minecraft:crafting_shaped' }, 'minecraft:oak_planks', 'minecraft:birch_planks')
})
```

The recipes work as soon as `/nekojs reload` finishes. Recipe viewers such as JEI or REI may still show old recipes; rejoin the world to refresh them. To undo everything, delete the script and reload again.

<a id="wiki-recipe-filters"></a>
<a id="wiki-section-3"></a>
## Selecting recipes: filters

The first argument of `remove`, `replaceInput`, `find` and similar methods is a filter. It picks the recipes to work on. The object form is the most common:

| Key | Value | Selects |
|---|---|---|
| `id` | Recipe ID or regex | Recipes whose ID equals it or matches the regex |
| `mod` | Mod ID or regex | Recipes whose ID namespace is that mod |
| `output` | Item, tag or `Item.of(...)` | Recipes that produce this item |
| `input` | Item, tag or `Item.of(...)` | Recipes that use this item as an ingredient |
| `type` | Recipe serializer ID | One exact kind of recipe, such as `minecraft:crafting_shaped` |
| `recipeType` | Recipe type ID | A broad category, such as `minecraft:crafting` (shaped and shapeless) |
| `group` | String or regex | The recipe group |
| `idStartsWith` / `idEndsWith` / `idContains` | String | IDs that start with, end with or contain the text |
| `and` | Array of filters | Every entry matches |
| `or` | Array of filters | Any entry matches |
| `not` | Filter | The entry does not match |

How filters combine:

- Several keys in one object must **all** match.
- An array `[a, b]` matches if **any** entry matches, the same as `{ or: [a, b] }`.
- `{}` and `'*'` select every recipe. `'-'` selects none.
- A plain string selects by ID, for example `event.remove('minecraft:stick')`.
- A misspelled key throws `NEKO-5003`. It never silently turns into "select everything".

```javascript
ServerEvents.recipes(event => {
  // Minecraft's own stick recipes
  event.remove({ mod: 'minecraft', output: 'minecraft:stick' })

  // Shaped recipes that use any planks
  event.remove({ type: 'minecraft:crafting_shaped', input: '#minecraft:planks' })

  // Minecraft recipes whose ID ends with planks
  event.remove({ id: /^minecraft:.*planks$/ })

  // A function filter must return true or false
  event.remove(match => match.recipe.mod === 'examplemod')
})
```

Keep in mind:

- `type` tells shaped and shapeless crafting apart. Old scripts that use `{ type: 'minecraft:crafting' }` should switch to `{ recipeType: 'minecraft:crafting' }`.
- `input` and `output` ignore the count. To compare every item component as well, write `{ output: { match: Item.of(...), exact: true } }`.
- `input` and `output` only understand vanilla shaped, shapeless, stonecutting and cooking recipes. Special recipes from mods may not match; use `id` or `mod` for those.
- A broken recipe that the game cannot read never matches any filter.

<a id="wiki-section-4"></a>
## Removing and replacing

| Method | What it does |
|---|---|
| `event.remove(filter)` | Removes every selected recipe |
| `event.removeById(id)` | Removes one recipe by ID |
| `event.replaceInput(filter, oldIngredient, newIngredient)` | Replaces an ingredient in the selected recipes |
| `event.replaceOutput(filter, oldResult, newResult)` | Replaces a result in the selected recipes and keeps the original count |

```javascript
ServerEvents.recipes(event => {
  // Use birch planks instead of oak planks everywhere
  event.replaceInput({}, 'minecraft:oak_planks', 'minecraft:birch_planks')

  // Every recipe that makes iron ingots now makes gold ingots
  event.replaceOutput({}, 'minecraft:iron_ingot', 'minecraft:gold_ingot')
})
```

When replacing an ingredient, any ingredient that **includes** the item is replaced as a whole. For example, replacing `minecraft:oak_planks` also replaces an ingredient written as `#minecraft:planks`. Narrow the filter if you only want to change some recipes.

Replacement only looks at common field names: `ingredient`, `ingredients`, `input`, `inputs` and `key` for ingredients, and `result`, `results`, `output` and `outputs` for results. If a mod's recipe uses other names, mark those fields in a schema (see below) or edit the JSON directly.

<a id="wiki-section-5"></a>
## Writing ingredients

Wherever an ingredient is expected (shortcut method ingredients, `replaceInput` arguments, the `input` filter and so on), you can use any of these forms:

| Form | Meaning | Editor completion |
|---|---|---|
| `'minecraft:stone'` | One item | Yes |
| `'#minecraft:planks'` | Item tag | Yes |
| `'@create'` | Every item from a mod | Yes |
| `'*'` | Any item | Yes |
| `'/wool$/'` | Items whose ID matches a regex | No |
| `Item.of('minecraft:stone')` | Item stack | Yes |
| `['minecraft:stone', '#minecraft:planks']` | Array: any entry works | Yes |
| `{ item, tag, mod, regex, wildcard, filter, any, all, not }` | Object form | Partly (`item`, `tag`, `mod`) |

A result can be written as `'4x minecraft:stick'` or `Item.of('minecraft:stick', 4)`.

Completion for item IDs, tags and mod names comes from `/nekojs probe`. After adding mods, registering new items or changing tags, run `/nekojs probe` again; otherwise the editor marks the new IDs as errors.

<a id="wiki-section-6"></a>
## Adding recipes

<a id="wiki-section-7"></a>
### Shortcut methods

| Method | Notes |
|---|---|
| `event.shaped(result, pattern, keys)` | Shaped crafting. `pattern` is an array of strings; `keys` is `{ character: ingredient }` |
| `event.shapeless(result, ingredients)` | Shapeless crafting |
| `event.smelting(result, ingredient)` / `event.smelting(result, ingredient, xp, time)` | Furnace. Defaults to 0.1 XP and 200 ticks |
| `event.blasting(result, ingredient)` / `event.blasting(result, ingredient, xp, time)` | Blast furnace. Defaults to 0.1 XP and 100 ticks |
| `event.smoking(result, ingredient)` / `event.campfireCooking(result, ingredient)` | Smoker (100 ticks) / campfire (600 ticks); two-argument form only |

```javascript
ServerEvents.recipes(event => {
  event.shaped('minecraft:diamond_sword', [
    'D',
    'D',
    'S'
  ], {
    D: 'minecraft:diamond',
    S: 'minecraft:stick'
  }).id('mypack:diamond_sword_alt')

  event.shapeless('minecraft:dirt', ['minecraft:sand', 'minecraft:gravel'])
    .id('mypack:sand_gravel_to_dirt')
})
```

<a id="wiki-recipe-namespace"></a>
<a id="wiki-section-8"></a>
### Any recipe type: event.recipes

`event.recipes.<namespace>.<type>(...)` adds any recipe type, including recipes from mods:

```javascript
ServerEvents.recipes(event => {
  event.recipes.minecraft.crafting_shaped(
    Item.of('minecraft:stick', 2), ['B'],
    { B: 'minecraft:bamboo' }
  ).id('mypack:bamboo_to_sticks')

  event.recipes.minecraft.smelting({
    ingredient: 'minecraft:gold_ore',
    result: 'minecraft:gold_ingot',
    experience: 1.0,
    cookingtime: 200
  }).id('mypack:smelt_gold')
})
```

Each recipe type takes different arguments. **Do not copy the argument order of the shortcut methods.** The reliable way is to run `/nekojs probe` and follow what your editor suggests. You will usually see two forms: positional arguments, and a single object with named fields (like `smelting` above).

Having a mod installed does not mean every one of its recipe types has a ready-made form. Trust the editor's suggestions, not examples copied from another modpack.

<a id="wiki-section-9"></a>
### Writing JSON directly: custom and builder

If you know a recipe's full JSON, you can write it directly. The JSON must match the format your game version and the mod expect:

```javascript
ServerEvents.recipes(event => {
  // custom: pass the complete JSON, including type
  event.custom({
    type: 'minecraft:smelting',
    ingredient: Ingredient.of('minecraft:cobblestone'),
    result: Item.of('minecraft:stone'),
    experience: 0.1,
    cookingtime: 200
  }).id('mypack:cobble_to_stone')

  // builder: write one field at a time
  event.builder('minecraft:crafting_shapeless')
    .id('mypack:stone_button')
    .property('category', 'redstone')
    .property('ingredients', [Ingredient.of('minecraft:stone')])
    .output('result', Item.of('minecraft:stone_button'))
    .validate()
})
```

You can put `Ingredient.of(...)` and `Item.of(...)` straight into the JSON; they are converted to the right format. If the JSON passed to `custom` has no `type`, it returns `null`, and a following `.id(...)` throws.

`.validate()` checks right away whether the game can read the recipe and throws if it cannot. Add it while you are testing a new recipe.

<a id="wiki-section-10"></a>
### Recipe IDs

Give every new recipe an ID with `.id('namespace:name')`. Without one, NekoJS generates an ID like `nekojs:shaped_1a2b3c4d`, which can change when the script changes.

Without a namespace, `.id('foo')`, `removeById('foo')` and `event.get('foo')` read it as `nekojs:foo`, but the filter `{ id: 'foo' }` reads it as `minecraft:foo`. Always write the full ID.

<a id="wiki-section-11"></a>
## Reading and editing existing recipes

| Method | Notes |
|---|---|
| `event.find(filter)` | Returns the selected recipes |
| `event.all()` | Returns every recipe |
| `event.get(id)` | Returns one recipe by ID, or `null` |
| `event.forEach(filter, recipe => {})` / `event.forEach(recipe => {})` | Handles recipes one by one. `event.modify` is the same method |
| `event.count()` / `event.count(filter)` | Number of recipes |
| `event.exists(id)` / `event.exists(filter)` | Whether a recipe exists |
| `event.ids()` | Every recipe ID |
| `event.getJson(id)` / `event.setJson(id, jsonString)` | Reads or overwrites one recipe as a JSON string |
| `event.print(filter?)` / `event.dump(filter?)` | Prints recipes to the log / returns them as a JSON string |

Each recipe returned by `find`, `forEach` and similar methods supports:

| Method | Notes |
|---|---|
| `.id()` / `.id(newId)` | Reads or changes the ID |
| `.type()` / `.group()` / `.group(name)` | Reads the type; reads or sets the group |
| `.json()` | The recipe's JSON object |
| `.setPath(path, value)` / `.setPaths({ ... })` | Sets a value by JSON path, such as `'result.count'` |
| `.removePath(path)` / `.removeProperty(key)` | Removes a field |
| `.merge({ ... })` | Merges the fields of an object in |
| `.copy(newId)` | Copies it into a new recipe |
| `.remove()` | Removes the recipe |
| `.validate()` | Checks that the game can read the recipe |

```javascript
ServerEvents.recipes(event => {
  event.forEach({ output: 'minecraft:iron_ingot', recipeType: 'minecraft:smelting' }, recipe => {
    console.info(`Found recipe: ${recipe.id()}`)
    recipe.setPath('experience', 0.5)
  })
})
```

The object returned when you add a recipe (for example from `event.shaped(...)`) also has `.id()`, `.group()`, `.setPath()`, `.merge()`, `.validate()` and so on, plus `.property(key, value)`, `.input(key, ingredient)` and `.output(key, item)`. On NeoForge, `.fluid(...)`, `.fluidIngredient(...)` and `.sizedFluidIngredient(...)` write fluid fields.

<a id="wiki-section-12"></a>
## Keeping or damaging ingredients after crafting

On Minecraft 26.x, a crafting recipe can decide what happens to an ingredient after crafting: a tool loses durability instead of disappearing, a catalyst stays, or the ingredient turns into another item. This only applies to crafting tables and the inventory crafting grid.

```javascript
ServerEvents.recipes(event => {
  // The iron pickaxe is a tool: it loses 1 durability and goes back to the grid
  event.shaped('minecraft:diamond_pickaxe', ['PDD', ' S ', ' S '], {
    P: 'minecraft:iron_pickaxe', D: 'minecraft:diamond', S: 'minecraft:stick'
  })
    .id('mypack:upgrade_pickaxe')
    .damageIngredient('minecraft:iron_pickaxe', 1)

  // The nether star is a catalyst: it stays after crafting
  event.shapeless('minecraft:enchanted_golden_apple', ['minecraft:golden_apple', 'minecraft:nether_star'])
    .id('mypack:golden_apple_upgrade')
    .keepIngredient('minecraft:nether_star')

  // The water bucket leaves a glass bottle instead of an empty bucket
  event.shapeless('minecraft:clay_ball', ['minecraft:dirt', 'minecraft:water_bucket'])
    .id('mypack:mud_clay')
    .replaceIngredient('minecraft:water_bucket', 'minecraft:glass_bottle')
})
```

| Method | Effect | Notes |
|---|---|---|
| `damageIngredient(ingredient, amount)` | The ingredient stays with less durability | `amount` is required; write `1` for one point. It disappears when durability runs out; items without durability stay unchanged |
| `keepIngredient(ingredient)` | The ingredient stays unchanged | Good for catalysts |
| `replaceIngredient(ingredient, newItem)` | The ingredient turns into the new item | `newItem` can be an ID or `Item.of(...)` |

Rules:

- Call `.id(...)` first, then these methods. They are stored under the recipe ID at the time you call them.
- A recipe can have several of these. Each grid slot uses the first rule that matches. Unmatched slots keep the vanilla behavior (a water bucket still leaves an empty bucket).
- These rules are not written into the recipe JSON. Every reload rebuilds them from your scripts, so deleting a line and reloading removes the rule.
- Use them on recipes returned by `event.shaped`, `event.shapeless`, `event.custom` and `event.builder`.

<a id="wiki-recipe-schema-json"></a>
<a id="wiki-section-13"></a>
## Writing a schema for a custom recipe type

A schema describes the fields of a recipe type. NekoJS scans installed recipe types at startup and builds schemas automatically, so most recipes need nothing from you. When the scan gets a type wrong or misses fields, write a JSON file to add or override its schema.

A schema lets you pass positional arguments, checks required fields, lets `replaceInput` / `replaceOutput` recognize the fields, and gives `/nekojs probe` the information for completion.

This feature currently works on NeoForge only.

<a id="wiki-section-14"></a>
### File location and example

Put the file at `nekojs/data/<namespace>/nekojs/recipe_types/<type>.json`. For example, `nekojs/data/mymod/nekojs/recipe_types/grinding.json` defines `event.recipes.mymod.grinding(...)`.

```json
{
  "type": "mymod:grinding",
  "constructors": [
    ["result", "ingredient"],
    ["result", "ingredient", "time"]
  ],
  "fields": {
    "result":     { "kind": "item_stack" },
    "ingredient": { "kind": "ingredient" },
    "time":       { "path": "processing_time", "kind": "int", "default": 100 }
  }
}
```

Run `/reload` or rejoin the world, then run `/nekojs probe` to refresh completion. You can then write `event.recipes.mymod.grinding('minecraft:gravel', 'minecraft:cobblestone')`.

<a id="wiki-section-15"></a>
### Top-level keys

| Key | Required | Default | Meaning |
|---|---|---|---|
| `type` | No | `<namespace>:<type>` | The `type` written into the recipe JSON |
| `id_prefix` | No | `<namespace>_<type>` | Prefix for generated IDs |
| `constructors` | No | All fields in the order written | Positional argument lists; each entry is a list of field names |
| `fields` | **Yes** | None | Field names and their definitions |
| `unique` | No | `[]` | Records which fields tell recipes apart; does not affect ID generation |

<a id="wiki-section-16"></a>
### Field definitions

| Key | Default | Meaning |
|---|---|---|
| `path` | Field name | Where the value goes in the JSON; a path like `a.b` works |
| `kind` | `json` | The value type; see below |
| `array` | `false` | Whether the value is written as an array |
| `optional` | `false` | Whether the field can be left out. A field with `default` can also be left out |
| `default` | None | The value used when the field is left out |
| `role` | Guessed from the field name | `INPUT`, `OUTPUT` or `OTHER`; decides whether `replaceInput` / `replaceOutput` touch the field |

<a id="wiki-section-17"></a>
### Values for kind

`json`, `string`, `int`, `number`, `boolean`, `ingredient`, `item_stack`, `fluid_stack`, `fluid_ingredient`, `sized_fluid_ingredient`. Case does not matter, and forms like `item-stack` also work.

<a id="wiki-section-18"></a>
### When the file is wrong

These mistakes make the file fail to load, with the error in the game log:

- `fields` is missing or an empty object.
- A field definition is not an object.
- An entry in `constructors` is not an array, or names a field that is not in `fields`.
- `kind` has an unknown value.

A misspelled `role` **does not** cause an error; the role is guessed from the field name instead. If `replaceInput` skips a field, check the spelling of `role` first.

<a id="wiki-recipe-schema-priority"></a>
<a id="wiki-section-19"></a>
### Schema priority

A recipe type can get schemas from several places. Later ones override earlier ones:

1. Schemas built by the automatic scan.
2. Schemas registered by plugins.
3. Your `recipe_types/*.json` files.

To see the schema in use from a script, use `RecipeSchema` in [Global bindings](global-bindings_us).

<a id="wiki-section-20"></a>
## Recipe types without a schema

You can still add a recipe type that has no schema through `event.recipes`. Pass one object with all the fields:

```javascript
ServerEvents.recipes(event => {
  event.recipes.mymod.unknown_type({
    some_field: 'value',
    another: [1, 2, 3]
  }).id('mypack:unknown_example')
})
```

NekoJS adds `type: 'mymod:unknown_type'` and writes the other fields as they are. This works the same as `event.custom`. Whether the fields are right is only known when the game reads the recipe, so add `.validate()`.

Pass exactly **one object**. Any other arguments return `null`, and a following `.id(...)` throws.

> If your editor shows a recipe as `xxx: never`, NekoJS has confirmed that it does not support that recipe. Calling it throws `NEKO-5211` instead of falling back to the form above. The startup log explains why. If you need it anyway, use `event.custom` and write the complete, correct JSON yourself.

<a id="wiki-section-21"></a>
## Optional NekuJS compatibility addon

NekuJS is a separate download that lets NekoJS use recipe extensions written for KubeJS, such as the recipe forms from KubeJS Create and KubeJS Mekanism.

- It only supports **Minecraft 1.21.1 + NeoForge 21.1 + Java 21**.
- Besides NekoJS, install NekuJS, the KubeJS extension mods you want and their dependencies.
- **Do not** install KubeJS or Rhino themselves. NekuJS already provides the parts they need.
- Not every recipe from those extensions is supported. The forms suggested after `/nekojs probe` are the ones that work.
- After adding or removing these mods, restart the game and run `/nekojs probe` again.

<a id="wiki-section-22"></a>
## Platform differences

| Feature | NeoForge 26.x | NeoForge 1.21.1 | Fabric 26.x |
|---|---|---|---|
| Add, remove and edit recipes; apply with `/nekojs reload` | ✅ | ✅ | ✅ |
| Fluid fields (`.fluid(...)` and friends) | ✅ | ✅ | ❌ |
| Keeping or damaging ingredients after crafting | ✅ | ❌ | ✅ |
| Automatic schemas and `recipe_types` JSON | ✅ | ✅ | ❌ |
| NekuJS compatibility addon | ❌ | ✅ | ❌ |

Fabric has no schemas. `event.recipes.minecraft.crafting_shaped` and `crafting_shapeless` still work; other types are treated as recipe types without a schema and need a complete JSON object.

<a id="wiki-section-23"></a>
## Next steps

- [Global bindings](global-bindings_us): `Item`, `Ingredient`, `RecipeSchema` and more.
- [Event reference](event-reference_us): server events beyond `ServerEvents.recipes`.
- [Event extensions](event-extensions_us): how plugin developers register recipe schemas and namespaces.

<!-- wiki-nav -->

---

[Previous: Versioned script packs](script-packs_us) · [Contents](Home) · [Next: Registering new content](registering-new-content_us)
