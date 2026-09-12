# 平台构建与 Stonecutter 策略规格

Status: ready-for-agent
Type: spec

## Problem Statement

当前平台差异同时散落在共享源码守卫、机械替换、Fabric 跨节点借源、节点 override、资源与 metadata 处理以及 loader Adapter 中。维护者很难从一个入口判断某段行为的唯一源、它会被哪些节点看到、最终 class 和 jar 来自哪里。Fabric 两个 26.x 节点目前共享借源，其中一个节点没有独立源；这使“可构建”容易被误读成“已有终态 source ownership 或完整 parity”。

同时，Fabric 的 annotation processor 尚未覆盖，若在 source root 收口时顺手接入，会把延期决策偷换成实现捷径。退出 Stonecutter 也会同时触及 guards、replacements、资源模板、metadata、mixin、active-source IDE 语义和全节点验证，当前没有等价替代证据。本规格要保留并收紧 Stonecutter，明确 raw 源、节点差异、Adapter、source trace、CI 门禁和 processor 延期边界。

## Solution

保留 Stonecutter 作为变体求值、机械替换、资源/metadata/mixin 处理、active-source IDE 语义和全节点验证工具。收紧其边界：业务差异退出共享业务代码和机械替换，进入 compat facade、平台 Adapter 或节点 Implementation；每个保留节点必须能追溯到唯一源。

`common` 继续保持 Minecraft/loader-free，但允许 GraalJS。共享 MC-facing 实现只在确实共享且可由现有 facade、guard 或 Adapter 表达时进入根共享面；不可用小型 Adapter 表达的整文件差异、高湍流或原生接线进入节点实现。Fabric 共享 raw loader source root 由 Fabric convention 显式挂载，Stonecutter 不会自动预处理它。

Fabric source bridge 的删除必须经过五层 source/class/pre-dedup/jar trace、两个 Fabric 节点门禁、三个 NeoForge 节点不挂载检查、sandbox 聚合检查和 smoke。processor 在 `1.2.0` 继续延期，不以临时接入代替非 processor 的 contract/event/declaration 覆盖 gate。

## User Stories

1. 作为维护者，我希望 Stonecutter 当前继续保留并只承担已验证的构建职责，同时支持等级的 gate 差异不被解释为静默功能 parity，以便版本树不会在缺少等价证据时被替换或虚假声称完整支持。
2. 作为维护者，我希望 `common` 及其 `api.*` 保持 Minecraft/loader-free，以便跨平台引擎不会反向依赖平台实现。
3. 作为维护者，我希望 `common` 允许使用 GraalJS，以便现有运行时不需要为旧的零 Graal 假设新增 DTO、Adapter 或 API jar。
4. 作为维护者，我希望根共享 MC-facing 面只承载真正共享且可由 facade、guard 或 Adapter 表达的实现，以便共享目录不会成为差异堆积区。
5. 作为维护者，我希望无法用小型 Adapter 表达的整文件差异、高湍流和原生接线留在节点实现中，同时节点不持有第二套 runtime owner、Plugin Runtime 或业务语义，以便每个节点有清晰归属且共享所有权不分裂。
6. 作为维护者，我希望平台和版本 Adapter 只向共享 Module 提供能力、生命周期、registry、network 和 event 接线，以便共享业务逻辑不依赖平台 artifact。
7. 作为维护者，我希望共享业务逻辑只有一份，以便 guard、replacement 或节点 override 不会复制语义。
8. 作为维护者，我希望新业务差异退出机械替换和共享业务代码，以便 replacements 只服务经证明等价的版本改名与转换。
9. 作为维护者，我希望 Fabric source bridge 被明确视为当前构建事实而不是终态 source ownership，以便不把借源误当成完整实现。
10. 作为维护者，我希望每个保留节点都有可追溯的唯一源，以便 class 和 jar 内容能回溯到真实 owner。
11. 作为 Fabric 维护者，我希望两个 26.x Fabric 节点共享的 raw loader 源根由 Fabric convention 显式挂载，以便挂载关系不依赖隐式约定。
12. 作为 Fabric 维护者，我希望 Stonecutter 不自动预处理 Fabric 共享 raw 源根，以便生成、预处理和源唯一性都有明确 gate。
13. 作为维护者，我希望版本差异优先复用现有 compat facade 或节点 override，以便不为一处差异预设新的预处理器。
14. 作为维护者，我希望预处理方案未落实时明确记录为未处理或由节点 override 承担，而不是预设新的预处理器，以便输入、输出和源唯一性责任不被偷换。
15. 作为维护者，我希望 guard lint 的扫描输入在 Fabric 共享 raw 源根加入后扩大并被记录，以便新增文件不会靠改规则规避检查。
16. 作为构建证据负责人，我希望 raw 源、processed 源、编译 class、去重前打包输入和最终 jar entries 分开记录，以便 source trace 不被生成副本混淆。
17. 作为构建证据负责人，我希望七个同名 FQCN 分别记录五层来源和最终重复计数，以便唯一性和 parity 有可核验证据。
18. 作为维护者，我希望 `DuplicatesStrategy.EXCLUDE`、集合存在性、guard 数量或 source count 不能冒充唯一性证明，以便重复类不会因构建策略被掩盖。
19. 作为维护者，我希望 NeoForge 防御性排除、Fabric 禁止资源检查和 Fabric 制品验证门禁继续保留，以便收口 source root 时不会顺手削弱制品隔离。
20. 作为维护者，我希望只有两个 Fabric 节点、三个 NeoForge 节点、sandbox 聚合、fixture smoke 和迁移前后 trace 全部通过后才删除 source bridge，以便失败时可以回滚而不是丢源。
21. 作为维护者，我希望 Fabric `common-api-processor` 在 `1.2.0` 继续延期，以便未覆盖的平台模型不会通过临时接线被伪装为完成。
22. 作为 Managed Surface/Probe 维护者，我希望 processor 延期由非 processor 的 contract/spec、event/surface 和 declaration 覆盖 gate 补偿，以便每类缺口都有 owner、输入、输出和失败诊断。
23. 作为能力矩阵维护者，我希望没有测试或证据的声明记为 `not verified` 并阻塞对应域验收，以便尚未验证不会直接变成 `unavailable` 或 `partial`。
24. 作为发布负责人，我希望 CI 的 NeoForge-only、Fabric-only、全节点和 release/publish 子集按用途分别校验，以便派生 manifest 不会成为第二节点事实源。
25. 作为维护者，我希望未来只有替代方案覆盖五节点、构建、测试、artifact、metadata、mixin、processor、IDE/source trace、runtime smoke 和真实新版本接入后，才另开 Stonecutter 退出决策，以便替换风险有完整对照。

## Implementation Decisions

- 当前保留 Stonecutter，不批准替换或退出。它负责 variant evaluation、机械 replacements、资源/metadata/mixin 处理、active-source IDE 语义和全节点验证。
- `common` 包括 `api.*` 在内保持 Minecraft/loader-free，但允许 GraalJS。允许 MC/loader 依赖的共享实现留在共享 MC-facing 面或节点。
- 根共享 MC-facing 面只承载确实共享且差异可由 facade、guard 或 Adapter 表达的实现。整文件差异明显、高湍流或原生接线进入节点 Implementation；节点不持有第二套 runtime owner、Plugin Runtime 或业务语义。
- 平台/版本 Adapter 只提供明确能力、生命周期时机、registry、network 和 event 接线。`common` 不反向依赖 platform artifact；两个真实 Adapter 才建立公开 Seam。支持等级沿用 02；primary、secondary 和 experimental 的 gate 差异不得被解释为静默功能 parity。
- 新业务逻辑不得进入 replacements。loader/version 业务差异优先进入现有 compat facade、平台 Adapter 或节点 Implementation。
- Fabric source bridge 是当前构建事实，不是终态 source ownership。每个保留节点必须能追溯到唯一源。
- Fabric 共享 raw loader source root 必须由 Fabric convention 显式挂载。Stonecutter 不会自动处理该 root，也不得声称它已被自动预处理。
- 版本差异优先复用现有 compat facade 或节点 override。预处理方案未落实时只能记录为未处理或由节点 override 承担；确需预处理时，必须先证明处理输入、输出和源唯一性 gate 存在，不能预设新的预处理器。
- guard lint 会因 Fabric 共享 raw 源根扩大扫描输入。必须记录扫描文件数、新增范围和零违规结果，不修改规则规避新输入。
- 保留 Fabric convention 的 Java/resources 挂载、access widener、metadata template、JUnit、fat jar 和 artifact gate；在 source trace 完成前不移除这些能力。
- 保留 `NeoForge*.java` 防御性排除、Fabric 禁止资源检查和 Fabric 制品验证门禁。删除任一项都需要独立迁移证明。
- source trace 必须分开记录 raw source origin、processed source、编译 class、去重前打包输入和最终 jar entries，并包含重复计数。processed source 是生成证据，不是第二源码事实源。
- 七个同名 FQCN 是 `CommandEvents`、`EntityEvents`、`ItemEvents`、`LevelEvents`、`PlayerEvents`、`ServerEvents`、`client/KeyBindEvents`。每个都必须完成 raw/processed/class/pre-dedup/final-jar 来源比对和重复计数。
- 不得用去重策略、集合存在性、guard 数量或 source count 代替 origin 比对和重复计数。预处理未落实时只能记录“未处理/由节点 override 承担”，不能写成已由 Stonecutter 处理。
- Fabric runtime smoke fixture 迁入 Fabric 共享 raw 源根后必须更新全部 CI 消费者。CI 直接复制 fixture 不等于 Gradle test source set 已挂载；测试消费需要显式接线，否则必须标明仅 CI 复制。
- 删除 Fabric source bridge 的条件是：两个 Fabric 节点的编译、检查、制品验证通过；七类 origin trace 无未说明差异；最终 jar 重复计数符合预期；sandbox 聚合通过；三个 NeoForge 节点检查证明不挂载 Fabric root；新 fixture smoke 通过；迁移前后 trace 对照通过。任一条件不满足时回滚 W8，不删除 bridge。
- Fabric `common-api-processor` 在 `1.2.0` 暂不接入。当前 processor 只接受 `nf26`、`nf121`、`cr`，没有 Fabric option/scope；不得把“挂上 processor”当作延期替代品。
- processor 延期必须补齐非 processor 覆盖 gate：contract/spec、event/surface 和 declaration 分别列出 owner、输入、输出和失败诊断。该清单是拟实施验收表，不造 catalog、第二 registry path 或第二 node 事实源。
- 没有测试或证据的声明只能记为 `not verified` 并阻塞对应域验收，不能直接改判为 `unavailable` 或 `partial`。`supported`、`partial`、`unavailable` 按实际语义和证据裁定；规范 `ALL` 与实际不符时显式记录并审阅。
- CI 的 NeoForge-only NBT、Fabric-only artifact/smoke、全节点 build 和 release/publish 子集按用途分别对照节点图。manifest 是派生快照，不是第二 node 事实源；允许有意子集，但每个子集必须有用途说明和一致性检查。
- 保留 `26.2.0` 节点身份、Minecraft 26.2 坐标语义和 Fabric 26.2 制品命名，不在 W8/W9 中擅自归一化。
- 不新增 Gradle project 或 API jar，不删除 Stonecutter，不改变支持矩阵，不在 W8 顺手接入 Fabric processor。
- Stonecutter 退出只有在替代方案覆盖五个节点，并证明编译、测试、artifact/metadata/mixin、processor、IDE/source trace、runtime smoke 和一次真实新版本接入均等价或更好后，才另开决策。当前不启动替代构建。

## Testing Decisions

- 测试的最高 Seam 是 Fabric convention 的 source root Interface、节点 artifact/contract 和 CI 节点图，不测试私有预处理副本或最终 jar 中的偶然路径。
- Fabric raw root 的成功断言是它只由 Fabric convention 显式挂载，且生成证据能说明哪些内容被预处理；失败断言是 Stonecutter 被默认为自动处理、两个节点看到不同隐含源或预处理责任不清。
- guard lint 的成功断言是扩大后的扫描范围被记录且零违规；失败断言是新增文件通过修改规则逃避扫描，或 `common` 的 MC/loader 隔离被放宽。
- 两个 Fabric 节点的成功断言是编译、检查、artifact verification 和 smoke 通过；失败断言是 origin trace 有未说明差异、最终 jar 重复计数异常或 smoke 只靠静态 marker。
- 七个同名 FQCN 的成功断言是 raw、processed、class、pre-dedup 和 final-jar 来源可解释且重复计数符合预期；失败断言是依赖去重策略、集合存在性或 source count 掩盖重复来源。
- 三个 NeoForge 节点的成功断言是检查通过且证明不挂载 Fabric raw root；失败断言是 Fabric 源意外进入 NeoForge 节点或防御性排除被削弱。
- 节点实现/平台 Adapter 的成功断言是差异有唯一源、共享逻辑不复制且节点不引入第二套 runtime owner/Plugin Runtime；失败断言是 source trace 或 runtime smoke 显示重复所有权或共享语义复制。
- 删除 source bridge 的成功断言是全部删除条件同时通过且迁移前后 artifact/resource/mixin/metadata trace 等价；失败断言是任一 trace 无说明差异，此时必须回滚 W8 而不是删除 bridge。
- 非 processor 覆盖 gate 的成功断言是 contract/spec、event/surface 和 declaration 各自具备 owner、输入、输出和失败诊断；失败断言是缺项被写成已通过，或缺少证据的声明被直接改判为 `unavailable`/`partial`。
- CI 子集的成功断言是每类子集与节点图按用途一致，manifest 只作派生快照；失败断言是手写列表漏节点、用单一等值检查替代用途校验或 manifest 被当作事实源。
- `verifyFabricRuntimeArtifact`、`guardLint`、`sandboxCheck`、NeoForge NBT smoke 和 Fabric development server smoke 是现有构建/运行时 prior art；`PlatformCapabilityTest`、`ApiManifestGoldenTest` 和 `ProbeOutputCompatibilityTest` 是能力、契约和派生输出 prior art。
- 获准实施后应复用现有 JUnit、artifact gate 和 loader smoke 补足 trace/coverage 缺口，不新增全仓测试框架。
- 当前没有执行上述迁移或验证。任何“通过”描述都是待实施 gate，不是已完成结果。

## Out of Scope

- 退出、替换或删除 Stonecutter。
- 在 `1.2.0` 接入 Fabric `common-api-processor`，或用临时 processor 接线代替覆盖 gate。
- 新增 Gradle 子项目或 API jar，改变支持矩阵或节点集合。
- 在未取得实施授权时实际移动源码、删除 Fabric source bridge、修改业务逻辑或调整节点坐标与制品名。
- 承诺未经证据支持的 Fabric parity，或把 `not verified` 自动改写为 `unavailable`/`partial`。
- 创建 GitHub issue、标签镜像或新的测试框架。

## Further Notes

- 本规格派生自 [版本与加载器差异如何组织，Stonecutter 何去何从？](../decisions/03-platform-build-strategy.md)。该票的 Resolution 是裁决权威；本文件只是执行视图。
- 支持等级和节点集合见 [哪些版本与加载器组合值得持续维护？](../decisions/02-support-matrix.md) 及派生规格 [版本与加载器支持矩阵规格](./02-support-matrix.md)。
- W8/W9 的完整 owner、输入、输出、失败诊断和删除条件见 [NekoJS 实施交接单](../implementation-handoff.md)；最终发布门禁见 [验证与迁移规格](./07-validation-and-migration.md)。
- 构建与平台事实见 [构建与平台拓扑：证据报告](../evidence/build-and-platforms.md)；测试资产见 [测试、文档、CI 与可观测性证据](../evidence/testing-and-docs.md)。
- W8/W9 补充明确覆盖 Fabric raw root 显式挂载、五层 source/class/pre-dedup/jar 证据、Fabric processor `1.2.0` 延期和非 processor 覆盖 gate，不能由 03 的旧笼统表述替代。
- `Status: ready-for-agent` 只表示规格可交给代理继续处理，不构成源码移动、构建、测试、迁移或发布授权。
- 本轮只完成规格文档，没有运行构建、测试或运行时验证；所有 source trace、artifact、smoke 和覆盖 gate 均待另行授权后执行。