<!-- wiki-page: registering-new-content; locale: us -->

> **English** · [中文](registering-new-content_cn)

<a id="wiki-section-1"></a>
# Registering new content

Register items, blocks and entities in `startup_scripts/` with `StartupEvents.registry(selector, callback)`. Import `StartupEvents` from `nekojs:event/versioned` in STARTUP scripts. **Fully restart the game or dedicated server** after adding, removing or changing entries. `/nekojs reload startup` is rejected.

<a id="wiki-section-2"></a>
## Quick start

Create `nekojs/startup_scripts/content.js`:

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('item', event => {
  event.create('mymod:cool_gem', build => {
    build.maxStackSize = 16
    build.rarity = 'rare'
    build.fireResistant = true
  })
})

StartupEvents.registry('block', event => {
  event.create('mymod:gem_block', build => {
    build.hardness = 3.0
    build.sound = 'stone'
    build.requiresTool = true
    build.lightLevel = 7
  })
})
```

Restart the game, then run `/give @s mymod:cool_gem` and `/give @s mymod:gem_block`. A block automatically gets an item with the same ID, so you can hold it too.

At this point both show up as purple-and-black squares named `item.mymod.cool_gem`. They have no texture or name yet; see [Textures, models and names](#wiki-assets) below.

> Once an item has been used in a world, do not just delete its registration code, or those items will vanish from the save. Try new content in a test world first.

<a id="wiki-section-3"></a>
## The basic pattern

Pass one required selector per subscription. Each event object works with one registry. Use Minecraft’s original resource path for vanilla selectors such as `item`, `block`, or `entity_type`. `item` and `minecraft:item` select the same registry. If you subscribe with both, each listener runs once. Use the full key for a modded registry, such as `example:machines`. The entry ID uses its own namespace. To set a listener priority, use `StartupEvents.registry(priority, selector, callback)`.

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('item', event => {
  event.create('mymod:ruby', build => {
    build.maxStackSize = 16   // a plain setting: assign it
    build.tag('c:gems')       // an action: call a method
  })
})
```

- `event.create(id, build)` uses the default builder. An empty configuration callback keeps the defaults.
- `event.create(id, typeName, build)` selects a named builder in this registry. In the `item` listener, `event.create('mymod:sapphire', 'basic', build => { build.rarity = 'rare' })` explicitly selects `basic`. Supply a type name if there is no default.
- Assign properties such as `build.maxStackSize = 16`. Use methods such as `build.tag(...)` or `build.noItem()` for actions and nested configuration.
- `event.createCustom(id, supplier)` queues a function that returns a valid native entry. The loader calls it when committing the entry. This works for registries without a builder and follows script access and loader registration rules.

Built-in selectors

| Selector | Registers |
|---|---|
| `item` | Items |
| `block` | Blocks, with a matching item by default |
| `fluid` | Fluids (NeoForge only) |
| `entity_type` | Entities |
| `creative_mode_tab` | Creative tabs |
| `mob_effect` | Status effects |
| `potion` | Potions |
| `sound_event` | Sounds |
| `particle_type` | Particle types |
| `villager_type` | Villager types |

You can subscribe to static registries explicitly opened for your Minecraft version and loader. NekoJS does not auto-discover writable registries. To open a modded registry, the host mod creates the native static registry through the loader and a NekoJS plugin declares its full key and supported versions and loaders. Plugins can also add named builders to a registry that is already open.

<a id="wiki-section-4"></a>
## Items

| Property | Default | Notes |
|---|---|---|
| `maxStackSize` | `64` | Maximum stack size, 1 to 99. Ignored when `maxDamage` is set |
| `maxDamage` | `0` | Maximum durability. Above 0, the item does not stack |
| `fireResistant` | `false` | Does not burn in lava |
| `rarity` | `'common'` | `'common'`, `'uncommon'`, `'rare'` or `'epic'` |
| `glowing` | `false` | Shows the enchantment glint |
| `burnTime` | `0` | How many ticks it burns as fuel; above 0 it works in furnaces |
| `groupTab` | None | Which creative tab it appears in, such as `'minecraft:building_blocks'` |
| `jukeboxPlayable` | None | Song played in a jukebox, such as `'minecraft:cat'` |
| `trimMaterial` | None | Makes the item an armor trim material, such as `'minecraft:diamond'` (26.x only) |
| `food(...)` | — | Makes it food; see below |
| `tag(...)` | — | Adds it to tags; see [Adding tags at registration](#wiki-builder-tags) |
| `component(id, value)` / `components` | — | Sets item components directly |
| `parentModel` / `texture(...)` | — | Generates an item model; see [Textures, models and names](#wiki-assets) |

<a id="wiki-section-5"></a>
### Food

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('item', event => {
  event.create('mymod:cheese', build => {
    build.food(food => {
      food.nutrition(4)
      food.saturation(0.6)
      food.fastEat()
      food.effect('minecraft:speed', 200, 0, 0.5)   // effect, duration in ticks, amplifier, chance
    })
  })
})
```

| Method | Notes |
|---|---|
| `nutrition(n)` / `saturation(n)` | Hunger and saturation restored |
| `alwaysEat()` | Can be eaten when full |
| `fastEat()` | Eaten faster |
| `effect(effect, duration, amplifier, chance)` | Gives a status effect after eating, with the given chance |

<a id="wiki-section-6"></a>
### Item components

For properties not listed above, set item components directly. Values use the same format as component JSON in vanilla data packs:

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('item', event => {
  event.create('mymod:gem', build => {
    build.component('minecraft:rarity', 'epic')
  })
})
```

`build.components = { ... }` replaces every component set before it; `build.component(id, value)` changes one. Components apply after the plain properties. A wrong value makes the game report an error at startup.

Item tooltips are not set here. Use the client event `ItemEvents.tooltip`; see [Event reference](event-reference_us).

<a id="wiki-section-7"></a>
## Blocks

| Property | Default | Notes |
|---|---|---|
| `hardness` | `1.5` | How long it takes to mine |
| `resistance` | `1.5` | Explosion resistance |
| `lightLevel` | `0` | Light level, 0 to 15 |
| `requiresTool` | `false` | Only drops when mined with the right tool |
| `sound` | `'stone'` | `'wood'`, `'stone'`, `'metal'`, `'glass'`, `'grass'`, `'gravel'`, `'wool'`, `'sand'`, `'snow'` or `'amethyst'` |
| `mapColor` | `'stone'` | Color on maps, such as `'dirt'`, `'water'`, `'gold'` or `'red'` |
| `renderType` | `'solid'` | `'solid'`, `'cutout'`, `'cutout_mipped'` or `'translucent'` |
| `unbreakable()` | — | Makes the block unbreakable |
| `item` | — | The matching item; set its properties directly |
| `noItem()` | — | Do not create a matching item |
| `tag(...)` | — | Adds the **block** to tags |

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('block', event => {
  event.create('mymod:ruby_ore', build => {
    build.hardness = 3.0
    build.requiresTool = true
    build.tag('minecraft:mineable/pickaxe', 'minecraft:needs_iron_tool')

    // Configure the matching item
    build.item.maxStackSize = 16
    build.item.tag('c:ores')
  })
})
```

If you set `requiresTool = true` without adding a tag like `minecraft:mineable/pickaxe`, the block drops nothing no matter which tool mines it.

A misspelled `sound` or `mapColor` does not throw; it falls back to `'stone'`.

About `renderType`: on 1.21.1 it works directly. On 26.x, transparency comes from the model, which needs `"force_translucent": true` or a texture with transparency. NeoForge 26.x generates such a model for blocks set to `'translucent'` that have no model of their own; on Fabric you provide the model yourself.

<a id="wiki-section-8"></a>
## Fluids (NeoForge only)

One `event.create` call in a `fluid` listener registers a complete fluid that can be placed, flows and fits in a bucket.

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('fluid', event => {
  event.create('mymod:molten_iron', build => {
    build.density = 3000
    build.viscosity = 2000
    build.temperature = 1500
    build.lightLevel = 12
    // build.noBucket()   // no bucket
    // build.noBlock()    // no liquid block
  })
})
```

This registers the source fluid `mymod:molten_iron`, the flowing fluid `mymod:flowing_molten_iron`, the liquid block `mymod:molten_iron` and the bucket `mymod:molten_iron_bucket`.

| Property | Default | Notes |
|---|---|---|
| `displayName` | None | Translation key for the name, such as `'fluid.mymod.molten_iron'` |
| `density` / `temperature` / `viscosity` | `1000` / `300` / `1000` | Density, temperature and viscosity |
| `lightLevel` | `0` | Light level |
| `canConvertToSource` | `false` | Whether it forms infinite sources like water |
| `slopeFindDistance` / `levelDecreasePerBlock` | `4` / `1` | How far it looks for a slope / how much it drops per block |
| `tickRate` | `5` | Flow speed; higher is slower |
| `explosionResistance` | `100` | Explosion resistance |
| `noBucket()` / `noBlock()` | — | Skip the bucket / skip the liquid block |
| `tag(...)` | — | Adds both the source and flowing fluid to tags |

You provide the fluid's textures and models in a resource pack.

<a id="wiki-section-9"></a>
## Entities

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('entity_type', event => {
  event.create('mymod:test_mob', build => {
    build.category = 'monster'
    build.size(0.6, 1.8)
    build.spawnEgg(0x8a6f4d, 0xffe2b3)
    build.attributes(attr => attr.maxHealth(18).movementSpeed(0.3).attackDamage(4))
    build.goals(goals => goals
      .floatInWater(0)
      .meleeAttack(2, 1.2, false)
      .target(4, 'minecraft:player', true))
  })
})
```

With no extra setup, the entity is a humanoid with the zombie texture that walks, turns its head and swings its arms.

| Property | Default | Notes |
|---|---|---|
| `category` | `'creature'` | `'creature'`, `'monster'`, `'ambient'`, `'water_creature'`, `'water_ambient'`, `'underground_water_creature'`, `'axolotls'` or `'misc'` |
| `width` / `height` | `0.6` / `1.8` | Hitbox size; `size(width, height)` also works |
| `trackingRange` / `updateInterval` | `8` / `3` | Sync distance (chunks) / sync interval (ticks) |
| `fireImmune` / `noSave` / `noSummon` | `false` | Immune to fire / not saved with the world / cannot be summoned with `/summon` |
| `texture` | Zombie texture | A 64×64 humanoid texture, such as `'mymod:textures/entity/test_mob.png'` |
| `shadowRadius` | `0.5` | Shadow size |
| `spawnEgg(baseColor, spotColor)` | — | Also registers the spawn egg `<id>_spawn_egg` |
| `attributes(...)` | — | `maxHealth`, `movementSpeed`, `followRange`, `attackDamage`, `armor`, `armorToughness`, `knockbackResistance` |
| `goals(...)` | — | AI behavior; see below |
| `tag(...)` | — | Adds it to entity tags, such as `'minecraft:raiders'` |

Spawn egg colors only apply on NeoForge 1.21.1. On 26.x, change the egg's look by providing `assets/<namespace>/items/<name>_spawn_egg.json` in a resource pack.

<a id="wiki-section-10"></a>
### Using a vanilla or modded entity class

To reuse the behavior and look of an existing mob, set its Java class and renderer:

```javascript
import { StartupEvents } from "nekojs:event/versioned";

const Zombie = Java.type('net.minecraft.world.entity.monster.zombie.Zombie')

StartupEvents.registry('entity_type', event => {
  event.create('mymod:my_zombie', build => {
    build.entityClass(Zombie)
    build.renderer = 'net.minecraft.client.renderer.entity.ZombieRenderer'
  })
})
```

On 1.21.1 the zombie class is `net.minecraft.world.entity.monster.Zombie`.

- The class brings all of the mob's vanilla attributes. `attributes(...)` only changes the ones you write.
- If the class has no attributes of its own, borrow another entity's with `attributeBase('minecraft:zombie')`.
- `factory(...)` does not accept JavaScript functions; use `entityClass(...)` instead.

<a id="wiki-section-11"></a>
## AI behavior

These methods work inside `goals(...)`. The first argument is the priority, where lower runs first; you can leave it out:

| Method | Notes |
|---|---|
| `floatInWater(priority?)` | Floats in water |
| `randomStroll(speed)` | Wanders around |
| `meleeAttack(speed, keepChasing)` | Melee attack |
| `panic(speed)` | Runs around after being hurt |
| `target(entityId, mustSee?)` | Targets nearby entities of this type |
| `hurtByTarget()` | Fights back against whatever hurts it |
| `lookAt(entityId, distance)` | Looks at nearby entities of this type |
| `avoid(entityId, distance, speed)` | Runs from nearby entities of this type |
| `customClass(priority, goalClass)` / `customTargetClass(priority, goalClass)` | Uses a Goal class from a mod or vanilla |

Write full entity IDs, such as `'minecraft:player'`. A custom Goal class needs a public constructor that takes only a `Mob`.

<a id="wiki-section-12"></a>
### Adding AI to existing entities

`GoalEvents.register` does not create entities. It adds behavior to existing ones, including vanilla mobs. It also goes in `startup_scripts`:

```javascript
import { GoalEvents } from "nekojs:event/versioned";

GoalEvents.register(event => {
  event.forType('minecraft:pig', goals => {
    goals.panic(0, 2.0).randomStroll(6, 0.8)
  })
})
```

<a id="wiki-section-13"></a>
## Creative tabs

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('creative_mode_tab', event => {
  event.create('mymod:main', build => {
    build.title = 'My Mod'
    build.icon = 'mymod:cool_gem'
    build.add('mymod:cool_gem')
    build.add('mymod:gem_block')
  })
})
```

`icon` and `add` accept an item ID or `Item.of(...)`. Without an icon, the tab shows a barrier. You can also put an item into the tab with `build.groupTab = 'mymod:main'`.

<a id="wiki-section-14"></a>
## Other content

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('mob_effect', event => {
  // Status effect: category is 'beneficial', 'harmful' or 'neutral' (default)
  event.create('mymod:shock', build => {
    build.category = 'harmful'
    build.color = 0x00FF00
  })
})

StartupEvents.registry('potion', event => {
  // Potion: effect(effect, duration in ticks, amplifier); call it as often as you like
  event.create('mymod:shock_potion', build => {
    build.effect('minecraft:poison', 600, 0)
    build.effect('minecraft:speed', 3600, 1, false, true)   // plus: ambient (beacon-style), show particles
  })
})

StartupEvents.registry('sound_event', event => {
  // Sound: without fixedRange, the sound definition decides
  event.create('mymod:thunder_clap', build => {
    build.fixedRange = 64
  })
})

StartupEvents.registry('particle_type', event => {
  // Particle type
  event.create('mymod:sparkle', build => {
    build.overrideLimiter = true
  })
})

StartupEvents.registry('villager_type', event => {
  // Villager type: nothing to configure
  event.create('mymod:miner', build => {})
})
```

Potions can only use effects that already exist in the game. A misspelled effect ID is silently skipped. An effect registered in the same script does not exist yet at that point, which is why the example uses vanilla effects.

<a id="wiki-section-15"></a>
### Enchantments and data-pack registries

Enchantments and other data-pack registry entries load through data packs. Put the JSON under `nekojs/data/` in the format for your Minecraft version.

<a id="wiki-builder-tags"></a>
<a id="wiki-section-16"></a>
## Adding tags at registration

Items, blocks, fluids and entities can join tags right where you register them, with no separate data pack JSON:

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('item', event => {
  event.create('mymod:ruby_pickaxe', build => {
    build.maxDamage = 500
    build.tag('minecraft:pickaxes', 'c:tools/mining_tool')
  })
})

StartupEvents.registry('block', event => {
  event.create('mymod:ruby_ore', build => {
    build.requiresTool = true
    build.tag('minecraft:mineable/pickaxe', 'minecraft:needs_iron_tool')
  })
})

StartupEvents.registry('entity_type', event => {
  event.create('mymod:raider_golem', build => {
    build.tag('minecraft:raiders')
  })
})
```

- Pass several tags at once or call `tag` several times.
- A bare name means `minecraft:`. A leading `#` is fine.
- A block's `tag` adds block tags. For item tags on the matching item, use `build.item.tag(...)`.
- A fluid's tags go on both the source and the flowing fluid.
- The tags are applied again every time the server starts and on `/reload`. They are added before `ServerEvents.tags` runs, so your scripts can still add or remove entries.

This currently only works on **NeoForge 26.x**. On 1.21.1 and Fabric, use `ServerEvents.tags` or data pack JSON; see [Event reference](event-reference_us).

<a id="wiki-assets"></a>
<a id="wiki-section-17"></a>
## Textures, models and names

Missing textures and names do not stop registration. The item still works; it just shows a purple-and-black square and its translation key. Put resources under `nekojs/assets/`, using the same paths as a vanilla resource pack:

| Content | Path |
|---|---|
| Item texture | `nekojs/assets/<namespace>/textures/item/<name>.png` |
| Block texture | `nekojs/assets/<namespace>/textures/block/<name>.png` |
| Models | `nekojs/assets/<namespace>/models/item/<name>.json` and so on |
| Names | `nekojs/assets/<namespace>/lang/en_us.json` (`zh_cn.json` for Chinese) |

Language keys are `item.<namespace>.<name>`, `block.<namespace>.<name>` or `entity.<namespace>.<name>`:

```json
{
  "item.mymod.cool_gem": "Cool Gem",
  "block.mymod.gem_block": "Gem Block"
}
```

Press F3+T after changing resources.

<a id="wiki-section-18"></a>
### Generating item models

If you have an item texture and do not want to write a model by hand, NekoJS can generate it:

```javascript
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry('item', event => {
  event.create('mymod:cool_gem', build => {
    build.texture('mymod:item/cool_gem')
  })
})
```

- Only items with `parentModel` or `texture(...)` set get a model. Items with neither get nothing.
- `parentModel` defaults to `'minecraft:item/generated'`; the texture defaults to `<namespace>:item/<name>`.
- `texture(slot, id)` sets another texture slot; `textures = { ... }` sets them all at once.
- You provide the PNG textures; NekoJS only writes the model JSON.
- Files you edited by hand are never overwritten; the log shows `NEKO-6008` when that happens.
- The generation record lives in `nekojs/.nekojs-item-assets/`. Keep it together with your resources.

<a id="wiki-section-19"></a>
## Registering while the server runs (experimental)

This experimental feature, available only on NeoForge 26.x, adds a small set of content without restarting the game. First enable it in `nekojs/config/engine.toml` on the server and on every client, then restart:

```toml
[dynamicRegistry]
enabled = true
```

Then write this in `server_scripts`:

```javascript
import { DynamicRegistryEvents } from "nekojs:event/versioned";

DynamicRegistryEvents.dynamicRegistry(event => {
  event.item('mymod:ruby', item => {
    item.maxStackSize = 64
    item.rarity = 'epic'
    item.fireResistant = true
  })
  event.soundEvent('mymod:boom', sound => { sound.fixedRange = 16 })
  event.mobEffect('mymod:wither_touch', effect => {
    effect.category = 'harmful'
    effect.color = 0x8B0000
  })
})
```

Limits:

- Only `item`, `soundEvent` and `mobEffect` exist, with only the properties shown above.
- Content is never removed once added. If you delete it from the script, it is marked stale but stays. `/nekojs registry` shows the status and `/nekojs registry stale` lists stale entries.
- If any definition is invalid, the whole batch is skipped and the previous content stays as it was.
- The server and clients must use the same setting and the same scripts.
- Experiment with new IDs in a throwaway test world.

<a id="wiki-section-20"></a>
## Migrating from the old style

`RegistryEvents` was removed without an alias. Import `StartupEvents` and split multi-registry callbacks into one listener per registry. Keep the original entry IDs and avoid depending on the order of callbacks for different registries.

| Old | New |
|---|---|
| `event.item(id, build)` or `event.block(id, build)` in `RegistryEvents.register(...)` | Subscribe to `StartupEvents.registry('item', ...)` or `StartupEvents.registry('block', ...)` and call `event.create(id, build)` in that listener |
| `event.item(id, typeName, build)` | Call `event.create(id, typeName, build)` in the `item` listener |
| `event.custom(id, typeName, build)` | Subscribe to the registry that owns the builder, then call `event.create(id, typeName, build)` |
| `event.register(registry, id, supplier)` | Subscribe to the registry, then call `event.createCustom(id, supplier)` |

Builder properties and action methods keep their existing form.

<a id="wiki-section-21"></a>
## Common errors

Errors in registration scripts are written to `logs/nekojs/startup.log`; `/nekojs error` shows them too:

| Error | Cause |
|---|---|
| Duplicate entry ID in one registry | The later registration does not replace the first |
| Invalid or unopened selector | Check the original registry key and support for your loader and Minecraft version |
| Missing builder | Check for a default builder or specify a named builder for the selected registry |
| Missing configuration callback | Pass the configuration callback last in `event.create(...)` |
| Native registration failed | Check loader support and whether the `createCustom` supplier returns a valid entry |

<a id="wiki-section-22"></a>
## Platform differences

| Feature | NeoForge 26.x | NeoForge 1.21.1 | Fabric 26.x |
|---|---|---|---|
| Items, blocks, entities, creative tabs, effects, potions, sounds, particles, villager types | ✅ | ✅ | ✅ |
| Fluids | ✅ | ✅ | ❌ |
| Adding tags at registration | ✅ | ❌ | ❌ |
| Generating item models | ✅ | ✅ | ✅ |
| Registering while the server runs | ✅ (experimental) | ❌ | ❌ |

See [Platform compatibility](platform-compatibility_us) for more platform differences.

<a id="wiki-section-23"></a>
## Next steps

- [Event reference](event-reference_us): `StartupEvents.registry`, `GoalEvents`, `ServerEvents.tags`.
- [Quick start](quick-start_us)

<!-- wiki-nav -->

---

[Previous: Recipe system](recipe-system_us) · [Contents](Home) · [Next: Module system](module-system_us)
