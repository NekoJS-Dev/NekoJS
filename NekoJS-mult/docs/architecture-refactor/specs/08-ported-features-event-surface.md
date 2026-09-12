# NekoJS 搬运功能事件面规格

Status: ready-for-agent

Type: spec

## Problem Statement

从其他 mod 搬入的功能目前混合了多种语义：Villager Trades 仍以静态 binding 和全局 Manager 暂存并直接操作 registry；Dynamic Registry 把服务器运行期注册与启动期 `RegistryEvents.register` 暴露成相近入口；PostEffects 把声明式注册与运行时 set/clear/toggle/current 混在同一静态面上。若照搬现状，实施会制造第二注册路径、重复事件、提前修改 live 对象、静默 no-op、property 写入绕过校验、reload 后物理删除旧定义，或把 prepare/ack 误称为分布式原子提交。

脚本作者需要清晰的声明入口和稳定查询，维护者需要把事件生命周期、平台 mutation、运行时动作和查询工具分开，平台负责人需要按真实证据记录 Fabric 与 1.21.1 的能力。可热 reload 只是目标，不等于已经实现；候选事件、typed Builder、事务协议和 capability 结论都必须由外部行为测试与跨节点证据闭合。

## Solution

只把具有明确生命周期、多个脚本或插件贡献、注册/reload/事务提交语义的公开面事件化；工厂、查询、运行时命令和发送动作继续作为 binding 或 Adapter。Villager Trades 第一版进入现有 `ServerEvents` 的数据/reload 子事件并提供 `add` 与稳定 query，registry mutation 只放平台/版本 Adapter。Dynamic Registry 保留为服务器运行期动态注册事件 facade，与启动期 `RegistryEvents.register` 明确分离；其 typed Builder、规范化指纹、prepare/ack/commit 和 stale 语义共同构成一批事务。PostEffects 的 register/unregister 进入 `ClientEvents` 资源/reload 子事件，运行时操作继续作为 binding。

方案复用既有事件和工具：Assets 继续使用 `ClientEvents.generateAssets`，EntitySelectors 保持 factory/query binding，已经事件化的 recipe、loot、tags、JEI、capability、goal、render 等域不重复造事件。第一版只开放已完成运行期 Adapter、事务和同步验证的 registry 类型；`remove`、`replace`、`modify`、同 key 变更覆盖和自动 Block/Fluid 连带注册不在一期承诺中。

## User Stories

1. 作为服务器脚本作者，我希望通过现有 `ServerEvents` 的数据/reload 子事件声明村民交易，以便不再直接调用静态 Manager 或在脚本任意位置修改 registry。
2. 作为服务器脚本作者，我希望 Villager Trades 第一版提供 `add` 和稳定 query，以便可以追加交易并读取确定快照。
3. 作为服务器脚本作者，我希望 query 返回只读快照并绑定 generation/stale 校验，以便不会通过查询面拿到 live registry view 或旧 generation 的可写对象。
4. 作为服务器脚本作者，我希望无效交易集、无效配置或失败事件不留下部分暂存结果，以便修正脚本后重载不会叠加脏交易。
5. 作为维护者，我希望第一版不公开 `remove`、`replace`、`modify`，并且不把未来 replace/update 当作已承诺能力，以便一期不会暴露无法可靠回滚的写操作。
6. 作为平台维护者，我希望 Villager Trades 的实际 registry mutation 只由版本或平台 Adapter 执行，以便共享事件面不直接依赖平台 surgery。
7. 作为 Fabric 用户，我希望 Villager Trades 的 `unavailable` 能力被显式记录，以便入口不会静默 no-op 或伪装成已支持。
8. 作为服务器脚本作者，我希望 Dynamic Registry 被保留，以便既有运行期动态注册能力不会因事件化重构消失。
9. 作为脚本作者，我希望服务器运行期动态注册使用独立事件 facade，而不是普通启动期 `RegistryEvents.register`，以便两种生命周期不会混成第二注册路径。
10. 作为脚本作者，我希望动态注册事件在初次 server registry ready 和每次成功的 script/data reload 时触发，以便重新声明本批计划。
11. 作为脚本作者，我希望事件只在收集阶段接收计划，不在任意脚本线程即时执行 registry mutation，以便 mutation 只发生在受控事务提交点。
12. 作为脚本作者，我希望使用 NekoJS typed callback Builder，而不是通用 `{type: ..., ...}` catalog，以便声明有类型、可发现且遵循同一配置模型。
13. 作为脚本作者，我希望 `item.setMaxStackSize(...)` 与 `item.maxStackSize = ...` 产生相同结果，以便显式 setter 和 JavaBean-style property 可以互换。
14. 作为维护者，我希望显式 setter 与 property 写入进入同一校验、规范化和 definition fingerprint 路径，以便不存在 public field 或旁路状态绕过事务。
15. 作为维护者，我希望全规范化 definition 指纹包含 Builder 输入及其连带声明，以便相同 key 是否变化可以由完整定义而不是部分字段判断。
16. 作为维护者，我希望同一 key 的定义变化在第一版按冲突失败，以便 reload 不会静默覆盖旧定义。
17. 作为维护者，我希望未来的 replace/update 只被视为可能方向而不作一期保证，以便当前冲突语义不会被提前承诺为稳定行为。
18. 作为脚本作者，我希望一批动态注册计划依次经过 preflight、同 key 指纹/冲突检测、服务端 prepare、客户端 prepare/ack 和原子 commit，以便错误在发布前被发现。
19. 作为服务器管理员，我希望任一步失败都让整批计划不提交并保留旧 active state，以便不会出现部分注册、半代际或重启后不一致。
20. 作为服务器管理员，我希望同步无法完成的节点不激活混合代际，以便多人生成环境不会暴露未同步定义。
21. 作为维护者，我希望 prepare/ack 只表示协议方向和事务阶段，不被宣称为真实分布式原子性证明，以便 capability 结论基于证据而非消息名称。
22. 作为维护者，我希望脚本不再声明的已暴露项标记为 stale/retired 而不是物理删除，以便普通 reload 不破坏既有世界引用和诊断轨迹。
23. 作为维护者，我希望非自动 Block/Fluid 在现有 type 验证通过后可以支持，而自动连带 Block/Fluid 不进入一期，以便类型验证、连带对象、资源和同步风险保持同一边界。
24. 作为客户端脚本作者，我希望 PostEffects 的 register/unregister 通过现有 `ClientEvents` 客户端资源/reload 子事件声明，以便资源生命周期和 reload cleanup 由同一事件面管理。
25. 作为客户端脚本作者，我希望 `set`、`clear`、`toggle`、`current` 继续作为 runtime binding 或 Adapter，以便运行时操作与声明式注册分离。
26. 作为维护者，我希望 Assets 复用现有 `ClientEvents.generateAssets`，以便不会新增第二个 Assets 事件或重复资源生成路径。
27. 作为维护者，我希望 EntitySelectors 保持 factory/query binding，以便查询工具不会因名称相似被错误事件化。
28. 作为维护者，我希望 recipe、loot、tags、JEI、capability、goal、render 等已事件化域不重复造事件，以便每种能力只有一个事实源。
29. 作为发布负责人，我希望可热 reload 只作为目标记录，只有实际 contract、transaction、capability、source trace 和 smoke 证据齐全后才宣称实现，以便规划方向不会被当成运行结果。
30. 作为升级用户，我希望旧静态入口到事件或 binding 的迁移逐项进入 1.2.0 迁移表，以便现有脚本可以按明确路径升级。

## Implementation Decisions

- 事件化边界只覆盖具有明确生命周期、多个贡献者、注册/reload/事务提交语义的公开面；工厂、查询、运行时命令、发送动作继续作为 binding 或 Adapter。
- 不创建万能 Event Module、第二 registry path 或没有真实生命周期的 Point。已有事件面可复用时必须复用，不能为搬运功能复制一个近似事件。
- Villager Trades 进入现有 `ServerEvents` 的数据/reload 子事件，事件名和 payload 仍是工作名，不由本规格冻结最终公开 API。
- Villager Trades 第一版只公开 `add` 与稳定 query；query 返回只读快照并绑定 generation/stale 校验，不暴露 live registry view。
- `remove`、`replace`、`modify` 不进入第一版公开面，必须等待冲突、顺序、回滚和版本/平台语义另行闭合。
- Villager Trades 的实际 registry mutation 只放在版本或平台 Adapter；事件面只收集与预验证，不在候选阶段提前修改 live registry。
- Fabric Villager Trades 当前记录为 `unavailable`，必须显式出现在 capability matrix；NeoForge 1.21.1 与 26.x 按真实验证结果公开，任何节点都不能静默 no-op。
- Dynamic Registry 保留。它使用服务器运行期动态注册事件 facade，工作名为 `ServerEvents.dynamicRegistry`，与启动期 `RegistryEvents.register` 的生命周期和语义完全分离。
- 动态注册事件在初次 server registry ready 与每次成功的 script/data reload 后触发；事件只收集本批计划，不在任意脚本线程即时执行 registry mutation。
- 动态注册使用 NekoJS typed callback Builder，不使用通用对象 catalog。精确事件名、payload、Builder 方法全集和开放 registry 类型仍由后续 contract 冻结。
- 第一版只开放已完成运行期 Adapter、事务和同步验证的 registry 类型。现有启动期 ItemBuilder、BlockBuilder、FluidBuilder 只能借鉴配置形状和类型事实源，不能直接复用会污染全局状态的启动期 drain 构建路径。
- 显式 setMaxStackSize setter 与同名 maxStackSize JavaBean 属性必须进入同一校验、规范化、definition fingerprint 和事务收集路径；两者不得产生不同定义。
- Builder 不得保留同名 public field 绕过 setter。setter 是否返回自身可在最终 Interface 中冻结，但显式调用与 JavaBean 属性写入必须产生相同规范化结果。
- GraalMC 上临时验证 property assignment 能调用 setter，只属于 characterization；它不构成集成通过。运行时 contract test 必须固定该行为，GraalJS 坐标升级时重新验证；若运行模式不保证映射，使用既有 ProxyObject seam 转发到同一 setter。
- 全规范化 definition fingerprint 覆盖 Builder 输入及其连带声明。指纹不能只看部分字段、对象身份或未经规范化的原始参数。
- 一批计划收集后依次执行 preflight、同 key 指纹/冲突检测、服务端 prepare、客户端 prepare/ack 和原子 commit。任一步失败都整批不提交，并保留旧 active state。
- 同一 key 的定义变化在第一版按冲突失败，不静默覆盖。未来 replace/update 或迁移机制只是一种可能方向，不构成一期保证。
- 脚本不再声明的已暴露项标记为 stale/retired；普通 reload 不物理删除。清理、claim 与 stale bookkeeping 由 Registry Runtime 和平台/版本 Adapter 负责。
- Registry Runtime 和平台/版本 Adapter 继续负责 claim、stale、cleanup、数值 ID、同步和 registry surgery；动态事件 facade 不承担平台反射或 registry 内部结构操作。
- prepare/ack 只表示协议方向和事务阶段，不证明跨进程分布式原子性。同步未完成的节点不得激活混合代际；不可完成同步时按实际证据标为 `partial` 或 `unavailable`。
- Fabric 与 1.21.1 的 Dynamic Registry capability 必须显式记录，不能以 no-op、server-only 路径或静默降级代替。
- 非自动 Block/Fluid 可在现有 type 验证通过后支持；自动连带 Block/Fluid 及其关联对象、资源和同步风险不进入一期。任何开放类型都必须同时具备运行期 Adapter、事务和同步验证。
- PostEffects 的 `register` 与 `unregister` 进入现有 `ClientEvents` 客户端资源/reload 子事件；`set`、`clear`、`toggle`、`current` 等运行时操作保留 binding 或 Adapter。
- Assets 复用现有 `ClientEvents.generateAssets`，不新增第二个 Assets 事件。EntitySelectors 保持 factory/query binding，不事件化。
- recipe、loot、tags、JEI、capability、goal、render 等已事件化域不重复造事件；每个域继续由原 owner 和原 contract 负责。
- candidate 阶段只能生成事件计划、Builder 定义、fingerprint、preflight 结果和 Adapter 请求，不得提前挂载生产事件、修改 live registry 或激活未同步代际。commit 后旧 generation 停止接收新计划并按要求清理。
- 可热 reload 是目标而非实现结果。只有 contract/golden、transaction/reload/delete-cleanup、capability/source-trace/smoke 与必要的外部 addon fixture 通过后，能力才能标记为 `supported`。
- 旧静态入口和现有事件只作为 characterization 与迁移输入，不自动升级为稳定公开契约。Script/Plugin breaking 逐项进入 1.2.0 迁移表。
- 本规格不冻结最终事件名、payload、API signature、`remove`/`replace`/`modify` 语义或 Fabric parity，也不批准源码实施。

## Testing Decisions

- 测试优先穿过最高调用者 Seam：Villager Trades 通过 `ServerEvents` 数据/reload 事件 Interface，PostEffects 通过 `ClientEvents` 资源/reload 事件 Interface，Dynamic Registry 通过运行期事件 facade 与 Registry Runtime/Adapter Interface。不得只测私有 Manager、反射字段或内部收集器。
- Villager Trades 成功断言是 `add` 在事件收集阶段形成计划，只在合法提交点更新 registry；稳定 query 返回只读快照，并在成功 reload 后反映新 generation、对 stale 项给出确定结果。
- Villager Trades 失败断言是未知 trade set、无效配置、候选失败或 Adapter 拒绝时没有部分 mutation，旧 active 交易仍可用，候选资源与临时计划被清理。
- Villager Trades 取消与清理断言是 reload 中断、close 或 generation 失效后旧 listener/计划不再生效；已删除声明不物理删除 registry 项，query 的 generation/stale 结果可观察。
- Villager Trades 的 Fabric `unavailable` 必须通过 capability/source-trace/smoke 证据验证，不得以 no-op 或无错误返回作为通过。
- 现有 `VillagerTradesJS` 静态 `add`/`pendingCount` 只作为 characterization 输入；`EventApiSurfaceGoldenTest` 是事件 surface golden 的 prior art，`ScriptReloadRegressionTest` 是 reload 失败保留与清理的 prior art。
- Dynamic Registry 成功断言是 typed Builder 可以创建定义，显式 `setMaxStackSize` 与 property assignment 产生同一规范化结果与 fingerprint，合法批次完成 preflight、prepare/ack 和 commit 后一次性可见。
- Dynamic Registry property 验证必须作为运行时 contract test，而不是只依赖临时 Graal characterization。成功断言是两种写法调用同一 setter 并得到同一校验/规范化/fingerprint；失败断言是 public field、绕过 setter 或不同 fingerprint 仍可通过。
- Dynamic Registry 冲突断言是同一 key 定义变化导致整批失败，旧 active 定义保留；未来 replace 未被一期承诺，因此测试不得把静默覆盖写成期望行为。
- Dynamic Registry 事务断言覆盖 preflight、服务端 prepare、客户端 prepare/ack、commit 任一步失败；失败时无部分注册、无半成功 ID、无混合代际，旧 active 继续服务。
- Dynamic Registry 取消断言覆盖 candidate 被 watchdog 终止、reload 被 close 抢占或同步无法完成；候选计划丢弃，未同步节点不激活，不自动创建第二个 active runtime。
- Dynamic Registry stale/cleanup 断言是脚本不再声明后 definition 标为 stale/retired，普通 reload 不物理删除，后续显式 replace/update 机制未实现时不会发生静默替换。
- Dynamic Registry 平台验证覆盖 Registry Runtime/Adapter 的 claim、ID、同步、registry surgery、失败回滚和 Fabric/1.21.1 capability；不可完成同步时只能按证据标为 `partial` 或 `unavailable`。
- `DynamicRegistryBuilderTest` 是 Builder defaults、链式形状和错误消息的 prior art；`DynamicRegistrationBookkeepingTest` 是 claim/stale/mode 的 prior art；`RegistryBridgeIntegrationTest` 是 registry 桥接的 prior art。新增测试需扩展外部事务与同步行为，不冻结私有实现。
- PostEffects 成功断言是 register/unregister 事件在资源 reload 周期正确生效，客户端资源按 generation 更新，TS/Python declaration 与实际成员一致。
- PostEffects 失败与取消断言是候选失败、reload 中断或客户端不可用时旧 active 资源保持可用，候选资源被清理，不出现重复注册或半更新。
- PostEffects 清理断言是旧 generation 的注册、listener 和资源不再生效；`set`、`clear`、`toggle`、`current` 的运行时 binding 行为不被声明事件替代。
- `PostEffectChainJsonTest` 是 26.x/1.21.1 JSON 形状与生成资源结构的 prior art；`ScriptReloadRegressionTest` 与 `ReloadMemoryStabilityTest` 继续作为 reload cleanup 和资源稳定性 prior art。
- `EventApiSurfaceGoldenTest` 用于验证事件成员集合的显式承诺；`NekoScriptCatalogEventsTest` 用于验证每条 bus 恰好出现一次、side filter 不重复声明、mixed side 保持独立条目。
- `EventBusConcurrentStressTest` 可作为并发事件收集、取消与清理的外部行为 prior art；测试应断言无重复 dispatch、无半提交和最终状态清空，不冻结调度器内部结构。
- Assets 验证必须断言复用 `ClientEvents.generateAssets`、无第二个事件、client-only 过滤和资源回读；EntitySelectors 验证必须断言 factory/query、非法 selector/level 错误和平台能力，不新增事件。
- 已事件化域验证必须通过 catalog/golden 证明 recipe、loot、tags、JEI、capability、goal、render 没有重复 bus；每个域的成功、失败、取消和清理由原 owner 的 contract/smoke 负责。
- 本规格中的热 reload、prepare/ack、property 映射和 Fabric/1.21.1 能力均为待验证目标；本轮未运行构建、测试或运行时 smoke，现有静态证据不能替代集成通过。

## Out of Scope

**本轮不实施或验证（不构成未来永久禁止）**

- 本轮不修改源码、运行构建或测试、更新 golden、生成 declaration、执行 reload 或发布 `1.2.0`。
- 本规格不冻结最终事件名、payload、Builder 方法全集、开放 registry 类型或 Fabric parity。

**本规格不承诺或明确排除的目标**

- 在第一版公开 `remove`、`replace`、`modify`，或承诺同 key 变更可自动覆盖；本规格也不承诺未来 replace/update 机制，任何未来机制须另行裁定。
- 把 Dynamic Registry 合并到启动期 `RegistryEvents.register`，或保留不安全 server-only 多人路径。
- 自动扩展 Block/Fluid 的连带对象、资源和同步语义，或绕过现有 type 验证开放未验证类型。
- 把 prepare/ack 消息宣称为跨进程分布式原子提交，或在同步未完成时激活混合代际。
- 把 PostEffects 运行时 set/clear/toggle/current 改成声明事件，或为 Assets、EntitySelectors 及已事件化域新增重复事件。
- 创建万能 Event Module、第二 registry path、无真实生命周期的 Point、长期 compatibility shim 或新的全仓测试框架。
- 创建 GitHub issue 或以本规格代替源决策的 Resolution。

## Further Notes

- 本规格派生自 [搬运功能如何适配 NekoJS 事件面与运行时扩展？](../decisions/08-ported-features-event-surface.md)。该票的 Resolution 是裁决权威；本文件只是执行视图，若两者冲突以源决策为准。
- 相关源决策包括 [脚本表面与插件作者模型如何只有一个事实源？](../decisions/04-public-contract-and-plugin-model.md)、[运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md)、[怎样以可验证的阶段完成本次重构并作为新标准？](../decisions/07-validation-and-migration.md)、[reload 候选环境、状态所有权与线程边界如何闭合？](../decisions/09-reload-candidate-state-and-thread-contract.md) 和 [跨 reload 的 global 共享状态如何参与候选事务？](../decisions/10-shared-global-candidate-writes.md)。
- 相关派生规格包括 [验证与迁移规格](./07-validation-and-migration.md)。W6/W7 的迁移方向、共同 gate 和验证顺序见 [NekoJS 实施交接单](../implementation-handoff.md)，功能覆盖账本与现存测试缺口见 [NekoJS 目标架构与迁移建图](../proposal.md)。
- 测试、golden、CI 与可观测性证据见 [测试、文档、CI 与可观测性证据](../evidence/testing-and-docs.md)。
- `Status: ready-for-agent` 只表示规格可交给代理继续处理，不构成源码、测试、构建、迁移、删除或发布授权。
- 所有验证均未因本规格生成而通过；可热 reload、事件化归属、typed Builder 和 prepare/ack 都只是待实施验证的执行契约。
