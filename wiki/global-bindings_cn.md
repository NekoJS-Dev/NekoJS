<!-- wiki-page: global-bindings; locale: cn -->

> **中文** · [English](global-bindings_us)

<a id="wiki-section-1"></a>
# 全局绑定

全局绑定是 NekoJS 注入到脚本顶层作用域的对象/函数。你不用 `require`，直接写名字就能用：`Item.of(...)`、`Ingredient.of(...)`、`Fluid.water()` 等。

> 本页是参考手册。每个绑定的方法签名都列出，括号里是参数，箭头后是返回值。

---

<a id="wiki-section-2"></a>
## 物品 / 物品栈

<a id="wiki-section-3"></a>
### `Item`

物品助手。`of`/`empty` 走助手函数，其余成员委托给 MC 的 `Item` 类（可直接访问原版物品常量）。

| 方法 | 说明 |
|---|---|
| `Item.of(id: string) → ItemStack` | 从 id 创建物品栈；支持 `"1x minecraft:stone"` 数量语法 |
| `Item.of(id: string, count: int) → ItemStack` | 带数量 |
| `Item.of(stack: ItemStack) → ItemStack` | 透传 |
| `Item.of(stack: ItemStack, count: int) → ItemStack` | 复制并改数量 |
| `Item.empty() → ItemStack` | `ItemStack.EMPTY` |

**`ItemStack` 扩展方法**（NekoJS 通过 Mixin 注入平台扩展）：

| 方法 | 说明 |
|---|---|
| `withCount(count) → ItemStack` | 返回指定数量的副本 |
| `copy() → ItemStack` | Minecraft 原版深拷贝，不重复注入 |
| `setCount(count)` | Minecraft 原版方法（void） |
| `setCountAndReturn(count) → ItemStack` | NekoJS 链式设置数量 |
| `getId() → string` | 物品 id |
| `getMod() → string` | 所属 mod id |
| `getBlock() → Block` | 对应方块（如果是方块物品） |
| `enchantById(id, level) → ItemStack` | 按 id 附魔（原版 `enchant()` 语义：写 ENCHANTMENTS 组件并按附魔的适用物品校验；附魔书请改写 `stored_enchantments` 组件，普通附魔对书不生效——与原版/KubeJS 一致） |
| `isEnchanted()` | Minecraft 原版附魔状态 |
| `hasGlint() / setGlint(bool)` | NekoJS 光效语义 |
| `hasEnchantment(id, level) → boolean` | 是否有某附魔 |
| `matches(other) → boolean` | 物品匹配 |
| `asIngredient() → Ingredient` | 转成配料 |
| `weakNBT() / strictNBT()` | NBT 匹配模式 |
| `pdata() → PersistentDataJS` | 读写 ItemStack 的自定义持久化数据；数据存入原版 `CUSTOM_DATA` 组件，清空后移除该组件；不使用 NekoJS 实体 PData 同步包，但 ItemStack 位于库存等原版同步路径时可随原版组件同步 |

<a id="wiki-section-4"></a>
### `PersistentDataJS` 的实体/玩家边界

`entity.pdata()`（玩家继承实体扩展）写入实体的 `NekoJSPersistentData` 子键，并在服务端写入后自动进入下一次 server tick 的同步队列。`sync()` 可立即向跟踪该实体的客户端发送当前快照。客户端脚本读取的是只读 mirror；任何 `put*`、`remove`、`merge`、`edit`、`clear`、`replaceTag`、`markDirty` 或 `sync` 写入口都会抛出异常，不会伪装成成功。

同步包按 entity id 和单调 revision 去重。实体离开世界时发送空快照清理客户端 mirror；id 复用继续服务器当前生命周期内的 revision 序列，防止延迟旧包重新写回旧实体数据。断线、切世界和切维度清理客户端 mirror；SERVER/CLIENT reload 不清理它。停服会清理服务端 dirty/revision 账本，但不改变存档格式或 `NekoJSPersistentData` key。

<a id="wiki-section-5"></a>
### `Items`

`net.minecraft.world.item.Items` —— 原版物品注册表常量。如 `Items.STONE`、`Items.DIAMOND_SWORD`。

<a id="wiki-section-6"></a>
### `Ingredient`

配料助手。**组合绑定**（与 `Item` 同模式）：上表工厂方法走助手，其余成员委托给原版 `Ingredient` 类的静态成员（如 `Ingredient.empty()`）。

| 方法 | 说明 |
|---|---|
| `Ingredient.of(...values) → IngredientJS` | 从任意混合的 id/物品栈/配料构建（OR 组合） |
| `Ingredient.item(id: string) → IngredientJS` | 单个物品 |
| `Ingredient.tag(id: string) → IngredientJS` | 标签（自动加 `#`）；1.12.2 用 `Ingredient.ore(name)` |
| `Ingredient.any(...ingredients) → IngredientJS` | OR 组合 |
| `Ingredient.all() → IngredientJS` | 匹配所有已注册物品的 wildcard。NeoForge 用 live `AnyHolderSet`（注册表变化即时反映）；1.12.2 枚举当前注册表快照（脚本注册后新增的物品不包含） |
| `Ingredient.not(ingredient) → IngredientJS` | 取反（匹配除该 ingredient 外所有物品）。NeoForge 1.21.1/26.x 支持（`DifferenceIngredient`）；**1.12.2 不支持**（无原生 negation 类型），会抛清晰异常 |

**`IngredientJS` 实例方法**：

| 方法 | 说明 |
|---|---|
| `or(id\|NekoId\|Item\|ItemStack\|Ingredient\|IngredientJS) → IngredientJS` | 并集 |
| `and(ingredient)` / `intersect(ingredient) → IngredientJS` | 交集 |
| `except(ingredient)` / `subtract(ingredient) → IngredientJS` | 差集 |
| `asIngredient() → Ingredient` | 转 MC `Ingredient` |
| `asStack() → ItemStack` | 第一个物品栈 |
| `withCount(int) → SizedIngredientJS` | 带数量 |
| `matches(ItemStack\|Ingredient)` / `test(...) → boolean` | 匹配 |
| `first() → ItemStack` | 第一个物品栈 |
| `stacks()` / `displayStacks() → ItemStack[]` | 所有物品栈 |
| `isEmpty() → boolean` | 是否为空 |
| `unwrap() → Ingredient` | 原始 MC `Ingredient` |

---

<a id="wiki-section-7"></a>
## 流体

<a id="wiki-section-8"></a>
### `Fluid`（仅 NeoForge）

> 当前 Fabric 26.x 没有注册 `Fluid`、`FluidStack`、`FluidIngredient`、`Fluids` 或流体配方面。以下 API 只适用于 NeoForge 1.21.1/26.x；1.12.2 资料属于 legacy 分支。

工厂方法走助手，其余成员委托给原版 `Fluid` 类的静态成员。

| 方法 | 说明 |
|---|---|
| `Fluid.of(value) → FluidStack` | 从 id / `{fluid, amount}` / 流体对象构建 |
| `Fluid.of(value, amount: int) → FluidStack` | 带数量（必须 > 0） |
| `Fluid.water() → FluidStack` / `Fluid.water(amount)` | 水流体栈（默认一桶） |
| `Fluid.lava() → FluidStack` / `Fluid.lava(amount)` | 岩浆流体栈 |
| `Fluid.empty() → FluidStack` | `FluidStack.EMPTY` |
| `Fluid.ingredient(...values) → FluidIngredientJS` | 构建流体配料（OR 组合） |
| `Fluid.sizedIngredient(value)` / `Fluid.sizedIngredient(value, amount) → SizedFluidIngredient` | 带数量的流体配料 |

<a id="wiki-section-9"></a>
### `FluidIngredient`

**组合绑定**（仅 NeoForge；1.12.2 无同名原版类，为普通绑定且 `of` 返回 `List<FluidStack>` 降级语义）：工厂方法走助手，其余成员委托给 NeoForge `FluidIngredient` 类的静态成员。

| 方法 | 说明 |
|---|---|
| `FluidIngredient.of(...values) → FluidIngredientJS` | OR 组合 |
| `FluidIngredient.fluid(id: string) → FluidIngredientJS` | 单个流体 |
| `FluidIngredient.tag(id: string) → FluidIngredientJS` | 标签 |
| `FluidIngredient.sized(value)` / `FluidIngredient.sized(value, amount) → SizedFluidIngredient` | 带数量 |

`FluidIngredientJS` 实例有 `.or(...)`、`.isEmpty()` 等。

<a id="wiki-section-10"></a>
### `FluidAmounts`

流体量常量。如 `FluidAmounts.BUCKET`（一桶的量）。

<a id="wiki-section-11"></a>
### `Fluids` / `FluidStack`

`net.minecraft.world.level.material.Fluids`（原版流体常量）和 MC `FluidStack` 类的直接访问。

---

<a id="wiki-section-12"></a>
## 文本组件

<a id="wiki-section-13"></a>
### `Text`

| 方法 | 说明 |
|---|---|
| `Text.of(text: string) → $TextValue` | 不可变字面量文本 |
| `Text.empty() → $TextValue` | 空文本 |
| `Text.translatable(key: string, ...args) → $TextValue` | 不可变翻译键文本 |
| `Text.translateWithFallback(key: string, fallback: string, ...args) → $TextValue` | 翻译键带 fallback：键缺失时显示 fallback 字面量。1.12.2 不支持原生 fallback，直接显示 fallback |
| `Text.keybind(keybind: string) → $TextValue` | 按键绑定（如 `'key.attack'`），渲染时解析为玩家当前按键名 |
| `Text.score(name: string, objective: string) → $TextValue` | 记分板分数（`name` 可填 `'*'` 取触发者） |
| `Text.selector(pattern: string) → $TextValue` | 实体选择器（如 `'@p'`、`'@a[type=zombie]'`）。NeoForge 26.x 退化为字面量（API 限制），1.21.1/1.12.2 正常渲染 |
| `Text.ofValues(...values) → $TextValue` | 按顺序拼接文本参数 |
| `Text.join(separator, ...values) → $TextValue` | 用分隔符（`$TextValue` 或字符串）拼接多个值 |

<a id="wiki-section-14"></a>
### `$TextValue`（含富文本样式）

`$TextValue` 支持 `.append(...values)` 返回新值，以及 `.isEmpty()`。在需要原生组件的现有绑定位置（`displayClientMessage`、tooltip、书本等）可作为输入使用，会自动转成 MC 组件。

富文本（样式 / 点击 / 悬停）通过链式方法返回**带样式**的新 `$TextValue`（不可变、可继续链式）：

| 方法 | 说明 |
|---|---|
| `.bold([value = true])` / `.italic()` / `.underlined()` / `.strikethrough()` / `.obfuscated()` | 字体修饰，省略参数等同 `true` |
| `.color(color)` | 颜色：命名色（`'red'`）或 hex（`'#FF0000'`）。hex 仅 1.15+ 生效，1.12.2 只认 16 个命名色 |
| `.black()` / `.darkBlue()` / `.darkGreen()` / `.darkAqua()` / `.darkRed()` / `.darkPurple()` / `.gold()` / `.gray()` / `.darkGray()` / `.blue()` / `.green()` / `.aqua()` / `.red()` / `.lightPurple()` / `.yellow()` / `.white()` | 16 色 KubeJS 风格快捷方法，零参数，等价于 `.color('<命名色>')`（`.red()` ≡ `.color('red')`） |
| `.insertion(text)` | 点击插入聊天框（仅聊天框内有效） |
| `.font(id)` | 字体 id（`'mymod:custom'`）。1.12.2 不支持，忽略 |
| `.click(action, value)` | 点击事件：`runCommand` / `suggestCommand` / `openUrl` / `openFile` / `copyToClipboard` / `changePage` |
| `.hover(text: $TextValue)` | 悬停显示文本 |
| `.append(...values)` | 追加子组件（每个子组件可独立带样式） |

> `copyToClipboard` 在 1.12.2 不存在（会被忽略）；`changePage` 的 value 为页码字符串。

```javascript
// 红色加粗、点击执行命令、悬停显示提示
const msg = Text.of('点我执行')
  .bold().color('red')
  .click('runCommand', '/give @s diamond')
  .hover(Text.of('点击获得钻石'))

player.displayClientMessage(msg, false)

// 拼接不同样式的子串
const mixed = Text.ofValues(
  Text.of('普通 ').color('white'),
  Text.of('粗体').bold(),
  Text.of(' 斜体').italic()
)
```

> 样式方法不会修改原 `$TextValue`（不可变），而是返回新的带样式值；链式调用会在已有样式基础上合并。

---


<a id="wiki-section-15"></a>
## 数据读写

<a id="wiki-section-16"></a>
### `NbtIO`（已移除）

旧的 NeoForge-only `NbtIO` native binding 已由 portable `NBT.read`/`NBT.write` 取代。新接口不暴露 `CompoundTag`/`NBTTagCompound`，并在 NeoForge 1.21.1、NeoForge 26.x 与 Cleanroom 1.12.2 上提供相同行为。

旧接口把相对路径解析到 `<gameDir>/nekojs/`，新接口只允许 `<gameDir>/nekojs/data/`。升级已有脚本时，需要先把原 `NbtIO` 文件移动到 `nekojs/data/`；这是有意收紧的 filesystem 安全边界，`NBT.read` 不会自动搜索或迁移旧目录中的文件。

```javascript
// 旧接口：NbtIO.write("player.nbt", nativeCompound)
NBT.write("player.nbt", {name: "neko", level: 12})

const player = NBT.read("player.nbt")
if (player !== null) console.log(player.toSnbt())
```

<a id="wiki-section-17"></a>
### `NBT`

portable immutable NBT value builder。不暴露 `CompoundTag`/`NBTTagCompound`，也不自动转换到原生网络、persistent data 或 item NBT 边界。

| 方法 | 说明 |
|---|---|
| `NBT.of(value: NbtInput) → $NbtValue` | 严格构建 string、number、同类数组或 object compound |
| `NBT.byte/short/int(number) → $NbtValue` | 显式整数宽度 |
| `NBT.long(decimal: string) → $NbtValue` | 精确 signed long，避免 JS number 精度损失 |
| `NBT.float/double(number) → $NbtValue` | 显式浮点宽度 |
| `NBT.byteArray/intArray(number[]) → $NbtValue` | 原生 NBT primitive arrays |
| `NBT.toSnbt(value) → string` | deterministic SNBT |
| `NBT.parse(snbt: string) → $NbtValue` | 解析 SNBT 字符串（与 `toSnbt` 互逆）；语法错误抛 `INVALID_NBT` |
| `NBT.toObject(value) → object` | 把 `$NbtValue` 递归转成普通 JS 对象（Map→Object / List→Array / scalar 直接取出） |
| `NBT.fromObject(value) → $NbtValue` | 把普通 JS 对象递归转成 `$NbtValue`（布尔→byte、整数→int/long、浮点→double） |
| `NBT.read(path: string) → $NbtValue \| null` | 读取压缩 binary NBT；文件不存在时返回 `null` |
| `NBT.write(path: string, value: NbtInput) → void` | 原子写入压缩 binary NBT；root 必须是 compound |
| `NBT.compound() → CompoundBuilder` | 创建空 compound 链式构建器（`put`/`putByte`/`putCompound`/`putList`/`contains`/`size`/`build`），`build()` 后得 `$NbtValue` |
| `$NbtValue.kind()` / `.scalar()` | 查询 tag kind 与 scalar 值；long scalar 是 decimal string |
| `$NbtValue.values()` / `.entries()` | 查询 list elements 或 compound entries |
| `$NbtValue.toSnbt()` | deterministic SNBT |

`NbtInput` 只接受字符串、有限数字、`$NbtValue`、同类 nested array 和 plain object。`null`、boolean、function、host object、cycle 与 heterogeneous list 会被拒绝。未经显式 builder 的整数规范为 `INT`，其他 number 规范为 `DOUBLE`；`LONG_ARRAY` 暂不属于 portable baseline。

`NBT.parse` 支持完整 Mojang SNBT 语法：compound（`{k:v}`，含引号键）、list（`[...]`）、byte array（`[B;...]`）、int array（`[I;...]`）、带后缀数字（`5b`/`5s`/`5l`/`5.0f`/`5.0d`）、无后缀整数（int 范围内按 `INT`，否则 `LONG`）、浮点（`DOUBLE`）、`true`/`false`（→ byte 1/0）、引号/无引号字符串与转义序列。`toSnbt` 与 `parse` 可用于值的往返，但输出会规范化，不能保证保留输入字符串的空白或引号写法。

binary NBT 持久化固定在 `<gameDir>/nekojs/data/`。path 必须是 forward-slash relative path；空路径、绝对路径、盘符、反斜杠、`.`/`..` segment、symlink、junction 与 reparse point 都会被拒绝。写入使用同级临时文件、强制刷盘和 atomic replacement，不会退回非原子覆盖。

默认限制为 3 MiB compressed file、8 MiB decoded binary、64 层深度、10,000 个节点和 10,000 个 primitive-array 元素。native `LONG_ARRAY`、standalone `END`、非有限浮点数和损坏文件会 fail closed。相关稳定错误包括 `INVALID_NBT`、`NBT_LIMIT_EXCEEDED`、`NBT_PATH_FORBIDDEN`、`NBT_FILE_TOO_LARGE`、`NBT_IO_ERROR` 与 `NBT_ATOMIC_WRITE_FAILED`。可通过 `Platform.capabilities().includes("nbt-binary-io")` 检测支持。

<a id="wiki-section-18"></a>
### `Registry`

只读注册表查询。`Registry.get(registryId)` 返回 `RegistryView`，注册表不存在时 `exists()` 为 `false`。所有方法只返回基础类型（字符串/布尔/数组），不暴露 MC 原生对象。

| 方法 | 说明 |
|---|---|
| `Registry.get(registryId) → RegistryView` | 获取注册表只读视图，如 `"minecraft:item"` |
| `view.exists() → boolean` | 注册表是否存在 |
| `view.all() → string[]` | 注册表内所有条目 id（含命名空间） |
| `view.has(id) → boolean` | 注册表内是否存在指定 id |
| `view.tag(tagId) → string[]` | 指定 tag 下的所有条目 id |
| `view.dataMapIds() → string[]` | 该注册表已注册的所有 data map 类型 id（如 `neoforge:furnace_fuels`） |
| `view.dataMapValue(dataMapTypeId, id) → string \| null` | 读取指定条目的 data map 值（JSON 字符串）；不存在返回 `null` |

<a id="wiki-section-19"></a>
### `JsonIO`

稳定 JSON 值不暴露 Gson、`Map`/`List` 或原始 filesystem path。持久化只允许 `nekojs/data/` 下的受限 JSON 文件。

| 方法 | 说明 |
|---|---|
| `JsonIO.parse(json: string) → $JsonValue` | 严格解析 JSON |
| `JsonIO.toString(value: JsonInput) → string` | 紧凑 JSON |
| `JsonIO.toPrettyString(value: JsonInput) → string` | 两空格缩进 JSON |
| `JsonIO.read(path: string) → $JsonValue \| null` | 读取 data 文件；不存在时返回 `null` |
| `JsonIO.write(path: string, value: JsonInput) → void` | 原子替换 data 文件，自动创建父目录 |
| `$JsonValue.toString() → string` | 紧凑 JSON |
| `$JsonValue.toPrettyString() → string` | 两空格缩进 JSON |

`JsonInput` 可以是 `null`、布尔、有限数字、字符串、数组、普通对象或 `$JsonValue`。重复对象键、非有限数字、函数、宿主对象、循环和超限值会被拒绝。

`read`/`write` path 必须是 forward-slash relative path，例如 `settings/ui.json`；空路径、绝对路径、反斜杠、`.`/`..` segment、盘符和任意 symlink 都会被拒绝。`write` 使用 UTF-8 两空格 JSON 与 atomic move，不会退回非原子覆盖。原始 Gson `parseRaw` 不属于此 API。

---

<a id="wiki-section-20"></a>
## 工具

<a id="wiki-section-21"></a>
### `VillagerTrades`（NeoForge）

NeoForge 支持在服务端脚本中追加交易。Fabric 当前没有等价的交易注册表 mutation；调用该能力会以 unavailable 原因拒绝，不应在 Fabric 脚本中使用。

服务端脚本通过 `ServerEvents.tradeDeclaration` 声明交易。收集期不修改 live registry；整批通过 preflight 与 commit 后由平台 Adapter 应用。无效声明拒绝整批，不部分写入。NeoForge 26.x 使用可 reload 的交易注册表；1.21.1 使用静态交易池。

```javascript
ServerEvents.tradeDeclaration(event => {
  event.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald',
    result: '5x minecraft:apple',
    maxUses: 12, xp: 2, priceMultiplier: 0.05,
  })
})
console.info(VillagerTrades.query().describe())
```

农民等职业 id 为 `minecraft:<职业>/level_1..5`。流浪商人 id 有版本差异：26.x 使用 `minecraft:wandering_trader/buying` 等 trade-set key；1.21.1 使用 `minecraft:wandering_trader/level_1|level_2`（别名 `buying|common`→1、`uncommon|rare`→2）。Adapter 在 preflight 校验目标。query 只读且绑定 generation；过期/不可用原因通过 `statusReason` 明确返回。

普通遗漏记为 unrestored；`ServerEvents.tradeReload(event => event.declareObsolete(id))` 显式 retire 某交易集。同一交易集不可混用旧新写入路径。旧 `VillagerTrades.add/pendingCount` 仅 canonical NeoForge 26.x 暂留，等待迁移确认；**1.21.1 已只有 query/describe**。Fabric 无 `VillagerTrades` global，非空交易声明会明确拒绝。

<a id="wiki-section-22"></a>
### `EntitySelectors`

纯代码构造原版实体选择器（等价 `@a[...]` 选择器引擎，无需拼字符串；服务端脚本）：

```javascript
const cows = EntitySelectors.find(level,
  EntitySelectors.create(b => b.type('minecraft:cow').distance(0, 32).limit(5)), x, y, z)
const nearest = EntitySelectors.find(level, EntitySelectors.nearestPlayer().create(), px, py, pz)
```

构建器支持 `type/typeTag/inverse/name/gamemode/team/tag`（等级 xp）、`distance(min,max)`、
`x/y/z/dx/dy/dz` 体积框、`limit`、`order({Arbitrary,Nearest,Furthest,Random})`；预设
`allPlayers/allEntities/nearestPlayer/nearestEntity/randomPlayer/randomEntity`。排序类选择器
记得传锚点坐标。仅 NeoForge（26.x/1.21.1）。

<a id="wiki-section-23"></a>
### `PostEffects`（客户端，仅 NeoForge 26.x/1.21.1）

> Fabric 当前没有注册 `PostEffects`，也没有对应的自定义 GLSL runtime 面。

客户端后处理链：`PostEffects.set('minecraft:invert')` / `clear()` / `toggle(id)` / `current()`。
generation 定义通过 `ClientEvents.postEffects` 声明，不再使用已删除的 `PostEffects.register/unregister/has`。运行时动作仍放在 binding：

```javascript
ClientEvents.postEffects(event => {
  event.register('mymod:blur', { blurRadius: 8, blurRounds: 1 })
})
ClientEvents.playerTickPost(() => {
  if (PostEffects.isAvailable('minecraft:invert') && !PostEffects.active) {
    PostEffects.set('minecraft:invert')
  }
})
```

`hasDefinition(id)`、`installed()`、`activeGeneration()` 回读已提交定义，不代表实际可出图。当前 `isAvailable(id)` 要求资源包 effect；没有该资源的声明 id 不能由 `set` 激活。NeoForge 26.x 的 ShaderManager hook 可为 resource-backed id 提供声明链/Shader 源（已实测覆盖 `minecraft:invert` 的声明链），不承诺任意新 inline id 可激活。**1.21.1 仅 resources-only**，未接 runtime declared chain hook。候选 preflight 失败保留旧 generation；binding teardown 本身不清屏。预设是否存在取决于当前资源包，应检查 availability，不假定所有列出的名字均存在。

注意：相机实体视觉（末影人/蜘蛛/苦力怕）会覆盖或清除脚本设置的效果。

<a id="wiki-section-24"></a>
### `once` / `clearOnce`

进程级 run-once 守卫：`once(key, callback)` 只在某个 key 第一次使用时执行回调，
**标记刻意跨脚本 reload 存活**（进程生命周期）。适合「只初始化一次」的外部副作用
（写入文件、注册外部系统等不随 reload 回滚的东西）。

| 调用 | 说明 |
|---|---|
| `once('myInit', () => ...)` | 首次调用执行回调并透传返回值；此后同 key 调用为空操作 |
| `clearOnce('myInit') → boolean` | 重新武装单个 key，返回标记是否原本存在 |
| `clearOnce()` | 清空全部标记 |

```javascript
once('warmup', () => console.info('只在第一次 reload 后打印'))
```

<a id="wiki-section-25"></a>
### `ClientData` / `clientData`

服务端→客户端的轻量 KV 数据通道（值为 JSON 类型：字符串/数字/布尔/对象/数组/null，
单个值序列化上限 32KB；同 key 重复推送后者覆盖；退出世界/断线时客户端侧清空）。
服务端脚本用 `ClientData` 推送，客户端脚本用只读的 `clientData` 读取——是后续 HUD
脚本的数据来源。

```javascript
// server_scripts/boss.js —— 服务端推送
ClientData.sync('boss_hp', { name: '灾厄魔像', hp: 0.42 })
ClientData.syncTo(player, 'personal_quest', { stage: 3 })

// client_scripts/hud.js —— 客户端读取
const info = clientData.get('boss_hp')
if (clientData.has('boss_hp')) { /* ... */ }
```

<a id="wiki-section-26"></a>
### `Capabilities`

NeoForge 1.21.1/26.x 与 Fabric 26.x 均注册此 Binding。它创建存储对象，供 `CapabilityEvents.register` 的 provider 返回；provider 注册只在 STARTUP 提交一次，普通 SERVER/CLIENT reload 不会重注册。注册方法及原生 context 差异见 [事件参考](event-reference_cn#wiki-section-15)。

| 方法 | 说明 |
|---|---|
| `Capabilities.itemHandler(size)` | N 格物品栏；格数不能为负数 |
| `Capabilities.itemHandler(size, onChange)` | 增加 owner 变更通知 |
| `Capabilities.energyStorage(capacity, maxReceive, maxExtract)` | 能量容量、单次插入/提取上限；参数不能为负数，不会自动按 tick 累计限流 |
| `Capabilities.energyStorage(capacity, maxReceive, maxExtract, onChange)` | 增加 owner 变更通知 |
| `Capabilities.fluidTank(capacity)` | 单槽流体罐；输入容量为 mB，不能为负数 |
| `Capabilities.fluidTank(capacity, onChange)` | 增加 owner 变更通知 |

NeoForge 26.x 返回原生 `ItemStacksResourceHandler`、`SimpleEnergyHandler`、`FluidStacksResourceHandler` 存储，参与外层事务提交与回滚，不是立即提交的旧接口桥接。1.21.1 返回 `ItemStackHandler`、`EnergyStorage`、`FluidTank`，使用原生 `simulate` 语义，不具有 26.x 外层事务接口。Fabric 返回 `FabricItemStorage`、`FabricEnergyStorage`、`FabricFluidStorage`，使用 Fabric Transfer 事务；物品/流体分别实现 `Storage<ItemVariant>` / `Storage<FluidVariant>`。

`onChange` 在 NeoForge 26.x/Fabric 的最终事务提交后执行，回滚不通知；1.21.1 仅在真实变更后通知，模拟传输不通知。owner 应据此标记存档脏状态或安排同步。一个操作涉及多个物品槽时可能有多个通知，不应把通知次数当成传输次数。

`fluidTank(1000)` 表示一桶容量；NeoForge 的流体传输量为 mB，Fabric 为 droplets（每桶 81000，即每 mB 81）。返回对象的 `getCapacity()`（脚本可读作 `capacity`）和 transfer 参数使用各平台原生单位，不能直接把 Fabric 数量当成 mB。

存储必须由各具体 owner 保留。不要在 provider 每次查询时创建新的空 handler，也不要把一个可变 handler 共享给所有同类型实体、物品或方块实体。owner 负责用节点原生 NBT 或 `ValueInput`/`ValueOutput` 接口保存、恢复并同步状态；注册本身不自动持久化。坏存档读取应严格失败并保留当前 live 值，不能以空存储覆盖。Fabric 工厂提供的 `readValue` / `writeValue` 已严格验证保存字段与容量；禁止在未提交的 transfer 事务中 load/save，最终提交的 `onChange` 回调允许保存。其他 native owner 同样应在已提交状态保存，并按其原生存档接口实现严格读取，不能仅靠 provider 注册获得这些保证。

Fabric 额外提供以下标准查询入口，能力名为 `'item'` / `'fluid'` / `'energy'`：

| 方法 | 说明 |
|---|---|
| `Capabilities.blockLookup(capability)` | 标准方块 Lookup；context 为可空 `Direction` |
| `Capabilities.entityLookup(capability)` | NekoJS 实体 Lookup；context 为可空 `Direction` |
| `Capabilities.itemLookup(capability)` | 标准物品 Lookup；context 为 `ContainerItemContext` |
| `Capabilities.getBlock(level, position, capability, direction)` | 查询指定位置；无 provider 或 provider 拒绝时返回 `null` |
| `Capabilities.getEntity(entity, capability, direction)` | 查询具体实体 |
| `Capabilities.getItem(stack, capability, context)` | 查询具体物品栈及其容器 context |

方块和物品的 item/fluid Lookup 使用 Fabric Transfer 的标准 `ItemStorage`/`FluidStorage` 实例；实体 Lookup 和能量 Lookup 由 NekoJS 提供。Fabric 没有标准跨模组能量 API，这里的 `FabricEnergyHandler` 不等同于其他模组的能量接口。自定义 Lookup 的查询仍走该 Lookup 的原生 `find(...)`。

所有 provider 均可用 `null` 拒绝当前查询；provider 异常、类型不匹配、重复注册、非法 context 和提交后再次注册都保留带 `NEKO-` 代码的失败。

<a id="wiki-section-27"></a>
### `Utils`

| 方法 | 说明 |
|---|---|
| `Utils.randomInt(maxExclusive) → int` / `Utils.randomInt(min, maxExclusive) → int` | 随机整数 |
| `Utils.randomDouble() → double` / `Utils.randomDouble(max)` / `Utils.randomDouble(min, max)` | 随机浮点 |
| `Utils.chance(probability) → boolean` | 以给定概率返回 true |
| `Utils.isArray(value)` / `Utils.isList(value)` / `Utils.isMap(value) → boolean` | 类型判断 |

<a id="wiki-section-28"></a>
### `Color`

| 方法 | 说明 |
|---|---|
| `Color.rgb(r, g, b) → int` | 不透明 ARGB |
| `Color.argb(a, r, g, b) → int` | ARGB |
| `Color.alpha(color)` / `Color.red(color)` / `Color.green(color)` / `Color.blue(color) → int` | 提取通道 |
| `Color.hex(color) → string` | `#RRGGBB` |
| `Color.hexArgb(color) → string` | `#AARRGGBB` |
| `Color.parse(value) → int` | 解析 `#RRGGBB` 或 `#AARRGGBB` |

<a id="wiki-section-29"></a>
### `Time`

时间常量与转换（基于 tick：1 秒 = 20 tick）。

| 成员 | 说明 |
|---|---|
| `Time.SECOND` = 20 / `Time.MINUTE` = 1200 / `Time.HOUR` = 72000 | tick 常量 |
| `Time.seconds(n)` / `Time.minutes(n)` / `Time.hours(n) → int` | 转 tick |
| `Time.parseTime(str) → long` | 解析时间字符串为 tick：`"5s"→100`、`"10m"→12000`、`"2h"→144000`、`"100t"→100`、`"250ms"→5` |
| `Time.parseMs(str) → long` | 同样的单位，但返回毫秒 |

<a id="wiki-section-30"></a>
### `UUID`

| 方法 | 说明 |
|---|---|
| `UUID.random() → UUID` | 随机 UUID |
| `UUID.fromString(value) → UUID` | 解析 |
| `UUID.fromName(value) → UUID` | 基于名字的 UUID（v3） |

<a id="wiki-section-31"></a>
### `StringUtils`

| 方法 | 说明 |
|---|---|
| `StringUtils.isBlank(s)` / `StringUtils.isEmpty(s) → boolean` | 空白判断 |
| `StringUtils.capitalize(s)` / `StringUtils.decapitalize(s) → string` | 大小写转换 |
| `StringUtils.snakeCase(s)` / `StringUtils.camelCase(s) → string` | 命名风格转换 |

<a id="wiki-section-32"></a>
### `ID`

返回 `NekoId`（NekoJS 的资源标识抽象）。

| 方法 | 说明 |
|---|---|
| `ID.of(value)` / `ID.of(namespace, path) → NekoId` | 创建 |
| `ID.namespace(id)` / `ID.path(id)` / `ID.asString(id) → string` | 访问器 |
| `ID.asString(id) → string` | 将 ID 对象转为字符串 |

<a id="wiki-section-33"></a>
### `Performance`

用户主动调用的性能检测工具，提供**高精度**（亚毫秒，基于单调时钟）计时能力。补齐脚本侧缺失的 `performance.now()`——此前脚本只有毫秒精度的 `Date.now()`，`process.hrtime()` 也是基于它的伪高精度。

| 方法 | 说明 |
|---|---|
| `Performance.now() → number` | 单调时钟当前时间戳（毫秒，`double`，亚毫秒精度） |
| `Performance.time(fn) → number` | 执行 `fn` 一次，返回耗时毫秒 |
| `Performance.bench(fn, runs) → object` | 执行 `fn` `runs` 次，返回统计 `{ runs, total, mean, min, max }`（毫秒；含一次 warmup 避免冷启动偏置） |
| `Performance.start(label?) → $PerfTimer` | 开始一个标签计时器，返回 `$PerfTimer` 句柄 |

<a id="wiki-section-34"></a>
#### `$PerfTimer`（链式标签计时器）

不可变值类型——`mark` 返回附加一个标记点的新实例（链式）；`end` 冻结计时基准。

| 方法 | 说明 |
|---|---|
| `.mark(label) → $PerfTimer` | 记录一个中间标记点，返回新实例（链式） |
| `.end() → $PerfTimer` | 冻结到当前时刻为基准，返回新实例；之后 `elapsedMillis` 不再增长 |
| `.elapsedMillis() → number` | 从开始到当前的耗时毫秒（已 `end` 则到 end 时刻） |
| `.report() → object` | 结构化报告：`{ label, total, marks: [{ label, at, sincePrev }] }`（`at`=自开始毫秒，`sincePrev`=距上一标记点毫秒） |

<a id="wiki-section-35"></a>
#### 用法示例

```javascript
// 测单次函数耗时
const ms = Performance.time(() => {
  // 你的代码
});
console.log(`耗时 ${ms} ms`);

// 批量基准，定位热点
const stat = Performance.bench(() => {
  // 被测代码
}, 1000);
console.log(stat);
// { runs: 1000, total: 12.34, mean: 0.0123, min: 0.010, max: 0.015 }

// 标签计时器 + 多段 mark
let t = Performance.start('reload');
// ... 阶段 1 ...
t = t.mark('parse');
// ... 阶段 2 ...
t = t.mark('compile');
// ... 阶段 3 ...
t = t.mark('run');
t = t.end();
console.log(t.report());
// { label: 'reload', total: 45.2, marks: [
//   { label: 'parse',   at: 10.1, sincePrev: 10.1 },
//   { label: 'compile', at: 30.5, sincePrev: 20.4 },
//   { label: 'run',     at: 45.2, sincePrev: 14.7 } ] }
```

> 注意：`bench` 在快速操作上单次耗时可能低于计时器分辨率，`min`/`max` 仍可反映波动；如需更稳定结果，把被测代码循环多次后整体测一次，再除以循环数。`Performance` 是主动测量工具，不监听脚本/监听器执行——如需定位慢监听器，用 `Performance.time(() => ...)` 包裹可疑回调。

---

<a id="wiki-section-36"></a>
## 网络

<a id="wiki-section-37"></a>
### `Network`

跨客户端-服务端的脚本网络通信。payload 是 `(channel: string, data: CompoundTag)`。配合 [NetworkEvents](event-reference_cn) 使用。

| 方法 | 侧 | 说明 |
|---|---|---|
| `Network.sendToServer(channel, data)` | client | 从客户端发送到服务端 |
| `Network.sendToPlayer(player, channel, data)` | server | 从服务端发送到单个玩家 |
| `Network.sendToAll(channel, data)` | server | 从服务端发送到所有玩家 |

示例见 [事件参考 - NetworkEvents](event-reference_cn)。

---

<a id="wiki-section-38"></a>
## 配方 Schema 查询

<a id="wiki-section-39"></a>
### `RecipeSchema`

**只读**的配方 schema 查询 API（运行时内省）。

| 方法 | 说明 |
|---|---|
| `RecipeSchema.namespaces() → string[]` | 所有已知配方命名空间 |
| `RecipeSchema.types(ns) → string[]` | 某命名空间下的类型名（handler 方法名 + schema 类型） |
| `RecipeSchema.describe(ns, type) → object` | 返回 `{exists, hasHandler, type, idPrefix, fields[], constructors[], handlerFields?, handlerConstructors?}` |

```javascript
ServerEvents.started(event => {
  console.info(RecipeSchema.namespaces())           // ["minecraft", "create", ...]
  console.info(RecipeSchema.types('minecraft'))     // ["crafting_shaped", "smelting", ...]
  console.info(RecipeSchema.describe('minecraft', 'smelting'))
})
```

> 要**定义**新 schema，见 [配方系统 - 数据驱动 Schema](recipe-system_cn#wiki-section-13)。

---

<a id="wiki-section-40"></a>
## 环境 / 共享

<a id="wiki-section-41"></a>
### `global` 与 `shared`

- `global`：当前 `ScriptType` 内共享的状态容器；同类型普通 reload 后仍保留，不会自动跨 SERVER/CLIENT 共享。
- `shared`：同一个 NekoJS runtime 内显式跨脚本类型共享的状态容器。两者都只存在于进程内，不是网络同步，也不是存档持久化。

```javascript
// server_scripts/config.js
global.config = { version: 2 }
shared.serverReady = true

// client_scripts/hud.js
const serverReady = shared.serverReady
```

<a id="wiki-section-42"></a>
### `Platform`

绑定到 `Platform` 类，提供当前运行环境信息。具体能力可用 `Platform.capabilities()` 查询；Fabric 未实现的 API 不会因为平台对象存在而自动可用。

| 方法 | 说明 |
|---|---|
| `Platform.isClient() → boolean` | 是否客户端 |
| `Platform.isDevelopment() → boolean` | 是否开发环境 |
| `Platform.getMcVersion() → string` | MC 版本字符串 |
| `Platform.getLoaderId() → string` | 加载器 id（如 `neoforge`、`cleanroom`） |
| `Platform.getLoaderVersion() → string` | 加载器版本 |
| `Platform.isLoaded(modId) → boolean` | 指定 mod 是否已加载 |
| `Platform.getInfo(modId) → object` | 指定 mod 信息 |
| `Platform.getList() → array` | 已加载 mod 列表 |
| `Platform.capabilities() → Set<PlatformCapability>` | 平台能力（特性标志） |

---

<a id="wiki-section-43"></a>
## 客户端独有绑定（仅 CLIENT 脚本）

| 绑定 | 说明 |
|---|---|
| `Minecraft` | `net.minecraft.client.Minecraft` 实例 |
| `Screen` | `net.minecraft.client.gui.screens.Screen` 类 |
| `Window` | 客户端窗口 |
| `KeyMapping` | 按键绑定类 |
| `InputConstants` | GLFW 输入常量 |

---

<a id="wiki-section-44"></a>
## 仅 STARTUP 的特殊绑定

<a id="wiki-section-45"></a>
### `NativeEvents`（仅 STARTUP，NeoForge）

桥接原始 NeoForge 事件，不经过 NekoJS 事件组抽象。Fabric 当前不注册这个绑定；Cleanroom 资料属于独立 legacy 分支。

| 方法 | 说明 |
|---|---|
| `NativeEvents.onEvent(eventType, handler)` | 以 NORMAL 优先级监听 |
| `NativeEvents.onEvent(priority, receiveCancelled, eventType, handler)` | 完整形式 |
| `NativeEvents.onGenericEvent(genericClass, eventType, handler)` | 泛型事件 |

`eventType` 可以是字符串 FQN（嵌套类用 `$`）、`Class` 或 Graal `Value`。监听器返回 `true` 会被翻译为平台取消操作（仅可取消事件生效）。STARTUP 当前不能通过 `/nekojs reload startup` 重载；修改监听器需要重启游戏。

<a id="wiki-section-46"></a>
### `ScriptEvents`（仅 STARTUP）

在启动脚本里**声明自定义的服务端/客户端事件组**（跨加载器一致；事件载荷由触发方自己传）。详见 [事件扩展 - ScriptEvents](event-extensions_cn)。

```javascript
// startup_scripts
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))

// server_scripts
MyEvents.bossKilled(payload => console.info(payload.boss))
MyEvents.bossKilled.post({ boss: 'ender_dragon' })
```

---

<a id="wiki-section-47"></a>
## 仅 TEST 脚本的绑定

<a id="wiki-section-48"></a>
### `Test`

测试断言 API。

| 方法 | 说明 |
|---|---|
| `Test.pass(msg)` / `Test.fail(msg)` | 通过 / 失败（fail 会抛） |
| `Test.assertTrue(cond, msg)` / `Test.assertFalse(cond, msg)` | 布尔断言 |
| `Test.assertEquals(expected, actual, msg)` | 相等断言 |
| `Test.assertNotNull(value, msg)` | 非空断言 |
| `Test.assertThrows(callback, msg)` | 期望抛异常 |
| `Test.section(name)` | 分节 |
| `Test.summary()` | 打印汇总 |
| `Test.passed()` / `Test.failed()` | 通过/失败计数 |

---

<a id="wiki-section-49"></a>
## 原版类直接访问

以下绑定是 MC 原版类的直接访问，可作为构造器或常量源：

| 绑定 | 类 | 用途 |
|---|---|---|
| `Blocks` | `net.minecraft.world.level.block.Blocks` | 原版方块常量 |
| `BlockPos` | `net.minecraft.core.BlockPos` | 方块坐标 |
| `Direction` | `net.minecraft.core.Direction` | 朝向枚举 |
| `Vec3` | `net.minecraft.world.phys.Vec3` | 三维向量 |
| `AABB` | `net.minecraft.world.phys.AABB` | 轴对齐包围盒 |
| `CompoundTag` | `net.minecraft.nbt.CompoundTag` | NBT 复合标签 |
| `DyeColor` | `net.minecraft.world.item.DyeColor` | 染料颜色枚举 |
| `SoundEvents` | `net.minecraft.sounds.SoundEvents` | 原版声音事件 |
| `ParticleTypes` | `net.minecraft.core.particles.ParticleTypes` | 原版粒子类型 |
| `EntityType` | `net.minecraft.world.entity.EntityType` | 实体类型常量 |
| `MobEffects` | `net.minecraft.core.registries.MobEffects`（或对应） | 药水效果常量 |
| `MobEffectInstance` | Mob 效果实例类 | 药水效果实例 |
| `DamageTypes` | 伤害类型常量 | — |
| `Identifier` | `net.minecraft.resources.Identifier`（26.x）/ `ResourceLocation`（1.21.1） | 资源标识 |
| `TriState` | 三态枚举（NeoForge 与 Fabric；包名随 Minecraft 版本变化） | TRUE/FALSE/DEFAULT |
| `Component` | `net.minecraft.network.chat.Component` | 文本组件 |
| `MutableComponent` | `net.minecraft.network.chat.MutableComponent` | 可变文本组件（NeoForge 与 Fabric） |

> 这些类还可通过 `java:` 模块语法导入，详见 [模块系统](module-system_cn)。

<a id="wiki-section-50"></a>
## 下一步

- [事件参考](event-reference_cn) —— 所有可用事件。
- [配方系统](recipe-system_cn) —— `RecipeSchema`、`event.recipes.*` 详解。
- [模块系统](module-system_cn) —— `java:` 导入与多文件模块。

<!-- wiki-nav -->

---

[上一篇: 脚本属性](script-properties_cn) · [目录](Home) · [下一篇: Python 脚本](python-scripts_cn)
