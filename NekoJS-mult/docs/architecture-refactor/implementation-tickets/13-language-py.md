# 13: Python 转译、模块行为与诊断路径

**What to build:** 脚本作者写入 .py 后，既有 Python 语法、缩进结构、定义/调用、模块模式和 import 行为经 Preparation 生成可执行 JS/IR，再由 Resolution/Cache 与 Graal 执行；Python 源错误和生成代码运行错误均保留原始位置，缓存与声明不把 Python 误标为 JS。

**Blocked by:** [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)

**Status:** closed

**Assignee:** dsh-agent

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

## Closure record（2026-09-14）

- 执行者：dsh-agent。分支 `ticket-13-language-py`（基于 `integration/tickets-08-11-12`，含已关闭的票 11/12）。
  实施区间 `3cd907c3..本提交`（1 个语义化 commit：缺陷修复 + corpus/示例/证据 + 票据）。
- 交付物：
  - 源码（全部在 `common`）：`SourceMapRegistry`（新增包内映射覆盖查询 `hasSourceMapCoverage` /
    `coversGeneratedLine` + 内部 `NormalizedSourceMap.coversLine`）、`ScriptError`（`parseSignature`
    采纳位置前过 `isExceptionConstructionFrame`，命中则 `firstOriginFrame` 在本模块内取真实起源帧）。
  - 证据：[`baseline/2026-09-14-language-python/REPORT.md`](../baseline/2026-09-14-language-python/REPORT.md)
    （方法、缺陷裁决与根因、物理落点、收缩 gate、逐 AC 判定、未验证项）；
    [`MIGRATION.md`](../baseline/2026-09-14-language-python/MIGRATION.md)（模块契约/import 写法/诊断契约/子集边界）；
    [`minimal-example.py`](../baseline/2026-09-14-language-python/minimal-example.py) /
    [`minimal-example-entry.mjs`](../baseline/2026-09-14-language-python/minimal-example-entry.mjs)。
  - 测试（新增 4 个类 / 18 个用例）：`PythonModulePipelineCorpusTest`（5）、
    `PythonModuleErrorPhaseCorpusTest`（8）、`PythonMinimalExampleTest`（4）、
    `PythonDeclarationOwnershipTest`（1）。全部使用 `@AfterEach` + `deleteFilesAndEmptyParents`
    真删文件与变空父目录（沿用工单 11 的清理纪律）。
- **本票实测发现并修复的真实缺陷：`.py` 执行期错误的原始行错位。**
  现象：`raise` 在模块顶层或文件后部时，`ScriptError` 报出的行不是 raise 行——
  实测（修复前 → 修复后）：raise 在第 3 行 `4 → 3`；helper 内第 8 行 `2 → 8`；
  顶层第 3 行 `1 → 3`；用户异常类第 7 行 `1 → 7`；**TS 用户异常类第 4 行 `1 → 4`**（同一根因）。
  根因两段：① `PythonEmitter.emitRuntimeHelpers` 用 `line0(...)` 在正文前注入运行时 helper 与
  19 个异常 prelude 类 + `.forEach` 重命名行，这些行**不调用 `recordMapping`**，在 source map 里是空段；
  ② GraalJS 对自定义 `Error` 子类不捕获构造时调用栈，`class Exception extends Error {}` /
  `class ValueError extends Exception {}` 会成为**独立 guest 栈帧**排在真正 throw 之前，而
  `PolyglotException.getSourceLocation()` 与 `DefaultErrorTracker.getBestSourceLocation()` 都取
  **第一个 guest 帧** → 取到 prelude 行 → `getMappedPosition` 返回 `(path=null, line=产物行)`，
  产物行号被当原始行号使用，经行号对行号错映射。
  修复判据（`isExceptionConstructionFrame`）：① 产物**登记了带段 source map** 且该行无映射段 → 不可信
  （原生 JS 的 null map 仍按恒等映射处理，不回归）；② 该位置代码是 `class ...` 声明行。
  只在本模块内查找，保住票 11 AC7 的跨 import 归属语义。
  **证据**：修复前实测 `PROBE6 exceptionLoc=null`、
  `tracker.getBestSourceLocation()=[...line=4 text=class Exception extends Error {}]`；
  修复后全部用例命中真实行（见 REPORT §2）。
  **未采纳的候选**：让 Python 异常 prelude 自截栈（`Error.captureStackTrace`）——实测 GraalJS 不支持。
- 行为保持：Python 语言范围、缩进/def/class/注释/import 语义、模块模式分派、cache key 维度、
  source map 语义与 reload 触发方式全部不变；**既有 golden 零变更**（无需维护者审阅的旧/新 diff）。
  工单 11 的 `NekoPreparedModule` / `NekoModulePreparation` / cache key / 错误阶段模型只消费未重定义；
  原生 JS 的 `sourceMap()==null ＝ 恒等映射` 契约未变。
- 验收判定（据实）：**AC1–AC9 全部 pass**。逐条证据见 REPORT §6。
  关键补充：AC9 示例**被真实执行**（`PythonMinimalExampleTest` 把示例当脚本源跑 `host.loadEntry`，
  断言 `hello world|6|30|1|16`），示例与实现漂移即失败；AC7 用同一份共享 IR 分别交给
  `PythonProbeBackend` 与 `TypeScriptProbeBackend`，断言两条归属的产物类型/目录/相对路径集合互不重叠。
- 收缩 gate（Work item 6）：**不存在** Python 的旧并行转换或装载旁路。全仓 `grep` 后唯一生产入口
  是 `PythonTranspilerPlugin` → `registry.registerLanguage("python", …)` → `PythonToJsCompiler`；
  `src/` 零 Python 引用，`versions/` 只有 Fabric 的 `FabricPluginLoader.BUILTIN_PLUGINS` 清单
  （同一插件类）。本票**未删除任何代码**——不存在旁路，不为凑票据删无辜代码。
- 门禁（全量真跑，一次执行）：`:common:test --rerun-tasks --no-build-cache` →
  **211 suites / 1535 tests / 0 failures / 0 errors / 5 skipped**；`:common:check` pass；
  `guardLint` pass（248 守卫块 / 331 文件 / 0 问题）。
- 未验证项与 owner：① 真实 Minecraft 客户端/服务端 smoke（含 `/nekojs reload`）**not-verified**——
  本票改动全在 `common`，未启动真实客户端；owner 维护者 / 票 19 / 票 34；② 节点 `:26.1.2:compileJava`
  未单独执行——改动不触碰平台面，owner 维护者。
- 遗留（**非本票引入**的 v1 子集边界，已 characterization 而非隐藏）：顶层 `return`/`break`/`continue`/`yield`
  与「类体直接 `raise`」在准备阶段不拦截，加载期以硬 `SyntaxError` 可见失败（位置仍指原 `.py` 行、
  后续语句保留在产物里）。是否前移到准备阶段 fail closed 应由后续语言票结合作者反馈评估；
  owner 后续语言票 / 维护者。另：Python source map 的列粒度为语句级（`PythonSourceMap.build` 的
  `vlq(0)`），错误报告的列是语句起始列——断言按此粒度写，未编造精确列。
- 说明：本票不删除任何公开语言，未新增 Python runtime / 公共 parser SPI / Gradle project / 第二套模块管线。
