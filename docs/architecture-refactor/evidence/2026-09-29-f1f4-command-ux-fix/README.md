# 票 20 smoke 发现 F1–F4 修复包（2026-09-29）

对 [`2026-09-29-ticket20-smoke`](../2026-09-29-ticket20-smoke/README.md) 记录的 4 项观察级发现
（F1 trust 地址解析 / F2 失败输出异常类名 / F3 裸根无用法 / F4 单文件 reload 路径口径）的
维护者审查修复包：**生产代码 + 测试 + 本证据包**，提交在分支 `ticket-20-command-ux`
（worktree `D:/mcmodDemo/NekoJS-mult-t20fix`，基线 `mult@60e113a3`）。
**合并等待维护者裁决**；四项发现全部按缺陷修复（无判定为预期行为而跳过者）。

## 各发现的处置

### F1 `trust` 地址参数在解析层拒绝合法 host:port —— 已修复（主要）

- **缺陷**：`address` 参数是 `StringArgumentType.string()`，brigadier 未加引号的 word 不允许
  `:`，`nekojs trust 127.0.0.1:25565`（最常见形态）在解析层即报
  `Expected whitespace to end one argument, but found trailing data`，永远到不了分发拒绝分支。
- **修复**：新增共享参数类型 `AddressArgument`（`src/main/java/com/tkisor/nekojs/command/`，
  无 loader 守卫、仅依赖 brigadier，五个节点全部编译）：未加引号时读到下一个空白为止
  （`:` 等字符不再截断）；引号形式走 `reader.readString()`，与
  `StringArgumentType.string()` 语义完全一致（去引号、支持转义与内部空格）；未加引号含空格
  仍是解析错误。三个命令文件（26.x 共享树、fabric 孪生、1.21.1 孪生）全部换用。
- **边界**：只改参数捕获，不动信任语义（`trustServer` → `PackSyncTrustStore` 流程原样，
  票 19 域）。不用 `greedyString()` 的原因：它会连引号一起吞掉带引号形式、且静默接受
  地址内空格，改变既有语义。
- **live 证实**（`transcripts/01`）：未加引号 `127.0.0.1:25565` 现在到达分发拒绝
  `Run /nekojs trust on a client process...`；port-less 与加引号形式响应不变。

### F2 单文件 reload 失败输出内嵌异常类名 —— 已修复（连带同因两处）

- **缺陷**：`reload failed: ... error=java.io.IOException: Unsupported or missing script file: ...`
  —— 用户面文本携带 Throwable `toString()`（FQCN）。
- **修复**：`RuntimeCommandResultFormatter.errorMessage(Throwable)` 只取 message；message 缺失时
  回退 `getSimpleName()`（保证失败行不空原因，且仍是简名非 FQCN）。替换三处同因嵌入：
  `reloadResult` 失败分支（smoke 实测路径）、`postReloadFailure`、`ReloadFailureReport.describe()`
  （候选失败路径，同类缺陷一并修）。完整类名与堆栈仍只进日志（调用方 `LOGGER.error(..., e)`
  未动）。
- **live 证实**（`transcripts/01`）：`error=Unsupported or missing script file: no_such_file.js`，
  无 `java.io.IOException:` 前缀；其余结构化字段（type/generation/phase/source/owner + 后果句）
  原样。

### F3 裸 `/nekojs` 报 Unknown or incomplete command —— 已修复

- **缺陷**：根 literal 是纯命名空间节点（无 executes），直接执行报 brigadier 的
  "Unknown or incomplete command"。
- **修复**：根 literal 增加 `.executes(sendRootUsage)`：从**已注册的活命令树**推导顶层子命令
  列表（`reload|test|error|view_all_errors|packs|registry|hand|inventory|trust|probe`，按声明序），
  以 `Component.translatable("nekojs.command.usage", <子命令列表>)` 输出并附 `/help nekojs` 指引，
  返回 1。三个命令文件同一实现；因从树推导，1.21.1/fabric 自动列出各自子集，无漂移。
- **本地化**：新增 lang 键 `nekojs.command.usage`（en_us + zh_cn，与邻近 `nekojs.command.*`
  键同机制同位置）；子命令名是标识符，按翻译规则不译。
- **live 证实**（`transcripts/01`）：`Usage: /nekojs <reload|test|error|view_all_errors|packs|registry|hand|inventory|trust|probe>. Run /help nekojs for the full command list.`

### F4 单文件 reload 路径口径不一致 —— 已修复（选了对齐口径的小改）

- **缺陷**：裸文件名（`t20-single.js`）可用，`server_scripts/t20-single.js`（物理目录形态，
  用户直觉路径）被解析成 `<scripts>/server_scripts/<file>` 而拒绝。
- **修复**：`ScriptManager.resolveScriptPath` 在既有 `server/`（id 风格）前缀剥离后，增加
  脚本目录名前缀剥离（`ScriptTypeEnv.scriptsDir(type).getFileName()`，即 `server_scripts/` 等，
  从目录派生不硬编码）。接受的等价形式变为：裸相对路径、`<type>/<file>`、
  `<type>_scripts/<file>`。目录穿越（`..`/绝对路径）与越根检查原样。建议器（返回
  scripts-dir 相对形式）与可用口径本就一致，未动。
- **live 证实**（`transcripts/01`）：`reload server server_scripts/t20f1.js` 与
  `reload server t20f1.js` 均成功（`phase=FILE source=... - no errors.`），标记脚本各重跑一次
  （`transcripts/02` 两条 `T20F1-OK`）。
- **备注**：wiki `命令.md` 的 reload `<file>` 行（"路径相对该脚本类型根目录"）仍然为真，
  新形式是加法；是否在 wiki 补一句三种等价写法留给维护者（避免本包扩大到翻译同步面）。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/java/com/tkisor/nekojs/command/AddressArgument.java` | **新增**（F1）：brigadier-only 地址参数类型，五节点共享 |
| `src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java` | F1 换 `AddressArgument`；F3 根 executes + `sendRootUsage` |
| `versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java` | 同上（1.21.1 孪生；无 registry/hand/inventory 字面量） |
| `src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java` | 同上（fabric 孪生，literal 集 = 26.x） |
| `common/src/main/java/com/tkisor/nekojs/core/lifecycle/RuntimeCommandResultFormatter.java` | F2：`errorMessage` 助手 + 两处嵌入改消息-only |
| `common/src/main/java/com/tkisor/nekojs/core/lifecycle/ReloadFailureReport.java` | F2：`describe()` 的 `error=` 同因修复 |
| `common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java` | F4：`resolveScriptPath` 接受脚本目录名前缀 |
| `common/src/main/resources/assets/nekojs/lang/en_us.json` / `zh_cn.json` | F3：`nekojs.command.usage` 键 |

测试（每项修复的最小断言）：

| 测试 | 覆盖 | 结果 |
|---|---|---|
| `src/test/.../command/AddressArgumentTest.java`（新增，纯 brigadier，无 MC 类） | F1 解析契约：未引号 host:port 到达 executor、port-less 不变、引号语义保持（去引号/内部空格）、未引号含空格仍报错 | 4/4 通过（26.1.2 与 1.21.1 实跑） |
| `src/test/.../command/NekoJSCommandsTrustArgumentAndUsageTest.java`（新增，neoforge 守卫） | 真实 `register()` 后的树形：trust 参数节点类型为 `AddressArgument`、根节点有 executes | 1.21.1 与 26.1.2 全量套件内实跑通过；26.1.2 单独跑会因 `Commands` 类初始化需 vanilla bootstrap 而跳过（既有 `NekoJSCommandsEditorRemovalTest` 同墙，套件内其它测试先 bootstrap 后即可实跑） |
| `src/test/.../command/RuntimeCommandLifecycleSourceTraceTest.java`（扩展） | F1/F3 三孪生镜像断言（fabric 树无法 null-fixture 构造，以 source-trace 覆盖）+ `AddressArgument` 保持 brigadier-only | 通过 |
| `common/src/test/.../lifecycle/RuntimeCommandResultFormatterTest.java`（扩展） | F2：单文件失败消息-only、post-commit 失败消息-only、message 缺失回退简类名、`ReloadFailureReport.describe()` 同口径；**有意更新**一处旧断言（原断言类名在列） | 通过 |
| `common/src/test/.../script/ScriptReloadRegressionTest.java`（扩展） | F4：三种等价路径形式均重跑入口 + 缺失文件稳定失败消息 | 通过 |

## runServer spot-check（一次性、有界）

- 命令：`./gradlew.bat :26.1.2:runServer --console=plain`（本分支代码）；`Done (4.506s)`，
  RCON `stop` 干净停服，`BUILD SUCCESSFUL in 57s`，exit 0。
- 布局：`versions/26.1.2/run/`（RCON 127.0.0.1:25892、server-port 25891、online-mode off、
  level-seed=t20f1fix）+ 标记脚本 `nekojs/server_scripts/t20f1.js`（输出 `T20F1-OK`）；
  驱动为最小 Python RCON 客户端（同 smoke round 2，源码嵌入转录），截取后 run 目录已删除
  （T08/T24 先例）。
- 会话覆盖：F3 裸根用法、F1 三种地址形式、F4 嵌套/裸两种路径、F2 缺失文件失败文本。
  逐字响应见 `transcripts/01`；服务端日志节选（标记行、Server thread 上的单文件 reload、
  停服）见 `transcripts/02`。

## Golden

仓库无命令树 golden（golden 目录为 events-declared d.ts / capability-matrix / api-manifest /
block-events-api，均不覆盖 `/nekojs` 树），故无 golden diff。命令树形状变化（F1 参数类型、
F3 根 executes）由树形测试 + source-trace 测试 + live spot-check 三面固定。

## 验证

| 命令 | 结果 |
|---|---|
| `./gradlew.bat :26.1.2:test` | **通过**（95 个套件全绿，含新增/扩展测试；套件内 EditorRemoval 与新树形测试均实跑通过） |
| `./gradlew.bat :1.21.1:test` | **通过**（孪生变更节点；新树形测试实跑通过） |
| `./gradlew.bat :26.1.2-fabric:test` | **通过**（fabric 孪生变更节点） |
| `./gradlew.bat :common:check` | **通过**（common 触及：formatter / ScriptManager / lang） |

已知既有状态（非本包引入，如实记录）：`NekoJSCommandsEditorRemovalTest` 在 26.1.2 裸 JVM
**单独**运行时因 `Commands` 类初始化需要 vanilla bootstrap（依赖 FML）而失败——基线
`60e113a3` 上单跑同样失败（已用 stash 验证）；全量套件内因其它测试先行 bootstrap 而通过。
本包新增树形测试用 `Class.forName("net.minecraft.commands.Commands")` 探针守卫该墙：
可初始化则实跑，否则诚实跳过。

## 剩余缺口（留维护者/后续轮）

- 权限拒绝 UX（`.requires` 隐藏式拒绝无显式消息）：票 34 真机轮，无头不可测（AC1 既有结论）。
- wiki `命令.md` 是否补记 trust 地址可用未引号 host:port、reload `<file>` 三种等价路径写法：
  一行文档事，留维护者裁决（避免本包触发翻译同步面）。
- `server/server_scripts/foo.js` 双前缀叠加等病态写法未专门支持（各剥离一次，行为确定）。

## 2026-09-29 post-merge addendum: F1 introduced a client-sync regression (fixed by hotfix)

The pack's F1 `AddressArgument` shipped without argument-type serialization registration.
RCON dispatch never serializes the command tree, so this pack's verification legs (RCON
spot-check + JVM tests) could not see it; the 2026-09-29 ticket-36 maintainer trial found
that every real client world entry failed during the configuration phase with
`IllegalArgumentException: Unrecognized argument type com.tkisor.nekojs.command.AddressArgument`
(`Couldn't place player in world`, world entry blocked). Fixed on branch `hotfix-address-arg`
by registering the argument type for client sync on all five nodes — NeoForge via a
`COMMAND_ARGUMENT_TYPE` `DeferredRegister` paired with `ArgumentTypeInfos.registerByClass`
(shared `NekoJSArgumentTypes`), fabric via `ArgumentTypeRegistry.registerArgumentType` (the
fabric path is the same vanilla serialization; it was equally affected). Root cause, fix,
verification, and the machine-verification boundary (real-client re-trial handed back to the
maintainer) are recorded in
[`../2026-09-29-address-arg-registration-hotfix/README.md`](../2026-09-29-address-arg-registration-hotfix/README.md).
Lesson for future command packs: any change to command-tree argument types requires a
client-sync leg — console/RCON dispatch alone is structurally blind to serialization
failures.
