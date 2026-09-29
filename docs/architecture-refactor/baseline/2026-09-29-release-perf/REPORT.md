# 2026-09-29 P4 性能复测与政策对照报告（工单 35）

工单：[35: P4 性能复测与政策对照](../implementation-tickets/35-release-perf-compare.md)。
**政策来源（唯一判定依据）**：[04 号票维护者确认（2026-09-28）](../implementation-tickets/04-perf-release-policy.md)
的 Maintainer confirmation 节——本报告只执行与对照该政策，**不新增、不修改任何阈值数字**。

| 维度 | 政策判定 | 阈值（均值口径） | 本次均值 | 结论 |
|---|---|---:|---:|---|
| startup | 发布阻断 | ≤ 41,173 ms | **16,512.2 ms** | **PASS**（余量 24,660.8 ms） |
| reload | 发布阻断 | ≤ 285.3 ms | **278.0 ms** | **PASS**（余量 7.3 ms，收窄，见 §5.2） |
| tick / Adapter / eval / heap / Probe | 仅观测记录 | 无阈值 | 见 §4.3–§4.7 | 不阻断，逐项差值与方差已列 |

**结论：两个阻断维度均满足已确认政策，本 release candidate 的性能面不阻塞。**
观测维度无自动阻塞项；显著变化与判读清单见 §5。

## 1. revision 绑定与对比性质

- 行为源 revision：**`28283cc4`**（mult 分支 HEAD，`claim(ticket-34,ticket-35)`），采样全部在隔离
  worktree `NekoJS-mult-t35`（branch `ticket-35-release-perf`，工作区干净）内执行。
- **与基线 revision 的关系（可比性如实声明）**：基线绑定 `3400e97e`（原始线，工程树嵌套在
  仓库的 `NekoJS-mult/` 子目录）。经 `git merge-base` 核验，`3400e97e` **不是** mult HEAD 的
  祖先（两条无共同祖先的历史，mult 线自基线点起有 422 个 commit）。harness 与基线证据的同一性
  依据是 **mult 线自身的共同祖先**：`bench/perf/` harness 与 `2026-09-12` 基线证据由本线
  ticket 02 的 `c641e5250`（fixed perf harness）与 `c8604eb4b`（resample + 基线证据入库）提交，
  两者均为 HEAD 祖先且本票未改动；本次运行产出的样本格式、通道行为与发现数（11 STARTUP /
  6 SERVER / 1 CLIENT）与基线留档逐项吻合。即：**对照是"基线采样线 → mult 整合线（422 commit
  的整合行为在测）"的跨分支对照**，harness/数据集/节点/统计规则（均值、不利样本不剔除）不变。
- 本次未改 `bench/perf/` 任何文件：mult 线上该目录最后变更即基线第二遍采样的
  `c8604eb4b`（resample on the fixed harness），此后无 commit，本票运行所用 harness 与基线
  第二遍正式样本的 harness 同一 blob（仓库内本票改动仅新增本证据目录与票据标注）。

## 2. 环境（含与基线 §2 的逐项对照）

| 项 | 基线（2026-09-12） | 本次（2026-09-29） | 漂移 |
|---|---|---|---|
| OS | Windows 10.0.26200 x64 | Windows 10.0.26200 x64（26200） | 无 |
| daemon/采样 JVM | Oracle JDK 25.0.2（隔离 home gradle.properties） | 同左（`Daemon JVM: C:\Program Files\Java\jdk-25.0.2`，启动器仍 Zulu 8 仅 wrapper 客户端） | 无 |
| Gradle | wrapper 9.6.0，隔离 `D:\mcmodDemo\NekoJS\.gradle-perf02` | 同版本同路径；**该目录在本轮已不存在，按票面指示重建**（见 §2.1） | 缓存代次不同 |
| 插件/依赖 | Stonecutter 0.9.7、Loom 1.17.20、GraalMC 25.1.3.7（curse 8762962） | Stonecutter 0.9.7、Loom 1.17.20（`runner-logs/phaseB-build-r6.log`）；GraalMC 同一构件 `curse.maven:graal-1504336:8762962`（会话日志 WARNING 行与隔离缓存路径核验） | 无 |
| 采样载体 | `:26.1.2:runServer`，25871/25872，`pause-when-empty-seconds=-1`，view-distance=8、peaceful、online-mode=false | 同左（harness deploy 写同一 `server.properties` 固定值） | 无 |
| 数据集 | `bench/perf/fixtures/nekojs/`（15 脚本 + 3 入口） | 同一目录，逐字节未改；发现数 11/6/1 与基线一致；startup/reload 0 错误 | 无 |
| 环境噪音 | Docker Desktop 常驻容器 + IDE 进程，未做 CPU 独占 | **Docker Desktop 停止（service Stopped）、无 IDE 进程**；另有并行代理的两个空闲 Gradle daemon（采样窗口内 CPU 0–0.3%/核，安静门通过） | **更安静**（见 §2.2） |

每采样相位的 `env-snapshot.txt`（utc / git rev / gradle user home / CPU 型号与负载 / 空闲内存 /
java 进程数）在对应 `raw/formal/*/` 内，字段与基线同名同序。采样期间 CPU 负载 0–2%、空闲内存
约 50/63.7 GB（基线采样点 load=23%、38.8 GB 空闲——本次噪音水平低于基线口径，方向有利，
按政策"如实记录并标注"执行，不换口径）。

### 2.1 隔离 Gradle home 的重建（环境事实，非口径变化）

- 基线复用的 `D:\mcmodDemo\NekoJS\.gradle-perf02` 在本轮开始时已不存在。按任务指示重建：
  重新写入同一条 `org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2`。
- wrapper 发行版与依赖构件缓存**从本机用户级缓存（`C:\Users\11515\.gradle`）复制引导**（发行版
  同版本 9.6.0；`modules-2` 1.3 GB 整体复制，Gradle 仍按模块元数据校验构件哈希）。
- **网络漂移与应对（构建期，非采样期）**：当日 `maven.neoforged.net` 直连 TLS reset（curl 复现
  000），`net.neoforged:neoform-runtime:2.0.18` 等构建依赖无法直连获取。应对：隔离 home 的
  `gradle.properties` 增加 `systemProp.http(s).proxyHost=127.0.0.1 / proxyPort=7890`（本机既有
  代理，探测该 host 经代理可达 200）。中间尝试过的仓库顺序 init 脚本已删除，最终方案只做代理
  路由，不改变仓库集合与解析顺序。失败尝试日志（phaseB-build r1–r5、help r1–r3）全部留档于
  `raw/runner-logs/`。**该代理只影响构建期依赖获取；四个采样模式的会话内无网络解析参与计时
  维度**（startup 计的是本地墙钟，reload/probe 计 RCON 往返与日志 marker）。
- Phase B 构建（`./gradlew :26.1.2:build :1.21.1:build :common:check`，含
  `:common:checkCommonIsolation`、`:common:test`、两个节点 `:test`）**BUILD SUCCESSFUL in 7m 10s**
  ——同时充当本票要求的 `:common:check` 最终 sanity（安静机规则下执行，见 §2.2）。

### 2.2 机器安静协议（错峰执行记录）

与并行代理（`../NekoJS-mult-t34` 五节点 Gradle 工作、`../NekoJS-mult-t20w1`）错峰：**每个采样
相位前**用 `quiet-wait.ps1`（5 s 窗口对 java/gradle/python/powershell 类进程采样 CPU，>5%/核
视为忙，可等待重试）判定安静并记录。四次门（startup/bench/reload/probe 前）**全部即时 QUIET
通过、零等待重试**：在册进程仅两个空闲 Gradle daemon（t34 用户级 home 一个、本隔离 home 一个，
CPU 0–0.3%/核）。完整记录 `raw/runner-logs/quiet-log.txt`。`:common:check` 所在的 Phase B 构建
亦在安静窗口执行。

## 3. 样本有效性与离群处置（政策执行记录）

- 政策：`forced_kill=true` 或 `timeout` 样本无效须重采；不利样本不得剔除。**本次全部 7 个
  startup 会话（2 预热 + 5 正式）、3 个 bench 会话、5 个 reload、6 个 probe（1 预热 + 5 正式）
  均为有效样本**：`forced_kill=false`、`timeout=null`、停服通道全为 `rcon`、无 killed。
  **没有任何样本被剔除，也没有作废重采发生**（两记录留档的要求因无作废样本而不适用）。
- startup 预热 1（453,980 ms，`done_s=2.456`）显著偏高：本 worktree 首个会话，含 run 目录/world
  首建与 NFRT 冷校验，Gradle 侧墙钟为主（基线同位预热 44,324 ms，其 worktree 采样前已有热
  NFRT 产物）。预热不计入正式统计，如实留档于 samples.jsonl。
- probe 正式样本 4（self 1,393 ms）为本次唯一显著离群（中位数的 5.4 倍），**保留在统计内**，
  单列于 §4.7。
- 采样顺序与基线一致：startup → bench → reload → probe；样本数与基线相同（5/3 轮/5/5）。

## 4. 逐维度结果与政策判定

### 4.1 startup — 阻断维度 → **PASS**

口径：`gradlew.bat :26.1.2:runServer` 进程启动 → stdout `Done (X.XXXs)!` 的墙钟差（含 Gradle
配置与 daemon 复用，脚本口径）；预热 2 次不计入。样本源
`raw/formal/20260929T123434Z-startup-26.1.2/samples.jsonl`（index 3–7 为正式）。

| 样本 | wall_done_ms | 日志自报 done_s |
|---|---:|---:|
| 1（warmup） | 453,980 | 2.456 |
| 2（warmup） | 13,193 | 0.271 |
| 3 formal | 12,889 | 0.279 |
| 4 formal | 17,446 | 0.356 |
| 5 formal | 17,435 | 0.370 |
| 6 formal | 17,097 | 0.326 |
| 7 formal | 17,694 | 0.376 |

- 正式汇总：min 12,889 / p50 17,435 / **mean 16,512.2** / max 17,694 / stdev 2,036.5 (ms)。
- **政策判定：16,512.2 ≤ 41,173 → PASS；余量 24,660.8 ms（59.9%）**。逐样本最大值 17,694 亦
  远低于阈值。
- 对照基线（mean 21,979.2 / max 30,172 / stdev 6,398.5）：均值 **−5,467 ms（−24.9%）**。基线
  §8 A1 记录的 `done_s` 双峰（0.26–0.28 vs 0.46–0.58）本次未复现（正式 0.279–0.376 连续分布，
  stdev 收窄至 1/3）——更安静的环境（无 Docker/IDE）与整合线行为变化共同可能，归因未做（政策
  不要求，观测记录）。

### 4.2 reload — 阻断维度 → **PASS**（余量收窄，见 §5.2）

口径：同一服务器会话连发 5 次 `nekojs reload`，间隔 3 s；`marker_ms` = RCON 发送 → stdout 出现
字典序最后的 server 脚本 load 行（ASCII 代理 marker，与基线同一判定列）。

| 样本 | rcon_rtt_ms | marker_ms | RCON 响应 |
|---|---:|---:|---|
| 1 | 242 | 353 | `NekoJS server reload committed: generation=2 phase=COMMIT - no errors.` |
| 2 | 145 | 266 | 同上（generation=3） |
| 3 | 146 | 266 | 同上（generation=4） |
| 4 | 163 | 268 | 同上（generation=5） |
| 5 | 132 | 237 | 同上（generation=6） |

- marker 汇总：min 237 / p50 266 / **mean 278.0** / max 353 / stdev 43.9 (ms)；rtt 汇总：min 132 /
  p50 146 / mean 165.6 / max 242 / stdev 44.1 (ms)。
- **政策判定：278.0 ≤ 285.3 → PASS；余量 7.3 ms（2.6%）**。
- 对照基线（marker mean 228.2 / stdev 8.9；rtt mean 139.2）：marker 均值 **+49.8 ms（+21.8%）**，
  方差放大（stdev 8.9 → 43.9，由样本 1 的 353 ms 首发类加载/JIT 冷启动主导——基线首发样本同样
  偏高的模式）。样本 1 不剔除；即便按其余 4 样本（mean 234.3）看，日常稳态与基线一致。
- **口径内合同变化（非测量口径变化）**：RCON 完成回执文本由基线的
  `NekoJS server scripts reloaded. - no errors.` 演进为
  `NekoJS server reload committed: generation=N phase=COMMIT - no errors.`（reload 生命周期/
  generation 工作，票 18/20 线）。计时通道（RCON 往返 + ASCII marker）与 marker 判定列不变，
  数字可比。5/5 `no errors`。
- 附带观测：本会话 `wall_done_ms` 12,748 / `done_s` 0.265，与 startup 正式样本同档。

### 4.3 tick — 观测维度（不阻断）

口径：`ServerEvents.tickPre` 回调间 `process.hrtime.bigint()` 差值；3 轮、每轮 ≥1200 行停止
（实测 1207/1201/1204 行，jsonl `tick_rows` 1208/1202/1205 含 CSV 表头/缓冲差，与基线同规则）。

| 轮 | 行数 | min (ms) | p50 (ms) | mean (ms) | max (ms) | 基线 mean |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 1207 | 38.17 | 50.02 | 49.99 | 62.21 | 49.99 |
| 2 | 1201 | 38.40 | 50.04 | 49.99 | 61.02 | 50.01 |
| 3 | 1204 | 34.24 | 50.05 | 49.99 | 65.84 | 49.99 |

- **差值：三轮 mean 与基线逐轮完全一致（49.99–50.0 ms，原版 20 TPS 节拍）**；p50 50.02–50.05
  （基线 47.08–49.95，更贴节拍）；min 34.2–38.4 低于基线 39.5–44.5（启动后早期未满速，基线
  §8 A2 同型，已计入）；max 61.0–65.8 与基线 63.7–64.3 同档。
- 方差注：脚本侧可见分发开销仍**低于采样分辨率**，与基线结论一致。

### 4.4 Adapter（registry/绑定查询）— 观测维度（不阻断）

口径：`ServerEvents.started` 触发，6 类操作组合 × 20000 次迭代、每 2000 次一个 chunk（10 chunk），
报 µs/op；冷 chunk（0–2）单列，稳态取 chunk≥4。

| 轮 | chunk 序列 (µs/op) | 稳态 min/p50/mean/max |
|---|---|---|
| 1 | 76.59 → 21.96 → 17.60 → 15.03 → 13.16 → 12.25 → 11.56 → 10.91 → 10.94 → 11.10 | 10.91 / 11.56 / 11.65 / 13.16 |
| 2 | 93.29 → 36.28 → 20.42 → 17.67 → 15.46 → 11.37 → 11.13 → 11.19 → 11.08 → 10.32 | 10.32 / 11.19 / 11.76 / 15.46 |
| 3 | 83.29 → 23.99 → 32.13 → 26.33 → 15.33 → 9.22 → 9.03 → 9.04 → 9.06 → 9.59 | 9.03 / 9.22 / 10.21 / 15.33 |

- 差值：稳态 p50 9.22–11.56 µs/op，基线 7.57–10.20；逐轮对照 +13%（r1）/ +43%（r2）/ +22%
  （r3），三轮中位 11.19 vs 7.81（+43%）。冷 chunk 76.6–93.3（基线 57.0–107.3）同量级。
- 方差注：基线自身记录跨轮稳态 p50 相差 1.35×、跨会话另 1.4×（§8 A7）；本次跨轮 spread 1.25×，
  与基线 r1（10.20）基本重合、高于基线 r2/r3。**该维度差值落在基线已声明的会话间噪声带内**，
  不构成回归结论；如需精确回归判定须同会话 A/B（基线 A7 同款限制）。

### 4.5 求值吞吐（GraalJS eval）— 观测维度（不阻断）

口径：8 block × 200,000 次纯 JS 算术，报 ns/op；稳态取 block≥2。

| 轮 | block 序列 (ns/op) | 稳态 min/p50/mean/max |
|---|---|---|
| 1 | 185.2 → 70.5 → 73.0 → 60.5 → 57.4 → 54.7 → 51.3 → 50.7 | 50.7 / 57.4 / 57.9 / 73.0 |
| 2 | 187.7 → 71.3 → 71.3 → 63.8 → 57.2 → 58.2 → 50.5 → 50.7 | 50.5 / 58.2 / 58.6 / 71.3 |
| 3 | 213.9 → 77.9 → 69.7 → 60.4 → 56.4 → 55.8 → 54.8 → 50.0 | 50.0 / 56.4 / 57.9 / 69.7 |

- 差值：稳态 p50 56.4–58.2 ns/op，基线 54.1–193.9（**全部落在基线区间内**，与基线最好的轮 2
  基本持平）；首 block 185–214（基线 178–444）。
- 方差注：基线声明本维度噪声最大（轮间 3.6×，禁止跨会话单点比较）。本次三轮 p50 spread 仅
  1.03×——形态反而更稳（与更安静环境一致）。**量级结论：无变化**。

### 4.6 heap/memory — 观测维度（不阻断）

口径：脚本侧 `process.memoryUsage()`，5 s 间隔，`heap_used/heap_total` 等列；3 轮 × 12 行。

| 轮 | 行数 | heap_used 首→末 | 每轮增量 | heap_total |
|---|---:|---|---:|---|
| 1 | 12 | 449.8 → 483.7 MB | +33.9 MB | 1056.0 MB 恒定 |
| 2 | 12 | 310.7 → 352.2 MB | +41.6 MB | 616.0 MB 恒定 |
| 3 | 12 | 301.6 → 337.9 MB | +36.3 MB | 616.0 MB 恒定 |

- 差值：每轮单调增量 +33.9/+41.6/+36.3 MB（基线 +27–42 MB，**同带**）；水位 302–484 MB
  （基线 230–575 MB，同量级）；heap_total 轮间不同（1056 vs 616 MB）与基线 A5"轮间水位由
  JVM/daemon 复用状态决定、不可比"一致。
- 量级参考结论不变：非稳态堆曲线、无分配率/GC 数据，不设预算、不宣称无泄漏。

### 4.7 Probe（类型声明生成）— 观测维度（不阻断）

口径：每样本前清空 `run/.neko_probe` 强制完整生成；1 预热 + 5 正式；`self` 为命令自报生成循环
计时（轮次比较列），`marker_ms` 含排队/刷盘/轮询粒度。

| 样本 | 类型 | files | self (ms) | marker_ms |
|---|---|---:|---:|---:|
| 1 | warmup | 390 | 545 | 1,276 |
| 2 | formal | 390 | 260 | 508 |
| 3 | formal | 390 | 269 | 527 |
| 4 | formal | 390 | **1,393** | 1,615 |
| 5 | formal | 390 | 216 | 474 |
| 6 | formal | 390 | 193 | 419 |

- 正式 `self` 汇总：min 193 / p50 260 / mean 466.2 / max 1,393 / stdev 519.0 (ms)；
  `probe_deleted=true` 5/5。
- 差值：**生成文件数 389 → 390（+1）**——类型声明输出量级前提有轻微变化（mult 线新增一个
  声明面），量级可比性保持。除样本 4 外（193–269 ms）**全部快于基线 min（586 ms）**；样本 4
  （1,393 ms）为显著离群（中位 5.4 倍），**保留在统计内**——它把 mean 推高到 466.2、stdev
  推到 519.0；剔除它会是 234.5 ms，但政策禁止剔除，本表两者并列供判读。marker 列同一样本
  1,615 ms，与其 self 同步偏高，指向该次生成期间的宿主侧干扰（一次性，无复现）。
- 方差注：基线 §8 A7 已声明 probe self 跨会话 330→780 ms 漂移；本次 193–1,393 的散布更宽，
  结论同样只给范围不给单点。

## 5. 显著变化与判读清单（AC5：owner / 定位方向 / 阻塞结论）

| # | 项 | 观测 | 定位方向（供人工判读） | owner / 阻塞结论 |
|---|---|---|---|---|
| 5.1 | startup 显著变快（mean −24.9%，双峰消失） | §4.1 | 环境更安静与整合线变化叠加，未做归因（`--info`/服务端侧计时是基线 A1 留下的后续动作，本次未做，政策不要求） | zcode-agent / 不阻塞（方向有利） |
| 5.2 | **reload 余量收窄至 7.3 ms（mean +21.8% vs 基线，首发样本主导）** | §4.2 | 首发类加载/JIT 冷启动（基线同型）+ reload 生命周期重构（generation 提交路径，票 18/20/08 线）后的真实稳态需持续观察；稳态 4 样本 mean 234.3 与基线持平 | zcode-agent / **不阻塞**（政策满足）；若后续复测 mean > 285.3 即按政策阻塞并交维护者重裁 |
| 5.3 | adapter 稳态 p50 最高 +43%（轮对轮） | §4.4 | 落在基线声明的跨会话 1.4× 噪声带内；精确回归须同会话 A/B | zcode-agent / 不阻塞，进入观测清单 |
| 5.4 | probe 单样本 1,393 ms 离群（保留） | §4.7 | 单次宿主干扰，无复现；输出量 +1 文件 | zcode-agent / 不阻塞，进入观测清单 |
| 5.5 | probe 输出 389 → 390 文件 | §4.7 | mult 线新增声明面（预期演进） | zcode-agent / 不阻塞（量级可比性保持） |
| 5.6 | RCON reload 回执文本合同变化 | §4.2 | 票 18/20 generation/phase 报告上线；脚本/工具若按旧文本匹配需注意 | zcode-agent / 不阻塞（计时通道未变） |

无政策失败项，故无强制维护者重裁决记录；上述条目供 release handoff（票 37）与维护者判读。
**政策数字未被触碰。**

## 6. 证据与留档完整性

- `raw/formal/`：四个维度的 `samples.jsonl`、`env-snapshot.txt`（startup 另含 `channel-test.txt`）、
  bench 的 `round-{1,2,3}-csv/`（tick/adapter/eval/mem 原始 CSV 全量）、每会话关键行摘录
  `*-excerpt.txt`（UTF-8，含 NekoJS INFO 行、PERF02 fixture 标记、`Done (` 行）与
  `full-logs/*.log.gz`（12 个会话的 stdout/stderr 全文 gzip，原始约 101 MB，压缩后
  约 1.2 MB）。
- `raw/runner-logs/`：4 个采样器运行日志、安静门 `quiet-log.txt`、Phase B 成功构建日志与
  **全部网络失败尝试日志（phaseB-build r1–r5、help r1–r3，作环境漂移证据保留）**、采样后
  drift 快照、三个本地辅助脚本副本（quiet-wait / quiet-check / drift-check）。
- 未入库：`bench/perf/out/`（gitignore 的采样工作区原件）、`versions/26.1.2/run`（游戏目录，
  可由 harness 重建）、隔离 Gradle home 本体。
- 采样工作区原件与入库副本逐字节一致（`cp` 直拷）。

## 7. 复现命令（与基线 §9 同套）

```bash
cd D:/mcmodDemo/NekoJS-mult-t35            # 隔离 worktree，branch ticket-35-release-perf
export GRADLE_USER_HOME=/d/mcmodDemo/NekoJS/.gradle-perf02
./gradlew --version --console=plain         # Daemon JVM 必须是 jdk-25.0.2
./gradlew :26.1.2:build :1.21.1:build :common:check --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode startup -Warmup 2 -Samples 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode bench   -Rounds 3
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload  -Reloads 5
powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode probe   -ProbeSamples 5
# 每相位前跑安静门：powershell -File bench/perf/out/quiet-wait.ps1（副本在 raw/runner-logs/）
# 注意：本次运行的隔离 home gradle.properties 含 127.0.0.1:7890 代理路由（§2.1 网络漂移）；
# 若直连 neoforged 可达可移除该四行 systemProp。
```

## 8. 何时必须重新复测（沿政策"后续触发"与基线 §10）

本结论绑定 `28283cc4` + 上述环境。下列任一发生即失效，须按同一 harness/政策重采并对照：

- 脚本编译/求值路径、事件总线与注册路径、reload 候选生命周期与线程契约、Adapter/wrapper 查询面、
  Probe 生成链路的任何行为改动（mult 线 422 commit 自基线后的此类改动已全部包含在本对照内；
  此后新增的须重测）；
- JVM/Gradle/loader/GraalMC 版本、`deps.java`、服务端配置或数据集变化；
- 环境负载特征显著变化（本次为"无 Docker/IDE + 双空闲 daemon"口径；若回到基线的常驻 Docker/IDE
  口径或更吵环境，reload 的 7.3 ms 余量下建议同口径复测后再下 release 结论）；
- 政策数字的任何变更须以新基线样本为据并再次记录维护者结论（票 04 规则，与本报告无关地持续生效）。
