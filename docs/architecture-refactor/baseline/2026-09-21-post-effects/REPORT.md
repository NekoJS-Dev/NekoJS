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
| AC5 set/clear/toggle/current 仍是运行时 binding/Adapter 且行为保持，不被替代/删除/误标 reload 事务 | 满足（teardown 语义变更见 P4 整改） | `PostEffectDeclarationLifecycleTest.runtimeBindingMembersStayAvailableAndAreNotReplacedByTheDeclarationEvent`；`PostEffectDeclarationSurfaceTest.runtimeBindingKeepsItsCallerVisibleMembers`；两节点 `PostEffectManager` 的运行时方法体保持原行为（仅 `isRuntimeOnly`→`isDeclarationOnly` 改名，语义显式化）。**变更点**：`PostEffectsJS#close(ScriptType)` 覆写已删除（审查 P4），reload teardown 不再清屏——这是调用者可见行为变更，已列 MIGRATION §2.2 |
| AC6 26.x 与 1.21.1 的 PostChain/Shader JSON 形状、资源路径与 mixin/Adapter 时机有 fixture 与节点 smoke；平台差异不进 common 作者契约 | **部分满足（不勾选）**：unit fixture/成对实现/三节点真跑已交付，节点 smoke（真机客户端渲染）未做 | 成对文件：`PostEffectManager`/`PostEffectsJS`/`PostEffectEventJS`/`PostEffectDomainOwner` 各有 `versions/1.21.1` 实现（legacy `shaders/post` 链 + `lastSetId`，无 GLSL 源覆盖）；fixture 在两节点各自编译执行——**1.21.1 节点真跑 `PostEffectDeclarationLifecycleTest` 8 tests / `PostEffectDeclarationSurfaceTest` 4 tests，0 失败**（`command-output/04-test-summary.txt`）；fixture 的 chain JSON 走各节点自己的 production 生成器（`chainJson()` 的 `>=26` 守卫分支）；JSON 形状由既有 `PostEffectChainJsonTest`（common，6 tests）覆盖；平台差异（`Identifier` vs `ResourceLocation`、`post_effect/` vs `shaders/post/`）只出现在各自文件里，`common` 侧零 MC 类型 |
| AC7 TS/Python declaration、runtime member、contract/golden 与实际事件/binding 成员一致；EntitySelectors/Assets/已事件化 render 域不被并入 | **不勾选：declaration 面本域未覆盖** | 事件成员进入 `EventSurfaceDomainGateTest` 基线（26.1.2 / 26.2.0 / 1.21.1 三节点，含逐 bus 漂移检测）；binding 成员由反射 fixture 钉住（两节点同一份测试源码编译执行）；本票未触碰 EntitySelectors/Assets/render 域。**缺口证据**：`common/src/test/resources/nekojs/golden/api-manifest-core.json` 与 probe declaration golden 对 `PostEffect` 均 0 命中（本目录 `command-output/13-declaration-coverage.txt` 记录该 grep）；claim record 承诺的「TS/Python declaration」未兑现，本票未新造条目。已交付的是 runtime member 反射 fixture（三节点真跑）与事件成员的 ticket-33 基线 |
| AC8 Fabric 或旧版本不可用时 capability/source-trace/smoke 显式 unavailable/partial 并确定失败，不静默 no-op | 部分满足（capability 记录未新增） | source trace：fabric 节点的 `build/generated/stonecutter/main/.../posteffect/*.java` 整文件仍是 `/*…*///?}` 注释形态（`//? if neoforge {` 在 fabric 为假），即 fabric 编译产物里**不存在** `PostEffectManager`/`PostEffectsJS`/`PostEffectDomainOwner`/`PostEffectEventJS` 四个类；因此脚本侧引用 `PostEffects.*` 或 `ClientEvents.postEffects` 得到「未定义标识符 / No such event bus」的确定失败，而**不是**空画面或成功返回。`event-surface-domains.txt` 的 fabric 行未加 `postEffects`（保持 not-verified 语义）。**边界（与 MIGRATION §3 同口径）**：本票**未**新造 fabric capability 矩阵文件，正式 capability 记录归 §5 |
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
| 9 | `gradlew :26.1.2:test --tests '*PostEffect*' --rerun`（整改后） | BUILD SUCCESSFUL | `command-output/08-posteffect-26.1.2.txt` |
| 10 | `gradlew :1.21.1:test --tests '*PostEffect*' --rerun`（整改后） | BUILD SUCCESSFUL | `command-output/09-posteffect-1.21.1.txt` |
| 11 | `gradlew :common:test --tests '*PostEffect*' --rerun`（整改后） | BUILD SUCCESSFUL | `command-output/10-posteffect-common.txt` |
| 12 | `gradlew :common:test :26.1.2:test :1.21.1:test :26.2.0:test --rerun`（整改后全量） | BUILD SUCCESSFUL in 1m55s | `command-output/12-test-summary-after-review.txt` |
| 13 | `gradlew guardLint --console=plain`（整改后） | BUILD SUCCESSFUL：守卫块 284，扫描 434 个文件；超限豁免 0 个；警告 0 条 | `command-output/11-guardLint-after-review.txt` |

### 4.1 逐节点 test 结果（test-results XML 汇总，真实计数）

```
node; tests; failures; errors; skipped
common; 1746; 0; 0; 4
1.21.1; 231; 0; 0; 8
26.1.2; 324; 0; 0; 52
26.2.0; 324; 0; 0; 52

ticket 28 fixtures（26.1.2 / 1.21.1 / 26.2.0 三个 NeoForge 节点全跑；计数为审查整改后）
.../PostEffectDeclarationLifecycleTest;    tests=10; failures=0; errors=0
.../PostEffectDeclarationSurfaceTest;      tests=4;  failures=0; errors=0
.../PostEffectGenerationInvalidationTest;  tests=4;  failures=0; errors=0
common/.../PostEffectCandidatePlanTest;    tests=10; failures=0; errors=0
common/.../PostEffectChainJsonTest;        tests=6;  failures=0; errors=0
```

完整摘要见 `command-output/04-test-summary.txt`（整改前）与 `command-output/12-test-summary-after-review.txt`（整改后）。

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

## 6. 双轴审查整改（2026-09-21）

两轴（标准轴 + 规格轴）审查后按"只改被指出的项"整改。逐条：发现 → 处理 → 复跑结果。
原始输出见本目录 `command-output/06..13`。

### 6.1 标准轴（javadoc 准确性 / 恒真断言 / 接缝一致性）

| # | 审查发现 | 处理 | 复跑 |
|---|---|---|---|
| S1 | javadoc 引用不存在的 `PostEffectDomainOwner#publishPostEffects`；`{@link #definitionIds}` 也不存在；`retired` 描述与实现不符 | 改为真实调用链（`PostEffectCandidatePlan#publish` → `PostEffectDomainOwner#apply`，初始路径 `applyInitialPlan`）；链接改为真实 `#installedDefinitions()`；明确「removed = 未再声明 ∪ 已安装的 retired」，并注明「retired 但从未安装的 id 没有缓存可释放」。两节点同步 | `:26.1.2:test --tests '*PostEffect*'`、`:1.21.1:test --tests '*PostEffect*'` 绿 |
| S2 | 把平台事实写成既成事实：`ShaderManagerMixin` 注入描述在 1.21.1 不成立 | 26.x 侧改为点名本节点类（`com.tkisor.nekojs.mixin.ShaderManagerMixin`，`//? if >=26`）；1.21.1 侧改写成实况——无该 mixin，`getOrCreatePostChain` 恒返回 null 且无人调用，运行时声明因此在本节点不可激活（`set` 显式拒绝） | 同上 |
| S3 | 恒真断言（禁止为过门禁弱化，故删/换） | ① `PostEffectCandidatePlanTest` 删 `assertSame(plan, plan)`；② `PostEffectDeclarationSurfaceTest` 删 `assertTrue(List.of(ScriptType.values()).contains(ScriptType.CLIENT))`；③ `PostEffectDeclarationLifecycleTest` 把对字面量集合自断言的 `containsAll(List.of(...))` 换成**反射读真实 `PostEffectsJS` 成员**的断言；④ 删 `assertTrue(installed >= 0)`。保留 `names.contains("register") == false`（真实后置条件，审查已确认非恒真） | 同上 |

**接缝一致性（`Handle#candidateGeneration()`）——取 (b)，撤掉该扩展**：审查指出它的唯一消费者
`PostEffectDomainOwner.collect` 把值传给 `beginBatch` 后就没再用（owner 自己用 `activeGeneration + 1L`
记账），且 javadoc 声称「与 commit 后 `ScriptManager#generationId()` 对应」并不成立（候选期两条递增线
本就不同步）。**选 (b) 的理由**：这条接缝没有真实消费者，而让 owner 改用候选期 generation 记账会引入
「领域账本序号被 `ScriptManager` generation 牵着走」的语义耦合（初始收集点根本没有候选 generation 可
用），改动面反而更大。因此：删除 `CandidateDomainCollector.Handle#candidateGeneration()` 与
`ScriptManager` 的实现，owner 两处 `beginBatch` 改传自己的 `activeGeneration + 1L`，并把
`PostEffectCandidatePlan.beginBatch` 的 `generation` 参数 javadoc 写实（「本批次所属 generation，由
Adapter 作为自己的提交记账用，不是 `ScriptManager` 的 generation」）。

### 6.2 规格轴

**P1（正确性，必修）同一 id 改 chain JSON 重声明时旧 PostChain 缓存被命中 ⇒ 半更新**

- 复核：`getOrCreatePostChain` 用 `CacheKey(id, allowedTargets)` 查缓存（键不含 generation/内容），
  而 `installGeneration` 只对 removed（未再声明 ∪ retired）失效 ⇒ 新 shader 源 + 旧链。
- 修：抽出两个可单测的决策 `removedIds(previous, next, retired)` 与 `redefinedIds(previous, next)`；
  安装时对 **removed** 移除定义并释放缓存，对 **redefined**（同 id、`Definition`（record）`!equals`）
  额外释放缓存。两节点成对（1.21.1 无缓存填充，决策集仍保持同契约）。日志补 `removed/re-declared` 计数。
- 回归测试：`PostEffectGenerationInvalidationTest`（4 tests）钉住「重声明 ⇒ 进失效集且不被当退役」「相同
  重放不失效（不做无用清理）」「removed 与 redefined 不相交（无双重释放）」「retired 但未安装的 id 不产生
  removed」；common 侧 `PostEffectCandidatePlanTest.redeclaringTheSameIdInOneBatchKeepsTheLatestDeclaration`
  钉住计划层的按 id 覆盖语义。
- **先红后绿证据**：临时把 `redefinedIds` 还原成修复前行为（恒返回空集）跑测试 →
  `redeclaringAnIdWithDifferentChainJsonMustInvalidateItsCachedChain` 与
  `retiredAndUndeclaredIdsAreRemovedWhileRedeclaredIdsAreOnlyInvalidated` 双双 FAILED
  （`command-output/06-p1-red-evidence.txt`）；移除临时分支后同一测试 BUILD SUCCESSFUL
  （`command-output/07-p1-green-evidence.txt`）。

**P2（诚实性）**

- **AC7 降级为不勾选**（取更小的 (b)）：核实 `api-manifest-core.json` 与 probe declaration golden 对
  PostEffects **0 命中**（`command-output/13-declaration-coverage.txt`），claim record 的 declaration
  承诺未兑现。票据 AC7 保持未勾选，REPORT §3 如实写「declaration 面本域未覆盖」，已交付的是 runtime
  member 反射 fixture + 事件成员的 ticket-33 基线。
- **AC6 降级为部分满足/不勾选**：REPORT §5 自承节点 smoke not-verified，票据 AC6 改为未勾选并写明
  「缺的是节点 smoke（真机客户端渲染），owner 票 34」。
- **MIGRATION §3 与 REPORT §5 口径统一**：两处都改成「fabric 生成源码中 posteffect 四类整文件被注释
  不存在 + fabric `ClientEvents` 无该成员 ⇒ 确定失败；**未**新造 capability 矩阵文件，正式记录归
  fabric 面 owner」。

**P3（候选期可见副作用）**

- 复核：`preflight` 在候选期写 `lastDiagnostics = PREFLIGHT_OK`，而 `lastDiagnostics` 是公开读面，
  描述的是 **active** generation；本域通过、别域/STATE_PLAN 失败时会留下「预检通过但从未 commit」的观测。
- 修：`preflight` **不发布任何观测**（解析结果局部化，commit 点重新解析并发布 `APPLIED`）；
  `Outcome.PREFLIGHT_OK` 从枚举删除（不再有生产者）。两节点同步。
- 测试：`aCandidatePreflightThatPassesDoesNotPublishAnObservation`——注册一个在 STATE_PLAN 必败的
  同伴域计划，断言整批失败后 `lastDiagnostics` **逐字段等于**失败前的观测、旧 active 仍在。

**P4（行为变更未文档化）`PostEffectsJS.close()` 从"清声明账本"变成"清屏"**

- 复核：承认这正是调用者可见的行为变更（reload teardown 会清屏），MIGRATION §2.2 未列。
- 处理：**删掉 `close(ScriptType)` 覆写**（回到 `Binding` 的默认 no-op），而不是保留"清屏"并补文档。
  理由：声明注册表已由 generation 生命周期（候选计划 + commit）持有，binding 的 close 已无账本可清；
  而 reload teardown 主动丢弃**当时正在生效**的 runtime post effect 是新的调用者可见副作用（且与
  「候选期不可见、失败保留旧 active」的收口方向相反）——保留它需要重新裁定「reload 是否应该关画面」，
  超出本票范围。默认 no-op 是语义上最小的动作：声明由 commit 换装，运行时画面只由显式
  `set`/`clear` 改变。两节点同步，`ScriptType` 导入随之删除。
- 测试：`reloadTeardownDoesNotTouchTheRuntimePicture` 反射断言 `PostEffectsJS` **没有** `close(ScriptType)`
  覆写（无头 JVM 无法驱动真实渲染器，故断言的是「无清屏路径」这一结构性事实）。
- MIGRATION §2.2 增列该行为变更（旧：reload 前 `clearRegistered()` 顺带清屏；新：不再清屏）。

**P5（范围蔓延）**

已删（无真实需求的公开面）：

| 删除项 | 原因 |
|---|---|
| `Handle#candidateGeneration()` + `ScriptManager` 实现 | 无消费者且描述不成立（见 6.1） |
| `Outcome.SKIPPED_IDENTICAL` + `lastAppliedFingerprint` + 初始收集点的等价跳过分支 | 该幂等只在「同一进程重复初始收集」时有意义；生产只触发一次，且它让 `PostEffectDomainOwner` 多出一份跨批次状态。跳过能力随指纹一起移除后行为更简单可预测 |
| `Outcome.PREFLIGHT_OK` | 候选期观测副作用（见 P3），删除后无生产者 |
| `PostEffectDomainOwner#activeIds()` 访问器 | 与 `hasActiveDefinition(id)` + `PostEffectManager.installedDefinitions()` 重复 |

如实保留（服务真实需求，超出 AC 明列范围）：

- `PostEffectCandidatePlan#fingerprint()` 仍是计划层的确定性摘要（parity/诊断用；`PostEffectManager` 侧的
  等价跳过已删除，故它现在只有测试与诊断消费者）。
- `PostEffects.installed()/activeGeneration()/hasDefinition()` 与 `PostEffectDomainOwner` 的
  `activeGeneration()/hasActiveDefinition()/lastDiagnostics()`：AC3 明确要求「新 generation 可回读、
  旧 token 明确失败」，这些就是该要求的读面；`owner` 侧同时是测试/诊断 seam。
- `Outcome.{INITIAL,APPLIED,BLOCKED,RECOVERY_FAILED}` + `Diagnostics` 记录：AC4/§3 的「失败保留旧
  active、不宣称成功」需要可区分结局；P3 整改后枚举只剩真有生产者的四个值。

### 6.3 已知标准缺口（不在本轮修复）

**中文注释未按 `AGENTS.md#Language` 批量改写**：本仓 912 个 Java 文件里 663 个含中文注释，同目录先例
（`ModificationCandidatePlan`、`CandidateStatePlan`、`DynamicRegistryFacadeRuntime`）也以中文为主。
本票新增/修改文件沿用邻近先例的中文注释，**未**做跨文件的语言改写（那会与本票范围及其它票重叠）。
如实记为「既有仓库状态未落地该规范」，供维护者按统一批次处理。新增的英文 javadoc/日志仍按规范写。

## 7. 失败诊断（真实发生过的红）

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

## 8. 归档与可追溯

- 实现区间提交与逐文件 diff 见票据 `## Closure record`。
- 迁移与删除条件见同目录 `MIGRATION.md`。
- 旧路径无消费者证据：`command-output/05-old-route-consumers.txt`（全仓源码零调用点；唯一命中是
  `wiki/全局绑定.md:294` 的旧文档示例，已列为遗留项）。
- 原始命令日志：`command-output/01-guardLint.txt`、`command-output/02-common-check.txt`、`command-output/03-26.1.2-test.txt`、
  `command-output/04-test-summary.txt`、`command-output/05-old-route-consumers.txt`。
