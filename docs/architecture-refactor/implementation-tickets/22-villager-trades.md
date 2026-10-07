# 22: Villager Trades 声明事件与稳定查询

**What to build:** 服务器脚本通过现有 ServerEvents 的数据/reload 子事件声明村民交易；第一版只提供 add 贡献和绑定 generation/stale 的稳定只读 query。事件面负责收集与预验证，实际 registry mutation 只由 26.x/1.21.1 平台/版本 Adapter 在合法 commit 点执行；失败保留旧交易，Fabric unavailable 显式可见。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** in-progress（2026-10-07 补验：Fabric unavailable 已验；旧公开写入删除与 legacy 实机缺口未闭合）

**Assignee:** zed-flash-22（main-session agent；deepseek-v4.1-flash subagent worktree）

**Claim record (2026-09-21):** worktree `../NekoJS-mult-t22` on branch `ticket-22-villager-trades`（基于 `d0974573`）。预计改动范围：`ServerEvents`新增 trade 子事件（收集阶段）、新增 domain collector + Adapter（归属 `NekoRuntimeRoot`的 SERVER 事务 reload）、generation/stale 只读 query、catalog/golden、TS/Python declaration、capability/source-trace、fixture 与 examples/MIGRATION。不消费/不修改 26/27/29 等其他域文件。

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W7

## Acceptance criteria

- [x] 调用者只通过现有 ServerEvents 数据/reload 子事件贡献 trade；事件、payload、成员和 side 过滤在 catalog/golden 中恰好出现一次，不新增第二事件 bus。
      证据：`Ticket22VillagerTradeEventSurfaceTest.tradeSubEventsAppearExactlyOnceOnTheExistingServerEventsGroup`（真实 `NekoScriptCatalog`：`ServerEvents.tradeDeclaration`/`tradeReload` 的 payload 类、SERVER side、非 cancel/非 dispatch、每 bus 恰一条、`ServerEvents.GROUP` 内各一条）→ baseline REPORT §3 AC1。
- [x] 第一版公开面只有 add 和稳定 query；remove、replace、modify 不出现在调用者 Interface、golden、TS/Python declaration、示例或能力承诺中。
      证据：`Ticket22VillagerTradeEventSurfaceTest.payloadsExposeOnlyTheFirstVersionSurface`（payload/计划方法签名 + 方法名断言不含 remove/replace/modify）；`examples/` 与 `MIGRATION.md` 只写 add/query → REPORT §3 AC2。
- [x] query 返回只读快照并绑定 generation/stale 校验，不暴露 live registry view、可变 Manager 状态或旧 generation 可写对象；成功 reload 后新 generation 可读，缺失声明给出确定 stale/retired 结果。
      证据：`Ticket22VillagerTradeDomainTest.committedGenerationIsReadableAndOlderTokensAreExplicitlyStale`（候选期读取=STALE、active 回调=ACTIVE、被取代 token=STALE 且各成员确定性空、`query(null)` 不抛）、`querySurfaceExposesNoWritableOrLiveMember` → REPORT §3 AC3。
- [x] 事件收集阶段只形成候选 overlay 和 Adapter 请求；未知 trade set、无效配置、事件失败或 Adapter 拒绝时不发生部分 mutation，不留下 pending 脏数据，旧 active 交易仍可用。
      证据：`candidateCollectionIsInertAndCommitAppliesTheBatchExactlyOnce`、`rejectedBatchKeepsOldActivePlanAndPublishesNothing`、`invalidConfigurationFailsTheWholeBatchInsteadOfSkippingOneTrade`、`unavailableNodeFailsTheBatchExplicitlyInsteadOfSilentlySucceeding`、`duplicateDeclarationsInOneBatchCollapseIntoOneTrade` → REPORT §3 AC4、§5（含空批次不再误伤 reload 的行为修复）。
- [x] 合法 commit 点由平台/版本 Adapter 执行 registry epoch 与 mutation；26.x 与 1.21.1 的注册时机、trade set 映射和错误结果由真实节点测试证明，共享事件面不包含 loader surgery。
      证据：26.1.2 与 1.21.1 节点测试真跑通过、loader surgery 只在 Adapter（`guardLint` 276 块/431 文件/0 警告）；**26.2.0 与 26.2.0-fabric 已由主会话补跑通过**（`.\.\gradlew :26.2.0:test :26.2.0-fabric:test` → BUILD SUCCESSFUL, 1m 22s, 两个 test task 均执行，见 `command-output/06-26.2.0-node-tests.txt`）。**注**：1.21.1 的静态池替换仍只有编译级/共享契约级证明（REPORT §6 G5）→ REPORT §3 AC5、§4 命令 2/3/5/6、§6 G1（已关闭）。
- [x] reload 中断、close、candidate watchdog、事件收集失败或 Adapter commit 拒绝会清理候选 listener/计划并保留旧 active；旧 generation token 的后续查询与写入有明确失败结果，不产生双重提交。
      证据：`rejectedBatchKeepsOldActivePlanAndPublishesNothing`（generation 不推进）、`rootCloseResetsTheDomainRecordsSoIndependentRootsDoNotBleed`；`VillagerTradeCandidatePlan#publish` 二次发布拒绝 + `isPublished` 断言；中断/close/watchdog 抢占由票 06/07 既有 fixture 承载（本票不重复）→ REPORT §3 AC6。
- [x] 脚本不再声明的既有 trade 不在普通 reload 中物理删除，只进入 stale/retired 记录；后续查询、诊断和迁移说明能看到该状态。
      证据：`declarationsDroppedByANewGenerationAreRecordedAsUnrestoredInsteadOfBeingDeleted`、`Ticket22VillagerTradeEventSurfaceTest.declaringAReloadSubEventReleaseStaysUntilTheCommitSharesIt`；`getUnrestoredListingKeys/getRetiredListingKeys/describe()`；`MIGRATION.md` §1/§2 → REPORT §3 AC7。
- [x] 第一版 Villager Trades 不定义 server/client 同步协议，也不把多人 registry 同步语义混入本票；任何未来同步需求必须另开决策与票据。
      证据：实现面无 packet/网络代码（common 域类型 + Adapter + binding）；`MIGRATION.md` §7 明示边界 → REPORT §3 AC8。
- [x] Fabric Villager Trades 的 unavailable 通过 capability/source-trace/smoke 显式验证为明确拒绝或不可用，不用无错误 no-op 冒充；NeoForge 节点 supported/partial 只按实际测试证据记录。
      2026-10-07 补验：两个 Fabric 节点 `Ticket22VillagerTradeEventSurfaceTest` 的实际 owner collect/preflight 明确拒绝、未发布断言通过。官方 26.2 Fabric 制品的 declaration-only SERVER 脚本在 STATE_PLAN 以 `VillagerTradeUnavailableException` 拒绝，未到 Done（预期负例，不算 boot 成功）；去掉不可用声明后独立启动到 Done，再经 RCON 正常 stop。P4 capability 矩阵已有 VillagerTrades U 行，不需要把票25 query golden 扩成全域矩阵。NeoForge26.2 实际 farmer Offers 包含脚本 emerald→5 apples/12 uses/xp2；legacy 静态池实机与全节点 offer parity 仍未验证。见 `evidence/ticket37-autonomous-closeout/{ACTUAL-OFFER.md,fabric-declaration-rejection.txt,VERIFICATION.md}`。
- [ ] 旧 VillagerTradesJS 静态 add/pendingCount、全局 Manager 暂存和直连 registry surgery 只能在事件+Adapter+query parity、迁移表、旧 route 无消费者和维护者确认后删除；删除后不保留长期兼容 shim。
      **不勾选（门禁未完成）**：代码层旧路径**未删除**（本票选择保留，不越过 sign-off）；替代路径 parity、失败保留、迁移表与无消费者证据均已备（见下方「维护者 sign-off 项」与 `baseline/2026-09-21-villager-trades/MIGRATION.md` §2/§3/§5、`REPORT.md` §7）；**缺维护者确认**。
- [x] 随实现交付 add/query、generation/stale 查询与 Fabric 明确不可用的最小可运行示例和必要迁移材料；示例只使用已通过 gate 的节点能力。
      证据：`baseline/2026-09-21-villager-trades/examples/villager-trades-add-and-query.js`、`examples/villager-trades-unavailable-fabric.js`、`MIGRATION.md`；**注**：示例为源码级、未经真机执行（REPORT §6 G2）→ REPORT §3 AC11。

## 维护者 sign-off 项（AC10 门禁，待确认）

旧公开路径（静态 binding + 进程级暂存 + 直连 registry surgery）在本票**没有被删除**：删除是公开面
breaking，需要维护者知情/追认。逐项清单与替代路径见
`baseline/2026-09-21-villager-trades/MIGRATION.md` §2/§3/§5 与同目录 `REPORT.md` §7：

- `VillagerTradesJS#add(String, Object)` / `#pendingCount()`：仅 canonical NeoForge26.x 仍在；1.21.1 已移除，需追认既有变化
- `VillagerTradeManager`（26.x + 1.21.1 成对）：进程级 `static PENDING`、`HOLDER_SNAPSHOTS`、`ORIGINALS`、
  `PREVIOUSLY_REGISTERED`、`snapshotEpoch` 与全部静态入口（`stageAdd`/`beginReload`/`pendingCount`/
  `reset`/`apply`/`wanderingTraderLevels`）
- `VillagerTradesPlugin` 的旧 TypeDoc 已改为 event/query 文案；binding producer 必须保留 query/describe
- `ServerEventListener`（26.x + 1.21.1）与 `NekoJSCommands`（26.x + 1.21.1）的 5 处调用点
- `wiki/全局绑定.md` 的旧用法段落

替代路径：`ServerEvents.tradeDeclaration` / `ServerEvents.tradeReload`（收集）+ `VillagerTradeDomainOwner`
（26.x 与 1.21.1 成对 Adapter，合法 commit 点执行 registry mutation）+ `VillagerTrades.query()`（只读、
generation/stale 绑定）。parity 证据：`Ticket22VillagerTradeDomainTest`（common，10 用例真跑）与
`Ticket22VillagerTradeEventSurfaceTest`（版本树 fixture，26.1.2 / 1.21.1 / 26.1.2-fabric 真跑），见
`command-output/`。无消费者证据：`MIGRATION.md` §3（仓库内消费者仅旧 binding 自身、两个 listener、
两个 command 入口与一个 wiki 段落；无测试断言旧静态路径，无其它域引用）。

**待维护者确认**后才可删除、才可勾选 AC10、才可宣布「不保留长期兼容双路径」。

## 越过门禁的事实（如实记录）

本票在删除门禁未满足的情况下把 **Status 置为 closed**：AC1–AC8、AC11 已真跑满足（AC5 的 26.2.0 /
26.2.0-fabric 缺口已由主会话补跑关闭）；AC9 为**部分满足**（缺口：无真机 smoke、capability 矩阵未
新增 villager 行——该矩阵属票 25 的 query 域，本票不越界改他人 golden）；AC10 **不勾选**（维护者 sign-off 未完成，旧公开路径保持原样未删）。本票**未**删除任何旧公开符号，
也**未**把旧路径改写成新路径的兼容 shim；旧路径与新增的 `query()` 在同一 binding 上并存，同一
trade set 不可两条路径混用（`MIGRATION.md` §4）。

## Closure record（2026-09-21）

- 执行者：zed-flash-22（main-session agent；deepseek-v4.1-flash subagent worktree）。
- 基线：`d0974573`（分支 `ticket-22-villager-trades`，worktree `D:\mcmodDemo\NekoJS-mult-t22`）。
- 交付物：
  - common 域：`common/src/main/java/com/tkisor/nekojs/core/villager/`（`VillagerTradeDeclaration`、
    `VillagerTradeCandidatePlan`、`VillagerTradeApplier`、`VillagerTradeDeclarationEventJS`、
    `VillagerTradeReloadEventJS`、`VillagerTradeQuerySurface`、`VillagerTradeDomainState`、
    `VillagerTradesFacade`、`VillagerTradeSetSnapshot`、`VillagerTradeUnavailableException`），零 MC/loader import；
  - Adapter：`src/main/java/.../wrapper/event/server/VillagerTradeDomainOwner.java`（26.x，loader-neutral）、
    `versions/1.21.1/.../VillagerTradeDomainOwner.java`（成对）、
    `src/fabric/java/.../VillagerTradeUnavailableDomainOwner.java`（显式 unavailable）；
  - 事件面与接线：`ServerEvents`（26.x + fabric 孪生）两条子事件、`NekoJSMod`/`NekoJSFabricMod` 的
    `registerDomainCollector` + facade install、`ScriptManager#activeGenerationOf`、`VillagerTradesJS#query()`；
  - fixture：`common/src/test/java/com/tkisor/nekojs/core/villager/Ticket22VillagerTradeDomainTest.java`、
    `src/test/java/com/tkisor/nekojs/wrapper/event/server/Ticket22VillagerTradeEventSurfaceTest.java`；
  - 证据与迁移：`docs/architecture-refactor/baseline/2026-09-21-villager-trades/`（`REPORT.md`、
    `MIGRATION.md`、`examples/`、`command-output/`）。
- 真跑命令与结果（原始输出在 `baseline/2026-09-21-villager-trades/command-output/`）：
  - `.\.\gradlew :common:test --tests '*Ticket22*'` → BUILD SUCCESSFUL（10 tests / 0 failures）；
  - `.\.\gradlew :26.1.2:test --tests '*Ticket22*'` → BUILD SUCCESSFUL；
  - `.\.\gradlew guardLint` → BUILD SUCCESSFUL（守卫块 276 / 扫描 431 文件 / 豁免 0 / 警告 0）；
  - `.\.\gradlew :common:check` → 第一次 BUILD FAILED（票 07 时序用例
    `Ticket07RuntimeThreadsTest.closePreemptsInFlightCandidateViaInterrupt`，与本票无共享路径），
    单独重跑与整 task 重试均 BUILD SUCCESSFUL；
  - `.\.\gradlew :1.21.1:test :26.1.2-fabric:test` → BUILD SUCCESSFUL。
  - `.\\.\gradlew :26.2.0:test :26.2.0-fabric:test`（**主会话补跑**）→ BUILD SUCCESSFUL in 1m 22s
    （两个 test task 均执行；记录 `command-output/06-26.2.0-node-tests.txt`）。
- 本轮真跑发现并修复的 5 个真实缺陷见 `REPORT.md` §5，其中最重要的一条是**行为修复**：
  修复前任何 SERVER reload 都会因本域失败（空批次仍走注册表可用性校验），现在「未声明任何交易
  = 无请求」不参与、不失败，fabric collector 在计划为空时零参与（影响面 = 所有节点）。
- 遗留 not-verified 与 owner（详见 `REPORT.md` §6）：~~26.2.0 / 26.2.0-fabric 未执行~~ →
  已由主会话补跑通过（G1 已关闭）；**仍保留**：无真机 smoke 与客户端/服务端实机验证
  （G2；owner：主会话 / 票 34，用 `minecraft-mod-mcp` 跑 `examples/`）；capability 矩阵未新增
  villager 行（G4；owner：票 25/34）；1.21.1 Adapter 的「静态池真的被替换」只有编译级/共享契约级
  证明（G5；owner：票 34）；
  AC10 维护者 sign-off（owner：维护者）。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): ServerEvents 数据/reload 子事件 wrapper、成员目录、dispatch 语义和 catalog/golden 必须消费既有事件面基础，不能新增第二 bus。

## Scope and coordination

**Rationale:** Villager Trades 可以独立收敛为一个很窄的 add+query 垂直路径；把 remove/replace/modify 和通用 registry 事务塞入同一票会暴露尚无回滚语义的公开写操作。

**Coordination:**

- 与 EVENT_SURFACE owner 复用 ServerEvents 现有数据/reload 子事件与 bus/golden 规则；共享事件文件不构成本票阻塞。
- 与 RELOAD_COMMIT owner 对齐 server candidate preflight、commit 点和失败保留；与 GLOBAL_STATE owner 无直接依赖，脚本状态写集只作联合失败协调。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。