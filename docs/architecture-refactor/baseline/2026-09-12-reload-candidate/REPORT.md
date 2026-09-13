# 2026-09-12 reload candidate / 阶段结果与 owner-thread commit 点报告（ticket 06）

> 工单：`docs/architecture-refactor/implementation-tickets/06-reload-commit.md`（W1：candidate generation 隔离 + 单一 commit 点 + 阶段结果）。
> 分支：`ticket-06-reload-commit`（worktree `D:\mcmodDemo\NekoJS\NekoJS-t06\NekoJS-mult`），基线 `98478559`。
> 核心 spec：[`../../specs/09-reload-candidate-state-and-thread-contract.md`](../../specs/09-reload-candidate-state-and-thread-contract.md)（候选状态与线程契约，本票验收口径来源）；辅助 [`05-runtime-lifecycle-and-data.md`](../../specs/05-runtime-lifecycle-and-data.md)。
> 前置交付（消费不推翻）：[`../2026-09-12-runtime-root-refactor/REPORT.md`](../2026-09-12-runtime-root-refactor/REPORT.md)、总账 [`../2026-09-12-runtime-ledger.md`](../2026-09-12-runtime-ledger.md)。
> 证据：本目录 `evidence/`（26.1.2 runServer 会话 28 项 checks 全绿 + RCON 应答 + gz 日志）。

**范围边界（工单明文，全程遵守）**：本票只做 **generation-owned reload 交接与阶段结果**。不做 GLOBAL_STATE 的 root map 联合事务（10 号票 I3）、不做完整 owner-thread 队列/重入/watchdog 调度（07 号票）；STARTUP 的不可逆平台注册保留显式 restart/unsupported 边界，不重做 registry bootstrap。

---

## 1. Commit 清单

| commit | Phase | 内容 |
|---|---|---|
| `32822bec` | 1 | `test(script): ticket 06 phase 1 characterization for reload generation contract`（367 行 characterization，先钉住当前行为，作行为保持锚） |
| `208fbb87` | 2–4 | `feat(lifecycle): ticket 06 candidate generation isolation for transactional reload`（candidate/active 记账分离、EventBusJS 挂起注册收集、阶段拆分、commit 点、失败丢弃、结构化失败结果 + 命令呈现） |
| `fef2935b` | 5 | `test(script): ticket 06 commit-point and candidate-only callback fixtures`（commit 无双执行 / 候选 timer 收集-提交-丢弃 / TEST 面 / candidate-phase 生产路由） |
| `cc2748d6` | 6 | `feat(lifecycle): ticket 06 STARTUP explicit non-transactional boundary + reload result phases`（`ReloadPhase`、`ReloadResult.generation/phase`、STARTUP 非事务标记） |
| `8ef55025` | 6（补） | `feat(lifecycle): ticket 06 STARTUP 非事务边界在入口外观显式化`（`nonTransactional()`/`requiresLoaderRestart()` + 三个节点命令面显式提示 + 三节点统一 `NekoReloadException` 呈现） |
| `10f95658` | 7 | `test+bench(lifecycle): ticket 06 reload 烟测 runner、commit witness 与资源上限发现`（`bench/smoke-reload/` runner + fixture + 测试补充 + `@Disabled` 资源上限复现件） |
| 本 commit | 8 | `docs(baseline): ticket 06 reload candidate report + gz evidence`（本报告 + evidence 归档） |

未推送、未合并、未触碰 master（另一会话在 `D:/mcmodDemo/NekoJS` 的 master 上做工单 31）。

---

## 2. generation 设计与 commit 点时序

### 2.1 数据结构（`common/.../script/ScriptManager.java`）

| 状态 | 字段 | 归属/可见性 |
|---|---|---|
| active 环境 | `RuntimeEnvironment runtime`（`context` + `nodeRuntime` + `outStream` + `errStream` **成对** volatile 发布） | 生产路由唯一读点（tick flush、`isContextDead`、单文件 reload） |
| active 存活标记 | `contextKilled`（语句上限 kill → 下次取用重建） | 进程内每 ScriptType 一份 |
| 候选环境 | `candidateContext`（构建期临时存活）、`candidateKilled`、`candidateKillSource` | 仅 owner thread 读写；`isContextDead` 用 `equals` 比较（Graal 的 `Value.getContext()` 可能是等值包装对象） |
| 候选挂起注册 | `List<EventBusJS.PendingListener> pendingListeners` | commit 前**不进总线、不进 type 分桶 mirror**；失败弃置 |
| generation 序号 | `generation`（单调递增） | 初始环境创建 / kill 重建 / commit 各 +1；失败候选号 = `generation + 1`，不写回 |
| 共享静态（不变） | `CONTEXT_TO_MANAGER`（强引用 Map，销毁路径唯一经 `closeRuntimeResources`） | Context → manager 反查，候选 Context 建好后即登记 |

进程级身份按 spec 09 user story 13–16 保持不变：`ScriptEventBridge`、`IPluginRuntime`、Plugin Runtime/Point/Contributor/Extension Handle、`NekoSharedEngine`、`NekoGlobal.SHARED` 都不随 generation 重建；只有 Graal Context、node runtime、script binding、listener token、timer、prepared module session 按 generation 创建/校验/释放。

### 2.2 commit 点时序（`commitGeneration`，owner thread 同步临界区）

```
reload(type)  [非 STARTUP]
 ├─ errorTracker.clearByType(type)                        ← 与既有实现同位次（候选错误可见）
 ├─ for binding : bindings(type) → binding.close(type)     ← 「先清账本再注册」域契约，位次不变（快照/回滚归域 Adapter）
 ├─ PREPARATION  createContext(type)        失败 → phase=PREPARATION, domain=candidate-context
 ├─ BINDING      installEnvironmentBindings 失败 → phase=BINDING,     domain=binding-install
 ├─ EXECUTION    discoverWithPacks → fireBeforeScriptsLoaded → 逐脚本 executeEntry
 │                 · 监听器：EventBusJS 经 ScriptManager.collectPendingListener 收进候选
 │                 · timer   ：只进候选 node runtime（生产 flush 读 active runtime）
 │                 · candidateKilled → 失败：phase=EXECUTION, source=首个触发脚本, domain=candidate-killed
 └─ COMMIT（单一原子切换，顺序即契约）：
      1. clearListeners(type)        ← 旧 generation 停止接收新 callback（候选监听器从未上总线，故整类型清空 == 旧代清扫）
      2. runtime = candidate; scripts = candidate; generation = 候选号; contextKilled=false; candidateContext=null
      3. pendingListeners → activate()（新 generation 成为唯一新 callback 接收者）
      4. NekoModulePipelineCache.clear(type) + NekoEsmVirtualModuleRegistry.clear(type)（旧 module session）
      5. closeRuntimeResources(旧环境)：node runtime/timer → Context → out/err 流（所有权顺序）
```

失败路径 `discardCandidate`：挂起注册直接弃置（从未上总线）、候选 Context/timer/streams 按 timer → Context → streams 关闭；`runtime`/`scripts`/`generation`/active 监听器原样保留。STARTUP 与单文件 reload 是非候选路径，结果 phase 显式标记（`STARTUP` / `FILE`）。

### 2.3 与 05 交付的接合点

commit 点挂在 05 收口的唯一 root 入口 `NekoRuntimeRoot.reload(ScriptType)`（NeoForge 构造期 final local / Fabric `runtimeRootOrNull()` 同包 accessor），没有新增第二 owner、第二 manager、静态 root 或 service locator；`NekoRuntimeAssembly` 与 `NekoRuntimeRootLifecycleTest` 的 close 语义未改（`closeSilently` 顺序 manager → bridge → resources 保持）。

---

## 3. 场景矩阵

### 3.1 单测（`common`，确定性、无 MC）

| 场景 | 断言（可观察结果） | 位置 |
|---|---|---|
| 成功 reload：入口重跑一次 + commit 后无双执行 | 旧监听器计数冻结、新监听器恰好 +1、稳态继续 | `ScriptReloadGenerationTest.successfulReloadRunsScriptsOnceAndSingleExecutionAfterCommit` |
| 连续两次 commit：旧 Context 关闭、generation 单调、只有最新代执行 | `contextV1/V2.eval` 抛、`contextV3` 可用、计数 v1=v2=0/v3=1 | 同上 `consecutiveCommitsKeepSingleExecutionAndCloseOldContext` |
| candidate 阶段生产事件仍由 active 服务 | 脚本内 `Trigger.now()` 中途 post → active 命中 ≥1、候选 0 | `candidatePhaseEventsServedByActiveGeneration`（AC1） |
| 失败（候选被资源上限终止）：active 保留、旧 state 可读、事件仍可收 | 旧 Context 仍当前、旧监听器计数继续 +1 | `failedReloadKeepsOldContextAndScriptState`（AC2） |
| 失败：候选监听器不落总线 | 失败后 `busHasListeners()` 仍真、候选计数 0 | `failedReloadKeepsActiveListenersOnBus` / `failedReloadDiscardsCandidateListenersOnly` |
| 失败结果外部可见字段 | `NekoReloadException.report()`：phase=EXECUTION、generation≥1、owner=`ScriptManager[server]`、`describe()` 含 `phase=` | 同上（AC3） |
| 清扫只在 commit 发生 | 失败：`clearListeners` 0 次；成功：恰好 1 次 + 清扫后仅新代在总线 | `bridgeLifecycleCallCounts` |
| commit 后 timer 生产分发单一归属 | 旧 interval 增量冻结、新 interval 持续 | `afterCommitProductionTimerDispatchBelongsToNewGenerationOnly`（AC4） |
| pending timer：随提交分发一次 / 随失败丢弃 | 提交前 0、提交后 flush 恰好 1；失败候选 200ms timer 永不触发、active 继续走针 | `pendingCandidateTimerCommittedWithGeneration` / `pendingCandidateTimerDiscardedWithFailedCandidate`（AC5） |
| TEST 面：候选 callback 在 harness 执行；失败保留旧 TEST 环境 | 提交后测试 timer/listener 命中；失败后旧 listener 计数继续 | `testTypeCandidateCallbacksRunInHarness`（AC5） |
| 阶段结果：成功=COMMIT / STARTUP=STARTUP（非事务+要求 restart）/ FILE=FILE | `phase()`、`nonTransactional()`、`requiresLoaderRestart()` | `NekoRuntimeRootReloadResultTest`（AC3/AC6） |

### 3.2 26.1.2 runServer 会话（`bench/smoke-reload/`，28 项 checks 全绿，rev `10f95658`）

观察设计：fixture 用**版本化 interval**（`ticket06-tick-<v>`）作 commit witness —— 生产 tick flush 只冲刷 active generation，候选 timer 在 commit 前不进生产路由、失败随候选丢弃，因此「新版本 tick 出现 + 所有旧版本 tick 归零」即单一 commit 点与无双执行的直接外部证据；`ticket06-entry-<v>` 只作入口计数（候选执行期可见，属 spec 09 user story 28 的「不深回滚外部副作用」范围）。

| Phase | 动作 | 结果（外部可见） |
|---|---|---|
| baseline | 启动（`Done(0.272s)`） | `entry-v1=1`；v1 tick 6/6s（active 持有生产 timer） |
| A 成功 reload | v1→v2 | 应答 `NekoJS server scripts reloaded. - no errors.`；`entry-v2=1`；**v2 tick 6、v1 tick 0**（生产路由已切换，无双重执行）；`/nekojs error` healthy |
| B 注入失败 | `server_scripts/sm02-boom.js`（`while(true)` 烧尽 200k 语句预算） | 应答 `reload failed: type=server generation=3 phase=EXECUTION source=server/sm02-boom.js owner=ScriptManager[server] domain=candidate-killed`（无修复指引）；**v3 tick 0**（候选未提交、候选 timer 随候选丢弃）；v2 tick 5/6s 且跨 reload 窗口 6——active 全程未断流；`/nekojs error` 报 2 个错误；全会话 ResourceLimits 行数 =1（只有注入候选被终止） |
| C 修复恢复 | 删除 boom，v3→v4 | 应答 no errors；`entry-v4=1`；v4 tick 6，v2/v3 tick 0；healthy |
| D 重复稳定 | v4→v5→v6（连续两次） | 两次均 no errors；`entry-v6=1`；窗口内仅 v6 tick 6，v1–v5 全 0（无累积、无双执行） |
| E STARTUP 边界 | `nekojs reload startup` | 应答 `NekoJS startup scripts reloaded. - no errors.` + `NekoJS startup scripts reloaded non-transactionally (reset+load, phase=STARTUP): irreversible platform registrations are not rolled back - restart the game/loader for a clean STARTUP state.`（AC6 入口外观） |
| 收尾 | RCON `stop` | `exitCode=0`、无 FATAL/crash report |

证据文件（`evidence/`，前缀 `*-20260913T031818Z-26.1.2.*`）：`checks`、`counts`、`env`（git rev=10f95658）、`rcon-phaseA..E-*`、`nekojs-server-*.log`、`smoke-26.1.2-stdout-*.log.gz`、`server-stderr-*.log`。

---

## 4. 测试计数对比

| 范围 | 05 号票报告（更早时点） | 本票 HEAD（实测） | 本票净增 |
|---|---|---|---|
| `:common` | 181 suites / 1366 / 4 skipped | **192 suites / 1417 tests / 5 skipped / 0 failed** | 见下（本票 +13 executed +1 `@Disabled`） |
| `:common-api-processor` | 1 / 13 / 0 | 1 / 13 / 0 | — |
| `:26.1.2` | 33 / 155 / 34 | 33 / 155 / 34 | — |
| `:26.2.0` | 33 / 155 / 34 | 33 / 155 / 34 | — |
| `:1.21.1` | 21 / 76 / 0 | 21 / 76 / 0 | — |
| `:26.1.2-fabric` | 11 / 58 / 6 | 11 / 58 / 6 | — |
| guardLint | 0 问题 | 0 问题 | — |

本票对 `:common` 的逐文件口径（可对照基线 commit `98478559` 的同一文件）：

| 测试文件 | 基线 `98478559` `@Test` 数 | HEAD | 差 |
|---|---|---|---|
| `script/ScriptReloadGenerationTest.java` | 0（文件不存在） | 11 | +11 |
| `core/lifecycle/NekoRuntimeRootReloadResultTest.java` | 0（文件不存在） | 2 | +2 |
| `script/ScriptReloadRegressionTest.java` | 10 | 10 | 0（仅适配 generation 新契约的断言，无删改方法） |
| `script/Ticket06RunawayProbeTest.java`（phase 7） | 0 | 1（`@Disabled`） | +1 skipped |

`98478559..HEAD` 的 `common/src/test` + `src/test` 改动只含上述文件（`git diff --name-only`），故本票净增 **+13 executed / +1 disabled，0 删除**；其余 +10 suites / +206 tests 的差值来自基线之后、本票之前已合入的 05/09 号票测试（非本票引入）。

**基线口径说明（重要，勿误读）**：为拿到"基线实测值"我在一个 detached 新 worktree 里跑过 `98478559`，得 `:common` 151 suites / 1215 tests —— 该数字**不可作基线**：stonecutter 在 detached 新 worktree 未注册版本节点项目（`Cannot locate tasks that match ':26.1.2:test'`），共享测试源集因而不完整（缺失 41 个与版本节点处理相关的 suite）。上表基线值改用「逐文件 `@Test` 计数 + 05 报告节点计数」，并在**同一环境内**比较（phase 1–6 后实测 191/1416 → phase 7 后 192/1417）。该临时 worktree 已删除。

---

## 5. AC1–AC7 逐条判定建议与证据指针

| AC | 建议 | 证据 |
|---|---|---|
| AC1 SERVER/CLIENT/TEST 成功 reload 都先构建 candidate generation；candidate 执行期间生产 callback/timer/对外 binding/live mutation 仍由 active 执行 | **pass** | 单测 `candidatePhaseEventsServedByActiveGeneration`（候选脚本执行中途 post 生产事件 → active 命中、候选 0）；`failedReloadKeepsActiveListenersOnBus`（候选监听器从未上总线：`busHasListeners()` 在候选阶段/失败后仍真）；烟测 baseline/A 段（candidate 执行期间 v1 tick 持续）；CLIENT 面为结构等价（同一 `reloadScriptsTransactional`，仅 owner thread 入口不同）→ CLIENT 真机 not-verified（§6） |
| AC2 preparation/execution/binding/域 preflight 失败 → candidate 的 Context/timer/listener/binding/临时计划全部关闭；active 仍收事件、读旧 state | **pass** | 单测 `failedReloadKeepsOldContextAndScriptState`（旧 Context 仍当前、旧监听器继续 +1）、`pendingCandidateTimerDiscardedWithFailedCandidate`（候选 timer 永不触发、active 继续走针）、`failedReloadDiscardsCandidateListenersOnly`；`discardCandidate` 关闭顺序 timer→Context→streams；烟测 B 段（v3 tick 0、v2 tick 不断流、ResourceLimits=1）。**域 preflight 面为 not-verified**：本票不含 feature 域 Adapter 的 preflight 实现（见 §6、§8） |
| AC3 失败结果外部可见地含 generation、phase、source location、owner/domain；无修复指引；不用内部锁/私有对象当契约 | **pass** | `ReloadFailureReport`（`type/generation/phase/sourceLocation/owner/domain` + `describe()`）、`NekoReloadException`；烟测 B 段应答原文 `generation=3 phase=EXECUTION source=server/sm02-boom.js owner=ScriptManager[server] domain=candidate-killed`（无 `/neko`、无 "try/reload again" 字样）；单测断言 `describe().contains("phase=EXECUTION")`。`sourceLocation` 用 `type/相对脚本路径` 文本，不暴露锁/私有对象 |
| AC4 commit 后新 generation 是唯一新 callback 接收者，同一事件无旧新双重执行；旧 generation 按 timer、listener、Context 所有权顺序释放 | **pass** | 单测 `successfulReloadRunsScriptsOnceAndSingleExecutionAfterCommit`、`consecutiveCommitsKeepSingleExecutionAndCloseOldContext`（旧 Context eval 抛）、`bridgeLifecycleCallCounts`（清扫恰好一次）、`afterCommitProductionTimerDispatchBelongsToNewGenerationOnly`；烟测 A/D 段（版本化 tick：新代 6/6s、全部旧代 0/6s，连续多次 reload 后仍 0）。**跨线程与 commit 重叠的在途 callback** 明确不在本票（spec 09 提交边界：commit 不承诺与在途 callback 零重叠，安全点由平台 Adapter/07 接线）→ 见 §6 |
| AC5 candidate-only 测试 callback 可在测试 harness 执行；pending timer 只被收集并随 generation 提交或关闭；commit 前都不进生产路由；被 watchdog 或语句上限终止时 candidate 丢弃、active 不变 | **pass（调度面归 07）** | 单测 `testTypeCandidateCallbacksRunInHarness`（TEST 面提交后 harness 执行）、`pendingCandidateTimerCommittedWithGeneration` / `pendingCandidateTimerDiscardedWithFailedCandidate`；终止面用语句上限路径（`withStatementLimit()` 100k；烟测 200k）— `candidateKilled` → `discardCandidate`，active 不变。watchdog/语句上限的**调度**（谁在哪个 owner thread 触发、重入、队列）归 07；本票只做接收面 |
| AC6 STARTUP 不可逆平台注册未被域 Adapter 证明可回滚时，入口显式要求 loader restart 或返回不支持阶段，不执行 reset 后宣称事务成功 | **pass（含口径说明）** | `ReloadResult.phase()==STARTUP` + `nonTransactional()`/`requiresLoaderRestart()`；命令面（共享/1.21.1/Fabric 三节点）追加显式文本「non-transactionally (reset+load, phase=STARTUP) ... restart the game/loader for a clean STARTUP state.」；单测 `startupReloadKeepsExplicitNonTransactionalBoundary`（含两个新谓词断言）；烟测 E 段 RCON 应答。**口径说明**：`reloadScripts()` 对 STARTUP 仍执行 reset+load（沿用 05 既有语义，未新增候选事务、未重做 registry bootstrap），但入口不再把它读作候选+commit 成功（phase=STARTUP + 显式 restart 提示）。若维护者口径要求"STARTUP 直接返回 unsupported 而不执行"，需要单独决策（本票不偷改行为） |
| AC7 本票通过只代表 generation-owned 交接绿；GLOBAL_STATE 联合写集与 RUNTIME_THREADS 完整调度仍需各自通过 | **pass** | 本报告 §8 边界声明；`NekoGlobal.SHARED`（总账 I3）、root map 联合事务未触碰；owner-thread 队列/重入/watchdog 调度未实现（`ScriptManager` 仅以实例锁串行 reload/load/close，候选构建与收集都在 owner thread 同步完成，跨线程 dispatch 与 commit 的临界区安全仍待 07） |

---

## 6. not-verified 与 owner

| 项 | 状态 | owner / 后续 |
|---|---|---|
| CLIENT 真机路径（客户端 Context 的 candidate → commit、F3+T、渲染线程 owner） | not-verified（无头环境不可真跑；单测用 TEST/SERVER 面覆盖同一 `reloadScriptsTransactional`） | owner 维护者 / minecraft-mcp 桥接客户端；05 报告同样把 CLIENT 真机列为 not-verified |
| 跨线程 callback 与 commit 点重叠（不同 owner thread 的 dispatch 与 commit 临界区、队列/重入/close 优先/watchdog） | not-verified（本票明文不做；spec 09 提交边界亦不承诺零重叠） | 07 号票（RUNTIME_THREADS） |
| feature 域 preflight（Dynamic Registry / Villager Trades / PostEffects 的 candidate plan 与失败保留） | not-verified（域 Adapter 面；本票只提供了通用阶段与失败结果承载） | W6/W7 域 Adapter + 相应 ticket |
| ★ 发现：`scriptRunawayTimeoutSeconds`（时间窗口 runaway 路径）对 `while(true){ }` **无效** | 复现并已钉住：单测 20s 断言窗口内不终止（线程停在 `DefaultLoopNode.execute`）；26.1.2 runServer 会话里注入后 RCON 180s 无应答、日志无 `ResourceLimits` 行（服务器线程卡死）。反例：`scriptStatementLimit` 路径稳定终止——100k/200k 均命中，`5_000_000` 因死循环在累计到 50 次谓词调用前已被 JIT 编译而**不**命中 | 属既有 core 缺陷（与 ticket 06 无关，05 之前即存在、非本票引入）：看门狗调度/生效面归 **07 号票**（watchdog 语义）或独立缺陷票。复现件：`common/src/test/java/com/tkisor/nekojs/script/Ticket06RunawayProbeTest.java`（`@Disabled`，修好后去掉注解即为通过证据）；烟测 fixture 因此改用语句上限注入（见 `bench/smoke-reload/fixtures/nekojs/config/engine.toml` 注释） |
| 总账 A2 的 3 个 `bind(root)` 监听器无 unbind | **本票未引入风险，仍留待 root 可重建时处理**：06 不做 root 重建（root 仍是进程级唯一 owner，generation 在 `ScriptManager` 内部），Fabric `runtimeRoot` 只在 entrypoint 装配赋值一次（`NekoJSFabricMod.java:133`），无置空路径 → 旧 handle 不会指向被替换的 root。故 `unbind()` 语义**无调用方**，本票不加死代码 | 由「让 root 可重建」的那张票（07/W7/W8 的可注入 fabric composition 通道）补 unbind；已在 §8 交接 |
| 工单 work item 5「保留 Plugin Runtime、Point、Contributor、frozen result 与 Extension Handle 的进程级身份；只让 session object 按 generation 校验」 | **结构性成立（无专门 fixture）**：本票 diff 未触碰这些类型或 `IPluginRuntime` 接口；`pluginRuntime` 只经构造注入、candidate 路径只用其 `bindings(type)` / `fireBefore-·fireAfterScriptsLoaded`。进程级共享对象跨 generation **同一实例**（如 `EventGroup`：`bindEvents` 每代把同一 group 装进新 Context 的 bindings，脚本监听器 token 才按 generation 清扫），测试可观察面见 `bridgeLifecycleCallCounts`（清扫恰好一次、清扫后仅新代在总线）与 `consecutiveCommitsKeepSingleExecutionAndCloseOldContext`（旧代 token 不再收到事件）。generation-scoped session object / 事件 token 的**显式** generation 校验（旧对象试图操作新 session 时返回明确拒绝）由 Context 绑定 + `isContextDead` 三态兜底承担，未新增第二套身份模型 | 进程级 Handle / frozen result 的跨 reload 有效性专门 fixture 归 W2（spec 09 user story 13–16 的验收面）；若维护者要求显式 session-generation 校验（而非 Context 绑定兜底），需在 W2/07 定契约 |
| 1.21.1 节点的行为面（仅编译 + 命令面/Catch 同步） | not-verified（`/nekojs reload startup` 的非事务提示未在 1.21.1 实跑） | owner 维护者（如 1.21.1 需要独立烟测，可复制 `bench/smoke-reload/` 并加 `-Node 1.21.1` 支持） |

---

## 7. 偏离 / 发现（尤其与 05 交付的摩擦）

1. **`while(true)` 的失败注入不能靠 runaway 时间窗口**（§6 ★）。这是本次实施最大的意外：工单 phase 4/5 期待的"被 watchdog 或语句上限终止"里，时间窗口那条路当前**不生效**。本票按"接收面"口径处理：`candidateKilled → discardCandidate` 用语句上限路径验证（单测 100k、烟测 200k）。同时测出语句上限的**可靠档位边界**（≤200k 在解释器内命中；5M 需 50 次谓词调用 → 被 JIT 绕过），这条边界已写进 fixture 注释，避免后来者重踩。
2. **候选执行的 `console` 输出对生产日志可见**：候选 Context 的 `LoggerStream` 立即接到同一日志，故「入口行」不能当 commit 判定面（本报告改用版本化 tick 作 witness）。这不是缺陷，是 spec 09 user story 28「不深回滚外部副作用」的直接体现，已在报告与 runner 注释里写明口径。
3. **per-script 错误 ≠ reload 失败**：候选脚本抛 JS 异常仍按既有语义记为脚本错误并**提交**（脚本 disabled + 错误面板可见），只有环境级失败（候选 Context 被资源上限关闭、阶段基础设施抛出）才走失败路径。这是 05 之前既有行为（`unreadableScriptSurfacesInErrorTrackerInsteadOfVanishing` 等 prior art），本票不动；但工单 AC2 的"execution 失败"须按此口径理解（已在 §5 AC2 行注明）。
4. **与 05 的交付无行为冲突**：`NekoRuntimeRoot.closeSilently` 的 manager → bridge → resources 顺序、`NekoRuntimeAssembly` 共享装配、`NekoRuntimeRootLifecycleTest` 的 close 语义都未改；本票只在 manager 内部增加 generation 记账。唯一"摩擦面"是 05 留给本票的 A2 unbind 注记——结论是 06 不引入 root 重建，故 unbind 无调用方（§6 末行）。
5. **写集边界外的一次最小适配**：`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java`（源码，非构建接线）加了一个 catch + 一段提示，与共享版/1.21.1 对齐。票 31 的领地是 `versions/*-fabric` 的**构建接线**（buildSrc 约定、`fabric.gradle.kts`、源根迁移），此文件可能与其源根迁移产生文本冲突，需协调者合并时留意（一行 catch 级冲突）。26.2.0-fabric 复用 26.1.2 fabric source bridge，故已追加 `:26.2.0-fabric:compileJava` 单独验证。
6. **golden/契约变化**：本票未触碰 `docs/architecture-refactor/baseline/` 下任何 golden 或 managed-surface 契约文件，`guardLint` 0 问题 → 无需走 09 的 REGENERATE 流程。

---

## 8. 与 07 / 10 的边界声明与对 07 的交接注记

**本票不包含（明确留给后续票）**

- **10 号票（GLOBAL_STATE 联合写集）**：`NekoGlobal.SHARED`（总账 I3）与 root map 的联合事务未动；本票的 commit 点只覆盖 generation-owned 资源，不宣称 global 写集事务化。
- **07 号票（RUNTIME_THREADS）**：owner-thread 队列、非 owner 排队、回调内 reload 的排队/拒绝、close 优先、reload 不重入的**调度**、watchdog 的生效与恢复策略、以及"commit 不与该 generation 在途 callback 重叠"的序列化安全点。

**给 07 的钩子（已就位，可直接消费）**

| 钩子 | 现状 | 07 需要补什么 |
|---|---|---|
| 候选状态机 | `candidateContext` / `candidateKilled` / `candidateKillSource` / `pendingListeners` 已就位，全部只在 owner thread 读写 | 把"owner thread"从"reload 调用线程"提升为显式队列目标；跨线程请求改排队 |
| 重入 | `reloadScripts` / `loadScripts` / `close` / `runTestScripts` 已共用实例锁（`synchronized`），reload 不重入（同步等待自身会死锁 → 07 应改为队列或显式拒绝结果） | 回调内 reload 的排队或拒绝；close 优先于未开始的 reload |
| watchdog 接收面 | `candidateKilled → discardCandidate`（candidate 丢弃、active 不变）与 `contextKilled → 下次取用重建`（active 隔离失败，不自动重跑脚本）已实现 | watchdog 的**触发有效性与调度**（含本报告 ★ 发现）；终止 active 时的隔离失败停止分发 |
| 事件交接序列化 | `commitGeneration` 的顺序（清扫 → 发布 runtime → 激活候选 → 释放旧 session → 释放旧环境）已定型；旧闭包另有 `isContextDead` 三态兜底 | 在 owner-thread 序列化入口内让 dispatch 与 commit 不重叠（spec 09 提交边界） |
| 平台 callback 接线 | `NativeEventsJS` 分发闭包加 `isContextDead` 短路（失败候选残留原生注册成为 no-op） | 原生注册面（NeoForge EVENT_BUS）的 per-generation 撤销；`bind(root)` 监听器的 unbind（总账 A2） |
| 阶段结果 | `ReloadPhase` / `ReloadFailureReport` / `NekoReloadException` / `ReloadResult.phase()` 已定型，命令面三节点已呈现 | 07 若新增调度类失败（排队拒绝/close 优先/重入拒绝），沿用同一结果契约加 phase，不要引入第二套错误模型 |

---

## 9. 复现命令

```bash
# 单测与静态门（worktree 根 = D:\mcmodDemo\NekoJS\NekoJS-t06\NekoJS-mult）
./gradlew :common:check :common-api-processor:test guardLint \
          :26.1.2:check :26.2.0:check :1.21.1:check \
          :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava

# 26.1.2 runServer 烟测（端口 25871 / RCON 25872；本目录 runner 自带 RCON 客户端副本）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/smoke-reload/run-reload-smoke.ps1 -Node 26.1.2
# 输出：bench/smoke-reload/out/<stamp>-26.1.2/（checks.json / counts.json / 各 phase RCON 应答 / 日志）
```
