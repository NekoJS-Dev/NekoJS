# 2026-09-22 diagnostics baseline (ticket 30)

Worktree `D:\mcmodDemo\NekoJS-mult-t30`, branch `ticket-30-diagnostics`（基于 mult@5cb86e2e）。
交付票 30 的 frozen diagnostic record、阶段/owner 归因、generation 边界、非 GUI
source-path/action seam、telemetry 边界与 workspace 保留证据。

## 1. What changed

### Frozen diagnostic record（核心）

- `common/src/main/java/com/tkisor/nekojs/core/error/ScriptDiagnosticRecord.java`（新）：
  不可变 record——`errorId`、`scriptType`、`phase`、`owner`、`generation`、
  `recordedDuringCandidate`、`sourcePath`（authored/映射后原始源，无定位为 null）、
  `line/column`、`moduleIdentity`（执行中模块身份）、`cacheRevision`（prepared 模块
  cache key）、`message`（有界）、`cause`（有界单行摘要，无堆栈/异常对象身份）。
  投影方法：`toErrorSummary(count, displayPath, fullDetails)`（DTO 六字段单点组装）、
  `openAction()`（无定位返回 null）、`describe()`（稳定英文归因行）、
  `ofReloadFailure(NekoReloadException)`（reload 失败投影，不改 reload 异常流）。
- `DiagnosticPhase.java`（新）：PREPARE / RESOLVE_LINK / CACHE / EXECUTION / TRUST /
  RELOAD_CANCEL / WATCHDOG。
- `ScriptDiagnostics.java`（新，包内）：cause 链分类器——staged `NekoModuleError` 优先
  （stage→phase，`OWNER_PACK_TRUST`→TRUST），裸/包装 `NekoEsmLinkException`→RESOLVE_LINK，
  `NekoReloadException`→RELOAD_CANCEL；Polyglot `isCancelled/isInterrupted/
  isResourceExhausted`→WATCHDOG，`isSyntaxError`→PREPARE，其余→EXECUTION；host 侧
  preflight 校验器错误→PREPARE。`MESSAGE_BOUND=400`、`CAUSE_BOUND=240`。

### 生产者与 generation 归因

- `ScriptError`：创建时冻结 `diagnostic()`（解析完成后一次构建）；`ErrorSignature` 增加
  `displayPath`（映射前 generated 路径）与 `hasLocation`；既有中文正文/去重/片段逻辑不变。
- `DefaultErrorTracker`：`ModuleViews` 携带 `generation/candidate`；新增带 generation 的
  `activateModuleViews`/`activateCandidateModuleViews` 重载（旧重载委托，generation=-1）；
  Context 绑定与类型回退发布两个不同实例（保持既有"distinct object"语义）；
  `diagnostics()` 输出公开错误集的 record 快照；回调错误日志行追加 `describe()`。
- `ScriptManager`：kill 重建（`this.generation + 1`）、候选创建（candidateGeneration）、
  commit、commit-rollback、discardCandidate 五个发布点传入代际号。
- `SourceMapRegistry`：新增 `revisions` 侧表（`register(path, json, prepend, cacheKey)`），
  `mappedCacheRevision(path)` 查询；clear/clearByPathPrefix/clearByScriptType/容量上限同步
  清理。`NekoModulePipelineCache.publishSourceMap` 注册时携带 `prepared.cacheKey()`。
  `NekoSourceMapView.mappedCacheRevision` 为 default null（不破坏 lambda 实现）。

### 投影消费（同一 record）

- 日志历史：`DefaultErrorTracker.recordCallbackError` 与 `ScriptExecutor.executeEntry` 的
  error 日志在既有 detail 文本后追加 `diagnostic().describe()`（英文稳定行）。
- Dashboard packet：`NekoJSCommands.errorSnapshot`（共享模板 `src/` + 1.21.1 孪生
  `versions/1.21.1/src/.../NekoJSCommands.java`）改经 `toErrorSummary` 组装；wire 六字段、
  legacy display path、`fullDetails` 过渡快照语义全部不变（`PayloadWireFormatGoldenTest` 绿）。
- 用户报告/telemetry 入口：`NekoRuntimeRoot.ErrorSnapshot.records()`。
- 非 GUI workspace seam：`DiagnosticOpenAction`（`action= open-source`、type/owner/
  generation/source/line/column；`payload()` 单行 key=value 带引号转义，`parse()` 严格解析
  且拒绝畸形输入）。票 27 消费该 record 实现外部打开与只读 GUI；本票零 GUI/进程分派。

### Telemetry 与 workspace 保留

- `JavaClassLoadTelemetryRecorder`（新）：去重计数、`MAX_ENTRIES=8192` 上限最旧淘汰、
  `snapshot()` 可重复；隐私口径写在 `JavaClassLoadTelemetrySink`/`Recorder`/
  `JavaClassLoadTelemetry` Javadoc——含 ScriptType/脚本 authored id/引擎类名/allow 位，
  不含脚本内容、绝对用户路径、环境信息；无 sink 即不采集（可关闭/可降级）。
- `WorkspaceGenerator.writeConfigIfMissing`（script 目录与 `.neko_probe` 两处统一）：
  仅缺失才写；reload/验证/迁移不覆盖用户编辑。

### 有意不改

- `ErrorSummaryDTO`/`ShowErrorListPacket` wire 形状与 `fullDetails` 内容语义（编辑器删除
  文档 §3 的过渡快照证据，不是本票 record）。
- `LocalErrorSource`/`ErrorOpenService`/`VsCodeProcessOpener`（GUI 打开链路归票 27）。
- 既有中文错误正文、日志里程碑节流、候选错误隐藏/发布语义。
- 零 golden/probe 产物改动；零 gameplay 事件域文件；零 AGENTS/CONTEXT/ADR/README 改动。

## 2. Red → green evidence

见 `command-output/red-compile-before-implementation.txt`（先写两测：
`ScriptDiagnosticGenerationTest`——generation mis-attribution guard；
`ScriptDiagnosticRecordTest.mappedExecutionErrorKeepsOriginalSourceModuleIdentityAndCacheRevision`
——source-map phase mapping。实现前 `:common:compileTestJava` 56 个编译错=红）与
`command-output/green-run.txt` / `verification-commands.txt`（实现后绿）。

Guard 语义（三测）：active 记录冻结自身 generation（5）→ 候选（6/7）staged 错误不进公开
集合 → discard 后旧记录仍 generation=5 → commit 失败 rollback `restoreType` 恢复旧
generation → 成功 commit 发布的错误携带已提交 generation=9 且保留 candidate 观察标记。

## 3. Phase / source-map matrix

| 语言 | prepare（syntax/transform） | resolve/link | cache | execution（回映射） | cache/revision |
|---|---|---|---|---|---|
| JS（入口） | eval 期 `isSyntaxError`→PREPARE（`syntaxErrorPolyglotClassifiesAsPrepare`） | — | — | EXECUTION，sourcePath=authored `.js`，行=throw 行（`jsEntryRuntimeFailure…`） | prepared cacheKey 保留（identity map 经同一注册表） |
| CJS（require 子模块） | 同 JS | — | — | EXECUTION，sourcePath=**子模块** authored 路径（`cjsChildRuntimeFailure…`） | 同上 |
| ESM（.mjs） | — | RESOLVE_LINK，specifier 模块身份（`unresolvedEsmImport…`；link 诊断行列见 `resolveAndLinkErrors…`） | — | EXECUTION | — |
| TS | PREPARE，authored 行列（枚举非法初始化，`tsSyntaxFailure…`） | 同 ESM | — | EXECUTION，erasure 保 authored 位置（`tsAndTsx…`） | cacheKey 保留（`tsErasureDiagnostics…`） |
| TSX | PREPARE（`jsxAndTsxTransformFailures…`） | 同 ESM | — | EXECUTION，authored `.tsx` 行（`tsAndTsx…`） | 同 TS |
| JSX | PREPARE（同上） | 同 ESM | — | EXECUTION，authored `.jsx` 行（`jsxRuntimeFailure…`） | cacheKey 保留 |
| Python | PREPARE，authored `.py`（`pythonSyntaxFailure…`） | 同 ESM | — | EXECUTION，source map 回映射 authored `.py` 行（`pythonRuntimeFailure…`） | `preparedCacheRevisionIsRetained…` |
| trust | PREPARE 门禁→TRUST，owner=Pack Trust（`trustDenial…`） | — | — | — | — |
| cache stage | — | — | CACHE，owner=Module Resolution/Cache（`cacheStageError…`） | — | — |
| reload/cancel | — | — | — | — | `ofReloadFailure`→RELOAD_CANCEL，generation/source/domain 来自 `ReloadFailureReport` |
| watchdog | — | — | — | `isCancelled/isInterrupted/isResourceExhausted`→WATCHDOG（真实 `SyncEvalWatchdog` 取消求值，`cancelledEvaluation…`） | — |

映射链路合成用例：`ScriptDiagnosticRecordTest.mappedExecutionError…`——generated
`gen.js` 行 2 经注册 source map 回映射 `authored.ts` 行 5，moduleIdentity=`gen.js`，
cacheRevision=注册的 prepared cache key。

已知语言面事实（非缺口）：TS/TSX/JSX 纯擦除不产出独立 source map（authored 位置由擦除
保行 + staged EXECUTE 归因保留）；Python 产出真实 source map 并带 revision 查询。

## 4. Verification commands and results

见 `command-output/verification-commands.txt`（全部真跑，Windows 本机）：

- `:common:check` → BUILD SUCCESSFUL；全量 `:common:test --rerun-tasks` → 1867 tests,
  0 failures, 4 skipped。
- `:1.21.1:test :26.1.2:test :26.2.0:test` → BUILD SUCCESSFUL。
- `guardLint`、三节点 `compileJava` → BUILD SUCCESSFUL。
- 首轮 `:common:check` 的两处失败与处置见 verification-commands.txt 尾注（一处真实回归
  已修；一处 Ticket07 close-抢占竞态 flake 复跑绿）。

## 5. Gaps / owners

- **G1（AC7）**：`ScriptExecutor.waitForEvaluation` 求值超时消息含既有配置指引
  （「可在 nekojs/config/engine.toml 中调整 scriptEvaluationTimeoutSeconds」，票 07 交付
  文本）。普通错误路径无 offline validator/migration 引用（检索 0 命中）；该指引是否属
  "修复提示"需维护者裁定后决定是否剥离。owner：维护者。
- **G2（code-gap）**：`wiki/en_us/Error-Reference.md` NEKO- 码注册表在本分支不存在；
  本票零新问题报告码（describe()/payload() 为稳定字段文本，不是 NEKO- 码）。合并到有
  注册表的分支时统一登记。owner：主会话/合并门。
- **G3**：`:26.1.2-fabric:test`/`:26.2.0-fabric:test` 未跑（本票未触 fabric 专属文件；
  Fabric 错误聊天降级消费同一 DTO/record 链路）。owner：合并门/票 34。
- **G4**：probe/declaration 金样未派生收录 record 字段（declaration 面归 09/33/34 的
  managed surface 流程；本票非 GUI seam 已可断言全部公开字段）。
- **G5**：真实外部 IDE 打开接线与只读报告 GUI 由票 27 消费 `DiagnosticOpenAction`
  实现；跨域真实集成归票 34。本票未实现任何 GUI 行为。

## 主会话复核修正（2026-09-22，合并后）

- packet 内容等价回退（复核发现未授权的用户可见变更）：`toErrorSummary` 初版用 record 的
  400 字符压平 message 并对空 displayPath 填 "Unknown location"——wire 形状虽未变、
  内容变了。已改为 legacy 透传：新增 `legacyMessage` 参数，两个 `NekoJSCommands`
  （模板 + 1.21.1 孪生）传 `err.getErrorMessage()`，displayPath 原样透传；record 自身的
  有界 message 仅用于 `describe()` 日志/报告行。
- 冻结保护用例：`recordIsFrozenAgainstLaterRegistryAndOccurrenceMutations`——映射替换与
  occurrence 递增后 record 字段不变、`diagnostic()` 恒返同一冻结实例。
- `JavaClassLoadTelemetryRecorder` 删除内容盲 `equals/hashCode`（无值语义消费者）。
- `DefaultErrorTracker.diagnostics()`/`ErrorSnapshot.records()` 收敛为单行 stream 投影，
  FQN 改 import；两处 javadoc 更正「telemetry 并非本 record 投影」（独立有界通道）。
- `SourceMapRegistry` 三个定向清理（clear(path)/clearByPathPrefix/clearByScriptType）的
  revision 侧表改为随映射条目同删（`removeMappingsWithRevisions`）——此前别名/generated-path
  匹配删除映射时对应 revision 残留，可能给无映射路径附上过期 cache revision。
- `WorkspaceGenerator.writeConfigIfMissing` 首次生成补 INFO 日志（生成位置 + 用户后续
  编辑保留语义）。
- 已知缺口补记：`ScriptErrorReporter` 未直接改动（经 ErrorTrackerReporter 传递统一）；
  seam 的生产消费方为票 27；NEKO- 码表不在本分支。
