# 2026-09-15 runtime threads：同类型串行、close 优先与 watchdog 隔离恢复报告（ticket 07）

> 工单：`docs/architecture-refactor/implementation-tickets/07-runtime-threads.md`（票面 Status/AC 勾选未动，关票由主会话负责）。
> 分支：`ticket-07-runtime-threads`（worktree `D:\mcmodDemo\NekoJS\.worktrees\t07\NekoJS-mult`），基线 `47f4a444`（master）。
> 核心 spec：[`../../specs/09-reload-candidate-state-and-thread-contract.md`](../../specs/09-reload-candidate-state-and-thread-contract.md)；辅助 [05](../../specs/05-runtime-lifecycle-and-data.md)、[实施交接单](../../implementation-handoff.md)。
> 前置交付（消费不推翻）：ticket 06 的 candidate/active generation + 单一 commit 点（`2026-09-12-reload-candidate`）、ticket 05 的 `NekoRuntimeRoot`/`NekoRuntimeAssembly`。
> 证据：本目录 `evidence/verification-commands.md`（全部验证命令、结果、复现命令、逐类用例数）。
> 接管背景：opencode-agent 半成品快照在 `wip/opencode-ticket07-snapshot-20260915`，取舍见 §6。

**范围边界（工单明文，全程遵守）**：不做 GLOBAL_STATE root map 联合事务（票 10）、不做 NativeEventsJS generation 化（W7）、不动 precommit 阶段 `binding.close(type)`/`errorTracker.clearByType` 账本位次（W6/票 10）、不做性能阈值、不进 feature 域。

---

## 1. Commit 清单

| commit | 内容 |
|---|---|
| `0f61fe0a` | `feat(core): ticket 07 SyncEvalWatchdog 墙钟求值守卫 + Graal 行为探针` |
| `d0310224` | `feat(lifecycle): ticket 07 ScriptLifecycleGate 同类型生命周期调度门` |
| `5541f9f9` | `feat(script): ticket 07 owner-thread 调度接线（gate/watchdog/close 抢占/隔离失败）` |
| `c0526303` | `fix(error): ticket 07 WORLD 包脚本 record 不再因 relativize IAE 掩掉 kill 失败` |
| `fef6d8d4` | `test(script): ticket 07 并发/重入/close 抢占/watchdog fixture + 转正 ticket 06 时间窗探针` |
| （本 commit） | `docs(baseline): ticket 07 runtime threads report + evidence` |

未 push、未合并、未触碰 master 与其它 worktree。

## 2. 机制设计（消费票 06 机制，不另起炉灶）

### 2.1 串行与调度面（AC1/AC2/AC4/AC9）

- **跨线程串行**：`ScriptManager` 既有实例锁承担——非 owner 线程的 lifecycle 请求在实例锁 monitor 队列排队，与 owner 上的请求单一序列执行。并发 reload 的事实单 candidate 由「锁 + 每 reload 恰好一个候选」结构保证（fixture：generation 恰好 +N、入口恰好执行 1+N 次）。
- **判决面**：新增 `core/lifecycle/ScriptLifecycleGate`（每 manager 一实例）。`loadScripts`/`reloadScripts`/`runTestScripts`/`reloadScriptFile` 公开入口先过门，判决四种：`EXECUTED` / `REJECTED_REENTRANT`（同线程重入或 managed 回调内）/ `REJECTED_CLOSING`（close 已请求）/ `REJECTED_CLOSED`。拒绝以结构化失败外显：抛出式入口得到 `NekoReloadException`（domain=`<action>-rejected:<DECISION>`，phase=PREPARATION）；非抛出式 `requestReload()` 直接返回判决枚举。唯一放行的嵌套是 STARTUP reset+load 的既有形状（RELOAD 内一层 LOAD，gate 以 `depth==1 && outer==RELOAD` 限定）。
- **回调内 reload**：`EventBusJS` 四个分发点与 `NekoNodeTimers.execute` 在 guest 回调执行体前后配对 `ScriptManager.noteCallbackEnter/Exit()`（ThreadLocal 深度，不加锁不触碰 Context）。回调内的同线程 lifecycle 请求被门判 `REJECTED_REENTRANT`——不递归进自身队列、不同步等待自身；外部可观察结果 = 判决枚举或结构化失败。
- **显式调度入口**：`ScriptManager.scheduleOnOwner(Callable)`——guest-created thread 访问 managed lifecycle 的唯一入口（实例锁队列串行，closed 时明确拒绝）。guest 的高级 Java/线程能力（allowThreads 等）不收紧（fixture 验证 guest 线程照常创建并执行 Java 工作）。
- **四类 owner 入口复用（AC9）**：SERVER/CLIENT/STARTUP 的 load/reload 与 TEST 的 runTests 继续经票 05 的 `NekoRuntimeRoot` 生命周期入口（fixture `fourOwnerEntriesReuseRootLifecycleGate` 覆盖四类 + root close 后全部拒绝）；没有第二 manager、没有 root 旁路。平台侧 owner 纪律既有事实保持：SERVER 命令/事件钩子在 server thread、CLIENT 命令面经 `ClientReloadExecutor` 转投 Render 线程（`NekoJSCommands.reloadType/reloadFile`）、STARTUP 走 bootstrap 串行入口、网络 receiver 先 hop 再分发（`NetworkMessageHandler`）。

### 2.2 close 优先与取消点（AC3）

`close()` 新顺序：**锁前** `requestClose()`（volatile 标志，尚未拿到锁的 reload/load 随后被门拒绝）→ `interruptCandidateBestEffort()`（对在途候选 `Context.interrupt(5s)`，中断超时才由守卫/兜底路径强关）→ 拿实例锁做确定性 teardown（`discardCandidate` 弃在途/未开始候选 → `fullReloadCleanup` → `binding.close` → 关 active session/streams → `markClosed`）。幂等（重复 close 空清理不抛）；同线程在 lifecycle 体内嵌套 close 只留标志并延迟（不内联拆除自己的状态机）。

在途 candidate 的**取消点**三处，外部结果收敛为 `domain=close-preempted` + generation 不变：
1. 候选求值中断（interrupt → `executeEntry` catch → `onContextKilled` → `candidateKilled` → EXECUTION/close-preempted）；
2. 脚本间检查（`loadCandidateScripts` 每脚本启动前看 closeRequested，未开始脚本不再启动）；
3. COMMIT 前检查（候选全部执行完但未提交 → 弃候选 → COMMIT/close-preempted，永不出现半激活 generation 或 close 后 commit）。

### 2.3 watchdog：墙钟守卫与隔离恢复（AC5/AC6）

- **`core/SyncEvalWatchdog`**：`executeEntry` 同步求值段 arm/disarm 配对的墙钟守卫（`scriptRunawayTimeoutSeconds`，0=禁用）。超时从单线程守护调度器 `Context.interrupt(Duration)`——语句检查点看不见的空循环 `while(true){}`（票 06 探针缺陷）由此可终止。触发后 `wasTriggered()` 参与 kill 归因（不依赖异常类型/文本）；中断 5s 超时（guest 卡在不可中断宿主调用）才降级 `close(true)`。清理仍在 owner 线程完成。
- **终止 candidate（AC5）**：走票 06 既有 `candidateKilled` 记账 → EXECUTION 失败（domain=`candidate-killed`）→ `discardCandidate`（候选 timer/listener/Context 全关）；active 的监听器/timer/state 原样（fixture：事件继续命中、timer 计数不变、旧 state 可读）；再次显式 reload 可创建新 candidate 并提交。
- **终止 active（AC6）**：`markContextKilled` 对 active 分支新增 `lifecycleGate.markActiveFailed()`——隔离失败可观察（`isActiveFailed()`）。分发停止依赖既有 `isContextDead`（contextKilled → 跳过监听器/timer 分发）；**不自动创建第二个 active**：timer flush 与事件分发路径从不触发重建，唯一恢复入口是显式 reload/load（`getOrCreateEnvironment` 重建与 `commitGeneration` 成功时才 `clearActiveFailed`，且 `loadScripts` 结束时若本轮刚被 kill 则保留标记）。

### 2.4 AC8：旧 Context 私有锁路线删除（探针驱动）

实证探针（`SyncEvalWatchdogTest`）确立两个 Graal 事实：**并发进入同一 Context 被 Graal 拒绝**（Multi threaded access）；**跨线程 interrupt 可打断在途求值**。据此删除了 `EventBusJS` 四个分发点的 `synchronized(context)` 旧路线（其注释记录的动因「命令线程 reload vs Render/tick 分发」已被 owner-thread 转投取代），替换为回调深度标记。删除依据：分发只在 owner thread；lifecycle 串行由实例锁+门承担；同一 Context 的并发进入兜底是 Graal 单线程约束本身（后进入者以回调错误呈现，不产生状态破坏）。**保留** manager 侧 `synchronized(ctx)`（executeEntry / reloadScriptFile / flush / close）——那是 manager 对自有资源的独占标记（lifetime 内仍是有效互斥面），不是被删的「分发序列化路线」。残余角落与风险见 §5-G3。

### 2.5 DefaultErrorTracker.record 的 WORLD 包 IAE（与 07 相交的最小修复）

票 03 基线记录的 `record:70` relativize IAE（WORLD 包脚本在存档侧、不在 nekojs root 下）发生在 `executeEntry` 的 **catch 体内**——票 07 起 watchdog 终止的 kill 归因与错误面板条目都依赖该路径不抛（否则二次异常掩掉 kill、面板丢条目），属 07 失败状态语义交集，故本票最小修复：relativize 失败回退原样路径文本（`relativeScriptPath`）。WORLD 包加载路径的其余修复（如 `ScriptExecutor:48` 的 requirePath relativize）仍归票 19，未越权。

## 3. 场景矩阵（fixture → 断言 → 位置）

| # | 场景 | 可观察断言 | 位置 |
|---|---|---|---|
| AC1 | 4 线程并发 reload | generation 恰好 base+4、入口恰好执行 1+4 次（无交叠损失/无双执行） | `Ticket07RuntimeThreadsTest.concurrentReloadsAreSerializedWithSingleCandidate` |
| AC1/门 | 门契约全量 | 串行进入/重入拒绝/嵌套放行一层/回调拒绝/close 优先/关闭后拒绝/嵌套 close 拒绝/exit 无 enter 抛 | `ScriptLifecycleGateTest`（11 用例） |
| AC2 | 真实事件分发回调内 requestReload / reloadScripts | 判决 `REJECTED_REENTRANT` 文本；结构化失败 domain 含判决名；generation 不动；回调外同线程恢复 EXECUTED | `Ticket07RuntimeThreadsTest.reloadInsideManagedCallbackIsRejectedNotRecursive` |
| AC3 | close 抢占在途候选（中断路径） | close 30s 内完成；reload 失败 domain=`close-preempted`；generation 不变；active 脚本值不变；幂等 close；close 后 reload 拒绝（REJECTED_CLOSED） | `closePreemptsInFlightCandidateViaInterrupt` |
| AC3 | close 抢占（取消点收敛 + 未开始脚本） | domain=`close-preempted`（phase EXECUTION 或 COMMIT）；`second.js` 绝不执行；永不 commit | `closePreemptsRemainingCandidateScriptsAndNeverCommits` |
| AC3 | close 优先于未开始 reload | close 后 reload/load 均 REJECTED_CLOSED 结构化失败 | `closeIsPrioritizedOverNotStartedReload` + `ScriptLifecycleGateTest.closeIsPrioritizedOverNotStartedReload` |
| AC4 | 非 owner（guest）线程 | `scheduleOnOwner` 排队串行 commit（generation +1）；直接调用公开入口同样 monitor 队列排队成功；guest 高级 Java 能力不收紧 | `nonOwnerThreadLifecycleIsQueuedThroughExplicitEntry`、`guestThreadAdvancedCapabilitiesAreNotTightened` |
| AC5 | watchdog 终止 candidate（statementLimit=0、墙钟 2s） | 60s 内失败 phase=EXECUTION、domain=`candidate-killed`；generation 不变；active 监听器继续 +1/timer 计数不变/旧 state 可读；`isActiveFailed`=false；显式 reload → 新 candidate 提交且监听器恢复 | `candidateWatchdogRetainsActiveAndRecoversByExplicitReload` |
| AC6 | watchdog 终止 active | loadScripts 60s 内返回；`isActiveFailed`=true；timer flush 不重建不清除（generation 不变）；事件分发不再命中被杀 Context（hits=0）；显式 reload → 标记清除、generation+1、分发恢复 | `activeWatchdogEntersIsolatedFailureAndRecoversByExplicitReload` |
| AC7 | SERVER+CLIENT owner 并发 | 两 manager 独立 generation 各 +1、各自 recorder 值正确 | `serverAndClientOwnersReloadIndependently` |
| AC7 | 锁顺序/无自等待/无双重 callback/清理幂等 | 回调拒绝杜绝自等待；并发用例无双执行；close 幂等用例；全套 1448 用例无死锁超时 | 上述用例合集 |
| AC8 | 分发点旧 monitor 删除 + Graal 兜底实证 | 并发 eval 被拒（探针）；跨线程 interrupt 可打断（探针）；票 05/06 既有回归全绿（行为保持） | `SyncEvalWatchdogTest.concurrentEvalOnSameContextIsRejectedByGraal`、`interruptFromOtherThreadAbortsRunningEval` + 既有回归套件 |
| AC9 | 四类 owner 入口复用 root | root.reload(SERVER/CLIENT/STARTUP)、root.runTests() 全成功（STARTUP 显式 nonTransactional）；root close 后同门拒绝 | `fourOwnerEntriesReuseRootLifecycleGate` |
| 票06缺口 | 时间窗 runaway 终止（@Disabled 移除） | `scriptStatementLimit=0`+`runaway=2s` 终止 `while(true){}`，reload 失败 phase=EXECUTION | `Ticket06RunawayProbeTest.runawayWindowAloneTerminatesInfiniteLoop`（@Disabled 已移除，历史结论文档保留） |
| 07相交修复 | WORLD 包 record 不抛 IAE | record 返回 ScriptError、面板保留条目、路径原样 | `DefaultErrorTrackerTest.recordOfWorldPackScriptOutsideRootDoesNotThrow` |

## 4. 验证结果（全部绿）

| 命令 | 结果 |
|---|---|
| `./gradlew :common:check --console=plain`（`--rerun` 复核） | **绿**：195 测试类 / 1448 tests / 0 失败 |
| `./gradlew :common-api-processor:test --rerun --console=plain` | **绿**：13 tests / 0 失败 |
| `./gradlew guardLint --console=plain` | **绿**：守卫块 248、扫描 331 文件、豁免 0、警告 0 |
| `./gradlew :26.1.2:check --console=plain`（`--rerun` 复核） | **绿**：38 测试类 / 173 tests / 0 失败 |
| `npm run test:probe-types` | 未运行（不适用）：零 probe/declaration 面改动 |

普通测试未写任何 golden。详见 `evidence/verification-commands.md`。

## 5. 已知缺口与建议 owner

| # | 缺口 | 说明 | 建议 owner |
|---|---|---|---|
| G1 | 回调内失控循环仍是 watchdog 盲区 | `SyncEvalWatchdog` 只覆盖同步求值段（arm/disarm 配对）；事件监听器/timer 回调内的空循环 `while(true){}` 没有墙钟布防（语句检查点同样看不见）。本票按「watchdog 终止 candidate（构建中）/active（初始 load）」的票面范围实现，回调路径未扩张（分发点 arm 会引入长回调误杀语义问题，需单独设计） | 票 19/后续 hardening（如 dispatch 段可选 arm + 独立超时配置） |
| G2 | 非 owner lifecycle 的「owner 队列」是 manager 串行队列，不是平台线程转投 | common 层无法获知平台 owner 线程；scheduleOnOwner/monitor 队列保证「不并发触碰」，但 work 仍在调用者线程执行。平台侧既有转投（ClientReloadExecutor、server-thread 命令、网络 hop）覆盖全部现存生产入口；严格「转投到平台 owner 线程」需平台注入 dispatcher（未在本票做，避免 5 节点平台接线扩张） | 主会话裁决是否立票（平台 dispatcher 注入）；现存入口已满足票面「复用既有 owner 入口」 |
| G3 | EventBusJS 分发点 monitor 删除的残余角落 | 违反 AC4 契约的调用者（非 owner 线程直接跑 lifecycle 绕过显式入口）恰逢 owner 分发时，Graal 拒绝后进入者、以回调错误呈现（探针实证），不再是无感等待。属契约违反路径的降级表现，非状态破坏 | 记录在案；若后续出现真实调用方，按 G2 的平台 dispatcher 收口 |
| G4 | WORLD 包加载路径其余 IAE 未修 | `ScriptExecutor:48` 的 requirePath relativize 对存档侧脚本仍会抛（脚本根本不执行）。本票只修了与 07 失败状态语义相交的 `DefaultErrorTracker.record` | 票 19 |
| G5 | `interrupt(5s)` 等待语义 | close 抢占与 watchdog 的 interrupt 最多阻塞 5s 等待安全点；guest 卡在不可中断宿主调用（如 Java sleep/IO）时 close 需等宿主调用返回（或 watchdog 侧 5s 后强关）。已知边界，非死锁 | 记录在案；如需收紧可在配置面暴露等待上限 |
| G6 | 烟测未扩展 | `bench/smoke-reload/` 未加 07 检查项：AC 观察点已由确定性单测 fixture 覆盖；in-game smoke（live server + RCON）本轮未跑 | 主会话决定是否补一轮 in-game smoke（可用 minecraft-mod-mcp） |
| G7 | ticket 06 遗留的 precommit 账目缺口不变 | `errorTracker.clearByType`/`binding.close(type)` 仍在候选构建前发生（域契约位次），失败不回滚 | 票 10 / W6/W7（票 06 报告 §5 已记录，本票不越权） |

## 6. 与前人快照（`wip/opencode-ticket07-snapshot-20260915`）的取舍

参考其架构方向（gate + watchdog + 回调深度标记 + close 抢占标志），全部实现独立重写并验证；逐项取舍：

| 快照内容 | 取舍 | 理由 |
|---|---|---|
| `ScriptLifecycleGate`（门+判决枚举+回调深度） | **采纳思路、重写** | 增补：CLOSE 永不嵌套执行（快照允许 close 在 RELOAD 体内内联 teardown——会边跑边拆自己的状态机）；嵌套放行限定 `depth==1`（快照允许任意深度 LOAD-in-RELOAD）；exit 无配对抛异常的防御 |
| `SyncEvalWatchdog` | **采纳思路、重写** | 同为 CAS 单发 + noop 禁用态；本票版本把布防状态显式化（构造即 armed，NOOP 独立路径）、补齐中断超时降级 `close(true)` 的处置注释与触发/解除竞态窗口的说明，并在类注释明确回调盲区（§5-G1） |
| ScriptManager +251 行调度改动 | **采纳结构、重写** | 快照的 close 抢占测试依赖 `awaitGate()` 宿主 latch——close 会阻塞在 monitor 上等 latch 超时（非确定性、≥20s）；本票改为 guest 侧自旋条件 + interrupt 取消点（确定性、<30s）。快照未做脚本间取消点与「close 后 commit 前检查的收敛断言」 |
| EventBusJS/NekoNodeTimers 回调标记 | **采纳** | 同点位同语义；NekoNodeTimers 处修正了快照编辑引入的缩进错位 |
| EventBusJS 分发点 monitor | **快照未删；本票删除（AC8）** | 以探针实证为前提（并发进入被 Graal 拒绝、interrupt 可打断），配合 owner-thread 纪律完成 AC8 的删除项；残余角落记录 §5-G3 |
| `Ticket07RuntimeThreadsTest`（606 行） | **结构参考、全部重写** | 修掉宿主 latch 死锁风险（见上）；回调内断言改为经 Recorder Java 方法中转（快照把 `ScriptManager` 直接暴露给 guest JS，受 ClassFilter/HostAccess 面影响且违背「不暴露 managed lifecycle 对象」的契约方向）；补 SERVER+CLIENT 独立、四类 root 入口、AC5 timer/state 观察、AC6 分发停止观察、取消点收敛断言 |
| `DefaultErrorTracker` WORLD 包 IAE | **快照未涉及；本票新增** | 票面已知缺口核对后的 07 相交最小修复（§2.5） |
| 票面/takeover 文档编辑 | **不采纳** | 票面 Status/AC 勾选按纪律不动 |

## 7. 结论

8 条 AC 中：AC1/AC2/AC3/AC5/AC6/AC7/AC9 **满足**（fixture 全绿，见 §3 矩阵与 §4）；AC4 **满足（common 层语义）**——非 owner 请求经 manager 串行队列/显式入口进入，不并发触碰 Context/binding/listener/timer，guest 高级能力不收紧；「排队到平台 owner 线程」的平台转投在既有入口已成立（ClientReloadExecutor 等），通用平台 dispatcher 注入未做（§5-G2，建议主会话裁决是否立票）。AC8 的删除以分发点 monitor 移除完成，manager 侧 ctx monitor 作为资源所有权标记保留（依据 §2.4）。已知缺口与 owner 见 §5。
