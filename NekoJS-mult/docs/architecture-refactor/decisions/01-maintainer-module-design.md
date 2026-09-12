# 维护者的最小理解范围与目标模块归属如何确定？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: session-0ccab587-1d9c-436f-b53b-a6428bdb1aaf (主 agent，与维护者共同裁决)
Blocked by: [PR 37 暴露的维护成本与当前遗留问题是什么？](00-pr37-maintainer-research.md)

## Question

维护者的最小理解范围与目标模块归属如何确定？

需要在以下四类工作流中划出维护者可理解、可追踪的最小范围：新增事件、增加 adapter、增加扩展点、增加版本。问题维度包括：

- 深模块相对于物理 Gradle 模块分别带来什么 leverage、locality 和测试收益；哪些模块拆分只是增加登记与同步成本。
- 目标包、模块和依赖图怎样表达归属与依赖的唯一事实源，维护者如何从入口追到实现、版本差异和登记位置。
- 如何禁止没有实际收益的 API 再拆分，同时保留确有两个 adapter 或真实差异时的 seam；“删除该模块后复杂度是否会散回调用方”应如何成为可审查判据。
- 四个工作流各自的验收应回答什么：新工作落在哪里、需要同步哪些事实源、依赖方向怎样验证、维护者需要先理解哪些概念。

证据入口：[proposal.md](../proposal.md)、[runtime evidence](../evidence/runtime-and-modules.md)、[APIs evidence](../evidence/apis-and-extensions.md)。[ADR-0007](../../adr/0007-module-boundaries.md) 是历史模块边界材料；本票必须明确哪些约束仍成立、哪些需要重新打开，不能把 ADR-0007 的现状直接当作本票结论。

## Resolution

### 已裁定

1. **目标形状采用逻辑深模块优先、物理 Gradle 模块谨慎。** 继续保留 common 作为跨平台引擎、common-api-processor 作为必须独立的注解处理器；不因目录整洁重新拆一个没有真实独立消费者、发布收益或测试收益的 API jar。运行时域可以在 common 内按职责形成清晰 Module，但 Module 不等于 Gradle 子项目。
2. **运行时所有权沿用 NekoRuntimeRoot 的职责并允许彻底重构。** 它可以大幅调整构造器、接口和内部实现；最终只保留一个运行时所有者，删除重复 manager 容器和公开 static root 旁路。不得为了改名或分层长期叠加 RuntimeKernel、RuntimeAssembly、万能 gateway 或 ServiceLocator；若共同创建逻辑需要函数或工厂，它只是装配实现，不是第二个运行时所有者。loader 仍负责原生生命周期、事件订阅、网络和注册表时机。
3. **登记采用域内单一事实源，禁止全仓万能 catalog。** Point 文件负责扩展点收集语义，NekoJSPlugin 是作者便利投影，配对测试负责机械约束；managed Script API 继续以规范性 contract 加实现映射为依据，manifest/probe 是派生物；registry 只允许窄域 descriptor 解决已证实的 runtime sugar 与声明同步；平台差异由平台 Adapter 持有。
4. **四类真实维护任务作为主要可用性验收。** 新增事件、新增 Adapter、新增扩展点、新增版本都必须能从维护者入口追到 owner、事实源、依赖方向、受影响节点和测试；不能只用目录数量或新增一层 Interface 宣称完成。
5. **深模块的准入判据固定。** 只有当模块删除后复杂度会散回多个调用方，或已有两个真实 Adapter/替换测试需要一个 seam，才提取 Interface；Interface 必须写清生命周期、错误、线程/reload 和平台差异。通过 guard/replacement 或构建拆分表达真实差异时，不得复制共享业务逻辑。

### 依据与边界

结论依据 NekoRuntimeRoot.java:20-35,45-115 已有的 lifecycle surface、两个 loader 在 NekoJSMod.java:142-181 与 NekoJSFabricMod.java:132-175 的同构装配、ADR-0007 对无收益 common-api 拆分的记录，以及 [PR 37 maintainer evidence](../evidence/pr37-maintainer-experience.md) 对多处接线成本的取证。这里裁定的是维护者结构和准入规则，不裁定 reload 是否重建 plugin runtime、节点生命周期、Stonecutter 终局、脚本/插件具体公开签名、数据迁移或语言前端替换；这些分别由后续决策票处理。候选 proposal 仍是 Draft，不能代替后续票的 HITL 结论。
