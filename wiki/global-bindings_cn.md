<!-- wiki-page: global-bindings; locale: cn -->

> **中文** · [English](global-bindings_us)

<a id="wiki-section-1"></a>
# 全局绑定

绑定是脚本里直接能用的名字，比如 `Item`、`Text`、`JsonIO`。不需要 `require` 或 `import`，写上名字就能调用：

```javascript
const stone = Item.of('minecraft:stone', 16)
console.info(Text.of('你好').bold())
```

本页按用途列出所有内置绑定。签名写法是 `方法(参数) → 返回值`。要看最准确的参数和成员，执行 `/nekojs probe` 生成代码补全，以编辑器提示为准。

<a id="wiki-section-2"></a>
## 先看能不能用

有些绑定只在某种脚本里存在，有些只在某个平台上存在。在不支持的地方使用，会报“找不到这个名字”。

| 绑定 | 在哪种脚本里用 | 平台 |
|---|---|---|
| `Item`、`Block`、`Ingredient`、`Items`、`ItemStack` | 全部 | 全部 |
| `Fluid`、`FluidIngredient`、`FluidAmounts`、`Fluids`、`FluidStack` | 全部 | 仅 NeoForge |
| `Text`、`NBT`、`JsonIO`、`ID`、`Platform`、`Performance`、`RecipeSchema` | 全部 | 全部 |
| `Registry` | 全部 | NeoForge；Fabric 上只返回空结果 |
| `I18n` | 客户端 | 全部 |
| `global`、`shared`、`once`、`clearOnce` | 全部 | 全部 |
| `ClientData` / `clientData` | 服务端推送 / 客户端读取 | 全部 |
| `Network` | 服务端、客户端 | 全部 |
| `Capabilities` | 全部（在启动事件里使用） | 全部，两个平台的实现不同 |
| `EntitySelectors` | 服务端、测试 | 全部 |
| `VillagerTrades` | 服务端 | 仅 NeoForge |
| `DataMap` | 全部 | 仅 NeoForge |
| `PostEffects` | 客户端 | 仅 NeoForge |
| `ClientUI` | 客户端 | 仅 NeoForge 26.x |
| `Assets` | 全部 | 仅 NeoForge 26.x |
| `NativeEvents` | 启动 | 仅 NeoForge |
| `Utils`、`Color`、`Time`、`UUID`、`StringUtils`、`KMath`、`JavaMath`、`TextIcons`、`DamageSource`、`ParticleOptions` | 全部 | 全部 |
| `Test` | 测试 | 全部 |
| `Minecraft`、`Screen`、`Window`、`KeyMapping`、`InputConstants` | 客户端 | 全部 |
| `TriState` | 全部 | 26.x |

平台之间的完整差异见 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-3"></a>
## 物品和配料

<a id="wiki-section-4"></a>
### Item

创建物品栈、查询物品。

| 方法 | 说明 |
|---|---|
| `Item.of(id) → ItemStack` | 按 ID 创建物品栈。可以写数量：`'16x minecraft:stone'` |
| `Item.of(id, count) → ItemStack` | 指定数量 |
| `Item.of(stack) → ItemStack` | 传入物品栈，原样返回 |
| `Item.of(stack, count) → ItemStack` | 传入物品栈，改成指定数量 |
| `Item.empty() → ItemStack` | 空物品栈 |
| `Item.id(id) → Item \| null` | 按 ID 查物品。不写命名空间时默认 `minecraft`。找不到返回 `null` |
| `Item.idOf(item) → Identifier` | 物品的注册 ID。参数是物品，不是物品栈 |

`Block.id(id)` 和 `Block.idOf(block)` 是对应的方块版本。`Items` 和 `Blocks` 是原版的物品、方块常量，例如 `Items.DIAMOND`。

```javascript
const sword = Item.of('minecraft:diamond_sword')
const apples = Item.of('minecraft:apple', 5)
if (Item.id('mymod:ruby') === null) console.warn('mymod 没装')
```

<a id="wiki-itemstack"></a>
<a id="wiki-section-5"></a>
### 物品栈上的方法

除了原版方法，NekoJS 还给物品栈加了下面这些常用方法：

| 方法 | 说明 |
|---|---|
| `getId() → string` | 物品 ID，例如 `minecraft:stone` |
| `getMod() → string` | 物品所属模组的 ID |
| `getBlock() → Block \| null` | 方块物品对应的方块；不是方块物品时返回 `null` |
| `withCount(count) → ItemStack` | 返回指定数量的副本，原物品栈不变。数量小于 1 时返回空物品栈 |
| `setCountAndReturn(count) → ItemStack` | 修改数量并返回自己，方便链式调用 |
| `enchantById(id, level) → ItemStack` | 添加附魔。只能在服务端运行时调用。附魔书请改写 `stored_enchantments` 组件 |
| `hasEnchantment(id, level) → boolean` | 是否有不低于该等级的附魔 |
| `hasGlint()` / `setGlint(bool)` | 读取或设置附魔光效 |
| `isUnbreakable()` / `setUnbreakable(bool)` | 读取或设置“无法破坏” |
| `getLore()` / `setLore(lines)` | 读取或设置物品说明文字 |
| `matches(other) → boolean` | 是否与另一个物品栈、物品或配料匹配 |
| `asIngredient() → Ingredient` | 转成配料 |
| `weakNBT()` / `strictNBT() → Ingredient` | 转成按组件匹配的配料：宽松或严格。Fabric 上两者相同，都要求组件完全一致 |
| `pdata() → PersistentDataJS` | 物品栈上的自定义数据，见下一节 |

<a id="wiki-pdata"></a>
<a id="wiki-section-6"></a>
### 自定义数据 pdata

物品栈、实体和玩家都可以用 `pdata()` 存自己的数据：

```javascript
// server_scripts/pdata.js
PlayerEvents.loggedIn(event => {
  const data = event.entity.pdata()
  data.putInt('logins', data.getInt('logins') + 1)
})
```

- **物品栈**：数据存在原版的 `custom_data` 组件里，跟着物品一起保存、一起移动。
- **实体和玩家**：数据随实体存档。服务端写入后会自动同步给附近的客户端。
- **客户端**：实体和玩家的 `pdata()` 只能读。在客户端脚本里写入会报错。

可用的读写方法、同步时机和存档格式见 [数据与状态](data-and-state_cn)。

<a id="wiki-section-7"></a>
### Ingredient

创建配方里用的配料。

| 方法 | 说明 |
|---|---|
| `Ingredient.of(...values)` | 由多个值组成配料，匹配其中任意一个。值可以是物品 ID、`'#标签'`、`'@模组ID'`、`'*'`（所有物品）、`'/正则/'`、物品栈或其他配料 |
| `Ingredient.item(id)` | 单个物品 |
| `Ingredient.tag(id)` | 物品标签，不用自己加 `#` |
| `Ingredient.any(...ingredients)` | 匹配几个配料中的任意一个 |
| `Ingredient.all()` | 所有物品。只包含调用时已经注册的物品 |
| `Ingredient.not(ingredient)` | 除指定配料外的所有物品 |

这些方法返回的配料对象还可以继续组合：

| 方法 | 说明 |
|---|---|
| `or(value)` | 并集，参数可以是 ID、物品、物品栈或配料 |
| `and(x)` / `intersect(x)` | 交集（仅 NeoForge） |
| `except(x)` / `subtract(x)` | 差集（仅 NeoForge） |
| `withCount(n)` / `asStack()` | 带数量的配料；`asStack()` 等于 `withCount(1)`（仅 NeoForge） |
| `matches(stack)` / `test(stack)` | 物品栈是否匹配 |
| `first() → ItemStack` | 第一个匹配的物品栈；没有时返回空物品栈 |
| `stacks()` / `displayStacks()` | 所有匹配的物品栈 |
| `isEmpty()` | 是否为空 |
| `unwrap()` | 取出原版 `Ingredient` |

```javascript
const logs = Ingredient.of('#minecraft:logs', 'minecraft:stick')
const notDirt = Ingredient.not('minecraft:dirt')
```

`Ingredient` 上没列出的成员会交给原版 `Ingredient` 类的静态成员处理。

<a id="wiki-section-8"></a>
## 流体（仅 NeoForge）

Fabric 上没有本节的任何绑定。

<a id="wiki-section-9"></a>
### Fluid

| 方法 | 说明 |
|---|---|
| `Fluid.of(value) → FluidStack` | 由流体 ID、流体对象或 `{ fluid, amount }` 创建流体栈，默认一桶 |
| `Fluid.of(value, amount) → FluidStack` | 指定数量（mB），必须大于 0 |
| `Fluid.water()` / `Fluid.water(amount)` | 水，默认一桶 |
| `Fluid.lava()` / `Fluid.lava(amount)` | 岩浆，默认一桶 |
| `Fluid.empty()` | 空流体栈 |
| `Fluid.ingredient(...values)` | 流体配料，匹配其中任意一个 |
| `Fluid.sizedIngredient(value)` / `Fluid.sizedIngredient(value, amount)` | 带数量的流体配料 |

<a id="wiki-section-10"></a>
### FluidIngredient

| 方法 | 说明 |
|---|---|
| `FluidIngredient.of(...values)` | 匹配其中任意一个 |
| `FluidIngredient.fluid(id)` | 单个流体 |
| `FluidIngredient.tag(id)` | 流体标签 |
| `FluidIngredient.sized(value)` / `FluidIngredient.sized(value, amount)` | 带数量的流体配料 |

返回的流体配料还有 `or(value)`、`isEmpty()`、`withAmount(amount)` 和 `unwrap()`。

<a id="wiki-section-11"></a>
### FluidAmounts 和流体常量

`FluidAmounts` 是常用流体量，单位 mB：

| 常量 | 数值 |
|---|---|
| `BUCKET` / `B` | 1000 |
| `MILLIBUCKET` / `MB` | 1 |
| `BOTTLE` | 250 |
| `INGOT` | 90 |
| `NUGGET` | 10 |
| `METAL_BLOCK` | 810 |

`Fluids` 是原版流体常量（例如 `Fluids.WATER`），`FluidStack` 是流体栈类。

<a id="wiki-section-12"></a>
## 文本

<a id="wiki-section-13"></a>
### Text

创建聊天消息、物品提示、标题等地方用的文本。

| 方法 | 说明 |
|---|---|
| `Text.of(text)` | 普通文字 |
| `Text.empty()` | 空文本 |
| `Text.translatable(key, ...args)` | 翻译键，按每个玩家自己的语言显示 |
| `Text.translateWithFallback(key, fallback, ...args)` | 翻译键；找不到这个键时显示 `fallback` |
| `Text.keybind(key)` | 显示玩家当前绑定的按键，例如 `'key.attack'` |
| `Text.score(name, objective)` | 记分板分数 |
| `Text.selector(pattern)` | 实体选择器，例如 `'@p'`。26.x 上只显示原样文字 |
| `Text.ofValues(...values)` | 把多段文本按顺序拼起来 |
| `Text.join(separator, ...values)` | 用分隔符拼接，分隔符可以是文本或字符串 |

文本可以直接传给需要文本组件的地方，例如 `displayClientMessage`、物品提示和书本。

<a id="wiki-section-14"></a>
### 样式和点击效果

样式方法不会修改原来的文本，而是返回一个新文本，所以可以一直链式写下去：

| 方法 | 说明 |
|---|---|
| `bold()` / `italic()` / `underlined()` / `strikethrough()` / `obfuscated()` | 粗体、斜体、下划线、删除线、乱码。传 `false` 可以关掉 |
| `color(color)` | 颜色名（`'red'`）或十六进制（`'#FF0000'`） |
| `red()`、`gold()`、`aqua()` 等 | 16 种颜色的简写，`red()` 等于 `color('red')` |
| `click(action, value)` | 点击效果：`runCommand`、`suggestCommand`、`openUrl`、`openFile`、`copyToClipboard`、`changePage` |
| `hover(text)` | 鼠标悬停时显示的文本 |
| `insertion(text)` | Shift+点击时插入聊天框的文字 |
| `font(id)` | 字体 ID |
| `append(...values)` | 在后面追加文本 |
| `isEmpty()` | 是否为空 |

16 种颜色简写是：`black`、`darkBlue`、`darkGreen`、`darkAqua`、`darkRed`、`darkPurple`、`gold`、`gray`、`darkGray`、`blue`、`green`、`aqua`、`red`、`lightPurple`、`yellow`、`white`。

```javascript
// server_scripts/welcome.js（NeoForge 上用 event.entity，Fabric 上用 event.player）
PlayerEvents.loggedIn(event => {
  const msg = Text.of('点我领钻石')
    .bold().red()
    .click('runCommand', '/give @s diamond')
    .hover(Text.of('点击执行 /give'))
  event.entity.displayClientMessage(msg, false)
})
```

<a id="wiki-section-15"></a>
### I18n（仅客户端）

在客户端脚本里，按玩家当前的语言把翻译键翻成普通字符串。

| 成员 | 说明 |
|---|---|
| `I18n.translate(key, ...args)` | 翻译。找不到这个键时返回键本身 |
| `I18n.has(key)` | 当前语言里有没有这个键 |
| `I18n.translateWithFallback(key, fallback, ...args)` | 有这个键就翻译，没有就用 `fallback` 作为模板 |
| `I18n.languageCode` / `I18n.rightToLeft` | 当前语言代码；是否从右往左书写。只读 |

参数用 `%s`、`%1$s` 这样的占位符，只能传字符串、数字、布尔值和 `null`。返回的是字符串，玩家切换语言后不会自动更新，需要重新调用。

要发给玩家、让每个玩家看到自己语言的文本，请在服务端用 `Text.translatable(...)`。

```javascript
// client_scripts/labels.js
console.info(I18n.translate('gui.done'), I18n.languageCode)
```

<a id="wiki-section-16"></a>
## 读写数据

<a id="wiki-section-17"></a>
### NBT

创建和读写 NBT 数据。`NBT` 生成的是 NekoJS 自己的 NBT 值，不能直接当成原版 `CompoundTag` 用。

| 方法 | 说明 |
|---|---|
| `NBT.of(value)` | 由字符串、数字、数组或普通对象创建 |
| `NBT.byte/short/int/float/double(n)` | 指定数字类型 |
| `NBT.long(text)` | 长整数。用字符串传入，避免 JS 数字丢精度 |
| `NBT.byteArray(arr)` / `NBT.intArray(arr)` | 字节数组、整数数组 |
| `NBT.compound()` | 链式构建器：`put`、`putInt`、`putString`、`putCompound`、`putList` 等，最后调用 `build()` |
| `NBT.parse(snbt)` / `NBT.toSnbt(value)` | SNBT 文本和 NBT 值互转 |
| `NBT.toObject(value)` / `NBT.fromObject(obj)` | NBT 值和普通 JS 对象互转 |
| `NBT.read(path)` | 读取 `nekojs/data/` 下的 NBT 文件。文件不存在时返回 `null` |
| `NBT.write(path, value)` | 写入 NBT 文件，最外层必须是对象 |

NBT 值本身有 `kind()`、`scalar()`、`values()`、`entries()`、`toSnbt()` 等方法。

```javascript
NBT.write('players/neko.nbt', { name: 'neko', level: 12 })
const saved = NBT.read('players/neko.nbt')
if (saved !== null) console.info(saved.toSnbt())
```

`NBT.of` 不接受 `null`、布尔值、函数，也不接受元素类型混杂的数组。没指定类型的整数存为 int，带小数的数存为 double。

<a id="wiki-jsonio"></a>
<a id="wiki-section-18"></a>
### JsonIO

解析、生成和保存 JSON。

| 方法 | 说明 |
|---|---|
| `JsonIO.parse(text)` | 解析 JSON 字符串 |
| `JsonIO.toString(value)` | 生成紧凑的 JSON 字符串 |
| `JsonIO.toPrettyString(value)` | 生成带两格缩进的 JSON 字符串 |
| `JsonIO.read(path)` | 读取 `nekojs/data/` 下的 JSON 文件。文件不存在时返回 `null` |
| `JsonIO.write(path, value)` | 写入 JSON 文件，自动创建上级目录 |

```javascript
JsonIO.write('settings/ui.json', { scale: 2, theme: 'dark' })
const ui = JsonIO.read('settings/ui.json')
console.info(ui === null ? '还没有设置' : ui.toPrettyString())
```

<a id="wiki-section-19"></a>
### 文件路径规则

`NBT.read/write` 和 `JsonIO.read/write` 只能访问 `<游戏目录>/nekojs/data/` 里的文件：

- 路径写成相对路径，用正斜杠，例如 `settings/ui.json`。
- 不能用绝对路径、盘符、反斜杠，也不能用 `..` 跳到上级目录。
- 写入是原子的：要么完整写入，要么保持原文件不变。

NBT 文件压缩后最大 3 MiB，最多 64 层嵌套。路径不合法时报 `NBT_PATH_FORBIDDEN`，文件太大时报 `NBT_FILE_TOO_LARGE`，SNBT 语法错误时报 `INVALID_NBT`。

<a id="wiki-section-20"></a>
### Registry

只读地查询注册表。返回的都是字符串、布尔值和数组。

| 方法 | 说明 |
|---|---|
| `Registry.get(registryId)` | 取得注册表视图，例如 `'minecraft:item'` |
| `view.exists()` | 注册表是否存在 |
| `view.all()` | 所有条目的 ID |
| `view.has(id)` | 是否有这个条目 |
| `view.tag(tagId)` | 某个标签里的所有条目 ID |
| `view.dataMapIds()` | 这个注册表有哪些 data map |
| `view.dataMapValue(mapId, id)` | 某个条目的 data map 值（JSON 字符串），没有时返回 `null` |

视图可以直接用 `for...of` 遍历：

```javascript
for (const id of Registry.get('minecraft:enchantment')) console.info(id)
```

> Fabric 上 `Registry` 目前只返回空结果：`exists()` 为 `false`，列表为空。不要据此判断注册表里真的没有内容。

<a id="wiki-section-21"></a>
## 在脚本之间共享数据

<a id="wiki-global-shared"></a>
<a id="wiki-section-22"></a>
### global 和 shared

- `global`：同一种脚本之间共享。例如所有服务端脚本共用一个 `global`。
- `shared`：所有种类的脚本之间共享，但只在同一个游戏进程里有效。

两者在 reload 和切换世界后都会保留，关闭游戏后清空。它们不会通过网络同步，也不会存进存档。

```javascript
// server_scripts/config.js
global.config = { version: 2 }
shared.serverReady = true

// client_scripts/hud.js（单人游戏时能读到）
console.info(shared.serverReady)
```

<a id="wiki-section-23"></a>
### once 和 clearOnce

`once(key, callback)` 只在这个 key 第一次出现时运行回调，之后的 reload 也不会再运行，直到关闭游戏。适合只需要做一次的事，比如写入一个外部文件。

| 调用 | 说明 |
|---|---|
| `once('myInit', () => ...)` | 第一次调用时运行回调并返回它的结果；之后什么也不做 |
| `clearOnce('myInit')` | 清除这个 key，下次 `once` 会再运行。返回之前是否存在 |
| `clearOnce()` | 清除所有 key |

```javascript
once('mypack:warmup', () => console.info('只打印一次'))
```

key 是全局的，不同脚本用同一个 key 会互相影响。建议加上自己的前缀。

<a id="wiki-client-data"></a>
<a id="wiki-section-24"></a>
### ClientData 和 clientData

服务端脚本把数据推给客户端，客户端脚本读取。常用来给 HUD 提供数据。

```javascript
// server_scripts/boss.js
PlayerEvents.loggedIn(event => {
  ClientData.sync('boss_hp', { name: 'Example Golem', hp: 0.42 })
  ClientData.syncTo(event.entity, 'quest', { stage: 3 })
})

// client_scripts/state.js
console.info(clientData.get('boss_hp'))
```

| 方法 | 说明 |
|---|---|
| `ClientData.sync(key, value)` | 推送给所有在线玩家 |
| `ClientData.syncTo(player, key, value)` | 推送给一个玩家 |
| `clientData.get(key)` | 读取。没有这个 key 时返回 `null` |
| `clientData.has(key)` | 是否有这个 key。用它区分“没有”和“值是 null” |
| `clientData.keys()` / `clientData.size()` | 所有 key、key 的数量 |

- 值必须是 JSON 能表示的类型：字符串、数字、布尔值、对象、数组或 `null`。
- key 不能为空，最长 256 个字符。值转成 JSON 后最长 32768 个字符（按字符数，不是 32 KB）。
- 同一个 key 再次推送会覆盖旧值。玩家断开连接或退出世界时，客户端的数据会清空。

<a id="wiki-network"></a>
<a id="wiki-section-25"></a>
### Network

在客户端和服务端之间发送自定义消息。接收方用 [NetworkEvents](event-reference_cn) 监听。

| 方法 | 在哪边调用 | 说明 |
|---|---|---|
| `Network.sendToServer(channel, data)` | 客户端 | 发给服务端 |
| `Network.sendToPlayer(player, channel, data)` | 服务端 | 发给一个玩家 |
| `Network.sendToAll(channel, data)` | 服务端 | 发给所有玩家 |

`data` 是 NBT 对象，直接写普通 JS 对象就会自动转换，例如 `{ action: 'open_menu' }`。完整例子见 [事件参考](event-reference_cn)。

<a id="wiki-section-26"></a>
## 游戏功能

<a id="wiki-capabilities"></a>
<a id="wiki-section-27"></a>
### Capabilities

创建物品栏、能量、流体储存，给 `CapabilityEvents.register` 使用。注册只在启动时进行一次，改了要重启游戏。注册的写法见 [事件参考](event-reference_cn)。

| 方法 | 说明 |
|---|---|
| `Capabilities.itemHandler(size)` | 有 `size` 格的物品栏 |
| `Capabilities.energyStorage(capacity, maxReceive, maxExtract)` | 能量储存：容量、单次最多输入、单次最多输出 |
| `Capabilities.fluidTank(capacity)` | 单格流体罐，容量单位 mB |

每个方法都可以在最后多传一个 `onChange` 回调，内容真正变化后调用。在回调里标记数据需要保存，或安排同步。

使用时注意：

- **每个方块、实体或物品要有自己的储存对象。** 不要在每次查询时新建一个空的，也不要让同类的所有对象共用一个。
- **保存和读取要自己做。** 注册不会自动把内容写进存档。
- **Fabric 的流体单位不同。** `fluidTank(1000)` 在两个平台上都是一桶，但 Fabric 返回的容量和传输数量用 droplet 计（1 mB = 81 droplet）。

Fabric 还提供查询其他模组储存的方法。能力名是 `'item'`、`'fluid'` 或 `'energy'`：

| 方法 | 说明 |
|---|---|
| `Capabilities.getBlock(level, pos, capability, direction)` | 查询某个位置的方块 |
| `Capabilities.getEntity(entity, capability, direction)` | 查询实体 |
| `Capabilities.getItem(stack, capability, context)` | 查询物品栈 |
| `Capabilities.blockLookup(capability)` | 取得方块查询对象 |
| `Capabilities.entityLookup(capability)` | 取得实体查询对象 |
| `Capabilities.itemLookup(capability)` | 取得物品查询对象 |

查不到时返回 `null`。Fabric 没有统一的能量标准，所以 `'energy'` 只能和使用 NekoJS 能量接口的储存互通。

NeoForge 上，没列出的成员会交给 NeoForge 原版的 `Capabilities` 类，例如 `Capabilities.ItemHandler.BLOCK`。

<a id="wiki-section-28"></a>
### EntitySelectors

在服务端脚本里，用代码构造实体选择器（相当于命令里的 `@e[...]`）并查询实体。下面的例子在玩家登录时，查找附近最多 5 头牛：

```javascript
// server_scripts/cows.js（NeoForge 上用 event.entity，Fabric 上用 event.player）
PlayerEvents.loggedIn(event => {
  const player = event.entity
  const selector = EntitySelectors.allEntities()
    .type('minecraft:cow').isAlive().distance(0, 32)
    .orderNearest().limit(5).create()
  const cows = EntitySelectors.find(player.level(), selector, player.x, player.y, player.z)
  console.info('附近的牛：' + cows.size())
})
```

**先选起点：**

- 查玩家：`allPlayers()`、`nearestPlayer()`、`randomPlayer()`，或 `builder()`。
- 查其他实体：必须从 `allEntities()`、`nearestEntity()` 或 `randomEntity()` 开始。从玩家起点加 `type('minecraft:cow')` 什么也查不到。

**再加条件：**

- 过滤：`type`、`typeTag`、`name`、`gamemode`、`team`、`tag`。第二个参数传 `true` 表示取反。
- 其他条件：`level(min, max)`（玩家经验等级）、`isAlive()`、`distance(min, max)`、`distanceBelow(max)`、`distanceAbove(min)`、`x/y/z/dx/dy/dz`（范围）、`limit(n)`。
- 排序：`orderArbitrary()`、`orderNearest()`、`orderFurthest()`、`orderRandom()`。

**最后查询：** `EntitySelectors.find(level, selector, x, y, z)`。坐标是距离计算的中心点，请明确传入；省略时用 `(0, 0, 0)`。查询只找已加载的实体，不会加载区块。

> 写错的实体标签不会报错，只会查不到任何东西。

<a id="wiki-section-29"></a>
### VillagerTrades（仅 NeoForge）

添加村民交易要用 `ServerEvents.tradeDeclaration` 事件。`VillagerTrades` 只用来查询当前的交易。

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

- 职业交易的 ID 是 `minecraft:<职业>/level_1` 到 `level_5`。
- 流浪商人：26.x 用 `minecraft:wandering_trader/buying` 等；1.21.1 用 `minecraft:wandering_trader/level_1` 或 `level_2`（`buying`、`common` 对应 1，`uncommon`、`rare` 对应 2）。
- 有一条交易写错，整批交易都不会生效，错误会出现在 `/nekojs error` 里。
- 不再需要某组交易时，用 `ServerEvents.tradeReload(event => event.declareObsolete(id))` 明确移除。

<a id="wiki-section-30"></a>
### DataMap（仅 NeoForge）

查询物品的内置数据：

- `DataMap.furnaceFuel(Item.of('minecraft:coal'))`：作为燃料能烧多少 tick。
- `DataMap.compostable(Item.of('minecraft:wheat_seeds'))`：放进堆肥桶的成功率。

没有数据时返回 `null`，可以用 `??` 给默认值。其他 data map 用 `Registry.get(registryId).dataMapValue(mapId, id)` 查询。

<a id="wiki-section-31"></a>
### PostEffects（仅 NeoForge 客户端）

开关屏幕后处理效果：

| 方法 | 说明 |
|---|---|
| `PostEffects.set(id)` / `clear()` / `toggle(id)` | 开启、关闭、切换效果 |
| `PostEffects.current()` / `isActive()` | 当前效果；是否有效果在运行 |
| `PostEffects.isAvailable(id)` | 这个效果现在能不能开启 |
| `PostEffects.presets()` | 原版效果 ID，例如 `minecraft:invert` |

```javascript
// client_scripts/invert.js
if (PostEffects.isAvailable('minecraft:invert')) PostEffects.set('minecraft:invert')
```

开启前先用 `isAvailable` 检查。效果需要资源包里有对应文件。玩家附身到末影人、蜘蛛、苦力怕视角时，原版效果会覆盖脚本设置的效果。声明自定义效果和各版本差异见 [客户端渲染](client-rendering_cn)。

<a id="wiki-section-32"></a>
### NativeEvents（仅 NeoForge 启动脚本）

直接监听 NeoForge 原生事件，适合 NekoJS 没有封装的事件。

| 方法 | 说明 |
|---|---|
| `NativeEvents.onEvent(eventType, handler)` | 以普通优先级监听 |
| `NativeEvents.onEvent(priority, receiveCancelled, eventType, handler)` | 指定优先级和是否接收已取消的事件 |
| `NativeEvents.onGenericEvent(genericClass, eventType, handler)` | 监听泛型事件 |

`eventType` 写事件类的完整类名，内部类用 `$` 分隔。回调返回 `true` 会取消事件（只对可取消的事件有效）。改了监听器要重启游戏。

<a id="wiki-section-33"></a>
### ScriptEvents（启动脚本）

`ScriptEvents` 是事件组，不是绑定，但常和绑定一起用：在启动脚本里声明自己的事件，服务端或客户端脚本再触发和监听它。

```javascript
// startup_scripts/events.js
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))

// server_scripts/boss.js
MyEvents.bossKilled(payload => console.info(payload.boss))
MyEvents.bossKilled.post({ boss: 'ender_dragon' })
```

详见 [事件扩展](event-extensions_cn)。

<a id="wiki-section-34"></a>
### RecipeSchema

查看有哪些配方类型、每种配方有哪些字段。只读。

| 方法 | 说明 |
|---|---|
| `RecipeSchema.namespaces()` | 所有配方命名空间 |
| `RecipeSchema.types(namespace)` | 某个命名空间下的配方类型 |
| `RecipeSchema.describe(namespace, type)` | 某种配方的字段和写法 |

```javascript
ServerEvents.started(event => {
  console.info(RecipeSchema.types('minecraft'))
  console.info(RecipeSchema.describe('minecraft', 'smelting'))
})
```

自己定义配方类型见 [配方系统](recipe-system_cn)。

<a id="wiki-section-35"></a>
### ClientUI 和 Assets

- `ClientUI`：用 JSX 写客户端界面，仅 NeoForge 26.x。见 [JSX 客户端界面](jsx-client-ui_cn)。
- `Assets`：用脚本生成方块状态、模型、材质等资源文件，仅 NeoForge 26.x。见 [注册新内容](registering-new-content_cn)。

<a id="wiki-section-36"></a>
## 小工具

<a id="wiki-section-37"></a>
### Utils

| 方法 | 说明 |
|---|---|
| `Utils.randomInt(max)` / `Utils.randomInt(min, max)` | 随机整数，不含 `max` |
| `Utils.randomDouble()` / `Utils.randomDouble(max)` / `Utils.randomDouble(min, max)` | 随机小数 |
| `Utils.chance(probability)` | 按概率返回 `true`，`0` 到 `1` |
| `Utils.isArray(v)` / `Utils.isList(v)` / `Utils.isMap(v)` | 判断类型 |

<a id="wiki-section-38"></a>
### Color

颜色用整数表示，格式是 ARGB。

| 方法 | 说明 |
|---|---|
| `Color.rgb(r, g, b)` | 不透明颜色 |
| `Color.argb(a, r, g, b)` | 带透明度的颜色 |
| `Color.alpha/red/green/blue(color)` | 取出某个通道 |
| `Color.hex(color)` / `Color.hexArgb(color)` | 转成 `#RRGGBB` / `#AARRGGBB` |
| `Color.parse(text)` | 解析 `#RRGGBB` 或 `#AARRGGBB` |

<a id="wiki-section-39"></a>
### Time

游戏时间以 tick 计，1 秒 = 20 tick。

| 成员 | 说明 |
|---|---|
| `Time.SECOND` / `Time.MINUTE` / `Time.HOUR` | 20 / 1200 / 72000 tick |
| `Time.seconds(n)` / `Time.minutes(n)` / `Time.hours(n)` | 换算成 tick |
| `Time.parseTime(text)` | 把 `'5s'`、`'10m'`、`'2h'`、`'100t'`、`'250ms'` 换算成 tick。纯数字当作 tick |
| `Time.parseMs(text)` | 同样的写法，换算成毫秒。纯数字当作毫秒 |

<a id="wiki-section-40"></a>
### UUID 和 StringUtils

| 方法 | 说明 |
|---|---|
| `UUID.random()` | 随机 UUID |
| `UUID.fromString(text)` | 解析 UUID 字符串 |
| `UUID.fromName(text)` | 由名字生成固定的 UUID |
| `StringUtils.isBlank(s)` / `StringUtils.isEmpty(s)` | 是否为空白 / 空字符串 |
| `StringUtils.capitalize(s)` / `StringUtils.decapitalize(s)` | 首字母大写 / 小写 |
| `StringUtils.snakeCase(s)` / `StringUtils.camelCase(s)` | 转成 `snake_case` / `camelCase` |

<a id="wiki-section-41"></a>
### ID

创建和拆分资源 ID。

| 方法 | 说明 |
|---|---|
| `ID.of(text)` / `ID.of(namespace, path)` | 创建 ID。**不写命名空间时默认是 `nekojs`，不是 `minecraft`** |
| `ID.resource(text)` | 同上，但严格检查格式：只允许小写字母、数字和 `_ . -`（路径还允许 `/`） |
| `ID.namespace(id)` / `ID.path(id)` | 取出命名空间 / 路径 |
| `ID.asString(id)` | 转成 `'命名空间:路径'` 字符串 |

<a id="wiki-section-42"></a>
### KMath 和 JavaMath

`JavaMath` 就是 Java 的 `Math` 类。`KMath` 提供几个常用的游戏数学工具：

| 成员 | 说明 |
|---|---|
| `KMath.PI` / `KMath.E` | 常量 |
| `KMath.deg(degrees)` | 角度换算成弧度 |
| `KMath.rad(radians)` | 弧度换算成角度 |
| `KMath.blockPos(x, y, z)` | 创建方块坐标，小数会向下取整 |
| `KMath.vec3(x, y, z)` / `KMath.vec3(pos)` | 创建三维向量 |

注意 `deg` 和 `rad` 的方向：`KMath.deg(90)` 得到的是弧度 `π/2`。

<a id="wiki-section-43"></a>
### TextIcons

常用符号字符串，可以拼进文本里：`TextIcons.CHECK`（✔）、`CROSS`（✘）、`STAR`（★）、`HEART`（❤）、`WARNING`（⚠）、`ARROW_RIGHT`（→）等。显示效果取决于游戏字体。

<a id="wiki-section-44"></a>
### DamageSource 和 ParticleOptions

| 方法 | 说明 |
|---|---|
| `DamageSource.of(level, typeId)` / `DamageSource.of(level, typeId, entity)` | 按伤害类型 ID 创建伤害来源，可以指定造成伤害的实体 |
| `DamageSource.generic(level)`、`magic`、`explosion`、`fall`、`lava`、`lightning`、`drowning`、`starvation`、`outOfWorld` | 常用伤害来源 |
| `ParticleOptions.of(id)` | 不需要参数的粒子，例如 `'minecraft:cloud'` |
| `ParticleOptions.of(id, options)` | 需要参数的粒子。`options` 按该粒子在原版数据里的格式填写 |

<a id="wiki-section-45"></a>
### Performance

测量代码运行了多久，精度可以到毫秒以下。

| 方法 | 说明 |
|---|---|
| `Performance.now()` | 当前时间戳，单位毫秒，带小数 |
| `Performance.time(fn)` | 运行一次 `fn`，返回耗时（毫秒） |
| `Performance.bench(fn, runs)` | 运行 `runs` 次，返回 `{ runs, total, mean, min, max }` |
| `Performance.start(label)` | 开始一个计时器 |

计时器的 `mark(label)` 记录一个阶段，`end()` 停止计时，`report()` 返回各阶段耗时。`mark` 和 `end` 会返回新的计时器，要把结果重新赋值：

```javascript
const ms = Performance.time(() => {
  // 要测的代码
})
console.info('耗时 ' + ms + ' ms')

let t = Performance.start('reload')
// ……第一阶段……
t = t.mark('parse')
// ……第二阶段……
t = t.mark('run').end()
console.info(t.report())
// { label: 'reload', total: 45.2, marks: [{ label: 'parse', at: 10.1, sincePrev: 10.1 }, ...] }
```

`Performance` 不会自动监视脚本。想找出慢的事件回调，用 `Performance.time` 把它包起来测。

<a id="wiki-section-46"></a>
## 运行环境

<a id="wiki-section-47"></a>
### Platform

| 方法 | 说明 |
|---|---|
| `Platform.isClient()` | 是否在客户端 |
| `Platform.isDevelopment()` | 是否是开发环境 |
| `Platform.getMcVersion()` | Minecraft 版本 |
| `Platform.getLoaderId()` / `Platform.getLoaderVersion()` | 加载器 ID（`neoforge`、`fabric`）和版本 |
| `Platform.isLoaded(modId)` | 某个模组是否已加载 |
| `Platform.getInfo(modId)` | 模组信息：`id`、`name`、`version` |
| `Platform.getList()` | 已加载的模组 ID 列表 |
| `Platform.capabilities()` | 当前平台支持的功能列表，例如 `'nbt-binary-io'` |

```javascript
if (Platform.isLoaded('jei')) console.info('JEI 已安装')
```

<a id="wiki-section-48"></a>
## 测试脚本

<a id="wiki-section-49"></a>
### Test

只在 `test_scripts` 里可用，用 `/nekojs test` 运行。所有方法都返回 `Test` 本身，可以链式调用。

| 方法 | 说明 |
|---|---|
| `Test.assertTrue(cond, msg)` / `Test.assertFalse(cond, msg)` | 条件为真 / 为假 |
| `Test.assertEquals(expected, actual, msg)` | 两个值相等 |
| `Test.assertNotNull(value, msg)` | 值不是 `null` |
| `Test.assertThrows(fn, msg)` | 运行 `fn` 时会报错 |
| `Test.pass(msg)` / `Test.fail(msg)` | 直接记一次通过 / 失败。`fail` 会抛出错误 |
| `Test.section(name)` | 在日志里分组 |
| `Test.summary()` | 输出汇总 |
| `Test.passed()` / `Test.failed()` | 通过 / 失败的次数 |

```javascript
// test_scripts/basic.js
Test.section('items')
  .assertEquals('minecraft:stone', Item.of('minecraft:stone').getId(), '石头 ID')
  .summary()
```

<a id="wiki-section-50"></a>
## 原版类

下面这些名字直接对应 Minecraft 的类，可以取常量、调用静态方法或创建对象：

| 绑定 | 用途 |
|---|---|
| `Blocks`、`Items` | 原版方块、物品常量 |
| `BlockPos` | 方块坐标 |
| `Direction` | 方向 |
| `Vec3` | 三维向量 |
| `AABB` | 碰撞箱 |
| `CompoundTag` | 原版 NBT 对象 |
| `DyeColor` | 染料颜色 |
| `SoundEvents` | 原版声音 |
| `ParticleTypes` | 原版粒子 |
| `EntityType` | 实体类型 |
| `MobEffects` / `MobEffectInstance` | 状态效果常量 / 状态效果实例 |
| `DamageTypes` | 伤害类型常量 |
| `Identifier` | 资源 ID（1.21.1 上对应 `ResourceLocation`） |
| `Component` / `MutableComponent` | 原版文本组件 |
| `TriState` | 三态值 TRUE / FALSE / DEFAULT（仅 26.x） |
| `Minecraft`、`Screen`、`Window`、`KeyMapping`、`InputConstants` | 客户端类，仅客户端脚本。用 `Minecraft.getInstance()` 取得游戏实例 |

需要其他 Java 类时，可以用 `java:` 导入，见 [模块系统](module-system_cn)。部分 API 也通过 [`nekojs/api/stable` 等模块](module-system_cn#wiki-api-modules) 提供，只在模块里导出的成员不会出现在全局。

<a id="wiki-section-51"></a>
## 下一步

- [事件参考](event-reference_cn)：所有可用事件。
- [数据与状态](data-and-state_cn)：怎样选择保存和同步数据的方式。
- [配方系统](recipe-system_cn)：`event.recipes` 和配方类型。
- [模块系统](module-system_cn)：`java:` 导入和多文件模块。

<!-- wiki-nav -->

---

[上一篇: 脚本属性](script-properties_cn) · [目录](Home) · [下一篇: 数据、状态与持久化](data-and-state_cn)
