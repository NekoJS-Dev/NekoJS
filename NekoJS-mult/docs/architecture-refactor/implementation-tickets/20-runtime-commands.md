# 20: 管理命令权限、生命周期入口与阶段诊断结果

**What to build:** 闭合 /nekojs 管理入口的权限与生命周期消费：reload/test/error/packs/trust 在两个 loader 上保持旧 fixture 证明的现行 gamemaster 权限与各自能力子集，统一经 NekoRuntimeRoot 调度与结果对象执行；命令输出成功、失败、候选保留、active 隔离等待显式 reload、TEST 未配置和 wrong distribution 的稳定状态。诊断只覆盖生命周期阶段与错误结果，不新增 dashboard、telemetry 或 workspace。

**Blocked by:** [19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 保持 /nekojs 现行 gamemaster 权限口径，在两个 loader 上统一 reload/test/error/packs/trust 的入口检查和拒绝输出。
- 把命令执行改为调用 root 生命周期结果，而不是读取 static root 或 ScriptManager；命令线程进入对应 owner 队列。
- 输出成功、失败、隔离等待显式 reload、TEST 未配置、wrong distribution 等稳定结果；诊断包含 generation/phase/source/owner 摘要。
- 保留 Fabric 文本错误与 NeoForge既有错误面差异，不实现 dashboard、workspace、telemetry 或客户端显示域。
- 与 pack trust、错误面板和 workspace 组只共享结果 DTO/文本构造，不建立第二诊断框架。

## Acceptance criteria

- [ ] NeoForge 与 Fabric 的 /nekojs 生命周期、packs、trust、test 和 error 子命令保持 gamemaster 以上可执行，无权限者得到明确拒绝且不触发 reload。
- [ ] reload/test 命令只经唯一 root 入口执行，不再读取公开 static root；命令线程按 ScriptType 进入 owner 队列。
- [ ] 成功 reload 输出类型与提交结果；失败输出 phase/source 摘要并明确 active 已保留或需显式 reload，不把 Throwable 栈直接当用户契约。
- [ ] active watchdog 隔离后的 reload 命令尝试显式创建 candidate；candidate 失败时仍保持隔离/旧 active 状态，不自动二次恢复。
- [ ] TEST 未配置、SERVER 命令在客户端侧、CLIENT 命令在专用服务器等边界有稳定错误，不发生半初始化 manager。
- [ ] 错误命令只展示 root ErrorSnapshot/阶段结果；Fabric 文本降级与 NeoForge 现有面板差异保持显式，不新增 dashboard。
- [ ] packs/trust 命令分别呈现 PACK_TRUST 结果，不改变 pack 启用状态文件或信任决策语义。
- [ ] 直接 static root 命令助手和重复 reload 结果包装在两 loader fixture 通过后删除；Fabric 独立命令子集在缺失 feature 组闭合前不被强行合并。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md): packs/trust 子命令实际消费 pack 列表、启用状态、trust 决策和失败结果。

## Scope and coordination

**Rationale:** 命令是 runtime 生命周期最外部的可观察入口；单独闭合权限、owner 调用和阶段结果可以避免把错误 UI、workspace 或 feature 重放混入 runtime 票。

**Coordination:**

- 独立 diagnostic/telemetry/workspace/dashboard 由 language-surface 组负责；本票只暴露生命周期错误结果。
- PACK_TRUST 提供 trust/packs 行为结果；共享命令树文件按冲突协调。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
