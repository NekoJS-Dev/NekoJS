<!-- wiki-page: event-reference; locale: cn -->

> **中文** · [English](event-reference_us)

<a id="wiki-section-1"></a>
# 事件参考

本页列出 NekoJS 的所有事件组：每个事件什么时候触发、写在哪种脚本里、能不能取消、各平台是否可用。第一次写事件，先看 [脚本基础](script-basics_cn)。

<a id="wiki-section-2"></a>
## 怎么监听事件

在脚本的**顶层**调用事件组上的方法，传入一个回调函数：

```javascript
// server_scripts/welcome.js
PlayerEvents.loggedIn(event => {
  console.info('有玩家进入了服务器')
})
```

改完后执行对应的重载命令（服务端脚本用 `/nekojs reload`），触发一次事件，再看日志或 `/nekojs error` 确认回调跑了。

监听必须写在顶层。在一个事件的回调里再注册另一个事件会报错。

回调里的 `event` 是事件对象。读取信息用属性写法，例如 `event.player`、`event.entity`；也可以调用对应的方法，例如 `event.getPlayer()`。一个属性能读不代表能赋值。不确定有哪些成员时，执行 `/nekojs probe` 生成补全，在编辑器里查看。

<a id="wiki-section-3"></a>
### 只监听某个物品、方块或实体

很多事件可以按 id 过滤，下文表格里标着“按 id”。把 id 写在回调前面，就只在这个对象上触发：

```javascript
// 只在挖钻石矿时触发
BlockEvents.broken('minecraft:diamond_ore', event => {
  console.info('钻石矿被挖了')
})

// 不写 id，所有方块都会触发
BlockEvents.broken(event => {
  console.info('有方块被挖了')
})
```

过滤用的 id 按事件不同，分别是物品 id、方块 id、实体类型 id，或其他字符串（如网络频道名、按键 id）。

<a id="wiki-section-4"></a>
### 优先级

多个回调监听同一个事件时，可以在最前面加优先级：`HIGHEST`、`HIGH`、`NORMAL`（默认）、`LOW`、`LOWEST`，不区分大小写。

```javascript
PlayerEvents.loggedIn('HIGH', event => { /* 先执行 */ })

// 按 id 过滤的事件：优先级、id、回调三个参数都要写
BlockEvents.broken('HIGH', 'minecraft:stone', event => { /* ... */ })
```

注意：按 id 过滤的事件只传两个参数时，第一个参数会被当成 id。`BlockEvents.broken('HIGH', event => {})` 监听的是 id 为 `HIGH` 的方块，不是高优先级。

<a id="wiki-section-5"></a>
### 取消事件

可以取消的事件，在回调里 `return true` 就会取消。例如阻止玩家挖石头：

```javascript
BlockEvents.broken('minecraft:stone', event => {
  return true
})
```

只有标着“可取消”的事件才有效。不能取消的事件，返回 `true` 不会有任何效果，也不会报错。各平台能取消的事件不完全一样，以下文表格为准。

<a id="wiki-section-6"></a>
## 各平台能用哪些事件组

NeoForge 上所有事件组都可用。Fabric 26.x 支持大部分常用事件，但部分事件组只有一部分事件，有些还没有移植。

| 事件组 | 用在哪种脚本 | NeoForge | Fabric 26.x |
|---|---|---|---|
| `ServerEvents` | 服务端 | 全部 | 没有 `lootTables`、`tags`、`generateData`；交易事件可以监听但不生效 |
| `PlayerEvents` | 服务端 | 全部 | 全部 |
| `EntityEvents` | 服务端 | 全部 | 没有 `useItem*` 系列 |
| `BlockEvents` | 服务端 | 全部（`modification` 仅 26.x） | 没有 `entityMultiPlaced`、`toolModification` |
| `ItemEvents` | 服务端；`tooltip` 在客户端 | 全部 | 全部 |
| `LevelEvents` | 服务端 | 全部 | 没有旧名 `tick` |
| `NetworkEvents` | 服务端 / 客户端 | 有 | 有 |
| `RegistryEvents`、`GoalEvents` | 启动 | 有 | 有 |
| `CapabilityEvents` | 启动 | 有 | 有，方法不同 |
| `CommandEvents` | 服务端 | `register`、`command` | 只有 `register` |
| `KeyBindEvents` | 客户端 | 仅 26.x | 有 |
| `ClientEvents` | 客户端 | 全部 | 只有 `tickPre`、`tickPost`、`tick`、`hud`、`screenRender` |
| `RecipeViewerEvents` | 客户端 | 需要安装 JEI | 没有 |
| `ProbeEvents` | 服务端 | 有 | 没有 |
| `ScriptEvents` | 启动 | 有 | 有 |

Fabric 上的事件对象和 NeoForge 不同，成员名可能不一样。下文遇到这种情况会单独说明。完整的平台差异见 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-server-events"></a>
<a id="wiki-section-7"></a>
## ServerEvents

服务端生命周期、tick 和数据加载相关的事件。写在 `server_scripts` 里。

| 事件 | 什么时候触发 | 说明 |
|---|---|---|
| `recipes` | 加载配方时 | **修改配方**，最常用。见 [配方系统](recipe-system_cn) |
| `afterRecipes` | 配方全部加载完成后 | 只适合查询。这里改配方不会生效，改配方请用 `recipes` |
| `tickPre` / `tickPost` | 每个服务端 tick 之前 / 之后 | 每秒 20 次，逻辑要轻 |
| `aboutToStart` / `starting` | 服务端启动过程中 | |
| `started` | 服务端启动完成 | 注册表已就绪，可以放心用 `Item.of(...)` |
| `stopping` / `stopped` | 服务端停止中 / 已停止 | |
| `datapackSync` | 向玩家同步数据包时 | |
| `tagsUpdated` | 标签重新加载完成 | |
| `lootTableLoad` | 每张战利品表加载时 | NeoForge 上可取消 |
| `lootTables` | 数据重载时 | **按 id 读写战利品表**，仅 NeoForge，见下文 |
| `tags` | 加载标签时 | **修改标签**，按注册表 id 过滤，仅 NeoForge，见下文 |
| `generateData` | 数据重载时 | **生成数据包文件**，按阶段过滤（目前只有 `'after_mods'`），仅 NeoForge |
| `tradeDeclaration` / `tradeReload` | 启动和 reload 时 | 村民交易，仅 NeoForge 生效 |

```javascript
// server_scripts/lifecycle.js
ServerEvents.started(event => {
  console.info('服务端启动完成')
})
```

<a id="wiki-section-8"></a>
### 战利品表：ServerEvents.lootTables

仅 NeoForge。每次数据重载（进入世界、`/reload`）时触发，修改在这次重载里立刻生效。

| 方法 | 说明 |
|---|---|
| `getJson(id)` | 读取战利品表当前的 JSON；不存在时返回 `null` |
| `setJson(id, json)` | 整张替换。`json` 可以是 JS 对象或 JSON 字符串 |
| `create(id, json)` | 新建一张表（id 可以原本不存在） |
| `remove(id)` | 删除一张表，之后读到的是空表 |
| `getIds()` | 所有战利品表 id，包括脚本新建的 |
| `modify(id, table => {})` | 用回调修改一张表 |
| `modifyBlockLoot(filter, table => {})` | 批量修改方块掉落。`filter` 可以是方块 id、`'#标签'`、`'*'` 或一个返回 true/false 的函数 |
| `modifyEntityLoot(filter, table => {})` | 批量修改实体掉落。`filter` 可以是实体 id、`'*'` 或函数 |

`modify` 系列回调拿到的 `table` 有这些方法：

- 表：`getType()` / `setType(type)`、`getPools()`、`addPool(pool => {})` 或 `addPool(完整的 pool JSON)`。
- 池：`rolls(次数)` 或 `rolls([最少, 最多])`、`addEntry(...)`、`getEntries()`、`when(条件 JSON)`。
- 条目：`item(id)`、`weight(权重)`、`when(条件 JSON)`。`addEntry` 也可以直接传物品 id 字符串。

```javascript
// server_scripts/loot.js
ServerEvents.lootTables(event => {
  // 直接改 JSON：给地牢箱子加一颗钻石
  let json = event.getJson('minecraft:chests/simple_dungeon')
  json.pools.push({
    rolls: 1,
    entries: [{ type: 'minecraft:item', name: 'minecraft:diamond' }]
  })
  event.setJson('minecraft:chests/simple_dungeon', json)

  // 用回调：钓鱼有一半概率额外钓到幸运币
  event.modify('minecraft:gameplay/fishing', table => {
    table.addPool(pool => {
      pool.rolls(1).addEntry(entry => entry
        .item('mymod:lucky_coin')
        .weight(5)
        .when({ condition: 'minecraft:random_chance', chance: 0.5 })
      )
    })
  })

  // 所有原木额外掉一个苹果
  event.modifyBlockLoot('#minecraft:logs', table => {
    table.addPool(pool => pool.rolls(1).addEntry('minecraft:apple'))
  })

  // 所有实体有 5% 概率掉一根羽毛
  event.modifyEntityLoot('*', table => {
    table.addPool(pool => pool.rolls(1).addEntry(
      entry => entry.item('minecraft:feather').when({ condition: 'minecraft:random_chance', chance: 0.05 })
    ))
  })

  // 新建一张表
  event.create('mymod:chests/treasure', {
    type: 'minecraft:chest',
    pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'minecraft:emerald' }] }]
  })

  // 删除一张表
  event.remove('minecraft:chests/jungle_temple')
})
```

用 `'*'` 或函数批量修改时，只会处理已经有战利品表的方块和实体。写精确 id 或 `#标签` 不受这个限制。

<a id="wiki-section-9"></a>
### 全局战利品修改器

全局战利品修改器（Global Loot Modifier）会作用于**每一次掉落**，而不是替换整张表。它没有对应的事件，要用 `generateData` 写数据包文件（仅 NeoForge）：

```javascript
// server_scripts/modifiers.js
ServerEvents.generateData('after_mods', event => {
  // 1) 把修改器加入启用列表。用 replace: false，不要覆盖其他模组的列表
  event.json('neoforge/loot_modifiers/global_loot_modifiers.json', {
    replace: false,
    entries: ['mymod:add_fishing_loot']
  })
  // 2) 修改器本身：每次掉落时额外掷一次钓鱼战利品表
  event.json('mymod/loot_modifiers/add_fishing_loot.json', {
    type: 'neoforge:add_loot_table',
    conditions: [],
    loot_table: 'minecraft:gameplay/fishing'
  })
})
```

文件写在 `nekojs/data` 下，路径相对于这个目录。`neoforge:add_loot_table` 是 NeoForge 自带的修改器类型；更复杂的修改器需要用 Java 编写。

<a id="wiki-section-10"></a>
### 标签：ServerEvents.tags

仅 NeoForge。按注册表 id 过滤，例如 `'minecraft:item'`、`'minecraft:block'`。

| 方法 | 说明 |
|---|---|
| `add(tag, id)` | 把一个条目加进标签 |
| `remove(tag, id)` | 从标签里移除一个条目 |
| `removeAll(tag)` | 清空标签 |
| `replaceAll(tag, ...ids)` | 用给定条目替换标签的全部内容 |
| `getEntries(tag)` | 读取标签当前的条目 |

```javascript
// server_scripts/tags.js
ServerEvents.tags('minecraft:item', event => {
  event.add('minecraft:logs', 'mymod:custom_log')
  event.remove('minecraft:logs', 'minecraft:birch_log')
  event.replaceAll('minecraft:planks', 'minecraft:oak_planks')
})

ServerEvents.tags('minecraft:block', event => {
  // 方块标签
})
```

在 26.x 上，注册新物品时用 `.tag(...)` 声明的标签，在这个事件里已经能读到。

<a id="wiki-section-11"></a>
## PlayerEvents

玩家相关的事件。写在 `server_scripts` 里。

| 事件 | 按 id 过滤 | 可取消 | 说明 |
|---|---|---|---|
| `loggedIn` / `loggedOut` | — | — | 玩家进入 / 离开服务器 |
| `chat` | — | NeoForge 可取消 | 玩家发送聊天消息。`event.message`、`event.username` 是字符串 |
| `tickPre` / `tickPost` | — | — | 每个玩家每 tick 一次 |
| `respawned` | — | — | 玩家重生 |
| `cloned` | — | — | 死亡重生或从末地返回时复制玩家数据 |
| `changedDimension` | — | — | 切换维度 |
| `advancement` | — | — | 获得进度 |
| `containerOpened` / `containerClosed` | — | — | 打开 / 关闭容器界面 |
| `entityInteract` | — | 可取消 | 玩家右键实体 |
| `crafted` | 物品 id | — | 合成出某物品 |
| `smelted` | 物品 id | — | 从熔炉取出烧好的物品 |
| `destroyed` | 物品 id | — | 物品耐久耗尽损坏 |
| `inventoryChanged` | 物品 id | — | 物品栏里某一格变化。`event.item` 是物品栈，`event.slot` 是格子编号 |

```javascript
// server_scripts/players.js
PlayerEvents.loggedIn(event => {
  // NeoForge 上玩家是 event.entity，Fabric 上是 event.player
  const player = event.entity ?? event.player
  player.displayClientMessage(Text.of('欢迎，' + player.getName().getString() + '！'), false)
})

PlayerEvents.crafted('minecraft:stick', event => {
  console.info('有人合成了木棍')
})

PlayerEvents.inventoryChanged('minecraft:diamond', event => {
  console.info('物品栏第 ' + event.slot + ' 格放进了钻石')
})
```

旧名 `inventoryOpened`、`inventoryClosed` 仍可使用，新脚本请用 `containerOpened`、`containerClosed`。

<a id="wiki-section-12"></a>
## EntityEvents

实体相关的事件。写在 `server_scripts` 里。大部分事件可以按实体类型 id 过滤，例如 `'minecraft:zombie'`。

| 事件 | 按 id 过滤 | 可取消 | 说明 |
|---|---|---|---|
| `damagePre` | 实体 id | 可取消 | 实体即将受到伤害，见下文 |
| `damagePost` | 实体 id | — | 实体受到伤害之后 |
| `death` | 实体 id | NeoForge 可取消 | 实体死亡 |
| `drops` | 实体 id | 可取消 | 实体死亡掉落物品。取消后不掉落 |
| `finalizeSpawn` | 实体 id | NeoForge 可取消 | 生物自然生成或刷怪笼生成时 |
| `joinLevel` / `leaveLevel` | 实体 id | `joinLevel` 在 NeoForge 可取消 | 实体进入 / 离开世界 |
| `tickPre` / `tickPost` | 实体 id | `tickPre` 在 NeoForge 可取消 | 实体每 tick 一次。实体很多，逻辑要轻 |
| `useItemStarted` / `useItemTick` / `useItemStopped` / `useItemFinished` | 物品 id | 前三个可取消 | 开始使用 / 使用中每 tick / 中途松开 / 使用完成。仅 NeoForge |

```javascript
// server_scripts/entities.js
EntityEvents.death('minecraft:zombie', event => {
  console.info('一只僵尸死在了 ' + event.entity.blockPosition())
})

// 玩家受到的伤害减半（NeoForge）
EntityEvents.damagePre('minecraft:player', event => {
  event.setNewDamage(event.getNewDamage() / 2)
})
```

取消 `damagePre` 的效果在两个平台上不同：

- **NeoForge**：伤害变成 0，`damagePost` 仍然会以 0 伤害触发。也可以用 `event.setNewDamage(数值)` 改伤害。
- **Fabric**：这次伤害完全不发生。Fabric 上不能修改伤害数值，只能取消。

<a id="wiki-section-13"></a>
## BlockEvents

方块相关的事件。写在 `server_scripts` 里。大部分事件按方块 id 过滤。

| 事件 | 可取消 | 说明 |
|---|---|---|
| `broken` | 可取消 | 玩家破坏方块 |
| `placed` / `entityPlaced` | 可取消 | 方块被放置（两个名字是同一事件） |
| `entityMultiPlaced` | 可取消 | 一次放置多个方块，如床、门。仅 NeoForge |
| `rightClicked` / `leftClicked` | 可取消 | 玩家右键 / 左键方块 |
| `neighborNotify` | 可取消 | 方块通知周围方块更新 |
| `fluidPlaced` | 可取消 | 流体生成方块，如岩浆遇水生成圆石 |
| `farmlandTrample` | 可取消 | 耕地被踩坏 |
| `portalSpawn` | 可取消 | 下界传送门生成 |
| `toolModification` | 可取消 | 用工具改变方块，如斧头去皮、锄头耕地。仅 NeoForge |
| `randomTick` | — | 方块随机刻。只对会随机刻的方块触发，例如小麦、草方块；石头不会触发 |
| `blockEntityTick` | — | 方块实体每 tick 一次。按**方块实体类型** id 过滤，例如 `'minecraft:furnace'` |
| `modification` | — | **修改方块属性**，见下文。不按 id 过滤 |

```javascript
// server_scripts/blocks.js
BlockEvents.broken('minecraft:diamond_ore', event => {
  console.info(event.player.getName().getString() + ' 挖到了钻石矿')
})

// 禁止踩坏耕地
BlockEvents.farmlandTrample(event => true)

BlockEvents.randomTick('minecraft:wheat', event => {
  // event.level、event.pos、event.state、event.random
})

BlockEvents.blockEntityTick('minecraft:furnace', event => {
  const furnace = event.blockEntity
})
```

<a id="wiki-section-14"></a>
### 修改方块属性：BlockEvents.modification

修改已有方块的硬度、亮度等属性。支持 NeoForge 26.x 和 Fabric 26.x；1.21.1 不支持。

| 属性 | 类型 | 说明 |
|---|---|---|
| `hardness` | 不小于 0 的数 | 硬度，决定挖掘时间。石头 1.5，黑曜石 50 |
| `resistance` | 不小于 0 的数 | 爆炸抗性。基岩级别用 `3600000` |
| `lightLevel` | 0 到 15 的整数 | 发光亮度。萤石是 15 |
| `requiresTool` | true / false | 是否必须用正确的工具挖才会掉落 |
| `friction` | 0 到 1 的数 | 摩擦力。冰 0.98，普通方块 0.6 |
| `jumpFactor` | 数 | 跳跃高度系数。原版方块在 0.5 到 1 之间 |

```javascript
// server_scripts/block_mods.js
BlockEvents.modification(event => {
  event.modify('minecraft:stone', block => {
    block.hardness = 2
    block.lightLevel = 15     // 会发光的石头
    block.requiresTool = false
    block.friction = 0.8      // 有点滑
  })

  // 先读当前值，再改
  event.modify('minecraft:obsidian', block => {
    block.hardness = block.hardness / 2
  })
})
```

修改在服务器启动时生效，每次 `/nekojs reload` 都会按当前脚本重新应用一遍。所以删掉某条 `modify` 后 reload，这个方块就会恢复原样。reload 失败时，上一次的修改继续保留。

修改会同步到已经连接的客户端，之后进入的玩家也会收到。已加载区块的光照可能不会马上刷新，重新加载区块即可。

<a id="wiki-item-events"></a>
<a id="wiki-section-15"></a>
## ItemEvents

物品相关的事件。除 `tooltip` 外都写在 `server_scripts` 里。大部分事件按物品 id 过滤。

| 事件 | 脚本 | 可取消 | 说明 |
|---|---|---|---|
| `rightClicked` | 服务端 | 可取消 | 玩家手持物品右键 |
| `entityInteracted` | 服务端 | 可取消 | 玩家手持物品右键实体 |
| `canPickUp` | 服务端 | 见下文 | 玩家即将捡起物品 |
| `pickedUp` | 服务端 | — | 玩家捡起物品之后 |
| `dropped` | 服务端 | NeoForge 可取消 | 玩家丢出物品 |
| `foodEaten` | 服务端 | — | 食物吃完，或盾、弓等物品使用完成 |
| `modification` | 服务端 | — | **修改物品属性**，见下文。不按 id 过滤 |
| `tooltip` | 客户端 | — | **修改物品提示文字**，见下文 |

```javascript
// server_scripts/items.js
ItemEvents.rightClicked('minecraft:stick', event => {
  event.player.displayClientMessage(Text.of('你挥了挥木棍'), false)
})

ItemEvents.foodEaten('minecraft:golden_apple', event => {
  console.info('有人吃了金苹果')
})
```

阻止捡起物品时，两个平台的写法不同：

- **NeoForge**：调用 `event.setCanPickup(TriState.FALSE)`。这里返回 `true` 没有效果。
- **Fabric**：在回调里 `return true`。

旧名 `pickedUpPre` 等同于 `canPickUp`，新脚本请用 `canPickUp`。

<a id="wiki-section-16"></a>
### 修改物品属性：ItemEvents.modification

修改已有物品的堆叠数量、稀有度、食物属性等。支持 NeoForge 1.21.1、NeoForge 26.x 和 Fabric 26.x。生效时机和 `BlockEvents.modification` 一样：启动时应用，每次 `/nekojs reload` 重新应用，删掉的修改会在下次 reload 后恢复。

| 属性 | 说明 |
|---|---|
| `maxStackSize` | 最大堆叠数，1 到 99 |
| `maxDamage` | 耐久值。大于 0 时 `maxStackSize` 不能大于 1 |
| `rarity` | 稀有度：`'common'`、`'uncommon'`、`'rare'`、`'epic'` |
| `fireResistant` | 是否不会被火烧毁 |
| `food` | 食物属性，设为 `null` 表示不能再吃 |
| `attackDamage` / `attackSpeed` | 主手攻击伤害 / 攻击速度的加成值 |
| `tool` | 让物品能当工具挖所有方块，设为 `null` 表示移除 |

```javascript
// server_scripts/item_mods.js
ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 16
    item.rarity = 'epic'
    item.fireResistant = true
  })

  // 让木棍可以吃。各项都可以省略：nutrition 默认 1，saturation 默认 0.1
  event.modify('minecraft:stick', item => {
    item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }
  })

  // 铁锭当剑用：总伤害 = 2 + 6，总攻速 = 4 - 2.4 = 1.6
  event.modify('minecraft:iron_ingot', item => {
    item.attackDamage = 6
    item.attackSpeed = -2.4
  })

  // 烈焰棒当万能工具：按速度 6 挖掘所有方块，并且都能掉落
  event.modify('minecraft:blaze_rod', item => {
    item.tool = { miningSpeed: 6 }
  })
})
```

几点说明：

- `saturation` 是系数，和 KubeJS 一样。实际恢复的饱和度 = `nutrition` × `saturation` × 2。
- 原本不能吃的物品设置 `food` 后，会自动获得进食动作，默认吃 1.6 秒。给了 `eatSeconds` 就用你给的时长。
- `attackDamage` 和 `attackSpeed` 是加在玩家基础值上的数：基础攻击伤害 2，基础攻速 4。只设其中一个也可以，物品的其他属性加成不受影响。
- `tool` 里 `miningSpeed` 必填，`damagePerBlock` 默认 1，`canDestroyBlocksInCreative` 默认 true。26.x 的物品上不能设挖掘等级。

<a id="wiki-section-17"></a>
### 物品提示：ItemEvents.tooltip

写在 `client_scripts` 里，按物品 id 过滤。改完用 `/nekojs reload client`。

```javascript
// client_scripts/tooltips.js
ItemEvents.tooltip('minecraft:diamond', event => {
  // NeoForge 用 event.toolTip，Fabric 用 event.lines
  const lines = event.toolTip ?? event.lines
  lines.add(Text.of('闪闪发光').gold())
})
```

<a id="wiki-section-18"></a>
## LevelEvents

世界（维度）相关的事件。写在 `server_scripts` 里。

| 事件 | 可取消 | 说明 |
|---|---|---|
| `loaded` / `unloaded` / `saved` | — | 世界加载 / 卸载 / 保存 |
| `tickPre` / `tickPost` | — | 每个世界每 tick 一次 |
| `explosionStart` | 可取消 | 爆炸即将发生。取消后不会爆炸 |
| `explosionDetonate` | — | 爆炸已经算出影响范围，即将破坏方块和伤害实体 |

旧名 `beforeExplosion`、`afterExplosion`、`tick` 仍可使用，新脚本请用 `explosionStart`、`explosionDetonate`、`tickPost`。Fabric 没有 `tick`。

<a id="wiki-section-19"></a>
## NetworkEvents

让服务端脚本和客户端脚本互相发消息。按频道名过滤，频道名是你自己起的字符串。发送用 [Network](global-bindings_cn#wiki-network)。

| 事件 | 脚本 | 说明 |
|---|---|---|
| `server` | 服务端 | 收到客户端发来的消息。`event.channel`、`event.data`、`event.player` |
| `client` | 客户端 | 收到服务端发来的消息。`event.channel`、`event.data` |

下面的例子是：客户端发请求，服务端处理后回复。

```javascript
// client_scripts/menu.js
NetworkEvents.client('my_channel', event => {
  console.info('服务端回复：', event.data)
})

// 在按键等客户端事件里发送：
// Network.sendToServer('my_channel', { action: 'open_menu' })
```

```javascript
// server_scripts/menu.js
NetworkEvents.server('my_channel', event => {
  console.info(event.player.getName().getString() + ' 发来：', event.data)
  Network.sendToPlayer(event.player, 'my_channel', { result: 'ok' })
})
```

`data` 是 NBT 对象。发送时直接写普通 JS 对象，会自动转换。服务端收到的是客户端发来的数据，使用前要自己检查内容是否合理。

<a id="wiki-section-20"></a>
## RegistryEvents

注册新物品、方块、实体等内容。写在 `startup_scripts` 里，改完要**重启游戏**。完整写法见 [注册新内容](registering-new-content_cn)。

| 事件 | 说明 |
|---|---|
| `register` | 在回调里用 `event.item(id, b => {})`、`event.block(...)`、`event.entityType(...)` 等方法注册内容 |

```javascript
// startup_scripts/items.js
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', item => {
    item.maxStackSize = 16
  })
})
```

可用的注册方法随平台和版本变化。例如 `particleType`、`paintingVariant` 仅 NeoForge；26.x 上附魔改由数据包定义，不能用脚本注册。

<a id="wiki-section-21"></a>
## GoalEvents

给已有的生物添加 AI 行为。写在 `startup_scripts` 里。详见 [注册新内容](registering-new-content_cn)。

| 事件 | 说明 |
|---|---|
| `register` | 用 `event.forType(实体 id, goals => {})` 给某种生物添加 AI 目标 |

<a id="wiki-capability-events"></a>
<a id="wiki-section-22"></a>
## CapabilityEvents

让方块、实体或物品对外提供物品栏、能量、流体存储，其他模组（如管道、电线）就能和它互动。写在 `startup_scripts` 里，只在启动时注册一次，改了要**重启游戏**。存储对象用 [Capabilities](global-bindings_cn#wiki-capabilities) 创建。

能力名用 `'item'`、`'energy'`、`'fluid'`。

<a id="wiki-section-23"></a>
### NeoForge

| 方法 | provider 收到的参数 |
|---|---|
| `registerBlock(方块 id, 能力, provider)` | `(level, pos, state, blockEntity, direction)`，`blockEntity` 可能是 `null` |
| `registerBlockEntityContext(方块实体类型 id, 能力, provider)` | `(blockEntity, direction)` |
| `registerBlockEntity(方块实体类型 id, 能力, provider)` | 无参数。旧写法，每次查询都会调用 |
| `registerEntity(实体 id, 能力, provider)` | `(entity, direction)`。实体还支持能力名 `'item_automation'` |
| `registerItem(物品 id, 能力, provider)` | `(stack, context)` |
| `registerBlockNative` / `registerBlockEntityNative` / `registerEntityNative` / `registerItemNative` | 参数同上，但第二个参数传其他模组的原生能力对象 |

```javascript
// startup_scripts/capabilities.js
// 'mymod:battery' 换成你注册的方块实体类型
// 每个方块实体按位置分配自己的能量存储（这个例子不会把能量存进存档）
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

使用 `Native` 系列方法时，其他模组的能力对象可以用 `Java.type(...)` 从那个模组的类里取得，NekoJS 不需要依赖那个模组。

物品能力的 `context` 在 1.21.1 上是 `null`，在 26.x 上是原生的 `ItemAccess`，不是方向。

<a id="wiki-section-24"></a>
### Fabric

Fabric 上的方法名不同，底层使用 Fabric 的 Lookup API：

| 方法 | provider 收到的参数 |
|---|---|
| `registerBlockEntityContext(方块实体类型 id, 能力, provider)` | `(blockEntity, direction)` |
| `registerEntityContext(实体 id, 能力, provider)` | `(entity, direction)` |
| `registerItemContext(物品 id, 能力, provider)` | `(stack, containerItemContext)` |
| `registerBlockEntity` / `registerEntity` / `registerItem` | 无参数。旧写法，每次查询都会调用 |
| `registerBlockLookup(方块 id, lookup, provider)` | `(level, pos, state, blockEntity, context)` |
| `registerBlockEntityLookup(方块实体类型 id, lookup, provider)` | `(blockEntity, context)` |
| `registerEntityLookup(实体 id, lookup, provider)` | `(entity, context)` |
| `registerItemLookup(物品 id, lookup, provider)` | `(stack, context)` |

`...Lookup` 系列用来接入其他模组自定义的 Lookup。Fabric 上物品和流体走 Fabric Transfer API，能和其他 Fabric 模组互通；能量用 NekoJS 自己的接口，不能直接和其他模组的能量系统互通。

<a id="wiki-section-25"></a>
### 写 provider 时要注意

- **每个对象返回它自己的存储。** 不要在每次查询时新建一个空存储，也不要让同类的所有对象共用一个存储。
- 不想对这次查询提供能力时，返回 `null`。
- 注册只负责“让别人找到存储”。存储内容的保存、同步要自己处理。
- 方块的能力发生变化（比如从有到无）时，NeoForge 上要调用 `level.invalidateCapabilities(pos)` 通知缓存。
- id 不存在、能力名写错、重复注册、provider 报错或返回了错误类型，都会报带 `NEKO-` 代码的错误。

<a id="wiki-command-events"></a>
<a id="wiki-section-26"></a>
## CommandEvents

注册自定义命令。写在 `server_scripts` 里。

| 事件 | 可取消 | 说明 |
|---|---|---|
| `register` | — | 注册命令。`event.dispatcher` 是原版的命令注册器 |
| `command` | 可取消 | 有命令即将执行时。取消后命令不执行。仅 NeoForge |

```javascript
// server_scripts/commands.js
const Commands = Java.type('net.minecraft.commands.Commands')

CommandEvents.register(event => {
  event.dispatcher.register(
    Commands.literal('hello')
      .executes(ctx => {
        ctx.getSource().sendSuccess(() => Text.of('你好！'), false)
        return 1
      })
  )
})
```

命令用原版的 Brigadier 写法：`Commands.literal(...)` 定义命令名，`.executes(...)` 定义执行内容，返回 `1` 表示成功。改完执行 `/nekojs reload`，新命令就能用了。

`/nekojs` 命令本身由 NekoJS 注册，不需要脚本处理。

<a id="wiki-keybind-events"></a>
<a id="wiki-section-27"></a>
## KeyBindEvents

添加可以在“按键设置”里修改的自定义按键。写在 `client_scripts` 里。支持 NeoForge 26.x 和 Fabric 26.x，1.21.1 没有。

| 事件 | 说明 |
|---|---|
| `register(id, 按键[, 分类])` | 注册一个按键 |
| `pressed` | 按下的那一刻触发一次 |
| `released` | 松开的那一刻触发一次 |
| `tick` | 按住期间每 tick 触发一次 |

`pressed`、`released`、`tick` 都可以按按键 id 过滤。

```javascript
// client_scripts/keybinds.js
// id 不写命名空间时默认是 nekojs；按键名如 'key.keyboard.g'、'key.mouse.left'
// 分类可以用原版的（如 'movement'），也可以自己起（如 'mymod:my_keys'）
KeyBindEvents.register('mymod:special', 'key.keyboard.g', 'mymod:my_keys')

KeyBindEvents.pressed('mymod:special', event => {
  console.info('按下了 ' + event.id)
  // 可以在这里用 Network.sendToServer(...) 通知服务端
})

KeyBindEvents.released('mymod:special', event => { /* ... */ })

KeyBindEvents.tick('mymod:special', event => {
  // 按住期间每 tick 执行
})
```

- 事件对象有 `event.id`（完整 id）、`event.keyMapping`（原版按键对象）和 `event.isDown()`（是否按着）。
- `register` 会返回原版的按键对象，也可以自己轮询它的 `isDown()`、`consumeClick()`。
- 同一个 id 重复注册不会产生重复的按键，所以 `/nekojs reload client` 是安全的。
- 按键在设置界面显示的名字用翻译键 `key.<命名空间>.<路径>`，自定义分类用 `key.category.<命名空间>.<路径>`。可以用 `ClientEvents.lang` 或资源包补上翻译。

<a id="wiki-section-28"></a>
## ClientEvents

客户端的 tick、画面绘制和资源生成等事件。写在 `client_scripts` 里，改完用 `/nekojs reload client`。

| 事件 | 平台 | 说明 |
|---|---|---|
| `tickPre` / `tickPost` | 全部 | 客户端每 tick 一次。旧名 `tick` 等于 `tickPost` |
| `hud` | 全部 | **在屏幕上绘制**，每帧触发，见下文 |
| `screenRender` | 全部 | **在界面上绘制**，例如箱子界面，见下文 |
| `playerTickPre` / `playerTickPost` | NeoForge | 本地玩家每 tick 一次。服务端对应的是 `PlayerEvents.tickPre` / `tickPost` |
| `interactionKey` | NeoForge | 玩家按下攻击、使用、选取方块键时。可取消，取消后原版不处理这次按键 |
| `loggedIn` / `loggedOut` / `cloned` | NeoForge | 客户端连接服务器 / 断开 / 玩家对象被替换 |
| `hudRender` | NeoForge | 按 id 注册 HUD 绘制，可以分层排序，见下文 |
| `worldRender` | NeoForge | 在世界里画线和方框，见下文 |
| `generateAssets` | NeoForge | **生成资源包文件**（模型、方块状态等），见下文 |
| `lang` | NeoForge | **添加翻译**，按语言代码过滤，见下文 |
| `postEffects` | NeoForge | 声明后处理效果，见 [客户端绘制](client-rendering_cn) |
| `commandRegistry` | NeoForge | 注册客户端命令 |
| `registerKeyMappings` / `registerMenuScreens` / `registerRenderers` / `registerParticleProviders` | NeoForge | 游戏启动时注册按键、菜单界面、渲染器、粒子。需要原生 Java 对象，一般用不到 |

<a id="wiki-section-29"></a>
### 在屏幕上绘制：hud 和 screenRender

`hud` 每帧在游戏画面上触发，回调参数就是画笔 `painter`。`screenRender` 在打开界面时每帧触发，画笔是 `event.painter`。

```javascript
// client_scripts/hud.js
ClientEvents.hud(painter => {
  // 右上角的状态面板
  let x = painter.getWidth() - 130
  painter
    .color(0xAA000000).rect(x, 10, 120, 60)      // 半透明黑底
    .outline(x, 10, 120, 60, 0xFFFFFFFF)          // 白色边框
    .centerText('NekoJS', x + 60, 20)             // 居中文字
    .text('Hello HUD', x + 10, 32, 0xFFFFFFAA)    // 左对齐文字
    .resetColor()
  painter.item(Item.of('minecraft:diamond'), x + 10, 46)   // 物品图标
})

// client_scripts/screen_overlay.js：在箱子界面顶部加一行字
ClientEvents.screenRender(event => {
  if (event.screenTitle === 'Chest') {
    event.painter.centerText('自定义提示', event.painter.getWidth() / 2, 4, 0xFFFFFFAA)
  }
})
```

画笔的方法如下。坐标是界面缩放后的像素，颜色是 ARGB 整数，例如 `0x80FF0000` 是半透明红色。

| 方法 | 说明 |
|---|---|
| `getWidth()` / `getHeight()` | 屏幕宽高 |
| `getPartialTick()` | 当前帧在两个 tick 之间的位置（0 到 1），做平滑动画用 |
| `getWorldTime()` | 客户端世界时间（tick），做循环动画用 |
| `color(argb)` / `resetColor()` | 设置 / 重置默认颜色。不写颜色的方法都用默认颜色 |
| `rect(x, y, w, h[, color])` | 实心矩形 |
| `outline(x, y, w, h[, color])` | 1 像素边框 |
| `gradient(x, y, w, h, 上方颜色, 下方颜色)` | 上下渐变矩形 |
| `gradientH(x, y, w, h, 左侧颜色, 右侧颜色)` | 左右渐变矩形。宽度太大会影响性能 |
| `text(文字, x, y[, color])` | 左对齐文字 |
| `centerText(文字, x, y[, color])` | 以 x 为中心的文字 |
| `textWidth(文字)` / `wrapText(文字, 最大宽度)` / `lineHeight()` | 文字宽度、按宽度换行、行高 |
| `texture(贴图 id, x, y, w, h)` | 从贴图左上角截取 w×h 画出来（贴图默认按 256×256） |
| `texture(贴图 id, x, y, u, v, w, h)` | 从贴图的 (u, v) 位置截取 |
| `texture(贴图 id, x, y, u, v, w, h, 贴图宽, 贴图高)` | 指定贴图实际尺寸 |
| `textureFull(贴图 id, x, y, w, h)` | 把整张贴图缩放到 w×h |
| `item(物品, x, y)` | 物品图标，物品用 `Item.of(...)` |
| `push()` / `pop()` / `translate(x, y)` | 保存 / 恢复坐标变换，平移坐标 |
| `scissor(x, y, w, h)` / `resetScissor()` | 只在这个区域内绘制 / 取消限制 |

`screenRender` 的事件对象还有 `event.screen`（界面对象）、`event.screenTitle`（界面标题，如 `'Chest'`）、`event.mouseX` / `event.mouseY`（鼠标位置）。

注意：

- `hud` 每帧都会执行，里面的逻辑要尽量轻。
- `color()` 会一直生效，画完一块后记得 `resetColor()`。`push()` 和 `pop()`、`scissor()` 和 `resetScissor()` 要成对使用。
- 画笔只在本次回调里有效。不要把它存进变量留到下一帧用；需要保留的是要画的数据，每帧重新画。

<a id="wiki-section-30"></a>
### 按 id 注册绘制：hudRender 和 worldRender

仅 NeoForge。`hudRender` 按 id 注册一个 HUD 绘制函数，可以指定图层和顺序；`worldRender` 在世界中画线和方框。同一个 id 再次注册会替换旧的。

```javascript
// client_scripts/boss_bar.js
// layer：'background' | 'normal' | 'foreground'；同一层里 priority 小的先画
ClientEvents.hudRender('boss_bar', { layer: 'normal', priority: 0 }, (ctx, gui) => {
  const hp = clientData.get('boss_hp')   // 服务端用 ClientData 同步过来的数据
  if (!hp) return
  ctx.text(hp.name + ' ' + (hp.hp * 100).toFixed(0) + '%', 8, 8, 0xFFFFFFFF)
  ctx.fillRect(8, 20, 120, 6, 0xAA000000)
  ctx.fillRect(8, 20, Math.floor(120 * hp.hp), 6, 0xFF5DBB63)
})

// client_scripts/lines.js
// layer：'early' | 'normal' | 'late'；坐标是世界坐标
ClientEvents.worldRender('quest_path', { layer: 'normal', priority: 0 }, ctx => {
  ctx.box(10, 70, 10, 12, 72, 12, 0xFF00FF00)
  ctx.line(0, 64, 0, 10, 70, 10, 0xFFFFFF00, 2)
})
```

一个绘制函数报错只影响它自己，错误会出现在错误面板里。更多用法见 [客户端绘制](client-rendering_cn)；`clientData` 见 [全局绑定](global-bindings_cn#wiki-client-data)。

<a id="wiki-section-31"></a>
### 生成资源和翻译：generateAssets 和 lang

仅 NeoForge。`generateAssets` 把文件写进 `nekojs/assets` 资源包，路径相对于这个目录；`lang` 按语言代码收集翻译。都在客户端加载资源时执行（启动游戏、`/nekojs reload client`）。

```javascript
// client_scripts/assets.js
ClientEvents.generateAssets(event => {
  event.json('mymod/models/item/cool_gem.json', {
    parent: 'minecraft:item/generated',
    textures: { layer0: 'mymod:item/cool_gem' }
  })
})

ClientEvents.lang('zh_cn', event => {
  event.add('item.mymod.cool_gem', '炫酷宝石')
})
```

<a id="wiki-section-32"></a>
### 资源生成助手：Assets

手写 `event.json(...)` 容易把目录写错。在 NeoForge 26.x 上，可以改用 `Assets` 绑定。它直接在脚本顶层调用，启动、服务端、客户端脚本都能用。文件同样写进 `nekojs/assets`，在下次加载资源时生效（例如按 F3+T）。

```javascript
// startup_scripts/assets.js
// 方块状态：可以写完整对象（variants 或 multipart 二选一），也可以只写模型 id
Assets.blockState('mymod:my_block', 'mymod:block/my_block')
Assets.blockState('mymod:my_block', { variants: { '': { model: 'mymod:block/my_block' } } })

// 方块模型
Assets.blockModel('mymod:my_block', {
  parent: 'minecraft:block/cube_all',
  textures: { all: 'my_block' }   // 简写，会变成 'mymod:block/my_block'
})

// 物品模型
Assets.itemModel('mymod:my_item', {
  parent: 'minecraft:item/generated',
  textures: { layer0: 'mymod:item/my_item' }   // 已经写全的值保持不变
})

// 洋红色占位贴图
Assets.texture('mymod:block/my_block')   // → textures/block/my_block.png
Assets.texture('mymod:my_item', 'item')  // → textures/item/my_item.png
```

| 方法 | 写到哪里 | 说明 |
|---|---|---|
| `Assets.blockState(id, state)` | `assets/<命名空间>/blockstates/<路径>.json` | `state` 是含 `variants` 或 `multipart` 的对象，或者一个模型 id |
| `Assets.blockModel(id, model)` | `assets/<命名空间>/models/block/<路径>.json` | 路径可以有子目录，如 `mymod:custom/x` |
| `Assets.itemModel(id, model)` | `assets/<命名空间>/models/item/<路径>.json` | 同上 |
| `Assets.texture(id[, 类型])` | `assets/<命名空间>/textures/<类型>/<路径>.png` | 16×16 洋红色占位图。类型是 `'block'`（默认）或 `'item'` |

- **贴图简写**：模型 `textures` 里的值既没有 `:` 也没有 `/` 时，会自动补成 `<命名空间>:block/<值>`（物品模型补成 `<命名空间>:item/<值>`）。其他值原样保留。
- **占位贴图**只是让模型不缺贴图，游戏里显示为洋红色。真正的贴图用资源包覆盖。
- id 必须是合法的资源 id：小写字母、数字、`_`、`.`、`-`，路径里可以有 `/`。不写命名空间时默认是 `minecraft`。id 不合法会直接报错，不会写文件。

<a id="wiki-section-33"></a>
## RecipeViewerEvents

修改 JEI 里显示的内容。写在 `client_scripts` 里。只在 NeoForge 上、并且安装了 JEI 时可用。JEI 重新加载时触发（进入世界、重新加载资源）。

| 事件 | 按什么过滤 | 说明 |
|---|---|---|
| `addEntries` | `'item'` 或 `'fluid'` | 在 JEI 列表里添加条目：`event.add(id)` |
| `removeEntries` | `'item'` 或 `'fluid'` | 从 JEI 列表里隐藏条目：`event.add(id)`。只是隐藏，不影响游戏 |
| `removeRecipes` | — | 按配方 id 隐藏配方：`event.remove(配方 id)` |
| `removeCategories` | — | 隐藏整个配方分类：`event.remove(分类 id)` |
| `addInformation` | — | 给条目加说明页：`event.add(id, 文字)` |

```javascript
// client_scripts/jei.js
RecipeViewerEvents.addEntries('item', event => {
  event.add('mymod:cool_gem')
})

RecipeViewerEvents.removeEntries('item', event => {
  event.add('minecraft:debug_stick')   // 要隐藏的条目也用 add 加进列表
})

RecipeViewerEvents.addInformation(event => {
  event.add('mymod:cool_gem', '很酷的宝石！')
})

RecipeViewerEvents.removeCategories(event => {
  event.remove('jei:information')
})
```

<a id="wiki-section-34"></a>
## ProbeEvents

在执行 `/nekojs probe` 生成代码补全时调整生成结果。写在 `server_scripts` 里，仅 NeoForge。修改会在下次执行 `/nekojs probe` 时生效。

| 事件 | 说明 |
|---|---|
| `modifyType` | 修改某个 Java 类的声明：`event.forClass(完整类名)` 返回编辑器，可以改方法名、参数类型、文档等 |
| `assignType` | 把某个 Java 类在所有地方替换成你指定的类型：`event.assign(完整类名, 类型)` |
| `addGlobal` | 声明额外的全局变量：`event.add(名字, 类型)` |
| `snippets` | 添加 VS Code 代码片段：`event.add(名字, 前缀, 内容[, 说明])` |

类型参数的写法：含 `.` 的字符串当作 Java 完整类名，否则当作基础类型名（如 `'string'`、`'number'`、`'boolean'`）。复杂类型用 `event.type(...)`、`event.array(...)`、`event.union(...)` 构造。

```javascript
// server_scripts/probe_patch.js
ProbeEvents.modifyType(event => {
  const editor = event.forClass('net.minecraft.world.entity.player.Player')
  if (editor) {
    editor
      .renameMethod('getXxx', 'getCustom')          // 同名的所有重载一起改
      .changeParamType('addItem', 0, 'net.minecraft.world.item.ItemStack')
      .markOptional('eat', 'level')                 // 参数变成可选
      .setMethodDoc('getCustom', '自定义文档')
      .setDoc('玩家')
  }
})

ProbeEvents.assignType(event => {
  event.assign('net.minecraft.nbt.CompoundTag', 'MyNbt')
})

ProbeEvents.addGlobal(event => {
  event.add('MyFlag', 'boolean')
})

ProbeEvents.snippets(event => {
  event.add('nekojs-listener', 'listen', 'ServerEvents.started(e => { $1 })', '监听服务端启动')
})
```

编辑器的完整方法列表见 [Probe 类型生成](probe-type-generation_cn)。按名字找不到的方法或字段会被直接忽略，不会报错。

<a id="wiki-section-35"></a>
## 自定义事件

上面没有你需要的事件时，有两种办法：

- **`ScriptEvents`**（启动脚本，NeoForge 和 Fabric 都可用）：声明自己的事件组。服务端或客户端脚本可以像内置事件一样监听它，由脚本自己触发。见 [事件扩展](event-extensions_cn)。
- **`NativeEvents`**（启动脚本，仅 NeoForge）：直接监听 NeoForge 的原生事件类，适合临时用一下。改了要重启游戏。见 [全局绑定](global-bindings_cn)。

<a id="wiki-section-36"></a>
## 加载时的拼写检查

加载脚本时，NekoJS 会检查事件回调里用到的事件对象成员。写错名字会直接报错，并给出拼写建议（`Did you mean ...`）。

能检查到的写法包括：`event.player`、`event.getPlayer()`、`event['player']`、链式调用（如 `event.player.getServer()`）、可选链 `event?.player`，以及把 `event` 赋给另一个变量后再用。

在运行时才能确定的成员名（例如从网络数据里取出的名字）不会检查，照常运行，出错时在运行时报告。脚本里有检查器不认识的语法时，这个文件会跳过检查并在日志里给出警告。

<a id="wiki-section-37"></a>
## 下一步

- [全局绑定](global-bindings_cn)：`Item`、`Text`、`Network` 等全局对象。
- [配方系统](recipe-system_cn)：`ServerEvents.recipes` 详解。
- [注册新内容](registering-new-content_cn)：`RegistryEvents` 和 `GoalEvents` 详解。
- [客户端绘制](client-rendering_cn)：HUD、世界绘制和后处理。

<!-- wiki-nav -->

---

[上一篇: Python 特性清单](python-feature-support_cn) · [目录](Home) · [下一篇: AI 辅助脚本编写](ai-support_cn)
