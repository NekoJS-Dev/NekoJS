# 32: Fabric 五层源唯一性与 bridge 删除条件

**What to build:** 用 raw、processed、class、去重前打包输入和最终 jar entries 的五层 trace 证明 Fabric 源唯一性和跨 loader 不挂载，并在全部删除条件满足后收掉 bridge；任一缺口则保留 bridge 并回滚。

**Blocked by:** [31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md)

**Status:** in-progress

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- W8

## Acceptance criteria

- [ ] raw 源、processed 源、编译 class、Jar 去重前输入和最终 ZIP entries 分层记录，生成副本不被误认为第二事实源。
- [ ] 七个同名 FQCN 在五层 trace 中均能解释 origin 与最终重复计数；去重策略、集合存在性、guard 数量或源文件数量不作为唯一性证明。
- [ ] 若某版本差异没有预处理方案，报告明确为未处理并由 compat facade 或节点 override 承担，不写成已由 Stonecutter 处理。
- [ ] 两个 Fabric 节点分别通过编译、检查、制品验证和 runtime smoke，最终 jar 的重复计数与预期一致。
- [ ] 三个 NeoForge 节点检查通过，并证明不挂载 Fabric raw 根或其生成副本。
- [ ] sandbox 聚合检查通过，且结果覆盖 guard lint 与节点检查，而不是只引用任务存在。
- [ ] 迁移前后 source、artifact、resource、mixin 和 metadata trace 等价；每个有意差异都有能力或迁移说明及 owner。
- [ ] 只有五层证据、两个 Fabric 门禁、三个 NeoForge 不挂载检查、聚合检查、smoke 与 fixture 消费者全部满足，且 bridge 依赖、引用与旧排除规则均有替代证明时，才删除 bridge。
- [ ] 任一条件不满足时 bridge 保留，迁移回滚或缺口修复路径明确，不用删除证据来源来换取收口。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md): 五层 trace、节点门禁和 bridge 删除条件必须基于已显式挂载且可构建的新 raw 根评估。

## Scope and coordination

**Rationale:** 它单独形成可判定的 bridge 删除/保留决策，避免把大规模源迁移和五层唯一性证明压进同一上下文。

**Coordination:**

- 与 CI 和 runtime smoke owner 同步 fixture 路径与证据格式；与功能域 owner 复核有意 artifact 差异。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
