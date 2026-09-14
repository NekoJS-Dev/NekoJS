# 工单 13：Python 转译、模块行为与诊断路径 — 证据报告

日期：2026-09-14 · 分支 `ticket-13-language-py`（基于 `integration/tickets-08-11-12`）
执行者：dsh-agent

## 0. 结论摘要

- **本票发现并修复了一个真实缺陷**：Python 转译产物执行期错误的**行列落到错误的原始行**
  （不是「没有 source map」，而是取到了产物注入区的帧）。根因与判定见 §2。
- 逐 AC 判定见 §6。**9 条 AC 全部 pass**；`Status` 改为 `closed`。
- 未改动任何既有 golden；未新增依赖、Gradle project、公共 parser SPI 或第二套模块管线。
- 未跑真实 Minecraft 客户端/服务端 smoke（**not-verified**，owner 维护者/票 19/34）。

## 1. 方法：先写探针实测，再决定改什么

沿用票 12 的教训（高价值缺陷来自实测而非读票），本票先在
`common/src/test/java/com/tkisor/nekojs/core/module/` 下写临时探针（`ZzPythonProbe*`），
实测以下可观察量，**用完即删**，结论只固化进正式测试：

| 探针 | 观察量 | 结论 |
|---|---|---|
| probe 1 | `.py` 经 `NekoModulePreparation.prepare` 的 languageId / mode / sourceMap / identityOf / IR languageId | 四层全部 `python`，**无票 12 的 `jsx`/`tsx` 式不一致** |
| probe 1 | `requestedMode` | `AUTO`（`.py` 不强制模式），解析模式由产物是否带 `export` 块决定 |
| probe 1 | `prependedLineCount` | `0`——Python 用 source map 的**空段**表示注入行，而不是前置行偏移 |
| probe 2 | 18 种源语法错误 | **全部**在准备阶段抛 `IllegalArgumentException`，带文件 + 行列 |
| probe 2/5 | 未终止构造是否吞掉后续代码（票 12 的坑） | `.py` **不吞**：未闭合括号/引号/def/list/dict 全部 fail closed；后续语句保留 |
| probe 3 | raise 在文件前部/后部的原始行映射 | **前部正确、后部错**（见 §2） |
| probe 5/8 | 各帧的 Polyglot 源位置 | 定位到根因 |
| probe 4/6 | `exception.getSourceLocation()` / `tracker.getBestSourceLocation()` | 均为 **null / prelude 第一帧** |
| probe A | GraalJS 是否支持 `Error.captureStackTrace` | **不支持**（`typeof` = undefined）——排除了「让 Python 异常 prelude 自截栈」的候选方案 |

## 2. 实测发现的缺陷（本票修复）

### 2.1 现象

`.py` 里的 `raise` 在**模块顶层或文件后部**时，执行期错误的原始行**错**：

| 实测输入（raise 所在行） | 修复前 | 修复后 |
|---|---|---|
| `def boom(n): ... raise`（raise 在第 3 行） | 第 **4** 行 | 第 **3** 行 ✅ |
| `helper()` 里的 raise（第 8 行） | 第 **2** 行 | 第 **8** 行 ✅ |
| 模块顶层 `raise`（第 3 行） | 第 **1** 行 | 第 **3** 行 ✅ |
| `class MyError(Exception)` 的 raise（第 7 行） | 第 **1** 行 | 第 **7** 行 ✅ |
| TS 侧 `throw new MyError(...)`（用户异常类，第 4 行） | 第 **1** 行 | 第 **4** 行 ✅ |

修复前既有测试仍全绿，因为既有 Python 错误定位测试只覆盖了「raise 恰好落在第 3 行且
expectation 恰好等于 4」的巧合（`NekoModuleErrorMappingCorpusTest` 的 `.ts` 用例用内置
`Error`，没有构造器帧，因此不受影响）。

### 2.2 根因（两段，缺一不可）

1. **Python 产物的注入区没有 source map 映射段**。`PythonEmitter.emit` 在正文之前注入
   运行时 helper 与异常 prelude（19 个 `class X extends Exception {}` + `.forEach` 重命名行 +
   `__nekoExcIs`），`emitRuntimeHelpers` 用 `line0(...)` 发射——**不调用 `recordMapping`**，
   产物尾部还会追加 `export { ... }`。实测 raise 在第 3 行时，正文 `function boom(n) {`
   落在产物第 26 行，而 `raise` 落在第 28 行。
2. **错误解析取到的帧被注入区顶掉**。GraalJS 对自定义 `Error` 子类**不捕获构造时的调用栈**，
   因此 `class Exception extends Error {}` / `class ValueError extends Exception {}` 会作为
   `root=Exception` / `root=ValueError` 的**独立栈帧**排在真正 throw 的帧之前。
   `PolyglotException.getSourceLocation()` 与 `DefaultErrorTracker.getBestSourceLocation()`
   都返回**第一个 guest 帧**，即 prelude 的 `class Exception extends Error {}`。
   该行在 `mappings` 里是空段 → `SourceMapRegistry.getMappedPosition` 返回
   `(path=null, line=产物行, column=产物列)`；`ScriptError` 于是把**产物行号当原始行号**用，
   经行号对行号错映射到无关的原始行（或直接采信产物行）。

> 证据（修复前实测）：`PROBE6 exceptionLoc=null`、
> `tracker.getBestSourceLocation()=[...line=4 text=class Exception extends Error {}] relative=...h.py`；
> 完整帧列表：`#0 root=Exception line=4` → `#2 root=boom line=28 text=new ValueError("kaboom")`。

### 2.3 修复（最小改动，集中在错误归属 seam）

| 文件 | 改动 |
|---|---|
| `SourceMapRegistry` | 新增包内可见 `hasSourceMapCoverage(path)` 与 `coversGeneratedLine(path, jsLine)`：区分「原生 JS 的 null map＝恒等映射」与「转译产物里没有源对应行的注入区」 |
| `ScriptError` | `parseSignature` 采纳位置前先过 `isExceptionConstructionFrame`；命中则用 `firstOriginFrame` 在本模块内取第一个**有映射段、且不是类声明行**的帧 |

两条判据（`isExceptionConstructionFrame`）：

1. **注入区**：产物**登记了带段 source map**，但该行没有映射段 → 不可信。
   原生 JS（`.js/.mjs/.cjs` 的 null map）仍按恒等映射处理，**不回归**。
2. **异常类声明帧**：该位置的代码是 `class ...` 声明行（覆盖 `class MyError(Exception)` /
   `class MyError extends Error {}` 两种来源）。

**只在本模块内查找**（`path.equals(originPath)`）：跳到调用方会把错误归属到错误的模块，
破坏票 11 AC7 的跨 import 归属语义。

**未采纳的候选**：让 Python 异常 prelude 自截栈（`Error.captureStackTrace`）——实测
GraalJS **不支持**（probe A）；把 prelude 内联成一行只是换一种产物形状，不解决「取错帧」
这一根因。当前方案同时修好了 JS/TS 侧的同类问题（TS 用户异常类）。

## 3. 物理落点

| 位置 | 内容 |
|---|---|
| `common/src/main/java/com/tkisor/nekojs/core/error/SourceMapRegistry.java` | 映射覆盖查询（2 个包内方法 + 1 个内部 record 方法） |
| `common/src/main/java/com/tkisor/nekojs/core/error/ScriptError.java` | 异常构造/注入帧的识别与跳过 |
| `common/src/test/java/com/tkisor/nekojs/core/module/PythonModulePipelineCorpusTest.java` | 新增：AC1/AC2/AC4/AC5 的 representative corpus |
| `common/src/test/java/com/tkisor/nekojs/core/module/PythonModuleErrorPhaseCorpusTest.java` | 新增：AC3/AC6 的错误阶段与混合加载 corpus |
| `common/src/test/java/com/tkisor/nekojs/core/module/PythonMinimalExampleTest.java` | 新增：AC9 示例的真实执行 + 迁移材料存在性 |
| `common/src/test/java/com/tkisor/nekojs/core/module/PythonDeclarationOwnershipTest.java` | 新增：AC7 declaration/probe 语言与 module 归属 |
| `docs/architecture-refactor/baseline/2026-09-14-language-python/` | 本报告 + `MIGRATION.md` + `minimal-example.py` + `minimal-example-entry.mjs` |

**未新增**：Gradle project、公共 parser SPI、Python 运行时或任何非纯 Java 依赖、第二套模块管线。

## 4. 收缩 gate（Work item 6）

**结论：不存在 Python 的旧并行转换或装载旁路。**

`grep` 全仓（`common` / `src` / `versions`）后 Python 的**唯一生产入口**是：

- `PythonTranspilerPlugin.registerScriptCompilers` → `registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler())`；
  非 NeoForge 平台的唯一摩擦点是 `FabricPluginLoader.BUILTIN_PLUGINS` 里的显式清单（同一插件类）。
- 转译唯一入口 `PythonToJsCompiler.compileDetailed`，经 `NekoLegacyLanguagePlugin`（`IScriptCompiler` 桥）
  挂进 `NekoModulePipeline`；`NekoCompilationPipeline` 走另一分支时也用同一注册表解析出的插件。
- 未找到第二条 lexer/parser/emitter 调用点、第二套 loader 或 Python 私有 resolver；
  `src/` 零 Python 引用，`versions/` 只有 Fabric 的插件清单。

因此**本票不删除任何无辜代码**（票据原文允许「不存在就如实写不存在」）。

## 5. 与票 11/12 已验收语义的关系

- 只**消费** `NekoPreparedModule` / `NekoModulePreparation` / `NekoModuleCacheKey` /
  `NekoModuleIdentity` / `NekoModuleDiagnosticOrigin` / 错误阶段模型，**未重定义**。
- 未新建 Python 私有准备/缓存路径；`.py` 与 `.js/.cjs/.mjs/.ts` 共享同一 prepared cache、
  dependency graph 与 source map 注册表（实测：混合 import 各身份正确、跨 import 错误归属正确）。
- `SourceMapRegistry` 只新增**只读查询**，不改既有 mapping 语义；原生 JS 的
  `sourceMap()==null ＝ 恒等映射` 契约**未变**（`NekoModulePreparationIdentityTest.sourceMapNullMeansIdentityForNativeJs` 继续绿）。

## 6. 逐 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| 1. `.py` corpus 得到 language id=Python、正确 module mode、可执行 code/IR、可用 source map | **pass** | `PythonModulePipelineCorpusTest.representativeCorpusProducesLanguageIdModeExecutableCodeAndUsableSourceMap`（3 条 corpus：缩进/定义/类/注释；断言 languageId=`python`、requestedMode=AUTO、mode=ESM、产物**真跑**出预期值、source map 非空且能把产物行映射回原始 .py 行） |
| 2. 缩进、定义、调用、注释、导入的既有行为由 golden/corpus 固定，不冻结私有 parser 对象身份 | **pass** | 同上 corpus（缩进 + def + 调用 + 注释 + 空行）；导入由既有 `PythonEmitterMagicImportTest` / `PythonGoldenTest` 固定；新测试只断言外部可观察值，不引用 `PythonNode` / `PythonEmitter` 内部结构 |
| 3. 源语法/转换错误在准备阶段携带原始行列；生成代码执行错误也能回映射到 `.py` | **pass** | 错误侧：`PythonModuleErrorPhaseCorpusTest.preparationErrorsCarryOriginalFileAndPosition`（5 种源错误，断言文件 + `line L, col C` + 非 PolyglotException）；执行侧：`executionErrorInPythonMapsBackToRaiseLine`（前部/后部两种布局）、`executionErrorFromUserExceptionClassMapsToRaiseLine`。**本票修复的缺陷即此条**（§2） |
| 4. Python 模块身份、依赖图和 cache invalidation 与统一 Resolution/Cache 一致 | **pass** | `pythonLanguageIdentityIsConsistentAcrossLayers`（prepared == cache key == identityOf == 注册表，四层同源）；`PythonModuleErrorPhaseCorpusTest.mixedImportsTrackIdentityAcrossPythonAndJsFamily`（同一 resolver/cache，身份按扩展名正确） |
| 5. 源码、mode、identity 或依赖变化不会命中旧 Python 产物 | **pass** | `unchangedPythonHitsCacheAndContentChangeInvalidatesIt`（未变化同实例；内容变化新实例 + 新 key + 新产物）；`languageIdentityChangeInvalidatesPythonCache`（同路径同内容换语言 → languageId 与 key 都变）；`failedPythonPreparationDoesNotPopulateCache`（失败不留条目） |
| 6. Python 与 JS/CJS/ESM/TS 混合加载的错误阶段和模块归属可观察 | **pass** | `mixedImportsTrackIdentityAcrossPythonAndJsFamily`（`.py/.js/.cjs/.mjs/.ts` 五扩展名混合 import，各自执行不串味，身份可追踪）；`errorThroughTsImportIsAttributedToThePythonModule`（TS 入口调用 Python helper 抛错 → 归属 .py 与其 raise 行）；`invalidTopLevelControlFlowFailsVisiblyAndKeepsFollowingCode`（加载期硬失败、非静默、位置仍指原 .py 行）；`pythonExecutionFailureSurfacesFromLoadEntryWithOriginalCause`（执行失败不被吞成无来源 IOException） |
| 7. Python declaration/probe 的语言与 module 归属不被 JS declaration 覆盖 | **pass** | `PythonDeclarationOwnershipTest`：同一份共享 IR 交给 `PythonProbeBackend`（`languageId()="python"`）与 `TypeScriptProbeBackend`（`"typescript"`）渲染 → 产物分别落在 `.pyi` / `.d.ts`（`.pyi` 只能出现在 python 目录、TS 目录无 `.pyi`、相对路径集合互不重叠），同一个 Java 类在各自语言中按同一 Java 包归属声明，且 python 侧 runtime member parity（`def compute`、`@property`）保持 |
| 8. 不删除 Python 支持，不新增 Python runtime、公共 parser SPI 或第二套模块管线 | **pass** | §4 收缩 gate：唯一入口 `PythonTranspilerPlugin` → `PythonToJsCompiler`；`git status` 无删除；未加依赖/项目/SPI；`guardLint` 与 `:common:check` 见 §7 |
| 9. 交付 `.py` 最小可运行示例与必要迁移材料；示例只用已通过 gate 的能力 | **pass** | `minimal-example.py` + `minimal-example-entry.mjs` + `MIGRATION.md`；`PythonMinimalExampleTest` 把示例**真实执行**（`host.loadEntry`，断言 `hello world|6|30|1|16`），并断言示例里的 `raise` 回到原始 `.py` 第 27 行 |

## 7. 门禁

见 Closure record 里的最终门禁数字与命令。

## 8. 未验证项与 owner

1. **真实 Minecraft 客户端/服务端 smoke（含 `/nekojs reload`）：not-verified**。
   本票改动全在 `common`（无 MC/loader import，`guardLint` 复核），未启动真实客户端。
   owner：维护者 / 票 19 / 票 34。
2. **节点 `:26.1.2:compileJava`：未单独执行**。改动不触碰平台面。owner：维护者。
3. **Python v1 子集的既有边界**（非本票引入，如实记录）：
   顶层 `return` / `break` / `continue` / `yield` 在准备阶段不拦截，加载期以硬 `SyntaxError` 可见失败
   （位置仍指原 `.py` 行、后续语句保留）；类体直接 `raise` 同理。已 characterization 于
   `invalidTopLevelControlFlowFailsVisiblyAndKeepsFollowingCode`。
   是否前移到准备阶段 fail closed 应由后续语言票结合作者反馈评估。owner：后续语言票 / 维护者。
4. **source map 列粒度为语句级**：Python 与 TS 一样，映射段固定在列 0（`PythonSourceMap.build`
   的 `vlq(0)`），因此错误报告的列是语句起始列。断言按此粒度写（`col=1`），未编造精确列。
