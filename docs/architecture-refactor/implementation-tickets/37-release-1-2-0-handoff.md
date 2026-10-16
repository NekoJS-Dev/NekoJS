# 37: 1.2.0 clean cutover 与发布交接

**What to build:** 在全部 P4 gate 通过后汇总公开迁移材料，核对各域旧路径已按删除条件收缩，完成最终版本切换与本地候选制品准备；仅处理已证明无调用者的少量残余过渡项，不在验收后启动大范围清理。任何影响候选制品的清理或版本包装后，重新验证确切产物；不执行远程上传。

**Blocked by:** [34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[35: P4 性能复测与政策对照](35-release-perf-compare.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)

**Status:** in-progress（2026-10-16：A15/A16/A22 精确删除已批准并验证；完整 IDE、客户端首帧、确切候选性能与回滚验收仍开放）

**Assignee:** main-session agent（stonecutter；写集：必要源码/测试修复、迁移与数据保护交接、票据和证据记录）

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示本地候选制品、迁移材料、验证证据和交接清单可由 agent 准备；每个 public breaking symbol 的维护者确认、最终发布/回滚策略确认和正式发布授权是不可代答的后续门禁。agent 不得替维护者 sign-off、执行远程上传或渠道公告，也不得勾选依赖维护者确认的验收项，且不因此把本票改判为 `ready-for-human`。

**Work items:**

- P4
- W10

## Acceptance criteria

- [ ] 每个 Script/Plugin public breaking 符号都有旧写法、1.2.0 新写法、替代路径、数据影响和维护者确认；没有迁移项被可选工具替代。
- [x] 数据保护清单覆盖 config、world、实体/玩家 pdata、脚本与 pack、trust-store、workspace/declaration、日志与 cache 的可再生性、备份、保留和回滚。【2026-10-07：`evidence/ticket37-autonomous-closeout/DATA-PROTECTION.md`；当前路径/owner核对及bounded rehearsal，未冒充实际用户数据恢复。】
- [ ] release rollback 与 data rollback 分开验证；旧制品回退不宣称恢复脚本对外部世界、网络或 Java 对象造成的副作用。
- [ ] 必要数据迁移具备备份或原子替换、版本/schema 标记、旧 fixture 回读、幂等验证、失败恢复和原始数据保留证据；无必要变化的默认路径、key、wire id、格式和启用规则未被暗中改变。
- [ ] 各功能域迁移票已按自身删除条件移除旧 public route、compat shim、重复 runtime path 或第二语义 pipeline，并完成对应域验证；本票只复核无残留，并只删除已被调用者证据证明为无调用者的小量过渡项，不在验收后临时扩大代码清理。
- [ ] 若最终清理、版本切换或发布包装改变确切候选制品，则对该候选制品重新执行必要 build、artifact、metadata、runtime smoke 和性能相关验证；旧制品证据不能冒充最终证据。
- [ ] README、wiki、ADR 与支持/能力矩阵的发布说明一致，公开说明 Fabric processor 延期、WORLD pack 差异和其他 partial/unavailable 能力。
- [ ] 各 API/功能域的最小可运行示例、迁移材料和 cookbook 已随对应实现票交付并通过验收；本票只汇总、核对链接和修正发布索引，不首次补写域内材料。
- [x] 可选离线 validator/migration report 保持显式运行、默认只读且不是 release blocker，也不进入普通 runtime 错误路径或第二套 Script API。【票38技术验收，现有CLI3tests再次通过；无runtime接线。】
- [ ] 发布产物在本地完成准备、校验和交接清单核对；远程上传、渠道公告或正式发布动作未在本票执行，需维护者另行明确授权。

## Current handoff record

2026-10-16 [泛型适配器实际复测](../evidence/2026-10-16-followup-acceptance/GENERIC.md)：`f3f32a69` 保留泛型/bound 和编辑后 IR；完整构建 4913 tests（271 skipped，0 failures/errors）、五节点新 JAR 实机、默认全部 Pyright 与定向严格 TSC 通过。完整默认 TSC 移除 40 条裸泛型错误但新增 930 条约束错误，净增 890，未按完整声明或无新增诊断验收。原始失败和 `$Bat`/`$Entity` Bean/方法投影链保留，继续开放。

当前交接沿用[本地清单](../evidence/2026-10-06-ticket37-handoff/README.md)和[技术收尾计划](../evidence/ticket37-autonomous-closeout/WORKPLAN.md)。票 36 维护者试做与票 48 JSX proof 是已完成输入。

2026-10-15 A15/A16/A22 的精确范围已获维护者明确批准；替代、消费者迁移和五节点官方 JAR 实机通过后删除，见[领域切换报告](../evidence/2026-10-15-domain-closeout/REPORT.md)。A28 既有精确追认继续有效，不重复申请。该批准不包含正式发布和最终回滚政策。

[当前性能索引](../evidence/ticket37-current-perf/README.md)中的 e473 源码组 startup 17718.2 ms、reload 214.4/228.4 ms 均通过；b7 的 320.6/327.2 ms 失败组保留为历史记录。e473 结果不证明本轮改动后的候选性能。本轮五节点安装包的 Python stub AST 全通过；完整默认严格 TSC/Pyright 的结果与诊断另列于领域报告，不以定向类型测试通过替代。历史 Python 语法失败已由后续修复取代，但原始失败证据保留。

2026-10-16 [复核修复与完整声明验收](../evidence/2026-10-16-followup-acceptance/REPORT.md)：重复动态物品声明保留既有 holder 组件，新旧 JAR 实机对照成立；插件同名 Builder 嵌套属性改用唯一导出别名。修复 Python 同类实例字段/方法重名后，五节点实际全部 stub 的默认 Pyright 均为 0 error / 0 warning，完整构建和五节点新 JAR 实机均通过。严格 TSC 仍有上轮同量诊断，完整 IDE gate 未通过。

[分离回退的四阶段重启](../evidence/2026-10-16-followup-acceptance/ROLLBACK.md)通过：旧 JAR 启动推进后的世界仍读取新 pdata/方块状态，恢复完整停机备份后实际读取回到旧状态。该证据限定领域切换时的指定 JAR、26.1.2 专属完整世界；不证明其他节点、未知旧制品来源或最终发布候选的恢复政策，因此不据此代签最终 release rollback gate。

[26.2 首帧像素验收](../evidence/2026-10-16-followup-acceptance/FRAME.md)已补：资源重载前后和恢复后的首个完成渲染的世界帧，以及失败后未再次 set 的保留画面均有实际 PNG、帧边界和 ROI 证据。仅覆盖 .75 / 0965 指定制品的 render target，不代替其他客户端节点或屏幕 present。

[0965 当前性能复测](../evidence/2026-10-16-followup-acceptance/PERFORMANCE.md)：startup 28118.4 ms 通过，reload 408.6/431.8 ms 超过原 285.3 ms 门槛，候选仍阻塞。旧通过 e473 在当前环境的两组对照也为 386.4/404.4 ms；全部样本、正常退出与定向 JFR 已归档，尚未建立单一源码原因，不以环境推测或历史通过结果消除当前失败。

跨进程动态同步、票 28 多节点客户端首帧、完整 TSC、确切候选性能、最终候选的 release rollback/data rollback 和最终发布/回滚策略仍按各自 gate 核验；领域服务器验收不外推为发布通过。

[枚举继承修复与实际声明复测](../evidence/2026-10-16-followup-acceptance/ENUM.md)：`489b4a6b` 的五节点新 JAR 实机、完整构建和全部默认 Pyright 通过。完整严格 TSC 的 server/startup 诊断分别为 572/573、725/726、619/620、1148/1149、903/904，十入口共减少 160 条且无新增诊断，仍未通过。所有 Java 类 Python stub 字节一致；入口/事件的顺序和本地导入别名差异保留，不能据此宣称完整输出确定性已通过。草稿 PR 为 [#67](https://github.com/NekoJS-Dev/NekoJS/pull/67)，保持草稿且不执行正式发布。

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

后续入口路径修复 `6faed0e9` 及正式性能复测见 [报告](../evidence/2026-10-16-followup-acceptance/RESOLVER.md)。4916 tests、五节点实机及 Pyright 通过，完整 TSC 保持失败；重载 335.4/376.6ms 超过原阈值 285.3ms，启动通过。票据继续开放，没有代签维护者或发布验收。

`569376c6` 恢复擦除返回值的接口契约：[五节点报告](../evidence/2026-10-16-followup-acceptance/RETURN-CONTRACTS.md)。4921 tests、实际运行及 Pyright 通过，完整 TSC 无新增并减少 118 条 TS2314，仍未通过整体验收；Python 普通 host 泛型实参投影仍有限制。
