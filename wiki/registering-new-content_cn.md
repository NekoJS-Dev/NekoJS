<!-- wiki-page: registering-new-content; locale: cn -->

> **中文** · [English](registering-new-content_us)

<a id="wiki-section-1"></a>
# 注册新内容

在 `startup_scripts/` 里通过**单一入口** `RegistryEvents.register` 注册新内容。注册脚本在游戏启动阶段执行；新增或修改注册内容后必须重启游戏。当前 `/nekojs reload startup` 会拒绝执行，不能从运行中的命令重跑 STARTUP 脚本。

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
## 入口形态

- `event.<registry>(id, cb)` —— 当前平台已登记的注册表糖方法，例如 `item` / `block` / `entityType` / `creativeModeTab` / `mobEffect` / `potion` / `soundEvent` / `particleType` / `paintingVariant` / `villagerType`。具体入口随平台变化。
- `event.<registry>(id, typeName, cb)` —— 同上，但显式指定第三方插件登记的命名类型。
- `event.custom(id, typeName, cb)` —— 按全局唯一类型名登记。
- `event.register(registry, id, supplier)` —— 裸 supplier 入口，`registry` 为糖名或完整键。

> `fluid`、`enchantment` 等入口不是跨平台能力：FluidBuilder 当前仅 NeoForge 可用；附魔 builder 仅适用于 NeoForge 1.21.1，26.x 应使用数据包，Fabric 当前不提供对应注册面。

**builder 是 public field 风格**：数据属性直接赋值（`b.maxStackSize = 16`），只有"动作"和复合配置保留方法（`b.noItem()`、`b.food(cb)`、`b.tag(...)`）。

写错什么会当场炸（全是脚本执行期抛异常，日志里是这条 startup 脚本的报错）：

- 同注册表同 id 注册两次 → `IllegalStateException`（`RegistryRepository`，fail-fast，不是后者覆盖前者）。
- 注册表名不认识 → `unknown registry 'xxx'`，提示可用糖名或完整键的形式。
- 类型名不认识 → `unknown type 'xxx' for registry '...'; known types: [...]`，直接列出该注册表下已登记的类型。
- 拼错糖方法名 → `RegistryEvent has no member 'xxx'; known: [...]`，列出当前平台实际存在的全部入口。
- 少传/传错回调、id 不是字符串 → 各自的 `IllegalArgumentException`。

反过来说，**builder 上什么都不设也是合法的**：`event.item('mymod:x', b => {})` 会用表里列出的默认值注册出一个能用的物品。下面各表的「默认」列就是不写时的取值。

<a id="wiki-section-3"></a>
## ItemBuilder —— 物品

| 成员 | 说明 |
|---|---|
| `maxStackSize` | 最大堆叠数（默认 64；设了 `maxDamage` 时忽略） |
| `maxDamage` | 最大耐久（>0 时替代堆叠数） |
| `fireResistant` | 抗火（默认 false） |
| `rarity` | `'common'` \| `'uncommon'` \| `'rare'` \| `'epic'`（默认 common） |
| `glowing` | 发光（默认 false） |
| `burnTime` | 燃料燃烧时间 tick（>0 时物品可作熔炉/高炉/烟熏炉燃料） |
| `groupTab` | 分配到创造标签页（如 `'minecraft:building_blocks'` 或自定义 tab id；null=不分配） |
| `food(cb)` | 设为食物（接 `FoodBuilder` 回调，见下） |
| `tag(tags...)` | 给物品打 tag（见[下方 tag 说明](#wiki-section-11)） |

> 物品 tooltip 不在 builder 上设置——用客户端事件 [`ItemEvents.tooltip`](event-reference_cn#wiki-section-11) 按物品 id 添加。

> 注册后物品的默认材质/模型按 `assets/mymod/textures/item/cool_gem.png` / `assets/mymod/models/item/cool_gem.json` 解析（标准原版资源路径）。可在 `nekojs/assets/` 下放资源。

<a id="wiki-section-4"></a>
### FoodBuilder（`food(cb)` 回调）

| 方法 | 说明 |
|---|---|
| `nutrition(v)` / `saturation(v)` | 营养 / 饱和度 |
| `alwaysEat()` / `fastEat()` | 总可吃 / 快速食用 |
| `effect(effectId, durationTicks, amplifier, probability)` | 食用后概率获得效果 |

<a id="wiki-section-5"></a>
## BlockBuilder —— 方块

| 成员 | 说明 |
|---|---|
| `hardness` / `resistance` | 硬度 / 抗爆（默认 1.5） |
| `lightLevel` | 亮度（默认 0） |
| `requiresTool` | 需要正确工具才能掉落（默认 false） |
| `sound` | `'wood'` \| `'stone'` \| `'metal'` \| `'glass'` \| `'grass'` \| `'gravel'` \| `'wool'` \| `'sand'` \| `'snow'` \| `'amethyst'` |
| `mapColor` | 地图颜色名（如 `'dirt'`/`'water'`/`'gold'`/`'color_red'`/`'nether'`，默认 stone） |
| `renderType` | `'solid'` \| `'cutout'` \| `'cutout_mipped'` \| `'translucent'`（默认 solid） |
| `item` | **预创建的 BlockItem 子 builder**——直接改字段：`b.item.maxStackSize = 16` |
| `unbreakable()` | 不可破坏（动作） |
| `noItem()` | 不生成 BlockItem（等价 `b.item = null`，动作） |
| `item(cb)` | 配置自动创建的 BlockItem（便捷面，等价直改 `b.item` 字段） |
| `tag(tags...)` | 给**方块**打 tag；给 BlockItem 打物品 tag 用 `b.item.tag(...)` |

默认会自动生成一个同名 BlockItem。**连带注册三件套**：子 builder 构造期预创建、`noItem()` 置 null 抑制、对象间经懒引用连接——`b.item` 的配置只在 BlockItem 真正注册时生效。

> **renderType 平台差异**：1.21.1 上会实际生效（经 `ItemBlockRenderTypes.setRenderLayer` 应用渲染层）。26.x（1.21.5+）渲染为**模型驱动**——需要模型 JSON 贴图引用加 `"force_translucent": true`（或贴图带 alpha）；声明了 `renderType = 'translucent'` 的方块 NekoJS 会自动生成带该标记的默认模型（没自写模型时）。

<a id="wiki-section-6"></a>
## FluidBuilder —— 流体（仅 NeoForge）

> Fabric 26.x 当前没有流体注册和流体配方面；Cleanroom 内容属于独立 legacy 分支。

NeoForge 上可注册完整的自定义流体：可放置、流动并用桶舀取。一次注册会产出流体类型、source/flowing 流体、液体方块和桶物品。

| 成员 | 说明 |
|---|---|
| `displayName` | 翻译 key（如 `fluid.mymod.molten_iron`） |
| `density` | 密度（>1000 比水重下沉，<1000 上浮；默认 1000） |
| `temperature` / `viscosity` / `lightLevel` | 温度 / 粘度（越大流得越慢）/ 发光（默认 300 / 1000 / 0） |
| `canConvertToSource` | 是否可无限生成源（默认 false，对标 1.21 原版水） |
| `slopeFindDistance` / `levelDecreasePerBlock` | 流动寻路距离（默认 4）/ 每格下降等级（默认 1） |
| `explosionResistance` / `tickRate` | 抗爆（默认 100）/ 流动 tick 间隔（默认 5） |
| `bucket` / `block` | 是否注册桶（`<id>_bucket`）/ 液体方块（id = 流体 id），默认都 true |
| `noBucket()` / `noBlock()` | 抑制对应连带注册（动作；等价 `b.bucket = false`） |
| `tag(tags...)` | source 与 flowing 两个流体都进 tag |

```javascript
RegistryEvents.register(event => {
  event.fluid('mymod:molten_iron', b => {
    b.density = 3000
    b.viscosity = 2000
    b.temperature = 1500
    b.lightLevel = 12
    b.slopeFindDistance = 3
    // b.noBucket()   // 不注册桶
    // b.noBlock()    // 不注册液体方块
  })
})
```

自动注册产物：`<id>`（source 流体）、`flowing_<id>`（flowing 流体）、`<id>`（液体方块）、`<id>_bucket`（桶）。流体纹理/模型需自行放资源包（26.x 走模型驱动，1.21.1 走 `IClientFluidTypeExtensions`）。

<a id="wiki-section-7"></a>
## EntityTypeBuilder —— 脚本化实体

默认实体使用 `NekoScriptMob`（`PathfinderMob`），NeoForge 1.21.1/26.x 与 Fabric 26.x 均自动注册真正可绘制的人形模型：默认使用原版僵尸纹理、走动/头部/挥动动画和 0.5 格阴影，不再使用空 renderer。可替换人形纹理，或为原生实体选择兼容的原生 renderer。

| 成员 | 说明 |
|---|---|
| `category` | 实体类别名：`'creature'`/`'monster'`/`'hostile'`/`'ambient'`/`'water_creature'`/`'water_ambient'`/`'underground_water_creature'`/`'axolotls'`/`'misc'` |
| `width` / `height` | 碰撞箱宽 × 高（默认 0.6 / 1.8；或用 `size(w, h)`） |
| `trackingRange` / `updateInterval` | 追踪范围 / 同步间隔 tick（默认 8 / 3） |
| `receiveVelocityUpdates` / `fireImmune` / `noSave` / `noSummon` | 布尔开关（默认 true / false / false / false） |
| `spawnEgg(bgColor, hlColor)` | 自动注册 `<id>_spawn_egg` 生物蛋（动作） |
| `attributes(cb)` | 属性表（`maxHealth`/`movementSpeed`/`followRange`/`attackDamage`/`armor`/`armorToughness`/`knockbackResistance`） |
| `renderer` | 默认 `'humanoid'`；也可为原生 Java renderer 完整类名，仅客户端加载；必须有公开 `(EntityRendererProvider.Context)` 构造器并接受该实体类 |
| `texture` | 默认 `'minecraft:textures/entity/zombie/zombie.png'`；人形模型使用 64×64 纹理，资源包可以覆盖 |
| `shadowRadius` | 阴影半径，默认 0.5；必须为有限非负数 |
| `factory(javaFactory)` | 原生 Java `BiFunction<EntityType, Level, LivingEntity>`；拒绝 JavaScript/Graal 回调，以免 STARTUP Context 被双端不同线程访问 |
| `entityClass(clazz)` | 选择公开、非抽象的原生 `LivingEntity` 类；公开 `(EntityType, Level)` 构造器在注册前校验 |
| `attributeBase(entityType)` | 继承已有原生实体类型的完整默认属性 |
| `attributeSupplier(supplier)` | 指定完整原生属性基线；用于没有 `createAttributes()` 的自定义类 |
| `goals(cb)` | AI 目标（见[GoalBuilder](#wiki-section-12)） |
| `tag(tags...)` | 给实体类型打 tag（如 `'minecraft:raiders'`） |

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

原生实体例（NeoForge/Fabric 26.x）：

```javascript
const Zombie = Java.type('net.minecraft.world.entity.monster.zombie.Zombie')
RegistryEvents.register(event => {
  event.entityType('mymod:native_mob', build => {
    build.entityClass(Zombie)
    build.renderer = 'net.minecraft.client.renderer.entity.ZombieRenderer'
  })
})
```

1.21.1 的僵尸类名是 `net.minecraft.world.entity.monster.Zombie`；renderer 类名不变。构造器在 `entityClass(...)` 配置时解析，缺失、非公开、抽象或不是 `LivingEntity` 的类会立即失败。实体创建后还会检查返回对象的 `EntityType` 必须是本次注册的类型。

原生类的公开静态 `createAttributes()` 会自动提供完整属性基线。没有该方法时必须显式选择 `attributeBase(...)` 或 `attributeSupplier(...)`，避免丢失飞行速度、增援概率等特殊属性。`attributes(...)` **只覆盖回调中显式配置的属性**，未配置的值完整保留基线，不会把原生默认值重置为 NekoJS 的七种常见默认值。默认 `NekoScriptMob` 使用完整 Mob 属性基线；无法推断实体类的 opaque Java factory 也必须具备 Mob 必需属性（包括 `FOLLOW_RANGE`），基线不完整或值非有限数会在注册前拒绝。

原生 Java factory 必须独立于 guest Context，能在服务器线程和客户端线程使用。普通 `factory((type, level) => ...)` 被明确拒绝，推荐使用 `entityClass(...)`。`renderer`、`texture` 与 `shadowRadius` 是可读写 Bean 属性，配置写作 `build.texture = 'mymod:textures/entity/mob.png'`，不是延迟执行的脚本 factory。

`renderer = 'humanoid'` 使用通用人形外观，`texture`/`shadowRadius` 作用于此 renderer。指定原生 renderer 后外观由其原生模型、纹理与阴影逻辑决定；客户端在安装前检查 renderer 的实体泛型，拒绝例如把 `ZombieRenderer` 用到普通 `NekoScriptMob`。声明只保存类名，不在专用服务器加载客户端类。

`GoalEvents.register` 同样适用于原版及本脚本注册的实体。`GoalBuilder.custom(...)` / `customTarget(...)` 只接受原生 Java `Function<Mob, Goal>`，不接受 `mob => goal` guest 闭包；脚本可用 `customClass(...)` / `customTargetClass(...)` 选择满足公开 `(Mob)` 构造器契约的原生 Goal 类，见下方。

**生物蛋版本差异**：NeoForge 1.21.1 颜色**生效**（运行时染色）；26.x 两个加载器的颜色参数不参与运行时染色。NeoForge 自动生成默认蛋模型，Fabric 仅对未提供物品定义的已注册生物蛋回退到原版鸡蛋模型。自定义外观需在资源包提供 `assets/<ns>/items/<path>_spawn_egg.json`，其模型引用相应几何与纹理；仅放入 PNG 不会自动替换默认引用。有效自定义定义和其他插件提供的模型优先，损坏的自定义定义仍保留原生错误，不被回退掩盖。

<a id="wiki-section-8"></a>
## EnchantmentBuilder —— 附魔（仅 NeoForge 1.21.1）

> 26.x 的附魔是数据驱动注册表，不能使用这里的运行时 builder；请使用数据包。Fabric 26.x 当前也没有对应注册面。源码中保留的 builder 不代表 26.x 注册通道可用。

| 成员 | 说明 |
|---|---|
| `supportedItems` | 可附魔的物品标签 id（如 `'minecraft:enchantable/weapon'`，`#` 前缀 tolerated；null=空集合） |
| `weight` / `maxLevel` | 权重（默认 1）/ 最大等级（默认 1） |
| `minCostBase` / `minCostPerLevel` | 最小消耗 `base + perLevel * (level - 1)` |
| `maxCostBase` / `maxCostPerLevel` | 最大消耗曲线（同上） |
| `anvilCost` | 铁砧成本（默认 0） |
| `slots` | `'any'`/`'armor'`/`'chest'`/`'feet'`/`'head'`/`'legs'`/`'hand'`/`'mainhand'`/`'offhand'`（默认 mainhand） |
| `tag(tags...)` | 给附魔打 tag（如 `'minecraft:treasure'`） |

> 默认不挂附魔效果组件（可注册、可附魔/书本/互斥，但无实际效果）；1.21+ 的附魔效果需经 `EnchantmentEffectComponents`，暂不开放。

<a id="wiki-section-9"></a>
## CreativeTabBuilder —— 创造标签页

| 成员 | 说明 |
|---|---|
| `title` | 标签页标题 |
| `icon` | 图标（物品 id 字符串或 ItemStack；null 回退屏障图标） |
| `add(item)` | 添加条目（物品 id 字符串或 ItemStack） |

```javascript
RegistryEvents.register(event => {
  event.creativeModeTab('mymod:cool_tab', b => {
    b.title = '我的模组'
    b.icon = 'mymod:cool_gem'
    b.add('mymod:cool_gem')
    b.add('mymod:custom_block')
  })
})
```

注册后在 `ItemBuilder` 的 `groupTab` 字段里引用该标签页。

<a id="wiki-section-10"></a>
## 简单注册表

```javascript
RegistryEvents.register(event => {
  // 状态效果
  event.mobEffect('mymod:shock', b => { b.category = 'harmful'; b.color = 0x00FF00 })

  // 药水（可含多个效果实例，effect 为追加方法）
  event.potion('mymod:shock_potion', b => {
    b.effect('mymod:shock', 600, 0)                      // (效果, 时长tick, 等级)
    b.effect('minecraft:speed', 3600, 1, false, true)    // (+环境效果, 显示粒子/图标)
  })

  // 声音（fixedRange=null 时由声音定义决定）
  event.soundEvent('mymod:thunder_clap', b => { b.fixedRange = 64 })

  // 粒子类型
  event.particleType('mymod:sparkle', b => { b.overrideLimiter = true })

  // 画作变体（宽高单位像素、16 的倍数；assetId 默认与注册 id 相同）
  event.paintingVariant('mymod:epic_painting', b => {
    b.width = 32; b.height = 16; b.title = '大海'
  })

  // 村民类型（纯标识，无配置）
  event.villagerType('mymod:miner', b => {})

  // 附魔
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
## tag —— 给注册内容打标签

注册时就能预埋 tag：**物品、方块、实体类型、流体、附魔、画作变体**的 builder 都有 `tag(...)` 方法。这是「方块不掉落 / 物品进不了 tag」问题的正解——不用再手写 datapack JSON 或等 `ServerEvents.tags`。

```javascript
RegistryEvents.register(event => {
  event.item('mymod:my_pick', b => {
    b.tag('c:tools/pickaxe', 'minecraft:mineable/pickaxe'.replace('mineable/', 'needs_'))
    // 一次可传多个，也可多次调用
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

规则与语义：

- **参数格式**：`'namespace:name'` 或裸 `'name'`（→ `'minecraft:name'`）；带 `#` 前缀也能识别；一次可传多个。
- **生效时机**：`.tag(...)` 先记成「待写条目」，等 tag 加载阶段（`ServerEvents.tags` 的分发点）自动灌入。**服务器每次启动 / `/reload` 都会重新应用**。
- **与 `ServerEvents.tags` 组合**：待写条目在该事件**之前**注入，脚本监听器里仍可 `add`/`remove` 覆盖。
- **流体的特殊性**：tag 同时打在 source（`<id>`）与 flowing（`flowing_<id>`）两个流体上。
- **方块的 BlockItem**：`block` builder 的 `tag(...)` 打**方块 tag**；给自动 BlockItem 打物品 tag 用 `b.item.tag(...)`。
- **支持范围**：`item` / `block` / `entityType` / `fluid` / `enchantment` / `paintingVariant`；其余注册表无 tag 体系。
- 26.x 的 tag 目录与注册表同名（`data/<ns>/tags/item/...`，单数形式）。

> builder 的 `tag(...)` 目前仅 **NeoForge 26.x** 提供；1.21.1 的 builder 无此方法，用 [事件参考](event-reference_cn) 中的 `ServerEvents.tags` 或 datapack JSON。Fabric 对应注册面仍是子集。

<a id="wiki-section-12"></a>
## GoalBuilder —— AI 目标

`goals(g => ...)` 回调里用的 builder。内置方法提供缺省或显式 `priority` 重载；自定义方法必须传 `priority`（数字越小优先级越高）。

| 方法 | 说明 |
|---|---|
| `floatInWater(priority?)` | 水中漂浮 |
| `randomStroll(speed)` / `randomStroll(priority, speed)` | 随机走动 |
| `meleeAttack(speed, longMemory)` / `meleeAttack(priority, speed, longMemory)` | 近战攻击 |
| `panic(speed)` / `panic(priority, speed)` | 恐慌逃跑 |
| `target(id)` / `target(priority, id)` / `target(priority, id, mustSee)` | 追击最近的目标 |
| `hurtByTarget()` / `hurtByTarget(priority)` | 被攻击后反击 |
| `lookAt(id, radius)` / `lookAt(priority, id, radius)` | 看向附近目标 |
| `avoid(id, radius, speed)` / `avoid(priority, id, radius, speed)` | 逃离附近目标 |
| `custom(priority, javaFactory)` | 原生 Java `Function<Mob, Goal>`，为当前 mob 创建普通 Goal；拒绝 guest 闭包 |
| `customTarget(priority, javaFactory)` | 同上，加入 target selector |
| `customClass(priority, goalClass)` | 公开、非抽象的原生 Goal 类，必须具有公开 `(Mob)` 构造器 |
| `customTargetClass(priority, goalClass)` | 同样的类/构造器契约，加入 target selector |

`target/lookAt/avoid` 的目标接受实体 id 字符串或原生 `LivingEntity` Java 类对象。常用内置映射仅用于裸名称或 `minecraft:` 命名空间；`mymod:zombie` 不会错误地匹配 `minecraft:zombie`。NekoJS 注册实体按完整 id 解析为实际配置的原生类（默认 `NekoScriptMob`）；opaque factory 无法推断类时须显式传 Java 类。建议始终使用完整的 `namespace:id`。

内置方法仍可在脚本 `goals(build => ...)` 中链式配置。自定义 Goal factory 则由永久注册表保存，必须独立于脚本 generation Context，不能写 `custom(priority, mob => ...)`，也不能用捕获 guest Context 的 Java adapter 绕过限制。类入口在配置时校验类和构造器，每个 mob 使用独立构造出的 Goal；仅有 `(PathfinderMob)` 等较窄参数的构造器不符合 `(Mob)` 入口契约。

为原生 mob 追加 Goal 的 join-level 路径使用 weak identity 去重，同一对象重新进入世界不会重复追加，新对象仍独立应用；默认脚本实体在自己的 Goal 初始化路径消费配置。

<a id="wiki-section-13"></a>
## GoalEvents —— 给已有实体追加 AI

不创建新实体，而是给**已有实体类型**（包括原版）加 goal。

```javascript
// startup_scripts/pig_goals.js
GoalEvents.register(event => {
  event.forType('minecraft:pig', goals => {
    goals.panic(0, 2.0).randomStroll(6, 0.8)
  })
})
```

<a id="wiki-section-14"></a>
## 资源与本地化

资源缺失**不影响注册**：物品照样注册出来、能拿在手上，只是显示成紫黑格 + 未翻译的 key。想让它看起来正常，路径由原版资源解析决定（不是 NekoJS 的规定，改不了）：

- **物品/方块材质**：`nekojs/assets/<mod>/textures/item/<name>.png`、`textures/block/<name>.png`。
- **模型 JSON**：`nekojs/assets/<mod>/models/item/<name>.json` 等。
- **语言文件**：`nekojs/assets/<mod>/lang/zh_cn.json`；键名由原版按注册表拼出，物品是 `item.<mod>.<name>`、方块 `block.<mod>.<name>`、实体 `entity.<mod>.<name>`。

<a id="wiki-section-15"></a>
## DynamicRegistry —— 服务器运行中注册（实验性，仅 NeoForge 26.x）

`RegistryEvents.register` 只在游戏启动阶段有效。如果服务器**已经启动**后需要注册新内容（比如按世界脚本包动态添加物品），用 `DynamicRegistry` 绑定——需先在 `nekojs/config/engine.toml` 打开 `[dynamicRegistry] enabled = true`：

```javascript
// server_scripts/dyn.js
// 必须等服务器启动后再调用；顶层调用会发生在 server registry access 建立之前。
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

`DynamicRegistry` 的 builder 仍是链式方法风格（`mode('world' | 'reloadable')` 等）。重载语义（stale-retain）：`/nekojs reload` **从不注销**已注册条目，脚本重跑时按 id 认领；用 `/nekojs registry` 查看健康快照。

<a id="wiki-section-16"></a>
## 从旧写法迁移

现在 `RegistryEvents` 只有 `register` 一个入口，builder 的数据属性也全部改成直接给 public field 赋值：

| 旧写法 | 新写法 |
|---|---|
| `RegistryEvents.item(e => e.create(id, b => ...))` | `RegistryEvents.register(e => e.item(id, b => ...))` |
| `RegistryEvents.block(...)` / `.fluid(...)` / 等每个注册表一个入口 | 同上，`event.<registry>(id, b => ...)` |
| `builder.maxStackSize(16)` | `b.maxStackSize = 16` |
| `builder.fireResistant()` / `.glowing()` | `b.fireResistant = true` / `b.glowing = true` |
| `builder.rarity('rare')` | `b.rarity = 'rare'` |
| `builder.group('tab_id')` | `b.groupTab = 'tab_id'` |
| `builder.hardness(3).resistance(6)` | `b.hardness = 3` `b.resistance = 6` |
| `builder.sound('wood')` / `.mapColor('dirt')` | `b.sound = 'wood'` / `b.mapColor = 'dirt'` |
| `builder.requiresTool()` / `.renderType('translucent')` | `b.requiresTool = true` / `b.renderType = 'translucent'` |
| `builder.item(cb)`（配置自动 BlockItem） | `b.item.xxx = ...` 直改，或仍可用 `b.item(cb)` |
| `fluid.bucket(false)` / `.block(false)` | `b.noBucket()` / `b.noBlock()`（或 `b.bucket = false` / `b.block = false`） |
| `entity.category('monster')` / `.fireImmune()` | `b.category = 'monster'` / `b.fireImmune = true` |
| `enchantment.minCost(5, 5)` / `.maxCost(20, 5)` | `b.minCostBase = 5` `b.minCostPerLevel = 5` / `b.maxCostBase = 20` `b.maxCostPerLevel = 5` |
| `enchantment.supportedItems('#...')` / `.slots('mainhand')` | `b.supportedItems = '...'` / `b.slots = 'mainhand'` |
| `sound.fixedRange(64)` | `b.fixedRange = 64` |
| `particle.overrideLimiter(true)` | `b.overrideLimiter = true` |
| `painting.width(2).height(2).assetId(...)` | `b.width = 2` `b.height = 2` `b.assetId = '...'` |
| `effect.category('harmful').color(...)` | `b.category = 'harmful'` `b.color = ...` |
| 保留不变：`b.food(cb)`、`b.tag(...)`、`b.unbreakable()`、`b.noItem()`、`b.size(w,h)`、`b.spawnEgg(a,b)`、`b.attributes(cb)`、`b.goals(cb)`、`potion.effect(...)` | —— 动作/复合配置仍是方法 |

<a id="wiki-section-17"></a>
## 平台差异

- **NeoForge 1.21.1**：支持本页的大部分 builder；附魔 builder 仅在此版本的运行时注册面可用。
- **NeoForge 26.x**：支持动态注册 builder 的主要面，但附魔改为数据驱动注册，不能使用 `event.enchantment(...)`。
- **Fabric 26.x**：支持注册 builder 的子集；实体类型 builder 使用可见的人形模型/64×64 僵尸纹理，可配置原生 renderer；GoalBuilder 支持内置脚本配置和原生 factory/类自定义入口。`Capabilities` / `CapabilityEvents` 已接入真实 Fabric Lookup/Transfer，不等同于 NeoForge 原生类型或标准跨模组能量 API。FluidBuilder、VillagerTrades、附魔和若干平台专属注册面仍不可用。详见 [平台与兼容性](platform-compatibility_cn)。
- Cleanroom 1.12.2 使用独立 legacy 分支的旧管线；当前仓库不验证本页写法在该分支的可用性。

<a id="wiki-section-18"></a>
## 下一步

- [事件参考](event-reference_cn) —— `RegistryEvents` / `GoalEvents`。
- [快速开始](quick-start_cn)

<!-- wiki-nav -->

---

[上一篇: 配方系统](recipe-system_cn) · [目录](Home) · [下一篇: 模块系统](module-system_cn)
