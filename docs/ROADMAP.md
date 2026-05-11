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
- [x] `.js` 直接执行；`.ts`、`.jsx`、`.tsx` 由 NekoSWC 或注册编译器支持。
- [x] 核心事件组、绑定注册、脚本属性和插件注册机制。
- [x] 工作区生成、游戏内工作区 UI、脚本同步和基础编辑体验。
- [x] `NekoId` 稳定脚本侧 ID 类型，替代 `IDJS.of` 返回 `Object` 的旧设计。
- [x] `JSTypeAdapter.getPrecedence()`，降低宽泛 adapter 参与 Graal overload resolution 时的歧义。
- [x] 26.1 早期 reload 阶段的 `ItemStack` 安全构造，避免 `Components not bound yet`。
- [x] CI Build workflow：编译/构建两个平台模块，区分 dev/release artifacts，并用 commit 首行提取 release version / summary。
- [x] `RecipeJsonValue` 边界类型：recipe builder/custom 的任意 JS JSON 输入收敛到 adapter/converter 层，builder 不直接暴露 Graal `Value` / 宽泛 `Object`。
- [x] 脚本事件错误日志增强：recipe/event 错误同时写 script logger 与主 NekoJS logger，并显示 JS 行列、上下文和列指针。

## 已完成的 KubeJS-lite API 迁移

- [x] `ItemJS.of(...)` 返回 `ItemStack`，支持脚本友好的物品栈创建。
- [x] `ItemStack` mixin extension：`withCount`、`copy`、`getId`、`getMod`、`getBlock`、`enchant`、`hasEnchantment`、`matches`、`asIngredient`、`weakNBT`、`strictNBT` 等。
- [x] `Ingredient` helper：`of`、`item`、`tag`、`any`，并明确暂不支持 `not` 的假语义。
- [x] `IngredientJS` wrapper：`or`、`and`、`intersect`、`except`、`subtract`、`matches`、`first`、`stacks`、`displayStacks`、`withCount`。
- [x] `SizedIngredientJS` 与 `SizedIngredientAdapter`。
- [x] `Fluid` / `FluidIngredient` / `SizedFluidIngredient` 相关 helper、wrapper 和 adapter MVP。
- [x] recipe JSON builder、recipe entry wrapper、filter、递归 `replaceInput` / `replaceOutput`。
- [x] `RecipeJsonValueAdapter` / `RecipeJsonValueConverter`，支持 JS object/array 内嵌 `IngredientJS`、`ItemStack`、fluid wrapper 等 recipe-aware JSON 序列化。
- [x] `event.recipes.minecraft` 常用 vanilla recipe helper。
- [x] `event.forEach(...)` 使用 `Consumer<RecipeEntryJS>`，不把 `Value callback` 作为常规 API。

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
- [ ] `IngredientJS` / `IngredientResolver`：需要隔离 `Ingredient` 展开、holder、component ingredient 的版本差异。
- [ ] 网络 packet record 与 `NekoJSNetwork`：需要先抽象 payload / channel 注册差异。

### 暂不建议迁移的内容

- [ ] registry builder、registry event 和 food builder 等强平台 API 绑定代码。
- [ ] mixin、injected extension API 和需要直接改写 Minecraft 类的代码。
- [ ] GUI screen 和客户端渲染代码，除非先拆出纯数据/工具层。
- [ ] `EventBusForgeBridge` 这类直接依赖平台事件总线的桥接类。

## 短期任务

- [x] 初步收敛 adapter / resolver 边界语义：已通过 26.1 runnable 测试覆盖 null、数组/对象、无效 ID、非正 count、可变 stack copy 等关键路径。
- [x] 为 26.1 优先补 adapter / resolver 回归测试，覆盖 Graal `Value` object/array 输入与错误路径。
- [x] 继续收敛 adapter / resolver 边界语义：已覆盖 null/EMPTY、数组/对象、fluid amount 覆盖、host object copy、无效 shape，并修复 26.1 recipe reload 阶段 tag wrapper 的 registry owner 安全序列化。
- [x] 为 `RecipeJsonValue` / `RecipeJsonValueConverter` 补 26.1 runnable 测试，已覆盖 nested JS object/array、`IngredientJS` tag wrapper、`SizedIngredientJS`、`ItemStack`、fluid wrapper、fallback namespace，并提供默认禁用的 invalid-shape 手动测试。
- [x] 增强 recipe 错误上下文：记录 builder/custom/copy 来源、recipe id、type、创建 API、prefix，并在最终 codec 失败时输出上下文和 JSON。
- [x] 增加 recipe path 操作：`setPath`、`removePath`，支持按 `ingredients.0` / `result.count` 这类 JSON path 修改/删除字段。
- [x] 细化 recipe path 操作：支持自动创建中间 object/array、反斜杠转义点号字段名。
- [x] 继续细化 recipe path 操作：支持批量路径编辑 `setPaths` / `removePaths`，以及点分、数组下标、反斜杠转义点号、括号/引号字段语法。
- [x] 增加 recipe dump/print 调试工具：`event.dump(filter)`、`event.print(filter)`。
- [x] 为 `event.recipes.minecraft` 增加 datapack type 名 raw JSON alias：`crafting_shaped(json)`、`crafting_shapeless(json)`。
- [x] 保持 README、`docs/ROADMAP.md`、`ai_docs/` 与当前实现同步；本轮短期任务已同步整理相关文档。

## 中期任务

- [ ] 按“低版本差异、无平台副作用、可编译验证”的顺序推进 common 迁移。
- [ ] 为 Create 等常见 mod 增加轻量 JSON helper，例如 mixing、crushing、pressing，但不引入 schema 系统。
- [ ] 扩展脚本同步安全校验：路径穿越、绝对路径、扩展名、文件数量、批量大小。
- [ ] 添加受控调度器：延迟任务、重复任务、reload 自动取消旧任务。
- [x] 添加 CI，至少构建两个受支持平台模块。
- [ ] 为路径校验、脚本发现、事件总线、adapter 和 recipe filter 增加聚焦测试。

## NekoProbe / ProbeJS-like 类型生成计划

目标是实现类似 ProbeJS 的编辑器类型、补全和片段生成能力，但不绑定 KubeJS / Rhino / `kubejs` 目录结构。NekoJS 应输出纯 TypeScript artifacts，并把 VSCode 配置、snippet、未来实时扩展作为可选增强。

### 先在 NekoJS 侧补齐的前置能力

这些能力应优先在 NekoJS core / common 契约中完成，避免 NekoProbe 以后像 ProbeJS 那样必须扫描运行时 scope、mixin hook 或依赖内部实现。

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
- [x] 为 `RecipeNamespaceCatalogEntry` 提供 fallbackSupported 与初始 examples。
- [x] 为 wrapper/helper 提供人工声明补充，例如 `IngredientJS`、`RecipeJsonBuilder`、`RecipeJsonValue`、recipe filter、fluid/item helper、`PersistentDataJS`、EntityType/Goal 注册 API 的链式返回和 union 输入类型。
- [x] 增加可选 Java class-load telemetry / `Java.loadClass` 等价 hook，供 NekoProbe 收集用户脚本实际加载过的类；通过用户脚本层 `Java.type` / `Java.loadClass` wrapper 记录成功加载，并由 `ClassFilter` 记录 lookup attempt，不通过 mixin 拦截内部 wrapper。

### NekoProbe 类型生成核心

- [ ] 建立 NekoJS 自己的类型生成插件 API，支持优先级、class discovery、alias、special docs、side docs、snippet 注入。
- [ ] 先生成中间 metadata graph / IR，再从 IR 输出 `.d.ts`、snippet 和 workspace config，避免 emitter 直接依赖运行时对象。
- [ ] 建立 Java class registry：从绑定、事件参数、wrapper、adapter target、人工 seed 中发现类，并递归发现字段、方法、构造器、泛型和引用类型。
- [ ] 建立安全的 class discovery 策略：默认 seed-based，JAR 扫描作为可配置高级功能，避免无边界扫描所有 mod 类。
- [ ] 实现 TypeScript emitter，生成 Java package declarations、全局绑定、side-specific declarations、special aliases 和 index 文件。
- [ ] 支持 `startup`、`server`、`client` 三类脚本 side 的独立 `.d.ts` 输出和 `jsconfig.json` / `tsconfig.json` path 配置。
- [x] `TypeOutputLayout` 默认类型输出根使用 `.neko_probe/`；workspace 生成器只读取 output layout，不硬编码具体目录。

### 编辑器体验

- [ ] 从 `SnippetCatalogEntry` 生成 VSCode snippets；当前 catalog 已提供 server started、recipe event、shapeless recipe、recipe builder 的初始片段。
- [ ] 为工作区生成或更新 per-script-dir `jsconfig.json`，让 `startup_scripts`、`server_scripts`、`client_scripts` 获得对应 side 类型。
- [ ] 提供 `/nekojs probe` 或工作区 UI 按钮触发 dump，并显示生成进度与错误摘要。
- [ ] 将未来 live editor bridge 作为独立增强，不作为类型生成核心依赖。

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
- [ ] 为 1.21.1 增加 runnable / client-server smoke 测试，覆盖实体/player pdata 持久化和同步。

### Node-compatible API 与 VFS

目标是在不引入完整 Node runtime 的前提下，为脚本提供尽量贴近 Node.js 的基础内置模块：`require('fs')`、`require('path')`、`require('util')`、`require('timers')`、`require('buffer')`、`require('process')`、`require('events')`，并支持 `node:` 前缀别名。所有真实文件访问必须通过 NekoJS VFS，最多只能访问 game root / `.minecraft` 目录内的内容。

- [x] 增加 26.1 runnable 探测脚本 `server_scripts/src/test_node_api_probe.js`，先记录当前 `require('fs')`、`path`、`util`、`timers`、`buffer`、`process`、`events` 可用性和 VFS 越界行为，不让缺失模块中断 reload。
- [x] 加强 VFS 路径校验：统一 `resolveGamePath` / `resolveNekoWritePath`，对相对路径、绝对路径、符号链接、创建新文件时的父目录 real path 做一致校验。
- [x] 明确默认访问策略：读路径限制在 `.minecraft` 内；写/删除默认限制在 `.minecraft/nekojs`；用户可在 `nekojs/config/engine.toml` 设置 `allowFsWriteOutsideNekojs = true` 允许写/删整个 `.minecraft`，但仍禁止越过 game root。
- [x] 收紧 `NekoJSFileSystem` 的危险入口：默认禁用 `createSymbolicLink`，避免脚本通过 symlink 创建外部访问通道。
- [x] 在 CommonJS `require` 外层安装 core module shim：保留现有相对路径/`node_modules` 解析，只拦截 `fs`、`node:fs` 等内置模块名。
- [x] 将 Node shim JS 从 Java text block 拆到 classpath resources：`common/src/main/resources/nekojs/node/modules.list` 按顺序加载 `internal/define.js`、各 builtin module 和 `bootstrap.js`，避免依赖 jar 内目录扫描。
- [x] 实现 `fs` 同步基础 API：`existsSync`、`readFileSync`、`writeFileSync`、`appendFileSync`、`mkdirSync`、`rmSync`、`unlinkSync`、`readdirSync`、`statSync`、`lstatSync`、`renameSync`、`copyFileSync`、`realpathSync`、`readlinkSync`。
- [x] 实现 `fs` callback API：`readFile`、`writeFile`、`appendFile`、`mkdir`、`rm`、`unlink`、`readdir`、`stat`、`lstat`、`rename`、`copyFile`、`realpath`，错误优先 callback 行为尽量贴近 Node。
- [x] 实现 `fs/promises`：`readFile`、`writeFile`、`appendFile`、`mkdir`、`rm`、`unlink`、`readdir`、`stat`、`lstat`、`rename`、`copyFile`、`realpath`。
- [x] 实现 `path`：`join`、`resolve`、`normalize`、`dirname`、`basename`、`extname`、`relative`、`isAbsolute`、`parse`、`format`、`sep`、`delimiter`、`posix`、`win32`；该模块只做字符串处理，不做权限判断。
- [x] 实现轻量 `Buffer` / `node:buffer`：`Buffer.from`、`Buffer.alloc`、`Buffer.isBuffer`、`byteLength`、`concat`、`toString`、`length`、基础下标访问；保证 `fs.readFileSync(path)` 未传 encoding 时返回 Buffer-like 对象。
- [x] 实现轻量 `process` / `node:process`：`cwd`、受 VFS 限制的 `chdir`、`platform`、`versions`、只读 `env`、`nextTick`。
- [x] 实现 `timers` / `node:timers` 与 `timers/promises`：`setTimeout`、`clearTimeout`、`setInterval`、`clearInterval`、`setImmediate`、`clearImmediate`；reload/Context close 时取消旧任务，避免脚本重载后定时器泄漏。
- [x] timer 回调按脚本 side 安全 flush：`server_scripts` 在 `ServerTickEvent.Post` 执行，`client_scripts` 在 `ClientTickEvent.Post` 执行；`startup_scripts` 只允许 immediate/0ms timer 并在 startup load 结束后 flush 一次。
- [x] 实现 `util`：`format`、`inspect`、`promisify`、`callbackify`、`types` 中常用判断函数。
- [x] 实现 `events`：轻量 `EventEmitter`、`once`、`on`，满足常见 npm 小模块依赖。
- [ ] 为 Node-compatible API 补 NekoProbe manual declarations，让 `require('fs')` / `require('node:fs')` 等返回准确类型。
- [ ] 为两个平台添加 runnable smoke test，覆盖 `.minecraft` 内读写、越界拒绝、symlink 逃逸拒绝、Buffer 返回、timer reload 清理。

### ESM authoring 到 CommonJS runtime 转换

目标是不把 NekoJS runtime 整体切成 native ESM，而是在 NekoJS 本体内提供稳定的 ESM authoring 支持：用户可以写 `import` / `export`，NekoJS 在模块加载管线中转成 Graal CommonJS 可执行的 CJS。NekoSWC 只负责 TS/TSX/JSX 等语言语法转 JS 与 sourcemap，不负责模块系统转换。

- [ ] 重构脚本编译接口：删除旧 `IScriptCompiler.compile(...) -> String` 设计，改为 `ScriptCompileResult(code, sourceMap)`，让 NekoSWC/其他编译器能返回 sourcemap。
- [ ] 新增 `NekoModulePipeline`，作为 VFS 唯一脚本转换入口：语言编译、ESM 检测、ESM→CJS、require patch、sourcemap 注册都收口到 pipeline，`NekoJSFileSystem` 不再直接拼接转换细节。
- [ ] 将当前 VFS 内的 module-local require patch 移到 classpath resource，例如 `nekojs/node/internal/require-patch.js`，由 pipeline 统一 prepend，并对 sourcemap 做明确行偏移。
- [ ] 重做 `SourceMapRegistry`：删除 regex 解析方案，改为 JSON parser + `SourceMapData` 模型，支持 `sources`、`sourcesContent`、`names`、`sourceRoot` 和最终执行路径到原始源码位置映射。
- [ ] 建立 sourcemap chain：`TS/TSX/JSX -> JS` 的输入 map 传给 ESM→CJS transformer，最终注册 `final CJS -> original source` 的 map，避免 require patch 或多段 transform 破坏报错行列。
- [x] 内置本体级 ESM→CJS transformer，不使用 swc4j；当前 resources 内置由 `tools/build-esm-transformer.mjs` 生成的最小 Babel bundle，只打包 ESM→CJS 所需 Babel core / modules-commonjs / dynamic-import 能力，通过独立 `NekoSharedEngine` holder 复用共享 Graal Engine，并按 Graal 共享 Engine 要求共用 `NekoSharedHostAccess`，但 transformer Context 仍禁用 HostClassLookup / IO / 线程 / 进程创建。
- [ ] ESM 检测策略：`.mjs` 强制 ESM，`.cjs` 强制 CJS，`.js/.ts/.tsx/.jsx` 用 parser 的 `sourceType: 'unambiguous'` / AST 检测，避免 regex 误判。
- [ ] 支持常见 ESM 语义：default import、namespace import、named import、side-effect import、named/default export、re-export、`export *`、builtin import（`fs` / `node:fs`）和相对 import。
- [ ] 明确第一版不支持 top-level await；遇到 TLA 输出清晰错误，建议把异步逻辑放到 event callback 或 timer 中。
- [ ] 增加 `.mjs` / `.cjs` 脚本扩展支持，并定义 `.js` auto、`.mjs` ESM、`.cjs` CJS 的行为。
- [ ] 增加转换缓存：以 real path、lastModified、size、compiler/transformer version、transform mode 为 key，避免每次 require 重复转换。
- [ ] 增加 runnable 测试：builtin import、relative import、default/named export、namespace import、side-effect import、re-export、CJS/ESM 互操作、TS sourcemap 报错映射。当前 26.1 已有基础 ESM smoke，覆盖 builtin import、relative import、default/named export、namespace import 和 CJS require transformed ESM。
- [ ] 为 ESM authoring 更新 NekoProbe 类型和 workspace 配置，让编辑器按现代 JS/TS 模块语法提示。

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

### PowerfulJS-like Capability 集成

- [ ] 设计轻量 `Capabilities` startup binding，不复制 PowerfulJS/KubeJS 完整能力系统。
- [ ] 第一版只支持标准 energy/item/fluid capability，使用 AttachmentType 或平台原生 backing storage 保存状态。
- [ ] 在 `RegisterCapabilitiesEvent` 中为 entity/item/block/block entity 注册 provider；暂不做任意 Java interface capability 生成。
- [ ] 将 pdata 作为脚本业务数据层，capability 作为 NeoForge 生态访问层，两者可复用 NBT 工具但不共用同一个 tag。

## 长期方向

- [ ] 定义稳定的 NekoJS API 版本和破坏性变更迁移策略。
- [ ] 从统一元数据生成 `.d.ts`、NekoProbe 类型输出和 API 文档。
- [ ] 逐步收紧 HostAccess，区分可信本地开发模式与更安全的服务器运行模式。
- [ ] 增加性能诊断：reload 耗时、脚本执行耗时、慢事件监听器和热点统计。
- [ ] 维护 NekoJS、GraalJS、NekoSWC、NekoProbe、Minecraft、Java、NeoForge 兼容矩阵。
- [ ] 只有在两个平台都能稳定表达 AnyHolderSet / wildcard ingredient 后，再考虑 `Ingredient.all()` / `Ingredient.not(...)`。
