<!-- wiki-page: event-reference; locale: us -->

> **English** · [中文](event-reference_cn)

<a id="wiki-section-1"></a>
# Event reference

This page lists every NekoJS event group: when each event fires, which script type it belongs in, whether you can cancel it, and which platforms support it. If this is your first event, read [Script basics](script-basics_us) first.

<a id="wiki-section-2"></a>
## Listening to an event

Call a method on an event group at the **top level** of your script and pass it a callback:

```javascript
// server_scripts/welcome.js
PlayerEvents.loggedIn(event => {
  console.info('A player joined the server')
})
```

Then run the matching reload command (`/nekojs reload` for server scripts), trigger the event once, and check the log or `/nekojs error` to confirm your callback ran.

Listeners must be registered at the top level. Registering an event inside another event's callback is an error.

The `event` argument is the event object. Read data with property syntax, such as `event.player` or `event.entity`, or call the matching method, such as `event.getPlayer()`. Being able to read a property does not mean you can assign it. To see which members an event has, run `/nekojs probe` and use code completion in your editor.

<a id="wiki-section-3"></a>
### Listening to one item, block or entity

Many events can be filtered by id; the tables below mark them as "by id". Put the id before the callback and the event only fires for that object:

```javascript
// Fires only when diamond ore is broken
BlockEvents.broken('minecraft:diamond_ore', event => {
  console.info('Diamond ore was broken')
})

// Without an id, every block fires it
BlockEvents.broken(event => {
  console.info('A block was broken')
})
```

Depending on the event, the id is an item id, a block id, an entity type id, or another string such as a network channel or key binding id.

<a id="wiki-section-4"></a>
### Priority

When several callbacks listen to the same event, you can put a priority first: `HIGHEST`, `HIGH`, `NORMAL` (default), `LOW` or `LOWEST`. Case does not matter.

```javascript
PlayerEvents.loggedIn('HIGH', event => { /* runs earlier */ })

// Events filtered by id need all three: priority, id, callback
BlockEvents.broken('HIGH', 'minecraft:stone', event => { /* ... */ })
```

Watch out: with only two arguments, an event filtered by id treats the first one as the id. `BlockEvents.broken('HIGH', event => {})` listens for a block with the id `HIGH`; it does not set a priority.

<a id="wiki-section-5"></a>
### Cancelling an event

To cancel an event that supports it, `return true` from the callback. For example, to stop players from breaking stone:

```javascript
BlockEvents.broken('minecraft:stone', event => {
  return true
})
```

This only works for events marked "cancellable". On other events, returning `true` does nothing and reports no error. Which events can be cancelled differs a little between platforms; the tables below are the reference.

<a id="wiki-section-6"></a>
## Event groups by platform

Every event group is available on NeoForge. Fabric 26.x supports most common events, but some groups only have part of their events, and a few have not been ported.

| Event group | Script type | NeoForge | Fabric 26.x |
|---|---|---|---|
| `ServerEvents` | Server | All | No `lootTables`, `tags` or `generateData`; trade events can be listened to but have no effect |
| `PlayerEvents` | Server | All | All |
| `EntityEvents` | Server | All | No `useItem*` events |
| `BlockEvents` | Server | All (`modification` on 26.x only) | No `entityMultiPlaced` or `toolModification` |
| `ItemEvents` | Server; `tooltip` is client | All | All |
| `LevelEvents` | Server | All | No old `tick` name |
| `NetworkEvents` | Server / client | Yes | Yes |
| `RegistryEvents`, `GoalEvents` | Startup | Yes | Yes |
| `CapabilityEvents` | Startup | Yes | Yes, with different methods |
| `CommandEvents` | Server | `register`, `command` | `register` only |
| `KeyBindEvents` | Client | 26.x only | Yes |
| `ClientEvents` | Client | All | Only `tickPre`, `tickPost`, `tick`, `hud`, `screenRender` |
| `RecipeViewerEvents` | Client | Requires JEI | No |
| `ProbeEvents` | Server | Yes | No |
| `ScriptEvents` | Startup | Yes | Yes |

Fabric uses its own event objects, so member names can differ from NeoForge. The sections below point out where this matters. For all platform differences, see [Platforms and compatibility](platform-compatibility_us).

<a id="wiki-server-events"></a>
<a id="wiki-section-7"></a>
## ServerEvents

Server lifecycle, ticks and data loading. Use them in `server_scripts`.

| Event | When it fires | Notes |
|---|---|---|
| `recipes` | While recipes load | **Change recipes**; the one you will use most. See [Recipe system](recipe-system_us) |
| `afterRecipes` | After all recipes have loaded | For queries only. Recipe changes made here do not apply; use `recipes` |
| `tickPre` / `tickPost` | Before / after each server tick | 20 times a second; keep the logic light |
| `aboutToStart` / `starting` | While the server starts | |
| `started` | The server has finished starting | Registries are ready; `Item.of(...)` is safe to use |
| `stopping` / `stopped` | The server is stopping / has stopped | |
| `datapackSync` | When data packs are synced to players | |
| `tagsUpdated` | After tags reload | |
| `lootTableLoad` | As each loot table loads | Cancellable on NeoForge |
| `lootTables` | On data reload | **Read and write loot tables by id**; NeoForge only, see below |
| `tags` | While tags load | **Change tags**, filtered by registry id; NeoForge only, see below |
| `generateData` | On data reload | **Generate data pack files**, filtered by stage (currently only `'after_mods'`); NeoForge only |
| `tradeDeclaration` / `tradeReload` | On startup and reload | Villager trades; only take effect on NeoForge |

```javascript
// server_scripts/lifecycle.js
ServerEvents.started(event => {
  console.info('Server started')
})
```

<a id="wiki-section-8"></a>
### Loot tables: ServerEvents.lootTables

NeoForge only. Fires on every data reload (entering a world, `/reload`), and changes apply in that same reload.

| Method | Description |
|---|---|
| `getJson(id)` | The loot table's current JSON, or `null` if it does not exist |
| `setJson(id, json)` | Replace the whole table. `json` can be a JS object or a JSON string |
| `create(id, json)` | Create a table (the id does not need to exist yet) |
| `remove(id)` | Delete a table; it reads as empty afterwards |
| `getIds()` | Every loot table id, including ones your scripts created |
| `modify(id, table => {})` | Edit a table in a callback |
| `modifyBlockLoot(filter, table => {})` | Edit block drops in bulk. `filter` is a block id, `'#tag'`, `'*'` or a function returning true/false |
| `modifyEntityLoot(filter, table => {})` | Edit entity drops in bulk. `filter` is an entity id, `'*'` or a function |

The `table` passed to the `modify` callbacks has these methods:

- Table: `getType()` / `setType(type)`, `getPools()`, `addPool(pool => {})` or `addPool(full pool JSON)`.
- Pool: `rolls(count)` or `rolls([min, max])`, `addEntry(...)`, `getEntries()`, `when(condition JSON)`.
- Entry: `item(id)`, `weight(weight)`, `when(condition JSON)`. `addEntry` also accepts a plain item id string.

```javascript
// server_scripts/loot.js
ServerEvents.lootTables(event => {
  // Edit the JSON directly: add a diamond to dungeon chests
  let json = event.getJson('minecraft:chests/simple_dungeon')
  json.pools.push({
    rolls: 1,
    entries: [{ type: 'minecraft:item', name: 'minecraft:diamond' }]
  })
  event.setJson('minecraft:chests/simple_dungeon', json)

  // With a callback: fishing has a 50% chance of an extra lucky coin
  event.modify('minecraft:gameplay/fishing', table => {
    table.addPool(pool => {
      pool.rolls(1).addEntry(entry => entry
        .item('mymod:lucky_coin')
        .weight(5)
        .when({ condition: 'minecraft:random_chance', chance: 0.5 })
      )
    })
  })

  // Every log also drops an apple
  event.modifyBlockLoot('#minecraft:logs', table => {
    table.addPool(pool => pool.rolls(1).addEntry('minecraft:apple'))
  })

  // Every entity has a 5% chance to drop a feather
  event.modifyEntityLoot('*', table => {
    table.addPool(pool => pool.rolls(1).addEntry(
      entry => entry.item('minecraft:feather').when({ condition: 'minecraft:random_chance', chance: 0.05 })
    ))
  })

  // Create a new table
  event.create('mymod:chests/treasure', {
    type: 'minecraft:chest',
    pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'minecraft:emerald' }] }]
  })

  // Delete a table
  event.remove('minecraft:chests/jungle_temple')
})
```

Bulk edits with `'*'` or a function only reach blocks and entities that already have a loot table. Exact ids and `#tag` filters do not have this limit.

<a id="wiki-section-9"></a>
### Global loot modifiers

A global loot modifier applies to **every drop** instead of replacing a whole table. There is no event for it; write data pack files with `generateData` instead (NeoForge only):

```javascript
// server_scripts/modifiers.js
ServerEvents.generateData('after_mods', event => {
  // 1) Add the modifier to the enabled list. Use replace: false so other mods' lists survive
  event.json('neoforge/loot_modifiers/global_loot_modifiers.json', {
    replace: false,
    entries: ['mymod:add_fishing_loot']
  })
  // 2) The modifier itself: every drop also rolls the fishing loot table
  event.json('mymod/loot_modifiers/add_fishing_loot.json', {
    type: 'neoforge:add_loot_table',
    conditions: [],
    loot_table: 'minecraft:gameplay/fishing'
  })
})
```

Files go under `nekojs/data`, and paths are relative to it. `neoforge:add_loot_table` is a modifier type built into NeoForge; more complex modifiers have to be written in Java.

<a id="wiki-section-10"></a>
### Tags: ServerEvents.tags

NeoForge only. Filtered by registry id, such as `'minecraft:item'` or `'minecraft:block'`.

| Method | Description |
|---|---|
| `add(tag, id)` | Add an entry to a tag |
| `remove(tag, id)` | Remove an entry from a tag |
| `removeAll(tag)` | Empty a tag |
| `replaceAll(tag, ...ids)` | Replace everything in a tag with the given entries |
| `getEntries(tag)` | Read a tag's current entries |

```javascript
// server_scripts/tags.js
ServerEvents.tags('minecraft:item', event => {
  event.add('minecraft:logs', 'mymod:custom_log')
  event.remove('minecraft:logs', 'minecraft:birch_log')
  event.replaceAll('minecraft:planks', 'minecraft:oak_planks')
})

ServerEvents.tags('minecraft:block', event => {
  // Block tags
})
```

On 26.x, tags declared with `.tag(...)` when registering new items are already visible in this event.

<a id="wiki-section-11"></a>
## PlayerEvents

Player events. Use them in `server_scripts`.

| Event | By id | Cancellable | Notes |
|---|---|---|---|
| `loggedIn` / `loggedOut` | — | — | A player joins / leaves the server |
| `chat` | — | Cancellable on NeoForge | A player sends a chat message. `event.message` and `event.username` are strings |
| `tickPre` / `tickPost` | — | — | Once per player per tick |
| `respawned` | — | — | A player respawns |
| `cloned` | — | — | Player data is copied after death or when returning from the End |
| `changedDimension` | — | — | A player changes dimension |
| `advancement` | — | — | A player earns an advancement |
| `containerOpened` / `containerClosed` | — | — | A container screen opens / closes |
| `entityInteract` | — | Cancellable | A player right-clicks an entity |
| `crafted` | Item id | — | A player crafts an item |
| `smelted` | Item id | — | A player takes a smelted item out of a furnace |
| `destroyed` | Item id | — | An item breaks from running out of durability |
| `inventoryChanged` | Item id | — | A slot in the inventory changes. `event.item` is the item stack, `event.slot` the slot number |

```javascript
// server_scripts/players.js
PlayerEvents.loggedIn(event => {
  // The player is event.entity on NeoForge and event.player on Fabric
  const player = event.entity ?? event.player
  player.displayClientMessage(Text.of('Welcome, ' + player.getName().getString() + '!'), false)
})

PlayerEvents.crafted('minecraft:stick', event => {
  console.info('Someone crafted a stick')
})

PlayerEvents.inventoryChanged('minecraft:diamond', event => {
  console.info('A diamond went into slot ' + event.slot)
})
```

The old names `inventoryOpened` and `inventoryClosed` still work; use `containerOpened` and `containerClosed` in new scripts.

<a id="wiki-section-12"></a>
## EntityEvents

Entity events. Use them in `server_scripts`. Most can be filtered by entity type id, such as `'minecraft:zombie'`.

| Event | By id | Cancellable | Notes |
|---|---|---|---|
| `damagePre` | Entity id | Cancellable | An entity is about to take damage; see below |
| `damagePost` | Entity id | — | After an entity takes damage |
| `death` | Entity id | Cancellable on NeoForge | An entity dies |
| `drops` | Entity id | Cancellable | A dying entity drops its items. Cancel to drop nothing |
| `finalizeSpawn` | Entity id | Cancellable on NeoForge | A mob spawns naturally or from a spawner |
| `joinLevel` / `leaveLevel` | Entity id | `joinLevel` is cancellable on NeoForge | An entity enters / leaves a world |
| `tickPre` / `tickPost` | Entity id | `tickPre` is cancellable on NeoForge | Once per entity per tick. There are many entities; keep it light |
| `useItemStarted` / `useItemTick` / `useItemStopped` / `useItemFinished` | Item id | The first three are cancellable | Starts using / each tick while using / released early / finished using an item. NeoForge only |

```javascript
// server_scripts/entities.js
EntityEvents.death('minecraft:zombie', event => {
  console.info('A zombie died at ' + event.entity.blockPosition())
})

// Halve damage taken by players (NeoForge)
EntityEvents.damagePre('minecraft:player', event => {
  event.setNewDamage(event.getNewDamage() / 2)
})
```

Cancelling `damagePre` works differently on each platform:

- **NeoForge**: the damage becomes 0, and `damagePost` still fires with 0 damage. You can also change the amount with `event.setNewDamage(amount)`.
- **Fabric**: the damage does not happen at all. Fabric cannot change the amount; it can only cancel.

<a id="wiki-section-13"></a>
## BlockEvents

Block events. Use them in `server_scripts`. Most are filtered by block id.

| Event | Cancellable | Notes |
|---|---|---|
| `broken` | Cancellable | A player breaks a block |
| `placed` / `entityPlaced` | Cancellable | A block is placed (both names are the same event) |
| `entityMultiPlaced` | Cancellable | Several blocks are placed at once, such as a bed or door. NeoForge only |
| `rightClicked` / `leftClicked` | Cancellable | A player right-clicks / left-clicks a block |
| `neighborNotify` | Cancellable | A block tells its neighbors to update |
| `fluidPlaced` | Cancellable | A fluid creates a block, such as lava and water making cobblestone |
| `farmlandTrample` | Cancellable | Farmland is trampled |
| `portalSpawn` | Cancellable | A Nether portal forms |
| `toolModification` | Cancellable | A tool changes a block, such as stripping a log with an axe or tilling with a hoe. NeoForge only |
| `randomTick` | — | A block's random tick. Only blocks that random-tick fire it, such as wheat or grass; stone never does |
| `blockEntityTick` | — | Once per block entity per tick. Filtered by **block entity type** id, such as `'minecraft:furnace'` |
| `modification` | — | **Change block properties**; see below. Not filtered by id |

```javascript
// server_scripts/blocks.js
BlockEvents.broken('minecraft:diamond_ore', event => {
  console.info(event.player.getName().getString() + ' mined diamond ore')
})

// Stop farmland from being trampled
BlockEvents.farmlandTrample(event => true)

BlockEvents.randomTick('minecraft:wheat', event => {
  // event.level, event.pos, event.state, event.random
})

BlockEvents.blockEntityTick('minecraft:furnace', event => {
  const furnace = event.blockEntity
})
```

<a id="wiki-section-14"></a>
### Changing block properties: BlockEvents.modification

Changes properties such as hardness and light level of existing blocks. Supported on NeoForge 26.x and Fabric 26.x; not on 1.21.1.

| Property | Type | Notes |
|---|---|---|
| `hardness` | Number, 0 or more | How long the block takes to mine. Stone is 1.5, obsidian 50 |
| `resistance` | Number, 0 or more | Explosion resistance. Use `3600000` for bedrock-level |
| `lightLevel` | Integer from 0 to 15 | Light emitted. Glowstone is 15 |
| `requiresTool` | true / false | Whether the correct tool is needed for the block to drop |
| `friction` | Number from 0 to 1 | Slipperiness. Ice is 0.98, normal blocks 0.6 |
| `jumpFactor` | Number | Jump height multiplier. Vanilla blocks are between 0.5 and 1 |

```javascript
// server_scripts/block_mods.js
BlockEvents.modification(event => {
  event.modify('minecraft:stone', block => {
    block.hardness = 2
    block.lightLevel = 15     // Glowing stone
    block.requiresTool = false
    block.friction = 0.8      // A little slippery
  })

  // Read the current value, then change it
  event.modify('minecraft:obsidian', block => {
    block.hardness = block.hardness / 2
  })
})
```

Changes apply when the server starts, and every `/nekojs reload` reapplies them from your current scripts. So if you delete a `modify` call and reload, that block goes back to normal. If the reload fails, the previous changes stay in place.

Changes are sent to connected clients and to players who join later. Lighting in chunks that are already loaded may not update right away; reloading the chunks fixes it.

<a id="wiki-item-events"></a>
<a id="wiki-section-15"></a>
## ItemEvents

Item events. All except `tooltip` go in `server_scripts`. Most are filtered by item id.

| Event | Script | Cancellable | Notes |
|---|---|---|---|
| `rightClicked` | Server | Cancellable | A player right-clicks with an item |
| `entityInteracted` | Server | Cancellable | A player right-clicks an entity while holding an item |
| `canPickUp` | Server | See below | A player is about to pick up an item |
| `pickedUp` | Server | — | After a player picks up an item |
| `dropped` | Server | Cancellable on NeoForge | A player drops an item |
| `foodEaten` | Server | — | Food is finished, or an item such as a shield or bow finishes being used |
| `modification` | Server | — | **Change item properties**; see below. Not filtered by id |
| `tooltip` | Client | — | **Change item tooltips**; see below |

```javascript
// server_scripts/items.js
ItemEvents.rightClicked('minecraft:stick', event => {
  event.player.displayClientMessage(Text.of('You wave the stick'), false)
})

ItemEvents.foodEaten('minecraft:golden_apple', event => {
  console.info('Someone ate a golden apple')
})
```

Blocking a pickup works differently on each platform:

- **NeoForge**: call `event.setCanPickup(TriState.FALSE)`. Returning `true` has no effect here.
- **Fabric**: `return true` from the callback.

The old name `pickedUpPre` is the same as `canPickUp`; use `canPickUp` in new scripts.

<a id="wiki-section-16"></a>
### Changing item properties: ItemEvents.modification

Changes properties such as stack size, rarity and food values of existing items. Supported on NeoForge 1.21.1, NeoForge 26.x and Fabric 26.x. It applies the same way as `BlockEvents.modification`: on startup, again on every `/nekojs reload`, and removed changes are undone after the next reload.

| Property | Notes |
|---|---|
| `maxStackSize` | Maximum stack size, 1 to 99 |
| `maxDamage` | Durability. When it is above 0, `maxStackSize` cannot be above 1 |
| `rarity` | Rarity: `'common'`, `'uncommon'`, `'rare'` or `'epic'` |
| `fireResistant` | Whether fire and lava cannot destroy it |
| `food` | Food values; set to `null` to make it inedible |
| `attackDamage` / `attackSpeed` | Main-hand attack damage / attack speed bonus |
| `tool` | Lets the item mine every block as a tool; set to `null` to remove |

```javascript
// server_scripts/item_mods.js
ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 16
    item.rarity = 'epic'
    item.fireResistant = true
  })

  // Make sticks edible. Every field is optional: nutrition defaults to 1, saturation to 0.1
  event.modify('minecraft:stick', item => {
    item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }
  })

  // An iron ingot that fights like a sword: damage = 2 + 6, speed = 4 - 2.4 = 1.6
  event.modify('minecraft:iron_ingot', item => {
    item.attackDamage = 6
    item.attackSpeed = -2.4
  })

  // A blaze rod as a universal tool: mines every block at speed 6, and every block drops
  event.modify('minecraft:blaze_rod', item => {
    item.tool = { miningSpeed: 6 }
  })
})
```

A few details:

- `saturation` is a multiplier, as in KubeJS. The saturation restored is `nutrition` × `saturation` × 2.
- An item that could not be eaten before gets an eating action when you set `food`, lasting 1.6 seconds by default. If you give `eatSeconds`, that duration is used.
- `attackDamage` and `attackSpeed` are added to the player's base values: base attack damage is 2 and base attack speed is 4. You can set just one of them; the item's other attribute bonuses are kept.
- In `tool`, `miningSpeed` is required, `damagePerBlock` defaults to 1 and `canDestroyBlocksInCreative` defaults to true. On 26.x you cannot set a mining level on the item.

<a id="wiki-section-17"></a>
### Item tooltips: ItemEvents.tooltip

Goes in `client_scripts` and is filtered by item id. Apply changes with `/nekojs reload client`.

```javascript
// client_scripts/tooltips.js
ItemEvents.tooltip('minecraft:diamond', event => {
  // NeoForge uses event.toolTip, Fabric uses event.lines
  const lines = event.toolTip ?? event.lines
  lines.add(Text.of('Shiny!').gold())
})
```

<a id="wiki-section-18"></a>
## LevelEvents

World (dimension) events. Use them in `server_scripts`.

| Event | Cancellable | Notes |
|---|---|---|
| `loaded` / `unloaded` / `saved` | — | A world loads / unloads / saves |
| `tickPre` / `tickPost` | — | Once per world per tick |
| `explosionStart` | Cancellable | An explosion is about to happen. Cancel it to prevent the explosion |
| `explosionDetonate` | — | The explosion's area is known; blocks and entities are about to be affected |

The old names `beforeExplosion`, `afterExplosion` and `tick` still work; use `explosionStart`, `explosionDetonate` and `tickPost` in new scripts. Fabric has no `tick`.

<a id="wiki-section-19"></a>
## NetworkEvents

Lets server scripts and client scripts send each other messages. Filtered by channel name, which is any string you choose. Send messages with [Network](global-bindings_us#wiki-network).

| Event | Script | Notes |
|---|---|---|
| `server` | Server | A message arrived from a client. `event.channel`, `event.data`, `event.player` |
| `client` | Client | A message arrived from the server. `event.channel`, `event.data` |

In this example the client sends a request and the server replies.

```javascript
// client_scripts/menu.js
NetworkEvents.client('my_channel', event => {
  console.info('Server replied:', event.data)
})

// Send from a client event such as a key press:
// Network.sendToServer('my_channel', { action: 'open_menu' })
```

```javascript
// server_scripts/menu.js
NetworkEvents.server('my_channel', event => {
  console.info(event.player.getName().getString() + ' sent:', event.data)
  Network.sendToPlayer(event.player, 'my_channel', { result: 'ok' })
})
```

`data` is an NBT object. When sending, write a plain JS object and it is converted for you. Data the server receives comes from a client, so check that it makes sense before using it.

<a id="wiki-section-20"></a>
## RegistryEvents

Registers new items, blocks, entities and other content. Goes in `startup_scripts`; **restart the game** to apply changes. For the full guide, see [Registering new content](registering-new-content_us).

| Event | Notes |
|---|---|
| `register` | In the callback, register content with `event.item(id, b => {})`, `event.block(...)`, `event.entityType(...)` and similar methods |

```javascript
// startup_scripts/items.js
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', item => {
    item.maxStackSize = 16
  })
})
```

The available registration methods depend on platform and version. For example, `particleType` and `paintingVariant` are NeoForge only, and on 26.x enchantments are defined by data packs and cannot be registered from scripts.

<a id="wiki-section-21"></a>
## GoalEvents

Adds AI behavior to existing mobs. Goes in `startup_scripts`. See [Registering new content](registering-new-content_us).

| Event | Notes |
|---|---|
| `register` | Use `event.forType(entity id, goals => {})` to add AI goals to a mob type |

<a id="wiki-capability-events"></a>
<a id="wiki-section-22"></a>
## CapabilityEvents

Lets blocks, entities and items expose item, energy or fluid storage, so other mods (pipes, cables and so on) can interact with them. Goes in `startup_scripts`. Registration happens once at startup, so **restart the game** after changes. Create the storage objects with [Capabilities](global-bindings_us#wiki-capabilities).

Capability names are `'item'`, `'energy'` and `'fluid'`.

<a id="wiki-section-23"></a>
### NeoForge

| Method | Arguments the provider receives |
|---|---|
| `registerBlock(block id, capability, provider)` | `(level, pos, state, blockEntity, direction)`; `blockEntity` can be `null` |
| `registerBlockEntityContext(block entity type id, capability, provider)` | `(blockEntity, direction)` |
| `registerBlockEntity(block entity type id, capability, provider)` | None. Older form; called on every query |
| `registerEntity(entity id, capability, provider)` | `(entity, direction)`. Entities also support the capability name `'item_automation'` |
| `registerItem(item id, capability, provider)` | `(stack, context)` |
| `registerBlockNative` / `registerBlockEntityNative` / `registerEntityNative` / `registerItemNative` | Same arguments as above, but the second parameter is another mod's native capability object |

```javascript
// startup_scripts/capabilities.js
// Replace 'mymod:battery' with a block entity type you registered
// Each block entity gets its own energy storage, keyed by position (not saved to disk in this example)
const batteries = new Map()

CapabilityEvents.register(event => {
  event.registerBlockEntityContext('mymod:battery', 'energy', (blockEntity, direction) => {
    const key = blockEntity.getBlockPos().toString()
    if (!batteries.has(key)) {
      batteries.set(key, Capabilities.energyStorage(10000, 100, 100))
    }
    return batteries.get(key)
  })
})
```

For the `Native` methods, get another mod's capability object from that mod's classes with `Java.type(...)`. NekoJS does not need to depend on that mod.

For item capabilities, `context` is `null` on 1.21.1 and the native `ItemAccess` on 26.x; it is not a direction.

<a id="wiki-section-24"></a>
### Fabric

Fabric uses different method names, built on Fabric's Lookup API:

| Method | Arguments the provider receives |
|---|---|
| `registerBlockEntityContext(block entity type id, capability, provider)` | `(blockEntity, direction)` |
| `registerEntityContext(entity id, capability, provider)` | `(entity, direction)` |
| `registerItemContext(item id, capability, provider)` | `(stack, containerItemContext)` |
| `registerBlockEntity` / `registerEntity` / `registerItem` | None. Older form; called on every query |
| `registerBlockLookup(block id, lookup, provider)` | `(level, pos, state, blockEntity, context)` |
| `registerBlockEntityLookup(block entity type id, lookup, provider)` | `(blockEntity, context)` |
| `registerEntityLookup(entity id, lookup, provider)` | `(entity, context)` |
| `registerItemLookup(item id, lookup, provider)` | `(stack, context)` |

The `...Lookup` methods connect to custom Lookups defined by other mods. On Fabric, items and fluids use the Fabric Transfer API and work with other Fabric mods. Energy uses NekoJS's own interface and does not connect directly to other mods' energy systems.

<a id="wiki-section-25"></a>
### Writing a provider

- **Return each object's own storage.** Do not create a fresh empty storage on every query, and do not share one storage between every object of the same type.
- Return `null` when you do not want to provide the capability for this query.
- Registration only lets others find the storage. Saving and syncing its contents is up to you.
- On NeoForge, when a block's capability changes (for example, it appears or goes away), call `level.invalidateCapabilities(pos)` to refresh the cache.
- An unknown id, a misspelled capability name, a duplicate registration, a provider that throws, or a provider that returns the wrong type all report an error with a `NEKO-` code.

<a id="wiki-command-events"></a>
<a id="wiki-section-26"></a>
## CommandEvents

Registers custom commands. Use it in `server_scripts`.

| Event | Cancellable | Notes |
|---|---|---|
| `register` | — | Register commands. `event.dispatcher` is the vanilla command dispatcher |
| `command` | Cancellable | A command is about to run. Cancel it to stop the command. NeoForge only |

```javascript
// server_scripts/commands.js
const Commands = Java.type('net.minecraft.commands.Commands')

CommandEvents.register(event => {
  event.dispatcher.register(
    Commands.literal('hello')
      .executes(ctx => {
        ctx.getSource().sendSuccess(() => Text.of('Hello!'), false)
        return 1
      })
  )
})
```

Commands use vanilla Brigadier: `Commands.literal(...)` names the command, `.executes(...)` defines what it does, and returning `1` means success. Run `/nekojs reload` and the new command is available.

NekoJS registers the `/nekojs` command itself; your scripts do not need to.

<a id="wiki-keybind-events"></a>
<a id="wiki-section-27"></a>
## KeyBindEvents

Adds custom key bindings that players can change in the Controls screen. Goes in `client_scripts`. Supported on NeoForge 26.x and Fabric 26.x; not on 1.21.1.

| Event | Notes |
|---|---|
| `register(id, key[, category])` | Register a key binding |
| `pressed` | Fires once when the key goes down |
| `released` | Fires once when the key comes up |
| `tick` | Fires every tick while the key is held |

`pressed`, `released` and `tick` can all be filtered by key binding id.

```javascript
// client_scripts/keybinds.js
// Without a namespace, the id defaults to nekojs. Keys look like 'key.keyboard.g' or 'key.mouse.left'
// The category can be a vanilla one (such as 'movement') or your own (such as 'mymod:my_keys')
KeyBindEvents.register('mymod:special', 'key.keyboard.g', 'mymod:my_keys')

KeyBindEvents.pressed('mymod:special', event => {
  console.info('Pressed ' + event.id)
  // You can tell the server with Network.sendToServer(...) here
})

KeyBindEvents.released('mymod:special', event => { /* ... */ })

KeyBindEvents.tick('mymod:special', event => {
  // Runs every tick while held
})
```

- The event object has `event.id` (the full id), `event.keyMapping` (the vanilla key mapping) and `event.isDown()` (whether the key is held).
- `register` returns the vanilla key mapping, so you can also poll its `isDown()` or `consumeClick()` yourself.
- Registering the same id again does not create a duplicate, so `/nekojs reload client` is safe.
- The name shown in the Controls screen uses the translation key `key.<namespace>.<path>`; a custom category uses `key.category.<namespace>.<path>`. Add translations with `ClientEvents.lang` or a resource pack.

<a id="wiki-section-28"></a>
## ClientEvents

Client ticks, drawing and resource generation. Goes in `client_scripts`; apply changes with `/nekojs reload client`.

| Event | Platform | Notes |
|---|---|---|
| `tickPre` / `tickPost` | All | Once per client tick. The old name `tick` equals `tickPost` |
| `hud` | All | **Draw on the screen** every frame; see below |
| `screenRender` | All | **Draw on a screen** such as a chest; see below |
| `playerTickPre` / `playerTickPost` | NeoForge | Once per tick for the local player. The server equivalents are `PlayerEvents.tickPre` / `tickPost` |
| `interactionKey` | NeoForge | The player presses attack, use or pick block. Cancellable; cancelling stops vanilla from handling the key |
| `loggedIn` / `loggedOut` / `cloned` | NeoForge | The client connects / disconnects / its player object is replaced |
| `hudRender` | NeoForge | Register HUD drawing by id, with layers and ordering; see below |
| `worldRender` | NeoForge | Draw lines and boxes in the world; see below |
| `generateAssets` | NeoForge | **Generate resource pack files** (models, block states and so on); see below |
| `lang` | NeoForge | **Add translations**, filtered by language code; see below |
| `postEffects` | NeoForge | Declare post-processing effects; see [Client rendering](client-rendering_us) |
| `commandRegistry` | NeoForge | Register client commands |
| `registerKeyMappings` / `registerMenuScreens` / `registerRenderers` / `registerParticleProviders` | NeoForge | Register key mappings, menu screens, renderers and particles at game start. These need native Java objects and are rarely used |

<a id="wiki-section-29"></a>
### Drawing on the screen: hud and screenRender

`hud` fires every frame over the game view, and its callback receives the painter directly. `screenRender` fires every frame while a screen is open, with the painter at `event.painter`.

```javascript
// client_scripts/hud.js
ClientEvents.hud(painter => {
  // A status panel in the top-right corner
  let x = painter.getWidth() - 130
  painter
    .color(0xAA000000).rect(x, 10, 120, 60)      // Translucent black background
    .outline(x, 10, 120, 60, 0xFFFFFFFF)          // White border
    .centerText('NekoJS', x + 60, 20)             // Centered text
    .text('Hello HUD', x + 10, 32, 0xFFFFFFAA)    // Left-aligned text
    .resetColor()
  painter.item(Item.of('minecraft:diamond'), x + 10, 46)   // Item icon
})

// client_scripts/screen_overlay.js: a line of text at the top of chest screens
ClientEvents.screenRender(event => {
  if (event.screenTitle === 'Chest') {
    event.painter.centerText('Custom hint', event.painter.getWidth() / 2, 4, 0xFFFFFFAA)
  }
})
```

Painter methods are listed below. Coordinates are GUI-scaled pixels, and colors are ARGB integers; for example, `0x80FF0000` is translucent red.

| Method | Description |
|---|---|
| `getWidth()` / `getHeight()` | Screen width and height |
| `getPartialTick()` | How far the frame is between two ticks (0 to 1), for smooth animation |
| `getWorldTime()` | Client world time in ticks, for looping animation |
| `color(argb)` / `resetColor()` | Set / reset the default color. Methods without a color argument use it |
| `rect(x, y, w, h[, color])` | Filled rectangle |
| `outline(x, y, w, h[, color])` | 1-pixel border |
| `gradient(x, y, w, h, topColor, bottomColor)` | Top-to-bottom gradient rectangle |
| `gradientH(x, y, w, h, leftColor, rightColor)` | Left-to-right gradient rectangle. Very wide ones cost performance |
| `text(text, x, y[, color])` | Left-aligned text |
| `centerText(text, x, y[, color])` | Text centered on x |
| `textWidth(text)` / `wrapText(text, maxWidth)` / `lineHeight()` | Text width, wrap to a width, line height |
| `texture(textureId, x, y, w, h)` | Draw a w×h piece from the texture's top-left corner (texture assumed 256×256) |
| `texture(textureId, x, y, u, v, w, h)` | Take the piece from position (u, v) of the texture |
| `texture(textureId, x, y, u, v, w, h, textureWidth, textureHeight)` | Give the texture's real size |
| `textureFull(textureId, x, y, w, h)` | Scale the whole texture into w×h |
| `item(stack, x, y)` | Item icon; create the stack with `Item.of(...)` |
| `push()` / `pop()` / `translate(x, y)` | Save / restore the transform, move the origin |
| `scissor(x, y, w, h)` / `resetScissor()` | Only draw inside this area / remove the limit |

The `screenRender` event also has `event.screen` (the screen object), `event.screenTitle` (the title, such as `'Chest'`) and `event.mouseX` / `event.mouseY` (mouse position).

Keep in mind:

- `hud` runs every frame, so keep its logic light.
- `color()` stays in effect, so call `resetColor()` after each panel. Pair every `push()` with `pop()` and every `scissor()` with `resetScissor()`.
- The painter is only valid during the current callback. Do not keep it for the next frame; keep the data you want to draw and redraw it every frame.

<a id="wiki-section-30"></a>
### Registering drawing by id: hudRender and worldRender

NeoForge only. `hudRender` registers a HUD drawing function by id, with a layer and order; `worldRender` draws lines and boxes in the world. Registering the same id again replaces the old one.

```javascript
// client_scripts/boss_bar.js
// layer: 'background' | 'normal' | 'foreground'; within a layer, lower priority draws first
ClientEvents.hudRender('boss_bar', { layer: 'normal', priority: 0 }, (ctx, gui) => {
  const hp = clientData.get('boss_hp')   // Data the server synced with ClientData
  if (!hp) return
  ctx.text(hp.name + ' ' + (hp.hp * 100).toFixed(0) + '%', 8, 8, 0xFFFFFFFF)
  ctx.fillRect(8, 20, 120, 6, 0xAA000000)
  ctx.fillRect(8, 20, Math.floor(120 * hp.hp), 6, 0xFF5DBB63)
})

// client_scripts/lines.js
// layer: 'early' | 'normal' | 'late'; coordinates are world coordinates
ClientEvents.worldRender('quest_path', { layer: 'normal', priority: 0 }, ctx => {
  ctx.box(10, 70, 10, 12, 72, 12, 0xFF00FF00)
  ctx.line(0, 64, 0, 10, 70, 10, 0xFFFFFF00, 2)
})
```

An error in one drawing function only affects that function and shows up in the error panel. For more, see [Client rendering](client-rendering_us); for `clientData`, see [Global bindings](global-bindings_us#wiki-client-data).

<a id="wiki-section-31"></a>
### Generating resources and translations: generateAssets and lang

NeoForge only. `generateAssets` writes files into the `nekojs/assets` resource pack, with paths relative to that folder; `lang` collects translations by language code. Both run when the client loads resources (game start, `/nekojs reload client`).

```javascript
// client_scripts/assets.js
ClientEvents.generateAssets(event => {
  event.json('mymod/models/item/cool_gem.json', {
    parent: 'minecraft:item/generated',
    textures: { layer0: 'mymod:item/cool_gem' }
  })
})

ClientEvents.lang('en_us', event => {
  event.add('item.mymod.cool_gem', 'Cool Gem')
})
```

<a id="wiki-section-32"></a>
### Resource helper: Assets

Writing `event.json(...)` by hand makes it easy to get a folder wrong. On NeoForge 26.x you can use the `Assets` binding instead. Call it at the top level of a startup, server or client script. It also writes into `nekojs/assets`, and the files take effect the next time resources load (for example, F3+T).

```javascript
// startup_scripts/assets.js
// Block state: a full object (either variants or multipart) or just a model id
Assets.blockState('mymod:my_block', 'mymod:block/my_block')
Assets.blockState('mymod:my_block', { variants: { '': { model: 'mymod:block/my_block' } } })

// Block model
Assets.blockModel('mymod:my_block', {
  parent: 'minecraft:block/cube_all',
  textures: { all: 'my_block' }   // Shorthand; becomes 'mymod:block/my_block'
})

// Item model
Assets.itemModel('mymod:my_item', {
  parent: 'minecraft:item/generated',
  textures: { layer0: 'mymod:item/my_item' }   // Values that are already complete stay as they are
})

// Magenta placeholder textures
Assets.texture('mymod:block/my_block')   // → textures/block/my_block.png
Assets.texture('mymod:my_item', 'item')  // → textures/item/my_item.png
```

| Method | Writes to | Notes |
|---|---|---|
| `Assets.blockState(id, state)` | `assets/<namespace>/blockstates/<path>.json` | `state` is an object with `variants` or `multipart`, or a model id |
| `Assets.blockModel(id, model)` | `assets/<namespace>/models/block/<path>.json` | The path can contain folders, such as `mymod:custom/x` |
| `Assets.itemModel(id, model)` | `assets/<namespace>/models/item/<path>.json` | Same as above |
| `Assets.texture(id[, kind])` | `assets/<namespace>/textures/<kind>/<path>.png` | 16×16 magenta placeholder. `kind` is `'block'` (default) or `'item'` |

- **Texture shorthand**: in a model's `textures`, a value with neither `:` nor `/` becomes `<namespace>:block/<value>` (`<namespace>:item/<value>` for item models). Other values stay as they are.
- **Placeholder textures** only keep models from missing a texture; they show up magenta in game. Replace them with real textures from a resource pack.
- The id must be a valid resource id: lowercase letters, digits, `_`, `.` and `-`, with `/` allowed in the path. Without a namespace it defaults to `minecraft`. An invalid id throws an error and writes nothing.

<a id="wiki-section-33"></a>
## RecipeViewerEvents

Changes what JEI shows. Goes in `client_scripts`. Only available on NeoForge with JEI installed. Fires when JEI reloads (entering a world, reloading resources).

| Event | Filtered by | Notes |
|---|---|---|
| `addEntries` | `'item'` or `'fluid'` | Add entries to the JEI list: `event.add(id)` |
| `removeEntries` | `'item'` or `'fluid'` | Hide entries from the JEI list: `event.add(id)`. Only hides them; the game is unaffected |
| `removeRecipes` | — | Hide recipes by recipe id: `event.remove(recipeId)` |
| `removeCategories` | — | Hide a whole recipe category: `event.remove(categoryId)` |
| `addInformation` | — | Add an information page to an entry: `event.add(id, text)` |

```javascript
// client_scripts/jei.js
RecipeViewerEvents.addEntries('item', event => {
  event.add('mymod:cool_gem')
})

RecipeViewerEvents.removeEntries('item', event => {
  event.add('minecraft:debug_stick')   // Entries to hide are also added with add
})

RecipeViewerEvents.addInformation(event => {
  event.add('mymod:cool_gem', 'A very cool gem!')
})

RecipeViewerEvents.removeCategories(event => {
  event.remove('jei:information')
})
```

<a id="wiki-section-34"></a>
## ProbeEvents

Adjusts the code completion that `/nekojs probe` generates. Goes in `server_scripts`; NeoForge only. Changes take effect the next time you run `/nekojs probe`.

| Event | Notes |
|---|---|
| `modifyType` | Edit a Java class's declaration: `event.forClass(full class name)` returns an editor for renaming methods, changing parameter types, adding docs and so on |
| `assignType` | Replace a Java class everywhere with a type you choose: `event.assign(full class name, type)` |
| `addGlobal` | Declare an extra global: `event.add(name, type)` |
| `snippets` | Add a VS Code snippet: `event.add(name, prefix, body[, description])` |

Type arguments: a string containing `.` is a full Java class name; anything else is a basic type name such as `'string'`, `'number'` or `'boolean'`. Build complex types with `event.type(...)`, `event.array(...)` and `event.union(...)`.

```javascript
// server_scripts/probe_patch.js
ProbeEvents.modifyType(event => {
  const editor = event.forClass('net.minecraft.world.entity.player.Player')
  if (editor) {
    editor
      .renameMethod('getXxx', 'getCustom')          // Renames every overload with this name
      .changeParamType('addItem', 0, 'net.minecraft.world.item.ItemStack')
      .markOptional('eat', 'level')                 // Makes the parameter optional
      .setMethodDoc('getCustom', 'Custom docs')
      .setDoc('The player')
  }
})

ProbeEvents.assignType(event => {
  event.assign('net.minecraft.nbt.CompoundTag', 'MyNbt')
})

ProbeEvents.addGlobal(event => {
  event.add('MyFlag', 'boolean')
})

ProbeEvents.snippets(event => {
  event.add('nekojs-listener', 'listen', 'ServerEvents.started(e => { $1 })', 'Listen for server start')
})
```

For the editor's full method list, see [Probe type generation](probe-type-generation_us). A method or field name that is not found is ignored without an error.

<a id="wiki-section-35"></a>
## Custom events

If none of the events above fit, there are two options:

- **`ScriptEvents`** (startup scripts, NeoForge and Fabric): declare your own event group. Server or client scripts listen to it like a built-in event, and your scripts fire it. See [Event extensions](event-extensions_us).
- **`NativeEvents`** (startup scripts, NeoForge only): listen to a native NeoForge event class directly; handy for one-off needs. Restart the game after changes. See [Global bindings](global-bindings_us).

<a id="wiki-section-36"></a>
## Spelling check on load

When scripts load, NekoJS checks the event object members used in event callbacks. A misspelled name is reported as an error with a suggestion (`Did you mean ...`).

The check covers `event.player`, `event.getPlayer()`, `event['player']`, chained calls such as `event.player.getServer()`, optional chaining such as `event?.player`, and `event` assigned to another variable first.

Member names only known at run time (for example, a name taken from network data) are not checked; they run as usual and report errors at run time. If a script uses syntax the checker does not understand, that file skips the check and a warning goes to the log.

<a id="wiki-section-37"></a>
## Next steps

- [Global bindings](global-bindings_us): global objects such as `Item`, `Text` and `Network`.
- [Recipe system](recipe-system_us): `ServerEvents.recipes` in depth.
- [Registering new content](registering-new-content_us): `RegistryEvents` and `GoalEvents` in depth.
- [Client rendering](client-rendering_us): HUD, world drawing and post-processing.

<!-- wiki-nav -->

---

[Previous: Python feature support](python-feature-support_us) · [Contents](Home) · [Next: AI-assisted authoring](ai-support_us)
