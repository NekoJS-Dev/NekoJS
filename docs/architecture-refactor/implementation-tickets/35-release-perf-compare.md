# 35: P4 性能复测与政策对照

**What to build:** 在主整合完成后按已确认政策复测 startup、Probe、reload、tick、Adapter 与内存表现，对比 P0 基线和最终结果，并给出是否满足政策、是否需维护者重裁决的可审计结论。

**Blocked by:** [04: P4 前性能发布政策确认](04-perf-release-policy.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- P4

## Acceptance criteria

- [ ] 最终采样复用 P0 基线与已确认政策的节点范围、负载、预热、重复次数、统计口径和样本规则；任何必要偏离都记录原因与可比性影响。
- [ ] startup、Probe、reload、tick、Adapter 与 heap/memory 的最终观测逐项与 P0 基线对照，并保留原始样本、汇总方法和离群值处置记录。
- [ ] 复测结果逐项套用已确认政策，明确 pass、fail、not applicable 或需维护者重新裁决；实现者不新增或修改阈值数字。
- [ ] 环境、数据集、初始化状态、样本剔除和执行中断留档；不得选择性删除不利样本。
- [ ] 每个显著变化或政策失败有 owner、定位方向、是否阻塞 release 的结论和必要的维护者重裁决记录。
- [ ] 政策或测量口径在复测中不可临时调整；若环境或实现变化使政策失效，停下取得维护者新确认后再继续。
- [ ] 若后续发生新的性能相关行为改动，明确必须重新复测或重新裁决，不沿用过期对比结论。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [04: P4 前性能发布政策确认](04-perf-release-policy.md): 进入 P4 复测前必须已有维护者确认的阈值/不设阈值政策、适用范围与失败规则；复测只执行和对照政策，不临时造政策。
- [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [22: Villager Trades 声明事件与稳定查询](22-villager-trades.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [29: Assets/Lang 资源生成与回读收口](29-assets.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。

## Scope and coordination

**Rationale:** 它把政策执行、最终复测和基线对照连成一个可审计 release 输入，且不在 P4 临时决定阈值。

**Coordination:**

- 执行、Probe、平台和数据 owner 确认负载与样本；若出现政策边界问题，交回维护者裁决。
- 39 已通过 24 传递覆盖：若性能政策范围包含 Item/Block modification，复测消费 39/24 的既有 fixture 与样本，不为了重复阻塞而另加直接依赖。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
