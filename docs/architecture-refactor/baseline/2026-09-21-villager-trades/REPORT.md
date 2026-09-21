# 2026-09-21 Villager Trades：声明事件、候选计划与稳定查询报告（ticket 22）

> Ticket: [22: Villager Trades 声明事件与稳定查询](../../implementation-tickets/22-villager-trades.md)
> Worktree: `D:\mcmodDemo\NekoJS-mult-t22`（分支 `ticket-22-villager-trades`，认领基线 `d0974573`）
> 执行：zed-flash-22（main-session agent；deepseek-v4.1-flash subagent worktree）
> 本文件是**实施与证据**记录，不是实施授权；涉及「删除旧公开路径」的 AC10 仍需维护者 sign-off。

## 1. 范围与结论摘要

- 旧路径（静态 binding `VillagerTrades.add` → `VillagerTradeManager` 的进程级 `static PENDING`
  → reload 末端直接反射改 `VILLAGER_TRADE`/`TRADE_SET` 或静态交易池）**收口到新路径**：
  既有 `ServerEvents` 数据/reload 子事件 → root 持有的 domain collector → inert 候选计划 →
  合法 commit 点由平台/版本 Adapter 执行 registry mutation → generation/stale 绑定的只读 query。
- 公开面第一版**只有 `add` 与稳定 query**：`ServerEvents.tradeDeclaration` / `ServerEvents.tradeReload` /
  `VillagerTrades.query()`；`remove`/`replace`/`modify` 不出现在调用者 Interface、catalog、
  declaration、示例或能力承诺中。
- 事件面**零新增 bus**：两条子事件挂在既有 `ServerEvents` 组上，payload 是共享树同一份 common 类。
- 合法 mutation 只在 Adapter：26.x（`VILLAGER_TRADE`/`TRADE_SET` 可重载注册表）与 1.21.1
  （经典静态交易池）成对；fabric 装配**显式 unavailable** owner（不是无错误 no-op）。
- 旧静态路径**保持原样未删**（删除需维护者 sign-off，AC10 不勾选，见 §7）。
- 逐条 AC 判定见 §3；真跑命令与结果见 §4 与 `command-output/`。

## 2. 实现摘要

### 2.1 新增（common，零 MC/loader import）

| 文件 | 职责 |
|---|---|
| `common/.../core/villager/VillagerTradeDeclaration.java` | 一条声明的纯 JVM 规范化形态（id 字符串/数字/布尔），含规范化 `listingKey()`/`key()`（同一声明跨 reload 幂等） |
| `common/.../core/villager/VillagerTradeCandidatePlan.java` | generation-scoped inert 计划，实现 `CandidateStatePlan`；收集只累积，`preflight` 走 Adapter 只读校验，`publish` 才应用 |
| `common/.../core/villager/VillagerTradeApplier.java` | Adapter 接缝（`adapterId`/`available`/`preflight`/`apply`/`committedSnapshot`），零 MC 类型 |
| `common/.../core/villager/VillagerTradeDeclarationEventJS.java` | 数据子事件 payload：`add(tradeSet, config)` + `getAddedCount()`；非法配置抛出（整批失败，不静默跳过） |
| `common/.../core/villager/VillagerTradeReloadEventJS.java` | reload 子事件 payload：只读检查 + `declareObsolete(tradeSetId)`（释放随同一 commit 生效） |
| `common/.../core/villager/VillagerTradeQuerySurface.java` | 只读查询结果（`ACTIVE`/`STALE` + 确定性空值 + `unavailable(...)`） |
| `common/.../core/villager/VillagerTradeDomainState.java` | 域账本：committed generation、committed snapshot、`unrestored`/`retired` 记录；`query(Context)` 绑定调用方 generation |
| `common/.../core/villager/VillagerTradesFacade.java` | 过程级查表接缝（binding 无状态；装配期 `install`，root close `uninstall`） |
| `common/.../core/villager/VillagerTradeSetSnapshot.java` | 只读快照（registry epoch + 每 trade set 计数），不暴露 live registry view |
| `common/.../core/villager/VillagerTradeUnavailableException.java` | Adapter 层「不可用/此刻不可提交」的显式结果 |

### 2.2 平台/版本 Adapter（MC 类型只在这里）

| 文件 | 节点 | 说明 |
|---|---|---|
| `src/main/java/.../wrapper/event/server/VillagerTradeDomainOwner.java` | 26.x（26.1.2 / 26.2.0，两个 loader 节点共享；本文件只 import `net.minecraft.*`） | 两个合法收集点（启动收集点 + `DOMAIN_PLAN`）；commit 恢复 NekoJS 基线 → 删除上次注入项 → 注册并挂载完整新计划；反射 unfreeze + 内部 map surgery 只在此文件 |
| `versions/1.21.1/.../wrapper/event/server/VillagerTradeDomainOwner.java` | 1.21.1（成对） | 同契约，注册表形状换成经典静态池 `VillagerTrades.TRADES`/`WANDERING_TRADER_TRADES` |
| `src/fabric/java/.../wrapper/event/server/VillagerTradeUnavailableDomainOwner.java` | fabric 26.x | 显式 unavailable：正常收集，joint preflight 以 `VillagerTradeUnavailableException` 整批拒绝（原因写明 fabric 无实现） |

### 2.3 接线与公开面

- `NekoJSMod`（26.x/1.21.1）与 `NekoJSFabricMod`（fabric）经 `NekoRuntimeRoot#registerDomainCollector`
  注册 owner，并 `VillagerTradesFacade.install(owner.state())`；owner `close()` 时 `uninstall`（root close 不残留进程级状态）。
- `ServerEvents` 新增 `tradeDeclaration` / `tradeReload`（26.x 共享文件 + fabric 孪生；同名组经 `EventGroupRegistry` 合并）。
- `VillagerTradesJS` 新增只读 `query()` / `describe()`；**旧 `add`/`pendingCount` 原样保留**（仅 javadoc 增加迁移提示与「同一 trade set 不可混用」说明）。
- `ScriptManager#activeGenerationOf(Context)`（common，新增窄读点）：只有「当前 active、未 close、
  未被 kill」的 generation 才返回非负 generation，其余返回 `-1` → query 得到明确 stale。

## 3. 逐条 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 只通过既有 ServerEvents 数据/reload 子事件贡献；事件/payload/成员/side 在 catalog/golden 恰好一次，不新增第二 bus | **满足** | `Ticket22VillagerTradeEventSurfaceTest.tradeSubEventsAppearExactlyOnceOnTheExistingServerEventsGroup`（真实 catalog：payload 类、SERVER、非 cancel/非 dispatch、每 bus 恰一条、`ServerEvents.GROUP` 内各一条）；实现上两条子事件就是 `ServerEvents` 组的新 bus（无第二组/bus） |
| AC2 第一版公开面只有 add 与稳定 query；remove/replace/modify 不出现 | **满足** | `Ticket22VillagerTradeEventSurfaceTest.payloadsExposeOnlyTheFirstVersionSurface`（`add`/`getAddedCount`/`declareObsolete`/`getTotal`/`getTradeSets`/`countOf` 签名固定；遍历 payload 与计划方法名断言不含 remove/replace/modify）；示例与 MIGRATION 只写 add/query |
| AC3 query 只读快照 + generation/stale 校验；成功 reload 后新 generation 可读、旧 token 明确失败 | **满足** | `Ticket22VillagerTradeDomainTest.committedGenerationIsReadableAndOlderTokensAreExplicitlyStale`（候选期读取 = STALE；active 回调读取 = ACTIVE 且 `adapterId`/`countOf`/`total` 正确；被取代的 token = STALE 且 total/tradeSetIds/countOf 确定性空、`adapterId == null`；`query(null)` = STALE 不抛）；`querySurfaceExposesNoWritableOrLiveMember`（只读成员断言） |
| AC4 收集期只形成候选 overlay/Adapter 请求；未知 trade set、无效配置、事件失败或 Adapter 拒绝时无部分 mutation、无 pending 脏数据、旧 active 可用 | **满足** | `candidateCollectionIsInertAndCommitAppliesTheBatchExactlyOnce`（候选期 probe 读到旧 live 内容；commit 后恰好 apply 一次）；`rejectedBatchKeepsOldActivePlanAndPublishesNothing`（未知 trade set → `STATE_PLAN`，applyCount 不变、旧 active 保留、generation 不推进）；`invalidConfigurationFailsTheWholeBatchInsteadOfSkippingOneTrade`（`DOMAIN_PLAN` 整批失败）；`unavailableNodeFailsTheBatchExplicitlyInsteadOfSilentlySucceeding`；`duplicateDeclarationsInOneBatchCollapseIntoOneTrade`（同批同键幂等，不产生重复 listing） |
| AC5 合法 commit 点由平台/版本 Adapter 执行 registry epoch 与 mutation；26.x 与 1.21.1 由真实节点测试证明；共享事件面不含 loader surgery | **满足（26.1.2 / 26.2.0 / 1.21.1 / 26.1.2-fabric 真跑；26.2.0-fabric 由主会话补跑）** | 26.1.2：`.\.\gradlew :26.1.2:test --tests '*Ticket22*'` 通过（`command-output/02-...`）；1.21.1：`.\.\gradlew :1.21.1:test :26.1.2-fabric:test` 通过（`command-output/05-...`）；loader surgery 只在 Adapter：`guardLint` 276 块/431 文件/0 警告（common 零 MC/loader import）。26.2.0 与 26.2.0-fabric 由主会话补跑 BUILD SUCCESSFUL（`command-output/06-26.2.0-node-tests.txt`）。注：1.21.1 的静态池替换仍只有编译级/共享契约级证明（§6 G5） |
| AC6 reload 中断/close/watchdog/收集失败/Adapter 拒绝 → 清理候选并保留旧 active；旧 generation token 明确失败，不双重提交 | **满足（机制面真跑；中断/close/watchdog 由既有 06/07 fixture 承载）** | `rejectedBatchKeepsOldActivePlanAndPublishesNothing`、`invalidConfigurationFailsTheWholeBatch...`（候选失败 → 旧 active 保留、generation 不推进）；`rootCloseResetsTheDomainRecordsSoIndependentRootsDoNotBleed`（root close 清理域记录）；`VillagerTradeCandidatePlan.publish` 二次发布被拒（不双重提交，代码契约 + `isPublished` 断言）；close/watchdog 抢占语义由票 06/07 既有 fixture 覆盖，本票不重复 |
| AC7 脚本不再声明的既有 trade 不物理删除，只进 stale/retired；查询/诊断/迁移说明可见 | **满足** | `declarationsDroppedByANewGenerationAreRecordedAsUnrestoredInsteadOfBeingDeleted`（新 generation 声明为空 → 旧 listing 进 `unrestoredListingKeys`，仍在注册表中）；`Ticket22VillagerTradeEventSurfaceTest.declaringAReloadSubEventReleaseStaysUntilTheCommitSharesIt`（`declareObsolete` 进计划、commit 才生效）；`VillagerTradeQuerySurface.getUnrestoredListingKeys/getRetiredListingKeys` + `describe()`；MIGRATION §1/§2 |
| AC8 不定义 server/client 同步协议，不混入多人 registry 同步语义 | **满足** | 本票无 packet/网络代码：实现面只有 common 域类型 + Adapter + binding；MIGRATION §7 明示；未来同步需求另开决策（票面 Scope） |
| AC9 Fabric unavailable 通过 capability/source-trace/smoke 显式验证为明确拒绝或不可用；NeoForge 节点 supported/partial 只按实际证据 | **部分满足（结构 + 真跑证据；无 capability 矩阵/真机 smoke 条目）** | `Ticket22VillagerTradeEventSurfaceTest.nodeWithoutAnAdapterAnswersExplicitUnavailableInsteadOfSilentSuccess`（fabric owner 实例：`domain`/`scriptType` 正确；收集后计划非空、`preflight` 抛 `VillagerTradeUnavailableException` 且原因含 `Fabric`；未发布、未提交；facade 无状态时返回 `unavailable:` —— 26.1.2-fabric 节点真跑通过）；source trace：事件面在 fabric 与 26.x 同形，mutation 只在 26.x/1.21.1 owner。**缺口**：仓库现有 capability/golden 机制（`src/test/resources/golden/query/capability-matrix-*.txt`）属票 25 的 query 域，本票**未**在其上新增 villager 行；无真机 smoke（见 §6 G2） |
| AC10 旧 `VillagerTradesJS` 静态 add/pendingCount、全局 Manager 暂存和直连 registry surgery 只能在 parity、迁移表、无消费者和**维护者确认**后删除；删除后不留长期 shim | **不勾选（门禁未完成）** | 代码层旧路径**未删除**（本票选择保留，避免越过 sign-off）；已备：parity（新路径节点测试真跑）、迁移表（MIGRATION §2/§4）、旧 route 消费者清单（MIGRATION §3）、失败保留（REPORT §3 AC4）。**缺维护者确认**，故不勾选。见 §7 |
| AC11 随实现交付 add/query、generation/stale 查询与 Fabric 明确不可用的最小可运行示例和必要迁移材料；示例只用已通过 gate 的能力 | **满足（示例为源码级，未在真机执行）** | `examples/villager-trades-add-and-query.js`、`examples/villager-trades-unavailable-fabric.js`；`MIGRATION.md`；示例只用已交付并通过节点测试的能力（add/query/unavailable）。**注**：示例未经真机运行（§6 G2） |

## 4. 真跑命令与真实结果

全部原始输出在 `command-output/`（每条含 `EXIT=`）。

| # | 命令 | 结果 | 关键数字 |
|---|---|---|---|
| 1 | `.\.\gradlew :common:test --tests '*Ticket22*' --console=plain` | **BUILD SUCCESSFUL**（exit 0） | 10 tests / 0 failures（`01-common-ticket22-test.txt`） |
| 2 | `.\.\gradlew :26.1.2:test --tests '*Ticket22*' --console=plain` | **BUILD SUCCESSFUL**（exit 0） | 版本树 fixture 编译+执行通过（`02-26.1.2-ticket22-test.txt`） |
| 3 | `.\.\gradlew guardLint --console=plain` | **BUILD SUCCESSFUL**（exit 0） | 守卫块 **276** / 扫描 **431** 文件 / 超限豁免 **0** / 警告 **0**（`03-guardlint.txt`）。基线（本票前）为 275 块 / 425 文件 |
| 4 | `.\.\gradlew :common:check --console=plain` | 第一次 **BUILD FAILED**（exit 1，1 个与票无关的用例）；重试 **BUILD SUCCESSFUL**（exit 0） | 失败：`Ticket07RuntimeThreadsTest > closePreemptsInFlightCandidateViaInterrupt()`（`AssertionFailedError`，`Ticket07RuntimeThreadsTest.java:550`），1746 tests / 1 failed / 4 skipped；单独重跑该用例 BUILD SUCCESSFUL，整 task 重试亦 SUCCESSFUL（1m 59s）。原始输出：`04-common-check.txt`（失败）/ `04b-common-check-retry.txt`（重试通过） |
| 5 | `.\.\gradlew :1.21.1:test :26.1.2-fabric:test --console=plain` | **BUILD SUCCESSFUL**（exit 0） | 1.21.1 与 26.1.2-fabric 全量节点测试通过（fabric 215 tests / 1 failed→修复后 0 failed / 25 skipped）；`05-1.21.1-and-fabric-tests.txt` |
| 6 | `.\.\gradlew :26.2.0:test :26.2.0-fabric:test --console=plain`（**主会话补跑**，workdir `D:\mcmodDemo\NekoJS-mult-t22`） | **BUILD SUCCESSFUL in 1m 22s**（exit 0） | 37 actionable tasks / 22 executed / 15 up-to-date；两个 test task 均执行（非 UP-TO-DATE），即 26.2.0 与 26.2.0-fabric 均编译了共享 26.x Adapter 并跑了各自测试树；原始记录 `command-output/06-26.2.0-node-tests.txt`（由主会话提供，非本 worktree 捕获的控制台日志） |

第 4 条第一次失败是票 07 的时序用例（`closePreemptsInFlightCandidateViaInterrupt`），与
Villager Trades 无共享代码路径；单独重跑与整 task 重试均通过 → 记为**环境/时序 flaky**，
不是本票回归（详见 §6 G3）。

## 5. 本次真跑发现并修复的真实缺陷

验证过程暴露了 5 个真实缺陷（全部在本次工作区内修复，非「改测试求绿」）：

| # | 缺陷 | 触发命令 | 修复 | 影响面 |
|---|---|---|---|---|
| 1 | `VillagerTradeDomainState.noteCommitted` 用 `Set.forEach(addAll)` 复制集合 → 编译错误（`String 无法转换为 Collection<? extends String>`） | 命令 1 | 改 `new LinkedHashSet<>(acceptedDeclaredKeys)` | common 编译 |
| 2 | `VillagerTradeDomainOwner` 读可重载注册表时把 `Provider` 当 `RegistryAccess` → 26.1.2 编译失败（3 处） | 命令 2 | 按旧路径同款：`server.reloadableRegistries().lookup() instanceof RegistryAccess access` 后再 `lookup(key)` | 26.x Adapter 编译 |
| 3 | 共享树 Adapter 引入 `net.neoforged.neoforge.server.ServerLifecycleHooks` → fabric 节点编译失败（共享树在 fabric 编译单元内求值） | 命令 5 | 去掉 loader import，改用 owner 持有的 bound server（本文件保持 loader-neutral）；同时修正 `mappedRegistry` 丢失的类型参数（`找不到符号`） | 共享树 + fabric 编译 |
| 4 | **行为缺陷：空批次让未使用该域的 reload 失败**——Adapter 的 `preflight` 在「没有任何声明」时仍走注册表可用性校验并抛 `VillagerTradeUnavailableException`；且 `VillagerTradeDeclarationEventJS.add` 在空配置上抛 `missing required 'cost' entry`（由测试桩以 `Map`（host 对象）调用触发，真实 guest 回调不受此影响） | 命令 5（fabric 节点全量测试） | （a）两个 Adapter 的 `preflight`：**声明为空 = 无请求 → 直接通过**，不因未使用该域而让 reload 失败；（b）`apply`：空批次且无 NekoJS 持有状态 → no-op，不算失败；（c）fabric collector 在「dispatch 后计划为空」时**不注册计划**（零参与），未触碰该域的 reload 与改动前完全一致 | **行为修复，影响面 = 所有节点**：修复前，任何一次 SERVER reload 都会因本域失败（无论脚本是否声明交易）；修复后只有真正声明了交易的批次才参与联合边界。这是本票最重要的一条修复，也是「不静默 no-op」与「不误伤无关 reload」之间的边界 |
| 5 | 测试侧：fabric 断言「不可用节点不记录声明」与设计语义（正常收集、整批在 preflight 拒绝）相反；且用 host `Map` 传配置（非 member 可读）导致 payload 抛错 | 命令 5 | 断言改为「计划含 1 条声明、未发布、域快照为 0」；配置改用真实 guest Context 内 `eval` 的对象（并 `enter()`） | 仅测试 |

修复 #4 之后，命令 1/2/4/5 全绿；修复 #5 是断言口径纠正（不是放宽断言：新增了
`isPublished`/`committedSnapshot().total()` 两条更强的断言）。

## 6. 未验证项与 owner

| # | 项 | 现状 | owner/建议 |
|---|---|---|---|
| G1 | ~~26.2.0 与 26.2.0-fabric 未执行~~ → **已关闭（主会话补跑通过）** | 26.2.0 与 26.2.0-fabric 的 test task 均已执行且 BUILD SUCCESSFUL（见右侧命令与 `command-output/06`），两个节点编译了共享的 26.x Adapter 并跑了各自测试树 | 主会话补跑 `.\\.\gradlew :26.2.0:test :26.2.0-fabric:test --console=plain`（workdir `D:\\mcmodDemo\\NekoJS-mult-t22`）：**BUILD SUCCESSFUL in 1m 22s**，37 actionable tasks / 22 executed / 15 up-to-date，两个 test task 均执行（非 UP-TO-DATE）；原始记录 `command-output/06-26.2.0-node-tests.txt`。无剩余缺口 |
| G2 | **无真机 smoke、无客户端/服务端实机验证** | 未启动游戏/服务器；交易是否真的出现在村民报价、`/nekojs reload server` 的真实命令输出、fabric 实机的 unavailable 提示均未在实机观察 | 用 `minecraft-mod-mcp` 按 `examples/` 脚本跑一次 26.1.2 与 26.1.2-fabric；owner：主会话/票 34 |
| G3 | `Ticket07RuntimeThreadsTest.closePreemptsInFlightCandidateViaInterrupt` 时序 flaky | 全量 `:common:check` 第一次失败、单独重跑与整 task 重试通过；与本票无共享代码路径 | 属票 07 测试基建；本票不处置 |
| G4 | 25 的 capability/golden 机制未新增 villager 行 | `src/test/resources/golden/query/capability-matrix-*.txt` 是 query 域（票 25）矩阵；本票未改它（避免越界改他人 golden），fabric unavailable 的证据落在 §3 AC9 的结构断言 | 若需要在 capability 矩阵里显式登记 villager，由票 25/34 owner 决定 |
| G5 | 1.21.1 Adapter 只有结构级/编译级证明 + 共享契约 fixture | 1.21.1 的 `VillagerTradeDomainOwner` 通过编译与节点全量测试（含共享事件面 fixture），但**未**在 1.21.1 上单独跑「声明→静态池真的被替换」的断言（该断言需要真实 vanilla 注册表/服务器生命周期） | 主会话在 ModDev/实机环境复核；owner：票 34 |
| G6 | 旧路径仍在（AC10 门禁） | 见 §7 | 维护者 sign-off |

## 7. AC10 删除门禁（明确不勾选）

前提已备：

1. **替代路径 parity**：`ServerEvents.tradeDeclaration` / `tradeReload` / `VillagerTrades.query()` 已交付；
   26.1.2 / 26.2.0 / 1.21.1 / 26.1.2-fabric 节点测试真跑，26.2.0-fabric 由主会话补跑（§4、`command-output/06`）。
2. **失败保留**：§3 AC4/AC6 的三条整批失败用例 + §5 修复 #4。
3. **迁移表**：`MIGRATION.md`（§1–§4），含「同一 trade set 两条路径不可混用」。
4. **旧 route 无消费者证据**：`MIGRATION.md` §3——仓库内消费者只有旧 binding 自身、两个
   `ServerEventListener`（26.x + 1.21.1）、两个 `NekoJSCommands`、一个 wiki 段落；无测试断言旧静态
   路径，无其它域引用。

**缺**：维护者 sign-off（工单 §Human input note 明确要求删除是发布门禁）。因此 **AC10 不勾选**，
本票**未删除**任何旧公开符号，也未把旧路径改写成 shim（避免造成「长期兼容双路径」）。

## 8. 集成与协调

- 与 14（事件面）：复用既有 `EventGroup`/`EventBusJS`/`catalog` 派生，无第二 bus。
- 与 39（domain collector 接缝）：复用 `CandidateDomainCollector` + `CandidateStatePlan` +
  `ReloadPhase.DOMAIN_PLAN`，与 `ModificationDomainOwner` 同款结构（root 持有、AutoCloseable、诊断）。
- 与 10（联合边界）：本域计划经同一 `GenerationGlobals` 联合预检/发布，失败不半提交。
- 与 06/07：候选惰性与 close/watchdog 抢占语义直接复用，本票不新增调度。
- 与 16：facade 形态（`VillagerTradesFacade`）与 `DynamicRegistryFacade` 同构，但**不**复用其
  运行时；两者各自持有自己的域状态。

## 9. 术语与文件索引

- common 域：`common/src/main/java/com/tkisor/nekojs/core/villager/*`
- 26.x Adapter：`src/main/java/com/tkisor/nekojs/wrapper/event/server/VillagerTradeDomainOwner.java`
- 1.21.1 成对：`versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/event/server/VillagerTradeDomainOwner.java`
- fabric unavailable：`src/fabric/java/com/tkisor/nekojs/wrapper/event/server/VillagerTradeUnavailableDomainOwner.java`
- 事件面：`src/main/java/.../bindings/event/ServerEvents.java`、`src/fabric/java/.../bindings/event/ServerEvents.java`
- binding：`src/main/java/.../bindings/static_access/VillagerTradesJS.java`（+ 1.21.1 成对）
- 接线：`NekoJSMod`、`NekoJSFabricMod`；generation 读点：`ScriptManager#activeGenerationOf`
- fixture：`common/src/test/java/com/tkisor/nekojs/core/villager/Ticket22VillagerTradeDomainTest.java`、
  `src/test/java/com/tkisor/nekojs/wrapper/event/server/Ticket22VillagerTradeEventSurfaceTest.java`
- 证据：`examples/`、`command-output/`