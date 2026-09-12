# 34: P4 五节点整体验证与能力矩阵收口

**What to build:** 在主整合完成后执行一次跨五节点的 P4 总体验证，汇总 build/check/artifact、contract/golden、runtime smoke、source trace、coverage ledger、能力矩阵和旧新对照，形成 release 是否可继续的判定。

**Blocked by:** [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[04: P4 前性能发布政策确认](04-perf-release-policy.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- P4
- W10

## Acceptance criteria

- [ ] 主节点通过编译/检查、artifact与 metadata、contract、data fixture、runtime smoke 和维护者试做所需输入；任一必要缺口阻塞 release。
- [ ] 次级 NeoForge 节点保持可构建、可发布验证和公开契约可追踪，版本差异进入 capability matrix 与发布说明。
- [ ] 三个 experimental 节点至少通过可重复构建、artifact 验证和已声明能力 smoke；不静默承诺完整 parity。
- [ ] 每项能力只以 supported、partial、unavailable 表达实际能力；not verified 与 deferred 作为独立证据/安排维度并阻塞对应未闭合域。
- [ ] managed、legacy、plugin、registry、packet、diagnostic、语言和功能的 contract/golden 均有旧新 diff、原因、影响和审阅记录，普通测试没有改写基线。
- [ ] runtime smoke 使用最终 remap 制品和真实 mods 场景，不把 checkout 开发运行冒充 P4 证据；每个节点输出发现/跳过/执行与失败日志。
- [ ] 最终物理架构一致性审计通过：按实施交接单的包/目录规则自动生成生产 source/resource 清单，并覆盖全部文件；例外逐文件说明 owner 和保留原因，不要求手写全仓矩阵，也不强制所有源码物理搬迁。`common` 无 Minecraft/loader import，根 `src` 与 `versions/<node>` 未堆积重复业务逻辑，旧路径/bridge/duplicate manager 删除账本与实施交接单一致。
- [ ] coverage ledger 每行都有 current path、target owner、gate、证据和删除条件；没有任何功能域因整体通过而被遗漏。
- [ ] 跨领域真实集成至少覆盖诊断 record 到 GUI/workspace、事件 candidate 到 runtime commit、global/shared 写集到领域计划、构建 trace 到能力矩阵；分域 contract fixture 不能替代最终真实链路。
- [ ] 维护者确认的性能发布政策已存在；P4 验证按该政策记录性能 gate 状态，不在本票临时设置或修改数字。
- [ ] Point、Contributor、Hook、显式依赖、freeze 与 Handle 的既有收益没有被重造或回归；新增通道仍只有一个事实源。
- [ ] 所有失败按节点、输入、期望、实际和 owner 记录并阻塞 release，不因单次失败自动降级或 EOL 任何节点。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [PR 37 维护体验回归约束规格](../specs/00-pr37-maintainer-research.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md): 最终节点与能力验证必须包含 CI 用途子集和 Fabric processor 延期替代 gate 的结果。
- [04: P4 前性能发布政策确认](04-perf-release-policy.md): P4 总体验证开始前，性能发布政策必须已由维护者基于 P0 基线确认，后续验证只执行已定政策。
- [39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md): 既有 Item/Block modification 是公开行为且涉及 live mutation，P4 必须消费其候选计划、snapshot ownership、恢复与迁移证据。
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

**Rationale:** 它是最终 release 前唯一的全域证据汇总点，但以已完成的主整合和各域 gate 为输入，避免横向重做实现。

**Coordination:**

- 由主整合 owner 汇总本票 Blocked by 已列出的真实先决票后并行收集各域报告；本票不拆解或代替各功能域实现。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
