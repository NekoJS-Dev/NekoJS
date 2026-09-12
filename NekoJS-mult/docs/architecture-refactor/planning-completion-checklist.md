# NekoJS 规划完成与实施验收清单

> 本清单区分“计划已可交接”和“实现已经验证”。决策正文只存于各票的 Resolution，状态以 [决策票导航](decisions/README.md) 所述查询为准；这里不维护第二份票状态表。

## 1. 什么时候算规划完成

规划完成要求：必要选择已有维护者结论，功能域有 owner/落点规则，工作单说明输入、产物、验收方法、失败处理和删除前提，文档没有互相矛盾的承诺。**不要求先实现 W0-W10 或跑完发布测试，才允许结束 wayfinder。**

反过来，写出了 gate、静态清单或代码示意，不能声称对应的构建、同步、回滚或 smoke 已通过。代码实施与发布仍按维护者要求另行开展；当前仅规划。

## 2. 规划交接检查（本阶段）

- [x] 维护者优先级、单一 runtime owner、逻辑 Module、五节点与 Stonecutter 策略有明确决策；入口见 [路线图](../architecture-refactor-map.md)。
- [x] 公开契约、语言路径、高级 Java 访问、数据保护与 clean cutover 的约束已记录；不因新功能另造全仓 catalog、API jar 或长期 shim。
- [x] 搬运域已区分事件、运行期命令与查询工具；typed Builder / JavaBean 双写法、动态注册保留与 stale 的方向见 [搬运功能适配](decisions/08-ported-features-event-surface.md)。工作名不是已实现 API。
- [x] candidate/active 状态图、generation 资源所有权、线程/重入矩阵与 watchdog 规则已在 [reload 候选契约](decisions/09-reload-candidate-state-and-thread-contract.md) 展开；不重复造第二个 runtime owner。
- [x] `global` 的候选顶层写集与不深回滚边界已由维护者确认；权威记录见下方 global 决策票的 Resolution，不在清单另存一套契约。
- [x] [跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md) 的作用域、显式共享与保留期限已经维护者确认，整票已形成 Resolution 并关闭；实施按该处唯一契约执行。
- [x] [功能覆盖账本与合同验证计划](proposal.md#25-现有功能覆盖账本迁移前必须闭合) 明确 DataMap、ScriptEvents/NativeEvents/ProbeEvents、EntitySelectors、Assets、Villager Trades、Dynamic Registry、PostEffects 等归属；保留 managed/legacy/raw-Java 的事实源区别。
- [x] [节点静态清单](baseline/node-source-artifact-manifest.md) 已按 Java/resources/templates/test 分类计数并记录声明来源、任务链与复现方法；这是 W0 的输入，不是完整 W0 构建报告。
- [x] [W8/W9 工作单](implementation-handoff.md#4-w8w9-物理构建与-ci-接线工作单) 已写清 raw root、pre-dedup origin、fixture/CI 消费者和 Fabric processor 延期的替代 gate；不假定新目录自动获得 Stonecutter 预处理。
- [x] Fabric WORLD pack 保持现状差异、processor 1.2.0 延期、P0 独立性能采样、可选只读 validator 已回写 [运行时/数据决策](decisions/05-runtime-lifecycle-and-data.md) 与 [验证/迁移决策](decisions/07-validation-and-migration.md)，无需重新提问。
- [x] 规划文档的链接、状态索引与相互引用已完成本轮有界复核；节点原始文件计数沿用前轮静态记录。文档校验不等于架构实现或发布验收。

本节规划交接条件已满足，wayfinder 达到“规划完成、可交接”的终点；下节实施证据仍待后续执行。维护者确认规划不等于授权本轮修改源码。

## 3. 实施/发布验收（后续执行；目前均未因本轮规划通过）

以下条目是 [实施交接单](implementation-handoff.md) 的证据索引，不是第二套规范或当前规划前置。

| 阶段/工作 | 责任 owner | 输入 → 必须产出的证据 | 通过/失败处理 | 当前执行状态 |
|---|---|---|---|---|
| P0 / W0 | build convention + 各功能域 owner | settings/node properties/源与资源 → 实际 source-set、generated trace、pre-dedup 输入、jar 清单、test discovered/skip/count、声明与实测差异 | 保留原始输入和生成命令；不以缓存目录或历史日志充当本轮结果 | 仅静态输入已整理；构建证据未生成 |
| P0 性能（独立于 W0） | Execution/Probe + 平台 owner | 固定负载、环境、预热、次数 → startup/probe/reload/tick/adapter/heap 基线 | 记录统计方法；P4 前由维护者基于数据确认阈值，不虚构预算 | 未运行 |
| W1/W2/W4 | Runtime Root / Plugin Runtime / Execution owner | 已定状态图与线程矩阵 → 失败保留、绑定/Handle 代际、timer/listener、close/watchdog、global 顶层写集与作用域 fixture | 失败不先清空旧 active；顶层写集按已确认事务语义验证，作用域、共享与清理按 global 票的正式结论验证 | 未实现/未运行 |
| W3/W5 | Preparation/Resolution + Managed Surface/Probe owner | 语言 corpus、normative/legacy 输入 → source-map、cache、typed member、TS/Python declaration、golden diff | 普通测试不改基线；显式再生成需差异和审阅，不将 legacy 自动升为 managed | 未实现/未运行 |
| W6/W7 | Registry Runtime + 各域/平台 owner | 覆盖账本逐行 → builder/JavaBean/definition fingerprint、事件 payload/query、交易/资源计划、packet/PData/diagnostic contract 与节点 smoke | 保持一期功能范围；不同步不宣称多人热更新成功；无证据记未验证，不假造 capability | 未实现/未运行 |
| W8 | Fabric convention owner | bridge 原始清单 → src/fabric raw root、fixture 迁移、CI 引用、raw/processed/class/pre-dedup/final-jar trace | 两 Fabric 与三 NeoForge 均验；防御性过滤独立满足删除条件后才能删 | 未迁移/未运行 |
| W9 | build convention + Managed Surface/Probe owner | settings 的按用途子集、既有 contracts/specs → CI 一致性和非 processor 覆盖报告 | Fabric processor 保持延期；不同子集不做盲目等值；缺验证不等于 unavailable | 未接线/未运行 |
| P4 / W10 | release + 各域 owner、维护者 | 所有 gate → 迁移表、旧数据读取/必要迁移与回滚、真实制品 smoke、四类维护者试做、发布报告 | 通过删除条件后移除旧 route；最后改 1.2.0；离线 validator 不替代必备材料 | 未执行 |

能力状态仅为 `supported`、`partial`、`unavailable`；`deferred` 是工作安排，`未验证` 是证据状态，两者不新增 capability 值。规划工作单不能因为“测试还没有”就宣布已有能力不存在。

## 4. 条件性后续事项

- 文件级搬迁清单、最终符号/字段名、各节点 trace 和迁移表在相应工作项中形成；一旦涉及新语义、删功能或保护边界改变，应先回到本地决策票。
- 第三方纯 Java 库仅在出现实际候选时再评估语义、许可证、体积和维护成本；当前不引入依赖。
- 性能阈值依赖 P0 数据，阶段工时依赖执行环境；记录触发时点和 owner，不要求规划阶段给出无依据数字。

## 当前结论

**规划已完成，可交接；实现与发布尚未完成。** 维护者已确认 global 的最终方案，并表示目前没有其他问题；最后一张必要决策票已闭合。在本次有界复核范围内，没有遗留必须在开始实施前再裁决的设计问题。

目标模块结构、事实源分层、功能覆盖账本、节点构建策略、迁移数据保护、实施依赖、阶段顺序与失败处理已有可交接口径。实施只有在维护者另行明确要求后才开始；获准后从既定工作单的基线采集推进，不重问已决选择。实施中若新证据揭示无法由现有 Resolution 推导的真实取舍，再回到本地决策票处理，而非无限延长当前地图。

## 前轮证据记录

- 前轮静态源清单已分类核对五节点 Java/resources/templates/test 输入；完整 W0 构建证据未生成，本轮不重复源计数。
- 前轮文档校验扫描 23 份规划 Markdown、150 条本地链接（含 2 个标题锚点）、25 张表格，未发现目标/锚点缺失或列数错误；这不是本轮修改后的校验结果。
- 所有构建、runtime smoke、性能、世界数据与迁移回滚验证仍以本清单第 3 节的实际执行状态为准。
## 本轮规划收口校验记录

- 决策 metadata：11 张 closed、0 张 open；closed 票均有 Resolution，路线图正确索引全部 11 个决策标题。
- 扫描 23 份规划 Markdown、153 条本地链接（含 2 个标题锚点）；目标与锚点缺失均为 0；25 张表格列数检查通过。
- 本轮更新 7 份规划 Markdown，并在根领域术语表补充两个已定概念；未修改 Java/Gradle/Stonecutter/CI、版本或持久化数据，原有用户文档改动保留。
- 未运行构建、runtime smoke、性能或世界数据测试；规划已闭合不代表这些后续 gate 已通过。
## 全部决策的 spec 投影

本轮全部已闭合票已转换为一一对应的 spec，入口见 [架构重构规格索引](specs/README.md)。这次是既定决策的规格化，不重新开启规划，也不开始源码重构；规格中的用户故事、实施与测试决策以源票 Resolution 为依据。

原决策仍保持 closed；本地 `ready-for-agent` 表示规格就绪，不代表本轮实施授权。规格生成后的覆盖、模板及链接复核记录见规格索引，本清单已有的构建与实施证据状态不变。
