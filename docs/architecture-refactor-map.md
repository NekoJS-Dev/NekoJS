# NekoJS 架构重构路线图

## Destination

基于源码和实际维护任务，形成经人确认的详细架构与代码重构方案，含结构、契约、迁移、数据保护与验收，可交给实施。本图只做调查、规划和决策，不实施代码；只有全部必要决策闭合后才达到终点。

规划状态：已完成，可交接；非源码实施授权。

## Notes

- 本文件是本轮 wayfinder 的 canonical map。tracker 使用仓库内的本地 Markdown，不创建 GitHub 新 issue；状态、认领和阻塞关系都归档在决策票文件中。建图完成不等于整个重构方案已裁决完成；未决问题以票文件状态为准，候选结构和迁移框架不是实施授权。
- 已确认的维护优先级是：维护者 > Java 插件作者 > JS/TS 作者。当前痛点是登记与同步工作过多、抽象过多，以及归属和依赖难以追踪。
- GraalJS 固定不更换。api 包允许使用 GraalJS，撤销为独立 common-api 制品设立的零 Graal 禁令；`common`（含 `api.*`）仍禁止 Minecraft/loader 依赖，允许这些依赖的共享实现放在 `src/` 或节点。前提记录在 [脚本表面与插件作者模型如何只有一个事实源？](architecture-refactor/decisions/04-public-contract-and-plugin-model.md)。Stonecutter 可以重新评估，但本图不预先批准删除。
- 支持矩阵已裁定：NeoForge 26.1.2 为 primary，NeoForge 26.2.0 为 secondary，NeoForge 1.21.1 与两个 Fabric node 为 experimental；五个 node 均未批准 EOL。具体 capability matrix、构建和发布门槛仍由 03/07 落实。
- 目标加载器为 NeoForge + Fabric，承认两者能力差异并逐步补齐。沿用已确认的票内契约；仅在发现无法由现有 Resolution 推导的新选择时建票，不能为实施步骤或测试结果另造决策状态。
- 脚本侧需要 KubeJS 风格的便利入口，同时保留高级 Java 访问。Java 插件侧已由 04 确认 Point/Hook/Contributor/Handle 的小核心契约和 capability 分层；不引入全仓元框架。
- 本次重构完成后以 `1.2.0` 作为新标准版本，从当前 `1.1.0-preview3` 只做一次 clean cutover，不等待 2.x，也不要求按 major 语义发布。脚本和插件接口可以 breaking，但必须有迁移表；持久化数据要受保护；现有功能删减逐项经人确认；最终只保留职责清晰的单套实现，分阶段执行不能演变为长期叠加兼容层。该规则取代 ADR-0009 的旧两发布/major 假设，但不改写历史 ADR。
- 语言实现优先自研；可以比较合适的小体积纯 Java 库，但不引入非纯 Java 转译依赖。10+MB 量级被认为不合适；候选库出现后按 06 的语义 corpus、许可证、维护性和 07 的显式 gate 评估。
- 本地可信脚本与远端脚本采用显式受限授权；不承诺对任意恶意代码提供强隔离。
- 每次推进本图前应使用 domain-modeling、codebase-design、grilling 的术语和工作方式。打开 docs/architecture-refactor/decisions/ 后，按文件名字典序查询每票的 Status、Assignee 与 Blocked by，不要从本图猜测 open 票。
- 规划收口清单：[planning-completion-checklist.md](architecture-refactor/planning-completion-checklist.md)。它区分已闭合决策与待生成实施输入，不构成源码实施授权。
- 本轮维护者确认的代理路由：调查和文档修改优先委派 `deepseek-v4.1-flash-expires-on-0910` / max；只有确实极难的跨域任务才考虑 Astra / medium。主代理负责范围、证据复核和整合；复用已有调查，写集不重叠，不为无必要的重复审计消耗代理。
- 规划完成不等于实现完成：本图以选择闭合、契约/工作单可交接为终点，不以 W0-W10 构建、搬迁和 smoke 全部跑完为终点。获准实施后的正式认领、依赖、验收和关闭入口是 [实现票据索引](architecture-refactor/implementation-tickets/README.md)；W0-W10 只是物理迁移/交接辅助视图，不是第二执行队列。反过来，静态清单和已关闭票不能作为 release 验证通过的证据。
- 详细候选与证据从以下入口查阅：
  - [实现票据索引](architecture-refactor/implementation-tickets/README.md)：**获准实施后的正式认领、依赖、验收与关闭入口**
  - [架构重构方案](architecture-refactor/proposal.md)
  - [全部决策的实施规格索引](architecture-refactor/specs/README.md)
  - [实施交接单](architecture-refactor/implementation-handoff.md)
  - [实施交接简述（给下一个执行 AI）](architecture-refactor/next-steps-brief.md)
  - [PR 37 维护者体验证据](architecture-refactor/evidence/pr37-maintainer-experience.md)
  - [运行时与模块证据](architecture-refactor/evidence/runtime-and-modules.md)
  - [API 与扩展证据](architecture-refactor/evidence/apis-and-extensions.md)
  - [构建与平台证据](architecture-refactor/evidence/build-and-platforms.md)
  - [测试与文档证据](architecture-refactor/evidence/testing-and-docs.md)

## Decisions so far

- [PR 37 暴露的维护成本与当前遗留问题是什么？](architecture-refactor/decisions/00-pr37-maintainer-research.md)：历史扩展点缺少生命周期、显式依赖和产物句柄；当前 Point/Handle 已部分解决，剩余维护成本交由目标模块票继续裁定。
- [维护者的最小理解范围与目标模块归属如何确定？](architecture-refactor/decisions/01-maintainer-module-design.md)：采用逻辑深模块优先、单一运行时所有者和域内事实源；四类真实维护任务作为验收，物理 Gradle 拆分留待有实际收益时再议。
- [哪些版本与加载器组合值得持续维护？](architecture-refactor/decisions/02-support-matrix.md)：NeoForge 26.1.2 primary、NeoForge 26.2.0 secondary、NeoForge 1.21.1 与两个 Fabric node experimental；暂不退役 node。
- [运行时所有权、reload 与数据保护的契约是什么？](architecture-refactor/decisions/05-runtime-lifecycle-and-data.md)：普通 reload 只切换脚本环境，失败保留旧 runtime/state；Plugin Runtime、平台注册和持久化数据受保护，runtime/data 不另拆票。
- [版本与加载器差异如何组织，Stonecutter 何去何从？](architecture-refactor/decisions/03-platform-build-strategy.md)：保留并收紧 Stonecutter；common 禁止 MC/loader，`src/` 承载共享 MC-facing 实现，node 承载不可表达的真实差异。
- [脚本表面与插件作者模型如何只有一个事实源？](architecture-refactor/decisions/04-public-contract-and-plugin-model.md)：managed contract、legacy preview、Point/plugin contract 与 Graal/raw Java 面分层；不新增 API artifact。
- [自研转译与模块加载应如何拆分且保持语义可控？](architecture-refactor/decisions/06-language-module-pipeline.md)：Preparation、Module Resolution/Cache、Execution 三个逻辑 Module；保留全部语言，纯 Java 自研优先，不引入非纯 Java 转译依赖。
- [怎样以可验证的阶段完成本次重构并作为新标准？](architecture-refactor/decisions/07-validation-and-migration.md)：P0-P4 内部阶段，一次 `1.2.0` clean cutover；显式 golden 审阅、五节点/能力门禁、数据回滚保护和维护者试做作为交接条件。离线 validator/migration report 可选、默认只读、非硬 release gate。
- [搬运功能如何适配 NekoJS 事件面与运行时扩展？](architecture-refactor/decisions/08-ported-features-event-surface.md)：搬运功能按生命周期事件/Adapter/工具 binding 分层；Villager Trades 事件化，Dynamic Registry 事件 facade + Registry Runtime；PostEffects 分拆。
- [reload 候选环境、状态所有权与线程边界如何闭合？](architecture-refactor/decisions/09-reload-candidate-state-and-thread-contract.md)：candidate/active 按 generation 隔离，Plugin Handle 进程级、session 对象按 generation 管理；按 owner thread 串行 reload，watchdog 不自动创建第二个 active runtime。
- [跨 reload 的 global 共享状态如何参与候选事务？](architecture-refactor/decisions/10-shared-global-candidate-writes.md)：按类型隔离并保留显式共享；受管顶层写集联合交接，状态保留期限与语言全局对象分工均已确定。

## Not yet specified

本次目的地无已知未决设计雾。实测、命名和迁移产物属于实施证据，不在这里继续制造决策票；只有发现真正的新取舍时，才建具体票。

## Out of scope

- 本轮写业务代码。
- 本轮执行 `1.2.0` 发布或实际格式迁移；这些是规划收口后的实施工作，受 07 的 release gate 约束。
- 实际删功能或删版本。
- 更换 GraalJS。
- 强行做 KubeJS drop-in 兼容。
- 全量恶意代码强隔离。
- 创建 GitHub 新 issue。
- 本轮的最终目标仍是 planning，而不是实施重构。
