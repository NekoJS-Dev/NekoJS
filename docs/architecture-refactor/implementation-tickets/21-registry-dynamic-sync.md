# 21: Dynamic Registry 多人 prepare/ack/commit 门禁

**What to build:** 在本地 inert 定义计划之上完成服务器与客户端的 Dynamic Registry 批事务：preflight、同 key 指纹冲突、服务端 prepare、客户端 prepare/ack、受控 commit、失败/取消保留旧 active、ID/sync/registry surgery 的 Adapter 边界与按节点声明能力的证据。prepare/ack 只是协议阶段，不宣称分布式原子提交；未通过同步与 reload gate 时不公开不安全热更新。

**Blocked by:** [16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md)、[17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md)

**Status:** in-review（实现/测试/证据已交付；AC9 不勾选（维护者 sign-off 门禁）；平台接线已合入（2026-09-29 wire 备审包）+ AC10 单节点激活真机演示已补做（`command-output/09-runserver-26-1-2-activation.txt`），真多人同步演示归票 34）

**Assignee:** zed-flash-21（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-22):** worktree `../NekoJS-mult-t21` on branch `ticket-21-registry-dynamic-sync`（基于 `3a6380b1`）。预计改动范围：Dynamic Registry 批事务 preflight/fingerprint 冲突/服务端 prepare/客户端 prepare/ack/commit 的共享管线与网络阶段消息、失败/取消/watchdog/close 抢占下整批不提交与旧 active 保留、candidate 可见性与资源释放顺序断言、Adapter 边界（数值 ID/payload/registry surgery 只经 Registry Runtime 与平台 Adapter）、16 号候选计划 fixture 转生产最小示例与迁移材料、`baseline/2026-09-22-registry-dynamic-sync/` 证据。不修改 23 recipe/data 域文件；旧 unsafe live mutation 删除项不勾选（维护者 sign-off 门禁）。

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W6
- W7

## Acceptance criteria

- [x] 一批动态注册按 preflight、同 key fingerprint/冲突检测、服务端 prepare、客户端 prepare/ack、commit 的顺序执行；每个阶段的外部结果、generation、owner/domain 和错误来源可观察。【evidence: `DynamicRegistryTransactionSemanticsTest.phasesRunInTicketOrderAndEachPhaseResultIsObservable`（PREFLIGHT→SERVER_PREPARE→CLIENT_PREPARE→ACK→COMMIT 逐条断言 + 每条 PhaseRecord 带 generation/owner/domain，passed 阶段零 errorSource、失败阶段 errorSource 可定位）；同 key fingerprint/冲突检测并入 PREFLIGHT 阶段（账本联合边界优先拒绝，engine 侧复检 abort，domain=dynamic-registry-conflict 可观察——`conflictedBatchAbortsAndOldActiveKeepsServing`）。实现：`core.dynamic.txn.DynamicRegistryTransactionCoordinator`（common，零 MC 依赖）】
- [x] 任一阶段失败、候选脚本失败、watchdog 终止、close 抢占或同步无法完成时整批不提交；候选计划丢弃并清理，旧 active state 继续服务，无部分注册、半成功 ID 或混合代际。【evidence: 客户端拒绝/ack 超时/断线/Adapter prepare 拒绝/close 抢占（含队列丢弃，红→绿 `red-batch-abort-salvaged-state.txt`）→ `DynamicRegistryTransactionSemanticsTest` 6 用例；候选脚本失败（毒化）⇒ 零 stage、旧 active 继续服务、零协议消息 → `DynamicRegistryActivationFacadeTest.poisonedCandidateReloadStagesNothingAndOldActiveKeepsServing`（真实 GraalJS reload；watchdog 终止候选经同一 noteCollectionError 毒化路径，票 16 prior art）；跨节点零部分注册 → `DynamicRegistryClusterConsistencyTest.oneClientRejectsAndNoNodeActivatesAnything`（服务端+双客户端零激活、零账本写、无半成功 ID）；主会话复核追加：Adapter activate 违约的降级路径现由 `rollbackActivation` 契约强制回滚——部分 surgery 被撤销、旧 active 继续服务、activatedGeneration 不推进（`degradedActivationIsRolledBackAndThePreviousStateKeepsServing`）】
- [x] prepare/ack 消息在契约、测试和文档中只表示协议方向与事务阶段，不证明跨进程分布式原子性；成功断言必须观察所有被激活节点的最终一致可见性或明确的拒绝/降级结果。【evidence: `DynamicSyncMessage`/`DynamicRegistryTransactionCoordinator` javadoc 明示「protocol phases only, not a distributed atomicity proof」；`DynamicRegistryClusterConsistencyTest.allNodesReachTheSameActivatedStateAfterAckAndCommit` 断言所有被激活节点的最终可见状态（服务端 live == 各客户端 live == 账本可见指纹、同一水位），非消息存在性；拒绝/降级：ack-rejected → 整批 ABORT、Adapter activate 违约 → degraded 记录（`adapterActivationFailureIsAnExplicitDegradedResult`、`RecordingTxnSupport` activateFailure 路径）】
- [x] 同步未完成的客户端不激活新代际，不保留 server-only 多人暴露路径；断线、重连、迟到 ack、重复 ack 和客户端不可用都有确定结果，不出现静默 no-op。【evidence: `DynamicRegistryClientParticipantTest`（commit-without-matching-prepare / already-activated / duplicate-generation / 断线保留账本水位 + STATE_SYNC 追平不激活更新代际）；`DynamicRegistryTransactionSemanticsTest`（迟到/重复 ack 记录式结果、中途加入必须 ack）；门禁：绑定后无参与者 ack 不得 commit（`clearActivationEngineAbortsInFlightWorkAtCloseBoundary` 在 AWAITING_ACK 断言零激活）。范围注记：事务路径未引入任何 server-only 多人暴露（生产未绑定 engine，绑定即受 prepare/ack 门禁）；旧路径的多人限制照旧，删除属 AC9 门禁；主会话复核追加：客户端 post-commit 激活失败经 `onParticipantActivationReport` 得到确定结果——记录 + 追平 STATE_SYNC 修复而非静默分歧（`failedClientActivationReportGetsACatchUpStateSyncRepair`）】
- [x] 数值 ID、网络 payload、registry surgery、claim、cleanup 和平台差异只由 Registry Runtime 与平台/版本 Adapter 执行；事件 facade 不做反射或直接修改 registry 内部结构。【evidence: 结构性：`core.dynamic.txn` 只暴露 `DynamicRegistryAdapter`/`DynamicSyncTransport` 两接缝，Coordinator/ClientParticipant/facade 零 MC 类型零反射（`:common:checkCommonIsolation` 过，`command-output/common-check.txt`）；票 16 `DynamicPlanInertnessTest` 结构门未动且过（本轮撤销了违反该门的 onPublish(Consumer) 接缝，改为 facade pull 队列）。注：平台 Adapter 实现未交付（AC8 口径），执行边界由接缝+测试双 Adapter 证明】
- [x] commit 前 candidate 对生产 callback、对外 binding、live registry 和其他节点不可见；commit 后旧 generation 不再接收新计划，新 generation 只执行一次，旧资源按所有权顺序释放。【evidence: commit 前：AWAITING_ACK 期间零 Adapter 激活（facade/clause 测试）+ 客户端 staged 不入 live/账本（`commitForADifferentGenerationThanTheStagedPrepareNeverActivates`）；恰好一次：重复 COMMIT = already-activated 记录式 no-op（红→绿 `red-commit-once-client-duplicate-commit.txt`）、迟到 ack 不重执行、STATE_SYNC 幂等；旧 generation 不再接收新计划：plan.publish() 一次性 + facade `lastStagedPlanGeneration` 水位 + engine 串行队列；释放顺序：`releaseCurrent` javadoc 所有权顺序（事务作用域资源一并不可达，保留的只有 activated wire entries）】
- [x] 同 key 冲突、缺失声明 stale/retired、普通 reload 不物理删除的行为与本地票集成后仍成立；未来 replace/update 未实现时不得静默覆盖旧 active。【evidence: `DynamicRegistryActivationFacadeTest.boundEngineActivatesCommittedBatchesAndKeepsTicket16LedgerSemantics`（engine 绑定下 stale 标记/不物理删除/同定义幂等重 claim 全保留）+ `sameKeyChangedDefinitionFailsReloadAndOldActiveKeepsServing`（联合边界冲突、旧 active 服务、零静默覆盖）；客户端侧本地冲突整消息拒绝（`conflictingTargetStateIsRejectedAsAWhole`）】
- [x] contract/golden、TS/Python declaration、transaction/reload/delete-cleanup fixture、capability/source-trace 和跨节点 runtime smoke 只对通过目标 Adapter、事务与同步 gate 的既有候选类型作出结论；未验证类型记录 not verified 并阻塞公开开放，不因缺测改写为 unavailable。【evidence: 三类候选类型（Item/SoundEvent/MobEffect）批事务语义经 JVM 双 Adapter 31 用例验证，但目标平台 Adapter/真机同步未实现未接线 ⇒ 能力表全部记 **not verified** 并阻塞公开激活（baseline REPORT §5；示例/迁移材料同口径）；contract/golden 与 TS/Python declaration 零改动（git diff 只含 common 源/测试与 docs）；票 17 wire gate（`NetworkRegistrationSourceTraceTest` 恰 6 调用/5 类型）未触碰——新增同步 payload 会破坏该冻结 gate，已记录为平台接线阻塞项而非越权放宽】
- [ ] 旧 unsafe live mutation、静态 DynamicRegistry 全局入口和不安全 server-only 路径只有在批事务、失败回滚、迁移表和旧 route 无消费者全部闭合并获维护者确认后才能删除。【**不勾选（门禁）**：维护者删除确认是发布门禁（Human input note）。本票已备：批事务/失败保留证据（AC1/AC2）、迁移表（baseline MIGRATION §5）、旧 route 消费者清单沿用票 16 REPORT §6 且本票零改动零新消费者；旧面零删除、零双写】
- [x] 本票只将已通过类型事务/同步 gate 的 16 号候选计划 fixture 转为生产最小示例与迁移材料；清楚说明启动期与动态注册的区别、失败保留和同 key changed definition 限制；16 的关闭不反向依赖这些激活验收。【示例（`baseline/2026-09-22-registry-dynamic-sync/examples/dynamic-registry-transaction.js`）与迁移材料（同目录 MIGRATION.md）已交付，头注三段清楚区分启动期 vs 动态注册选型、失败保留、同 key changed definition 限制，且票 16 保持 closed 不反向依赖（其证据链未引用本票任何激活验收）。2026-09-29：维护者批准并合入 wire 备审包（ticket-21-wire-prep）后，单节点「已激活生产能力」演示已补做——26.1.2 dedicated server、run 目录 gate 开启（仓库默认仍关），声明批（Item+SoundEvent）经账本 commit → Adapter prepare → 单节点 commit 路径 → 真实 surgery → 条目 LIVE 且脚本/平台双面可观察，reload 腿证明逐 tick pump 幂等重激活；证据 `baseline/2026-09-22-registry-dynamic-sync/command-output/09-runserver-26-1-2-activation.txt`（含 A1–A3 观察记录：启动双事务/`<unknown>` owner、过时 INFO 文案、空服暂停延迟 pump——均未修复，见 transcript Findings）。边界：MobEffect 未在本真机演示中声明、跨节点 PREPARE/ack/STATE_SYNC/客户端 surgery 未验证——真多人同步演示归票 34，能力表 not verified 口径不变】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [global 共享状态与候选写入规格](../specs/10-shared-global-candidate-writes.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md): 多人事务只能提交已经由 typed Builder、fingerprint、preflight、冲突和 stale 语义稳定生成的 generation-scoped Adapter 请求。
- [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md): 客户端 prepare/ack、连接生命周期、重试/断线和 payload 传输由 network owner 提供；该基础不得反向依赖 Dynamic Registry，避免形成依赖环。
- [10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md): 一次脚本候选的动态注册失败必须与受管 global/shared 写集交接一致，验收依赖已闭合的联合写集行为。

## Scope and coordination

**Rationale:** 多人可见性、网络确认和 commit gate 是独立于 Builder 本地语义的风险最高的垂直切片；拆开后可分别用本地 Adapter 测试和跨节点事务 smoke 验收，避免把 prepare/ack 误当原子性证明。

**Coordination:**

- 与 NETWORK_SYNC owner 固定 payload 语义、连接生命周期与重试边界；network 基础只依赖 runtime root，不依赖 Dynamic Registry。
- 与 GLOBAL_STATE owner 协调同一 candidate 中注册计划与受管 global/shared 写集的联合成败；GLOBAL_STATE 是本票硬 blocker，协调仅覆盖接口细节和联调输入，不把 Dynamic Registry 实现转移给 global owner，也不允许绕过联合提交验收。
- 与 EVENT_SURFACE owner 确认动态事件在 catalog/golden 中只有一条 bus，且 side filter 不把 SERVER 事件泄漏给 CLIENT。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Delivery record（2026-09-22）

- 执行者：zed-flash-21（接管同票中断的未提交 worktree；认领基线 `97aab2b0`）。实施/文档提交见 `git log --oneline 97aab2b0..HEAD`；合并与全量 gate 归主会话。
- 交付物：`common/.../core/dynamic/txn/`（Coordinator/ClientParticipant/SyncMessage/Adapter/Transport 五件套，零 MC 依赖）、`DynamicRegistryFacadeRuntime` 激活接缝（bind/clear/pumpActivation pull 队列）、plan 的 `stageExistingDefinition`（客户端同步路径）与 `DynamicAdapterRequest` 派生构造器公开化、4 个测试套件 31 用例（真实 GraalJS facade 集成 + JVM 跨节点 cluster）、示例与迁移材料、`baseline/2026-09-22-registry-dynamic-sync/`（REPORT/MIGRATION/examples/command-output）。
- 接管处置：保留并修复前次运行的 txn 雏形（3 处实现/测试缺陷，含 abortInFlight 的 pump 复活真 bug 与 onCommit 重复提交分类次序）；**撤销**前次的 `plan.onPublish(Consumer)` 观察者接缝（违反票 16 `DynamicPlanInertnessTest` 执行通道门），改为 facade pull 队列——门未放宽。
- 验证：`:common:check` 全绿（含隔离，最终源码状态复跑）；`:common:test --tests "com.tkisor.nekojs.core.dynamic.*"` 84/84（票 16 存量回归 + 票 21 新增）；`:26.1.2:test` 354/0（节点 sanity；附带修复共享测试树 `Ticket08LoaderDiscoveryTest` 的裸 `Platform.init` 守卫缺失——本票 common jar 内容变化移动 JUnit 类扫描顺序后暴露的既有缺陷，对齐兄弟测试 try/catch 惯例，测试-only）。红→绿证据两份（batch-abort / commit-once）。未跑：真机多人 smoke、`runGameTestServer`、guardLint（未触碰守卫/build 文件）、其余节点（版本树生产源零改动）——详见 baseline REPORT §3。
- 遗留：平台激活接线（payload+Adapter+pump 驱动，owner network+维护者）、`DynamicDefinition` 跨进程 codec、NEKO- 码表缺口（owner 票 30）、AC9 维护者 sign-off、AC10「已通过 gate 的生产示例」部分满足。

## Review pack note（2026-09-29，平台接线预备）

维护者裁决「现在备方案+diff」：平台接线 review pack 已备于分支 `ticket-21-wire-prep`
（基于 mult@`973defbc`，未合并未推送）。内容：设计（批事务协议骑既有 register-once
通道族——单条双向 payload `nekojs:dynamic_registry_sync` + common JSON codec）、26.x
共享树真实现（`NeoForgeDynamicRegistryAdapter`/`NeoForgeDynamicSyncTransport` +
服务端/客户端接线，三类候选类型 surgery 复用既有 `DynamicRegistries` 冻结旁路路径，
无类型被标 unavailable）、冻结 wire gate 的受管更新（`NetworkRegistrationSourceTraceTest`
纯增量方向钉住 + javadoc，fabric 恰 6 调用/5 类型子集断言原样未动）、wire golden 从
第一天钉住（`DynamicSyncPayloadWireFormatTest`，26.1.2 实测 hex）。证据与裁决事项见
`evidence/2026-09-29-ticket21-wire-prep/REPORT.md`。AC8/AC10 的平台接线缺口以此 pack
的维护者裁决 + 真机 smoke（owner 34）为闭环路径；裁决前本票注记与能力表口径不变。
