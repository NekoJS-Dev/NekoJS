# 维护者模块设计与运行时所有权规格

Status: ready-for-agent
Type: spec

## Problem Statement

当前仓库同时存在 Gradle 子项目、Stonecutter 节点和逻辑运行时域三种尺度，但它们经常被混称为“模块”。维护者因此很难判断一段行为的 owner 在哪里、哪个登记是事实源、依赖朝哪个方向流动，以及一次改动会影响哪些节点。两个 loader 的运行时装配高度相似，公共创建顺序和状态所有权容易分裂；部分调用者又通过公开静态 root 绕过统一入口，使所有权更难追踪。

若以目录整洁为理由继续拆 API jar、物理 Gradle 项目或中间层，登记、同步和依赖追踪成本会上升，但调用者未必获得更多能力。反过来，如果继续保留重复 manager、静态旁路和分散事实源，每次新增事件、Adapter、扩展点或版本都要跨多处修改。本规格要求用逻辑深 Module 和单一运行时所有者降低维护成本，而不是用更多物理层掩盖复杂度。

## Solution

采用逻辑深 Module 优先、物理 Gradle 拆分谨慎的目标形状。继续保留 `common` 作为跨平台引擎、`common-api-processor` 作为必须独立的注解处理器；不新增没有独立消费者、发布收益或测试收益的 API jar。运行时域可以在 `common` 内按职责形成清晰的 Module，但 Module 不等于 Gradle 子项目。

运行时最终只保留一个 `NekoRuntimeRoot` 所有者。它可以彻底调整构造器、Interface 和内部实现，但共同装配只能表现为函数或工厂，loader 继续拥有原生生命周期、事件订阅、网络和注册表时机。每个域采用单一事实源，seam 只有在删除测试或两个真实 Adapter 证明其必要后才建立；四类真实维护任务作为主要可用性验收。

## User Stories

1. 作为维护者，我希望逻辑 Module 与 Gradle 子项目是两个独立概念，以便按职责理解代码而不被目录结构误导。
2. 作为维护者，我希望每个 Module 用小 Interface 隐藏大量行为，以便调用者只需理解少量契约就能完成工作。
3. 作为维护者，我希望用“删除该 Module 后复杂度是否散回多个调用方”作为 seam 准入判据，以便不新增纯转发的 Interface。
4. 作为维护者，我希望两个真实 Adapter 或替换测试只用于证明 Interface seam，而物理 Gradle/API 拆分只在独立消费者、发布或测试收益成立时评估，以便目录整齐不会单独成为拆分理由。
5. 作为维护者，我希望继续保留 `common` 与 `common-api-processor` 的现有职责，以便跨平台引擎和注解处理器不需要重新验证新的发布拓扑。
6. 作为维护者，我希望不再为运行时域拆分新的 API jar，以便避免跨 jar 静态工厂、包拆分和重复规范源。
7. 作为维护者，我希望最终只有一个 `NekoRuntimeRoot` 运行时所有者，以便创建、reload、错误和关闭都沿同一条所有权链发生。
8. 作为维护者，我希望共同装配可以由函数或工厂承担，以便复用创建逻辑而不制造第二个运行时 owner。
9. 作为维护者，我希望 loader 继续拥有原生生命周期、事件订阅、网络和注册表时机，以便平台回调不会被错误地抽象进跨平台运行时。
10. 作为维护者，我希望删除重复 manager 容器和公开 static root 旁路，以便调用者不能绕过单一 owner 读取或替换状态。
11. 作为维护者，我希望不长期叠加 `RuntimeKernel`、`RuntimeAssembly`、万能 gateway 或 `ServiceLocator`，以便装配实现不会演变为第二套运行时。
12. 作为维护者，我希望每个域采用单一事实源而不是全仓万能 catalog，以便登记关系有明确 owner 且不会集中成新的耦合中心。
13. 作为维护者，我希望扩展点收集语义由 Point 持有、作者便利门面由 `NekoJSPlugin` 投影、配对约束由机械测试守护，以便新增通道只有一个事实源。
14. 作为维护者，我希望 managed Script API 继续以规范契约和实现映射为依据，manifest 与 probe 只作为派生物，以便生成物不会反向成为规范。
15. 作为维护者，我希望 registry descriptor 只解决已证实的 runtime sugar 与声明同步，以便窄域元信息不会被扩张为通用注册中心。
16. 作为维护者，我希望平台差异由平台 Adapter 持有，以便版本和 loader 行为有明确归属而不是散落在共享业务代码中。
17. 作为维护者，我希望新增事件、Adapter、扩展点和版本时都能从入口追到 owner、事实源、依赖方向、受影响节点和测试，以便四类真实任务验证结构是否可用。
18. 作为 Java 插件作者，我希望每个公开 Interface 明确生命周期、错误、线程、reload 和平台差异，以便正确使用它而不需要猜测内部实现。
19. 作为维护者，我希望共享业务逻辑只有一份，以便 guard、replacement 或物理拆分不会复制语义。
20. 作为维护者，我希望依赖方向保持单向，以便平台 Adapter 可以依赖共享 Module，而共享 Module 不反向依赖平台 artifact。
21. 作为测试作者，我希望在最高调用者 Interface 上验证行为，以便重构内部实现时测试仍能保护真实契约。

## Implementation Decisions

- 目标结构采用逻辑深 Module 优先、物理 Gradle Module 谨慎。`common` 继续承载跨平台引擎和运行时域，`common-api-processor` 继续作为必须独立的注解处理器。
- 不新增 API jar，也不因目录整洁新增没有真实独立消费者、发布收益或测试收益的 Gradle 子项目。
- 运行时只保留一个 `NekoRuntimeRoot`。其构造器、Interface 和内部实现可以彻底重构，但所有权必须唯一。
- 共同创建逻辑可以提取为函数或工厂；该装配实现不是第二个运行时 owner，也不改变 loader 对原生生命周期的所有权。
- 删除重复 manager/container、公开 static root 旁路和候选 runtime 的提前发布路径；调用者只能经单一 owner 的 Interface 使用运行时。
- 不建立 `RuntimeKernel`、`RuntimeAssembly`、万能 gateway 或 `ServiceLocator` 的长期叠加层。只有在现有 owner 无法表达真实契约时才调整 Interface。
- 域内事实源固定为：Point 持有扩展点收集语义，`NekoJSPlugin` 是其作者便利投影，配对测试约束机械关系；managed Script API 以规范契约与实现映射为依据，manifest/probe 为派生物；registry 只用窄域 descriptor 解决已证实的同步；平台差异由平台 Adapter 持有。
- 不创建全仓万能 catalog。登记应尽量靠近其域 owner，并明确谁负责收集、谁负责合并、谁负责发布结果。
- 深 Module 准入采用两项判据：删除该 Module 后复杂度会散回多个调用方，或已有两个真实 Adapter/替换测试需要一个 seam。两者都不成立时不提取 Interface。
- Interface 必须写明生命周期、错误模式、线程与 reload 约束和平台差异；只冻结调用者需要知道的事实，不冻结私有类分布。
- 依赖方向保持单向：`common` 含 `api.*` 时禁止 Minecraft/loader 依赖，但允许 GraalJS；平台 Adapter 和节点实现依赖共享 Module，共享 Module 不反向依赖平台 artifact。
- 只有确实共享且可由 facade、guard 或 Adapter 表达的 MC-facing 实现才进入共享 MC 面；无法用小型 Adapter 表达的节点差异由节点 Implementation 承担。任何承载方式都不得复制共享业务逻辑。
- 新增事件、Adapter、扩展点和版本是四类主要维护任务。每类都必须能说明新工作落在哪里、同步哪些事实源、依赖方向如何验证、影响哪些节点以及由哪些测试保护。
- 物理拆分只在独立消费者、独立发布或独立测试收益成立时评估；当前不以“更清晰的目录”作为拆分依据。
- 本规格不裁定 reload 重建范围、节点生命周期、Stonecutter 终局、公开签名、数据迁移或语言前端替换；这些服从对应已闭合 Resolution。

## Testing Decisions

- 测试优先穿过最高调用者 Interface。运行时所有权在 `NekoRuntimeRoot` 的生命周期 Interface 上验证，不在私有装配方法或重复 manager 上验证。
- 成功断言是 loader 启动、CLIENT、初始化后、reload 和 close 只经一个 owner 发生，且共享装配不产生第二套状态；失败断言是 static 旁路仍可取得状态、同一次生命周期回调重复发生或失败后旧 runtime/state 丢失。
- `ScriptReloadRegressionTest` 是 runtime 生命周期与失败保留的 prior art；`PlatformConcurrencyTest` 是单次初始化和竞态语义的 prior art。二者继续验证外部可观察结果，不冻结私有对象身份。
- `PluginHookPairingTest` 与 `NekoPluginBootstrapV2Test` 是 Point、投影、依赖和结果句柄的 prior art。成功断言是配对完整、依赖有序、产物经句柄取得；失败断言是事实源分裂、环依赖、未知依赖或静态全局旁路。
- `ApiManifestGoldenTest` 是规范契约与派生 manifest 关系的 prior art。成功断言是规范源变化产生确定性的派生输出；失败断言是派生文件反向决定运行时行为或普通测试写入基线。
- `checkCommonIsolation` 继续作为依赖方向的最高静态 Seam：成功断言是 `common` 不出现 Minecraft/loader import，同时不错误禁止 GraalJS；失败断言是平台类型反向进入共享引擎。
- 四类维护任务试做是端到端验收，不新增测试框架。成功标准是维护者能从入口找到 owner、事实源、依赖方向、受影响节点和测试；需要跨处猜测或复制共享逻辑时视为失败。
- 当前没有直接覆盖全部 `NekoRuntimeRoot` 所有权的单测，这是获准实施后的测试缺口。补测应落在现有生命周期 Interface 和现有 JUnit 资产上，不建立新的全仓测试框架。

## Out of Scope

- 因目录偏好新增 Gradle 子项目、API jar 或长期中间层。
- 把 `NekoRuntimeRoot` 重命名为新的第二 owner，或保留静态旁路与旧 owner 并行。
- 重开已闭合的公开契约、reload、节点生命周期、语言管线、数据迁移和 Stonecutter 方向。
- 创建 GitHub issue、标签镜像或新的测试框架。

## Further Notes

- 本规格派生自 [维护者的最小理解范围与目标模块归属如何确定？](../decisions/01-maintainer-module-design.md)。该票的 Resolution 是裁决权威；本文件只是执行视图。
- 历史维护痛点见 [PR 37 暴露的维护成本与当前遗留问题是什么？](../decisions/00-pr37-maintainer-research.md) 和 [PR #37 维护者体验与架构证据](../evidence/pr37-maintainer-experience.md)。
- 相关派生规格包括 [PR 37 维护体验回归约束规格](./00-pr37-maintainer-research.md)、[公开契约与插件模型规格](./04-public-contract-and-plugin-model.md)、[运行时生命周期与数据规格](./05-runtime-lifecycle-and-data.md) 和 [平台构建与 Stonecutter 策略规格](./03-platform-build-strategy.md)。
- 实施顺序、物理落点和删除条件见 [NekoJS 实施交接单](../implementation-handoff.md)；最终门禁见 [验证与迁移规格](./07-validation-and-migration.md)。
- `Status: ready-for-agent` 只表示规格可交给代理继续处理，不构成源码、测试、构建、迁移或发布授权。
- 获准实施后，本规格范围内的源码重构、验证与验收属于执行内容；本轮只完成规格文档，没有运行构建、测试或运行时验证，通过条件均为待验证契约。