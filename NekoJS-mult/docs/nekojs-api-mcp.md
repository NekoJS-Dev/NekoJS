# NekoJS API MCP：把 probe 产物变成 AI 可查的脚本 API

**结论：可以做，而且 probe 已经把需要的原料全都产出成文件了——缺的只是一个索引层和一个 MCP 外壳。**
推荐做法是**只读 `.neko_probe/**/*.d.ts`，不碰 mod 一行 Java**：用一个独立的 Node/TS MCP server 把声明树索引成结构化数据库，对外暴露 6 个 tool。

Status: proposal
Author: zcode-agent
Date: 2026-09-15

---

## 1. 要解决的问题

### 1.1 现在的做法，写死在 agent 模板里

`common/src/main/java/com/tkisor/nekojs/probe/AgentTemplateGenerator.java` 每次 probe 都会往 `<gameDir>/.github/agents/` 写三个模板，教 AI **手工**去翻声明树。原文（`EXPLORE_TEMPLATE` 的 `## Search Strategy` 一节）：

```markdown
- Go **broad to narrow**:
    1. Start with glob patterns or semantic codesearch to discover relevant areas
    2. Narrow with text search (regex) for specific symbols or patterns
    3. Read files only when you know the path or need full context
    4. All type declarations are in `index.d.ts` files organized by package — DO NOT
       search for files named like `ClassName.d.ts`, they don't exist.
- Always read global bindings and events in `@side-only` thoroughly before deep diving
  into `@package` Java classes
```

以及同一文件 `## Probe Type Declarations` 一节里这句：

```markdown
- DO NOT explore anything under the folder `@special`, as it contains huge amount of
  type declarations that will overload the context.
```

**这两段就是需求本身。** 第一段描述的是"用 grep + read 手工重建一份类型索引"；第二段是承认这个流程在某个目录下会直接崩掉。把这段流程换成一个能精确回答问题的索引，就是本提案。

### 1.2 手工流程的真实代价

- **搜不准。** 声明按 Java 包路径铺开（`@package/net/minecraft/world/item/index.d.ts`），一个类的成员散在 `import` 块、`declare module`、`declare global` 三种结构里。grep 一个词会命中几十个 import 行（见下）。
- **一次读太多。** 真实产物里单个 `index.d.ts` 可能覆盖整个 Java 包；`@special/types/index.d.ts` 装的是全游戏注册表 id 的字面量 union，模板明令禁止 AI 读它。
- **拿不到结论。** "这个方法在两个版本里都在吗"、"这个 builder 有哪些链式方法"、"这个 id 合法吗" —— 这三类问题现在都要靠人肉读文件加心算。

已提交的 golden 树（`common/src/test/resources/nekojs/probe/legacy-tree/`，31 个 `.d.ts`）实测 **180,570 字节**，约 45k tokens——**而它只是夹具，里面全是 `$LegacyProbeFixture$*` 假类**。真实产物覆盖 Minecraft 全部暴露类，量级远高于此。真实产物体积目前**没有测量数据**（本机 `versions/*/run/.neko_probe/` 只剩一个 `jsconfig.json` 残骸，此前 probe 被关过），所以把它列为实施第 0 项任务。

### 1.3 一个典型查询在两种流程下的对照

问："给已有物品加食物属性，`ItemBuilder` 上到底有哪些方法？"

**现在（手工）**：`grep -rn "ItemBuilder" .neko_probe/` → 命中一堆 import 行 → 顺着找到 `@manual/index.d.ts`（builder 面是手写声明，不在 `@side-only`）→ 整文件读进来（含全部 builder 与糖方法重载）→ 人工挑出 `food(...)` 那一行 → 还不确定回调类型 `FoodBuilder` 有什么，得再读一次。

顺带一个手工流程容易漏的点：`item` 这个糖方法**只在 NeoForge 上有**（`NekoRegistryDeclarations.java` 里整段包在 `//? if neoforge {` 中），Fabric profile 下它根本不存在。手工 grep 时这个信息埋在 Stonecutter 预处理指令里，看不到。

**本提案**：一次 `nekojs_builders { "registry": "minecraft:item" }`，返回该 builder 的成员表，每行一个成员。

---

## 2. 数据源选型

有四个候选来源，结论是 **A**。

| 方案 | 内容 | 判断 |
|---|---|---|
| **A. 解析 `.neko_probe/**/*.d.ts`** | 作者面声明树，即 IDE 与 AI 读的同一份产物 | ✅ **选它** |
| B. 新增 probe backend 输出 JSON | 复用 probe IR，直接吐结构化数据 | 渲染逻辑与 TS backend 重复，必然漂移 |
| C. 读 `api-manifest-core.json` | 已冻结的符号表 golden | ❌ 覆盖面与信息量都不够，见 2.2 |
| D. 运行时向游戏内 HTTP 查询 | 活客户端反射 | ❌ 要求游戏在跑，且要动 mod |

### 2.1 为什么是 A

1. **唯一真源，不可能漂移。** 产品就是 IDE 补全和 AI 模板读的那份 `.d.ts`。索引器读它，就不存在"索引说的和实际能调的不一致"这类 bug——而这正是方案 B 的内在风险（TS backend 有 8 个生成器：`IndexFileGenerator` / `AdapterAliasGenerator` / `EventDeclarationGenerator` / `BindingDeclarationGenerator` / `RecipeEventDeclarationGenerator` / `ManagedApiDeclarationGenerator` / `RegistryBuilderTsRenderer`，JSON backend 要重写一遍这 8 类的派生逻辑）。
2. **零 Java 改动。** 不动 `ProbeBackend` SPI、不动 `ProbeCoordinator`、不动 `ProbeOutputCommitter`，不引入 golden 变更审阅成本。整个功能是一个仓库内独立 npm 包。
3. **信息量刚好够。** `.d.ts` 里已包含：JSDoc（`@Doc`/`@Param`/`@Return` 渲染结果）、`@deprecated`、`@Overload` 展开的多签名、`$Foo_` 输入别名、side 归属（靠目录）、`@special` 字面量、events 命名空间函数、JSX/managed API。
4. **顺带支持 Python。** 同一套索引思路对 `.pyi` 复用，只是换一个解析器。

### 2.2 为什么不选 C

`api-manifest-core.json`（16.5KB）的真实内容长这样：

```json
{
  "catalogSchemaVersion": 1,
  "apiVersion": "0.12.0",
  "symbols": [
    { "id": "global:ID", "signatures": ["()"] },
    { "id": "member:ID.of", "signatures": ["(PRIMITIVE:string)", "(PRIMITIVE:string,PRIMITIVE:string)"] },
    { "id": "member:JsonIO.parse", "signatures": ["(PRIMITIVE:string)"] }
  ]
}
```

**没有文档、没有参数名、没有返回值、没有 deprecation、没有类型**（`PRIMITIVE:string` 不是作者能用的类型写法），而且 `modules: []`、`capabilities: []`、`platform.loader: "test"`——它是给第三方插件工具读的**内核**符号表，不是脚本作者面。它能回答"这个方法存在吗"，回答不了"怎么调"。

不过它有一个不可替代的用处：**作为索引器的交叉校验基线**（见 §10.2）。

---

## 3. 架构

```text
        ┌─ 游戏内 /nekojs probe ─┐
        │  ProbeCoordinator      │   （完全不动）
        │  → ProbeOutputCommitter│
        └───────────┬────────────┘
                    ▼
   <gameDir>/.neko_probe/typescript/**/*.d.ts       ← 唯一真源
   <gameDir>/.neko_probe/python/nekojs/**/*.pyi
                    │
                    ▼  一次性索引（可 --watch 增量）
   ┌────────────────────────────────────┐
   │ nekojs-api-mcp / indexer           │  解析 .d.ts → 结构化记录
   │  · TS declaration parser           │
   │  · 符号提取 / 歧义检测 / 别名解析   │
   │  · 落盘 <cache>/<profile>.index.json│
   └────────────────┬───────────────────┘
                    ▼
   ┌────────────────────────────────────┐
   │ MCP server（stdio）                │  6 个 tool + resources
   │  nekojs_search / describe / members │
   │  events / builders / compare       │
   └────────────────┬───────────────────┘
                    ▼
            AI / MCP 客户端
```

### 3.1 技术栈与形态

- **Node.js + TypeScript + `@modelcontextprotocol/sdk` + `zod`，stdio 传输。**
  与既有的 `minecraft-mod-mcp` 桥接器同构（那个是 `"type":"module"`、`@modelcontextprotocol/sdk ^1`、`zod ^3`、`tsup` 打包、`npx` 启动、纯 stdio），用户侧的心智负担为零。
- **仓库内路径 `tools/nekojs-api-mcp/`**，独立 `package.json`，**不接 gradle**。理由：它不参与 mod 构建、不是 mod 的运行时依赖，接进 stonecutter 版本树只会增加每轮的构建面。
- **只读。** 不写任何东西进 `.neko_probe`（那是 probe 的地盘），索引缓存放 `<cache>/` 或用户指定目录。
- **不需要游戏在跑。** 与 `minecraft-mod-mcp` 的根本区别：那个是"驱动活客户端"，这个是"读产物文件"。两者互补，可同时用。

### 3.2 索引器做什么

对每个 `.d.ts` 文件：

1. 用 `typescript` 包的 parser 建 AST（它是纯解析器，无运行时依赖，快）。
2. 按目录判定 side、来源与 `provenance.source`：
   - `@package/**` → `side: "both"`、`source: "reflected"`，Java 源路径可还原（`@package/net/minecraft/world/item/$ItemStack` → `net.minecraft.world.item.ItemStack`）；
   - `@side-only/{startup,server,client}/**` → 该 side 专属、`source: "reflected"`；
   - `@manual/**` → `source: "manual-declaration"`（**注册表 builder 面就在这里**，`NekoRegistryDeclarations` 产出）；
   - `@special/types/**` → 字面量 union，单独归类；
   - `@nekojs/managed/**` → managed API。
3. 抽取记录：`class / interface / type-alias / function / const / namespace / event-handler / registry-literal-union / builder`。
4. 解析 JSDoc：正文段落、`@param`、`@returns`、`@deprecated <text> Use <x> instead.`。
5. **平台判定**：`platforms` 由"该符号在所索引的 profile 产物里存在与否"直接得出，不需要理解 Stonecutter 预处理语法。NeoForge 的树里有 `ItemBuilder` 与 `item` 糖方法，Fabric 的树里没有 → 事实如此。
6. **重载分组与歧义检测**（§7.3）。
7. 建立倒排索引：`id`、`jsName`、`javaFqn`、`memberName`、`side`、`platforms`、`kind`、文档词。

### 3.3 落盘格式

`<cache>/<profile>.index.json`，结构大致：

```json
{
  "schemaVersion": 1,
  "profile": "26.2.0-fabric",
  "builtAt": "2026-09-15T10:22:31Z",
  "sourceRoot": "<gameDir>/.neko_probe/typescript",
  "sourceFingerprint": { "files": 412, "bytes": 3927114, "hash": "sha256:..." },
  "types": [ ... ],
  "members": [ ... ],
  "events": [ ... ],
  "builders": [ ... ],
  "literals": [ ... ]
}
```

多 profile 并存：`<cache>/1.21.1.index.json`、`<cache>/26.2.0-fabric.index.json` 等。这是本设计里价值最高的一根轴，理由见 §7.2。

---

## 4. 工具面

6 个 tool，全部带 token 上限与分页。工具名统一 `nekojs_` 前缀，避免和其它 MCP server 的 `search` / `describe` 撞名。

### 4.1 `nekojs_search`

模糊/前缀检索符号。**默认返回紧凑行**，不是完整声明。

```jsonc
// 输入
{ "query": "food", "kind": "method",
  "profile": "26.2.0", "limit": 20 }
```

```jsonc
// 输出
{
  "profile": "26.2.0",
  "index": { "builtAt": "2026-09-15T10:22:31Z", "stale": false },
  "total": 2, "returned": 2, "truncated": false,
  "rows": [
    { "id": "ItemBuilder.food", "kind": "method", "side": "startup",
      "platforms": ["neoforge"],
      "sig": "food(cb: (f: FoodBuilder) => void): void",
      "doc": "配置食物属性（nutrition/saturation/效果等），复合配置保留方法面。",
      "deprecated": null, "flags": [] },
    { "id": "FoodBuilder.alwaysEat", "kind": "method", "side": "startup",
      "platforms": ["neoforge"], "sig": "alwaysEat(): FoodBuilder",
      "doc": null, "deprecated": null, "flags": [] }
  ]
}
```

（这两行都能在 `NekoRegistryDeclarations.java` 里逐字复核，包括那个容易猜错的 `: void`。）

`kind` 取值：`class | interface | type | function | method | property | static | const | event | handler | builder | registry-literal | namespace`。

`platforms` 直接过滤：`{ "query": "food", "platform": "fabric" }` 时上面前两行不该出现，并附 `hint: "2 项因平台守卫被过滤（见 nekojs_builders）"`——**静默过滤会让人以为 API 不存在**。

### 4.2 `nekojs_describe`

单个符号的完整展开。

```jsonc
// 输入
{ "id": "ItemBuilder.food", "profile": "26.2.0" }
```

```jsonc
// 输出
{
  "id": "ItemBuilder.food",
  "kind": "method",
  "owner": "ItemBuilder",
  "side": "startup",
  "platforms": ["neoforge"],
  "signature": "food(cb: (f: FoodBuilder) => void): void",
  "overloads": [
    { "signature": "food(cb: (f: FoodBuilder) => void): void" }
  ],
  "doc": "配置食物属性（nutrition/saturation/效果等），复合配置保留方法面。",
  "params": [ { "name": "cb", "type": "(f: FoodBuilder) => void", "doc": null } ],
  "returns": { "type": "void", "doc": null },
  "deprecated": null,
  "declaration": "food(cb: (f: FoodBuilder) => void): void;",
  "provenance": {
    "source": "manual-declaration",
    "file": ".neko_probe/typescript/@manual/index.d.ts",
    "generator": "NekoRegistryDeclarations.register (com.tkisor.nekojs.wrapper.registry.gen)",
    "javaImpl": "ItemBuilder.food(Consumer<FoodBuilderJS>)  — wrapper/registry/gen/ItemBuilder.java:137"
  },
  "related": { "seeAlso": ["FoodBuilder"], "callbackArg": "FoodBuilder" }
}
```

`declaration` 字段是**原文逐字**（从 AST 节点还原），保证 AI 拿到的是可直接粘进脚本的写法。

注意 `provenance.javaImpl` 这一条：手写声明（`@manual`）与反射声明（`@package`）的来源要区分开。前者在声明树里看不出对应哪个 Java 方法，但索引器可以对 builder 面做一次 **宽松的符号名匹配**（`food` → `food`，`maxStackSize` → `setMaxStackSize`）给出提示。这是**尽力而为的提示，不是事实**，字段命名与 `source: "manual-declaration"` 一起表明它的可信度低于反射来源。

### 4.3 `nekojs_members`

大类型的成员分页浏览，只给名字与一行签名。

```jsonc
// 输入
{ "type": "$Player", "filter": "food", "includeInherited": false,
  "page": 0, "pageSize": 50, "profile": "26.2.0-fabric" }
```

返回 `members[]` + `page` / `pageSize` / `total` / `hasMore`，以及按 kind 的数量直方图，方便 AI 决定下一页怎么翻：

```jsonc
{ "counts": { "method": 212, "property": 68, "static": 9 }, "total": 289, "hasMore": true }
```

### 4.4 `nekojs_events`

事件目录。这是脚本作者第一高频需求，单独成 tool 是因为它的返回形状和 search 不同：**要把 payload 字段摊平**，而不是让 AI 再去 describe 那个 payload 类型。

```jsonc
// 输入
{ "side": "server", "filter": "block", "profile": "26.2.0-fabric" }
```

```jsonc
// 输出
{
  "events": [
    {
      "handler": "BlockEvents.rightClicked",
      "side": "server",
      "cancelable": true,
      "platforms": ["neoforge", "fabric"],
      "payload": "BlockRightClickEventJS",
      "signature": "rightClicked(handler: ((event: BlockRightClickEventJS) => void)): void",
      "dispatchKey": "$Block",
      "fields": [
        { "name": "player", "type": "$Player",          "doc": "The clicking player." },
        { "name": "level",  "type": "$Level",           "doc": "The level the interaction happened in." },
        { "name": "pos",    "type": "$BlockPos",        "doc": "Position of the clicked block." },
        { "name": "state",  "type": "$BlockState",      "doc": "Block state of the clicked block." },
        { "name": "hand",   "type": "$InteractionHand", "doc": "The hand used (main hand or off hand)." }
      ],
      "methods": [
        { "name": "getBlock", "sig": "getBlock(): $Block", "doc": "The block that was clicked." }
      ],
      "cancel": "listener 返回 true 即取消（无 event.cancel()）",
      "platformNotes": [
        "fabric 侧由 UseBlockCallback 转换；NeoForge 侧直传原生 RightClickBlock",
        "攻击语义差异：fabric 的 AttackBlockCallback 只在生存模式触发，NeoForge 侧全模式"
      ]
    }
  ]
}
```

`fields` 里每一条都直接来自 `BlockRightClickEventJS` 的 `@Doc` 注解（`src/main/java/com/tkisor/nekojs/wrapper/event/block/BlockRightClickEventJS.java`），不需要 AI 再去 describe 那个 payload 类型——**这正是单独给它一个 tool 的理由**。

`cancel` 与 `platformNotes` 是**从 agent 模板和源码 javadoc 里搬进来的领域知识**。现在"可取消事件靠 return true 取消"只是模板正文里的一句自由文本，容易被 AI 忽略；放进每次事件的返回里就变成了不会漏的字段。同理 `platformNotes` 那条攻击语义差异，原本埋在 `FabricBlockEventBindings` 的类 javadoc 里。

### 4.5 `nekojs_builders`

注册表 builder 的链式成员面。两个数据源在索引里合并成一张表：

- **手写声明**（脚本面真源）：`NekoRegistryDeclarations.java` 注册进 `@manual/index.d.ts` 的 `interface ItemBuilder { ... }` 等——**builder 类型名不带 `$` 前缀**，与 `@package` 下的 Java 类区分开。
- **结构化契约**：`RegistryBuilderSurfaceEntry`（`common/src/main/java/com/tkisor/nekojs/api/catalog/RegistryBuilderSurfaceEntry.java`）的 `MemberKind` 三取值 `WRITABLE_PROPERTY / READ_ONLY_PROPERTY / METHOD`，成员分类用它的词汇，不另起一套。

```jsonc
// 输入
{ "registry": "minecraft:item", "profile": "26.2.0" }
```

```jsonc
// 输出（成员取自 NekoRegistryDeclarations 的真实脚本体面）
{
  "builder": "ItemBuilder",
  "registryKey": "minecraft:item",
  "sugar": { "name": "item", "platforms": ["neoforge"],
             "note": "Fabric 侧不注册此糖方法（Stonecutter //? if neoforge 守卫）" },
  "callSite": "RegistryEvents.register(event => { event.item('mymod:ruby', b => { ... }) })",
  "members": [
    { "name": "maxStackSize", "kind": "writable-property", "tsType": "number" },
    { "name": "maxDamage",    "kind": "writable-property", "tsType": "number" },
    { "name": "fireResistant","kind": "writable-property", "tsType": "boolean" },
    { "name": "rarity",       "kind": "writable-property", "tsType": "string" },
    { "name": "glowing",      "kind": "writable-property", "tsType": "boolean" },
    { "name": "burnTime",     "kind": "writable-property", "tsType": "number" },
    { "name": "groupTab",     "kind": "writable-property", "tsType": "string | null" },
    { "name": "food",         "kind": "method",
      "tsType": "food(cb: (f: FoodBuilder) => void): void",
      "doc": "配置食物属性（nutrition/saturation/效果等），复合配置保留方法面。" },
    { "name": "tag",          "kind": "method", "tsType": "tag(...tags: string[]): void" }
  ],
  "relatedBuilders": [
    { "builder": "FoodBuilder",
      "members": [
        { "name": "nutrition",  "kind": "method", "tsType": "nutrition(v: number): FoodBuilder" },
        { "name": "saturation", "kind": "method", "tsType": "saturation(v: number): FoodBuilder" },
        { "name": "alwaysEat",  "kind": "method", "tsType": "alwaysEat(): FoodBuilder" },
        { "name": "fastEat",    "kind": "method", "tsType": "fastEat(): FoodBuilder" },
        { "name": "effect",     "kind": "method",
          "tsType": "effect(effectId: string, durationTicks: number, amplifier: number, probability: number): FoodBuilder" }
      ] }
  ]
}
```

注意 `food` 的返回值是 **`void` 而不是 `ItemBuilder`**——它保留了方法面而非链式面（`ItemBuilder.java:136` 的注释"复合配置保留方法面"）。这种反直觉的细节正是 AI 靠猜最容易猜错的，也正是这个 tool 存在的意义。`relatedBuilders` 把回调参数类型一并展开，省掉一次 `describe`。

**每个 builder 与糖方法都必须带 `platforms`。** `NekoRegistryDeclarations.java` 里 `item` / `block` / `fluid` / `entityType` / `enchantment` / `particleType` / `creativeModeTab` 七个糖方法整段被 `//? if neoforge {` 包住，Fabric 只保留 `soundEvent` / `mobEffect` / `potion` / `paintingVariant` / `villagerType` 五个。索引器从实际读到的 profile 产物里判定，不需要理解 Stonecutter 语法——**NeoForge profile 的声明树里有，Fabric 的没有，这就是事实本身**。

### 4.6 `nekojs_compare`

两个 profile 的 API 差异。**本仓库当前的开发模式（stonecutter 多节点、NeoForge↔Fabric 互移植）里，这是杠杆最高的一个 tool。**

profile 天然是两个轴的组合（版本 × 加载器），diff 必须先判断自己变的是哪个轴，否则会把"Fabric 上没实现"报成"新版本删掉了"：

```jsonc
// 输入
{ "base": "26.2.0", "target": "26.2.0-fabric", "filter": "registry" }
```

```jsonc
// 输出
{
  "base": "26.2.0", "target": "26.2.0-fabric",
  "axes": { "version": "same", "loader": "changed" },
  "summary": { "platformGated": 7, "removed": 0, "added": 0, "signatureChanged": 0 },
  "platformGated": [
    { "id": "RegistryEvent.item",  "sig": "item(id: string, cb: (b: ItemBuilder) => void): ItemBuilder",
      "presentIn": "neoforge", "absentIn": "fabric",
      "reason": "Stonecutter //? if neoforge 守卫（NekoRegistryDeclarations）" },
    { "id": "RegistryEvent.block", "sig": "...", "presentIn": "neoforge", "absentIn": "fabric", "reason": "同上" }
  ],
  "platformNotes": [
    "RegistryEvent 的 platformGated 差异不影响平台无关的 5 个糖方法"
  ]
}
```

同一个 tool 换一个 profile 对，报的就是版本轴差异：

```jsonc
// 输入 { "base": "1.21.1", "target": "26.2.0", "filter": "$Level" }
// 输出 axes: { "version": "changed", "loader": "same" }
//   summary: { removed: 0, added: 3, signatureChanged: 2 }
//   signatureChanged: [ { id: "...", base: "...", target: "..." } ]   ← 并排 before/after
```

轴上只变一个时，差异归因是确定的；两个轴同时变（`1.21.1` → `26.2.0-fabric`）时，输出会把差异标成 `attribution: "ambiguous"` 并建议拆成两次比较。**不要给出无法归因的 diff。**

---

## 5. 资源面

除了 tool，把原始文件也挂成 MCP resource——需要整文件时不必走 tool 的参数化裁剪。

- `nekojs://api/<profile>/brief` — **一段人写的速查页**，内容等价于 agent 模板里 `## NekoJS Notes` 那几段（脚本类型、事件取消语义、type wrapping 的 `_` 后缀、`java:` 导入写法、`Java.loadClass`），但由 MCP 直接提供，不再依赖把模板文件写进 `<gameDir>/.github/agents/`。它替代的是模板里"怎么翻声明树"的那部分，人格与 workflow 部分另议（§13.4）。
- `nekojs://api/<profile>/file/<相对路径>` — 按需读任意一个 `.d.ts` 原文（当 AI 明确知道要哪个文件时，比 tool 更直接）。
- `nekojs://api/<profile>/index-status` — 索引新鲜度、文件数、构建时间。

**不做** `@special` 全量资源。它就该只在 tool 里按需命中，不提供"整包拉走"的入口。

---

## 6. 索引新鲜度

probe 产物由游戏内 `/nekojs probe` 生成，可能在索引之后被覆盖。契约：

- 每次响应带 `index.builtAt` 与 `index.stale`。
- `stale` 判定：`sourceFingerprint.hash` 与当前磁盘重算值不一致 → `stale: true`，并在响应里附 `hint: "probe 产物已更新，运行 nekojs-api-mcp index --profile <p> 或等待 --watch 重建"`。
- `--watch` 模式：监听 `.neko_probe` 目录（chokidar），去抖 500ms 后重建，不阻塞已建立的 tool 调用（旧索引继续服务）。
- 冷启动无索引时**不自动全量建**（真实树可能很大），而是返回明确错误说明要跑哪条命令。自动建会让第一次 tool 调用超时。

---

## 7. 关键设计点

### 7.1 token 预算纪律（这是整个东西的意义所在）

| 原则 | 落地 |
|---|---|
| 默认紧凑 | `search` 默认只回 `id/kind/side/sig/doc/flag`，不回 `declaration` |
| 硬上限 | 每个 tool 有响应字节上限，超了截断并置 `truncated: true`，附 `hint` 指路而不是静默截断 |
| 分页 | `members` 必须分页（默认 50），返回 `counts` 直方图帮 AI 决定是否该翻下一页 |
| 两级 detail | `detail: "brief" \| "full"`，默认 `brief` |
| 不算重 | `describe` 一次只允许一个 `id`（数组输入会让响应体积失控） |

对照数据：手工整文件读一个 Java 包 index.d.ts 是万级 token；一次 `search` 是千级以内，`describe` 是百级。

### 7.2 多版本 profile：版本 × 加载器两个轴

`versions/` 下并存 `1.21.1` / `26.1.2` / `26.1.2-fabric` / `26.2.0` / `26.2.0-fabric`，磁盘上已有六个 `.neko_probe` 目录位置。profile 名就是"版本 + 加载器"，索引按 profile 分开存，tool 全部接受可选 `profile`：

- 未指定时：若只有一个索引，用它；若多个，**报错并要求指定**，不猜。
- 每个响应都回显实际使用的 profile（AI 常会忘记自己查的是哪个版本）。

**平台可用性必须是一等字段，不是备注。** 这不是设计洁癖——`NekoRegistryDeclarations.java` 里七个注册表糖方法整段包在 `//? if neoforge {` 中，同样的脚本在 Fabric 上会因为"`event.item` is not a function"挂掉，而声明树在两边也不会给出任何提示。索引器按实际读到的产物判定 `platforms`，让"这个方法在我的平台上存在吗"变成查询而不是经验。

因为 profile 是两个轴的组合，`nekojs_compare` 必须先判断自己变的是哪个轴（见 §4.6），否则会把"Fabric 上没实现"误报成"新版本删掉了"——这类错误归因比不回答更糟。

### 7.3 重载歧义检测——把自由文本警告变成可查询事实

agent 模板里有一段全靠 AI 自觉的警告：

```markdown
Beware of methods that have same names and same count of parameters — Java can easily
distinguish them by their parameter types, but sometimes in NekoJS they might lead to
ambiguity due to type wrapping. Hint these methods if you find them relevant to the
question and provide explicit naming that contains method signature to disambiguate,
for example:

    class Foo{
        "bar(java.path.to.Class)"(param: $Class)
    }
```

索引器在建立重载分组时**顺手就能算出这个**：同一个名字 + 同一个参数个数 + 参数类型不同 → 打 `ambiguous-overload` flag。

于是：
- `nekojs_search` 的行里带 `flags: ["ambiguous-overload"]`；
- `nekojs_describe` 对这类符号**直接给出消歧写法**：

```jsonc
{
  "id": "$Foo.bar",
  "flags": ["ambiguous-overload"],
  "disambiguation": {
    "reason": "两个重载参数个数相同，type wrapping 下无法按实参区分",
    "syntax": "\"bar(net.minecraft.world.item.ItemStack)\"(param: $ItemStack)",
    "note": "非必要不用；优先改写脚本避开该重载"
  }
}
```

这是纯解析就能得到的确定性信息，零 Java 改动，却把一个"AI 要记得小心"的坑变成了"AI 一定看得见"的字段。

### 7.4 `@special` 从上下文炸弹变成一次成员测试

`@special/types/index.d.ts` 装的是全游戏 id 字面量 union：

```ts
declare module "@special/types" {
    export namespace RegistryTypes {
        type SampleBlock = "testcraft:alpha" | "testcraft:beta" | "packns:gamma";
        type SampleBlockTag = "testcraft:all_blocks";
        type Namespace = "othermod" | "packns" | "testcraft";
    }
}
```

索引器把它拆成 `registry-literal` 记录，于是：

- `nekojs_search { "query": "testcraft:alpha" }` → 命中一行 `{ id: "RegistryTypes.SampleBlock#testcraft:alpha", kind: "registry-literal" }`，回答"这个 id 合法吗、属于哪个注册表"。
- 反向查询：`nekojs_search { "query": "", "kind": "registry-literal", "type": "RegistryTypes.SampleBlock", "limit": 20 }` 分页列成员。

**永远不需要把这个 union 整块喂给模型。**

---

## 8. 与现有通道的关系（不重复什么）

| 现有通道 | 关系 |
|---|---|
| `.neko_probe/**/*.d.ts` + `jsconfig.json` | **本方案的输入**。IDE 补全照旧，不受影响。 |
| `AgentTemplateGenerator` → `.github/agents/*.agent.md` | **部分被替代**（待裁定，见 §13.4）。模板里"怎么翻声明树"的流程说明退役成 MCP 的 `brief` resource；人格与 workflow 部分保留。 |
| `ApiManifestGenerator` → `api-manifest-core.json` | **不重复**。它是给第三方插件工具读的、构建期冻结的**内核**符号表；本 MCP 是给脚本作者/AI 用的开发期**全量**表面。定位不同，且互为校验（§10.2）。 |
| python backend 的 `.pyi` | 同一索引思路的第二语言。**Phase 3**，先做 TS。 |
| `/nekojs probe` 游戏内命令 | 上游生产者，不动。MCP 只是它的消费者。 |
| GitHub wiki（`wiki/Probe-类型生成.md`） | 人读的叙述文档，不生成。MCP 是机器读的索引。 |

---

## 9. 已知边界

### 9.1 硬限制：JS 名 → Java 成员名在 `@Remap` 下不可逆

`@Remap("jsName")` / `@RemapByPrefix` 会改脚本面名字，运行时与 probe 共用 `JavaMemberIndex.remapName(...)` 这同一个函数（`common/src/main/java/com/tkisor/nekojs/api/JavaMemberIndex.java:262`），所以 `.d.ts` 里出现的是**改后的 JS 名**——原名在声明树里丢了。

**影响**：`nekojs_describe` 的 `javaSourceHint` 对被 remap 过的成员只能定位到类，不能定位到具体 Java 方法名。

**处理**：先接受，并在响应里显式标注 `provenance.javaMemberName: null, provenance.note: "name remapped; Java-side member name not recoverable from .d.ts"`。不假装知道。

**如果将来确实需要**：这是全案**唯一**一处加一点 Java 侧产出就划算的地方——一个极小的 sidecar（`name-map.json`，`{jsName, javaName, classFqn}` 三元组）即可闭合。**不放进 Phase 1**，它会把"零 Java 改动"这个最大优点换掉，需要单独裁定。

### 9.2 其它边界

- **看不到 Java 泛型擦除前的信息。** 看到的是作者能看到的，这正是设计意图。
- **不保证声明树是当前 mod 版本生成的。** probe 产物是快照，`/nekojs reload` 不重生成声明。`stale` 只能检测"文件变了"，检测不了"文件本来就旧"。响应里回显 `builtAt` 让 AI 自己判断。
- **不解析 `.d.ts` 的语义等价性。** 只做结构索引，不做类型推导（不做子类型查询的传递闭包——`includeInherited` 只按声明的 `extends`/`implements` 一跳一跳走，走不通就如实报告）。
- **不做写操作。** 不改脚本、不生成脚本、不触发 probe。

---

## 10. 验收与测试

### 10.1 单元

- 对 `common/src/test/resources/nekojs/probe/legacy-tree/`（31 个 `.d.ts`，180,570 字节）建索引。
- 断言：类型数、成员数、事件数；`$LegacyProbeFixture$SampleHelper.of` 有**两个**重载且被正确分组；`$LegacyProbeFixture$SampleWidget_` 别名被识别为 `type alias` 且出现在 `$LegacyProbeFixture$SampleWidget` 的 `related.inputAliases`。
- 索引结果作为新的 golden 提交（`tools/nekojs-api-mcp/test/golden/legacy-tree.index.json`），用与 `ProbeGoldenSupport` 同样的"regen 模式 + 显式 diff 审阅"纪律维护。

### 10.2 与 `api-manifest-core.json` 交叉校验

对 `common/src/test/resources/nekojs/golden/api-manifest-core.json` 里每个 `global:*` 与 `member:*` 符号，断言 MCP 索引里存在对应条目。

**这是防索引器盲区的最便宜手段**：如果解析器漏了某种声明形态，这个测试会直接红。注意它是**单向**断言（manifest ⊂ index），不是相等——index 覆盖面本来就更大。

### 10.3 往返验证

对索引里抽样若干类型，把 `nekojs_describe` 的 `declaration` 字段拼回一个 `.d.ts`，用 `tsc --noEmit` 验证可编译。复用现成的 `common/src/test/probe-ts/tsconfig.json`（`"typeRoots": ["./generated"]`、`strict: true`、`noEmit: true`）那套办法——它已经在做同一件事（验证生成声明真的能通过类型检查）。

### 10.4 端到端

```bash
# 1. 起客户端并生成真实产物
minecraft-mod-mcp launch 26.2.0 --loader neoforge
# 游戏内：/nekojs probe

# 2. 建索引（同时产出第 0 项任务要的真实体积数据）
npx nekojs-api-mcp index --profile 26.2.0 \
  --root "%APPDATA%\.minecraft\mcp_launcher\game\.neko_probe\typescript"

# 3. 起 MCP，问三个已知答案的问题
npx nekojs-api-mcp                  # stdio
```

三个已知答案的问题（回归基线，答案已按本仓库源码核对过）：

1. `nekojs_builders {registry:"minecraft:item", profile:"26.2.0"}` → 必须有 `food` 成员，且签名为 **`food(cb: (f: FoodBuilder) => void): void`**（返回 `void`，不是 `ItemBuilder`），`platforms` 含 `neoforge`。
   同一个查询换 `profile:"26.2.0-fabric"` → **必须报"该糖方法在此平台不存在"**，而不是返回空 members。
2. `nekojs_events {side:"server", filter:"rightClicked"}` → payload `BlockRightClickEventJS`，`fields` 恰好五项 `player/level/pos/state/hand`，`cancelable: true`。
3. `nekojs_compare {base:"26.2.0", target:"26.2.0-fabric", filter:"registry"}` → `axes.loader === "changed"`、`platformGated` 恰为 **7** 项（`item` / `block` / `fluid` / `entityType` / `enchantment` / `particleType` / `creativeModeTab`），且 `removed === 0`。

第 1、3 问的数字（`void` 返回值、7 个糖方法）都能在 `NekoRegistryDeclarations.java` 里逐字人工复核——这是选它们做基线的原因。

---

## 11. 实施计划

| 阶段 | 交付 | 验收 |
|---|---|---|
| **0. 测量** | 真实 `.neko_probe/typescript/` 的文件数与字节数 | 有数字；据此决定索引是否必须增量、是否需要流式解析 |
| **1. 索引器** | `tools/nekojs-api-mcp/` 包骨架 + `.d.ts` 解析 + 落盘 index.json | §10.1 + §10.2 绿 |
| **2. 核心 tool** | `search` / `describe` / `members` | §10.3 绿；能答出"`ItemBuilder` 的全部成员"（此阶段需手工用 `members` 逐项核对 §10.4 第 1 问的真值） |
| **3. 领域 tool** | `events` / `builders`（含重载歧义检测 §7.3、`@special` 成员测试 §7.4） | 歧义 flag 在 golden 树上有正例；`@special` 查询不返回整块 union |
| **4. 接线** | `--watch`、`stale` 判定、`brief` resource、README + 在 `docs/agents/` 补一页 | 改 `.neko_probe` 后 `stale: true` 出现 |
| **5. 多版本** | 多 profile 索引 + `nekojs_compare` | §10.4 第 3 问 |
| **6. 退役** | 按 §8 瘦身 `AgentTemplateGenerator` 的模板正文（改为指向 MCP） | 模板 diff 经审阅；`@special` 禁令由"别读"变成"用 tool 查" |

Phase 1–3 是一个可用闭环，可以独立交付并开始使用。Phase 5 依赖真实的多版本产物，可以推迟。

---

## 12. 做完之后的一天

**写一个新的 startup 脚本，注册带食物属性的物品。**

- 以前：让 AI 去 `.neko_probe` 找 `ItemBuilder`。它先 grep 到一堆 import 行，再整文件读 `@manual/index.d.ts`（全部 builder 加糖方法重载），最后给出一个差不多但对细节没把握的答案——比如把 `food(cb)` 当成链式方法写成 `b.food(f => f.nutrition(4)).maxStackSize = 16`。
- 之后：`nekojs_builders {registry:"minecraft:item"}` 一次调用，成员表带 `writable-property` / `method` 分类、完整签名，以及那条容易猜错的 `: void` 返回值。AI 直接落笔。

**把一个 NeoForge 上跑通的脚本移到 Fabric 26.2.0。**

- 以前：跑了报 `event.item is not a function` → 去两个平台的声明树里分别 grep → 发现 neoforge 那边有、fabric 这边没有 → 回源码里翻，才看到是 Stonecutter 的 `//? if neoforge` 守卫 → 再人工判断哪些糖方法是平台无关的。
- 之后：`nekojs_compare {base:"26.2.0", target:"26.2.0-fabric", filter:"registry"}`，直接告诉你变的是加载器轴、7 个糖方法被平台门禁、且**没有**任何方法被版本删除。一条查询替代整条排查链，而且不会把"Fabric 没实现"误判成"新版本删了"。

**让 AI 别踩那个 type wrapping 的歧义坑。**

- 以前：模板里一段警告，AI 心情好才记得。
- 之后：那个方法行上挂着 `flags: ["ambiguous-overload"]`，`describe` 里直接给出消歧语法。

**查询一个注册表 id 是否合法。**

- 以前：模板明令禁止读 `@special`，于是这类问题只能靠猜或让游戏报错。
- 之后：`nekojs_search {query:"create:andesite_alloy"}`，一行命中，附带它属于哪个注册表。

**这四个场景的共同点**：都不再需要 AI 去"翻声明树"。声明树还在那儿（IDE 和 tsc 还要用），但 AI 不再手爬它。

---

## 13. 待裁定

1. **profile 未指定且有多个索引时报错还是取最新？** 本提案选**报错**（不猜版本，版本猜错比报错贵）。
2. **索引缓存放哪？** 提案倾向 `<repo>/.neko-cache/`（跟 `.memsearch/` 一格），需确认是否要进 `.gitignore`——建议进。
3. **是否在 Phase 1 就支持 Python `.pyi`？** 提案选**先不做**：TS 是主面，且 Python 产物是另一套语法（`__init__.pyi` + `pyrightconfig`），并行做会让 Phase 1 翻倍。
4. **`AgentTemplateGenerator` 是否真的瘦身？** 它现在产出的是给 VS Code custom agents 用的完整人格模板，不只是"怎么翻树"。本提案只主张删掉其中"怎么翻声明树"的部分（§8），人格与 workflow 部分保留。这一步影响用户既有的 VS Code 工作流，需要用户拍板。
5. **要不要 `nekojs_compare` 之外再加"这个脚本用了哪些 API，哪些在新版本没了"的脚本扫描？** 现在不做——它需要解析用户的 `.ts` 脚本，是另一个量级的工作（虽然 `NekoTypeScriptCompiler` 已有 TS 前端可复用）。
