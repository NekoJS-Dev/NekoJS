# 票据 11 迁移材料：JS/CJS/ESM 模块管线（2026-09-18）

> 范围：语言模块管线 W3（Preparation / Resolution-Cache 显式注入、prepared 不可变语义、
> 阶段错误、trust 同阶段门、legacy 桥表征、示例）。脚本侧无 breaking；Java 侧删除
> process-wide static 管线门面（一次性 breaking，见 §2 对照表）。

## 1. 脚本作者：无须迁移

`require` / `module.exports`（含 `.cjs` 强制 CJS）、`import` / `export`（含 `.mjs` 强制 ESM）、
循环 require 的部分 exports、跨入口共享身份、缺失导出的 link 期报错位置——语义与 11 号票之前一致。
唯一可观察变化是错误归因更精确（准备/解析/link/缓存/执行分阶段携带 owner、文件与模块身份），
旧的 `try/catch` 与错误文本匹配不受影响（阶段错误继承 `IOException`，消息文本保持原样）。

### 1.1 最小可运行示例（交付物正本：`common/src/test/resources/nekojs/module-examples/`）

示例只使用已过 gate 的模块能力；`ModuleExamplesSmokeTest` 逐项执行并断言下述输出。

**JS（CommonJS，`js/hello.js` + `js/greet.js`）**

`greet.js`:

```js
function greet(name) {
    return 'hello, ' + name + '!';
}

module.exports = { greet };
```

`hello.js`（入口）：

```js
// 最小 JS 示例（CommonJS）：入口 require 同目录依赖，导出问候语。
// 运行：把本目录两文件放入 server_scripts/ 任一子目录，入口被加载后 message 即结果。
const { greet } = require('./greet.js');

module.exports = { message: greet('neko') };
```

期望：`message === 'hello, neko!'`。

**CJS（强制模式，`cjs/hello.cjs` + `cjs/greet.cjs`）**

`greet.cjs`:

```js
function add(a, b) {
    return a + b;
}

module.exports = { add };
```

`hello.cjs`（入口）：

```js
// 最小 CJS 示例：强制 CommonJS 模式（.cjs 后缀）；重复 require 返回同一模块身份。
// 缓存行为：同一依赖只求值一次；内容变化后经 invalidate/reload 重新求值。
const { add } = require('./greet.cjs');
const again = require('./greet.cjs');

module.exports = { sum: add(20, 22), identical: again.add === add };
```

期望：`sum === 42`，`identical === true`。

**ESM（强制模式，`esm/hello.mjs` + `esm/greet.mjs`）**

`greet.mjs`:

```js
export const TAG = 'esm';

export default function greet(name) {
    return 'hi, ' + name + '!';
}
```

`hello.mjs`（入口）：

```js
// 最小 ESM 示例：命名导入 + 默认导入 + 命名空间一致性（.mjs 强制 ESM 模式）。
// link 行为：缺失导出在 link 阶段报错（带文件行列），不延迟到执行时。
import greet, { TAG } from './greet.mjs';
import * as ns from './greet.mjs';

export const message = greet('neko');
export const tag = TAG;
export const same = ns.default === greet;
```

期望：`message === 'hi, neko!'`，`tag === 'esm'`，`same === true`。

### 1.2 缓存 / reload 行为（与示例说明一致，已由测试锁定）

- 同内容重复准备命中缓存（稳定 cache key）；内容、路径、mode、language 任一变化即失效，
  同长度覆盖写入不返回旧模块（`NekoModulePipelineCacheStampTest`）。
- 按 `ScriptType` 清理只影响目标类型；`node_modules` 等跨类型共享条目保留
  （`ScriptTypeScopedCacheClearTest`）。
- 单文件改动后经 `invalidateModuleTree` + 重跑入口看到新输出，旧模块不复返
 （`ModuleExamplesSmokeTest#documentedCacheAndReloadBehaviorHolds`）。

## 2. Java 调用点迁移（static → 显式注入）

| 旧（已删除） | 新 |
|---|---|
| `NekoModulePipeline.bindLegacyInstance / legacyInstance / legacyPrepare` | 构造器注入 `new NekoModulePipeline(compilation, registry, config)`；生产唯一实例由 `NekoRuntimeAssembly` 创建并传入 `NekoSandboxFactory` 与 `NekoRuntimeRoot` |
| `NekoModulePipelineCache.prepare / clear / clear(type) / invalidate`（static） | 实例方法；生产共享 `NekoRuntimeRoot#preparationCache()`；旧构造器（manager/host/linker/rewriter/coordinator/filesystem/installer）自建隔离实例 |
| `NekoModuleReadService.readPreparedBytes(path)` / `readTransformedModule(path)` | 同名方法加 `NekoModulePipelineCache` 参数（纯函数，无隐藏状态） |
| `new NekoScriptModuleLoaderHost(ctx[, resolver, paths])` | 行为不变（自建隔离缓存）；生产用四参构造传入共享实例 |
| `NekoPreparedModule(code, sourceMap, mode, ast, cjs, lines)` 位置构造 | 新增 `languageId / sourcePath / cacheKey` 组件；稳定 key 覆盖规范化 source path、语言、mode、code 和实际 source map；管线用显式工厂 `commonJs(lang, path, …)` / `esm(lang, path, …)`，旧三参工厂保留（默认 `language=unknown`） |

`NekoRuntimeRoot#closeSilently` 全清其持有的 prepared 缓存（server stop/切世界/reload 不清空，
按类型清理仍走各 manager 入口）。

## 3. legacy CJS bridge：保留原因与收缩 gate（AC9）

- **当前语义**：以后缀注册的外部 `IScriptCompiler` 经 `compileDetailed` 产出 code/sourceMap，
  再跑 CJS 静态分析进入同一 prepared 形态（`LegacyCjsBridgeCharacterizationTest`）；
  node 内建资源（classpath manifest）直装求值，不经过管线（独立语义：无文件身份/缓存/reload）。
- **source-map 事实**：原生 JS/CJS/ESM 和没有 compiler map 的 legacy compiler 现在由
  `NekoSourceMapBuilder.identity` 生成真实非空 fallback map，包含 authored `sourcePath`、`sources`、
  `sourcesContent` 和 generated/source mappings；原生同源 JS 提供行/列对应，legacy transformed
  输入明确使用 generated-line -> authored-line 的保守映射，超出 authored 行时 clamp 到最后一行。
  TS/JSX 等 compiler-produced map 仍使用 compiler map。script-loader 的 `sourceURL` 仍可作 execution
  fallback，但不能替代 prepared source map。
  `NekoSourceMapBuilder` 的 identity 是唯一明确的 source-map utility API，不是 parser/lowering/compiler
  公共 SPI。
- **保留原因**：外部语言参与路径是公开扩展能力（删即删公开语言）；内建直装语义不同，
  不是第二条用户管线。
- **收缩 gate**（须全部满足；满足后随票删除，不进 final release 统一清理；
  同一公开语义没有第二条长期 pipeline）：
  1. 替代 behavior（同后缀内置插件产物逐字节一致，corpus 对照）；
  2. declaration（managed 声明面覆盖该后缀模块能力）；
  3. trace（全仓无该后缀 `IScriptCompiler` 注册）；
  4. 无调用者（legacy 分支零生产/测试调用）。
- **结构证据现状**：管线与 prepared 缓存零 static 可变状态、无 legacy static 门面
  （同名测试类中断言锁定）；source-map utility 只有 identity 入口，javadoc 明确不是 parser/lexer/
  lowering/compiler SPI（`pipelinePublicSurfaceExposesNoParserSpi` 锁定）。runtime registry 的 public
  mutable observability 已收口为 package owner seam 与 `NekoSourceMapView`/`NekoVirtualModuleView`。

## 4. 未做事项（非本票范围）

- 不删除公开语言、不新增 parser ModuleSPI、不设性能发布阈值（PERF_BASELINE 独立）。
- `SourceMapRegistry` / `NekoEsmVirtualModuleRegistry` 由 prepared-cache 实例持有；生产装配与
  `NekoRuntimeRoot` 共用并在 root close 时释放，按 `ScriptType` 清理只影响该 owner 的条目。
  不同 host 即使使用相同模块 id / virtual URI，也不能读取或清理对方的 source/map 内容。
- `ScriptCompilerRegistry.current()` 语言扩展注册点保留（语言插件机制本身，非 static 管线）。

## 5. Review-round-8 lifecycle and boundary migration

### 5.1 Candidate module sessions

普通 SERVER/CLIENT/TEST reload 仍只由现有 `NekoRuntimeRoot` 持有。root-owned
`NekoModulePipelineCache` 现在为每个 generation 打开 child session：pipeline、trust context 和 path
policy 共享，prepared entries、source maps、virtual ESM sources 与 preparation observations 不共享。
candidate 失败时 child session 与其 Context 一起关闭，active session/error views 保持；成功 commit
切换到 candidate session 后才关闭旧 session。root close 清理 root 下所有 child。脚本作者和插件作者无需
迁移，Java 内部调用者只需遵守 `RuntimeEnvironment` session 的生命周期，不能把已关闭 session 带入下一代。

### 5.2 PackSync reload hook

`PackSyncClient.installClientReloadHook` 的 hook 现在必须返回 `boolean`。NeoForge/Fabric composition
root 只在 `root.reload(ScriptType.CLIENT).success()` 为真时返回 true，并将 reload exception 作为 false
返回。PackSync 在 registry 与 remote credential 激活后若 hook 返回 false，会停用选中的 SERVER_CACHE
集合并 revoke remote sources；若旧 active generation 仍被 runtime 保留，则恢复旧 registry/credential
而不是制造混合状态，并返回 disconnect Outcome；不会把失败 bundle 报成 accepted。common
JUnit 覆盖这一回滚顺序和成功路径；本材料不宣称真实 Minecraft client 或 network session smoke 已运行。

### 5.3 Path case policy and internal visibility

common preparation/resolution/cache 不再通过 `System.getProperty("os.name")` 推断大小写。canonical path
与 source-map lookup 使用注入 `Path`/`FileSystem` 的 equality 行为（`A` 与 `a`），因此 Linux/Windows
语义由实际 provider 决定。`NekoModuleHash` 仅是 `core.module` package-private implementation；
`NekoModuleResolutionPaths` 已删除，resolver 使用 plain `Path` 参数。`NekoSourceMapBuilder.identity`
仍是唯一明确的 public source-map utility。现有 production/test callers 已完成迁移；golden 未更新。

### 5.4 Evidence boundary

Review-round-8 的证据是 `NekoModulePipelineCacheSessionTest`、
`ScriptReloadGenerationTest`、`PackSyncClientTest`、`ModulePipelineIsolationTest`、
`SourceMapRegistryTest`、`NekoModuleResolverTest` 及 common/platform Gradle gates。未执行的真实
Minecraft、loader runtime、client/server network session 不在本轮 evidence 中，不应从这些单测或编译结果推断已通过。
