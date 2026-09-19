# 13: Python 转译、模块行为与诊断路径

**What to build:** 脚本作者写入 .py 后，既有 Python 语法、缩进结构、定义/调用、模块模式和 import 行为经 Preparation 生成可执行 JS/IR，再由 Resolution/Cache 与 Graal 执行；Python 源错误和生成代码运行错误均保留原始位置，缓存与声明不把 Python 误标为 JS。

**Blocked by:** [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)

**Status:** closed

**Assignee:** 13-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 扩展 Python golden/corpus，覆盖缩进、定义/类/调用、注释、模块导入和既有 Python-to-JS 行为的固定输出。, 确保 .py 的 language id、requested mode、cache key、source map 和 prepended-line 语义进入统一 prepared module。, 区分 Python 源解析/转换错误与生成 JS 的 link/执行错误，均能映射回原始 .py 文件。, 让 Python import/require 与 CJS/ESM 依赖图使用同一模块身份与失效规则，不复制第二条 resolver。, 保持 Python 支持和纯 Java自研实现，不引入 Python 运行时或非纯 Java 依赖。, 收缩 gate：Python 旧并行转换或装载旁路只有在替代 behavior、source-map/declaration、trace 通过且无调用者后移除；随本票完成收缩，不推迟 final release，也不删除 Python 支持或公开功能。

## Acceptance criteria

- [x] .py representative corpus 得到 language id=Python 语义、正确 module mode、可执行 code/IR 和可用 source map。
- [x] 缩进、定义、调用、注释和导入的既有行为由 golden/corpus 固定，不冻结私有 parser 对象身份。
- [x] Python 源语法/转换错误在准备阶段携带原始行列；生成代码执行错误也能回映射到 .py。
- [x] Python 模块身份、依赖图和 cache invalidation 与统一 Resolution/Cache 行为一致。
- [x] 源码、mode、identity 或依赖变化不会命中旧 Python 产物。
- [x] Python 与 JS/CJS/ESM/TS 混合加载的错误阶段和模块归属可观察。
- [x] Python declaration/probe 中的语言与 module 归属不被 JS declaration 覆盖。
- [x] 不删除 Python 支持，不新增 Python runtime、公共 parser SPI或第二套模块管线。
- [x] 随实现交付 .py 最小可运行示例与必要迁移材料；示例只使用已通过 gate 的 Python 语法、import 和诊断能力。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md): Python 必须复用 prepared module、模块身份、cache key 和 source location 模型；独立 transpiler/load 路径会破坏全语言语义一致性。

## Scope and coordination

- **Rationale:** Python 的完整路径独立验收，因为其源形式、source map 和错误映射与 TS/JSX 不同；但仍阻塞于同一模块基础，避免私有 resolver。
- **Coordination:**
  - MANAGED_SURFACE: Python declaration 与 Probe 输出必须由 managed 契约派生。
  - DIAGNOSTICS: Python 源位置和阶段信息进入统一错误上下文与 workspace 报告。
  - PERF_BASELINE: Python corpus 成本只记录对照，不设定未确认阈值。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
## Closure record（2026-09-19）

- 执行者：13-agent。工作流：读票全文 / 规格 06 / 票 11 全部 Closure 与 Review-round addendum /
  票 12 Closure / README「认领、前沿与完成」/ 11 的 MIGRATION 与 module-examples →
  源码定位（prepared module、pipeline、cache、loader host、Python compiler/lexer/parser/emitter、
  source-map registry、probe backend）→ 用 9 个一次性探针测试（**已删除**）实测当前 Python
  prepare/执行/错误映射行为并定位真实缺陷 → TDD 红（`NekoPythonPrepareTest` 3 红、
  `NekoPythonRuntimeTest` 12 红）→ 实现收口（绿）→ 全量
  `./gradlew.bat :common:test --rerun-tasks`（1725 tests, 0 failed, 4 skipped）→
  `:common:check` + `:26.2.0:compileJava` + `:26.2.0-fabric:compileJava` +
  `git diff --check` → 双轴自查 → 勾选 AC → 关票提交（不 push）。
- 先决输入（票 11 closed，不重做）：`NekoPreparedModule` / `NekoModulePipeline` /
  `NekoModulePipelineCache` / `NekoModuleError` / `NekoSourceMapBuilder.identity` /
  `NekoCompileException`（票 12 建立的 authored 行列载体）的形态与语义未变；
  本票只复用这些既有 seam。

### 定位到的真实缺陷（本票修复）

1. **Python 准备期错误没有 authored 行列字段。** `PythonLexer` / `PythonParser` /
   `PythonEmitter` / `FStringParser` 抛裸 `IllegalArgumentException`，位置只在消息文本里，
   `NekoModuleError.sourceLine()/sourceColumn()` 恒为 `-1`。票 12 已建立
   `NekoCompileException` 与「pipeline 发布 authored 位置」这条 seam，Python 未接入。
2. **Python source map 的 `sources[0]` 是纯文件名。** `SourceMapRegistry` 用 source 条目把
   映射结果解析回 authored 文件，因此每个 Python 诊断都落到
   `.native_esm_modules/<name>.py` 而不是 authored 模块。
3. **执行期取错 guest 栈帧。** Python 产物会在顶层前置异常 prelude
   （`class ValueError extends Error {}` 等）。抛出该 prelude 类实例时栈顶是**类声明帧**，
   宿主 `sourceLocation` 取第一个 guest 帧 → 把生成坐标当成 authored 行
   （authored 第 4 行的 `raise` 报成第 5 行）。

### 交付物（源码）

- `PythonLexer` / `PythonParser` / `PythonEmitter` / `FStringParser`：改抛
  `NekoCompileException`（继承 `IllegalArgumentException`，**消息文本逐字保留**），
  携带 authored 行列；emitter 为语句级位置（列取语句起点，类注已说明）。
  `PythonToJsCompiler` 的 `catch (IllegalArgumentException)` 包装语义不变。
- `PythonToJsCompiler`：source map 的 `sources[0]` / `file` 由纯文件名改为 authored 路径
  （与其它前端 `NekoSourceMapBuilder.displayName` 同一约定）；
  既有「`sources[0]` 含 `test.py`」断言继续成立。
- `NekoScriptModuleLoaderHost#sourceLocation`：由「第一个 guest 帧」改为按优先级选帧——
  (1) 能解析到 authored 位置且属于不同模块的帧；(2) 任何能解析到 authored 位置的帧；
  (3) 都解析不到时保留原 source location 不变（**不编造**位置）。新增
  `mapsToAuthoredSource` 判定。**没有**第二条 pipeline / 第二个 resolver / 新 parser SPI。
- 未引入外部转译依赖、未新增公共 parser SPI、未新增 Gradle 子项目或依赖；
  `common` 未新增 Minecraft/loader import。

### 交付物（测试，common，4 新类 28 用例 + 1 类断言更新）

- `NekoPythonPrepareTest`(10)：AC1/AC3（准备侧）/AC4 的 prepare 级 seam——language id、mode、
  可执行 code、statement 粒度可用 map、**prepended-line 语义**（前置助手行显式无映射且不偏移
  后续语句）、词法/语法/发射期/缩进错误的 authored 行列、内容/mode/language 失效。
- `NekoPythonRuntimeTest`(13)：AC1/AC2/AC3（执行侧）/AC4/AC6 的执行级 seam——缩进+注释+elif+
  for、class/闭包/默认参数/f-string、兄弟 `.py` 模块 import（命名 + 命名空间身份一致）、
  `raise` 的 authored 行、CJS 形态脚本不报生成助手行、跨 import 子模块身份与行、
  异步入口一致性、依赖失效/未变化不重求值、`.py`↔`.mjs` 双向 import、缺失模块的 RESOLVE 归属、
  失败的 ESM 子模块保留自身身份。
- `PythonExamplesSmokeTest`(2)：AC9 示例冒烟 + 文档化 language 身份/source-map 行为。
- `PythonDeclarationAttributionTest`(3)：AC7——Python 与 TS backend 各自拥有 language id 与输出
  目录、由同一 catalog/IR 派生但 module 归属按 Java 包路径、Python 产物不冒充 TS 声明。
- `NekoTypeScriptJsxRuntimeTest`(1 处断言更新)：interop 包装子模块失败的 `sourceLine()`
  由 `-1` 变为子模块真实的 `throw` 行 3（本次宿主选帧修复的连带收益，归因仍是子模块）。

### 交付物（示例/迁移）

- `common/src/test/resources/nekojs/language-py-examples/py/{hello.py,greet.py}` 最小可运行示例。
- `docs/architecture-refactor/baseline/2026-09-19-language-py/MIGRATION.md`：脚本零迁移声明、
  作者可见的唯一变化（错误位置更精确）、示例内联、language 身份/mode/source map/cache key/
  prepended-line 口径表、诊断阶段与位置口径、执行期帧选择根因、Java 调用点迁移（无）、
  已知边界与未做事项。

### 验收判定（逐条 AC：seam + 精确命令 + 结果）

| AC | 实现 seam / 测试方法 | 精确 Gradle 命令 | 结果与限制 |
|---|---|---|---|
| 1 | `NekoPythonPrepareTest#pythonDefinitionsPublishLanguageModeAndExecutableCode`、`#pythonBareStatementsPrepareAsCommonJsAndStayExecutable`、`#pythonSourceMapPublishesAuthoredPathAndContent`、`#prependedHelperLinesAreUnmappedAndDoNotShiftAuthoredStatements` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonPrepareTest` | PASS（10/10）；language id=`python`、mode=ESM/CJS、可执行 JS、非空 statement map，且每条 authored 语句映射回真实 authored 行。`prependedLineCount=0`（前置行已在 map 内部消化）。 |
| 2 | `NekoPythonRuntimeTest#indentationDefinitionsCallsAndCommentsRunUnchanged`、`#classesAndClosuresRunUnchanged`、`#pythonModulesImportEachOtherThroughTheSharedResolver`；既有 `PythonGoldenTest`(20)/`PythonToJsCompilerTest`(181)/`PythonEmitterMagicImportTest`(4) 未改且全绿 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest --tests com.tkisor.nekojs.core.compiler.PythonGoldenTest --tests com.tkisor.nekojs.core.compiler.PythonToJsCompilerTest --tests com.tkisor.nekojs.core.compiler.python.PythonEmitterMagicImportTest` | PASS；缩进/注释/elif/for 的控制流与返回值、class+闭包+默认参数+f-string、兄弟模块 import 均经**真实 Graal 执行**断言输出值。**golden 未更新。** |
| 3 | `NekoPythonPrepareTest#pythonLexErrorCarriesAuthoredFileLineAndColumn`、`#pythonParseErrorCarriesAuthoredFileLineAndColumn`、`#pythonEmitterErrorCarriesTheAuthoredStatementLine`、`#pythonIndentationErrorCarriesTheAuthoredLine`；执行侧 `NekoPythonRuntimeTest#runtimeFailureInRaisedFunctionReportsAuthoredLineAndIdentity`、`#runtimeFailureInCommonJsShapedPythonReportsAuthoredLineNotGeneratedLine`、`#crossImportRuntimeFailureKeepsTheFailingChildPythonIdentity` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonPrepareTest --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest` | PASS；准备期错误为 `PREPARE/Script Preparation` + authored 文件与行列（词法 3:7、语法 authored 行、发射期 authored 语句行、缩进 3）；执行期异常为 `EXECUTE/Script Execution Environment` + authored `.py` 路径/行列 + guest cause。限制：发射期列固定为语句起点（emitter 只有语句级行）。 |
| 4 | `NekoPythonRuntimeTest#pythonModulesImportEachOtherThroughTheSharedResolver`、`#unchangedPythonDependencyIsNotReevaluated`、`#changedPythonDependencyIsReloadedThroughTheSharedCache`、`NekoPythonPrepareTest#pythonDependencyChangeInvalidatesThroughTheSameInvalidateSeam`；既有 `NekoModuleIdentityLifecycleTest`(25)/`NekoModulePipelineCacheStampTest` 未改且全绿 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest --tests com.tkisor.nekojs.core.module.NekoModuleIdentityLifecycleTest` | PASS；`.py` 走同一 `NekoModuleResolver` + 同一模块身份/失效（被 import 模块只求值一次；改动经 `invalidateModuleTree` 后新值可见）。无第二条 resolver。 |
| 5 | `NekoPythonPrepareTest#contentModeAndLanguageChangesInvalidatePythonEntries`、`#unchangedPythonInputsHitTheSamePreparedIdentity` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonPrepareTest` | PASS；内容变化、`.py` 语言身份替换（registry revision 连带）、顶层定义→纯语句的 mode 变化都产生不同 `cacheKey` 与新产物；同输入稳定命中。 |
| 6 | `NekoPythonRuntimeTest#missingPythonModuleIsResolvedAtTheResolveStageNotExecution`、`#failingEsmChildImportedFromPythonKeepsTheEsmChildIdentity`、`#pythonAndEsmModulesImportEachOtherWithObservableAttribution`、`#crossImportRuntimeFailureKeepsTheFailingChildPythonIdentity`、`#asyncEntryLoadingOfPythonChildKeepsTheChildIdentityAndAuthoredLine` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest` | PASS；缺失模块=`RESOLVE/Module Resolution-Cache` 且 sourcePath 为 authored 引用方；失败的 ESM 子模块保留自身 identity；`.py`↔`.mjs` 双向 import 的返回值与错误归属可观察。限制：literal dynamic import 到失败的 CJS 子模块时 Graal promise 边界仍丢子模块身份（票 12 已记录，本票未伪造成已修复）。 |
| 7 | `PythonDeclarationAttributionTest#pythonBackendKeepsItsOwnLanguageIdAndOutputDirectory`、`#pythonDeclarationIsDerivedFromTheSharedCatalogAndKeepsModuleAttribution`、`#pythonDeclarationDoesNotClaimJsLanguageIdentity`；既有 `PythonDeclarationDeterminismParityTest`/`PythonProbeBackendIntegrationTest`/`NekoProbeBuiltinPluginTest` 未改且全绿 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.probe.PythonDeclarationAttributionTest --tests com.tkisor.nekojs.probe.PythonDeclarationDeterminismParityTest --tests com.tkisor.nekojs.probe.backend.python.PythonProbeBackendIntegrationTest` | PASS；Python language id 与 language 输出目录独立于 TS；同一 catalog/IR 下 module 归属是 Java 包路径；输出全是 `.pyi` + `py.typed`，不含 `.d.ts` 与 TS 语法。 |
| 8 | `ModulePipelineIsolationTest`(16，未改，含源码/签名扫描与零参构造断言)；`git status` 无新 Gradle 子项目、无新依赖、无新 parser SPI | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest`；`git diff --stat` | PASS；`common` 未新增 Minecraft/loader import，未新增 build 文件/依赖，未新增公共 parser SPI；Python 仍走唯一 `NekoModulePipeline` + `NekoModulePipelineCache`。 |
| 9 | `PythonExamplesSmokeTest`(2) + `common/src/test/resources/nekojs/language-py-examples/` + `baseline/2026-09-19-language-py/MIGRATION.md` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.PythonExamplesSmokeTest` | PASS；示例经真实 host 装载并断言文档承诺输出（`hello, neko!` / `py`）、language id 与 authored source-map 映射。真实 loader/in-game smoke 不在此命令内。 |

### 综合门禁

```text
./gradlew.bat :common:compileJava :common:compileTestJava
./gradlew.bat :common:test --rerun-tasks        → 1725 tests completed, 0 failed, 4 skipped
./gradlew.bat :common:check
./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava
git diff --check
```

以上命令均 PASS（平台编译仅有既有 unchecked/deprecation 与注解缺失告警，不影响成功）。
**未更新任何 golden。**

### 双轴自查

- **Standards**：Python 前端复用票 12 已建立的 `NekoCompileException` seam，没有新建并行载体；
  执行期帧选择收口在既有的 `sourceLocation`/`mappedPosition` 单一入口，没有第二套位置解析；
  source map 的 authored 路径与其它前端共用同一约定与同一 `SourceMapRegistry`。
  无投机抽象、无未调用 helper、无残留重复实现（探针测试已全部删除）。
- **Spec**：9 条 AC 均落到最高调用者 seam（`prepare` 的 prepared module 字段与异常字段、
  `loadEntry`/`loadEntryAsync` 的返回值/异常字段、shared prepared cache、probe backend 产物），
  未断言私有 AST 布局或私有 parser 对象身份。

### 限制与未做事项

- 未运行真实 Minecraft client/server、loader runtime 或 network session smoke；本 closure 只声称
  common JUnit 与平台编译证据。
- Python 发射期错误的列固定为语句起点（`col 1`）；行准确。
- 未闭合的调用/括号到文件末尾才报错（CPython 同样的文法行为），位置取 parser 实际报告处。
- literal dynamic import 到失败的 CJS 子模块时 Graal promise/host 边界仍丢子模块身份
  （票 12 已记录）；本票未伪造位置掩盖。
- 本票不扩大 Python 语言范围、不新增 Python 语法；Probe 声明契约（`.pyi`/pyrightconfig）
  由 runtime catalog 单一来源派生，本票未改其契约。
- 未设置任何性能阈值（PERF_BASELINE 独立）；未改 05/06/07 语义。
## Review-round-1 addendum（2026-09-19）

协调者复核的 Spec 轴 finding（F8/F9）与本轮 fix-forward 的实际收口。票保持 `closed`。
本轮**不改任何 main 源码**（只改测试与文档）；三条 finding 都已落到真实运行路径或如实记录边界。

### F8 [已修] AC3 后半（生成 JS 的 link 错误）此前未被真正测到

**Finding（属实）。** 原 `generatedCodeSyntaxErrorIsReportedAsPreparationFailureAtTheAuthoredLine`
的输入是未闭合的 `value = f(`，走的是 **Python 源解析错误**，与
`NekoPythonPrepareTest#pythonParseErrorCarriesAuthoredFileLineAndColumn` 重复；测试名与
注释里的「破坏性探针不可用」是探针阶段残留的自我说服，不是事实约束。

**实际修法（先查清事实，再决定 a/b）。**

1. 先验证「Python 发射器是否保证生成 JS 合法」：对 20 种 JS 保留字 / 严格模式不安全名
   在**每一个绑定位置**（参数、for 目标、walrus、import/from 别名、except 名、with 目标、
   match 捕获、推导式目标、lambda 参数、类名、`*args`/`**kwargs`、装饰器根、augassign、
   元组目标、嵌套函数）构造 Python 源，逐个 `cache.prepare` 后用 Graal `context.parse`
   以 module 模式**重新解析产物** —— **20/20 全部解析通过**。`PythonEmitter` 的
   `collectBindingNames` + `JS_RESERVED_IDENTIFIERS` 重命名是完备的，因此
   **「生成 JS 的语法错误」在当前发射器下不可构造**（这正是选项 b 的适用条件）。
2. 但 AC3 的后半还有**另一条真实可达的路径**：**生成 JS 的 link 错误**。
   Python 的 `from <sibling> import <name>` 会转译成 ESM 静态 import；被 import 的模块
   若没有该导出，失败发生在 JS 这一侧（link），而不是 Python 源解析侧。

**因此采用 a 的「真判别用例」形态，但落在 link 而非 syntax 上：**

- 新版 `NekoPythonRuntimeTest#generatedJsLinkFailureIsDistinguishedFromPythonSourceAndExecutionErrors`：
  断言 `cache.prepare(entry)` **成功**（Python 源合法、产物确实含
  `import { absent_name } from './link_sibling'`、mode=ESM）；
  `loadEntry` 抛 `Stage.LINK` / `Module Resolution/Cache`，
  cause 是 `NekoEsmLinkException`，sourcePath 是 authored 引用方；
  并在同一用例内用「存在但运行期抛错」的 sibling 对照断言 `EXECUTE`——
  即 AC3 要求区分的源解析错误 / link 错误 / 执行错误三条路径**同时**被覆盖。
- 旧测试按选项 b 改名/改注释为它实际测的东西：
  `pythonSourceParseErrorIsReportedAtPrepareWithTheAuthoredLine`，
  删掉「破坏性探针不可用」那句自我说服。

**红侧证据（必给，已实际执行）。** 把 link 诊断的归类从 LINK 改成 EXECUTE
（`NekoEsmLinker.link` 的 catch 改为 `NekoModuleError.execute(...)`，临时改动、已回滚）：

```text
NekoPythonRuntimeTest > generatedJsLinkFailureIsDistinguishedFromPythonSourceAndExecutionErrors() FAILED
    java.lang.AssertionError: Expected NekoModuleError[LINK/Module Resolution/Cache] in chain of
    com.tkisor.nekojs.core.module.NekoModuleError: Missing ESM export 'absent_name' at ...link_entry.py:1:10
15 tests completed, 1 failed
```

证明该用例真的在断言 LINK 归类，而不是恒真。回滚后 15/15 全绿。

**精确命令**

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest --rerun-tasks
```

**结果**：PASS（15/15，0 failed）。

**仍未覆盖**：生成 JS 的**语法**错误（lowered-JS syntax error）在 Python 下不可构造——
因为发射器对全部 20 种保留字/不安全名绑定位置都做了确定性重命名，产物始终合法。
这条不是"没测"，而是**在当前实现下不存在该路径**；若将来在发射器里新增不经
`jsName()` 的绑定位置，需要按票 12 的
`NekoTypeScriptJsxRuntimeTest#loweredJsSyntaxErrorIsMappedAfterPreparationSucceeded`
形态补一条（断言 `cache.prepare` 成功 + `loadEntry` 抛 PREPARE + cause 是
`NekoEsmLinkException`）。

### F9 [已修] AC7 此前只是构造性证明

**Finding（属实）。** 原用例把两个 backend 各自 `new` 出来分别 `generate()`，只能证明
"按构造不会撞"，没有经过真实的 `ProbeCoordinator` 会话；「拒绝共享输出目录」这条保护
从未被行使。

**实际修法**：新增两条经过真实 `ProbeCoordinator` 的用例（本目录的
`common` 单测可以驱动它，`ProbeCoordinatorHardeningTest` 已是先例）：

1. `coordinatorRunsTypeScriptAndPythonTogetherWithoutOverwritingEachOther`：
   经 `NekoPluginBootstrap` 注册两个 builtin backend 并 lock（同生产 bootstrap），
   再 `ProbeBackendSelector.named` 取出 `typescript:builtin` 与 `python:builtin`
   **在同一次 runProbe 里同时在场**（正是命令层 `/nekojs probe all` 的形态）。
   断言两个结果都 success、各自 `outputDir` 是 `.neko_probe/typescript` 与
   `.neko_probe/python`；并**双向**断言目录内容不串：
   Python 目录里没有任何 `.d.ts`，TS 目录里没有任何 `.pyi`，
   Python 侧确实产出 `nekojs/py.typed` + `nekojs/__init__.pyi`。
2. `coordinatorRejectsTwoBackendsSharingOneOutputDirectory`：
   用固定输出目录的探针 backend 把两个语言指向同一目录，行使
   `ProbeCoordinator` 的目录去重保护——断言第二个被跳过（消息含
   `duplicate output directory`）、第一个产物完整存活、被跳过的那个没有写进共享目录。

**精确命令**

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.probe.PythonDeclarationAttributionTest --rerun-tasks
```

**结果**：PASS（5/5，0 failed；新增 2 条真实 coordinator 用例）。

**仍未覆盖**：真实 Minecraft 内 `/nekojs probe all` 命令层（平台命令类）未执行；
本轮证据是 common 层的真实协调器会话 + 真实 registry bootstrap，不含游戏内命令路径。

### AC5 口径澄清 + 补测 [已修]

**Finding（属实）**：原 `changedPythonDependencyIsReloadedThroughTheSharedCache`
调用了 `host.invalidateModuleTree(...)`，只证明了**显式**失效。

**实际修法**：补 `NekoPythonRuntimeTest#changedPythonDependencyIsObservedWithoutAnExplicitInvalidate`——
只改盘上被 import 的 `.py`，**不做任何** `invalidateModuleTree`/`invalidate`/`clear`，
直接重跑入口，断言看到新值。这走的是宿主 `refreshPreparedExecutionTree` 的
依赖驱动失效路径。

**红侧证据（已实际执行）**：在 `refreshPreparedExecutionTree` 开头临时加
`if (true) return;`（已回滚）：

```text
NekoPythonRuntimeTest > changedPythonDependencyIsObservedWithoutAnExplicitInvalidate() FAILED
    org.opentest4j.AssertionFailedError at NekoPythonRuntimeTest.java:382
15 tests completed, 1 failed
```

回滚后全绿。故 AC5 现在**显式失效与自动失效都已验证**（两条独立用例）。

**精确命令**

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest --rerun-tasks
```

**结果**：PASS（15/15）。

### Review-round-1 evidence matrix

| Finding / AC | 精确证据 | 精确 Gradle 命令 | 结果与限制 |
|---|---|---|---|
| F8 生成 JS 的 link 错误被真判别 | `generatedJsLinkFailureIsDistinguishedFromPythonSourceAndExecutionErrors`（prepare 成功 + LINK + cause `NekoEsmLinkException` + EXECUTE 对照）；红侧：`NekoEsmLinker.link` catch 改 EXECUTE → 该用例 FAILED（已回滚） | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest --rerun-tasks` | PASS 15/15。生成 JS 的**语法**错误在 Python 下不可构造（20/20 保留字绑定位置产物均合法解析），已在 addendum 写明原因与将来补测形态。 |
| F8 旧测试名不副实 | 改名为 `pythonSourceParseErrorIsReportedAtPrepareWithTheAuthoredLine`，删除「破坏性探针不可用」注释 | 同上 | PASS；名称与断言一致（Python 源解析错误 + authored 行）。 |
| F9 真实 ProbeCoordinator 会话 | `coordinatorRunsTypeScriptAndPythonTogetherWithoutOverwritingEachOther`（同一次 run 两 backend 在场、双向目录内容不串） | `./gradlew.bat :common:test --tests com.tkisor.nekojs.probe.PythonDeclarationAttributionTest --rerun-tasks` | PASS 5/5。限制：未执行游戏内命令层。 |
| F9 目录去重保护被行使 | `coordinatorRejectsTwoBackendsSharingOneOutputDirectory`（第二者被跳过 + 第一者产物存活） | 同上 | PASS；`duplicate output directory` 保护真实触发。 |
| AC5 自动失效 | `changedPythonDependencyIsObservedWithoutAnExplicitInvalidate`（无显式 invalidate）；红侧：`refreshPreparedExecutionTree` 提前 return → 该用例 FAILED（已回滚） | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest --rerun-tasks` | PASS；显式与自动两条失效路径现在都有独立证据。 |
| 综合门禁 | common 全量测试、common check、平台编译、whitespace | `./gradlew.bat :common:test --rerun-tasks`; `./gradlew.bat :common:check`; `./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava`; `git diff --check` | PASS：`:common:test --rerun-tasks` = **1736 tests, 0 failed, 4 skipped**（较上一轮 +11：round-1 新增 3 条用例，其余为同分支其他票的测试）；`:common:check` 与两个平台编译 BUILD SUCCESSFUL；`git diff --check` 对本轮 3 个文件干净（唯一命中是**其他票**的 `2026-09-19-ci-processor-gate/evidence/gates-selftest.txt` 尾随空格，非本票改动）。**golden 未更新。** |

### 本轮执行证据（命令与实际结果）

```text
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonRuntimeTest --rerun-tasks
  → 15 tests, 0 failed
./gradlew.bat :common:test --tests com.tkisor.nekojs.probe.PythonDeclarationAttributionTest --rerun-tasks
  → 5 tests, 0 failed
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoPythonPrepareTest --rerun-tasks
  → 10 tests, 0 failed
./gradlew.bat :common:test --rerun-tasks
  → 1736 tests completed, 0 failed, 4 skipped
./gradlew.bat :common:check
./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava
  → BUILD SUCCESSFUL
git diff --check
  → 本轮文件干净
```

红侧证据（两处临时改动均已回滚，main 源码与 `07bd32c4` 一致）：

```text
1) NekoEsmLinker.link catch: NekoModuleError.link → NekoModuleError.execute
   → generatedJsLinkFailureIsDistinguishedFromPythonSourceAndExecutionErrors FAILED
     (Expected NekoModuleError[LINK/Module Resolution/Cache] ...); 15 tests, 1 failed
2) NekoScriptModuleLoaderHost.refreshPreparedExecutionTree: 开头加 if (true) return;
   → changedPythonDependencyIsObservedWithoutAnExplicitInvalidate FAILED; 15 tests, 1 failed
```

### 本轮未改 main 源码

本轮所有临时红侧改动（`NekoEsmLinker` 的 catch 归类、`refreshPreparedExecutionTree`
提前 return）都已回滚，`git diff -- common/src/main/java` 为空；main 源码与上一轮提交
`07bd32c4` 一致。本轮提交只含测试与本文档。
