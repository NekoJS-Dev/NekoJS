<!-- wiki-page: registering-new-content; locale: cn -->

> **中文** · [English](registering-new-content_us)

<a id="wiki-section-1"></a>
# 注册新内容

新物品、方块、实体等内容写在 `startup_scripts/` 里，用 `RegistryEvents.register` 注册。注册只在游戏启动时进行，所以每次增改注册内容都要**重启游戏**。`/nekojs reload startup` 会被拒绝。

<a id="wiki-section-2"></a>
## 快速上手

新建 `nekojs/startup_scripts/content.js`：

```javascript
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', item => {
    item.maxStackSize = 16
    item.rarity = 'rare'
    item.fireResistant = true
  })

  event.block('mymod:gem_block', block => {
    block.hardness = 3.0
    block.sound = 'stone'
    block.requiresTool = true
    block.lightLevel = 7
  })
})
```

重启游戏后执行 `/give @s mymod:cool_gem` 和 `/give @s mymod:gem_block`。方块会自动带一个同名物品，所以也能直接拿到手里。

这时它们显示成紫黑格子，名字是 `item.mymod.cool_gem`。这是因为还没有材质和名称，做法见下文的 [材质、模型和名称](#wiki-assets)。

> 物品在存档里用过之后，不要随便删掉它的注册代码，否则存档里的这些物品会消失。建议先在测试世界里试。

<a id="wiki-section-3"></a>
## 基本写法

`event` 上的方法以注册表命名，例如 `event.item`、`event.block`、`event.entityType`。每个方法接收两个参数：ID 和一个配置函数。

```javascript
RegistryEvents.register(event => {
  event.item('mymod:ruby', item => {
    item.maxStackSize = 16   // 普通设置：直接赋值
    item.tag('c:gems')       // 动作：调用方法
  })
})
```

- 普通设置直接赋值，例如 `item.maxStackSize = 16`。
- 动作和复杂配置调用方法，例如 `item.food(...)`、`item.tag(...)`、`block.noItem()`。
- 什么都不设也可以。`event.item('mymod:x', item => {})` 会用默认值注册一个能用的物品。下面各表写了默认值。

内置可用的方法：

| 方法 | 注册什么 |
|---|---|
| `event.item` | 物品 |
| `event.block` | 方块，默认带同名物品 |
| `event.fluid` | 流体（仅 NeoForge） |
| `event.entityType` | 实体 |
| `event.creativeModeTab` | 创造标签页 |
| `event.mobEffect` | 状态效果 |
| `event.potion` | 药水 |
| `event.soundEvent` | 声音 |
| `event.particleType` | 粒子类型 |
| `event.villagerType` | 村民类型 |
| `event.enchantment` | 附魔（仅 NeoForge 1.21.1） |

其他模组或插件可能会增加更多方法。方法名拼错时，报错会列出当前能用的全部方法。

<a id="wiki-section-4"></a>
## 物品

| 属性 | 默认值 | 说明 |
|---|---|---|
| `maxStackSize` | `64` | 最大堆叠数，1 到 99。设了 `maxDamage` 时无效 |
| `maxDamage` | `0` | 最大耐久。大于 0 时物品不能堆叠 |
| `fireResistant` | `false` | 掉进岩浆不会烧掉 |
| `rarity` | `'common'` | 稀有度：`'common'`、`'uncommon'`、`'rare'`、`'epic'` |
| `glowing` | `false` | 显示附魔光效 |
| `burnTime` | `0` | 作为燃料能烧多少 tick，大于 0 时可以放进熔炉 |
| `groupTab` | 无 | 放进哪个创造标签页，例如 `'minecraft:building_blocks'` |
| `jukeboxPlayable` | 无 | 放进唱片机播放的歌曲，例如 `'minecraft:cat'` |
| `trimMaterial` | 无 | 让物品成为盔甲纹饰材料，例如 `'minecraft:diamond'`（仅 26.x） |
| `food(...)` | — | 设为食物，见下方 |
| `tag(...)` | — | 加入标签，见 [在注册时加入标签](#wiki-builder-tags) |
| `component(id, value)` / `components` | — | 直接设置物品组件 |
| `parentModel` / `texture(...)` | — | 自动生成物品模型，见 [材质、模型和名称](#wiki-assets) |

<a id="wiki-section-5"></a>
### 食物

```javascript
RegistryEvents.register(event => {
  event.item('mymod:cheese', item => {
    item.food(food => {
      food.nutrition(4)
      food.saturation(0.6)
      food.fastEat()
      food.effect('minecraft:speed', 200, 0, 0.5)   // 效果、时长（tick）、等级、概率
    })
  })
})
```

| 方法 | 说明 |
|---|---|
| `nutrition(n)` / `saturation(n)` | 恢复的饥饿值 / 饱和度 |
| `alwaysEat()` | 饱的时候也能吃 |
| `fastEat()` | 吃得更快 |
| `effect(效果, 时长, 等级, 概率)` | 吃完后按概率获得状态效果 |

<a id="wiki-section-6"></a>
### 物品组件

需要设置上面没有的属性时，可以直接写物品组件。值的格式和原版数据包里的组件 JSON 一样：

```javascript
RegistryEvents.register(event => {
  event.item('mymod:gem', item => {
    item.component('minecraft:rarity', 'epic')
  })
})
```

`item.components = { ... }` 会替换掉之前设置的所有组件，`item.component(id, value)` 只改一个。组件在普通属性之后应用。值写错时游戏会在启动时报错。

物品的提示文字不在这里设置，请用客户端事件 `ItemEvents.tooltip`，见 [事件参考](event-reference_cn)。

<a id="wiki-section-7"></a>
## 方块

| 属性 | 默认值 | 说明 |
|---|---|---|
| `hardness` | `1.5` | 硬度，决定挖掘时间 |
| `resistance` | `1.5` | 爆炸抗性 |
| `lightLevel` | `0` | 亮度，0 到 15 |
| `requiresTool` | `false` | 是否必须用正确的工具挖才会掉落 |
| `sound` | `'stone'` | 声音：`'wood'`、`'stone'`、`'metal'`、`'glass'`、`'grass'`、`'gravel'`、`'wool'`、`'sand'`、`'snow'`、`'amethyst'` |
| `mapColor` | `'stone'` | 地图上的颜色，例如 `'dirt'`、`'water'`、`'gold'`、`'red'` |
| `renderType` | `'solid'` | 渲染方式：`'solid'`、`'cutout'`、`'cutout_mipped'`、`'translucent'` |
| `unbreakable()` | — | 变成不可破坏的方块 |
| `item` | — | 自动生成的同名物品，可以直接设置它的属性 |
| `noItem()` | — | 不生成同名物品 |
| `tag(...)` | — | 把**方块**加入标签 |

```javascript
RegistryEvents.register(event => {
  event.block('mymod:ruby_ore', block => {
    block.hardness = 3.0
    block.requiresTool = true
    block.tag('minecraft:mineable/pickaxe', 'minecraft:needs_iron_tool')

    // 设置自动生成的物品
    block.item.maxStackSize = 16
    block.item.tag('c:ores')
  })
})
```

只写了 `requiresTool = true` 而没有加入 `minecraft:mineable/pickaxe` 这类标签时，方块用什么工具挖都不会掉落。

名字拼错的 `sound` 和 `mapColor` 不会报错，会退回 `'stone'`。

关于 `renderType`：在 1.21.1 上直接生效。在 26.x 上透明效果由模型决定，需要模型里写 `"force_translucent": true` 或贴图带透明度。NeoForge 26.x 会为设置了 `'translucent'`、又没有自带模型的方块自动生成这样的模型；Fabric 需要自己提供模型。

<a id="wiki-section-8"></a>
## 流体（仅 NeoForge）

一次 `event.fluid` 会注册一整套可以放置、流动、用桶装起来的流体：

```javascript
RegistryEvents.register(event => {
  event.fluid('mymod:molten_iron', fluid => {
    fluid.density = 3000
    fluid.viscosity = 2000
    fluid.temperature = 1500
    fluid.lightLevel = 12
    // fluid.noBucket()   // 不要桶
    // fluid.noBlock()    // 不要液体方块
  })
})
```

上面的代码会注册：静止流体 `mymod:molten_iron`、流动流体 `mymod:flowing_molten_iron`、液体方块 `mymod:molten_iron` 和桶 `mymod:molten_iron_bucket`。

| 属性 | 默认值 | 说明 |
|---|---|---|
| `displayName` | 无 | 名称的翻译键，例如 `'fluid.mymod.molten_iron'` |
| `density` / `temperature` / `viscosity` | `1000` / `300` / `1000` | 密度、温度、黏度 |
| `lightLevel` | `0` | 亮度 |
| `canConvertToSource` | `false` | 能否像水一样形成无限水源 |
| `slopeFindDistance` / `levelDecreasePerBlock` | `4` / `1` | 寻找下坡的距离 / 每流一格降低的高度 |
| `tickRate` | `5` | 流动速度，数值越大流得越慢 |
| `explosionResistance` | `100` | 爆炸抗性 |
| `noBucket()` / `noBlock()` | — | 不注册桶 / 不注册液体方块 |
| `tag(...)` | — | 静止和流动两种流体都加入标签 |

流体的材质和模型需要自己放进资源包。

<a id="wiki-section-9"></a>
## 实体

```javascript
RegistryEvents.register(event => {
  event.entityType('mymod:test_mob', mob => {
    mob.category = 'monster'
    mob.size(0.6, 1.8)
    mob.spawnEgg(0x8a6f4d, 0xffe2b3)
    mob.attributes(attr => attr.maxHealth(18).movementSpeed(0.3).attackDamage(4))
    mob.goals(goals => goals
      .floatInWater(0)
      .meleeAttack(2, 1.2, false)
      .target(4, 'minecraft:player', true))
  })
})
```

不做额外设置时，实体是一个用僵尸材质的人形生物，会走动、转头、挥手。

| 属性 | 默认值 | 说明 |
|---|---|---|
| `category` | `'creature'` | 类别：`'creature'`、`'monster'`、`'ambient'`、`'water_creature'`、`'water_ambient'`、`'underground_water_creature'`、`'axolotls'`、`'misc'` |
| `width` / `height` | `0.6` / `1.8` | 碰撞箱大小，也可以写 `size(宽, 高)` |
| `trackingRange` / `updateInterval` | `8` / `3` | 同步距离（区块）/ 同步间隔（tick） |
| `fireImmune` / `noSave` / `noSummon` | `false` | 免疫火焰 / 不随存档保存 / 不能用 `/summon` 召唤 |
| `texture` | 僵尸材质 | 64×64 人形材质，例如 `'mymod:textures/entity/test_mob.png'` |
| `shadowRadius` | `0.5` | 阴影大小 |
| `spawnEgg(底色, 斑点色)` | — | 同时注册生物蛋 `<id>_spawn_egg` |
| `attributes(...)` | — | 属性：`maxHealth`、`movementSpeed`、`followRange`、`attackDamage`、`armor`、`armorToughness`、`knockbackResistance` |
| `goals(...)` | — | AI 行为，见下方 |
| `tag(...)` | — | 加入实体标签，例如 `'minecraft:raiders'` |

生物蛋的颜色只在 NeoForge 1.21.1 上生效。26.x 上要换生物蛋外观，需要在资源包里提供 `assets/<命名空间>/items/<名字>_spawn_egg.json`。

<a id="wiki-section-10"></a>
### 使用原版或模组的实体类

想让实体沿用某个现有生物的行为和外观，可以指定 Java 类和渲染器：

```javascript
const Zombie = Java.type('net.minecraft.world.entity.monster.zombie.Zombie')

RegistryEvents.register(event => {
  event.entityType('mymod:my_zombie', mob => {
    mob.entityClass(Zombie)
    mob.renderer = 'net.minecraft.client.renderer.entity.ZombieRenderer'
  })
})
```

在 1.21.1 上僵尸的类名是 `net.minecraft.world.entity.monster.Zombie`。

- 这个类会自动带上原版生物的全部属性。`attributes(...)` 只改你写到的那几项。
- 类没有自带属性时，用 `attributeBase('minecraft:zombie')` 借用一个现有实体的属性。
- `factory(...)` 不接受 JavaScript 函数，请改用 `entityClass(...)`。

<a id="wiki-section-11"></a>
## AI 行为

`goals(...)` 里可以用这些方法。第一个参数是优先级，数字越小越优先，可以省略：

| 方法 | 说明 |
|---|---|
| `floatInWater(优先级?)` | 在水里浮起来 |
| `randomStroll(速度)` | 随便走走 |
| `meleeAttack(速度, 是否一直追)` | 近战攻击 |
| `panic(速度)` | 受伤后乱跑 |
| `target(实体ID, 必须看见?)` | 把附近这种实体当作目标 |
| `hurtByTarget()` | 谁打它就反击谁 |
| `lookAt(实体ID, 距离)` | 看向附近的这种实体 |
| `avoid(实体ID, 距离, 速度)` | 躲开附近的这种实体 |
| `customClass(优先级, Goal类)` / `customTargetClass(优先级, Goal类)` | 使用模组或原版的 Goal 类 |

实体 ID 请写完整，例如 `'minecraft:player'`。自定义 Goal 类必须有一个只接收 `Mob` 参数的公开构造器。

<a id="wiki-section-12"></a>
### 给已有实体加 AI

`GoalEvents.register` 不创建新实体，而是给已有实体（包括原版生物）加行为。同样写在 `startup_scripts` 里：

```javascript
GoalEvents.register(event => {
  event.forType('minecraft:pig', goals => {
    goals.panic(0, 2.0).randomStroll(6, 0.8)
  })
})
```

<a id="wiki-section-13"></a>
## 创造标签页

```javascript
RegistryEvents.register(event => {
  event.creativeModeTab('mymod:main', tab => {
    tab.title = '我的模组'
    tab.icon = 'mymod:cool_gem'
    tab.add('mymod:cool_gem')
    tab.add('mymod:gem_block')
  })
})
```

`icon` 和 `add` 都可以写物品 ID 或 `Item.of(...)`。不设图标时显示屏障。也可以在物品上写 `item.groupTab = 'mymod:main'` 把它放进这个标签页。

<a id="wiki-section-14"></a>
## 其他内容

```javascript
RegistryEvents.register(event => {
  // 状态效果：category 可以是 'beneficial'、'harmful'、'neutral'（默认）
  event.mobEffect('mymod:shock', effect => {
    effect.category = 'harmful'
    effect.color = 0x00FF00
  })

  // 药水：effect(效果, 时长tick, 等级)，可以写多次
  event.potion('mymod:shock_potion', potion => {
    potion.effect('minecraft:poison', 600, 0)
    potion.effect('minecraft:speed', 3600, 1, false, true)   // 再加：是否为信标效果、是否显示粒子
  })

  // 声音：fixedRange 不设时由声音本身决定
  event.soundEvent('mymod:thunder_clap', sound => {
    sound.fixedRange = 64
  })

  // 粒子类型
  event.particleType('mymod:sparkle', particle => {
    particle.overrideLimiter = true
  })

  // 村民类型：没有可设置的属性
  event.villagerType('mymod:miner', villager => {})
})
```

药水只能使用游戏里已经存在的效果，ID 写错时这个效果会被忽略，不会报错。同一个脚本里刚注册的效果此时还不存在，所以上面用的是原版效果。

<a id="wiki-section-15"></a>
### 附魔（仅 NeoForge 1.21.1）

```javascript
RegistryEvents.register(event => {
  event.enchantment('mymod:ice_bane', ench => {
    ench.supportedItems = 'minecraft:enchantable/weapon'
    ench.weight = 5
    ench.maxLevel = 3
    ench.minCostBase = 5
    ench.minCostPerLevel = 5
    ench.maxCostBase = 20
    ench.maxCostPerLevel = 5
    ench.anvilCost = 2
    ench.slots = 'mainhand'
  })
})
```

这样注册的附魔可以出现在附魔台和书上，但**没有任何实际效果**，效果需要自己用事件实现。Minecraft 26.x 上附魔改为由数据包定义，请把附魔 JSON 放在 `nekojs/data/` 下。

<a id="wiki-builder-tags"></a>
<a id="wiki-section-16"></a>
## 在注册时加入标签

物品、方块、流体、实体和附魔可以在注册时直接加入标签，不用再单独写数据包 JSON：

```javascript
RegistryEvents.register(event => {
  event.item('mymod:ruby_pickaxe', item => {
    item.maxDamage = 500
    item.tag('minecraft:pickaxes', 'c:tools/mining_tool')
  })

  event.block('mymod:ruby_ore', block => {
    block.requiresTool = true
    block.tag('minecraft:mineable/pickaxe', 'minecraft:needs_iron_tool')
  })

  event.entityType('mymod:raider_golem', mob => {
    mob.tag('minecraft:raiders')
  })
})
```

- 一次可以传多个标签，也可以调用多次。
- 只写名字时默认是 `minecraft:`。开头带 `#` 也可以。
- 方块的 `tag` 加的是方块标签。要给同名物品加物品标签，写 `block.item.tag(...)`。
- 流体的标签会同时加给静止和流动两种流体。
- 每次服务器启动和执行 `/reload` 时都会重新应用。它们先于 `ServerEvents.tags` 加入，所以脚本里仍然可以再增删。

这个功能目前只在 **NeoForge 26.x** 上生效。在 1.21.1 和 Fabric 上，请用 `ServerEvents.tags` 或数据包 JSON，见 [事件参考](event-reference_cn)。

<a id="wiki-assets"></a>
<a id="wiki-section-17"></a>
## 材质、模型和名称

没有材质和名称不影响注册，物品照样能用，只是显示成紫黑格子和翻译键。资源放在 `nekojs/assets/` 下，路径和原版资源包一样：

| 内容 | 路径 |
|---|---|
| 物品材质 | `nekojs/assets/<命名空间>/textures/item/<名字>.png` |
| 方块材质 | `nekojs/assets/<命名空间>/textures/block/<名字>.png` |
| 模型 | `nekojs/assets/<命名空间>/models/item/<名字>.json` 等 |
| 名称 | `nekojs/assets/<命名空间>/lang/zh_cn.json`（英文用 `en_us.json`） |

语言文件的键是 `item.<命名空间>.<名字>`、`block.<命名空间>.<名字>` 或 `entity.<命名空间>.<名字>`：

```json
{
  "item.mymod.cool_gem": "酷炫宝石",
  "block.mymod.gem_block": "宝石块"
}
```

改完资源按 F3+T 刷新。

<a id="wiki-section-18"></a>
### 自动生成物品模型

只放了物品材质、不想手写模型时，可以让 NekoJS 生成模型：

```javascript
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', item => {
    item.texture('mymod:item/cool_gem')
  })
})
```

- 设置了 `parentModel` 或 `texture(...)` 的物品才会生成模型。都不设时不生成。
- `parentModel` 默认是 `'minecraft:item/generated'`，材质默认是 `<命名空间>:item/<名字>`。
- `texture(slot, id)` 可以设置其他材质槽，`textures = { ... }` 一次设置全部。
- PNG 材质需要自己提供，NekoJS 只生成模型 JSON。
- 你手动改过的文件不会被覆盖，此时日志里会出现 `NEKO-6008`。
- 生成记录保存在 `nekojs/.nekojs-item-assets/`，请和资源一起保留。

<a id="wiki-section-19"></a>
## 服务器运行中注册（实验性）

这是仅 NeoForge 26.x 可用的实验功能，可以不重启游戏就添加少量内容。先在服务器和每个客户端的 `nekojs/config/engine.toml` 中开启，然后重启：

```toml
[dynamicRegistry]
enabled = true
```

然后在 `server_scripts` 里写：

```javascript
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

限制：

- 只有 `item`、`soundEvent`、`mobEffect` 三种，属性也只有上面这些。
- 内容一旦加入就不会被删掉。从脚本里删掉后，它会被标记为过期，但仍然存在。`/nekojs registry` 可以查看状态，`/nekojs registry stale` 列出过期的条目。
- 有一项定义无效时，这一批都不生效，之前的内容保持不变。
- 服务器和客户端要开启同样的配置、使用同样的脚本。
- 请用新的 ID 和可以丢弃的测试世界来尝试。

<a id="wiki-section-20"></a>
## 从旧写法迁移

旧版本每种注册表有单独的入口，属性用链式方法设置。现在统一用 `RegistryEvents.register`，属性直接赋值：

| 旧写法 | 新写法 |
|---|---|
| `RegistryEvents.item(e => e.create(id, b => ...))` | `RegistryEvents.register(e => e.item(id, b => ...))` |
| `RegistryEvents.block(...)` 等每种一个入口 | `event.block(id, b => ...)` 等 |
| `b.maxStackSize(16)` | `b.maxStackSize = 16` |
| `b.fireResistant()` / `b.glowing()` | `b.fireResistant = true` / `b.glowing = true` |
| `b.group('tab_id')` | `b.groupTab = 'tab_id'` |
| `b.hardness(3).resistance(6)` | `b.hardness = 3`，`b.resistance = 6` |
| `b.requiresTool()` | `b.requiresTool = true` |
| `fluid.bucket(false)` / `fluid.block(false)` | `b.noBucket()` / `b.noBlock()` |
| `entity.fireImmune()` | `b.fireImmune = true` |
| `enchantment.minCost(5, 5)` | `b.minCostBase = 5`，`b.minCostPerLevel = 5` |

`food(...)`、`tag(...)`、`unbreakable()`、`noItem()`、`size(...)`、`spawnEgg(...)`、`attributes(...)`、`goals(...)`、`potion.effect(...)` 这些动作仍然是方法，写法不变。

<a id="wiki-section-21"></a>
## 常见错误

注册脚本出错时，报错写在 `logs/nekojs/startup.log`，也可以用 `/nekojs error` 查看：

| 报错 | 原因 |
|---|---|
| `Duplicate registration '...'` | 同一个 ID 注册了两次。后一次不会覆盖前一次，而是直接报错 |
| `RegistryEvent has no member '...'` | 方法名拼错，或当前平台没有这种注册表。报错会列出能用的方法 |
| `unknown registry '...'` | `event.register` 里的注册表名写错 |
| `... expects a builder callback as the last argument` | 少了配置函数 |
| `... expects an id string like 'mymod:thing'` | ID 不是字符串 |
| `maxStackSize must be in [1, 99] ...` | 属性值超出范围，其他属性也有类似检查 |

> 进阶用法：`event.register('item', 'mymod:raw', () => ...)` 可以直接提供一个创建对象的函数，注册表名可以写 `'item'` 或 `'minecraft:item'`。`event.<注册表>(id, 类型名, 配置函数)` 和 `event.custom(id, 类型名, 配置函数)` 用来调用插件提供的其他构建器类型。

<a id="wiki-section-22"></a>
## 平台差异

| 功能 | NeoForge 26.x | NeoForge 1.21.1 | Fabric 26.x |
|---|---|---|---|
| 物品、方块、实体、创造标签页、效果、药水、声音、粒子、村民类型 | ✅ | ✅ | ✅ |
| 流体 | ✅ | ✅ | ❌ |
| 附魔 | ❌（用数据包） | ✅ | ❌ |
| 注册时加入标签 | ✅ | ❌ | ❌ |
| 自动生成物品模型 | ✅ | ✅ | ✅ |
| 服务器运行中注册 | ✅（实验性） | ❌ | ❌ |

更多平台差异见 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-23"></a>
## 下一步

- [事件参考](event-reference_cn)：`RegistryEvents`、`GoalEvents`、`ServerEvents.tags`。
- [快速开始](quick-start_cn)

<!-- wiki-nav -->

---

[上一篇: 配方系统](recipe-system_cn) · [目录](Home) · [下一篇: 模块系统](module-system_cn)
