# 42: JSX UI CLIENT generation、reload、诊断与 cleanup 接线

**What to build:** 把 JSX UI root 纳入唯一 `NekoRuntimeRoot` 与 CLIENT generation 生命周期：candidate 中准备的 UI 计划和 root 对生产 Screen、输入路由不可见；commit 后旧 generation 停止接收事件并关闭旧 Screen；candidate 失败或取消只清理候选资源并保留 active UI。render/layout/event/host/resource 错误进入既有诊断 record，旧 handle 与过期 generation 明确失败。

**Blocked by:**
- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)

**Status:** closed（AC 全项勾选并附证据;唯一未合入真机腿的 26.2 JSX Screen 点击/关闭与 41 同源,按维护者 2026-09-29 授权按 23/27 先例移交 34 真机轮——inreview-digest 建议与 41 AC8/43 AC6/44 AC6 合并为一轮 26.2 真机 smoke）

**Assignee:** workbuddy-kimi-42（main-session agent；mult worktree）

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

**Claim record (2026-09-25):** 用户于本会话明确授权"领取一张票开始做"，作为本票实施的另行授权。按索引规则 2，本票 Blocked by 中 06/07 为 `closed`，30/40/41 为 `in-review`（实现已交付待维护者审阅），因此本认领偏离"先决票均 closed"的字面要求；偏差理由与票 46 先例一致：维护者直接指令，且本票消费的 06/07（candidate/commit/线程契约）、30（统一诊断 record）、40（UI common core/事件对象/host contract）、41（NeoForge host Adapter/JsxScreen）产物已在本分支源码中存在并可经测试验证。工作分支/worktree：`mult`。既有实现基线：`1136f30a` 已随 42/44/45/47 批次把本票的 `UiRootLifecycle`、`GenerationGlobals` UI 写集、`ScriptManager` 接线和 `Ticket42UiGenerationLifecycleTest` 交付到本分支但票据未记录；本轮对该基线做逐条 AC 验证、补缺与证据落盘。预计改动范围：`versions/26.2.0/src/main/java/com/tkisor/nekojs/client/ui/**`、`common/src/main/java/com/tkisor/nekojs/core/state/GenerationGlobals.java`、`common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java` 及对应测试；不触碰 golden、不改 30/40/41 票面、不扩张写集。

## Acceptance criteria

- [x] UI root 创建时绑定当前 CLIENT generation；不新增 UI global singleton、静态跨 generation registry 或第二 runtime owner。【evidence：`JsxHostAdapter` 构造 → `ScriptManager.registerUiRoot(Context, UiRoot)` 解析创建 Context 属于在飞候选还是活动 generation 并注册进对应 `GenerationGlobals`（`ScriptManager.java` registerUiRoot/UiRootBinding seam）；`UiRootLifecycle` 按实例持有 (generation, globals)；`GenerationGlobals.uiRoots` 是按 generation 的实例字段而非 static；全 `client/ui` 包无可变 static 字段（grep 验证）；非 managed CLIENT context 创建被 NEKO-7003 拒绝、Context 既非候选也非活动时 NEKO-7001 拒绝——不附着到任何全局 registry，因此无 UI singleton 与第二 runtime owner（单 owner = `NekoRuntimeRoot` 下既有 `ScriptManager`/`GenerationGlobals` 体系）】
- [x] candidate UI 计划、binding、signal 订阅、事件 token 和 host 计划在 commit 前对生产 Screen/输入路由不可见；candidate 失败全部清理且 active Screen 继续可用。【evidence：`UiRootLifecycle.isProduction` 仅对已提交 generation 为 true；候选 root 的 `open()` 不直接 show，经 `minecraft.execute` 延迟到 commit 后由 `runDeferredOpen` 重判（commit→show / 候选被 discard→NEKO-7006 丢弃诊断）；候选失败路径 `discardCandidate → closeRuntimeResources → globals.close() → closeUiRoots → closeForGeneration`（ScriptManager discardCandidate/closeRuntimeResources），只清候选 globals 的 roots，active globals 不动。测试：`candidateBecomesProductionOnlyAfterItsGenerationCommits`（候选准备期 usable 但非 production，commit 后 promotion）、`failedCandidateRootStaysUnusableEvenWhenALaterCandidateReusesTheNumber`（失败候选的 root 永不复活，同号新候选不受影响）、`candidateTeardownPathCleansCandidateRootsAndKeepsActiveRoots`（候选清理保留 active root）】
- [x] commit 后旧 generation 停止接收新事件，取消 pending reconcile，释放订阅、host node、原生 widget、输入 token 和 Screen handle；新 generation 恰好接收一次事件。【evidence：commit 取代路径旧 runtime `closeRuntimeResources → globals.close()` → 旧 root `teardown`：dismiss 当前 Screen（`showScreen(null)`）、`tree.close` 释放 host node/原生句柄、guest root 句柄置空；此后旧 handle 的一切操作被 `requireUsable` 以 NEKO-7001 显式拒绝；signal 订阅、事件 token 与 pending reconcile 随旧 generation 的 guest Context 销毁一并失效——host 无独立 reconcile 取消机制，Context 销毁即取消（`closeUiRoots` javadoc 记录该机制；"取消"语义=随 Context 销毁失效，非显式 cancel 句柄，如实记录）；新 generation 由各自的 adapter/Screen 实例独占输入路由，Minecraft 单一当前 Screen 结构保证事件恰好投递一次。测试：`supersededActiveRootFailsExplicitlyWithStableCode`（被取代 root 不可用且不被降级重释）、`generationCloseReleasesEveryRootOnceAndSurvivesAFailingRoot`（每个 root 恰好释放一次，单个失败不阻塞其余）】
- [x] reload 成功、reload 失败、Screen 主动关闭、外部 `setScreen` 替换、客户端退出和 close 抢占的 cleanup 幂等；旧 handle 不能操作新 generation。【evidence：`teardown` 重入 guard——lifecycle 已离开 CANDIDATE/ACTIVE 的调用直接返回，首个到达者独占 teardown；路径映射：reload 成功=commit 取代→`closeForGeneration`、reload 失败=候选 discard→`closeForGeneration`、Screen 主动关闭=`JsxScreen.onClose→close()`、外部 setScreen 替换与客户端退出=`JsxScreen.removed→close()`（ dismissed 时 `disposed` guard 防二次）、close 抢占=`beginClose` 幂等吸收重复 close 请求；`unregisterUiRoot` 使脚本自关的 root 不被 generation 二次释放；旧 handle 在 closed generation 上注册新 root 被 NEKO-7001 拒绝、操作被 `requireUsable` 拒绝。测试：`closingIsIdempotentAndTerminal`（重复 begin/finish 幂等、关闭期输入拒绝）、`finishingAnUnopenedCloseIsIllegal`（非法转换带 NEKO-7002）、`unregisteredRootIsNotReleasedByItsGeneration`、`registeringOnAClosedGenerationFailsExplicitly`（NEKO-7001）】
- [x] 非-owner-thread 状态更新只能显式排队或明确失败；回调内 invalidate/close、关闭期间输入和 watchdog/cancel 路径可观察。【evidence：显式排队=`enqueue()`（唯一跨线程入口，入队闭包在运行时再检 epoch，过期丢弃并报 NEKO-7006）；明确失败=`requireUsable` 在全部状态变更/guest 调用入口（bindRoot/measureText/inspect/layout/begin/resize/dispatchAt/dispatchScroll/key/textInput）对非 owner 线程抛 NEKO-7004（**本轮 review 修复补齐**：此前仅 `open()` 有线程检查，其余入口错线程会静默执行）；回调内 invalidate/close 由 teardown 重入 guard 吸收；关闭期间输入 `isUsable=false` 显式拒绝（`closingIsIdempotentAndTerminal` 断言）；watchdog/cancel 经票 07 close-preempt → discardCandidate 同一路径进入 generation close，单个 root 释放失败以 NEKO-7005 warn 可观察且不阻断 teardown】
- [x] render、component、layout、event、host Adapter、resource 和 disposed/stale root 错误进入 30 的统一诊断链路，包含阶段、脚本来源、UI root、generation 和 owner；不建立 UI 专用错误事实源。【evidence：唯一 seam = 装配期安装的 `ScriptErrorReporter`（root-owned tracker 的进程级门面）；render/component/layout/event 经 guest `report(adapter, rootId, phase, error)` envelope → `reportDiagnostic` → `recordCallbackError(CLIENT, "ui-"+phase, …)`（NEKO-7007，消息含 rootId 与 generation）；host Adapter 操作失败 → `reportHostFailure`（ui-resize/ui-close/ui-event）；resource/visual → `resolveVisual` 把每个 `UiDiagnostic`（含 rootId/nodeType/nodeKey/generation 的 Location）送进 "ui-visual"；runtime/host 快照歪斜 → "ui-inspect"（NEKO-8001，保留最后好帧）；disposed/stale root → `reportStaleDrop` "ui-stale"（NEKO-7006）与 NEKO-7001。票 30 frozen record（`ScriptDiagnosticRecord`/`ScriptDiagnostics`）为每条记录补齐 phase/owner/generation 归因（`describe()` 行）；UI 阶段在 callbackKind、UI root 与 generation 在稳定消息文本；脚本来源按票 30 对 callback 错误的既定词汇——guest 异常可达时由 tracker 抽取 polyglot 定位，envelope 转发的 host 侧镜像以 kind+去重 hash 为来源键；不建 UI 专用错误事实源（`retainedErrors` 是同一报告的 8 条有界镜像，仅供 Inspector 展示，写入即转发）】
- [x] 真实 NeoForge 26.2 smoke 覆盖成功 reload、失败 reload、旧 Screen 关闭、旧事件失效和 active UI 保留。【Minecraft-free seam:`Ticket42UiGenerationLifecycleTest` 把五类场景(成功 reload/失败 reload/旧 Screen 关闭/旧事件失效/active UI 保留)逐一定约为可执行契约;真机侧 26.1.2 的 CLIENT reload 生命周期已于 2026-09-29 维护者操作会话真机验证(keybind/HUD 域:generation=3 COMMIT、keybind 幂等重注册、HUD 跨代际稳定——`evidence/2026-09-29-ticket26-realmachine/`),26.2 JSX Screen 真机点击/关闭腿与 41 同源,按 2026-09-29 维护者授权「按 23/27 先例(部分满足+缺口移交)关闭」移交 34 真机轮;`docs/architecture-refactor/evidence/2026-09-29-inreview-digest/` 建议 41 AC8/42 真机腿/43 AC6/44 AC6 合并为一轮 26.2 真机 smoke】

## Delivery record (2026-09-25)

背景：`1136f30a` 已把本票实现（`UiRootLifecycle` 新增、`GenerationGlobals` UI root 注册段、`ScriptManager.registerUiRoot` seam、`JsxHostAdapter`/`JsxScreen` generation 接线、`Ticket42UiGenerationLifecycleTest`）随 42/44/45/47 批次交付到 `mult` 分支，但本票从未认领。本轮按 Claim record 的授权范围完成逐条 AC 验证、双轴 code review、缺口修复与证据落盘。

本轮变更（全部在 Claim record 声明的写集内）：

- `src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java`（review 修复，AC5）：`requireUsable` 由纯 epoch 校验扩为共享入口校验——非 owner 线程调用状态变更/guest 调用入口一律抛 NEKO-7004（Error-Reference 既有语义"off the client owner thread without explicit queueing"），此前仅 `open()` 有线程检查；跨线程路径维持唯一显式排队口 `enqueue()`。
- `docs/agents/coding.md`（review 修复，Standards）：诊断码区段表补 `6xxx`/`7xxx`/`8xxx`（client UI visual/resource、JSX UI 生命周期与诊断、UI inspector），与 `wiki/en_us/Error-Reference.md` 已登记的码对齐；该文件另有维护者未提交批次，本行随其批次落盘。

Review 记录（2026-09-25，`/code-review` 双轴：Standards + Spec，固定点 `0816c6b2`）：

- 已修复：上述 AC5 线程检查缺口（Spec 轴）、诊断码区段登记（Standards 轴）。
- 判断级发现未修改及理由：
  - `open()`/`runDeferredOpen()` 决策形状重复（Duplicated Code）：两处第三分支语义不同（候选可用→延迟重判 vs 丢弃+诊断），为两个调用点参数化控制流无新增行为，按 AGENTS.md「仅为具体当前需要引入抽象」保留。
  - `generation == activeGeneration + 1` 魔法算术（Primitive Obsession）：`UiRootLifecycle` javadoc 已完整记录「单一在飞候选」语义与失败候选不复活的实例校验设计，引入新类型无新增行为。
  - `retainedErrors` 8 条环形（Speculative Generality）：有真实消费者 `inspect()`（票 45 Inspector 装饰），非投机预留。
  - `// ponytail:` 注释口吻：仓库既有惯例标记（`ClientRenderRegistry`、`McFontAdapter` 同款），按「repo 标准覆盖基线」保留。
  - AC3「取消 pending reconcile」非字面显式 cancel：机制为随 guest Context 销毁连带失效（`closeUiRoots` javadoc），语义等价且无独立 host 取消句柄可调用，已在 AC3 证据中如实记录。
  - 不可用态输入路由抛 NEKO-7001 进入 Minecraft 输入分发（稳健性待定）：teardown 同步 dismiss Screen 且 `JsxScreen.disposed` guard 使关闭后输入不可达该 Screen，抛错是 AC 要求的明确失败兜底。
  - 批次观察（非本票范围）：`1136f30a` 把 42/44/45/47 合入单提交（Divergent Change），后续批次宜分提交；不改历史。

验证（命令与结果，本地 Windows x64，Gradle 9.6.0 / JDK 25.0.2）：

- `./gradlew.bat :26.2.0:test --tests "com.tkisor.nekojs.client.ui.*" --console=plain` → PASS（Ticket41 4/0、Ticket42 9/0、Ticket44 8/0、Ticket45 3/0；XML 时间戳 2026-09-25T05:56Z）。
- `./gradlew.bat :26.1.2:test --tests "com.tkisor.nekojs.client.ui.*" --console=plain` → PASS（Ticket42 9/0；05:57Z）。
- 修复前全套：`./gradlew.bat :common:test :26.1.2:test :26.2.0:test :1.21.1:compileJava :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava --console=plain` → BUILD SUCCESSFUL；common tests=1928 skipped=4 failures=0 errors=0；26.1.2 tests=437 skipped=58 failures=0；26.2.0 tests=437 skipped=58 failures=0；1.21.1 与两个 fabric 节点编译通过（`neoforge && >=26` 守卫剥离 UI 源码）。
- review 修复后复跑 `./gradlew.bat :26.2.0:test :26.1.2:test --tests "com.tkisor.nekojs.client.ui.*" --console=plain` → PASS（Ticket42 两节点各 9/0；06:12–06:13Z）。
- review 修复后全套复跑 `./gradlew.bat :26.1.2:test :26.2.0:test --console=plain`（无过滤器）→ BUILD SUCCESSFUL；26.1.2 tests=437 skipped=58 failures=0 errors=0；26.2.0 tests=437 skipped=58 failures=0 errors=0（06:17Z）。

剩余缺口：AC7 真实 NeoForge 26.2 客户端 smoke（成功/失败 reload、旧 Screen 关闭、旧事件失效、active UI 保留）需注册 Minecraft MCP 环境，本会话不可用；Minecraft-free seam 已把五类场景定约为可执行契约。

## Dependency rationale

- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
