# NekoJS 查询 binding harness（工单 25）

用**最小脚本 fixture + 真实专用服务器**（NeoForge `26.1.2` primary 节点）驱动
DataMap / EntitySelectors 查询 binding 的**游戏内行为**取证：命中、缺失、空值、类型转换、
只读结果、非法输入错误、选择器作用域与距离语义。裸 JUnit 拿不到这些信号——无 FML Loader 的
JVM 里 `BuiltInRegistries` / `EntitySelector` 的类初始化链不可用（`VanillaRegistryProbe` 已在
代码里固化为事实），所以行为级证据一律走 `runServer` + `/nekojs test`。

- 节点：`26.1.2`（primary，NeoForge）。其余节点不在本 harness 采样（selector/数据包行为
  需要真实服务端；未采样节点不外推）。
- 隔离：只在**本 worktree** 的 `versions/26.1.2/run` 里跑，主检出用户数据零接触。
- 端口固定：`server-port=25971`、`rcon.port=25972`、`rcon.password=ticket25`。
- 原始运行输出（`out/`）不入库；证据副本入
  `docs/architecture-refactor/baseline/2026-09-12-query-tools/evidence/`（`.log`/`.log.gz`
  被仓库级 `.gitignore` 忽略，入库需 `git add -f`）。

## 目录布局

```text
bench/query/
├── README.md            # 本文件：口径与复现命令
├── run-query.ps1        # 一键运行器（Windows PowerShell 5.1；必须带 UTF-8 BOM，见「已知边界」1）
├── rcon.py              # Source-RCON 客户端（命令通道 / 停服通道，bench/perf 已验证）
├── .gitignore           # 忽略 out/、run/、*.pid
├── fixtures/
│   ├── test_scripts/    # 默认 fixture 集（部署进 run/nekojs/test_scripts/），预期全绿
│   │   ├── datamap-query.js          # DataMap：命中/缺失/空值/类型转换/只读（15 断言）
│   │   └── entityselectors-query.js  # EntitySelectors：factory/builder/query/作用域/距离/limit/非法输入
│   └── negative/        # 负向探针（**预期失败**，只做 AC3 源位置证据，不进默认集）
│       └── entityselectors-illegal-location.js
├── diagnostics/         # 实读诊断脚本（非验收 fixture，只用于定位现象；-IncludeDiagnostics 才部署）
└── out/                 # 原始输出（gitignore，不入库）
```

## 运行器步骤（run-query.ps1）

| 步骤 | 作用 |
|---|---|
| `deploy` | 删净并重建 `run/nekojs`（删净重试）→ `robocopy /E` 部署 `fixtures/test_scripts`（`-IncludeDiagnostics` / `-IncludeNegative` 追加对应目录）→ 写 `eula.txt` / `server.properties`（固定 25971/25972、`pause-when-empty-seconds=-1`） |
| `start` | 分离启动 `gradlew.bat :26.1.2:runServer`，stdout/stderr → `out/<Session>-*.log` |
| `wait-done` | 轮询 stdout 出现 `Done (` 或失败标记（BUILD FAILED / Exception in thread "main" / DirectoryLock） |
| `setup` | **世界状态前置（必须）**：RCON `forceload add -16 -16 16 16` + 锚点附近 summon 5 个带 tag 的实体（相对坐标 `~`，即服务器 command source 原点） |
| `test` | RCON `nekojs test`（回执 tee 到 `out/rcon-<ts>.log`） |
| `extract` | 汇总证据：`run/logs/nekojs/test.log`（mod 自己写的完整测试输出，**平台 ANSI 编码**，本机 zh-CN = GBK）+ 会话 stdout 的 ASCII marker（`Done (`/`BUILD`/`Unknown identifier`）→ `out/<Session>-extract.log`（写成 UTF-8，中文可读） |
| `verify` | 解析 extract：每个 `[SUMMARY]` 必须 `0 failed`、PASS 行数 > 0、无 `[FAIL]` 行、无脚本级失败（`脚本执行失败` 或 ASCII 兜底 `nekojs:test/<path>.js` / `Script binding-preflight`）、无 preflight 拒绝（`Unknown identifier`）；否则抛错（**不允许留下未解释的失败样本**） |
| `stop` | RCON `stop` → 等 gradle 客户端退出 → 等 `world/session.lock` 释放（MC `DirectoryLock` 在 JVM 退出才释放） |
| `all` | `deploy → start → wait-done → setup → test → extract → verify → stop`（单命令复现） |

## 一键复现（单命令）

```bash
# 0) 前置：本 worktree 内、无服务器在跑（端口 25971 空闲）
cd D:/mcmodDemo/NekoJS/NekoJS-t25/NekoJS-mult

# 1) 全流程（首轮含编译，wait-done 预留 15 分钟；脚本内部已含全部步骤）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/query/run-query.ps1 all -Session q1
```

输出：`bench/query/out/q1-extract.log`（判定输入）、`q1-stdout.log`、`rcon-*.log`。
`verify` 通过时最后一行打印 `QUERY-HARNESS PASSED (summary=…, pass=…, fail=0)`。

分步跑（调试用，服务器会话保持）：把 `all` 换成 `deploy` / `start` / `wait-done` / `setup` /
`test` / `extract` / `verify` / `stop` 依次执行，`-Session` 保持一致。

负向探针（**预期失败**，只做 AC3 源位置证据；`verify` 会因此报错，属预期）：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File bench/query/run-query.ps1 deploy -IncludeNegative
# … start / wait-done / setup / test / extract；然后从 extract 里取「环境/位置/原因」三元组
powershell -NoProfile -ExecutionPolicy Bypass -File bench/query/run-query.ps1 stop
```

## 世界状态前置为什么要单独一步（`setup`）

`/nekojs test` 在**服务器线程上同步**跑完，期间世界不 tick；无玩家的专用服务器也不会
entity-ticking 载入区块。因此：

1. 未 entity-ticking 区块里 summon 的实体会停在 `PersistentEntitySectionManager` 的
   `pendingEntities`，任何 `EntitySelector` 查询都看不到它（实测：`hasChunkAt=false` /
   `entityTicking=false` → `getAllEntities()=0`）；
2. **同一 tick 内** summon 的实体同样不可见。

所以 summon 必须由 RCON 在上一次 tick 完成，`/nekojs test` 只做查询与断言。锚点 = 服务器的
command source 原点（fixture 内用 `server.createCommandSourceStack().getPosition()` 现算），
`setup` 用相对坐标 `~` 与它对齐，故对世界种子/生成类型不敏感。

## 口径（与 REPORT §5 一致）

- **作用域基座**：`EntitySelectors.builder()` / `create(cfg)` 的基座是**玩家集合**
  （`includesEntities=false`，等价 `@a`）；`allEntities()` / `nearestEntity()` /
  `randomEntity()` 的基座是**实体集合**。`type(...)` / `typeTag(...)` **不切作用域**
  （只有玩家类型会调整该位）——这是工单 25 双轴审查对「显式实体类型过滤切作用域」的
  越权改动的**回退结果**，fixture 把现状钉住并标注为**缺口**（REPORT §7）。
- **未知 type tag 静默接受**：`typeTag('nekojs:no_such_tag')` 不报错，过滤恒假、查询恒空
  （silent no-op）。同样按审查回退为现状并钉住；对照面 `type('nekojs:no_such_type')` 直接
  报错——`fixtures/test_scripts/entityselectors-query.js` 同时 pin 住两侧。
- **期望集合自校准**：脚本现算世界里的 tag/类型计数，重跑不漂移；召唤体 `NoAI:1b` 固定位置，
  距离断言稳定。
- **计数是累计值**：`TestJS` 的 passed/failed 在 TEST 环境内跨 `/nekojs test` 调用累计
  （同一 binding 实例）。判定以**逐条 PASS/FAIL 行**为准；`verify` 对每个 SUMMARY 行都要求
  `0 failed`，所以历史累计的失败不会被 SUMMARY 掩盖。

## 证据源与编码（实测踩过的坑）

判定输入是 `out/<Session>-extract.log`。它由两处拼成，**缺一不可**：

1. `versions/26.1.2/run/logs/nekojs/test.log` —— mod 自己写的完整测试输出（`[PASS]`/`[SUMMARY]`
   以及脚本级失败的「环境/位置/原因」三元组）。它是**平台 ANSI 编码**（本机 zh-CN = GBK；已归档的
   旧证据 `nekojs-test-green.log` 同款字节），`run-query.ps1` 用 `-Encoding Default` 读、写成 UTF-8，
   所以 extract 里的中文可读。
2. 会话 stdout —— JVM/gradle 管道会把非 ASCII **吞成 ASCII**（本机实测：cmd 字节透明重定向、
   `chcp 65001`、`Start-Process -RedirectOutput` 三种写法都改不掉；Gradle daemon 的 stdout 编码在
   daemon 启动时就固定了）。因此 stdout 只用来取 ASCII marker（`Done (` / `BUILD` /
   `Unknown identifier`）。

推论：`verify` 的失败判定**同时**依赖中文 marker 与 ASCII 兜底（`nekojs:test/<path>.js`、
`Script binding-preflight`、`Unknown identifier`），在任何 ANSI 平台上都不会退化成「零检查」。

## 对照组探针（DataMap class binding vs instance binding）

`diagnostics/q25-datamap-binding-control.js` 是给 F1 补**可复现对照**用的探针：把
`NekoJSCorePlugin` 的注册行临时还原成 `registry.register("DataMap", DataMapJS.class)`（class
binding）再按上面的步骤跑一次，脚本侧会得到

```text
diag[binding]: typeof DataMap = function
diag[binding]: typeof DataMap.furnaceFuel = undefined
diag[binding]: DataMap.furnaceFuel(coal) THREW: … Unknown identifier: furnaceFuel
```

且 `datamap-query.js` 直接执行失败；还原成 `new DataMapJS()`（instance binding）后是
`typeof DataMap = object` / `typeof DataMap.furnaceFuel = function` / `furnaceFuel(coal) = 1600`。
两份 extract 已归档在 `docs/architecture-refactor/baseline/2026-09-12-query-tools/evidence/`
（`datamap-binding-control-{class,instance}-binding.log`）。该探针属诊断集，**不进默认 fixture**。

## 已知边界（勿重复踩）

1. `run-query.ps1` **必须带 UTF-8 BOM**：PS 5.1 无 BOM 按 ANSI 解码，中文注释吞换行 → 解析错位
   （bench/perf / bench/datafix 实测教训）。
2. 停服必须 RCON，`gradlew` 的 stdin 实测不通；停服后必须等 `world/session.lock` 释放再开下一会话。
3. `deploy` 只清 `run/nekojs`（脚本 + 配置），不动 `run/world`——重跑复用同一世界，实体累积由
   脚本自校准吸收。
4. RCON 命令走 `rcon.py`（纯 PowerShell 封帧会在 vanilla RCON auth 阶段被重置，bench/perf 已证）。
5. `fixtures/negative/**` 与 `diagnostics/**` **不进默认集**：前者是刻意失败的源位置探针，后者是
   实读诊断（含恒真断言）。
