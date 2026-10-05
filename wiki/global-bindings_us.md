<!-- wiki-page: global-bindings; locale: us -->

> **English** · [中文](global-bindings_cn)

<a id="wiki-section-1"></a>
# Global bindings

Global bindings are objects/functions that NekoJS injects into the script's top-level scope. You do not need `require`; use their names directly: `Item.of(...)`, `Ingredient.of(...)`, `Fluid.water()`, and so on.

> This page is a reference manual. Each binding's method signatures are listed, with arguments in parentheses and the return type after the arrow.

---

<a id="wiki-section-2"></a>
## Items / item stacks

<a id="wiki-section-3"></a>
### `Item`

An item helper. `of`/`empty` use helper functions; other members delegate to Minecraft's `Item` class (vanilla item constants can be accessed directly).

| Method | Description |
|---|---|
| `Item.of(id: string) → ItemStack` | Create an item stack from an id; supports count syntax such as `"1x minecraft:stone"` |
| `Item.of(id: string, count: int) → ItemStack` | Specify a count |
| `Item.of(stack: ItemStack) → ItemStack` | Pass through the stack |
| `Item.of(stack: ItemStack, count: int) → ItemStack` | Copy and change the count |
| `Item.empty() → ItemStack` | `ItemStack.EMPTY` |

**`ItemStack` extension methods** (NekoJS injects platform extensions through Mixins):

| Method | Description |
|---|---|
| `withCount(count) → ItemStack` | Return a copy with the specified count |
| `copy() → ItemStack` | Vanilla Minecraft deep copy; not injected again |
| `setCount(count)` | Vanilla Minecraft method (void) |
| `setCountAndReturn(count) → ItemStack` | NekoJS chainable count setter |
| `getId() → string` | Item id |
| `getMod() → string` | Owning mod id |
| `getBlock() → Block` | Corresponding block, if this is a block item |
| `enchantById(id, level) → ItemStack` | Enchant by id (vanilla `enchant()` semantics: writes the ENCHANTMENTS component and validates the enchantment's applicable items; for enchanted books, change the `stored_enchantments` component instead. Ordinary enchanting does not affect books, matching vanilla/KubeJS) |
| `isEnchanted()` | Vanilla Minecraft enchantment state |
| `hasGlint() / setGlint(bool)` | NekoJS glint semantics |
| `hasEnchantment(id, level) → boolean` | Check for an enchantment |
| `matches(other) → boolean` | Match items |
| `asIngredient() → Ingredient` | Convert to an ingredient |
| `weakNBT() / strictNBT()` | NBT matching modes |
| `pdata() → PersistentDataJS` | Read/write custom persistent data on an ItemStack; data is stored in the vanilla `CUSTOM_DATA` component and removed when empty; it does not use the NekoJS entity-PData packet, but stacks in vanilla inventory or other sync paths may carry the component through vanilla synchronization |

<a id="wiki-section-4"></a>
### Entity/player limits of `PersistentDataJS`

`entity.pdata()` (players inherit the entity extension) writes to the entity's `NekoJSPersistentData` subkey. Server-side writes automatically enter the synchronization queue for the next server tick. `sync()` immediately sends the current snapshot to clients tracking the entity. Client scripts read a read-only mirror; all write entry points, including `put*`, `remove`, `merge`, `edit`, `clear`, `replaceTag`, `markDirty`, and `sync`, throw rather than pretending to succeed.

Synchronization packets are deduplicated by entity id and a monotonic revision. When an entity leaves the world, an empty snapshot clears its client mirror. Reused ids continue the revision sequence for the current server lifecycle, preventing delayed old packets from restoring old entity data. Disconnects, world changes, and dimension changes clear client mirrors; SERVER/CLIENT reload does not. Server shutdown clears the server-side dirty/revision records without changing the save format or the `NekoJSPersistentData` key.

<a id="wiki-section-5"></a>
### `Items`

`net.minecraft.world.item.Items`: vanilla item registry constants, such as `Items.STONE` and `Items.DIAMOND_SWORD`.

<a id="wiki-section-6"></a>
### `Ingredient`

An ingredient helper. **Combined binding** (the same pattern as `Item`): the factory methods listed above use the helper; other members delegate to static members of the vanilla `Ingredient` class (such as `Ingredient.empty()`).

| Method | Description |
|---|---|
| `Ingredient.of(...values) → IngredientJS` | Build from any mix of ids/item stacks/ingredients (OR combination) |
| `Ingredient.item(id: string) → IngredientJS` | A single item |
| `Ingredient.tag(id: string) → IngredientJS` | A tag (adds `#` automatically); use `Ingredient.ore(name)` on 1.12.2 |
| `Ingredient.any(...ingredients) → IngredientJS` | OR combination |
| `Ingredient.all() → IngredientJS` | Wildcard matching all registered items. NeoForge uses a live `AnyHolderSet` (registry changes are reflected immediately); 1.12.2 enumerates a snapshot of the current registry (items added after script registration are not included) |
| `Ingredient.not(ingredient) → IngredientJS` | Negation (match every item except those in the ingredient). Supported on NeoForge 1.21.1/26.x (`DifferenceIngredient`); **not supported on 1.12.2** (no native negation type), where it throws a clear exception |

**`IngredientJS` instance methods**:

| Method | Description |
|---|---|
| `or(id\|NekoId\|Item\|ItemStack\|Ingredient\|IngredientJS) → IngredientJS` | Union |
| `and(ingredient)` / `intersect(ingredient) → IngredientJS` | Intersection |
| `except(ingredient)` / `subtract(ingredient) → IngredientJS` | Difference |
| `asIngredient() → Ingredient` | Convert to Minecraft `Ingredient` |
| `asStack() → ItemStack` | The first item stack |
| `withCount(int) → SizedIngredientJS` | Add a count |
| `matches(ItemStack\|Ingredient)` / `test(...) → boolean` | Match |
| `first() → ItemStack` | The first item stack |
| `stacks()` / `displayStacks() → ItemStack[]` | All item stacks |
| `isEmpty() → boolean` | Check whether empty |
| `unwrap() → Ingredient` | The underlying Minecraft `Ingredient` |

---

<a id="wiki-section-7"></a>
## Fluids

<a id="wiki-section-8"></a>
### `Fluid` (NeoForge only)

> Fabric 26.x currently does not register `Fluid`, `FluidStack`, `FluidIngredient`, `Fluids`, or a fluid recipe surface. The following API applies only to NeoForge 1.21.1/26.x; information for 1.12.2 belongs to the legacy branch.

Factory methods use the helper; other members delegate to static members of the vanilla `Fluid` class.

| Method | Description |
|---|---|
| `Fluid.of(value) → FluidStack` | Build from an id / `{fluid, amount}` / fluid object |
| `Fluid.of(value, amount: int) → FluidStack` | Specify an amount (must be > 0) |
| `Fluid.water() → FluidStack` / `Fluid.water(amount)` | Water fluid stack (one bucket by default) |
| `Fluid.lava() → FluidStack` / `Fluid.lava(amount)` | Lava fluid stack |
| `Fluid.empty() → FluidStack` | `FluidStack.EMPTY` |
| `Fluid.ingredient(...values) → FluidIngredientJS` | Build a fluid ingredient (OR combination) |
| `Fluid.sizedIngredient(value)` / `Fluid.sizedIngredient(value, amount) → SizedFluidIngredient` | A fluid ingredient with an amount |

<a id="wiki-section-9"></a>
### `FluidIngredient`

**Combined binding** (NeoForge only; 1.12.2 has no vanilla class of this name, so it is a regular binding with fallback semantics where `of` returns `List<FluidStack>`): factory methods use the helper; other members delegate to static members of NeoForge's `FluidIngredient` class.

| Method | Description |
|---|---|
| `FluidIngredient.of(...values) → FluidIngredientJS` | OR combination |
| `FluidIngredient.fluid(id: string) → FluidIngredientJS` | A single fluid |
| `FluidIngredient.tag(id: string) → FluidIngredientJS` | A tag |
| `FluidIngredient.sized(value)` / `FluidIngredient.sized(value, amount) → SizedFluidIngredient` | Specify an amount |

`FluidIngredientJS` instances provide `.or(...)`, `.isEmpty()`, and other methods.

<a id="wiki-section-10"></a>
### `FluidAmounts`

Fluid amount constants, such as `FluidAmounts.BUCKET` (the amount in one bucket).

<a id="wiki-section-11"></a>
### `Fluids` / `FluidStack`

Direct access to `net.minecraft.world.level.material.Fluids` (vanilla fluid constants) and the Minecraft `FluidStack` class.

---

<a id="wiki-section-12"></a>
## Text components

<a id="wiki-section-13"></a>
### `Text`

| Method | Description |
|---|---|
| `Text.of(text: string) → $TextValue` | Immutable literal text |
| `Text.empty() → $TextValue` | Empty text |
| `Text.translatable(key: string, ...args) → $TextValue` | Immutable translation-key text |
| `Text.translateWithFallback(key: string, fallback: string, ...args) → $TextValue` | Translation key with fallback: shows the fallback literal when the key is missing. 1.12.2 has no native fallback support and displays the fallback directly |
| `Text.keybind(keybind: string) → $TextValue` | A key binding (for example `'key.attack'`), resolved to the player's current key name when rendered |
| `Text.score(name: string, objective: string) → $TextValue` | Scoreboard score (`name` can be `'*'` to use the triggering entity) |
| `Text.selector(pattern: string) → $TextValue` | Entity selector (for example `'@p'`, `'@a[type=zombie]'`). Falls back to literal text on NeoForge 26.x (API limitation); renders normally on 1.21.1/1.12.2 |
| `Text.ofValues(...values) → $TextValue` | Concatenate text arguments in order |
| `Text.join(separator, ...values) → $TextValue` | Join values with a separator (`$TextValue` or string) |

<a id="wiki-section-14"></a>
### `$TextValue` (including rich text styles)

`$TextValue` supports `.append(...values)`, which returns a new value, and `.isEmpty()`. It can be passed to existing binding entry points that expect native components (`displayClientMessage`, tooltips, books, and so on) and is converted to a Minecraft component automatically.

Rich text methods (styles / clicks / hover) return a new **styled** `$TextValue` through chained calls (immutable and chainable):

| Method | Description |
|---|---|
| `.bold([value = true])` / `.italic()` / `.underlined()` / `.strikethrough()` / `.obfuscated()` | Font decorations; an omitted argument means `true` |
| `.color(color)` | Color: a named color (`'red'`) or hex (`'#FF0000'`). Hex works only on 1.15+; 1.12.2 recognizes only 16 named colors |
| `.black()` / `.darkBlue()` / `.darkGreen()` / `.darkAqua()` / `.darkRed()` / `.darkPurple()` / `.gold()` / `.gray()` / `.darkGray()` / `.blue()` / `.green()` / `.aqua()` / `.red()` / `.lightPurple()` / `.yellow()` / `.white()` | KubeJS-style shortcuts for 16 colors, with no arguments; equivalent to `.color('<named color>')` (`.red()` ≡ `.color('red')`) |
| `.insertion(text)` | Insert text into chat on click (effective only in the chat screen) |
| `.font(id)` | Font id (`'mymod:custom'`). Unsupported and ignored on 1.12.2 |
| `.click(action, value)` | Click event: `runCommand` / `suggestCommand` / `openUrl` / `openFile` / `copyToClipboard` / `changePage` |
| `.hover(text: $TextValue)` | Show text on hover |
| `.append(...values)` | Append child components (each child can have its own style) |

> `copyToClipboard` does not exist on 1.12.2 (it is ignored); the value for `changePage` is a page-number string.

```javascript
// Red and bold, with a command on click and a hint on hover
const msg = Text.of('Click to run')
  .bold().color('red')
  .click('runCommand', '/give @s diamond')
  .hover(Text.of('Click to get a diamond'))

player.displayClientMessage(msg, false)

// Concatenate text with different styles
const mixed = Text.ofValues(
  Text.of('Normal ').color('white'),
  Text.of('Bold').bold(),
  Text.of(' Italic').italic()
)
```

> Style methods do not modify the original `$TextValue` (it is immutable); they return a new styled value. Chained calls merge with the existing style.

---


<a id="wiki-section-15"></a>
## Reading and writing data

<a id="wiki-section-16"></a>
### `NbtIO` (removed)

The old NeoForge-only native `NbtIO` binding has been replaced by portable `NBT.read`/`NBT.write`. The new interface does not expose `CompoundTag`/`NBTTagCompound` and provides the same behavior on NeoForge 1.21.1, NeoForge 26.x, and Cleanroom 1.12.2.

The old interface resolved relative paths under `<gameDir>/nekojs/`; the new interface permits only `<gameDir>/nekojs/data/`. When upgrading existing scripts, first move old `NbtIO` files into `nekojs/data/`. This is an intentional tightening of the filesystem security boundary; `NBT.read` does not automatically search for or migrate files from the old directory.

```javascript
// Old interface: NbtIO.write("player.nbt", nativeCompound)
NBT.write("player.nbt", {name: "neko", level: 12})

const player = NBT.read("player.nbt")
if (player !== null) console.log(player.toSnbt())
```

<a id="wiki-section-17"></a>
### `NBT`

A portable immutable NBT value builder. It does not expose `CompoundTag`/`NBTTagCompound`, nor does it automatically convert to native networking, persistent data, or item NBT boundaries.

| Method | Description |
|---|---|
| `NBT.of(value: NbtInput) → $NbtValue` | Strictly construct a string, number, homogeneous array, or object compound |
| `NBT.byte/short/int(number) → $NbtValue` | Explicit integer width |
| `NBT.long(decimal: string) → $NbtValue` | Exact signed long, avoiding JS number precision loss |
| `NBT.float/double(number) → $NbtValue` | Explicit floating-point width |
| `NBT.byteArray/intArray(number[]) → $NbtValue` | Native NBT primitive arrays |
| `NBT.toSnbt(value) → string` | Deterministic SNBT |
| `NBT.parse(snbt: string) → $NbtValue` | Parse an SNBT string (inverse of `toSnbt`); syntax errors throw `INVALID_NBT` |
| `NBT.toObject(value) → object` | Recursively convert `$NbtValue` to plain JS objects (Map→Object / List→Array / scalars extracted directly) |
| `NBT.fromObject(value) → $NbtValue` | Recursively convert plain JS objects to `$NbtValue` (boolean→byte, integer→int/long, floating-point→double) |
| `NBT.read(path: string) → $NbtValue \| null` | Read compressed binary NBT; return `null` if the file does not exist |
| `NBT.write(path: string, value: NbtInput) → void` | Atomically write compressed binary NBT; the root must be a compound |
| `NBT.compound() → CompoundBuilder` | Create an empty chainable compound builder (`put`/`putByte`/`putCompound`/`putList`/`contains`/`size`/`build`); `build()` returns `$NbtValue` |
| `$NbtValue.kind()` / `.scalar()` | Query the tag kind and scalar value; long scalars are decimal strings |
| `$NbtValue.values()` / `.entries()` | Query list elements or compound entries |
| `$NbtValue.toSnbt()` | Deterministic SNBT |

`NbtInput` accepts only strings, finite numbers, `$NbtValue`, homogeneous nested arrays, and plain objects. `null`, booleans, functions, host objects, cycles, and heterogeneous lists are rejected. Without an explicit builder, integers are normalized to `INT` and other numbers to `DOUBLE`; `LONG_ARRAY` is not currently part of the portable baseline.

`NBT.parse` supports the full Mojang SNBT syntax: compounds (`{k:v}`, including quoted keys), lists (`[...]`), byte arrays (`[B;...]`), int arrays (`[I;...]`), suffixed numbers (`5b`/`5s`/`5l`/`5.0f`/`5.0d`), unsuffixed integers (`INT` within the int range, otherwise `LONG`), floating-point numbers (`DOUBLE`), `true`/`false` (→ byte 1/0), quoted/unquoted strings, and escape sequences. `toSnbt` and `parse` support value round trips, but output is normalized and need not preserve the input's whitespace or quoting.

Binary NBT persistence is confined to `<gameDir>/nekojs/data/`. Paths must be forward-slash relative paths; empty paths, absolute paths, drive letters, backslashes, `.`/`..` segments, symlinks, junctions, and reparse points are rejected. Writes use a temporary file in the same directory, forced flushing, and atomic replacement; they do not fall back to non-atomic overwrites.

Default limits are 3 MiB per compressed file, 8 MiB of decoded binary data, 64 levels of depth, 10,000 nodes, and 10,000 primitive-array elements. Native `LONG_ARRAY`, standalone `END`, non-finite floating-point numbers, and corrupted files fail closed. Stable errors include `INVALID_NBT`, `NBT_LIMIT_EXCEEDED`, `NBT_PATH_FORBIDDEN`, `NBT_FILE_TOO_LARGE`, `NBT_IO_ERROR`, and `NBT_ATOMIC_WRITE_FAILED`. Detect support with `Platform.capabilities().includes("nbt-binary-io")`.

<a id="wiki-section-18"></a>
### `Registry`

Read-only registry queries. `Registry.get(registryId)` returns a `RegistryView`; `exists()` is `false` if the registry does not exist. All methods return only basic types (strings/booleans/arrays), not native Minecraft objects.

| Method | Description |
|---|---|
| `Registry.get(registryId) → RegistryView` | Get a read-only registry view, for example `"minecraft:item"` |
| `view.exists() → boolean` | Check whether the registry exists |
| `view.all() → string[]` | All entry ids in the registry, including namespaces |
| `view.has(id) → boolean` | Check whether an id exists in the registry |
| `view.tag(tagId) → string[]` | All entry ids in a tag |
| `view.dataMapIds() → string[]` | All data map type ids registered for this registry (for example `neoforge:furnace_fuels`) |
| `view.dataMapValue(dataMapTypeId, id) → string \| null` | Read the data map value of an entry (JSON string); return `null` when absent |

<a id="wiki-section-19"></a>
### `JsonIO`

Stable JSON values do not expose Gson, `Map`/`List`, or raw filesystem paths. Persistence permits only constrained JSON files under `nekojs/data/`.

| Method | Description |
|---|---|
| `JsonIO.parse(json: string) → $JsonValue` | Strictly parse JSON |
| `JsonIO.toString(value: JsonInput) → string` | Compact JSON |
| `JsonIO.toPrettyString(value: JsonInput) → string` | JSON with two-space indentation |
| `JsonIO.read(path: string) → $JsonValue \| null` | Read a data file; return `null` when absent |
| `JsonIO.write(path: string, value: JsonInput) → void` | Atomically replace a data file, creating parent directories automatically |
| `$JsonValue.toString() → string` | Compact JSON |
| `$JsonValue.toPrettyString() → string` | JSON with two-space indentation |

`JsonInput` can be `null`, a boolean, finite number, string, array, plain object, or `$JsonValue`. Duplicate object keys, non-finite numbers, functions, host objects, cycles, and values exceeding limits are rejected.

`read`/`write` paths must be forward-slash relative paths such as `settings/ui.json`; empty paths, absolute paths, backslashes, `.`/`..` segments, drive letters, and any symlinks are rejected. `write` uses UTF-8 JSON with two-space indentation and an atomic move; it does not fall back to non-atomic overwrites. Raw Gson `parseRaw` is not part of this API.

---

<a id="wiki-section-20"></a>
## Utilities

<a id="wiki-section-21"></a>
### `VillagerTrades` (NeoForge)

NeoForge supports adding trades in server scripts. Fabric currently has no equivalent trade registry mutation; calls to this capability are rejected as unavailable and should not be used in Fabric scripts.

On NeoForge, trades are staged when scripts load and written together at the end of reload. 26.x uses a reloadable trade registry; 1.21.1 replaces static trade tables. New trades appear when villagers next restock:

```javascript
VillagerTrades.add('minecraft:farmer/level_1', {
  cost: '1x minecraft:emerald',       // Also accepts 'minecraft:emerald' or { item: '...', count: n }
  result: '5x minecraft:apple',
  maxUses: 12, xp: 2, priceMultiplier: 0.05,
})
VillagerTrades.add('minecraft:wandering_trader/level_1', { cost: 'minecraft:book', result: 'minecraft:emerald' })
```

Trade set ids have the forms `minecraft:<profession>/level_1..5` and `minecraft:wandering_trader/level_1|level_2`
(aliases `buying|common`→1, `uncommon|rare`→2). The trade set is checked for existence during staging; invalid ids produce a clear error.

<a id="wiki-section-22"></a>
### `EntitySelectors`

Build vanilla entity selectors entirely in code (equivalent to the `@a[...]` selector engine, without assembling strings; server scripts):

```javascript
const cows = EntitySelectors.find(level,
  EntitySelectors.create(b => b.type('minecraft:cow').distance(0, 32).limit(5)), x, y, z)
const nearest = EntitySelectors.find(level, EntitySelectors.nearestPlayer().create(), px, py, pz)
```

The builder supports `type/typeTag/inverse/name/gamemode/team/tag` (xp levels), `distance(min,max)`,
`x/y/z/dx/dy/dz` volume boxes, `limit`, and `order({Arbitrary,Nearest,Furthest,Random})`. Presets are
`allPlayers/allEntities/nearestPlayer/nearestEntity/randomPlayer/randomEntity`. Remember to pass anchor coordinates
for selectors that sort. NeoForge only (26.x/1.21.1).

<a id="wiki-section-23"></a>
### `PostEffects` (client, NeoForge 26.x/1.21.1 only)

> Fabric currently does not register `PostEffects` or provide the corresponding custom GLSL runtime surface.

Client post-processing chains: `PostEffects.set('minecraft:invert')` / `clear()` / `toggle(id)` / `current()`.
Built-in presets (available on both 26.x/1.21.1): `minecraft:invert`, `spider`, `creeper`, `blur`,
`entity_outline`, `transparency`. You can also register custom chains at runtime (inline GLSL, NeoForge 26.x only):

```javascript
PostEffects.register('mymod:gray', {
  chainJson: PostEffectChainJson.simpleBlit('mymod:gray_frag'),  // Generate a main→swap→main chain
  fragmentShaders: { 'mymod:gray_frag': '#version 330\n...' },
})
PostEffects.set('mymod:gray')
```

Note: camera-entity visuals (enderman/spider/creeper) can override or clear script-selected effects.

<a id="wiki-section-24"></a>
### `once` / `clearOnce`

A process-wide run-once guard: `once(key, callback)` runs the callback only on the first use of a key.
**Markers intentionally survive script reload** for the process lifetime. This is useful for external side effects
that should be initialized only once (writing files, registering external systems, and other operations not rolled back by reload).

| Call | Description |
|---|---|
| `once('myInit', () => ...)` | On the first call, run the callback and pass through its return value; later calls with the same key are no-ops |
| `clearOnce('myInit') → boolean` | Rearm one key; return whether the marker previously existed |
| `clearOnce()` | Clear all markers |

```javascript
once('warmup', () => console.info('Printed only after the first reload'))
```

<a id="wiki-section-25"></a>
### `ClientData` / `clientData`

A lightweight server→client key/value data channel (values are JSON types: strings/numbers/booleans/objects/arrays/null;
one serialized value is limited to 32KB; later pushes with the same key replace earlier ones; the client clears data on world exit/disconnect).
Server scripts push with `ClientData`, and client scripts read through read-only `clientData`. It supplies data
for HUD scripts.

```javascript
// server_scripts/boss.js - server pushes
ClientData.sync('boss_hp', { name: 'Calamity Golem', hp: 0.42 })
ClientData.syncTo(player, 'personal_quest', { stage: 3 })

// client_scripts/hud.js - client reads
const info = clientData.get('boss_hp')
if (clientData.has('boss_hp')) { /* ... */ }
```

<a id="wiki-section-26"></a>
### `Capabilities`

NeoForge 1.21.1/26.x and Fabric 26.x all register this Binding. It creates storage objects for providers returned from `CapabilityEvents.register`. Provider registration commits once in STARTUP; ordinary SERVER/CLIENT reload does not register them again. See the [event reference](event-reference_us#wiki-section-15) for registration methods and native context differences.

| Method | Description |
|---|---|
| `Capabilities.itemHandler(size)` | An inventory with N slots; the slot count must not be negative |
| `Capabilities.itemHandler(size, onChange)` | Add an owner change notification |
| `Capabilities.energyStorage(capacity, maxReceive, maxExtract)` | Energy capacity and maximum insertion/extraction per operation; arguments must not be negative; limits are not automatically accumulated per tick |
| `Capabilities.energyStorage(capacity, maxReceive, maxExtract, onChange)` | Add an owner change notification |
| `Capabilities.fluidTank(capacity)` | A single-tank fluid store; input capacity is in mB and must not be negative |
| `Capabilities.fluidTank(capacity, onChange)` | Add an owner change notification |

NeoForge 26.x returns native `ItemStacksResourceHandler`, `SimpleEnergyHandler`, and `FluidStacksResourceHandler` storage, participating in outer transaction commits and rollbacks rather than immediately committing through an old-interface bridge. 1.21.1 returns `ItemStackHandler`, `EnergyStorage`, and `FluidTank`, using native `simulate` semantics without the 26.x outer transaction interface. Fabric returns `FabricItemStorage`, `FabricEnergyStorage`, and `FabricFluidStorage`, using Fabric Transfer transactions; item/fluid storage implement `Storage<ItemVariant>` / `Storage<FluidVariant>`, respectively.

On NeoForge 26.x/Fabric, `onChange` runs after the final transaction commit, not on rollback. On 1.21.1, it notifies only after a real change, not simulated transfer. Owners should use this to mark saves dirty or schedule synchronization. An operation affecting multiple item slots may produce multiple notifications; do not treat the number of notifications as the number of transfers.

`fluidTank(1000)` means one bucket of capacity. NeoForge fluid transfers use mB; Fabric uses droplets (81000 per bucket, or 81 per mB). The returned object's `getCapacity()` (readable as `capacity` in scripts) and transfer arguments use the platform's native units. Do not treat Fabric amounts directly as mB.

Storage must be retained by its concrete owner. Do not create a new empty handler for every provider query, or share one mutable handler among all entities, items, or block entities of the same type. Owners are responsible for saving, restoring, and synchronizing state through the node's native NBT or `ValueInput`/`ValueOutput` interfaces; registration does not provide automatic persistence. Reading corrupted saves must fail strictly and retain the current live values, not replace them with empty storage. Fabric factory `readValue` / `writeValue` methods strictly validate saved fields and capacity; loading/saving within an uncommitted transfer transaction is forbidden, while saving in the final-commit `onChange` callback is allowed. Other native owners should likewise save committed state and implement strict reads through their native persistence interfaces. Provider registration alone does not provide these guarantees.

Fabric additionally provides the following standard query entry points, with capability names `'item'` / `'fluid'` / `'energy'`:

| Method | Description |
|---|---|
| `Capabilities.blockLookup(capability)` | Standard block Lookup; context is a nullable `Direction` |
| `Capabilities.entityLookup(capability)` | NekoJS entity Lookup; context is a nullable `Direction` |
| `Capabilities.itemLookup(capability)` | Standard item Lookup; context is `ContainerItemContext` |
| `Capabilities.getBlock(level, position, capability, direction)` | Query a position; return `null` if no provider exists or the provider declines |
| `Capabilities.getEntity(entity, capability, direction)` | Query a specific entity |
| `Capabilities.getItem(stack, capability, context)` | Query a specific item stack and its container context |

Block/item item/fluid Lookups use Fabric Transfer's standard `ItemStorage`/`FluidStorage` instances; NekoJS provides entity and energy Lookups. Fabric has no standard cross-mod energy API, and `FabricEnergyHandler` is not equivalent to other mods' energy interfaces. Custom Lookup queries still use that Lookup's native `find(...)`.

Every provider can return `null` to decline the query. Provider exceptions, type mismatches, duplicate registration, invalid contexts, and registration after commit remain failures with `NEKO-` codes.

<a id="wiki-section-27"></a>
### `Utils`

| Method | Description |
|---|---|
| `Utils.randomInt(maxExclusive) → int` / `Utils.randomInt(min, maxExclusive) → int` | Random integer |
| `Utils.randomDouble() → double` / `Utils.randomDouble(max)` / `Utils.randomDouble(min, max)` | Random floating-point number |
| `Utils.chance(probability) → boolean` | Return true with the specified probability |
| `Utils.isArray(value)` / `Utils.isList(value)` / `Utils.isMap(value) → boolean` | Type checks |

<a id="wiki-section-28"></a>
### `Color`

| Method | Description |
|---|---|
| `Color.rgb(r, g, b) → int` | Opaque ARGB |
| `Color.argb(a, r, g, b) → int` | ARGB |
| `Color.alpha(color)` / `Color.red(color)` / `Color.green(color)` / `Color.blue(color) → int` | Extract channels |
| `Color.hex(color) → string` | `#RRGGBB` |
| `Color.hexArgb(color) → string` | `#AARRGGBB` |
| `Color.parse(value) → int` | Parse `#RRGGBB` or `#AARRGGBB` |

<a id="wiki-section-29"></a>
### `Time`

Time constants and conversions (tick-based: 1 second = 20 ticks).

| Member | Description |
|---|---|
| `Time.SECOND` = 20 / `Time.MINUTE` = 1200 / `Time.HOUR` = 72000 | Tick constants |
| `Time.seconds(n)` / `Time.minutes(n)` / `Time.hours(n) → int` | Convert to ticks |
| `Time.parseTime(str) → long` | Parse time strings into ticks: `"5s"→100`, `"10m"→12000`, `"2h"→144000`, `"100t"→100`, `"250ms"→5` |
| `Time.parseMs(str) → long` | The same units, returning milliseconds |

<a id="wiki-section-30"></a>
### `UUID`

| Method | Description |
|---|---|
| `UUID.random() → UUID` | Random UUID |
| `UUID.fromString(value) → UUID` | Parse |
| `UUID.fromName(value) → UUID` | Name-based UUID (v3) |

<a id="wiki-section-31"></a>
### `StringUtils`

| Method | Description |
|---|---|
| `StringUtils.isBlank(s)` / `StringUtils.isEmpty(s) → boolean` | Blank/empty checks |
| `StringUtils.capitalize(s)` / `StringUtils.decapitalize(s) → string` | Change capitalization |
| `StringUtils.snakeCase(s)` / `StringUtils.camelCase(s) → string` | Convert naming styles |

<a id="wiki-section-32"></a>
### `ID`

Returns `NekoId` (NekoJS's resource identifier abstraction).

| Method | Description |
|---|---|
| `ID.of(value)` / `ID.of(namespace, path) → NekoId` | Create |
| `ID.namespace(id)` / `ID.path(id)` / `ID.asString(id) → string` | Accessors |
| `ID.asString(id) → string` | Convert an ID object to a string |

<a id="wiki-section-33"></a>
### `Performance`

An explicitly invoked performance measurement tool providing **high-precision** timing (sub-millisecond, based on a monotonic clock). It fills the gap left by the missing script-side `performance.now()`: previously scripts had only millisecond-resolution `Date.now()`, and `process.hrtime()` also used it for simulated high precision.

| Method | Description |
|---|---|
| `Performance.now() → number` | Current monotonic-clock timestamp (milliseconds, `double`, sub-millisecond precision) |
| `Performance.time(fn) → number` | Execute `fn` once and return elapsed milliseconds |
| `Performance.bench(fn, runs) → object` | Execute `fn` `runs` times and return statistics `{ runs, total, mean, min, max }` (milliseconds; includes one warmup to avoid cold-start bias) |
| `Performance.start(label?) → $PerfTimer` | Start a labeled timer and return a `$PerfTimer` handle |

<a id="wiki-section-34"></a>
#### `$PerfTimer` (chainable labeled timer)

An immutable value type: `mark` returns a new instance with one more mark (chainable); `end` freezes the timing reference.

| Method | Description |
|---|---|
| `.mark(label) → $PerfTimer` | Record an intermediate mark, returning a new instance (chainable) |
| `.end() → $PerfTimer` | Freeze the reference at the current time and return a new instance; `elapsedMillis` stops increasing afterward |
| `.elapsedMillis() → number` | Elapsed milliseconds from start to now (or to end time after `end`) |
| `.report() → object` | Structured report: `{ label, total, marks: [{ label, at, sincePrev }] }` (`at` = milliseconds since start; `sincePrev` = milliseconds since the previous mark) |

<a id="wiki-section-35"></a>
#### Usage examples

```javascript
// Measure one function call
const ms = Performance.time(() => {
  // Your code
});
console.log(`Elapsed ${ms} ms`);

// Batch benchmark to locate hotspots
const stat = Performance.bench(() => {
  // Code under measurement
}, 1000);
console.log(stat);
// { runs: 1000, total: 12.34, mean: 0.0123, min: 0.010, max: 0.015 }

// Labeled timer with several marks
let t = Performance.start('reload');
// ... Phase 1 ...
t = t.mark('parse');
// ... Phase 2 ...
t = t.mark('compile');
// ... Phase 3 ...
t = t.mark('run');
t = t.end();
console.log(t.report());
// { label: 'reload', total: 45.2, marks: [
//   { label: 'parse',   at: 10.1, sincePrev: 10.1 },
//   { label: 'compile', at: 30.5, sincePrev: 20.4 },
//   { label: 'run',     at: 45.2, sincePrev: 14.7 } ] }
```

> Note: for fast operations, each run in `bench` may be shorter than the timer resolution; `min`/`max` can still reflect variation. For more stable results, repeat the measured code in a loop, measure the entire loop once, and divide by the iteration count. `Performance` is an explicit measurement tool, not a monitor of script/listener execution. To locate slow listeners, wrap suspected callbacks in `Performance.time(() => ...)`.

---

<a id="wiki-section-36"></a>
## Networking

<a id="wiki-section-37"></a>
### `Network`

Script networking between clients and the server. The payload is `(channel: string, data: CompoundTag)`. Use it with [NetworkEvents](event-reference_us).

| Method | Side | Description |
|---|---|---|
| `Network.sendToServer(channel, data)` | client | Send from client to server |
| `Network.sendToPlayer(player, channel, data)` | server | Send from server to one player |
| `Network.sendToAll(channel, data)` | server | Send from server to all players |

For examples, see [Event reference - NetworkEvents](event-reference_us).

---

<a id="wiki-section-38"></a>
## Querying recipe schemas

<a id="wiki-section-39"></a>
### `RecipeSchema`

A **read-only** recipe schema query API (runtime introspection).

| Method | Description |
|---|---|
| `RecipeSchema.namespaces() → string[]` | All known recipe namespaces |
| `RecipeSchema.types(ns) → string[]` | Type names in a namespace (handler method names + schema types) |
| `RecipeSchema.describe(ns, type) → object` | Returns `{exists, hasHandler, type, idPrefix, fields[], constructors[], handlerFields?, handlerConstructors?}` |

```javascript
ServerEvents.started(event => {
  console.info(RecipeSchema.namespaces())           // ["minecraft", "create", ...]
  console.info(RecipeSchema.types('minecraft'))     // ["crafting_shaped", "smelting", ...]
  console.info(RecipeSchema.describe('minecraft', 'smelting'))
})
```

> To **define** a new schema, see [Recipe system - Data-driven schemas](recipe-system_us#wiki-section-13).

---

<a id="wiki-section-40"></a>
## Environment / shared state

<a id="wiki-section-41"></a>
### `global` and `shared`

- `global`: a state container shared within the current `ScriptType`; it survives ordinary reloads of that type and is not automatically shared across SERVER/CLIENT.
- `shared`: a state container explicitly shared across script types in the same NekoJS runtime. Both exist only in-process; neither provides network synchronization or save persistence.

```javascript
// server_scripts/config.js
global.config = { version: 2 }
shared.serverReady = true

// client_scripts/hud.js
const serverReady = shared.serverReady
```

<a id="wiki-section-42"></a>
### `Platform`

Bound to the `Platform` class, providing information about the current runtime environment. Query capabilities with `Platform.capabilities()`; the existence of the platform object does not make unimplemented Fabric APIs available.

| Method | Description |
|---|---|
| `Platform.isClient() → boolean` | Check whether this is the client |
| `Platform.isDevelopment() → boolean` | Check whether this is a development environment |
| `Platform.getMcVersion() → string` | Minecraft version string |
| `Platform.getLoaderId() → string` | Loader id (for example `neoforge`, `cleanroom`) |
| `Platform.getLoaderVersion() → string` | Loader version |
| `Platform.isLoaded(modId) → boolean` | Check whether a mod is loaded |
| `Platform.getInfo(modId) → object` | Information about a mod |
| `Platform.getList() → array` | List of loaded mods |
| `Platform.capabilities() → Set<PlatformCapability>` | Platform capabilities (feature flags) |

---

<a id="wiki-section-43"></a>
## Client-only bindings (CLIENT scripts only)

| Binding | Description |
|---|---|
| `Minecraft` | `net.minecraft.client.Minecraft` instance |
| `Screen` | `net.minecraft.client.gui.screens.Screen` class |
| `Window` | Client window |
| `KeyMapping` | Key binding class |
| `InputConstants` | GLFW input constants |

---

<a id="wiki-section-44"></a>
## Special STARTUP-only bindings

<a id="wiki-section-45"></a>
### `NativeEvents` (STARTUP only, NeoForge)

Bridges native NeoForge events without NekoJS's event group abstraction. Fabric does not currently register this binding; Cleanroom information belongs to a separate legacy branch.

| Method | Description |
|---|---|
| `NativeEvents.onEvent(eventType, handler)` | Listen with NORMAL priority |
| `NativeEvents.onEvent(priority, receiveCancelled, eventType, handler)` | Full form |
| `NativeEvents.onGenericEvent(genericClass, eventType, handler)` | Generic events |

`eventType` can be a fully qualified class-name string (use `$` for nested classes), a `Class`, or a Graal `Value`. A listener returning `true` is translated to the platform's cancellation operation (only for cancellable events). STARTUP currently cannot be reloaded with `/nekojs reload startup`; restart the game after changing listeners.

<a id="wiki-section-46"></a>
### `ScriptEvents` (STARTUP only)

**Declare custom server/client event groups** in startup scripts (consistent across loaders; the caller supplies the payload). See [Event extensions - ScriptEvents](event-extensions_us).

```javascript
// startup_scripts
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))

// server_scripts
MyEvents.bossKilled(payload => console.info(payload.boss))
MyEvents.bossKilled.post({ boss: 'ender_dragon' })
```

---

<a id="wiki-section-47"></a>
## Bindings for TEST scripts only

<a id="wiki-section-48"></a>
### `Test`

Test assertion API.

| Method | Description |
|---|---|
| `Test.pass(msg)` / `Test.fail(msg)` | Pass / fail (fail throws) |
| `Test.assertTrue(cond, msg)` / `Test.assertFalse(cond, msg)` | Boolean assertions |
| `Test.assertEquals(expected, actual, msg)` | Equality assertion |
| `Test.assertNotNull(value, msg)` | Non-null assertion |
| `Test.assertThrows(callback, msg)` | Expect an exception |
| `Test.section(name)` | Start a section |
| `Test.summary()` | Print a summary |
| `Test.passed()` / `Test.failed()` | Passed/failed counts |

---

<a id="wiki-section-49"></a>
## Direct access to vanilla classes

The following bindings provide direct access to vanilla Minecraft classes, as constructors or sources of constants:

| Binding | Class | Purpose |
|---|---|---|
| `Blocks` | `net.minecraft.world.level.block.Blocks` | Vanilla block constants |
| `BlockPos` | `net.minecraft.core.BlockPos` | Block coordinates |
| `Direction` | `net.minecraft.core.Direction` | Direction enum |
| `Vec3` | `net.minecraft.world.phys.Vec3` | 3D vector |
| `AABB` | `net.minecraft.world.phys.AABB` | Axis-aligned bounding box |
| `CompoundTag` | `net.minecraft.nbt.CompoundTag` | NBT compound tag |
| `DyeColor` | `net.minecraft.world.item.DyeColor` | Dye color enum |
| `SoundEvents` | `net.minecraft.sounds.SoundEvents` | Vanilla sound events |
| `ParticleTypes` | `net.minecraft.core.particles.ParticleTypes` | Vanilla particle types |
| `EntityType` | `net.minecraft.world.entity.EntityType` | Entity type constants |
| `MobEffects` | `net.minecraft.core.registries.MobEffects` (or its counterpart) | Potion effect constants |
| `MobEffectInstance` | Mob effect instance class | Potion effect instance |
| `DamageTypes` | Damage type constants | — |
| `Identifier` | `net.minecraft.resources.Identifier` (26.x) / `ResourceLocation` (1.21.1) | Resource identifier |
| `TriState` | Tri-state enum (NeoForge and Fabric; package name varies by Minecraft version) | TRUE/FALSE/DEFAULT |
| `Component` | `net.minecraft.network.chat.Component` | Text component |
| `MutableComponent` | `net.minecraft.network.chat.MutableComponent` | Mutable text component (NeoForge and Fabric) |

> These classes can also be imported through `java:` module syntax; see [Module system](module-system_us).

<a id="wiki-section-50"></a>
## Next steps

- [Event reference](event-reference_us) - All available events.
- [Recipe system](recipe-system_us) - Details of `RecipeSchema` and `event.recipes.*`.
- [Module system](module-system_us) - `java:` imports and multi-file modules.

<!-- wiki-nav -->

---

[Previous: Script properties](script-properties_us) · [Contents](Home) · [Next: Python scripts](python-scripts_us)
