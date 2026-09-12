# 02: P0 独立性能基线

**What to build:** 在任何性能相关行为改动前，用固定路径、固定命令和固定数据集的 benchmark harness，按固定负载、环境、预热和重复次数采集 startup、Probe、reload、tick、Adapter 与内存基线，并留下原始样本、统计口径和复现说明。

**Blocked by:** None (can start immediately)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** P0-performance；建立固定 benchmark harness、数据集、独立 run 目录与原始 JSON/CSV/log 输出；与 01 绑定同一记录源 revision，但使用隔离 checkout/run，不把环境修复污染成性能变化。

## Acceptance criteria

- [x] 采样前记录与 01 相同的源 revision；本票使用隔离 checkout、Gradle/caches 与 run 目录，01 的 Gradle/JDK 修复、构建产物和日志不得混入性能输入。
- [x] benchmark harness、数据集、调用命令和输出路径固定并留档；run 目录独立于用户数据，重复执行可复现且不污染 fixture。
- [x] 原始 JSON/CSV/log 与汇总报告分离留档，记录 warmup、重复次数、GC/JIT 采集口径、clean/cache 状态和中断样本；失败或中断样本不得删除。
- [x] 性能基线与 W0 source/artifact manifest 分离，不把性能指标塞入构建基线或用 manifest 代替采样。
- [x] 每个采样维度明确节点范围、支持等级、负载、数据集、预热、重复次数、采集点和统计方法；未采样节点或维度显式记录而不是外推。
- [x] startup、Probe、reload、tick、Adapter 与 heap/memory 至少按已声明范围留下原始样本、汇总值和离群值处置规则。
- [x] 运行环境、Java/Graal/加载器版本、初始化状态和数据规模留档；任何环境差异或中断都进入诊断而不是被静默丢弃。
- [x] 采样方法可由另一名维护者按记录复现，样本与生成结果不可被普通检查改写。
- [x] 报告只陈述观测值、方差和风险，不在取得数据前设定发布阻断阈值或虚构预算。
- [x] 明确该基线必须先于性能相关行为改动完成，并列出会触发重新采样或对比的行为类别。
- [x] 每个异常或不可信样本有 owner、原因假设和后续验证动作。

## Closure record（2026-09-12）

- 执行者：zcode-agent。绑定 revision：**`3400e97e`**（取代 01 号报告 §2 的建议 `14de611f`；取代理由与性质见
  基线报告 §1——editor-removal 是已批准的产品决定，非性能调优）。
- 交付物：harness `bench/perf/`（`sample.ps1` + `rcon.py` + `fixtures/nekojs/` + README，随仓库提交，P4 复用）；
  证据 `../baseline/2026-09-12-perf-baseline/`（`REPORT.md` + `raw/formal/` + `raw/shakedown/` + `raw/runner-logs/`）。
- 正式样本：startup n=5（p50 16.5 s / max 24.4 s，离群保留）、reload n=5（p50 117 ms，0 错误）、
  tick 3 轮（p50 50.0 ms，等于原版 tick 节拍）、Adapter 3×10 chunk（稳态 p50 6.9–8.8 µs/op）、
  eval 3×8 block（稳态 p50 58–102 ns/op）、memory 3 轮（仅量级）、Probe n=5（完整生成 389 文件 330–780 ms）。
  未采样：secondary/experimental 节点与 CLIENT 维度，已在报告 §4 显式声明并给方法。
- 证据完整性：会话日志全文 gzip 入库（234.2 MB → 6.73 MB），无裁剪；作废/中断样本按原样保留并逐条
  记录原因（`raw/shakedown/README.md`）。本次采样在 harness 内发现并修复 6 类失败模式（lock 竞争、
  RCON 封帧、Copy-Item 嵌套、空服暂停、fixture API 误用、probe 增量快路径、.ps1 BOM），全部写入
  `bench/perf/README.md` 的「已知失败模式」。
- 阈值口径：本基线**不含**任何发布阻断阈值或预算；阈值按决策 07 在 P4 前依据本基线决定
  （[04](04-perf-release-policy.md) 维护者结论、[35](35-release-perf-compare.md) 复测对照消费）。
- 遗留异常（均有 owner，见报告 §8）：A1 startup 两个离群未定位到根因；A5 memory 非稳态曲线。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

No direct blockers.

## Scope and coordination

- **Rationale:** 独立性能事实可以在重构前单独完成并复核，为后续行为变更和 P4 维护者阈值决策提供同一口径的数据。
- **Coordination:**
  - 与执行、Probe 和平台 owner 确认负载与节点范围；若与其他组共享运行环境，只安排调度，不把资源争用当作依赖。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
