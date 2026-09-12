# reload 候选状态与线程契约规格

Status: ready-for-agent
Type: spec

## Problem Statement

普通 reload 虽然已经要求先构造候选环境，但候选与 active 之间的可见性、generation 失效、进程级 Plugin Runtime 与 session object 的边界、owner thread 的串行规则、close 优先级和 watchdog 恢复仍可能被实现者自行解释。若候选在 commit 前挂上生产事件、启动生产 timer、发布对外 binding 或修改 live registry，旧 active 会被提前污染；若旧新 generation 同时接收回调，会出现双重执行；若线程亲和和重入没有明确契约，跨线程访问、回调内 reload、close 与 watchdog 会形成锁顺序和恢复歧义。维护者需要一套可观察的状态、所有权和调度契约，使失败保留、资源清理和隔离恢复可以测试，而不是依赖私有锁字段或实现细节。

## Solution

`NekoRuntimeRoot` 继续作为唯一 runtime owner。普通 reload 为同一 ScriptType 创建新的 candidate generation，候选在 preparation、execution、binding、事件计划以及 Dynamic Registry、Villager Trades、PostEffects 等域的 preflight 全部完成后，才在 owner thread 的明确 commit 点切换为 active。commit 前，生产 callback、timer、对外 binding、受管 global/shared 写入和 live registry mutation 只属于 active；candidate 只使用自己的资源收集和预验证。失败时关闭候选的全部资源并保留 active；commit 时旧 generation 停止接收新 callback，新 generation 接管后按所有权顺序释放旧 session。Plugin Runtime、Point、Contributor、frozen result 与 Extension Handle 保持进程级；session object、事件 token 和临时注册计划按 generation 失效。同一 ScriptType 的 evaluate、reload、close 串行，reload 不重入，close 优先；watchdog 终止 candidate 时丢弃候选，终止 active 时进入隔离失败并等待显式 reload，不自动创建第二个 active runtime。该隔离不承诺深回滚外部 Java、世界、网络或其他副作用。

## User Stories

1. 作为 NekoJS 维护者，我希望普通 reload 仍由唯一 `NekoRuntimeRoot` 负责，以便候选、active 和平台接线不会出现第二个 owner。
2. 作为脚本作者，我希望同一 ScriptType 的 reload 创建新的 candidate generation，而不是重启或重新 bootstrap Plugin Runtime，以便脚本迭代不改变进程级插件契约。
3. 作为维护者，我希望 candidate 与 active 完全隔离，以便候选执行不会提前改变正在服务的环境。
4. 作为脚本作者，我希望 commit 前真实平台 callback、生产 timer、对外 binding、受管 global/shared 写入和 live registry mutation 只属于 active generation，以便测试中的新脚本不会提前影响玩家。
5. 作为脚本作者，我希望 candidate 可以使用自己的 binding、收集事件和测试 callback，以便在提交前完成脚本自身的预验证。
6. 作为维护者，我希望 candidate 依次完成 preparation、execution、binding、事件计划和适用域 preflight，以便切换前知道所有必要阶段是否成功。
7. 作为维护者，我希望只有 candidate 全部通过后才进入 commit，以便不会发布半成品 generation。
8. 作为服务器管理员，我希望 commit 时旧 generation 立即停止接收新 callback，以便旧脚本不会继续处理新事件。
9. 作为服务器管理员，我希望新 generation 接管后旧新 generation 不会双重回调，以便事件只执行一次。
10. 作为脚本作者，我希望 candidate 失败或取消时其全部资源关闭，以便 Context、timer、listener、binding 和临时计划不会泄漏。
11. 作为服务器管理员，我希望 candidate 失败后 active 的 listener、binding、timer 和 NekoJS 自有 state 仍可用，以便修复脚本后可以再次 reload。
12. 作为维护者，我希望失败结果至少包含 generation、phase、source location 和 owner/domain，以便定位失败发生在哪个生命周期边界。
13. 作为 Java 插件作者，我希望 Plugin Runtime、Point、Contributor、frozen result 和 Extension Handle 在普通 reload 后仍是进程级有效对象，以便插件发现和产物句柄不随脚本环境重建。
14. 作为 Java 插件作者，我希望 session object、事件 token 和临时注册计划按 generation 校验，以便旧对象不会静默操作新 session。
15. 作为 Java 插件作者，我希望 binding 返回共享 Java 对象时不会被误当作 generation 私有快照，以便共享对象的所有权和隔离边界保持真实。
16. 作为 Java 插件作者，我希望 session 清理不会关闭仍由进程级 Plugin Runtime 持有的共享产物，以便一次 reload 不会破坏其他 generation 或插件贡献。
17. 作为服务器管理员，我希望 root shutdown 先停止 callback 与 session，再释放 root 拥有的插件产物，以便关闭顺序不会留下悬挂引用。
18. 作为脚本作者，我希望 SERVER、CLIENT、STARTUP 和 TEST 分别在既定 owner thread 访问，以便 Graal Context、binding、listener 和 timer 不被任意外部线程触碰。
19. 作为脚本作者，我希望非 owner 线程的 managed lifecycle 请求排队到对应 owner thread，以便跨线程调用不会直接进入 Context。
20. 作为维护者，我希望同一 ScriptType 的 evaluate、reload 和 close 串行，以便生命周期状态不会被并发修改。
21. 作为脚本作者，我希望回调内部请求 reload 时不递归开启第二个 candidate，而是得到明确的排队或拒绝结果，以便不会同步等待自身队列。
22. 作为服务器管理员，我希望 close 优先于尚未开始的 reload，以便关闭期间不会启动新的候选工作。
23. 作为维护者，我希望平台 callback 在 commit 前后通过同一序列化入口分派，以便交接点不与会话中的 callback 重叠。
24. 作为脚本作者，我希望 guest-created thread 只能通过显式调度入口访问 runtime，以便线程能力不会被意外扩大或绕过 owner-thread 规则。
25. 作为维护者，我希望 watchdog 终止 candidate 时丢弃候选并关闭其资源，同时保持旧 active 不变，以便一次失控候选不会污染当前环境。
26. 作为服务器管理员，我希望 watchdog 终止 active 时进入隔离失败、停止向被终止 Context 分发，并等待显式 reload，以便系统不会自动偷偷创建第二个 active runtime 或重跑脚本。
27. 作为事件功能作者，我希望 Villager Trades、Dynamic Registry 和 PostEffects 在 commit 前只生成 candidate plan，以便平台 Adapter 不会提前修改 live 外部对象。
28. 作为脚本作者，我希望 NekoJS 明确不深回滚外部 Java、世界或网络副作用，以便 candidate 隔离的范围和高级 Java 访问边界一致。

## Implementation Decisions

- 唯一 owner：`NekoRuntimeRoot` 是唯一 runtime owner；普通 reload 为同一 ScriptType 创建新的 candidate generation，不重启或重新 bootstrap Plugin Runtime。不得新增 RuntimeKernel、RuntimeGateway、ServiceLocator 或第二 manager。
- 状态机：`ACTIVE(N)` 持有生产事件路由和自己的资源；reload 进入 `CANDIDATE(N+1)`；候选完成 preparation、execution、binding、事件计划与适用域 preflight 后进入 `READY(N+1)`；owner thread 在明确 commit 点把 callback 路由切到 N+1；旧 generation 停止接收新 callback 并释放；新状态为 `ACTIVE(N+1)`。
- Candidate 可见性：commit 前，真实平台 callback、生产 timer、对外 binding、受管 global/shared 写入和 live registry mutation 只属于 active；candidate 只收集和预验证自己的 session 资源、事件计划与注册计划。候选可以使用自己的 binding、收集事件和测试 callback，但不得提前挂上生产路由。
- 失败状态：candidate 失败或取消时进入关闭态，释放全部 candidate 资源；`ACTIVE(N)` 的 listener、binding、timer 和 NekoJS 自有 state 保持可用。错误至少包含 generation、phase、source location 和 owner/domain。
- 提交边界：“旧 active 保持可用”不表示两个 Context 在同一 owner thread 上并行执行或零停顿承诺；commit 不与该 generation 在途 callback 重叠，具体安全点由平台 Adapter 接线。
- 进程级所有权：Plugin Runtime、Point、Contributor、frozen result 与 Extension Handle 跨普通 reload 保持进程级；普通 reload 不重建它们，Handle 本身保持有效。
- Generation 级所有权：Graal Context、prepared module session、script binding、listener、timer、session object、事件 token 和临时注册计划按 generation 创建、校验和释放；旧对象不得静默操作新 session。
- 其他所有权：Dynamic Registry active overlay、claim/stale bookkeeping 由 Registry Runtime 与平台/version Adapter 管理；成功 commit 更新，失败保留旧 active state，stale 不在普通 reload 物理删除；module cache 与 Probe/manifest 临时结果按 source、definition 或 generation 失效或重建，不作为 active state 回滚替代物。
- 数据保护所有权：config、world、pdata、pack、trust-store、用户编辑 workspace/declaration 由 Data Protection 与 Pack Trust owner 管理，不因普通 reload 覆盖或删除。
- Root shutdown：close 先停止接受新工作，再停止 callback 与 session，随后释放 root 拥有的插件产物（若有释放契约）。session 清理不得关闭仍由进程级 Plugin Runtime 持有的共享产物。
- 线程矩阵：SERVER eval、脚本 callback 和 reload 进入 Minecraft server owner thread；CLIENT eval、资源计划和 callback 进入 client/render owner thread；STARTUP 使用 bootstrap/loader 的既定串行入口；TEST 使用 test runner，涉及 MC 时进入对应平台 owner 入口；非 owner 线程请求排队；guest-created thread 只能使用显式调度入口。
- 重入与 close：同一 ScriptType 的 evaluate、reload、close 串行；reload 不重入；回调内部 reload 只能排队或返回明确拒绝，不能同步等待自身队列；close 优先于尚未开始的 reload。
- Watchdog：终止 candidate Context 时，candidate 失败并关闭，旧 active 不变；终止 active Context 时只记录隔离失败并保持单一 owner，不再向被终止 Context 分发，后续由显式 reload 尝试恢复；不自动创建第二个 active runtime，也不自动重跑脚本。
- 事件域约束：Dynamic Registry、Villager Trades 和 PostEffects 的平台 Adapter 在 commit 前只能生成候选计划；域 preflight 通过不等于 live mutation 已可安全回滚。Dynamic Registry 的准备、可见性和失败处理必须另行通过 gate，prepare/ack 消息本身不证明跨进程原子性；未通过时不得宣称能力已实现。
- 实施验收分派：W1 验证 candidate/active 可见性、generation 切换、owner-thread 序列化和无双重 callback；W2 验证进程级 Plugin Handle 与 generation-scoped session object 的失效边界；W4 验证候选资源释放、失败保留、watchdog 与 reload/close 重入；W6/W7 的事件化域只能生成 candidate plan，不得在 commit 前修改 live 外部对象。
- 外部副作用边界：候选隔离只覆盖 NekoJS 管理的 generation 资源与声明计划，不承诺对任意 Java 对象、世界、文件或网络副作用做深拷贝或深回滚；高级 Java 访问和既有外部副作用边界不变。

## Testing Decisions

测试优先现有最高公开 Seam：`NekoRuntimeRoot` 生命周期 Interface 是 candidate/active/generation、owner-thread、close 和 watchdog 的最高观察点；现有 `ScriptManager` 的 discover、load、reload、close 入口以及公开事件、binding 和 Plugin Handle Interface 作为当前可达调用路径与 prior art。断言应来自 eval、reload、event、close 的可观察结果，例如回调执行次数、timer 是否触发、旧 state 是否可读、错误结果字段、资源关闭后的公开行为；不得把私有 Map、锁字段、Context mapping 或对象身份当作契约。

已有 prior art：

- `ScriptReloadRegressionTest.transactionalReloadLoadsScriptsIntoCandidateContext` 通过脚本执行验证 candidate Context。
- `ScriptReloadRegressionTest.transactionalReloadRejectsCandidateKilledByStatementLimitWhenPreviousKilledIsTrue` 验证候选失败后旧 Context 仍是当前环境。
- `ScriptReloadRegressionTest.transactionalReloadCandidateTimerCallbacksAreNotSkipped` 验证候选 timer 可执行。
- `ScriptReloadRegressionTest.rebuildAfterStatementLimitKillClearsEventListeners` 与 `rebuildAfterStatementLimitKillMatchesFullTeardown` 验证 listener、binding 和错误状态清理。
- `ScriptReloadRegressionTest.infiniteLoopInScriptEntryDoesNotFreezeServerThread` 提供 watchdog 或语句上限终止后的可观察恢复 prior art。
- `ReloadMemoryStabilityTest.fiftyConsecutiveReloadsDoNotGrowMemoryMonotonically` 提供重复 reload 的资源稳定性 prior art。
- `ResourceTrackerTest.closeRunsCleanupsInLifoOrder`、`closeContinuesAfterFailureAndRethrowsFirstThrowable` 与 `closeIsIdempotent` 提供关闭顺序、失败继续清理和幂等 prior art。
- `ScriptEventRegistryTest` 与 `ScriptEventDefinitionClearListenersTest` 提供事件注册替换、按脚本或类型清理 listener 的 prior art。

需新增 fixture，以下场景尚未通过这些测试，不能写成已通过：

- candidate 在 preparation、execution、binding 或域 preflight 失败时，通过公开 eval、event、timer、close 结果确认 candidate 资源全部关闭，active 仍能接收事件且旧 state 可读。
- commit 前 candidate 的事件计划、timer、binding、受管 global/shared 写入和 registry plan 对生产路由不可见；commit 后旧 generation 不再收到新 callback，新 generation 只执行一次。
- 同一 ScriptType 的 owner-thread 串行、非 owner 排队、回调内 reload 的排队或拒绝、close 优先和 reload 不重入。
- Plugin Runtime、Point、Contributor、frozen result 和 Extension Handle 跨 reload 仍有效；generation-scoped session object、事件 token 和临时计划在旧 generation 失效后不能再操作新 session。
- watchdog 终止 candidate 时 active 不变；watchdog 终止 active 时进入隔离失败、停止分发并等待显式 reload，不自动创建第二个 active runtime。
- Dynamic Registry、Villager Trades 和 PostEffects 的 Adapter 在 commit 前只产生计划，不提前修改 live 外部对象；域 preflight 失败不提交旧 active。
- root shutdown 的 callback、session、插件产物释放顺序，以及失败清理后无双重 callback。

本轮未运行上述构建、测试或迁移；这些 fixture 是实施验收输入，不是已通过证据。

## Out of Scope

本轮仅做规格生成；不修改 Java、Gradle、Stonecutter、CI 或源码，也不执行构建、测试、事件域实现或发布。这是本轮生成边界，不是本规格的目标排除项。以下才是目标重构明确不包含的事项：

- 不新增 RuntimeKernel、RuntimeGateway、ServiceLocator、第二 runtime owner 或第二套生命周期。
- 不承诺撤销 Java、世界、网络、文件或其他外部对象副作用，也不做任意对象深拷贝沙盒。
- 不改变普通 reload 不重启 Plugin Runtime 的既定边界，不借 reload 隐式重做插件发现或平台注册。
- 不冻结 Villager Trades、Dynamic Registry、PostEffects 的最终事件名、payload、builder 方法或平台同步协议。
- 不把 `prepare/ack` 消息等同于跨进程原子性，也不在未通过 gate 时宣称域能力已实现。
- 不承诺候选和 active 在同一 owner thread 上并行执行或零停顿交接。
- 不限制既有高级 Java 访问，也不新增通用权限系统、全仓事件框架或事务框架。

## Further Notes

源决策是 [reload 候选环境、状态所有权与线程边界如何闭合？](../decisions/09-reload-candidate-state-and-thread-contract.md)。本 spec 是该 Resolution 的派生实施输入；若本 spec 与 Resolution 冲突，以 Resolution 为唯一裁决权威。事件域约束直接引用 [搬运功能如何适配 NekoJS 事件面与运行时扩展？](../decisions/08-ported-features-event-surface.md)，生命周期与数据边界引用 [运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md)。

相关 spec 为 [05 运行时生命周期与数据保护](05-runtime-lifecycle-and-data.md) 和 [10 global 共享状态与候选写入](10-shared-global-candidate-writes.md)。现有运行时证据与测试 prior art 见 [runtime evidence](../evidence/runtime-and-modules.md) 和 [testing evidence](../evidence/testing-and-docs.md)。

Status 为 ready-for-agent 只表示本 spec 可交给实施代理，不表示本轮已经授权修改源码、构建、运行测试或执行事件域实现；实施仍须取得维护者的单独授权并满足 07 的 release gate。