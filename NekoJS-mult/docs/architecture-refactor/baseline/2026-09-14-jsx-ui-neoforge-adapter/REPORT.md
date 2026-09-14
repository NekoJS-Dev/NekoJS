# 票 41 证据报告：NeoForge 26.2 JSX Screen / 输入 / 焦点 / 滚动 Adapter

**日期**：2026-09-14
**分支**：`ticket-41-jsx-ui-neoforge-screen-adapter`（基于 `integration/tickets-08-11-12`）
**Status**：**blocked**（AC8 真实客户端 smoke 未达成端到端可见验证）
**执行者**：dsh-agent

> **诚实性声明（请先读这一节）**
>
> 本票**没有**达成 AC8。真实 NeoForge 26.2 客户端上，`Ui.open(...)` 确实被调用、render function
> 确实在 Render thread 上执行、root 确实提交，但**屏幕上没有出现任何 JSX 像素**：同一次运行里
> 连续截图始终显示原版 `TitleScreen`，MCP 的 `enumerate_widgets`/`get_screen_buttons` 一律返回
> `{"error":"no screen"}`。此外本环境的 MCP 输入注入对游戏无效（用 F3 对照实验证明），
> 点击/文本/滚轮/Tab/Escape **一条都没有**真实客户端证据。
>
> 因此 AC1–AC7 依据代码 + 单测判定，**AC8 记 `partial`**，票据 Status = `blocked`。
> 报告与票据中不再出现任何「已打开/已验证 JSX Screen」的表述。

---

## 1. 运行环境与通道（可复现）

仓库规定通道：`docs/agents/minecraft-mcp.md`（MCP stdio 桥 + 独立启动器目录，不硬编码端口）。

```powershell
# 启动真实 NeoForge 26.2 客户端
minecraft-mod-mcp launch 26.2 --loader neoforge --java "C:\Program Files\Java\jdk-25.0.2\bin\java.exe"
# 状态确认
minecraft-mod-mcp status      # -> Minecraft mod connected, Loader neoforge, Port 9876
```

**关键路径（与 Gradle dev-run 无关）**

| 项 | 路径 |
|---|---|
| gameDir | `%APPDATA%\.minecraft\mcp_launcher\game` |
| 客户端日志 | `<gameDir>\logs\latest.log` |
| 放入的 mod | `<gameDir>\mods\nekojs-neoforge-26.2.0-1.1.0-preview3.jar`（sha256 `33F0DA8B…0E33`） |
| smoke 脚本 | `<gameDir>\nekojs\client_scripts\src\t41_smoke.jsx` |

> 注意：`versions/26.2.0/run` 与 `versions/26.2.0/logs` 在本票中**不存在/为空**是正常的——
> 本票没有使用 `:26.2.0:runClient`。核查客户端证据请查上面的 `<gameDir>`。

### 环境坑（建议写入 docs/agents/minecraft-mcp.md）

1. **必须显式指定普通 JDK 25。** 桥接器默认用 `C:\Program Files\Java\graalvm-jdk-25`，会让
   GraalMC 25.1.3.7 在 Truffle 初始化时崩：
   `IllegalArgumentException: Got null compiler version. If you're using GraalVM JDK, update to 25.1 or newer!`
   （GraalVM JDK 的 JVMCI 与 GraalMC 期待的 Truffle 编译器版本对不上）。改用
   `C:\Program Files\Java\jdk-25.0.2\bin\java.exe` 后正常启动。
2. **TitleScreen 状态下不能执行 slash 命令。** `/nekojs reload` 返回 `{"error":"no player"}`；
   因此 smoke 脚本必须「随脚本加载自动执行」或「定时器触发」，不能依赖 `execute_command`。
3. **本环境 MCP 输入注入对游戏无效。** 对照实验：`press_key F3` 后 F3 调试面板未出现（截图逐像素确认）。
   这限制了所有需要 in-game 交互的验证；建议把「输入注入是否生效」作为 smoke 前置探测。

---

## 2. 交付物

### 2.1 common 侧（平台无关）

| 文件 | 说明 |
|---|---|
| `common/src/main/java/com/tkisor/nekojs/api/ui/UiScreenHost.java`（新） | **原生 Screen 投影的公开 seam**：`open/close/isAlive`，零 Minecraft/loader 类型，含 `NONE` 空实现 |
| `common/src/main/java/com/tkisor/nekojs/api/ui/UiResources.java`（改） | 新增 `default UiScreenHost screenHost()`；既有实现者不受影响 |
| `common/src/main/java/com/tkisor/nekojs/core/ui/RootUiFacade.java`（改） | `open()` 仅在首次提交成功（status==OPEN）后投影 Screen；`dispose` 路径统一 `closeScreen`；owner thread 改为**每次向 resources 求值** |
| `common/src/main/java/com/tkisor/nekojs/core/ui/UiRoot.java`（改） | `dispose()` 先关 Screen 再释放 host node（同一路径）；新增 `resizeViewport(UiSize)`（Java 侧，非契约）；owner thread 动态求值 |
| `common/src/main/java/com/tkisor/nekojs/core/ui/UnavailableUiFacade.java`（改） | 保持显式拒绝语义 |
| `common/src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java` + `versions/1.21.1/.../NeoForgePlatform.java`（改） | 实现 `IPlatform.uiResources()`：仅客户端且节点提供 `McUiCompat.Impl` 时返回资源，否则 null（显式 `UNSUPPORTED_CAPABILITY`） |
| `common/src/main/java/com/tkisor/nekojs/core/api/CoreManagedApiBootstrap.java`（改） | 脚本 `Ui.open({render, options})` → `ScriptUiScreen` 强转；`screenHost` 排除出契约面 |
| `common/src/main/java/com/tkisor/nekojs/core/api/ApiValueMarshaller.java`（改） | **修既有缺陷**：`object` 参数/返回值此前在 `convertValue`/`marshalReturn` 无分支，导致计分通过却抛 `TYPE_MISMATCH`/`NATIVE_TYPE_LEAK`（影响 `member:Ui.open`、`NBT.fromObject`、`Performance.time/bench`） |
| `common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java`（改） | 新增 `onClientUiShutdown(Runnable)`，close 时先收口 UI |

### 2.2 26.2 节点侧（持有 Minecraft 类型）

| 文件 | 说明 |
|---|---|
| `versions/26.2.0/.../client/gui/Nf262UiHostAdapter.java` | `UiHostAdapter` 实现：create/update/reorder/bindEvents/dispose/arrange/paint；节点只记可观察事实，不持渲染对象 |
| `versions/26.2.0/.../client/gui/Nf262UiScreen.java` | 原生 `Screen` 投影：绘制/输入/焦点/滚动/narration；**普通帧只 paint 已提交树** |
| `versions/26.2.0/.../client/gui/Nf262UiInputMapper.java` | **无 Minecraft 类型的纯映射层**：焦点顺序、Tab/±Shift、Enter/Space/Escape、disabled/hover 命中、事件构造、narration |
| `versions/26.2.0/.../client/gui/Nf262UiScreenHost.java` | `UiScreenHost` 实现：`Minecraft.getInstance().gui.setScreen(...)`，per-root `WeakHashMap`（非静态跨 generation registry） |
| `versions/26.2.0/.../client/gui/Nf262UiTextMeasurer.java` | 原生 `Font` 文本测量与换行 |
| `versions/26.2.0/.../client/gui/Nf262UiResources.java` | `UiResources` 装配：adapter + measurer + screenHost + 诊断汇 + 事件桥（`ProxyObject`），owner thread 动态取 `Minecraft#getRunningThread()` |
| `src/main/java/.../platform/compat/McUiCompat.java`（新，共享树） | 跨版本装配门面，**签名零 MC 类型**，ServiceLoader 解析，无提供者返回 null |
| `versions/26.2.0/.../platform/compat/Nf262UiCompat.java` + `META-INF/services/…McUiCompat$Impl` | 26.2 的 `McUiCompat.Impl` 注册 |
| `src/main/java/.../client/NekoJSClient.java`（改） | 注册 `root.onClientUiShutdown(closeClientUi)`，经唯一 facade `closeGeneration` 收口 |

### 2.3 测试

- `versions/26.2.0/src/test/java/.../Nf262UiInputMapperTest.java`：**16 项全绿**
  （焦点循环/下标稳定性/disabled+invisible 排除/Enter-Space-Escape-Tab/事件字段/滚轮消费/
  命中层级/颜色解析/文本测量/narration 优先级与 visible 过滤）。
- `common/src/test/java/.../UiScreenHostLifecycleTest.java`：**5 项全绿**
  （候选失败不投影、dispose 幂等只关一次、closeGeneration 走同一路径、facade dispose、缺 host 不报错）。

---

## 3. AC8 真实客户端证据（如实）

### 3.1 成立的部分

`evidence/t41-client-log.txt`（摘自 `<gameDir>\logs\latest.log`，本次运行）：

```text
[149月2026 22:53:01.635] [Render thread/INFO] [NekoJS/]: [client] [t41] render#1
[149月2026 22:53:02.321] [NekoJS-Log-Flusher/INFO] [NekoJS/]: [client] [t41] OPENED ok renders=1
...
[149月2026 23:01:18.973] [Render thread/INFO] [NekoJS/]: [client] [t41] render#33
```

可核验结论：
- `Ui.open(jsxScreen)` 在真实 26.2 客户端上**被调用且返回成功**；
- render function **真的执行**（`render#1 … render#33`，连续 reopen 时递增）；
- 执行线程是 **Render thread**（owner-thread 校验通过）；
- 打包进被测 jar 的适配器类与服务注册见 `evidence/jar-adapter-classes.txt`。

### 3.2 **不成立**的部分（因此 AC8 = partial）

- **屏幕上没有 JSX 像素。** 让 render 每 4 秒连续 open（日志里 render 走到 #33），
  在**同一次** MCP 会话内截图，画面始终是原版 `TitleScreen`（`evidence/01-screen-open.png`、
  `01b-screen-open.png`，**这两张图是原版主菜单，不是 JSX UI 证据**）。
- MCP `enumerate_widgets` / `get_screen_buttons` 全程返回 `{"error":"no screen"}`。
- **输入注入对游戏无效**（F3 对照实验：`press_key F3` 后调试面板未出现），
  故点击/文本/滚轮/Tab/Shift+Tab/Enter/Space/Escape/hover/disabled **未验证**。
- resize / 关闭 / 外部替换的**原生可见行为未验证**（仅 common 侧 fake 路径有测试）。

### 3.3 仍未定位的疑点（交维护者）

26.2 的 `Gui#setScreen` 语义与 26.1 不同（`screenLayers` 栈 + `popScreenLayerInternal`）。
现象符合「Screen 被 set 但未进入显示层 / 立即被替换」，但也可能是本适配器绘制路径问题。
**未做进一步定位**：既有 `NekoErrorDashboardScreen` 在 26.2 上也未验证过，无法区分是本票引入
还是 26.2 既有问题。建议维护者用一个最小 Mixin/命令在 26.2 上 `setScreen` 一个空 `Screen`
做对照，以判定归属。

---

## 4. 构建与测试结果

| 命令 | 结果 |
|---|---|
| `:26.2.0:compileJava` | SUCCESS |
| `:26.2.0:jar` / `:26.2.0:build -x test` | SUCCESS（含 `:26.2.0:check`、`verifyDevModSourceSets`） |
| `:26.2.0:test --tests '*Nf262UiInputMapperTest*'` | 16 tests / 0 failures |
| `:common:test --tests '*UiScreenHostLifecycleTest*'` | 5 tests / 0 failures |
| `guardLint :common:check` | SUCCESS，guardLint 249 守卫 / 332 文件 / 0 违规 / 0 警告 |
| `:common:test --rerun-tasks --no-build-cache` | 见票据 Closure record（全量结果） |

**未新增** Gradle project / API jar / 第二 runtime owner / UI global singleton / 静态跨 generation registry。
common 不 import Minecraft/loader（guardLint 0 违规）。

---

## 5. 未验证项与 owner

| 未验证项 | owner |
|---|---|
| 26.2 真实客户端上 JSX Screen 的**可见绘制**（原生显示层归属疑点） | 维护者 |
| 点击 / 文本输入 / 滚轮 / Tab / Shift+Tab / Enter / Space / Escape / hover / disabled 的 in-game 行为 | 维护者（需可注入输入的通道） |
| 原生 resize / Screen 关闭 / 外部 `setScreen` 替换 / reload 收口 | 票 42 + 维护者 |
| 1.21.1 / 26.1.2 / 26.2.0-fabric / 26.1.2-fabric 的 UI capability | 各节点 Adapter 落地后裁定（当前 `not verified`） |
