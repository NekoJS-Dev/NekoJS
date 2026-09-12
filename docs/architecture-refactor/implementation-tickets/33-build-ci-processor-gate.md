# 33: CI 用途子集与 Fabric processor 延期替代 gate

**What to build:** 收口构建约定与 CI：各类手写节点子集按用途核对，Fabric processor 在 1.2.0 明确延期，并用真实非 processor 的 contract/spec、event/surface 与 declaration 覆盖 gate 补足延期说明。

**Blocked by:** [32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- W9

## Acceptance criteria

- [ ] Fabric common-api-processor 在 1.2.0 保持未接入，延期原因和未覆盖范围公开，不被描述为 NeoForge processor 等价或临时接通。
- [ ] contract/spec、event/surface、declaration 三类非 processor gate 分别有 owner、输入、逐项输出和失败诊断，并能指出缺失的 contract、method、platform、domain、binding、member 或 type。
- [ ] 没有证据的项保持 not verified 并阻塞对应域验收；不得因缺测试直接改判为 unavailable 或 partial，也不得用改表掩盖规范 ALL 与实际能力的差异。
- [ ] NeoForge-only NBT、Fabric-only artifact/smoke、全节点 build、release/publish 子集分别按用途与节点事实源核对，不用单一等值检查冒充所有用途。
- [ ] 每个 CI 子集都有用途说明、节点覆盖、 intentional skip 和一致性检查；manifest 仅作为派生快照，不成为第二节点事实源。
- [ ] 五个节点的 check、artifact 与 source trace 结果进入同一报告；Fabric artifact 验证和已声明能力 smoke 不被静默省略。
- [ ] NeoForge 既有接线保持，过时 Graal lint 更新且不放宽 Minecraft/loader 隔离；不新增 Gradle project、API jar，不删除 Stonecutter，不改变支持矩阵。
- [ ] CI 或 gate 失败输出能定位节点、输入、期望结果和 owner，而不是只留下任务失败摘要。
- [ ] 本票完成表示 CI 与替代 gate 可发现并验证输入，不代表尚未迁移的新功能域已验收；后续域票提交其真实 fixture 并通过同一 gate。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md): CI 消费者、节点 source trace 和 Fabric 制品 gate 必须基于最终 raw 根与 bridge 判定结果。
- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md): 非 processor 的 contract/spec、event/surface 与 declaration gate 需要规范契约、coverage ledger、golden 和 declaration fixture 作为真实输入。

## Scope and coordination

**Rationale:** 它交付可运行的 CI/覆盖 gate 和诚实的 processor 延期结论，独立于具体功能实现且可由报告逐项验收。

**Coordination:**

- Managed Surface/Probe owner 提供契约与 declaration fixture，build owner 负责接线与报告；CI secret、runner 或共享环境只作协调，不作为 blocked by。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
