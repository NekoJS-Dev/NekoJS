# 现有游戏内脚本报错 UI 与数据契约：现状证据

> 观察时间：2026-09-10 18:48 +08:00。
> 范围：仓库根说明、`CONTEXT.md`、共享 `src/`、`common/`、`versions/1.21.1`、`versions/26.1.2`、`versions/26.2.0`、两个 Fabric 26.x 节点，以及 NeoForge/Fabric 官方 UI 与网络文档。
> 方法：只读静态调查；未运行游戏、未执行构建、未创建子任务。本文只新增本文件。
> 事实等级：正文标注“事实”“推断”“建议”。源码引用使用仓库相对路径和 1-based 行号；官方文档另列 URL。

## 0. 给 Astra 的结论摘要

- 事实：完整错误 Dashboard 当前只存在于 NeoForge 1.21.1 / 26.1.2 / 26.2.0。26.x 共享实现带 `//? if neoforge && >=26`，1.21.1 使用单独求值副本；`src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java:1`、`versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java:1-2`。
- 事实：Fabric 26.1.2 / 26.2.0 没有错误 Dashboard 与对应网络面板；`view_all_errors` 逐条发到聊天。`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java:42-54,234-247`。26.2.0-fabric 复用 26.1.2 Fabric source bridge：`settings.gradle.kts:3-8`。
- 事实：客户端从网络只收到 `ErrorSummaryDTO` 的 6 个字段：`id`、`path`、`line`、`count`、`message`、`fullDetails`。`column`、`ScriptType`、原始异常、结构化 stack frame、severity、timestamp 均不在 DTO 中。`common/src/main/java/com/tkisor/nekojs/network/ErrorSummaryDTO.java:3-9`。
- 事实：错误事实源在逻辑服务端进程的 `NekoRuntimeRoot.errors()`。显式打开是服务端命令向单个玩家发送 S2C packet；客户端没有请求错误列表的 serverbound packet。`src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java:97-108,464-473`、`src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java:36,106-111`。
- 事实：当前 Dashboard 是原生 `Screen` 上的手绘像素布局、`EditBox` 和两个 `ObjectSelectionList`。它没有 HTML/DOM/CSS 运行时。界面动作是列表选择、搜索、4 类过滤、复制完整文本、定位本地文件、打开 `latest.log`、编辑/同步、全屏/关闭。
- 事实：当前没有分页按钮、没有“重新向服务端拉错误列表”按钮、没有 UI 内 reload 按钮、没有复制路径/消息/代码片段/stack 的细分动作，也没有排序、清空、忽略或历史。
- 事实：`fullDetails` 已经是中文标签的自由文本，不是结构化字段。普通行只显示 `path` 和 `message`；`line` 与 `count` 实际未被列表绘制。`NekoErrorDashboardScreen.java:573-586`。
- 硬边界：`CONTEXT.md:7-9` 规定报错保持普通形式，不带修复指引。HTML mock 不应出现“自动修复”“一键回滚”“撤销重载”“恢复旧版本”“建议修复”等未实现承诺，也不应把错误 UI 描述成事务回滚或自愈工具。

## 引用路径简写

后文为紧凑起见使用简写；对应完整仓库路径如下。

| 简写 | 仓库路径 |
|---|---|
| `NekoErrorDashboardScreen.java` | `src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java` |
| `1.21.1/NekoErrorDashboardScreen.java` | `versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java` |
| `NekoErrorUIHelper.java` | `src/main/java/com/tkisor/nekojs/core/error/NekoErrorUIHelper.java` |
| `ScriptError.java` | `common/src/main/java/com/tkisor/nekojs/core/error/ScriptError.java` |
| `DefaultErrorTracker.java` | `common/src/main/java/com/tkisor/nekojs/core/error/DefaultErrorTracker.java` |
| `SourceMapRegistry.java` | `common/src/main/java/com/tkisor/nekojs/core/error/SourceMapRegistry.java` |
| `ErrorSummaryDTO.java` | `common/src/main/java/com/tkisor/nekojs/network/ErrorSummaryDTO.java` |
| `ShowErrorListPacket.java` | `src/main/java/com/tkisor/nekojs/network/ShowErrorListPacket.java` |
| `NekoJSNetwork.java` | `src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java` |
| `NekoJSCommands.java` | `src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java` |
| `NekoRuntimeRoot.java` | `common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java` |
## 1. 平台与实现矩阵

| 节点 | 错误收集 | 游戏内 UI | 网络错误列表 | 证据 |
|---|---|---|---|---|
| NeoForge 26.1.2 | 有 | 有，共享 26.x Screen | `ShowErrorListPacket` S2C | `src/.../NekoErrorDashboardScreen.java:1`、`NekoJSNetwork.java:36` |
| NeoForge 26.2.0 | 有 | 有，使用 26.2 compat screen/toast API | 同上 | `versions/26.2.0/.../Nf262ClientCompat.java:17-46` |
| NeoForge 1.21.1 | 有 | 有，独立 API 形态的同类 Screen | 同上 | `versions/1.21.1/.../NekoErrorDashboardScreen.java:1-2` |
| Fabric 26.1.2 | 有 `DefaultErrorTracker` 与 reporter | 无 Dashboard；`view_all_errors` 文本降级 | 无错误 Dashboard 包 | `versions/26.1.2-fabric/.../NekoJSFabricMod.java:141-162`、`FabricNekoJSCommands.java:42-54,234-247` |
| Fabric 26.2.0 | 复用 26.1.2 Fabric source bridge | 同上 | 同上 | `settings.gradle.kts:3-8` |

`ErrorTracker` 不是通用崩溃中心：其文档边界仅覆盖脚本入口、事件/timer callback、`node:test`、module evaluation 等脚本错误；Java bootstrap、平台 setup、插件发现仍走 logger。`common/src/main/java/com/tkisor/nekojs/core/error/ErrorTracker.java:10-17`。

## 2. 从脚本异常到屏幕的完整链路

~~~text
脚本异常 / callback exception
  -> ErrorTracker.record(...) 或 recordCallbackError(...)
  -> ScriptError 解析 message/path/line/column/snippet
  -> DefaultErrorTracker.errors: Map<ScriptId, ScriptError>
  -> NekoRuntimeRoot.errors()
  -> NekoJSCommands.errorSnapshot()
  -> ErrorSummaryDTO 的 6 个字符串/整数字段
  -> ShowErrorListPacket（服务端 -> 客户端）
  -> context.enqueueWork(...)
  -> NekoErrorDashboardScreen.create(...) 或 updateErrors(...)
  -> ErrorListWidget / StackTraceList 原生绘制
~~~

关键事实：

- `ErrorSummaryDTO` 精确定义为 `id, path, line, count, message, fullDetails`：`common/src/main/java/com/tkisor/nekojs/network/ErrorSummaryDTO.java:3-9`。
- 投影顺序与线格式是 `id -> path -> line -> count -> message -> fullDetails`，随后写 `openIfMissing`：`src/main/java/com/tkisor/nekojs/network/ShowErrorListPacket.java:26-46`。
- `ShowErrorListPacket` 的默认构造器把 `openIfMissing` 设为 `true`；reload 自动刷新使用 `false`：`ShowErrorListPacket.java:13-17`、`NekoJSCommands.java:476-479`。
- 客户端接收后投递到游戏主线程；若当前已是 Dashboard 则替换列表，否则只在 `openIfMissing=true` 时打开 Screen：`NekoJSNetwork.java:81-82,105-111`。

## 3. 客户端实际可得的数据契约

### 3.1 字段与真实含义

| 字段 | Java 类型 | 服务端来源 | 客户端可否依赖 | 重要边界 |
|---|---|---|---|---|
| `id` | `String` | `err.getErrorId().toString()` | 是，用于选中项稳定匹配 | 普通脚本形如 `nekojs:<type>/<relative-path>`；callback 形如 `nekojs:rt/<TYPE>/<path-or-kind>`。`ScriptTypeEnv.java:67-72`、`DefaultErrorTracker.java:173-179` |
| `path` | `String` | `err.getDisplayPath()` | 是，但可能是 root-relative、virtual module path、绝对路径或 fallback | `ScriptError.java:226-249`；不是 HTML URL，也是本地/远端语义混在一起 |
| `line` | `int` | `err.getLineNumber()` | 是；缺失时为 `-1` | 不一定经过真实源码显示行修正；stack 还会走另一条 `getRealCodeLine` |
| `count` | `int` | `err.getOccurrenceCount()` | 是 | 这是同一错误记录的累计发生次数，不是错误条数 |
| `message` | `String` | `err.getErrorMessage()` | 是 | 可为多行；服务端 fallback 是 `Unknown error` |
| `fullDetails` | `String` | `err.getFullDetailText()` | 是，但应视为不可解析/仅弱解析的自由文本 | 已含中文标签、可选片段、stack；不是 JSON/record |

事实摘录：

~~~java
public record ErrorSummaryDTO(
        String id,
        String path,
        int line,
        int count,
        String message,
        String fullDetails
) {}
~~~

来源：`common/src/main/java/com/tkisor/nekojs/network/ErrorSummaryDTO.java:3-9`。

### 3.2 服务端内部有、但当前不会结构化传给客户端的字段

`ScriptError` 内部还持有或暴露：

- `columnNumber`、`originalSymbolName`、`sourceCodeSnippet`、`rawException`、`scriptType`、`script`、`errorPath`：`ScriptError.java:17-33,206-234`。
- `column` 只可能通过 `fullDetails` 的代码片段标题间接出现，DTO 没有独立列字段。
- `ScriptType` 只会在 `fullDetails` 的 `环境:` 行出现，DTO 没有独立 type 字段。
- stack frame、host/guest 标记、Java frame 只存在 `rawException` 与 `fullDetails` 文本中。
- 当前 DTO 没有：时间戳、首次/最近出现时间、severity、模块身份、owner、generation、reload 阶段、trust 来源、修复建议、历史版本、是否可恢复。
- `docs/architecture-refactor/implementation-tickets/30-diagnostics.md:3,21-33` 描述的是未来统一 diagnostic record；这些拟议字段不是当前 wire contract，HTML 样例不得伪装成现成数据。

### 3.3 排序与数量

- 事实：tracker 用 `ConcurrentHashMap<ScriptId, ScriptError>` 保存，`getAllErrors()` 返回 `values()`；没有显式排序：`DefaultErrorTracker.java:46,164-170`。
- 事实：列表投影直接 `stream()` 后 `toList()`，仍未排序：`NekoJSCommands.java:464-473`。
- 推断：当前列表顺序不是稳定 UI 契约。HTML mock 可以固定一个有代表性的顺序，但真实实现不能承诺按时间/行号/路径排序。
- 事实：命令判断和聊天提示使用 `errors().count()`，这是错误记录数；单条 `count` 是该记录的 occurrence 数。

### 3.4 行列、路径与源码片段

- `ScriptError.lineNumber` / `columnNumber` 初始为 `-1`：`ScriptError.java:23-28`。
- ESM 诊断直接提供 `diagnostic.line()/column()`；Polyglot 异常先取 source location，再经 `SourceMapRegistry` 映射：`ScriptError.java:83-114`。
- `SourceMapRegistry` 无 map 时返回原始 JS 行列；有 map 时返回 `originalLine + 1, originalColumn + 1`：`common/src/main/java/com/tkisor/nekojs/core/error/SourceMapRegistry.java:52-62,360-387,392-416`。
- `getDisplayPath()` 优先 `errorPath`；否则对 `script.path` 相对 root；再退到 `"Unknown location"`：`ScriptError.java:226-249`。
- 路径可能是 workspace 相对路径，也可能是 registry 提供的 virtual module display path，或 `relativize` 失败后的绝对路径：`DefaultErrorTracker.java:311-357`。
- 代码片段默认取错误行的前后各 2 行；高亮当前行，`column > 0` 时画 caret。若本地文件不可读，才使用 embedded `sourcesContent` 或 fallback snippet：`ScriptError.java:160-195`。
- 事实：代码片段只在 `lineNumber != -1 && sourceCodeSnippet` 非空时进入 `fullDetails`。因此 `line` 有值不代表一定有源码片段。`ScriptError.java:420-432`。

### 3.5 `fullDetails` 的真实格式

`ScriptError.getFullDetailText()` 的模板可概括为：

~~~text
环境: <ScriptType.name() 或 未知>
脚本: <getDisplayPath()>
[频次: 连续发生了 N 次]

[>> 异常代码片段 (于方法 `<symbol>` 行 L, 列 C):
 > L | <source>
     | <spaces>^

<相邻源码行>]

<PolyglotException message>
    at <guest root name> (<mapped path>:<line>)
    at [Java] <host frame>
~~~

若没有 PolyglotException，尾部只追加 `getErrorMessage()`。事实来源：`ScriptError.java:411-443`。

细节标签是硬编码中文，即使 UI 语言是英文也不会从 DTO 改变：`ScriptError.java:413-431,434-439`。HTML mock 可以显示这些中文，但不要把 `环境:`、`频次:`、`异常代码片段` 误标为独立字段。

Polyglot stack 的格式化事实：

- 首行是 `e.getMessage()`。
- guest frame 固定为 `"    at " + rootName + " (" + mappedPath + ":" + realLine + ")"`。
- 无 source location 的 guest frame 为 `"    at " + rootName + " (Unknown Source)"`。
- host frame 为 `"    at [Java] " + hostStr`，但会过滤 Truffle/Graal/NekoJS executor 等噪声。
- 来源：`DefaultErrorTracker.java:263-309`；黑名单在 `DefaultErrorTracker.java:31-37`。

### 3.6 长度与包体上限

- 事实：`fullDetails` 写读上限均为 `262144`：`ShowErrorListPacket.java:33,44`。
- 除 `fullDetails` 外，`id/path/message` 使用未显式传上限的默认 `writeUtf` 重载；本机 26.1.2 Minecraft client artifact 中该重载默认上限为 `32767`，且先按 Java `CharSequence.length()` 检查，再按 UTF-8 编码结果检查。核验：`C:\Users\11515\.gradle\caches\neoformruntime\artifacts\minecraft_26.1.2_client.jar` 的 `net.minecraft.network.FriendlyByteBuf` / `Utf8String`（`javap`）。
- 事实：NeoForge 26.1 官方文档规定 clientbound `CustomPacketPayload` 至多 1 MiB，serverbound 小于 32 KiB：<https://docs.neoforged.net/docs/networking/payload/>，Sending Payloads。
- 推断：`ShowErrorListPacket` 一次发送全部错误，没有 NekoJS 侧分片、页大小或总字节预算；多条接近 262144 的 UTF-8 详情可超过 1 MiB。当前 UI/packet 路径没有可见的超限状态处理。
- 事实：packet 没有 schema version、snapshot revision、错误记录总数或分页游标；一次 `updateErrors` 是全量替换。

## 4. 客户端与逻辑服务端来源、权限和控制方向

### 4.1 谁产生、谁接收

- 错误数据来自正在运行脚本的进程中的 `NekoRuntimeRoot.errors()`：`NekoRuntimeRoot.java:104-110,179-183`。
- 单人/局域网集成端中，逻辑服务端与客户端在同一进程；`NekoJSClient` 直接使用同一个 `NekoJSMod.RUNTIME_ROOT` 加载 CLIENT 脚本：`src/main/java/com/tkisor/nekojs/client/NekoJSClient.java:43-47`。
- 专用服务端只有服务端进程可产生的脚本类型和错误；客户端不会自动收到源码或所有本地类型。
- 打开列表时，命令必须是玩家来源：`source.getPlayerOrException()`；控制台不能作为 Dashboard 接收者：`NekoJSCommands.java:97-103`。

### 4.2 权限

- NeoForge 26.x 整个 `/nekojs` 命令树要求 `Commands.LEVEL_GAMEMASTERS.check(source.permissions())`：`NekoJSCommands.java:58-60`。
- NeoForge 1.21.1 对应要求 `source.hasPermission(2)`：`versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java:52-54`。
- Fabric 命令树同样要求 `LEVEL_GAMEMASTERS`：`versions/26.1.2-fabric/.../FabricNekoJSCommands.java:66-70`。
- `ShowErrorListPacket` 本身没有 handler 内权限复检；它只是 S2C，服务器代码决定收件人。当前唯一内置打开路径是被 GM-gated 命令触发。
- 服务端读/写脚本的单独操作会再次检查 `isGameMaster(player)`：`NekoJSNetwork.java:146-216`。26.x 实现为 `Commands.LEVEL_GAMEMASTERS.check`，1.21.1 实现为 `hasPermissions(2)`：`versions/26.1.2/.../Nf261PlatformCompat.java:24-27`、`versions/26.2.0/.../Nf262PlatformCompat.java:23-26`、`src/main/java/com/tkisor/nekojs/platform/compat/Nf1211PlatformCompat.java:26-29`。

### 4.3 打开、刷新和服务端拒绝

- `/nekojs error` 只发错误数量与“点击打开列表”的 click component，不直接打开 UI：`NekoJSCommands.java:85-94`、`NekoErrorUIHelper.java:14-26`。
- `/nekojs view_all_errors` 在错误数大于 0 时向执行玩家发送新快照；错误数等于 0 时只回聊天，不发空 packet：`NekoJSCommands.java:97-108`。
- reload/test 完成后 `sendReloadResult` 先调用 `refreshOpenErrorDashboard`；仅当命令 source entity 是 `ServerPlayer` 时发送 `openIfMissing=false` 的全量快照：`NekoJSCommands.java:476-495`。若 Dashboard 未打开，该刷新 packet 被忽略；若已打开，空列表也会清空选择与详情。
- 当前没有客户端“请求错误列表”payload。`ShowErrorListPacket` 只注册为 `playToClient`：`NekoJSNetwork.java:36`。
- 当前 Dashboard 级没有标准化的“服务器拒绝查看”回包。命令权限不足时通常走命令层拒绝，UI 根本不会打开。
- 编辑同步操作的服务端拒绝会走 `SyncFeedbackPacket(false, message)`；Dashboard 只以 toast 显示，例如精确文本：
  - `权限不足，无法拉取服务端代码！`
  - `权限不足，无法修改服务端代码！`
  - `权限不足！`
  来源：`NekoJSNetwork.java:149-151,170-172,187-189,204-206`；回包结构见 `src/main/java/com/tkisor/nekojs/network/SyncFeedbackPacket.java:10-23`。
- 建议：HTML mock 若要画“服务器拒绝”，应画成同步操作失败的 transient toast/banner，或明确标成“拟议的查看授权状态”；不要画成错误的字段或当前已存在的错误列表拒绝协议。

### 4.4 远端路径限制

- `path` 作为文本从服务端来，但 `actionLocate()`、`openTab()` 都使用客户端自己的 `NekoJSPaths.get().root()` 打开本地文件：`NekoErrorDashboardScreen.java:193-207,248-251`。
- 因而专用服务器上的 error `path` 不代表当前客户端磁盘上存在同名文件。Dashboard 不会自动为选中错误拉取服务端源码。
- 编辑器中的“拉取当前脚本”是另一条 C2S 路径，且要求编辑中、当前 tab 存在与服务端 GM：`NekoErrorDashboardScreen.java:303-310`、`NekoJSNetwork.java:146-165`。
- 26.x compat 的 `dashboardLoadServerScript(screen, path, content)` 实际只调用 `dashboard.loadServerScript(content)`，忽略 `path`；当前 26.x Screen 也未校验响应 path，内容会写入当前激活 tab：`versions/26.1.2/.../Nf261ClientCompat.java:41-45`、`versions/26.2.0/.../Nf262ClientCompat.java:42-46`、`NekoErrorDashboardScreen.java:286-292`。1.21.1 副本则校验 `tab.path.equals(path)`：`versions/1.21.1/.../NekoErrorDashboardScreen.java:284-291`。这是现存跨版本行为差异/风险，不是 mock 应暗示的可靠能力。

## 5. 现有 UI：布局与动作清单

### 5.1 布局事实

- 根类型：`Screen`；26.x 用 `GuiGraphicsExtractor`、`EditBox`、`ObjectSelectionList`：`NekoErrorDashboardScreen.java:10-16,30-35`。
- 背景：垂直渐变 `#0A0A0B -> #121214`，顶部菜单栏 `#18181A`，详情底 `#1E1E1E`，边框 `#333333`：`NekoErrorDashboardScreen.java:344-399`。
- 非全屏左侧列表宽为 `max(160, width * 0.35)`；外边距 15；内容从 y=55 开始：`NekoErrorDashboardScreen.java:139-149`。
- 列表 row 高 36；每行只画 `path` 与单行截断的 `message`：`NekoErrorDashboardScreen.java:564-585`。
- 详情列表原始 row 高 12，先将 tab 扩为 4 空格，再按像素宽度拆行，每个 wrapped line 成为一个 entry：`NekoErrorDashboardScreen.java:222-240,610-630`。
- 无显式最小窗口宽/高；右侧宽度直接由 root `width` 算。推断：窄窗口、超长 GUI-scale 调整或长 label 没有布局保护。

### 5.2 动作表

| 动作 | 触发 | 当前真实行为 | 关键限制 |
|---|---|---|---|
| 选择错误 | 左键 row | 按 `id` 标记选中，刷新详情 | 行没有 `line/count` 展示 |
| 打开标签页 | 双击 row、右键菜单、`[编辑]` | `root.resolve(path)` 后本地读文件并打开编辑器 | 远端 path 可能不是本地文件；缺失文件可能打开空 tab |
| 复制 | `[复制]` | 只复制 `selectedError.fullDetails()` | 不复制 path/message/snippet/stack 的独立格式 |
| 定位 | `[定位]` / File 菜单 | 调 OS 打开本地路径，并先显示成功 toast | 不检查存在性、未知 path 或远端 path |
| 打开日志 | `[日志]` | 打开客户端 `logs/latest.log` | 不是该脚本的 per-type 日志 |
| 编辑 | `[编辑]` | 进入内嵌 tabbed editor | 无编辑器版本不应保留此入口 |
| 保存 | `[保存当前]`、File 菜单、Ctrl+S | 写本地 workspace；初始读失败的 tab 禁止写回 | 是编辑器能力，不是错误查看必需能力 |
| 推送当前 | Sync 菜单 | C2S `SaveScriptPacket` | 需编辑状态；服务端 GM 复检 |
| 拉取当前 | Sync 菜单 | C2S `FetchScriptRequestPacket` | 需编辑状态；服务端 GM 复检 |
| 推送全部 | Sync 菜单 | C2S `UploadAllScriptsPacket` | 服务端 GM 复检；危险操作 |
| 拉取全部 | Sync 菜单 | C2S `FetchAllScriptsRequestPacket` | 服务端 GM 复检；危险操作 |
| 搜索 | 左上 EditBox | 仅对 `path` 和 `message` 做不区分大小写 contains | 不搜 `id/line/count/fullDetails` |
| 过滤 | 左侧 tabs | `ALL/RUNTIME/SYNTAX/OTHER`，并显示记录数 | 类别由 message 子串猜测 |
| 滚动 | 两列表 | 各自滚动；详情把 wrap 后每行做成 entry | 无分页、无虚拟化承诺 |
| 右键菜单 | row 右键 | 全屏、打开 tab、外部打开 | 不是通用 keyboard/context action |
| 全屏 | 标题栏/右键 | 切 `isMaximized` 并重建布局 | 非全屏左侧列表、搜索、过滤器会消失 |
| 关闭 | 标题栏 X / File/Exit | 调 `onClose()` | 没有显式 parent screen/返回栈 |
| 再次向服务端取列表 | 无 | 不存在 | 只等命令/reload 推送 |
| reload 脚本 | 无 | UI 内不存在 | 仍需聊天命令，受 GM 权限约束 |
| 分页上一页/下一页 | 无 | 不存在 | 只用滚动列表 |
| 排序/清空/忽略/历史 | 无 | 不存在 | 不要当作现状 |

动作证据：菜单 `NekoErrorDashboardScreen.java:72-91`；open tab `193-207`；搜索/过滤 `209-220,337-342`；详情 `222-241`；复制/定位/日志 `248-263`；同步 `294-326`；标题按钮和点击区 `406-444,485-562`；列表类 `564-633`。1.21.1 的 UI 语义相同，仅 Java/Minecraft API 形态和双击实现不同：`versions/1.21.1/.../NekoErrorDashboardScreen.java:246-260,284-291,519-613`。

### 5.3 过滤是 UI 猜测，不是服务端分类

当前精确算法：

~~~java
if (msg.contains("syntax") || msg.contains("unexpected")) return SYNTAX;
if (msg.contains("null") || msg.contains("undefined") || msg.contains("error")) return RUNTIME;
return OTHER;
~~~

来源：`NekoErrorDashboardScreen.java:337-342`。它把 message 转小写后做英文子串匹配，不知道真实 exception class、module phase 或 ScriptType。HTML mock 显示“运行/语法/其他”时可以保留现有视觉，但不要把它画成来自服务端的可信 severity。

## 6. 空态、长消息、无源码与拒绝状态的准确 mock 建议

### 6.1 状态矩阵

| Mock 状态 | 当前契约是否支持 | 应显示什么 | 不应显示什么 |
|---|---|---|---|
| 正常运行错误 | 支持 | `path`、`message`、occurrence、完整详情、复制/定位/日志 | 修复建议、自动修复 |
| 重复 callback | 支持 | `count > 1`；`fullDetails` 有 `频次` 行 | 时间线、首次发生时间 |
| 语法错误 | 部分支持 | message、line、可能 snippet 与 stack | 结构化 compiler phase |
| 超长 message | 支持 | 列表单行截断；详情必须可滚动/换行 | 假设 message 只有一行 |
| 超长 `fullDetails` | 支持 | 只读详情滚动；保留原始空白/tab 展开后的可读性 | 假定有分页或延迟加载 |
| 无本地源码文件 | 支持 | 仍显示 DTO；禁用/降级定位和编辑 | 伪造源码、空白代码块 |
| 无 source map/无 snippet | 支持 | 显示 `line = -1` 或仅有 message/stack；标明无源码上下文 | 编造行号或代码 |
| `Unknown location` | 契约允许 fallback | 纯文本详情，禁用文件动作 | 把它当合法 workspace 路径 |
| 选中项被新快照移除 | 支持 | 选择第一条；列表为空则清空详情 | 保留已失效错误为“当前” |
| 空列表 | 协议可解码，当前 UI 无专页 | 设计可见 empty state；总数 0 | 把它说成当前已有空态界面 |
| 过滤结果 0 | 支持 | “0 / N”并保留过滤条件 | 改为全局“无错误” |
| 服务器拒绝查看 | 当前没有列表级协议 | 只能标为拟议授权状态；命令拒绝发生在 UI 前 | 伪造 `ShowErrorListPacket` denial |
| 同步拒绝 | 支持 feedback toast | 使用精确权限不足文本，成功/失败区分 | 永久错误行、自动重试 |
| 远端源码不可访问 | 推断/行为支持 | “本机不可用”；详情仍依赖服务端已送 `fullDetails` | 保证可打开/自动下载 |
| 网络数据超限 | 无处理状态 | 可设计为拟议降级态；当前为空缺 | 说当前已自动分页/重试 |
| Fabric | 无 Dashboard | 若要 mock，单独标明文本降级或未来设计 | 暗示 Fabric 已有同款面板 |

### 6.2 空态精确性

- 明确执行 `/nekojs view_all_errors` 且 error count = 0 时，服务端只回 `nekojs.command.error.none` 聊天，不发送空列表：`NekoJSCommands.java:100-106`。
- reload 自动刷新可能把空列表发送给已打开 Dashboard（`openIfMissing=false`）；`updateErrors([])` 会清空 `errors`，令 `selectedError = null`：`NekoErrorDashboardScreen.java:104-124`。
- 因而 HTML 的 empty state 是“对现有空白结果的设计补齐”，不是当前已经显示的专页。

### 6.3 服务器拒绝的精确性

- `/nekojs view_all_errors` 无权限时，命令在打开前被拒绝，当前没有 UI 内拒绝页或拒绝 packet。
- `SyncFeedbackPacket` 的权限不足可真实呈现在 Dashboard toast 中，因为 client handler 会调用 `screen.onSyncFeedback(false, message)`：`NekoJSNetwork.java:89-90,120-128`、`NekoErrorDashboardScreen.java:328-335`。
- 建议 mock 中的拒绝态使用 toast/banner，并明确它来自同步操作；若设计一个查看权限页，标记为“拟议”。

## 7. HTML 样例可直接使用的代表数据

以下是严格保持 6 字段 wire shape 的 mock fixture。内容是代表性文案，不是游戏捕获日志；请保留字段类型，不要把 `fullDetails` 拆成看似已存在的结构化字段。

~~~json
[
  {
    "id": "nekojs:server/events/player_tick.js",
    "path": "server_scripts/events/player_tick.js",
    "line": 42,
    "count": 3,
    "message": "TypeError: Cannot read properties of null (reading 'id')",
    "fullDetails": "环境: SERVER\n脚本: server_scripts/events/player_tick.js\n频次: 连续发生了 3 次\n\n>> 异常代码片段 (行 42, 列 11):\n > 42 | const playerId = event.player.id\n     |           ^\n\nTypeError: Cannot read properties of null (reading 'id')\n    at onPlayerTick (server_scripts/events/player_tick.js:42)\n"
  },
  {
    "id": "nekojs:server/commands/example_command.js",
    "path": "server_scripts/commands/example_command.js",
    "line": 18,
    "count": 1,
    "message": "SyntaxError: Unexpected token '}'",
    "fullDetails": "环境: SERVER\n脚本: server_scripts/commands/example_command.js\n\n>> 异常代码片段 (行 18, 列 1):\n > 18 | }\n     | ^\n\nSyntaxError: Unexpected token '}'\n"
  },
  {
    "id": "nekojs:client/hud/render_hud.js",
    "path": "client_scripts/hud/render_hud.js",
    "line": -1,
    "count": 7,
    "message": "ReferenceError: missingBinding is not defined",
    "fullDetails": "环境: CLIENT\n脚本: client_scripts/hud/render_hud.js\n频次: 连续发生了 7 次\n\nReferenceError: missingBinding is not defined\n    at <anonymous> (Unknown Source)\n"
  },
  {
    "id": "nekojs:rt/SERVER/timer/1a2b3c",
    "path": "timer/1a2b3c",
    "line": -1,
    "count": 25,
    "message": "Unknown error",
    "fullDetails": "环境: SERVER\n脚本: timer/1a2b3c\n频次: 连续发生了 25 次\n\nUnknown error\n"
  }
]
~~~

说明：

- 第一项适合做默认选中项；`fullDetails` 内含真实模板的 snippet/stack。
- 第二项展示语法错误与特殊字符消息。
- 第三项演示 `line = -1`、无 snippet、`Unknown Source` stack；列表仍需正常显示。
- 第四项演示 callback/path 不是 workspace 文件，定位/编辑应降级。
- `fullDetails` 的真实标签是中文字符串；`path` 使用 `/`；`id` 的普通脚本前缀是不含 `_scripts/` 的逻辑路径，而 `path` 是 root-relative workspace 路径。
- 长消息用例可以把第二项 `message` 重复到数百字符，但保持它在 JSON 中是一个字符串。
- “无源码”不要只画空代码框；应画“无源码片段/仅消息与堆栈”，并保留复制完整详情。

## 8. 去掉编辑器时，错误面板必须保留什么

建议最小只读 Dashboard 保留：

1. 错误列表与选中状态；刷新前置用 `id` 保持同一错误选中。
2. `path`、`message`、`count` 与可选的 `line`；当前真实 UI 只画前两项，mock 可以补出后两项但应标明是呈现改进。
3. `fullDetails` 的只读、可滚动区域，保留原文空白，tab 显示为 4 空格。
4. 搜索与现有 4 类过滤；过滤计数仍按当前记录数。
5. 复制完整详情；若产品想细分，可另设复制 path/message，但这是新增动作，不是现状。
6. 打开日志。
7. 本地路径有效时的定位/外部打开；无效、`Unknown location`、virtual/remote path 时必须禁用或降级，不能只弹“成功”。
8. 被动接收服务端新快照；若 Dashboard 已打开则更新，未打开时尊重 `openIfMissing`。
9. 空态、长详情、无源码、远端不可访问和过滤 0 结果。
10. 明确的关闭/全屏与键盘 Escape/滚动行为。

可以移除或隔离：

- `[编辑]`、tab、dirty/save、`readFailedTabs`、`loadServerScript`。
- 推送/拉取当前脚本、推送/拉取全部脚本；这些属于 workspace/sync，不是错误查看核心。
- 依赖编辑器状态的 header 控件和 `File > Save`。

## 9. 原生 Minecraft 渲染与交互约束

以下约束按官方文档核验；HTML 只是视觉稿，不能据此假定运行时有 DOM/CSS。

| 约束 | 官方事实 | 对 mock / 实现的影响 |
|---|---|---|
| Screen 生命周期 | NeoForge 26.1 文档：窗口 resize 会重新初始化 Screen，widgets/预计算布局应在 `init` 完成；Fabric 26.1.2 文档同样要求在 `init` 创建 widgets，`extractRenderState` 每帧调用。 | HTML 中的响应式布局应在逻辑像素中明确断点；不要在构造期固定错误尺寸。 |
| 没有 label widget | Fabric 26.1.2 文档明确：Minecraft 没有 label widget，文字要自行 draw。 | 标题、标签、说明文字都可 mock，但不能假设有现成 Button/Label 组件。 |
| 按钮尺寸 | Fabric 26.1.2 文档建议 vanilla Button 使用固定高度 20。 | HTML 按钮视觉高度至少按 20 logical px；当前 10px 高文字热区偏小。 |
| GUI scale / 坐标 | NeoForge 26.1 文档：坐标不是固定范围，受屏幕尺寸和 GUI scale 影响，错误缩放会错位。 | 用逻辑像素和相对布局，不用浏览器 px 直接映射物理像素。 |
| 绘制模型 | NeoForge 26.1 文档：GUI 提交 `GuiRenderState`，`GuiGraphicsExtractor` 提供 fill/text/blit/pose/scissor。 | HTML shadow、filter、DOM z-index、CSS scroll 不保证可直接实现；视觉上应拆成矩形、文字、边框、scissor 区域。 |
| 裁剪 | NeoForge 26.1 文档提供 `enableScissor/disableScissor`。 | 长详情、列表和代码块必须有明确裁剪边界，不能依赖父元素 `overflow` 语义。 |
| 输入 | `Screen` 是 `GuiEventListener`；当前实现右键、双击、滚动、Escape、搜索框。 | Mock 应至少展示左键选择、双击/显式按钮打开、右键菜单、滚动、Escape。 |
| 主线程 | NeoForge 26.1 文档：payload handler 默认主线程；耗时工作应放 network thread；当前客户端 handler 显式 `enqueueWork`。 | UI 更新必须回到 render/main thread；mock 中不能把交互当作网络线程同步操作。 |
| Clientbound 包体 | NeoForge 26.1 文档：至多 1 MiB。当前全量列表没有分片。 | 详情异步分页/懒加载是架构建议，不是现状；mock 不要伪造已存在的 per-error lazy loading。 |

官方依据：

- NeoForge 26.1，Screens：<https://docs.neoforged.net/docs/rendering/screens>，尤其是 Rendering a GUI、Relative Coordinates、GuiGraphicsExtractor、Initialization、Input Handling。
- NeoForge 26.1，Registering Payloads：<https://docs.neoforged.net/docs/networking/payload/>，尤其是 Handling Payloads、Sending Payloads。
- Fabric 26.1.2，Custom Screens：<https://docs.fabricmc.net/26.1.2/develop/rendering/gui/custom-screens>，Creating a Screen。
- Fabric 26.1.2，Networking：<https://docs.fabricmc.net/26.1.2/develop/networking>，Registering a Payload、Receiving a Packet on the Client。

## 10. 现有 UI 痛点与设计约束

### 10.1 数据层痛点（事实/推断）

- 事实：客户端没有结构化 `column`、`ScriptType`、exception type、module、phase、owner、generation 或时间。
- 事实：`line` 和 `count` 已传输但列表不用；用户在列表看不到发生次数。
- 事实：类别过滤只凭英文 message 子串猜测；本地化、异常命名变化或同时命中时不可靠。
- 事实：搜索漏掉 `id`、line、count、环境、snippet 与 stack。
- 事实：`fullDetails` 是单一长字符串，依靠 UI 改 tab、按行和像素宽拆分。
- 事实：无稳定排序、无快照版本、无时间。
- 推断：这些限制会让分组、排序、重放和可访问性设计都只能建立在弱解析上；HTML mock 可以展示“结构化外观”，但必须注明字段不是当前 wire contract。

### 10.2 交互痛点

- 没有主动刷新/服务器重取；用户只能等待命令/reload 推送。
- 没有分页；大量错误和大量 wrapped detail line 都由单个客户端包与 `ObjectSelectionList` 承担。
- Fullscreen 会隐藏搜索和列表，却仍保留 details/编辑头部；不是真正的详情只读模式。
- 定位不判断 path 是否本地存在或有效，且预先生成成功 toast。
- 远端服务端错误没有自动 source pull；详情可能只有服务端已发送的文本。
- 10x10 左右的关闭/全屏/文字按钮命中区偏小；没有 disabled/loading/error 的统一视觉状态。
- 右键菜单与双击是隐式动作；无显式 copy path/open source 替代。
- `TextLineEntry.getNarration()` 返回 empty，详情行没有有效 narration：`NekoErrorDashboardScreen.java:619-632`。
- 26.x 拉取源码 path 校验缺失（第 4.4 节）；不应在 UI 设计里把它包装成可靠流程。

### 10.3 视觉/布局痛点

- 当前颜色对小字号、无高对比背景的代码片段不够稳定；source/caret 依赖空格和等宽假设，但 Minecraft 默认字体不是代码字体。
- stack 颜色按 `rawLine.contains("Error")` 和 `trim().startsWith("at")` 判断，对非英文/本地化文本无效。
- wrapped detail line 每个都成为 list entry；长 stack 会显著增加 entry 数。
- 详情没有统一 tab stop、word wrap 开关、水平滚动或折叠 frame。
- 路径和 message 各自单行截断/换行没有统一 ellipsis 或 tooltip 契约。
- 顶部菜单仅 14px 高，工具栏 y=38..48，内容 y=55；可用空间非常紧。

### 10.4 推荐 HTML mock 布局

建议以 960x540 或 854x480 logical canvas 为主，另给窄宽版本：

- 顶栏：14-24px，放 NEKOJS 标识、File/Sync 菜单、关闭/全屏。
- 第二行：标题与动作按钮（Copy、Locate、Log；无编辑器时不放 Edit/Save/Sync）。
- 主区：左侧 35%（最小 240px）错误列表；右侧详情。列表 row 36-40px，显示 path、message、count，可选 line；当前真实 UI 只显示前两项。
- 详情：顶部固定 target path + 元信息（只使用 `line/count` 可可靠获得，type 只从 `fullDetails` 弱推）；下面为可滚动、裁剪的预格式化文本。
- 底部/列表上方：搜索、All/Runtime/Syntax/Other 及 counts；全屏时不要把搜索状态当成不存在，应用明确的只读详情 toolbar 表达。
- 交互：点击选择、双击或“Open”显式打开、右键/更多菜单、滚轮、Escape。所有按钮至少 20px 高。
- 视觉：沿用当前颜色种子 `#0A0A0B`、`#121214`、`#18181A`、`#1E1E1E`、`#333333`，类别色 `#E5534B/#F2A134/#748394`。使用 1px 边框和简单 fill，避免只能靠 CSS 实现的复杂玻璃/阴影。
- 文本：不要渲染 Markdown、链接或自动高亮修复；详情按 plain text 保留换行、缩进和 caret。长 content 必须 scroll/crop，不能扩张页面。
- 状态：至少给“有数据/空列表/过滤 0/无源码/长详情/远端不可访问/同步权限拒绝 toast”各一个可视状态。
- 显式禁用：`path` 为 `Unknown location`、virtual path、明显非本地路径或 platform unsupported 时，File actions 禁用并显示原因。

## 11. 研究局限与后续验证边界

- 本轮没有运行游戏或构建，所有 UI 布局与 packet-size 结论来自静态源码、官方文档和 local artifact 检查。
- 没有捕获真实错误日志，因此第 7 节是严格按 6 字段/文本模板构造的代表性 fixture，不是 golden runtime sample。
- `26.2.0-fabric` 本地没有独立源码目录，按 `settings.gradle.kts:3-8` 的 source bridge 事实复用 26.1.2 Fabric 结论；本轮未运行 26.2 Fabric。
- 官方 NeoForge 文档当前显示 26.1，Fabric 文档使用 26.1.2；它们用于核验生命周期、绘制、输入和网络包体，不替代未来 API 的逐版本编译验证。
- 后续真正实现前应新增 packet/dashboard contract fixture，覆盖 DTO 字段、空列表、无源码、长详情、权限拒绝、总包体上限和 Fabric 降级；现有证据文档也明确列出这些缺失：`docs/architecture-refactor/evidence/testing-and-docs.md:114,132-134,153`。