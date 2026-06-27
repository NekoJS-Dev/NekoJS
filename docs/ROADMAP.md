# NekoJS 路线图

NekoJS 的目标是在 NeoForge 上提供一个基于 GraalVM/GraalJS 的现代 Minecraft JavaScript 脚本运行时。当前方向是保持轻量、JSON-first、贴近 Minecraft / NeoForge 原生类型，同时吸收 KubeJS 中能明显改善脚本体验的 helper、wrapper、adapter 和调试能力。

## 当前设计原则

- [x] 保持 datapack JSON-first 的 recipe 设计，不迁移 KubeJS 完整 `RecipeSchema` / `RecipeComponent` / 自动 builder 生成系统。
- [x] 保留 vanilla Java 类名绑定，例如 `Item`、`ItemStack`、`Items`、`Ingredient`、`FluidStack`。
- [x] 额外能力优先通过 `ItemJS`、`Ingredient`、`Fluid`、mixin extension 和轻量 wrapper 提供。
- [x] 避免把 Graal `Value` 暴露为常规脚本 API 参数，优先使用 Java 类型或函数式接口。
- [x] 平台相关验证优先编译两个平台模块，不单独编译 `common`。

## 已完成的基础能力

- [x] 独立 GraalJS Context + 共享 Graal Engine 的脚本运行基础。
- [x] `startup_scripts/`、`server_scripts/`、`client_scripts/` 脚本目录。
- [x] `.js/.mjs/.cjs` 直接执行；`.ts` 由内置 erasable TypeScript 前端支持；`.jsx/.tsx` 由内置轻量 classic runtime lowering 支持；后续高级 TS/TSX/JSX 语法优先收敛到 NekoJS 本体语言前端。
- [x] 核心事件组、绑定注册、脚本属性和插件注册机制。
- [x] 工作区生成、游戏内工作区 UI、脚本同步和基础编辑体验。
- [x] `NekoId` 稳定脚本侧 ID 类型，替代 `IDJS.of` 返回 `Object` 的旧设计。
- [x] `JSTypeAdapter.getPrecedence()`，降低宽泛 adapter 参与 Graal overload resolution 时的歧义。
- [x] 26.1 早期 reload 阶段的 `ItemStack` 安全构造，避免 `Components not bound yet`。
- [x] CI Build workflow：编译/构建两个平台模块，区分 dev/release artifacts，并用 commit 首行提取 release version / summary。
- [x] `RecipeJsonValue` 边界类型：recipe builder/custom 的任意 JS JSON 输入收敛到 adapter/converter 层，builder 不直接暴露 Graal `Value` / 宽泛 `Object`。
- [x] 脚本事件错误日志增强：recipe/event/timer callback 错误同时写 script logger 与主 NekoJS logger，并显示 JS 行列、上下文和列指针；错误追踪按脚本路径替换/清理，避免同一文件修改或重跑后 stale error 累积。
- [x] `ScriptEvents` startup API：startup scripts 可把 NeoForge 原生事件注册成 server/client 脚本侧事件方法，并接入 reload listener 清理；“任意脚本类型”语义由 `ScriptTypePredicate.any()` 表达，不再使用 `ScriptType.COMMON`。

## 已完成的 KubeJS-lite API 迁移

- [x] `ItemJS.of(...)` 返回 `ItemStack`，支持脚本友好的物品栈创建。
- [x] `ItemStack` mixin extension：`withCount`、`copy`、`getId`、`getMod`、`getBlock`、`enchant`、`hasEnchantment`、`matches`、`asIngredient`、`weakNBT`、`strictNBT` 等。
- [x] `Ingredient` helper：`of`、`item`、`tag`、`any`，并明确暂不支持 `not` 的假语义。
- [x] `IngredientFactory` wrapper：`or`、`and`、`intersect`、`except`、`subtract`、`matches`、`first`、`stacks`、`displayStacks`、`withCount`。
- [x] `SizedIngredientJS` 与 `SizedIngredientAdapter`。
- [x] `Fluid` / `FluidIngredient` / `SizedFluidIngredient` 相关 helper、wrapper 和 adapter MVP。
- [x] recipe JSON builder、recipe entry wrapper、filter、递归 `replaceInput` / `replaceOutput`。
- [x] Recipe lifecycle hooks：外部插件可通过 `registerRecipeLifecycleHooks` 或 `RecipeLifecyclePlugin` 注册 `beforeRecipeLoading` / `afterRecipes`；脚本侧可用 `ServerEvents.afterRecipes` 在普通 recipe 脚本后、最终 codec parse 前检查或最终改写 recipe JSON。
- [x] `RecipeJsonValueAdapter` / `RecipeJsonValueConverter`，支持 JS object/array 内嵌 `IngredientFactory`、`ItemStack`、fluid wrapper 等 recipe-aware JSON 序列化。
- [x] `event.recipes.minecraft` 常用 vanilla recipe helper。
- [x] `event.forEach(...)` 使用 `Consumer<RecipeEntryJS>`，不把 `Value callback` 作为常规 API。

### Recipe 系统重构（参考 KubeJS Schema，保持 NekoJS 轻量）

**目标**：让用户能像 KubeJS 一样用类型安全的方式调用配方类型，但不引入完整的 `RecipeSchema/RecipeComponent` 系统。

```js
// 目标体验
event.recipes.minecraft.crafting_shaped('result', ['AAA'], { A: '#planks' })
event.recipes.create.mixing('result', ['input1', 'input2'])
```

KubeJS 的做法是 `RecipeSchema(keys).constructors()`，每个 key 有 `RecipeComponent<T>` 定义类型+Codec。插件用 Java 代码注册 `namespace("minecraft").register("crafting_shaped", schema)`。

NekoJS 的做法是增强已有的 `RecipeTypeDefinition`（data-driven JSON + `RecipeFieldKind`），让它兼职 schema 角色：

```java
// 新设计：RecipeTypeDefinition 升级为轻量 Schema
RecipeTypeDefinition {
    type: "minecraft:crafting_shaped",
    keys: [
        { name: "result",   kind: ITEM_STACK,   role: OUTPUT },
        { name: "pattern",  kind: JSON,          role: INPUT },
        { name: "key",      kind: JSON,          role: INPUT },
        { name: "group",    kind: STRING,        role: OTHER, optional: true }
    ]
}
```

与 KubeJS 的关键差异：**NekoJS 用 data-driven JSON 定义 + 原版 Codec 自动推断，而不是 Java 代码手写 Schema**。

**实现阶段**：

- [ ] **阶段 1 — 原版 schema 自动生成**: 启动时扫描 `BuiltInRegistries.RECIPE_SERIALIZER`，对每个 recipe type 从它的 Codec 推断出 field 结构。不需要手写 ~200 个 schema。
- [ ] **阶段 2 — 类型安全构造器**: `RecipeTypeDefinition` 的 keys 数组新增 `role`（INPUT/OUTPUT/OTHER）和 `optional`。构造时校验参数数量和类型，错误有行号定位。
- [ ] **阶段 3 — Codec 集成**: 每个 `RecipeFieldKind` 绑定对应的原版 Codec。`INGREDIENT` → `Ingredient.CODEC`，`ITEM_STACK` → `ItemStack.CODEC`，`FLUID_STACK` → `FluidStack.CODEC`。确保序列化与 vanilla 格式完全一致。
- [ ] **阶段 4 — Component-aware replacement**: `replaceInput`/`replaceOutput` 通过 schema 的 role 字段来定位 INPUT/OUTPUT key，用对应的 Codec 匹配而不是 raw JSON 字符串比较。修复复合 ingredient 无法部分替换的问题。
- [ ] **阶段 5 — 错误上下文**: recipe 创建/修改时携带 sourceLine，出错时输出具体脚本位置 + recipe type + 字段名。`

**不采纳的部分**：
- 完整的 `RecipeComponent<T>` 类型系统（40+ Java 类）— 改用 `RecipeFieldKind` enum + data-driven JSON
- 自动 builder 方法生成 — 那是 Rhino `jsToJava()` 能力，NekoJS 用 `DataDrivenRecipeNamespaceProxy` 的 `ProxyExecutable` 模式
- Schema JSON loader — NekoJS 已有 `RecipeTypeDefinitionJsonLoader`

## common 迁移状态

### 已迁移或已经在 common 的能力

- [x] 事件基础设施：事件组、事件总线、脚本事件桥接所需的 common 抽象。
- [x] 脚本管理基础：`NekoJSScriptManager` 相关可共享逻辑。
- [x] 绑定聚合：`NekoBindings`。
- [x] 脚本同步通用逻辑：`ScriptSyncService`、`ScriptSyncFiles`、`ErrorSummaryDTO`。
- [x] 静态基础 helper：`IDJS`、`ColorJS`、`UUIDJS`、`StringUtilsJS`、`TimeJS`、`UtilsJS`。
- [x] `JSTypeAdapter` 和 `NekoSandboxBuilder` 的共享 adapter 注册机制。

### 可优先迁移到 common 的候选

- [ ] 事件声明类：`CommandEvents`、`EntityEvents`、`ItemEvents`、`LevelEvents`、`PlayerEvents`、`RegistryEvents`、`ServerEvents`。
- [ ] 纯 helper / static access：`ItemJS`、`FluidJS`、`FluidIngredientJS`、`NativeEventsJS`。
- [ ] 低版本差异 adapter：`JsonObjectAdapter`、`ComponentAdapter`、`CompoundTagTypeAdapter`、`IngredientAdapter`、`FluidStackAdapter`、`FluidIngredientAdapter`、`SizedFluidIngredientAdapter`、`SizedIngredientAdapter`、`RecipeFilterAdapter`。
- [ ] 低版本差异 wrapper：`NekoWrapper`、`RecipeRegistryProxy`、`FluidAmounts`、`FluidIngredientJS`、`SizedIngredientJS`。
- [ ] `MinecraftRecipeHandler`：先确认两个平台 recipe JSON 字段和 serializer helper 是否保持一致，再迁移。

### 需要 compat 层后再迁移的候选

- [ ] `ResourceLocationAdapter` / `IdentifierAdapter`：需要统一 `ResourceLocation` 与 `Identifier` 差异。
- [ ] recipe 核心类：`RecipeEventJS`、`RecipeEntryJS`、`RecipeJsonBuilder`、`RecipeFilter`、`RecipeJsonValue`、`RecipeJsonValueConverter`、`FallbackNamespaceProxy`。
- [ ] 物品/方块/实体/tag adapter：`ItemAdapter`、`BlockAdapter`、`ItemStackAdapter`、`EntityTypeAdapter`、`TagKeyAdapter`。
- [ ] `IngredientFactory` / `IngredientResolver`：需要隔离 `Ingredient` 展开、holder、component ingredient 的版本差异。
- [ ] 网络 packet record 与 `NekoJSNetwork`：需要先抽象 payload / channel 注册差异。

- [x] **common→platform 跨模块类型消除**: 尝试抽取 `RecipeEventContext` 接口到 common，但 handler 方法需要调用的 `RecipeJsonBuilder` / `serializeIngredient` 都是平台类型，common 无法引用。当前 `Function<Object,Object>` + `NekoJSCorePlugin` 中一处 cast 是现实约束下的最简方案。

### 暂不建议迁移的内容

- [ ] registry builder、registry event 和 food builder 等强平台 API 绑定代码。
- [ ] mixin、injected extension API 和需要直接改写 Minecraft 类的代码。
- [ ] GUI screen 和客户端渲染代码，除非先拆出纯数据/工具层。
- [ ] `EventBusForgeBridge` 这类直接依赖平台事件总线的桥接类。

## 短期任务

- [x] 初步收敛 adapter / resolver 边界语义：已通过 26.1 runnable 测试覆盖 null、数组/对象、无效 ID、非正 count、可变 stack copy 等关键路径。
- [x] 为 26.1 优先补 adapter / resolver 回归测试，覆盖 Graal `Value` object/array 输入与错误路径。
- [x] 继续收敛 adapter / resolver 边界语义：已覆盖 null/EMPTY、数组/对象、fluid amount 覆盖、host object copy、无效 shape，并修复 26.1 recipe reload 阶段 tag wrapper 的 registry owner 安全序列化。
- [x] 为 `RecipeJsonValue` / `RecipeJsonValueConverter` 补 26.1 runnable 测试，已覆盖 nested JS object/array、`IngredientFactory` tag wrapper、`SizedIngredientJS`、`ItemStack`、fluid wrapper、fallback namespace，并提供默认禁用的 invalid-shape 手动测试。
- [x] 增强 recipe 错误上下文：记录 builder/custom/copy 来源、recipe id、type、创建 API、prefix，并在最终 codec 失败时输出上下文和 JSON。
- [x] 增加 recipe path 操作：`setPath`、`removePath`，支持按 `ingredients.0` / `result.count` 这类 JSON path 修改/删除字段。
- [x] 细化 recipe path 操作：支持自动创建中间 object/array、反斜杠转义点号字段名。
- [x] 继续细化 recipe path 操作：支持批量路径编辑 `setPaths` / `removePaths`，以及点分、数组下标、反斜杠转义点号、括号/引号字段语法。
- [x] 增加 recipe dump/print 调试工具：`event.dump(filter)`、`event.print(filter)`。
- [x] 为 `event.recipes.minecraft` 增加 datapack type 名 raw JSON alias：`crafting_shaped(json)`、`crafting_shapeless(json)`。
- [x] 增加插件侧 recipe lifecycle hooks：`beforeRecipeLoading` 可在脚本 recipes 事件前预处理 raw JSON，`afterRecipes` 可在脚本修改后做校验、统计或最终改写。
- [x] 增加脚本侧 `ServerEvents.afterRecipes`，在普通 `ServerEvents.recipes` 后、最终 recipe codec parse 前运行。
- [x] 为 `event.recipes` 增加 namespace/type introspection：`namespaces()`、`types(namespace)`、`hasNamespace(namespace)`、`hasType(namespace, type)`，同时保留未知 namespace raw JSON fallback。
- [x] 保持 README、`docs/ROADMAP.md`、`ai_docs/` 与当前实现同步；本轮短期任务已同步整理相关文档。
- [x] 低风险 Java 清理：删除死代码（NekoUnifiedIR、NekoIRNode、NekoIRImport/Export/Binding/Scope、NekoJsDeclarationStatement、NekoModuleTransformResult）、修复 Bug（linkedEsmRecord 错误处理、SourceMap 缓存失效、moduleCache.put 竞态、isInteger("-")、ctimeMs 混淆）、消除 ESM 双重解析、精简编译器管线、IngredientJS→IngredientFactory 重命名。

## 中期任务

- [ ] 按“低版本差异、无平台副作用、可编译验证”的顺序推进 common 迁移。
- [ ] 为 Create 等常见 mod 增加轻量 JSON helper，例如 mixing、crushing、pressing，但不引入 schema 系统。
- [ ] 扩展脚本同步安全校验：路径穿越、绝对路径、扩展名、文件数量、批量大小。
- [ ] 添加受控调度器：延迟任务、重复任务、reload 自动取消旧任务。
- [x] 添加 CI，至少构建两个受支持平台模块。
- [x] 分析确认 common 模块因无 Minecraft 依赖，仅 6 个纯工具类可迁移（RecipeCreationContext、RecipeJsonPath、RecipeJsonValue、FluidAmounts、NekoWrapper、JsonObjectAdapter）。其余 ~120 个平台文件均有 Minecraft/NeoForge import，需 compat 层才能迁移。
- [ ] 为路径校验、脚本发现、事件总线、adapter 和 recipe filter 增加聚焦测试。

## Catalog 与外部类型工具契约

NekoProbe 是独立项目，不属于本项目的实现范围。NekoJS 本体负责提供稳定、可枚举的 catalog metadata、workspace layout 和 snippet 数据，让 NekoProbe 或其他外部工具可以生成 TypeScript artifacts，而不是扫描运行时 scope、mixin hook 或依赖内部实现。

### NekoJS 侧元数据契约

这些能力属于 NekoJS core / common 契约，应保持稳定并继续补齐富元数据。

- [x] 建立统一 catalog snapshot API：`NekoScriptCatalog.snapshot()` / `snapshot(side)`，聚合 bindings、events、adapters、recipe namespaces、host extensions、snippets、output layout。
- [x] 建立中立 catalog DTO：`BindingCatalogEntry`、`EventCatalogEntry`、`AdapterCatalogEntry`、`RecipeNamespaceCatalogEntry`、`HostExtensionCatalogEntry`、`SnippetCatalogEntry`、`TypeOutputLayout`。
- [x] 建立平台 catalog provider：平台显式贡献 recipe namespace、host extension manifest 和 output layout。
- [x] 让 recipe namespace 注册可被枚举；`NekoRecipeNamespaces` 提供有序 handler class view。
- [x] 建立 mixin extension manifest 基础：平台 provider 显式列出 target class / extension interface，catalog 复用 `MemberVisibilityQuery` 得到 JS 暴露名。
- [x] 解耦 `WorkspaceGenerator` 对 `.probe/{env}/probe-types` 的硬编码，改为读取 `NekoScriptCatalog.outputLayout()`。
- [x] 将默认类型输出根从 `.probe/` 改为 `.neko_probe/`，避免与 ProbeJS 冲突。
- [x] 增加 `registerTypeDocs` / catalog contribution 轻量插件钩子，让 NekoJS 插件能显式贡献富声明数据；旧 `api.probe` facade、`Probe*Doc` DTO、`NekoProbeMetadataProvider` 和 `NekoContextSnapshot` 已删除，统一改用 `api.catalog.NekoScriptCatalog`。
- [x] 为 `BindingCatalogEntry` 补声明元数据：是否 host class 的人工修正、人工类型覆盖、文档、示例。
- [x] 为 `EventCatalogEntry` 提供基础 snippet 模板。
- [x] 为 `AdapterCatalogEntry` 提供常见 adapter input shape、错误策略与示例。
- [x] 为 `RecipeNamespaceCatalogEntry` 提供 fallbackSupported、handler recipe type 方法名与初始 examples。
- [x] 为 wrapper/helper 提供人工声明补充，例如 `IngredientFactory`、`RecipeJsonBuilder`、`RecipeJsonValue`、recipe filter、fluid/item helper、`PersistentDataJS`、EntityType/Goal 注册 API 的链式返回和 union 输入类型；内置 manual declaration 注册已收敛到 common 的 `NekoCommonManualDeclarations`。
- [x] 增加可选 Java class-load telemetry / `Java.loadClass` 等价 hook，供外部类型工具收集用户脚本实际加载过的类；通过用户脚本层 `Java.type` / `Java.loadClass` wrapper 记录成功加载，并由 `ClassFilter` 记录 lookup attempt，不通过 mixin 拦截内部 wrapper。
- [x] `TypeOutputLayout` 默认类型输出根使用 `.neko_probe/`；workspace 生成器只读取 output layout，不硬编码具体目录。

### 本项目不负责的类型生成实现

以下内容属于 NekoProbe 或其他外部类型工具，不作为 NekoJS 本体路线图任务：

- 类型生成插件 API、class discovery、alias、special docs、side docs、snippet 注入。
- metadata graph / IR 到 `.d.ts`、snippet 和 workspace config 的完整 emitter。
- Java class registry、递归字段/方法/构造器/泛型发现和 JAR 扫描策略。
- Java package declarations、全局绑定、side-specific declarations、special aliases 和 index 文件生成。

NekoJS 本体只需保证 `NekoScriptCatalog`、manual declarations、snippets、`JavaModuleImportPolicy`、workspace layout 等输入稳定准确。

### 编辑器体验

- [x] 从 `SnippetCatalogEntry` 生成 VSCode snippets；当前 catalog 已提供 server started、recipe event、afterRecipes、recipe namespace introspection、fallback namespace、shapeless recipe、recipe builder 的初始片段，VSCode snippets JSON 序列化已收敛到 common 的 `NekoSnippetJson`。
- [x] 为工作区生成 per-script-dir `jsconfig.json`，让 `startup_scripts`、`server_scripts`、`client_scripts`、`test_scripts` 获得对应 side 类型。
- [ ] 保持 workspace layout、side-aware `jsconfig.json` / `tsconfig.json` 与 catalog 输出一致，供外部类型工具接入。
- [ ] 将未来 live editor bridge 作为外部增强，不作为 NekoJS 本体核心依赖。

### 明确不照搬 ProbeJS 的部分

- [ ] 不依赖 KubeJS plugin、KubeJSPaths、Rhino wrapper 或 `Java.loadClass` mixin hook。
- [ ] 不为了类型生成迁移 KubeJS 完整 recipe schema / component 系统。
- [ ] 不把 VSCode 扩展连接作为第一阶段必需能力，先保证纯 `.d.ts` 可用。
- [x] 不把脚本 API 改回大量 `Object` / `Value` 入口；`RecipeJsonBuilder` / `RecipeEventJS.custom` 已使用 `RecipeJsonValue` 中间类型收敛任意 JS JSON 输入。

## 下一阶段脚本能力规划

### PData typed API 与自动同步

- [x] 提供通用 `PersistentDataJS` 链式 wrapper，封装 `CompoundTag` 的 typed get/put/remove/merge/copy/replace API。
- [x] 为实体/玩家暴露 `pdata()`，脚本侧直接得到 `PersistentDataJS`，不需要直接操作裸 `CompoundTag`；底层使用实体原生 `getPersistentData()` 的 `NekoJSPersistentData` 子 tag。
- [x] 为 `PersistentDataJS` 补 server authoritative 自动同步 MVP：dirty tracking、`PDataSyncPacket`、服务端 tick 批量 full-tag sync、客户端只读 mirror。
- [x] 将 pdata 同步目标从全体在线玩家优化为 tracking/self，并增加 revision、每 tick 同步数量上限和 tag size 上限，避免旧包覆盖和基础网络压力。
- [x] 为 26.1 增加 runnable server/client smoke 测试，覆盖 player pdata 服务端写入、tick dirty、立即 sync、客户端 mirror 读取和客户端只读拒绝写入。
- [x] 增加 `test_scripts/` 脚本类型，用于显式运行 smoke/regression 脚本；默认不参与 startup/server/client 自动加载，第一版作为 server-like 测试环境，可复用 server binding/event，事件监听和 timer 生命周期按 TEST 自身隔离，并提供 TEST-only `Test` 断言 helper。
- [ ] 为 1.21.1 增加 runnable / client-server smoke 测试，覆盖实体/player pdata 持久化和同步。

### Node-compatible API 与 VFS

目标是在不引入完整 Node runtime 的前提下，为脚本提供尽量贴近 Node.js 的基础内置模块：`require('fs')`、`require('path')`、`require('util')`、`require('timers')`、`require('buffer')`、`require('process')`、`require('events')`，并支持 `node:` 前缀别名。所有真实文件访问必须通过 NekoJS VFS，最多只能访问 game root / `.minecraft` 目录内的内容。

- [x] 增加 26.1 runnable 探测脚本 `server_scripts/src/test_node_api_probe.js`，先记录当前 `require('fs')`、`path`、`util`、`timers`、`buffer`、`process`、`events` 可用性和 VFS 越界行为，不让缺失模块中断 reload。
- [x] 加强 VFS 路径校验：统一 `resolveGamePath` / `resolveNekoWritePath`，对相对路径、绝对路径、符号链接、创建新文件时的父目录 real path 做一致校验。
- [x] 明确默认访问策略：读路径限制在 `.minecraft` 内；写/删除默认限制在 `.minecraft/nekojs`；用户可在 `nekojs/config/engine.toml` 设置 `allowFsWriteOutsideNekojs = true` 允许写/删整个 `.minecraft`，但仍禁止越过 game root。
- [x] 收紧 `NekoJSFileSystem` 的危险入口：默认禁用 `createSymbolicLink`，避免脚本通过 symlink 创建外部访问通道。
- [x] 在 CommonJS `require` 外层安装 core module shim：保留现有相对路径/`node_modules` 解析，只拦截 `fs`、`node:fs` 等内置模块名。
- [x] 增加 issue #23 Java module import 解析：`require('java:java/lang')` / `import { $Integer } from 'java:java/lang'` 通过懒加载 namespace proxy 按 `$Class` 解析 Java 类型；`java:java/lang/Integer` class-level module 会直接返回 Java class 并暴露 `$Integer`/default。`JavaModuleImportPolicy` 允许类型生成器在 package module 与 class module 之间切换。
- [x] 增加轻量 data-driven recipe type definition：数据包可通过 `data/<namespace>/nekojs/recipe_types/<type>.json` 声明 `event.recipes.<namespace>.<type>(...)` 的 constructors、fields 和 JSON path 映射；静态 Java handler 仍优先，未知 namespace/type 继续 raw JSON fallback。
- [x] 将 Node shim JS 从 Java text block 拆到 classpath resources：`common/src/main/resources/nekojs/node/modules.list` 按顺序加载 `internal/define.js`、各 builtin module 和 `bootstrap.js`，避免依赖 jar 内目录扫描。
- [x] 实现 `fs` 同步基础 API：`existsSync`、`readFileSync`、`writeFileSync`、`appendFileSync`、`mkdirSync`、`rmSync`、`unlinkSync`、`readdirSync`、`statSync`、`lstatSync`、`renameSync`、`copyFileSync`、`realpathSync`、`readlinkSync`。
- [x] 实现 `fs` callback API：`readFile`、`writeFile`、`appendFile`、`mkdir`、`rm`、`unlink`、`readdir`、`stat`、`lstat`、`rename`、`copyFile`、`realpath`，错误优先 callback 行为尽量贴近 Node。
- [x] 实现 `fs/promises`：`readFile`、`writeFile`、`appendFile`、`mkdir`、`rm`、`unlink`、`readdir`、`stat`、`lstat`、`rename`、`copyFile`、`realpath`。
- [x] 实现 `path`：`join`、`resolve`、`normalize`、`dirname`、`basename`、`extname`、`relative`、`isAbsolute`、`parse`、`format`、`sep`、`delimiter`、`posix`、`win32`；该模块只做字符串处理，不做权限判断。
- [x] 实现轻量 `Buffer` / `node:buffer`：`Buffer.from`、`Buffer.alloc`、安全零填充的 `Buffer.allocUnsafe`、`Buffer.isBuffer`、`Buffer.isEncoding`、`byteLength`、`concat`、`toString`、`length`、基础下标访问；保证 `fs.readFileSync(path)` 未传 encoding 时返回 Buffer-like 对象。
- [x] 实现轻量 `process` / `node:process`：`cwd`、受 VFS 限制的 `chdir`、`platform`、`versions`、只读 `env`、`nextTick`、`argv`、`exitCode`、`pid`、`uptime()`、`hrtime()`。
- [x] 实现 `timers` / `node:timers` 与 `timers/promises`：`setTimeout`、`clearTimeout`、`setInterval`、`clearInterval`、`setImmediate`、`clearImmediate`，以及 promise 版 `setTimeout`、`setImmediate`、`setInterval` async iterator；type reload/Context close 时取消旧任务，单文件 reload 会取消目标入口脚本直接注册的旧 timer，避免脚本重载后定时器泄漏。
- [x] timer 回调按脚本 side 安全 flush：`server_scripts` 在 `ServerTickEvent.Post` 执行，`client_scripts` 在 `ClientTickEvent.Post` 执行；`startup_scripts` 只允许 immediate/0ms timer 并在 startup load 结束后 flush 一次。
- [x] 实现 `util`：`format`、`inspect`、`promisify`、`callbackify`、`deprecate`、`inherits`、`isDeepStrictEqual`、`debuglog`、`inspect.custom` 和 `types` 中常用判断函数。
- [x] 实现 `events`：轻量 `EventEmitter`、`once`、`on`、`prependListener`、`prependOnceListener`、`listenerCount`、`eventNames`、`rawListeners`、`setMaxListeners`、`getMaxListeners` 和未监听 `error` 事件抛出行为，满足常见 npm 小模块依赖。
- [x] 实现轻量 `assert` / `node:assert` 与 `test` / `node:test` shim；`assert` 已支持 `match` / `doesNotMatch`，`node:test` 仅在 `test_scripts` 可用，当前支持基础 runner、`describe` / `it`、`before` / `after` / `beforeEach` / `afterEach`、子测试、skip/todo 与 Promise 测试，复用 TEST-only `Test` helper 输出 section/pass，适合 smoke/regression 脚本。
- [x] 为 Node-compatible API 补 catalog manual declarations，让外部类型工具可为 `require('fs')` / `require('node:fs')` 等生成轻量 shim 对应的类型。
- [x] 为 26.1 添加 Node/VFS runnable smoke test，覆盖 `nekojs/` 内读写、默认写越界拒绝、父路径越界拒绝、Buffer 返回和 TEST timer promise/callback 基础行为。
- [x] 补 Node/VFS 剩余专项：26.1 runnable 增加 symlink 逃逸夹具（宿主允许创建 symlink 时断言 VFS 拒绝逃逸读取；不允许时环境门控跳过），并同步 1.21.1 run-dir smoke fixture。
- [x] Node API 完善：新增 `os` 模块（arch/platform/cpus/freemem/totalmem/homedir/hostname/tmpdir/uptime/userInfo/networkInterfaces/endianness/loadavg/release/type/version/EOL/constants/devNull）；Buffer 补全 slice/subarray/fill/indexOf/includes/copy/equals/compare 及 14 个多字节读写方法（readUInt8~writeDoubleBE）；Stats 补全 isBlockDevice/isFIFO/isSocket/dev/ino/mode/nlink/uid/gid/rdev/blksize/blocks；process 补全 memoryUsage/cpuUsage/hrtime.bigint/env 真实环境变量；path.posix/win32 补全 format/parse/resolve/relative/isAbsolute；fs.accessSync 支持 mode 参数（F_OK/R_OK/W_OK/X_OK）。

### 原生 ESM runtime 与后续 CJS runtime

目标是实现 NekoJS 自有的 native ESM module runtime，而不是把 ESM 降级转换成 `require` / CommonJS。ESM 应由 Java 侧 lexer/parser/AST/IR 构建模块记录，执行层按 ESM 的 parse / instantiate(link) / evaluate 思路处理 import/export binding、live binding、循环依赖、`import.meta` 和 dynamic import。JS glue 只用于 Graal 执行边界必要的最小包装，不作为 Babel/SWC 这类模块转换器，也不把 CJS 当作 ESM 的最终执行模型。

NekoJS 的 ESM 仍然不是传统 npm package-main/import-graph 脚本发现模型：普通脚本入口继续由 NekoJS 从 `startup_scripts/`、`server_scripts/`、`client_scripts/`、`test_scripts/` 独立发现并加载；`import` 主要用于导入特定值、helper、JSON、Node/`node:` 模块、Node ESM-style module 或 `java:` Java 模块。入口发现不依赖 `package.json main`，也不依赖某个 root import graph 才决定哪些脚本会运行。CJS 后续也要实现，但应作为 NekoJS 自有兼容模块格式，与 native ESM 互操作，而不是让 ESM 永久转成 CJS。

实现路线更新为：Babel / Babel bundle / transformer 构建工具 / npm 依赖保持移除；现有 Java ESM→CJS transformer 只能视为临时原型和 parser 探路，不作为最终方向继续扩展。后续应把 `NekoEsmParser` 继续推进成稳定 AST/IR 前端，并替换执行层为 native ESM module graph/runtime。

当前 ESM 已可覆盖常用 authoring 路径：native Graal ESM evaluation 已承担实际执行，Java 侧 module record/linker/rewriter 负责 resolver、diagnostics、`import.meta`、dynamic import、JSON/special/CJS synthetic module、TLA async evaluation 和 CJS require ESM namespace capture。parser 前端已从 regex import/export 拆分推进为 NekoJS 自有 token/IR parser，能够稳定提供 statement/specifier/binding span，并已记录常见函数参数、catch binding、class static block var 作用域、对象 method body、block arrow function body、private class method、computed class method 和 class field initializer 内 method/arrow 的函数作用域边界。`NekoEsmModuleAst.program()` 现在额外暴露源码感知 `NekoJsProgram`，包含 runtime/default-export expressions、block body、function-like 和 class body/element AST。当前 TS/JSX/TSX sourcemap chain 已接入 prepared module cache 和 `node:test` mapped stack。后续优先级应保持明确：补齐 decorator/complex class element 精度、更深 expression tree 与 diagnostics；随后推进真正模块实例级热替换、Java AST/IR-backed CJS runtime 和完整自有 evaluator。这样先解决用户混用 `import` / `require` 的高频问题，避免过早投入复杂 evaluator。

- [x] 重构脚本编译接口：新增 `IScriptCompiler.compileDetailed(...) -> ScriptCompileResult(code, sourceMap)`，让语言插件/legacy 编译器能返回 sourcemap；旧 `compile(...) -> String` 暂作为兼容默认入口保留。内置 `.ts` erasure 和 `.jsx/.tsx` classic lowering 已产出标准 v3 sourcemap，其中 `.tsx` 先生成 JSX 到原始 TSX 的直接映射，再复用 whitespace-preserving TS erasure。
- [x] 新增 shared prepared module cache：`NekoModulePreparationCache` 作为 loader/linker/rewriter/VFS 的统一准备入口，按 canonical path 的 mtime/size 缓存 compiled source、source map、AST 和 mode，并集中注册 source map；后续本体 TS/JSX/TSX 编译器应接入这一层。
- [x] 建立 bootstrapped plugin registration snapshot：`NekoPluginRuntime` 在插件加载后一次性收集 script language/compiler、script property、bindings、client bindings、JS type adapters、events、client events、type docs/manual declarations、recipe namespaces 和 recipe lifecycle hooks；插件 API 保留多入口 typed hooks（`registerScriptCompilers`、`registerScriptProperty`、`registerBindings`、`registerClientBindings`、`registerAdapters`、`registerEvents`、`registerClientEvents`、`registerTypeDocs`、`registerRecipeNamespaces`、`registerRecipeLifecycleHooks`），bootstrap 由 extension point descriptor 列表驱动，每个 descriptor 负责一个 hook 的插件类型、client gating 和收集动作。内置 descriptor 覆盖 core hooks，外部插件可通过 `NekoPluginExtensionProvider` 注册新的 typed plugin interface descriptor（例如 startup-only binding hook），最终统一写入 `NekoPluginRuntime`，读路径不再各自 lazy 遍历 plugin manager。`ScriptCompilerRegistry`、`ScriptPropertyRegistry` 和 extension context 暴露的 bootstrap registry 都会在 bootstrap 后 freeze，插件保存旧 registry 后延迟注册会 fail-fast。`NekoJSCorePlugin` 通过 `registerScriptCompilers` 注册内置 `.ts` erasable TypeScript 前端，外部插件可后注册覆盖 `.ts` 或补 `.tsx/.jsx`。script discovery、resolver extension probing、VFS `.js` fallback 和 module pipeline 都从当前 runtime registry 读取。
- [x] 建立语言插件流水线 Phase 1 壳：新增 `NekoLanguagePlugin -> NekoLexer -> NekoParser -> NekoSourceAst -> NekoAstLowering -> NekoUnifiedIR -> NekoJSBackend` 的最小 API，`ScriptCompilerRegistry` 同时支持新 language plugin 与旧 `IScriptCompiler` legacy adapter，`NekoModulePipeline` 已先通过 `NekoCompilationPipeline` 产出 IR/backend 输出再转换回现有 `NekoPreparedModule`，保持 `.js/.mjs/.cjs/.ts` 当前运行语义不变；JS、TS 和 legacy 编译器输出现在统一收敛到 `NekoEsmSourceAst`，并复用 `NekoEsmToUnifiedIrLowering` 生成 unified IR；公共 IR record 不再暴露 `Object native*` 底层 AST 节点，内部 ESM module AST 由 pipeline 私有处理。
- [x] 继续重构 `NekoModulePipeline` 第一阶段：新增 pipeline/cache facade 接管 prepared module 与 sourcemap 生命周期，`NekoModuleReadService` 接管 transformed/virtual module 读取和 `.js` fallback，ESM module record 构建与 link metadata 经 context-scoped cache/facade 委托。
- [x] 增加 revision-aware targeted invalidation：模块 targeted reload 会 bump module revision，同步失效 ESM record/link cache 与 native virtual URI generation，并在 affected-module invalidation 中保留 dependency graph 拓扑；entry subtree rerun 仍会按需要清理 graph 节点。26.1 runnable regression 已覆盖 CJS require cache、ESM namespace/link metadata、dynamic import virtual URI 和失败后恢复。
- [x] 继续推进真正模块实例级热替换：新增 `ModuleVersion`（sourceStamp + generation 追踪）、`ModuleSliceRelinker`（依赖切片拓扑排序、快照回滚、逐模块重链）。`NekoScriptModuleLoaderHost.hotReloadModule()` 支持非入口依赖变更时仅重链受影响模块而无需重跑入口。`NekoJSScriptManager.reloadScriptFile()` 集成了 hot-reload 优先策略，失败时自动退回到入口重跑。JS bridge 已暴露 `hotReloadModule`。
- [x] 实现 NekoJS 自有脚本 loader 第一版实验路径：由 `NekoScriptModuleLoaderHost` + `internal/script-loader.js` 执行入口和相对模块，支持稳定局部 `require`、相对模块、模块缓存、JSON 模块、目录 `index`、扩展候选解析、Node builtin / `node:` alias 和 `java:` Java 特殊模块。文件级 `/nekojs reload <type> <file>` 已接入依赖图失效：helper reload 会沿反向父边找受影响入口，entry reload 会先清理该入口已知静态依赖子树；失效会同步清理 CJS cache、ESM module record、prepared module cache、dependency graph 和 native ESM virtual source generation。
- [x] 将脚本执行入口切到统一 NekoJS loader：`NekoJSScriptManager` 不再按旧实验 flags 或 Graal CommonJS 三路径分流，统一由 `NekoScriptModuleLoaderHost` 进入自有 resolver/runtime。
- [x] 移除旧 native GraalJS ESM 实验分流和 `.js -> .mjs` alias 方向；后续稳定路线不是路径伪装或 regex import rewrite，而是 NekoJS 自有 AST/IR-backed native ESM runtime。
- [x] 移除 per-module `require-patch` prepend：Node builtins、`node:` alias、`java:` Java 模块和相对模块解析由自有 loader/runtime 处理，避免污染用户模块行号。
- [x] 增加可配置脚本错误日志格式：`engine.toml` 默认 `conciseScriptErrorLogs = true`，script/test/event 错误优先输出原因、用户脚本路径、行列号和代码片段；设为 `false` 时输出完整 verbose diagnostics/stack，便于调试分析。
- [x] 重做 `SourceMapRegistry`：删除 regex 解析方案，改为 Gson 解析的 v3 sourcemap 模型，支持 `sources`、`sourcesContent`、`names`、`sourceRoot`、prepended line offset 和最终执行路径到原始源码位置映射；错误日志、mapped stack、`node:test` stack line 和 prepared module cache invalidation 已接入 mapped path/source content。
- [x] 建立当前 TS/TSX/JSX sourcemap chain：`TS` erasure 生成 identity map，`JSX/TSX` classic lowering 产出到原始源码的 v3 map，`.tsx` 在 whitespace-preserving TS erasure 后保留 JSX->TSX map，最终由 prepared module cache 注册执行代码到原始源码的映射。
- [x] 完全移除 Babel 路线：已删除 Babel transformer Java wrapper、generated bundle、transformer build tools 和仅服务于 Babel transformer 的 npm package 文件；运行时不保留 Babel fallback。
- [x] 建立 NekoJS 自有 Java AST/IR parser 初版：`.mjs` 强制 ESM，`.cjs` 强制 CJS，`.js/.ts/.tsx/.jsx` 通过 Java lexer/parser 检测真实 top-level import/export，避免 regex/contains 误判。
- [x] 将当前 Java ESM→CJS transformer 降级并停止作为目标扩展：默认 ESM 路径已改为 native ESM module record / linker / evaluator，运行时不再把 ESM 降级到 `require` / CommonJS。
- [x] 设计 ESM AST/IR 初版：显式表达 import declaration、export declaration、re-export、runtime expressions、dependency table、source spans 和初始 diagnostics，不用零散字符串替换作为核心语义；当前 module syntax 前端已改为 NekoJS 自有 token/IR parser，不再用 regex 拆 import/export，parser 会为静态 specifier literal 提供准确 span，native ESM source rewriter 直接使用该 span；parser 已记录 import/export、顶层声明、嵌套函数/块作用域声明、常见函数参数、catch binding、class static block var 作用域、对象 method body、block arrow function body、private class method、computed class method、class field initializer 内 method/arrow 和常见解构声明形成的本地 binding table，解构 parser 会跳过默认值表达式并识别 rest binding 与 computed property binding；linker 能诊断 `export { missing }`、同一 lexical scope 内 duplicate local binding、重复 explicit export 和 ambiguous star export；诊断 span 会优先指向具体 binding/export 名称。通用 `module.jsast` 已从骨架推进到源码感知 `NekoJsProgram`，可暴露 import/export statement、binding/scope、runtime/default-export expression、block body、function-like raw/name/kind/parameters/body、class body/element 和 field initializer AST；后续继续补 decorator/complex class element 精度与更深 expression tree。
- [x] 实现 native ESM module record 初版：记录 module id/path、prepared source/AST、link metadata、status、namespace、failure、evaluation future 和同步/异步 evaluation 状态位；入口加载已接入 async evaluation 等待和 timer flush，后续继续补 dfs index 与完整 binding 表。
- [x] 实现 ESM linker 初版：按 NekoJS resolver 解析静态依赖，生成 dependency/local export/indirect export/star export metadata，并对可静态确认的 ESM/JSON dependency 缺失导出提前给出文件行列 diagnostic；export shape 已提升为 `NekoEsmExportShape` typed model，会区分 explicit/local、indirect re-export、star export、ambiguous star export，并保持 `export *` 不转发 default；module graph 只用于依赖链接，不用于决定脚本目录入口发现。
- [x] 实现 ESM live binding 与 cycle 语义初版：入口和依赖通过 canonical virtual URI 交给 Graal native ESM evaluation，测试覆盖 live binding 与 ESM↔ESM cycle；后续 Java module record 仍需补完整 binding 表和冲突诊断。
- [x] 实现 ESM evaluator 初版：Java module record 管理 resolver/link/cache/diagnostics，Graal 以 `application/javascript+module` 执行模块并提供 native ESM namespace/live binding 语义；top-level await 已走 Graal native async evaluation，Java 侧通过 async-safe namespace capture 完成 entry/dynamic import future。
- [x] 支持 native `import.meta.url/filename/dirname/resolve` 和 dynamic `import(specifier)` 初版；`import.meta.resolve(specifier)` 现在返回可直接交给 native dynamic import 的最终 module URI：物理 ESM 返回稳定 native virtual URI，JSON/CJS/special module 返回 synthetic module URI。字面量 dynamic import 在 rewrite 阶段解析为 NekoJS resolver URI，非字面量表达式运行时调用 resolver 后仍交给 Graal 原生 `import()` 返回 namespace，并支持动态导入 Node builtin / `node:` / `java:` special module 的 default/namespace synthetic module。物理 ESM source 通过 module-id 稳定 virtual URI 注册，入口和依赖共享同一模块身份，并用 reserve/register 处理 ESM cycle 的递归展开风险；virtual module 同时记录用户可读展示路径，供默认简洁错误日志隐藏 `.native_esm_modules/<hash>.mjs`。loader cache clear 会同步清理 prepared module cache 和 virtual registry，避免 reload 后旧 source/path 残留。后续继续补完整 module cache/link/evaluate 集成。
- [x] 增加 token parser 回归 fixture：覆盖 import/export 文本出现在字符串、模板和正则中不会污染模块语法解析，本地 `export { a as b }` 按源 binding 校验并按别名导出，同一条 `export const a, b` 的所有 binding 都能进入 export table。
- [x] 实现 top-level await 第一阶段：pipeline 不再硬拒绝 TLA，ESM entry/static dependency/dynamic import 会等待 Graal native async module evaluation 完成；CJS `require()` 遇到 TLA/async-descendant ESM 会抛出清晰同步错误，而不是用 CJS Promise 包装模拟。
- [x] 增加 `.mjs` / `.cjs` 脚本扩展支持，并定义 `.js` auto、`.mjs` ESM、`.cjs` CJS 的行为：pipeline/resolver 均按该规则分类，script language registry 也内置这些扩展。
- [ ] 实现 CJS runtime 第二阶段：Java AST/IR-backed CJS parser/loader，支持 `require`、`module.exports`、`exports`、`__filename`、`__dirname`、JSON、Node builtin、`java:`，并与 native ESM 建立互操作规则。
- [x] 定义 ESM/CJS interop 第一阶段：ESM import CJS 的 default/namespace/named 读取 `module.exports`，CJS require 已支持的同步 ESM 通过 native namespace capture module 返回 namespace；测试覆盖 default/namespace/named import CJS、CJS require ESM 和 require TLA ESM 的同步拒绝。后续仍需补 CJS↔ESM cycles 和更完整 CJS runtime。
- [x] 增加命令级依赖索引 reload：`/nekojs reload <server|client|startup|test> [file]` 支持按 type 整体重载、重跑指定入口，或在指定 helper module 时通过 runtime dependency graph 反向找到已加载的受影响入口并重跑；目标入口会清理自身 event listener、timer、错误记录，并按受影响模块切片失效 CJS cache、ESM module record、prepared cache 和 virtual ESM source。virtual ESM URI 现在带 module generation，reload 后会生成新 URI，避免 Graal 继续复用旧 native ESM module instance，同时避免每次 helper reload 都清空整个 runtime module cache。
- [ ] 增加真正模块实例级热替换：以 real path、lastModified、size、可选内容 hash、compiler/parser/runtime version、module format 为 key，拆分 source/compile/AST/link/evaluate 缓存；维护 generation/module instance，helper module 变更时只 invalid/relink/re-evaluate 受影响图切片而不是重跑入口，并隔离失败 reload。
- [x] 增加 native ESM invalid diagnostics 自动断言：`/nekojs test` 通过 CJS helper 显式加载 disabled invalid fixtures，覆盖 duplicate export、import/local duplicate binding、same-scope duplicate binding、重复参数/catch binding、ambiguous star export、missing dependency named import、missing re-export、re-export alias 诊断名和 `export *` 不转发 default；TLA 已迁移为正向 runtime fixture，并补 CJS require TLA ESM 的清晰同步错误断言。
- [x] 增加 native ESM runnable 测试：builtin import、relative import、default/named export、namespace import、side-effect import、re-export、`export *`、cycles、dynamic import、`import.meta`、JSON import、Node builtin / `node:` import、`java:` named import、TLA static/dynamic evaluation、ESM/CJS interop、TS/TSX sourcemap 报错映射。
- [x] 把 parser edge case 从 fixture 扩成 invalid/diagnostic 覆盖：新增 computed `import.meta` 边界、re-export alias diagnostic、decorator/class 边界和重复参数/catch binding 诊断；后续仍可继续补更深 decorator/complex class element AST 精度。
- [ ] 为 native ESM/CJS 更新 catalog manual declarations 和 workspace 配置输入，让外部类型工具可按现代 JS/TS 模块语法提示。
- [x] 实现 NekoJS 内置纯 Java `.ts` erasable TypeScript 第一阶段：类型标注、`type` / `interface`、`import type` / `export type` 等会在语言前端擦除，保留 ESM/CJS runtime 语义，unsupported enum/namespace 等语法给出清晰错误；`.ts` 现已注册为 `NekoTypeScriptLanguagePlugin`，通过 `NekoTypeScriptLexer` / `NekoTypeScriptParser` 产出 `NekoEsmSourceAst`，再复用 `NekoEsmToUnifiedIrLowering` 输出 JS-compatible `NekoUnifiedIR`，旧 `NekoTypeScriptCompiler` 仅保留为 legacy adapter 兼容。`.jsx/.tsx` 现已注册为 `NekoJsxLanguagePlugin`，提供轻量 classic runtime lowering，并复用同一 ESM parser/lowering；TS/JSX/TSX sourcemap chain 已接入运行时 mapped stack。后续继续在本体补 decorator/parameter property、自动 JSX runtime、更完整 TSX、高级 TS diagnostics 和复杂 JSX/TSX sourcemap 精度。

### Painter API 与 client render events

- [ ] 增加 client-only `PainterJS` 链式 API；1.21.1 包装 `GuiGraphics`，26.1 包装 `GuiGraphicsExtractor` / render state API。
- [ ] 增加 `ClientEvents.hud` / screen render 事件，事件对象携带 `PainterJS`、尺寸、鼠标等上下文。
- [ ] MVP 绘制能力：`color/resetColor`、`rect/fill`、`outline`、`gradient`、`text/centerText`、`texture`、`item`、`push/pop/translate`、`scissor`。
- [ ] 确认 dedicated server 不加载 painter/client class。

### 原生 EntityType 与 Goal 注册

- [x] 扩展 `RegistryEvents` / `RegistryEventListener`，增加 startup-only `entityType` registry builder。
- [x] 增加 `EntityTypeBuilderJS`，支持 category、size、tracking、update interval、fire immune、no save/no summon、attributes。
- [x] 增加属性注册和客户端 no-op renderer，保证默认脚本实体可被服务端生成且客户端不会因缺 renderer 崩溃。
- [x] 增加 Goal 注册 MVP：`floatInWater`、`panic`、`randomStroll`、`meleeAttack` vanilla goal factory。
- [x] 对已有实体追加 goal 时使用 join-level 注入并做 identity marker 去重。
- [ ] 增加 spawn egg 子阶段。
- [ ] 增加 target/look/avoid 等更多 vanilla goal factory，并评估按 `EntityType` 映射目标 class 的脚本 API。
- [ ] 增加 functional-interface backed script goal，内部捕获脚本异常并限流日志。

### Loot Table API

参考 LootJS 但针对 NekoJS（GraalVM）重新设计。LootJS 基于 Rhino/KubeJS 的 `TypeWrapperRegistry` 和 builder 模式；NekoJS 采用**混合模式**：底层 JSON 访问 + 高层便利 builder，与 recipe 系统的 JSON-first 理念一致。

**LootJS vs NekoJS 设计差异分析**：

| 方面 | LootJS (Rhino) | NekoJS 适配 |
|------|---------------|------------|
| 类型转换 | `TypeWrapperRegistry` 自动转 `ItemStack→LootEntry` | `JSTypeAdapter` 显式适配，避免 Graal 重载歧义 |
| ItemFilter | 自建全套 Predicate builder（hasEnchantment/tag/toolAction 等） | **去掉**：复用 NekoJS 已有的 `IngredientFactory` + `ItemJS` |
| Predicate 包装 | Java builder 包装所有原版 Predicate | **简化**：允许直接传 JSON，由 Codec 反序列化 |
| 战利品表修改 | `MutableLootTable` 完整 builder | **混合**：底层 `getJson/setJson` + 高层 `modify(table, consumer)` |
| ID 类型 | `ResourceLocation` | `NekoId`（NekoJS 统一 ID 类型） |
| 条件/函数 | 链式 builder（`addCondition`/`addFunction`） | 保持链式，但支持 raw JSON fallback |

**推荐实现阶段**：

- [ ] **阶段 1 — JSON 访问（MVP for 80% 用例）**: `ServerEvents.LOOT_TABLES` 事件，`event.getJson(id)` / `event.setJson(id, json)` / `event.getIds(filter)` / `event.remove(id)` / `event.create(id, type)`。与 recipe 系统理念一致，脚本可以直接操作 `JsonObject`。
- [ ] **阶段 2 — 便利 builder**: `event.modify(id, table => table.addPool(pool => pool.addEntry(...)))`，LootEntryJS（`of(item)`/`ofTag(tag)`/`group(...)`/`when(consumer)`），JSTypeAdapter（`ItemStack`/`Ingredient` → `LootEntryJS`）。
- [ ] **阶段 3 — 全局 modifier 事件**: `ServerEvents.LOOT_MODIFIERS`，`event.remove(id)` / `event.add(id, modifierJson)`。
- [ ] **阶段 4 — Predicate builder（可选）**: 是否包装 vanilla Predicate 取决于用户需求频率，先用 raw JSON 观察。
- [ ] **阶段 5 — 批量操作**: `modifyBlockLoot(filter, consumer)` / `modifyEntityLoot(filter, consumer)`。

**设计原则**：
- NekoJS 不要照搬 LootJS 的 `ItemFilter`（与 `IngredientFactory` 功能重叠，会造成两种写法混淆用户）
- NekoJS 不要照搬 LootJS 的 Predicate builder（那是 Rhino 类型优势的产物，对 Graal 无优势）
- Builder API 保持简洁：只覆盖高频操作（addPool/addEntry/setRolls/removePool），冷门操作走 raw JSON
- 用 `NekoId` 而不是 `ResourceLocation`，保持 NekoJS API 一致

### PowerfulJS-like Capability 集成

- [ ] 设计轻量 `Capabilities` startup binding，不复制 PowerfulJS/KubeJS 完整能力系统。
- [ ] 第一版只支持标准 energy/item/fluid capability，使用 AttachmentType 或平台原生 backing storage 保存状态。
- [ ] 在 `RegisterCapabilitiesEvent` 中为 entity/item/block/block entity 注册 provider；暂不做任意 Java interface capability 生成。
- [ ] 将 pdata 作为脚本业务数据层，capability 作为 NeoForge 生态访问层，两者可复用 NBT 工具但不共用同一个 tag。

## 长期方向

- [ ] 定义稳定的 NekoJS API 版本和破坏性变更迁移策略。
- [ ] 保持统一元数据可供外部工具生成 `.d.ts`、编辑器类型输出和 API 文档。
- [ ] 逐步收紧 HostAccess，区分可信本地开发模式与更安全的服务器运行模式。
- [ ] 增加性能诊断：reload 耗时、脚本执行耗时、慢事件监听器和热点统计。
- [ ] 维护 NekoJS、GraalJS、Minecraft、Java、NeoForge 兼容矩阵，并记录外部工具对 catalog 契约的兼容要求。
- [ ] 只有在两个平台都能稳定表达 AnyHolderSet / wildcard ingredient 后，再考虑 `Ingredient.all()` / `Ingredient.not(...)`。

## 架构迁移：显式依赖与生命周期治理

> 最终更新：2026-06-25
> 目标：从静态全局状态迁移到显式对象图、组合根、构造器注入
> 推进文档：[ai_arch/plan.md](../ai_arch/plan.md) | [ai_arch/architecture.md](../ai_arch/architecture.md)

### Phase 0：架构规则冻结

- [x] 建立 architecture allowlist、architecture check (`gradle architectureCheck`) + dashboard
- [x] 阻止 allowlist 外新增 `NekoJS.COMMON`、`current()`、static mutable config 等隐藏依赖
- [x] 迁移仪表盘记录 legacy 调用数量（每个 Phase 降 1 处以上或有注记）

### Phase 1：核心值对象、配置和错误状态实例化

- [x] `SandboxConfig` record + `SandboxConfigLoader`（TOML 读取集中）
- [x] `ClassFilter` 实例化 + `INSTANCE` legacy facade
- [x] `NekoJSPaths` 实例化：`fromGameDir(Path)` factory + 实例 getter + 实例校验方法 + `legacy()` seam
- [x] `ScriptFilePolicy`：解耦 paths 与 compiler registry（已委托 `ScriptFilePolicy.legacyRuntime()`）
- [x] `ErrorTracker` 接口 + `DefaultErrorTracker` 实例 + `NekoErrorTracker` static facade 委托实例
- [x] `NekoJS.COMMON`: 33 → 15 → 4（commands/platform listeners 逐步迁移到 `NekoRuntimeRoot`）

### Phase 2：组合根和生命周期所有权

- [x] `NekoCoreContext` record（paths/engine/sandboxConfig/classFilter/errorTracker，5 字段，无 get/current）
- [x] `ResourceTracker`：AutoCloseable，track/cleanup 逆序、addSuppressed 聚合
- [x] `NekoRuntimeRoot`：composition root，lifecycle API（reload/reloadFile/runTests/errors/close）
- [x] platform bootstrap (`NekoJSMod`) 创建 `NekoCoreContext` → `NekoRuntimeRoot`，script managers 双写
- [x] commands 改用 `NekoJSMod.RUNTIME_ROOT` lifecycle API

### Phase 3：Sandbox、module pipeline 和 module reload owner 实例化

- [x] `NekoSandboxFactory`：注入 `NekoCoreContext`+`ScriptCompilerRegistry`；`NekoSandboxBuilder` 退为 legacy
- [x] `NekoModulePipeline` 实例服务：注入 `NekoCompilationPipeline`+`ScriptCompilerRegistry`+`SandboxConfig`
- [x] `NekoModuleResolver` 注入 `NekoJSPaths`+`ScriptFilePolicy`+`ScriptCompilerRegistry`
- [x] `NekoModulePipelineCache` 接入实例 pipeline（`legacyPrepare` → `legacyInstance().prepare`）
- [x] `ModuleReloadCoordinator`：统一失效顺序（bump revision → CJS/ESM/link cache → dependency graph → virtual registry → prepared cache → source map）

### Phase 4：Script environment 和 ScriptManager 依赖显式化

- [x] `ScriptContextRegistry` + `ScriptContextSeam`：窄 Context 身份注册表（scriptTypeOf/currentScriptIdOf，不返回 manager）
- [x] `ScriptEnvironmentFactory`：接管 Context/Node/bindings/event/telemetry 初始化
- [x] `ScriptExecutor`：current script id + telemetry scope + entry 加载 + TLA 等待 + 错误记录
- [x] `ScriptReloadCoordinator`：统一 targeted reload 顺序（listener cleanup → timer cancel → error clear → preload → rerun）
- [x] `ScriptManager` 删除 `NekoJS parent`，构造器注入具体协作者（6 参数 + `ScriptEnvironmentFactory`）
- [x] `ScriptManager` 实现 `AutoCloseable`（Context/Node/listener closedown 由 `closeRuntimeResources` 统一）
- [x] `NekoJS` 顶层只保留常量（`MODID`/`VERSION`/`LOGGER`）+ `@Deprecated` legacy facade

### Phase 5：错误模型细化和异常边界整理

- [x] `ScriptErrorReporter` 为 bootstrap seam（`NekoErrorTracker` class-load 绑定 → `bindLegacy` 重绑）
- [x] `catch(Throwable)` 审计：18 处均为脚本执行/callback/清理安全边界，无需要改动的业务路径

### Phase 6：LoaderHost 代码组织与 legacy facade 删除

- [x] 6A：LoaderHost interop shell 固定（16 个 `@CalledByDynamicCode` Graal callback 入口已标注）
- [x] 6C：缓存生命周期边界固化（`ModuleReloadCoordinator` 为一等 owner，`NekoModulePreparationCache` dead facade 删除）
- [x] 6D：legacy facade 标记 `@Deprecated`（`NekoJS.COMMON`/`NekoJSPaths`/`NekoErrorTracker`/`NekoSandboxBuilder`/`ClassFilter.INSTANCE`）
- [x] 6E：architecture allowlist baseline 更新（`NekoJS.COMMON` 33→4，`NekoJSPaths.static` 120→138，`NekoErrorTracker.static` 28→30）
- [x] 6B：`EsmModuleLifecycle` / `CjsModuleLoader` / `ModuleHotReloader` 从 LoaderHost 实体提取（EsmModuleLifecycle + CjsModuleLoader 已提取，~200 行下沉，LoaderHost 692→487 行，-30%）

### 验证状态

- [x] 3 平台 `compileJava` 通过（neoforge-1.21.1、26.1、26.2）
- [x] `architectureCheck` 通过（allowlist baseline 已同步）
- [x] 支持平台 neoforge-26.2 已加入 architecture scan roots
- [x] `NekoJS.COMMON` 从初始 33 降至 4（仅剩 bootstrap 赋值 + `NekoPluginBootstrap` 静态引用）
