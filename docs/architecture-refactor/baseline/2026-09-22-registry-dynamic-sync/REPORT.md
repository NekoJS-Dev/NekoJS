# Ticket 21 基线报告：Dynamic Registry 多人 prepare/ack/commit 门禁（2026-09-22）

执行者：zed-flash-21（GLM-5.3 subagent worktree `../NekoJS-mult-t21`，branch
`ticket-21-registry-dynamic-sync`，基于 mult@97aab2b0）。本报告记录接管中断运行后的
交付、红→绿证据、验证命令与遗留缺口。

## 1. 交付内容（按域）

### 1.1 接管与抢救（salvage）

中断的前次运行留下了未提交的 txn 包雏形与 facade/plan 接缝。本轮处置：

- **保留并修复**：`core.dynamic.txn` 四件套（Coordinator / ClientParticipant /
  SyncMessage / Adapter+Transport 接口）、facade 的 `bindActivationEngine` 接缝、
  plan 的 `stageExistingDefinition`（客户端同步路径）与 `DynamicAdapterRequest`
  派生构造器公开化（有 javadoc 理由）。
- **修复的实现缺陷**（前次运行未完成）：
  1. `abortInFlight` 在 abort 当前批之前没有排空队列，`abortCurrent` 尾部的
     `pump()` 会在 close 边界立刻复活下一批（真 bug，红→绿见 §2）；
  2. `beginNext` 同步 transport 重入守卫从 `current == null` 收紧为
     `current != txn`（防止同步 ack 提交后 pump 起新批时循环误用新事务数据）；
  3. 客户端 `onCommit` 重复 COMMIT 分类次序颠倒（staged 匹配检查遮蔽
     already-activated 检查），且重复 no-op 未入事件日志（红→绿见 §2）。
- **撤销的接缝**：前次在 `DynamicCandidateRegistryPlan` 上加的
  `onPublish(Consumer)` 观察者钩子违反票 16 结构性惰性门
  （`DynamicPlanInertnessTest` 禁止 plan 公开面出现 Consumer 执行通道）。
  改为 **facade 侧 pull 队列**：`pendingCandidates` + `pumpActivation()`，
  计划面保持纯 inert 数据、零执行通道（门未动、未放宽）。该重设计同时补上了
  「reload commit 后谁来 stage/pump」的缺口（owner-thread 任意事件可幂等 pull）。

### 1.2 common 主源码（全部零 MC/loader 依赖，`com.tkisor.nekojs.core.dynamic.txn`）

- `DynamicRegistryTransactionCoordinator`（服务端）：一批事务按 PREFLIGHT →
  SERVER_PREPARE → CLIENT_PREPARE → ACK → COMMIT 顺序驱动；每阶段外部结果
  （passed/generation/owner/domain/errorSource/detail）进有界 `phaseLog`。任一
  阶段失败 ⇒ 整批 ABORT（broadcast abort、staged 丢弃、旧 activatedState 保留）。
  定义结果（不静默 no-op）：迟到 ack、重复 ack、未知事务 ack、断线、中途加入
  （必须补 ack 才允许 commit）、ack 超时、close 抢占（队列排空后 abort）。
  commit 先执行 Adapter.activate（no-throw 契约，违约记 degraded）再 broadcast。
- `DynamicRegistryClientParticipant`（客户端节点）：本地 ledger +
  activatedGeneration 水位。PREPARE 校验（本地同 key fingerprint 冲突 ⇒ 整消息
  拒绝）并 stage；COMMIT 只激活「有同代 accepted PREPARE」的批次且恰好一次
  （重复 COMMIT = already-activated 记录式 no-op）；无匹配 prepare 的 COMMIT
  拒绝（同步未完成不激活）；ABORT 丢弃 staged；STATE_SYNC 追平到服务端已激活
  代际（绝不激活更新代际）；断线丢 staged、账本与水位保留供重连追平。
- `DynamicSyncMessage`：协议消息（PREPARE/STATE_SYNC/COMMIT/ABORT，全字段
  string/enum/number，全量状态非 delta）；javadoc 明示 prepare/ack 只是协议阶段、
  不是分布式原子性证明。
- `DynamicRegistryAdapter` / `DynamicSyncTransport`：Adapter/Transport 边界契约
  （数值 ID、registry surgery、payload、cleanup 只经此处；coordinator/facade
  无反射、无 MC 类型、无第二通道）。
- `DynamicRegistryFacadeRuntime`：`bindActivationEngine` / `clearActivationEngine`
  （close 抢占先 abort）/ `pumpActivation()`（pull 队列：已 publish 的候选批次
  stage 进 engine，未 publish 的丢弃；空批/stale-only 不起事务）。

### 1.3 测试（common，31 新用例）

- `DynamicRegistryTransactionSemanticsTest`（13，前次运行遗留修复+保留）：
  阶段顺序与可观察性、单节点零参与者即 commit、账本级同 key 冲突（不 stage）+
  coordinator PREFLIGHT 复检 abort、客户端拒绝整批 abort、ack 超时、断线 abort、
  Adapter prepare 拒绝（零客户端流量）、close 抢占（在飞 abort + 队列丢弃）、
  迟到/重复 ack、串行批次、中途加入必须 ack、晚加入者 STATE_SYNC。
- `DynamicRegistryClientParticipantTest`（9）：prepare→commit 恰好一次 + 重复
  COMMIT 记录式 no-op、无匹配 prepare 不激活、代际错配不激活、旧代际 prepare
  duplicate 接受不重 stage、本地冲突整消息拒绝、abort 清理、Adapter 违约显式
  degraded、断线保留账本/水位 + 重连追平、全类型最终可见性（账本逐条 fingerprint
  + owner 断言）。
- `DynamicRegistryClusterConsistencyTest`（4，跨节点 JVM smoke）：双客户端
  ack+commit 后**所有被激活节点**最终一致（服务端 live == 各客户端 live ==
  账本可见指纹，同一水位）；一端拒绝 ⇒ 任何节点零激活零账本写；晚加入者
  STATE_SYNC 追平到同一状态；断线在飞批全节点 abort。
- `DynamicRegistryActivationFacadeTest`（5，真实 ScriptManager 事务式 reload +
  真实 GraalJS）：绑定后初批激活 + reload 批激活 + ticket 16 stale 语义不变
  （不物理删除）；毒化候选（脚本异常）⇒ NekoReloadException、零 stage、旧 active
  继续服务、零协议消息（watchdog 终止候选走同一 noteCollectionError 毒化路径，
  票 16 prior art）；同 key 改定义 ⇒ 联合边界冲突、engine 不动、无静默覆盖；
  close 抢占（clearActivationEngine）abort 在飞批；未绑定 ⇒ ticket 16 inert 面
  原样（回归守卫）。

### 1.4 文档与示例

- `examples/dynamic-registry-transaction.js`（AC10）：由 16 号 fixture 转换的生产
  最小示例，头注三段：激活门禁现状（三类 not verified、平台侧未接线）、启动期
  vs 动态注册选型、失败保留与同 key changed definition 限制。
- `MIGRATION.md`：与 ticket 16 的分层关系表、批事务作者语义、启动期/动态注册
  选型表、门禁现状与未验证类型（不改写为 unavailable）、平台接线阻塞清单、
  旧路径迁移口径（零删除零双写）。

## 2. 红 → 绿证据

- **batch-abort（失败保留旧 active）**：`command-output/red-batch-abort-salvaged-state.txt`
  ——前次运行遗留状态 3 红（13 用例）；其中 `closePreemptionAbortsInFlightBatchAndDiscardsQueuedBatches`
  期望 ABORTED 实得 AWAITING_ACK，暴露 `abortInFlight` 的 pump 复活 bug；修复后
  全绿（`green-ticket21-txn-and-facade.txt`）。另两红为测试前提错误（见该文件
  逐条记录：断言次序 / 账本级冲突从未进 engine），改测试不改门。
- **commit-once（不双执行）**：`command-output/red-commit-once-client-duplicate-commit.txt`
  ——把客户端 `onCommit` 暂时回退为前次运行的次序（staged 匹配检查在前），
  `prepareThenCommitActivatesExactlyOnceAndDuplicateCommitIsANoOp` 红（重复 COMMIT
  被误分类 commit-without-matching-prepare）；恢复修复后 9/9 绿
  （`green-ticket21-txn-and-facade.txt`，注：该 green 文件为 pull 重设计前的运行，
  重设计后同套件复跑见 §3）。

## 3. 验证命令与结果

| 命令 | 结果 |
|---|---|
| `./gradlew.bat :common:test --tests "com.tkisor.nekojs.core.dynamic.*"` | **通过**（84/84：票 16 存量 53 + 票 21 新增 31） |
| `./gradlew.bat :common:check`（含 checkCommonIsolation） | **通过**（`command-output/common-check.txt`，最终源码状态复跑） |
| `./gradlew.bat :26.1.2:test`（节点 sanity；版本树生产源零改动，仅共享树测试守卫修复见 §3 末节） | **通过**（354/0，`command-output/26-1-2-test.txt`；flake 调查过程见 `26-1-2-test-retry.txt`） |
| `guardLint` | 未跑（未触碰 build/source roots/守卫文件；新增包位于既有 common 源根下） |
| 真机多人 smoke / `runGameTestServer` | **未跑**（headless 环境无客户端双节点；平台绑定本身未交付，见 §4） |

隔离性：txn 包与 facade 改动全部位于 common，零 Minecraft/loader import
（`:common:checkCommonIsolation` 在 `:common:check` 内执行）。

### 共享树测试守卫修复：`Ticket08LoaderDiscoveryTest`

首跑 `:26.1.2:test` 出现 1 个与本票域无关的 `initializationError`（`Platform has
already been initialized`）。调查：基线（stash 本票全部改动后）通过；本票改动改变
common jar 内容 ⇒ JUnit 类扫描顺序移动 ⇒ 另一个先初始化 Platform 的测试类排到了
Ticket08 之前，暴露其 `@BeforeAll` 的既有缺陷——注释声称 defensive 但裸调
`Platform.init`（同树兄弟测试均以 try/catch(IllegalStateException) 复用既有实例，
如 `EventBusForgeBridgeTest`/`Ticket29AssetBindingTest`）。修复＝对齐同一守卫
（`src/test/java/com/tkisor/nekojs/core/Ticket08LoaderDiscoveryTest.java`，测试-only、
零生产代码），修后 354/0。**这是本票唯一的版本树（共享测试树）改动。**

## 4. 门禁现状与缺口（含所有者）

1. **平台激活未接线（本票最大缺口）**：`bindActivationEngine` 只有测试装配调用。
   未绑定时行为与票 16 完全一致（inert 声明 + 账本）。刻意不绑定的原因：真实
   Adapter 需要接 `DynamicRegistries` 的 surgery 路径，真实 Transport 需要新增
   网络 payload——而票 17 冻结的 wire 子集 gate（`NetworkRegistrationSourceTraceTest`
   断言 fabric 恰 6 注册调用/5 payload 类型、NeoForge 注册一次语义的源码 trace）
   会因新增 payload 失败；更新该 gate 属网络 owner 决策，本票不越权放宽。
   **所有者：network owner（票 17 面）+ 维护者**。
2. **`DynamicDefinition` 跨进程 codec**：`DynamicSyncMessage.Entry` 当前为 JVM 内
   传值；真实 payload 需要序列化。所有者：network owner。
3. **reload 后 pump 驱动点**：`pumpActivation()` 已幂等可从任意 owner-thread 事件
   调用；生产装配需要服务端 tick 或 post-commit 事件接线（属 §1 平台装配）。
4. **AC9（旧 unsafe live mutation / 静态 DynamicRegistry 全局入口删除）**：维护者
   sign-off 门禁，本票零删除。替代证据：票 16 REPORT §6 消费者清单 + 本票
   MIGRATION §5 迁移口径；旧 route 本票零改动、零新消费者。
5. **NEKO- 错误码缺口**：新增的问题报告文本（PhaseRecord/SyncOutcome 的
   errorSource/detail、客户端 outcome 字符串）为英文诊断标识，未分配 NEKO- 码
   ——本分支无 `wiki/en_us/Error-Reference.md` 码表，不得虚构；按任务约束记录
   此缺口。所有者：票 30（diagnostics）。
6. **真机跨节点 smoke**：JVM 级 cluster fixture 已覆盖协议语义；真机双节点
   验证依赖 §1 接线，未跑。

## 5. 能力表（诚实口径）

| 候选类型 | 声明面（票 16） | 批事务语义（JVM 双 Adapter） | 目标 Adapter / 真机同步 | 公开激活 |
|---|---|---|---|---|
| Item | 已验证 | 已验证（本票 31 用例） | **not verified**（平台 Adapter 未实现） | 阻塞 |
| SoundEvent | 已验证 | 已验证 | **not verified** | 阻塞 |
| MobEffect | 已验证 | 已验证 | **not verified** | 阻塞 |

not verified 不改写为 unavailable；三类不展示为可用能力（示例与迁移材料同口径）。

## 6. 影响面与契约

- 脚本作者面（事件组、成员、Builder、错误信息）：**零变化**。
- contract/golden、TS/Python declaration：**零改动**（只读约束遵守）。
- 票 16 结构门 `DynamicPlanInertnessTest`：**未改动**（前次运行的红已被本票
  撤销的接缝修复——pull 重设计使计划面保持零执行通道）。
- 版本树：生产源零改动；唯一改动是共享测试树 `Ticket08LoaderDiscoveryTest`
  的 Platform.init 守卫（§3 末节，测试-only、对齐兄弟测试惯例）。
- 公开 API 新增：`DynamicRegistryFacadeRuntime.bindActivationEngine/
  clearActivationEngine/activationEngine/pumpActivation`、plan 的
  `stageExistingDefinition`、`DynamicAdapterRequest(definition, generation, owner)`
  派生构造器公开化。

## 主会话复核修正（2026-09-22，合并后）

- 降级激活回滚契约（复核发现 AC2 缺口）：Adapter `activate` 违约时原实现仅记录
  degraded 并 ABORT——若违约发生在 surgery 中途，部分 registry 变异无人撤销。现
  `DynamicRegistryAdapter` 新增抽象 `rollbackActivation(requests)`，coordinator 降级路径
  强制调用并记录恢复结果（回滚失败并入 degradation detail，不逃逸 commit 路径）；
  `degradedActivationIsRolledBackAndThePreviousStateKeepsServing` 断言部分变异被撤销、
  旧 active 继续服务、activatedGeneration 不推进。
- 客户端 post-commit 激活失败（复核发现 AC4 缺口）：server 已 commit 后客户端自身激活
  失败原本在 coordinator 无确定结果（静默分歧）。新增
  `onParticipantActivationReport(participant, generation, activated, detail)`：失败记录
  `activation-failed-post-commit` 并向该节点追平发送 STATE_SYNC（幂等修复），
  成功仅记录（`failedClientActivationReportGetsACatchUpStateSyncRepair`）。
- `DynamicCandidateRegistryPlan` 的 `add` 与 `stageExistingDefinition` 重复冲突检查合并为
  `stageUnique`（消息不再漂移，sync 路径现在同样带双 owner/双定义定位）。
- `commitCurrent` 降级 detail 的 `e.getMessage()` 空值回退为类名（与包内既有模式一致）。

## 单节点激活真机 smoke（2026-09-29，AC10 单节点腿）

平台接线（wire 备审包，维护者 2026-09-29 批准合入）之后，本票补做了 AC10 缺失的
「已激活生产能力」演示：26.1.2 dedicated server 真机运行，run 目录 engine.toml 开启
`[dynamicRegistry]`（仓库默认仍 false）。声明批（Item + SoundEvent，SERVER 脚本）经
账本 commit → Adapter prepare → 单节点 commit 路径（无远程参与者、无 ack）→
`NeoForgeDynamicRegistryAdapter` 真实 surgery → 新条目 LIVE 且脚本可观察
（`Item.id`/`Item.of` 读 live `BuiltInRegistries.ITEM`）与平台可观察
（`/nekojs registry` 计数）；`/nekojs reload server` 腿证明 reload 驱动的逐 tick
`pumpActivation` 在 joint commit 后的下一 tick 重新走完事务（同 fingerprint 幂等重
claim，条目跨 reload 存活、计数/stale 不变）。证据：
`command-output/09-runserver-26-1-2-activation.txt`（RCON 驱动、有界运行、完整
transcript；脚本与驱动内嵌）。

记录的观察（未修复，生产修复不在本轮范围，详见 transcript Findings）：

- **A1** 每次启动对同一声明跑两个事务：脚本装载本身是 reload 事务（候选收集带 owner
  归因），`server-registry-ready` 初次收集经 active 总线再收集一次（owner 记为
  `<unknown>`）；同 fingerprint 幂等重 claim，计数不变——记账噪声非正确性问题。
- **A2** `collectInitial` 的 INFO 文案在 gate 开启时已过时（「inert only — activation
  is gated」在引擎已绑定并激活的运行中仍打印）——纯文案缺陷。
- **A3** 空服暂停（`pause-when-empty-seconds=60`）期间 `ServerTickEvent.Post` 不触发：
  暂停中 `/nekojs reload server` 只 commit 账本批，激活事务延迟到下一非暂停 tick
  （玩家加入）；stop-while-paused 经 close 边界丢弃，下次启动初次收集重新激活——
  延迟行为非正确性违背，票 34 真机 smoke 需知晓。

边界（诚实口径）：本 smoke 只覆盖单节点 commit 路径；真多人 PREPARE/ack/STATE_SYNC/
客户端 surgery 仍归票 34。MobEffect 真机单节点激活未在本轮声明（只演示了 Item +
SoundEvent），能力表「真机同步 not verified」口径不变（§4.1 的「平台 Adapter 未实现」
表述已被 2026-09-29 接线合并取代）。
