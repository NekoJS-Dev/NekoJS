# Ticket 20 无头 live 命令 smoke（2026-09-29）

票据 20（`/nekojs` 管理命令权限、生命周期入口与阶段诊断结果，in-review）7 项未勾 AC 全部卡在
"live command smoke"。本目录是对 **主 NeoForge 节点 26.1.2 专用服务器** 的一次无头
（无客户端）live 命令 smoke 证据：真实 `runServer` 会话内经 RCON 以 console 身份遍历整个
`/nekojs` 命令树，记录每条响应，并核对响应与实现意图的一致性。

- **性质**：仅证据收集。未改任何生产/测试代码，未触碰票据文件、golden 与基线产物。
- **工作区**：worktree `D:/mcmodDemo/NekoJS-mult-t20e`，分支 `ticket-20-smoke`，基线 `mult@197853fd`。
- **对应摘要建议**（`evidence/2026-09-29-inreview-digest/README.md` 票 20 节）："主树无 baseline
  归档，建议补一份 command-output 证据目录"——本目录即该补充。

## 运行环境与命令

| 项 | 值 |
|---|---|
| 节点 | `:26.1.2`（NeoForge，主节点） |
| 命令 | `./gradlew.bat :26.1.2:runServer --console=plain`（两次会话，均 BUILD SUCCESSFUL、exit 0、RCON `stop` 干净停服：run1 3m29s / run2 1m3s） |
| 布局 | `versions/26.1.2/run/`（`eula.txt` + `server.properties`：RCON 127.0.0.1:25892、server-port 25891、online-mode off、level-seed=t20smoke、pause-when-empty-seconds=-1）；run 目录在转录截取后整体删除（T08/T24 先例） |
| 脚本 | run 目录内 `nekojs/server_scripts/`：`t20-good.js`（成功标记 `T20-SMOKE-OK`）、`t20-bad.js`（蓄意 `ReferenceError`，制造 error tracker 记录；phase C 由驱动删除）、`t20-single.js`（单文件 reload 目标）。全文嵌入 `command-output/01` |
| 驱动 | 最小 Python RCON 客户端（改自 `baseline/2026-09-22-gameplay-event-surface/command-output/08-runserver-26.1.2-smoke.txt` 内嵌驱动），等 `Done (` 后驱动全树、删除坏脚本、reload 复查、`stop`。驱动源码逐字嵌入两份转录 |

## 命令树（源码枚举，`src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java`，`>=26` 双 NeoForge 节点）

`/nekojs`（根，`LEVEL_GAMEMASTERS` 门）下实际存在的全部节点：

| 节点 | 参数 | 行为要点 |
|---|---|---|
| `reload` | 无参默认 SERVER；`startup|server|client|test` 各带可选 `<file>`（greedyString + 建议器） | STARTUP 运行时拒绝；CLIENT 专用服务器拒绝；TEST 转 `runTests`；SERVER 走 root reload + recipe/pack 后处理 |
| `test` | — | `root.runTests()`，未配置稳定报错 |
| `error` | — | root ErrorSnapshot 计数；>0 出 tracker 警告 + 可点击打开错误列表 |
| `view_all_errors` | — | 玩家专用（`getPlayerOrException` + `ShowErrorListPacket`） |
| `packs` | `enable <id>` / `disable <id>` | 列包/写包状态文件 |
| `registry` | `stale` | 动态注册健康快照（每表计数 + stale 残留）；`registry`/`stale`/`hand`/`inventory` 为 26.x 独有（见下） |
| `hand` / `inventory` | — | 玩家专用诊断 |
| `trust <address>` | `StringArgumentType.string()` | 仅客户端进程生效；专用服务器稳定拒绝 |
| `probe` | 无参默认 typescript；`all|list|reload|reset_config|enable|disable`；`<language>` [+ `<name>`] | probe 后端生成 |

**节点间差异（源码事实）**：`registry`/`stale`/`hand`/`inventory` 四个 literal 只在 `>=26`
NeoForge 版本树存在；`versions/1.21.1/.../NekoJSCommands.java` 无这四个节点（1.21.1 树 =
reload/test/error/view_all_errors/packs/trust/probe）。Fabric
（`src/fabric/.../FabricNekoJSCommands.java`）literal 集与 26.x NeoForge 完全一致。

## 权限面（源码事实 + 无头边界）

- 26.x NeoForge（`NekoJSCommands.java:56`）与 Fabric（`FabricNekoJSCommands.java:68`）：
  **唯一根级** `.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))`
  （permission level ≥ 2），整棵子树继承，无任何子节点级 requires。
- 1.21.1 双胞胎（`NekoJSCommands.java:51`）：`.requires(source -> source.hasPermission(2))`——
  1.21.1 API 下的等价 level-2 门。
- **行为后果**：低于 level 2 的发送者得到的是 brigadier 的"隐藏式拒绝"（命令不出现在补全、
  dispatch 报 `Unknown or incomplete command`），**没有任何显式拒绝消息**——与票据 AC1 记录的
  gap（"`.requires` hides the command without an explicit denial message"）一致，本次从源码再次确认。
- **无头边界（如实记录，不伪造）**：RCON console 发送者权限为 level 4，永远通过该门，因此
  **权限拒绝 UX 无法在无头会话中测试**。需要真实未 OP 玩家（owner：票 34 真机轮）。

## 每命令结果（完整逐字响应见 `command-output/01`、`02`）

| 命令 | 观测响应（摘要） | 判定 |
|---|---|---|
| `help nekojs` | 列出 10 行用法：`reload [startup\|server\|client\|test]`、`test`、`error`、`view_all_errors`、`packs [enable\|disable]`、`registry [stale]`、`hand`、`inventory`、`trust <address>`、`probe [all\|list\|reload\|reset_config\|enable\|disable\|<language>]` | 与源码树一致（usage 行不显示 `<file>`/`<name>` 参数，brigadier usage 渲染惯例，观察项） |
| `nekojs`（裸） | `Unknown or incomplete command` | 根 literal 无 executes（纯命名空间节点），观察项 F3 |
| `nekojs error`（有 1 个蓄意错误） | `§c[NekoJS] ⚠ Warning: The engine currently has 1 active script error(s).` + 打开 Error Dashboard 链接行 | 符合（en_us lang 在 RCON 通道正确解析） |
| `nekojs view_all_errors`（console） | `A player is required to run this command here` | 符合（玩家专用，`getPlayerOrException` 的 vanilla 拒绝） |
| `nekojs reload` | `NekoJS server reload committed: generation=2 phase=COMMIT (1 error(s) remain)` | 符合：报告 generation/phase + 遗留错误数 |
| `nekojs reload server` | 同上，generation=3 | 符合 |
| `nekojs reload server t20-single.js` | `NekoJS server script reload completed: generation=3 phase=FILE source=t20-single.js (1 error(s) remain)` | 符合（FILE 阶段 + source） |
| `nekojs reload server server_scripts/t20-single.js` | `reload failed: type=server generation=3 phase=FILE source=server_scripts\t20-single.js owner=ScriptManager[server] error=java.io.IOException: Unsupported or missing script file: ...; active runtime was not switched; use a full reload to reconcile it.` | 结构化失败输出符合；但嵌套路径被拒 + 异常类名入列 → 发现 F1/F2 |
| `nekojs reload startup`（及带 file） | `STARTUP scripts cannot be reloaded from a runtime command; restart the game/loader.` | 符合（票据交付记录声称的 STARTUP 双 loader 拒绝，NeoForge 侧 live 证实） |
| `nekojs reload client`（及带 file） | `Client script reload is only available in an integrated client runtime.` | 符合（专用服务器上 CLIENT 分发稳定拒绝，AC5 腿） |
| `nekojs reload test` / `nekojs test` | `NekoJS TEST scripts are not configured.` | 符合（TEST 未配置稳定错误，AC5 腿；专用服务器不创建 TEST manager） |
| `nekojs registry` | `sound_event: 0 registered, 0 stale` / `mob_effect: ...` / `item: ...` | 符合（三张动态注册表快照，空脚本运行 0 条属预期） |
| `nekojs registry stale` | `No stale dynamic registry entries.` | 符合 |
| `nekojs packs` | `No script packs found (looked in nekojs/packs/ and <world>/nekojs_packs/).` | 符合（AC7 面，无包场景） |
| `nekojs packs enable/disable no_such_pack` | `No script pack with id 'no_such_pack'. Use /nekojs packs to list.` | 符合（未知 id 稳定失败 + 提示） |
| `nekojs hand` / `nekojs inventory`（console） | `A player is required to run this command here` | 符合（玩家专用） |
| `nekojs trust 127.0.0.1` / `trust "127.0.0.1:25565"` | `Run /nekojs trust on a client process (e.g. in a singleplayer world).` | 符合（专用服务器 wrong-distribution 稳定拒绝，AC5 腿 live 证实） |
| `nekojs trust 127.0.0.1:25565`（未加引号） | `Expected whitespace to end one argument, but found trailing data` | 发现 F1：解析层拒绝，到达不了分发拒绝消息 |
| `nekojs probe list` | `typescript:builtin`、`python:builtin` | 符合 |
| `nekojs probe`（默认） | `Probe generated: 390 files in 1355ms`，输出目录 `.neko_probe\typescript` | 符合 |
| `nekojs probe typescript` / `probe all` | 390 / 769 文件（python+typescript） | 符合 |
| `nekojs probe typescript builtin` / `probe python builtin` | 命名后端选择生效 | 符合 |
| `nekojs probe cobol` / `probe typescript no_such_backend` | `No probe backend matched. Use /nekojs probe list.` | 符合（无匹配稳定失败） |
| `nekojs probe reload` | `Probe config (probe.toml) reloaded.` | 符合 |
| `nekojs probe enable` / `disable` | `Probe enabled.` / `Probe disabled.` | 符合 |
| `nekojs probe`（disable 后） | `backend failed: probe disabled in probe.toml` | 符合（禁用态稳定失败，重 enable 后恢复） |
| `nekojs probe reset_config` | `Editor configs reset (2 backend(s)); regenerating probe...` + 390 文件重生成 | 符合 |
| `nekojs reload server no_such_file.js` | `reload failed: type=server generation=1 phase=FILE source=no_such_file.js owner=ScriptManager[server] error=java.io.IOException: ...; active runtime was not switched; ...` | 结构化失败符合；异常类名入列 → F2 |
| phase C：删除 `t20-bad.js` 后 `reload server` | `NekoJS server reload committed: generation=4 phase=COMMIT - no errors.` 且 `nekojs error` → `§a[NekoJS] ✔ ... healthy...` | 符合：修复后 reload 提交新 generation、错误清零、健康消息（AC3 成功/失败双态 + 恢复 live 证实） |

服务器日志侧（`command-output/03`）：蓄意错误每次 reload 都产生结构化
`script-diagnostic ... phase=RESOLVE_LINK/EXECUTION ... generation=N candidate=true source=...`
记录；命令触发的 reload 在 **Server thread** 上执行（boot 首载在 Worker-Main，符合所有权模型）；
每 generation 两条 `T20-SMOKE-OK` 成功标记；两次会话均干净停服。

## 发现（记录，未修复——生产代码不在本轮授权范围）

- **F1 `trust` 地址参数解析拒绝合法地址形态**：`address` 是 `StringArgumentType.string()`，
  brigadier 未加引号的 unquoted string 不允许 `:`，因此 `nekojs trust 127.0.0.1:25565`（最常见
  的 host:port 形态）在解析层即被拒（"Expected whitespace to end one argument..."），永远到不了
  分发拒绝分支。带引号或无端口形态可到达。可用性缺陷候选，留维护者裁决。
- **F2 单文件 reload 失败输出携带原始异常 toString**：失败行内嵌
  `error=java.io.IOException: Unsupported or missing script file: ...`。输出其余部分结构化
  （type/generation/phase/source/owner + 后果句），无堆栈，符合 AC3 "不把 Throwable 栈当用户契约"
  的字面要求；但异常类名进入用户面文本，边界情况留维护者判断。
- **F3 裸 `nekojs` 无 executes**：根 literal 是纯命名空间节点，直接执行报 brigadier 的
  "Unknown or incomplete command" 而非用法提示。观察项。
- **F4 单文件 reload 路径口径不一致**：裸文件名（`t20-single.js`）可用，`server_scripts/t20-single.js`
  嵌套形态被拒（与 F2 同一条失败输出）。建议器（`ScriptLocator.suggestScriptFiles`）返回裸名形态，
  与可用口径一致；不一致的是用户直觉路径。观察项。
- 附带观察：服务器 console 日志以 zh_cn 渲染 lang，RCON 通道以 en_us 渲染（同一 translatable
  组件）。仅为语言环境差异，非缺陷。

## 与票据未勾 AC 的对应（本轮补到的 live 腿）

| AC | 本轮覆盖 | 剩余 |
|---|---|---|
| AC1 权限拒绝 | 源码确认 `.requires` 隐藏式拒绝现状（三处源文件） | **未 OP 真人拒绝 UX**（RCON console=level 4 永过门；票 34 真机轮） |
| AC2 唯一 root 入口/owner 队列 | 命令 reload 确在 Server thread 执行并返回 root 结果对象 | owner 队列内部调度仍靠 source-trace 测试；无头不可见 |
| AC3 成功/失败输出 | 全覆盖：成功（generation/phase）、FILE 阶段、结构化失败、错误计数并入、修复后恢复 | recipe/pack 后处理失败的 post-reload 输出未注入（需构造 recipe 错误脚本） |
| AC4 watchdog 隔离后 reload | 未覆盖 | 需制造 active watchdog 隔离（且 `bench/smoke-reload` 注记时间窗路径对 `while(true)` 无效），留真机/后续轮 |
| AC5 边界稳定错误 | TEST 未配置、CLIENT-on-dedicated、trust wrong-distribution 三条 live 证实 | SERVER 命令在真实客户端侧的分支需真客户端 |
| AC6 错误命令展示 | `error` 计数 + 链接行、console 拒绝面 live 证实 | `view_all_errors` packet / Error Dashboard UI 需真实玩家 |
| AC8 删除旧助手 | 代码面已由 source-trace 覆盖；本轮无新增证据 | 现场观察删除 parity 需对照运行时（非命令面） |

## 产物

- `command-output/01-rcon-session-full-tree.txt` — run 1 全树 RCON 会话（含驱动与 run 目录脚本源码全文）
- `command-output/02-rcon-session-gap-round.txt` — run 2 补缺会话（trust 三形态、命名 probe 后端、不存在文件 reload）
- `command-output/03-server-log-excerpt.txt` — 两次会话的服务器日志节选（诊断行、标记、生命周期）
- run 目录（`versions/26.1.2/run/`）在转录截取后已删除；驱动与完整 gradle 日志留在仓库外临时目录

## 结论

26.1.2 NeoForge 专用服务器上 `/nekojs` 整棵命令树的 live 行为与实现意图一致：reload 报告
generation/phase/遗留错误数，蓄意脚本错误经 error tracker 进入 `error` 命令且修复后干净恢复，
STARTUP/CLIENT/TEST/信任分发四类边界拒绝全部给出稳定单行错误，probe/packs/registry 诊断面
输出正确。未发现命令层错误行为；记录 4 项观察级发现（F1 trust 地址解析最有裁决价值）。
权限拒绝 UX 与全部玩家/客户端分支留票 34 真机轮。
