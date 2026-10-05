<!-- wiki-page: registering-new-content; locale: us -->

> **English** · [中文](registering-new-content_cn)

<a id="wiki-section-1"></a>
# Registering new content

Register new content in `startup_scripts/` through the **single entry point** `RegistryEvents.register`. Registration scripts run during game startup; you must restart the game after adding or changing registered content. `/nekojs reload startup` currently rejects execution and cannot rerun STARTUP scripts from a command while the game is running.

```javascript
// startup_scripts/my_content.js
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', b => {
    b.maxStackSize = 16
    b.rarity = 'rare'
    b.fireResistant = true
  })

  event.block('mymod:custom_block', b => {
    b.hardness = 3.0
    b.sound = 'stone'
    b.requiresTool = true
    b.lightLevel = 7
  })

  event.fluid('mymod:molten_iron', b => {
    b.density = 3000
    b.viscosity = 2000
    b.lightLevel = 12
  })
})
```

<a id="wiki-section-2"></a>
## Entry-point forms

- `event.<registry>(id, cb)` - Convenience methods for registries registered on the current platform, such as `item` / `block` / `entityType` / `creativeModeTab` / `mobEffect` / `potion` / `soundEvent` / `particleType` / `paintingVariant` / `villagerType`. The available entries vary by platform.
- `event.<registry>(id, typeName, cb)` - The same, but explicitly selects a named type registered by a third-party plugin.
- `event.custom(id, typeName, cb)` - Register by a globally unique type name.
- `event.register(registry, id, supplier)` - A direct supplier entry point; `registry` is a convenience name or a full key.

> Entries such as `fluid` and `enchantment` are not cross-platform capabilities: FluidBuilder is currently available only on NeoForge; the enchantment builder applies only to NeoForge 1.21.1. Use datapacks on 26.x; Fabric currently does not provide the corresponding registration surface.

**Builders use public fields**: assign data properties directly (`b.maxStackSize = 16`). Only actions and compound configuration retain methods (`b.noItem()`, `b.food(cb)`, `b.tag(...)`).

Invalid input fails immediately (all of these throw while the script executes and are logged as errors in that startup script):

- Registering the same id twice in the same registry → `IllegalStateException` (`RegistryRepository`, fail-fast; the later entry does not override the earlier one).
- An unrecognized registry name → `unknown registry 'xxx'`, with a hint to use an available convenience name or full key.
- An unrecognized type name → `unknown type 'xxx' for registry '...'; known types: [...]`, listing the types registered for that registry.
- A misspelled convenience method → `RegistryEvent has no member 'xxx'; known: [...]`, listing all entry points actually available on the current platform.
- A missing/invalid callback or an id that is not a string → the corresponding `IllegalArgumentException`.

Conversely, **leaving every builder setting unset is valid**: `event.item('mymod:x', b => {})` registers a usable item using the defaults listed below. The default values in these tables are what apply when you omit a setting.

<a id="wiki-section-3"></a>
## ItemBuilder - items

| Member | Description |
|---|---|
| `maxStackSize` | Maximum stack size (default 64; ignored when `maxDamage` is set) |
| `maxDamage` | Maximum durability (>0 replaces stackability) |
| `fireResistant` | Fire resistance (default false) |
| `rarity` | `'common'` \| `'uncommon'` \| `'rare'` \| `'epic'` (default common) |
| `glowing` | Glint (default false) |
| `burnTime` | Fuel burn time in ticks (>0 lets the item fuel furnaces/blast furnaces/smokers) |
| `groupTab` | Assign to a creative tab (for example `'minecraft:building_blocks'` or a custom tab id; null = no assignment) |
| `food(cb)` | Make it food (a `FoodBuilder` callback; see below) |
| `tag(tags...)` | Add item tags (see [tag details below](#wiki-section-11)) |

> Item tooltips are not configured on the builder. Add them by item id through the client event [`ItemEvents.tooltip`](event-reference_us#wiki-section-11).

> After registration, default item textures/models are resolved from `assets/mymod/textures/item/cool_gem.png` / `assets/mymod/models/item/cool_gem.json` (standard vanilla resource paths). You can place resources under `nekojs/assets/`.

<a id="wiki-section-4"></a>
### FoodBuilder (the `food(cb)` callback)

| Method | Description |
|---|---|
| `nutrition(v)` / `saturation(v)` | Nutrition / saturation |
| `alwaysEat()` / `fastEat()` | Always edible / fast eating |
| `effect(effectId, durationTicks, amplifier, probability)` | Chance to apply an effect when eaten |

<a id="wiki-section-5"></a>
## BlockBuilder - blocks

| Member | Description |
|---|---|
| `hardness` / `resistance` | Hardness / explosion resistance (default 1.5) |
| `lightLevel` | Light level (default 0) |
| `requiresTool` | Requires the correct tool to drop items (default false) |
| `sound` | `'wood'` \| `'stone'` \| `'metal'` \| `'glass'` \| `'grass'` \| `'gravel'` \| `'wool'` \| `'sand'` \| `'snow'` \| `'amethyst'` |
| `mapColor` | Map color name (for example `'dirt'`/`'water'`/`'gold'`/`'color_red'`/`'nether'`; default stone) |
| `renderType` | `'solid'` \| `'cutout'` \| `'cutout_mipped'` \| `'translucent'` (default solid) |
| `item` | **Pre-created BlockItem child builder**: assign fields directly, for example `b.item.maxStackSize = 16` |
| `unbreakable()` | Make unbreakable (action) |
| `noItem()` | Do not create a BlockItem (equivalent to `b.item = null`; action) |
| `item(cb)` | Configure the automatically created BlockItem (a convenience form equivalent to directly changing `b.item` fields) |
| `tag(tags...)` | Add **block** tags; use `b.item.tag(...)` for item tags on the BlockItem |

A BlockItem with the same name is created automatically by default. **Co-registration has three parts**: the child builder is pre-created during construction, `noItem()` sets it to null to suppress it, and lazy references link the objects. The configuration of `b.item` takes effect only when the BlockItem is actually registered.

> **renderType platform differences**: it takes effect on 1.21.1 (the render layer is applied through `ItemBlockRenderTypes.setRenderLayer`). On 26.x (1.21.5+), rendering is **model-driven**: the model JSON texture reference needs `"force_translucent": true` (or a texture with alpha). For blocks declaring `renderType = 'translucent'`, NekoJS automatically generates a default model containing this flag when you have not supplied a model.

<a id="wiki-section-6"></a>
## FluidBuilder - fluids (NeoForge only)

> Fabric 26.x currently has no fluid registration or fluid recipe surface; Cleanroom content belongs to a separate legacy branch.

On NeoForge, you can register a complete custom fluid that can be placed, flow, and be collected with a bucket. One registration produces the fluid type, source/flowing fluids, fluid block, and bucket item.

| Member | Description |
|---|---|
| `displayName` | Translation key (for example `fluid.mymod.molten_iron`) |
| `density` | Density (>1000 sinks below water, <1000 rises; default 1000) |
| `temperature` / `viscosity` / `lightLevel` | Temperature / viscosity (higher means slower flow) / light emission (defaults 300 / 1000 / 0) |
| `canConvertToSource` | Whether sources can be generated infinitely (default false; comparable to vanilla water in 1.21) |
| `slopeFindDistance` / `levelDecreasePerBlock` | Flow pathfinding distance (default 4) / level decrease per block (default 1) |
| `explosionResistance` / `tickRate` | Explosion resistance (default 100) / flow tick interval (default 5) |
| `bucket` / `block` | Whether to register a bucket (`<id>_bucket`) / fluid block (id = fluid id); both default to true |
| `noBucket()` / `noBlock()` | Suppress the corresponding co-registration (actions; equivalent to `b.bucket = false`) |
| `tag(tags...)` | Add both source and flowing fluids to the tag |

```javascript
RegistryEvents.register(event => {
  event.fluid('mymod:molten_iron', b => {
    b.density = 3000
    b.viscosity = 2000
    b.temperature = 1500
    b.lightLevel = 12
    b.slopeFindDistance = 3
    // b.noBucket()   // Do not register a bucket
    // b.noBlock()    // Do not register a fluid block
  })
})
```

Automatically registered objects: `<id>` (source fluid), `flowing_<id>` (flowing fluid), `<id>` (fluid block), and `<id>_bucket` (bucket). You must provide fluid textures/models in a resource pack (26.x is model-driven; 1.21.1 uses `IClientFluidTypeExtensions`).

<a id="wiki-section-7"></a>
## EntityTypeBuilder - scripted entities

The default entity uses `NekoScriptMob` (`PathfinderMob`). NeoForge 1.21.1/26.x and Fabric 26.x all automatically register a genuinely visible humanoid model: it uses the vanilla zombie texture, walking/head/swing animations, and a 0.5-block shadow by default, rather than an empty renderer. You can replace the humanoid texture or select a compatible native renderer for a native entity.

| Member | Description |
|---|---|
| `category` | Entity category name: `'creature'`/`'monster'`/`'hostile'`/`'ambient'`/`'water_creature'`/`'water_ambient'`/`'underground_water_creature'`/`'axolotls'`/`'misc'` |
| `width` / `height` | Hitbox width × height (defaults 0.6 / 1.8; or use `size(w, h)`) |
| `trackingRange` / `updateInterval` | Tracking range / synchronization interval in ticks (defaults 8 / 3) |
| `receiveVelocityUpdates` / `fireImmune` / `noSave` / `noSummon` | Boolean switches (defaults true / false / false / false) |
| `spawnEgg(bgColor, hlColor)` | Automatically register an `<id>_spawn_egg` spawn egg (action) |
| `attributes(cb)` | Attribute configuration (`maxHealth`/`movementSpeed`/`followRange`/`attackDamage`/`armor`/`armorToughness`/`knockbackResistance`) |
| `renderer` | Defaults to `'humanoid'`; can also be the fully qualified class name of a native Java renderer, loaded only on the client; it must have a public `(EntityRendererProvider.Context)` constructor and accept this entity class |
| `texture` | Defaults to `'minecraft:textures/entity/zombie/zombie.png'`; the humanoid model uses a 64×64 texture, which resource packs can override |
| `shadowRadius` | Shadow radius, default 0.5; must be finite and non-negative |
| `factory(javaFactory)` | Native Java `BiFunction<EntityType, Level, LivingEntity>`; rejects JavaScript/Graal callbacks to prevent access to the STARTUP Context from different threads on the two sides |
| `entityClass(clazz)` | Select a public, non-abstract native `LivingEntity` class; its public `(EntityType, Level)` constructor is validated before registration |
| `attributeBase(entityType)` | Inherit the complete default attributes of an existing native entity type |
| `attributeSupplier(supplier)` | Specify a complete native attribute baseline; for custom classes without `createAttributes()` |
| `goals(cb)` | AI goals (see [GoalBuilder](#wiki-section-12)) |
| `tag(tags...)` | Add entity type tags (for example `'minecraft:raiders'`) |

```javascript
RegistryEvents.register(event => {
  event.entityType('mymod:test_mob', b => {
    b.category = 'monster'
    b.size(0.6, 1.8)
    b.spawnEgg(0x8a6f4d, 0xffe2b3)
    b.attributes(a => a.maxHealth(18).movementSpeed(0.3).attackDamage(4))
    b.goals(g => g.floatInWater(0).target(2, 'minecraft:player', true).meleeAttack(4, 1.2, false))
  })
})
```

Native entity example (NeoForge/Fabric 26.x):

```javascript
const Zombie = Java.type('net.minecraft.world.entity.monster.zombie.Zombie')
RegistryEvents.register(event => {
  event.entityType('mymod:native_mob', build => {
    build.entityClass(Zombie)
    build.renderer = 'net.minecraft.client.renderer.entity.ZombieRenderer'
  })
})
```

In 1.21.1, the zombie class name is `net.minecraft.world.entity.monster.Zombie`; the renderer class name is unchanged. The constructor is resolved when `entityClass(...)` is configured. Missing constructors, non-public or abstract classes, and classes that are not `LivingEntity` fail immediately. After entity creation, the returned object's `EntityType` is also checked: it must be the type registered by this declaration.

A native class's public static `createAttributes()` automatically supplies the complete attribute baseline. If that method is absent, explicitly select `attributeBase(...)` or `attributeSupplier(...)` to avoid losing special attributes such as flying speed or reinforcement chance. `attributes(...)` **overrides only the attributes explicitly configured in the callback**; all unconfigured values retain the baseline, rather than resetting native defaults to NekoJS's seven common defaults. The default `NekoScriptMob` uses the complete Mob attribute baseline. An opaque Java factory whose entity class cannot be inferred must also supply the required Mob attributes (including `FOLLOW_RANGE`). An incomplete baseline or a non-finite value is rejected before registration.

A native Java factory must be independent of the guest Context and usable on both the server and client threads. A plain `factory((type, level) => ...)` is explicitly rejected; `entityClass(...)` is recommended. `renderer`, `texture`, and `shadowRadius` are readable/writable Bean properties, configured as `build.texture = 'mymod:textures/entity/mob.png'`, not deferred script factories.

`renderer = 'humanoid'` uses a generic humanoid appearance; `texture`/`shadowRadius` apply to that renderer. With a native renderer, its native model, texture, and shadow logic determine the appearance. Before installation, the client checks the renderer's entity generic type and rejects cases such as using `ZombieRenderer` for a plain `NekoScriptMob`. Declarations store only class names and do not load client classes on dedicated servers.

`GoalEvents.register` also applies to vanilla entities and entities registered by these scripts. `GoalBuilder.custom(...)` / `customTarget(...)` accept only native Java `Function<Mob, Goal>`, not `mob => goal` guest closures. Scripts can use `customClass(...)` / `customTargetClass(...)` to select native Goal classes satisfying the public `(Mob)` constructor contract; see below.

**Spawn egg version differences**: colors **take effect** on NeoForge 1.21.1 through runtime tinting. Neither 26.x loader uses these arguments for runtime tinting. NeoForge generates a default egg model; Fabric falls back to the vanilla egg model only for registered eggs without an item definition. A custom appearance requires `assets/<namespace>/items/<path>_spawn_egg.json` referencing the appropriate model and texture; supplying a PNG alone does not change the default reference. Custom definitions and visible plugin models have priority. Malformed custom definitions retain native errors rather than being hidden by the fallback.

<a id="wiki-section-8"></a>
## EnchantmentBuilder - enchantments (NeoForge 1.21.1 only)

> Enchantments on 26.x use a data-driven registry and cannot use this runtime builder; use datapacks instead. Fabric 26.x currently has no corresponding registration surface either. A builder remaining in the source does not mean the 26.x registration path is available.

| Member | Description |
|---|---|
| `supportedItems` | Tag id for enchantable items (for example `'minecraft:enchantable/weapon'`; a `#` prefix is tolerated; null = empty set) |
| `weight` / `maxLevel` | Weight (default 1) / maximum level (default 1) |
| `minCostBase` / `minCostPerLevel` | Minimum cost `base + perLevel * (level - 1)` |
| `maxCostBase` / `maxCostPerLevel` | Maximum cost curve (same formula) |
| `anvilCost` | Anvil cost (default 0) |
| `slots` | `'any'`/`'armor'`/`'chest'`/`'feet'`/`'head'`/`'legs'`/`'hand'`/`'mainhand'`/`'offhand'` (default mainhand) |
| `tag(tags...)` | Add enchantment tags (for example `'minecraft:treasure'`) |

> No enchantment effect components are attached by default (registration, enchanting/books, and exclusivity work, but there is no actual effect). Enchantment effects in 1.21+ require `EnchantmentEffectComponents`, which is not exposed yet.

<a id="wiki-section-9"></a>
## CreativeTabBuilder - creative tabs

| Member | Description |
|---|---|
| `title` | Tab title |
| `icon` | Icon (item id string or ItemStack; null falls back to the barrier icon) |
| `add(item)` | Add an entry (item id string or ItemStack) |

```javascript
RegistryEvents.register(event => {
  event.creativeModeTab('mymod:cool_tab', b => {
    b.title = 'My mod'
    b.icon = 'mymod:cool_gem'
    b.add('mymod:cool_gem')
    b.add('mymod:custom_block')
  })
})
```

After registration, refer to this tab from the `groupTab` field on `ItemBuilder`.

<a id="wiki-section-10"></a>
## Simple registries

```javascript
RegistryEvents.register(event => {
  // Status effect
  event.mobEffect('mymod:shock', b => { b.category = 'harmful'; b.color = 0x00FF00 })

  // Potion (can contain multiple effect instances; effect appends an instance)
  event.potion('mymod:shock_potion', b => {
    b.effect('mymod:shock', 600, 0)                      // (effect, duration in ticks, level)
    b.effect('minecraft:speed', 3600, 1, false, true)    // (+ambient, show particles/icon)
  })

  // Sound (when fixedRange=null, the sound definition determines the range)
  event.soundEvent('mymod:thunder_clap', b => { b.fixedRange = 64 })

  // Particle type
  event.particleType('mymod:sparkle', b => { b.overrideLimiter = true })

  // Painting variant (width/height in pixels, multiples of 16; assetId defaults to the registration id)
  event.paintingVariant('mymod:epic_painting', b => {
    b.width = 32; b.height = 16; b.title = 'The sea'
  })

  // Villager type (identifier only; no configuration)
  event.villagerType('mymod:miner', b => {})

  // Enchantment
  event.enchantment('mymod:ice_bane', b => {
    b.supportedItems = 'minecraft:enchantable/weapon'
    b.weight = 5; b.maxLevel = 3
    b.minCostBase = 5; b.minCostPerLevel = 5
    b.maxCostBase = 20; b.maxCostPerLevel = 5
    b.anvilCost = 2; b.slots = 'mainhand'
  })
})
```

<a id="wiki-section-11"></a>
## tag - tagging registered content

You can declare tags during registration: builders for **items, blocks, entity types, fluids, enchantments, and painting variants** all provide `tag(...)`. This addresses cases where a block does not drop or an item is missing from a tag, without requiring handwritten datapack JSON or waiting for `ServerEvents.tags`.

```javascript
RegistryEvents.register(event => {
  event.item('mymod:my_pick', b => {
    b.tag('c:tools/pickaxe', 'minecraft:mineable/pickaxe'.replace('mineable/', 'needs_'))
    // Pass multiple tags at once, or call this multiple times
  })

  event.block('mymod:my_ore', b => {
    b.hardness = 3.0; b.requiresTool = true
    b.tag('minecraft:mineable/pickaxe', 'minecraft:needs_iron_tool')
  })

  event.entityType('mymod:raider_golem', b => {
    b.category = 'monster'
    b.tag('minecraft:raiders')
  })
})
```

Rules and semantics:

- **Argument format**: `'namespace:name'` or bare `'name'` (→ `'minecraft:name'`); a `#` prefix is recognized too; you can pass multiple values at once.
- **When it takes effect**: `.tag(...)` records pending entries, which are inserted automatically during tag loading (the dispatch point for `ServerEvents.tags`). **They are reapplied on every server startup / `/reload`**.
- **Combining with `ServerEvents.tags`**: pending entries are inserted **before** that event; script listeners can still override them with `add`/`remove`.
- **Fluids**: the tag is applied to both the source (`<id>`) and flowing (`flowing_<id>`) fluid.
- **BlockItem for a block**: `tag(...)` on a `block` builder adds **block tags**; use `b.item.tag(...)` for item tags on the automatically created BlockItem.
- **Supported registries**: `item` / `block` / `entityType` / `fluid` / `enchantment` / `paintingVariant`; the other registries have no tag system.
- On 26.x, tag directories use the registry name (`data/<ns>/tags/item/...`, singular).

> Builder `tag(...)` is currently provided only on **NeoForge 26.x**. The 1.21.1 builders do not have this method; use `ServerEvents.tags` from the [event reference](event-reference_us), or datapack JSON. Fabric's corresponding registration surface is still a subset.

<a id="wiki-section-12"></a>
## GoalBuilder - AI goals

This is the builder used in the `goals(g => ...)` callback. Built-in methods provide default-priority or explicit `priority` overloads; custom methods require `priority` (lower numbers mean higher priority).

| Method | Description |
|---|---|
| `floatInWater(priority?)` | Float in water |
| `randomStroll(speed)` / `randomStroll(priority, speed)` | Wander randomly |
| `meleeAttack(speed, longMemory)` / `meleeAttack(priority, speed, longMemory)` | Melee attack |
| `panic(speed)` / `panic(priority, speed)` | Flee in panic |
| `target(id)` / `target(priority, id)` / `target(priority, id, mustSee)` | Pursue the nearest target |
| `hurtByTarget()` / `hurtByTarget(priority)` | Retaliate after being attacked |
| `lookAt(id, radius)` / `lookAt(priority, id, radius)` | Look at a nearby target |
| `avoid(id, radius, speed)` / `avoid(priority, id, radius, speed)` | Avoid a nearby target |
| `custom(priority, javaFactory)` | Native Java `Function<Mob, Goal>` that creates a regular Goal for this mob; rejects guest closures |
| `customTarget(priority, javaFactory)` | The same, added to the target selector |
| `customClass(priority, goalClass)` | Public, non-abstract native Goal class with a public `(Mob)` constructor |
| `customTargetClass(priority, goalClass)` | The same class/constructor contract, added to the target selector |

Targets for `target/lookAt/avoid` accept entity id strings or native `LivingEntity` Java class objects. Common built-in mappings apply only to bare names or the `minecraft:` namespace; `mymod:zombie` does not incorrectly match `minecraft:zombie`. NekoJS-registered entities resolve by full id to the actual configured native class (default `NekoScriptMob`). When the class cannot be inferred from an opaque factory, pass a Java class explicitly. Always using the full `namespace:id` is recommended.

Built-in methods can still be chained inside the script's `goals(build => ...)` callback. Custom Goal factories are retained by the permanent registry, however, and must be independent of the script generation Context. Do not use `custom(priority, mob => ...)`, or bypass the restriction with a Java adapter capturing the guest Context. Class entry points validate the class and constructor during configuration, and each mob receives its own newly constructed Goal. A constructor accepting only a narrower parameter such as `(PathfinderMob)` does not satisfy the `(Mob)` entry-point contract.

The join-level path that adds Goals to native mobs uses weak identity deduplication: the same object reentering the world does not receive duplicate Goals, while new objects are handled independently. Default scripted entities consume their configuration through their own Goal initialization path.

<a id="wiki-section-13"></a>
## GoalEvents - adding AI to existing entities

Instead of creating new entities, add goals to **existing entity types**, including vanilla types.

```javascript
// startup_scripts/pig_goals.js
GoalEvents.register(event => {
  event.forType('minecraft:pig', goals => {
    goals.panic(0, 2.0).randomStroll(6, 0.8)
  })
})
```

<a id="wiki-section-14"></a>
## Resources and localization

Missing resources **do not affect registration**: the item is still registered and can be held, but displays a purple-and-black missing texture and an untranslated key. To give it the intended appearance, follow vanilla resource-resolution paths (these are not defined by NekoJS and cannot be changed):

- **Item/block textures**: `nekojs/assets/<mod>/textures/item/<name>.png`, `textures/block/<name>.png`.
- **Model JSON**: `nekojs/assets/<mod>/models/item/<name>.json`, and so on.
- **Language files**: `nekojs/assets/<mod>/lang/zh_cn.json`; vanilla builds keys from the registry: `item.<mod>.<name>` for items, `block.<mod>.<name>` for blocks, and `entity.<mod>.<name>` for entities.

<a id="wiki-section-15"></a>
## DynamicRegistry - registration while the server is running (experimental, NeoForge 26.x only)

`RegistryEvents.register` is effective only during game startup. If you need to register content **after the server has started** (for example, dynamically adding items through world script packs), use the `DynamicRegistry` binding. First enable `[dynamicRegistry] enabled = true` in `nekojs/config/engine.toml`:

```javascript
// server_scripts/dyn.js
// Wait for server startup before calling; a top-level call precedes server registry access initialization.
ServerEvents.started(event => {
  const ruby = DynamicRegistry.item('mymod:ruby', builder => builder
      .maxStackSize(64)
      .rarity('epic')
      .fireResistant()
      .mode('world'))

  DynamicRegistry.soundEvent('mymod:boom', builder => builder
      .mode('world').fixedRange(16))
  DynamicRegistry.mobEffect('mymod:wither_touch', builder => builder
      .category('harmful').color(0x8B0000))
})
```

`DynamicRegistry` builders still use chained methods (`mode('world' | 'reloadable')`, and so on). Reload semantics are stale-retain: `/nekojs reload` **never unregisters** registered entries. Rerunning scripts claims entries by id; use `/nekojs registry` to inspect the health snapshot.

<a id="wiki-section-16"></a>
## Migrating from older syntax

`RegistryEvents` now has only the `register` entry point, and builder data properties are assigned directly to public fields:

| Old syntax | New syntax |
|---|---|
| `RegistryEvents.item(e => e.create(id, b => ...))` | `RegistryEvents.register(e => e.item(id, b => ...))` |
| `RegistryEvents.block(...)` / `.fluid(...)` / other per-registry entries | The same: `event.<registry>(id, b => ...)` |
| `builder.maxStackSize(16)` | `b.maxStackSize = 16` |
| `builder.fireResistant()` / `.glowing()` | `b.fireResistant = true` / `b.glowing = true` |
| `builder.rarity('rare')` | `b.rarity = 'rare'` |
| `builder.group('tab_id')` | `b.groupTab = 'tab_id'` |
| `builder.hardness(3).resistance(6)` | `b.hardness = 3` `b.resistance = 6` |
| `builder.sound('wood')` / `.mapColor('dirt')` | `b.sound = 'wood'` / `b.mapColor = 'dirt'` |
| `builder.requiresTool()` / `.renderType('translucent')` | `b.requiresTool = true` / `b.renderType = 'translucent'` |
| `builder.item(cb)` (configure the automatic BlockItem) | Assign `b.item.xxx = ...` directly, or continue to use `b.item(cb)` |
| `fluid.bucket(false)` / `.block(false)` | `b.noBucket()` / `b.noBlock()` (or `b.bucket = false` / `b.block = false`) |
| `entity.category('monster')` / `.fireImmune()` | `b.category = 'monster'` / `b.fireImmune = true` |
| `enchantment.minCost(5, 5)` / `.maxCost(20, 5)` | `b.minCostBase = 5` `b.minCostPerLevel = 5` / `b.maxCostBase = 20` `b.maxCostPerLevel = 5` |
| `enchantment.supportedItems('#...')` / `.slots('mainhand')` | `b.supportedItems = '...'` / `b.slots = 'mainhand'` |
| `sound.fixedRange(64)` | `b.fixedRange = 64` |
| `particle.overrideLimiter(true)` | `b.overrideLimiter = true` |
| `painting.width(2).height(2).assetId(...)` | `b.width = 2` `b.height = 2` `b.assetId = '...'` |
| `effect.category('harmful').color(...)` | `b.category = 'harmful'` `b.color = ...` |
| Unchanged: `b.food(cb)`, `b.tag(...)`, `b.unbreakable()`, `b.noItem()`, `b.size(w,h)`, `b.spawnEgg(a,b)`, `b.attributes(cb)`, `b.goals(cb)`, `potion.effect(...)` | Actions/compound configuration remain methods |

<a id="wiki-section-17"></a>
## Platform differences

- **NeoForge 1.21.1**: supports most builders on this page; the enchantment builder is available only on this version's runtime registration surface.
- **NeoForge 26.x**: supports the main dynamic registration builder surface, but enchantments use data-driven registration and cannot use `event.enchantment(...)`.
- **Fabric 26.x**: supports a subset of registration builders. The entity type builder uses a visible humanoid model/64×64 zombie texture and allows native renderer configuration. GoalBuilder supports built-in script configuration and native factory/class entry points for custom goals. `Capabilities` / `CapabilityEvents` are connected to real Fabric Lookup/Transfer, which does not mean NeoForge native types or a standard cross-mod energy API. FluidBuilder, VillagerTrades, enchantments, and some platform-specific registration surfaces remain unavailable. See [Platforms and compatibility](platform-compatibility_us).
- Cleanroom 1.12.2 uses the older pipeline on a separate legacy branch; this repository does not verify whether the syntax on this page works there.

<a id="wiki-section-18"></a>
## Next steps

- [Event reference](event-reference_us) - `RegistryEvents` / `GoalEvents`.
- [Quick start](quick-start_us)

<!-- wiki-nav -->

---

[Previous: Recipe system](recipe-system_us) · [Contents](Home) · [Next: Module system](module-system_us)
