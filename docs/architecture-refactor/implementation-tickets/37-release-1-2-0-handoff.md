# 37: 1.2.0 clean cutover 与发布交接

**What to build:** 在全部 P4 gate 通过后汇总公开迁移材料，核对各域旧路径已按删除条件收缩，完成最终版本切换与本地候选制品准备；仅处理已证明无调用者的少量残余过渡项，不在验收后启动大范围清理。任何影响候选制品的清理或版本包装后，重新验证确切产物；不执行远程上传。

**Blocked by:** [34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[35: P4 性能复测与政策对照](35-release-perf-compare.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示本地候选制品、迁移材料、验证证据和交接清单可由 agent 准备；每个 public breaking symbol 的维护者确认、最终发布/回滚策略确认和正式发布授权是不可代答的后续门禁。agent 不得替维护者 sign-off、执行远程上传或渠道公告，也不得勾选依赖维护者确认的验收项，且不因此把本票改判为 `ready-for-human`。

**Work items:**

- P4
- W10

## Acceptance criteria

- [ ] 每个 Script/Plugin public breaking 符号都有旧写法、1.2.0 新写法、替代路径、数据影响和维护者确认；没有迁移项被可选工具替代。
- [ ] 数据保护清单覆盖 config、world、实体/玩家 pdata、脚本与 pack、trust-store、workspace/declaration、日志与 cache 的可再生性、备份、保留和回滚。
- [ ] release rollback 与 data rollback 分开验证；旧制品回退不宣称恢复脚本对外部世界、网络或 Java 对象造成的副作用。
- [ ] 必要数据迁移具备备份或原子替换、版本/schema 标记、旧 fixture 回读、幂等验证、失败恢复和原始数据保留证据；无必要变化的默认路径、key、wire id、格式和启用规则未被暗中改变。
- [ ] 各功能域迁移票已按自身删除条件移除旧 public route、compat shim、重复 runtime path 或第二语义 pipeline，并完成对应域验证；本票只复核无残留，并只删除已被调用者证据证明为无调用者的小量过渡项，不在验收后临时扩大代码清理。
- [ ] 若最终清理、版本切换或发布包装改变确切候选制品，则对该候选制品重新执行必要 build、artifact、metadata、runtime smoke 和性能相关验证；旧制品证据不能冒充最终证据。
- [ ] README、wiki、ADR 与支持/能力矩阵的发布说明一致，公开说明 Fabric processor 延期、WORLD pack 差异和其他 partial/unavailable 能力。
- [ ] 各 API/功能域的最小可运行示例、迁移材料和 cookbook 已随对应实现票交付并通过验收；本票只汇总、核对链接和修正发布索引，不首次补写域内材料。
- [ ] 可选离线 validator/migration report 保持显式运行、默认只读且不是 release blocker，也不进入普通 runtime 错误路径或第二套 Script API。
- [ ] 发布产物在本地完成准备、校验和交接清单核对；远程上传、渠道公告或正式发布动作未在本票执行，需维护者另行明确授权。

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

- [34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md): 五节点、能力、契约、smoke 和 coverage ledger 的最终 release 判定必须已通过。
- [35: P4 性能复测与政策对照](35-release-perf-compare.md): 最终发布必须具备按已确认政策完成的 P4 复测、基线对照和失败处置结论。
- [36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md): 维护者四类任务、脚本作者代表性任务和维护 cookbook 是 release gate。

## Scope and coordination

**Rationale:** 它是所有技术 gate 之后的单一发布交接边界，能完整审计 clean cutover，同时明确不自动获得远程发布授权。

**Coordination:**

- 主整合 owner 汇总本票 Blocked by 已列出的真实先决票并提供各域完成证据；维护者审阅迁移、阈值、试做和最终发布决定。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
