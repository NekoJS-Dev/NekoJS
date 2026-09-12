# 运行时生命周期与数据保护规格

Status: ready-for-agent
Type: spec

## Problem Statement

NekoJS 的运行时生命周期目前跨越单一 `NekoRuntimeRoot` 与两个 loader composition root，而普通 reload、插件装配、平台注册和数据保护的风险落在同一条路径上。若 reload 重新创建或重新注册平台事件、registry、network，就可能出现重复回调或第二套生命周期；若候选环境在准备、执行或绑定阶段失败后仍有资源泄漏，当前 active 环境可能被污染或不可用；若模块重排、数据迁移或 Fabric WORLD 差异没有明确边界，用户数据、信任决策和跨 loader 兼容性可能在重构中静默改变。维护者和整合包作者需要一套可观察的契约，明确什么跨 reload 保留、什么失败时保留、什么只能显式重启、什么数据绝不丢失，以及哪些外部副作用不在回滚范围内。

## Solution

NekoJS 以一个 `NekoRuntimeRoot` 作为唯一 runtime owner。普通 reload 只切换脚本环境与模块 session，不重新 bootstrap/freeze Plugin Runtime，也不重复注册平台事件、registry 或 network。reload 先构造 candidate，只有 preparation、execution、binding 及必要的域 preflight 全部通过后才切换；失败时关闭 candidate 并保留当前 active runtime/state。数据保护采用“默认不改”的规则：config、world、实体/玩家 pdata、脚本与 pack、trust-store、用户编辑的 workspace/declaration 和历史日志受保护；只有确有必要才引入格式专用迁移，并要求备份或原子替换、schema/version、旧 fixture 回读、幂等验证、失败回滚和旧数据保留。Fabric WORLD 保持当前行为，已知生命周期差异显式公开而不强求 parity；离线 validator/migration report 可选、默认只读、不是硬 release gate。该契约不承诺撤销脚本通过 Java、网络、世界或其他外部对象造成的副作用。

## User Stories

1. 作为 NekoJS 维护者，我希望所有 runtime 生命周期最终归一个 `NekoRuntimeRoot` 管理，以便 NeoForge 与 Fabric 只承担平台接线，不会形成第二套运行时。
2. 作为 Java 插件作者，我希望普通脚本 reload 不重新 bootstrap 或 freeze Plugin Runtime，以便进程级插件产物和 Extension Handle 在 reload 后仍按同一契约有效。
3. 作为整合包脚本作者，我希望普通 reload 只重建脚本环境与模块 session，以便修改脚本时不必重启游戏或重新装配平台注册。
4. 作为服务器管理员，我希望 reload 不重复注册平台事件、registry 或 network，以便不会出现重复回调和重复注册。
5. 作为脚本作者，我希望 reload 先构造 candidate，以便错误脚本不会破坏当前可用的脚本环境。
6. 作为脚本作者，我希望 preparation、execution 或 binding 失败时 candidate 的全部资源关闭，以便失败尝试不留下 Context、timer 或 listener 泄漏。
7. 作为服务器管理员，我希望 candidate 失败后 active runtime 与 state 仍可用，以便可以修复脚本后再次 reload。
8. 作为脚本作者，我希望失败结果包含 source location、phase 和 error reason，以便定位失败发生在哪个阶段。
9. 作为维护者，我希望 candidate 只有完整通过后才切换，以便不会发布半成品环境。
10. 作为维护者，我希望切换后旧环境按 timer、listener、Context 的所有权顺序释放，以便资源清理有确定顺序。
11. 作为脚本作者，我希望 NekoJS 不承诺撤销脚本已造成的 Java、网络或世界副作用，以便事务边界清楚，不会误以为 reload 是深回滚。
12. 作为插件作者，我希望插件实现、平台注册或 capability 变化走显式重启或重新装配，以便普通 reload 不隐式改变插件契约。
13. 作为整合包作者，我希望本地可信与远端脚本继续使用显式受限授权，以便执行前能知道能力是允许、拒绝还是降级。
14. 作为服务器管理员，我希望信任拒绝、降级和审计出现在执行或 pack trust 结果中，以便能解释脚本为何未运行或能力受限。
15. 作为脚本作者，我希望 NekoJS 不声称对任意恶意 guest 提供强隔离，以便安全边界与真实能力一致。
16. 作为整合包作者，我希望普通模块重排不改变 config、world、pdata、pack 的路径、格式、key、读写语义和默认启用规则，以便升级不破坏现有数据。
17. 作为服务器管理员，我希望不可再生或用户编辑的数据始终保留，以便 reload 或重构不会丢失世界、玩家数据、脚本、信任决策或历史日志。
18. 作为维护者，我希望 probe 输出和 module cache 只有在来源可重建且有证据时才重建，以便不会把唯一数据当作缓存删除。
19. 作为整合包作者，我希望用户编辑的 workspace config 与 declaration 不被盲目覆盖，以便生成物与人工修改可以区分。
20. 作为维护者，我希望必要的数据迁移先备份或原子替换，以便失败时可以恢复原始数据。
21. 作为维护者，我希望迁移写入 schema/version 标记，以便读取端能识别格式和迁移状态。
22. 作为整合包作者，我希望旧 fixture 在迁移后可读，以便升级不会只验证新格式。
23. 作为服务器管理员，我希望迁移失败可回滚且旧数据保留到验证完成，以便有安全恢复路径。
24. 作为维护者，我希望 PersistentDataJS 的 key 和跨 loader pdata 语义保持不变，以便实体与玩家数据不因重构丢失。
25. 作为维护者，我希望 network sync 的 wire id 与格式默认不变，任何变化都经过迁移表和 fixture gate，以便客户端与服务端不会静默不兼容。
26. 作为 Fabric 用户，我希望 WORLD pack 保持当前行为并公开已知生命周期差异，以便不会为了虚假 parity 改变现有路径、默认启用或信任规则。
27. 作为发布维护者，我希望离线 validator 或 migration report 可显式运行且默认只读，并且不是硬 release gate，以便它辅助检查而不改变普通执行路径。
28. 作为维护者，我希望只在格式确有必要变化时引入专用迁移，以便重构不被通用 migration framework 扩大。

## Implementation Decisions

- 单一 owner：`NekoRuntimeRoot` 是唯一 runtime owner。NeoForge 与 Fabric composition root 只负责原生生命周期、事件、网络和 registry 时机，不能各自持有第二套 runtime lifecycle。
- 普通 reload 的切换单位：脚本环境与模块 session。可以重建 prepared module、Graal Context、bindings、timers 和 script listeners，但不得重新 bootstrap/freeze Plugin Runtime，也不得重复注册平台事件、registry 或 network。
- Candidate 生命周期：`ACTIVE(N)` 持有生产路由和资源；reload 进入 `CANDIDATE(N+1)`，候选只做 preparation、execution、binding 和域 preflight；全部通过后进入 `READY(N+1)`，再在明确 commit 点切换为 `ACTIVE(N+1)`。候选失败或取消时关闭候选资源，`ACTIVE(N)` 不变。
- 提交结果：只有候选完整通过才发布新环境；提交后旧 generation 停止接收新 callback，再按 timer、listener、Context 等所有权顺序释放。不得出现旧新双重回调。
- 失败结果：错误至少包含 source location、phase 和 error reason；失败只保证 NekoJS 自有 runtime/state 回退，不承诺撤销外部 Java、网络、世界或其他对象副作用。
- 进程级状态：Plugin Runtime、Point、Contributor、frozen result 和 Extension Handle 跨普通 reload 保持进程级有效；generation 级 session object、事件 token 和临时注册计划必须随 generation 失效，不能静默操作新 session。
- 信任与能力：本地可信脚本与远端脚本继续使用显式受限授权；拒绝、降级和审计必须出现在执行或 pack trust 结果中。NekoJS 不宣称对任意恶意 guest 提供强隔离。
- 数据保护默认：默认保持 config、world、实体/玩家 pdata、脚本与 pack、trust-store、用户编辑的 workspace/declaration、历史日志的既有路径、key、wire id、格式、读写语义和默认启用规则不变。普通 Module 重排不附带数据迁移。
- 可再生数据：probe 输出和 module cache 只有在来源可重建且有证据时才可重建；workspace config 与 declaration 可能被用户编辑，不得因“生成物”标签被盲目覆盖。远端 cache 只有在源仍可重新获取且有明确重建证据时才可重建。
- 必要迁移：只有持久格式确有必要变化时才引入格式专用 migration。迁移必须先备份或原子替换，写入 schema/version 标记，验证旧 fixture 可读，满足幂等验证，失败可恢复，并保留原始数据直到验证完成；不得引入通用 migration framework。
- 受保护数据面：`PersistentDataJS` 的 key 与跨 loader pdata 语义属于保护清单；network sync 的 wire id 与格式默认保持，任何改变必须由迁移表和 fixture gate 单独批准；trust-store、bucket 与 key pinning 格式默认保持。
- 回滚边界：release rollback 与 data rollback 分开；旧 artifact 可以回退，但 config、world、pdata、脚本与 pack、trust-store、用户编辑 workspace/declaration 和历史日志在验证完成前不得被覆盖或删除；历史日志按历史诊断记录保留，不冻结日志措辞。
- Fabric WORLD：1.2.0 保持当前 Fabric WORLD pack 行为，公开已知生命周期差异；完整 parity 不是本次发布硬要求。能力表按实际行为标记 `partial` 或 `unavailable`，延期是工作状态而不是第四种 capability；不隐藏差异，也不改变本地 pack 的路径、默认启用或信任规则。
- 验证工具：离线 validator 或 migration report 可显式运行，默认只读，不进入普通执行错误路径，也不替代必须的公开接口迁移表、数据迁移或回滚 fixture；它不是 1.2.0 硬 release gate。
- 边界：不新增 RuntimeKernel、RuntimeGateway、ServiceLocator、第二 manager、第二 runtime owner 或第二套生命周期；不把普通 reload 扩大为插件重新装配，也不把 NekoJS 自有资源回退扩大为外部副作用深回滚。

## Testing Decisions

测试优先跨现有最高公开 Seam：`NekoRuntimeRoot` 的生命周期 Interface 是唯一 runtime owner、装配、reload 和 close 的最高观察点；现有 `ScriptManager` 的 discover、load、reload、close 入口作为当前可达调用路径和 prior art，pack trust、数据读写和公开事件结果用于观察外部行为。不要用私有 Map、锁字段或对象身份作为契约断言；资源释放、失败保留和迁移必须通过 eval、reload、event、close 以及公开数据结果验证。

已有 prior art：

- `ScriptReloadRegressionTest.transactionalReloadLoadsScriptsIntoCandidateContext` 通过脚本执行观察 candidate Context，而不是只检查内部字段。
- `ScriptReloadRegressionTest.transactionalReloadRejectsCandidateKilledByStatementLimitWhenPreviousKilledIsTrue` 覆盖候选被终止后保留旧 Context 的失败路径。
- `ScriptReloadRegressionTest.unreadableScriptSurfacesInErrorTrackerInsteadOfVanishing` 验证不可读脚本仍产生可观察错误。
- `ScriptReloadRegressionTest.transactionalReloadCandidateTimerCallbacksAreNotSkipped` 验证候选自己的 timer 在提交前可完成。
- `ScriptReloadRegressionTest.rebuildAfterStatementLimitKillClearsEventListeners` 与 `rebuildAfterStatementLimitKillMatchesFullTeardown` 验证 listener、binding 和错误状态清理。
- `ReloadMemoryStabilityTest.fiftyConsecutiveReloadsDoNotGrowMemoryMonotonically` 提供重复 reload 的内存稳定性 prior art。
- `ResourceTrackerTest.closeRunsCleanupsInLifoOrder`、`closeContinuesAfterFailureAndRethrowsFirstThrowable` 与 `closeIsIdempotent` 提供资源释放顺序和失败清理 prior art。
- `PackSyncTrustStoreTest.serverTrustPersistsAcrossReload` 与 `corruptFileDegradesToEmptyStore` 覆盖 trust-store 跨 reload 保留和损坏文件降级。
- `PersistentDataJSTest` 与 `PDataSyncAcceptTest` 覆盖当前 pdata 读写、脏标记和同步接受语义。

需新增 fixture，以下场景尚未通过这些测试，不能写成已通过：

- 分别在 preparation、execution、binding 和域 preflight 失败时，通过脚本回调、timer、event、close 观察 candidate 资源全部关闭，active 仍可接收事件且 state 可读。
- 普通 reload 后通过公开 Extension Handle、事件触发次数和平台注册结果确认 Plugin Runtime 未重新 bootstrap、平台资源未重复注册。
- 数据保护 fixture：迁移前备份或原子替换、schema/version 标记、旧 fixture 回读、幂等验证、失败回滚、原始数据保留，并确认 config、world、pdata、pack、trust-store、workspace/declaration 和 network wire 的路径、key、格式未被静默改变。
- Fabric WORLD fixture：记录当前行为与已知生命周期差异，验证 capability 显式、路径、默认启用和信任规则未改变；不以强制 parity 作为通过条件。
- 离线 validator 或 migration report fixture：显式运行时只读、普通执行路径不受影响、不能替代迁移表或数据回滚 gate。

本轮未运行上述构建、测试或迁移；这些是实施验收输入，不是已通过证据。

## Out of Scope

本轮仅做规格生成；不修改 Java、Gradle、Stonecutter、CI 或源码，也不执行构建、测试、1.2.0 发布、实际数据格式迁移、功能删除或版本退役。这是本轮生成边界，不是本规格的目标排除项。以下才是目标重构明确不包含的事项：

- 不更换 GraalJS，不改变高级 Java 访问，也不承诺对任意恶意脚本提供强隔离。
- 不承诺撤销脚本通过 Java、网络、世界或其他外部对象造成的副作用。
- 不把 Fabric WORLD 的完整 parity 作为 1.2.0 硬要求，也不通过 experimental 身份隐藏差异。
- 不新增第二 runtime owner、RuntimeKernel、RuntimeGateway、ServiceLocator、通用 migration framework 或通用事务框架。
- 不让离线 validator 或 migration report 成为硬 release gate，也不让普通执行错误携带修复指引。
- 不在本 spec 中冻结未被 05 Resolution 明确批准的具体迁移格式、备份位置或实施窗口。

## Further Notes

源决策是 [运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md)。本 spec 是该 Resolution 的派生实施输入；若本 spec 与 Resolution 冲突，以 Resolution 为唯一裁决权威。数据迁移、validator 和回滚门禁还直接受 [怎样以可验证的阶段完成本次重构并作为新标准？](../decisions/07-validation-and-migration.md) 约束。

相关 spec 为 [09 reload 候选状态与线程契约](09-reload-candidate-state-and-thread-contract.md) 和 [10 global 共享状态与候选写入](10-shared-global-candidate-writes.md)。现有运行时证据与测试 prior art 见 [runtime evidence](../evidence/runtime-and-modules.md) 和 [testing evidence](../evidence/testing-and-docs.md)。

Status 为 ready-for-agent 只表示本 spec 可交给实施代理，不表示本轮已经授权修改源码、构建、运行测试或执行数据迁移；实施仍须取得维护者的单独授权并满足 07 的 release gate。