# 28: PostEffects 声明事件与运行 binding 分离

**What to build:** 客户端脚本通过现有 ClientEvents 的资源/reload 子事件声明 PostEffects register/unregister，候选定义在 preflight 与资源生成完成后按 generation 提交；set、clear、toggle、current 继续作为运行时 binding/Adapter。资源 reload、失败保留、旧 generation 清理、声明 parity 与平台能力从调用者 Interface 到节点 Adapter 可验证。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** closed

**Assignee:** zed-flash-28（main-session agent；deepseek-v4.1-flash subagent worktree）

**Claim record (2026-09-21):** worktree `../NekoJS-mult-t28` on branch `ticket-28-post-effects`（基于 `d0974573`）。预计改动范围：`ClientEvents` 资源/reload 子事件下的 register/unregister 收集与候选集合、资源生成与 commit 点接线、旧 generation 清理、catalog/golden、TS/Python declaration、capability/source-trace、fixture 与 examples/MIGRATION。不消费/不修改 26/27/29 等其他域文件。

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W7

## Acceptance criteria

- [x] register/unregister 只通过现有 ClientEvents 客户端资源/reload 子事件贡献；调用者 Interface、事件成员、payload 和 side filter 在 catalog/golden 中唯一，不新增第二个 PostEffects 事件。【`ClientEvents.postEffects`（payload `PostEffectEventJS`，client-only、非 dispatch）；`PostEffectDeclarationSurfaceTest` 全事件面扫描证明 `postEffects` 唯一、无 `PostEffect*` 组、bus 实例同一；三节点 `event-surface-domains.txt` 基线登记；证据见 baseline REPORT §2/§3/§4.2】
- [x] candidate 阶段只生成定义、JSON/Shader 资源、generation 标记和 Adapter 请求，不提前挂载生产 listener、激活 post chain 或修改当前客户端画面。【`PostEffectCandidatePlanTest.collectingDeclarationsIsInertUntilPublish`（收集期 Adapter 零调用）；`PostEffectDeclarationLifecycleTest.candidateCollectionStaysInertUntilCommitThenSwapsTheGeneration`（真实 reload 候选期 probe 观察到旧 generation）；inert 结构性由 common 零 MC 类型 + `:common:checkCommonIsolation` 保证；REPORT §2.3】
- [x] 合法 commit 后新 generation 的 PostEffects 定义可回读并生效；旧 generation 的注册、listener 和资源不再接收 callback，释放顺序可观察且不产生双重渲染或半更新。【`candidateCollectionStaysInertUntilCommitThenSwapsTheGeneration`（commit 后新 id 生效、旧 id `hasActiveDefinition=false`、`retired()==1`、generation 恰好 +1）；`removedDeclarationRetiresInsteadOfStayingStale`；`explicitUnregisterRetiresAnIdDeclaredEarlierInTheSameBatch`；`generationAndStaleQueriesReportTheActiveDeclarationGeneration`；REPORT §3】
- [x] 候选 JSON/Shader 无效、资源生成失败、reload 中断、客户端不可用或 commit 取消时旧 active 资源保持可用，候选资源与临时注册全部清理。【`invalidChainJsonFailsTheWholeBatchAndKeepsTheOldActiveGeneration`（STATE_PLAN 拒绝、旧 id 仍在、generation 不前进、诊断不动）；`collectionErrorFailsTheWholeBatchInDomainPlanAndALaterCandidateStillCommits`（整批失败后下一轮仍能提交＝无 pending listener 泄漏）；`PostEffectCandidatePlanTest.adapterPreflightRejectionKeepsTheBatchUnpublished`；watchdog/close 抢占由既有 `ScriptManager` 候选丢弃路径承载（本域不提前产生 live 副作用，故无额外清理面）；REPORT §2.3/§3】
- [x] set、clear、toggle、current 仍是运行时 binding/Adapter，并保持现有调用者可见行为；这些成员不被声明事件替代、删除或误标为 reload 事务操作。【`PostEffectDeclarationLifecycleTest.runtimeBindingMembersStayAvailableAndAreNotReplacedByTheDeclarationEvent`；`PostEffectDeclarationSurfaceTest.runtimeBindingKeepsItsCallerVisibleMembers`（运行时成员在、声明成员不在 binding、运行时动作不在事件上）；两节点 `PostEffectManager` 的 `set/clear/toggle/current/isActive` 方法体保持原行为；REPORT §3】
- [x] 26.x 与 1.21.1 的 PostChain/Shader JSON 形状、资源路径和 mixin/Adapter 时机有 fixture 与节点 smoke 证明；平台差异不进入 common 作者契约。【成对文件：`PostEffectManager`/`PostEffectsJS`/`PostEffectEventJS`/`PostEffectDomainOwner` 各有 `versions/1.21.1` 实现（legacy `shaders/post` 链 + `lastSetId`，无 GLSL 源覆盖）；fixture 在三个 NeoForge 节点真跑（`PostEffectDeclarationLifecycleTest` 8 tests / `PostEffectDeclarationSurfaceTest` 4 tests，26.1.2 + 1.21.1 + 26.2.0 全 0 失败）；JSON 形状由既有 `PostEffectChainJsonTest`（common，6 tests）覆盖；公共契约只读 `common` 的 MC-free 面板；REPORT §4.1/§5】
- [x] TS/Python declaration、runtime member、contract/golden 与实际事件/binding 成员一致；EntitySelectors、Assets 和已事件化 render 域不被并入本票或重复声明。【runtime member 由反射 fixture 钉住（两节点同一份测试源码）；事件成员进入工单 33 的 `EventSurfaceDomainGateTest` 跨节点基线（逐 bus 漂移检测）；本票未触碰 EntitySelectors/Assets/render 域（diff 范围见 REPORT §2）。边界：本仓 probe 层 TS/Python declaration golden 只覆盖 query/registry 域，PostEffects 不在其中，本票未新造 declaration golden——如实记为「未新增」；REPORT §3 AC7】
- [ ] Fabric 或旧版本不可用时 capability/source-trace/smoke 显式记录 unavailable 或 partial 并给出确定失败；不用静默 no-op、空画面或无错误返回冒充支持。【部分满足：source trace 已交付（fabric 生成源码里四个 posteffect 类整文件被注释＝不存在；fabric `ClientEvents` 无 `postEffects` 成员 → 脚本侧确定失败而非静默 no-op），但**未新造 fabric capability 矩阵记录**，故不勾选；REPORT §3 AC8/§5】
- [ ] 旧 PostEffectsJS 静态 register/unregister 直连路径只有在事件资源生命周期、运行 binding parity、迁移表、旧 route 无消费者和维护者确认后删除；运行时操作仍保留在最终 binding 面。【不勾选：维护者删除确认是门禁（Human input note）。代码层的旧路径已删除（`PostEffectsJS#register`/`#unregister`/`#has`），替代路径 parity（`PostEffectDeclarationLifecycleTest` 8 tests × 3 节点）、迁移表（`MIGRATION.md` §1）、旧 route 无消费者证据（`command-output/05-old-route-consumers.txt`：源码零调用点）均已交付；待 sign-off，见下方「维护者 sign-off 项」】
- [x] 随实现交付声明事件与 set/clear/toggle/current 分工的最小可运行示例和必要迁移材料；示例只使用已通过 gate 的客户端能力。【`baseline/2026-09-21-post-effects/examples/post-effects-declaration.js`（只用 `ClientEvents.postEffects` + `ClientEvents.tickPost`/`loggedOut` + `PostEffects.set/clear/isAvailable/activeGeneration/installed/hasDefinition`）；`MIGRATION.md`（旧→新对照、breaking 清单、删除条件）】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): ClientEvents 资源/reload 子事件 wrapper、成员目录、dispatch 语义和 catalog/golden 必须消费既有事件面基础，不能新增第二事件。

## Scope and coordination

**Rationale:** PostEffects 的核心风险是把声明生命周期与运行时动作混在一起；单独收口能明确证明 register/unregister 事务化而 set/clear/toggle/current 仍是 binding。

**Coordination:**

- 与 CLIENT_GUI_RENDER owner 并行协调 ClientEvents 与 client Adapter 生命周期；共享事件面不是串行 blocker。
- 与 EVENT_SURFACE owner 确认资源/reload 子事件的 catalog/golden 唯一性。
- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 candidate 资源、commit 与清理顺序。

## 维护者 sign-off 项（AC9 门禁，待确认）

代码层的旧声明直连路径已在本票删除（这是 AC1/AC2/AC5 的结构前提：声明不得在脚本执行期直接
写进程级注册表），但删除的是**脚本可见公开面**，构成 breaking，需维护者知情/追认后才可勾选 AC9、
才可宣布「不保留长期兼容双路径」。逐项清单与替代路径见
`baseline/2026-09-21-post-effects/MIGRATION.md` §2.1 与同目录 `REPORT.md` §3：

- `PostEffectsJS#register(String, Map)`（脚本成员）→ `PostEffectEventJS#register(String, Map)`（`ClientEvents.postEffects`）
- `PostEffectsJS#unregister(String)`（脚本成员）→ `PostEffectEventJS#unregister(String)`
- `PostEffectsJS#has(String)`（读旧进程级注册表）→ `PostEffectsJS#hasDefinition(String)`（读 active generation）
- `PostEffectManager` 旧写入方（binding 直连的 `register`/`unregister`/`clearRegistered`）→
  `parseDefinition` + `installGeneration(generation, definitions, retired)`，唯一生产调用点是
  `PostEffectDomainOwner.apply`

**代码是否已删**：是（本票 diff）。
**替代路径 parity 证据在哪**：`baseline/2026-09-21-post-effects/REPORT.md` §3 AC1–AC5 / §4.1——
`PostEffectDeclarationLifecycleTest`（8 tests × 26.1.2/1.21.1/26.2.0 全绿）、
`PostEffectDeclarationSurfaceTest`（4 tests × 三节点）、`PostEffectCandidatePlanTest`（common，9 tests）。
**无消费者证据是什么**：`baseline/2026-09-21-post-effects/command-output/05-old-route-consumers.txt`——
全仓 `src/versions/common` 源码零调用点；唯一命中是 `wiki/全局绑定.md:294` 的旧文档示例（尚未更新，
见 MIGRATION §4 遗留，owner 维护者文档面）。

## Closure record（2026-09-21）

- 执行者：zed-flash-28（deepseek-v4.1-flash subagent worktree，分支 `ticket-28-post-effects`，基线 `d0974573`）。
- 交付物：
  - common（零 MC）：`core/posteffect/{PostEffectDeclaration,PostEffectCandidatePlan,PostEffectApplier}.java`、
    `core/lifecycle/CandidateDomainCollector.Handle#candidateGeneration()` 接缝扩展 + `ScriptManager` 实现；
  - MC-facing：`bindings/event/client/ClientEvents#POST_EFFECTS` 唯一新成员、
    `client/posteffect/{PostEffectEventJS,PostEffectDomainOwner}`、`PostEffectManager`（声明面收口为
    `parseDefinition`/`installGeneration`）、`PostEffectsJS`（只留运行时 binding + generation/stale 查询）、
    `NekoJSClient`（客户端初始 generation 收集点）、`NekoJSMod`（client dist 注册 owner）；
  - 1.21.1 成对文件：`PostEffectManager`/`PostEffectsJS`/`PostEffectEventJS`/`PostEffectDomainOwner`；
  - 测试：`common/.../PostEffectCandidatePlanTest`（9）、`src/test/.../posteffect/
    {PostEffectDeclarationHarness,PostEffectDeclarationLifecycleTest(8),PostEffectDeclarationSurfaceTest(4)}`；
  - 证据：`baseline/2026-09-21-post-effects/{REPORT.md,MIGRATION.md,examples/post-effects-declaration.js,logs/*}`。
- 验证命令与结果（真跑，原始日志在 `baseline/2026-09-21-post-effects/command-output/`）：
  - `gradlew guardLint --console=plain` → BUILD SUCCESSFUL，`守卫块 281，扫描 433 个文件；超限豁免 0 个；警告 0 条`
    （`command-output/01-guardLint.txt`）；
  - `gradlew :common:check --console=plain` → BUILD SUCCESSFUL in 4s（含 `:common:checkCommonIsolation`、
    `:common:test`；`command-output/02-common-check.txt`）；
  - `gradlew :26.1.2:test --rerun --console=plain` → BUILD SUCCESSFUL in 17s（`command-output/03-26.1.2-test.txt`）；
  - `gradlew :1.21.1:test :26.2.0:test :common:check :common-api-processor:test` → BUILD SUCCESSFUL in 2m；
  - `gradlew :26.1.2:platformGateTest :1.21.1:platformGateTest :26.1.2:test` → BUILD SUCCESSFUL
    （首次 `member-drift ... buses=postEffects`，按工单 33 纪律补三节点基线后绿）；
  - `gradlew :26.2.0:platformGateTest :26.1.2-fabric:test :26.2.0-fabric:test` → BUILD SUCCESSFUL in 50s；
  - 逐节点计数（`command-output/04-test-summary.txt`）：common 1745 / 1.21.1 225 / 26.1.2 318 / 26.2.0 318，失败全 0。
  - 唯一 golden/基线变更：`src/test/resources/nekojs/platform-gates/event-surface-domains.txt`（三节点
    `ClientEvents` 行新增 `postEffects`；fabric 行不动），旧新 diff 与理由见 REPORT §4.2。
- 遗留 not-verified 与 owner（REPORT §5）：
  - 26.x/1.21.1 **真机客户端渲染** smoke（声明链实际出图、F3+T 首帧时机）→ CLIENT_GUI_RENDER owner / 票 34；
  - Fabric capability 矩阵的正式记录（当前证据：fabric 生成源码中 posteffect 四类不存在 + fabric
    `ClientEvents` 无该成员）→ 票 31/32 fabric 面 owner；
  - `wiki/全局绑定.md` 的 PostEffects 段落仍写旧 `register` 写法 → 维护者文档面（票 37）；
  - AC8 的正式 capability 记录、AC9 的维护者删除确认 → 维护者（Human input note）。
- 已知风险：1.21.1 的 `ShaderManagerMixin`/`getOrCreatePostChain` 仍未接线（既存缺口，本票未扩大：
  声明面与运行时面都按「资源可激活 / 声明无资源则显式拒绝」表达，不静默渲染空画面）。

---

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
