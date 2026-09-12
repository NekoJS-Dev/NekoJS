# 03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移

**What to build:** 建立必备数据保护与旧读回回归：对 config、world、实体/玩家 pdata、脚本与 pack、trust-store、用户编辑 workspace/declaration、历史日志和可再生 cache 固定旧 fixture 为事实输入，验证路径、格式、key、wire、默认启用与读写语义不被运行时重构改变；不可再生数据不删除，cache 只有来源可重建才重建。数据保护、旧数据读回和必要迁移的备份/原子替换、版本、幂等与回滚都是必备验收；只有离线 validator/report 是可选、只读且非发布 gate。

**Blocked by:** [01: P0 五节点构建与契约基线](01-build-baseline.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 建立 config、world、pdata、pack、trust-store、workspace/declaration、logs、probe/module cache 的数据清单与可再生性分类。, 用旧 fixture 在普通 reload、失败 reload、切世界、server stop 和 root close 前后比较路径、格式、key、wire 和默认启用状态。, 验证用户编辑文件不被生成物覆盖，probe/module cache 只有来源可重建且留有证据时才清理。, 把旧 fixture 回读、不可再生数据保留和必要格式迁移的备份/原子替换、schema/version、幂等、失败回滚列为必备验收，不以可选报告替代。, 记录离线 validator/migration report 的可选边界与输入要求；本票不实现该工具，实现仅由可选票 38 在明确选用后承担。

## Acceptance criteria

- [ ] 基线列出的每类数据都有 owner、路径、格式/key/wire、可再生性、备份策略和旧 fixture 结论；未知项不能默认当作 cache 删除。
- [ ] 普通成功 reload、失败 reload、server stop、切换世界和 root close 后，config、world、pdata、pack、trust-store、workspace/declaration 和 logs 原样可读。
- [ ] 脚本 pack 的 GLOBAL/WORLD/SERVER_CACHE 路径、启用状态文件、manifest key 和默认启用规则不变；Fabric WORLD 当前行为被记录为现状差异而非被迫 parity。
- [ ] probe 输出和 module cache 只有在源输入存在、可重建且报告证据成立时重建；无法证明可再生的文件保留。
- [ ] 若必须迁移，迁移前生成备份或使用原子替换，写入版本标记，旧 fixture 可回读，重复运行幂等，注入失败后原始数据可回滚且旧数据保留到验证完成；这些是本票必备 gate。
- [ ] 离线 validator/report 仅属于可选票 38：本票交付数据清单、fixture 和边界说明，不实现报告工具；若 38 未选用，普通执行和 release gate 均不得依赖或隐式调用它。
- [ ] 票 38 的可选报告即使未来选用也必须显式只读、不进入普通 runtime 错误路径、不更新规范源、不作为硬发布 gate；必备数据保护与迁移回滚验收不得被它替代。
- [ ] 数据专用迁移与回滚 fixture 通过前，不删除旧格式读取路径；无数据收益时不得引入通用 migration framework。
- [ ] 所有断言通过公开文件内容、脚本读写、pack/trust 输出和 reload 结果观察，不以私有文件句柄或内部字段为契约。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [01: P0 五节点构建与契约基线](01-build-baseline.md): 数据保护必须以旧路径、旧格式、旧 key、旧 wire 和旧 fixture 输入为比较基线。

## Scope and coordination

- **Rationale:** 数据保护可以用旧 fixture 和公开数据结果先行回归，不需要等待新 root；后续 DATA_SYNC、PACK_TRUST 等改数据或传输的票消费并重跑这些保护验收，最终新 root 生命周期下的数据验收由主整合另行闭合。
- **Coordination:**
  - PACK_TRUST、DATA_SYNC 与 MANAGED_SURFACE/workspace 组共享数据清单和用户编辑文件判断；以协调避免重复写入，不把本票变成发布 gate。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
