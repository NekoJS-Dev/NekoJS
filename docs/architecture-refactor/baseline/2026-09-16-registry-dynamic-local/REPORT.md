# Ticket 16 实施报告：Dynamic Registry inert 本地计划与 typed Builder

> 工单：`docs/architecture-refactor/implementation-tickets/16-registry-dynamic-local.md`。
> 权威 spec：`08-ported-features-event-surface.md`（事件化边界、typed Builder、fingerprint、
> 冲突与 stale、prepare/ack 边界）、`09-reload-candidate-state-and-thread-contract.md`
> （candidate 只收集、commit 点发布、失败保留旧 active）、`04-public-contract-and-plugin-model.md`
> （契约派生与 legacy 观察）、`07-validation-and-migration.md`（golden/能力口径/五节点 gate）。
> 认领基线：`230587cc`。本报告覆盖的未提交工作区间见 §0。
> **结论先行（票面 12 条 AC 口径）：11 条满足；AC11（旧路径删除）不勾选**——维护者 sign-off 是
> 发布门禁，本票只交付替代 parity、消费者清单与迁移材料（§6）。本票只证明本地 inert 计划
> 行为：不激活多人同步、不宣称动态热更新（公开激活归票 21）。

## 0. Commit 清单

见工单最终回复的 `git log --oneline 230587cc..HEAD`；分组语义如下（conventional commits）：

| 组 | 内容 | AC |
|---|---|---|
| `feat(dynamic-registry)` | common 计划面（`core/dynamic/plan/`）、facade（`core/dynamic/facade/`）、reload 收集 seam（`CandidateDomainCollector` + `ScriptManager`/`EventBusJS` 窄扩展） | AC1/3/5/6/7/8 |
| `feat(dynamic-registry)` | 平台装配（`DynamicRegistryFacade`/`DynamicRegistryPlugin` 事件与声明贡献、`ServerEventListener` 初次候选触发 + `>=26` 守卫） | AC1/2/9 |
| `fix(dynamic-registry)` | 缺口补强（候选期收集可观察记录、收集期 kill 上报对称、`>=26` 守卫） | AC1/7 |
| `test(dynamic-registry)` | 12 个套件中的 10 个（plan 语义/惰性/parity、facade/reload/惰性/示例、声明 golden） | AC4/5/6/7/8/9/12 |
| `test(data-sync)` | 顺带修复：`DataSyncGenerationBoundaryTest` 适配 `ScriptEnvironmentFactory` 4 参构造器（认领基线漏适配，编译期必须） | — |
| `docs(baseline)` | 本报告 + `MIGRATION.md` + 示例注释 + REGENERATE 登记 + 票面 AC 勾选 | 材料 |

## 1. 现状测绘（实施前形态）

- **旧路径（保留，未改动）**：`DynamicRegistryJS`（`ServerEvents.started` 内 `DynamicRegistry.item/soundEvent/mobEffect(id, builder)`）→ `DynamicRegistries`（static `DynamicRegistrySet`）→ `RegistrySurgery`（`withUnfrozenRegistry`/`withUnfrozenAndHolders`/`clearHolderTags`）+ `RegistryDataCollectorMixin`（RETURN 处重放 item 组件）——**在脚本线程即时执行 registry mutation**，门控为「运行中的服务器 + `engine.toml [dynamicRegistry] enabled=true`（默认 false）」，`claim/stale` 由 `DynamicRegistrationBookkeeping`（static，`beginServerReload`）记账。整包住在 `neoforge && >=26` 守卫内（fabric/1.21.1 无此功能）。
- **本票新增面（与旧路径完全独立）**：`DynamicRegistryEvents`（独立事件组，唯一 SERVER 总线 `dynamicRegistry`）→ `DynamicRegistryEventJS`（payload，成员目录冻结为三个类型直达入口）→ `DynamicCandidateRegistryPlan`（generation-scoped inert 计划，`CandidateStatePlan` 联合边界实现）→ `DynamicRegistryPlanStore`（exposed 账 + claim/stale/mode 账）→ `DynamicAdapterRequest`（inert 描述，动作封闭为 `register`）。计划包住 common，零 MC/loader import。
- **收集入口两条**：初次（server registry ready，active 总线 post）+ 候选期（`ScriptManager` 的 `CandidateDomainCollector` seam：EVENT_PLAN 后、STATE_PLAN 前执行候选挂起监听器，计划挂入 `publishJoint` 联合边界，commit 点联合发布）。

## 2. 逐 AC 判定与证据指针

| AC | 判定 | 证据 |
|---|---|---|
| AC1 server registry ready 触发初次候选；reload 在候选阶段重新收集并完成 preflight，成功 commit 才发布；失败/取消不另起写入、不在脚本线程即时 mutation | **满足** | 初次触发：`ServerEventListener.onServerAboutToStart` → `DynamicRegistryFacade.fireInitialCollection()`（`//? if >=26` 守卫，与旧动态注册整包同边界）；候选期：`ScriptManager` STATE_PLAN 前调用已注册收集器（`CandidateDomainCollector`）。证据：`DynamicRegistryReloadPipelineTest.serverRegistryReadyFiresInitialCandidateAndReloadRecollectsInCandidatePhase`（reload 后 store 批次推进 + stale 标记只能来自候选期收集）、`DynamicRegistryCandidateInertnessTest.successfulReloadCollectsInTheCandidatePhaseAndPublishesOnlyAtCommit`（`lastCandidateCollection` 可观察、commit 才发布）、失败路径三例（同 key 冲突 / 收集错误 / 毒化）均零写入且旧 active 继续服务；`DynamicRegistryEventFacadeTest`（初次路径）。「不在脚本线程即时 mutation」= 计划面无执行通道（`DynamicPlanInertnessTest`）+ common 零 MC/loader（`checkCommonIsolation`/guardLint L1 L2） |
| AC2 脚本作者面类型直达入口冻结为 `event.item/soundEvent/mobEffect` 的 callback Builder；命名若偏离必须记录并进 contract/golden、declaration、迁移表 | **满足（命名决策已记录）** | 成员目录冻结：`DynamicRegistryEventJS`（三个入口，`getMemberKeys` 恰好 3）+ `DynamicRegistryEventFacadeTest.payloadMemberDirectoryIsClosedToTheThreeVerifiedTypes`、`DynamicRegistryDeclarationParityTest.builderSurfaceEntriesDeriveFromTheSameContractAsRuntimeMembers`（sugarName ∈ 三个入口名）。**命名决策**：spec 08 工作名 `ServerEvents.dynamicRegistry` 未采用，实施采用独立组 `DynamicRegistryEvents`（理由与迁移口径见 §5 + `MIGRATION.md` §1）；该决策已进 contract/golden（`nekojs/dynamic/dynamic-registry-events.expected.d.ts`，`DynamicRegistryEventsDeclarationGoldenTest` 显式断言组名/成员名且断言不出现工作名）、declaration（同一 golden + parity 测试）、迁移表（§5 表逐项） |
| AC3 回调只接收 typed callback Builder；无通用 type catalog；未知 type 字符串不转注册能力；候选范围只有 Item/SoundEvent/MobEffect；未验证类型 not verified 阻塞开放 | **满足** | payload 成员目录无通用成员：`typeNamesAndRegistryKeysAreNotInterchangeable`（`minecraft:item`/`block`/`custom`/`register`/`sound_event` 等既不是入口名、也不在成员目录、`typeof` 为 undefined；`byApiName` 恒 empty）、`unknownTypeNamesAreNotRegistrationCapability`（脚本级吞掉未知入口的调用不产生定义；不吞则毒化整批不发布）。范围封闭：`DynamicPlanInertnessTest.frozenCandidateSurfaceIsExactlyTheThreeVerifiedTypes`（枚举 3 值、registry key 集合恒等、apiNameDirectory 逐字冻结）；`idNormalizationFollowsVanillaIdentifierSemantics`（id 规范化与旧入口同语义）。未验证类型口径见 §3 能力表（记 not verified，不改写为 unavailable） |
| AC4 setter 与 JavaBean-style property 写入调用同一 setter/校验/规范化/fingerprint 路径；由运行时 contract test 固定，GraalMC 临时实验只作 characterization | **满足** | `DynamicBuilderSurfaceParityTest`（真实 GraalJS，6 用例）：同 id 两种写法 ⇒ 规范化读数与 fingerprint 完全相同、错误面同源（越界/未知取值/`global` mode）、property 读回与 `null ≡ 抑制`；示例 harness 内跨 reload 换写法 parity（`DynamicRegistryInertPlanExampleTest` 场景 2b：ruby 上轮 property、本轮显式 setter ⇒ fingerprint 不变、不冲突）。结构保证：`DynamicBuilderContract.Member.setter()` 是唯一 `Method`，property 写（`putMember`）与显式调用转发同一条路径；Graal 宿主对象 property 写不落 setter 的 characterization 结论写在 `DynamicBuilderSurface` javadoc（不作为集成通过证据） |
| AC5 全规范化 fingerprint 覆盖 Builder 输入与约定连带声明；不依赖身份/部分字段；重复 reload 同定义同 fingerprint，字段或连带声明变化可识别 | **满足** | `DynamicDefinition.fingerprint` = sha256(type|id|mode|全量可写属性读数，字典序)；`DynamicCandidatePlanSemanticsTest.sameDefinitionAcrossReloadsGetsSameFingerprintAndReclaims`（跨轮同声明同指纹）、`fingerprintCoversEveryBuilderInputTypeAndId`（三个类型字段逐个可识别 + mode + 类型 + id；读数=全量可写属性、规范化默认值逐字断言）、`DynamicBuilderSurfaceParityTest.fingerprintIsStableAcrossInstancesAndReloadsAndChangesWithAnyField`（跨实例稳定、字段/连带声明/id 变化可识别）。「不依赖对象身份」由全量读数（不含 hash）与跨实例断言共同保证 |
| AC6 同 key 定义变化 → 第一版整批冲突失败、旧 active 继续服务；remove/replace/modify 不出现在公开 Interface/golden/declaration/迁移承诺 | **满足** | 计划层：`sameKeyDefinitionChangeFailsTheWholeBatchAndOldActiveKeepsServing`（domain=dynamic-registry-conflict、整批失败、旧指纹保留、未过 preflight 禁止 publish）；reload 层：`DynamicRegistryReloadPipelineTest.sameKeyChangeFailsTheWholeReloadAndOldActiveKeepsServing`（STATE_PLAN 失败、generation 不推进、修正后可再成功）；stale 项 replace 同样冲突：`undeclaredExposedEntriesBecomeStaleButAreNotPhysicallyDeleted`。动作集合封闭：`DynamicAdapterRequest` 构造期校验 action ∈ {register}（`adapterRequestExposesNoOperationOtherThanRegister`）；迁移表 §3.4 明示 remove/replace/modify 不在第一版，golden/declaration 中无对应成员 |
| AC7 preflight/指纹冲突/Adapter 请求都是 generation-scoped inert candidate plan；候选期不改 live registry、不挂生产 callback、不提前发布绑定；失败清理临时计划与资源 | **满足（结构 + 行为双证）** | 结构：`DynamicPlanInertnessTest`（计划/store/Adapter 请求的全部字段、方法与构造器无 Supplier/Consumer/Function/Runnable/ProxyExecutable/Binding 通道，无 MC/loader 类型；Adapter 请求只有数据构造器）、common 零 MC/loader（`checkCommonIsolation` + guardLint）。行为：`poisonedCandidateNeverAttachesItsListenerAndNeverWritesTheLedger`（候选失败后 `DYNAMIC_REGISTRY.hasListeners()` 仍为 false＝候选 listener 从未上生产总线；store 空、generation 不推进、修正后可用）、`collectionErrorFailsTheCandidateWithoutAnyWrite`、`DynamicCandidatePlanSemanticsTest` 的未发布批次断言；「失败清理」＝计划对象丢弃后不可达（`CandidateCollectionRecord` 只留诊断摘要，不持有计划引用）、无临时平台资源（计划面不持有任何平台句柄） |
| AC8 脚本不再声明的已暴露项标记 stale/retired，普通 reload 不物理删除；claim、stale、mode 与后续显式清理语义可经 Registry Runtime/Adapter Interface 观察并测试 | **满足** | `DynamicRegistryCandidateInertnessTest.staleMarkingIsNotAPhysicalDeletionAndKeepsTheConflictLedger`（stale 查询、exposed 指纹保留、claim.stale/owner 可读、trackedClaims 含 stale）、`DynamicRegistryReloadPipelineTest`（reload 后 boom stale、ruby 重 claim）、`undeclaredExposedEntriesBecomeStaleButAreNotPhysicallyDeleted`（stale 后重声明恢复 claim）、`DynamicRegistrationBookkeepingTest`（12 用例，claim/stale/mode 转换的 prior art 覆盖）。**语义表见 §4**；显式清理语义明确不在本票（state-plan store 只保留账） |
| AC9 调用者 Interface、Registry Runtime/Adapter 契约、TS/Python declaration、contract/golden、本地行为测试同一条证据链；测试优先穿过事件 facade 与 Adapter Interface，不断言私有 Manager 字段 | **满足** | 单一契约输入：`DynamicBuilderContract` 反射（builder 类）→ runtime member（`DynamicBuilderSurface`）/fingerprint 读数/结构化条目（`DynamicBuilderSurfaces.derive`）→ 生产渲染器（`RegistryBuilderTsRenderer`/`RegistryBuilderPyRenderer`/`EventDeclarationGenerator`/`PythonEventRenderer`）。证据：`DynamicRegistryDeclarationParityTest`（事件条目一次、TS/Python 同源、声明成员与 runtime 契约同集合）、`DynamicRegistryEventsDeclarationGoldenTest` + 两份 golden（§8）、`DynamicRegistryInertPlanExampleTest`（示例逐行一致地跑真实管线）。测试断言面全部是 payload/`DynamicRegistryEvents`/`DynamicRegistryPlanStore` 公开查询与 reload 失败报告，无私有字段断言（`lastOutcome`/`lastCandidateCollection` 是不携带内部状态的公开诊断记录） |
| AC10 只证明本地 inert 计划行为，不激活多人同步、不宣称热更新或节点能力完成 | **满足** | 代码面零网络/同步通道（`DynamicPlanInertnessTest` 通道扫描 + 计划包无 network/sync 引用）；发布计划只更新账本并产出 inert 请求；日志/文档口径统一（`collectInitial` 日志明示 "inert local plan only — activation is gated by the transaction/sync gate"）；`MIGRATION.md` §4 明示不承诺热更新/多人同步。能力表见 §3（未验证类型 not verified） |
| AC11 旧 `DynamicRegistry` 静态全局入口与直接 registry surgery 只能在替代 parity、候选计划、Adapter 请求、声明与迁移路径全部覆盖且无消费者后删除；删除需维护者确认，不保留双写 shim | **不勾选（维护者删除门禁）** | 本票交付删除前提材料：旧路径消费者清单（§6）、替代路径 parity（本报告 §2 全表 + `MIGRATION.md` §3 迁移要点）、声明/迁移材料（§8 + `MIGRATION.md`）。旧面**零删除、零双写**（`DynamicRegistryJS`/`DynamicRegistries`/`RegistrySurgery`/`RegistryDataCollectorMixin` 本票 diff 为零）；`DynamicRegistryFacade` javadoc 明示两条路径各自独立记账、删除需 sign-off |
| AC12 隔离 harness fixture 可运行，演示类型直达入口、setter/property parity、同 key 冲突与 stale 查询；明确标注仅本地计划、尚未公开激活，不作生产脚本指南 | **满足** | `FacadeTestHarness`（真实 `ScriptManager` 事务式 reload + 真实 GraalJS + 生产同款 `EventGroupJS` 绑定 + 收集器挂载）+ `DynamicRegistryInertPlanExampleTest`（示例 4 场景：类型直达入口/parity、幂等 reload、同 key 冲突、stale 查询）+ `examples/dynamic-registry-inert-plan.js` 头注与 `DynamicRegistryInertPlanExampleTest` javadoc 双处标注「仅本地 inert 计划、尚未公开激活、非生产指南」；未开放类型不以可用能力出现（示例末段显式说明 + §3 能力表） |

**范围外遵守**：未激活同步/热更新；未扩大候选类型；未动旧动态注册语义与启动期 drain；未新增 Gradle project/API artifact/第二 registry path；未改 golden 生成机制（只新增两份 golden 并登记）。

## 3. 能力表（capability / not-verified 分离口径）

`supported`/`partial`/`unavailable` 只用于有真实实现与证据的能力；缺测或未执行一律记 `not verified` 并阻塞开放，不因缺测改写为 `unavailable`（spec 07）。

| 能力（本票范围） | 26.1.2（primary） | 26.2.0 | 1.21.1 | 26.1.2-fabric | 26.2.0-fabric |
|---|---|---|---|---|---|
| inert 候选计划收集/preflight/fingerprint/冲突/stale（common 面） | supported（本地证据：`DynamicCandidatePlanSemanticsTest`/`DynamicPlanInertnessTest` 全绿） | not verified（定向：本票只跑 26.1.2/1.21.1 全量 build） | not verified（common 同源，定向：`:1.21.1:build` 通过，未跑本票测试面定向） | not verified | not verified |
| 事件 facade（`DynamicRegistryEvents.dynamicRegistry`）可用性 | not verified（源码接线在 `>=26` + neoforge 面；缺真机 `/nekojs reload` smoke 与 probe 真机输出） | not verified | **源码面缺席**（与旧动态注册同边界，整包 `>=26` 守卫；`ServerEventListener` 触发点同守卫）——按 spec 07 记 not verified，不写成 unavailable | 源码面缺席（`DynamicRegistryPlugin` 整文件 `neoforge` 守卫；fabric 侧无对应实现） | 同左 |
| 真实 registry mutation / 多人同步 / 热更新 | 不在本票（票 21 gate） | — | — | — | — |
| 未开放类型（`entity_type`/`fluid`/`block` 等） | not verified（从未进入候选范围，无入口；不以 no-op/静默降级出现） | not verified | not verified | not verified | not verified |

> 口径说明：本票不含任何 game 内 smoke（预期如此——激活归票 21）。因此**所有节点**的「facade 可用性」都停在 `not verified`；`supported` 只用于本票能真实跑出的本地计划行为。

## 4. fingerprint / stale 语义表

| 维度 | 语义 | 断言位置 |
|---|---|---|
| fingerprint 输入 | `sha256("dyn-v1\|" + type.apiName + "\|" + 规范化 id + "\|" + mode + "\|" + 全量可写属性读数(字典序, name=value;) )` | `DynamicDefinition.fingerprint`（唯一构造路径 `DynamicDefinition.of`） |
| 规范化 | id：无命名空间补 `minecraft:`、trim、必须小写 `[a-z0-9_.-]/[a-z0-9_./-]`；mode：`world`/`reloadable`（`global` 拒绝）；字符串属性：trim + 小写封闭集合（rarity/category）；`null` 与「从未写入」同一状态 | `normalizeId`、`DynamicRegisterMode.parse`、`normalizeToken`、parity 测试断言读数逐字值 |
| 不依赖 | 对象身份、builder 实例、属性写入顺序、部分字段 | `fingerprintIsStableAcrossInstancesAndReloadsAndChangesWithAnyField`、读数为全量属性 |
| 约定连带声明 | 一期三个类型无自动连带注册对象（spec 08 边界），因此「连带声明」= builder 的完整属性集 + mode；`mode` 变化可识别 | `fingerprintCoversEveryBuilderInputTypeAndId` |
| 重复 reload 同定义 | 同 fingerprint ⇒ 幂等去重/重 claim（同批同 key 同指纹也去重） | `sameDefinitionAcrossReloadsGetsSameFingerprintAndReclaims`、`sameBatchDuplicateWithDifferentDefinitionFailsAtCollection` |
| 同 key 定义变化 | 整批冲突失败（`dynamic-registry-conflict`），旧 active 定义继续服务；collect: 同批重复不同定义在收集期 fail-fast（毒化整批） | §2 AC6 证据 |
| stale/retired | 成功 commit 时先全量 `beginReload`（标记 stale）再逐条 claim；未重声明者停留 stale，**不物理删除**（exposed 定义与 fingerprint 保留，继续参与冲突检测） | `commitBatch`、`staleMarkingIsNotAPhysicalDeletionAndKeepsTheConflictLedger` |
| generation | 每次 `beginBatch` 递增（失败轮只耗号不提交）；已提交 generation 只在 commit 点推进；Adapter 请求带批次 generation | `committedGeneration`、`adapterRequestsAreGenerationScopedAndInertByConstruction` |
| 显式清理 | 不在本票（`remove` 只存在于旧 `DynamicRegistrationBookkeeping` 的世界退出路径） | §10 P4 |

## 5. 命名决策记录（AC2 记录义务）

| 项 | 票面/spec 08 工作名 | 实施采用 | 理由 | 进入面 |
|---|---|---|---|---|
| 事件组 | `ServerEvents.dynamicRegistry`（原文「工作名，非最终 API」） | **`DynamicRegistryEvents`**（独立组，唯一成员 `dynamicRegistry`，SERVER side） | `ServerEvents` 是 NeoForge 专属树 + fabric 另有一份同 FQCN 文件；挂进去会把独立生命周期焊死在 loader 专属组上，common 层无法承载/测试；独立组与启动期 `RegistryEvents`（同为独立组）对称表达「两个生命周期」，符合 spec 08「不与启动期混淆」 | declaration golden（`DynamicRegistryEventsDeclarationGoldenTest` 断言不出现工作名）、contract（catalog 条目）、迁移表（`MIGRATION.md` §1） |
| 成员名 | `dynamicRegistry` | **`dynamicRegistry`**（保持） | 未偏离 | 同上 |
| 类型直达入口 | `event.item/soundEvent/mobEffect` | **同名保持** | 票面冻结 | 成员目录 + declaration sugar |
| Builder 成员形态 | spec 08 user story 13：`item.setMaxStackSize(...)` ≡ `item.maxStackSize = ...` | **JavaBean 双形态**（`setXxx`/`getXxx` + property 写） | 与票 15 启动期 builder 契约一致；旧 `DynamicRegistryJS` 的 fluent `maxStackSize(64)` 形态属旧路径（本票不动，迁移表说明）；新面不引入「属性名＝方法名」双重语义（Graal `invokeMember` 与属性读值冲突，interop 不可靠） | `DynamicDefinitionBuilder` javadoc、`MIGRATION.md` §2、声明 golden |

## 6. 旧路径与删除 gate（AC11 输入；本票零删除）

| 旧路径 | 现状消费者 | 替代物状态 | 处置 |
|---|---|---|---|
| `DynamicRegistryJS`（静态 `INSTANCE` + `ServerEvents.started` 内直注；脚本面 `DynamicRegistry.item/soundEvent/mobEffect`） | `DynamicRegistryBinding`（binding 注册）、脚本（迁移表面对）、`DynamicRegistryBuilderTest`（7 用例） | 本票 facade（`DynamicRegistryEvents.dynamicRegistry` → inert 计划）；**行为差异**：候选计划 vs 即时注册，无 remove/replace | **保留**；删除需维护者 sign-off |
| `DynamicRegistries` static registry surgery（`DynamicRegistrySet` + `RegistrySurgery.withUnfrozenRegistry/withUnfrozenAndHolders/clearHolderTags` + `RegistryDataCollectorMixin` 重放） | `DynamicRegistryJS`、`RegistryDataCollectorMixin`（mixin 配置 `nekojs-dynamic.mixins.json`）、`DynamicRegistryDebug` | 计划面**不含** surgery 通道（`DynamicPlanInertnessTest`）；真实 mutation 归票 21 的 Adapter | **保留**（本票不触碰 mixin/注册路径） |
| `DynamicRegistryDebug.snapshot()`（`/nekojs registry` 调试视图） | `NekoJSCommands:131/139`、`FabricNekoJSCommands:371/379` | 新面提供 `store.exposedSnapshot/staleIds/retiredKeys/trackedClaims/claimOf`（本票测试与诊断用），尚未接入命令面 | **保留**；命令面统一归票 20/21 |
| `DynamicRegistrationBookkeeping`（static 账） | `DynamicRegistries`/`DynamicRegistryDebug` | 新面 `DynamicRegistryPlanStore` 每实例一份账（同 label 语义，独立实例） | **保留**（旧账仍服务旧路径；本票不双写） |
| 旧 `[dynamicRegistry] enabled` 配置门（默认 false） | `DynamicRegistryJS.requireRunningServer` | 新 facade 的**计划收集**不受该门控（inert、无 mutation）；真实激活必须同时满足配置门 + 票 21 事务/同步 gate（**§12 审查点 2**） | 记录待票 21 裁决 |

`parity` 说明：本票的替代 parity 是「能力等价面 + 差异显式」而非字节等价——旧路径的即时 mutation 语义不在本票替代范围（票 21 才激活），差异逐项写在 `MIGRATION.md` §3。

## 7. 与 14/15/06/10 的衔接与边界

| 票 | 关系 | 本票做法 |
|---|---|---|
| 14（事件面） | 复用既有事件面，不新增第二 bus | facade 是**独立事件组 + 唯一总线**，经 `EventsPoint.Contributor`（`DynamicRegistryPlugin.registerEvents`）注册进既有 `EventGroupRegistry`；catalog/声明链复用票 14 的渲染器与条目形状；不复制 bus |
| 15（启动期注册） | 共享类型事实与 Builder 表达方式，**禁止复用启动期 global drain** | 动态 builder 是独立类（不实现 `RegistryObjectBuilder`，`builderSurfaceDoesNotWrapStartupDrainBuilders` 结构钉住）；计划不进入 `RegistryRepository`/drain；类型事实只共享注册表键与「已验证类型」口径 |
| 06（候选环境/commit 点） | 消费其候选 generation 与 commit 点 | 候选监听器是 `PendingListener`（collection 期执行、commit 才 activate）；计划挂入其 `GenerationGlobals` 联合边界，`publishJoint` 在 commit 步骤 0 发布计划；失败走 `discardCandidate` |
| 10（global/shared 联合提交） | 只协调同一候选的联合失败边界 | `DynamicCandidateRegistryPlan implements CandidateStatePlan`（零领域语义接口），与 global/shared 写集在同一 candidate 内**联合成功或全部不发布**；域失败以精确 domain（`dynamic-registry-*`）拒绝，不把 global 实现作为 blocker |
| 07（线程/close） | 复用 owner-thread 串行与回调标记 | 收集期在候选 Context（owner thread）执行，`switchCurrentScriptId` + `noteCallbackEnter/Exit` 与生产分发同款；收集期 kill 上报与生产 catch 对称（`reportContextKilled`） |
| 09/17（managed surface/probe） | 声明派生归各自 owner | 动态 builder 声明走既有 `TypeDocsPoint` + 生产渲染器（条目形状复用，不手写第二份成员表）；manifest/legacy golden 零改动（`api-manifest-core.json`、probe-ts fixture、legacy tree 未变，`:common:check` 通过即证） |

## 8. golden 变更留痕（REGENERATE.md §3 格式）

**新增两份 golden（本票零改动既有 golden）：`common/src/test/resources/nekojs/dynamic/dynamic-registry-events.expected.d.ts`（11 行）与 `dynamic-builders.expected.d.ts`（31 行）**

- **原因**：AC2 要求命名决策进入 contract/golden 与 declaration；AC9 要求调用者 Interface、契约、声明与 contract/golden 同一证据链。两份 golden 均由**生产渲染器**输出：事件声明 = `NekoScriptCatalog.events` + `EventDeclarationGenerator`（票 14/15 同一渲染器）；Builder 声明 = `DynamicBuilderSurfaces.derive()` + `RegistryBuilderTsRenderer`（票 15 同一渲染器）。
- **旧新 diff**：两份均为新文件（旧值：不存在）。生成方式 = `:common:regenerateGoldens --tests DynamicRegistryEventsDeclarationGoldenTest`（先落 placeholder 使资源目录可解析，产物覆盖后逐行审阅），流程与票据 14/25 同款；REGENERATE.md §1 已加登记行。
- **影响**：只被 `DynamicRegistryEventsDeclarationGoldenTest` 消费；`api-manifest-core.json`、probe-ts fixture、legacy probe tree、startup-builders golden 零变化。
- **冻结口径**：golden 只冻结**声明形状**（组名/成员名/类型名/成员集合与标注），不冻结能力结论（§3）；Builder golden 的头注是共享渲染器（ticket 15 所有）的固定文本，动态条目的派生输入是 `DynamicBuilderContract`（已登记进 REGENERATE.md）。
- **审阅记录**：owner 自查（zcode-agent，2026-09-17）——逐行核对成员来自真实 builder 反射与生产渲染器；**缺维护者审阅**（与票 14 G6/票 15 G4 同款如实标注）。渲染观察：facade 事件声明的 payload 别名 `$DynamicRegistryEventJS` 无对应 `java:` import 行（与 ProbeEvents golden 形状不同）——已记 §11 G3，公开激活前需 probe owner 确认。

## 9. 测试结果（真实运行）

| 套件/命令 | 结果 | 数字 |
|---|---|---|
| `:common:check` + `:common-api-processor:test` | 通过 | common 213 suites / 1539 tests / 0 failures / 0 errors / 4 skipped；processor 1 suite / 13 tests / 0 failures |
| `guardLint` | 通过 | 守卫块 265 / 扫描 415 文件 / 超限豁免 0 / 警告 0 |
| `:26.1.2:build` | 通过 | 54 suites / 271 tests / 0 failures / 0 errors / 36 skipped（`compileJava` 真编译 + `test` 真跑，非 UP-TO-DATE） |
| `:1.21.1:build` | 通过 | 41 suites / 188 tests / 0 failures / 0 errors / 0 skipped（含本次行内 `>=26` 守卫的节点复验） |
| 本票新增/扩展套件（common） | 通过 | 9 套件 / 43 用例 / 0 失败（plan 语义 9 + Builder parity 6 + 计划惰性 4 + facade 6 + reload 6 + 候选惰性 5 + 声明 parity 4 + 示例 1 + 声明 golden 2）；同域 `DynamicRegistrationBookkeepingTest` 12 用例为旧路径 prior art（本票未改） |

套件级（`:common:test`，2026-09-17 本 worktree）：

| 套件 | 用例 | 覆盖面 |
|---|---|---|
| `core.dynamic.plan.DynamicCandidatePlanSemanticsTest` | 9 | AC5/6/7/8 计划语义（fingerprint 全量、整批冲突、毒化、stale、Adapter 请求、联合边界、id 规范化） |
| `core.dynamic.plan.DynamicBuilderSurfaceParityTest` | 6 | AC4/AC5 真 Graal setter/property parity 与错误面 |
| `core.dynamic.plan.DynamicPlanInertnessTest` | 4 | AC7 结构惰性（无执行通道/无 MC 类型/动作封闭/范围封闭） |
| `core.dynamic.facade.DynamicRegistryEventFacadeTest` | 6 | AC2/AC3 类型直达入口、目录封闭、毒化、无通用 catalog |
| `core.dynamic.facade.DynamicRegistryReloadPipelineTest` | 6 | AC1/AC6/AC7 reload 候选收集、冲突、收集错误、CLIENT 隔离、未使用域零参与、seam 对称 |
| `core.dynamic.facade.DynamicRegistryCandidateInertnessTest` | 5 | AC1/AC7/AC8 候选期可观察、不挂回调、收集器崩坏归因、域参与范围、stale 语义 |
| `core.dynamic.facade.DynamicRegistryDeclarationParityTest` | 4 | AC9 声明链同源（catalog/TS/Py/契约同集合） |
| `core.dynamic.facade.DynamicRegistryInertPlanExampleTest` | 1 | AC12 示例四场景（含 parity 场景） |
| `probe.DynamicRegistryEventsDeclarationGoldenTest` | 2 | AC2/AC9 两份 golden（命名决策冻结） |
| `core.dynamic.DynamicRegistrationBookkeepingTest` | 12 | 旧路径 claim/stale/mode prior art（本票未改） |

## 10. 问题与处理

1. **1.21.1 编译断裂（接管时未发现）**：`ServerEventListener` 是 `neoforge` 全文件守卫（各 NeoForge 节点共享），而 `DynamicRegistryFacade` 在 `>=26` 包内——1.21.1 会因找不到符号失败。处理：触发点加行内 `//? if >=26 {` 守卫（与 `NekoJSMod`/`NeoForgeRegistryQueryService` 同款格式），并在注释里写明「与旧动态注册整包同边界，能力记 not verified，不是漏接线」；`:1.21.1:build` 复验通过（§9）。
2. **候选期收集可观察性缺口**：接管时 `collectForCandidate` 不留任何记录，reload 路径的「候选阶段重新收集」只能间接推断。处理：新增 `CandidateCollectionRecord`（note/planGeneration/collectedDefinitions/poisoned/failureDetail，**不持有计划引用**）与 `lastCandidateCollection()` 观察点，三条参与/跳过路径都可断言（§2 AC1/AC12 证据）。
3. **收集期 kill 归因不对称**：生产分发闭包在 catch 内调用 `ScriptManager.reportContextKilled`，收集器只记收集错误。处理：补齐该调用，使候选 Context 被资源上限终止时按候选失败记账而不是普通收集错误（`DynamicRegistryFacadeRuntime` 注释说明）。
4. **`DataSyncGenerationBoundaryTest` 认领基线漏适配**：`ScriptEnvironmentFactory` 在票 10 后只有 4 参构造器（含 `GlobalStateStores`），该测试仍用 3 参 → 平台测试树无法编译。处理：顺带修复为 4 参并加注释（`test(data-sync)` 单独提交；不含语义变更，测试仍不覆盖 global/shared 面）。
5. **graal/`hasListeners` 观察面**：AC7「不挂生产 callback」没有直接的「listener 计数」API；用 `hasListeners()` + 「旧 active 无本域监听器」的前置构造使候选 listener 是否越界成为可观察布尔（§2 AC7）。
6. **golden 只能经显式再生成**：新增 golden 需先让资源目录在 classpath 可解析（`ProbeGoldenSupport.resourceDir` 走 `getResource`），处理为「先放 placeholder → regen 覆盖 → 逐行审阅」，并保持 `:common:regenerateGoldens` 过滤集不变（golden 测试住 `probe` 包）。
7. **工单文件**：本票按认领元数据补齐 AC 勾选（AC11 保持未勾选）并追加 Closure record；关票/合并与五节点全量门归主会话（沿用票 10/15 惯例）。

## 11. 遗留缺口与建议 owner

| # | 事项 | 状态 | 建议 owner |
|---|---|---|---|
| G1 | 真机 smoke：server registry ready 初次候选 + `/nekojs reload server` 候选收集 + 日志可观察（AGENTS.md 的 minecraft-mod-mcp 通道） | not verified（本地管线证据已备） | 主会话（可顺带覆盖票 21 前置接线） |
| G2 | probe 真机输出中的动态声明（事件 + Builder）与两份 golden 的一致性；未跑 `/nekojs probe` | not verified | 随 G1 |
| G3 | facade 事件声明的 payload 别名 `$DynamicRegistryEventJS` 无 `java:` import 行（渲染观察，见 §8） | not verified（渲染器证据） | 票 21 / managed-surface(W5) probe owner 在公开激活前裁定（补 import 或显式 `any`） |
| G4 | 动态 Builder 声明的 `setMode(...)` 链式返回渲染为 `any`（Java 侧返回基类 `DynamicDefinitionBuilder`，`returnShape` 不视作 builder） | 已记录（不影响运行期与脚本可用性） | W5/probe 渲染器 owner（可选收紧） |
| G5 | `:26.2.0:build`、`:1.21.1` 本票测试面定向、两个 fabric 节点全量未跑（common/src/main 与 src/main 均有改动） | 本票未验证 | 主会话合并后五节点统一 build（既定流程） |
| G6 | AC11 删除 gate：旧 `DynamicRegistryJS`/`RegistrySurgery`/mixin/旧配置门 | 材料已备（§6 + MIGRATION） | 维护者 sign-off（主会话裁定） |
| G7 | `[dynamicRegistry] enabled` 配置门与新 facade 的关系（计划收集不门控、激活须双门） | 待票 21 裁决（§12 审查点 2） | REGISTRY_DYNAMIC_SYNC(票 21) |
| G8 | golden 审阅缺维护者确认（§8） | owner 自查完成 | 主会话维护者审阅 |
| G9 | common 测试树 gameDir 唯一化：本票新测试用 `TestPlatformInit.uniqueGameDir(base)`，其余 10 个 common 测试类仍是固定 `nekojs-test-gamedir`（票 10 N8 的 helper 下沉债） | 部分完成 | 后续整理票（helper 下沉 common） |

## 12. 需要主会话审查的重点

1. **命名决策**（§5）：facade 采用独立组 `DynamicRegistryEvents` 而非 spec 08 工作名 `ServerEvents.dynamicRegistry`。理由已记录并进 golden/declaration/迁移表；若维护者坚持工作名，需要改组名 + 重新生成 golden + 更新 `MIGRATION.md`（改动面小、已留痕位置明确）。
2. **配置门边界**（§6 末行 / G7）：新 facade 的**计划收集**不读 `[dynamicRegistry] enabled`（inert、无副作用），真实激活须同时满足该门与票 21 事务/同步 gate。若维护者要求计划收集也门控，需要把 config 访问接进 facade runtime（当前刻意不引入该耦合）。
3. **`CandidateCollectionRecord` 公开观察点**：属诊断/测试面，不携带内部状态、不持有计划引用；若不希望出现在公开面，可降为包内可见（会削弱 AC1 的可观察性证据）。
4. **AC11 与五节点口径**：AC11 未勾选（维护者删除门禁）；§3 能力表按 spec 07 口径把「源码面缺席（1.21.1/fabric）」如实记 `not verified` 而非 `unavailable`，请确认该表述在发布材料中的用法。
