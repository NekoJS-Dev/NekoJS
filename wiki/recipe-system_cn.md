<!-- wiki-page: recipe-system; locale: cn -->

> **中文** · [English](recipe-system_us)

<a id="wiki-section-1"></a>
# 配方系统

配方修改是 NekoJS 最常用的功能之一。通过 `ServerEvents.recipes` 事件来增删改原版和 mod 配方。

<a id="wiki-section-2"></a>
## 基本用法

```javascript
ServerEvents.recipes(event => {
  // 删
  event.remove({ output: 'minecraft:stick' })

  // 加（原版样式）
  event.shaped('4x minecraft:stick', ['C'], { C: 'minecraft:cobblestone' })
    .id('nekojs:cobble_to_sticks')

  // 加（任意类型，schema 驱动）
  event.recipes.minecraft.smelting('minecraft:iron_ingot', 'minecraft:iron_ore')

  // 改输入
  event.replaceInput({ type: 'minecraft:crafting_shaped' }, 'minecraft:planks', 'minecraft:oak_planks')
})
```

`event` 是 `RecipeEventJS` 对象，下面所有方法都在它上面。

---

<a id="wiki-section-3"></a>
## 过滤器（RecipeFilter）

很多方法接受一个「过滤器」对象来选中一批配方。

| 键 | 值 | 含义 |
|---|---|---|
| `mod` | mod id | 来自该 mod 的配方 |
| `type` | `"minecraft:crafting_shaped"` | 指定配方类型 |
| `output` | `"minecraft:stick"` 或 `"#minecraft:planks"` | 输出物品/标签（有补全） |
| `input` | 物品 id 或 `"#tag"` | 输入配料（有补全） |
| `group` | 字符串 | 配方组 |
| `id` | 配方 id | 精确 id（命名空间可省，默认 `minecraft:`） |
| `idStartsWith` / `idEndsWith` / `idContains` | 字符串 | id 子串匹配 |
| `and` | 过滤器数组 | 与 |
| `or` | 过滤器数组 | 或 |
| `not` | 过滤器 | 非 |

```javascript
event.remove({ mod: 'minecraft', output: 'minecraft:stick' })   // 来自 minecraft 的木棍配方
event.remove({ and: [{ type: 'minecraft:crafting_shaped' }, { output: '#minecraft:planks' }] })
event.remove({ not: { mod: 'create' } })                          // 除 create 外全部
```

---

<a id="wiki-section-4"></a>
## 删除 / 替换

| 方法 | 说明 |
|---|---|
| `event.remove(filter)` | 删除所有匹配的配方 |
| `event.removeById(id)` | 按 id 删除单个 |
| `event.replaceInput(filter, match: Ingredient, replacement: Ingredient)` | 递归替换输入配料 |
| `event.replaceOutput(filter, match: Ingredient, replacement: ItemStack)` | 递归替换输出 |

```javascript
// 把所有用到「 minecraft:oak_planks 」的输入换成「 minecraft:birch_planks 」
event.replaceInput({}, 'minecraft:oak_planks', 'minecraft:birch_planks')

// 删除所有 mod 配方里输出为钻石剑的
event.remove({ output: 'minecraft:diamond_sword' })
```

---

<a id="wiki-section-5"></a>
## 原料写法（Ingredient）

凡是参数类型写作 `Ingredient` 的位置（`event.shapeless` 的原料数组、直接方法 `event.blasting` 的第二个参数、`replaceInput` 的 `match` 等），都接受下面这些写法：

| 写法 | 含义 | 编辑器补全 |
|---|---|---|
| `'minecraft:stone'` | 单个物品 id | ✅ 列出全部已注册物品 |
| `'#minecraft:planks'` | 物品标签 | ✅ 列出全部已加载标签 |
| `'@create'` | 某个命名空间的全部物品 | ✅ 列出已加载 mod id + 注册表里出现过的命名空间 |
| `'*'` | 任意物品 | ✅ |
| `'/wool$/'` | 正则匹配 id | ❌（正则无穷，没法枚举） |
| `Item.of('minecraft:stone')` / `ItemStack` | host 对象 | ✅ |
| `['minecraft:stone', '#minecraft:planks']` | 数组 = 任一匹配 | ✅ |
| `{ item, tag, mod, regex, wildcard, filter, any, all, not }` | 对象形式 | ✅（`item` / `tag` / `mod` 字段） |

补全来自 probe 生成的 `$Ingredient_` 别名：物品 id 走 `RegistryTypes.Item` 字面量联合，标签走 <code>\`#${RegistryTypes.ItemTag}\`</code>、命名空间走 <code>\`@${RegistryTypes.Namespace}\`</code> 这样的模板字面量类型。`RegistryTypes.ItemTag` 是 probe 运行时从注册表已绑定的标签导出的，`RegistryTypes.Namespace` 是加载器 mod 列表与注册表条目命名空间的并集（所以脚本自己注册的 `mymod:cool_gem` 带来的 `@mymod` 也有补全），所以**加了新 mod、脚本注册了新内容或改了数据包标签后要重新跑 `/nekojs probe`**，否则新 id / 标签会被标红。

> 平台没有标签系统时（Cleanroom 1.12.2 用矿辞而非标签），`RegistryTypes.ItemTag` 回退成 `string`，`#xxx` 接受任意矿辞名。
>
> 流体原料（`FluidIngredient` / `SizedFluidIngredient`）同理，只是把 `Item` 换成 `Fluid`。

---

<a id="wiki-section-6"></a>
## 添加配方

<a id="wiki-section-7"></a>
### 方式一：`event.<type>(...)` 快捷方法

针对常用原版类型，`event` 上直接有方法：

| 方法 | 签名 |
|---|---|
| `event.shaped(result, pattern, keys)` | 有序合成。`result` 是 ItemStack/id；`pattern` 是字符串数组；`keys` 是 `{ 字符: Ingredient }` |
| `event.shapeless(result, ingredients)` | 无序合成。`ingredients` 是数组 |
| `event.smelting(result, ingredient)` / `event.smelting(result, ingredient, xp, cookTime)` | 熔炉 |
| `event.blasting(result, ingredient)` / `event.blasting(result, ingredient, xp, cookTime)` | 高炉（同 smelting 重载） |
| `event.smoking(result, ingredient)` / `event.campfireCooking(result, ingredient)` | 烟熏炉 / 营火（仅 2 参形式，无 xp/cookTime） |

```javascript
event.shaped('minecraft:diamond_sword', [
  'D',
  'D',
  'S'
], {
  D: 'minecraft:diamond',
  S: '#minecraft:sticks'
}).id('nekojs:diamond_sword_alt')

event.shapeless('minecraft:dirt', ['minecraft:sand', 'minecraft:gravel'])
```

<a id="wiki-section-8"></a>
### 方式二：`event.recipes.<namespace>.<type>(...)`

对可调用的配方，解析顺序为已注册 handler、schema、原始 JSON。已知配置若带有 `unsupportedReason`，会优先拒绝调用，而不是静默转到 handler 或原始 JSON 兜底。**不能把直接快捷方法的参数顺序套用到所有命名空间方法。** 内置 handler 提供 `crafting_shaped`/`crafting_shapeless`；烹饪和锻造取决于当前节点最终生效的 schema。请查看生成的重载，或在 schema 支持时使用命名字段。参见[运行时解析](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java#L64-L77)。

<a id="wiki-section-9"></a>
### 原生 handler 与 schema 的调用合同

原生 handler 声明保留**所有可见重载**及真实 Java 返回类型，包括已声明的泛型结构；不会因配方名字相同就把返回值改成某个 schema builder。未绑定的 Java 类型变量按上界近似，不会凭空生成未声明的 TS 类型变量。只有末尾连续的 `Optional` 参数允许省略；中间的可选槽仍是必传位置，可传 null/undefined。这条反射调用路径上的 Java varargs 仍要求传一个数组，不支持散列为多个参数。参见 [handler 元数据](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/api/catalog/RecipeNamespaceCatalogEntry.java#L66-L94)、[参数转换](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L70-L96)及 [Optional 尾部规则](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L183-L188)。

schema 使用自身声明的构造器，并在有字段时保留**完整命名 options 对象重载**，与位置参数形式并存。命名选项保留必填、默认值及规范键与别名的互斥关系，不是只列几个常用字段的简化示例；链式 setter 同样沿用完整字段输入合同。生成器可能为较长的 options 对象声明命名类型，避免重复展开。参见 [schema 重载生成](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java#L238-L255)与 [Probe 类型约定](probe-type-generation_cn#wiki-section-16)。

将完整示例放入 `server_scripts/namespace-recipes.js`：

```javascript
ServerEvents.recipes(event => {
  event.recipes.minecraft.crafting_shaped(
    Item.of('minecraft:stick', 2), ['B'],
    { B: Ingredient.of('minecraft:bamboo') }
  ).id('nekojs_example:bamboo_to_sticks')

  event.recipes.minecraft.smelting({
    ingredient: Ingredient.of('minecraft:gold_ore'),
    result: Item.of('minecraft:gold_ingot'),
    experience: 1.0,
    cookingtime: 200
  }).id('nekojs_example:smelt_gold')
})
```

配方对象、原料映射、过滤器和 schema JSON 是结构化数据，不是通用 builder 配置约定。装有某个 mod 并不能证明特定 schema/构造器存在；使用该 mod 当前 serializer/schema 文档，不要假设 `create.mixing(result, inputs)` 或拼错的 `smithingtransform` 可用。链式 `.id(...)` 是当前受支持 API，不表示已经有回调 builder 替代形式。参见[脚本 API 风格](script-api-style_cn)。

> 自动 id 是实现派生的 `nekojs:<prefix>_<8位十六进制hash>`，可能追加冲突后缀；整合包应使用显式唯一 id。

<a id="wiki-section-10"></a>
### 方式三：`event.custom(...)` / `event.builder(...)` —— 原始 JSON

对于已安装的 serializer，可以直接提供符合当前节点/mod 实际配方格式的完整 JSON 对象；这不会推导缺失字段、安装 serializer，也不代表任意 addon 都已受支持：

```javascript
// custom：必须包含 type
event.custom({
  type: 'minecraft:smelting',
  ingredient: 'minecraft:cobblestone',
  result: 'minecraft:stone',
  experience: 0.1,
  cookingtime: 200
}).id('nekojs:cobble_to_stone')

// builder：链式构建任意类型
event.builder('minecraft:crafting_shapeless')
  .id('nekojs:custom_button')
  .property('category', 'redstone')
  .property('ingredients', [Ingredient.of('minecraft:stone')])
  .output('result', Item.of('minecraft:stone_button'))
  .validate()
```

---

<a id="wiki-section-11"></a>
## RecipeJsonBuilder（链式）

`event.builder(type)`、`event.recipes.<ns>.<type>(...)`、`event.custom(...)` 都返回链式 builder。

| 方法 | 说明 |
|---|---|
| `.id(newId)` | 设置配方 id（会重新索引） |
| `.group(group)` | 设置配方组 |
| `.validate()` | 校验必填字段 |
| `.json() → object` | 返回底层 JSON |
| `.merge(jsonObject\|value)` | 合并 JSON |
| `.property(key, value)` | 设任意属性 |
| `.jsonProperty(key, JsonElement\|Number\|String\|Boolean)` | 设 JSON 属性 |
| `.input(key, Ingredient)` | 设输入字段（schema 感知角色） |
| `.output(key, ItemStack)` | 设输出字段 |
| `.fluid(key, FluidStack)` | 设流体字段 |
| `.fluidIngredient(key, FluidIngredient)` | 设流体配料字段 |
| `.sizedFluidIngredient(key, SizedFluidIngredient)` | 设带数量流体配料字段 |
| `.setPath(path, value)` / `.setPaths(obj)` | 按 JSON 路径设值 |
| `.removePath(path)` / `.removePaths(array)` | 按路径移除 |
| `.removeProperty(key)` | 移除属性 |

---

<a id="wiki-section-12"></a>
## 查询 / 遍历

| 方法 | 说明 |
|---|---|
| `event.all() → RecipeEntryJS[]` | 所有配方 |
| `event.get(id) → RecipeEntryJS` | 按 id 取 |
| `event.find(filter) → RecipeEntryJS[]` | 按过滤器查找 |
| `event.forEach(filter?, cb)` | 遍历 |
| `event.modify(filter, cb)` | 遍历并修改 |
| `event.count()` / `event.count(filter) → int` | 计数 |
| `event.exists(id)` / `event.exists(filter) → boolean` | 是否存在 |
| `event.ids() → string[]` | 所有配方 id |
| `event.setJson(id, json)` / `event.getJson(id)` | 原始 JSON 读写 |
| `event.dump(filter?) → string` / `event.print(filter?)` | 转 JSON 字符串 / 打印日志 |

`RecipeEntryJS` 暴露 `.id`、`.json`、`.setPath(path, value)`、`.removePath(path)` 等。

```javascript
event.forEach({ output: 'minecraft:iron_ingot' }, recipe => {
  console.info(`找到配方：${recipe.id}`)
  recipe.setPath('experience', 0.5)   // 改经验
})
```

---

<a id="wiki-section-13"></a>
## 原料动作（Ingredient Actions）

> 仅 **NeoForge 26.x**。对配方消耗原料时的"合成余量"（crafting remainder）做定制：
> 让工具损耗而不是消失、让催化剂保留、或把原料换成别的东西。动作是**纯脚本状态**
> （不写进配方 JSON），`/reload` 或 `/nekojs reload server` 重跑脚本时自动重建——删掉
> 脚本里的动作，下次 reload 即失效。

三种动作，都挂在 `RecipeJsonBuilder` 链上（`event.shaped(...)` / `event.recipes...` 的返回值）：

```js
ServerEvents.recipes(event => {
  // 铁镐作为工具参与合成后：耐久 -1 回到网格（耗尽则消失；不可破坏物品按保留处理）
  event.shaped('mymod:super_pick', ['PPP', ' S ', ' S '], {
      P: 'minecraft:iron_pickaxe', S: 'minecraft:stick'
    })
    .damageIngredient('minecraft:iron_pickaxe', 1)

  // 钻石催化剂原样保留（每次合成只消耗其它原料）
  event.shaped('mymod:magic_block', ['DAD', 'AAA', 'DAD'], {
      D: 'minecraft:diamond_block', A: '#c:ingots',
    })
    .keepIngredient('minecraft:diamond_block')

  // 原料被换成别的东西（拿走成品时替换物品进入网格/背包）
  event.shapeless('mymod:swap', ['minecraft:diamond'])
    .replaceIngredient('minecraft:diamond', 'minecraft:emerald')
})
```

匹配参数与[原料写法](#wiki-section-5)一致：物品 id、数组、`'#tag'` 或 Ingredient 对象；
一个配方可以叠加多条动作，同一格只应用**第一条**匹配的动作；未匹配的格子保持原版余量语义
（空桶 / 经验瓶等）。`.id(...)` 重命名要在动作调用**之前**——动作登记在调用时的配方 id 上。

| 动作 | 语义 | 边界 |
|---|---|---|
| `damageIngredient(filter, amount = 1)` | 余量 = 原料副本，耐久 +amount | 超过最大耐久 → 消失；不可破坏 → 原样保留 |
| `keepIngredient(filter)` | 余量 = 原料副本，不损耗 | 等价于"催化剂" |
| `replaceIngredient(filter, with)` | 余量 = `with` 的副本 | `with` 支持物品 id 或 ItemStack |

---

<a id="wiki-section-14"></a>
## 数据驱动 Schema

自动发现会扫描已注册的 serializer，结合当前节点已知的原版合同与 codec 检查、启发式规则提取 schema。存在 serializer **不代表**已经具备完整字段 schema、正确的位置构造器，或支持某个 addon 的所有组件。复杂或不透明 codec 的发现结果可能有限，应检查最终 catalog 与生成声明，不能据此推断整个 mod 都兼容。参见[原生扫描器](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/src/main/java/com/tkisor/nekojs/api/recipe/definition/MinecraftRecipeSchemaScanner.java#L33-L74)及 [codec 检查与兜底](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/src/main/java/com/tkisor/nekojs/api/recipe/definition/MinecraftRecipeSchemaScanner.java#L117-L159)。

可选的 NekuJS addon 会把受支持的兼容资源与程序化 schema 编译进**同一套原生 NekoJS schema 注册表**，复用原生转换和 Probe 声明；这不是运行上游 Rhino/KubeJS，也不是第二套配方引擎。兼容范围仍受实际导入的插件、组件 codec、schema 继承及已实现的 factory 操作限制。参见[桥接发布](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/addons/nekujs/src/main/java/com/tkisor/nekujs/bridge/LegacyRuntimeBridge.java#L75-L107)与[原生参数转换](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L70-L143)。

NekuJS 为 `minecraft:shaped` 和 `minecraft:special` 提供共享原生父 schema 依赖；它们不会作为合成出来的额外方法覆盖原版 handler，子定义仍保留自己的 serializer ID。`special` 保留 `category` 的原生 codec、`misc` 默认值和零位置参数构造；上游 `hidden` 是描述元数据，不再被误当作运行时禁止调用。`shaped` 提供结果、图案、单字符原料映射、分类及通知字段，在原生编码生命周期中处理空原料和图案一致性。

因此原始 Mekanism addon 的 `mek_data` 继承 shaped，`bin_extract`、`bin_insert`、`clear_configuration` 继承 special，不需要按这些配方名特判。原生父模型只支持原版默认镜像／收缩行为，不声明支持 `noMirror`、`noShrink` 等非默认 KubeJS 控制；未知工厂、处理器及映射仍明确拒绝。升级后重启实例并重新生成 Probe，不能用旧声明判断新模型是否已生效。

如果你想给一个**自定义/未知类型**定义 schema（让位置参数和字段校验生效），写一个 JSON 文件：

**路径**：`nekojs/data/<namespace>/nekojs/recipe_types/<type>.json`

**示例**（`data_driven_shapeless.json`）：

```json
{
  "type": "minecraft:crafting_shapeless",
  "id_prefix": "data_driven_shapeless",
  "constructors": [
    ["result", "ingredients"],
    ["result", "ingredients", "group"]
  ],
  "fields": {
    "result":      { "path": "result",     "kind": "item_stack" },
    "ingredients": { "path": "ingredients","kind": "ingredient", "array": true },
    "group":       { "path": "group",      "kind": "string", "optional": true }
  }
}
```

<a id="wiki-section-15"></a>
### 顶层字段

| 键 | 必填? | 默认 | 含义 |
|---|---|---|---|
| `type` | 可选 | `<namespace>:<name>` | 目标配方类型 id |
| `id_prefix` | 可选 | `<namespace>_<name>` | 自动生成 id 的前缀 |
| `constructors` | 可选 | 所有字段按声明顺序 | 位置构造器：字段名数组的数组 |
| `fields` | **必填** | 无 | 字段名与字段定义的映射 |

<a id="wiki-section-16"></a>
### 字段定义

| 键 | 默认 | 含义 |
|---|---|---|
| `path` | 字段名 | 值写入的 JSON 路径 |
| `kind` | `json` | 类型（见下表） |
| `array` | `false` | 是否包成 JSON 数组 |
| `optional` | `false` | false 且缺失时报错 |
| `default` | — | 默认 JSON 值 |
| `role` | 按名字推断 | `INPUT` / `OUTPUT` / `OTHER`（控制 `replaceInput`/`replaceOutput` 的键识别） |

<a id="wiki-section-17"></a>
### 写坏了会怎样

`RecipeTypeDefinitionJsonLoader.parse` 对这几条直接抛异常（加载该 JSON 时失败）：缺 `fields` 或 `fields` 为空对象、某个字段定义不是对象、`constructors` 里某项不是数组、`constructors` 引用了 `fields` 里没有的字段名、`kind` 写了不认识的值（`RecipeFieldKind.parse` 的 `valueOf` 抛）。

`role` 例外：值不合法时**不报错**，静默退回按字段名推断（`RecipeFieldRoles.roleOfName`）——所以拼错 `role` 的症状是 `replaceInput` 认不出这个键，而不是启动失败。

<a id="wiki-section-18"></a>
### `kind` 取值

`json`、`string`、`int`、`number`、`boolean`、`ingredient`、`item_stack`、`fluid_stack`、`fluid_ingredient`、`sized_fluid_ingredient`（大小写不敏感，接受 kebab-case）。

<a id="wiki-section-19"></a>
### Schema 优先级

NekoJS 的配方 schema 有三个来源，按优先级合并：

1. **自动发现**（最低）：反射 MC codec。
2. **插件覆盖**（中）：插件通过 `registerRecipeSchemas` 注册。
3. **数据驱动 JSON**（最高）：你写的 `recipe_types/*.json`。

可以用高优先级覆盖低优先级的 schema。运行时查询用 [全局绑定 - RecipeSchema](global-bindings_cn)。

---

<a id="wiki-section-20"></a>
## 未知类型兜底

提交完整配方 JSON 不要求先定义字段 schema。对没有 handler 或 schema 的未知 namespace/type，原始 JSON 兜底接受一个对象并补上 `type`。它**不是**绕过已知被拒绝配置的通道，最终仍要求已安装的 serializer 能接受这份 JSON：

```javascript
event.recipes.mymod.unknown_type({
  some_field: 'value',
  another: [1, 2, 3]
})
```

原始 JSON 兜底接受任意成员名，但“成员存在”并不能证明对应 serializer 或作者调用合同有效。它不进行配方字段校验：

- 必须是**恰好一个**带成员的对象参数；不匹配的参数形状返回 `null`，此时立即接 `.id(...)` 可能失败。错误处理取决于入口：[FallbackNamespaceProxy](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/FallbackNamespaceProxy.java#L41-L61) 捕获转换/注册异常、记录 debug 日志并返回 `null`；[RecipeNamespaceProxy](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java#L195-L209) 的原始 JSON 分支不捕获这些异常。两者都不会把 handler 执行错误或已知被拒绝的配置转成成功的兜底调用。
- 字段名/类型对不对，只有游戏加载该配方时才会知道。

schema 换来的是位置参数写法、字段必填校验、`replaceInput`/`replaceOutput` 的键识别，以及 probe 生成的补全。只想塞一个 JSON 进去的话，兜底或 `event.custom({...})` 就够了。

<a id="wiki-section-21"></a>
### 诊断声明为 `never` 的配方成员

配方成员若显示为 `some_profile: never`，表示**最终 catalog 已记录明确的拒绝原因**（`unsupportedReason`）。生成器不会为这个名字继续提供可调用的 handler/schema 替代项，运行时也会用 `NEKO-5211` 拒绝，而不是转入未知类型兜底。这与 options 字段里的 `input?: never` 不同：后者是在正常可调用的命名参数中表达规范键与别名互斥。参见[声明拒绝分支](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java#L188-L208)与[运行时拒绝分支](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java#L64-L77)。

先查看启动时的 schema 编译诊断，定位资源、JSON 路径和配方 id；更换 mod/资源或安装修复后重新生成 Probe。例如，`NEKO-5242` 表示兼容图中的父 schema 解析失败，不是行宽限制，也不能推断整个 mod 或原生 serializer 都不受支持。补齐受支持的父定义可解决这类能力缺口；其他未支持的组件/factory 仍需各自实现。不要用 TS 强转掩盖原因，也不要认为放宽扫描白名单就能修复。参见[父定义解析](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/addons/nekujs/src/main/java/com/tkisor/nekujs/recipe/schema/RecipeSchemaGraphCompiler.java#L75-L95)。单独使用 `event.custom` 时仍须提供实际已安装 serializer 的合法 JSON；它不会复现被拒绝配置的构造器、默认值或动作。

---

<a id="wiki-section-22"></a>
## 平台差异

| 平台 | 配方 reload |
|---|---|
| **Cleanroom 1.12.2** | 独立 legacy 分支，当前仓库不验证配方重载与查看器刷新行为。 |
| **Fabric 26.x** | 支持 `/nekojs reload server` 重跑配方脚本，具体 API 以当前节点实现为准。 |
| **NeoForge 26.x / 1.21.1** | 支持。`/nekojs reload server` 从缓存的 `baseJsons` 重建工作集、重跑配方脚本并替换 `RecipeManager`（服务端合成即时生效）；JEI/REI 配方面板可能需要客户端刷新。 |

<a id="wiki-section-23"></a>
## 下一步

- [全局绑定](global-bindings_cn) —— `Item`/`Ingredient` 等参考。
- [事件参考](event-reference_cn#wiki-section-3) —— `ServerEvents.recipes` 之外的事件。
- [事件扩展](event-extensions_cn) —— 给插件开发者看的 schema/namespace 注册。

<!-- wiki-nav -->

---

[上一篇: 版本化脚本包](script-packs_cn) · [目录](Home) · [下一篇: 注册新内容](registering-new-content_cn)
