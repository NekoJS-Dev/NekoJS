# 票 27 按节点能力证据表（AC6）

日期：2026-09-28（证据收口轮）
Worktree：`D:/mcmodDemo/NekoJS-mult-t27s`（分支 `ticket-27-smoke-evidence`，基于 mult@`64f5ea95`＝本票含 review-fix 的合并态）
执行者：ticket-27 证据收口 subagent（GLM-5.3）

本表只覆盖票 27 域（render/world-render/screen-render callback 面、error dashboard GUI、error
open/locate seam），按 AC6 要求以 **supported / partial / unavailable / not verified** 明示五节点
真实差异；**不是**全局 capability 矩阵（那属票 31/32），fabric 缺席一律显式标注、不做静默 parity，
experimental 不记为 primary。每个单元格的判定都给出可引用证据；无法核验处记 `not verified`
及其 blocker。Windows 本机无头 JVM 证据，不代表其他平台/CI/release。

## 1. 能力矩阵

| 能力域 | 1.21.1 (NeoForge) | 26.1.2 (NeoForge) | 26.2.0 (NeoForge) | 26.1.2-fabric | 26.2.0-fabric |
|---|---|---|---|---|---|
| ① render/world-render/screen-render callback 面（`ClientEvents` 的 `hud`/`hudRender`/`screenRender`/`worldRender` 成员 + 平台 Adapter 分发） | supported | supported | supported | unavailable（显式） | unavailable（显式） |
| ② error dashboard GUI（屏幕挂载 + `/nekojs view_all_errors` 打开路径） | supported | supported | supported | unavailable（文本降级，显式） | unavailable（文本降级，显式） |
| ③ error open/locate seam（票 30 `DiagnosticOpenAction` 消费）——common seam 本体 | supported（节点无关） | supported（节点无关） | supported（节点无关） | supported（节点无关） | supported（节点无关） |
| ③′ 玩家可达的 open/locate 入口（GUI 消费点） | supported | supported | supported | unavailable（入口缺席，显式） | unavailable（入口缺席，显式） |

## 2. 单元格证据

判定基础（本列表所有行号/路径均在本 worktree `mult@64f5ea95` 核对）：

- **golden（票 33 只读基线，`src/test/resources/nekojs/platform-gates/event-surface-domains.txt`）**
  的五节点 `ClientEvents` 行：1.21.1 / 26.1.2 / 26.2.0 均含全部 GUI/render 成员（`hud,hudRender,
  screenRender,worldRender` 各恰一次，L24–L26）；26.1.2-fabric / 26.2.0-fabric 仅
  `buses=tick,tickPost,tickPre`（L27–L28）——GUI/render 成员显式缺席。
- **surface fixture（`Ticket27ClientGuiRenderSurfaceTest`，只读 golden）**：
  `catalogGoldenKeepsRenderMembersUniqueAndFabricExplicitlyWithoutThem` 断言 fabric 行不含
  GUI/render 成员（"documented unavailable, no silent parity"）、非 fabric 行每成员恰一次、
  其他组不得重复声明，且基线覆盖全部五节点（`clientEventsRows >= 5`）。

### ① render/world-render/screen-render callback 面

- **NeoForge 三节点 ＝ supported**
  - 成员面：golden L24–L26（见上）。成员集/字段单例/payload（`PainterJS`/`ScreenRenderEventJS`）/
    `scriptType()==CLIENT`/跨组 bus identity 唯一由
    `Ticket27ClientGuiRenderSurfaceTest.renderAndScreenMembersStayInTheSingleClientEventsSurface`
    经生产注册路径断言（26.x 节点执行：本树 `:26.2.0:test` 4/0、`:26.1.2:test` 4/0，
    `command-output/13`/`16`；合并前同代码态 `03`/`09`）。
  - Adapter 挂载面：`platformRenderAdapterMountsOnTheClientDistOnly` 断言 `ClientRenderEvents`
    `@EventBusSubscriber(value=[Dist.CLIENT])`（26.x 节点执行，同上）。
  - 1.21.1 补充：surface fixture 自身带 `//? if >=26` 守卫，在 1.21.1 不执行——该节点的差异
    证据由 golden 行（真实节点求值产物）+ 孪生文件核对承载：`versions/1.21.1/.../render/
    ClientRenderEvents.java` 为 26.x 共享文件的本节点孪生（三个 RenderLevelStage 事件
    `AFTER_TRANSLUCENT_BLOCKS`/`AFTER_WEATHER`/`AFTER_LEVEL` → `EARLY`/`NORMAL`/`LATE`
    分层映射；26.x 侧为同名拆分事件，行为孪生成对维护）；`ClientRenderRegistry` 与
    `RenderRegistrationBusJS` 无 1.21.1 override＝与 26.x 逐字节同一文件。本树
    `:1.21.1:test` 全绿（`command-output/14`，tests=313 failures=0）。
- **fabric 两节点 ＝ unavailable（显式，非静默 parity）**
  - golden L27–L28：仅 `tick,tickPost,tickPre`；fixture 断言其不含 GUI/render 成员（见上）。
  - 源头：共享树 `bindings/event/client/ClientEvents.java` 整体 `//? if neoforge {` 守卫——fabric
    求值树中该文件 body 被注释排除（核对于 `versions/26.1.2-fabric/build/generated/stonecutter/
    .../ClientEvents.java`，guard 未消解、内容为空）；fabric 侧由
    `src/fabric/.../event/FabricClientEventBindings.java` 提供同名子集（`tickPre`/`tickPost`/
    `tick`），javadoc 明示「随各事件的 fabric 桥落地逐个加入」——缺席是既定支持等级，不是缺陷。
  - `ClientRenderEvents.java` 同样被 `neoforge` 守卫排除（fabric 求值树核对同上）；共享的
    `ClientRenderRegistry`/`RenderRegistrationBusJS`（无守卫）在 fabric 求值树中存在并编译
    （inert：无成员/Adapter 向其分派），这不构成 fabric 可用性声明。
  - fabric 两节点全量套件在本树真跑全绿（`command-output/11`/`12`，各 tests=251 failures=0
    errors=0；skipped=25 均为票 27 域外的既有 assumption 跳过）——共享树（含本票合并的
    common 丢弃/清理改动与无守卫 render 类）在 fabric 节点编译并通过，零回归。

### ② error dashboard GUI

- **NeoForge 三节点 ＝ supported（无头 JVM 级：挂载/打开路径与呈现逻辑；真机出图见 §3 not verified）**
  - 26.1.2 / 26.2.0：屏幕为共享文件 `client/gui/NekoErrorDashboardScreen.java`
    （`//? if neoforge && >=26`）+ 共享 `AbstractErrorDashboardScreen`/`dashboard/*`；打开路径
    `/nekojs view_all_errors` → `ShowErrorListPacket`（`NekoJSCommands.java` L81–L93，
    `NekoJSNetwork` 为 `neoforge` 守卫）。`DashboardViewTest`/`DashboardLayoutTest`/
    `DashboardTextTest` 在两节点全绿（本树 `13`/`16`）。
  - 1.21.1：`versions/1.21.1/.../gui/NekoErrorDashboardScreen.java` 孪生（文件头自述
    "all layout, state and platform actions are shared"；与 26.x 共享文件的 diff 仅为
    1.21.1/26.x GUI API 适配：`render(GuiGraphics)` vs `extractRenderState(GuiGraphicsExtractor)`
    等）；`AbstractErrorDashboardScreen`/`dashboard/*` 无 1.21.1 override＝共享；命令孪生
    `versions/1.21.1/.../command/NekoJSCommands.java` L76 起同路径。Dashboard 三测在本树
    1.21.1 全绿（`14`：12/0、5/0、4/0）。
- **fabric 两节点 ＝ unavailable（文本降级，显式）**
  - `src/fabric/.../FabricNekoJSCommands.java` L46（javadoc）：「`error` / `view_all_errors`
    降级为文本输出（错误 UI 与网络面板未移植）」；`viewAllErrorsCommand`（L307 起）以聊天
    文本列错误，无屏幕、无 `NekoJSNetwork`。
  - fabric 求值树中 `NekoErrorDashboardScreen`/`AbstractErrorDashboardScreen`/`NekoJSNetwork`
    均被守卫排除（body 注释为空，核对于 `versions/26.1.2-fabric/build/generated/stonecutter/`）。
  - 呈现**逻辑**类（`DashboardView`/`DashboardLayout`/`DashboardText`，无守卫）在 fabric
    编译且三测全绿（`11`/`12`：12/0、5/0、4/0）＝逻辑共享、平台挂载缺席——与票内协调口径
    一致（Fabric 维持既有错误文本降级，本票不承诺 Fabric 新面板），不自动补 parity。

### ③ error open/locate seam（票 30 `DiagnosticOpenAction` 消费）

- **五节点 seam 本体 ＝ supported（节点无关）**：`LocalErrorSource`/`ErrorOpenService` 住
  `common`（`com.tkisor.nekojs.core.error`，零 `net.minecraft`/`net.neoforged` import——common
  隔离纪律由 guardLint 强制），五节点逐字节同一份；`common` 不参与版本化，无节点差异可言。
  本树 `:common:test` 选择复跑绿（`15`）：`Ticket27DiagnosticOpenActionConsumeTest` 4/0
  （record `openAction()` 经 `payload()/parse()` 无损往返 → `ErrorOpenService` 分派到已验证
  本机文件；同一失败的 DTO 投影与 seam 解析逐字段相等＝无第二事实源）、
  `Ticket27ErrorDashboardSnapshotTest` 2/0、`GenerationGlobalsDiscardResilienceTest` 2/0。
- **③′ 玩家可达入口**：NeoForge 三节点 supported——`AbstractErrorDashboardScreen`（neoforge
  守卫、三节点共享）调用 `opener.openAsync(...)` 并以 `ErrorOpenService.Outcome` 报告
  （`AbstractErrorDashboardScreen.java` L129 起）；fabric 两节点 unavailable（入口缺席）——
  `FabricNekoJSCommands` 无任何 open/vscode 动作（grep 零命中），common seam 存在但无
  fabric 入口消费，不做静默 parity。

## 3. not verified（如实记录，不改判 unavailable/supported）

| 项 | 节点 | blocker / owner |
|---|---|---|
| 真机 GUI 实际出图、渲染器实际绘制、VS Code 打开真机验证 | 全部五节点 | 无头 JVM 无法驱动 `RenderGuiEvent`/真实 Screen——owner 票 34（真机 smoke）；本表「supported」限于挂载/路径/逻辑的无头证据级 |
| `Ticket27ClientGuiRenderSurfaceTest` 在 1.21.1 的执行 | 1.21.1 | fixture 带 `>=26` 守卫（本票交付形态即如此）；1.21.1 差异由 golden 行 + 孪生 diff + 全量套件承载，见 §2① |
| declaration 面（TS/Python）收录 | 全部五节点 | `api-manifest-core.json`/probe/declaration-parity 对本票域 0 命中（`10-declaration-coverage.txt`）——owner 09/33/34（AC7 域，非本表关闭范围） |

## 4. 本轮命令与结果（`command-output/`，均本 worktree 真跑）

| # | 命令 | 结果 |
|---|---|---|
| 11 | `gradlew :26.1.2-fabric:test --rerun --console=plain` | BUILD SUCCESSFUL；tests=251 failures=0 errors=0 skipped=25（skips 均票 27 域外既有） |
| 12 | `gradlew :26.2.0-fabric:test --rerun --console=plain` | BUILD SUCCESSFUL；tests=251 failures=0 errors=0 skipped=25 |
| 13 | `gradlew :26.2.0:test --rerun --console=plain` | BUILD SUCCESSFUL；tests=448 failures=0 errors=0 skipped=58；Ticket27 Lifecycle 4/0、Surface 4/0 |
| 14 | `gradlew :1.21.1:test --rerun --console=plain` | BUILD SUCCESSFUL；tests=313 failures=0 errors=0 skipped=14；Dashboard 12+5+4 全绿 |
| 15 | `gradlew :common:test --tests '*Ticket27*' --tests '*GenerationGlobalsDiscardResilience*' --rerun` | BUILD SUCCESSFUL；Consume 4/0、Snapshot 2/0、DiscardResilience 2/0 |
| 16 | `gradlew :26.1.2:test --rerun --console=plain` | BUILD SUCCESSFUL；tests=448 failures=0 errors=0；Ticket27 Lifecycle 4/0、Surface 4/0 |

测试计数取自各节点 `build/test-results/test/*.xml` 汇总（JUnit XML `tests/failures/errors/skipped`
属性求和），原始控制台输出见对应 transcript。
