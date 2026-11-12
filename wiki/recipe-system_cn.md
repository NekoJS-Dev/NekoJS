<!-- wiki-page: recipe-system; locale: cn -->

> **中文** · [English](recipe-system_us)

<a id="wiki-section-1"></a>
# 配方系统

改配方的脚本放在 `server_scripts/` 里，写在 `ServerEvents.recipes` 事件中。保存后执行 `/nekojs reload`，再去工作台或熔炉里试一下，就能确认改动是否生效。

<a id="wiki-section-2"></a>
## 快速上手

新建 `nekojs/server_scripts/recipes.js`：

```javascript
ServerEvents.recipes(event => {
  // 删除所有产出木棍的配方
  event.remove({ output: 'minecraft:stick' })

  // 1 个圆石合成 4 根木棍
  event.shaped('4x minecraft:stick', ['C'], { C: 'minecraft:cobblestone' })
    .id('mypack:cobble_to_sticks')

  // 沙子烧成玻璃，给 0.1 经验，烧 200 tick
  event.smelting('minecraft:glass', 'minecraft:sand', 0.1, 200)
    .id('mypack:sand_to_glass')

  // 所有有序合成里，把橡木木板换成白桦木板
  event.replaceInput({ type: 'minecraft:crafting_shaped' }, 'minecraft:oak_planks', 'minecraft:birch_planks')
})
```

执行 `/nekojs reload` 后配方立即生效。JEI、REI 等配方查看器有时还显示旧配方，重新进入世界即可。想恢复原样，删掉脚本再 reload 一次。

<a id="wiki-recipe-filters"></a>
<a id="wiki-section-3"></a>
## 选择配方：过滤器

`remove`、`replaceInput`、`find` 等方法的第一个参数是过滤器，用来选出要处理的配方。最常用的是对象写法：

| 键 | 值 | 选出哪些配方 |
|---|---|---|
| `id` | 配方 ID、正则 | ID 完全一致或匹配正则的配方 |
| `mod` | 模组 ID、正则 | ID 命名空间是这个模组的配方 |
| `output` | 物品、标签、`Item.of(...)` | 产出这个物品的配方 |
| `input` | 物品、标签、`Item.of(...)` | 原料里用到这个物品的配方 |
| `type` | 配方序列化器 ID | 某一种具体配方，例如 `minecraft:crafting_shaped` |
| `recipeType` | 配方类型 ID | 某一大类配方，例如 `minecraft:crafting`（有序和无序都算） |
| `group` | 字符串、正则 | 配方组 |
| `idStartsWith` / `idEndsWith` / `idContains` | 字符串 | ID 以某段文字开头、结尾或包含某段文字 |
| `and` | 过滤器数组 | 每一项都匹配 |
| `or` | 过滤器数组 | 任意一项匹配 |
| `not` | 过滤器 | 不匹配这一项 |

组合规则：

- 同一个对象里写多个键，要**同时满足**。
- 写成数组 `[a, b]`，满足**任意一个**即可，和 `{ or: [a, b] }` 一样。
- `{}` 和 `'*'` 选中所有配方，`'-'` 一个也不选。
- 直接写一个字符串，等于按 ID 选，例如 `event.remove('minecraft:stick')`。
- 键名拼错会直接报错 `NEKO-5003`，不会悄悄变成“选中全部”。

```javascript
ServerEvents.recipes(event => {
  // minecraft 自己的木棍配方
  event.remove({ mod: 'minecraft', output: 'minecraft:stick' })

  // 用到任意木板的有序合成
  event.remove({ type: 'minecraft:crafting_shaped', input: '#minecraft:planks' })

  // ID 以 planks 结尾的 minecraft 配方
  event.remove({ id: /^minecraft:.*planks$/ })

  // 用函数判断，必须返回 true 或 false
  event.remove(match => match.recipe.mod === 'examplemod')
})
```

注意：

- `type` 区分有序和无序合成。以前写 `{ type: 'minecraft:crafting' }` 的脚本，要改成 `{ recipeType: 'minecraft:crafting' }`。
- `input` 和 `output` 不看数量。要连物品的组件一起完全比较，写 `{ output: { match: Item.of(...), exact: true } }`。
- `input` 和 `output` 只认识原版的有序合成、无序合成、切石和烧炼类配方。模组的特殊配方可能选不到，可以改用 `id` 或 `mod`。
- 本身就无法读取的损坏配方，任何过滤器都选不中。

<a id="wiki-section-4"></a>
## 删除和替换

| 方法 | 作用 |
|---|---|
| `event.remove(filter)` | 删除所有选中的配方 |
| `event.removeById(id)` | 按 ID 删除一个配方 |
| `event.replaceInput(filter, 旧原料, 新原料)` | 在选中的配方里替换原料 |
| `event.replaceOutput(filter, 旧产物, 新产物)` | 在选中的配方里替换产物，原来的数量保留 |

```javascript
ServerEvents.recipes(event => {
  // 所有配方里的橡木木板换成白桦木板
  event.replaceInput({}, 'minecraft:oak_planks', 'minecraft:birch_planks')

  // 所有产出铁锭的配方改为产出金锭
  event.replaceOutput({}, 'minecraft:iron_ingot', 'minecraft:gold_ingot')
})
```

替换原料时，只要配方里的原料**包含**要替换的物品，这个原料就会整个换掉。比如替换 `minecraft:oak_planks` 时，写着 `#minecraft:planks` 的原料也会被换成新原料。只想改某些配方，就在过滤器里缩小范围。

替换只检查常见的字段名：原料看 `ingredient`、`ingredients`、`input`、`inputs`、`key`，产物看 `result`、`results`、`output`、`outputs`。模组配方用别的字段名时，可以用下文的 schema 标记字段，或者直接修改 JSON。

<a id="wiki-section-5"></a>
## 原料写法

凡是需要原料的地方（快捷方法的原料、`replaceInput` 的参数、过滤器的 `input` 等），都可以用下面的写法：

| 写法 | 含义 | 编辑器补全 |
|---|---|---|
| `'minecraft:stone'` | 一个物品 | 有 |
| `'#minecraft:planks'` | 物品标签 | 有 |
| `'@create'` | 某个模组的全部物品 | 有 |
| `'*'` | 任意物品 | 有 |
| `'/wool$/'` | ID 匹配正则的物品 | 无 |
| `Item.of('minecraft:stone')` | 物品栈 | 有 |
| `['minecraft:stone', '#minecraft:planks']` | 数组，任意一个都行 | 有 |
| `{ item, tag, mod, regex, wildcard, filter, any, all, not }` | 对象写法 | 部分（`item`、`tag`、`mod`） |

产物写成 `'4x minecraft:stick'` 或 `Item.of('minecraft:stick', 4)` 都可以。

物品 ID、标签和模组名的补全来自 `/nekojs probe`。加了新模组、注册了新物品或改了标签之后，重新执行一次 `/nekojs probe`，否则新的 ID 会在编辑器里标红。

<a id="wiki-section-6"></a>
## 添加配方

<a id="wiki-section-7"></a>
### 常用快捷方法

| 方法 | 说明 |
|---|---|
| `event.shaped(产物, 图案, 键)` | 有序合成。图案是字符串数组，键是 `{ 字符: 原料 }` |
| `event.shapeless(产物, 原料数组)` | 无序合成 |
| `event.smelting(产物, 原料)` / `event.smelting(产物, 原料, 经验, 时间)` | 熔炉。默认 0.1 经验、200 tick |
| `event.blasting(产物, 原料)` / `event.blasting(产物, 原料, 经验, 时间)` | 高炉。默认 0.1 经验、100 tick |
| `event.smoking(产物, 原料)` / `event.campfireCooking(产物, 原料)` | 烟熏炉（100 tick）/ 营火（600 tick），只有两个参数的写法 |

```javascript
ServerEvents.recipes(event => {
  event.shaped('minecraft:diamond_sword', [
    'D',
    'D',
    'S'
  ], {
    D: 'minecraft:diamond',
    S: 'minecraft:stick'
  }).id('mypack:diamond_sword_alt')

  event.shapeless('minecraft:dirt', ['minecraft:sand', 'minecraft:gravel'])
    .id('mypack:sand_gravel_to_dirt')
})
```

<a id="wiki-recipe-namespace"></a>
<a id="wiki-section-8"></a>
### 任意配方类型：event.recipes

`event.recipes.<命名空间>.<类型>(...)` 可以添加任意配方类型，包括模组的配方：

```javascript
ServerEvents.recipes(event => {
  event.recipes.minecraft.crafting_shaped(
    Item.of('minecraft:stick', 2), ['B'],
    { B: 'minecraft:bamboo' }
  ).id('mypack:bamboo_to_sticks')

  event.recipes.minecraft.smelting({
    ingredient: 'minecraft:gold_ore',
    result: 'minecraft:gold_ingot',
    experience: 1.0,
    cookingtime: 200
  }).id('mypack:smelt_gold')
})
```

每种配方的参数不一样，**不要把快捷方法的参数顺序照搬过来**。最可靠的方法是执行 `/nekojs probe`，然后看编辑器提示的写法。提示里通常有两种：按位置传参，以及传一个带字段名的对象（像上面的 `smelting`）。

装了某个模组，不代表它的每种配方都有现成的写法。以编辑器提示为准，不要照抄别的整合包的示例。

<a id="wiki-section-9"></a>
### 直接写 JSON：custom 和 builder

知道配方的完整 JSON 时，可以直接写。JSON 必须符合当前游戏版本和模组要求的格式：

```javascript
ServerEvents.recipes(event => {
  // custom：传入完整 JSON，必须包含 type
  event.custom({
    type: 'minecraft:smelting',
    ingredient: Ingredient.of('minecraft:cobblestone'),
    result: Item.of('minecraft:stone'),
    experience: 0.1,
    cookingtime: 200
  }).id('mypack:cobble_to_stone')

  // builder：一个字段一个字段地写
  event.builder('minecraft:crafting_shapeless')
    .id('mypack:stone_button')
    .property('category', 'redstone')
    .property('ingredients', [Ingredient.of('minecraft:stone')])
    .output('result', Item.of('minecraft:stone_button'))
    .validate()
})
```

JSON 里可以直接放 `Ingredient.of(...)` 和 `Item.of(...)`，它们会被转换成正确的格式。`custom` 的 JSON 里没有 `type` 时会返回 `null`，后面接 `.id(...)` 就会报错。

`.validate()` 会立刻检查这个配方能不能被游戏读取，有问题马上报错。建议在调试新配方时加上。

<a id="wiki-section-10"></a>
### 配方 ID

建议每个新配方都用 `.id('命名空间:名字')` 指定 ID。不指定时会自动生成 `nekojs:shaped_1a2b3c4d` 这样的 ID，内容改了 ID 也可能变。

不写命名空间时，`.id('foo')`、`removeById('foo')`、`event.get('foo')` 会当成 `nekojs:foo`，而过滤器 `{ id: 'foo' }` 会当成 `minecraft:foo`。所以 ID 请始终写全。

<a id="wiki-section-11"></a>
## 查看和修改已有配方

| 方法 | 说明 |
|---|---|
| `event.find(filter)` | 返回选中的配方列表 |
| `event.all()` | 返回所有配方 |
| `event.get(id)` | 按 ID 取一个配方，没有时返回 `null` |
| `event.forEach(filter, recipe => {})` / `event.forEach(recipe => {})` | 逐个处理配方。`event.modify` 是同一个方法 |
| `event.count()` / `event.count(filter)` | 配方数量 |
| `event.exists(id)` / `event.exists(filter)` | 是否存在 |
| `event.ids()` | 所有配方 ID |
| `event.getJson(id)` / `event.setJson(id, jsonString)` | 以 JSON 字符串读取或覆盖一个配方 |
| `event.print(filter?)` / `event.dump(filter?)` | 把配方打印到日志 / 返回 JSON 字符串 |

`find`、`forEach` 等拿到的每个配方都可以这样用：

| 方法 | 说明 |
|---|---|
| `.id()` / `.id(newId)` | 读取或修改 ID |
| `.type()` / `.group()` / `.group(name)` | 读取类型，读取或设置配方组 |
| `.json()` | 配方的 JSON 对象 |
| `.setPath(path, value)` / `.setPaths({ ... })` | 按 JSON 路径设置值，例如 `'result.count'` |
| `.removePath(path)` / `.removeProperty(key)` | 删除字段 |
| `.merge({ ... })` | 把一个对象的字段合并进来 |
| `.copy(newId)` | 复制成一个新配方 |
| `.remove()` | 删除这个配方 |
| `.validate()` | 检查配方能否被游戏读取 |

```javascript
ServerEvents.recipes(event => {
  event.forEach({ output: 'minecraft:iron_ingot', recipeType: 'minecraft:smelting' }, recipe => {
    console.info(`找到配方：${recipe.id()}`)
    recipe.setPath('experience', 0.5)
  })
})
```

新加配方返回的对象（例如 `event.shaped(...)` 的结果）也有 `.id()`、`.group()`、`.setPath()`、`.merge()`、`.validate()` 等方法，还多了 `.property(key, value)`、`.input(key, 原料)`、`.output(key, 物品)`。NeoForge 上另有 `.fluid(...)`、`.fluidIngredient(...)`、`.sizedFluidIngredient(...)` 用来写流体字段。

<a id="wiki-section-12"></a>
## 合成后保留或损耗原料

Minecraft 26.x 上，合成配方可以决定原料合成后怎么处理：工具掉耐久而不是消失，催化剂原样留下，或者换成别的物品。这只对工作台和背包里的合成有效。

```javascript
ServerEvents.recipes(event => {
  // 铁镐作为工具：每次合成掉 1 点耐久，回到合成格
  event.shaped('minecraft:diamond_pickaxe', ['PDD', ' S ', ' S '], {
    P: 'minecraft:iron_pickaxe', D: 'minecraft:diamond', S: 'minecraft:stick'
  })
    .id('mypack:upgrade_pickaxe')
    .damageIngredient('minecraft:iron_pickaxe', 1)

  // 下界之星作为催化剂：合成后原样保留
  event.shapeless('minecraft:enchanted_golden_apple', ['minecraft:golden_apple', 'minecraft:nether_star'])
    .id('mypack:golden_apple_upgrade')
    .keepIngredient('minecraft:nether_star')

  // 水桶合成后变成空桶以外的东西：这里换成玻璃瓶
  event.shapeless('minecraft:clay_ball', ['minecraft:dirt', 'minecraft:water_bucket'])
    .id('mypack:mud_clay')
    .replaceIngredient('minecraft:water_bucket', 'minecraft:glass_bottle')
})
```

| 方法 | 效果 | 说明 |
|---|---|---|
| `damageIngredient(原料, 数量)` | 原料扣耐久后留下 | 数量必须写，扣一点就写 `1`。耐久用完时消失，没有耐久的物品原样保留 |
| `keepIngredient(原料)` | 原料原样留下 | 适合做催化剂 |
| `replaceIngredient(原料, 新物品)` | 原料变成新物品 | 新物品可以写 ID 或 `Item.of(...)` |

规则：

- 先写 `.id(...)`，再写这些方法。它们记在当时的配方 ID 上。
- 一个配方可以写多条。同一格只应用第一条匹配的规则。没被匹配的格子按原版处理（比如水桶留下空桶）。
- 这些规则不写进配方 JSON，每次 reload 都按脚本重新生成。删掉脚本里的一行，reload 后就失效。
- 在 `event.shaped`、`event.shapeless`、`event.custom`、`event.builder` 返回的配方上使用。

<a id="wiki-recipe-schema-json"></a>
<a id="wiki-section-13"></a>
## 给自定义配方类型写 schema

schema 描述一种配方有哪些字段。NekoJS 启动时会自动扫描已安装的配方类型并生成 schema，所以大部分配方不用自己写。自动生成的结果不对或缺字段时，可以自己写一个 JSON 文件来补充或覆盖。

有了 schema，就可以按位置传参、检查必填字段，`replaceInput` / `replaceOutput` 能认出这些字段，`/nekojs probe` 也能给出补全。

这个功能目前只在 NeoForge 上可用。

<a id="wiki-section-14"></a>
### 文件位置和示例

文件放在 `nekojs/data/<命名空间>/nekojs/recipe_types/<类型>.json`。例如 `nekojs/data/mymod/nekojs/recipe_types/grinding.json` 定义的就是 `event.recipes.mymod.grinding(...)`。

```json
{
  "type": "mymod:grinding",
  "constructors": [
    ["result", "ingredient"],
    ["result", "ingredient", "time"]
  ],
  "fields": {
    "result":     { "kind": "item_stack" },
    "ingredient": { "kind": "ingredient" },
    "time":       { "path": "processing_time", "kind": "int", "default": 100 }
  }
}
```

写好后执行 `/reload` 或重新进入世界，再执行 `/nekojs probe` 刷新补全。之后就能写 `event.recipes.mymod.grinding('minecraft:gravel', 'minecraft:cobblestone')`。

<a id="wiki-section-15"></a>
### 顶层字段

| 键 | 是否必填 | 默认值 | 含义 |
|---|---|---|---|
| `type` | 否 | `<命名空间>:<类型>` | 写进配方 JSON 的 `type` |
| `id_prefix` | 否 | `<命名空间>_<类型>` | 自动生成 ID 时用的前缀 |
| `constructors` | 否 | 全部字段按书写顺序 | 按位置传参的写法，每一项是一组字段名 |
| `fields` | **是** | 无 | 字段名和字段定义 |
| `unique` | 否 | `[]` | 记录哪些字段能区分配方，不影响 ID 生成 |

<a id="wiki-section-16"></a>
### 字段定义

| 键 | 默认值 | 含义 |
|---|---|---|
| `path` | 字段名 | 写进 JSON 的位置，可以是 `a.b` 这样的路径 |
| `kind` | `json` | 值的类型，见下方 |
| `array` | `false` | 是否写成数组 |
| `optional` | `false` | 是否可以不填。有 `default` 的字段也可以不填 |
| `default` | 无 | 不填时使用的值 |
| `role` | 按字段名推断 | `INPUT`、`OUTPUT` 或 `OTHER`，决定 `replaceInput` / `replaceOutput` 会不会处理这个字段 |

<a id="wiki-section-17"></a>
### kind 的取值

`json`、`string`、`int`、`number`、`boolean`、`ingredient`、`item_stack`、`fluid_stack`、`fluid_ingredient`、`sized_fluid_ingredient`。不区分大小写，也可以写成 `item-stack` 这样的形式。

<a id="wiki-section-18"></a>
### 写错时会怎样

下面这些错误会让这个文件加载失败，错误写在游戏日志里：

- 没有 `fields`，或者 `fields` 是空对象。
- 某个字段的定义不是对象。
- `constructors` 里某一项不是数组，或者用到了 `fields` 里没有的字段名。
- `kind` 写了不认识的值。

`role` 写错**不会报错**，会退回按字段名推断。如果 `replaceInput` 没有替换某个字段，先检查 `role` 的拼写。

<a id="wiki-recipe-schema-priority"></a>
<a id="wiki-section-19"></a>
### schema 的优先级

同一种配方可能有多个 schema 来源，后面的覆盖前面的：

1. 自动扫描生成的 schema。
2. 插件注册的 schema。
3. 你写的 `recipe_types/*.json`。

想在脚本里查看当前生效的 schema，用 [全局绑定](global-bindings_cn) 里的 `RecipeSchema`。

<a id="wiki-section-20"></a>
## 没有 schema 的配方类型

没有 schema 的配方类型也能用 `event.recipes` 添加，只要传入一个包含全部字段的对象：

```javascript
ServerEvents.recipes(event => {
  event.recipes.mymod.unknown_type({
    some_field: 'value',
    another: [1, 2, 3]
  }).id('mypack:unknown_example')
})
```

NekoJS 会自动补上 `type: 'mymod:unknown_type'`，其余字段原样写入。这和 `event.custom` 的效果一样。字段写得对不对，要等游戏读取配方时才知道，所以建议接上 `.validate()`。

只能传**一个对象**。参数不对时会返回 `null`，接着写 `.id(...)` 会报错。

> 如果编辑器把某个配方显示成 `xxx: never`，说明 NekoJS 已经确认不支持这种配方。调用它会报 `NEKO-5211`，不会退回上面的写法。启动日志里有具体原因。需要时可以改用 `event.custom`，自己写出完整而正确的 JSON。

<a id="wiki-section-21"></a>
## 可选的 NekuJS 兼容插件

NekuJS 是一个单独下载的插件，可以让 NekoJS 使用原本为 KubeJS 写的配方扩展，例如 KubeJS Create、KubeJS Mekanism 提供的配方写法。

- 只支持 **Minecraft 1.21.1 + NeoForge 21.1 + Java 21**。
- 除了 NekoJS，还要装 NekuJS、对应的 KubeJS 扩展模组和它们的前置。
- **不要**同时安装 KubeJS 或 Rhino 本体。NekuJS 已经提供了它们需要的部分。
- 不是扩展里的每种配方都能用。能用的写法以 `/nekojs probe` 生成的提示为准。
- 增删这些模组后要重启游戏，再执行一次 `/nekojs probe`。

<a id="wiki-section-22"></a>
## 平台差异

| 功能 | NeoForge 26.x | NeoForge 1.21.1 | Fabric 26.x |
|---|---|---|---|
| 增删改配方、`/nekojs reload` 生效 | ✅ | ✅ | ✅ |
| 流体字段（`.fluid(...)` 等） | ✅ | ✅ | ❌ |
| 合成后保留或损耗原料 | ✅ | ❌ | ✅ |
| 自动 schema 和 `recipe_types` JSON | ✅ | ✅ | ❌ |
| NekuJS 兼容插件 | ❌ | ✅ | ❌ |

Fabric 上没有 schema，`event.recipes.<命名空间>.<类型>({...})` 会按“没有 schema 的配方类型”处理，传入完整字段的对象仍然可以用。

<a id="wiki-section-23"></a>
## 下一步

- [全局绑定](global-bindings_cn)：`Item`、`Ingredient`、`RecipeSchema` 等。
- [事件参考](event-reference_cn)：`ServerEvents.recipes` 以外的服务端事件。
- [事件扩展](event-extensions_cn)：插件开发者如何注册配方 schema 和命名空间。

<!-- wiki-nav -->

---

[上一篇: 版本化脚本包](script-packs_cn) · [目录](Home) · [下一篇: 注册新内容](registering-new-content_cn)
