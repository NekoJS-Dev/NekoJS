# 24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径

**What to build:** 脚本作者继续使用既有 Block、Item、Level、Player、Command、Capability、Goal 和 Entity 事件族完成订阅、修改、取消和清理；其中 Item/Block modification 的 candidate/snapshot 语义由票 39 负责，本票负责事件族完整盘点与公开面一致性。事件公开名、payload、side、priority/cancel、平台差异和 TS/Python declaration 由 managed catalog/contract 固定，不新增事件或万能 Event Module。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md)

**Status:** in-review（实现/测试/证据已交付；AC5/AC8/AC9 部分满足未勾选——D2 行为缺陷待维护者裁定、declaration 0 覆盖归 09/33/34、真机 smoke 归票 34）

**Assignee:** zed-flash-24（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-22):** worktree `../NekoJS-mult-t24` on branch `ticket-24-gameplay-event-surface`（基于 mult HEAD）。预计改动范围：Block/Item/Level/Player/Command/Capability/Goal/Entity 事件族 catalog snapshot 盘点与差异、representative caller-to-result fixture（注册/payload/修改/取消/优先级/side）、并发与多次 reload 清理断言、wrapper 公开名进 managed contract（golden 只读）、NeoForge/Fabric source trace 与 capability matrix、示例与必要迁移材料、`baseline/2026-09-22-gameplay-event-surface/` 证据。Item/Block modification 只验证事件面接线（事务/snapshot 归票 39 证据）；不修改 30 诊断域文件；不新增事件、Extension Point 或第二注册路径。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 盘点现有 BlockEvents、ItemEvents、LevelEvents、PlayerEvents、CommandEvents、CapabilityEvents、GoalEvents、EntityEvents 及 entity/living wrapper 的公开成员，形成真实 catalog snapshot 差异，不逐 symbol 开票。
- 为 block、item、level、player、command、capability、goal、实体生命周期/伤害/死亡/掉落/生成行为各选 representative caller path，验证注册、payload、修改、取消、优先级和 side。
- 把 wrapper 公开名与 payload 纳入 managed contract/golden，原生 NeoForge/Fabric callback和 mixin留在平台 Adapter 并记录 source trace。
- 验证并发/多次 reload 后 listener 清理、无重复 dispatch、取消结果和线程/时机语义。
- 补 TS/Python declaration parity、错误阶段与逐节点 capability matrix/source trace/smoke。
- 收缩 gate：capability/goal/entity 旧 wrapper、binding 或声明旁路只有在替代 behavior、declaration、trace 与无调用者证据通过后移除；公开事件功能不删除，清理随本票完成而不是 final release 统一处理。

## Acceptance criteria

- [x] Block、Item、Level、Player、Command、Capability、Goal、Entity 的公开事件族在真实 catalog snapshot 中没有遗漏家族；新增/删除公开成员会产生 contract diff 而不是静默 drift。【evidence: `Ticket24GameplayEventCatalogTest`（NeoForge，生产注册入口→`NekoScriptCatalog.events` 真实派生 vs 冻结表：名/payload/side/dispatch 键/cancel 五列）+ `Ticket24FabricGameplayEventCatalogTest`（fabric 生产次序：v1/v2 Adapter 类初始化 + `FabricCorePlugin.registerEvents` 同名合并→同一派生）+ 票 33 基线三节点复验（26.1.2/1.21.1/26.1.2-fabric platformGateTest 0 失败 0 member-drift）。发现 D1：fabric 运行面比票 33 基线多 BlockEvents×10/LevelEvents×5（gate 输入边界），基线再生成归 33/34，本票不改只读基线】
- [x] 每个事件族至少有一条 caller-to-result fixture；`CommandEvents.register` 与 `CommandEvents.command` 不与管理命令票混Owner，Item/Block modification 只验证事件面接线，事务与 snapshot 语义以票 39 证据为准。【evidence: block=FamilyBusBehavior(blockBrokenWrapperPayload…)+PhaseTrace；item=FamilyBusBehavior(modificationFamilyIsPostedObjectMode…)+PhaseTrace(modification poster 唯一=ModificationDomainOwner，票 39 域)；level/player=ReloadLifecycle e2e（真实 Graal）；command=ReloadLifecycle(cancel*，与票 20 /nekojs 命令树无交集)；capability=FamilyBusBehavior+PhaseTrace(唯一 poster=NekoJSMod.onRegisterCapabilities)；goal=ReloadLifecycle(goalStartupFamily…)+PhaseTrace(两 loader entry 各一次)；entity=ReloadLifecycle(entityFamilyLifecycle… 六行为)+FamilyBusBehavior(dispatchKeyRouting…)】
- [x] 每个 representative path 从脚本 listener 注册到平台 callback、wrapper payload、执行或取消结果可追踪。【evidence: 脚本注册→总线→payload→执行/取消在 e2e/bus 测试真跑（无头 JVM 边界内）；平台原生回调腿 = PhaseTrace 六用例（FORGE_BRIDGE 逐族绑定数、唯一 poster、双逻辑侧过滤）+ 既有 `EventBusForgeBridgeTest`/`EventBusForgeBridgeSideFilterTest`；真机回调 smoke 归 AC9/票 34】
- [x] priority、cancel、返回值修改和多次订阅行为符合既有语义，并发 stress 后无重复 dispatch或半清理状态。【evidence: priority 次序/cancel 短路与 post 返回值/多次订阅恰一次/dispatch 定向（FamilyBusBehavior 8 用例）；多次+失败 reload 每代恰一次、失败保留旧 active、下一轮恢复（ReloadLifecycle 6 用例）；突变 C（双重激活）4 用例红→还原绿、突变 D（取消被忽略）红→还原绿（baseline command-output/03）；清理三层防御 characterization 同文件；并发 stress 本体 = common `EventBusJSExternalBehaviorStressTest`（`:common:check` 全绿）】
- [ ] server/client side 过滤、mixed side 与 loader/version capability 显式记录；不可用能力不静默 no-op。**部分满足**：side 全列冻结（CLIENT tooltip、STARTUP goal/capability、其余 SERVER；双逻辑侧 `!isClientSide` 过滤逐文件 trace，含 1.21.1 孪生）；fabric 能力差异逐项显式（command/useItem* 缺席、chat/dropped/finalizeSpawn 不可取消、CapabilityEvents 缺席）。**缺（D2，行为缺陷）**：`BlockEvents.broken` 文档记「可取消」但总线在全部四个节点不可取消——脚本取消静默 no-op（wiki 与 `BlockBrokenEventJS` @Doc 还互相矛盾：后者写 return false 取消）。修复=公开行为变更，需维护者裁定（baseline REPORT §5-D2）
- [x] 实体加入/离开、伤害、死亡、掉落、finalize spawn 等行为至少各有一条 caller-to-result fixture，覆盖当前公开家族代表。【evidence: `entityFamilyLifecycleListenersFollowTheSameGenerationSwap`：joinLevel/leaveLevel/damagePre/death/drops/finalizeSpawn 六行为同 e2e 两代换装（每代各恰一次/零次断言）+ FamilyBusBehavior dispatch 腿 + fabric catalog/trace 腿】
- [x] capability/goal 事件不与启动 registry、交易或客户端实现 owner混淆；本票只冻结事件面和 Adapter 交界。【evidence: 两族 STARTUP 面、posting site 唯一（trace）；fabric core plugin 零 CapabilityEvents 触点（显式缺席断言）；生产源码零改动（无 registry mutation 参与）；GoalRegistry 静态账本为 STARTUP 生命周期状态 characterization，不动】
- [ ] TS/Python declaration 与 runtime member/payload一致，普通测试不更新 golden。**部分满足（declaration 面不勾选）**：runtime member/payload 四列快照已交付（两 loader catalog 测试）；零 golden 改动 ✓。**缺（实证）**：八个事件域在 declaration golden 与 parity 基线 **0 命中**（baseline command-output/04，与票 26/28/23 同类缺口）→ owner Managed Surface/Probe（09/33）+ 票 34
- [ ] NeoForge/Fabric source trace 和 runtime smoke 按支持等级记录，不能用反射清单替代。**部分满足**：source trace 六用例（真实文件读）+ 三节点 platformGateTest 复验 + catalog 经真实注册入口派生（非反射清单）；**缺**：真机 runtime smoke（runGameTestServer/真实游戏事件）未跑 → owner 票 34（baseline REPORT §6-G1）
- [x] 不新增平行事件、Extension Point或第二注册路径。【evidence: 生产主源码零改动（git diff）；`EventGroup.of("<族名>")` 声明点计数冻结（PhaseTrace.noSecondRegistrationPath 断言 Block/Capability/Goal=1、Level/Command=2、Item/Player/Entity=3）；FORGE_BRIDGE 绑定数冻结；posted-object/listener-posted 总线不在 bridge 上】
- [x] 旧 wrapper/binding/声明旁路只有在替代 behavior、declaration、trace 通过且无调用者后移除；公开 capability/goal/entity 事件功能不删除，清理不推迟 final release。【evidence: 本轮零删除——盘点未发现待移除旁路：唯一 poster 逐项钉住、无第二注册路径；deprecated 别名（pickedUpPre/tick/beforeExplosion/afterExplosion/inventoryOpened/inventoryClosed）为公开功能保留（源码 @Deprecated 迁移提示一致）；`GoalRegistry` 进程级静态为 STARTUP 绑定语义（同 KeyBindEvents 既有裁定）非 reload 旁路——characterization 记录于 baseline REPORT §2-AC11】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): 全部 gameplay/platform wrapper 需要复用同一事件 contract、bus 清理和 declaration 链；直接逐 symbol 补测试会留下第二套事件规范。
- [39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md): Item/Block modification 的 candidate、live mutation、snapshot 与恢复语义必须先由专项票收口；本票不得用 catalog 清单冒充该行为验收。

## Scope and coordination

**Rationale:** 按用户补充，把 capability/goal/entity 行为放入一个足够窄的既有事件域票，用家族 representative path 加 catalog diff 防漏，不为每个 symbol开票，也不造新事件。

**Coordination:**

- RUNTIME_ROOT/RELOAD_COMMIT: listener generation、失败保留和清理语义与 runtime 组共同验证。
- REGISTRY_STARTUP: capability registry/type 事实源如涉及启动注册，由 registry 组负责；本票不迁移 registry mutation。
- client 域拥有者: render/client-only 实体或 UI 相关平台实现不由本票重写，只保留事件公开面边界。
- BUILD_BASELINE: 当前 catalog 与事件 wrapper 行为先作 characterization，再冻结有意承诺。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
