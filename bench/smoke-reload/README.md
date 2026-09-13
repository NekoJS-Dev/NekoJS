# bench/smoke-reload — ticket 06 candidate generation / commit 点 runServer 烟测

`run-reload-smoke.ps1 -Node <26.1.2 | 26.2.0>`：铺设 fixture（`fixtures/nekojs/`，含
`config/engine.toml` 的 **`scriptStatementLimit = 200_000` 确定性失败注入**）→ 启动
`:<node>:runServer` → 等 `Done` → 五个 phase（成功 reload / 注入失控脚本 reload 失败 /
修复恢复 / 重复 reload 稳定 / STARTUP 非事务边界）→ RCON `stop`。

- **commit witness**：脚本用版本化 `ticket06-tick-<v>` interval 行区分 generation。
  生产 tick flush 只冲刷 active generation，候选 timer 在 commit 前不进生产路由、失败时随
  候选关闭丢弃——于是「新版本 tick 出现 + 所有旧版本 tick 归零」即单一 commit 点与
  无双执行的直接外部证据。`ticket06-entry-<v>` 只作入口计数（候选执行期可见，属
  spec 09 user story 28 的不深回滚范围）。
- **失败注入（重要，勿按旧口径误读）**：注入源是 `while(true){}`（runner 写入
  `server_scripts/sm02-boom.js`），终止它的是 **`scriptStatementLimit = 200_000` 语句上限**
  （Graal 烧尽候选 Context 预算后关闭它）→ reload 以 `phase=EXECUTION` /
  `domain=candidate-killed` / `source=server/sm02-boom.js` 失败，active 保持分发。
  fixture 的 `scriptRunawayTimeoutSeconds = 0`（时间窗口 watchdog **关闭**），runner 启动时会
  校验这两个键的取值。
  **不要**改用 `scriptRunawayTimeoutSeconds` 的时间窗口路径：实测该路径对 `while(true){}`
  **无效**——单测 20s 窗口内不终止，26.1.2 会话里注入后 RCON 180s 无应答、日志无
  `ResourceLimits` 行（服务器线程卡死）。该缺陷属 core 既有问题（看门狗调度面归 07 号票），
  复现件见 `common/src/test/java/com/tkisor/nekojs/script/Ticket06RunawayProbeTest.java`（`@Disabled`）、
  结论见 `docs/architecture-refactor/baseline/2026-09-12-reload-candidate/REPORT.md` 与
  `fixtures/nekojs/config/engine.toml` 的注释。
- **通道**：命令与停服统一走 RCON（gradlew stdin 不可达；复用 ticket 02/05 结论）。
  `rcon.py` 是本目录内的 Source-RCON 客户端副本（原文件 `bench/perf/rcon.py`，本票不修改它）。
- **端口**：25871 / RCON 25872（与并行票 25/31 的会话不冲突）。
- **输出**：`out/<UTC 时间戳>-<node>/`（`checks.json` / `counts.json` / `env.txt` / 各 phase
  RCON 应答 / stdout 与 nekojs server.log 副本）。`out/` 不入库；入库证据为
  `docs/architecture-refactor/baseline/2026-09-12-reload-candidate/evidence/` 下的 gz 归档
  （`*.log`/`*.log.gz` 被 .gitignore 忽略，需 `git add -f`）。
