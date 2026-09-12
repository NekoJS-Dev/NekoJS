# 2026-09-12 runtime root 重构报告（ticket 05 / 单 owner 预整理）

> 工单：`docs/architecture-refactor/implementation-tickets/05-runtime-root.md`（W1 预整理：单一 `NekoRuntimeRoot` + shared construction function + 旁路删除）。
> 实施区间：`94557b41..57022321`（基线 `4020c130`，01/02/03 已关闭）。执行方式：6 个 Phase 各一 commit。
> 总账：[`../2026-09-12-runtime-ledger.md`](../2026-09-12-runtime-ledger.md)（Phase 1，符号级 owner 分类与 AC1 维度映射）。

## 1. Commit 清单

| commit | Phase | 内容 |
|---|---|---|
| `94557b41` | 1 | `docs(baseline): ticket 05 static lifecycle state ledger`（不改代码） |
| `ece176c4` | 2 | `refactor(lifecycle): shared runtime assembly for both loaders`（NekoRuntimeAssembly + 删除重复 manager 容器 `NekoJS.scriptManagers`） |
| `eac4a6c2` | 3 | `refactor(lifecycle): inject lifecycle handle, migrate static root readers`（13 个生产读者迁移，1.21.1 孪生同步） |
| `227550b3` | 4 | `test(lifecycle): behavior smoke for single-owner runtime (both loaders)`（2 个新单测类 + bench/smoke + 双 loader 烟测证据） |
| `57022321` | 5 | `refactor(lifecycle): remove static root bypass after full migration`（删除两个 public static root 字段 + 复验） |
| 本 commit | 6 | `docs(baseline): ticket 05 runtime root refactor report`（本报告 + evidence gz） |

## 2. 装配序列 before / after

**Before**（两 loader 各自一份，逐行相同）：
```
loader discovery（NeoForgePluginLoader / FabricPluginLoader）
→ NekoPluginRuntime.bootstrapOwned(ownedPlugins, props) → NekoRuntimeAccess.fireInit()
→ loader 事件面接线（registrar.bindRuntime / bridge.setPluginRuntime，顺序两 loader 不同）
→ ScriptCompilerRegistry.current() → ClassFilter.loadEngineConfig() → ClassFilter.INSTANCE
→ new DefaultErrorTracker → ScriptErrorReporter.set(ErrorTrackerReporter)
→ new NekoCoreContext(NekoSharedEngine.get(), …) → new NekoSandboxFactory(…)
→ NekoModulePipeline.bindLegacyInstance(new NekoModulePipeline(new NekoCompilationPipeline(), …))
→ new NekoRuntimeRoot(core, pluginRuntime, bridge, props, sandboxFactory)
→ for autoLoadTypes { createScriptManager + scriptManagers.set(重复容器) + discoverScripts }
→ scriptManagers.at(STARTUP).loadScripts() → fireInitStartup() → GoalEvents.postRegister()
→ RUNTIME_ROOT = <root>（public static 字段，全仓可读）
```

**After**（`common/.../core/lifecycle/NekoRuntimeAssembly.assemble(...)` 单一构造实现）：
- 相同序列整段收口进共享装配函数；loader 差异保留在 entry：插件发现、`PluginWiring` 回调内接线顺序（NeoForge `bindRuntime→setPluginRuntime` / Fabric 反序，逐字保持）、`GoalEvents.postRegister()`、`fireAfterInit()` 时机。
- 装配函数不缓存任何 static、无 `get()`/`current()`，产物由 entry 私有持有；无 RuntimeKernel / gateway / service locator / 第二 owner。
- 重复 manager 容器 `NekoJS.scriptManagers`（ScriptTypedValue 镜像）随 manager 循环移入装配函数而失去全部生产者/读者，Phase 2 同 commit 删除。
- NeoForge root：mod entry 构造期 **final local**（不落任何字段）；Fabric root：entry `private static runtimeRoot` + package-private `runtimeRootOrNull()`（Fabric entrypoint 由 loader 反射实例化，无法构造注入；同包 composition 家族唯一读点）。

## 3. 迁移清单（读者 → 新注入方式）

| # | 读者（26.x / 1.21.1 孪生） | 旧读取 | 新方式 |
|---|---|---|---|
| 1 | `client/NekoJSClient`（×2 节点树） | `NekoJSMod.RUNTIME_ROOT` ×4 处 | `register(IEventBus, NekoRuntimeRoot)`；事件监听器 lambda 显式类型捕获 root |
| 2 | `command/NekoJSCommands`（×2） | ×9 处 | `register(RegisterCommandsEvent, NekoRuntimeRoot)`；命令树构建期捕获 root，reload/errors 全经 root |
| 3 | `listener/ServerEventListener`（×2） | ×4 处 | `bind(NekoRuntimeRoot)`（entry 装配后注入的私有 static handle） |
| 4 | `listener/PDataSyncListener` | ×2 处 | `bind(NekoRuntimeRoot)` 同上 |
| 5 | `listener/PlayerEventListener`（×2） | `errors().count()` ×2 | `ScriptErrorReporter.errorCount()`（root-owned ErrorTracker 的静态报告门面，总账 A5） |
| 6 | `mixin/RecipeManagerMixin`（×2） | `errorTracker()/errors()` ×5 处 | `ScriptErrorReporter.recordEventError/hasErrors/errorCount()`（mixin 静态上下文无法注入，经既有静态门面改道；Reporter 接口加 3 个 default 方法，NOOP 语义不变；stonecutter 双 loader 分支随之统一） |
| 7 | `network/PackSyncClientConnections` | ×3 处 | `bind(NekoRuntimeRoot)`；`PackSyncClient.clientReloadHook` 闭包改走注入 handle（保留原 null 判定语义） |
| 8 | `fabric/NekoJSFabricClient` | ×2 处 | `NekoJSFabricMod.runtimeRootOrNull()`（package-private seam） |
| 9 | `fabric/FabricPackSync` | ×3 处 | 同上 |
| 10 | `fabric/FabricNekoJSCommands` | ×1 处 | 同上 |
| 11 | `fabric/NekoJSFabricMod` 自身（flushClientNodeTimers / FabricServerEventBindings reload 回调） | ×4 处 | entry 私有字段直读（不外泄） |

合计：**11 个读者簇 / 13 个文件 / 约 35 个读取点**全部迁移；`test` 代码零迁移（test-only seam 在总账 J 节标注），仅 `NekoJSCommandsEditorRemovalTest` 因签名变更适配调用点（树构建期不解引用 root，传 null，断言不变）。

## 4. 烟测矩阵（两 loader × 生命周期）

执行器：`bench/smoke/run-smoke.ps1 -Node <node>`（RCON 通道复用 ticket 02 已验证结论；fixture 为最小 marker 脚本 + 默认示例脚本，见 `bench/smoke/fixtures/nekojs/`）。

| 检查项 | NeoForge `26.1.2`（run/） | Fabric `26.1.2-fabric`（run-server/） |
|---|---|---|
| startup（mod 构造期装配 + STARTUP load） | startup.log：`发现了 2 个 STARTUP 脚本` ×1 + marker ×1 | 同左 |
| server started | `Done(0.254s)!`（phase4）/ `Done(0.254s)`（phase5） | `Done(0.345s)` / `Done(0.321s)` |
| SERVER 首次加载（server started 前资源 reload） | server.log marker ×1（reload 前） | 同左 |
| SERVER reload（RCON `nekojs reload server` → root.reload(SERVER)） | 应答 `NekoJS server scripts reloaded. - no errors.`；marker 计数 +1 | 同左 |
| CLIENT（专用服务器） | client.log 不产生（marker=0，manager 已建） | 同左 |
| afterInit（`fireAfterInit` 平台时机） | FMLLoadComplete enqueueWork（NeoForge 专属，无日志面）；reload 前后无重复注册异常 | `onInitialize` 尾部（同位次）；无重复异常 |
| close（RCON stop） | exitCode=0，无二次回调异常，session.lock 释放 | 同左 |
| 错误计数 | `nekojs error` → healthy（0） | `nekojs.command.error.healthy`（0） |

- 每会话 10 项自动 checks 全绿（`evidence/checks-20260912T*.json` 4 份：phase4 两个 loader 各 1 + phase5 删除旁路后各 1）。注意：phase2 删除的 `NekoJS.scriptManagers`（重复 manager 容器）早于烟测，由单测与后续烟测共同覆盖；AC10 的"烟测后删除"时序严格适用于 static root 旁路本身。`clean_exit` check 的实际断言是 `stopChannel == "rcon"`+ gradle 客户端退出；`session.lock` 释放目前仅 WARN 不计入 checks（run-smoke.ps1:191-199）。
- AC8 计数证据：fabric stdout `fabric entrypoint reached` ×1、`fabric bootstrap done (startup scripts loaded, registry drained)` ×1（reload 后计数不变，无第二套装配）；NeoForge 无单行注册日志，以（a）reload 响应 healthy、（b）全日志无重复装配/注册异常、（c）`publish()` 唯一调用路径在 `NekoRuntimeAssembly`（代码层单一调用点）为证据。
- CLIENT 真实端（client load / F3+T / 渲染侧）：**not-verified（owner 维护者 / minecraft-mcp）**；无头最小替代 = `NekoRuntimeRootLifecycleTest.clientLoadAndReloadThroughRootLifecycleEntry`（CLIENT manager loadScripts + reload(CLIENT) + 错误边界经同一 root 入口，即 client listener 调用的同一组 API）。

## 5. 测试计数对比

| 范围 | 基线报告（01 号票时点） | 本票后（实测） | 本票净增 |
|---|---|---|---|
| `:common` | 177 suites / 1336 tests / 2 skipped | **181 / 1366 / 4** | +2 suites / +6 tests（`NekoRuntimeRootLifecycleTest` 5 + `NekoRuntimeAccessTest` 1）；与基线报告的其余差值（+2 suites/+24 tests、LocalErrorSourceTest 新增 2 个 assume skip）来自基线测量之后、本票开始之前已合入的 editor-removal 等测试（`a2715b03`），非本票引入 |
| `:common-api-processor` | 1 / 13 / 0 | 1 / 13 / 0 | — |
| `:26.1.2` / `:26.2.0` test | 29 / 137 / 34 | 33 / 155 / 34 | 本票 0 新增（差异同上源；`NekoJSCommandsEditorRemovalTest` 调用点适配，测试数不变） |
| `:1.21.1` test | 17 / 58 / 0 | 21 / 76 / 0 | 本票 0 新增 |
| fabric 两节点 test | 8 / 37 / 6 | 11 / 58 / 6 | 本票 0 新增 |
| guardLint | 0 问题 | 0 问题（check 块全量扫描） | — |
| nbtSmokeTest ×3 节点 | 8×3 | 随 `:26.x:check` 通过 | — |

全套命令：`./gradlew :common:check :common-api-processor:test guardLint :26.1.2:check :26.1.2-fabric:check :1.21.1:check :26.2.0:check :26.2.0-fabric:check` → BUILD SUCCESSFUL，0 failed（Phase 4 后跑一轮；Phase 5 删除旁路后复跑 `:common:check` + guardLint + 双 loader 烟测确认）。

## 6. AC1–AC10 逐条判定建议与证据指针

| AC | 建议 | 证据 |
|---|---|---|
| AC1 static 总账覆盖点名维度 | **pass** | 总账 §L 映射：runtime access→B1、plugin current→B2、plugin entries→B3/I7、shared engine→B4、platform/path→C1-C3、compiler→A7-A10、schema→D1-D3/D6-D7、event callback→D4-D5/E16、loader 侧 server/level/event/registry→E1-E13/I9；每项有 owner/生命周期/可替换性/测试或删除理由 |
| AC2 进程级例外不成第二 owner、可重复测试、close 释放、独立 root 不互污染 | **pass** | `NekoRuntimeAccessTest`（单一槽位 + fire 计数）、`NekoRuntimeRootLifecycleTest`（共享 NekoSharedEngine 下独立 root 错误面/manager 隔离 + close 释放）；总账 B 节保留理由与约束（publish 唯一调用路径） |
| AC3 每 loader 只创建一个 root、entry 外无公开 static root | **pass** | Phase 5 删除后全仓 grep：`RUNTIME_ROOT` 符号零残留；`runtimeRootOrNull()` 为 package-private 唯一读点（fabric entrypoint 反射实例化的结构性约束）；两装配函数各只调用一次 |
| AC4 STARTUP/SERVER/CLIENT/afterInit 时机与旧烟测一致 | **pass** | 装配序列逐行收口（不改时序）；烟测矩阵第 1-2、6 行；1.21.1 节点编译验证共享树守卫不变 |
| AC5 SERVER/CLIENT reload 只经 root 入口；命令/F3+T/pack sync/listener 不再直触 static root | **pass** | 迁移清单 §3：reload 全部经 `root.reload(type)`；`ScriptManager` 无 listener 直写（flushTimers/clearWorldPackListeners 属 manager 查询面，经注入 handle） |
| AC6 close 冲刷、异常不阻断、重复 close 无二次回调 | **pass** | `NekoRuntimeRootLifecycleTest.closeReleasesManagersAndClearsBridgeListeners / closeExceptionDoesNotBlockSubsequentCleanup / doubleCloseIsSafe`；烟测 RCON stop exitCode=0 |
| AC7 双 loader 烟测可复现（marker/错误计数/资源释放/退出状态） | **pass** | `bench/smoke/run-smoke.ps1` 可复现脚本 + 4 份 checks.json + gz 日志（evidence/）；输出脚本 marker、错误应答、exitCode |
| AC8 Plugin Runtime bootstrap / 平台事件注册 / network 注册 reload 前后各一次 | **pass（含口径限制）** | fabric 计数 marker ×1（§4 AC8 行）；NeoForge 无单行注册日志——以唯一调用路径 + 无重复异常 + reload healthy 为证（结构性证据，维护者可在真实客户端用 mcp 复核）；共同装配函数未产生第二套状态 |
| AC9 config/world/pdata/pack/trust/workspace 路径格式 key wire 默认规则与基线一致 | **pass** | 本票未触碰任何数据面代码（数据面仅 pack sync 钩子的注入方式改变，wire/格式不动）；03 号票冻结边界全程遵守 |
| AC10 旁路、重复容器、旧装配路线同票删除 | **pass** | Phase 2 删 `NekoJS.scriptManagers`；Phase 2 收口旧装配；Phase 5 删两个 public static root 字段（烟测绿后同票删除，无并行路径） |

## 7. not-verified 与 owner

| 项 | 状态 | owner / 后续 |
|---|---|---|
| CLIENT 真实端（client load、F3+T reload、客户端 HUD/渲染路径） | not-verified（无头环境不可真跑） | owner 维护者：真实客户端 smoke；或 minecraft-mcp 桥接客户端验证 |
| AC8 的 NeoForge 侧单行注册计数日志 | 结构性证据（无现成日志面） | owner 维护者：如需一行式证据，可在 W7/W9 的 smoke 门面中补 `LOGGER.info` 计数行（本票不改行为，未加日志） |
| 06/07（candidate/active/generation、watchdog、owner-thread 串行化） | 未实施（本票红线） | tickets 06/07；总账 A9（NekoEsmVirtualModuleRegistry GENERATIONS）等已标注 |

## 7b. code-review 裁决记录（2026-09-12）

- **类名撞名说明**：规格 01 的"不建清单"列有 "`RuntimeAssembly`"字样，指**不要新建一个常驻 runtime 装配层**；
  本票的 `NekoRuntimeAssembly` 是无字段、无 get/current 的纯构造函数（root 只存在于 loader entry 的
  final local / private 字段），属规格允许的"共享构造函数"形态，未撞入禁用清单所指的层。若后续演进
  让它开始持有状态，即违反本裁决，应改名并回到决策。
- **fabric 字段 volatile**：`NekoJSFabricMod.runtimeRoot`（entrypoint 线程写、server/client 线程读）已补 volatile。
- **`Assembled` 返回值收敛**：两个调用方都只用 `.root()`，`pluginRuntime` 分量已从 record 删除（防投机访问面）。
- **AC8 计数面补齐（NeoForge）**：装配函数在 `bootstrapOwned` 后输出一行
  `NekoJS plugin runtime bootstrapped once (assembly)`（NekoJS logger），烟测可 grep 计数（应为 1，
  reload 前后不变）；Fabric 侧原有 entrypoint/bootstrap-done marker 不变。该行是 review 后新增，
  formal 烟测截图不含它（纯日志增量，无行为影响）。
- **AC2 测试补强**：`NekoRuntimeAccessTest` 槽位用例改为自愈（保存/还原进程槽位）；
  `NekoRuntimeRootLifecycleTest` 显式断言 ErrorTracker 存活语义（防 06/07 语义漂移无人察觉）。
- **总账 A2 补删除条件**：`runtimeRootOrNull()` 的删除条件（W7/W8 可注入通道）与 3 个 `bind(root)`
  无 unbind 的 06/07 风险注记已入账（§8.4 原声称"已标注"与账不符，已修正）。
- **evidence 文件名/口径更正**：checks 文件实际为 `checks-20260912T*.json`（非 phase4/5 命名）；
  `clean_exit` check 的实际断言是 `stopChannel == "rcon"` + gradle 客户端退出，`session.lock` 释放
  目前仅 WARN 不计入 checks（run-smoke.ps1:191-199）。

## 8. 偏离、问题与既有 bug 记录

1. **smoke 脚本两轮 shakedown**：shell `&` 后台任务随工具调用终止（改用平台后台任务）；`Done (0.283s)!` 的 `s` 单位正则遗漏。修复后 4 会话全绿；失败会话不入库。
2. **`addListener` 泛型推断**：NeoForge `addListener(Consumer<T>)` 的隐式 lambda 无法从 body 推断 T，注入 lambda 补显式事件参数类型（编译需求，行为不变）。
3. **既有实现事实（未顺手修，记录）**：`NekoRuntimeRoot.closeSilently()` 重复 close 时会对 bridge 逐类型再调一次 `clearListeners`（幂等注册表清理，无监听器回调）；`doubleCloseIsSafe` 测试按此语义断言。若 06/07 希望收敛为显式 `closed` 短路，属行为变更需另票。
4. **fabric entrypoint 无法构造注入**：Fabric loader 反射实例化 entrypoint，root 读取只能经 entry 的 package-private seam（`runtimeRootOrNull`）。这是平台约束下的最小 seam，已在总账 A2/E7 标注删除条件（W7/W8 平台 Adapter 收口时一并处理）。
5. **ScriptErrorReporter 门面扩展**（api/event 接口 +3 default 方法）：mixin/静态上下文的错误读取替代面，属于"经已有静态门面改道 root"的授权方案；契约/golden 未受影响（ApiManifestGoldenTest 等全绿）。

## 9. 删除的旁路清单（符号 + 规模）

| 符号 | 位置 | 处置 |
|---|---|---|
| `public static NekoRuntimeRoot NekoJSMod.RUNTIME_ROOT` | `src/main/java/com/tkisor/nekojs/NekoJSMod.java`（原 :52，赋值 :163） | 删除（Phase 5）：字段 + 5 个外部读点 → 0；root 变构造期 final local |
| `public static NekoRuntimeRoot NekoJSFabricMod.RUNTIME_ROOT` | `versions/26.1.2-fabric/.../NekoJSFabricMod.java`（原 :59，赋值 :154） | 删除（Phase 5）：字段 + 6 个外部读点 → private + package-private accessor |
| `public final ScriptTypedValue<ScriptManager> NekoJS.scriptManagers` | `common/.../NekoJS.java`（原 :21） | 删除（Phase 2）：重复 manager 容器，manager 循环收口进 NekoRuntimeAssembly |
| 旧直接装配路线（两 loader initializeScripts 内 20+ 行装配体） | `NekoJSMod.java` / `NekoJSFabricMod.java` | 收口（Phase 2）：`NekoRuntimeAssembly.assemble(...)` 单一实现 |
| RecipeManagerMixin 内 fabric 分支的 `NekoJSFabricMod.RUNTIME_ROOT` stonecutter 块 | `src/main/.../mixin/RecipeManagerMixin.java`（原 :121/:160/:168） | 统一（Phase 3）：双 loader 同走 ScriptErrorReporter 门面，分支删除 |
