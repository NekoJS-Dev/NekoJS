# NekoJS 性能基准 harness（工单 02）

固定口径的性能采样 harness：固定节点范围、固定数据集、固定命令、固定输出路径。本目录随仓库提交，
是后续（P4 复测）必须复用的同一套 harness；原始样本与报告分离存放在
`docs/architecture-refactor/baseline/2026-09-12-perf-baseline/raw/`。

- 基线绑定 revision：`3400e97e`（工单 02 报告 §1 说明它为何取代 01 号报告建议的 `14de611f`）。
- 支持等级口径：`26.1.2` = **primary**（默认采样节点）；`26.2.0` = secondary；`1.21.1` 与两个 fabric
  节点 = experimental。未采样的节点/维度必须在报告里显式记录，不许外推。

## 目录布局

```text
bench/perf/
├── README.md            # 本文件：固定口径与复现命令
├── sample.ps1           # 采样入口（Windows PowerShell 5.1；必须带 UTF-8 BOM，见下）
├── rcon.py              # Source-RCON 客户端（命令通道，见下）
├── .gitignore           # 忽略 out/ 与 run/（原始输出不入库，入库的是 baseline/raw/ 证据副本）
├── fixtures/nekojs/     # 固定脚本数据集（铺设进 run 目录的 nekojs/ 脚本根）
│   ├── startup_scripts/ # 10 个启动期脚本（s01–s10）+ src/main.js 入口
│   └── server_scripts/  # 5 个服务端脚本 + src/main.js 入口
├── out/                 # 采样原始输出（gitignore，不入库）
└── run/                 # 目录联接别名 → versions/<node>/run（gitignore，不入库）
```

## 编码前提（实测踩过的坑，勿改）

`sample.ps1` 含中文注释，因此**必须保存为带 UTF-8 BOM 的文件**。Windows PowerShell 5.1 读取无 BOM
的 `.ps1` 时按当前 ANSI 代码页解码：中文注释的字节会吞掉后续换行，导致解析器行号错位、
`Copy-Item` 等报出与真实位置无关的错误，且中文字符串比较恒不匹配。加 BOM 后上述现象全部消失
（2026-09-12 实测：同一文件去 BOM 报 `sample.ps1:421` 意外 `}`，加 BOM 即 `PARSE OK`）。

非 ASCII 的日志比较（如 mod 的中文完成行）不要写成 `.ps1` 字面量比较；本 harness 的做法是用
ASCII 代理 marker，并用 `Read-LogTailUtf8` 显式按 UTF-8 解码日志增量。

## run 目录约定与隔离

服务器 game dir 由 Gradle MDG run 配置决定，为 `versions/<node>/run`（不修改构建脚本即无法迁移）。
本 harness 的隔离与不污染约定：

1. 所有采样只在**隔离 worktree** 内执行（先 `git worktree add ../NekoJS-perf02 <revision>`），
   `versions/<node>/run` 位于 worktree 内，与用户数据完全无关；
2. `bench/perf/run/<node>` 通过目录联接指向 `versions/<node>/run`，仅作为布局别名（机器本地操作，不提交）：
   ```bash
   mkdir -p bench/perf/run
   MSYS_NO_PATHCONV=1 cmd //c "mklink /J <abs>\\bench\\perf\\run\\26.1.2 <abs>\\versions\\26.1.2\\run"
   ```
3. 每次会话前 deploy 步骤：删净并重建 `run/nekojs`，其中三项都是实测必要的：
   - **删净等待**：刚停服的服务器句柄可能晚于 `Remove-Item` 释放，删不干净就复制会撞半删除目录；
   - **用 `robocopy /E` 而非 `Copy-Item`**：PS 5.1 的通配源 + `-Recurse` 在 fixture 树含嵌套目录
     （`server_scripts/src`）时报 `CopyContainerItemToLeafError`；且若先建了 `nekojs/perf-out` 子目录，
     `Copy-Item` 会把整个 fixtures 目录**嵌套**复制成 `run/nekojs/nekojs/...`，数据集一个都不被发现；
   - 生成 `eula.txt` / `server.properties`（存在则不覆盖）→ 按需放 `perf-out/RUN_BENCH`。
   `world/` 在预热会话建立后复用以降方差（不入库）。
4. fixture 的 CSV/标志文件写 `nekojs/perf-out/`：沙盒默认 `allowFsWriteOutsideNekojs=false`，写必须在
   `nekojs/` 根内。

`server.properties` 固定值：`server-port=25871`、`enable-rcon=true`、`rcon.port=25872`、
`rcon.password=perf02`、`online-mode=false`、`difficulty=peaceful`、`gamemode=survival`、
`view-distance=8`、`spawn-protection=0`、**`pause-when-empty-seconds=-1`**（不加这项时空服 60 s 会
自动暂停 tick，直接污染 tick 样本）。

## 固定口径（每维度）

固定环境前提：

- 隔离 `GRADLE_USER_HOME`（基线用 `D:\mcmodDemo\NekoJS\.gradle-perf02`，其中 `gradle.properties` 一行
  `org.gradle.java.home=...` 提供 daemon JVM——**换了 GRADLE_USER_HOME 就换了用户级属性文件位置**，
  必须在该目录内重建这份文件，否则 daemon 会落到 PATH 上的 Zulu 8 而启动失败）；
- 采样命令统一 `--console=plain`；
- 命令通道：`nekojs reload` / `nekojs probe` 统一走 **RCON**（`rcon.py`）。时间口径 = 命令发送 →
  完成信号。纯 PowerShell 封帧版本会被 vanilla RCON 线程在 auth 阶段重置连接（实测），不要再改回去；
- 停服通道：stdin 实测**不通**（`channel-test.txt` 记 `stdin forwarded to server: False`）→ RCON `stop`；
  两者都失败才 `taskkill /F /T` 并标 `killed=true`。**每次停服后等待 `world/session.lock` 释放**
  （MC 的 `DirectoryLock` 在服务器 JVM 退出才释放，晚于 gradle 客户端退出；不等会让下一会话在
  `DirectoryLock.create` 竞争失败）。

### 1. startup

- 节点 `26.1.2`。采集点：`gradlew.bat :<node>:runServer` 进程启动 → stdout 出现 `Done (X.XXXs)!`
  的墙钟差；日志自报 `Done` 秒数同列。预热 2 + 正式 ≥5，每会话独立输出。
- 输出 `out/<ts>-startup-<node>/samples.jsonl` + 每会话 stdout/stderr 原文。

### 2. reload

- 节点 `26.1.2`。同一服务器会话内连发 ≥5 次，间隔 3 s。
- 双列口径：`rcon_rtt_ms`（发送→响应，响应即完成回执）与 `marker_ms`（发送→stdout 出现完成代理
  marker）。代理 marker 是字典序最后一个 server 脚本的 load 行（ASCII），避免中文完成行的编码问题。

### 3. bench（tick / adapter / eval / mem，每轮重启服务器，≥3 轮）

- **tick**：`tick-bench.js` 注册 `ServerEvents.tickPre`，记 `process.hrtime.bigint()` 差值，累计
  ≥1200 行后落 `tick-samples.csv`。测的是**脚本侧可见的 tick 分发节奏**，不是原版服务器 tick 性能。
- **adapter**：`adapter-bench.js`，固定 6 类操作组合 × 20000 次迭代、每 2000 次一个 chunk，报 ns/op。
  **触发点是 `ServerEvents.started`**，不能在脚本 load 阶段跑（早于 registry 组件绑定，
  `Item.idOf` 会抛 `Components not bound yet`）。首 chunk 含 JIT 冷路径，统计时报稳态 p50。
- **eval**：`arith-bench.js`，8 blocks × 200000 次纯 JS 算术，报 ns/op；首 block 含解释器/JIT 预热。
- **mem**：`mem-bench.js`，5 s 间隔采 `process.memoryUsage()`（脚本侧等价 binding；沙盒屏蔽
  `java.lang.Runtime`）。heap 水位轮间不可比，只作量级参考。

### 4. probe

- 节点 `26.1.2`。**每个样本前清空 `run/.neko_probe`**，强制走完整生成路径——probe 有增量快路径
  （不清时后续样本是 `0 written / N unchanged`，实测 ~217 ms，与完整生成 330–780 ms 不可混用）。
- 完成 marker 实测格式：`Probe [typescript] generated <N> files in <M>ms (<w> written, ...)`。
  1 预热 + ≥5 正式，间隔 3 s。

### 未采样（显式声明，不许外推）

- `26.2.0`（secondary）、`1.21.1`、两个 fabric 节点：默认只采 primary；扩展用 `-Node <name>`，
  fabric 节点 run 目录是 `run-server`，需先适配脚本。
- CLIENT 维度（渲染/HUD/输入）：不采样（载体是专用服务器）。

## 复现命令

```bash
# 0) 前置
cd D:/mcmodDemo/NekoJS/NekoJS-mult
git worktree add ../NekoJS-perf02 <revision>
cd ../NekoJS-perf02/NekoJS-mult
mkdir -p ../.gradle-perf02
printf 'org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2\n' > ../.gradle-perf02/gradle.properties
export GRADLE_USER_HOME=/d/mcmodDemo/NekoJS/.gradle-perf02
./gradlew --version --console=plain       # 确认 Daemon JVM

# 1) 构建
./gradlew :26.1.2:build :1.21.1:build :common:check --console=plain

# 2) 采样（顺序执行）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode startup -Warmup 2 -Samples 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode bench   -Rounds 3
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload  -Reloads 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode probe   -ProbeSamples 5
# 也可以用 bench/perf/out/run-mode.cmd <Mode> <args...> 分离启动（长会话更稳）
```

## 数据集 API 依据

事件名与绑定面以代码为准（2026-09-12 @3400e97e 实读并实测）：

- `ServerEvents.tickPre/tickPost/started/stopped`：`src/main/java/.../bindings/event/ServerEvents.java`
- `RegistryEvents.register(event => event.item/block/entityType/fluid/creativeModeTab(...))`：
  `src/main/java/.../wrapper/registry/gen/RegistryEvents.java`
- 注册 builder 的属性面是 **public 字段**（`b.title = 'x'`、`b.icon = 'y'`、`b.maxStackSize = 16`），
  动作是方法（`b.add('id')` 单参、`size/spawnEgg/attributes/goals`）；`attributes`/`goals` 的回调
  builder 支持链式（返回自身）。把 `title`/`icon` 当方法调用会报错——首轮实测踩过。
- `ScriptEvents.server(event => event.register('Ns', 'name'))`：`common/.../api/event/ScriptEvents.java`
- `process.hrtime.bigint()` / `performance.now()` / `process.memoryUsage()`：node shim
  `common/src/main/resources/nekojs/node/modules/process.ts`
- 沙盒 startup 预检会拦截未知标识符（如对数组字面量调 `.slice`），fixture 里避免使用。

若后续版本 API 变化导致 fixture 报错，**先修 fixture 并在报告记录，不许放宽口径或丢弃失败样本**。

## 已知失败模式（本次采样全部踩到并已修）

1. 停服后不等 `session.lock` → 下一会话 `FatalStartupException`。
2. 用 `Copy-Item` 铺数据集 → 嵌套复制或 `CopyContainerItemToLeafError` → 数据集零发现。
3. 空服 `pause-when-empty` 暂停 tick → tick 样本被静默污染。
4. `adapter-bench` 在 load 阶段执行 → `Components not bound yet`。
5. probe 不删输出目录 → 量到 `unchanged` 快路径而非生成。
6. `.ps1` 无 BOM + 中文注释 → 解析错位、marker 恒不匹配。
