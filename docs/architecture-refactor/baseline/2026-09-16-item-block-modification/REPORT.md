# 2026-09-16 item/block modification：候选计划、snapshot ownership 与 Adapter 应用报告（ticket 39）

> Ticket: [39: Runtime Item/Block modification 候选计划与 snapshot ownership](../../implementation-tickets/39-item-block-modification.md)
> Worktree: `D:\mcmodDemo\NekoJS\.worktrees\t39\NekoJS-mult`（分支 `ticket-39-item-block-mod`，认领基线 `230587cc`）
> 执行：zcode-agent（接管半成品 worktree：保留 4 个已提交成果，重写编译不过的未提交测试改动）。
> 本文件是**实施与证据**记录，不是实施授权；AC14 的旧公开路径删除仍需维护者 sign-off。

## 1. 结论摘要

- 旧路径（直接改 live Item/Block + 进程级 `static SNAPSHOTS` + `fire()` restore-all 重放）已收口为
  **generation-scoped inert 候选计划**：候选期只收集/校验/规范化声明，不触碰 live 对象；合法 commit 点
  才由平台/版本 Adapter `ModificationDomainOwner` 应用；失败保留旧 active 并清理候选资源。
- `ItemEvents.modification` / `BlockEvents.modification` 的**事件面未变**（同一组、同一事件名、同一 payload
  类、SERVER side、非 cancel/非 dispatch、posted-object 模式），不新增第二 bus；catalog/golden 面继续派生。
- 与票 10 的联合边界已接通：领域计划实现 `CandidateStatePlan`，同一候选的修改计划与 global/shared 顶层
  写集在 STATE_PLAN 联合预检、commit 点联合发布/联合失败（common 层 fixture 双向覆盖）。
- `item.setMaxStackSize(16)` 与 `item.maxStackSize = 16` 经 `ModificationViewSurface`（ProxyObject
  putMember → 同一 `Method` seam）命中同一 setter/校验/规范化/声明/fingerprint；真实 GraalJS Context
  fixture 在本机实测五节点真跑（26.1.2 / 26.2.0 / 26.1.2-fabric / 26.2.0-fabric / 1.21.1）；
  registry-gated fixture 仅在 vanilla 注册表可用的环境执行，skip 计数如实登记。
- 14 条 AC：13 条满足（其中 AC1/AC10/AC11 的**在游戏内/客户端侧**那一部分以 capability/source-trace +
  registry-gated fixture 记录，裸 JVM 下 skip 计数如实登记）；**AC14 明确不勾选**（删除门禁等待维护者
  sign-off，见 §11）。
- 真实运行：`:common:check`、`:common-api-processor:test`、`guardLint`、`:26.1.2:build`、
  `:26.1.2-fabric:build`、`:1.21.1:build` 全部 BUILD SUCCESSFUL（逐命令数字见 `evidence/verification-commands.md`）。

## 2. 提交清单

| Commit | 内容 | 对应 AC |
|---|---|---|
| `fef773b9` | `test`: DataSyncGenerationBoundaryTest 适配 4 参 `ScriptEnvironmentFactory`（接管前已提交，保留） | 前置修复 |
| `bcbbad6f` | `test(modification)`: 旧 direct-mutation 路径 characterization（同目标多声明整体替换、item 声明移除 stale、GraalJS 宿主 property 写静默丢弃） | AC6（characterization 前置） |
| `7b0d5d8b` | `feat(core)`: generation-scoped modification plan core + `ReloadPhase.DOMAIN_PLAN` 收集阶段 + root 收集器注册表 | AC2/3/4/7/9 机制面 |
| `d2c49f2a` | `feat(modification)`: domain-owned inert candidate plans with adapter apply（`ModificationViewSurface`、owner、payload、平台接线） | AC1/2/5/7/8/11/13 |
| `41b2bc2c` | `fix(modification)`: 恢复改造前函数式回调签名 `Consumer<T>`（接管时未提交改动把 `modify` 形参改成 `Object` → Java 侧 lambda 不可用、契约被弱化），显式 setter 调用形态经成员目录可解析，ProxyObject 接缝不进成员目录 | AC8/AC13 |
| `8c928a98` | `test(modification)`: 脚本→Adapter E2E（item/block）、catalog/surface、ownership guard、示例执行 + 接管时测试改动复核对齐 | AC1/5/8/12/13 |
| `docs(modification)` | baseline 交付物：本 REPORT + `MIGRATION.md` + `examples/` + `evidence/`（提交号见 `git log --oneline 230587cc..HEAD`） | AC12/AC14 门禁材料 |
| `bc1fb396` | 双轴审查整改（F1–F5 必修 + F6/F7/F8/F9/F10/F11/F13）：生产投递形态 parity 真跑、registry-free AC6/旧事实探针、breaking 清单与 sign-off 节、dispatch-key 拒绝、write-only 读面、指纹复位、可机检网络边界、数字勘误 | 见 §14 逐条 |

## 3. 机制设计（与 05/06/07/10 的衔接）

### 3.1 两个合法收集点（唯一 post 来源）

| 收集点 | 触发 | 派发目标 | 计划去向 |
|---|---|---|---|
| 初始 generation | 服务器 `about-to-start`（`ServerEventListener.onServerAboutToStart` / fabric `FabricServerEventBindings`）→ `ModificationDomainOwner#applyInitialPlan` | **active** 总线监听器（`ItemEvents.MODIFICATION.post`） | 同 owner 内 preflight → 应用（无旧 active 时旧值 = vanilla 基线）；指纹与上次相同则跳过重放 |
| 事务 reload | `SERVER` reload 的 `ReloadPhase.DOMAIN_PLAN` → `CandidateDomainCollector#collect` | **候选** 挂起监听器（`Handle#dispatch`，priority 降序 + 注册序稳定，与 `EventBusBase` 编译快照同序） | inert `ModificationCandidatePlan` → `CandidateStatePlan` 挂入候选 `GenerationGlobals`（STATE_PLAN 联合预检、commit 联合发布） |

source trace（`grep` 结果见 §8）：生产路径上只有 `ModificationDomainOwner` 引用这两条总线——
`versions/1.21.1` 与 fabric 复用同一 owner 类；无第三处 `post`/`dispatch`，无第二 bus。

### 3.2 候选 inert 与联合边界

- 收集期 `ItemModificationEventJS#modify` 只产出 `ModificationDeclaration`（纯 JVM 值：String/Number/
  Boolean/null/嵌套 Map），挂到候选计划；`ModificationDeclaration` 结构性拒绝 MC 对象类型。
- `ModificationCandidatePlan implements CandidateStatePlan`：`preflight()` 走 Adapter 只读校验、
  `publish()` 才应用到 live 对象；候选失败/被 close 抢占/watchdog 终止时随 `GenerationGlobals.discard()`
  丢弃（从不 publish）。
- 联合成功/失败由票 10 的 `GenerationGlobals.preflightJoint()/publishJoint()` 承载：领域计划失败时
  global/shared 写集不发布，反之亦然（`Ticket39DomainCollectionTest.domainPlanAndGlobalWriteSetCommitJointlyOrNotAtAll`
  双向断言）。

### 3.3 失败整批保留与诊断

| 失败位置 | 触发例 | 结果 | 诊断 |
|---|---|---|---|
| DOMAIN_PLAN（收集期） | 未知目标 id、回调抛出 | 候选失败，旧 active 保留，候选资源丢弃 | reload 失败结果 `phase=DOMAIN_PLAN`、`domain=domain-collect:item-block-modification` |
| STATE_PLAN（联合预检） | 值域越界（`maxStackSize=500`）、组件不变量（可堆叠 + 可损坏）、未知目标在 Adapter 侧解析失败；以及收集器用可选 API `ModificationCandidatePlan#fail` 记下的失败标记（**Item/Block 生产路径不使用它**——真实收集器让 `modify` 的异常直接上抛，见上一行；仅合成/第三方收集器可达） | 整批不提交，global/shared 写集同样不发布 | `phase=STATE_PLAN`、`domain=state-plan-preflight:item-block-modification` |
| Adapter 拒绝（预检内） | `fireResistant` 无 server 绑定（26.x damage type registry） | 整批 blocked，保持既有值 | `ModificationDomainOwner.Outcome.BLOCKED` + detail |
| 应用期意外抛出 | Adapter 不变量被破坏（对象在预检后消失等） | 尽力恢复基线后异常上抛（不宣称成功） | `Outcome.RECOVERY_FAILED` |

诊断面 `ModificationDomainOwner.Diagnostics(outcome, source, itemDeclarations, blockDeclarations,
restoredTargets, detail)` 区分 `INITIAL / APPLIED / RESTORED / SKIPPED_IDENTICAL / BLOCKED /
RECOVERY_FAILED`（AC7：「静默 stale 不算成功」）。

### 3.4 Adapter 契约与恢复承诺

`ModificationApplier`（common 接缝，零 MC 类型）：

- `preflight(declarations)`：目标解析 + 用**基线**校验不变量 + 确认可恢复；只读，抛出即整批失败；
- `apply(declarations)`：**先恢复全部 NekoJS 持有基线，再按声明顺序应用完整新计划**；preflight 通过后
  不得抛出；意外抛出时尽力恢复并上抛（`CandidateStatePlan#publish` 契约：部分副作用不承诺深回滚）。

恢复只承诺「NekoJS 拥有且 Adapter 已证明可恢复」的字段：item 默认组件映射（首改时捕获）、block 六属性的
三处副本（`Properties` + `Block` + 每个 `BlockState`，含原始 per-state 光照函数）。不承诺回滚任意 Java
对象内部状态、其他 mod、世界、网络或文件副作用（spec 09 外部副作用边界）。

### 3.5 setter / property parity（AC8）

`ModificationViewSurface` 把视图包成 `ProxyObject`（票 15 `BuilderSurface` 同款手法），并被视图类自身
实现转发（`ItemModificationJS`/`BlockModificationJS implements ProxyObject`），因此脚本回调参数无论以
「surface 包装」还是**生产投递形态**（裸视图）到达，行为一致：

- `item.maxStackSize = 16` → `putMember("maxStackSize")` → `setMaxStackSize(int)`；
- `item.setMaxStackSize(16)` → `getMember("setMaxStackSize")` → 同一 `Method` 的 `ProxyExecutable` 包装
  （成员目录同时收录属性名与访问器原名；读面命中 getter，write-only 成员的读面显式报错而不是返回
  setter 函数对象）；
- 未知成员/只读成员/类型不匹配在写入期抛出带成员名与可写成员目录的错误（两种写法的错误同源）；
- 成员目录按视图类反射派生并缓存，`memberNames()` 去重（同一访问器同时以属性名与访问器原名收录）；
  `ProxyObject` 自身的接口方法（`getMember`/`putMember`/…）与包私有引擎接缝（`applyTo`、
  `normalizedProperties`）**不进**脚本面。

**两种到达形态都有真跑证据**（`ModificationSetterPropertyParityTest`，10 用例，本机实测五节点真跑）：

| 到达形态 | 产生方式 | 覆盖 |
|---|---|---|
| surface 包装 | `context.getBindings("js").putMember("item", ModificationViewSurface.of(view))` | property 写 / 显式 setter / 错误同源 / 类型不匹配 / 未知成员 / food 面（>=26） |
| **生产投递** | JS 函数 → 沙盒 `HostAccess`（`NekoSharedHostAccess`）实现成 `Consumer` → `callback.accept(裸视图)`（与 `ItemModificationEventJS#modify` 同形） | property 写 / 显式 setter / 与 surface 形态同 declaration + 同 fingerprint / 错误同源 |

生产投递形态是最危险的静默失败形态（若裸视图的 ProxyObject 不被 honor，property 写会在生产路径上
被静默丢弃而 surface 形态测试仍全绿），因此有专门用例固定。

`ModificationCandidatePlan.fingerprint()` 对规范化声明求确定性指纹（声明顺序 + 单声明内属性按名排序 +
`5` 与 `5.0` 同值的数值形态归一），两种写法产生同一 fingerprint（真实 GraalJS fixture 固定）。
收集派发（`CandidateDomainCollector.Handle#dispatch`）只支持非 dispatch 总线：按 key 定向分发的总线
需要 key 才能判定投递子集，收集期没有 key 上下文，调用被显式拒绝（`UnsupportedOperationException`），
不做「忽略 key 全量派发」的静默降级。

## 4. 旧行为 characterization 摘要（commit `bcbbad6f`，本次复核）

| 旧事实（重构前实测） | 处置 | 现状 fixture |
|---|---|---|
| 同一次重放内同目标多条声明：后一条**整体替换**前一条（每次从基线叠加，不按属性合并） | **保留**（不引入同 key 拒绝、不新增 last-write-wins 政策） | **registry-free（本机实测五节点真跑）**：`ModificationLegacyCharacterizationTest.sameTargetDeclarationsKeepOrderAndLastOneWholeReplacesEarlierAtPlanLayer`（真实视图产出声明 + 真实计划 + 按声明序应用/整体替换）；端到端：同文件 `sameTargetMultipleDeclarationsLastOneEntirelyReplacesEarlierOnes`（registry-gated）+ common 层 `Ticket39DomainCollectionTest` |
| 「声明消失」不对称：block 每次重放前 restore-all；item 只在再次 modify 时恢复 → item 声明移除后保持 stale | **有意改变**（AC7）：item/block 统一「恢复基线 → 应用完整新计划」，记入 MIGRATION.md | `removedItemDeclarationNowRestoresBaselineInsteadOfStayingStale`（断言翻转 + 迁移表条目） |
| GraalJS 宿主对象的 property 写被静默丢弃（`assigned,read=undefined`，不落 setter） | **修复**（AC8）：`ModificationViewSurface` putMember seam（视图自实现 ProxyObject） | 旧事实复现：`ModificationLegacyCharacterizationTest.hostPropertyWriteLegacyFactAndProxyObjectViewContract`（registry-free，真跑）；修复面：`ModificationSetterPropertyParityTest`（10 用例，含生产投递形态，真跑） |
| 进程级 `static SNAPSHOTS`（item `Map<Identifier, DataComponentMap>` / block `Map<Identifier, PropertySnapshot>`）跨脚本、跨世界存活 | **删除**（AC5）：改为 root 授权 owner 的实例字段，root close 恢复并清空 | `Ticket39ModificationOwnershipTest.payloadsAndOwnerHoldNoProcessLevelStaticSnapshotState`（真跑）+ `Ticket39DomainCollectionTest.rootCloseClosesCollectorsSoIndependentRootsDoNotBleed` |
| `fire()`（restore-all 后重新 post）+ 直接改 live 对象 | **删除**（AC3/AC14 旁路证据）：改为收集 + commit 点 Adapter 应用 | `restoreAllThenReplayEntryPointsAreGone`（真跑）+ §8 source trace |

## 5. snapshot ownership 表（AC5）

| 状态 | Owner | 生命周期分类 | 清理点 | 证据 |
|---|---|---|---|---|
| item 默认组件基线 `itemBaselines` | `ModificationDomainOwner`（实例字段，root 注册持有） | 进程级例外（默认组件注册期冻结、从不下变），但**按 root 生命周期持有** | root close → 恢复基线并 clear | `rootCloseClosesCollectorsSoIndependentRootsDoNotBleed`、`Ticket39ModificationScriptE2ETest` 的 `@AfterEach owner.close()` |
| block 属性基线 `blockBaselines`（26.x，含原始光照函数） | 同上 | 同上 | 同上 | `Ticket39BlockModificationScriptE2ETest`（恢复 per-state 光照函数）、`BlockModificationEventJSTest` |
| 候选计划 `ModificationCandidatePlan` | 候选 generation（`GenerationGlobals`） | generation 级 | 候选失败/抢占/watchdog → `discard()`（从不 publish） | `Ticket39DomainCollectionTest.candidateCollectsInertPlanAndAppliesExactlyOnceAtCommit` / `candidatePlanIsInertUntilCommit` |
| 挂起监听器 `pendingListeners` | 候选 generation（`ScriptManager`） | generation 级 | commit 点激活 / 候选丢弃 | 同上 + `collectionListenerErrorFailsTheCandidateAndKeepsOldActivePlan` |
| 最近应用指纹 `lastAppliedFingerprint` + 诊断 | `ModificationDomainOwner` 实例 | 进程级例外（同一 owner） | root close 清空 | `Ticket39ModificationOwnershipTest` |
| 旧 `static SNAPSHOTS` / `fire()` | —— | **已删除** | —— | 同类结构性 guard（真跑） |

独立测试 root 不互相污染：每个 fixture 实例化自己的 owner（`new ModificationDomainOwner()`）并在
`@AfterEach` close；common 层 fixture 用真实 root 并在 try-with-resources 内 close。

## 6. 五节点差异表（AC11）

| 面 | 26.1.2 | 26.2.0 | 26.1.2-fabric | 26.2.0-fabric | 1.21.1 |
|---|---|---|---|---|---|
| Adapter 实现 | `ModificationDomainOwner`（共享 `src/main`，`//? if >=26`） | 同左（同一编译单元） | 同左（fabric 复用同一 owner，接线在 `NekoJSFabricMod`/`FabricServerEventBindings`） | 同左 | `versions/1.21.1` 成对文件 |
| 收集/应用接缝 | `CandidateDomainCollector` + `ModificationApplier` | 同左 | 同左 | 同左 | 同左（同一 common 契约） |
| block 半边 | 有（`BlockEvents.modification`，26.x 独有） | 有 | 有（`BlockEvents` 共享声明） | 有 | **无**（无此总线；脚本得「无此成员」明确错误） |
| 组件发布机制 | `Holder.Reference#bindComponents`（无反射） | 同左 | 同左 | 同左 | 反射写 `Item#components` 私有 final 字段 |
| item 复合面 | `food`/`tool`/`attackDamage`/`attackSpeed` | 同左 | 同左 | 同左 | 只有 `maxStackSize`/`maxDamage`/`rarity`/`fireResistant` |
| `fireResistant` | `DAMAGE_RESISTANT` 组件指向 `IS_FIRE` damage type tag（需 server 绑定的 registry） | 同左 | 同左 | 同左 | `FIRE_RESISTANT:Unit`（无需 server 绑定） |
| `maxStackSize` 上限 | `Item#ABSOLUTE_MAX_STACK_SIZE` = 99 | 同左 | 同左 | 同左 | 同值常量（1.21.1 无该常量） |
| 初始收集点接线 | `NekoJSMod.initializeScripts` + `ServerEventListener.onServerAboutToStart` | 同左（共享 `src/main`） | `NekoJSFabricMod` + `FabricServerEventBindings` | 同左 | `versions/1.21.1` 的 `ServerEventListener` |
| catalog 成员 golden | `golden/block-events-api.txt`（含 `modification`） | 同 26.1.2 | fabric 复用共享声明 | 同左 | `golden/block-events-api.txt`（无 `modification`） |
| Fabric parity | —— | —— | 复用同一 Adapter，**不自动补** Fabric 特有同步面（未在本票声明 supported 的地方保持显式边界） | 同左 | —— |

差异只出现在 Adapter（`ModificationDomainOwner` 及 payload 的成对文件）与平台接线，common 契约、
事件面、catalog、parity fixture 五节点共享。

## 7. AC1–AC14 逐条判定

| AC | 判定 | 证据（真跑标记） |
|---|---|---|
| AC1 事件名/payload/side/priority/cancel/dispatch 进 catalog/golden，不新增第二 bus | **满足** | `Ticket39ModificationEventSurfaceTest`（2 用例，真跑：catalog 每 bus 恰一条、payload 类、SERVER、非 cancel/非 dispatch）；`EventApiSurfaceGoldenTest` + 节点 golden `block-events-api.txt`（26.x 含 `modification`）；`EventSurfaceOwnershipTest`（跨组 bus identity 唯一）；dispatch 时机 = §3.1 两个收集点（§8 source trace） |
| AC2 candidate 只生成 inert 计划，不碰 live Item/Block/BlockState/默认组件/平台集合，对其他 generation/外部节点不可见 | **满足** | `Ticket39DomainCollectionTest.candidatePlanIsInertUntilCommit`（真跑，probe 读到旧 active 值）；`Ticket39ModificationScriptE2ETest.candidateStaysInertUntilCommitAndReloadAppliesTheNewPlan`、`Ticket39BlockModificationScriptE2ETest.candidateStaysInertForBlocksAndEmptyPlanRestoresBaseline`（registry-gated，裸 JVM skip）；`ModificationDeclaration` 结构性拒绝 MC 类型 |
| AC3 commit 前旧修改继续执行；commit 后新计划恰好应用一次，旧 generation 计划/候选资源按所有权释放，无双重 restore/reapply | **满足** | `candidateCollectsInertPlanAndAppliesExactlyOnceAtCommit`（applyCount==1，真跑）；`ModificationDomainOwner.apply` 单次恢复+应用；初始收集点的指纹等价跳过（`SKIPPED_IDENTICAL`）；`rootCloseClosesCollectorsSoIndependentRootsDoNotBleed` |
| AC4 收集/payload 校验/规范化/Adapter 拒绝/reload 失败/watchdog/close 抢占 → 整批失败保留旧 active，无部分修改/混合 generation/残留 pending listener；恢复只承诺可证明可恢复字段 | **满足** | `collectionListenerErrorFailsTheCandidateAndKeepsOldActivePlan`、`planMarkedFailedDuringCollectionFailsJointPreflightWithoutPartialApply`（真跑）；`Ticket39ModificationScriptE2ETest.rejectedBatchKeepsOldActivePlanWithoutPartialApply` / `collectionErrorFailsWholeBatchAndNextGoodReloadStillCommits`、`Ticket39BlockModificationScriptE2ETest.multipleBlocksInOneScriptCommitTogetherOrNotAtAll`（registry-gated）；watchdog/close 抢占语义由票 07 fixture 承载并在 §10 登记 |
| AC5 snapshot/restore/reapply 唯一 owner + 生命周期归类；root close 清理；进程级例外可重复测试；旧 static `SNAPSHOTS` 按删除条件移除 | **满足（删除按门禁）** | §5 ownership 表；`Ticket39ModificationOwnershipTest`（真跑：无 static Map/Collection 字段、无 restore-all 入口）；`rootCloseClosesCollectorsSoIndependentRootsDoNotBleed`。旧公开路径的**正式删除**仍待维护者 sign-off（§11） |
| AC6 先 characterization 多脚本/多声明/注册顺序行为；重构后按同一可观察顺序确定性重放，不引入同 key 拒绝或新合并政策 | **满足（两层证据：计划层真跑 + 端到端 registry-gated）** | characterization：`bcbbad6f`（旧 direct-mutation 实测）；**registry-free（本机实测五节点真跑）**：`ModificationLegacyCharacterizationTest.sameTargetDeclarationsKeepOrderAndLastOneWholeReplacesEarlierAtPlanLayer`（声明按注册序全量保留 + Adapter 按声明序收到全部声明 + 后声明整体替换、前声明其它属性不残留）、`Ticket39DomainCollectionTest`（真实脚本 + 真实 root + 按声明序应用）；registry-gated 端到端：`sameTargetMultipleDeclarationsLastOneEntirelyReplacesEarlierOnes`；注册顺序语义：collector dispatch 顺序 = priority 降序 + 注册序（`ScriptManager.CandidateCollectionHandleImpl`） |
| AC7 声明移除 → 成功 reload 先恢复基线再应用完整新计划；不可证明可恢复的字段阻止该批；诊断区分 active/blocked/recovery-failed/restored，不把静默 stale 当成功 | **满足** | `removedDeclarationsRestoreBaselinesViaAnEmptyPlanAtCommit`（真跑）；`ModificationLegacyCharacterizationTest.removedItemDeclarationNowRestoresBaselineInsteadOfStayingStale`；`Ticket39ModificationScriptE2ETest.removedDeclarationRestoresNekoJsBaselineInsteadOfStayingStale`；`Outcome` 枚举 6 态 + 两个 E2E 断言 outcome |
| AC8 setter 与 property 赋值同一 setter/校验/规范化/fingerprint/计划路径；无同名 public field 旁路；GraalJS runtime contract test 固定两种写法等价 | **满足（两种到达形态均真跑）** | `ModificationSetterPropertyParityTest`（10 用例，本机实测五节点真跑，真实 GraalJS Context）：规范化声明等价、fingerprint 等价、错误同源、未知成员、类型不匹配、write-only 读面报错、food 对象字面量/null 移除（>=26）；**生产投递形态**（JS 函数 → 沙盒 `HostAccess` 实现成 `Consumer` → 裸视图，与 `ItemModificationEventJS#modify` 同形）与 surface 形态产生同一声明与同一 fingerprint；`viewsExposeNoLiveMutationSeam`（真跑：视图无接受 live 类型的 public 方法） |
| AC9 同一 candidate 的修改计划与 global/shared 顶层写集联合预检、联合成败 | **满足** | `domainPlanAndGlobalWriteSetCommitJointlyOrNotAtAll`（真跑，双向）；`GenerationGlobals` 联合边界（票 10 既有 fixture） |
| AC10 客户端可见性有真实 fixture：自动同步/显式 resync/relog/unsupported 由 capability 与错误/提示表达，无长期隐藏不一致 | **部分满足（降级：文档化边界 + 可机检结构 guard，无客户端 fixture）** | 能力记录：§8 capability 表（自动同步 = unsupported；显式 resync = partial；relog = supported）；可机检边界：`Ticket39ModificationOwnershipTest.modificationPathReferencesNoNetworkSymbols`（本机实测五节点真跑：modification 类的方法/字段签名不得出现网络类型，加同步路径必须先更新能力记录）；文档化：payload javadoc + `MIGRATION.md` §1/§4 明示 relog / chunk resync 需求；**缺口**：没有真实客户端 fixture（无 in-game 客户端复验），见 §12 G2 —— 该 AC 的「真实 fixture」要求未闭环 |
| AC11 26.x 与 1.21.1 的 item default components、block/state 属性、注册时机和同步差异只存在平台/版本 Adapter；五节点 capability/source trace 与 smoke 记录实际结果，不自动补 Fabric parity | **满足（五节点已有实测构建/测试）** | §6 五节点差异表；common 契约零 MC import（`guardLint` 通过）；本机实测五节点 `build`（含 test 编译与执行）：26.1.2 / 26.2.0 / 26.1.2-fabric / 26.2.0-fabric / 1.21.1（§9）；fabric 两节点复用同一 Adapter 编译，不自动补 Fabric 特有 parity；**缺口**：无 in-game smoke（gametest 被票 15 的 Point bootstrap 阻塞，见 §12 G2b） |
| AC12 最小可运行示例 + 迁移材料；示例只用已通过 gate 的能力 | **满足** | `MIGRATION.md`；`examples/item-modification.js`、`item-setter-property-parity.js`、`block-modification.js`、`declaration-removal-recovery.js`；`Ticket39ModificationExamplesTest` 按生产序列（初始收集点 + reload）执行示例同源代码 |
| AC13 Interface/Registry/Event/Adapter owner/declaration/golden/迁移表互相追溯；不新增公开 Modification Runtime、第二 registry path 或第二事件框架；测试从脚本事件贯穿到 Adapter 可观察结果 | **满足** | `ModificationDomainOwner implements CandidateDomainCollector, ModificationApplier, AutoCloseable`（`restoreAllThenReplayEntryPointsAreGone` 真跑断言归属）；`Ticket39ModificationScriptE2ETest`（脚本 → 事件 → 计划 → Adapter → `Items.DIAMOND.components()` 可观察结果）；`Ticket39ModificationOwnershipTest`（断言不读私有静态 Map）；catalog/golden + MIGRATION 追溯表 |
| AC14 旧 direct live mutation / restore-all 重放 / 无 owner static snapshot / 不受测 server-only 旁路仅在替代路径 parity、失败保留、迁移表、无消费者证据与维护者确认后删除；不保留长期双路径 | **不勾选（门禁未完成）** | 见 §11：代码层面旧路径已删除且无消费者；**缺维护者确认**，故 AC14 保持未勾选 |

## 8. capability / source-trace（AC10/AC11）

| 节点 | capability | 依据 |
|---|---|---|
| 26.1.2 / 26.2.0 / 26.1.2-fabric / 26.2.0-fabric | `modification.item` = supported（收集→commit→Adapter 应用；`fireResistant` 需 server 绑定，未绑定时整批 blocked） | §3.1/§3.4 + registry-gated E2E fixture |
| 同上 | `modification.block` = supported（26.x 面，六属性三副本 + per-state 光照函数恢复） | 同上 |
| 1.21.1 | `modification.item` = supported（四基础属性；组件发布走反射） | 1.21.1 成对 owner + `ItemModificationComponentsTest`（registry-gated） |
| 1.21.1 | `modification.block` = **unavailable**（无总线；脚本得明确「无此成员」错误，不是静默 no-op） | 节点 golden 无 `modification` 条目 + 成对 owner 的 `preflight` 只接受 `item` |
| 全节点 | 客户端自动同步 = **unsupported**；显式区块 resync = partial（需平台/网络侧动作，本票不改）；relog = supported | source trace：写入面只有服务端对象图，无 packet/sendTo；`BlockModificationEventJS`/`ItemModificationEventJS` javadoc 的 Visibility note + MIGRATION.md |

source trace（唯一 dispatch/post 来源，`grep -rn "ItemEvents.MODIFICATION\|BlockEvents.MODIFICATION" src versions/*/src common/src src/fabric`，排除 build 与测试）：

```
src/main/.../wrapper/event/server/ModificationDomainOwner.java:103  handle.dispatch(ItemEvents.MODIFICATION, new ItemModificationEventJS(plan));   // DOMAIN_PLAN 收集
src/main/.../wrapper/event/server/ModificationDomainOwner.java:104  handle.dispatch(BlockEvents.MODIFICATION, new BlockModificationEventJS(plan));  // DOMAIN_PLAN 收集
src/main/.../wrapper/event/server/ModificationDomainOwner.java:120  ItemEvents.MODIFICATION.post(new ItemModificationEventJS(plan));                // 初始 generation 收集
src/main/.../wrapper/event/server/ModificationDomainOwner.java:121  BlockEvents.MODIFICATION.post(new BlockModificationEventJS(plan));               // 初始 generation 收集
versions/1.21.1/.../ModificationDomainOwner.java:79   handle.dispatch(ItemEvents.MODIFICATION, ...)   // 无 block 半边
versions/1.21.1/.../ModificationDomainOwner.java:90   ItemEvents.MODIFICATION.post(...)
```

调用方 trace：`NekoJSMod.initializeScripts`（NeoForge）与 `NekoJSFabricMod`（Fabric）注册 owner；
`ServerEventListener.onServerAboutToStart` / `FabricServerEventBindings` 触发初始收集点；
`/nekojs reload server` 与 vanilla `/reload` 的 SERVER 事务 reload 走 `ReloadPhase.DOMAIN_PLAN`。

## 9. 验证证据（命令 + 结果）

| 命令 | 结果 | 关键数字 |
|---|---|---|
| `./gradlew :common:check :common-api-processor:test --console=plain` | BUILD SUCCESSFUL（exit 0） | `:common` 205 suites / 1504 tests / 0 failures / 4 skipped；`common-api-processor` 13 tests / 0 failures |
| `./gradlew guardLint --console=plain` | BUILD SUCCESSFUL（exit 0） | 守卫块 271 / 扫描 424 文件 / 超限豁免 0 / 警告 0（common 零 MC/loader import） |
| `./gradlew :26.1.2:build :1.21.1:build :26.1.2-fabric:build :26.2.0:build :26.2.0-fabric:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 五节点全绿（含 test 编译与执行）：26.1.2 306 tests / 26.2.0 306 / 26.1.2-fabric 209 / 26.2.0-fabric 209 / 1.21.1 213，全部 0 failures；票 39 真跑：26.x 各 19、两个 fabric 各 19、1.21.1 17；registry-gated skip：26.x 各 37、fabric 各 16、1.21.1 8 |

逐类结果与 skip 口径说明见 `evidence/verification-commands.md`（含聚合方式与 registry-gated
清单）。

额外的真机 smoke 尝试（**不计入 AC 证据**）：`./gradlew :26.1.2:runGameTestServer`
（`run/nekojs/server_scripts/` 内放一份 item+block 修改脚本）在 mod 构造期失败，
原因是票 15 的启动注册 Point `nekojs:registry_types` bootstrap 未完成
（`NekoRegistryPointsPlugin.requireResult` → `NekoPluginBootstrap.bootstrapOwned`），
与本票代码路径无关（细节见 §12 G2b）。该失败早于任何 modification 收集，因此既不能作为
本票的通过证据，也不构成本票的回归。

golden 完整性：本票未改任何 golden 输入（`block-events-api.txt` 的 `modification` 条目在本票之前即存在，
本次未改）；未执行 `-Dnekojs.golden.regenerate`，`ApiManifestGoldenTest` 在 `:common:check` 内以只读模式通过。

## 10. 与 10 / 14 / 15 / 06 / 07 的衔接

- **票 10（global/shared 联合提交）**：领域计划经 `CandidateStatePlan` 挂入 `GenerationGlobals`，
  复用 `preflightJoint/publishJoint` 的联合边界；本票不把 Item/Block 逻辑塞进 global owner，只提供
  领域集成 fixture（`Ticket39DomainCollectionTest`）。
- **票 14（事件面）**：modification 复用既有 `EventGroup`/`EventBusJS` 声明与 catalog 派生，没有第二 bus；
  payload 类与 scriptType 进入 catalog（本票新增 `Ticket39ModificationEventSurfaceTest` 固定）。
- **票 15（setter/property parity 与 BuilderSurface）**：`ModificationViewSurface` 复用同一 ProxyObject
  putMember → 同一 `Method` seam 手法；本票不共享启动注册/动态定义生命周期。
- **票 06（候选环境与 commit 点）**：`ReloadPhase.DOMAIN_PLAN` 插在 EVENT_PLAN 与 STATE_PLAN 之间，
  收集器只在 owner thread 的 reload 临界区内同步调用；计划在 commit 点由 Adapter 应用。
- **票 07（线程/close 抢占/watchdog）**：候选计划随候选环境丢弃，close 抢占在 COMMIT 前检查点丢弃候选
  （`ScriptManager` 既有语义）；本票的 owner 是 root 注册的 `AutoCloseable`，root close 恢复基线并清空。

## 11. AC14 删除门禁（明确不勾选）

已完成的删除前置：

1. **代码层旧路径已删除**：`ItemModificationEventJS`/`BlockModificationEventJS` 不再有 `static SNAPSHOTS`、
   `fire()`、`applyTo` 直改 live 对象；1.21.1 成对文件同步；fabric `ItemEvents` 注释同步。结构性 guard
   `Ticket39ModificationOwnershipTest`（本机实测五节点真跑）固定这些事实。
2. **替代路径 parity**：parity（`ModificationSetterPropertyParityTest`，真跑）、失败保留
   （`Ticket39DomainCollectionTest` + E2E，真跑/registry-gated）、迁移表（`MIGRATION.md`）。
3. **无消费者证据**：全仓 `grep` 无 `ModificationEventJS.fire`/`SNAPSHOTS`（item/block modification）调用点；
   唯一 `fire()` 命中为 javadoc 文本（已在本票清理）。
4. **不受测 server-only 旁路**：不存在（block/item 写入面均在 `ModificationDomainOwner`，由 fixture 覆盖）。

**缺**：维护者 sign-off 记录（工单 §Human input note 明确要求）。因此 AC14 不勾选；本节列出删除条件与
已备证据，等维护者确认后在 ticket 与本节补记。旧公开路径**不再保留长期双路径**这一条同样待 sign-off 后
才能在 ticket 上标记。

## 12. not-verified / 风险与偏离（给 reviewer）

| # | 项 | 现状 | 建议 |
|---|---|---|---|
| G1 | registry-gated fixture 在无 FML loader 的测试 JVM 报 **skipped**（26.1.2：`Ticket39ModificationScriptE2ETest` 6、`Ticket39BlockModificationScriptE2ETest` 4、`Ticket39ModificationExamplesTest` 4、`BlockModificationEventJSTest` 6、`ItemModificationComponentsTest` 15、`ModificationLegacyCharacterizationTest` 2） | 与既有基线同口径（票 14 证据里 `:26.1.2:check` 也是 34 skipped）；真跑面由 registry-free fixture 承担：`:26.1.2` parity 7 + ownership 4 + surface 2，`:26.1.2-fabric` 13，`:1.21.1` 11，`common` `Ticket39DomainCollectionTest` 7（真实 root + 真实脚本管线 + 真实 Graal） | 主会话在 ModDev/开发环境复核 registry-gated 那批；in-game smoke 用 `minecraft-mod-mcp`（注意 G2b 的既有阻塞） |
| G2 | 客户端可见性（relog/chunk resync）**未在本 worktree 做客户端复验** | 只有服务端写入面 source trace + 文档边界；无隐藏漂移 | 主会话用 MCP 起客户端复核一次（放置/观察红石灯 lightLevel 变化需 relog 的现象），并在 ticket 34/36 的 P4 证据里消费 |
| G2b | 真机 smoke 尝试：`./gradlew :26.1.2:runGameTestServer`（在 `run/nekojs/server_scripts/` 放一份 item+block 修改脚本）**失败于 mod 构造期**：`IllegalStateException: extension point 'nekojs:registry_types' has not finished yet (bootstrap incomplete)` @ `NekoRegistryPointsPlugin.requireResult/registryTypes/registerTypeDocs` ← `NekoPluginBootstrap.bootstrapOwned` ← `NekoRuntimeAssembly.assemble` ← `NekoJSMod.initializeScripts` | **与本票无关的既有问题**：堆栈全在票 15 的启动注册 Point 面（`NekoRegistryPointsPlugin` 由 `5ac74024` 引入，本票未触碰），失败点在 mod 构造，早于任何 modification 收集；`run/` 目录为生成物（已清理脚本，不影响后续运行） | 归 ticket 15/34 owner 处置（gametest 环境下的 Point bootstrap 未完成）；修好后可用同一脚本路径复核 modification 启动收集点，本票不把该次失败计入 AC 证据 |
| G3 | 五节点全量（check/artifact/CI 子集） | 本 worktree 已跑五节点 `build`（含 test 编译与执行）：26.1.2 / 26.2.0 / 26.1.2-fabric / 26.2.0-fabric / 1.21.1（§9）；CI 子集与 artifact gate 未在本 worktree 跑 | 合并后由主会话跑五节点全量 check/artifact + CI 子集 |
| G6 | 并行 worktree 环境碰撞（非本票缺陷，但影响验证可复现性） | 本机同时有其它 worktree（主树 / t16）在跑 `:common:test`，而 `TestPlatformInit.ensureInitialized()` 使用**固定名** tmp gameDir `nekojs-test-gamedir`（`TestGameDirs.unique` 才是 PID 隔离约定）→ 日志文件锁竞争（`FileSystemException: ...server.log -> ...old/server.log: 另一个程序正在使用此文件`）会让 `ScriptLocator.discover` 在个别用例里读不到脚本，表现为偶发 `expected: <7> but was: <1>` | 与其它 worktree 错峰重跑即全绿（本轮实测两次重跑后 `:common:check` 全绿）；建议后续票把 common 测试的 Platform 初始化也改成 `TestGameDirs.unique(...)`（属测试基建，不在本票范围） |
| G4 | 初始收集点的「等价跳过」只在 owner 内可观察 | `SKIPPED_IDENTICAL` 仅在 `applyInitialPlan` 路径产生；reload 路径每次都是新 generation 的全量应用 | 如需 reload 幂等跳过，另开决策（本票不引入新政策） |
| G5 | `Object` → `Consumer` 的签名回退 | 接管时未提交改动把 `modify(String, Object)` 当作 API（Java 侧无法传 lambda、丢失改造前契约）；本报告按「恢复函数式接口 + 视图自实现 ProxyObject + 成员目录收录访问器原名」修复，脚本面（`event.modify(id, fn)`）两种写法都经真实 fixture 验证 | 如维护者认为 JS 侧需要 `Value` 直传形态，再单独讨论（当前无消费者） |

## 13. 术语与文件索引

- common 契约：`common/src/main/java/com/tkisor/nekojs/core/modification/{CandidateDomainCollector,ModificationApplier,ModificationCandidatePlan,ModificationDeclaration}.java`
- 版本树 Adapter：`src/main/java/com/tkisor/nekojs/wrapper/event/server/{ModificationDomainOwner,ItemModificationEventJS,BlockModificationEventJS,ItemModificationJS,BlockModificationJS,ModificationViewSurface,ItemModificationComponents}.java`
- 1.21.1 成对：`versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/event/server/*`
- 平台接线：`NekoJSMod`、`ServerEventListener`、`NekoJSCommands`（`/nekojs reload server`）、`src/fabric/java/com/tkisor/nekojs/{NekoJSFabricMod,event/FabricServerEventBindings}.java`
- fixture：`common/src/test/.../Ticket39DomainCollectionTest.java`、`src/test/.../Ticket39{ModificationScriptE2ETest,BlockModificationScriptE2ETest,ModificationOwnershipTest,ModificationEventSurfaceTest,ModificationExamplesTest,ModificationSetterPropertyParityTest,ModificationLegacyCharacterizationTest,BlockModificationEventJSTest,ItemModificationComponentsTest}.java`

## 14. 双轴审查整改记录（reviewer 判定：需修复后合并 → 已整改）

第一轮双轴审查对 `ticket-39-item-block-mod` 判定「需修复后合并」，必修项 F1–F5 与本轮一并处理的
优化项 F6/F7/F8/F9/F10/F11/F13 逐条处置如下（数字为整改后实测）：

| # | 审查意见 | 处置 | 证据（整改后实测） |
|---|---|---|---|
| F1 | AC8 生产到达形态（裸视图经 `Consumer`）零真跑覆盖，REPORT 却声称两形态一致 | `ModificationSetterPropertyParityTest` 增 `productionDelivery*` 两用例：JS 函数在<b>沙盒 `NekoSharedHostAccess`</b> 下 `as(Consumer.class)` 后 `accept(裸视图)`（与 `ItemModificationEventJS#modify` 同形），断言与 surface 形态同声明/同 fingerprint/同错误 | parity 由 7 → **10 用例，0 skip**（五节点实测）；REPORT §3.5 增两形态表 |
| F2 | 「五节点真跑」过度声称（26.2.0 两节点 test 树未编译） | 实跑 `:26.2.0:build :26.2.0-fabric:build`（含 test 编译与执行）；三处 javadoc 与 REPORT AC11/§11 措辞改为与实测一致 | `:26.2.0` 306 tests / 0 fail（票 39 真跑 19）；`:26.2.0-fabric` 全绿（§9） |
| F3 | AC6 核心断言零真跑 + `ModificationViewSurface` 悬空引用 | 恢复并升级 registry-free Graal 探针（普通宿主对象 property 写静默丢弃 = 旧事实；视图自实现 ProxyObject 后同写法到达 setter）；新增计划层 AC6 用例（声明序保留 + 按声明序应用 + 后声明整体替换）；悬空引用改指新用例 | `ModificationLegacyCharacterizationTest` 由 2 → **4 用例（2 registry-free 真跑 + 2 registry-gated）**；AC6 行改为两层证据 |
| F4 | 「收集失败记录在计划上」在生产不成立 | `ModificationCandidatePlan#fail` javadoc 降级为「收集器可选 API，Item/Block 生产路径不适用」；REPORT §3.3 STATE_PLAN 行注明仅合成/第三方收集器可达 | 全仓调用点核对：生产零调用者（唯一调用在 common 合成收集器 fixture） |
| F5 | 旧公开符号删除缺 breaking 清单与 sign-off 项 | `MIGRATION.md` 新增 §2.1 逐项 breaking 表（`fire(MinecraftServer)`/`fire()`/两个构造器/两个 `SNAPSHOTS`/`applyTo`）；票面新增「维护者 sign-off 项」章节（AC14 仍不勾选） | 与 `230587cc` 逐文件签名对照 |
| F6 | `memberNames()` 重复 | 改 `LinkedHashSet` 去重；新增 `getterNames()` | 表面代码 + parity 用例回归 |
| F7 | setter-only 成员读面返回函数对象 | 读面显式抛 `write-only; readable: [...]`；访问器原名形态（`item.setX(v)`）仍可调用 | `writeOnlyMemberReadIsRejectedInsteadOfReturningTheSetterObject`（真跑） |
| F8 | 带 dispatch key 的 pending 静默全量派发 | `Handle#dispatch` 契约写明「仅支持非 dispatch 总线」，`ScriptManager` 实现显式拒绝（`UnsupportedOperationException`） | `Ticket39DomainCollectionTest.dispatchKeyedBusIsRejectedByDomainCollectionInsteadOfSilentlyBroadcasting`（真跑，DOMAIN_PLAN 归因） |
| F9 | 数字勘误 | §9 与 evidence 全部改由 `build/test-results` XML 聚合实测值填写（26.1.2 票 39 registry-gated skip = **37**，含本轮新增 fixture 后的实测；guardLint = **守卫块 271 / 扫描 424 文件 / 警告 0**） | §9 表 + `evidence/verification-commands.md` |
| F10 | AC10 判定过强 | AC10 行降级为「部分满足（文档化边界 + 可机检结构 guard，无客户端 fixture）」，并加 `modificationPathReferencesNoNetworkSymbols` 结构 guard（本机实测五节点真跑） | `Ticket39ModificationOwnershipTest` 由 4 → **5 用例** |
| F11 | apply 失败后指纹未复位 | 两个 owner（26.x + 1.21.1）的 `RECOVERY_FAILED` 分支复位 `lastAppliedFingerprint`，避免下次启动误报 `SKIPPED_IDENTICAL` | 代码 + 注释（AC7 归因） |
| F13 | `ItemEvents` javadoc 陈旧（快照恢复模型） | 改为票 39 收集语义（与 block 侧/`MIGRATION.md` 对齐） | `ItemEvents.java`（neoforge 共享面，1.21.1 同源） |

**AC 判定措辞修订**：AC6 改为两层证据（计划层真跑 + 端到端 registry-gated）；AC8 补生产投递形态
真跑；AC10 **降级**为部分满足（无客户端 fixture）；AC11 改为「五节点已有实测构建/测试，无 in-game
smoke」。AC1–AC5、AC7、AC9、AC12、AC13 判定不变；AC14 仍不勾选（F5 已补 breaking 清单与票面
sign-off 章节）。
