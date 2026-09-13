# 2026-09-12 reload candidate / 阶段结果与 owner-thread commit 点报告（ticket 06）

> 工单：`docs/architecture-refactor/implementation-tickets/06-reload-commit.md`（W1：candidate generation 隔离 + 单一 commit 点 + 阶段结果）。
> 分支：`ticket-06-reload-commit`（worktree `D:\mcmodDemo\NekoJS\NekoJS-t06\NekoJS-mult`），基线 `98478559`。
> 核心 spec：[`../../specs/09-reload-candidate-state-and-thread-contract.md`](../../specs/09-reload-candidate-state-and-thread-contract.md)（候选状态与线程契约，本票验收口径来源）；辅助 [`05-runtime-lifecycle-and-data.md`](../../specs/05-runtime-lifecycle-and-data.md)。
> 前置交付（消费不推翻）：[`../2026-09-12-runtime-root-refactor/REPORT.md`](../2026-09-12-runtime-root-refactor/REPORT.md)、总账 [`../2026-09-12-runtime-ledger.md`](../2026-09-12-runtime-ledger.md)。
> 证据：本目录 `evidence/`（26.1.2 runServer 会话 29 项 checks 全绿 + RCON 应答 + gz 日志 + A1 red/green 探针原始输出）。
>
> **本轮（第 9 个 commit）是双轴代码审查的修复轮**：审查发现 A1–A5 共 5 组缺陷，逐条修复并重跑全套门与烟测；
> AC1/AC2/AC4/AC5/AC6 的判定据实下调为**部分满足**（逐条缺口见 §5），新增「审查修复记录」见 §10。

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
| `6e1d9b40` | 8 | `docs(baseline): ticket 06 reload candidate report + gz evidence`（报告 + evidence 归档） |
| 本 commit（Phase 9） | 9 | `fix(lifecycle): ticket 06 审查修复 A1–A5`（commit 点不可回滚、死码、generation 可见性、AC6 命令面顺序、烟测文档失真）+ 报告据实改判 + 归档重跑证据 |

未推送、未合并、未触碰 master（另一会话在 `D:/mcmodDemo/NekoJS` 的 master 上做工单 31）。

---

## 2. generation 设计与 commit 点时序

### 2.1 数据结构（`common/.../script/ScriptManager.java`）

| 状态 | 字段 | 归属/可见性 |
|---|---|---|
| active 环境 | `RuntimeEnvironment runtime`（`context` + `nodeRuntime` + `outStream` + `errStream` **成对** volatile 发布） | 生产路由唯一读点（tick flush、`isContextDead`、单文件 reload） |
| active 存活标记 | `contextKilled`（语句上限 kill → 下次取用重建） | 进程内每 ScriptType 一份 |
| 候选环境 | `candidateContext`（构建期临时存活）、`candidateKilled`、`candidateKillScript`（存的是 `ScriptContainer`，不是 source 字符串——审查 A2 改名以免误读） | 仅 owner thread 读写；`isContextDead` 用 `equals` 比较（Graal 的 `Value.getContext()` 可能是等值包装对象） |
| 候选挂起注册 | `List<EventBusJS.PendingListener> pendingListeners` | commit 前**不进总线、不进 type 分桶 mirror**；失败弃置；候选 EVENT_PLAN 阶段完成 key 转换（审查 A1） |
| generation 序号 | `volatile long generation`（单调递增；审查 A3 补可见性——它是公开读点 `generationId()`） | 初始环境创建 / kill 重建 / commit 各 +1；失败候选号 = `generation + 1`，不写回 |
| 共享静态（不变） | `CONTEXT_TO_MANAGER`（强引用 Map，销毁路径唯一经 `closeRuntimeResources`） | Context → manager 反查，候选 Context 建好后即登记 |

进程级身份按 spec 09 user story 13–16 保持不变：`ScriptEventBridge`、`IPluginRuntime`、Plugin Runtime/Point/Contributor/Extension Handle、`NekoSharedEngine`、`NekoGlobal.SHARED` 都不随 generation 重建；只有 Graal Context、node runtime、script binding、listener token、timer、prepared module session 按 generation 创建/校验/释放。

### 2.2 commit 点时序（`commitGeneration`，owner thread 同步临界区）

```
reload(type)  [非 STARTUP]
 ├─ errorTracker.clearByType(type)                        ← 与既有实现同位次（候选错误可见）★见 §10.A1 缺口
 ├─ for binding : bindings(type) → binding.close(type)     ← 「先清账本再注册」域契约，位次不变（快照/回滚归域 Adapter）★
 ├─ PREPARATION  createContext(type)        失败 → phase=PREPARATION, domain=candidate-context
 ├─ BINDING      installEnvironmentBindings 失败 → phase=BINDING,     domain=binding-install
 ├─ EXECUTION    discoverWithPacks → fireBeforeScriptsLoaded → 逐脚本 executeEntry
 │                 · 监听器：EventBusJS 经 ScriptManager.collectPendingListener 收进候选
 │                 · timer   ：只进候选 node runtime（生产 flush 读 active runtime）
 │                 · candidateKilled → 失败：phase=EXECUTION, source=首个触发脚本, domain=candidate-killed
 ├─ EVENT_PLAN   逐条 prepareForActivation()（dispatch key 转换等 commit 期工作前移）
 │                 失败 → phase=EVENT_PLAN, source=注册脚本, domain=candidate-listener-plan
 │                 ← 审查 A1：commit 点不可再抛；失败必在 commit 之前，必走 discardCandidate
 └─ COMMIT（单一原子切换，顺序即契约）：
      1. clearListeners(type)        ← 旧 generation 停止接收新 callback（候选监听器从未上总线，故整类型清空 == 旧代清扫）
      2. runtime = candidate; scripts = candidate; generation = 候选号; contextKilled=false; candidateContext=null
      3. pendingListeners → activate()（新 generation 成为唯一新 callback 接收者；key 已在 EVENT_PLAN 解析，此步不抛）
      4. NekoModulePipelineCache.clear(type) + NekoEsmVirtualModuleRegistry.clear(type)（旧 module session）
      5. closeRuntimeResources(旧环境)：node runtime/timer → Context → out/err 流（所有权顺序）
```

失败路径 `discardCandidate`：挂起注册直接弃置（从未上总线）、候选 Context/timer/streams 按 timer → Context → streams 关闭；`runtime`/`scripts`/`generation`/active 监听器原样保留。STARTUP 与单文件 reload 是非候选路径，结果 phase 显式标记（`STARTUP` / `FILE`）。

**★ 候选构建前就改动进程级账本的位次（审查 AC1/AC2 缺口，见 §5）**：`errorTracker.clearByType(type)` 与 `binding.close(type)`（PostEffects/NativeEvents/DynamicRegistry 等域 Adapter 的进程级注册账本）都发生在候选 Context 创建**之前**。候选若失败，这两处改动不回滚——因此「候选执行期间一切对外状态只属 active」在**诊断账本与域注册账本**这两个层面并不成立（`binding.close` 的位次是 spec 09 user story 15 的域契约，账本快照/回滚归 W6/W7 域 Adapter）。

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
| **失败：候选 Context 真关闭 + 解除登记**（审查补证，原先只有「active 不变」） | `candidate.eval` 抛、`ScriptManager.from(candidate)` 抛、`pendingListeners` 空 | `killedCandidateContextIsClosedAndUnregistered`（AC2） |
| **激活期工作前移**（审查 A1 回归）：非法 dispatch key | 抛 `NekoReloadException`（`phase=EVENT_PLAN`/`domain=candidate-listener-plan`/`source=server/entry.js`）；候选 Context 关闭、挂起注册空、候选 timer 不进生产分发；active 的 runtime/scripts/generation/监听器 `assertSame` 不变 | `illegalDispatchKeyFailsCandidatePlanAndPreservesActive` |
| **commit 清扫先于发布**（审查补证，AC4 顺序面） | `clearListeners` 时刻读到的仍是旧 runtime（`assertSame`），发布发生在其后 | `commitSweepsOldListenersBeforePublishingNewRuntime` |
| 失败结果外部可见字段 | `NekoReloadException.report()`：phase=EXECUTION、generation≥1、owner=`ScriptManager[server]`、`describe()` 含 `phase=` | 同上（AC3） |
| 清扫只在 commit 发生 | 失败：`clearListeners` 0 次；成功：恰好 1 次 + 清扫后仅新代在总线 | `bridgeLifecycleCallCounts` |
| commit 后 timer 生产分发单一归属 | 旧 interval 增量冻结、新 interval 持续 | `afterCommitProductionTimerDispatchBelongsToNewGenerationOnly`（AC4） |
| pending timer：随提交分发一次 / 随失败丢弃 | 提交前 0、提交后 flush 恰好 1；失败候选 200ms timer 永不触发、active 继续走针 | `pendingCandidateTimerCommittedWithGeneration` / `pendingCandidateTimerDiscardedWithFailedCandidate`（AC5） |
| TEST 面：候选 callback 在 harness 执行；失败保留旧 TEST 环境 | 提交后测试 timer/listener 命中；失败后旧 listener 计数继续 | `testTypeCandidateCallbacksRunInHarness`（AC5） |
| 阶段结果：成功=COMMIT / STARTUP=STARTUP（非事务+要求 restart）/ FILE=FILE + **FILE 失败携带 source location**（审查 A2） | `phase()`、`nonTransactional()`、`requiresLoaderRestart()`、`sourceLocation()` | `NekoRuntimeRootReloadResultTest`（AC3/AC6） |

### 3.2 26.1.2 runServer 会话（`bench/smoke-reload/`，29 项 checks 全绿，rev `6e1d9b40` + 审查修复工作树）

观察设计：fixture 用**版本化 interval**（`ticket06-tick-<v>`）作 commit witness —— 生产 tick flush 只冲刷 active generation，候选 timer 在 commit 前不进生产路由、失败随候选丢弃，因此「新版本 tick 出现 + 所有旧版本 tick 归零」即单一 commit 点与无双执行的直接外部证据；`ticket06-entry-<v>` 只作入口计数（候选执行期可见，属 spec 09 user story 28 的「不深回滚外部副作用」范围）。

| Phase | 动作 | 结果（外部可见） |
|---|---|---|
| baseline | 启动（`Done(0.281s)`） | `entry-v1=1`；v1 tick 6/6s（active 持有生产 timer） |
| A 成功 reload | v1→v2 | 应答 `NekoJS server scripts reloaded. - no errors.`；`entry-v2=1`；**v2 tick 6、v1 tick 0**（生产路由已切换，无双重执行）；`/nekojs error` healthy |
| B 注入失败 | `server_scripts/sm02-boom.js`（`while(true)` 烧尽 200k 语句预算） | 应答 `reload failed: type=server generation=3 phase=EXECUTION source=server/sm02-boom.js owner=ScriptManager[server] domain=candidate-killed`（无修复指引）；**v3 tick 0**（候选未提交、候选 timer 随候选丢弃）；v2 tick 5/6s 且跨 reload 窗口 6——active 全程未断流；`/nekojs error` 报 2 个错误；全会话 ResourceLimits 行数 =1（只有注入候选被终止） |
| C 修复恢复 | 删除 boom，v3→v4 | 应答 no errors；`entry-v4=1`；v4 tick 6，v2/v3 tick 0；healthy |
| D 重复稳定 | v4→v5→v6（连续两次） | 两次均 no errors；`entry-v6=1`；窗口内仅 v6 tick 6，v1–v5 全 0（无累积、无双执行） |
| E STARTUP 边界（审查 A4 后重测） | `nekojs reload startup` | **先**给非事务结论：`NekoJS startup scripts reloaded non-transactionally (reset+load, phase=STARTUP): irreversible platform registrations are not rolled back - restart the game/loader for a clean STARTUP state.`，**再**给结果行：`NekoJS startup scripts reload finished (non-transactional, phase=STARTUP). - no errors.`（结果行不再重复宣称 `reloaded`）；新增判定 `E_non_transactional_conclusion_precedes_success_line=true` |
| 收尾 | RCON `stop` | `exitCode=0`、无 FATAL/crash report |

证据文件（`evidence/`）：
- 本轮（审查修复后）：前缀 `*-20260913T035123Z-26.1.2.*`（`checks`/`counts`/`env`/`rcon-phaseA..E-*`/`nekojs-server-*.log`/`smoke-26.1.2-stdout-*.log.gz`/`server-stderr-*.log`），`env.txt` 记 `git rev: 6e1d9b40`（= 当时的 HEAD；审查修复尚未提交，证据对应「HEAD + 工作树修复」）。
- 上一轮（Phase 8）保留：前缀 `*-20260913T031818Z-26.1.2.*`，用于对比 A4 改动前后的 E 段应答顺序。
- 门与测试计数归档：`verify-gates-and-test-counts-2026-09-12.txt`（审查要求：Phase 8 的「192/1417、guardLint 0」当年只有自述）。
- A1 red→green 探针原始输出：`a1-red-green-probe-2026-09-12.txt`。

---

## 4. 测试计数对比

> 本节数字已入库归档（可重跑核对）：`evidence/verify-gates-and-test-counts-2026-09-12.txt`
> （审查指出 Phase 8 报告的「192/1417、guardLint 0」当时只有自述、无产物）。

| 范围 | 05 号票报告（更早时点） | 本票 HEAD（Phase 9 实测，含审查修复） | 本票净增 |
|---|---|---|---|
| `:common` | 181 suites / 1366 / 4 skipped | **192 suites / 1420 tests / 5 skipped / 0 failed** | 见下（本票 +16 executed +1 `@Disabled`） |
| `:common-api-processor` | 1 / 13 / 0 | 1 / 13 / 0 | — |
| `:26.1.2` | 33 / 155 / 34 | 33 / 155 / 34 | — |
| `:26.2.0` | 33 / 155 / 34 | 33 / 155 / 34 | — |
| `:1.21.1` | 21 / 76 / 0 | 21 / 76 / 0 | — |
| `:26.1.2-fabric` | 11 / 58 / 6 | 11 / 58 / 6（本轮只跑 `compileJava`，计数为上一轮 check 归档值） | — |
| guardLint | 0 问题 | 0 问题（守卫块 247，扫描 326 文件；豁免 0、警告 0） | — |

本票对 `:common` 的逐文件口径（可对照基线 commit `98478559` 的同一文件）：

| 测试文件 | 基线 `98478559` `@Test` 数 | HEAD | 差 |
|---|---|---|---|
| `script/ScriptReloadGenerationTest.java` | 0（文件不存在） | 14 | +14（Phase 2–7 建 11 个；**Phase 9 审查修复 +3**） |
| `core/lifecycle/NekoRuntimeRootReloadResultTest.java` | 0（文件不存在） | 2 | +2（Phase 9 未加方法，只在 FILE 失败用例加 `sourceLocation` 断言） |
| `script/ScriptReloadRegressionTest.java` | 10 | 10 | 0（仅适配 generation 新契约的断言，无删改方法） |
| `script/Ticket06RunawayProbeTest.java`（phase 7） | 0 | 1（`@Disabled`） | +1 skipped |

Phase 9 新增的 3 个用例（对应审查 A1 / AC2 补证 / AC4 补证）：见 §3.1 与 §10。

**基线口径说明（重要，勿误读）**：为拿到"基线实测值"我在一个 detached 新 worktree 里跑过 `98478559`，得 `:common` 151 suites / 1215 tests —— 该数字**不可作基线**：stonecutter 在 detached 新 worktree 未注册版本节点项目（`Cannot locate tasks that match ':26.1.2:test'`），共享测试源集因而不完整（缺失 41 个与版本节点处理相关的 suite）。上表基线值改用「逐文件 `@Test` 计数 + 05 报告节点计数」，并在**同一环境内**比较（phase 1–6 后实测 191/1416 → phase 7 后 192/1417 → Phase 9 后 192/1420）。该临时 worktree 已删除。

---

## 5. AC1–AC7 逐条判定建议与证据指针

> 本轮（Phase 9）按双轴审查要求**据实下调** AC1/AC2/AC4/AC5/AC6 为「部分满足」，逐条写明缺口与 owner；
> AC3/AC7 维持 pass 并补证。下调不是修不动，而是「已验部分」与「未验/不成立部分」必须分开记账。

| AC | 判定 | 已验部分（证据） | 缺口 / owner |
|---|---|---|---|
| AC1 SERVER/CLIENT/TEST 成功 reload 都先构建 candidate generation；candidate 执行期间生产 callback/timer/对外 binding/live mutation 仍由 active 执行 | **部分满足** | 单测 `candidatePhaseEventsServedByActiveGeneration`（候选脚本执行中途 post 生产事件 → active 命中、候选 0）、`failedReloadKeepsActiveListenersOnBus`（候选监听器从未上总线）；烟测 baseline/A 段（candidate 执行期间 v1 tick 持续） | ① **`NativeEventsJS` 缺口（审查指出，本次只披露不修）**：`onEvent*` 在**候选执行期**就把 handler 直挂 NeoForge 生产 `EVENT_BUS`（`NativeEventsJS.registerNative` 的 `addListener`），只有事后分发时的 `isContextDead(handlerContext)` 短路；而 `isContextDead` 对「正在构建的候选 Context」返回 `candidateKilled`（候选存活期=false），故**候选期的原生注册在生产事件分发中并非 no-op**（`common/.../bindings/static_access/NativeEventsJS.java:151`）。原生注册的 per-generation 撤销归 **W7 域 Adapter**（§8 钩子表已列）。② 候选构建**之前**已改动进程级账本：`errorTracker.clearByType(type)` 与各 domain `binding.close(type)`（见 §2.2 ★）→ 失败候选不回滚这两处，「对外 binding 只属 active」在账本层面不成立；账本快照/回滚归 **W6/W7 域 Adapter**（10 号票 GLOBAL_STATE 联合写集）。③ CLIENT 真机 not-verified（无头环境，见 §6）。 |
| AC2 preparation/execution/binding/域 preflight 失败 → candidate 的 Context/timer/listener/binding/临时计划全部关闭；active 仍收事件、读旧 state | **部分满足** | 单测 `failedReloadKeepsOldContextAndScriptState`、`pendingCandidateTimerDiscardedWithFailedCandidate`、`failedReloadDiscardsCandidateListenersOnly`；**本轮补** `killedCandidateContextIsClosedAndUnregistered`（候选 Context 真关闭 + 解除 `CONTEXT_TO_MANAGER` 登记——审查指出原先只有「active 不变」，缺显式断言）与 `illegalDispatchKeyFailsCandidatePlanAndPreservesActive`（A1 修复后新增的失败相位判定）；烟测 B 段（v3 tick 0、v2 tick 不断流、ResourceLimits=1） | ① 域 Adapter 的进程级注册账本在候选前已被 `binding.close(type)` 重置且失败不回滚（同上，W6/W7）。② 域 preflight 本身未实现/未验（feature 域 Adapter 面，§6）。③ 「候选 binding 撤销」只对 generation-owned 资源成立；`eventGroups()` 共享实例上的 Java 侧直挂监听器不在本票撤销面内（W7）。 |
| AC3 失败结果外部可见地含 generation、phase、source location、owner/domain；无修复指引；不用内部锁/私有对象当契约 | **pass（本轮补全 → 唯一上一轮不成立的路径已修）** | `ReloadFailureReport`（`type/generation/phase/sourceLocation/owner/domain` + `describe()`）、`NekoReloadException`；烟测 B 段应答原文含 `generation=3 phase=EXECUTION source=server/sm02-boom.js owner=ScriptManager[server] domain=candidate-killed`；新增 A1 用例断言 `phase=EVENT_PLAN` + `sourceLocation` 指向注册脚本 | 上一轮缺口（**审查 A2**）：非候选单文件路径的 `ReloadResult` 没有 `sourceLocation` 字段，`reloadFile` 算出的位置被 record 丢弃 → AC3 在 FILE 路径不成立。本轮已把 `sourceLocation` 加进 `ReloadResult` 并在 `NekoRuntimeRootReloadResultTest` 断言（`endsWith("missing-file.js")`）。剩余：域 preflight 失败自身的 source 归属要等域 Adapter（W6/W7）。 |
| AC4 commit 后新 generation 是唯一新 callback 接收者，同一事件无旧新双重执行；旧 generation 按 timer、listener、Context 所有权顺序释放 | **部分满足**（顺序字面偏离，**请维护者裁决**） | 单测 `successfulReloadRunsScriptsOnceAndSingleExecutionAfterCommit`、`consecutiveCommitsKeepSingleExecutionAndCloseOldContext`（旧 Context eval 抛）、`bridgeLifecycleCallCounts`（清扫恰好一次）、`afterCommitProductionTimerDispatchBelongsToNewGenerationOnly`；**本轮补** `commitSweepsOldListenersBeforePublishingNewRuntime`（清扫时刻读到的仍是**旧** runtime → 清扫先于发布）；烟测 A/D 段（版本化 tick：新代 6/6s、全部旧代 0/6s，连续多次 reload 后仍 0） | **顺序偏离的显式论证与裁决请求**：本票实际顺序是 `listener(发布前清) → 发布 runtime → 激活候选 listener → module session → 旧环境 timer/Context/streams`，而 AC4 字面是 `timer → listener → Context`。论证：① 「新旧双重执行」并不由物理释放顺序决定，而由**发布点**决定——发布后 `flushReadyNodeTimers` 读的是新 runtime（生产 flush 再也触不到旧 node runtime 的 timer），旧监听器闭包经 `isContextDead` 三态判 dead，故第 5 步的物理顺序不携带执行语义；② 若照字面先释放旧 timer，就必须把 `closeRuntimeResources`（现在是一个整体：node runtime → Context → streams）拆成两段调用，只为对齐措辞而不改变任何可观察行为，反而增加「旧 Context 与旧 timer 半销毁」的中间态；③ 本票**没有**把字面顺序改成 pass（不静默改判），把裁决权交回维护者：若要求字面一致，需要一张实现「旧环境两段式 teardown」的票。另：timer→Context→streams 的**物理释放顺序无外部观察点**（`closeRuntimeResources` 内部代码顺序，非契约面），故只补了「清扫先于发布」这条可断言顺序，物理释放顺序记为无法断言。 |
| AC5 candidate-only 测试 callback 可在测试 harness 执行；pending timer 只被收集并随 generation 提交或关闭；commit 前都不进生产路由；被 watchdog 或语句上限终止时 candidate 丢弃、active 不变 | **部分满足** | 单测 `testTypeCandidateCallbacksRunInHarness`（TEST 面提交后 harness 执行）、`pendingCandidateTimerCommittedWithGeneration` / `pendingCandidateTimerDiscardedWithFailedCandidate`；**语句上限终止路径**验到：单测 100k、烟测 200k（`candidateKilled → discardCandidate`） | **watchdog（`scriptRunawayTimeoutSeconds` 时间窗口）终止路径未验**：实测该路径对 `while(true){}` **无效**（单测 20s 窗口不终止；26.1.2 会话里 RCON 180s 无应答、服务器线程卡死、日志无 `ResourceLimits` 行），复现件 `Ticket06RunawayProbeTest`（`@Disabled`）。因此 AC5 里「被 watchdog **或**语句上限终止」只完成了语句上限那一支；watchdog 的触发有效性与调度归 **07 号票**（或独立 core 缺陷票），owner 与上一轮一致。 |
| AC6 STARTUP 不可逆平台注册未被域 Adapter 证明可回滚时，入口显式要求 loader restart 或返回不支持阶段，不执行 reset 后宣称事务成功 | **部分满足**（入口外观本轮已修，行为仍是 reset+load） | `ReloadResult.phase()==STARTUP` + `nonTransactional()`/`requiresLoaderRestart()`；三个节点命令面**先**给非事务/需重启结论、**再**给结果行（审查 A4 修复，见 §10.A4）；单测 `startupReloadKeepsExplicitNonTransactionalBoundary`；烟测 E 段 RCON 应答 + 新增判定 `E_non_transactional_conclusion_precedes_success_line=true` | **STARTUP 仍然执行 reset+load**（未改成「返回 unsupported 而不执行」），入口不再宣称事务成功并显式要求 restart —— 即 AC6 的「不宣称事务成功」这一支成立，而「或返回不支持阶段」这一支未取。若维护者口径要求 STARTUP reload 直接拒绝执行，需要单独决策（本票不偷改行为，与上一轮口径一致）。另：1.21.1 节点的 STARTUP 提示未在实机跑（§6）。 |
| AC7 本票通过只代表 generation-owned 交接绿；GLOBAL_STATE 联合写集与 RUNTIME_THREADS 完整调度仍需各自通过 | **pass** | 本报告 §8 边界声明；`NekoGlobal.SHARED`（总账 I3）、root map 联合事务未触碰；owner-thread 队列/重入/watchdog 调度未实现（`ScriptManager` 仅以实例锁串行 reload/load/close，候选构建与收集都在 owner thread 同步完成） | 补充（审查要求）：`versions/26.1.2-fabric/.../FabricNekoJSCommands.java` 的改动是 **AC6 入口外观的必要面**（三个节点命令面必须一致），不是票 31 的构建接线；但它与票 31 的 `versions/*-fabric` 源根迁移存在**潜在文本冲突**（同文件、同区域），合并时需协调者留意。见 §7.5。 |

---

## 6. not-verified 与 owner

| 项 | 状态 | owner / 后续 |
|---|---|---|
| CLIENT 真机路径（客户端 Context 的 candidate → commit、F3+T、渲染线程 owner） | not-verified（无头环境不可真跑；单测用 TEST/SERVER 面覆盖同一 `reloadScriptsTransactional`）**→ 使 AC1 只能判部分满足** | owner 维护者 / minecraft-mcp 桥接客户端；05 报告同样把 CLIENT 真机列为 not-verified |
| **`NativeEventsJS` 原生注册的生产可见性（审查新增缺口，本次只披露不修）** | 已定位：`registerNative` 在候选执行期就 `NeoForge.EVENT_BUS.addListener(...)`（`src/main/java/com/tkisor/nekojs/bindings/static_access/NativeEventsJS.java:151`），只有分发时的 `isContextDead(handlerContext)` 短路；而候选存活期 `isContextDead(candidateContext)==candidateKilled==false` → 候选期原生注册**不是** no-op，也不随失败候选原子撤销。**使 AC1/AC2 只能判部分满足** | W7 域 Adapter：原生注册面的 per-generation 撤销（失败候选的残留注册必须移除，而不是只靠分发短路）；§8 钩子表已列 |
| **候选构建前改动的进程级账本不回滚**（`errorTracker.clearByType` + 各 domain `binding.close(type)`，审查新增缺口） | 已定位并写进 §2.2 ★：候选失败时这两处改动保持已生效状态（`binding.close` 的位次是 spec 09 user story 15 的域契约）。**使 AC1/AC2 只能判部分满足** | W6/W7 域 Adapter：账本快照/回滚；10 号票 GLOBAL_STATE 联合写集 |
| **AC4 释放顺序的字面偏离待裁决**（审查要求显式论证而非静默改判） | 论证见 §5 AC4 行：物理释放顺序不携带执行语义（发布点决定唯一接收者），照字面拆 teardown 只改措辞不改行为 | **请维护者裁决**：接受顺序偏离，或另开一张「旧环境两段式 teardown」票 |
| 跨线程 callback 与 commit 点重叠（不同 owner thread 的 dispatch 与 commit 临界区、队列/重入/close 优先/watchdog） | not-verified（本票明文不做；spec 09 提交边界亦不承诺零重叠） | 07 号票（RUNTIME_THREADS） |
| feature 域 preflight（Dynamic Registry / Villager Trades / PostEffects 的 candidate plan 与失败保留） | not-verified（域 Adapter 面；本票只提供了通用阶段与失败结果承载） | W6/W7 域 Adapter + 相应 ticket |
| ★ 发现：`scriptRunawayTimeoutSeconds`（时间窗口 runaway 路径）对 `while(true){ }` **无效** | 复现并已钉住：单测 20s 断言窗口内不终止（线程停在 `DefaultLoopNode.execute`）；26.1.2 runServer 会话里注入后 RCON 180s 无应答、日志无 `ResourceLimits` 行（服务器线程卡死）。反例：`scriptStatementLimit` 路径稳定终止——100k/200k 均命中，`5_000_000` 因死循环在累计到 50 次谓词调用前已被 JIT 编译而**不**命中。**这条发现直接决定 AC5 只能判部分满足**（watchdog 那一支未验） | 属既有 core 缺陷（与 ticket 06 无关，05 之前即存在、非本票引入）：看门狗调度/生效面归 **07 号票**（watchdog 语义）或独立缺陷票。复现件：`common/src/test/java/com/tkisor/nekojs/script/Ticket06RunawayProbeTest.java`（`@Disabled`，修好后去掉注解即为通过证据）；烟测 fixture 因此改用语句上限注入（见 `bench/smoke-reload/fixtures/nekojs/config/engine.toml` 注释） |
| 总账 A2 的 3 个 `bind(root)` 监听器无 unbind | **本票未引入风险，仍留待 root 可重建时处理**：06 不做 root 重建（root 仍是进程级唯一 owner，generation 在 `ScriptManager` 内部），Fabric `runtimeRoot` 只在 entrypoint 装配赋值一次（`NekoJSFabricMod.java:133`），无置空路径 → 旧 handle 不会指向被替换的 root。故 `unbind()` 语义**无调用方**，本票不加死代码 | 由「让 root 可重建」的那张票（07/W7/W8 的可注入 fabric composition 通道）补 unbind；已在 §8 交接 |
| 工单 work item 5「保留 Plugin Runtime、Point、Contributor、frozen result 与 Extension Handle 的进程级身份；只让 session object 按 generation 校验」 | **结构性成立（无专门 fixture）**：本票 diff 未触碰这些类型或 `IPluginRuntime` 接口；`pluginRuntime` 只经构造注入、candidate 路径只用其 `bindings(type)` / `fireBefore-·fireAfterScriptsLoaded`。进程级共享对象跨 generation **同一实例**（如 `EventGroup`：`bindEvents` 每代把同一 group 装进新 Context 的 bindings，脚本监听器 token 才按 generation 清扫），测试可观察面见 `bridgeLifecycleCallCounts`（清扫恰好一次、清扫后仅新代在总线）与 `consecutiveCommitsKeepSingleExecutionAndCloseOldContext`（旧代 token 不再收到事件）。generation-scoped session object / 事件 token 的**显式** generation 校验（旧对象试图操作新 session 时返回明确拒绝）由 Context 绑定 + `isContextDead` 三态兜底承担，未新增第二套身份模型 | 进程级 Handle / frozen result 的跨 reload 有效性专门 fixture 归 W2（spec 09 user story 13–16 的验收面）；若维护者要求显式 session-generation 校验（而非 Context 绑定兜底），需在 W2/07 定契约 |
| 1.21.1 节点的行为面（仅编译 + 命令面/Catch 同步） | not-verified（`/nekojs reload startup` 的非事务提示未在 1.21.1 实跑；审查 A4 的顺序改正在 1.21.1 只做了**文本一致 + 编译通过**，未实跑） | owner 维护者（如 1.21.1 需要独立烟测，可复制 `bench/smoke-reload/` 并加 `-Node 1.21.1` 支持） |

---

## 7. 偏离 / 发现（尤其与 05 交付的摩擦）

1. **`while(true)` 的失败注入不能靠 runaway 时间窗口**（§6 ★）。这是本次实施最大的意外：工单 phase 4/5 期待的"被 watchdog 或语句上限终止"里，时间窗口那条路当前**不生效**。本票按"接收面"口径处理：`candidateKilled → discardCandidate` 用语句上限路径验证（单测 100k、烟测 200k）。同时测出语句上限的**可靠档位边界**（≤200k 在解释器内命中；5M 需 50 次谓词调用 → 被 JIT 绕过），这条边界已写进 fixture 注释，避免后来者重踩。
2. **候选执行的 `console` 输出对生产日志可见**：候选 Context 的 `LoggerStream` 立即接到同一日志，故「入口行」不能当 commit 判定面（本报告改用版本化 tick 作 witness）。这不是缺陷，是 spec 09 user story 28「不深回滚外部副作用」的直接体现，已在报告与 runner 注释里写明口径。
3. **per-script 错误 ≠ reload 失败**：候选脚本抛 JS 异常仍按既有语义记为脚本错误并**提交**（脚本 disabled + 错误面板可见），只有环境级失败（候选 Context 被资源上限关闭、阶段基础设施抛出）才走失败路径。这是 05 之前既有行为（`unreadableScriptSurfacesInErrorTrackerInsteadOfVanishing` 等 prior art），本票不动；但工单 AC2 的"execution 失败"须按此口径理解（已在 §5 AC2 行注明）。
4. **与 05 的交付无行为冲突**：`NekoRuntimeRoot.closeSilently` 的 manager → bridge → resources 顺序、`NekoRuntimeAssembly` 共享装配、`NekoRuntimeRootLifecycleTest` 的 close 语义都未改；本票只在 manager 内部增加 generation 记账。唯一"摩擦面"是 05 留给本票的 A2 unbind 注记——结论是 06 不引入 root 重建，故 unbind 无调用方（§6 末行）。
5. **写集边界外的一次最小适配**：`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java`（源码，非构建接线）加了一个 catch + 一段提示，与共享版/1.21.1 对齐。票 31 的领地是 `versions/*-fabric` 的**构建接线**（buildSrc 约定、`fabric.gradle.kts`、源根迁移），此文件可能与其源根迁移产生文本冲突，需协调者合并时留意（一行 catch 级冲突）。26.2.0-fabric 复用 26.1.2 fabric source bridge，故已追加 `:26.2.0-fabric:compileJava` 单独验证。**AC7 补充说明（审查要求）**：这个文件的改动是 **AC6 入口外观的必要面**——STARTUP 非事务结论必须在三个节点都「先于成功宣称」出现，否则 Fabric 节点的用户看到的仍是先成功再补边界（本轮 A4 修复同样落在该文件，冲突面从「一行 catch」扩到「同一代码块的顺序调整」），**不是**票 31 的构建接线；与票 31 的源根迁移仍存在潜在文本冲突，合并时需协调者裁决。
6. **golden/契约变化**：本票未触碰 `docs/architecture-refactor/baseline/` 下任何 golden 或 managed-surface 契约文件，`guardLint` 0 问题 → 无需走 09 的 REGENERATE 流程。**Phase 9 例外**：新增/修改的是本票自己的报告与证据归档（`.../2026-09-12-reload-candidate/REPORT.md` + `evidence/`），不是被 guard 的契约文件；`guardLint` 复跑仍 0 问题。

---

## 8. 与 07 / 10 的边界声明与对 07 的交接注记

**本票不包含（明确留给后续票）**

- **10 号票（GLOBAL_STATE 联合写集）**：`NekoGlobal.SHARED`（总账 I3）与 root map 的联合事务未动；本票的 commit 点只覆盖 generation-owned 资源，不宣称 global 写集事务化。
- **07 号票（RUNTIME_THREADS）**：owner-thread 队列、非 owner 排队、回调内 reload 的排队/拒绝、close 优先、reload 不重入的**调度**、watchdog 的生效与恢复策略、以及"commit 不与该 generation 在途 callback 重叠"的序列化安全点。

**给 07 的钩子（已就位，可直接消费）**

| 钩子 | 现状 | 07 需要补什么 |
|---|---|---|
| 候选状态机 | `candidateContext` / `candidateKilled` / `candidateKillScript` / `pendingListeners` 已就位，全部只在 owner thread 读写 | 把"owner thread"从"reload 调用线程"提升为显式队列目标；跨线程请求改排队 |
| 重入 | `reloadScripts` / `loadScripts` / `close` / `runTestScripts` 已共用实例锁（`synchronized`），reload 不重入（同步等待自身会死锁 → 07 应改为队列或显式拒绝结果） | 回调内 reload 的排队或拒绝；close 优先于未开始的 reload |
| watchdog 接收面 | `candidateKilled → discardCandidate`（candidate 丢弃、active 不变）与 `contextKilled → 下次取用重建`（active 隔离失败，不自动重跑脚本）已实现 | watchdog 的**触发有效性与调度**（含本报告 ★ 发现）；终止 active 时的隔离失败停止分发 |
| 事件交接序列化 | `commitGeneration` 的顺序（清扫 → 发布 runtime → 激活候选 → 释放旧 session → 释放旧环境）已定型，且**激活步骤经 EVENT_PLAN 预备后不抛**（审查 A1）；旧闭包另有 `isContextDead` 三态兜底 | 在 owner-thread 序列化入口内让 dispatch 与 commit 不重叠（spec 09 提交边界） |
| 事件计划预备 | `ReloadPhase.EVENT_PLAN` + `EventBusJS.PendingListener#prepareForActivation()`（审查 A1 新增）：候选期解析 dispatch key 等 commit 期工作，失败以 `domain=candidate-listener-plan` + 注册脚本 source 返回 | 07 若新增需要在 commit 期执行的工作（重入检查、队列交接），继续前移到本阶段，不要往 `commitGeneration` 里加抛点 |
| 平台 callback 接线 | `NativeEventsJS` 分发闭包加 `isContextDead` 短路（失败候选残留原生注册成为 no-op） | 原生注册面（NeoForge EVENT_BUS）的 per-generation 撤销（**审查确认：短路不等于撤销，候选期注册对生产分发并非 no-op**）；`bind(root)` 监听器的 unbind（总账 A2） |
| 阶段结果 | `ReloadPhase`（`PREPARATION`→`BINDING`→`EXECUTION`→`EVENT_PLAN`→`COMMIT` + 非候选 `STARTUP`/`FILE`；`UNKNOWN` 随死码删除）/ `ReloadFailureReport` / `NekoReloadException` / `ReloadResult.phase()/sourceLocation()` 已定型，命令面三节点已呈现且**非事务结论先于成功宣称** | 07 若新增调度类失败（排队拒绝/close 优先/重入拒绝），沿用同一结果契约加 phase，不要引入第二套错误模型 |

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

Phase 9 的两条定向复现（审查 A1 与 A4）：

```bash
# A1：非法 dispatch key 的候选期失败回归（先看 red：stash 掉 EventBusJS/ScriptManager 的修复）
./gradlew :common:test --tests "com.tkisor.nekojs.script.ScriptReloadGenerationTest.illegalDispatchKeyFailsCandidatePlanAndPreservesActive"

# A4：非事务结论先于成功宣称（烟测 Phase E）
grep -n "non-transactionally\|reload finished" \
     bench/smoke-reload/out/<stamp>-26.1.2/rcon-phaseE-reload-startup.txt
python -c "import json;print(json.load(open('bench/smoke-reload/out/<stamp>-26.1.2/checks.json'))['E_non_transactional_conclusion_precedes_success_line'])"
```

---

## 10. 审查修复记录（Phase 9：A1–A5）

审查结论：5 组缺陷（A1 正确性 + A2 死码 + A3 可见性 + A4 命令面顺序 + A5 文档失真）+ 6 条 AC 判定据实修正要求。
逐条给出「审查发现 → 修法 → 测试/证据」。**工单文件未改**（`docs/architecture-refactor/implementation-tickets/06-reload-commit.md` 保持原样）。

### A1（正确性，必修）commit 点不可回滚 —— 会泄漏候选并留下半激活 generation

**审查发现**：`commitGeneration` 把「旧监听器清扫 → 发布候选 runtime → 激活候选监听器」排在前面，而 `EventBusJS` 的 dispatch key 转换（`Value.as(keyType)`）被推迟到激活期才做。激活期抛出的**非** `NekoReloadException` 不被外层 `catch (NekoReloadException)` 接住 → `discardCandidate` 不执行。

**red 证据（先写测试、先看它失败）**：`evidence/a1-red-green-probe-2026-09-12.txt` 记录了 `git stash` 掉修复后同一输入的实测状态：

```
thrownType=java.lang.ClassCastException          ← 不是结构化失败结果
activeContextReplacedByCandidate=true            ← runtime 已指向候选（半激活）
candidateBecameRuntimeHalfActivated=true
generationAdvancedDespiteFailure=true (1 → 2)
activeListenersStillOnBus=false                  ← 旧代已清扫 + 候选未激活 ⇒ 该类型再无监听器
pendingListenersSize=0
leakedOldActiveContextStillUsable=true           ← 旧环境从未关闭（泄漏）
```

即审查的三项判断全部成立：候选泄漏、半激活 generation、active 不再接收事件（AC2 直接不成立）。

**修法（首选方案：把 commit 期会抛的工作前移到候选期）**：

- `EventBusJS.PendingListener` 新增 `prepareForActivation()` / `prepared` / `resolvedKey`：候选期把 dispatch key 解析成可直接挂载的 Java 对象；`activatePending` 对已预备的挂起注册**不再做任何转换**，未预备（非候选注册路径）保持原位转换，行为不变。
- `ScriptManager.reloadScriptsTransactional` 新增 **`ReloadPhase.EVENT_PLAN`** 阶段（在 EXECUTION 与 COMMIT 之间）：逐条 `prepareForActivation()`，失败即 `reloadFailure(..., EVENT_PLAN, sourceOfScriptId(注册脚本), "candidate-listener-plan", t)` → 走 `discardCandidate`。
- **为什么必须有这个显式阶段**：`ScriptExecutor.executeEntry` 按既有语义吞掉脚本级异常（per-script 错误 ≠ reload 失败，§7.3）。如果只在 `EventBusJS.execute` 里提前转换，异常仍会被吞成「脚本错误 + 候选照常提交」；必须让转换发生在**不在 JS 调用帧内**的 ScriptManager 候选阶段，异常才能到达事务式 reload 的失败路径。
- **为什么不选备选（commit 内补偿）**：备选要求承认「发布已发生，保留 active 语义不再成立」，并且要新增一段无法被测试触达的补偿分支（审查同时在 A2 反对死码）。前移方案让失败**必然发生在 commit 之前**，`discardCandidate` 是唯一失败出口，不需要补偿代码。
- **激活步骤的不可抛论证**（写进 `commitGeneration` 与 `PendingListener` 的 javadoc）：预备后剩余操作只有 `bus.listen(...)`（`CopyOnWriteArrayList.add` + `synchronized` 下把编译快照置 null）与 mirror 的 `ConcurrentHashMap.compute`——两者都没有条件性抛出路径；`activeListenersOnBus=true` / `activeContextReplacedByCandidate=false` 的 green 探针即是端到端证据。

**green 证据**：同一探针（`=== A1 probe ===`）显示 `thrownType=NekoReloadException`、`thrownHasStructuredReport=true`、`activeContextReplacedByCandidate=false`、`candidateBecameRuntime=false`、`generationAdvancedDespiteFailure=false`、`activeListenersOnBus=true`、`pendingListenersSize=0`、`candidateContextFieldUsable=false`（候选已关闭）。

**断言化回归**：`ScriptReloadGenerationTest#illegalDispatchKeyFailsCandidatePlanAndPreservesActive`（red 时失败于 `Unexpected type, expected: <NekoReloadException> but was: <ClassCastException>`；green 后通过），断言集覆盖审查要求的全部四项：候选资源全关、`pendingListeners` 空、active 的 runtime/scripts/generation/监听器 `assertSame` 不变、失败结果含 generation/phase/source location/owner/domain。

### A2（清理）死代码与自相矛盾

| 审查项 | 修法 | 位置 |
|---|---|---|
| `ScriptManager.loadScriptsInto(List, Context, NekoNodeRuntime)` 无调用方 | **删除**（而非让 `loadCandidateScripts` 复用）：候选路径需要 per-script 的 `candidateKillScript` 归因（AC3 source location），复用这个无归因的旧重载会丢信息；同时把 `loadScriptsInto` 的 javadoc 改成「只被 active 首次加载使用」，去掉「reload 复用」的失真描述 | `common/.../script/ScriptManager.java` |
| `ReloadResult.success(type)` 返回 `phase=COMMIT`+`generation=-1` 自相矛盾且无调用方 | **删除**；连带删除同样无调用方的 `failure(type, error)` 与随之失去唯一生产者的 `ReloadPhase.UNKNOWN`（留着就是新的死码） | `common/.../core/lifecycle/NekoRuntimeRoot.java`、`ReloadPhase.java` |
| `reloadFailure(...)` 的 `sourceLocation` 被丢弃（AC3 在 FILE 路径不成立） | 定位到实际丢失点是 **`ReloadResult` 没有 `sourceLocation` 字段**（`ReloadFailureReport` 本身一直有）：给 record 加该字段并在各工厂填充；顺带新增 `sourceLocation` 的测试断言 | `NekoRuntimeRoot.ReloadResult` + `NekoRuntimeRootReloadResultTest` |
| `candidateKillSource` 存的是 `ScriptContainer`，命名易误读为 Source | **重命名为 `candidateKillScript`** 并加 javadoc 明示「存的是脚本容器，不是 source 文本」 | `ScriptManager`（含 §2.1/§8 文档同步） |

### A3（可见性）`generation` 公开读点的可见性

**审查发现**：`generation` 无锁无 volatile，而 `ScriptManager.generationId()`（`NekoRuntimeRoot.reload`/`reloadFile` 都调它）是公开读点。

**修法**：`private volatile long generation`。取 volatile 而非收窄读点，理由：写入全部在实例锁临界区且每次 reload 只写一次，代价可忽略；而 `generationId()` 的调用者（命令面、平台调用方、测试）可能不在 owner thread 上，收窄读点会把可见性负担转嫁给每个调用方。javadoc 里写明「读点无需自带锁」的语义。

### A4（AC6 命令面顺序）不得先宣称成功

**审查发现**：三个节点都是先 `sendReloadResult("... scripts reloaded.")`（进而打印 `- no errors.`）**再**补一条「其实是 reset+load 非事务」——外部只看到成功。

**修法**：三个节点统一改为

1. 先输出非事务结论：`NekoJS <type> scripts reloaded non-transactionally (reset+load, phase=STARTUP): irreversible platform registrations are not rolled back - restart the game/loader for a clean STARTUP state.`
2. 再输出结果行，且非事务路径的结果行**不再重复「reloaded」**（避免二次宣称）：`NekoJS <type> scripts reload finished (non-transactional, phase=STARTUP). - no errors.`

节点：`src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java`、`versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java`、`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java`。

**测试/证据**：runner 新增判定 `E_non_transactional_conclusion_precedes_success_line`（判定面 = 结论中的 `non-transactionally` 早于结果行中的 `reload finished`）；26.1.2 烟测重跑 29/29 全绿，`rcon-phaseE-reload-startup-20260913T035123Z-26.1.2.txt` 保留应答原文顺序（与上一轮 `...031818Z...` 对比可见顺序反转）。1.21.1 / Fabric 节点只做文本一致 + 编译验证（未实跑，见 §6）。

### A5（文档与事实矛盾）烟测 fixture 口径失真

**审查发现**：`bench/smoke-reload/README.md` 与 `fixtures/nekojs/server_scripts/sm01-witness.js` 仍称失败注入靠 `scriptRunawayTimeoutSeconds = 2` watchdog，而同目录 `engine.toml` 实际是 `runaway=0 + scriptStatementLimit=200000`，报告自身也已判定时间窗口路径无效——后读维护者会被误导。

**修法**：两处文本改为「语句上限注入」口径，并显式写出「**不要**改用时间窗口路径 + 为什么无效 + 复现件位置」；同时修掉 runner 里 `Write-Boom` 注释仍写 `scriptStatementLimit=5e6` 的陈旧括号（实际 200k）。

### 未修 / 未闭合（owner 明确）

| 项 | 为什么没修 | owner |
|---|---|---|
| `NativeEventsJS` 候选期原生注册的原子撤销 | 域 Adapter 面（NeoForge EVENT_BUS per-generation 撤销），本票范围外；本次只把缺口写实（§5 AC1/AC2、§6、§8） | W7 域 Adapter |
| 候选构建前 `binding.close(type)` / `errorTracker.clearByType` 的账本回滚 | spec 09 user story 15 明确「共享 Java 对象不做 generation 私有快照」，位次是域契约 | W6/W7 域 Adapter、10 号票 |
| watchdog 时间窗口终止路径 | 属既有 core 缺陷（看门狗调度面），复现件已 `@Disabled` 钉住 | 07 号票 / 独立缺陷票 |
| AC4 释放顺序的字面一致 | 需拆分 `closeRuntimeResources` 只为对齐措辞，且不改变可观察行为；**请维护者裁决** | 维护者（如需则另开票） |
| STARTUP「返回不支持阶段」而不执行 | 会改变既有行为，需单独决策 | 维护者 |
| CLIENT 真机 / 1.21.1 真机 | 无头环境 | 维护者 / minecraft-mcp |
