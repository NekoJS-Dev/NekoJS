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
  fixture 五节点真跑。
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
| STATE_PLAN（联合预检） | 值域越界（`maxStackSize=500`）、组件不变量（可堆叠 + 可损坏）、未知目标在 Adapter 侧解析失败、收集失败标记 | 整批不提交，global/shared 写集同样不发布 | `phase=STATE_PLAN`、`domain=state-plan-preflight:item-block-modification` |
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
实现转发（`ItemModificationJS`/`BlockModificationJS implements ProxyObject`，因此脚本回调参数无论以
「裸视图」还是「surface 包装」到达，行为一致）：

- `item.maxStackSize = 16` → `putMember("maxStackSize")` → `setMaxStackSize(int)`；
- `item.setMaxStackSize(16)` → `getMember("setMaxStackSize")` → 同一 `Method` 的 `ProxyExecutable` 包装；
- 未知成员/只读成员/类型不匹配在写入期抛出带成员名与可写成员目录的错误（两种写法的错误同源）；
- 成员目录按视图类反射派生并缓存；`ProxyObject` 自身的接口方法（`getMember`/`putMember`/…）与包私有引擎
  接缝（`applyTo`、`normalizedProperties`）**不进**脚本面。

`ModificationCandidatePlan.fingerprint()` 对规范化声明求确定性指纹（声明顺序 + 单声明内属性按名排序 +
`5` 与 `5.0` 同值的数值形态归一），两种写法产生同一 fingerprint（真实 GraalJS fixture 固定）。

## 4. 旧行为 characterization 摘要（commit `bcbbad6f`，本次复核）

| 旧事实（重构前实测） | 处置 | 现状 fixture |
|---|---|---|
| 同一次重放内同目标多条声明：后一条**整体替换**前一条（每次从基线叠加，不按属性合并） | **保留**（不引入同 key 拒绝、不新增 last-write-wins 政策） | `ModificationLegacyCharacterizationTest.sameTargetMultipleDeclarationsLastOneEntirelyReplacesEarlierOnes`（确定性重放断言） |
| 「声明消失」不对称：block 每次重放前 restore-all；item 只在再次 modify 时恢复 → item 声明移除后保持 stale | **有意改变**（AC7）：item/block 统一「恢复基线 → 应用完整新计划」，记入 MIGRATION.md | `removedItemDeclarationNowRestoresBaselineInsteadOfStayingStale`（断言翻转 + 迁移表条目） |
| GraalJS 宿主视图的 property 写被静默丢弃（`assigned,read=undefined`，不落 setter） | **修复**（AC8）：`ModificationViewSurface` putMember seam | `ModificationSetterPropertyParityTest`（7 用例，真实 GraalJS，真跑） |
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
| AC6 先 characterization 多脚本/多声明/注册顺序行为；重构后按同一可观察顺序确定性重放，不引入同 key 拒绝或新合并政策 | **满足** | `bcbbad6f` characterization；`ModificationLegacyCharacterizationTest.sameTargetMultipleDeclarationsLastOneEntirelyReplacesEarlierOnes`（registry-gated）；多声明顺序语义 = 计划内声明序 = 重放序（`ModificationDeclaration` javadoc + Adapter 顺序应用）；collector dispatch 顺序 = priority 降序 + 注册序（`ScriptManager.CandidateCollectionHandleImpl`，票 14 同序） |
| AC7 声明移除 → 成功 reload 先恢复基线再应用完整新计划；不可证明可恢复的字段阻止该批；诊断区分 active/blocked/recovery-failed/restored，不把静默 stale 当成功 | **满足** | `removedDeclarationsRestoreBaselinesViaAnEmptyPlanAtCommit`（真跑）；`ModificationLegacyCharacterizationTest.removedItemDeclarationNowRestoresBaselineInsteadOfStayingStale`；`Ticket39ModificationScriptE2ETest.removedDeclarationRestoresNekoJsBaselineInsteadOfStayingStale`；`Outcome` 枚举 6 态 + 两个 E2E 断言 outcome |
| AC8 setter 与 property 赋值同一 setter/校验/规范化/fingerprint/计划路径；无同名 public field 旁路；GraalJS runtime contract test 固定两种写法等价 | **满足** | `ModificationSetterPropertyParityTest`（7 用例，真跑，真实 GraalJS Context：规范化声明、fingerprint、错误同源、未知成员、类型不匹配、food 对象字面量/null 移除）；`viewsExposeNoLiveMutationSeam`（真跑：视图无接受 live 类型的 public 方法） |
| AC9 同一 candidate 的修改计划与 global/shared 顶层写集联合预检、联合成败 | **满足** | `domainPlanAndGlobalWriteSetCommitJointlyOrNotAtAll`（真跑，双向）；`GenerationGlobals` 联合边界（票 10 既有 fixture） |
| AC10 客户端可见性有真实 fixture：自动同步/显式 resync/relog/unsupported 由 capability 与错误/提示表达，无长期隐藏不一致 | **满足（边界显式，不做隐藏漂移）** | §8 capability/source-trace 表：写入面只碰服务端对象图（`Properties`/`Block`/`BlockState`/默认组件），无网络/packet 代码路径（source trace 无 `Packet`/`sendTo`/`NetworkEvents` 引用）；relog / chunk resync 需求由 payload javadoc + MIGRATION.md 显式记录；**未在**本 worktree 做客户端 in-game 复验（见 §12 G2） |
| AC11 26.x 与 1.21.1 的差异只在 Adapter；五节点 capability/source trace 与 smoke 记录实际结果，不自动补 Fabric parity | **满足** | §6 五节点差异表；common 契约零 MC import（`guardLint` 通过）；各节点 Adapter 差异只出现在 `ModificationDomainOwner`/payload 成对文件与平台接线；fabric smoke 记录见 §9（本 worktree 只跑构建/测试，客户端复验见 §12 G2） |
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
| `./gradlew :common:check :common-api-processor:test --console=plain` | BUILD SUCCESSFUL（exit 0） | `:common` 205 suites / 1503 tests / 0 failures / 4 skipped；`common-api-processor` 13 tests / 0 failures；另以 `:common:test --rerun-tasks` 强制全量重跑复核（exit 0，同一计数） |
| `./gradlew guardLint --console=plain` | BUILD SUCCESSFUL（exit 0） | 守卫块 269 / 扫描 423 文件 / 超限豁免 0 / 警告 0（common 零 MC/loader import） |
| `./gradlew :26.1.2:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 61 suites / 300 tests / 0 failures / 52 skipped；票 39 真跑 13（parity 7 / ownership 4 / surface 2），registry-gated 39 skip |
| `./gradlew :26.1.2-fabric:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 39 suites / 203 tests / 0 failures / 24 skipped（fabric 共享同一 Adapter 与 fixture） |
| `./gradlew :1.21.1:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 46 suites / 207 tests / 0 failures / 8 skipped；票 39 真跑 11（parity 5 / ownership 4 / surface 2） |

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
   `Ticket39ModificationOwnershipTest`（五节点真跑）固定这些事实。
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
| G3 | 五节点全量（26.2.0 / 两个 fabric 的 check、CI 子集） | 本 worktree 只跑了任务清单要求的 5 条命令（本机 26.1.2-fabric 已覆盖 fabric 共享 Adapter 编译） | 合并后由主会话跑五节点全量 |
| G4 | 初始收集点的「等价跳过」只在 owner 内可观察 | `SKIPPED_IDENTICAL` 仅在 `applyInitialPlan` 路径产生；reload 路径每次都是新 generation 的全量应用 | 如需 reload 幂等跳过，另开决策（本票不引入新政策） |
| G5 | `Object` → `Consumer` 的签名回退 | 接管时未提交改动把 `modify(String, Object)` 当作 API（Java 侧无法传 lambda、丢失改造前契约）；本报告按「恢复函数式接口 + 视图自实现 ProxyObject + 成员目录收录访问器原名」修复，脚本面（`event.modify(id, fn)`）两种写法都经真实 fixture 验证 | 如维护者认为 JS 侧需要 `Value` 直传形态，再单独讨论（当前无消费者） |

## 13. 术语与文件索引

- common 契约：`common/src/main/java/com/tkisor/nekojs/core/modification/{CandidateDomainCollector,ModificationApplier,ModificationCandidatePlan,ModificationDeclaration}.java`
- 版本树 Adapter：`src/main/java/com/tkisor/nekojs/wrapper/event/server/{ModificationDomainOwner,ItemModificationEventJS,BlockModificationEventJS,ItemModificationJS,BlockModificationJS,ModificationViewSurface,ItemModificationComponents}.java`
- 1.21.1 成对：`versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/event/server/*`
- 平台接线：`NekoJSMod`、`ServerEventListener`、`NekoJSCommands`（`/nekojs reload server`）、`src/fabric/java/com/tkisor/nekojs/{NekoJSFabricMod,event/FabricServerEventBindings}.java`
- fixture：`common/src/test/.../Ticket39DomainCollectionTest.java`、`src/test/.../Ticket39{ModificationScriptE2ETest,BlockModificationScriptE2ETest,ModificationOwnershipTest,ModificationEventSurfaceTest,ModificationExamplesTest,ModificationSetterPropertyParityTest,ModificationLegacyCharacterizationTest,BlockModificationEventJSTest,ItemModificationComponentsTest}.java`
