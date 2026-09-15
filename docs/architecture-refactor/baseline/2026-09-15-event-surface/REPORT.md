# Ticket 14 实施报告：事件总线与 Script/Native/Probe 事件声明基础

> 工单：`docs/architecture-refactor/implementation-tickets/14-event-surface.md`。
> 权威 spec：`08-ported-features-event-surface.md`（本票核心）、`04-public-contract-and-plugin-model.md`、`07-validation-and-migration.md`。
> 基线 commit：`7fb521eb`（ticket 09 已关闭后的认领点）。本报告 commit 区间：`a54202ad..本提交`。
> 结论先行：11 条 AC 中 10 条满足（其中 AC8 主要由票 06/07 既有 fixture 满足、本票补引用与判定），AC10「Native/Probe 示例可运行」按面拆分满足（Script 示例单测可运行；Native/Probe 示例受 FML/probe 运行时约束，以 tier characterization + golden 承载，详见 §2 AC10 与 §8）。

## 0. Commit 清单

| Commit | 内容 | AC |
|---|---|---|
| `a54202ad` | `test(event): ticket 14 EventBusJS 外部行为并发 stress fixture` | AC1 |
| `c43701b5` | `test(api): ticket 14 EventContractReflector 契约派生 fixture` | AC2 |
| `0309494a` | `feat(probe)+test(event): ticket 14 ScriptEvents 动态声明可诊断失败与 TS/Python parity` | AC3 |
| `fd54db44` | `test(probe): ticket 14 ProbeEvents 扩展面 catalog/declaration golden`（含新 golden） | AC5 |
| `d80e30c6` | `test(event): ticket 14 NativeEvents legacy tier 定位与真实注册面无重复 bus fixture`（根树） | AC4/AC6 |
| `2b2ea07e` | `test(event): ticket 14 ScriptEvents 最小可运行示例链路` | AC10 |
| 本提交 | `docs(baseline): ticket 14 event surface report + examples + migration` | AC9/AC10 |

## 1. 现状测绘（Phase 0，实施前完成）

三类事件面的注册路径、tier 归属与声明来源（详见 §3 归属表与 MIGRATION.md）：

- **ScriptEvents（动态声明）**：STARTUP 脚本经 `ScriptEvents.server/client` 注册总线（`ScriptEvents.GROUP`，两条 startup 注册总线）声明 `ScriptEventDefinition` → `ScriptEventRegistry`（静态记账）→ `DefaultScriptEventBridge.bindEvents` 以 `ScriptEventGroupJS/ScriptEventBusJS` 绑定到 target side 环境；`NekoScriptCatalog.events` 并列进目录（`EventCatalogEntry.ofScriptEvent`，scriptDefined、无事件类）；TS 渲染 any payload + post（`EventDeclarationGenerator`），Python 渲染 Any payload（本票补 post，见 §7 P3）。注册 API 符号（`ScriptEventRegistrationEvent`）在 portable-core 契约反射输入内（ticket 09）。
- **NativeEvents（raw/legacy）**：`NativeEventsJS`（src/main，NeoForge-only）实现 `Binding`，只在 STARTUP 绑定集注册；声明来源是 `TypeDocCatalogEntry.binding(STARTUP, "NativeEvents", ...)`；不经 registerEvents（不是事件组）、不进 catalog events、不贡献 ApiContribution（不进 managedApis）。STARTUP reload 时 `close()` → `clear()` 注销上一轮原生监听器。
- **ProbeEvents（Probe 扩展）**：common 侧接口常量组（4 条 SERVER 总线），由 `NekoJSCorePlugin.registerEvents` 注册进 runtime；唯一 post 来源是 probe 管线（`ProbeIrBuilder` 4 处 + `ProbeCoordinator.needIr` 的 hasListeners 探测）；进 catalog events → probe TS/Python declaration（本票新增 golden）。

运行时机制（票 06/07 已落地，本票直接消费）：`EventBusJS.PendingListener`（候选期 `prepareForActivation`，commit 点不抛）、`ScriptLifecycleGate`（回调深度/重入拒绝）、watchdog 隔离失败（`isActiveFailed` 后停止分发）、owner-thread 分发（分发点 Context monitor 已删）。

## 2. 逐 AC 判定与证据指针

| AC | 判定 | 证据 |
|---|---|---|
| AC1 并发 mutation/post 后最终空态、无重复 dispatch | **满足** | `EventBusJSExternalBehaviorStressTest`（6 用例）：顺序前置（priority 顺序 `high→normal→low`、可取消返回 true 停止后续、dispatch key 定向 + `registeredKeys`、按 scriptId remove 不误伤他脚本、清扫后 mirror+底层 bus 双空）；并发核心（owner 注册/post 与 3 个 cleaner 线程的按 scriptId/整型 `clearTokens` 竞争同一 bin 锁，2000 post × 400 注册，收尾双空态，任意 (event, 注册) 送达次数 ≤1） |
| AC2 side 独立条目、side filter 无重复声明、契约/catalog 派生 | **满足** | 契约面：`EventContractReflectorDerivationTest`（4 用例：每 bus 恰一条 ContractEvent、tier/dispatch/cancellable/payload 冻结、server/client/mixed 独立条目、BY_ID/PLAIN 构造期拒绝）；catalog 面：既有 `NekoScriptCatalogEventsTest`（每 bus 一条 + side 单次命中 + mixed 独立）与 `NekoScriptCatalogScriptEventsTest`（动态条目独立）继续通过；声明面无重复由 AC5 golden + `LegacyProbeCompatibilityTest` 承载 |
| AC3 ScriptEvents 可诊断失败 + parity | **满足** | `ScriptEventsDeclarationDiagnosticsTest`（9 用例）：组名撞内置组/内置 binding/跨来源同 key 的带 key 拒绝、非法标识符拒绝、未清理重注册可诊断 + STARTUP 清扫后重注册恰好一条、动态/内置组「No such ... bus」与「not available in SIDE」、SERVER-target 组只绑 SERVER 环境 + listen/post 端到端、**同一 catalog entry 列表**驱动 TS+Python 生成器（listener 与 post 各恰好一次） |
| AC4 NativeEvents 保留 raw/legacy tier、不静默升级 | **满足** | 根树 `NativeEventsLegacyTierCharacterizationTest`（3 用例）：STARTUP-only 绑定注册、catalog 收录 = LegacySurfaceAdapter 观察（`global:NativeEvents`）+ events 目录零条目、声明来源 = TypeDocCatalogEntry（description/examples）；managed stable 唯一来源是 apiRuntime 贡献，NativeEventsJS 不注册 ApiContribution（结构性证据：注册路径仅 `NekoJSCorePlugin.registerBinding` 的 STARTUP 分支，无 ApiContributionRegistry 调用） |
| AC5 ProbeEvents 真 golden、不造通用事件/第二 bus | **满足** | `ProbeEventsSurfaceGoldenTest`（5 用例）+ 新 golden `common/src/test/resources/nekojs/probe/probe-events.expected.d.ts`（regenerate 流程生成，§4 留痕）：组成员冻结 4 条 probe-only、全部 SERVER；catalog 恰 4 条；TS golden SERVER 侧 4 成员各一次（真实 payload 类型）；CLIENT side 过滤零条目零声明；Python 同 entry 列表 4 成员各一次。唯一 post 来源 = ProbeIrBuilder（§6 旁路清单） |
| AC6 每 bus 一次、事件域不被复制 | **满足** | 既有 `NekoScriptCatalogEventsTest`/`NekoScriptCatalogScriptEventsTest` + 根树 `EventSurfaceOwnershipTest`（3 用例，生产注册路径 `NekoJSCorePlugin.registerEvents + registerClientEvents`）：14 个域组各就各位、跨组 bus identity 唯一、catalog (group,name) 无重复且条目数=总线数、ScriptEvents 组仅 {server,client}、ProbeEvents 组仅 4 条、NativeEvents 非事件组、动态声明拒绝内置组名 |
| AC7 平台时机留 Adapter、common 无 MC/loader 类型 | **满足** | 本票 common 主代码唯一改动 `PythonEventRenderer`（import 仅 java/probe/surface）；guardLint 通过（§5）；平台 callback 仍在 src/main 的 `EventBusForgeBridge`（`bus().listen` 桥接 NeoForge IEventBus，域组全部经它）与 `NativeEventsJS`（NeoForge.EVENT_BUS 直挂） |
| AC8 reload 失败/取消不双注册、不泄漏旧 generation | **满足（既有 fixture 判定）** | 票 06 `ScriptReloadGenerationTest`：`failedReloadKeepsActiveListenersOnBus`（active 不动）、`failedReloadDiscardsCandidateListenersOnly`（候选 v2 永不触发、active v1 恰一次=无双注册）、`commitSweepsOldListenersBeforePublishingNewRuntime`（清扫先于发布、恰一次）、`successfulReloadRunsScriptsOnceAndSingleExecutionAfterCommit`（旧代不再收、新代恰一次）。票 07 `Ticket07RuntimeThreadsTest`：`closePreemptsRemainingCandidateScriptsAndNeverCommits`（取消永不 commit）、`reloadInsideManagedCallbackIsRejectedNotRecursive`（回调可见性：重入拒绝）、watchdog 隔离两用例。本票 AC1 stress 的并发清理面（clearTokens 竞争）为同一 bookkeeping 的压力补充 |
| AC9 收缩 gate | **满足（本票零删除，证据齐前不移除）** | §6 旁路与调用者清单：4 条旧事件注册/声明旁路全部保留——替代 declaration（managed TS）只覆盖 facade 符号不覆盖事件、事件 contract 的 trace 面尚为运行时反射观察（票 09 §1.2 结论）、`EventBusJS.bus()` 直挂路径仍被平台 Adapter 生产调用。合法 legacy/raw tier（NativeEvents/ProbeEvents/legacy catalog）与公开功能未删除；清理不推迟 final release 的义务以清单 + 删除条件形式移交后续票 |
| AC10 示例与迁移材料 | **满足（面拆分，见 §8 G3）** | `examples/script-events.js` + `ScriptEventsMinimalExampleTest`（真实 Graal 按生产序列跑通 declare→post→listen，console.log 端到端）；`examples/native-events-legacy.js`（明确标注 legacy/raw、非 managed stable、NeoForge-only；行为由根树 characterization 承载——单测无法进入 FML loader）；`examples/probe-events.js`（probe 扩展面，golden 即运行证据——事件只在 /nekojs probe 触发）；`MIGRATION.md`（tier 归属表 + 1.2.0 迁移要点 + 已知边界） |

**范围外遵守**：未动 recipe/capability/goal/实体行为域内事件语义；未做插件 addon 外部发现；无 performance 阈值变更；未动 GUI/网络域。

## 3. 三类事件面 tier 归属表

（与 MIGRATION.md §1 同源；「声明来源」= TS/Python declaration 的派生输入路径）

| 面 | tier | 声明来源 | side | 声明路径唯一性证据 |
|---|---|---|---|---|
| ScriptEvents 注册 API | managed（portable-core 契约反射符号 `member:ScriptEventRegistrationEvent.*`） | `CoreManagedApiBootstrap.buildContract` 反射 → manifest | STARTUP | `api-manifest-core.json`（本票零变化） |
| ScriptEvents 声明出的动态事件 | legacy 观察（catalog events，scriptDefined） | `NekoScriptCatalog.events` → `EventDeclarationGenerator`(TS) / `PythonEventRenderer`(Py) | 声明 target（SERVER/CLIENT） | AC3 parity 用例（同 entry 列表双后端） |
| 内置事件组（14 组） | legacy 观察（catalog events）+ managed 回调 schema（`EventContractReflector` 运行时反射） | 同上（catalog 派生） | 各组规范 side | AC2/AC6 fixture |
| NativeEvents | **legacy/raw Adapter 观察**（不升 managed stable） | `TypeDocCatalogEntry`（probe 补全/文档）；不进 catalog events | STARTUP 注册；事件 side 由 NeoForge bus 决定 | AC4 characterization |
| ProbeEvents | Probe 扩展面（probe 管线独占触发） | catalog events → probe declaration（本票新增 golden） | SERVER-only | AC5 golden |

## 4. golden 变更留痕（REGENERATE.md §3 格式）

**唯一变更：新增 `common/src/test/resources/nekojs/probe/probe-events.expected.d.ts`（46 行）**

- **原因**：AC5 要求 ProbeEvents 事件→真实 catalog/declaration golden。输入 = `ProbeEvents.GROUP` 的 4 条总线（`modifyType/assignType/addGlobal/snippets`，payload 类 `ProbeModifyTypeEventJS` 等）经 `NekoScriptCatalog.events` → `EventDeclarationGenerator.generate(entries, SERVER)` 反射派生。
- **旧新 diff**：新文件（旧值：不存在）。内容 = 31 行 java imports（payload 类反射 BFS，包内排序保证确定性）+ `declare module "@side-only/server/events"` 空声明 + `declare global { namespace ProbeEvents { 4 个 function 声明 } }`。生成命令与只读复核见 `evidence/verification-commands.md`。
- **影响**：新 golden 只被 `ProbeEventsSurfaceGoldenTest` 消费；`api-manifest-core.json`、`probe-ts/generated/index.d.ts`、`probe/legacy-*` 全部零变化（契约输入未动）；`npm run test:probe-types` 复核通过。Python 侧无事件 golden（沿票 09 惯例：Python 确定性/parity 在内存断言，不入基线）。
- **审阅记录**：owner 自查（zcode-agent，2026-09-15）——逐行核对 import 列表与 4 个函数签名均来自 ProbeEvents 真实成员与 payload 反射，无手工编辑。**缺维护者审阅**（票 25 先例如实标注：目前所有 golden 审阅记录只有 owner 自查）。

主代码改动 `PythonEventRenderer`（scriptDefined 补 post）**不触发任何既有 golden 变化**：legacy probe golden 不含 scriptDefined 事件（`SampleEvent` 非 scriptDefined），无 Python 事件 golden 存在。

## 5. 测试结果

| 套件 | 结果 | 数字 |
|---|---|---|
| `:common:check` | 通过 | 200 suites / 1474 tests / 0 failures / 4 skipped（本票 +5 suites / +25 tests：Stress 6、Contract 4、Diagnostics 9、Example 1、ProbeGolden 5） |
| `:common-api-processor:test` + `guardLint` | 通过 | guardLint 确认 common 主代码无 MC/loader 类型（AC7） |
| `:26.1.2:check`（含 `--rerun-tasks` 复核） | 通过 | 40 suites / 179 tests / 0 failures / 34 skipped（本票 +2 suites / +6 tests） |
| `npm run test:probe-types` | 通过 | tsc 无错误 |

## 6. 旁路与调用者清单（AC9 收缩 gate 输入）

| 旁路 | 现状调用者 | 替代物状态 | 处置 |
|---|---|---|---|
| `EventBusJS.bus()` 直挂 Java 监听（绕过 JS mirror 记账） | `EventBusForgeBridge`（src/main，全部内置域组的 NeoForge 桥）、测试（`ProbeCoordinatorHardeningTest` 等） | managed TS declaration 只覆盖 facade 符号，不含事件；无 declaration/trace 替代 | **保留**（平台 Adapter 正当入口；hasListeners 已兜底底层 bus 非空） |
| `EventContractReflector` 运行时反射 eventGroups → `ManagedCallbackSchemaRegistry.installContractEvents` | `NekoPluginRuntime.installManagedCallbackSchemas`（生产唯一） | 契约 JSON events 字段已删（票 09）；事件 schema 的规范源仍是运行时观察 | **保留**（票 09 §1.2 已裁定为观察面不写回契约；事件进 portable-core 契约的事件面归后续票） |
| `NekoScriptCatalog.events` legacy 观察派生（→ legacySurface → probe declaration） | probe TS/Python 后端、`LegacySurfaceAdapter` | managed declaration 不渲染事件 | **保留**（事件声明的当前唯一派生路径；legacy shadow characterization 已冻结不升 stable） |
| `TypeDocCatalogEntry` 片段作为 NativeEvents 声明来源 | `NekoJSCorePlugin.registerTypeDocs`（生产唯一） | 无（raw 面不计划进 managed declaration） | **保留**（合法 legacy/raw tier） |
| ProbeEvents 4 总线的 post 旁路 | `ProbeIrBuilder`（4 处 post）+ `ProbeCoordinator`（hasListeners 探测） | 无（probe 扩展面独占触发是其定义） | **保留**（不是通用运行时事件；成员集合已冻结） |

**收缩 gate 判定**：上表无任何一行满足「替代 behavior/declaration/trace 通过 + 无调用者」——本轮零删除。合法 legacy/raw tier 与公开功能全部保留；后续清理必须以本清单为输入逐行补证据后再动。

## 7. 问题与处理

1. **Graal `Value` 操作即进入所属 Context（多线程拒绝）**（AC1 设计过程发现）：loader 线程调用 `bus.execute(listener)` 时 `parsePriority` 的 `Value.isString()` 触发 `Multi threaded access` 拒绝。处理：stress 按票 07 owner-thread 契约重设计——注册与回调只在 owner 线程，reload 清理（纯 host 记账）并发。这不是 bug，是被 fixture 钉住的线程契约本身。
2. **`ScriptEventsJS.register` 的 `validateAvailable` 前置使同来源 replacement 分支不可达**（AC3 过程发现）：`ScriptEventRegistry.register` 的同 source replacement 语义（`ScriptEventRegistryTest` 直测）在生产入口 `ScriptEventsJS` 上永远走不到——`validateAvailable` 先抛「already registered」。生产行为依赖 STARTUP 清扫先行（`DefaultScriptEventBridge.clearListeners`），语义自洽且可诊断，故**不改主代码**；以 `reloadCleanupMakesReregistrationPossibleAfterDiagnosableConflict` 钉住生产序列，边界记入 MIGRATION.md §3。
3. **Python 声明缺 scriptDefined `post`（真缺口，已修）**：TS 渲染 any payload + post，runtime member（`ScriptEventBusJS`）可调用且带 post，Python 只渲染 Any payload 的 handler——三面不齐。修复：`PythonEventRenderer` 为 scriptDefined 条目渲染「嵌套类 `__call__` + `post` + 同名实例注解」（与 TS 同一 catalog 条目驱动）。无既有 golden 受影响。
4. **`NekoScriptCatalog.snapshot()` 依赖运行中的 FML loader**（AC4 过程发现）：根树单测经 `EnvironmentKey.current` → `NeoForgePlatform.isClient` → `FMLLoader` 抛 `There is no current FML Loader`。处理：characterization 直接驱动同一 `LegacySurfaceAdapter.convert`（snapshot 的 legacy 投影步骤），不进 FML 路径；已在测试注释说明。
5. **工单文件只读**：未改 `implementation-tickets/14-event-surface.md` 的 Status/AC 勾选（关票归主会话）。

## 8. 遗留缺口与建议 owner

| # | 事项 | 状态 | 建议 owner |
|---|---|---|---|
| G1 | NativeEvents/ProbeEvents 示例的**真机运行**证据（单测无法进入 FML loader / probe 需游戏内 `/nekojs probe`） | not-verified（tier/catalog/声明面已由 characterization + golden 覆盖） | 主会话可用 minecraft-mod-mcp 做一次 in-game smoke（AGENTS.md 的 MCP 通道） |
| G2 | 事件面进入 portable-core **契约 events 字段**（当前 ContractEvent 是运行时反射观察，见 §6 第 2 行） | 结构性事实，未改变（票 09 裁定） | W5 Managed Surface owner（后续票：事件 schema 规范化） |
| G3 | AC10 口径：Native/Probe 示例未在自动化中执行 | 已如实拆分满足（Script 示例可运行；Native/Probe 受运行时约束） | 随 G1 一并闭合 |
| G4 | `ScriptEventsJS` 的 replacement 不可达（§7 P2）：若未来允许免清扫重放需调整注册入口顺序 | 已钉住现状 + 记边界 | 事件面后续票（如 STARTUP reload 语义细化） |
| G5 | 其他节点（:26.2.0/:1.21.1/两个 Fabric）check 未跑（Java 主代码改动仅 common；Fabric 无 NativeEvents 对应面） | 本票未验证 | W9/W10 发布 gate |
| G6 | golden 审阅缺维护者确认（§4） | owner 自查完成 | 主会话维护者审阅 |

## 9. 需要主会话审查的重点

1. **新 golden `probe-events.expected.d.ts`**：46 行 import 列表较大（payload 类反射 BFS 的既定行为），确认接受其作为 ProbeEvents 声明基线（§4）。
2. **`PythonEventRenderer` 的 scriptDefined 渲染形状**：嵌套类 `__call__` + `post` 模型是否符合 Python 侧期望（票 09 曾裁定 Python parity 以 fixture 表达；本票是事件面的最小对齐）。
3. **AC8 的判定口径**：本票以票 06/07 既有 fixture 满足（未新增重复用例），确认该引用式判定可接受。
4. **AC9 清单处置**：本轮零删除、五条旁路全保留（§6），确认收缩节奏与后续票的输入交接。
