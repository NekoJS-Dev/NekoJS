# Ticket 20 AC4 live-watchdog smoke（2026-09-29）

票据 20（`/nekojs` 管理命令，in-review）AC4 唯一缺失的腿是 live 侧：真实服务器上失控脚本触发
watchdog 隔离后，`nekojs reload` 经 RCON 显式创建 candidate。前一轮 smoke
（`evidence/2026-09-29-ticket20-smoke/`，命令树遍历）在其 AC 映射表中把 AC4 标为
「未覆盖……留真机/后续轮」。本目录即该腿的证据：**一个真实 26.1.2 专用服会话**内完成
完整标记链——active 服务 → 失控候选被 watchdog 终止（active 保留）→ 单文件 reload 在
active 上下文上求值 → **active 被 watchdog 隔离** → 显式 reload 建 candidate → candidate
再次失败且隔离保持、无自动二次恢复 → 移除失控脚本后显式 reload 提交恢复。

- **性质**：仅证据收集。未改任何生产/测试代码，未触碰票据文件、golden 与基线产物。
- **工作区**：worktree `D:/mcmodDemo/NekoJS-mult-t20w`，分支 `ticket-20-watchdog-smoke`，基线 `mult@225344f8`。
- **JVM 语义侧**（`Ticket07RuntimeThreadsTest`，common check 全绿）不在本轮重复；本轮只补 live 腿。

## 运行环境与命令

| 项 | 值 |
|---|---|
| 节点 | `:26.1.2`（NeoForge，主节点） |
| 命令 | `./gradlew.bat :26.1.2:runServer --console=plain`（BUILD SUCCESSFUL in 4m 5s，exit 0，RCON `stop` 干净停服） |
| 布局 | `versions/26.1.2/run/`（`eula.txt` + `server.properties`：RCON 127.0.0.1:25902 pw t20w、server-port 25901、online-mode off、level-seed=t20w、pause-when-empty-seconds=-1，避开兄弟工作树端口）；run 目录在转录截取后整体删除（T08/T24/上轮 smoke 先例） |
| watchdog 源 | run 目录 `nekojs/config/engine.toml`：`scriptRunawayTimeoutSeconds = 3`（票 07 墙钟守卫 `SyncEvalWatchdog`，每次脚本入口求值 arm）；`scriptStatementLimit = 0` 关闭语句预算路径，使 kill 归因唯一落在失控 watchdog。启动日志 `Engine config loaded`（L21228）证实 run 目录配置被加载，三次 kill 的 `Can't keep up! ... 2960~3141ms` 侧面印证 3s 墙钟 |
| 脚本 | `nekojs/server_scripts/t20w-good.js` 开机即在（load 标记 + 5s 周期 `T20W-ALIVE` 存活标记，作为「active 仍在服务 / 分发已停」的观测面）；`t20w-runaway.js`（`while (true) {}`）由驱动在会话中段写入、恢复前删除。模组自动脚手架的 `src/main.js`（"Hello, World!" 示例）也在发现集内（发现数 2→3→2） |
| 驱动 | 最小 Python RCON 客户端（改自上轮 smoke 的内嵌驱动），等 `Done (` 后按相位驱动全链、中途写入/删除失控脚本、观察窗、`stop`。源码与逐字会话嵌入 `command-output/01` |

## 机制前提（源码事实，决定观测点）

- 完整 reload（`nekojs reload server`）走事务式候选：`reloadScriptsTransactional` 建 candidate
  generation，`loadCandidateScripts` 逐脚本执行；watchdog/interrupt 终止候选 →
  `markContextKilled` 命中 `candidateContext` 分支 → `reloadFailure(EXECUTION, domain=candidate-killed)` →
  `discardCandidate`（候选资源全关），active 的 runtime/监听器/timer/generation 原样。
- 单文件 reload（`nekojs reload server <file>`）在 **active 上下文**上求值
  （`reloadScriptFile → getOrCreateContext`）：失控脚本被 watchdog 终止时 kill 落在 active →
  `contextKilled + lifecycleGate.markActiveFailed()`（隔离失败）。此后 `NekoRuntimeRoot.reloadFile`
  的入口检查拒绝一切 FILE reload（"Only a full candidate reload may recover"），唯一恢复入口是
  显式完整 reload 的 candidate commit（`commitGeneration` 成功才 `clearActiveFailed`）。
- timer/事件分发从不重建环境（`isContextDead` 对被杀 active 静默跳过回调）——因此
  `T20W-ALIVE` 标记停摆即「隔离、不再分发」的 live 可观测信号。

## 相位链与逐相证据（RCON 逐字响应见 `command-output/01`，日志行见 `command-output/02`）

| 相位 | RCON 观测（摘要） | 服务器日志标记 |
|---|---|---|
| P0 boot | —（无命令） | gen 1 active：发现 2 脚本、`T20W-GOOD loaded`（Worker-Main-11）；`T20W-ALIVE` 每 5s（11:21:01 起） |
| P1 sanity | `nekojs error` → healthy；`reload server t20w-good.js` → `completed: generation=1 phase=FILE ... - no errors.` | FILE 重载完成；标记继续 |
| P2 候选终止腿 | `reload failed: type=server generation=2 phase=EXECUTION source=server/t20w-runaway.js owner=ScriptManager[server] domain=candidate-killed ...; candidate was discarded and the active generation remains unchanged.` | 候选执行 good→runaway，3s 后 `脚本执行失败 ... script-diagnostic ... generation=2 candidate=true`、`SERVER 脚本事务重载失败，候选 generation 已关闭，active 环境保持不变`；**`T20W-ALIVE` 继续**（11:22:28–:42） |
| P3 第二次显式候选 | 同 P2 逐字失败，`generation=2` 不变 | 同样 `candidate=true` kill；标记继续（11:22:48–11:23:02）；两轮之间无任何自动重试 |
| P4 active 隔离腿 | `reload server t20w-runaway.js` → 命令面报 `NekoJS server script reload completed: generation=1 phase=FILE source=t20w-runaway.js (1 error(s) remain)`（见发现 W1） | `正在重载 SERVER 脚本文件 ... 受影响入口 1 个` → runaway 在 **active** 上下文求值 → 3s 中断：`script-diagnostic ... generation=1 candidate=false`（active-kill 词汇，对照 P2/P3 的 `candidate=true`）→ **此后 `T20W-ALIVE` 全停** |
| P5 隔离观测 | `nekojs error` → `1 active script error(s)`；`reload server t20w-good.js` → `reload failed: type=server generation=1 phase=PREPARATION source=t20w-good.js owner=ScriptManager[server]; active generation remains isolated; explicit full reload is required.` | （拒绝发生在 root 层，服务器日志无对应行） |
| **P6 AC4 核心** | `nekojs reload server` → `reload failed: type=server generation=2 phase=EXECUTION source=server/t20w-runaway.js ... domain=candidate-killed ...; active generation remains isolated; explicit full reload is required.`；2 秒后再试 FILE reload 仍被拒（同 P5 措辞） | 显式 candidate：发现 3 脚本 → good 在候选上执行（`T20W-GOOD loaded`）→ runaway 再度 `candidate=true` kill → `事务重载失败 ... active 环境保持不变`；**隔离保持**（失败行的 isolation 后缀 + 后续 FILE 拒绝双证） |
| 无自动恢复窗 | —（驱动观察窗） | 11:23:30 → 11:23:59 **零 NekoJS 运行行**：无 timer 标记、无重建、无自动第二候选（P4→P6 之间 11:23:06→11:23:26 同样为零） |
| P7 显式恢复 | 删除 runaway 后 `reload server` → `NekoJS server reload committed: generation=2 phase=COMMIT - no errors.`；`nekojs error` → healthy | 发现 2 脚本、good 提交、`SERVER 脚本重载完毕`;`T20W-ALIVE` 于 11:24:04 恢复 |

## AC4 判定

AC4 原文：「active watchdog 隔离后的 reload 命令尝试显式创建 candidate；candidate 失败时仍保持
隔离/旧 active 状态，不自动二次恢复。」逐分句对照 live 证据：

1. **active watchdog 隔离后** — P4：单文件 reload 在 active 上下文求值失控脚本，
   `script-diagnostic generation=1 candidate=false` + 存活标记停摆 + P5 的 FILE 拒绝
   （"active generation remains isolated"）三重证实 active 已被隔离。
2. **reload 命令尝试显式创建 candidate** — P6：隔离后的 `nekojs reload server` 逐阶段日志
   （发现 3 脚本 → good 在候选上执行 → runaway `candidate=true`）证明命令显式建了新候选
   （generation=2），且只在命令触发时发生。
3. **candidate 失败时仍保持隔离/旧 active 状态** — P6 失败输出携带
   `; active generation remains isolated; explicit full reload is required.`（formatter 的
   activeIsolated 分支，`root.isActiveFailed` 在失败后仍为 true），2 秒后的 FILE reload 仍被
   PREPARATION 拒绝，双证隔离未清除、未被死候选替换。
4. **不自动二次恢复** — P6 失败后 29s（及 P4→P6 间 20s）日志零运行行；P2/P3 腿同时证明
   候选失败后旧 active 继续服务、第二次候选只在下一次显式命令时出现。
5. **显式恢复可用**（AC 的恢复面）— P7：移除失控源后 reload 提交 generation=2、标记恢复。

**结论：AC4 的 live 腿成立，与 JVM 侧 `Ticket07RuntimeThreadsTest` 语义一致；AC4 现已可勾**
（票据文件按任务约束本轮不动，勾选留维护者/主会话）。

## 发现（记录，未修复——生产代码不在本轮授权范围）

- **W1 单文件 reload 杀死 active 后命令面仍报成功**：P4 的 RCON 响应是
  `NekoJS server script reload completed: generation=1 phase=FILE source=t20w-runaway.js (1 error(s) remain)`。
  机制：`ScriptExecutor.executeEntry` 吞掉 kill 异常（仅记错误面板 + 错误日志），
  `doReloadScriptFile` 照常走到 `重载完毕` 并正常返回，`NekoRuntimeRoot.reloadFile` 只在**入口**
  检查 `isActiveFailed`（L226），**出口**没有复查——于是「杀掉了 active」被当作 FILE 成功上报
  （仅以遗留错误数提示）。状态机本身正确（隔离真实生效、可经 `error`/下一次 FILE 拒绝发现），
  但用户面措辞与实际后果不符。建议维护者裁决：出口补一次 `isActiveFailed` 复查并降级为
  失败输出，或接受现状。severity：用户面文案；无状态破坏。
- **W2（观察项）**：watchdog 的 3s 失控自旋阻塞 server thread（`Can't keep up! ... 62 ticks
  behind` ×3）——候选/FILE 求值本就在 owner（server）线程上同步执行，与所有权模型一致，
  非缺陷。
- **W3（观察项）**：模组在空 `server_scripts` 下自动脚手架 `src/main.js` 示例脚本（发现数
  2/3/2 的基数来源），仅影响发现计数解读。

## 产物

- `command-output/01-rcon-session-watchdog-chain.txt` — 全链 RCON 会话（含 run 目录脚本/配置源文与驱动源码全文）
- `command-output/02-server-log-excerpt.txt` — 逐相位服务器日志节选（含两个「零运行行」静默证明窗；栈帧按段省略，完整 gradle 日志留在仓库外临时目录）
- run 目录（`versions/26.1.2/run/`）在转录截取后已删除

## 结论

26.1.2 专用服上 AC4 的 live 链路逐相成立：watchdog 3s 墙钟真实终止候选与 active
（`candidate=true/false` 词汇与票 07 实现一致），隔离后的 reload 显式建 candidate，候选失败
隔离保持、无自动二次恢复，显式 reload 恢复。命令/运行时状态机未发现违背 AC 的行为；
1 项用户面文案发现（W1）留维护者裁决。

## W1 修复（2026-09-29，维护者已批准，同日落地）

维护者批准后，W1 在 worktree `D:/mcmodDemo/NekoJS-mult-t20w1`（分支 `ticket-20-w1-wording`，
基线 `mult@236f7c16`）落地：

- **机制**：`NekoRuntimeRoot.reloadFile` 在 `reloadScriptFile` 返回后**出口**复查
  `manager.isActiveFailed()`。入口检查已证明 reload 开始时 active 健康，因此此处
  失败即「本次 reload 的求值杀死了 active」——`ScriptExecutor.executeEntry` 吞掉 kill
  异常（仅记错误面板 + 错误日志，无异常冒泡），状态机（隔离真实生效）不变，仅把结果
  从 `successFile` 降级为携带 FILE 阶段与 source 的失败。错误消息
  `script evaluation was terminated by the watchdog and the active generation is isolated`
  遵循已合并的 F2 规则（消息进命令面，类名只留日志）。
- **JVM 侧**：`NekoRuntimeRootReloadResultTest.fileReloadThatWatchdogKillsTheActiveReportsIsolationFailure`
  （red→green 验证：无修复时以 W1 精确签名失败——`a reload whose evaluation killed the
  active must not report success (W1) ==> expected: false but was: true`；修复后 4/4 通过）。
  同时断言 P5 既有隔离拒绝措辞不变、真实成功措辞不变。`:common:check` 全绿。
- **live 侧**（`command-output/03-rcon-session-w1-wording.txt`，端口 25903/25904）：
  P4 腿 RCON 响应由
  `NekoJS server script reload completed: generation=1 phase=FILE source=t20w-runaway.js (1 error(s) remain)`
  变为
  `reload failed: type=server generation=1 phase=FILE source=t20w-runaway.js owner=ScriptManager[server] error=script evaluation was terminated by the watchdog and the active generation is isolated; active generation remains isolated; explicit full reload is required.`
  服务器日志同链复核：`generation=1 candidate=false` kill → `重载完毕`（吞没点）→
  19:37:44–19:38:04 零 `T20W-ALIVE`（隔离保持）→ 显式 reload 提交恢复 → RCON `stop`
  干净停服。P1 真实成功与 P5 拒绝措辞逐字不变。
