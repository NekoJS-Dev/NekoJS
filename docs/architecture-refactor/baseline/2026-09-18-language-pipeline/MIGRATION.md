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
| `NekoModulePipelineCache.prepare / clear / clear(type) / invalidate`（static） | 实例方法；生产共享 `NekoRuntimeRoot` 持有的 cache；manager/host/linker/rewriter/coordinator/filesystem/installer 均使用显式注入的 cache，测试使用显式 fixture |
| `NekoModuleReadService.readPreparedBytes(path)` / `readTransformedModule(path)` | 同名方法加 `NekoModulePipelineCache` 参数（纯函数，无隐藏状态） |
| Host construction | 统一使用带 resolver 与显式 cache 的构造器；生产与测试都由调用者提供 cache |
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
  `NekoSourceMapBuilder` 的 identity 与必要的 rewritten-map composition 是明确的 source-map utility
  边界，不是 parser/lowering/compiler 公共 SPI。
- **保留原因**：外部语言参与路径是公开扩展能力（删即删公开语言）；内建直装语义不同，
  不是第二条用户管线。
- **收缩 gate**（须全部满足；满足后随票删除，不进 final release 统一清理；
  同一公开语义没有第二条长期 pipeline）：
  1. 替代 behavior（同后缀内置插件产物逐字节一致，corpus 对照）；
  2. declaration（managed 声明面覆盖该后缀模块能力）；
  3. trace（全仓无该后缀 `IScriptCompiler` 注册）；
  4. 无调用者（legacy 分支零生产/测试调用）。
- **结构证据现状**：管线与 prepared 缓存零 static 可变状态、无 legacy static 门面
  （同名测试类中断言锁定）；source-map utility 只保留 identity 与 rewritten-map composition 入口，javadoc
  明确不是 parser/lexer/lowering/compiler SPI（`pipelinePublicSurfaceExposesNoParserSpi` 锁定）。runtime registry 的 public
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
语义由实际 provider 决定。`NekoModuleHash` 只公开必要的 `sha256` cross-package implementation seam
供 `core.module.esm` registry 复用，`jsonExecutionKey` 等其它实现细节仍保持 package-private；
`NekoModuleResolutionPaths` 已删除，resolver 使用 plain `Path` 参数。source-map utility 只保留
`NekoSourceMapBuilder.identity` 与必要的 rewritten-map composition；现有 production/test callers 已完成
迁移；golden 未更新。

### 5.4 Evidence boundary

Review-round-8 的证据是 `NekoModulePipelineCacheSessionTest`、
`ScriptReloadGenerationTest`、`PackSyncClientTest`、`ModulePipelineIsolationTest`、
`SourceMapRegistryTest`、`NekoModuleResolverTest` 及 common/platform Gradle gates。未执行的真实
Minecraft、loader runtime、client/server network session 不在本轮 evidence 中，不应从这些单测或编译结果推断已通过。

## 6. Review-round-9 final review fixes

本轮没有脚本侧迁移；修复的是 common 运行时边界、候选生命周期和平台适配结果。

- `ScriptBindingSchema.inferType` 现在只按 `ScriptType` 的统一 `<type>_scripts` path helper 推导类型，Preparation、global binding validator 和 event callback validator 不再读取 `ScriptTypeEnv`、`NekoJSPaths` 或 `Platform`。对应测试使用任意路径根，隔离扫描也覆盖该 schema 文件。
- `DefaultErrorTracker` 按 `ScriptType` 保存 active fallback，并按 generation `Context` 保存 session view；`ScriptError` 在创建时捕获来源 context/session 的只读 source-map/virtual-module view。候选 session 不进入 type 级 active 槽，commit 才发布该类型 active view，失败时移除候选 Context view 并恢复 active error snapshot，不会覆盖其它类型或其它 manager。
- CJS `require()` 使用严格的 `resolveChildForRequire`。最高调用者对缺失 bare package 观察到 `NekoModuleError.Stage.RESOLVE`，不会降级为 SPECIAL 或延迟到 EXECUTE。
- `PackSyncClient.handleHashList` 返回 `Outcome`。deactivation/reload 失败时恢复旧 active registry、runtime trust、address、bucket 和 expected hashes；NeoForge/Fabric hash-list handler 消费该 Outcome 并断连。bundle failure 的既有 rollback 语义保持。
- `NekoModulePipelineCache.clear()` 仍是可继续使用的普通清理；新增 root-only `closeOwner()`/`AutoCloseable` 终止 owner，`NekoRuntimeRoot.closeSilently()` 调用它，closed root 拒绝新的 session。
- `identify`、`NekoModulePipeline` 的 preparation overloads、`NekoModuleIdentity` 和 `NekoModulePipelineCache.approvedSource` 收窄为 `core.module` package-private implementation surface；`NekoPreparedModule`、cache/host 的实际 production API 保持不变。

Final review evidence commands:

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.api.event.ScriptBindingSchemaInferTypeTest --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheSessionTest --tests com.tkisor.nekojs.core.error.DefaultErrorTrackerTest --tests com.tkisor.nekojs.core.module.NekoModuleIdentityLifecycleTest --tests com.tkisor.nekojs.core.pack.sync.PackSyncClientTest
./gradlew.bat :common:compileJava :common:compileTestJava
```

本轮还需执行的验收命令为 `:common:check`、`:26.2.0:compileJava`、`:26.2.0-fabric:compileJava` 和 `git diff --check`；本轮未执行真实 Minecraft、loader runtime 或 network session smoke，未更新 golden。

## 6. Review-round-10 runtime boundary updates

### 6.1 Candidate diagnostics and cleanup

脚本作者无需迁移。candidate reload 不再把 candidate module view 放进同一 `ScriptType` 的全局槽位；
active fallback 仍按类型保存，但错误/stack/callback 在有 Graal `Context` 时按 generation session 捕获
source-map 与 virtual-module view。candidate 失败只恢复 active error snapshot，成功 commit 才发布该类型
的新 active view。这样同类型 active callback 与 candidate execution 在重载窗口中不会互读 maps。

`NekoSandboxFactory.build` 与 `NekoNodeModuleInstaller.install` 现在覆盖 Context、Node runtime、module-host
observer 和 logger stream 的 partial construction cleanup。插件 Node module 或 manifest eval 失败仍会
失败，但不会留下 live Context、host observer 或 buffered stream。没有脚本 API 迁移。

### 6.2 Strict require and registered plugin modules

未知 bare package 的 CJS `require()` 现在保持最高调用者 `RESOLVE`，detail 明确为 `MODULE_NOT_FOUND`；
它不会再无条件进入 special resolver。插件通过 `NodeModuleRegister` 注册的 special id（例如
`mymod:hello`）由 host allow-list 识别，仍经 `__nekoNodeDefine`/special resolver 返回 exports。
已有合法 builtin、`java:` 和 file module 用法不变。

### 6.3 Rewritten source maps

TS/JSX/compiler-produced source map 的 authored `sources`、`sourcesContent` 与 original line mapping
在 native ESM import/runtime rewrite 后继续保留；replacement range 只变换 generated coordinates。
替换文本或插入行没有一一对应 authored token 时使用明确 conservative anchor，不把该路径描述为 exact
column composition。没有 compiler map 的 native fallback 仍可使用 conservative identity/line map。
`loadEntry` 的 TS/JSX rewritten-import-then-throw 测试只承诺 authored path、line 和合法 column。

### 6.4 Single facts and visibility

`ScriptType.scriptsDirectoryName` 与内部 `ScriptPathClassifier` 是 `<type>_scripts` path segment 的唯一事实，
Cache、VirtualRegistry、ScriptBindingSchema、SourceMapRegistry、ScriptPack、loader host 和 diagnostics
均复用它。ESM virtual registry 复用 `NekoModuleHash.sha256`，不再自带 SHA-256 实现。没有新增 parser/
compiler SPI；必要的 source-map composition 属 execution-side utility。

### 6.5 Review-round-13 cache, provider and PackSync closure

脚本作者无需迁移。prepared cache stamp 现在还包含 captured compiler registry revision，因此同一
language id 的 compiler replacement 也会重新准备模块。类型 scoped clear 通过内部
`ScriptPathClassifier` 按注入 `Path` provider 比较目录段，并扫描 GLOBAL/WORLD/SERVER_CACHE 的
package path；大小写敏感 provider 不会把 `Server_scripts` 当成 `SERVER`。

PackSync 的 replacement rollback 使用严格物理删除；物理恢复失败时保留 staging、停止恢复旧
logical activation 并 fail closed，返回 fatal rollback 断连结果，不宣称旧 generation 已恢复。
production bundle 与 active hash-list transition 必须有成功的 CLIENT reload hook；无激活断线清理
仍可独立完成。无脚本 API 或公开迁移项变化。

## 7. Review-round-10 evidence boundary

本轮实际验收命令与结果：

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.error.DefaultErrorTrackerTest --tests com.tkisor.nekojs.core.module.NekoModuleResolverTest --tests com.tkisor.nekojs.core.module.NekoModuleIdentityLifecycleTest --tests com.tkisor.nekojs.core.module.NekoSandboxFactoryResourceTest --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheSessionTest --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest --tests com.tkisor.nekojs.api.event.ScriptBindingSchemaInferTypeTest --tests com.tkisor.nekojs.script.ScriptReloadGenerationTest
./gradlew.bat :common:compileJava :common:compileTestJava
./gradlew.bat :common:check
./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava
git diff --check
```

## Review-round-14 final boundary closure

本轮没有脚本作者迁移，也没有更新 golden。Java 内部边界有以下收口：

- `ScriptPathClassifier` 现在是 package-private implementation；它只识别平铺
  `root/<type>_scripts` 与明确的 `packs/<id>`, `nekojs_packs/<id>`、
  `server_packs/<bucket>/<id>` 布局。`node_modules` 或 malformed package path 不再因为
  偶然包含 `server_scripts` 而归类；diagnostics/source-map/cache 共用该事实。
- `ScriptBindingSchema` 不再由进程级 static schema/global maps 持有。它由
  `NekoModulePipelineCache` root owner 绑定，candidate 使用 session token；每个 root close
  同时清理 active/candidate views，同类型的独立 roots 不互相覆盖。validators、environment
  factory 和 pipeline cache 均使用显式 View。
- `ScriptErrorReporter` 的 Context-aware bridge 是 internal/package-private；没有新增 public
  Context API。candidate commit 的 listener/schema/error/module-view 路径捕获 `Throwable`，
  对已激活的 pending listener 执行撤销，失败保持 active generation。
- authored diagnostics 保留 package 前缀（`packs/<id>/`, `nekojs_packs/<id>/`,
  `server_packs/<bucket>/<id>/`）；只有真实 script-root segment 才做 type normalization，
  不同 pack 的同名文件不会合并身份。
- PackSync disconnect 在存在 CLIENT manager/runtime 时要求 reload hook 成功；hook 缺失或返回
  false 会恢复 registry、trust/authorization 与 connection state。完全没有 active runtime 的
  早期清理仍允许无 hook 完成。

本轮 evidence 命令：

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.api.event.ManagedBindingSchemaTest --tests com.tkisor.nekojs.api.event.ScriptBindingSchemaInferTypeTest --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest --tests com.tkisor.nekojs.core.module.ScriptTypeScopedCacheClearTest --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheSessionTest --tests com.tkisor.nekojs.core.module.NekoModuleIdentityLifecycleTest --tests com.tkisor.nekojs.core.pack.sync.PackSyncClientTest --tests com.tkisor.nekojs.core.error.DefaultErrorTrackerTest
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidatorTest --tests com.tkisor.nekojs.core.compiler.EventCallbackSourceValidatorTest --tests com.tkisor.nekojs.core.compiler.ManagedEventCallbackSourceValidatorTest --tests com.tkisor.nekojs.script.EventGroupSchemaWiringTest --tests com.tkisor.nekojs.script.ManagedApiEnvironmentTest --tests com.tkisor.nekojs.script.ScriptReloadGenerationTest --tests com.tkisor.nekojs.script.ScriptReloadRegressionTest
./gradlew.bat :common:compileJava :common:compileTestJava :common:check :26.2.0:compileJava :26.2.0-fabric:compileJava
git diff --check
```

The targeted tests above passed. The required full gates and whitespace check are the final
verification for this change. No real Minecraft client/server, loader runtime, or network session
smoke was executed. Golden files were not updated.

以上命令均 PASS；平台编译保留既有 deprecation、this-escape 和 Gson `InlineMe` classfile warnings，未更新
golden。真实 Minecraft client/server、loader runtime、network session smoke 本轮未执行，不从 JUnit 或平台
编译推断通过。

## 8. Review-round-11 isolation and rollback fixes

本轮没有脚本侧迁移。Java 内部生命周期继续使用显式 `NekoModulePipelineCache` generation
session；无 session 的 sandbox/environment construction overload 已删除，调用者不能再隐式写
root cache。

### 8.1 Active/candidate diagnostics and schema

`DefaultErrorTracker` 的 public error count、boolean 和 collection，以及 root 的 `ErrorSnapshot`
只包含 active generation。candidate `Context` 的错误在 commit 前只存在内部 candidate store；
失败 reload 丢弃它们，成功 commit 才 publish。`ScriptBindingSchema` 同样以不可变 `View`/
`Snapshot` 进行 candidate schema/global transaction：validator 取 generation view，static active
schema/globals 在 commit 前不变，失败时恢复 captured snapshot。脚本作者无需迁移。

### 8.2 Resource ownership

Sandbox/Node/environment construction failure now closes every resource already created: Context,
Node runtime, module-host preparation observer, generation globals and both `LoggerStream` instances.
The module-host observer is registered only after host construction has completed; partial installer
failure therefore cannot leave a live observer in a session.

### 8.3 PackSync replacement rollback

PackSync bundle replacement first writes a staging tree and validates its on-disk hash. The old
same-bucket pack directory is retained in a rollback tree while the new directory is exposed. A
trust, persist, authorization, registry, key-pinning or client reload failure restores the old
physical directory before rebuilding the old SERVER_CACHE registry and remote runtime approvals;
the trust-store file and connection expected-hash state are restored as part of the transaction.
For a same-bucket replacement whose active sync-id set is unchanged, the old active pack remains
live until the bundle is accepted. The new regression test uses changed content under the same sync id
and asserts that the old file and runtime approval survive a failed reload.

### 8.4 Round-11 evidence boundary

```text
./gradlew.bat :common:compileJava :common:compileTestJava
./gradlew.bat :common:check
./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava
git diff --check
```

All four commands passed for this round. Platform compilation emitted only existing deprecation,
`this-escape` and Gson `InlineMe` classfile warnings. Golden files were not updated. The evidence is
limited to common tests/compile and platform compile; it does not claim a real Minecraft, loader
runtime or network session smoke test.

## 9. Review-round-12 isolation closure

本轮没有脚本作者迁移。Java 生命周期继续使用 root-owned、generation-scoped module session；变化集中在
错误观察、binding schema view 和实现可见性边界。

- `ErrorTracker` 的 public count/boolean/collection 与 root `ErrorSnapshot` 只返回 active generation。
  candidate `Context` 的错误不会出现在 public diagnostics，直到 commit 显式 publish；失败 candidate discard
  不改变 active errors。返回 collection 与 snapshot collection 均不可变。
- module session 创建时必须绑定一个非空 `ScriptBindingSchema.View`。active session 由创建者传入 active
  snapshot；candidate session 先使用 empty view，binding installation 成功后再安装 candidate view。没有
  session-specific view 的 lookup 不再 fallback 到 static active schema，避免 candidate 在 binding 安装前误用
  或在失败恢复时污染 active。same-type 的旧 candidate late discard 不会覆盖已经 publish 的 active schema。
- generation child cache 不能继续创建 session，也不能被传给 `NekoRuntimeRoot` 作为 owner。root lifecycle 通过
  cache 的 `AutoCloseable.close()` 结束 owner；脚本 manager 的 production candidate session creation 不变。
- `NekoPreparedModule` 仍是 public immutable record，旧兼容 factory 保留；稳定 key 和显式 language/source
  factory 没有跨包 production caller，已收窄到 `core.module` package-private。模块 identity、字段和观察方法
  的既有 record 语义不变。
- `prepareCaptured` 的重复 trust guard 合并为一个带 schema View 的实现；无 session 的 direct preparation 使用
  empty view。cache 的 relative path helper 保留，因其同时服务 invalidate、JSON execution identity 与
  source-map publish 的 injected-root fallback，不在本轮扩大删除范围。

本轮最终证据命令：

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.error.DefaultErrorTrackerTest --tests com.tkisor.nekojs.api.event.ManagedBindingSchemaTest --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheSessionTest --tests com.tkisor.nekojs.core.lifecycle.NekoRuntimeModuleCacheOwnershipTest --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest --tests com.tkisor.nekojs.core.module.NekoModulePipelinePrepareTest
./gradlew.bat :common:compileJava :common:compileTestJava :common:check :26.2.0:compileJava :26.2.0-fabric:compileJava
git diff --check
```

以上命令均 PASS；保留既有 unchecked/deprecation、`this-escape` 和 Gson `InlineMe` classfile warnings，不更新
golden。真实 Minecraft client/server、loader runtime 和 network session smoke 未执行，不能从这些 common 测试
或平台编译推断通过。
