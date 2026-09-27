# 票 27 证据报告：CLIENT GUI 与 render Adapter 资源呈现清理

日期：2026-09-27
Worktree：`D:/mcmodDemo/NekoJS-mult-t27`（分支 `ticket-27-client-gui-render`，基线 `7196c524`）
执行者：zed-flash-27（GLM-5.3 subagent worktree）

## 1. 范围与产出摘要

本票收口 CLIENT 只读错误报告 GUI 调用路径与 render/world render/screen render Adapter 资源
从注册、呈现到 generation 清理的完整路径。**不新增任何事件成员、binding、bus 或节点**
（AC2/AC8 的复用与唯一性由 fixture 钉住）；golden 零改动。

### 1.1 修复的真实缺陷（本票主要产出，红→绿）

**缺陷**：候选 generation 的渲染器注册批次（`ClientRenderRegistry.Candidate`，挂在候选
`CandidateStatePlan` 联合边界上）在**候选失败/取消时不被释放**——`GenerationGlobals.discard()`
只清空计划列表，不通知计划本身，批次以已销毁候选 Context 为 key 滞留在
`CANDIDATE_BATCHES`（含 Graal `Value` 引用），直到下一轮候选的首个注册才被剪枝。
这正是票 26 REPORT §2.1 如实记下的缺口：**AC4 字面的「候选资源全部清理」未在失败当刻兑现，
也没有直接断言**。功能上 inert（dispatch/hasHud 只走生产表），但违反 spec 09
「candidate 失败或取消时进入关闭态，释放全部 candidate 资源」。

**修复（复用既有接缝，不新增框架）**：

| 文件 | 变更 |
|---|---|
| `common/.../core/state/CandidateStatePlan.java` | 新增 `default void discard() {}`（候选失败/取消的释放通知；契约：幂等、不得抛；既有实现零改动） |
| `common/.../core/state/GenerationGlobals.java` | `discard()`/`close()` 在清空计划列表前逐个通知 `plan.discard()`；单个计划失败记 WARN `[NEKO-1001]` 并继续 teardown（对齐 `closeUiRoots` 先例）。`close()` 也通知，覆盖关停期间滞留候选的边界 |
| `src/.../client/render/ClientRenderRegistry.java` | `Candidate.discard()` 从 `CANDIDATE_BATCHES` 移除自身（`remove(context, this)`，幂等）；`registerCandidateBatch` 改为先注册计划后入表（注册抛出时新批次不入表）；新增只读观察面 `pendingCandidateRegistrations()`；`clearAll()` 补充清空候选批次（生产代码零调用者，诊断/测试语义补全） |

**修复后的候选生命周期**（事务路径）：候选脚本执行期 `hudRender/worldRender(...)` → inert 批次
+ 联合边界 → STATE_PLAN/DOMAIN_PLAN 预检 → **成功**：commit 点 `publish()` 整批换装（恰一次）；
**失败/取消**：discardCandidate → `globals().discard()` → `plan.discard()` 批次当刻释放，
生产表不动，旧 active 继续服务。

### 1.2 票 30 seam 的 GUI 消费（AC1 新增部分）

`DiagnosticOpenAction` 的 javadoc 声明票 27 消费该 seam 实现外部打开与只读 GUI。本轮交付：

- `LocalErrorSource.resolve(DiagnosticOpenAction, boolean)` 与 `ErrorOpenService.openAsync(
  DiagnosticOpenAction, boolean, Executor)`：record 锚定的打开链路，与 DTO（wire 投影）入口
  **共用同一套位置校验与分派收尾**——动作语义以 record 为准，不建立第二事实源；无定位
  record（`openAction()==null`）报告 `NO_ERROR` 且不触碰 opener；远端/虚拟/越界路径沿用
  既有显式拒绝，不猜测。
- 消费证据 fixture：record 的 `openAction()` 经 `payload()/parse()` 无损往返后由
  `ErrorOpenService` 解析并分派到已验证本机文件；同一失败的 DTO 投影与 seam 解析结果
  逐字段相等（`Ticket27DiagnosticOpenActionConsumeTest`）。
- 现状边界（如实记录）：packet 仍按票 30 冻结的六字段 wire 传输 record 投影（golden 只读），
  GUI 屏幕层继续消费该投影；屏幕自身不缓存可变共享状态（`ErrorDashboardModel` 只持有
  不可变快照拷贝，fixture 钉住）。

### 1.3 明确不改的面

- `ClientEvents.java` / `RenderRegistrationBusJS` / `ClientRenderEvents`（26.x 与 1.21.1 孪生）
  / `HudRenderContextJS` / `WorldRenderContextJS` / `ScreenRenderEventJS` / `PainterJS` /
  `DashboardView` / `ErrorDashboardModel` / `NekoErrorDashboardScreen`（两节点孪生）/
  `NekoJSNetwork` / `NekoJSCommands`：**零行 diff**（AC2 复用既有事件与 Adapter）。
- keybind/HUD 输入域（票 26）、Assets/Lang（29）、PostEffects（28）、JSX runtime（40–48）、
  `AGENTS.md`/`CONTEXT.md`/`docs/adr`/票据 README：零改动。
- golden / declaration 产物：零改动（git diff 无 golden/probe 文件；`platformGateTest` 无 drift）。

## 2. 逐条 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 只读 error dashboard GUI 调用者 Interface 能打开、渲染列表/详情/过滤/复制/日志与有效本地定位动作并报告错误；事实只来自票 30 frozen record；GUI 不缓存可变共享状态、无第二事实源或旧 DTO 旁路 | **部分满足（不勾选）** | 已满足部分：打开/列表/详情/过滤/选中/复制/复制全文/校验本地 VS Code 打开/错误反馈为既有只读面板面（blue-a-2），经 `DashboardViewTest`/`DashboardLayoutTest`/`DashboardTextTest`（既有）与本票 `Ticket27DiagnosticOpenActionConsumeTest`（record seam 打开/往返/等价/拒绝）、`Ticket27ErrorDashboardSnapshotTest`（不可变快照）观察；`fullDetails` 未被当作 record。**缺**：①「打开日志」动作不在已批准的 blue-a-2 原生界面元素清单内（editor-removal-and-error-ui.md §9），现状未实现——与 §3「保留打开日志」存在口径差，属 UI 范围裁定（MIGRATION §3.2，owner 维护者）；②结构化 open-action 本身不上 wire（wire 冻结，golden 只读），GUI 在 seam 层消费 record 语义 |
| AC2 render/world render/screen render callback 继续复用既有 ClientEvents/render 事件与 Adapter；成员/payload/side filter/取消/优先级/触发线程在 catalog/golden 唯一；不新增重复 bus | **满足** | `Ticket27ClientGuiRenderSurfaceTest.renderAndScreenMembersStayInTheSingleClientEventsSurface`（生产注册路径成员集、字段单例 `assertSame`、payload `PainterJS`/`ScreenRenderEventJS`、`scriptType()==CLIENT`、注册入口 vs 监听形态、跨组 identity 唯一）；`catalogGoldenKeepsRenderMembersUniqueAndFabricExplicitlyWithoutThem`（golden 每节点成员恰一次、fabric 显式无这些成员）；`ClientRenderEvents`/`ClientEvents.java` 零 diff（复用即现状） |
| AC3 平台 client Adapter 在正确注册期挂载 render/screen 资源并按 render owner thread 分发；Adapter 持 MC/loader 类型，shared 作者契约不引入平台类型 | **满足** | `platformRenderAdapterMountsOnTheClientDistOnly`（`ClientRenderEvents` `@EventBusSubscriber(value=[CLIENT])` 反射断言——mod 构造期挂载、dedicated server 不订阅）；`sharedRenderRegistrationSurfaceCarriesNoPlatformTypes`（`ClientRenderRegistry`/`RenderRegistrationBusJS` 签名+字段 0 个 `net.minecraft`/`net.neoforged` 类型，渲染上下文以 Object 透传；MC 面包装类 0 个 loader 类型）；分发线程契约由 `ClientRenderRegistry` javadoc + `ClientReloadExecutor`（CLIENT owner thread = Render 线程）承载，harness 经真实 reload 路径驱动同一注册表 |
| AC4 reload candidate 阶段新 GUI/render 资源与 listener 对生产路由不可见；commit 后旧 generation 停止接收、新 generation 恰好呈现一次；失败或取消时旧 active 继续可用且候选资源全部清理 | **满足（本票修复项，红→绿）** | `Ticket27ClientGuiRenderLifecycleTest` 4 tests：`aRejectedCandidateReleasesItsPendingRendererBatchAtFailureTime` + `aCandidateKilledDuringExecutionReleasesItsPendingBatchAndKeepsTheOldActive`（修复前红：`expected: <0> but was: <1>`，见 §4；修复后失败当刻批次释放、旧 active 继续服务、下一轮正常提交）、`worldRenderPresentationsSwapExactlyOnceAtTheCommitPoint`（候选期生产路由观察 `[[], ['first']]`、commit 恰一次、空批退役）、`screenRenderAndHudListenersFollowTheSameCandidateBoundary`（候选期 pending 不可见、commit 接管、空代退役）。渲染器恰一次呈现由 `List` 探针（本帧真正被调用的 id）断言；候选不可见性复用票 26 已合并语义并由本票 fixture 复验 |
| AC5 GUI 操作、render context、取消和错误呈现可从调用者 Interface 观察；不依赖私有屏幕字段、Renderer 对象身份或未公开平台集合 | **满足** | 全部断言走公开面：`DashboardView.Canvas` 捕获/`ErrorDashboardModel` 快照/`ErrorOpenService.Result`/`LocalErrorSource.Result`（GUI）；`dispatchHud/dispatchWorld` List 探针、`hasHud/hasWorld`、`hasListeners()`、`pendingCandidateRegistrations()` 只读计数（新增发布面，刻意为计数而非集合）；无私有字段反射、无回调对象身份断言 |
| AC6 平台/版本 Adapter 按节点既定支持等级与声明能力验证真实差异；不可用时 supported/partial/unavailable 明示；不自动补 Fabric parity | **部分满足（不勾选）** | 已核实（只读）：NeoForge 三节点 ClientEvents 含全部 GUI/render 成员（golden 行），fabric 两节点 ClientEvents 仅 `tick,tickPost,tickPre`——GUI/render 能力在 fabric 显式缺席（unavailable，fixture 断言 golden 行，不做静默 parity）；`ClientRenderRegistry`/`RenderRegistrationBusJS` 为 1.21.1+26.x 共享树文件（1.21.1 仅 `ClientRenderEvents`/两个 context wrapper 孪生，层分发差异）。**缺**：未新造 capability 矩阵条目（owner 票 31/32）；`:26.1.2-fabric:test`/`:26.2.0-fabric:test` 未跑（本票未触 fabric 专属文件） |
| AC7 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 和节点 runtime smoke 可互相追溯；普通测试只读 golden | **部分满足（declaration 面不勾选）** | 已交付：runtime member fixture（经生产注册路径读 catalog metadata 与字段单例）+ golden 只读断言 + `platformGateTest` 绿（无 member-drift，golden 未改）。**缺口（实证）**：`api-manifest-core.json`、probe `*.expected.d.ts`、`declaration-parity.txt` 对本票域 0 命中（`command-output/10-declaration-coverage.txt`），与票 26/28 AC7 同类——owner 09/33/34；节点 runtime smoke（真机 GUI/渲染出图）未做——owner 票 34 |
| AC8 PostEffects、Assets、recipe/loot/tags/JEI/capability/goal/keybind/HUD 等 owner 不被并入；不重复声明其事件或 binding | **满足** | 本票 diff 不含上述域文件（`KeyBindEvents`/`PostEffect*`/`DataGeneratorJS`/`LangGeneratorJS` 等零改动）；surface fixture 的跨组 identity 断言 + golden 断言（render 成员不在其他组出现）钉住唯一 owner；`ClientRenderDomainOwner` 只管渲染器注册（票 26 交付，本票未改其文件） |
| AC9 旧 render/目标 GUI 入口、重复 handler、不受测 wrapper 或绕过 Runtime Root 的资源装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径。内置 workspace/编辑器 GUI 与编辑文件同步不因"旧公开路径"理由保留 | **不勾选（维护者 sign-off 门禁）** | 本票**零删除**；已备证据：渲染分发 handler 唯一（生产调用点仅平台 Adapter）、GUI 入口唯一、内置编辑器链路 11 个符号生产源码 0 命中、`clearAll()` 生产调用者 0（`command-output/05-old-route-remnant-scan.txt`）；残留项（旧 UI lang key、日志入口、wire 演进）与删除条件见 MIGRATION §3/§4，待维护者裁定 |

## 3. 真实验证命令与结果

所有命令在本 worktree 真跑（Windows 本机，不代表其他平台/CI/release）；原始输出在 `command-output/`。

| # | 命令 | 结果 |
|---|---|---|
| 1 | `gradlew :26.1.2:test --tests '*Ticket27ClientGuiRenderLifecycleTest*' --rerun --console=plain`（修复前） | **BUILD FAILED**；tests=4 failures=2（两条失败当刻释放断言红：`expected: <0> but was: <1>`；另 1 条为 fixture 期观察值笔误，修正后复跑仍 2 红）→ `01-red-lifecycle-test.txt` |
| 2 | 同上（修复后） | BUILD SUCCESSFUL；tests=4 failures=0 errors=0 → `02-green-lifecycle-test.txt` |
| 3 | `gradlew :26.1.2:test --console=plain`（全量） | BUILD SUCCESSFUL；tests=448 failures=0 errors=0 → `03-26.1.2-test-full.txt` |
| 4 | `gradlew :26.2.0:test :1.21.1:test --console=plain` | BUILD SUCCESSFUL；26.2.0 tests=448、1.21.1 tests=313，failures=0 errors=0 → `04-26.2.0-1.21.1-test.txt` |
| 5 | 旧 route/残留扫描（grep 11 个编辑器符号 + 生产调用点） | 编辑器链路 0 命中；渲染 handler/GUI 入口唯一；`clearAll()` 生产 0 调用者 → `05-old-route-remnant-scan.txt` |
| 6 | `gradlew :common:check --rerun-tasks --console=plain` | BUILD SUCCESSFUL in 1m32s；19/19 tasks（含隔离检查）；tests=1936 failures=0 errors=0 skipped=4 → `06-common-check.txt` |
| 7 | `gradlew guardLint --console=plain` | BUILD SUCCESSFUL；守卫块 320、扫描 469 文件、超限豁免 0、警告 0 → `07-guardLint.txt` |
| 8 | `gradlew :26.1.2:platformGateTest --rerun --console=plain` | BUILD SUCCESSFUL（票 33 只读基线无 member-drift，golden 未改）→ `08-platformGate.txt` |
| 9 | `gradlew :26.1.2:test --tests '*Ticket27*' --console=plain`（收尾复跑） | BUILD SUCCESSFUL；Lifecycle 4/0/0、Surface 4/0/0 → `09-ticket27-selection-green.txt` |
| 10 | declaration 覆盖 grep | `api-manifest-core.json`、probe `*.expected.d.ts`、`declaration-parity.txt` 对本票域 0 命中 → `10-declaration-coverage.txt` |
| 11 | `:common:test --tests '*Ticket27*'` | BUILD SUCCESSFUL；DiagnosticOpenActionConsume 4/0/0、ErrorDashboardSnapshot 2/0/0（输出并入运行记录） |

**红→绿证据原文（AC4 核心）**

```
--- 修复前（01-red-lifecycle-test.txt） ---
Ticket27ClientGuiRenderLifecycleTest > aRejectedCandidateReleasesItsPendingRendererBatchAtFailureTime() FAILED
    org.opentest4j.AssertionFailedError: a failed candidate must release its pending registration
    batch at failure time (AC4: candidate resources fully released; ...) ==> expected: <0> but was: <1>
Ticket27ClientGuiRenderLifecycleTest > aCandidateKilledDuringExecutionReleasesItsPendingBatchAndKeepsTheOldActive() FAILED
    org.opentest4j.AssertionFailedError: a killed candidate must release its pending registration
    batch at failure time ==> expected: <0> but was: <1>

--- 修复后（02-green-lifecycle-test.txt） ---
BUILD SUCCESSFUL；tests=4 failures=0 errors=0
```

## 4. 已知环境注记（测试隔离）

一次过渡运行中，`Ticket27*` 两类同 worker 执行时 lifecycle 在 `setUp` 报
`FMLPaths.get()` NPE：Gradle 复用的测试 worker JVM 中 `Platform.INSTANCE` 被先前状态置为真实
`NeoForgePlatform`（其 `getGameDir` 依赖本 JVM 未执行的 FML bootstrap）。这是共享静态
`Platform` 单例的既有测试隔离脆弱点（票 26 harness 的 `ensurePlatformInitialized` 判 null 跳过
即为其症状）。处置：本票 lifecycle fixture 的 `@BeforeAll` 增加尽力而为的隔离卫生（实例为
`NeoForgePlatform` 时反射复位后重建 stub）；修复后连续 3 次组合复跑全绿。未改动票 26 harness 文件。

## 5. 未验证项与 owner

| 项 | 状态 | owner / 说明 |
|---|---|---|
| 真机 GUI/render smoke（只读面板实际出图、渲染器实际绘制、VS Code 打开真机验证） | **not run**：无头 JVM 无法驱动 `RenderGuiEvent`/真实 Screen；尝试过的替代（26.1.2 版本树测试树全绿）只覆盖注册/生命周期语义 | 票 34 P4（命令示例：`gradlew :26.1.2:runClient` + `/nekojs view_all_errors`，本轮未执行） |
| `:26.1.2-fabric:test` / `:26.2.0-fabric:test` | not run（本票零 fabric 专属文件改动；fabric 的 GUI/render 缺席由 golden 只读断言承载） | 合并门 / 票 31/32 |
| capability 矩阵新条目 | 未新造 | 票 31/32 |
| TS/Python declaration 收录 | 0 命中实证（同票 26/28 缺口） | Managed Surface/Probe owner（09/33/34）；`npm run test:probe-types` 因零声明改动未跑 |
| NEKO-1001 登记 | 已按 coding.md 在 `wiki/en_us/Error-Reference.md` 同变更登记（该注册表在本分支存在，任务简报的“注册表不存在”前提与实际不符，已按实际证据处理） | 合并门复核 |
| `runGameTestServer` / Minecraft MCP 实机证据 | not run | 票 34 |

## 6. 双轴自查（/code-review 口径）要点

- **复用既有机制**：discard 通知复用 `CandidateStatePlan` 联合边界与 `ScriptManager.discardCandidate`
  既有路径；open seam 复用 `LocalErrorSource` 既有校验（抽取共享 `resolveLocation`，DTO 路径
  行为逐字节不变）；未新增框架、bus、registry、Gradle 工程。
- **注释与实现一致**：新增/改写注释为英文（AGENTS.md 语言规则），既有中文注释未做批量重写；
  `CANDIDATE_BATCHES` 字段 javadoc 与 `registerCandidateBatch` 注释按修复后行为改写。
- **不放宽门禁**：红的处置是改生产代码（discard 通知），不是改断言；world 观察断言的首版
  期望值是 fixture 笔误（HUD 探针在该场景本应为空），修正后仍为真断言。
- **golden**：零改动；`platformGateTest` 复跑无 drift。
- **write-set**：不涉 keybind/HUD 输入域、Assets/Lang、PostEffects、JSX runtime 文件、票据
  README、AGENTS/CONTEXT/ADR、兄弟 worktree；`wiki/en_us/Error-Reference.md` 为本票新码登记
  （coding.md 要求的同变更动作）。

## 7. 归档与可追溯

- 迁移与删除边界：同目录 `MIGRATION.md`。
- 最小可运行示例：`examples/client-error-gui-render.js`（只读错误 GUI 消费链路 + render 资源生命周期）。
- 原始命令日志：`command-output/01..10`。
- 测试源码：
  - `common/src/test/java/com/tkisor/nekojs/core/error/Ticket27DiagnosticOpenActionConsumeTest.java`
  - `common/src/test/java/com/tkisor/nekojs/core/error/Ticket27ErrorDashboardSnapshotTest.java`
  - `src/test/java/com/tkisor/nekojs/client/render/Ticket27ClientGuiRenderLifecycleTest.java`
  - `src/test/java/com/tkisor/nekojs/client/render/Ticket27ClientGuiRenderSurfaceTest.java`

## 8. 主会话复核修正（2026-09-27，合并后）

- 语言规则修正：三个生产文件中本次 diff 新写/改写的 Javadoc 译为英文
  （ErrorOpenService 类文档扩展、LocalErrorSource 类文档与 resolve(action)/resolveLocation、
  ClientRenderRegistry CANDIDATE_BATCHES 文档改写段）；各文件既有中文注释未动。
- `CandidateStatePlan.discard()` Javadoc 修正与实现不符处：close() 路径也会对已发布计划
  调用 discard，实现必须把 post-publish 调用视为幂等 no-op（原文「publish never ran for
  this plan」不成立）。
- NEKO-1001 消息分隔符修正：`— plan=`（em-dash 保留给摘要追加）→ `; plan=`。
- 补 AC4 弹性测试：`GenerationGlobalsDiscardResilienceTest` 2 tests——单计划 discard 抛错
  不阻断其余计划清理（失败记录后继续拆除）；publish + close-time discard 恰好一次释放
  （幂等契约）。
- 已知 judgement call 保留：ErrorOpenService 两个 openNow 变体的 resolve+catch 形状未再
  抽取（共享尾 dispatch() 已提取，残余为 4 行局部形状，避免无谓扰动）；
  `pendingCandidateRegistrations()` 为 AC5 要求的只读观察面（测试专用、零生产调用者，
  REPORT §6 已标注）。
