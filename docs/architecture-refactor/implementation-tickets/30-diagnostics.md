# 30: 错误诊断、telemetry、workspace 与用户报告链路

**What to build:** 脚本语法/转换、模块 resolve/link、Graal 执行、reload/cancel、trust 和 watchdog 错误进入统一 frozen diagnostic record，保留阶段、owner、generation、ScriptType、模块身份和 source map 后的原始位置；本票拥有 record 生产者、公开字段契约、日志/报告/包投影与既有非 GUI 投影 contract fixture。本票的 workspace 指外部 IDE workspace/declaration/Probe 生成与定位；error dashboard 是只读报告，不是游戏内编辑器。最终 GUI/外部 workspace 打开接线由票 27 拥有，最终跨域真实集成由 P4 汇总；本票不等待 GUI 完成后才关闭。

**Blocked by:** [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)、[13: Python 转译、模块行为与诊断路径](13-language-py.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)、[19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md)

**Status:** in-review（实现/测试/证据已交付；AC7 部分满足未勾选，AC3 备注过渡证据边界；证据见 `baseline/2026-09-22-diagnostics/`）

**Assignee:** zed-flash-30（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-22):** worktree `../NekoJS-mult-t30` on branch `ticket-30-diagnostics`（基于 mult HEAD）。预计改动范围：ErrorTracker/ScriptError/ErrorSummaryDTO/ScriptErrorReporter 的错误 ID/阶段/owner/source/generation/ScriptType/模块身份字段统一为 frozen diagnostic record、JS/CJS/ESM/TS/JSX/TSX/Python 的 source-map 与阶段矩阵、日志/报告/packet/workspace 投影消费同一 record 的非 GUI contract fixture、JavaClassLoadTelemetry/watchdog 可观察语义与隐私边界、reload 边界（旧错误历史归属/候选错误不伪装）、offline validator 保持显式独立、`baseline/2026-09-22-diagnostics/` 证据。不修改 24 gameplay 事件域文件；error dashboard 最终 GUI/外部打开接线归票 27；删除旧旁路仅在替代/trace/无调用者证据后且公开诊断与外部 workspace 功能不删除。

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** agent 可以实现、测试并整理证据；涉及删除旧公开资源/诊断路径或最终 golden/发布确认的维护者 sign-off 不能由 agent 代答，未经 sign-off 不得删除旧路径或勾选对应删除验收项。

**Implementation record (2026-09-22, zed-flash-30):**

- 交付：`ScriptDiagnosticRecord`（frozen record：errorId/ScriptType/phase/owner/generation/
  candidate 标记/sourcePath/line/column/moduleIdentity/cacheRevision/message/有界 cause）、
  `DiagnosticPhase`、`ScriptDiagnostics`（阶段分类器）、`DiagnosticOpenAction`（非 GUI
  source-path/action record seam，票 27 消费）；`ScriptError.diagnostic()` 创建时冻结一次，
  频次等可变态留在既有 getter。generation 归因：`DefaultErrorTracker.ModuleViews` 携带
  generation/candidate，`ScriptManager` 在 kill 重建/候选创建/commit/rollback/discard 五个
  发布点传入代际号；`SourceMapRegistry` 新增 revision 侧表（prepared cacheKey 随
  `publishSourceMap` 注册），`NekoSourceMapView.mappedCacheRevision` 缺省 null。telemetry：
  `JavaClassLoadTelemetryRecorder`（去重/上限/可重复快照）+ 隐私口径 Javadoc。
  workspace：`WorkspaceGenerator.writeConfigIfMissing` 统一"仅缺失才写"。DTO wire 六字段
  与 `fullDetails` 过渡快照语义零变化（编辑器删除文档 §3）。
- 红→绿（baseline `../baseline/2026-09-22-diagnostics/command-output/red-compile-before-implementation.txt`
  与 `green-run.txt`）：generation mis-attribution guard（旧 active 历史 generation 不被候选
  改写/rollback 恢复）与 source-map phase mapping（gen.js→authored.ts 映射保留
  moduleIdentity+cacheRevision）两测先行红（56 编译错），实现后绿。
- 验证（真跑，摘录见 command-output/）：`:common:check` BUILD SUCCESSFUL（含隔离检查；
  首轮两处失败为 ModuleViews 同实例回归[已修]与 Ticket07 close-抢占计时 flake[复跑绿]）；
  `:1.21.1:test`/`:26.1.2:test`/`:26.2.0:test` BUILD SUCCESSFUL；`guardLint` 通过；
  `:1.21.1:compileJava`/`:26.1.2:compileJava`/`:26.2.0:compileJava` 通过。本机 Windows
  结果，不代表其他平台/CI/release。
- 遗留与 owner（REPORT §5）：AC7 超时配置指引文本裁定→维护者；`Error-Reference.md`
  NEKO- 码注册表在本分支不存在（本票零新码，code-gap 已记录）；Fabric 节点测试未跑
  （本票未触 fabric 专属文件）；declaration/golden 派生字段投影（probe 声明收录 record
  字段）不属本票非 GUI seam 范围——owner 09/33/34。

**Work items:**

- 统一 ErrorTracker/ScriptError/ErrorSummaryDTO/ScriptErrorReporter 的错误 ID、阶段、owner、source、generation、ScriptType、module identity、cause 和用户可见字段投影。
- 为 JS/CJS/ESM、TS/JSX/TSX、Python 的准备、resolve/link、缓存、执行和 reload/trust/watchdog 错误建立 source-map 与阶段矩阵。
- 让日志历史、dashboard packet、外部 IDE workspace source-path/action record seam、用户报告和 telemetry 消费同一 frozen diagnostic record；本票交付非 GUI record contract fixture，最终外部打开/只读屏幕接线由票 27 与 P4 验收。
- 保留 JavaClassLoadTelemetry 与 runaway watchdog 的既有可观察语义，补充隐私/体积边界和可重复采集口径。
- 验证 reload boundary：candidate 失败保留 active、generation/owner可读、旧错误历史不误归属新代际；外部副作用边界显式可见。
- 把可选 offline validator/migration report 保持为显式、默认只读的离线产物，不进入普通错误路径或 release 硬 gate。
- 收缩 gate：旧错误 tracker/report/dashboard旁路只有在替代 behavior、declaration/字段投影、trace 与无调用者证据通过后移除；公开诊断和外部 IDE workspace功能不删除，清理随本票完成而不是 final release 统一处理。“workspace 功能不删除”不保留内置编辑器、workspace GUI 或编辑文件同步。

## Acceptance criteria

- [x] syntax/transform、resolve/link、cache、runtime、reload/cancel、trust 和 watchdog representative 错误均有正确阶段和 owner。【`ScriptDiagnosticRecord`/`DiagnosticPhase`/`ScriptDiagnostics`（`common/src/main/java/com/tkisor/nekojs/core/error/`）；`ScriptDiagnosticRecordTest` 逐类覆盖 prepare（含 guest 语法错误归 PREPARE）、trust（`NekoModuleError.denied`）、resolve/link、cache、watchdog（真实 `SyncEvalWatchdog` 取消求值）与 reload/cancel（`ofReloadFailure`）；`DiagnosticPhaseMatrixTest` 经真实管线（`NekoModulePipelineCache.prepare` / `loadEntry`）验证 ts/py/jsx/tsx 语法、ESM resolve 与 execution 阶段归属；红→绿证据见 `baseline/2026-09-22-diagnostics/command-output/red-compile-before-implementation.txt`】
- [x] JS、CJS、ESM、TS、JSX、TSX、Python 的错误位置能回映射到原始 source，并保留模块身份、行列和 cache/revision 信息。【`DiagnosticPhaseMatrixTest`：`jsEntryRuntimeFailure…`、`cjsChildRuntimeFailure…`（require 子模块身份）、`unresolvedEsmImport…`、`tsAndTsxRuntimeFailures…`、`jsxRuntimeFailureMapsBackToAuthoredJsxSource`、`pythonRuntimeFailureMapsBackToAuthoredPythonSource`、`preparedCacheRevisionIsRetainedOnMappedDiagnostics`（prepared cacheKey 随 source map 注册并在 record 保留）；合成映射链路 `ScriptDiagnosticRecordTest.mappedExecutionErrorKeepsOriginalSourceModuleIdentityAndCacheRevision`（gen.js→authored.ts 映射 + moduleIdentity + cacheRevision）】
- [x] 同一错误在日志、ErrorSummaryDTO、packet/record projection、用户报告和现有外部 IDE workspace/declaration fixture 中呈现一致核心字段；本票不要求改完票 27 的最终屏幕，也不用私有 GUI 对象当契约。现状 `fullDetails` 快照可作为编辑器删除过渡证据，但不是本票目标 record。【字段统一由 frozen record 单点投影：日志历史在 `DefaultErrorTracker.recordCallbackError`/`ScriptExecutor.executeEntry` 追加 `diagnostic().describe()`（既有中文正文不变）；`ErrorSummaryDTO` 六字段经 `ScriptDiagnosticRecord.toErrorSummary` 组装（`NekoJSCommands.errorSnapshot` 模板+1.21.1 孪生改走该方法，wire 形状不变、`PayloadWireFormatGoldenTest` 绿）；用户报告/telemetry 消费 `NekoRuntimeRoot.ErrorSnapshot.records()`/`DefaultErrorTracker.diagnostics()`；workspace/declaration fixture 不受影响（`WorkspaceGeneratorPreserveTest`、probe 测试绿）。`fullDetails` 按编辑器删除文档 §3 保留为过渡快照，未伪装成本票 record——record 经 `ScriptError.diagnostic()` 公开；主会话复核修正：packet 内容与旧组装逐字节等价（原消息不截断、displayPath 原样透传——初版曾对 message 做 400 字符压平并对空 displayPath 填 "Unknown location"，属未授权的用户可见变更，已回退为 legacy 透传，`toErrorSummary` 增加 legacyMessage 参数）；telemetry 并非本 record 的投影（独立有界通道，javadoc 已更正）；`ScriptErrorReporter` 经 ErrorTrackerReporter 传递统一，未直接改动（记 REPORT 缺口）】
- [x] diagnostics owner 通过非 GUI seam 输出可解析、可定位的 source path/action record；generation、owner、ScriptType、source path、行列和 action payload 人类可读且可被 contract fixture 断言。实际外部 IDE 打开与只读报告 GUI 动作由票 27 消费该 record 实现，本票不提前实现或私有化 GUI 打开行为。【`DiagnosticOpenAction`（`action/type/owner/generation/source/line/column`，`payload()` 单行 key=value、带引号转义，`parse()` 严格解析）；`ScriptError.diagnostic().openAction()` 对无定位错误返回 null；round-trip 与字段断言见 `ScriptDiagnosticRecordTest.openActionPayloadIsParseableAndRoundTrips`/`unknownSourcePathYieldsNoOpenAction`；本票未新增任何 GUI/进程分派行为（`LocalErrorSource`/`ErrorOpenService` 未改语义）；主会话复核注记：seam 的生产消费方是票 27 的 GUI/外部打开接线（其 blocker 含本票），当前证据形态为非 GUI contract fixture + 冻结 record API；另补冻结保护用例 `recordIsFrozenAgainstLaterRegistryAndOccurrenceMutations`（映射/occurrence 外部变更后 record 字段不变、`diagnostic()` 恒返同一冻结实例）】
- [x] 在 RELOAD_COMMIT 后的 candidate/active 状态中验证失败、取消、watchdog 终止与恢复：旧 active 错误历史不丢失，新候选错误不伪装成 active generation；旧 fixture只能作对照。【`ScriptDiagnosticGenerationTest` 三测：候选失败后旧记录 generation 保持（mis-attribution guard，红→绿）；commit 失败 rollback `restoreType` 恢复旧 generation；commit 发布候选错误携带已提交 generation+candidate 标记；既有 `DefaultErrorTrackerTest.candidateErrors…`/`failedCandidateErrors…` 与 `Ticket07RuntimeThreadsTest`（close 抢占/watchdog 面）在 `:common:check` 全绿作对照】
- [x] telemetry/Java class-load/watchdog 记录可重复采集、可关闭或降级，并写明是否包含用户路径、脚本内容或环境信息。【`JavaClassLoadTelemetryRecorder`（去重计数、8192 上限、最旧淘汰、可重复 `snapshot()`）+ `JavaClassLoadTelemetryRecorderTest` 5 测（含 no-sink 即禁用、null 重装禁用）；隐私口径写在 `JavaClassLoadTelemetrySink`/`Recorder`/`JavaClassLoadTelemetry` Javadoc：含 ScriptType、脚本 authored id、引擎类名与 allow 位；不含脚本内容、绝对用户路径、环境信息；watchdog 可观察语义未改（票 07 测试全绿）】
- [ ] 普通 runtime 错误文本不包含 offline validator、migration report 或修复提示；辅助工具只能显式独立运行。**部分满足**：全仓检索确认普通错误路径没有 offline validator/migration report 引用（`common/src/main` 的 error/script 包 0 命中），辅助工具无隐式运行；但 `ScriptExecutor.waitForEvaluation` 的求值超时消息含既有配置指引「可在 nekojs/config/engine.toml 中调整 scriptEvaluationTimeoutSeconds」（票 07 交付的既有文本，是否属"修复提示"需维护者裁定；本票未改该用户可见文本以避免越权行为变更）——owner：维护者 sign-off，见 REPORT §5】
- [x] 历史日志与用户在外部 IDE 编辑的 workspace config/declaration 在验证和迁移中不被覆盖或删除。【`WorkspaceGenerator.writeConfigIfMissing`（script 目录与 `.neko_probe` 两处统一"仅缺失才写"）+ `WorkspaceGeneratorPreserveTest`（首写后不再覆盖、用户字节原样保留）；reload/验证路径无日志文件删除调用（巡检 + 票 07 reload 测试绿）；declaration/probe 产物本票零改动】
- [x] 诊断 golden 只冻结公开字段和用户可见语义，不冻结 UI私有对象、布局或私有异常对象身份。【本票零 golden 更新（git diff 无 golden/probe 产物）；`ShowErrorListPacket` wire 六字段不变、`PayloadWireFormatGoldenTest` 绿；record 只携带公开字段与有界 cause 摘要（`ScriptDiagnostics.CAUSE_BOUND`），不含堆栈或异常对象身份】
- [x] 本票关闭范围是 frozen record、生产者和 contract fixture；票 27 负责最终只读 GUI 与外部 workspace 打开接线，票 34 负责跨域真实集成。旧投影/报告旁路仅在替代 behavior、declaration/字段投影、trace 通过且无调用者后移除，公开诊断和外部 IDE workspace功能不删除，清理不推迟 final release。【record+生产者（`ScriptError.diagnostic()`，创建时冻结）+contract fixture（上述测试）已交付；GUI/外部打开零实现（归票 27）；trace：`ErrorSummaryDTO` 生产者全仓仅 `NekoJSCommands.errorSnapshot`（模板+1.21.1 孪生）与 record `toErrorSummary`——无静态全局旁路、无第二事实源（`ScriptErrorReporter` 门面→`ErrorTrackerReporter`→`DefaultErrorTracker` 单链）；因此无可移除的无调用者旁路，公开诊断与外部 workspace 功能零删除】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [内置游戏内编辑器移除与只读报错 UI 规划覆盖](../editor-removal-and-error-ui.md)
- [实现票据索引](README.md)

## Dependency rationale

- [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md): 完整验收必须包含 TS/JSX/TSX 的原始 source-map、cache revision与阶段映射；仅用 LANGUAGE_PIPELINE 的 JS 基线不能代表全语言诊断。
- [13: Python 转译、模块行为与诊断路径](13-language-py.md): 完整验收必须包含 Python 的原始 source-map、cache revision与阶段映射；仅用 LANGUAGE_PIPELINE 的 JS 基线不能代表全语言诊断。
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md): 诊断验收包括 active watchdog 隔离、取消及关闭状态，不能用旧调度状态代替新结果。
- [19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md): trust representative 错误的阶段、owner、结果对象和用户可见字段必须基于最终 pack trust 链路，不能按旧 trust 行为提前关闭后回填。

## Scope and coordination

**Rationale:** 诊断是语言、事件、查询和 runtime 的共同用户可见结果；该票以统一上下文和投影一致性为边界，既不接管网络/client实现，也不把工具提示混入普通错误。

**Coordination:**

- RUNTIME_ROOT/RELOAD_COMMIT: active/candidate 保留、generation 切换和 watchdog 恢复语义由 runtime 组提供，本票负责诊断投影一致性。
- GLOBAL_STATE: 报告不得通过静态全局旁路读取错误状态。
- MANAGED_SURFACE: 诊断公开字段/错误码进入 managed contract，Probe/declaration只作投影。
- EVENT_SURFACE/RECIPE_DATA_SURFACE/GAMEPLAY_EVENT_SURFACE/QUERY_TOOLS: 各域错误必须回填阶段和 owner，不用私有异常绕过统一链路。
- CLIENT_GUI_RENDER/P4: 票 27 消费本票 record 完成最终只读 GUI；外部 IDE workspace 打开与错误报告职责分开，真实跨域集成由票 34 汇总，30 与 27 不形成互相等待。编辑器删除不是本票 record 设计的前置条件。
- PERF_BASELINE: telemetry采集成本有基线对照，不自行设定发布阻断阈值。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Follow-up record（2026-09-25）

**执行者：** ticket-30 follow-up subagent（mult 分支本机复核；零源码改动）。

**范围：** 已勾选 AC（AC1/AC2/AC3/AC4/AC5/AC6/AC8/AC9）回归复核；AC7 未勾选态维持并补全检索证据；Closure 本机可跑门禁（`guardLint`、三 NeoForge 节点编译/测试、`:common:check` 含隔离检查）。写集自查：`git status` 下本票写集零修改；工作区他票改动（AGENTS.md/CONTEXT.md/ADR/domain/README/票 26 的 `KeyBindEventsTest` 及其基线）一律未碰。

**命令与结果（全真跑，Windows 本机，不代表其他平台/CI/release）：**

- focused 五测 BUILD SUCCESSFUL（34 tests，0 失败）：`ScriptDiagnosticRecordTest` 12、`DiagnosticPhaseMatrixTest` 12、`ScriptDiagnosticGenerationTest` 3、`WorkspaceGeneratorPreserveTest` 2、`JavaClassLoadTelemetryRecorderTest` 5。
- 对照绿：`DefaultErrorTrackerTest` + `SourceMapRegistryTest`、`Ticket07RuntimeThreadsTest`（`com.tkisor.nekojs.script` 包；一次误用 `core.lifecycle` 包过滤得 "No tests found"，系过滤器笔误非产品失败，更正后绿）。
- wire/只读契约绿（零 golden 更新，golden 只读）：`:1.21.1:test` 下 `PayloadWireFormatGoldenTest`、`ShowErrorListPacketReadOnlyContractTest`、`DashboardViewTest`。
- `guardLint` BUILD SUCCESSFUL；`:1.21.1:compileJava`/`:26.1.2:compileJava`/`:26.2.0:compileJava` BUILD SUCCESSFUL。
- 收尾全量：`:common:check --rerun-tasks` BUILD SUCCESSFUL（19/19 executed；TOTAL 1930 tests，0 failures/errors，4 skipped，含隔离检查）；`:1.21.1:test`/`:26.1.2:test`/`:26.2.0:test --rerun-tasks` BUILD SUCCESSFUL（49/49 executed）。
- 完整命令/计数见 `baseline/2026-09-22-diagnostics/followup-2026-09-25-verification-commands.txt`；AC7 检索 0 命中证据见同目录 `followup-2026-09-25-ac7-search.txt`（本机无 `rg`，以工作区内容检索执行，模式与根目录逐条记录）。

**AC 结论要点：**

- AC1/AC2：frozen record 与阶段矩阵无退化（focused 24 测 + 全量门禁绿）。
- AC3：`toErrorSummary` 四参 legacy 透传在模板与 1.21.1 孪生一致；`summaryDtoProjectionCarriesTheFrozenCoreFields` 逐字段断言等价；`fullDetails` 仍为过渡快照；record 经 `ScriptError.diagnostic()` 公开；telemetry 为独立有界通道（Javadoc 口径已更正）；`ScriptErrorReporter` 未直接改动（单链经 `ErrorTrackerReporter`→`DefaultErrorTracker`）。
- AC4/AC5/AC6/AC8/AC9：generation 归因三测、telemetry 边界五测、workspace 写保护两测、wire golden 均绿；无新增 NEKO- 码（error 包 + telemetry 类 0 命中）；`wiki/en_us/Error-Reference.md` 在 mult 工作区现已存在，基线 G2“分支内缺注册表”已过时，但本票零新码、无需登记动作。
- AC7（保持未勾选）：普通错误路径无 offline validator/migration report 引用（7 组检索 0 命中，error 包内仅 host 侧 preflight 提述）；`ScriptExecutor.waitForEvaluation` 超时文本零改动。

**未覆盖项与 owner：** Fabric 节点测试 not run（本票/本次零 fabric 专属文件改动；owner 合并门/票 34）；`runGameTestServer`/真机 smoke、probe 类型检查 not run（零声明改动）；declaration 派生收录 record 字段归 09/33/34；最终 GUI/外部打开归票 27、跨域集成归票 34。

**Sign-off 事项（需维护者裁定，agent 不代答）：** `ScriptExecutor.java:175-176` 超时消息中的配置指引「可在 nekojs/config/engine.toml 中调整 scriptEvaluationTimeoutSeconds」是否属于 AC7 的“修复提示”，以及剥离与否归谁。AC7 勾选与任何旧旁路删除仍需该 sign-off。

## JSX UI feature coordination（2026-09-12）

[42: JSX generation/reload/诊断接线](42-jsx-ui-generation-reload-cleanup.md) 消费本票统一 diagnostic record，补充 UI phase/root/generation 归因，不建立 UI 私有错误事实源。本票不反向依赖 JSX feature。
