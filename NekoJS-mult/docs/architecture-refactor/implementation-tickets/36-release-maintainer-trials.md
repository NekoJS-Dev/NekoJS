# 36: P4 维护者与脚本作者真实试做

**What to build:** 由维护者在最终集成结构上完成新增事件、新增 Adapter、新增扩展点和新增版本四类真实维护任务，并以脚本作者视角完成代表性脚本任务；记录入口、owner、事实源、依赖方向、受影响节点、测试、诊断与迁移体验，证明维护成本和脚本作者上手成本确实下降。

**Blocked by:** [04: P4 前性能发布政策确认](04-perf-release-policy.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)

**Status:** ready-for-human

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** required

**Work items:**

- P4
- 维护者四类任务：新增事件、新增 Adapter、新增扩展点、新增版本。
- 脚本作者代表性任务：只从已通过 capability 的功能中选取启动期注册、Dynamic Registry、Villager Trades add/query、Item/Block modification、server/client event、ESM/CJS/TS 导入、诊断定位、declaration 使用、旧 global 迁移和 capability 识别。
- 维护者 cookbook：新增事件、新增 Adapter、新增扩展点、新增版本四份操作说明，由真实试做修正，只引用公开入口和单一事实源。

## Acceptance criteria

- [ ] 维护者真实完成新增事件、新增 Adapter、新增扩展点和新增版本四类任务；四类记录分别保留任务、入口、结果和遇到的问题。
- [ ] 维护者以脚本作者视角仅凭公开 declaration、示例、诊断和必要公开文档完成代表性脚本任务；阅读多份公开文档本身不算失败，只有必须读内部实现、重复事实源、猜 owner 或绕过 managed API 才记录为失败或待修复。
- [ ] 脚本作者任务只使用对应节点已通过 gate 的 capability；unavailable/not verified 能力验证明确拒绝与说明，不得被拿来要求脚本作者寻找隐藏替代路径。
- [ ] 每个脚本作者任务保留可复制示例、实际诊断/错误输出和公开材料是否足够的结论；需要读内部实现、重复事实源、猜 owner 或改 Java 才能完成的情况记录为失败或待修复。
- [ ] Adapter 试做区分注册类型/Builder 与平台能力接线两类差异，不把平台原生时机错误抽象进共享运行时。
- [ ] 每类任务都能从作者入口追踪到 owner、事实源、依赖方向、受影响节点和保护测试，不需要跨多处猜测或静默同步。
- [ ] 扩展点试做复用既有 Point、Contributor、Hook、显式依赖、freeze 与 Handle 语义，不重造生命周期、静态结果表或第二事实源。
- [ ] 新增版本试做覆盖节点身份、坐标、制品命名、CI 用途子集和受影响测试，不改变五节点支持等级。
- [ ] 每个试做改动在相关节点上通过必要检查，并能说明未覆盖节点的 gate 差异。
- [ ] 任何需要修改规则、复制共享逻辑、查多个登记点或猜 owner 才能完成的情况被记录为失败或待修复，不得宣称维护体验改善。
- [ ] 四份维护 cookbook 分别覆盖入口、owner、事实源、contract/golden 再生成、受影响节点、必要测试和不需要修改 runtime/bootstrap 的情形，并经对应真实试做验证。
- [ ] 维护者明确确认四类任务和 cookbook 可按新结构完成，未解决问题有 owner 和 release 影响。
- [ ] 新增版本试做在临时试做分支或夹具完成，保留验证记录，不把演示节点或试做功能合入最终五节点支持矩阵。

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

- [04: P4 前性能发布政策确认](04-perf-release-policy.md): 维护者与脚本作者试做属于 P4 活动，开始前必须已有维护者确认的性能发布政策，避免试做引入性能相关改动时缺少裁决口径。
- [39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md): 脚本作者试做必须覆盖既有 Item/Block modification 公开行为，且该行为必须先通过专项 candidate/snapshot 验收。
- [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md): 新增事件、Adapter、扩展点和版本需要通过最终 CI/processor 延期替代 gate 验证影响面。
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

**Rationale:** 维护者与脚本作者真实任务是独立于自动化报告的人类可用性 release gate，能分别验证维护成本与公开 API 上手成本，且不引入新测试框架。

**Coordination:**

- 维护者亲自执行并确认；各域 owner 只提供入口说明和失败修复，不代替试做。

**Note:** 维护者四类真实试做与结论必须由维护者本人完成并记录，不可由 agent 代写。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
