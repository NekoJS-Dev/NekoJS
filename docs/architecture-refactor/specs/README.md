# NekoJS 架构重构规格索引

Status: ready-for-agent
Type: spec-index

本目录将本轮全部已闭合的架构决策整理为一一对应的 spec，供后续获准实施的 agent 使用。每份 spec 按 `to-spec` 模板说明问题、方案、用户故事、实施决策、测试决策、范围边界和补充说明；这次只生成规格，不实施代码。

## 阅读与权威来源

1. 从下表选择功能域，先读对应 spec。规格中的用户故事与测试要求是已确认决策的可执行投影，不是新一轮架构提案。
2. 涉及取舍、冲突或范围变化时，读其链接的源决策 Resolution；裁决仍以 Resolution 为准。语义变化先更新本地决策，再同步受影响的 spec，不能维护两套相反的契约。
3. 文件位置、迁移顺序、构建任务及删除前提查 [实施交接单](../implementation-handoff.md)。spec 不复制易过时的源码路径与命令。
4. 获得实施授权后，按 [执行说明](../next-steps-brief.md) 和既定 W0–W10 工作单推进；规格的编号不代表执行顺序，也不代表新增 Module、Gradle project 或测试框架。
5. [路线图](../../architecture-refactor-map.md) 保留决策索引，[规划完成与实施验收清单](../planning-completion-checklist.md) 区分已定规划与未生成的实施证据。[领域术语表](../../../CONTEXT.md) 提供统一词义。

## 决策到 spec 的完整覆盖

<!-- spec-coverage:start -->
| Spec | 来源决策 | 用户故事 |
|---|---|---|
| [PR 37 维护体验回归约束规格](00-pr37-maintainer-research.md) | [PR 37 暴露的维护成本与当前遗留问题是什么？](../decisions/00-pr37-maintainer-research.md) | 14 |
| [维护者模块设计与运行时所有权规格](01-maintainer-module-design.md) | [维护者的最小理解范围与目标模块归属如何确定？](../decisions/01-maintainer-module-design.md) | 21 |
| [版本与加载器支持矩阵规格](02-support-matrix.md) | [哪些版本与加载器组合值得持续维护？](../decisions/02-support-matrix.md) | 18 |
| [平台构建与 Stonecutter 策略规格](03-platform-build-strategy.md) | [版本与加载器差异如何组织，Stonecutter 何去何从？](../decisions/03-platform-build-strategy.md) | 25 |
| [公开契约与插件模型规格](04-public-contract-and-plugin-model.md) | [脚本表面与插件作者模型如何只有一个事实源？](../decisions/04-public-contract-and-plugin-model.md) | 22 |
| [运行时生命周期与数据保护规格](05-runtime-lifecycle-and-data.md) | [运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md) | 28 |
| [语言模块管线规格](06-language-module-pipeline.md) | [自研转译与模块加载应如何拆分且保持语义可控？](../decisions/06-language-module-pipeline.md) | 23 |
| [NekoJS 验证与迁移规格](07-validation-and-migration.md) | [怎样以可验证的阶段完成本次重构并作为新标准？](../decisions/07-validation-and-migration.md) | 28 |
| [NekoJS 搬运功能事件面规格](08-ported-features-event-surface.md) | [搬运功能如何适配 NekoJS 事件面与运行时扩展？](../decisions/08-ported-features-event-surface.md) | 30 |
| [reload 候选状态与线程契约规格](09-reload-candidate-state-and-thread-contract.md) | [reload 候选环境、状态所有权与线程边界如何闭合？](../decisions/09-reload-candidate-state-and-thread-contract.md) | 28 |
| [global 共享状态与候选写入规格](10-shared-global-candidate-writes.md) | [跨 reload 的 global 共享状态如何参与候选事务？](../decisions/10-shared-global-candidate-writes.md) | 28 |
<!-- spec-coverage:end -->

历史维护体验的规格记录现有扩展模型的回归约束，不把已经解决的旧问题重新列为待重造功能。其余规格分别描述各自的 Interface 与可观察结果，跨域契约通过来源链接保持一致。

## 测试 Seam

测试沿用已确认的既有最高调用者 Interface；下表用于选入口，不新建一套覆盖全仓的测试框架。相同 Seam 可承载多份规格，避免为每张票分别造桩和内部访问口。

| 行为域 | 优先测试入口 | 可观察证据 |
|---|---|---|
| 插件扩展与维护体验 | 插件贡献、bootstrap/freeze 与 Handle 消费的既有 Interface | 合法扩展能被消费，依赖/配对/冻结错误可诊断，维护任务只触及所属 Module |
| 语言与脚本执行 | 脚本输入、prepared module、require/import 与完整执行环境 | 运行结果、模块身份、source location、失效与资源释放，而非私有 cache 字段 |
| Runtime、reload 与共享状态 | eval/reload、事件与 timer 分派、关闭等现有生命周期入口 | 新旧 generation 可见性、候选失败保留、线程/重入、状态冲突与清理 |
| Managed Surface 与 Probe | normative contract 的贡献/消费及派生声明、manifest、Probe 输出 | 契约一致性、类型和错误、可审阅 golden 差异，legacy 不被静默升级 |
| Registry 与搬运功能 | 脚本事件和 typed Builder，连接既有平台/版本 Adapter | 声明计划、查询结果、激活/失败保留、stale 与同步可见性 |
| 版本树、构建与发布 | 实际节点构建、产物检查、平台启动/运行 smoke 与旧数据读写入口 | source/artifact trace、能力差异、发现/跳过测试、迁移回滚和发布材料 |

每份 spec 的 Testing Decisions 列出该域的 prior art 与具体成功/失败断言。缺少当前覆盖意味着需要后续 fixture，不意味着能力已不可用；文档中描述的测试也不代表测试已经通过。

## 状态与实施边界

- 本地 tracker 使用 `Status: ready-for-agent` 表达规格已充分说明；不创建 GitHub issue 或另一套标签系统。
- `ready-for-agent` 不是源码实施授权。用户本轮要求的是生成 spec，未要求执行重构、升级版本、安装依赖或迁移数据。
- 原决策票保持 closed，地图保持规划完成。生成 spec 不重新询问已确认的测试 Seam，也不把最终符号命名或实测证据变成新的规划阻塞。
- 实施验收、性能测量、真实节点 smoke 和数据回滚仍以工作单中的实际结果为准。新的事实若确实需要改变既定选择，再通过本地决策处理。

## 本次生成与验证

本次只生成来源可追踪的规格并接入现有导航；完成后的覆盖、模板与链接校验结果在此记录。
## 本次生成校验

- 覆盖 11/11 张已闭合决策票，生成 11 份一一对应规格，共 265 条用户故事。
- 每份规格均通过模板检查：中文 H1、`Status: ready-for-agent`、`Type: spec`、7 个指定二级标题且顺序一致、各节非空、用户故事连续编号且符合格式、无代码块。
- 规格中的源决策标题链接、内部链接和标题锚点已核对；未发现缺失目标。Implementation Decisions 未包含具体仓库路径或命令；状态机使用的状态名只描述契约，不是执行代码。
- 本次只是把已闭合 Resolution 转成派生 spec；未运行构建、测试、性能、smoke、迁移或发布，也没有把 `ready-for-agent` 当成实施授权。
