<!-- wiki-page: event-reference; locale: us -->

> **English** · [中文](event-reference_cn)

<a id="wiki-section-1"></a>
# Event reference

NekoJS organizes game events into **Event Groups (EventGroup)**. Each group is a top-level Binding, and its events are invoked as methods.

> **Platforms**: this page primarily documents NeoForge's complete event surface. Fabric 26.x supports script loading, recipes, networking, common player/entity/block/item events, and key bindings, but remains a subset. NeoForge-only events and events not yet ported are marked. See [Platforms and compatibility](platform-compatibility_us) for the full differences.

> **Unified cancellation**: cancel a cancellable event by **returning `true` from its listener**. NekoJS translates this into native platform cancellation, such as NeoForge's `setCanceled`. This is the only cross-platform cancellation form; native methods such as `event.setCanceled(true)` / `event.isCanceled()` can also be called directly on their respective platforms.
> - `event.level`: the 1.12.2 BlockEvent has an injected `level` property mapped to `getWorld()`, matching NeoForge's `getLevel()`.
>
> Scripts receive native event objects with all native getters available. The 1.12.2 BlockEvent additionally has the injected `level` property described above.
>
> Earlier versions provided mixin-injected `event.cancel()` / `event.isCancelled()` on NeoForge. These have been **removed**; use `return true` instead. They were never available on 1.12.2, and Cleanroom's `Event` base class cannot be mixed into, so the two platforms were inherently asymmetric.

```javascript
ServerEvents.recipes(event => { /* ... */ })
PlayerEvents.loggedIn(event => { /* ... */ })
```

<a id="wiki-section-2"></a>
## Basic event listener forms

```javascript
// Ordinary event
事件组.事件名(event => { /* Handler logic */ })

// Dispatched event: the first argument is a dispatch key, such as a block/item/entity type id
BlockEvents.broken('minecraft:stone', event => { /* Trigger only when stone is broken */ })
ItemEvents.foodEaten('minecraft:apple', event => { /* Trigger only after eating an apple */ })
EntityEvents.death('minecraft:zombie', event => { /* Trigger only when a zombie dies */ })

// Cancellable event: return true to cancel
BlockEvents.broken('minecraft:stone', event => {
  if (某种条件) return true   // Cancel block breaking
})
```

> **Important**: register listeners in the **top-level script scope**, during script loading. Registering another event inside an event callback fails.

---

<a id="wiki-section-3"></a>
## ServerEvents (server)

| Event | Event object | Description |
|---|---|---|
| `tickPre` | `ServerTickEvent.Pre` | Before each server tick. |
| `tickPost` | `ServerTickEvent.Post` | After each server tick. |
| **`recipes`** | [`RecipeEventJS`](recipe-system_us) | **Recipe modification**, the most commonly used event. |
| `afterRecipes` | `RecipeEventJS` | After the entire recipe data batch is parsed and committed to the `RecipeManager` (postprocessing/queries; JSON changes at this stage no longer take effect, so make changes in `recipes`). |
| `aboutToStart` | `ServerAboutToStartEvent` | The server is about to start. |
| `starting` | `ServerStartingEvent` | The server is starting. |
| `started` | `ServerStartedEvent` | Server startup is complete (registries are ready, so `Item.of(...)` can safely be called). |
| `stopping` | `ServerStoppingEvent` | The server is stopping. |
| `stopped` | `ServerStoppedEvent` | The server has stopped. |
| `datapackSync` | `OnDatapackSyncEvent` | Data pack synchronization. |
| `tagsUpdated` | `TagsUpdatedEvent` | Tag reload completed. |
| `lootTableLoad` | `LootTableLoadEvent` | Loot table loading (modifiable). |
| **`lootTables`** (NeoForge only) | `LootTableEventJS` | **Loot table JSON management** (getJson/setJson/create/remove/getIds; see below). |
| **`tags`** (dispatched, NeoForge only) | `TagEventJS` | **Tag modification**; the dispatch key is a registry id such as `'minecraft:item'`. |
| **`generateData`** (dispatched, NeoForge only) | `DataGeneratorJS` | **Data generation**: write data pack JSON (loot/advancement/worldgen, etc.); the dispatch key is a stage such as `'after_mods'`. |

```javascript
ServerEvents.started(event => {
  console.info('服务端启动完成')
})

ServerEvents.tags('minecraft:item', event => {
  event.add('minecraft:logs', 'mymod:custom_log')
})
```

> `lootTables`, `tags`, and `generateData` in the table above are currently registered only on NeoForge. Fabric's `ServerEvents` primarily provides `recipes`, `afterRecipes`, and ported lifecycle events. Do not call these NeoForge events in Fabric scripts.

<a id="wiki-section-4"></a>
### ServerEvents.lootTables - loot table JSON management

Triggered during server data reload, before loot table parsing; modifications **take effect in that same reload**. The event object is `LootTableEventJS`:

| Method | Description |
|---|---|
| `getJson(id)` | Current JSON for the specified loot table, including script changes; returns `null` for an unknown id. |
| `setJson(id, json)` | Replace the specified loot table (json is a JS object or JSON string). |
| `modify(id, consumer)` | Convenience editing: read current JSON, invoke the builder callback, then write it back (see below). |
| `modifyBlockLoot(blockId, consumer)` | Batch-edit block tables: block id / `'#tag'` (expanded) / `'*'` / predicate function. |
| `modifyEntityLoot(entityId, consumer)` | Batch-edit entity tables: entity id / `'*'` / predicate function. |
| `create(id, json)` | Create or replace a loot table (`setJson` alias; the table need not already exist). |
| `remove(id)` | Remove a loot table (reading it yields an empty table). |
| `getIds()` | All known loot table ids, including tables created by scripts. |

The `modify` callback receives a `LootTableJS`:

| LootTableJS | Description |
|---|---|
| `getType()` / `setType(str)` | Table type, such as `'minecraft:chest'`. |
| `getPools()` | Existing pool list (wrapped objects; edits take effect directly). |
| `addPool(consumer)` / `addPool(obj)` | Append a pool using a builder callback or complete pool JSON. |

`LootPoolJS` (pool): `rolls(number or [min,max])`, `getEntries()`, `addEntry(consumer)` / `addEntry(obj)`, `when(conditionJson)`, `toJson()`.

`LootEntryJS` (entry): `LootEntry.of(item id / '#tag' / ItemStack / JS object)`, `type()`, `name()`, `item()`, `weight(int)`, `when(conditionJson)`, `group(...)`.

```javascript
// server_scripts/loot.js
ServerEvents.lootTables(event => {
  // Add an extra item to the vanilla dungeon chest
  let json = event.getJson('minecraft:chests/simple_dungeon')
  json.pools.push({
    rolls: 1,
    entries: [{ type: 'minecraft:item', name: 'minecraft:diamond' }]
  })
  event.setJson('minecraft:chests/simple_dungeon', json)

  // Builder form: add a high-weight item to the fishing table
  event.modify('minecraft:gameplay/fishing', table => {
    table.addPool(pool => {
      pool.rolls(1).addEntry(entry => entry
        .item('mymod:lucky_coin')      // Item id / ItemStack / '#tag'
        .weight(5)
        .when({ condition: 'minecraft:random_chance', chance: 0.5 })
      )
    })
  })

  // Create a table for use with chests and LootContext
  event.create('mymod:chests/treasure', {
    type: 'minecraft:chest',
    pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'mymod:test_mob_spawn_egg' }] }]
  })

  // Batch: every log block drops an extra apple
  event.modifyBlockLoot('#minecraft:logs', table => {
    table.addPool(pool => pool.rolls(1).addEntry('minecraft:apple'))
  })

  // Batch predicate: match all block tables in the minecraft namespace
  event.modifyBlockLoot(id => id.startsWith('minecraft:'), table => {
    table.addPool(pool => pool.rolls(1).addEntry('minecraft:stick'))
  })

  // Batch: add a 5% feather drop to every entity table
  event.modifyEntityLoot('*', table => {
    table.addPool(pool => pool.rolls(1).addEntry(
      entry => entry.item('minecraft:feather').when({ condition: 'minecraft:random_chance', chance: 0.05 })
    ))
  })

  // Remove an unwanted table
  event.remove('minecraft:chests/jungle_temple')
})
```

> Note: `getIds()/getJson()` return data from **the most recent load**, including modifications. Calling them before entering a world for the first time returns an empty list. Changes are applied together during server data reload (`/reload`). Batch modifications affect **known tables** (`'*'` and predicates do not enumerate blocks/entities without loot tables; exact ids and `#tag` expansion are not subject to this restriction).

<a id="wiki-section-5"></a>
### Global Loot Modifiers

NeoForge global modifiers append or rewrite **each loot result**, rather than replace entire tables. They are implemented by writing data pack JSON through `generateData`; there is no runtime event (`LootModifierManager` is read-only, and modifiers must be enabled in the `neoforge:loot_modifiers/global_loot_modifiers.json` list):

```javascript
// server_scripts/modifiers.js
ServerEvents.generateData('after_mods', event => {
  // 1) Enable the modifier (NekoJS data packs merge in layers with other packs;
  // omit replace: true to avoid replacing another pack's list)
  event.json('neoforge/loot_modifiers/global_loot_modifiers.json', {
    replace: false,
    entries: ['mymod:add_apple']
  })
  // 2) Modifier definition: replace loot, or add it (NeoForge's built-in LootModifier)
  event.json('mymod/loot_modifiers/add_apple.json', {
    type: 'neoforge:add_loot_table',
    conditions: [],
    loot_table: 'minecraft:gameplay/fishing'
  })
})
```

`neoforge:add_loot_table` injects another loot table's complete output into each loot result. Custom modifiers require Java (`IGlobalLootModifier`).

<a id="wiki-section-6"></a>
### ServerEvents.tags - tag modification

Modify tag contents. `TagEventJS` provides `add(tag, entry)`, `remove(tag, entry)`, `removeAll(tag)`, `replaceAll(tag, ...entries)`, and `getEntries(tag)`.

**NeoForge (26.x / 1.21.1)**: the dispatch key is a registry id. The event fires during tag loader construction through a mixin, before `tagsUpdated`. On 26.x, pending entries from a registration builder's `.tag(...)` (see [Registering new content - tag](registering-new-content_us#wiki-section-11)) are injected **before** event dispatch. Listeners can already see them through `getEntries` and still override them with `add`/`remove`.

```javascript
// server_scripts/tags.js
ServerEvents.tags('minecraft:item', event => {
  event.add('minecraft:logs', 'mymod:custom_log')   // Add an item to a tag
  event.remove('minecraft:logs', 'minecraft:birch_log')  // Remove a tag entry
  event.replaceAll('minecraft:planks', 'minecraft:oak_planks')  // Replace all tag contents
})
ServerEvents.tags('minecraft:block', event => { /* Block tags */ })
```

**Cleanroom 1.12.2 (OreDictionary adapter)**: the dispatch key is fixed to `'ore_dict'` (1.12.2 predates 1.13 and has no modern registry-specific tag system). Scripts use the same form, but it maps to OreDictionary underneath and is **meaningful only for items/blocks**. The event fires once at `serverAboutToStart`, after SERVER scripts load, so ore dictionary changes take effect before recipe registration.

```javascript
// 1.12.2: the dispatch key is fixed to 'ore_dict'
ServerEvents.tags('ore_dict', event => {
  event.add('ingots/iron', 'mymod:custom_iron_ingot')  // Add to an ore name
  event.getEntries('ingots/iron')  // Read the ore name's item list
})
```

> **Cross-platform semantics**: NeoForge's `tags('minecraft:item', ...)` and Cleanroom's `tags('ore_dict', ...)` use different dispatch keys, so scripts cannot be ported word for word. OreDictionary in 1.12.2 describes groups of equivalent items, and removal is a runtime operation. Strict cross-platform tag manipulation is therefore only approximated on 1.12.2.

---

<a id="wiki-section-7"></a>
## PlayerEvents (server)

| Event | Dispatch? | Event object | Description |
|---|---|---|---|
| `loggedIn` | — | `PlayerLoggedInEvent` | Player joined. |
| `loggedOut` | — | `PlayerLoggedOutEvent` | Player left. |
| `chat` | — | `ServerChatEvent` | Sending chat. |
| `tickPre` / `tickPost` | — | `PlayerTickEvent.Pre`/`Post` | Player tick. |
| `cloned` | — | `PlayerEvent.Clone` | Player data cloned (death/dimension change). |
| `respawned` | — | `PlayerRespawnEvent` | Respawn. |
| `changedDimension` | — | `PlayerChangedDimensionEvent` | Dimension change. |
| `advancement` | — | `AdvancementEarnEvent` | Advancement earned. |
| `containerOpened` (old name `inventoryOpened` deprecated) | — | `PlayerContainerEvent.Open` | Container opened. |
| `containerClosed` (old name `inventoryClosed` deprecated) | — | `PlayerContainerEvent.Close` | Container closed. |
| `entityInteract` | — | `PlayerInteractEvent.EntityInteract` | Entity interaction. |
| `crafted` | By `Item` | `ItemCraftedEvent` | An item was crafted. |
| `smelted` | By `Item` | `ItemSmeltedEvent` | An item was smelted. |
| `destroyed` | By `Item` | `PlayerDestroyItemEvent` | An item broke. |
| `inventoryChanged` | By `Item` | `InventoryChangedEventJS` | Player inventory contents changed (`event.item` / `event.slot`; for example `PlayerEvents.inventoryChanged('minecraft:stone', ...)`). |

```javascript
PlayerEvents.inventoryChanged('minecraft:diamond', event => {
  console.info(`玩家物品栏里出现了钻石（槽位 ${event.slot}）`)
})
```

```javascript
PlayerEvents.loggedIn(event => {
  const player = event.getEntity()
  player.displayClientMessage(Text.of(`欢迎，${player.getName().getString()}！`), false)
})

PlayerEvents.crafted('minecraft:stick', event => {
  console.info('玩家合成了一根木棍')
})
```

---

<a id="wiki-section-8"></a>
## EntityEvents (server, dispatched by `EntityType`)

| Event | Event object | Description |
|---|---|---|
| `damagePre` | `LivingDamageEvent.Pre` | Before damage (cancellable: return true sets damage to zero; NeoForge maps this to `setNewDamage(0)`, the damage pipeline continues, and `damagePost` still fires with 0; Fabric prevents the entire damage instance. `event.setNewDamage(n)` can change the amount on NeoForge only). |
| `damagePost` | `LivingDamageEvent.Post` | After damage. |
| `death` | `LivingDeathEvent` | Death. |
| `drops` | `LivingDropsEvent` | Drops (modifiable). |
| `finalizeSpawn` | `FinalizeSpawnEvent` | Spawn decision. |
| `tickPre` / `tickPost` | `EntityTickEvent` | Entity tick. |
| `joinLevel` / `leaveLevel` | — | Enter/leave a world. |
| `useItemStarted` / `useItemStopped` / `useItemFinished` / `useItemTick` (dispatched by `Item`, NeoForge only) | — | Item use lifecycle. |

```javascript
EntityEvents.death('minecraft:zombie', event => {
  console.info('一只僵尸死在了', event.getEntity().blockPosition())
})
```

---

<a id="wiki-section-9"></a>
## BlockEvents (server, dispatched by `Block`)

| Event | Event object | Description |
|---|---|---|
| `broken` | `BreakBlockEvent` | Block broken (cancellable). |
| `entityPlaced` | `BlockEvent.EntityPlaceEvent` | Placed by an entity. |
| `entityMultiPlaced` | `EntityMultiPlaceEvent` | Multiple blocks placed. |
| `neighborNotify` | `NeighborNotifyEvent` | Neighbor notification. |
| `fluidPlaced` | — | Fluid placed. |
| `farmlandTrample` | — | Farmland trampled (cancellable). |
| `portalSpawn` | `PortalSpawnEvent` | Portal generated. |
| `toolModification` | — | Tool modification. |
| `placed` | `BlockEvent.EntityPlaceEvent` | Block placed (alias of `entityPlaced`). |
| `rightClicked` | `PlayerInteractEvent.RightClickBlock` | Block right-clicked. |
| `leftClicked` | `PlayerInteractEvent.LeftClickBlock` | Block left-clicked. |
| `randomTick` | `RandomTickEvent` | Random block tick (only for `isRandomlyTicking()` blocks; `event.getLevel()/getPos()/getState()/getRandom()`). |
| `blockEntityTick` | `BlockEntityTickEvent` | Block entity tick (dispatched by `BlockEntityType`; `event.getBlockEntity()`). |
| `modification` | `BlockModificationEventJS` | **Runtime block property modification** (NeoForge 26.x; snapshot restoration semantics, see below). |

```javascript
// Random ticks fire only for naturally randomly ticking blocks (isRandomlyTicking,
// such as wheat/grass blocks; stone and similar blocks do not randomly tick)
BlockEvents.randomTick('minecraft:wheat', event => {
  // event.getLevel() / event.getPos() / event.getState() / event.getRandom()
})

// Block entity tick: listen to a furnace every tick
BlockEvents.blockEntityTick('minecraft:furnace', event => {
  const be = event.getBlockEntity()
})
```

> **Cleanroom 1.12.2 differences**: event names have been unified to the canonical names (`broken`/`placed`, matching NeoForge). It additionally provides `harvestDrops`; it has no `toolModification` and does not support `modification`.

<a id="wiki-section-10"></a>
### BlockEvents.modification - runtime block property modification

Fires on every server startup and `/nekojs reload server`. Before reload, all previous modifications are automatically restored (restore the snapshot, then replay),
so removing a `modify` declaration automatically reverts that change on the next reload. Modifiable properties follow below; unset properties retain their original values. An unset getter reads the current value, so it can be read before being changed:

| Property | Type | Description |
|---|---|---|
| `hardness` | `float >= 0` | Hardness (mining time; stone 1.5, obsidian 50). |
| `resistance` | `float >= 0` | Blast resistance (use `3600000` for bedrock-level resistance). |
| `lightLevel` | `int 0..15` | Light level (glowstone 15). |
| `requiresTool` | `boolean` | Whether the correct tool is required for drops. |
| `friction` | `float 0..1` | Friction (ice 0.98, default 0.6). |
| `jumpFactor` | `float` | Jump factor (vanilla blocks 0.5..1). |

```javascript
// server_scripts/block_mods.js
BlockEvents.modification(event => {
  event.modify('minecraft:stone', block => {
    block.hardness = 2
    block.resistance = 6
    block.lightLevel = 15     // Glowing stone
    block.requiresTool = false
    block.friction = 0.8      // Slightly slippery
    // block.jumpFactor = 1.2
  })

  // Change only one property, or read before writing
  event.modify('minecraft:obsidian', block => {
    block.hardness = block.hardness / 2   // Read the current value and halve it
  })
})
```

> **Visibility**: changes are written to block properties and **all existing block states**, taking effect immediately on the server for mining, explosions, drop decisions, etc.
> They are **not actively synchronized to clients**. Visual changes such as light levels become visible after reentering the world or resynchronizing chunks. Mining speed
> is calculated by the server and is unaffected. Supported only on NeoForge 26.x, not currently on 1.12.2 or 1.21.1.


```javascript
BlockEvents.broken('minecraft:diamond_ore', event => {
  console.info('钻石矿被挖了！玩家：', event.getPlayer().getName().getString())
})
```

---

<a id="wiki-section-11"></a>
## ItemEvents

| Event | Type | Dispatch? | Event object | Description |
|---|---|---|---|---|
| `rightClicked` | server | By `Item` | `PlayerInteractEvent.RightClickItem` | Item right-clicked. |
| **`tooltip`** | **client** | By `Item` | `ItemTooltipEvent` | **Modify an item tooltip**. |
| `canPickUp` | server | By `Item` | `ItemEntityPickupEvent.Pre` | Whether pickup is allowed (return `true` to prevent it; old name `pickedUpPre` deprecated). |
| `pickedUp` | server | By `Item` | `ItemEntityPickupEvent.Post` | After pickup. |
| `dropped` | server | By `Item` | `ItemTossEvent` | Dropped. |
| `entityInteracted` | server | By `Item` | `PlayerInteractEvent.EntityInteract` | Interact with an entity using an item. |
| `foodEaten` | server | By `Item` | `LivingEntityUseItemEvent.Finish` | Eating completed. |
| `modification` | server | — | `ItemModificationEventJS` | **Runtime modification of default item properties** (NeoForge 26.x/1.21.1; snapshot restoration semantics). |

> **Cleanroom 1.12.2 differences**: `dropped` now uses the canonical name (`ItemTossEvent`); `expire` (`ItemExpireEvent`) is also available. There is no separate Pre variant: `pickedUp`/`canPickUp` bind to the same event (`pickedUpPre` is deprecated and never existed on 1.12.2). `tooltip` is a server event on 1.12.2. `modification` is unsupported on 1.12.2, where item properties use a field model and need a separate adapter.

**Default item property modification example** (`ItemEvents.modification`, fired on each server startup and `/nekojs reload server`;
previous changes are restored before reload, so removing a `modify` declaration automatically removes that change on the next reload):

```javascript
// server_scripts/item_mods.js
ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 64     // 1..99
    item.rarity = 'epic'       // 'common' | 'uncommon' | 'rare' | 'epic'
    item.fireResistant = true
    // item.maxDamage = 2000   // maxDamage > 0 and maxStackSize > 1 cannot both be set
  })

  // Food fields are optional: nutrition defaults to 1, saturation to 0.1,
  // canAlwaysEat to false; eatSeconds is optional. Saturation is a coefficient,
  // matching KubeJS: absolute saturation = nutrition × saturation × 2.
  // Missing eating components are added (default 1.6 seconds); existing ones are kept;
  // explicitly setting eatSeconds replaces the duration
  event.modify('minecraft:stick', item => {
    item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }
    // item.food = null       // Remove food and eating animation from edible items (potions unaffected)
  })

  // Weapon attributes: replace base_attack_damage / base_attack_speed modifiers
  // (main-hand ADD_VALUE); keep other modifiers unchanged. Either property can be set alone.
  // Values are bonuses: they exclude the player's base attack damage 2.0 and attack speed 4.0
  event.modify('minecraft:iron_ingot', item => {
    item.attackDamage = 6    // Total attack damage = 2.0 + 6
    item.attackSpeed = -2.4  // Total attack speed = 4.0 - 2.4 = 1.6 (sword speed)
  })

  // Tools: write a TOOL rule that applies to all blocks (mine at the given speed
  // and count as the correct tool, including drops from obsidian).
  // miningSpeed is required (> 0); damagePerBlock defaults to 1, canDestroyBlocksInCreative to true.
  // 26.x has no item-side mining level to set (tool tiers belong to block components)
  event.modify('minecraft:blaze_rod', item => {
    item.tool = { miningSpeed: 6 }
    // item.tool = null      // Remove the TOOL component
  })
})
```

**Tooltip modification example** (place in `client_scripts`):

```javascript
// client_scripts/tooltips.js
ItemEvents.tooltip('minecraft:diamond', event => {
  event.add(Text.of('闪耀夺目').setStyle(/* ... */))
  event.add('第二行普通文本')
})
```

---

<a id="wiki-section-12"></a>
## LevelEvents (server)

| Event | Event object | Description |
|---|---|---|
| `loaded` / `unloaded` / `saved` | `LevelEvent` | World loaded/unloaded/saved. |
| `tickPre` / `tickPost` (old name `tick` deprecated) | `LevelTickEvent` | World tick. |
| `explosionStart` (old name `beforeExplosion` deprecated) | `ExplosionEvent.Start` | Explosion starts (cancellable). |
| `explosionDetonate` (old name `afterExplosion` deprecated) | `ExplosionEvent.Detonate` | Explosion effects. |

---

<a id="wiki-section-13"></a>
## NetworkEvents (dispatched by channel string)

Client-server script communication. Use with the [Network](global-bindings_us#wiki-section-37) Binding.

| Event | Type | Description |
|---|---|---|
| `server` | server | Receive a client packet: `event.channel`, `event.data` (CompoundTag), `event.player`. |
| `client` | client | Receive a server packet: `event.channel`, `event.data`. |

**Complete example** (client key press → notify server → server response):

```javascript
// client_scripts/key_handler.js
NetworkEvents.client('my_channel', event => {
  console.info('收到服务端消息：', event.data)
})

// Send from a client key event
// Network.sendToServer('my_channel', { action: 'open_menu' })
```

```javascript
// server_scripts/menu_handler.js
NetworkEvents.server('my_channel', event => {
  console.info(`玩家 ${event.player.getName().getString()} 发来：`, event.data)
  Network.sendToPlayer(event.player, 'my_channel', { result: 'ok' })
})
```

> `data` is a `CompoundTag`; GraalJS automatically converts JS objects to NBT.

---

<a id="wiki-section-14"></a>
## RegistryEvents (STARTUP only)

Register new content through the **single entry point** `register`. The callback's `event` is the generic registration event, with a convenience method for each registry. See [Registering new content](registering-new-content_us).

| Event | Event object | Description |
|---|---|---|
| `register` | `RegistryEvent` | Collection callback posted once per startup; convenience methods `event.item(id, b => ...)` / `event.block(...)` / `event.fluid(...)` / `event.entityType(...)` / `event.enchantment(...)` / `event.creativeModeTab(...)` / `event.mobEffect(...)` / `event.potion(...)` / `event.soundEvent(...)` / `event.particleType(...)` / `event.paintingVariant(...)` / `event.villagerType(...)`, plus `event.custom(id, typeName, cb)` / `event.register(registry, id, supplier)`. |

> Convenience methods come from the type list registered at the `registry_types` Extension Point: a registry has one only if it has a default type. The set changes with the platform's registries; `particleType` / `paintingVariant` are NeoForge-only. Cleanroom 1.12.2 still uses the old multiple-entry pipeline and has not adopted this form.

<a id="wiki-section-15"></a>
## CapabilityEvents (STARTUP)

<a id="wiki-section-16"></a>
### NeoForge: native capability registration

Register providers for ordinary blocks, block entities, entities, and items on NeoForge 1.21.1 / 26.x. The event commits only once during the mod bus's
`RegisterCapabilitiesEvent` phase; ordinary script reload does not modify native capability registration again.
Collection validates target ids and capability scopes without calling providers; providers execute only when native queries arrive.
After a successful commit, late registration and a second commit are rejected. Failed commits also cannot retry a partially modified native registry.

| Method | Description |
|---|---|
| `registerBlock(blockId, capability, provider)` | Standard ordinary-block capability; provider receives `(level, pos, state, blockEntity, direction)`, where `blockEntity` may be `null`. |
| `registerBlockNative(blockId, nativeCapability, provider)` | Third-party `BlockCapability`; provider receives the same five arguments, with native context as the last one. |
| `registerBlockEntity(typeId, capability, provider)` | Compatibility entry; provider takes no arguments, is called on every query, and its result is not automatically cached. |
| `registerBlockEntityContext(typeId, capability, provider)` | Standard block entity capability; provider receives `(blockEntity, direction)`. |
| `registerEntity(typeId, capability, provider)` | Standard entity capability; provider receives `(entity, context)`; also supports `'item_automation'`. |
| `registerItem(itemId, capability, provider)` | Standard item capability; provider receives `(stack, context)`. |
| `registerBlockEntityNative(typeId, nativeCapability, provider)` | Third-party `BlockCapability`; provider receives `(blockEntity, nativeContext)`. |
| `registerEntityNative(typeId, nativeCapability, provider)` | Third-party `EntityCapability`; provider receives `(entity, nativeContext)`. |
| `registerItemNative(itemId, nativeCapability, provider)` | Third-party `ItemCapability`; provider receives `(stack, nativeContext)`. |

Standard capability names are `'item'` / `'energy'` / `'fluid'`. Native entries preserve the supplied capability's
`typeClass` / `contextClass` contract. Obtain third-party objects through static fields using an installed mod's `Java.type(...)` or a plugin Binding,
without making NekoJS depend directly on Curios, Botania, or similar mods. Native entries use distinct names and do not compete with old Supplier entries for script overload resolution.

Native context differences:

| Scope | NeoForge 1.21.1 | NeoForge 26.x |
|---|---|---|
| Block entity item/energy/fluid | Nullable `Direction` | Nullable `Direction` |
| Entity item | `null` (full entity inventory) | `null` (full entity inventory) |
| Entity item_automation/energy/fluid | Nullable `Direction` | Nullable `Direction` |
| Item item/energy/fluid | `null` | Native `ItemAccess`, not `Direction` |
| Third-party capability | Determined by the native capability's `contextClass` | Same as on the left |

Any provider may return `null` to decline the query. Non-null results must implement the native capability's `typeClass`;
the 1.21.1 item fluid capability requires `IFluidHandlerItem`, not a standalone tank implementing only `IFluidHandler`.
Standard 26.x results are `ResourceHandler` / `EnergyHandler`; 1.21.1 results are `IItemHandler` / `IEnergyStorage` / `IFluidHandler` (`IFluidHandlerItem` for items).

In the following example, `ModCapabilityAccess` is a Binding supplied by a third-party mod or plugin; its `ofEntity/ofItem` return handlers already owned by those objects:

```javascript
CapabilityEvents.register(event => {
  event.registerEntity('minecraft:player', 'energy', (entity, direction) => {
    return ModCapabilityAccess.ofEntity(entity, direction)
  })
  event.registerItemNative('mymod:accessory', ModCapabilityAccess.itemCapability, (stack, context) => {
    return ModCapabilityAccess.ofItem(stack, context)
  })
})
```

Providers must return stable handlers owned by the specific object. Do not create fresh empty storage with `Capabilities.itemHandler(...)`,
`energyStorage(...)`, or `fluidTank(...)` on each query, and do not share one mutable storage instance across every same-type object in the world.
Registration only provides an access entry point; it does not replace the object's saving, synchronization, lifecycle, or transaction implementation. When block capability availability changes,
the provider's owner must still call native `level.invalidateCapabilities(pos)` to notify caches.

Unknown ids (including defaulted-registry fallback), invalid capability/context input, duplicate registration, provider exceptions, and wrong return types
remain failures carrying `NEKO-` codes. Provider registration callbacks have loader lifetime; restart the game to replace them.

<a id="wiki-section-17"></a>
### Fabric: native Lookup registration

Fabric 26.x also registers `CapabilityEvents.register`. After STARTUP declarations and object registration finish, a validated batch is installed into real `BlockApiLookup` / `EntityApiLookup` / `ItemApiLookup` instances; this is neither NeoForge's registration bus nor a no-op. Registration has loader lifetime and is not repeated on ordinary reload. Late registration, duplicate providers, and retries of failed batches are rejected.

| Method | Description |
|---|---|
| `registerBlockEntity(typeId, capability, provider)` | No-argument Supplier compatibility entry, called for each query without automatic result caching. |
| `registerBlockEntityContext(typeId, capability, provider)` | Standard capability; provider receives `(blockEntity, direction)`. |
| `registerEntity(typeId, capability, provider)` | No-argument Supplier compatibility entry. |
| `registerEntityContext(typeId, capability, provider)` | Standard capability; provider receives `(entity, direction)`. |
| `registerItem(itemId, capability, provider)` | No-argument Supplier compatibility entry. |
| `registerItemContext(itemId, capability, provider)` | Standard capability; provider receives `(stack, containerItemContext)`. |
| `registerBlockLookup(blockId, lookup, provider)` | `BlockApiLookup`; provider receives `(level, pos, state, blockEntity, context)`, with a nullable block entity. |
| `registerBlockEntityLookup(typeId, lookup, provider)` | `BlockApiLookup`; provider receives `(blockEntity, context)` and supplies a result only for block entities matching that type. |
| `registerEntityLookup(typeId, lookup, provider)` | `EntityApiLookup`; provider receives `(entity, context)`. |
| `registerItemLookup(itemId, lookup, provider)` | `ItemApiLookup`; provider receives `(stack, context)`. |

Standard capability names are `'item'` / `'fluid'` / `'energy'`. Block and block entity item/fluid capabilities connect to `ItemStorage.SIDED` / `FluidStorage.SIDED`; items connect to `ItemStorage.ITEM` / `FluidStorage.ITEM`. Entity capabilities use NekoJS Lookups. Standard block/entity context is nullable `Direction`; item context is `ContainerItemContext`, not a direction. Custom Lookups retain their `apiClass` / `contextClass`, which must not be replaced with incorrect types.

Item/fluid providers return native Fabric Transfer `Storage<ItemVariant>` / `Storage<FluidVariant>`; energy returns NekoJS `FabricEnergyHandler`. Fabric has no standard cross-mod energy API, and this interface does not automatically bridge other mods' energy capabilities. Return `null` to decline a query; exceptions and incompatible non-null results remain failures.

Each owner must hold its storage and manage its lifecycle, saving, and synchronization. Suppliers must not create empty storage on each query either. Standard queries use `Capabilities.getBlock/getEntity/getItem`; custom Lookups use their own `find(...)`. For factory transactions, units, `onChange`, and persistence limits, see [Global bindings](global-bindings_us#wiki-section-26).

---

<a id="wiki-section-18"></a>
## CommandEvents (server)

| Event | Event object | Description |
|---|---|---|
| `register` | `RegisterCommandsEvent` / `FMLServerStartingEvent` | Register commands (see platform differences below). |
| `command` | `CommandEvent` | Command execution. |

<a id="wiki-section-19"></a>
### NeoForge (26.x / 1.21.1)

`event` is Brigadier's `RegisterCommandsEvent`. Use `event.getDispatcher()` to register commands:

```javascript
CommandEvents.register(event => {
  const dispatcher = event.getDispatcher()
  dispatcher.register(
    dispatcher.literal('hello')                // Brigadier builder chain
      .executes(ctx => {
        ctx.getSource().sendSuccess(() => Text.of('你好！'), false)
        return 1
      })
  )
})
```

<a id="wiki-section-20"></a>
### Cleanroom 1.12.2 (no Brigadier)

1.12.2 has no modern `RegisterCommandsEvent`/Brigadier. `CommandEvents.register` receives `FMLServerStartingEvent`; scripts must supply an `ICommand` implementation, usually extending `CommandBase`, and call `event.registerServerCommand(cmd)`:

```javascript
// server_scripts/commands.js
const CommandBase = Java.type('net.minecraft.command.CommandBase')
const ICommand   = Java.type('net.minecraft.command.ICommand')

CommandEvents.register(event => {
  event.registerServerCommand(new Java.adapter(CommandBase, ICommand, {
    getName() { return 'hello' },
    getUsage(sender) { return '/hello' },
    execute(server, sender, args) {
      sender.sendMessage(new TextComponentString('你好！'))
    },
    getRequiredPermissionLevel() { return 0 }
  }))
})
```

Key points:

- **`register` fires once on each server startup**, in the same `FMLServerStartingEvent` phase as vanilla/mod commands.
- `event.registerServerCommand(cmd)` adds the command to the server command table; `cmd` must implement `net.minecraft.command.ICommand`.
- Scripts have no Brigadier `.literal(...).executes(...)` chain. Parse the `args` string array in `execute` yourself, optionally using helpers such as `CommandBase.parseDouble`/`getBlock`.
- `CommandEvents.command`, the command execution event, behaves the same on both platforms.

> NekoJS automatically registers `/nekojs` itself; scripts do not need to handle it.

---

<a id="wiki-section-21"></a>
## KeyBindEvents (CLIENT only, 26.x)

Script-friendly key bindings, corresponding to KubeJS §7.4-6. All events fire on the client thread and are visible only in `client_scripts`.

```javascript
// client_scripts/keybinds.js
// Register a binding (ids default to the nekojs namespace; keys look like
// 'key.keyboard.g' / 'key.mouse.left'; categories accept vanilla spellings
// such as 'key.categories.movement' or 'movement', or custom ids like 'mymod:my_keys')
KeyBindEvents.register('mymod:special', 'key.keyboard.g', 'mymod:my_keys')

// Pressed: edge-triggered on the tick where released becomes pressed
KeyBindEvents.pressed(event => {
    console.log(`${event.getId()} 按下`)
})

// Targeted listener: listen only to the specified binding
KeyBindEvents.pressed('mymod:special', event => { /* ... */ })

// Released
KeyBindEvents.released('mymod:special', event => { /* ... */ })

// Each tick while held (KubeJS semantics: not triggered while released)
KeyBindEvents.tick('mymod:special', event => { /* event.isDown() is always true */ })
```

Key points:

- `register()` returns a vanilla `KeyMapping` handle; poll `isDown()` / `consumeClick()` if needed. Repeated registration of the same id is idempotent, so CLIENT reload does not create duplicate entries.
- The event object provides `getId()` (the full id, also the dispatch key of all three buses), `getKeyMapping()`, and `isDown()`.
- **Registration timing**: `RegisterKeyMappingsEvent` fires before client scripts load. A script's `register()` first queues registration for that event, allowing user remapping to persist to options.txt; subsequent runtime registrations, such as new bindings after reload, append directly to `options.keyMappings`, as the event itself does.
- Binding and custom category translation keys are `key.<ns>.<path>` / `key.category.<ns>.<path>`. Supply translations through the lang event.

<a id="wiki-section-22"></a>
## ProbeEvents (SERVER only)

Type-generation hooks run during `/nekojs probe`, allowing scripts to refine declarations and register globals and snippets. The probe command runs on the **server thread**, so listeners for all four events must be registered at the **top level** of `server_scripts`, not inside callbacks.

| Event | Event object | Description |
|---|---|---|
| `modifyType` | `ProbeModifyTypeEventJS` | **Before** type rendering: `event.forClass(fqn)` obtains a `ClassEditor` for parameter-level edits (rename/hide/change types/add parameters/docs, etc.). Touched classes are rerendered by the IR renderer and overwrite the declaration cache; untouched classes follow the old path, avoiding regressions in TS output. |
| `assignType` | `ProbeAssignTypeEventJS` | Global type redirection: `event.assign(javaFqn, typeDesc)` rewrites a Java fully qualified name **everywhere** to a custom type, in both TS (rerendering touched classes) and Python. |
| `addGlobal` | `ProbeAddGlobalEventJS` | Register additional global declarations: `event.add(name, typeDesc)` → TS writes `@manual/globals.d.ts` (`declare const Name: T;`); Python adds to `nekojs/__init__.pyi`. |
| `snippets` | `ProbeSnippetEventJS` | Register VS Code snippets: `event.add(name, prefix, body[, description])` → merge into `nekojs/.vscode/nekojs.code-snippets` (replace probe-owned snippet names; preserve user snippets). |

Type inputs for all editing/declaration methods: a string containing `.` is a Java fully qualified name (SYMBOL); otherwise it is a raw type name (`"string"`/`"number"`/`"boolean"`/`"int"`, etc.). Construct complex types with `event.type(...)` / `event.array(...)` / `event.union(...)`.

```javascript
// server_scripts/probe_patch.js
ProbeEvents.modifyType(event => {
  const editor = event.forClass("net.minecraft.world.entity.player.Player")
  if (editor) {
    editor
      .renameMethod("getXxx", "getCustom")                       // Edit every overload with that name
      .changeParamType("addItem", 0, "net.minecraft.world.item.ItemStack")  // Parameter type by index
      .markOptional("eat", "level")                               // Make the parameter TS-optional
      .setMethodDoc("getCustom", "自定义文档")
      .setDoc("玩家扩展")
  }
})

ProbeEvents.assignType(event => {
  event.assign("net.minecraft.nbt.CompoundTag", "MyNbt")   // CompoundTag → MyNbt in all declarations
})

ProbeEvents.addGlobal(event => {
  event.add("MyFlag", "boolean")                           // → declare const MyFlag: boolean;
})

ProbeEvents.snippets(event => {
  event.add("nekojs-listener", "listen", "ServerEvents.started(e => { $1 })",
            "监听服务端启动")
})
```

> For the complete `ClassEditor` method list (`renameMethod`/`hideMethod`/`setMethodDoc`/`changeReturnType`/`changeParamType`/`renameParam`/`removeParam`/`markOptional`/`addParam`/`renameField`/`hideField`/`changeFieldType`/`setFieldDoc`/`hide`/`setDoc`/`changeSuper`, etc.; name-based operations affect every matching overload, and missing targets are silent no-ops) and probe.toml configuration, see [Probe type generation](probe-type-generation_us). Changes appear in declarations after the next `/nekojs probe`.

---

<a id="wiki-section-23"></a>
## GoalEvents (STARTUP only)

Append AI goals to existing entity types. See [Registering new content - GoalEvents](registering-new-content_us).

| Event | Event object | Description |
|---|---|---|
| `register` | `GoalRegisterEventJS` | Add goals to an entity type. |

---

<a id="wiki-section-24"></a>
## ClientEvents (CLIENT only)

> NeoForge provides this section's full event surface. Fabric 26.x currently provides `tickPre`, `tickPost`, the old name `tick`, `hud`, and `screenRender`; `hud` dispatches through the Fabric HUD layer API, and `screenRender` through `ScreenEvents.afterExtract`. Fabric's `generateAssets`, `lang`, player network state events, renderer registration, `hudRender`, and `worldRender` have not yet been ported. For Fabric key bindings, use [KeyBindEvents](event-reference_us).

| Event | Event object | Description |
|---|---|---|
| `tickPre` / `tickPost` (old name `tick` deprecated) | `ClientTickEvent` | Client tick. |
| **`playerTickPre` / `playerTickPost`** | `PlayerTickEvent.Pre/Post` | **Client player tick**, for the local player. Logical-side filtering is handled internally; server instances do not dispatch to client scripts. Server equivalents are `PlayerEvents.tickPre/tickPost`. |
| **`interactionKey`** | `InputEvent.InteractionKeyMappingTriggered` | **Client interaction keys**: attack/use item/pick block (`isAttack()`/`isUseItem()`/`isPickBlock()`/`getHand()`); **cancellable**, where `return true` intercepts vanilla input handling. |
| `loggedIn` / `loggedOut` / `cloned` | `ClientPlayerNetworkEvent` | Client player network state. |
| `commandRegistry` | `RegisterClientCommandsEvent` | Register client commands. |
| `registerKeyMappings` | `RegisterKeyMappingsEvent` | Register key bindings. |
| `registerMenuScreens` | `RegisterMenuScreensEvent` | Register menu Screens. |
| `registerRenderers` (old names `registerEntityRenderers` / `registerBlockEntityRenderers` deprecated) | `EntityRenderersEvent.RegisterRenderers` | Register renderers. |
| `registerParticleProviders` | `RegisterParticleProvidersEvent` | Register particle providers. |
| **`generateAssets` (NeoForge only)** | `DataGeneratorJS` | **Asset generation**: write asset JSON (models/textures/recipe categories, etc.) to `nekojs/assets`. |
| **`lang` (dispatched, NeoForge only)** | `LangGeneratorJS` | **Language entries**: collect translations by language code, such as `'zh_cn'`, which is the dispatch key. |
| **`hud`** | `PainterJS` | **HUD drawing** after GUI rendering on each frame (NeoForge/Fabric 26.x). |
| **`hudRender`** | Registration-based | **HUD renderer registration** (NeoForge only, layer × priority; see below). |
| **`worldRender`** | Registration-based | **World-space rendering** (NeoForge only, 3D lines/wireframes; see below). |
| **`screenRender`** | `ScreenRenderEventJS` | **Screen rendering** after a Screen renders (NeoForge/Fabric 26.x; draw with `event.painter`, plus `screenTitle`/mouse coordinates). |

```javascript
// client_scripts/asset_gen.js
ClientEvents.generateAssets(event => {
  event.json('mymod:models/item/cool_gem', { parent: 'minecraft:item/generated', textures: { layer0: 'mymod:item/cool_gem' } })
})

// client_scripts/hud2.js: registered HUD renderer (named id, ordered by layer × priority)
// layer: 'background' | 'normal' | 'foreground'; callback exceptions affect only that renderer (error panel)
ClientEvents.hudRender('boss_bar', { layer: 'normal', priority: 0 }, (ctx, gui) => {
  const hp = clientData.get('boss_hp')   // Use with the server ClientData.sync channel
  if (!hp) return
  ctx.text(`${hp.name} ${(hp.hp * 100).toFixed(0)}%`, 8, 8, 0xFFFFFFFF)
  ctx.fillRect(8, 20, 120, 6, 0xAA000000)
  ctx.fillRect(8, 20, Math.floor(120 * hp.hp), 6, 0xFF5DBB63)
})

// client_scripts/lines.js: world-space lines/wireframes (gracefully skipped on 26.2)
ClientEvents.worldRender('quest_path', { layer: 'normal', priority: 0 }, ctx => {
  ctx.box(10, 70, 10, 12, 72, 12, 0xFF00FF00)
  ctx.line(0, 64, 0, 10, 70, 10, 0xFFFFFF00, 2)
})
```

<a id="wiki-section-25"></a>
### Asset generation helper `Assets` (typed convenience writing)

Writing `event.json('<ns>/models/item/xxx.json', ...)` by hand in `generateAssets` can lead to directory mistakes. The global `Assets` Binding provides KubeJS-style typed convenience methods that can be called directly at the top level of **startup/server scripts**, as well as client scripts. All files are written to the `nekojs/assets` resource pack, the same directory as the `generateAssets` event, and take effect on the **next resource reload**. Path validation, capacity limits, and atomic writes are the same as `DataGeneratorJS`.

```javascript
// startup_scripts/assets.js
// 1) blockState: an object must contain exactly one of variants or multipart;
// the second argument can also be a model id string as single-variant shorthand
Assets.blockState('mymod:my_block', { variants: { '': { model: 'mymod:block/my_block' } } })
Assets.blockState('mymod:my_block', 'mymod:block/my_block')   // Equivalent shorthand
Assets.blockState('mymod:my_block', { multipart: [{ model: 'mymod:block/my_block' }] })

// 2) blockModel: write assets/<ns>/models/block/<path>.json
Assets.blockModel('mymod:my_block', {
  parent: 'minecraft:block/cube_all',
  textures: { all: 'my_block' }   // No ':' or '/' → expand to 'mymod:block/my_block'
})

// 3) itemModel: write assets/<ns>/models/item/<path>.json
Assets.itemModel('mymod:my_item', {
  parent: 'minecraft:item/generated',
  textures: { layer0: 'mymod:item/my_item' }   // Values with namespace/directory stay unchanged
})

// 4) texture: write a 16x16 magenta placeholder PNG to assets/<ns>/textures/...
Assets.texture('mymod:block/my_block')   // A '/' in the path → textures/block/my_block.png
Assets.texture('mymod:my_item', 'item')  // No '/' → second argument chooses 'block'|'item' (default 'block')
```

| Method | Output location | Description |
|---|---|---|
| `Assets.blockState(id, state)` | `assets/<ns>/blockstates/<path>.json` | `state` is an object/JSON string containing either `variants` (object) or `multipart` (array), or a model id string shorthand. |
| `Assets.blockModel(id, model)` | `assets/<ns>/models/block/<path>.json` | The id path may contain subdirectories, such as `mymod:custom/x` → `models/block/custom/x.json`. |
| `Assets.itemModel(id, model)` | `assets/<ns>/models/item/<path>.json` | As above; `textures` shorthand expands using the `<ns>:item/` prefix. |
| `Assets.texture(id)` / `Assets.texture(id, kind)` | `assets/<ns>/textures/<kind>/<path>.png` | 16x16 magenta placeholder; ids without `ns:` default to `minecraft:`. |

- **Texture shorthand** (only the `textures` maps of `blockModel`/`itemModel`): expand strings to `<ns>:block/<value>` (`<ns>:item/<value>` for itemModel) only if they contain **neither `:` nor `/`**. Other values with a namespace or directory, such as `'block/stone'`, and object texture references such as 26.x `force_translucent`, remain unchanged.
- **Placeholder textures**: `texture()` generates a solid-magenta 16x16 PNG, a valid PNG built manually using only the JDK. It only ensures model references have a texture; in game it appears magenta. Supply the real appearance through a resource pack override or another `texture()` write to the same filename.
- **Id rules**: match vanilla resource locations (lowercase letters/digits/`_`/`.`/`-`, with `/` subdirectories in paths). Invalid ids, such as uppercase letters, `..` segments, or multiple `:` characters, throw without writing to disk.

```javascript
// client_scripts/hud.js: HUD drawing
ClientEvents.hud(painter => {
  // Top-right status panel
  let x = painter.getWidth() - 130
  painter
    .color(0xAA000000).rect(x, 10, 120, 60)      // Translucent black background
    .outline(x, 10, 120, 60, 0xFFFFFFFF)          // White border
    .centerText('NekoJS', x + 60, 20)             // Centered text
    .text('Hello HUD', x + 10, 32, 0xFFFFFFAA)    // Left-aligned text
  // Transform and clipping
  painter.push().translate(10, 10).scissor(0, 0, 100, 50)
  painter.text('裁剪区域内的文本', 0, 0, 0xFFFFAA00)
  painter.resetScissor().pop()
  // Item icon
  painter.item(Item.of('minecraft:diamond'), x, 76)
})
```

**PainterJS methods** (coordinates are GUI-scaled pixels; colors are ARGB ints, such as `0x80FF0000`):

| Method | Description |
|---|---|
| `getWidth()` / `getHeight()` | Scaled screen dimensions. |
| `getPartialTick()` | Current render frame interpolation (0–1), for smooth animation. |
| `getWorldTime()` | Client world tick (periodic animation: `worldTime % period`). |
| `color(argb)` / `resetColor()` | Set/reset the default color for methods that omit a color. |
| `rect(x, y, w, h[, color])` | Filled rectangle. |
| `outline(x, y, w, h[, color])` | 1px border. |
| `gradient(x, y, w, h, colorTop, colorBottom)` | Vertical gradient rectangle. |
| `gradientH(x, y, w, h, colorLeft, colorRight)` | Horizontal gradient rectangle (per-column interpolation; keep width reasonably small). |
| `text(str, x, y[, color])` | Left-aligned text. |
| `centerText(str, x, y[, color])` | Horizontally centered text. |
| `texture(texId, x, y, w, h)` | Crop `w×h` from the upper-left corner of a default 256×256 source texture; does not scale the entire texture. |
| `texture(texId, x, y, u, v, w, h)` | As above; `u/v` are source texture pixel offsets, before width and height arguments. |
| `texture(texId, x, y, u, v, w, h, textureWidth, textureHeight)` | Explicitly specify source texture dimensions, which must be positive. |
| `textureFull(texId, x, y, w, h)` | Scale the entire texture to the destination `w×h` rectangle. |
| `textWidth(str)` / `wrapText(str, maxWidth)` / `lineHeight()` | Text width, wrapping at a GUI-pixel width, and line height. |
| `item(ItemStack, x, y)` | Item icon (`Item.of(...)`). |
| `push()` / `pop()` / `translate(x, y)` | Transform stack. |
| `scissor(x, y, w, h)` / `resetScissor()` | Clipping region. |

The `screenRender` event object `ScreenRenderEventJS` provides `painter` (the same drawing API), `screen` (Screen object), `screenTitle` (such as `'Chest'`), and `mouseX`/`mouseY`.

HUD/Screen painters and their native graphics are usable only during the current drawing callback. Do not save them in `global`, timers, or asynchronous closures for another frame. Persistent state should hold only drawing data; redraw with the current frame's painter. Restore paired transform and clipping state before the callback ends. Fabric's `hud` / `screenRender` do not mean identified `hudRender` / `worldRender` registration has been ported.

```javascript
// client_scripts/hud_anim.js: gradient and animation example
ClientEvents.hud(painter => {
  // Breathing glow: alpha follows a sine wave over world time
  let alpha = Math.floor(80 + 60 * Math.sin(painter.getWorldTime() / 20))
  painter.rect(10, 10, 200, 20, (alpha << 24) | 0x00FFAA)
  // Horizontal gradient health bar
  painter.gradientH(10, 40, 200, 12, 0xFFFF5555, 0xFF55FF55)
})

// client_scripts/screen_overlay.js: add a hint above the chest Screen
ClientEvents.screenRender(event => {
  if (event.screenTitle === 'Chest') {
    event.painter.centerText('自定义箱子提示', event.painter.getWidth() / 2, 4, 0xFFFFFFAA)
  }
})
```

> Note: `hud` fires every frame, so keep logic lightweight. `color()` is **stateful**; drawing methods without an explicit color use the current color. Call `resetColor()` between panels when needed.

```javascript
ClientEvents.lang('zh_cn', event => {
  event.add('item.mymod.cool_gem', '炫酷宝石')
})
```

---

<a id="wiki-section-26"></a>
## RecipeViewerEvents (CLIENT only, JEI)

Interact with the JEI recipe viewer. Events fire when the JEI runtime rebuilds or resources reload.

| Event | Dispatch? | Event object | Description |
|---|---|---|---|
| `addEntries` | By `'item'` / `'fluid'` | `RecipeViewerEntryListJS` | Add entries to the viewer (`event.add('minecraft:stone')`). |
| `removeEntries` | By `'item'` / `'fluid'` | `RecipeViewerEntryListJS` | Remove entries from the viewer, not entirely: they remain viewable through other means. |
| `removeRecipes` | — | `RecipeViewerRecipeListJS` | Hide recipes by id (`event.remove('minecraft:xxx')`). |
| `removeCategories` | — | `RecipeViewerCategoryListJS` | Hide an entire viewer category by category id. |
| `addInformation` | — | `RecipeViewerInformationJS` | Add tooltip information to entries (`event.add('minecraft:stone', '说明')`). |

```javascript
// client_scripts/recipe_viewer.js
RecipeViewerEvents.addEntries('item', event => {
  event.add('mymod:cool_gem')
})

RecipeViewerEvents.addInformation(event => {
  event.add('mymod:cool_gem', '很酷的宝石！')
})

RecipeViewerEvents.removeCategories(event => {
  event.remove('jei:information')   // Hide the information category
})
```

---

<a id="wiki-section-27"></a>
## Custom events

If the events above do not meet your needs, there are two complementary first-class APIs:

1. **`ScriptEvents`** (STARTUP, consistent on NeoForge and Fabric): declare a custom Event Group, let server/client scripts listen like built-in events, and trigger it with `post(payload)`. Dynamically declared Event Groups and events enter probe type generation. See [Event extensions - ScriptEvents](event-extensions_us).
2. **`NativeEvents`** (STARTUP, NeoForge surface): listen directly to raw Forge/NeoForge event classes without Event Group abstraction, suitable for one-off or local listeners. Returning `true` cancels cancellable events; priority and receiveCancelled are configurable. Listener changes require a game restart. See [Global bindings - NativeEvents](global-bindings_us).

---

<a id="wiki-section-28"></a>
## Event contracts

NekoJS builds event contracts by reflecting the `EventGroup` instances registered during the current startup, not from Wiki tables. The table below describes only the stably maintained event surface in the current repository. NeoForge has the fuller set, and Fabric guarantees only its actually registered subset. Cleanroom 1.12.2 is outside the current version graph; compatibility belongs to its independent legacy branch, and this page makes no runtime commitment for it.

<a id="wiki-section-29"></a>
### Contract events (as registered on current platforms)

| Group | Event | tier | Dispatch | Cancellable | Stable payload properties |
|---|---|---|---|---|---|
| `ScriptEvents` | `server`, `client` | STARTUP | Ordinary | — | `register(...)`, `targetType` |
| `ServerEvents` | `tickPre`, `tickPost`, `aboutToStart`, `starting`, `started`, `stopping`, `stopped` | SERVER | Ordinary | — | `server` (native object) |
| `LevelEvents` | `loaded`, `unloaded`, `saved`, `tickPre`, `tickPost` | SERVER | Ordinary | — | — |
| `PlayerEvents` | `loggedIn`, `loggedOut`, `cloned`, `respawned`, `changedDimension`, `advancement`, `tickPre`, `tickPost` | SERVER | Ordinary | — | — |
| `PlayerEvents` | `chat` | SERVER | Ordinary | ✅ Return `true` to cancel | `message`, `username` (strings) |
| `PlayerEvents` | `entityInteract` | SERVER | Ordinary | ✅ Return `true` to cancel | — |
| `PlayerEvents` | `containerOpened`, `containerClosed` (old names `inventoryOpened`/`inventoryClosed` deprecated) | SERVER | Ordinary | — | — |
| `PlayerEvents` | `crafted`, `smelted`, `destroyed`, `inventoryChanged` | SERVER | By item id (string) | — | — |
| `CommandEvents` | `register` | SERVER | Ordinary | — | — |
| `CommandEvents` | `command` | SERVER | Ordinary | ✅ Return `true` to cancel | — |
| `RegistryEvents` | `register` | STARTUP | Ordinary | — | `event.<registry>(id, b => { ... })`; available convenience methods vary by platform, and 26.x enchantments are not part of the runtime registration API. |
| `BlockEvents` | `broken`, `placed`, `entityPlaced`, `entityMultiPlaced`, `neighborNotify`, `fluidPlaced`, `farmlandTrample`, `portalSpawn`, `rightClicked`, `leftClicked` | SERVER | By block id (string) | ✅ All cancellable | `player`, `state`, `pos`, `itemStack`, etc. (native objects, depending on the event) |
| `EntityEvents` | `joinLevel`, `death`, `damagePre`, `damagePost`, `drops`, `finalizeSpawn` | SERVER | By entity id (string) | `joinLevel`/`damagePre`/`finalizeSpawn` depend on the event | `entity`, `source`, `amount`, etc. (native objects) |
| `ItemEvents` | `rightClicked`, `canPickUp`, `pickedUp`, `dropped`, `entityInteracted`, `foodEaten` | SERVER | By item id (string) | `rightClicked`/`canPickUp`/`dropped`/`entityInteracted` ✅; `pickedUp`/`foodEaten` ❌ | `player`, `itemStack`, `target`, etc. (native objects) |
| `ClientEvents` | `tickPre`, `tickPost` (old name `tick` deprecated) | CLIENT | Ordinary | — | — |

> This table lists only the common contract. `EntityEvents.useItemStarted/useItemStopped/useItemFinished/useItemTick`, `ClientEvents.generateAssets/lang`, and some 26.x registration convenience methods remain usable on NeoForge but are outside Fabric's common contract.

- **Cancellation**: a listener returning `true` requests cancellation; this is not a method on the event object. Only platforms actually registering an event provide its cancellation semantics. An unsupported Fabric event does not become available by appearing in the NeoForge table.
- **Id dispatch**: registration keys are string ids, such as `PlayerEvents.crafted('minecraft:stick', ...)`, `BlockEvents.broken('minecraft:stone', ...)`, and `EntityEvents.death('minecraft:zombie', ...)`.
- **Payload properties**: names are determined by event objects registered on the current platform. String fields in the common contract, such as `PlayerEvents.chat`'s `message` / `username`, stay stable; native event object types and members can still differ by platform.
- **RegistryEvents**: one `register(event => ...)` entry, posted once per startup. Convenience methods such as `event.item(id, cb)` come from the type list registered at `registry_types`. Builder members, such as public field assignment `b.maxStackSize = 16`, vary by version and are outside the contract. The payload is a dynamic-member object; preflight does not deeply validate convenience method spelling. At runtime, unknown methods/members produce errors listing known names.
- **Unified interaction event names**: the contract uses canonical NeoForge names. Cleanroom 1.12.2 Bindings have adopted them; early names (`blockBreak`/`place`/`joinWorld`/`hurt`/`damage`/`checkSpawn`/`toss`) have **changed to canonical names** (`broken`/`placed`/`joinLevel`/`damagePre`/`damagePost`/`finalizeSpawn`/`dropped`). Update old scripts' event names.

> Events outside the common contract may still exist on NeoForge, such as `ServerEvents.datapackSync`, `lootTables`, `tags`, `generateData`, `BlockEvents.randomTick`, `blockEntityTick`, `modification`, and ClientEvents rendering events. Check platform markers before using them. Cleanroom 1.12.2's old event names, OreDictionary tag behavior, and tooltip side belong to an independent legacy branch and are not verified by the current repository.

<a id="wiki-section-30"></a>
### Event callback member checking (preflight)

During script loading, static member checking compares event-object member accesses against the union of contract payload fields and reflected platform event members. Unknown members produce an error with spelling suggestions (`Did you mean ...`). Contract fields such as `server`/`message`/`username` are allowed even if platform reflection lacks them; the contract is authoritative and covers implementation gaps.

Supported forms:
- Method calls and property access: `e.getServer()` / `e.server`.
- Quoted brackets: `e['getServer']()` / `e["server"]`.
- **const string dynamic keys**: `const key = 'getServer'; e[key]()` (const strings are statically evaluable and checked normally).
- **Chained return values**: `e.getPlayer().getServer()`, `e.player.getServer()`, `e.directField.getServer()`. Infer each step from the previous return type; overloads use a conservative union filtered by argument count, allowing the access if any candidate supports it.
- Local aliases: `const x = e; x.getServer()`.
- Optional chaining: `e?.getServer()` / `e?.[key]()`.

Known limits: checking uses the simplified ValParser. If a file contains syntax it cannot parse, checking is skipped for that file with a warning, without silently ignoring it or hanging. **Truly runtime-dynamic keys**, such as `e[keyFromNetwork]()`, cannot be statically proven, so they are skipped without false positives. When generic/native types cannot be determined, they are marked unknown without deep checks. These forms remain usable but have no static guarantee; runtime errors are still reported normally.

<a id="wiki-section-31"></a>
## Next steps

- [Global bindings](global-bindings_us): `Item`/`Ingredient`/`Fluid` reference.
- [Recipe system](recipe-system_us): `ServerEvents.recipes` in detail.
- [Registering new content](registering-new-content_us): `RegistryEvents`/`GoalEvents` in detail.

<!-- wiki-nav -->

---

[Previous: Python feature support](python-feature-support_us) · [Contents](Home) · [Next: Commands](commands_us)
