# 03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移

**What to build:** 建立必备数据保护与旧读回回归：对 config、world、实体/玩家 pdata、脚本与 pack、trust-store、用户编辑 workspace/declaration、历史日志和可再生 cache 固定旧 fixture 为事实输入，验证路径、格式、key、wire、默认启用与读写语义不被运行时重构改变；不可再生数据不删除，cache 只有来源可重建才重建。数据保护、旧数据读回和必要迁移的备份/原子替换、版本、幂等与回滚都是必备验收；只有离线 validator/report 是可选、只读且非发布 gate。

**Blocked by:** [01: P0 五节点构建与契约基线](01-build-baseline.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 建立 config、world、pdata、pack、trust-store、workspace/declaration、logs、probe/module cache 的数据清单与可再生性分类。, 用旧 fixture 在普通 reload、失败 reload、切世界、server stop 和 root close 前后比较路径、格式、key、wire 和默认启用状态。, 验证用户编辑文件不被生成物覆盖，probe/module cache 只有来源可重建且留有证据时才清理。, 把旧 fixture 回读、不可再生数据保留和必要格式迁移的备份/原子替换、schema/version、幂等、失败回滚列为必备验收，不以可选报告替代。, 记录离线 validator/migration report 的可选边界与输入要求；本票不实现该工具，实现仅由可选票 38 在明确选用后承担。

## Acceptance criteria

- [x] 基线列出的每类数据都有 owner、路径、格式/key/wire、可再生性、备份策略和旧 fixture 结论；未知项不能默认当作 cache 删除。
- [x] 普通成功 reload、失败 reload、server stop、切换世界和 root close 后，config、world、pdata、pack、trust-store、workspace/declaration 和 logs 原样可读。
- [x] 脚本 pack 的 GLOBAL/WORLD/SERVER_CACHE 路径、启用状态文件、manifest key 和默认启用规则不变；Fabric WORLD 当前行为被记录为现状差异而非被迫 parity。
- [x] probe 输出和 module cache 只有在源输入存在、可重建且报告证据成立时重建；无法证明可再生的文件保留。
- [x] 若必须迁移，迁移前生成备份或使用原子替换，写入版本标记，旧 fixture 可回读，重复运行幂等，注入失败后原始数据可回滚且旧数据保留到验证完成；这些是本票必备 gate。
- [x] 离线 validator/report 仅属于可选票 38：本票交付数据清单、fixture 和边界说明，不实现报告工具；若 38 未选用，普通执行和 release gate 均不得依赖或隐式调用它。
- [x] 票 38 的可选报告即使未来选用也必须显式只读、不进入普通 runtime 错误路径、不更新规范源、不作为硬发布 gate；必备数据保护与迁移回滚验收不得被它替代。
- [x] 数据专用迁移与回滚 fixture 通过前，不删除旧格式读取路径；无数据收益时不得引入通用 migration framework。
- [x] 所有断言通过公开文件内容、脚本读写、pack/trust 输出和 reload 结果观察，不以私有文件句柄或内部字段为契约。

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
## Closure record（2026-09-12）

- 执行者：zcode-agent；盘点与场景基线 revision：`d0aa6e0d`，fixture/运行器经 code-review 修订后
  归档于 `2b62072e`。全部运行在隔离 worktree（`NekoJS-datafix`）的 `versions/26.1.2/run` 以合成
  fixture 执行，主仓库用户数据零接触；零运行时源码修改（"默认不改"纪律）。
- 交付物：盘点 `../baseline/2026-09-12-data-protection/data-inventory.md`（17 类数据，含 owner
  代码锚点列）、fixture+运行器 `bench/datafix/`、场景证据与报告
  `../baseline/2026-09-12-data-protection/`（REPORT.md + evidence/ 42 件，会话全文 gzip 归档）。
- 场景矩阵：普通 reload / 失败 reload / server stop→重启 / 切换世界（换存档目录口径）/
  root close（并入 stop）/ 用户编辑不被覆盖 / 可再生 cache 重建，全部 pass；单人客户端形态的
  切换世界与 Fabric/非 primary 节点回读等 not-verified 项均在报告 §5 显式列出并给 owner。
- **重要发现（实码复现缺陷）**：WORLD pack 激活触发的 SERVER reload 在 Windows 相对 world 路径下
  灾难性失败（`DefaultErrorTracker.record:70` 对相对路径 `relativize` 抛 IAE → 事务 reload 失败、
  监听器清空、WORLD 包脚本不加载、原始错误被吞）。数据完整性不受影响（受保护文件逐字节不变），
  但 WORLD pack 功能语义被破坏——**归票 19/07 修复**（报告 §3-1，含归因口径与复现路径）。
- 其余缺口：除 trust-store 外无原子写/备份/schema-version（迁移票 18/19 需先落 gate）；
  joinLevel 窗口内写 pdata 静默丢弃（票 18）；Fabric WORLD 从不激活但命令文案声称查找
  `nekojs_packs`（票 19）。全部只记录未修复，符合本票"默认不改"边界。
- 阈值/边界：离线 validator/report（票 38）未实现、未依赖；未引入通用 migration framework。
- code-review（双轴）后修订：REPORT 断链/provenance/归因口径、盘点补 owner 列、fixture 删空
  监听器、runner 消除双份硬编码；切换世界以换存档目录口径补验并归档会话全文（wA/wB）。
