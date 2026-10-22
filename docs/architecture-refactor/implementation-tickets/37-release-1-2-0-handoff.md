# 37: 1.2.0 clean cutover 与发布交接

2026-10-22 后续：[最新统一候选三节点安装客户端](../evidence/2026-10-22-unified-client-native/README.md)完成 30 图和六次正常退出；[制品/数据分离回退](../evidence/2026-10-22-candidate-rollback/README.md)完成已知 `d91ca974 → 4405b0b0 → d91ca974` 与停服备份恢复四阶段、四次正常退出。换旧 JAR 保留推进数据，恢复备份才回到旧值，范围仅该已知候选对和完整世界夹具。五节点首轮安装运行四过一未过（26.1.2 召唤后实体查询未成功），该失败与后续控制实验分别保留；不能用正常退出或构建通过覆盖验收失败。最新默认完整 TSC 仍失败 5867 条；Python 默认检查仍通过。性能与最终维护者结论继续开放。

2026-10-22 最新候选 `4405b0b0` 全量矩阵通过：11m41s，106 tasks / 40 executed，5067 tests / 271 skipped / 0 failures or errors，含 common 隔离、处理器、guard、五节点 build/platform gate；矩阵 XML 与生产 class/JAR 绑定在本地冻结。另有 [legacy 安装客户端 16 阶段](../evidence/2026-10-22-posteffects-legacy-installed/README.md)通过，实际使用普通 client JAR 与 server 分类 JAR。五节点安装运行、完整 IDE、原门槛性能及分离回退仍按各自结果记录，不能用构建通过代替。

2026-10-22 补充：[双节点真实 TCP 验收](../evidence/2026-10-22-dynamic-tcp/README.md)以冻结安装候选 `d91ca974` 在 26.1.2/26.2.0 完成各八阶段、八个进程正常退出，覆盖真实双 ACK/NACK、配置补同步、超时与迟到 ACK、准备中断线和重连后的实际注册表。它不替代后续源码的统一候选、COMMIT 后激活失败、完整 TSC、最终性能、分离回退或维护者结论；失败尝试及外部清理记录已封存。

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

最终 `04550ae2` 的 [TS raw 输入修复](../evidence/2026-10-16-followup-acceptance/RAW-INPUT.md)完成 4923 tests、五节点实际运行、完整 Pyright 和严格正负例；完整 TSC 再减少 160 条 TS2314、无新增，仍失败。Java 类 Python stub 与上批逐字节一致。本批未执行 Entity Bean 删除；后续批准与独立结果见下。


用户明确批准五处 Entity 公开声明精确迁移后，最终 `e551e559` 完成 [实际验证](../evidence/2026-10-16-followup-acceptance/ENTITY-BEAN.md)：4933 tests、五节点新 JAR、完整 Pyright、严格正负例及 CI 通过，恰好删除五处派生属性、保留其他 24 处、真实方法与运行时/Python。完整 TSC 减少 1560 条且无新增，仍失败；最近正式性能及其他 release gate 继续开放。本次精确批准不代表整票维护者或发布验收。

2026-10-17 用户批准扩大 TS 声明依赖输出后，[依赖补齐报告](../evidence/2026-10-17-tsc-closeout/REPORT.md)记录：主扫描仍受原配置限制，TS 单独补齐真实引用并保留 exclude/hide、编辑后 IR 与运行时权限；4949 tests（271 skipped）、五节点实际运行、全部默认 Pyright 和定向严格类型正负例通过。完整默认 TSC 的缺失模块 TS2307 从 3433 减到 29，但诊断总数从 6991 增至 7071，完整 IDE gate 仍失败。默认依赖报告如实保留排除类型与专服不可装载类型；没有删除其他公开成员或代签维护者结论。本批不创建 PR，性能、客户端和最终候选回滚门禁继续开放。

同日继续完成 [Character 值类型投影](../evidence/2026-10-17-tsc-closeout/CHARACTER.md)：4952 tests（271 skipped）、五节点新 JAR、全部默认 Pyright 和严格正负例通过，完整 TSC 移除 204 条且没有新增，仍有 6867 条，整体验收未通过。保留原始 IR、Python 和运行时转换，记录泛型擦除与 1.21.1 旧 Guava 谓词限制；首次实机失败、旧 JAR 复现及最终各节点正负能力检查均封存，没有把原有拒绝行为宣称为已修复。不创建 PR，其他发布门禁继续开放。

2026-10-21 [Probe 共享原因与新制品复测](../evidence/2026-10-21-probe-closeout/README.md)：`dd15186c` 完成多父接口继承签名合并、显式手写 host/input 依赖和动态物品布尔输入校验，5056 tests（271 skipped）、五节点新 JAR 实机、1609 个 stub 的默认 Pyright 与定向严格消费链通过。十入口完整严格 TSC 从 6867 降至 6523，按首行诊断多重集移除 344 / 新增 0，所有 TS2320 消失；完整 IDE gate 仍失败。`ManualDeclarationCatalogEntry` 保留旧构造但增加 record 组件，六组件 record pattern 源码需要迁移，已在报告和插件指南记录。本阶段不替代剩余客户端、多客户端动态同步、最终候选性能与分离回退，不代签发布决定。

2026-10-21 [PostEffects 三节点原生补验](../evidence/2026-10-21-posteffects-native/README.md)：legacy 源码 `8798257f` 修复原生资源重载丢失效果及 stale 退役误清原版链，最终节点构建 447 tests / 31 skipped 零失败，三节点 30 张世界目标图与原生 debug 重载通过并逐张复核。legacy 两个实际红测、依赖缺失及开发警告阻挡尝试均保留；现代节点仍绑定 `dd15186c`，开发客户端与安装候选 class 匹配，不冒充正式安装客户端启动。票 28 的节点 smoke 条件已补齐；正式统一候选、多人动态 TCP、完整 TSC、性能和分离回退仍开放，不代签整票或发布验收。

2026-10-22 [静态继承与安装客户端补验](../evidence/2026-10-22-static-inheritance/README.md)：中间 JAR `d91ca974` 的完整 5066 tests / 271 skipped、五节点新安装运行与全部默认 Pyright 通过；完整 TSC 为 5867 条，移除 594 / 新增 8（SectionPos 既有 Bean/方法冲突改报 TS2430），仍失败。26.1.2 独立开发启动器已确认 NekoJS 从安装生产 JAR 加载，七阶段原图与正常退出通过。后续源码 `ad4d3750` 修复父重载编辑权威及缺失参数选择器的无操作边界，common/check 2411 tests / 4 skipped 与严格定向消费、两轴复审通过；尚未以该源码重建最终五节点候选。其他节点安装客户端、完整 IDE、真实多人动态 TCP、最终性能与分离回退继续开放。


2026-10-22 最新统一候选 `4405b0b0` 的[正式性能复验](../evidence/2026-10-22-candidate-performance/README.md)已完成：两组重载均值 348.2 / 332.2 ms，均超过原 285.3 ms 门槛；启动均值 28026.2 ms，通过 41173 ms 门槛。17 条样本有效，9 次会话均正常退出；原采样器、fixture 和门槛不变。归档复核通过只表示原始记录及实际失败可复算，不表示性能验收通过。该结果取代旧候选在本轮交接中的性能状态；仍须定位并修复当前重载超标后按同一标准复验。


2026-10-22 统一候选 `4405b0b0` 的[完整检查归档](../evidence/2026-10-22-candidate-checks/README.md)：common/check、处理器、guard 和五节点构建通过，5067 tests / 271 skipped / 零失败。安装首轮四节点通过，26.1.2 实体查询未完成；同一 JAR 的独立不暂停控制组严格全链通过，但首次原因未确诊，不能改称首轮五节点全绿。六个服务端均正常退出。1609 个 stub 的默认 Pyright 全通过；十入口默认严格 TSC 仍有 5867 条诊断、无崩溃，完整 IDE gate 继续失败。


2026-10-22 后续源码 `7bea6d6d` 的[纯源码事实复用检查](../evidence/2026-10-22-source-facts/README.md)通过 common/check、处理器、guard 及 26.1.2 build（3113 tests / 85 skipped / 零失败）；其余四节点尚未重建，不沿用旧候选结果。相同采样政策的[正式性能复验](../evidence/2026-10-22-source-facts-performance/README.md)：两组重载 340.0 / 292.8 ms，均仍超过 285.3 ms；启动 33486.6 ms，通过 41173 ms。17 条有效样本、9 次正常 RCON 退出，1640 个类及 JAR 与主检出一致。源码复用已实现，但重载性能门禁仍失败；不以第二组接近门槛或历史通过代替本轮结论。完整严格 TSC 的最近冻结结果仍为 `4405b0b0` 的 5867 条诊断，当前源码未重跑完整 IDE 与五节点运行，不声称已经完成最终统一候选。
