# 41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter

**What to build:** 将 40 的 host Adapter contract 接到 NeoForge 26.2 真实客户端：脚本可打开 JSX 描述的独立 Screen，看到 label/button/input/scroll/layout 的首次绘制，并通过稳定事件对象完成点击、文本输入、滚轮、Tab/Shift+Tab、Enter/Space、Escape、焦点、hover、disabled 和基础 narration。正常绘制帧只 paint 已提交 host tree，不重新执行 GraalJS render。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)

**Status:** blocked

**Assignee:** dsh-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

**协调但不硬阻塞：**
- [26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)：26 继续拥有 keybind/HUD；41 拥有 JSX Screen 内输入路由。
- [27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)：27 继续拥有既有 error GUI/render callback 域；41 与其确认平台 Adapter 和 client owner 线程边界，不把 JSX runtime 塞入 27。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)：41 先通过公开诊断 seam 报告阶段化错误；与 30 的最终 record 字段对齐在 42 完成整合验收。

## Acceptance criteria

- [x] NeoForge 26.2 真实客户端能打开、绘制、操作、resize 和关闭 JSX Screen；Screen 选项覆盖标题、关闭行为、暂停策略和外部替换 cleanup。
      —— **判定 = partial，不计通过（见 Closure record「AC1/AC8 联合判定」）**：open/render/首次提交/resize/关闭**代码路径**与单测成立且真实客户端上 `Ui.open` 成功、render 在 Render thread 执行；但**屏幕上没有出现 JSX 像素**，操作/原生 resize/关闭的 in-game 行为未验证。
- [x] 原生鼠标、滚轮、键盘、文本输入、焦点和捕获被转换为稳定脚本事件；事件对象不暴露原生 Screen、widget、`GuiGraphics`、Java 对象身份或版本特有字段。
- [x] input 的值、光标、选择、焦点、最大长度和文本事件可用；scroll 正确管理内容范围、裁剪、偏移和滚轮消费。
      —— **判定 = partial，不计通过**：代码与单测覆盖稳定事件与滚轮消费；scroll 的**内容范围/裁剪/偏移**依赖 common 侧 `UiLayoutEngine`，而票 40 closure record 已记「scroll 裁剪/偏移语义不完整」，本票未修。
- [x] 焦点顺序、Tab/Shift+Tab、Enter/Space/Escape、hover、pressed、disabled、tooltip 和基础 narration 行为与 fake Adapter contract 一致。
- [x] 首次构建、resize、状态更新和事件回调都不在普通 paint 帧重新执行 JSX render；每帧只绘制已提交 host tree。
- [x] host Adapter 持有 Minecraft/loader 类型，shared/common author contract 不引入平台类型；脚本侧状态、VNode、host tree 和事件闭包只在 client owner thread 访问。
- [x] 节点删除后旧事件闭包不再响应；Screen 关闭、外部替换和重复 cleanup 进入同一释放路径。
- [ ] NeoForge 26.2 真实客户端 smoke 使用仓库规定 Minecraft MCP 或等价注册 smoke 通道完成；其它节点只记录 `not verified`，不因 common 编译通过而标记支持。
      —— **判定 = partial，AC8 明确不通过**：MCP 通道已接通且真实客户端确实跑过（见证据），但 JSX Screen 未出现在显示层、且本环境 MCP 输入注入对游戏无效，故未达成「smoke 完成」。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。


## Closure record

**执行者**：dsh-agent
**分支**：`ticket-41-jsx-ui-neoforge-screen-adapter`（基于 `integration/tickets-08-11-12`，含已验收关闭的 05/09/11/12/13/40）
**Status**：**blocked**（AC8 真实客户端 smoke 未达成端到端可见验证；8 条 AC 未全通过）

> **诚实性前置声明**：本票**没有**关闭。真实 NeoForge 26.2 客户端上 `Ui.open(jsxScreen)` 确实成功、
> render function 确实在 Render thread 执行、root 确实提交，但**屏幕上没有出现任何 JSX 像素**，
> 且本环境 MCP 输入注入对游戏无效。因此 AC8 保持 `[ ]`，AC1/AC3 记 partial。
> 本记录与票据正文中不存在任何「JSX Screen 已验证显示」的表述。

### commit 区间

`<base f2039686>`..<本轮 commit>（见本票提交历史）

### 交付物

**common 侧（平台无关，零 Minecraft/loader import）**

| 文件 | 说明 |
|---|---|
| `api/ui/UiScreenHost.java`（新） | 原生 Screen 投影公开 seam：`open/close/isAlive` + `NONE` 空实现；零平台类型（AC6） |
| `api/ui/UiResources.java` | 新增 `default UiScreenHost screenHost()`（返回 null 表示无宿主） |
| `core/ui/RootUiFacade.java` | `open()` 仅在首次提交成功（status==OPEN）后投影 Screen；统一 `closeScreen` 释放路径；owner thread **每次向 resources 求值** |
| `core/ui/UiRoot.java` | `dispose()` 先关 Screen 再释放 host node（同一路径、幂等）；新增 Java 侧 `resizeViewport(UiSize)`；owner thread 动态求值 |
| `core/api/CoreManagedApiBootstrap.java` | 脚本 `Ui.open({render, options})` → 引擎侧 `ScriptUiScreen` 强转；`screenHost` 排除出契约面 |
| `core/api/ApiValueMarshaller.java` | **修既有缺陷**：`object` 形参在 `convertValue` 缺分支（计分通过却抛 `TYPE_MISMATCH`）；返回值方向对**契约接口实现**放行 `object`（任意宿主对象仍拒绝，钉在原测试上） |
| `core/lifecycle/NekoRuntimeRoot.java` | 新增 `onClientUiShutdown(Runnable)`：close 时先收口 UI |
| `platform/NeoForgePlatform.java` + `versions/1.21.1/.../NeoForgePlatform.java` | 实现 `IPlatform.uiResources()`：仅客户端且节点注册了 `McUiCompat.Impl` 时返回资源，否则 null（显式 `UNSUPPORTED_CAPABILITY`，不静默 no-op） |
| `platform/compat/McUiCompat.java`（新，共享树） | 跨版本装配门面，**签名零 MC 类型**，ServiceLoader 解析 |
| `client/NekoJSClient.java` | 注册 `root.onClientUiShutdown`，经唯一 facade `closeGeneration` 收口 |

**26.2 节点侧（持有 Minecraft 类型）**

| 文件 | 说明 |
|---|---|
| `client/gui/Nf262UiHostAdapter.java` | `UiHostAdapter` 实现：create/update/reorder/bindEvents/dispose/arrange/paint；节点只记可观察事实 |
| `client/gui/Nf262UiScreen.java` | 原生 `Screen`：绘制/输入/焦点/滚动/narration；**普通帧只 paint 已提交树**（绘制前只读 `inspection`，不调 render） |
| `client/gui/Nf262UiInputMapper.java` | **无 Minecraft 类型的纯映射层**（可脱离客户端做单测） |
| `client/gui/Nf262UiScreenHost.java` | `UiScreenHost` 实现；per-root `WeakHashMap`，非静态跨 generation registry |
| `client/gui/Nf262UiTextMeasurer.java` | 原生 `Font` 测量与换行 |
| `client/gui/Nf262UiResources.java` | `UiResources` 装配 + Graal `ProxyObject` 事件桥；owner thread 动态取 `Minecraft#getRunningThread()` |
| `platform/compat/Nf262UiCompat.java` + `META-INF/services/…McUiCompat$Impl` | 26.2 注册（**其它节点没有**，故其 capability = `not verified`） |

**测试**

- `versions/26.2.0/src/test/.../Nf262UiInputMapperTest.java`：**16 项全绿**
- `common/src/test/.../UiScreenHostLifecycleTest.java`：**5 项全绿**

### 行为保持

- 未新增 Gradle project / API jar / 第二 runtime owner / UI global singleton / 静态跨 generation registry。
- 未改 40 的公开契约语义（只新增一个 `default` seam 方法 + 一个 Java 侧非契约入口）。
- **未更新任何 golden**：`api-manifest-core.json` 未改（`screenHost` 已从反射面排除；`viewport`/`resize` 刻意不进 `UiFacade` 契约，见「code-review 遗留」）。
- common 不 import Minecraft/loader：`guardLint` 0 违规。

### 逐 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 真实客户端打开/绘制/操作/resize/关闭 + Screen 选项 | **partial（不计通过）** | open/render/首次提交/resize/关闭的代码路径成立；真机日志 `Ui.open` 成功、`render#N` 在 Render thread 递增。**但屏幕上无 JSX 像素**（同会话截图始终为原版 TitleScreen，MCP `enumerate_widgets` 返回 `no screen`），操作/原生 resize/关闭未验证 |
| AC2 原生输入 → 稳定事件；事件对象无原生身份 | **通过** | `Nf262UiInputMapper` 只构造 `UiEvent`（16 项单测含字段断言）；原生 `KeyEvent/CharacterEvent/MouseButtonEvent/GuiGraphicsExtractor` 只在 `Nf262UiScreen` 内拆包，不越界 |
| AC3 input 值/光标/选择/焦点/maxLength + scroll 范围/裁剪/偏移/消费 | **partial（不计通过）** | input 复用原生 `EditBox`（光标/选择/maxLength）；滚轮**已消费**（`mouseScrolled` 返回 true 不回落世界）。但 scroll 的**内容范围/裁剪/偏移**依赖 common `UiLayoutEngine`，票 40 已记该语义不完整，本票未修 |
| AC4 焦点顺序/Tab/±Shift/Enter/Space/Escape/hover/pressed/disabled/tooltip/narration | **通过** | `Nf262UiInputMapperTest`：焦点循环与下标稳定性、disabled+invisible 排除、Enter/Space/Escape/Tab、命中层级、narration 优先级与 visible 过滤、颜色回退 |
| AC5 普通 paint 帧不重跑 render | **通过** | `extractRenderState` 只调 `root.paint()`（遍历已提交树）+ 读 `inspection`；render 只在 open/resize/reconcile 触发。真机日志可佐证 render 次数与 open/事件次数同阶而非每帧 |
| AC6 host 持 MC 类型、common 零平台类型、client owner thread | **通过** | `guardLint` 0 违规（249 守卫/332 文件）；适配器/Resources/Screen 全在 `versions/26.2.0`；`McUiCompat` 签名零 MC。owner thread 由 `UiResources#ownerThread()` 动态解析（真机上 Render thread 通过校验，modloading worker 被正确拒绝——这条拒绝本身就是探针证据） |
| AC7 节点删除后旧闭包不响应；关闭/外部替换/重复 cleanup 同一路径 | **通过** | `Nf262UiHostNode.dispose` 清空 `boundEvents`；`UiRoot.dispose` 先 `closeScreen` 再释放 host node，幂等；`UiScreenHostLifecycleTest` 5 项（dispose×3 只 close 一次、closeGeneration 幂等、候选失败不投影） |
| AC8 真机 smoke 用规定 MCP 通道；其它节点标 not verified | **不通过** | MCP 通道已接通、真机确实运行（`evidence/t41-client-log.txt`），但 **JSX Screen 未出现在显示层**，且本环境 MCP 输入注入对游戏无效（F3 对照实验），未达成 smoke。其它节点无 `McUiCompat.Impl` → `not verified` |

### code-review 遗留

1. **26.2 原生 Screen 为何不出现在显示层——未定位**（本票阻塞根因）。
   26.2 的 `Gui#setScreen` 走 `screenLayers` 栈 + `popScreenLayerInternal`，语义与 26.1 不同。
   现象符合「Screen 被 set 但未进入显示层 / 立即被替换」，也可能是本适配器绘制路径问题。
   既有 `NekoErrorDashboardScreen` 在 26.2 上也从未验证，**无法区分是本票引入还是 26.2 既有问题**。
   建议：维护者用一个最小命令/Mixin 在 26.2 上 `setScreen` 一个空 `Screen` 做对照，再判定归属。
2. **`viewport`/`resize` 留在 Java 侧而非脚本契约**：把二者加进 `UiFacade` 会新增反射成员，
   改变冻结的 `api-manifest-core.json`（需维护者审阅的 golden 变更）。本票选择不改 golden，
   由平台 `UiScreenHost`/Java 侧 `UiRoot.resizeViewport` 驱动。若维护者希望脚本可 resize，需单独走 golden 审阅。
3. **`ApiValueMarshaller` 的 `object` 形参修复**是既有缺陷（影响 `member:Ui.open`、`NBT.fromObject`、
   `Performance.time/bench`）。本票只补转换分支；**未**为这些既有成员补回归测试（超出本票范围），建议单独补。
4. **owner thread 改为动态求值**：装配期（插件 bootstrap）拿到的线程不是客户端线程，
   快照会让合法 Render thread 被误判 OFF_OWNER_THREAD。已改为每次向 `UiResources#ownerThread()` 求值；
   这是对 40 契约注释语义的实现，但**改变了 `RootUiFacade.ownerThread()` 的可观察行为**（构造期读 vs 调用期读）。
5. **scroll 语义**（AC3 partial）：内容范围/裁剪/偏移仍不完整，依赖后续 UI 增强票或票 43。
6. **`Nf262UiScreen.paintNode` 的 IMAGE 分支未接线**：`IMAGE` 不在适配器 `primitives()` 中，
   提交前即被 capability 校验拒绝（不静默画空）。

### 未验证项与 owner

| 未验证项 | owner |
|---|---|
| 26.2 真机 JSX Screen 的**可见绘制**（原生显示层归属疑点） | 维护者 |
| 点击/文本/滚轮/Tab/±Shift/Enter/Space/Escape/hover/pressed 的 **in-game** 行为 | 维护者（需可注入输入的通道） |
| 原生 resize / Screen 关闭 / 外部 `setScreen` 替换 / reload 收口 | 票 42 + 维护者 |
| 1.21.1 / 26.1.2 / 26.2.0-fabric / 26.1.2-fabric 的 UI capability | 各节点 Adapter 落地后裁定（当前 `not verified`） |
| scroll 内容范围/裁剪/偏移完整语义 | 后续 UI 增强票 |

### 环境发现（建议写入 docs/agents/minecraft-mcp.md）

1. 启动 26.2 必须显式 `--java "C:\Program Files\Java\jdk-25.0.2\bin\java.exe"`：默认的 GraalVM JDK 会让
   GraalMC 25.1.3.7 在 Truffle 初始化崩（`Got null compiler version`）。
2. 26.2 在 TitleScreen 无法执行 slash 命令（`/nekojs reload` → `no player`）；smoke 须走启动即执行/定时器。
3. **MCP 的 `press_key`/`hotkey`/`click` 在本环境对游戏输入无效**（已用 F3 对照实验证明）。
   建议把「输入注入是否生效」作为所有 in-game 交互票的前置探测。

### 证据

- [证据报告](../../baseline/2026-09-14-jsx-ui-neoforge-adapter/REPORT.md)
- [真实客户端日志摘录](../../baseline/2026-09-14-jsx-ui-neoforge-adapter/evidence/t41-client-log.txt)
- [被测 jar 的适配器类与服务注册](../../baseline/2026-09-14-jsx-ui-neoforge-adapter/evidence/jar-adapter-classes.txt)
- `evidence/01-screen-open.png` / `01b-screen-open.png`：**画面是原版 TitleScreen，未见 JSX UI**（否定性证据）

**验证命令与结果**

| 命令 | 结果 |
|---|---|
| `:common:test --rerun-tasks --no-build-cache` | **1633 tests / 0 failures / 0 errors / 5 skipped**（222 suites） |
| `:common:check` | SUCCESS（含 `checkCommonIsolation`） |
| `guardLint` | SUCCESS：249 守卫 / 332 文件 / **0 违规** / 0 警告 |
| `:26.2.0:compileJava` | SUCCESS |
| `:26.2.0:test` | **16 tests / 0 failures**（`Nf262UiInputMapperTest`） |
| `:26.2.0:build -x test` | SUCCESS（含 `:26.2.0:check`、`verifyDevModSourceSets`） |

> 注：本票**没有**更新任何 golden。新增测试未使用 `deleteOnExit()`。
