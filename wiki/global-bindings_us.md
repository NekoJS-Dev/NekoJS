<!-- wiki-page: global-bindings; locale: us -->

> **English** · [中文](global-bindings_cn)

<a id="wiki-section-1"></a>
# Global bindings

Bindings are names you can use directly in a script, such as `Item`, `Text` and `JsonIO`. You don't need `require` or `import`. Just write the name:

```javascript
const stone = Item.of('minecraft:stone', 16)
console.info(Text.of('Hello').bold())
```

This page lists every built-in binding, grouped by purpose. Signatures are written as `method(args) → result`. For exact parameters and members, run `/nekojs probe` to generate code completion and trust what your editor shows.

<a id="wiki-section-2"></a>
## Check availability first

Some bindings exist only in certain script types, and some only on certain platforms. Using one where it doesn't exist fails with an unknown-name error.

| Binding | Script types | Platform |
|---|---|---|
| `Item`, `Block`, `Ingredient`, `Items`, `ItemStack` | All | All |
| `Fluid`, `FluidIngredient`, `FluidAmounts`, `Fluids`, `FluidStack` | All | NeoForge only |
| `Text`, `NBT`, `JsonIO`, `ID`, `Platform`, `Performance`, `RecipeSchema` | All | All |
| `Registry` | All | NeoForge; returns empty results on Fabric |
| `I18n` | Client | All |
| `global`, `shared`, `once`, `clearOnce` | All | All |
| `ClientData` / `clientData` | Server pushes / client reads | All |
| `Network` | Server, client | All |
| `Capabilities` | All (used in a startup event) | All, implemented differently per platform |
| `EntitySelectors` | Server, test | All |
| `VillagerTrades` | Server | NeoForge only |
| `DataMap` | All | NeoForge only |
| `PostEffects` | Client | NeoForge only |
| `ClientUI` | Client | NeoForge 26.x only |
| `Assets` | All | NeoForge 26.x only |
| `NativeEvents` | Startup | NeoForge only |
| `Utils`, `Color`, `Time`, `UUID`, `StringUtils`, `KMath`, `JavaMath`, `TextIcons`, `DamageSource`, `ParticleOptions` | All | All |
| `Test` | Test | All |
| `Minecraft`, `Screen`, `Window`, `KeyMapping`, `InputConstants` | Client | All |
| `TriState` | All | 26.x |

For all platform differences, see [Platforms and compatibility](platform-compatibility_us).

<a id="wiki-section-3"></a>
## Items and ingredients

<a id="wiki-section-4"></a>
### Item

Create item stacks and look up items.

| Method | Description |
|---|---|
| `Item.of(id) → ItemStack` | Creates a stack from an ID. You can include a count: `'16x minecraft:stone'` |
| `Item.of(id, count) → ItemStack` | With a count |
| `Item.of(stack) → ItemStack` | Returns the given stack unchanged |
| `Item.of(stack, count) → ItemStack` | Takes a stack and sets the count |
| `Item.empty() → ItemStack` | An empty stack |
| `Item.id(id) → Item \| null` | Looks up an item. The namespace defaults to `minecraft`. Returns `null` if not found |
| `Item.idOf(item) → Identifier` | The registry ID of an item. Takes an item, not a stack |

`Block.id(id)` and `Block.idOf(block)` do the same for blocks. `Items` and `Blocks` are the vanilla item and block constants, for example `Items.DIAMOND`.

```javascript
const sword = Item.of('minecraft:diamond_sword')
const apples = Item.of('minecraft:apple', 5)
if (Item.id('mymod:ruby') === null) console.warn('mymod is not installed')
```

<a id="wiki-itemstack"></a>
<a id="wiki-section-5"></a>
### Item stack methods

On top of the vanilla methods, NekoJS adds these to every item stack:

| Method | Description |
|---|---|
| `getId() → string` | The item ID, for example `minecraft:stone` |
| `getMod() → string` | The ID of the mod that owns the item |
| `getBlock() → Block \| null` | The block for a block item; `null` otherwise |
| `withCount(count) → ItemStack` | Returns a copy with the given count; the original is unchanged. A count below 1 gives an empty stack |
| `setCountAndReturn(count) → ItemStack` | Changes the count and returns the same stack, for chaining |
| `enchantById(id, level) → ItemStack` | Adds an enchantment. Works only while a server is running. For enchanted books, set the `stored_enchantments` component instead |
| `hasEnchantment(id, level) → boolean` | Whether the stack has the enchantment at this level or higher |
| `hasGlint()` / `setGlint(bool)` | Reads or sets the enchantment glint |
| `isUnbreakable()` / `setUnbreakable(bool)` | Reads or sets "unbreakable" |
| `getLore()` / `setLore(lines)` | Reads or sets the lore lines |
| `matches(other) → boolean` | Whether it matches another stack, an item or an ingredient |
| `asIngredient() → Ingredient` | Converts it to an ingredient |
| `weakNBT()` / `strictNBT() → Ingredient` | An ingredient that also matches components, loosely or strictly. On Fabric both require an exact component match |
| `pdata() → PersistentDataJS` | Custom data stored on the stack; see the next section |

<a id="wiki-pdata"></a>
<a id="wiki-section-6"></a>
### Custom data with pdata

Item stacks, entities and players can all store your own data with `pdata()`:

```javascript
// server_scripts/pdata.js
PlayerEvents.loggedIn(event => {
  const data = event.entity.pdata()
  data.putInt('logins', data.getInt('logins') + 1)
})
```

- **Item stacks**: data lives in the vanilla `custom_data` component, so it is saved and moved with the item.
- **Entities and players**: data is saved with the entity. After a server-side write, it syncs to nearby clients automatically.
- **Client side**: entity and player `pdata()` is read-only. Writing to it from a client script throws an error.

For all read and write methods, sync timing and the save format, see [Data and state](data-and-state_us).

<a id="wiki-section-7"></a>
### Ingredient

Create ingredients for recipes.

| Method | Description |
|---|---|
| `Ingredient.of(...values)` | Matches any of the values. A value can be an item ID, `'#tag'`, `'@modid'`, `'*'` (any item), `'/regex/'`, a stack or another ingredient |
| `Ingredient.item(id)` | A single item |
| `Ingredient.tag(id)` | An item tag; the `#` is added for you |
| `Ingredient.any(...ingredients)` | Matches any of several ingredients |
| `Ingredient.all()` | Every item. Only includes items registered at the time of the call |
| `Ingredient.not(ingredient)` | Every item except the given ingredient |

The ingredient objects these return can be combined further:

| Method | Description |
|---|---|
| `or(value)` | Union; takes an ID, item, stack or ingredient |
| `and(x)` / `intersect(x)` | Intersection (NeoForge only) |
| `except(x)` / `subtract(x)` | Difference (NeoForge only) |
| `withCount(n)` / `asStack()` | An ingredient with a count; `asStack()` equals `withCount(1)` (NeoForge only) |
| `matches(stack)` / `test(stack)` | Whether a stack matches |
| `first() → ItemStack` | The first matching stack, or an empty stack |
| `stacks()` / `displayStacks()` | All matching stacks |
| `isEmpty()` | Whether it is empty |
| `unwrap()` | The underlying vanilla `Ingredient` |

```javascript
const logs = Ingredient.of('#minecraft:logs', 'minecraft:stick')
const notDirt = Ingredient.not('minecraft:dirt')
```

Members not listed here go to the static members of the vanilla `Ingredient` class.

<a id="wiki-section-8"></a>
## Fluids (NeoForge only)

None of the bindings in this section exist on Fabric.

<a id="wiki-section-9"></a>
### Fluid

| Method | Description |
|---|---|
| `Fluid.of(value) → FluidStack` | Creates a fluid stack from a fluid ID, a fluid object or `{ fluid, amount }`. Defaults to one bucket |
| `Fluid.of(value, amount) → FluidStack` | With an amount in mB; must be greater than 0 |
| `Fluid.water()` / `Fluid.water(amount)` | Water, one bucket by default |
| `Fluid.lava()` / `Fluid.lava(amount)` | Lava, one bucket by default |
| `Fluid.empty()` | An empty fluid stack |
| `Fluid.ingredient(...values)` | A fluid ingredient matching any of the values |
| `Fluid.sizedIngredient(value)` / `Fluid.sizedIngredient(value, amount)` | A fluid ingredient with an amount |

<a id="wiki-section-10"></a>
### FluidIngredient

| Method | Description |
|---|---|
| `FluidIngredient.of(...values)` | Matches any of the values |
| `FluidIngredient.fluid(id)` | A single fluid |
| `FluidIngredient.tag(id)` | A fluid tag |
| `FluidIngredient.sized(value)` / `FluidIngredient.sized(value, amount)` | A fluid ingredient with an amount |

The returned fluid ingredient also has `or(value)`, `isEmpty()`, `withAmount(amount)` and `unwrap()`.

<a id="wiki-section-11"></a>
### FluidAmounts and fluid constants

`FluidAmounts` holds common amounts, in mB:

| Constant | Value |
|---|---|
| `BUCKET` / `B` | 1000 |
| `MILLIBUCKET` / `MB` | 1 |
| `BOTTLE` | 250 |
| `INGOT` | 90 |
| `NUGGET` | 10 |
| `METAL_BLOCK` | 810 |

`Fluids` holds the vanilla fluid constants (for example `Fluids.WATER`), and `FluidStack` is the fluid stack class.

<a id="wiki-section-12"></a>
## Text

<a id="wiki-section-13"></a>
### Text

Build text for chat messages, tooltips, titles and so on.

| Method | Description |
|---|---|
| `Text.of(text)` | Plain text |
| `Text.empty()` | Empty text |
| `Text.translatable(key, ...args)` | A translation key, shown in each player's own language |
| `Text.translateWithFallback(key, fallback, ...args)` | A translation key that shows `fallback` when the key doesn't exist |
| `Text.keybind(key)` | The key the player has bound, for example `'key.attack'` |
| `Text.score(name, objective)` | A scoreboard score |
| `Text.selector(pattern)` | An entity selector such as `'@p'`. On 26.x it shows as plain text |
| `Text.ofValues(...values)` | Joins several pieces of text in order |
| `Text.join(separator, ...values)` | Joins with a separator, which can be text or a string |

You can pass text anywhere that expects a text component, such as `displayClientMessage`, tooltips and books.

<a id="wiki-section-14"></a>
### Styles and click actions

Style methods don't change the original text. They return new text, so you can keep chaining:

| Method | Description |
|---|---|
| `bold()` / `italic()` / `underlined()` / `strikethrough()` / `obfuscated()` | Bold, italic, underline, strikethrough, obfuscated. Pass `false` to turn one off |
| `color(color)` | A color name (`'red'`) or hex (`'#FF0000'`) |
| `red()`, `gold()`, `aqua()` and so on | Shortcuts for the 16 named colors; `red()` equals `color('red')` |
| `click(action, value)` | Click action: `runCommand`, `suggestCommand`, `openUrl`, `openFile`, `copyToClipboard`, `changePage` |
| `hover(text)` | Text shown on mouse hover |
| `insertion(text)` | Text inserted into the chat box on Shift+click |
| `font(id)` | A font ID |
| `append(...values)` | Appends more text |
| `isEmpty()` | Whether it is empty |

The 16 color shortcuts are `black`, `darkBlue`, `darkGreen`, `darkAqua`, `darkRed`, `darkPurple`, `gold`, `gray`, `darkGray`, `blue`, `green`, `aqua`, `red`, `lightPurple`, `yellow` and `white`.

```javascript
// server_scripts/welcome.js (event.entity on NeoForge, event.player on Fabric)
PlayerEvents.loggedIn(event => {
  const msg = Text.of('Click for a diamond')
    .bold().red()
    .click('runCommand', '/give @s diamond')
    .hover(Text.of('Runs /give'))
  event.entity.displayClientMessage(msg, false)
})
```

<a id="wiki-section-15"></a>
### I18n (client only)

In client scripts, translates a key into a plain string using the player's current language.

| Member | Description |
|---|---|
| `I18n.translate(key, ...args)` | Translates. Returns the key itself if it doesn't exist |
| `I18n.has(key)` | Whether the current language has the key |
| `I18n.translateWithFallback(key, fallback, ...args)` | Translates if the key exists, otherwise uses `fallback` as the template |
| `I18n.languageCode` / `I18n.rightToLeft` | The current language code; whether it is written right to left. Read-only |

Use placeholders like `%s` and `%1$s`. Arguments can only be strings, numbers, booleans and `null`. The result is a string and does not update when the player switches language, so call it again.

For text sent to players that each player should see in their own language, use `Text.translatable(...)` on the server.

```javascript
// client_scripts/labels.js
console.info(I18n.translate('gui.done'), I18n.languageCode)
```

<a id="wiki-section-16"></a>
## Reading and writing data

<a id="wiki-section-17"></a>
### NBT

Create, read and write NBT data. `NBT` produces NekoJS NBT values; you can't use them directly as a vanilla `CompoundTag`.

| Method | Description |
|---|---|
| `NBT.of(value)` | Creates a value from a string, number, array or plain object |
| `NBT.byte/short/int/float/double(n)` | A number of a specific type |
| `NBT.long(text)` | A long. Pass it as a string so JS numbers don't lose precision |
| `NBT.byteArray(arr)` / `NBT.intArray(arr)` | Byte array, int array |
| `NBT.compound()` | A chained builder: `put`, `putInt`, `putString`, `putCompound`, `putList` and more, then `build()` |
| `NBT.parse(snbt)` / `NBT.toSnbt(value)` | Converts between SNBT text and NBT values |
| `NBT.toObject(value)` / `NBT.fromObject(obj)` | Converts between NBT values and plain JS objects |
| `NBT.read(path)` | Reads an NBT file under `nekojs/data/`. Returns `null` if the file doesn't exist |
| `NBT.write(path, value)` | Writes an NBT file. The top level must be an object |

NBT values have methods such as `kind()`, `scalar()`, `values()`, `entries()` and `toSnbt()`.

```javascript
NBT.write('players/neko.nbt', { name: 'neko', level: 12 })
const saved = NBT.read('players/neko.nbt')
if (saved !== null) console.info(saved.toSnbt())
```

`NBT.of` rejects `null`, booleans, functions and arrays with mixed element types. Whole numbers without an explicit type become int; numbers with a fraction become double.

<a id="wiki-jsonio"></a>
<a id="wiki-section-18"></a>
### JsonIO

Parse, generate and save JSON.

| Method | Description |
|---|---|
| `JsonIO.parse(text)` | Parses a JSON string |
| `JsonIO.toString(value)` | Produces compact JSON |
| `JsonIO.toPrettyString(value)` | Produces JSON indented by two spaces |
| `JsonIO.read(path)` | Reads a JSON file under `nekojs/data/`. Returns `null` if the file doesn't exist |
| `JsonIO.write(path, value)` | Writes a JSON file, creating parent folders as needed |

```javascript
JsonIO.write('settings/ui.json', { scale: 2, theme: 'dark' })
const ui = JsonIO.read('settings/ui.json')
console.info(ui === null ? 'No settings yet' : ui.toPrettyString())
```

<a id="wiki-section-19"></a>
### File path rules

`NBT.read/write` and `JsonIO.read/write` can only access files inside `<game folder>/nekojs/data/`:

- Use a relative path with forward slashes, for example `settings/ui.json`.
- Absolute paths, drive letters, backslashes and `..` are not allowed.
- Writes are atomic: the file is either fully written or left unchanged.

NBT files can be at most 3 MiB compressed and 64 levels deep. An invalid path fails with `NBT_PATH_FORBIDDEN`, a file that is too large with `NBT_FILE_TOO_LARGE`, and an SNBT syntax error with `INVALID_NBT`.

<a id="wiki-section-20"></a>
### Registry

Read-only registry lookups. Results are always strings, booleans and arrays.

| Method | Description |
|---|---|
| `Registry.get(registryId)` | Gets a registry view, for example `'minecraft:item'` |
| `view.exists()` | Whether the registry exists |
| `view.all()` | All entry IDs |
| `view.has(id)` | Whether the entry exists |
| `view.tag(tagId)` | All entry IDs in a tag |
| `view.dataMapIds()` | The data maps this registry has |
| `view.dataMapValue(mapId, id)` | An entry's data map value as a JSON string, or `null` |

You can loop over a view with `for...of`:

```javascript
for (const id of Registry.get('minecraft:enchantment')) console.info(id)
```

> On Fabric, `Registry` currently returns only empty results: `exists()` is `false` and lists are empty. Don't take that as proof the registry is actually empty.

<a id="wiki-section-21"></a>
## Sharing data between scripts

<a id="wiki-global-shared"></a>
<a id="wiki-section-22"></a>
### global and shared

- `global`: shared between scripts of the same type. For example, all server scripts share one `global`.
- `shared`: shared between all script types, but only inside the same game process.

Both survive reloads and world changes, and are cleared when the game closes. They are not synced over the network and not saved to the world.

```javascript
// server_scripts/config.js
global.config = { version: 2 }
shared.serverReady = true

// client_scripts/hud.js (readable in singleplayer)
console.info(shared.serverReady)
```

<a id="wiki-section-23"></a>
### once and clearOnce

`once(key, callback)` runs the callback only the first time the key is used. It won't run again after a reload either, until the game closes. Use it for things that should happen once, such as writing an external file.

| Call | Description |
|---|---|
| `once('myInit', () => ...)` | Runs the callback the first time and returns its result; does nothing afterwards |
| `clearOnce('myInit')` | Clears this key so the next `once` runs again. Returns whether it existed |
| `clearOnce()` | Clears every key |

```javascript
once('mypack:warmup', () => console.info('Printed only once'))
```

Keys are global, so scripts that use the same key affect each other. Add your own prefix.

<a id="wiki-client-data"></a>
<a id="wiki-section-24"></a>
### ClientData and clientData

Server scripts push data to clients, and client scripts read it. This is the usual way to feed a HUD.

```javascript
// server_scripts/boss.js
PlayerEvents.loggedIn(event => {
  ClientData.sync('boss_hp', { name: 'Example Golem', hp: 0.42 })
  ClientData.syncTo(event.entity, 'quest', { stage: 3 })
})

// client_scripts/state.js
console.info(clientData.get('boss_hp'))
```

| Method | Description |
|---|---|
| `ClientData.sync(key, value)` | Pushes to every online player |
| `ClientData.syncTo(player, key, value)` | Pushes to one player |
| `clientData.get(key)` | Reads a value. Returns `null` if the key is missing |
| `clientData.has(key)` | Whether the key exists. Use it to tell "missing" from "value is null" |
| `clientData.keys()` / `clientData.size()` | All keys; the number of keys |

- Values must be JSON types: string, number, boolean, object, array or `null`.
- Keys can't be blank and are at most 256 characters. A value can be at most 32768 characters once converted to JSON (a character count, not 32 KB).
- Pushing the same key again replaces the old value. The client's data is cleared when the player disconnects or leaves the world.

<a id="wiki-network"></a>
<a id="wiki-section-25"></a>
### Network

Send custom messages between client and server. The receiver listens with [NetworkEvents](event-reference_us).

| Method | Call from | Description |
|---|---|---|
| `Network.sendToServer(channel, data)` | Client | Sends to the server |
| `Network.sendToPlayer(player, channel, data)` | Server | Sends to one player |
| `Network.sendToAll(channel, data)` | Server | Sends to every player |

`data` is an NBT object. A plain JS object such as `{ action: 'open_menu' }` is converted for you. See [Event reference](event-reference_us) for a full example.

<a id="wiki-section-26"></a>
## Game features

<a id="wiki-capabilities"></a>
<a id="wiki-section-27"></a>
### Capabilities

Creates item, energy and fluid storage for `CapabilityEvents.register`. Registration happens once at startup, so changes need a game restart. For how to register, see [Event reference](event-reference_us).

| Method | Description |
|---|---|
| `Capabilities.itemHandler(size)` | An inventory with `size` slots |
| `Capabilities.energyStorage(capacity, maxReceive, maxExtract)` | Energy storage: capacity, max input per transfer, max output per transfer |
| `Capabilities.fluidTank(capacity)` | A single-slot fluid tank; capacity in mB |

Each method also accepts an `onChange` callback as the last argument. It runs after the contents actually change. Use it to mark data for saving or to schedule a sync.

Keep in mind:

- **Give each block, entity or item its own storage object.** Don't create a new empty one on every lookup, and don't share one between everything of the same kind.
- **Saving and loading is your job.** Registering storage does not write it to the world save.
- **Fabric uses a different fluid unit.** `fluidTank(1000)` is one bucket on both platforms, but on Fabric the reported capacity and transfer amounts are in droplets (1 mB = 81 droplets).

Fabric also offers methods to look up other mods' storage. The capability name is `'item'`, `'fluid'` or `'energy'`:

| Method | Description |
|---|---|
| `Capabilities.getBlock(level, pos, capability, direction)` | Looks up the block at a position |
| `Capabilities.getEntity(entity, capability, direction)` | Looks up an entity |
| `Capabilities.getItem(stack, capability, context)` | Looks up an item stack |
| `Capabilities.blockLookup(capability)` | Gets the block lookup object |
| `Capabilities.entityLookup(capability)` | Gets the entity lookup object |
| `Capabilities.itemLookup(capability)` | Gets the item lookup object |

They return `null` when nothing is found. Fabric has no common energy standard, so `'energy'` only works with storage that uses the NekoJS energy interface.

On NeoForge, members not listed here go to NeoForge's own `Capabilities` class, for example `Capabilities.ItemHandler.BLOCK`.

<a id="wiki-section-28"></a>
### EntitySelectors

In server scripts, build an entity selector in code (like `@e[...]` in a command) and find entities with it. This example finds up to 5 cows near a player when they log in:

```javascript
// server_scripts/cows.js (event.entity on NeoForge, event.player on Fabric)
PlayerEvents.loggedIn(event => {
  const player = event.entity
  const selector = EntitySelectors.allEntities()
    .type('minecraft:cow').isAlive().distance(0, 32)
    .orderNearest().limit(5).create()
  const cows = EntitySelectors.find(player.level(), selector, player.x, player.y, player.z)
  console.info('Nearby cows: ' + cows.size())
})
```

**Pick a starting point:**

- For players: `allPlayers()`, `nearestPlayer()`, `randomPlayer()` or `builder()`.
- For other entities you must start from `allEntities()`, `nearestEntity()` or `randomEntity()`. Adding `type('minecraft:cow')` to a player starting point finds nothing.

**Add conditions:**

- Filters: `type`, `typeTag`, `name`, `gamemode`, `team`, `tag`. Pass `true` as the second argument to invert.
- Other conditions: `level(min, max)` (player XP level), `isAlive()`, `distance(min, max)`, `distanceBelow(max)`, `distanceAbove(min)`, `x/y/z/dx/dy/dz` (an area) and `limit(n)`.
- Sorting: `orderArbitrary()`, `orderNearest()`, `orderFurthest()`, `orderRandom()`.

**Run the query:** `EntitySelectors.find(level, selector, x, y, z)`. The coordinates are the center for distance checks; always pass them. Without them, `(0, 0, 0)` is used. The query only finds loaded entities and never loads chunks.

> A misspelled entity tag doesn't raise an error. It just matches nothing.

<a id="wiki-section-29"></a>
### VillagerTrades (NeoForge only)

Add villager trades with the `ServerEvents.tradeDeclaration` event. `VillagerTrades` is only for looking at the current trades.

```javascript
// server_scripts/trades.js
ServerEvents.tradeDeclaration(event => {
  event.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald',
    result: '5x minecraft:apple',
    maxUses: 12, xp: 2, priceMultiplier: 0.05,
  })
})
console.info(VillagerTrades.query().describe())
```

- Profession trade IDs are `minecraft:<profession>/level_1` to `level_5`.
- Wandering trader: 26.x uses `minecraft:wandering_trader/buying` and similar; 1.21.1 uses `minecraft:wandering_trader/level_1` or `level_2` (`buying` and `common` map to 1, `uncommon` and `rare` to 2).
- If one trade is invalid, none of the batch applies, and the error shows up in `/nekojs error`.
- To remove a group of trades you no longer want, call `ServerEvents.tradeReload(event => event.declareObsolete(id))`.

<a id="wiki-section-30"></a>
### DataMap (NeoForge only)

Looks up built-in item data:

- `DataMap.furnaceFuel(Item.of('minecraft:coal'))`: how many ticks it burns as fuel.
- `DataMap.compostable(Item.of('minecraft:wheat_seeds'))`: the chance it fills a composter.

Both return `null` when there is no data, so you can use `??` for a default. For other data maps, use `Registry.get(registryId).dataMapValue(mapId, id)`.

<a id="wiki-section-31"></a>
### PostEffects (NeoForge client only)

Turn screen post-processing effects on and off:

| Method | Description |
|---|---|
| `PostEffects.set(id)` / `clear()` / `toggle(id)` | Turns an effect on, off, or toggles it |
| `PostEffects.current()` / `isActive()` | The current effect; whether an effect is running |
| `PostEffects.isAvailable(id)` | Whether the effect can be turned on now |
| `PostEffects.presets()` | Vanilla effect IDs such as `minecraft:invert` |

```javascript
// client_scripts/invert.js
if (PostEffects.isAvailable('minecraft:invert')) PostEffects.set('minecraft:invert')
```

Check `isAvailable` before turning an effect on; the effect needs matching files in a resource pack. When the player views through an enderman, spider or creeper, the vanilla effect replaces the one your script set. To declare your own effects and for version differences, see [Client rendering](client-rendering_us).

<a id="wiki-section-32"></a>
### NativeEvents (NeoForge startup scripts only)

Listens to raw NeoForge events directly, for events NekoJS doesn't wrap.

| Method | Description |
|---|---|
| `NativeEvents.onEvent(eventType, handler)` | Listens at normal priority |
| `NativeEvents.onEvent(priority, receiveCancelled, eventType, handler)` | Sets the priority and whether to receive cancelled events |
| `NativeEvents.onGenericEvent(genericClass, eventType, handler)` | Listens to a generic event |

Write `eventType` as the full class name, with `$` for nested classes. Returning `true` from the handler cancels the event (only for cancellable events). Changing a listener needs a game restart.

<a id="wiki-section-33"></a>
### ScriptEvents (startup scripts)

`ScriptEvents` is an event group, not a binding, but it often goes with them: declare your own events in a startup script, then fire and listen to them from server or client scripts.

```javascript
// startup_scripts/events.js
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))

// server_scripts/boss.js
MyEvents.bossKilled(payload => console.info(payload.boss))
MyEvents.bossKilled.post({ boss: 'ender_dragon' })
```

See [Event extensions](event-extensions_us) for details.

<a id="wiki-section-34"></a>
### RecipeSchema

Shows which recipe types exist and which fields each one has. Read-only.

| Method | Description |
|---|---|
| `RecipeSchema.namespaces()` | All recipe namespaces |
| `RecipeSchema.types(namespace)` | The recipe types in a namespace |
| `RecipeSchema.describe(namespace, type)` | The fields and forms of one recipe type |

```javascript
ServerEvents.started(event => {
  console.info(RecipeSchema.types('minecraft'))
  console.info(RecipeSchema.describe('minecraft', 'smelting'))
})
```

To define your own recipe types, see [Recipe system](recipe-system_us).

<a id="wiki-section-35"></a>
### ClientUI and Assets

- `ClientUI`: build client screens with JSX. NeoForge 26.x only. See [JSX client UI](jsx-client-ui_us).
- `Assets`: generate block states, models, textures and other resource files from a script. NeoForge 26.x only. See [Registering new content](registering-new-content_us).

<a id="wiki-section-36"></a>
## Small helpers

<a id="wiki-section-37"></a>
### Utils

| Method | Description |
|---|---|
| `Utils.randomInt(max)` / `Utils.randomInt(min, max)` | A random integer, excluding `max` |
| `Utils.randomDouble()` / `Utils.randomDouble(max)` / `Utils.randomDouble(min, max)` | A random decimal |
| `Utils.chance(probability)` | Returns `true` with the given probability, from `0` to `1` |
| `Utils.isArray(v)` / `Utils.isList(v)` / `Utils.isMap(v)` | Type checks |

<a id="wiki-section-38"></a>
### Color

Colors are integers in ARGB format.

| Method | Description |
|---|---|
| `Color.rgb(r, g, b)` | An opaque color |
| `Color.argb(a, r, g, b)` | A color with alpha |
| `Color.alpha/red/green/blue(color)` | Extracts one channel |
| `Color.hex(color)` / `Color.hexArgb(color)` | Converts to `#RRGGBB` / `#AARRGGBB` |
| `Color.parse(text)` | Parses `#RRGGBB` or `#AARRGGBB` |

<a id="wiki-section-39"></a>
### Time

Game time is counted in ticks; 1 second = 20 ticks.

| Member | Description |
|---|---|
| `Time.SECOND` / `Time.MINUTE` / `Time.HOUR` | 20 / 1200 / 72000 ticks |
| `Time.seconds(n)` / `Time.minutes(n)` / `Time.hours(n)` | Converts to ticks |
| `Time.parseTime(text)` | Converts `'5s'`, `'10m'`, `'2h'`, `'100t'` or `'250ms'` to ticks. A plain number counts as ticks |
| `Time.parseMs(text)` | Same formats, converted to milliseconds. A plain number counts as milliseconds |

<a id="wiki-section-40"></a>
### UUID and StringUtils

| Method | Description |
|---|---|
| `UUID.random()` | A random UUID |
| `UUID.fromString(text)` | Parses a UUID string |
| `UUID.fromName(text)` | A fixed UUID derived from a name |
| `StringUtils.isBlank(s)` / `StringUtils.isEmpty(s)` | Whether a string is blank / empty |
| `StringUtils.capitalize(s)` / `StringUtils.decapitalize(s)` | Uppercases / lowercases the first letter |
| `StringUtils.snakeCase(s)` / `StringUtils.camelCase(s)` | Converts to `snake_case` / `camelCase` |

<a id="wiki-section-41"></a>
### ID

Create resource IDs and split them apart.

| Method | Description |
|---|---|
| `ID.of(text)` / `ID.of(namespace, path)` | Creates an ID. **Without a namespace it defaults to `nekojs`, not `minecraft`** |
| `ID.resource(text)` | Same, with strict format checks: only lowercase letters, digits and `_ . -` (paths may also use `/`) |
| `ID.namespace(id)` / `ID.path(id)` | Gets the namespace / path |
| `ID.asString(id)` | Converts to a `'namespace:path'` string |

<a id="wiki-section-42"></a>
### KMath and JavaMath

`JavaMath` is Java's `Math` class. `KMath` adds a few handy game math helpers:

| Member | Description |
|---|---|
| `KMath.PI` / `KMath.E` | Constants |
| `KMath.deg(degrees)` | Converts degrees to radians |
| `KMath.rad(radians)` | Converts radians to degrees |
| `KMath.blockPos(x, y, z)` | Creates a block position; decimals are floored |
| `KMath.vec3(x, y, z)` / `KMath.vec3(pos)` | Creates a 3D vector |

Mind the direction of `deg` and `rad`: `KMath.deg(90)` returns the radian value `π/2`.

<a id="wiki-section-43"></a>
### TextIcons

Common symbol strings you can put into text: `TextIcons.CHECK` (✔), `CROSS` (✘), `STAR` (★), `HEART` (❤), `WARNING` (⚠), `ARROW_RIGHT` (→) and more. How they look depends on the game font.

<a id="wiki-section-44"></a>
### DamageSource and ParticleOptions

| Method | Description |
|---|---|
| `DamageSource.of(level, typeId)` / `DamageSource.of(level, typeId, entity)` | Creates a damage source from a damage type ID, optionally with the entity that caused it |
| `DamageSource.generic(level)`, `magic`, `explosion`, `fall`, `lava`, `lightning`, `drowning`, `starvation`, `outOfWorld` | Common damage sources |
| `ParticleOptions.of(id)` | A particle with no options, such as `'minecraft:cloud'` |
| `ParticleOptions.of(id, options)` | A particle that needs options. Write `options` in the same format as that particle's vanilla data |

<a id="wiki-section-45"></a>
### Performance

Measures how long code takes, with sub-millisecond precision.

| Method | Description |
|---|---|
| `Performance.now()` | The current timestamp in milliseconds, with decimals |
| `Performance.time(fn)` | Runs `fn` once and returns the time in milliseconds |
| `Performance.bench(fn, runs)` | Runs `fn` `runs` times and returns `{ runs, total, mean, min, max }` |
| `Performance.start(label)` | Starts a timer |

On a timer, `mark(label)` records a stage, `end()` stops timing, and `report()` returns the time for each stage. `mark` and `end` return a new timer, so assign the result back:

```javascript
const ms = Performance.time(() => {
  // code to measure
})
console.info('Took ' + ms + ' ms')

let t = Performance.start('reload')
// ... stage 1 ...
t = t.mark('parse')
// ... stage 2 ...
t = t.mark('run').end()
console.info(t.report())
// { label: 'reload', total: 45.2, marks: [{ label: 'parse', at: 10.1, sincePrev: 10.1 }, ...] }
```

`Performance` doesn't watch your scripts by itself. To find a slow event callback, wrap it in `Performance.time`.

<a id="wiki-section-46"></a>
## Environment

<a id="wiki-section-47"></a>
### Platform

| Method | Description |
|---|---|
| `Platform.isClient()` | Whether this is the client |
| `Platform.isDevelopment()` | Whether this is a development environment |
| `Platform.getMcVersion()` | The Minecraft version |
| `Platform.getLoaderId()` / `Platform.getLoaderVersion()` | The loader ID (`neoforge`, `fabric`) and version |
| `Platform.isLoaded(modId)` | Whether a mod is loaded |
| `Platform.getInfo(modId)` | Mod info: `id`, `name`, `version` |
| `Platform.getList()` | The IDs of all loaded mods |
| `Platform.capabilities()` | Features this platform supports, such as `'nbt-binary-io'` |

```javascript
if (Platform.isLoaded('jei')) console.info('JEI is installed')
```

<a id="wiki-section-48"></a>
## Test scripts

<a id="wiki-section-49"></a>
### Test

Available only in `test_scripts`; run them with `/nekojs test`. Every method returns `Test` itself, so you can chain calls.

| Method | Description |
|---|---|
| `Test.assertTrue(cond, msg)` / `Test.assertFalse(cond, msg)` | The condition is true / false |
| `Test.assertEquals(expected, actual, msg)` | Two values are equal |
| `Test.assertNotNull(value, msg)` | The value is not `null` |
| `Test.assertThrows(fn, msg)` | Running `fn` throws an error |
| `Test.pass(msg)` / `Test.fail(msg)` | Records a pass / failure directly. `fail` throws |
| `Test.section(name)` | Groups the log output |
| `Test.summary()` | Prints a summary |
| `Test.passed()` / `Test.failed()` | The number of passes / failures |

```javascript
// test_scripts/basic.js
Test.section('items')
  .assertEquals('minecraft:stone', Item.of('minecraft:stone').getId(), 'stone ID')
  .summary()
```

<a id="wiki-section-50"></a>
## Vanilla classes

These names map straight to Minecraft classes. Use them for constants, static methods or creating objects:

| Binding | Use |
|---|---|
| `Blocks`, `Items` | Vanilla block and item constants |
| `BlockPos` | Block position |
| `Direction` | Direction |
| `Vec3` | 3D vector |
| `AABB` | Bounding box |
| `CompoundTag` | Vanilla NBT object |
| `DyeColor` | Dye colors |
| `SoundEvents` | Vanilla sounds |
| `ParticleTypes` | Vanilla particles |
| `EntityType` | Entity types |
| `MobEffects` / `MobEffectInstance` | Status effect constants / a status effect instance |
| `DamageTypes` | Damage type constants |
| `Identifier` | Resource ID (`ResourceLocation` on 1.21.1) |
| `Component` / `MutableComponent` | Vanilla text components |
| `TriState` | A three-state value TRUE / FALSE / DEFAULT (26.x only) |
| `Minecraft`, `Screen`, `Window`, `KeyMapping`, `InputConstants` | Client classes, client scripts only. Get the game instance with `Minecraft.getInstance()` |

For other Java classes, use a `java:` import; see [Module system](module-system_us). Some APIs are also published through [modules such as `nekojs/api/stable`](module-system_us#wiki-api-modules); members exported only from a module are not globals.

<a id="wiki-section-51"></a>
## Next steps

- [Event reference](event-reference_us): every available event.
- [Data and state](data-and-state_us): how to choose where to save and sync data.
- [Recipe system](recipe-system_us): `event.recipes` and recipe types.
- [Module system](module-system_us): `java:` imports and multi-file modules.

<!-- wiki-nav -->

---

[Previous: Script properties](script-properties_us) · [Contents](Home) · [Next: Data, state and persistence](data-and-state_us)
