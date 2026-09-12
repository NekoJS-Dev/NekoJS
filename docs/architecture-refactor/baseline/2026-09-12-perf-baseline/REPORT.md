# 2026-09-12 P0 独立性能基线报告（工单 02）

工单：[02: P0 独立性能基线](../implementation-tickets/02-perf-baseline.md)。固定 harness 与数据集在
`bench/perf/`（随仓库提交，P4 复测复用同一套）；原始样本见 `raw/formal/`，作废运行见
`raw/shakedown/`，被取代的第一遍正式样本见 `raw/superseded/`，运行器日志见 `raw/runner-logs/`。

**本报告只陈述观测值、方差与风险，不设发布阻断阈值或预算**；阈值按决策 07 在 P4 前依据本基线决定。

## 1. revision 绑定与取代说明

- 行为源 revision：**`3400e97e`**（`refactor(wrapper): drop redundant spawnEgg cast`，master）。
  全部采样在隔离 worktree `NekoJS-perf02`（detached @ `3400e97e`，不含任何工作区 WIP）内执行。
- **与 01 号报告的关系**：01 号基线报告 §2 原建议 02 绑定 `14de611f`，而本票实际绑定 `3400e97e`。
  `14de611f..3400e97e` 共 6 个 commit，逐条性质如下（**不含未记录项**）：

  | commit | 内容 | 对性能输入的影响 |
  |---|---|---|
  | `d95f816b` | 基线证据文档 | 无（纯 docs） |
  | `4d6db26e` | gradle.properties 注释修正 + wiki | 无（注释/文档） |
  | `f777cb25` | 基线报告修订 + 01 关票 | 无（纯 docs） |
  | `892702c9` | 规划语料入库（103 文件） | 无（纯 docs） |
  | `a2715b03` | 游戏内编辑器/脚本同步移除（已批准产品决定） | **有**：删除 8 个同步 payload、编辑器 GUI 与 `MultiLineEditBox` AT；`ShowErrorListPacket` 与 P4D/ClientData 保留 |
  | `3400e97e` | EntityTypeBuilder 去掉冗余 `spawnEgg` cast | **无运行时行为变化**：cast 到泛型子类型是编译期冗余（去掉后同一对象传入，编译与全部测试通过）；但它确实触及 `wrapper/registry/gen/` 声明面 |

  结论：**绑定 `3400e97e` 是合理起点，但不等于"与 01 完全同源"**——`a2715b03` 是有运行时影响的
  已批准产品决定（删除的同步链路不在本基线的 7 个维度里，故维度口径可比；但**不得与 `14de611f`
  的数字混用**）。这属于"源码新证据要求改变已定语义先回到决策"的留痕性质，记在此处与 §8 A8。
  `a2715b03` 之前（`14de611f`/`9f702195`）的构建事实仍以 01 号报告为准。
- 与 01 的隔离要求：本票使用独立 worktree + 独立 `GRADLE_USER_HOME`（`D:\mcmodDemo\NekoJS\.gradle-perf02`）
  + 独立 run 目录（worktree 内 `versions/26.1.2/run`）；01 的 Gradle/JDK 修复只作为环境前提，
  其构建日志与产物没有进入任何性能输入。
- **本报告的数字来自第二遍采样**（harness 修订后）；第一遍正式样本在 `raw/superseded/`，两遍对照见该目录
  README——结论是跨会话离散度大于 harness 版本差异。

## 2. 环境

| 项 | 值 |
|---|---|
| OS | Windows 10.0.26200 x64 |
| daemon / 采样 JVM | Oracle JDK `25.0.2`（`C:\Program Files\Java\jdk-25.0.2`；由隔离 `GRADLE_USER_HOME` 的 `gradle.properties` 提供） |
| PATH `java` | Zulu 8（仅 wrapper 客户端；不影响 daemon） |
| Gradle | wrapper 9.6.0；**隔离缓存** `D:\mcmodDemo\NekoJS\.gradle-perf02`（首次冷构建 1.2 GB 后复用） |
| 插件/依赖 | Stonecutter 0.9.7、Loom 1.17.20、GraalMC 25.1.3.7（curse 8762962） |
| 采样载体 | `:26.1.2:runServer`（primary 节点），专用服务器，`pause-when-empty-seconds=-1`，固定端口 25871 / RCON 25872，view-distance=8、peaceful、online-mode=false |
| 数据集 | `bench/perf/fixtures/nekojs/`：10 个 startup 脚本 + 5 个 server 脚本 + 3 个入口文件（见 §5） |
| 数据规模 | 2821 个可生成类型、389 个 probe 文件；注册负载 5 item + 2 block + 2 entityType + 1 fluid + 1 tab + 3 自定义事件声明 |

缓存/clean 状态按维度：`startup`/`bench`/`reload`/`probe` 四种模式**各自独立进程**，每会话前 deploy 会
重建 run 目录的 `nekojs/`；**Gradle 缓存与 `world/` 全程未清理**（daemon 与依赖复用，属"热缓存 + 复用 world"
口径），因此 startup 的 wall 值不含冷依赖下载。冷构建时长只在 harness README 的复现步骤里作为前置记录
（第二遍 Phase B：`BUILD SUCCESSFUL in 1m 20s`，43 tasks，19 executed；首遍冷构建另见 runner-logs）。

`env-snapshot.txt`（每轮 CPU/内存/java 进程数）在对应 `raw/formal/*/` 内。采样期间本机另有
Docker Desktop 常驻容器与 IDE 进程，未做 CPU 独占；这是本基线的环境噪音口径，P4 复测须记录是否相同。

## 3. 逐维度结果（第二遍正式样本）

### 3.1 startup — 5 样本

口径：`gradlew.bat :26.1.2:runServer` 进程启动 → stdout 出现 `Done (X.XXXs)!` 的墙钟差（含 Gradle
配置与 daemon 复用开销，即脚本口径）；同列日志自报 `Done` 秒数。预热 2 次不计入。

| 样本 | wall_done_ms | 日志自报 done_s |
|---|---:|---:|
| 1 | 30,172 | 0.582 |
| 2 | 16,034 | 0.258 |
| 3 | 14,926 | 0.278 |
| 4 | 24,379 | 0.499 |
| 5 | 24,385 | 0.461 |

- 汇总：min 14,926 / **p50 24,379** / mean 21,979.2 / max 30,172 / stdev 6,398.5 (ms)。
- 预热 2 次：44,324（daemon 刚被 `--stop` 过，含重启开销）/ 28,454 ms。
- 双峰形态：`done_s` 与 wall 同步分档（0.26–0.28 s vs 0.46–0.58 s），说明差异在**服务端初始化**而非
  Gradle 客户端；第一遍采样也复现同一双峰（`raw/superseded/README.md`）→ 不是偶发，见 §8 A1。
- **离群处置**：不删除。五个样本全数列入，因双峰已占多数（3/5 在高端），p50 与 mean 并列给出。

### 3.2 reload — 5 样本（同一服务器会话连发）

口径（双列）：`rcon_rtt_ms` = RCON 发送→响应返回（命令在服务端线程执行，响应文本即完成回执）；
`marker_ms` = 发送→stdout 出现完成代理 marker（字典序最后的 server 脚本 load 行）。

| 样本 | rcon_rtt_ms | marker_ms | RCON 响应 |
|---|---:|---:|---|
| 1 | 228 | 236 | `NekoJS server scripts reloaded. - no errors.` |
| 2 | 122 | 235 | 同上 |
| 3 | 123 | 233 | 同上 |
| 4 | 113 | 219 | 同上 |
| 5 | 110 | 218 | 同上 |

- rtt 汇总：min 110 / p50 122 / mean 139.2 / max 228 / stdev 50.0 (ms)。
- marker 汇总：min 218 / p50 233 / mean 228.2 / max 236 / stdev 8.9 (ms)。marker 稳定比 rtt 高
  约 100 ms，其中 ~100 ms 是 harness 的日志轮询粒度（`Start-Sleep -Milliseconds 100`），其余是
  "最后一个脚本 load 行"出现在回执之后的自然延迟。**跨维度比较用 rtt，跨会话比较用 marker**（后者方差更小）。
- 负载：重编译 + 重求值 6 个 SERVER 脚本（adapter/arith/main/mem/tick + 入口）+ 事件重注册；
  **5/5 报 `no errors.`**，与 §5 的 fixture 修复一致。
- 样本 1 偏高（228 ms）与首个 reload 的类加载/JIT 冷启动一致；不删除。

### 3.3 tick — 3 轮，每轮 ≥1204 个 tick 回调样本

口径：`ServerEvents.tickPre` 回调间 `process.hrtime.bigint()` 差值（宿主 `System.nanoTime()`，单调）。
**这是脚本侧可见的 tick 分发节奏，不是原版服务器 tick 性能**；负载含脚本分发与事件桥开销。

| 轮 | 行数 | min (ms) | p50 (ms) | mean (ms) | max (ms) |
|---|---:|---:|---:|---:|---:|
| 1 | 1213 | 44.53 | 47.30 | 49.99 | 64.34 |
| 2 | 1210 | 43.03 | 49.95 | 50.01 | 63.74 |
| 3 | 1204 | 39.49 | 47.08 | 49.99 | 64.20 |

- 均值稳定在 **50.0 ms**，即原版 20 TPS 节拍本身；p50 略低（47.1–50.0 ms）说明回调间隔相对节拍
  有轻微前后抖动。无论用 p50 还是 mean，脚本事件分发的可见开销都**低于采样分辨率**（无法与理论节拍区分）。
- 行数说明：harness 用 `TickWindowRows=1200` 作为停止条件（2 s 轮询），行数为停止后统计，故略高于阈值；
  `samples.jsonl` 的 `tick_rows` 与归档 CSV 行数一致（1214/1211/1205 vs 1213/1210/1204，差值来自
  CSV 表头与停止瞬间的一行）。
- min 值（39.5–44.5 ms）出现在启动后早期（服务器仍未满速），已计入并如实呈现。

### 3.4 Adapter（registry/绑定查询）— 3 轮 × 10 chunk

口径：每 chunk 2000 次迭代（`Item.of` + `Item.id` + `Item.idOf` + `Utils.randomInt` + `JavaMath.sqrt` 组合），
`performance.now()` 计时，报告 ns/op（每 op = 6 类操作组合）。

| 轮 | chunk 序列 (µs/op) | 稳态（chunk≥4）min / p50 / mean / max |
|---|---|---|
| 1 | 107.32 → 32.21 → 22.27 → 16.40 → 12.93 → 13.03 → 10.37 → 10.03 → 9.01 → 9.22 | 9.01 / 10.20 / 10.77 / 13.03 |
| 2 | 56.98 → 14.72 → 12.21 → 9.40 → 8.49 → 9.38 → 7.14 → 8.69 → 5.82 → 5.49 | 5.49 / 7.81 / 7.50 / 9.40 |
| 3 | 94.23 → 54.75 → 17.46 → 12.44 → 6.51 → 7.99 → 8.56 → 8.08 → 7.15 → 6.22 | 6.22 / 7.57 / 7.42 / 12.44 |

- 明确的两段形态：**chunk 0–2 是 JIT/绑定冷路径**（57–107 µs/op），随后收敛到 5.5–13 µs/op。
  故报稳态 p50（7.6–10.2 µs/op）而非均值；冷 chunk 不删除、单列。
- 跨轮稳态 p50 相差 1.35×，跨会话（vs `raw/superseded/`）相差另 1.4× → 见 §8 A7。

### 3.5 求值吞吐（GraalJS eval）— 3 轮 × 8 block

口径：8 个 block × 200,000 次纯 JS 算术迭代（总量 1.6e6 语句），每 block 报 ns/op。

| 轮 | block 序列 (ns/op) | 稳态（block≥2）min / p50 / mean / max |
|---|---|---|
| 1 | 374.4 → 161.7 → 130.0 → 129.1 → 115.5 → 122.5 → 141.7 → 125.4 | 115.5 / 127.2 / 127.4 / 161.7 |
| 2 | 178.0 → 106.1 → 94.2 → 55.4 → 53.1 → 53.9 → 53.4 → 54.3 | 53.1 / 54.1 / 60.7 / 106.1 |
| 3 | 444.3 → 142.3 → 138.1 → 214.9 → 169.9 → 180.3 → 243.9 → 207.4 | 138.1 / 193.9 / 192.4 / 243.9 |

- 首 block 明显偏高（178–444 ns/op，解释器启动 + 内联），稳态 p50 54.1–193.9 ns/op。
- **本维度是本基线噪声最大的**：轮 3 稳态 p50 是轮 2 的 3.6 倍，且轮 3 内部不再单调下降（block 3–7 反弹），
  指向宿主侧干扰（同会话后段的 GC/CPU 争用或 daemon 负载）。**只能作为量级参考，不得跨轮或跨会话比较**。
- 该维度按 §8 A7 处理（owner zcode-agent，P4 需在同一会话内做对照）。

### 3.6 heap/memory — 3 轮

口径：脚本侧等价 binding `process.memoryUsage()`（内部 `Runtime.totalMemory()/freeMemory()`；
沙盒 `ClassFilter` 不允许脚本直接取 `java.lang.Runtime`），5 s 间隔，`rss_bytes/heap_total/heap_used/os_free`。

| 轮 | 行数 | heap_used 首→末 | heap_total |
|---|---:|---|---|
| 1 | 12 | 230.3 MB → 267.2 MB | 645.9 MB 恒定 |
| 2 | 12 | 537.2 MB → 575.0 MB | 729.8 MB 恒定 |
| 3 | 12 | 476.1 MB → 518.0 MB | 679.5 MB 恒定 |

- 每轮内 heap_used 单调上升 27–42 MB（含服务器常驻 + 采样自身），轮间水位差主要由 JVM/daemon
  复用状态决定（537 vs 230 MB，2.3×）。
- **这不是稳态堆曲线**，也没有分配率或 GC 停顿数据（§4），只能作为量级参考；本基线不设内存预算，
  也不宣称无泄漏。

### 3.7 Probe（类型声明生成）— 1 预热 + 5 正式

口径：每样本前清空 `run/.neko_probe`，使每个样本走**完整生成**路径（不清时后续样本走
`0 written / 389 unchanged` 的增量快路径，仅 ~217 ms，不可混用）；`marker_ms` = RCON 发送 →
stdout 出现 `Probe [typescript] generated N files in Mms`。

| 样本 | 类型 | files | 命令自报 self (ms) | marker_ms |
|---|---|---:|---:|---:|
| 1 | warmup | 389 | 1,604 | 3,365 |
| 2 | formal | 389 | 779 | 1,340 |
| 3 | formal | 389 | 747 | 1,132 |
| 4 | formal | 389 | 622 | 1,005 |
| 5 | formal | 389 | 586 | 951 |
| 6 | formal | 389 | 651 | 1,005 |

- 正式 `self` 汇总：min 586 / p50 651 / mean 677.0 / max 779 / stdev 82.6 (ms)；`probe_deleted=true` 5/5。
- `marker_ms` 比 `self` 高 350–560 ms：`self` 是生成循环自身计时，`marker_ms` 还包含命令排队、日志刷盘
  与 100 ms 轮询粒度。**比较不同轮次用 self，跨命令比较用 marker_ms。**
- 与第一遍（330–780 ms，见 `raw/superseded/`）相比整体偏高（586–779 ms）→ 会话间差异，见 §8 A7。

## 4. 未采样范围（显式声明，不外推）

| 未采样项 | 原因 | 建议 |
|---|---|---|
| `26.2.0`（secondary）、`1.21.1`、两个 fabric 节点 | 02 口径只要求 primary；跨节点会引入 loader/MC 坐标差异，需各自调整数据集 | 扩展时用 `-Node <name>` 跑同一 harness；fabric 节点 run 目录为 `run-server`，需先适配 |
| CLIENT 维度（渲染/HUD/输入） | `runServer` 为专用服务器载体 | 需客户端维度时另建 harness 并明确 GUI 合成开销口径 |
| GC 停顿、分配率、JIT 编译计数、线程/锁竞争 | 无 JFR/async-profiler 接线；只采到脚本侧墙钟与 `memoryUsage` | 需要时在 `runServer` 加 JVM 参数并记录口径 |
| 长时间稳态（>2 min）与多玩家负载 | 单机空服、无玩家；tick 窗口约 60 s | P4 复测如变更负载须重新采样 |
| 冷依赖下载 / 冷 Gradle 缓存下的 startup | 本基线用热缓存 + 复用 world 口径（§2） | 如需冷口径，单独清理缓存/world 后重采并单列 |

## 5. 数据集（固定负载）与本次修正

`bench/perf/fixtures/nekojs/`（15 个脚本 + 3 个入口，全部随仓库提交）：

- `startup_scripts/`：s01 item×5、s02 item 带标签、s03 block×5、s04 block 带标签、s05 entityType×2
  （含 attributes/goals 链式）、s06 fluid + creativeModeTab、s07 `ScriptEvents` 自定义事件声明×3、
  s08 纯 JS 数据结构（Map/200 条）、s09 interop 引用、s10 marker。
- `server_scripts/`：`adapter-bench`、`arith-bench`、`mem-bench`、`tick-bench`、`main` + 入口。
- 入口 `src/main.js` 存在即避免 mod 自动脚手架 "Hello World"，使发现数完全由数据集决定。
- 四个采样 fixture 的 `gen` 现在读 harness 在 deploy 时写的**会话 token**（`perf-out/GEN`），
  因此同一轮里 tick/adapter/eval/mem 的 `gen` 一致（实测 `1789210980656-*`），可跨 CSV 关联。

**本次对 fixture 的修正**（首轮实测报错，证据在 `raw/shakedown/`）：

1. `b.title(...)` / `b.icon(...)` 误当方法调用——真实 `CreativeTabBuilder` 里 `title`/`icon` 是
   **public 字段**（`b.title = 'x'`），与 ADR-0005 修订后的 managed Builder 语义一致。
2. `b.add(a, b)` 传两参——`add(Object)` 只接受一个，改为逐条 `add`。
3. `.slice(...)` 触发 startup binding-preflight 的 unknown-identifier 拦截（沙盒预检按标识符白名单），
   改为纯字面构造。
4. `adapter-bench` 的触发点从脚本 load 改到 `ServerEvents.started`：load 阶段早于 registry 组件绑定，
   `Item.idOf` 会抛 `Components not bound yet`。

这些是**数据集自身缺陷，不是产品缺陷**；修正后 startup 与 reload 均报 0 错误（§3.2）。

## 6. 通道口径（跨实现固定下来的事实）

- 命令通道：`nekojs reload` / `nekojs probe` 走 **RCON**（`bench/perf/rcon.py`）。纯 PowerShell 封帧版本
  被 vanilla RCON 线程在 auth 阶段重置连接（实测），故固定用 python 客户端。
- 停服通道：stdin **不通**（`channel-test.txt`: `stdin forwarded to server: False`）→ 一律 RCON `stop`。
  harness 默认**不再重试 stdin**（那会让每个 Mode 首个会话白等 60 s，并把 tick 窗口拉长 ~60 s）；
  需要复现工单 D1 的试验时用 `-TestStdin`。第一遍样本的该项代价见 `raw/superseded/README.md`。
- 停服后必须等 `world/session.lock` 释放：MC 的 `DirectoryLock` 在服务器 JVM 退出才释放，晚于 gradle
  客户端退出；不等会让下一会话在 `DirectoryLock.create` 竞争失败（shakedown 第 1 条）。
- 日志读取：mod 完成行经异步 Log-Flusher 刷出；PS 5.1 读取无 BOM 的 `.ps1` 按 ANSI 解码，中文注释会
  吞换行导致行号错位与匹配失败 → `sample.ps1` **必须带 UTF-8 BOM**（已加），非 ASCII 比较改为
  显式 UTF-8 解码 + ASCII 代理 marker。

## 7. 证据与留档完整性

- `raw/formal/`：4 个维度第二遍样本的 `samples.jsonl`、`env-snapshot.txt`、`channel-test.txt`、
  bench 的 `round-*-csv/`（tick/adapter/eval/mem 原始 CSV 全量）、`*-excerpt.log`（关键行摘要），
  以及 **`full-logs/*.log.gz`——每个会话 stdout/stderr 全文（gzip）**。
- `raw/shakedown/`：全部**从未有效**的运行 + 逐条原因（同样含 `full-logs/` 全文）。
- `raw/superseded/`：第一遍正式样本（数据有效，因 harness 修订被取代）+ 两遍对照表。
- `raw/runner-logs/`：16 个采样器运行日志（含每样本控制台行、`deleted=True` 等字段）+ Phase B 构建日志。

留档规模：会话日志全文 130 个文件、原始 273.9 MB，gzip 后 **7.9 MB**（FML DEBUG 日志重复度极高，
压缩比 ~35×），已全部随仓库提交，**没有任何原始证据被裁剪**。未入库的只有 `versions/<node>/run` 下的
游戏目录本身（world/logs/config，可由 harness 重建，不属采样证据）。
体积权衡：把 7.9 MB 日志放进 git 是为满足"原始 log 分维度留档 + 失败样本不得删除"这一验收要求；
若维护者认为仓库体积优先，可改为外部存储——**但须同步修改工单 02 验收 3 的口径**，不能单方面裁剪。

## 8. 异常 / 不可信样本清单（每项 owner、原因假设、后续动作）

| # | 项 | 状态 | 原因假设 | owner / 后续动作 |
|---|---|---|---|---|
| A1 | startup 双峰（0.26 s vs 0.58 s 的 `done_s`），两遍采样均复现 | 保留在统计内 | 服务端初始化分支受宿主干扰（未定位） | **zcode-agent**：P4 复测加 `--info` 或加服务端侧计时，分离 Gradle/服务端归因 |
| A2 | tick min 值偏低（39.5–44.5 ms） | 保留 | 启动后早期服务器未满速 | **zcode-agent**：P4 如关注尾部再按"启动后 10 s"为起点截窗，并在报告单列 |
| A3 | `probe_deleted` 在第一遍样本 jsonl 为空 | 已定位（第二遍已写入） | 该字段在采样后才补进 harness 映射 | **zcode-agent**：已修；第一遍值见 `runner-logs/sampler-probe-r8.log`（不入统计） |
| A4 | adapter/eval 的冷起始（chunk0–2 / block0）显著偏高 | 保留、单列稳态 | JIT 未预热 + 首次绑定解析 | **zcode-agent**：已在 §3.4/§3.5 分开报冷/稳态；P4 复测沿用同一冷/稳划分 |
| A5 | memory 水位轮间不可比（230 MB vs 537 MB） | 记录为量级参考 | daemon 复用与 JVM 增长策略 | **zcode-agent**：若要内存预算须先固定 JVM 参数并单独采样（§4） |
| A6 | 采样期本机有 Docker/IDE 常驻 | 记录 | 未做 CPU 独占 | **zcode-agent**：P4 复测须记录同项；差异即诊断输入 |
| A7 | 跨会话/跨轮离散度大（eval 稳态 p50 54→194 ns/op；probe self 330→780 ms） | 保留、报告只给范围 | 宿主负载与 JVM/JIT 状态漂移 | **zcode-agent**：P4 对照必须在**同一会话内**做，或接受同量级噪声；禁止用单点数字跨会话比较 |
| A8 | revision 绑定偏离 01 报告建议（`3400e97e` vs `14de611f`），其中 `a2715b03` 有运行时影响 | 已论证并记录（§1） | 编辑器移除是已批准产品决定 | **维护者**：若需要与 01 严格同源的性能对照，应在 `14de611f` 上补一轮；否则以本基线为 P4 起点 |

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

# 1) 构建（热缓存复跑约 1m20s；冷缓存首跑数分钟，见 runner-logs/phaseB-build.log）
./gradlew :26.1.2:build :1.21.1:build :common:check --console=plain

# 2) 采样（顺序：startup -> bench -> reload -> probe）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode startup -Warmup 2 -Samples 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode bench   -Rounds 3
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload  -Reloads 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode probe   -ProbeSamples 5
# 长会话建议分离启动（脚本内含本机路径，换 checkout 时改 PERF02_PROJECT）：
#   cmd //c "start /b <worktree>\bench\perf\run-mode.cmd startup -Warmup 2 -Samples 5"

# 3) 样本在 bench/perf/out/<UTC 时间戳>-<mode>-<node>/；统计口径见 §3 与 bench/perf/README.md
```

## 10. 何时必须重采样

本基线必须先于任何性能相关行为改动完成；下列改动会使其失效，须按同一 harness 重采并对照：

- 脚本编译/求值路径（语言管线、TS/Python 转译、模块缓存与 identity）——**§3.5 是本基线最敏感维度**；
- 事件总线与声明/注册路径（`ServerEvents`、`RegistryEvents`、动态注册 prepare/commit）；
- reload 候选生命周期与线程契约（§3.2 直接量它）；
- Adapter/wrapper 与 registry 查询面（§3.4）；
- Probe 生成链路与类型声明输出量（389 文件是可比的量级前提）；
- JVM/Gradle/loader/GraalMC 版本、`deps.java`、服务端配置（`view-distance`、`pause-when-empty`），
  以及宿主负载特征（§8 A6/A7——同机同负载才可比）。
