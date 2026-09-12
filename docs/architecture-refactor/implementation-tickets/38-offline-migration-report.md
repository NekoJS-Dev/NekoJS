# 38: 离线 validator / migration report（可选）

**What to build:** 显式读取已有契约、迁移表和数据保护清单，输出只读的迁移检查与缺失信息报告；不进入普通 runtime 错误路径，不自动修改脚本、数据或规范源。

**Blocked by:** [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** true

**Selected:** false

**Human input:** none

**Work items:**

- W10

## Acceptance criteria

- [ ] 默认只读且仅显式启动；故障、取消或报告生成不得改动脚本、world、pdata、pack、trust-store 或用户编辑文档。
- [ ] 报告关联已有旧/新 public symbol 迁移记录与数据保护输入，缺失信息明确标记，不编造替代接口或迁移成功。
- [ ] 相同输入产生可比报告，非法或缺少输入有普通可读错误，不静默跳过风险。
- [ ] 只有用户明确选用时才实施；不作为任何必选票或 release gate 的先决条件。
- [ ] 报告不替代必需的迁移表、旧 fixture 回读、必要迁移/回滚与 contract diff 证据。
- [ ] 验证通过文件内容或校验和不变、公开报告内容与退出状态观察，不为报告新增全仓 catalog/事务/迁移框架。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md): 报告必须消费受保护数据与旧格式的明确分类，不能自行判定哪些数据可删或可迁移。
- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md): 符号与能力报告使用同一规范源及派生输入，不建立第二份 Script API catalog。

## Scope and coordination

**Rationale:** 单独可选的只读输入到报告路径，不影响必选实现依赖图。

**Coordination:**

- 消费各域随实现更新的迁移记录；报告缺少尚未实施域的结果时必须明示。

**Note:** 本票只是随发布开放的可选票，未被选择实施；它不阻塞 release。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
