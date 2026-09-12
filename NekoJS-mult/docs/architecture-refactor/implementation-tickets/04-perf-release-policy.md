# 04: P4 前性能发布政策确认

**What to build:** 维护者在取得 P0 独立性能基线后、进入 P4 前，基于实际数据确认每个关键维度是否设置发布阻断阈值、适用节点范围、判定口径和失败处置；不预设任何数字。

**Blocked by:** [02: P0 独立性能基线](02-perf-baseline.md)

**Status:** ready-for-human

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** required

**Work items:** P4-preparation

## Acceptance criteria

- [ ] 维护者在 P0 基线完成后、任何票进入 P4 活动前完成政策确认，并留下人工确认记录；本票不能被默认当作可无人代办的技术验证。
- [ ] 每个关键性能维度都获得维护者结论：设置发布阻断阈值、仅记录观测、或暂不设阈值；不预设数字或预算。
- [ ] 若设置阈值，逐项记录数值来源、适用节点、支持等级、测量口径、样本规则、判定方式和失败处置；数值必须能追溯到基线数据与维护者理由。
- [ ] 若不设置阈值，也明确记录依据和后续触发重新评估的条件，不得写成性能不阻塞或省略采样。
- [ ] 政策区分发布阻断与观测指标，并说明 primary、secondary、experimental 或未采样范围的适用差异。
- [ ] 环境漂移、负载变化、样本剔除和不可复现情况的处理规则事先明确，不能在结果不利时临时调整。
- [ ] 政策确认后，后续性能相关行为改动、P4 复测或重新裁决的触发条件明确；不得沿用过期政策或旧样本。
- [ ] 确认记录包含 owner、输入基线、维护者结论、理由、适用范围和失败规则，可供 release handoff 直接引用。

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
