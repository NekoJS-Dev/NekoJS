# 2026-09-12 P0 独立性能基线报告（工单 02）

工单：[02: P0 独立性能基线](../implementation-tickets/02-perf-baseline.md)。固定 harness 与数据集在
`bench/perf/`（随仓库提交，P4 复测复用同一套）；原始样本见 `raw/formal/`，作废运行见
`raw/shakedown/`，运行器日志见 `raw/runner-logs/`。

**本报告只陈述观测值、方差与风险，不设发布阻断阈值或预算**；阈值按决策 07 在 P4 前依据本基线决定。

## 1. revision 绑定与取代说明

- 本基线的行为源 revision：**`3400e97e`**（`refactor(wrapper): drop redundant spawnEgg cast`，master）。
  全部采样在隔离 worktree `NekoJS-perf02`（detached @ `3400e97e`，不含任何工作区 WIP）内执行。
- **取代 01 号报告的建议**：01 号基线报告 §2 原建议 02 绑定 `14de611f`。此后有两笔已批准工作入库：
  `892702c9`（规划语料，纯文档，不影响运行时）与 `a2715b03`（游戏内编辑器/脚本文件同步移除，
  2026-09-10 已批准并验证的产品决定）。`3400e97e` 才是重构起点的真实运行时状态，故以它绑定。
  差异性质是**产品决定而非性能调优**：不改变 tick/注册/脚本求值路径的形状；但编辑器移除确实删除了
  `MultiLineEditBox` AT 与 8 个同步 payload（`ShowErrorListPacket` 保留），故不得拿 `14de611f` 的
  数字与本基线混用。
- 与 01 的隔离要求：本票使用独立 worktree + 独立 `GRADLE_USER_HOME`（`D:\mcmodDemo\NekoJS\.gradle-perf02`）
  + 独立 run 目录（worktree 内 `versions/26.1.2/run`）；01 的 Gradle/JDK 修复只作为环境前提，
  其构建日志与产物没有进入任何性能输入。

## 2. 环境

| 项 | 值 |
|---|---|
| OS | Windows 10.0.26200 x64 |
| daemon / 采样 JVM | Oracle JDK `25.0.2`（`C:\Program Files\Java\jdk-25.0.2`；由隔离 `GRADLE_USER_HOME` 的 `gradle.properties` 提供） |
| PATH `java` | Zulu 8（仅 wrapper 客户端；不影响 daemon） |
| Gradle | wrapper 9.6.0；**隔离缓存** `D:\mcmodDemo\NekoJS\.gradle-perf02`（首次冷构建后复用，1.2 GB；逐样本未清理，冷/热状态在下文按维度记录） |
| 插件/依赖 | Stonecutter 0.9.7、Loom 1.17.20、GraalMC 25.1.3.7（curse 8762962） |
| 采样载体 | `:26.1.2:runServer`（primary 节点），专用服务器，`pause-when-empty-seconds=-1`（禁用空服暂停），固定端口 25871 / RCON 25872，view-distance=8、peaceful、online-mode=false |
| 数据集 | `bench/perf/fixtures/nekojs/`：10 个 startup 脚本 + 5 个 server 脚本 + 3 个入口文件（见 §5） |
| 数据规模 | 2821 个可生成类型、389 个 probe 文件；注册负载 5 item + 2 block + 2 entityType + 1 fluid + 1 tab + 3 自定义事件声明 |

`env-snapshot.txt`（每轮 CPU/内存/java 进程数）在对应 `raw/formal/*/` 内。采样期间本机另有
Docker Desktop 常驻容器与 IDE 进程，未做 CPU 独占；这是本基线的环境噪音口径，P4 复测须记录是否相同。

## 3. 逐维度结果（正式样本）

### 3.1 startup — 5 样本

口径：`gradlew.bat :26.1.2:runServer` 进程启动 → stdout 出现 `Done (X.XXXs)!` 的墙钟差（含 Gradle
配置与 daemon 复用开销，即脚本口径）；同列日志自报 `Done` 秒数。预热 2 次不计入。

| 样本 | wall_done_ms | 日志自报 done_s |
|---|---:|---:|
| 1 | 13,220 | 0.302 |
| 2 | 24,011 | 0.547 |
| 3 | 24,391 | 0.535 |
| 4 | 16,523 | 0.275 |
| 5 | 14,338 | 0.317 |

- 汇总：min 13,220 / p50 16,523 / mean 18,496.6 / max 24,391 / stdev 5,342.8 (ms)。
- 预热 2 次：14,221 / 14,320 ms（均低于 p50，说明不能只看均值）。
- 方差来源：样本 2、3 的 `done_s` 同步升高（0.547/0.535 vs 0.28–0.32），说明这两次慢在**服务端初始化**
  而非 Gradle 客户端；未定位到具体原因（无并行构建、无杀毒扫描记录）→ 见 §8 异常项 A1。
- **离群处置**：不删除。样本 2、3 保留在统计内，并在均值旁给出 p50 以体现偏态。

### 3.2 reload — 5 样本（同一服务器会话连发）

口径（双列）：`rcon_rtt_ms` = RCON 发送→响应返回（命令在服务端线程执行，响应文本即完成回执）；
`marker_ms` = 发送→stdout 出现完成代理 marker（字典序最后的 server 脚本 load 行）。两者实测差 1–3 ms。

| 样本 | rcon_rtt_ms | marker_ms | RCON 响应 |
|---|---:|---:|---|
| 1 | 239 | 242 | `NekoJS server scripts reloaded. - no errors.` |
| 2 | 125 | 126 | 同上 |
| 3 | 96 | 97 | 同上 |
| 4 | 104 | 105 | 同上 |
| 5 | 117 | 118 | 同上 |

- rtt 汇总：min 96 / p50 117 / mean 136.2 / max 239 / stdev 58.6 (ms)。
- 负载：重编译 + 重求值 6 个 SERVER 脚本（adapter/arith/main/mem/tick + 入口）+ 事件重注册；
  **0 错误**（`no errors.`），与 §5 的 fixture 修复一致。
- 样本 1 偏高（239 ms）与首个脚本被 JIT/类加载冷启动一致；不删除。

### 3.3 tick — 3 轮，每轮 ≥1201 个 tick 回调样本

口径：`ServerEvents.tickPre` 回调间 `process.hrtime.bigint()` 差值（宿主 `System.nanoTime()`，单调）。
**这是脚本侧可见的 tick 分发节奏，不是原版服务器 tick 性能**；负载含脚本分发与事件桥开销。

| 轮 | 行数 | raw min (ms) | p50 (ms) | mean (ms) | max (ms) |
|---|---:|---:|---:|---:|---:|
| 1 | 2405 | 38.98 | 49.98 | 50.00 | 60.55 |
| 2 | 1201 | 44.49 | 50.01 | 50.00 | 54.83 |
| 3 | 1206 | 48.12 | 50.02 | 50.00 | 52.03 |

- p50 与 mean 稳定在 **50.0 ms**，即原版 20 TPS 节拍本身；脚本事件分发的可见开销低于采样分辨率
  （每 tick 差值的中位数与理论节拍无法区分）。含注册表与 6 个 server 脚本的常驻监听。
- 轮 1 行数 2405 是采样窗口更长（`TickWindowRows=1200` 在 2 s 轮询下越过阈值），非异常。
- 冷启动头部偏高（首 5 个差值 34–49 ms 混有启动阶段），已计入并如实呈现；分位摘要用全样本。

### 3.4 Adapter（registry/绑定查询）— 3 轮 × 10 chunk

口径：每 chunk 2000 次迭代（`Item.of` + `Item.id` + `Item.idOf` + `Utils.randomInt` + `JavaMath.sqrt` 组合），
`performance.now()` 计时，报告 ns/op（每 op = 6 类操作组合）。

| 轮 | chunk 序列 (ns/op) | 稳态（chunk≥4）min / p50 / mean / max |
|---|---|---|
| 1 | 59,179 → 16,068 → 12,444 → 10,770 → 8,869 → 9,487 → 7,008 → 11,491 → 8,740 → 6,863 | 6,863 / 8,804 / 8,743 / 11,491 |
| 2 | 55,018 → 16,044 → 14,864 → 9,408 → 9,190 → 7,425 → 7,919 → 6,313 → 5,360 → 5,335 | 5,335 / 6,869 / 6,924 / 9,190 |
| 3 | 73,274 → 19,308 → 15,961 → 11,864 → 10,274 → 7,723 → 7,291 → 7,206 → 7,578 → 6,922 | 6,922 / 7,434 / 7,832 / 10,274 |

- 明确的两段形态：**chunk 0 是 JIT/绑定冷路径**（55–73 μs/op），随后 4 个 chunk 内收敛到
  5–11 μs/op。故报稳态 p50（6.9–8.8 μs/op）而非均值；冷 chunk 不删除、单列。
- 敏感点：`Item.of` 这类 wrapper 创建的绝对值受 GC 与解析缓存影响，方差按轮可达 30%（轮 2 p50 6.9 vs 轮 3 7.4 μs）。

### 3.5 求值吞吐（GraalJS eval）— 3 轮 × 8 block

口径：8 个 block × 200,000 次纯 JS 算术迭代（总量 1.6e6 语句），每 block 报 ns/op。

| 轮 | block 序列 (ns/op) | 稳态（block≥2）min / p50 / mean / max |
|---|---|---|
| 1 | 359.5 → 105.7 → 90.3 → 100.2 → 110.4 → 106.0 → 71.9 → 60.0 | 60.0 / 95.2 / 89.8 / 110.4 |
| 2 | 218.8 → 170.8 → 129.7 → 111.2 → 106.1 → 95.5 → 97.0 → 68.4 | 68.4 / 101.5 / 101.3 / 129.7 |
| 3 | 236.1 → 96.4 → 91.2 → 77.0 → 59.8 → 54.0 → 55.1 → 55.8 | 54.0 / 57.8 / 65.5 / 91.2 |

- 首 block 明显偏高（219–360 ns/op，解释器启动 + 内联），稳态 p50 57.8–101.5 ns/op。
- 跨轮漂移（轮 3 明显更低）指向 daemon/宿主 JIT 状态差异；同轮内单调下降是同一原因。报区间不报单点。

### 3.6 heap/memory — 3 轮

口径：脚本侧等价 binding `process.memoryUsage()`（内部 `Runtime.totalMemory()/freeMemory()`；
沙盒屏蔽 `java.lang.Runtime`，`ClassFilter` 不允许脚本直接取），5 s 间隔，`rss_bytes/heap_total/heap_used/os_free`。

| 轮 | 行数 | heap_used 首→末 | heap_total 范围 |
|---|---:|---|---|
| 1 | 24 | 279.2 MB → 327.5 MB | 729.8 MB 恒定 |
| 2 | 12 | 235.3 MB → 268.1 MB | 562.0 MB 恒定 |
| 3 | 12 | 531.1 MB → 240.4 MB | 604.0 → 889.2 MB |

- 每轮内 heap_used 单调上升 13–39 MB（含服务器常驻 + 采样自身），轮间水位差主要由 JVM/daemon
  复用状态决定；轮 3 首值偏高（531 MB）后回落，说明**这不是稳态堆曲线**，只能作为量级参考。
- `heap_total` 按 JVM 增长策略变化，非泄漏证据；本基线不设内存预算，也不宣称无泄漏。

### 3.7 Probe（类型声明生成）— 1 预热 + 5 正式

口径：每样本前清空 `run/.neko_probe`，使每个样本走**完整生成**路径（实测：不清时后续样本走
`0 written / 389 unchanged` 的增量快路径，仅 ~217 ms，不可混用）；`marker_ms` = RCON 发送 →
stdout 出现 `Probe [typescript] generated N files in Mms`。

| 样本 | 类型 | files | 命令自报 self (ms) | marker_ms | rcon_rtt_ms |
|---|---|---:|---:|---:|---:|
| 1 | warmup | 389 | 667 | 1370 | 1351 |
| 2 | formal | 389 | 440 | 687 | 545 |
| 3 | formal | 389 | 361 | 609 | 462 |
| 4 | formal | 389 | 330 | 464 | 445 |
| 5 | formal | 389 | 780 | 879 | 867 |
| 6 | formal | 389 | 390 | 626 | 490 |

- 正式 `self` 汇总（n=5，含 1 个 780 ms 离群）：min 330 / p50 390 / mean 460.2 / max 780 (ms)。
- `marker_ms` 比 `self` 高 100–700 ms：`self` 是生成循环自身计时，`marker_ms` 还包含命令排队、
  日志刷盘与我的 100 ms 轮询粒度（上限 ~100 ms）。**比较不同轮次时用 self，跨命令比较用 marker_ms。**
- 每样本 `probe_deleted=true` 见运行器日志 `raw/runner-logs/sampler-probe-r8.log`；
  样本 jsonl 的 `probe_deleted` 字段为空是因为该字段在采样后才补进 harness（见 §8 A3）。

## 4. 未采样范围（显式声明，不外推）

| 未采样项 | 原因 | 建议 |
|---|---|---|
| `26.2.0`（secondary）、`1.21.1`、两个 fabric 节点 | 02 号工单口径只要求 primary 节点；跨节点采样会引入 loader/MC 坐标差异，需各自的数据集调整 | 扩展时用 `-Node <name>` 跑同一 harness；fabric 节点 run 目录为 `run-server`，需先适配 |
| CLIENT 维度（渲染/HUD/输入） | `runServer` 为专用服务器载体；红线不跑 runClient | 需客户端维度时另建 harness 并明确 GUI 合成开销口径 |
| GC 停顿、分配率、JIT 编译计数 | 无 JFR/async-profiler 接线；仅采到脚本侧墙钟与 `memoryUsage` | 需要时在 `runServer` 加 JVM 参数并记录口径 |
| 长时间稳态（>2 min）与多玩家负载 | 单机空服、无玩家；tick 窗口约 60 s | P4 复测如变更负载须重新采样 |

## 5. 数据集（固定负载）与本次修正

`bench/perf/fixtures/nekojs/`（15 个脚本 + 3 个入口，全部随仓库提交）：

- `startup_scripts/`：s01 item×5、s02 item 带标签、s03 block×5、s04 block 带标签、s05 entityType×2
  （含 attributes/goals 链式）、s06 fluid + creativeModeTab、s07 `ScriptEvents` 自定义事件声明×3、
  s08 纯 JS 数据结构（Map/200 条）、s09 interop 引用、s10 marker。
- `server_scripts/`：`adapter-bench`、`arith-bench`、`mem-bench`、`tick-bench`、`main` + 入口。
- 入口 `src/main.js` 存在即避免 mod 自动脚手架 "Hello World"，使发现数完全由数据集决定。

**本次对 fixture 的修正**（首轮实测报错，见 `raw/shakedown/`）：

1. `b.title(...)` / `b.icon(...)` 两个 API 误用为方法调用——真实 `CreativeTabBuilder` 里 `title`/`icon`
   是 **public 字段**（`b.title = 'x'`、`b.icon = 'y'`），与 ADR-0005 修订后的 managed Builder 语义一致。
2. `b.add(a, b)` 传两参——`add(Object)` 只接受一个，改为逐条 `add`。
3. `.slice(...)` 触发 startup binding-preflight 的 unknown-identifier 拦截（沙盒预检按标识符白名单），
   改为纯字面构造。
4. `adapter-bench` 的触发点从脚本 load 改到 `ServerEvents.started`：load 阶段早于 registry 组件绑定，
   `Item.idOf` 会抛 `Components not bound yet`。

这些是**数据集自身缺陷，不是产品缺陷**；修正后 startup 与 reload 均报 0 错误（§3.2）。

## 6. 通道口径（跨实现固定下来的事实）

- 命令通道：`nekojs reload` / `nekojs probe` 走 **RCON**（`bench/perf/rcon.py`，Source RCON 最小实现）。
  纯 PowerShell 封帧版本被 vanilla RCON 线程在 auth 阶段重置连接（实测），故固定用 python 客户端。
  这解释了为什么首个 harness 版本会 fallback 到 taskkill 并留下孤儿服务器。
- 停服通道：stdin **不通**（`channel-test.txt`: `stdin forwarded to server: False`）→ 一律 RCON `stop`；
  双通道都失败才 `taskkill /F /T` 并标 `killed=true`。本次正式样本 **0 次 taskkill**（全部 `stop_channel=rcon`）。
- 停服后必须等 `world/session.lock` 释放：MC 的 `DirectoryLock` 在服务器 JVM 退出才释放，晚于 gradle
  客户端退出；不等会让下一会话在 `DirectoryLock.create` 竞争失败（shakedown 第 1 条）。
- 日志读取：mod 完成行经异步 Log-Flusher 刷出；PS 5.1 读取无 BOM 的 `.ps1` 按 ANSI 解码，中文注释会吞换行
  导致行号错位与匹配失败 → `sample.ps1` **必须带 UTF-8 BOM**（已加），非 ASCII 字符串比较改为
  显式 UTF-8 解码 + ASCII 代理 marker。

## 7. 证据与留档完整性（含一处有记录的裁剪）

- `raw/formal/`：4 个维度的 `samples.jsonl`、`env-snapshot.txt`、`channel-test.txt`、bench 的
  `round-*-csv/`（tick/adapter/eval/mem 原始 CSV 全量）、以及每个会话 stdout 的**关键行摘要**
  `*-excerpt.log`。
- `raw/shakedown/`：全部作废运行的同类产物 + `README.md` 逐条原因（工单要求「失败或中断样本不得删除」已满足，
  所有样本数据保留）。
- `raw/runner-logs/`：13 个采样器运行日志（含每样本控制台行、`deleted=True` 等字段）+ Phase B 构建日志。
- **有记录的裁剪**：会话 stdout 全文每个 4.3–30.9 MB（FML DEBUG 级别），总计约 225 MB，未入库；
  仅保留关键行摘要与运行器日志。这是本报告唯一的原始证据裁剪，原因是仓库体积，不改变任何样本数值。
  重建方式：按 §9 命令重跑（同一 revision + 同一 harness 可复现该量级的日志）。

## 8. 异常 / 不可信样本清单（每项 owner、原因假设、后续验证）

| # | 项 | 状态 | 原因假设 | owner / 后续动作 |
|---|---|---|---|---|
| A1 | startup 样本 2、3 偏高（24.0/24.4 s；`done_s` 同步升高） | 保留在统计内 | 服务端初始化路径受外部干扰（未定位） | zcode-agent：P4 复测时加 `--info` 或同时采服务端耗时分离 Gradle/服务端归因 |
| A2 | tick 轮 1 行数 2405（>1200 阈值） | 保留 | 2 s 轮询粒度越过阈值 | 无需动作；P4 如需等长窗口改按时间窗截断 |
| A3 | `probe_deleted` 在样本 jsonl 中为空 | 已定位 | 该字段在采样后才补进 harness 映射 | 已修 harness；本次值见 `runner-logs/sampler-probe-r8.log` |
| A4 | adapter/eval 的冷起始（chunk0 / block0）显著偏高 | 保留、单列稳态 | JIT 未预热 + 首次绑定解析 | 已在 §3.4/§3.5 分开报冷/稳态，不混算 |
| A5 | memory 水位轮间不可比 | 记录为量级参考 | daemon 复用与 JVM 增长策略 | 若要内存预算须先固定 JVM 参数并单独采样 |
| A6 | 采样期本机有 Docker/IDE 常驻 | 记录 | 未做 CPU 独占 | P4 复测须记录同项；差异即诊断输入 |

## 9. 复现命令（逐条）

```bash
# 0) 前置：Windows x64 + JDK 17+（本机为 jdk-25.0.2）
cd D:/mcmodDemo/NekoJS/NekoJS-mult
git worktree add ../NekoJS-perf02 3400e97e
cd ../NekoJS-perf02/NekoJS-mult
mkdir -p ../.gradle-perf02
printf 'org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2\n' > ../.gradle-perf02/gradle.properties
export GRADLE_USER_HOME=/d/mcmodDemo/NekoJS/.gradle-perf02
./gradlew --version --console=plain            # 必须显示 Daemon JVM = jdk-25.0.2

# 1) 构建（冷缓存首跑约数分钟；本基线记录项）
./gradlew :26.1.2:build :1.21.1:build :common:check --console=plain

# 2) 采样（顺序：startup -> bench -> reload -> probe；每个 Mode 一个独立输出目录）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode startup -Warmup 2 -Samples 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode bench   -Rounds 3
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload  -Reloads 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode probe   -ProbeSamples 5

# 3) 样本在 bench/perf/out/<UTC 时间戳>-<mode>-<node>/；统计口径见 §3 与本 README
```

## 10. 何时必须重采样

本基线必须先于任何性能相关行为改动完成；下列改动会使其失效，须按同一 harness 重采并对照：

- 脚本编译/求值路径（语言管线、TS/Python 转译、模块缓存与 identity）；
- 事件总线与声明/注册路径（`ServerEvents`、`RegistryEvents`、动态注册 prepare/commit）；
- reload 候选生命周期与线程契约（本基线的 reload 口径直接量它）；
- Adapter/wrapper 与 registry 查询面（§3.4 的敏感面）；
- Probe 生成链路与类型声明输出量（389 文件是可比的量级前提）；
- JVM/Gradle/loader/GraalMC 版本、`deps.java` 或服务端配置（`view-distance`、`pause-when-empty`）。
