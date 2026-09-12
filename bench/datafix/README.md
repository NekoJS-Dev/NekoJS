# NekoJS 数据保护回读 harness（工单 03）

用**合成旧格式 fixture** 驱动一台真实专用服务器（NeoForge `26.1.2` primary 节点），在普通 reload、
失败 reload、server stop、重启前后对工单点名的数据面（config / world pack / GLOBAL pack /
pdata / trust-store / 用户编辑文件 / logs / probe cache）做**公开观察面**取证：文件内容 hash、
日志 marker、命令回显。目录随仓库提交；原始运行输出（`out/`、`run/`）不入库，证据副本入
`docs/architecture-refactor/baseline/2026-09-12-data-protection/`（见该目录 REPORT.md）。

- 运行 revision：`d0aa6e0d`（master，工单 03 开工时前端）。
- 节点：`26.1.2`（primary）。其余节点不在本票采样（ Fabric WORLD 现状差异以代码盘点记录，
  见 `data-inventory.md` §9；未实测的节点不外推）。
- 隔离：全部场景只在**隔离 worktree** 的 `versions/26.1.2/run` 里跑，主仓库用户数据零接触。

## 目录布局

```text
bench/datafix/
├── README.md            # 本文件：口径与复现命令
├── run-datafix.ps1      # 步骤化运行器（PowerShell 5.1；必须带 UTF-8 BOM，同 bench/perf 的坑）
├── rcon.py              # Source-RCON 客户端（bench/perf/rcon.py 的副本；不改 bench/perf，故拷贝）
├── .gitignore           # 忽略 out/ 与 run/
└── fixtures/            # 合成 fixture 数据集（部署进 run 目录）
    ├── jsconfig.json    # 用户编辑过的 <gamedir>/jsconfig.json（编辑器合并面，FileEditorConfigContributor）
    ├── nekojs/          # 部署为 run/nekojs/
    │   ├── config/engine.toml            # 全键预置 + 用户自定义 scriptEvaluationTimeoutSeconds=77
    │   ├── config/probe.toml             # 全键预置 + 用户自定义 scan.maxDepth=7
    │   ├── config/trusted-servers.json   # trust-store fixture（1 个受信服务器 + 1 个 pinning key）
    │   ├── server_scripts/{main.js,pdata-probe.js,jsconfig.json}
    │   ├── startup_scripts/marker.js
    │   └── packs/demo-pack/              # GLOBAL pack：manifest enabled=false + .neko_pack.state.json enabled=true
    └── world/nekojs_packs/world-demo/    # WORLD pack（NeoForge aboutToStart 激活路径）
```

fixture 全部按**当前代码实读格式**构造（manifest 键集 = `ScriptPackManifest`、state 文件 =
`ScriptPackState`、trust-store = `PackSyncTrustStore` javadoc 形状、engine/probe.toml 键集 =
两个 Loader 的 `setupConfigEntry` 清单），引用见 `data-inventory.md`。

## 运行器步骤（run-datafix.ps1）

| 步骤 | 作用 |
|---|---|
| `deploy` | 清空并重建 `run/nekojs`（robocopy /E + 删净重试），写 `eula.txt` / `server.properties`（固定 25871/RCON 25872/`pause-when-empty-seconds=-1`） |
| `deploy-world` | 部署 WORLD pack 到 `run/world/nekojs_packs/`（需 world 已生成） |
| `start` | 分离启动 `gradlew.bat :26.1.2:runServer`，stdout/stderr → `out/<Session>-*.log`，pid → `out/<Session>.pid` |
| `wait-done` | 轮询 stdout 出现 `Done (` 或失败标记（BUILD FAILED / Exception in thread "main" / DirectoryLock） |
| `rcon` | 经 RCON 发送 `-Commands`（stdin 停服不通，命令通道一律 RCON——bench/perf 已证） |
| `stop` | RCON `stop` → 等 gradle 客户端退出 → 等 `world/session.lock` 释放（MC DirectoryLock 在 JVM 退出才释放） |
| `snapshot` / `compare-snapshot` | 受保护数据集（config×3、packs 全树、脚本树、world pack 全树、README、根 jsconfig）的 SHA256+size+mtime 快照与对比 |
| `break` / `restore` | 塞入 / 移除语法错误脚本 `zz-broken.js`（失败 reload 场景） |
| `summon-target` | RCON `forceload add 0 0` + `summon` 带 `nekojs_pdata_target` 标签的盔甲架（pdata 写入触发） |
| `query-pdata` | RCON `data get entity @e[type=minecraft:armor_stand,tag=nekojs_pdata_target,limit=1] NeoForgeData...`（第二观察通道） |
| `probe-clear` | 删除 `run/.neko_probe`（probe cache 重建场景） |
| `find` | 在 `logs/latest.log` + 会话 stdout/stderr 中 grep marker |

场景编排（哪个步骤序列构成哪个场景）与判定口径见
`docs/architecture-refactor/baseline/2026-09-12-data-protection/REPORT.md`。

## 复现命令

```bash
# 0) 隔离 worktree（bench/datafix 已提交后的 revision）
cd D:/mcmodDemo/NekoJS/NekoJS-mult
git worktree add --detach ../NekoJS-datafix <revision>
cd ../NekoJS-datafix/NekoJS-mult

# 1) 部署 + 首启（首轮含 :26.1.2 编译，wait-done 预留 15 分钟）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 deploy
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 start -Session s1
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 wait-done -Session s1 -TimeoutSec 900

# 2) 场景步骤（rcon/summon/query-pdata/snapshot/compare-snapshot/break/restore/stop 见上表）
# 3) 收尾
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 stop -Session s1
```

## 已知边界（勿重复踩）

1. `run-datafix.ps1` 必须带 UTF-8 BOM（PS 5.1 无 BOM 按 ANSI 解码，中文注释吞换行——bench/perf 实测教训）。
2. 停服必须 RCON；停服后必须等 `session.lock` 释放再启动下一会话。
3. `deploy` 只清 `run/nekojs`，不动 `run/world`、`.neko_probe` 与 `run/logs`——场景间受保护数据必须存活。
4. 盔甲架 summon 在 `forceload add 0 0` 之后做（专用服务器 spawn 区块加载不假设）。
5. fixture 的 `jsconfig.json`（脚本目录）只含自定义键 + 最小编译器字段；它是 only-if-missing 保护面的靶子，
   不是运行时输入。
