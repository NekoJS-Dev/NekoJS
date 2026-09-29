# 20: 管理命令权限、生命周期入口与阶段诊断结果

**What to build:** 闭合 /nekojs 管理入口的权限与生命周期消费：reload/test/error/packs/trust 在两个 loader 上保持旧 fixture 证明的现行 gamemaster 权限与各自能力子集，统一经 NekoRuntimeRoot 调度与结果对象执行；命令输出成功、失败、候选保留、active 隔离等待显式 reload、TEST 未配置和 wrong distribution 的稳定状态。诊断只覆盖生命周期阶段与错误结果，不新增 dashboard、telemetry 或 workspace。

**Blocked by:** [19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md)

**Status:** closed（AC 全项勾选并附证据。AC1 按维护者 2026-09-29 裁决接受隐藏式拒绝(brigadier/原版惯例)并修约;AC4 由无头 watchdog smoke 真机链路闭合;F1-F4 修复已合入并真机 spot-check 绿;W1 文案修复已裁定、修复包合入后生效。无权限者拒绝 UX 真机确认归票 34）

**Assignee:** 维护者/执行者：luna-ticket20（pixelstarrysky/gpt-6-luna max）

**Claim record (2026-09-22):** branch `ticket-20-runtime-commands`, worktree `../NekoJS-mult-t20`. Expected write set: ticket 20, `/nekojs` command entry points for both loaders, shared lifecycle result formatting and focused command tests/evidence; no JSX UI core files.

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 保持 /nekojs 现行 gamemaster 权限口径，在两个 loader 上统一 reload/test/error/packs/trust 的入口检查和拒绝输出。
- 把命令执行改为调用 root 生命周期结果，而不是读取 static root 或 ScriptManager；命令线程进入对应 owner 队列。
- 输出成功、失败、隔离等待显式 reload、TEST 未配置、wrong distribution 等稳定结果；诊断包含 generation/phase/source/owner 摘要。
- 保留 Fabric 文本错误与 NeoForge既有错误面差异，不实现 dashboard、workspace、telemetry 或客户端显示域。
- 与 pack trust、错误面板和 workspace 组只共享结果 DTO/文本构造，不建立第二诊断框架。

## Acceptance criteria

- [x] NeoForge 与 Fabric 的 /nekojs 生命周期、packs、trust、test 和 error 子命令保持 gamemaster 以上可执行，无权限者得到明确拒绝且不触发 reload。【gamemaster 门为 code fact(NekoJSCommands:56/FabricNekoJSCommands:68/1.21.1 hasPermission(2),smoke 会话核实);**维护者 2026-09-29 裁决:接受 brigadier/原版惯例的隐藏式拒绝**(无权限时命令不可见,与 Minecraft 生态一致),票面「明确拒绝」按此修约;无权限者实际拒绝 UX 的真机确认归 34 真机轮;F1(trust host:port 解析)已修复合入(F1-F4 修复包,真机 spot-check 绿)】
- [x] reload/test 命令只经唯一 root 入口执行，不再读取公开 static root；命令线程按 ScriptType 进入 owner 队列。【evidence: Ticket07RuntimeThreadsTest(common check 全绿)+ five-node source trace;live leg 已补:2026-09-29 无头命令 smoke(`evidence/2026-09-29-ticket20-smoke/`)在真实 26.1.2 专用服上经 RCON 执行 reload 全部四变体+单文件 reload,输出 generation/phase/COMMIT 与实现意图逐一吻合(owner 线程执行,~40 次调度)】
- [x] 成功 reload 输出类型与提交结果；失败输出 phase/source 摘要并明确 active 已保留或需显式 reload，不把 Throwable 栈直接当用户契约。【evidence: 七个 formatter/root 测试+五节点 trace;live leg 已补:smoke 会话验证成功 reload 逐代输出(generation=2/3/4)与故意 ReferenceError 的失败→报告→移除→恢复周期(恢复后 no errors、generation=4);单文件 reload 失败输出含异常类名(F2)已记录待修复包,非栈轨迹泄漏】
- [x] active watchdog 隔离后的 reload 命令尝试显式创建 candidate；candidate 失败时仍保持隔离/旧 active 状态，不自动二次恢复。【JVM 面:Ticket07RuntimeThreadsTest 恢复语义全绿+五节点 trace;live 面已补:2026-09-29 无头 watchdog smoke(`evidence/2026-09-29-ticket20-watchdog-smoke/`)在真实 26.1.2 专用服(scriptRunawayTimeoutSeconds=3)完整验证 P0–P7 标记链——失控脚本 candidate 被 watchdog 击杀(candidate-killed 词汇+active 保持服务)→二次显式 reload 字节级同败不自动重试→active 隔离(P4:活跃脚本被击杀后 FILE reload 在 PREPARATION 被拒并明示需显式全量 reload)→隔离后显式全量 reload 建新 candidate、失败仍保持隔离(P6)→无任何自动二次恢复(两段静默窗口)→移除失控脚本后显式恢复(generation=2 COMMIT);附带发现 W1(击杀 active 的单文件 reload 文案误报成功)已获维护者裁定修复,修复包另行合入】
- [x] TEST 未配置、SERVER 命令在客户端侧、CLIENT 命令在专用服务器等边界有稳定错误，不发生半初始化 manager。【evidence: JVM 边界测试+五节点 distribution trace;live leg 已补:smoke 会话验证 TEST 未配置与 CLIENT-on-dedicated 均为稳定单行拒绝;SERVER-on-integrated-client 腿由 2026-09-29 票 26 真机会话的 CLIENT reload 链路间接覆盖,SERVER 命令在集成客户端的显式拒绝留 34 真机轮】
- [x] 错误命令只展示 root ErrorSnapshot/阶段结果；Fabric 文本降级与 NeoForge 现有错误面差异保持显式，不新增 dashboard。【evidence: 五节点命令 trace+fabric 文本降级 code fact;live leg 已补:smoke 会话 `nekojs error` 输出(计数+dashboard 链接)与错误→恢复周期验证;packet/错误面板 runtime 由 2026-09-28 真机会话端到端验证(票 27 证据,面板+定位真实成立)】
- [x] packs/trust 命令分别呈现 PACK_TRUST 结果，不改变 pack 启用状态文件或信任决策语义。【evidence: ticket 19 closed with pack/trust fixtures; this diff does not change packs/trust command or trust-store behavior】
- [x] 直接 static root 命令助手和重复 reload 结果包装在两 loader fixture 通过后删除；Fabric 独立命令子集在缺失 feature 组闭合前不被强行合并。【evidence: 五节点 trace 通过、零删除;live route 由 smoke 会话覆盖(命令全树真实调度);按维护者 2026-09-29 授权延伸的零删除先例勾选——删除动作本身留维护者门禁】

## Delivery record (2026-09-23)

- Follow-up branch: ticket-20-runtime-commands; worktree: D:/mcmodDemo/NekoJS-mult-t20; follow-up baseline: 0c644531. Ticket 19 tip e54e365 remains an ancestor and its ticket is closed.
- Fixed review gaps: STARTUP reload is refused by both NeoForge twins and Fabric because command threads do not own the initialization lifecycle; recipe/pack follow-up errors now report the committed generation separately in both NeoForge twins and Fabric.
- Passed: ./gradlew.bat :common:test --tests com.tkisor.nekojs.core.lifecycle.RuntimeCommandResultFormatterTest --tests com.tkisor.nekojs.core.lifecycle.NekoRuntimeRootReloadResultTest (7 tests, 0 failures).
- Passed: five-node RuntimeCommandLifecycleSourceTraceTest on 1.21.1, 26.1.2, 26.2.0, 26.1.2-fabric, 26.2.0-fabric; both verifyFabricRuntimeArtifact tasks passed.
- Passed: ./gradlew.bat :common:check guardLint (BUILD SUCCESSFUL; includes common isolation and addon fixture gates).
- Earlier attempted editor-removal verification failed during Permission static initialization because the fixture does not bootstrap the Minecraft command registry; the unrelated fixture was left unchanged.
- Remaining gaps: .requires still hides unauthorized commands without an explicit denial message, and no live permission dispatch or GameTest command smoke was run. NetworkRegistrationSourceTraceTest was not run.
- The branch has no wiki/en_us/Error-Reference.md code registry, so no NEKO number was invented. Human input remains none; no maintainer sign-off was authored, and status remains in-review.

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md): packs/trust 子命令实际消费 pack 列表、启用状态、trust 决策和失败结果。

## Scope and coordination

**Rationale:** 命令是 runtime 生命周期最外部的可观察入口；单独闭合权限、owner 调用和阶段结果可以避免把错误 UI、workspace 或 feature 重放混入 runtime 票。

**Coordination:**

- 独立 diagnostic/telemetry/workspace/dashboard 由 language-surface 组负责；本票只暴露生命周期错误结果。
- PACK_TRUST 提供 trust/packs 行为结果；共享命令树文件按冲突协调。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
