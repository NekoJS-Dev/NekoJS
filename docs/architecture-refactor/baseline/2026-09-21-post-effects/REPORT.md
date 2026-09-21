# 票 28 证据报告：PostEffects 声明事件与运行 binding 分离

日期：2026-09-21
Worktree：`D:/mcmodDemo/NekoJS-mult-t28`（分支 `ticket-28-post-effects`，基线 `d0974573`）
执行者：zed-flash-28（deepseek-v4.1-flash subagent worktree）

## 1. 范围

把 PostEffects 的**声明生命周期**收口到**既有** `ClientEvents` 客户端资源/reload 子事件：

- 新增的唯一事件成员 `ClientEvents.postEffects`（payload `PostEffectEventJS`，client-only、非 dispatch）；
- `register`/`unregister` 在候选期只产出 inert 定义/JSON/Shader 资源描述，commit 点由客户端
  Adapter（`PostEffectDomainOwner`）整体安装新 generation 并退役旧 generation；
- `set`/`clear`/`toggle`/`current` 继续是运行时 binding/Adapter，**不**被声明事件替代，也**不**被
  标记为 reload 事务操作；
- 增加 generation/stale 的只读查询面（`PostEffects.hasDefinition`/`installed`/`activeGeneration`）。

不在本票范围：EntitySelectors / Assets / 已事件化 render 域（不并入、不重复声明）；Fabric 侧
PostEffects 移植（capability 显式记为 unavailable）；不新增第二 runtime owner、第二事件 bus、
第二 registry path 或公开领域 Runtime。

## 2. 实现摘要

### 2.1 common（零 MC/loader，可机检 isolation）

| 文件 | 作用 |
|---|---|
| `common/.../core/posteffect/PostEffectDeclaration.java` | generation-scoped 声明记录（INSTALL/RETIRE + chain JSON + 可选 GLSL 源表），inert 值对象 |
| `common/.../core/posteffect/PostEffectCandidatePlan.java` | 候选计划：收集 install/retire、冻结、退役推导、fingerprint；实现 `CandidateStatePlan`（联合预检/联合发布） |
| `common/.../core/posteffect/PostEffectApplier.java` | 平台接缝：`adapterId` / `preflight`（只校验）/ `apply`（commit 点一次性安装+退役） |
| `common/.../core/lifecycle/CandidateDomainCollector.java` | 收集把手补 `candidateGeneration()`（领域计划归因；票 39 接缝的最小扩展） |
| `common/.../script/ScriptManager.java` | `Handle` 实现返回候选 generation（`generation + 1`） |

inert 结构性证明：`PostEffectCandidatePlan` 与 `PostEffectDeclaration` 的可见依赖里没有任何
Minecraft、renderer、资源或 listener 类型（由 `:common:checkCommonIsolation` 强制）。

### 2.2 MC-facing（共享版本树 src/）

| 文件 | 作用 |
|---|---|
| `src/.../bindings/event/client/ClientEvents.java` | 新增 `POST_EFFECTS = GROUP.client("postEffects", PostEffectEventJS.class)`——既有 ClientEvents 资源/reload 子事件，非第二事件 |
| `src/.../client/posteffect/PostEffectEventJS.java` | 声明 payload：`register(id, options)` / `unregister(id)` / `getDeclaredCount()`；无效 payload 记录收集错误（整批失败），不抛不回滚部分 |
| `src/.../client/posteffect/PostEffectDomainOwner.java` | `CandidateDomainCollector` + `PostEffectApplier`：DOMAIN_PLAN 收集（派发进候选挂起监听器）、joint preflight、commit 点 `apply`、初始 generation 收集点 `applyInitialPlan()`、诊断 outcome（INITIAL/PREFLIGHT_OK/APPLIED/BLOCKED/SKIPPED_IDENTICAL/RECOVERY_FAILED） |
| `src/.../client/posteffect/PostEffectManager.java` | 定义面改为 `parseDefinition`（预检）+ `installGeneration(generation, definitions, retired)`（commit 点原子换 generation、退役 id 并关闭其缓存链）；`set/clear/toggle/current/isActive` 行为保持；`isDeclarationOnly` 取代旧 `isRuntimeOnly`（显式拒绝而不是静默渲染空画面） |
| `src/.../client/posteffect/PostEffectsJS.java` | 只留运行时 binding + 只读查询；**删除** `register`/`unregister` 直连路径 |
| `src/.../client/NekoJSClient.java` | 客户端初始 generation 收集点（CLIENT 脚本初次 `loadScripts()` 之后）——非事务路径没有候选/commit，故显式收集一次 |
| `src/.../NekoJSMod.java` | `registerClient`（client dist）注册 `PostEffectDomainOwner` 到 root；dedicated server 不注册入口、不加载 client 类 |
| `versions/1.21.1/src/.../client/posteffect/{PostEffectManager,PostEffectsJS,PostEffectEventJS,PostEffectDomainOwner}.java` | 1.21.1 成对文件（legacy `shaders/post` 链形状、无 GLSL 源覆盖、`lastSetId` 记账） |

### 2.3 关键语义

- **候选前不可见**：`collect` 只把声明收进 inert plan 并 `registerPlan`；`PostEffectManager.DEFINITIONS`
  在 `apply` 之前不动（本票测试在真实 reload 中用一个 probe 收集器观察 live 面，断言为旧 generation）。
- **整批失败**：收集错误 → plan 毒化 → `preflight` 抛 → `STATE_PLAN` 联合预检失败 → 候选丢弃、
  旧 active 保持；chain JSON 无效同样由 Adapter preflight 整批拒绝。无部分安装路径。
- **退役（声明移除）**：`finish()` 把「旧 generation 拥有但本批未声明」的 id 以及显式 `unregister`
  的 id 变成 RETIRE 声明；`apply` 退役它们（`retired` 计数可观察），不再有旧 generation 的静默 stale 定义。
- **释放顺序**：`installGeneration` 先移除退役 id（并关闭其缓存 runtime chain），再放入新 generation。
- **generation/stale**：`PostEffects.activeGeneration()` 报告已提交的声明 generation；
  `hasDefinition(id)` 对退役/未声明 id 返回 false（旧 token 明确失败，不静默读旧注册）。

## 3. 逐条 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 register/unregister 只通过既有 ClientEvents 资源/reload 子事件；成员/payload/side 唯一，无第二 PostEffect 事件 | 满足 | `PostEffectDeclarationSurfaceTest`（生产注册入口 + 全事件面扫描：`postEffects` 只出现一次、无 `PostEffect*` 组、bus 实例同一、payload=PostEffectEventJS、client-only、非 dispatch）；`event-surface-domains.txt` 三节点基线补 `postEffects` |
| AC2 candidate 只生成定义/JSON/Shader 资源/generation 标记与 Adapter 请求，不提前挂 listener、激活 chain、改画面 | 满足 | `PostEffectCandidatePlanTest.collectingDeclarationsIsInertUntilPublish`（收集期 Adapter 零调用）；`PostEffectDeclarationLifecycleTest.candidateCollectionStaysInertUntilCommitThenSwapsTheGeneration`（真实 reload 候选期 probe 观察到旧定义）；common 计划零 MC 类型 |
| AC3 commit 后新 generation 可回读并生效；旧 generation 注册/listener/资源不再回调，释放顺序可观察，无双重渲染/半更新 | 满足 | `candidateCollection...`（提交后新 id 生效、旧 id `hasActiveDefinition=false`、`retired()==1`、generation +1）；`removedDeclarationRetiresInsteadOfStayingStale`；`explicitUnregisterRetiresAnIdDeclaredEarlierInTheSameBatch`；`generationAndStaleQueries...`（binding 查询与 owner 一致） |
| AC4 候选 JSON/Shader 无效、资源生成失败、reload 中断、客户端不可用或 commit 取消时旧 active 保持可用，候选资源清理 | 满足（reload 中断/客户端不可用见 §5 边界） | `invalidChainJsonFailsTheWholeBatchAndKeepsTheOldActiveGeneration`（STATE_PLAN 拒绝、旧 id 仍在、generation 不前进、诊断不动）；`collectionErrorFailsTheWholeBatchInDomainPlan...`（整批失败后下一轮仍能提交＝无 pending listener 泄漏） |
| AC5 set/clear/toggle/current 仍是运行时 binding/Adapter 且行为保持，不被替代/删除/误标 reload 事务 | 满足 | `PostEffectDeclarationLifecycleTest.runtimeBindingMembersStayAvailableAndAreNotReplacedByTheDeclarationEvent`；`PostEffectDeclarationSurfaceTest.runtimeBindingKeepsItsCallerVisibleMembers`；两节点 `PostEffectManager` 的运行时方法体保持原行为（仅 `isRuntimeOnly`→`isDeclarationOnly` 改名，语义显式化） |
| AC6 26.x 与 1.21.1 的 PostChain/Shader JSON 形状、资源路径与 mixin/Adapter 时机有 fixture 与节点 smoke；平台差异不进 common 作者契约 | 满足（真机渲染 smoke 见 §5） | 成对文件：`PostEffectManager`/`PostEffectsJS`/`PostEffectEventJS`/`PostEffectDomainOwner` 各有 `versions/1.21.1` 实现（legacy `shaders/post` 链 + `lastSetId`，无 GLSL 源覆盖）；fixture 在两节点各自编译执行——**1.21.1 节点真跑 `PostEffectDeclarationLifecycleTest` 8 tests / `PostEffectDeclarationSurfaceTest` 4 tests，0 失败**（`command-output/04-test-summary.txt`）；fixture 的 chain JSON 走各节点自己的 production 生成器（`chainJson()` 的 `>=26` 守卫分支）；JSON 形状由既有 `PostEffectChainJsonTest`（common，6 tests）覆盖；平台差异（`Identifier` vs `ResourceLocation`、`post_effect/` vs `shaders/post/`）只出现在各自文件里，`common` 侧零 MC 类型 |
| AC7 TS/Python declaration、runtime member、contract/golden 与实际事件/binding 成员一致；EntitySelectors/Assets/已事件化 render 域不被并入 | 满足（证据边界见下） | 事件成员进入 `EventSurfaceDomainGateTest` 基线（26.1.2 / 26.2.0 / 1.21.1 三节点，含逐 bus 漂移检测）；binding 成员由反射 fixture 钉住（两节点同一份测试源码编译执行）；本票未触碰 EntitySelectors/Assets/render 域。**边界**：本仓 TS/Python declaration 由 probe 层生成，其 golden（`/golden/query/*.d.txt`、`startup-builders*.d.ts`）只覆盖 query/registry 域——PostEffects 不在其中，本票未新造 declaration golden，如实记为「未新增」而不是「已对齐」 |
| AC8 Fabric 或旧版本不可用时 capability/source-trace/smoke 显式 unavailable/partial 并确定失败，不静默 no-op | 部分满足（capability 记录未新增） | source trace：fabric 节点的 `build/generated/stonecutter/main/.../posteffect/*.java` 整文件仍是 `/*…*///?}` 注释形态（`//? if neoforge {` 在 fabric 为假），即 fabric 编译产物里**不存在** `PostEffectManager`/`PostEffectsJS`/`PostEffectDomainOwner`/`PostEffectEventJS` 四个类；因此脚本侧引用 `PostEffects.*` 或 `ClientEvents.postEffects` 得到「未定义标识符 / No such event bus」的确定失败，而**不是**空画面或成功返回。`event-surface-domains.txt` 的 fabric 行未加 `postEffects`（保持 not-verified 语义）。**边界**：本票未新造 fabric capability 矩阵文件，正式 capability 记录归 §5 |
| AC9 旧静态 register/unregister 直连路径只有在事件资源生命周期、parity、迁移表、无消费者与维护者确认后删除；运行时操作保留 | **不勾选（维护者 sign-off 门禁）** | 代码侧已删除 `PostEffectsJS.register/unregister`（见 §6 breaking 清单与 MIGRATION.md），替代路径 parity/无消费者证据已备；按 Human input note 不等同勾选 |
| AC10 交付声明事件与 set/clear/toggle/current 分工的最小可运行示例与必要迁移材料 | 满足 | `examples/post-effects-declaration.js`；`MIGRATION.md` |

## 4. 真实验证命令与结果

所有命令在本 worktree（`D:/mcmodDemo/NekoJS-mult-t28`）真跑；原始日志落在本目录 `command-output/`。

| # | 命令 | 结果 | 原始输出 |
|---|---|---|---|
| 1 | `gradlew guardLint --console=plain` | BUILD SUCCESSFUL：`guardLint: 守卫块 281，扫描 433 个文件；超限豁免 0 个；警告 0 条` | `command-output/01-guardLint.txt` |
| 2 | `gradlew :common:check --console=plain` | BUILD SUCCESSFUL in 4s（含 `:common:checkCommonIsolation`、`:common:test`、`:common:check`） | `command-output/02-common-check.txt` |
| 3 | `gradlew :26.1.2:test --rerun --console=plain` | BUILD SUCCESSFUL in 17s（`> Task :26.1.2:test` 真执行，非 UP-TO-DATE） | `command-output/03-26.1.2-test.txt` |
| 4 | `gradlew :26.1.2:test :1.21.1:test --console=plain` | BUILD SUCCESSFUL in 21s | 同上 |
| 5 | `gradlew :1.21.1:test :26.2.0:test :common:check :common-api-processor:test --console=plain` | BUILD SUCCESSFUL in 2m | 见 §4.1 |
| 6 | `gradlew :26.1.2:platformGateTest :1.21.1:platformGateTest :26.1.2:test --console=plain` | BUILD SUCCESSFUL（首次 `member-drift ... buses=postEffects`，按 gate 纪律补基线后绿） | 见 §4.2 |
| 7 | `gradlew :26.2.0:platformGateTest :26.1.2-fabric:test :26.2.0-fabric:test --console=plain` | BUILD SUCCESSFUL in 50s | — |
| 8 | `gradlew :26.1.2:compileJava :26.1.2:compileTestJava :1.21.1:compileJava :1.21.1:compileTestJava` | BUILD SUCCESSFUL（仅有仓库既存 deprecation / this-escape 提示） | — |

### 4.1 逐节点 test 结果（test-results XML 汇总，真实计数）

```
node; tests; failures; errors; skipped
common; 1745; 0; 0; 4
1.21.1; 225; 0; 0; 8
26.1.2; 318; 0; 0; 52
26.2.0; 318; 0; 0; 52

ticket 28 fixtures（26.1.2 / 1.21.1 / 26.2.0 三个 NeoForge 节点全跑）
.../PostEffectDeclarationLifecycleTest; tests=8; failures=0; errors=0
.../PostEffectDeclarationSurfaceTest;   tests=4; failures=0; errors=0
common/.../PostEffectCandidatePlanTest; tests=9; failures=0; errors=0
```

完整摘要见 `command-output/04-test-summary.txt`。

### 4.2 golden / 基线变更（旧新 diff 与理由）

本票**唯一**改动的 golden/基线资源是 `src/test/resources/nekojs/platform-gates/event-surface-domains.txt`
（工单 33 的只读跨节点基线）。旧新 diff：

```diff
-ClientEvents | 1.21.1 = present | buses=...,playerTickPre,registerBlockEntityRenderers,...
-ClientEvents | 26.1.2 = present | buses=...,playerTickPre,registerBlockEntityRenderers,...
-ClientEvents | 26.2.0 = present | buses=...,playerTickPre,registerBlockEntityRenderers,...
+ClientEvents | 1.21.1 = present | buses=...,playerTickPre,postEffects,registerBlockEntityRenderers,...
+ClientEvents | 26.1.2 = present | buses=...,playerTickPre,postEffects,registerBlockEntityRenderers,...
+ClientEvents | 26.2.0 = present | buses=...,playerTickPre,postEffects,registerBlockEntityRenderers,...
 ClientEvents | 26.1.2-fabric = present | buses=tick,tickPost,tickPre      （未改）
 ClientEvents | 26.2.0-fabric = present | buses=tick,tickPost,tickPre      （未改）
```

理由：本票新增**唯一**事件成员 `ClientEvents.postEffects`，而该 gate 按设计拒绝「运行时有、
基线未登记」的新成员（`member-drift`），并要求先改实现、再更新基线并留档。基线只增成员、不改
其它域、不动 fabric 行（fabric 无此入口，保持 present/tick 子集语义）。

影响：`EventSurfaceDomainGateTest` 在 26.1.2 / 26.2.0 / 1.21.1 三节点要求该 bus 存在；若将来
删除 `ClientEvents.POST_EFFECTS`，gate 会以 `missing-binding` 失败——这正是本票要的回归保护。
未发现其它 golden 需要变更（`PostEffectChainJsonTest` 断言的是生成器结构，未被本票改动；
query/registry declaration golden 不覆盖 PostEffects，见 AC7 证据边界）。

### 4.3 失败过的红（真实发生并记录）

见 §6「失败诊断」。

## 5. 未验证项与 owner

| 项 | 状态 | owner |
|---|---|---|
| 26.x 真机客户端 smoke（真实 GameRenderer 渲染一帧声明的 post chain） | not-verified：无头/无 MC 客户端运行环境，测试只到 `PostEffectManager` 定义面 + Adapter 诊断 | CLIENT_GUI_RENDER owner / 票 34 P4 真机试做 |
| 1.21.1 `PostEffectDeclarationLifecycleTest` / `PostEffectDeclarationSurfaceTest` | **已验（本机真跑）**：无头 JVM 路径（非事务初始收集点 + 事务 reload + 真实 Adapter）在 1.21.1 节点 8/4 tests 全绿（证据：`command-output/04-test-summary.txt` 列出三个节点各 8/4，0 失败）；仍未验的是**真机客户端渲染**（见上一行） | 票 34 / 平台维护者 |
| Fabric capability/source-trace 记录（PostEffects 当前 unavailable） | 未在本票新增独立 capability 文件；已交付的证据：① fabric 生成源码里四个 posteffect 类整文件被注释（不存在），② fabric `ClientEvents` 无 `postEffects` 成员，③ `:26.1.2-fabric:test`/`:26.2.0-fabric:test` 绿（无红但也不证明支持） | 票 31/32 fabric 面 owner |
| `ShaderManagerMixin` 对 1.21.1 的接入（运行时链激活） | 与本票无关的既存缺口（1.21.1 无对应 mixin，`getOrCreatePostChain` 保持 null/未接线）；声明面与运行时面都按「资源可激活 / 声明无资源则显式拒绝」表达 | 既存移植票 |
| 真机 `ClientEvents.postEffects` 首帧时机（F3+T、初始加载、断言无双重渲染） | not-verified：fixture 走真实 root/Graal/reload 管线与真实 Adapter，但未渲染 | 票 34 真机 smoke |

## 6. 失败诊断（真实发生过的红）

1. **`PostEffectManager.parseShaderMap` 在 1.21.1 不存在**：1.21.1 孪生文件是节点 override，26.x
   共享文件在 1.21.1 上仍走 stonecutter 预处理并编译——新共享文件必须在孪生文件里补同名 API，否则
   `找不到符号`。修复：给 1.21.1 `PostEffectManager` 补 `parseDefinition` / `installGeneration` /
   `installedDefinitions` / `activeGeneration`。
2. **`PostEffectDomainOwner.ACTIVE_GENERATION`（static）被实例字段替换后未完全清理**：一处
   `Select-String` 全仓检查发现残留 `ACTIVE_GENERATION` 仅剩 `PostEffectManager` 的进程级静态
   generation（有意保留）；owner 全部改为实例 `activeGeneration` 后编译绿。
3. **fixture 用 legacy 形状的 chain JSON 在 26.x 上被 `PostChainConfig.CODEC` 拒绝**
   （`No key output / fragment_shader / vertex_shader`）：修复为调用 production 生成器
   `PostEffectChainJson.simpleBlitChainModern/Legacy`，两节点各取自己的形状。
4. **`resetDeclarations` 推进 generation 导致 `-1` 断言失败**：改成直接走 `installGeneration(-1, …)`
   接缝清空定义、不推进 owner generation；用例断言改为相对 generation（每次成功批次恰好 +1），
   避免依赖 JVM 内其它测试的历史。
5. **`event-surface-domains.txt` member-drift**（gate 按设计拒绝未登记的新 bus）：补三节点
   `ClientEvents` 基线行（fabric 行不加，保持 not-verified 语义）。

## 7. 归档与可追溯

- 实现区间提交与逐文件 diff 见票据 `## Closure record`。
- 迁移与删除条件见同目录 `MIGRATION.md`。
- 旧路径无消费者证据：`command-output/05-old-route-consumers.txt`（全仓源码零调用点；唯一命中是
  `wiki/全局绑定.md:294` 的旧文档示例，已列为遗留项）。
- 原始命令日志：`command-output/01-guardLint.txt`、`command-output/02-common-check.txt`、`command-output/03-26.1.2-test.txt`、
  `command-output/04-test-summary.txt`、`command-output/05-old-route-consumers.txt`。
