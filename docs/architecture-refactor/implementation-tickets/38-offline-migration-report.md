# 38: 离线 validator / migration report（可选）

**What to build:** 显式读取已有契约、迁移表和数据保护清单，输出只读的迁移检查与缺失信息报告；不进入普通 runtime 错误路径，不自动修改脚本、数据或规范源。

**Blocked by:** [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)

**Status:** in-review

**Assignee:** main-session agent

**Optional:** true

**Selected:** true

**Human input:** none

**Work items:**

- W10

## Acceptance criteria

- [x] 默认只读且仅显式启动；故障、取消或报告生成不得改动脚本、world、pdata、pack、trust-store 或用户编辑文档。——`scripts/offline-migration-report.mjs` 只读取两个显式路径，不提供写入或迁移分支；测试比较运行前后输入文件内容。
- [x] 报告关联已有旧/新 public symbol 迁移记录与数据保护输入，缺失信息明确标记，不编造替代接口或迁移成功。——报告解析 Markdown 表格，输出 `migration.associations`（old/replacement/missing）以及 `protection.topics`/`missing`；缺少迁移表、old/replacement 列或数据保护主题会显式进入 `missing`。focused test 固定完整关联结果。
- [x] 相同输入产生可比报告，非法或缺少输入有普通可读错误，不静默跳过风险。——JSON 输出由 focused test 做两次字节级比较；参数、缺失文件和空文件均返回状态 2 并输出可读错误。
- [x] 只有用户明确选用时才实施；不作为任何必选票或 release gate 的先决条件。——本次由用户明确请求实施；CLI 无默认启动入口，票据仍为 Optional。
- [x] 报告不替代必需的迁移表、旧 fixture 回读、必要迁移/回滚与 contract diff 证据。——报告固定输出警告，明确要求人工检查这些证据。
- [x] 验证通过文件内容或校验和不变、公开报告内容与退出状态观察，不为报告新增全仓 catalog/事务/迁移框架。——focused test 验证输入内容不变；实现仅使用 Node 标准库。

## Delivery record（2026-10-05）

- Added `scripts/offline-migration-report.mjs`: explicit `--migration`/`--protection` inputs, deterministic text/JSON output, read-only behavior, ordinary exit-2 errors, explicit old/replacement association records, data-protection topic coverage, and explicit warnings that the report is not a migration or rollback.
- Added `scripts/offline-migration-report.test.mjs`: deterministic output, read-only input preservation, old/replacement association, protection-topic completeness, malformed/empty input, and missing-input failure coverage.
- Verification: `node --test scripts/offline-migration-report.test.mjs` passed (3 tests).
- The report remains an evidence summarizer, not a migration executor or rollback validator; it does not claim those operations succeeded.


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
