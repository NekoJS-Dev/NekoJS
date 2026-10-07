# 04: P4 前性能发布政策确认

**What to build:** 维护者在取得 P0 独立性能基线后、进入 P4 前，基于实际数据确认每个关键维度是否设置发布阻断阈值、适用节点范围、判定口径和失败处置；不预设任何数字。

**Blocked by:** [02: P0 独立性能基线](02-perf-baseline.md)

**Status:** closed（维护者 2026-09-28 交互确认,记录见文末 Maintainer confirmation）

**Assignee:** unassigned（政策确认由维护者本人给出;整理记录 zed-flash-04 main-session agent）

## Maintainer confirmation（2026-09-28）

- **owner**:维护者(项目 owner,经主会话交互选项确认,非 agent 代答)。
- **输入基线**:[02 号 P0 独立性能基线](../baseline/2026-09-12-perf-baseline/REPORT.md)及其
  `raw/formal/`(revision `3400e97e`,primary 节点 26.1.2,热缓存+复用 world 口径,环境噪音口径见报告 §2)。
- **维护者结论(逐维)**:

| 维度 | 结论 | 阈值/口径 |
|---|---|---|
| startup | **发布阻断** | 复测 5 正式样本 `wall_done_ms` 均值 ≤ **41173 ms**(基线均值 21979 ms + max(3σ=19194 ms, +25%=27474 ms) 取宽者=3σ;样本源 `raw/formal/20260912T105931Z-startup-26.1.2/samples.jsonl` index 3–7) |
| reload | **发布阻断** | 复测 5 正式样本 `marker_ms` 均值 ≤ **285.3 ms**(基线均值 228.2 ms + max(3σ=26.8 ms, +25%=57.05 ms) 取宽者=25%;样本源 `raw/formal/20260912T110719Z-reload-26.1.2/samples.jsonl` index 1–5) |
| tick / Adapter 查询 / eval 吞吐 / heap / Probe | **仅观测记录** | 不阻断;复测采样并在 P4 报告中对照基线逐项列出差值与方差,供人工判读 |

- **适用范围**:阻断阈值仅适用 **primary 节点 26.1.2** 同口径复测;secondary(26.2.0)/experimental(1.21.1、两 fabric)与 CLIENT/GC/长稳/多玩家/冷缓存按基线 §4 属**未采样范围**——暂不设阈值,任何范围扩展(新节点/新维度/口径变化)须先扩展采样并回到本票重新裁决,不得外推现有阈值。
- **测量与判定口径**:固定 harness `bench/perf/`(与 02 相同),startup/reload 各 ≥5 正式样本,判定用均值;`forced_kill=true` 或 `timeout` 样本无效须重采;不得剔除不利样本(方差已由 +3σ/+25% 取宽规则吸收)。
- **环境漂移与不可复现**:复测须记录与基线相同的 `env-snapshot` 字段;环境噪音水平显著偏离基线口径(如独占 vs 常驻进程)时如实记录并在结果中标注,不得事后换口径;不可复现样本整组重采,不得单点剔除。
- **失败处置**:任一阻断维度超阈值 → 该 release candidate 阻塞,修复或维护者显式重裁后方可放行;观测维度异常不自动阻塞,但必须进入 P4 报告的异常清单。
- **后续触发**:性能相关行为改动(runtime/语言/线程/注册路径)、或 P4 复测(票 35)按本政策执行;政策数字的任何变更须以新基线样本为据并再次记录维护者结论,不得沿用过期政策。
- 本记录可直接被 release handoff(票 37)引用。

**Optional:** false

**Selected:** true

**Human input:** required

**Work items:** P4-preparation

## Acceptance criteria

- [x] 维护者在 P0 基线完成后、任何票进入 P4 活动前完成政策确认，并留下人工确认记录；本票不能被默认当作可无人代办的技术验证。
- [x] 每个关键性能维度都获得维护者结论：设置发布阻断阈值、仅记录观测、或暂不设阈值；不预设数字或预算。
- [x] 若设置阈值，逐项记录数值来源、适用节点、支持等级、测量口径、样本规则、判定方式和失败处置；数值必须能追溯到基线数据与维护者理由。
- [x] 若不设置阈值，也明确记录依据和后续触发重新评估的条件，不得写成性能不阻塞或省略采样。
- [x] 政策区分发布阻断与观测指标，并说明 primary、secondary、experimental 或未采样范围的适用差异。
- [x] 环境漂移、负载变化、样本剔除和不可复现情况的处理规则事先明确，不能在结果不利时临时调整。
- [x] 政策确认后，后续性能相关行为改动、P4 复测或重新裁决的触发条件明确；不得沿用过期政策或旧样本。
- [x] 确认记录包含 owner、输入基线、维护者结论、理由、适用范围和失败规则，可供 release handoff 直接引用。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [02: P0 独立性能基线](02-perf-baseline.md): 政策只能基于已取得的 P0 独立性能基线和其环境、负载、预热、重复次数与统计口径确认，不能在采样前预设结论。

## Scope and coordination

- **Rationale:** 这是必须在 P4 前闭合的人工政策门，能独立于实现进度完成，并防止无数据阈值或临时裁量。
- **Coordination:**
  - 维护者亲自确认政策；执行、Probe 与平台 owner 只解释基线口径，不代替裁决。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

需要真实维护者结论，不能由agent代确认。
