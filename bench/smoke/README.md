# bench/smoke — ticket 05 双 loader runServer 烟测

- `run-smoke.ps1 -Node <26.1.2 | 26.1.2-fabric>`：铺设最小 marker fixture（`fixtures/nekojs/`，
  含 ScriptBootstrap 默认示例脚本会一并生成）→ 启动 `:<node>:runServer` → 等 `Done` →
  RCON `nekojs reload server` → marker 计数 → RCON `nekojs error` → RCON `stop`。
- 命令/停服通道：RCON（复用 `bench/perf` 的结论：gradlew stdin 不可达）；`rcon.py` 为
  Source-RCON 客户端副本。
- run 目录：NeoForge = `versions/<node>/run`，fabric = `versions/<node>/run-server`
  （loom `runs.named("server").runDir`）；每会话清空 `nekojs/` 与 `logs/nekojs/` 保证计数从零。
- 输出：`out/<UTC 时间戳>-<node>/`（checks.json / RCON 应答 / server-stdout.log.gz）。
  注意 `.gitignore` 的 `*.log.gz` 会忽略日志归档——入库证据放
  `docs/architecture-refactor/baseline/2026-09-12-runtime-root-refactor/evidence/`（force add）。
- ticket 05 报告：`docs/architecture-refactor/baseline/2026-09-12-runtime-root-refactor/REPORT.md`。
