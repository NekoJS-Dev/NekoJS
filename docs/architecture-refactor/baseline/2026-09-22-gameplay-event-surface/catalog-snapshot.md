# 票 24 catalog snapshot：八个 gameplay 事件族的公开成员清单（2026-09-22）

事实源（真实派生，非手抄）：
- NeoForge 侧：`Ticket24GameplayEventCatalogTest`（生产注册入口 `NekoJSCorePlugin.registerEvents/registerClientEvents`
  （含 `NeoForgeBlockEvents.bootstrap()`）→ `NekoScriptCatalog.events`）。
- fabric 侧：`Ticket24FabricGameplayEventCatalogTest`（生产次序：v1/v2 Adapter 类初始化 +
  `FabricCorePlugin.registerEvents` 同名合并 → 同一 catalog 派生）。
- 跨节点 bus 名：`platformGateTest` + `event-surface-domains.txt`（票 33 只读基线，本票复验
  26.1.2 / 1.21.1 / 26.1.2-fabric 三节点 0 失败、0 member-drift，见 command-output/05–07）。

列含义：payload = catalog eventType；side = SERVER/CLIENT/STARTUP（单一 side）；
dispatch = 定向分发键类型（`-` = 非定向）；cancel = 总线可取消性（NeoForge 由
`ICancellableEvent` predicate 决定，例外为显式 `EventBusJS.of(..., true, ...)` 声明的
总线（broken/damagePre，D2/D4 修复）；fabric 同为显式声明）。

## BlockEvents

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| broken | BlockBrokenEventJS（中立） | SERVER | Block | **false** | **D2 缺陷**：wiki 记「可取消」，实现为不可取消（所有加载器），见 REPORT |
| entityPlaced | BlockEvent.EntityPlaceEvent | SERVER | Block | true | 26.x/1.21.1 |
| entityMultiPlaced | BlockEvent.EntityMultiPlaceEvent | SERVER | Block | true | |
| neighborNotify | BlockEvent.NeighborNotifyEvent | SERVER | Block | true | |
| fluidPlaced | BlockEvent.FluidPlaceBlockEvent | SERVER | Block | true | |
| farmlandTrample | BlockEvent.FarmlandTrampleEvent | SERVER | Block | true | |
| portalSpawn | BlockEvent.PortalSpawnEvent | SERVER | Block | true | |
| toolModification | BlockEvent.BlockToolModificationEvent | SERVER | Block | true | |
| rightClicked | PlayerInteractEvent.RightClickBlock | SERVER | Block | true | 双逻辑侧：SERVER 总线带 `!isClientSide` 过滤（fabric：BlockRightClickEventJS，同形） |
| placed | BlockEvent.EntityPlaceEvent | SERVER | Block | true | fabric：BlockPlacedEventJS |
| leftClicked | PlayerInteractEvent.LeftClickBlock | SERVER | Block | true | fabric：BlockLeftClickEventJS |
| randomTick | RandomTickEvent（NekoJS） | SERVER | Block | false | fabric：BlockRandomTickEventJS。D5（2026-09-28 冒烟，REPORT §8.3）：两 loader mixin 注入在接口 default `randomTick` HEAD，被原版方块覆写绕过——原版随机 tick 方块上事件不可达 |
| blockEntityTick | BlockEntityTickEvent（NekoJS） | SERVER | BlockEntityType | false | |
| modification | BlockModificationEventJS | SERVER | - | false | posted-object 模式（>=26 才有；1.21.1 无此成员）；票 39 域 |

fabric v1/v2 Adapter 追加成员（NeoForge 侧同名同义）：portalSpawn/neighborNotify/farmlandTrample/
placed/entityPlaced/fluidPlaced 额外显式可取消（中立 payload）。

## ItemEvents

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| rightClicked | PlayerInteractEvent.RightClickItem | SERVER | Item | true | 双逻辑侧过滤；fabric：ItemRightClickEventJS |
| modification | ItemModificationEventJS | SERVER | - | false | posted-object；票 39 域 |
| tooltip | ItemTooltipEvent | CLIENT | Item | false | 唯一 CLIENT 成员；fabric：ItemTooltipEventJS |
| canPickUp | ItemEntityPickupEvent.Pre | SERVER | Item | true | |
| pickedUpPre | 同 canPickUp | SERVER | Item | true | @Deprecated 冗余别名 |
| pickedUp | ItemEntityPickupEvent.Post | SERVER | Item | false | fabric：ItemEntityPickupEventJS |
| dropped | ItemTossEvent | SERVER | Item | true | fabric：ItemDroppedEventJS（**不可取消**，已知能力差异） |
| entityInteracted | PlayerInteractEvent.EntityInteract | SERVER | Item | true | 双逻辑侧过滤 |
| foodEaten | LivingEntityUseItemEvent.Finish | SERVER | Item | false | fabric：ItemUseFinishedEventJS |

## LevelEvents

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| loaded / unloaded / saved | LevelEvent.Load/Unload/Save | SERVER | - | false | fabric：LevelEventJS / LevelSavedEventJS |
| tickPre / tickPost | LevelTickEvent.Pre/Post | SERVER | - | false | 双逻辑侧过滤 |
| tick | LevelTickEvent.Post | SERVER | - | false | @Deprecated 别名 |
| explosionStart / beforeExplosion | ExplosionEvent.Start | SERVER | - | true | beforeExplosion @Deprecated；fabric：LevelExplosionEventJS |
| explosionDetonate / afterExplosion | ExplosionEvent.Detonate | SERVER | - | false | afterExplosion @Deprecated |

## PlayerEvents

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| loggedIn / loggedOut | PlayerEvent.PlayerLoggedIn/Out | SERVER | - | false | fabric：PlayerLifecycleEventJS |
| chat | ServerChatEvent | SERVER | - | true | fabric：ServerChatEventJS（**不可取消**，能力差异） |
| tickPre / tickPost | PlayerTickEvent.Pre/Post | SERVER | - | false | 双逻辑侧过滤 |
| cloned | PlayerEvent.Clone | SERVER | - | false | |
| respawned | PlayerEvent.PlayerRespawnEvent | SERVER | - | false | |
| changedDimension | PlayerEvent.PlayerChangedDimensionEvent | SERVER | - | false | |
| advancement | AdvancementEvent.AdvancementEarnEvent | SERVER | - | false | |
| containerOpened / inventoryOpened | PlayerContainerEvent.Open | SERVER | - | false | inventoryOpened @Deprecated 别名 |
| containerClosed / inventoryClosed | PlayerContainerEvent.Close | SERVER | - | false | inventoryClosed @Deprecated 别名 |
| entityInteract | PlayerInteractEvent.EntityInteract | SERVER | - | true | 双逻辑侧过滤；两加载器均可取消 |
| crafted / smelted | PlayerEvent.ItemCrafted/Smelted | SERVER | Item | false | |
| destroyed | PlayerDestroyItemEvent | SERVER | Item | false | |
| inventoryChanged | InventoryChangedEventJS（中立） | SERVER | Item | false | 唯一 poster = InventoryChangeListener |

## CommandEvents

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| register | RegisterCommandsEvent | SERVER | - | false | 与票 20 的 /nekojs 管理命令注册**不同域** |
| command | CommandEvent | SERVER | - | true | fabric 无此成员（显式缺席：Commands#performCommand mixin 未落地） |

## CapabilityEvents（NeoForge 专属）

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| register | CapabilityRegistryEventJS | STARTUP | - | false | poster = NekoJSMod.onRegisterCapabilities（mod bus）；fabric not-verified（票 33 基线） |

## GoalEvents

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| register | GoalRegisterEventJS | STARTUP | - | false | poster = GoalEvents.postRegister（两 loader entry 各调一次）；不与启动 registry mutation 混域 |

## EntityEvents

| 成员 | payload | side | dispatch | cancel | 备注 |
|---|---|---|---|---|---|
| damagePre | LivingDamageEvent.Pre | SERVER | EntityType | true | 可改伤害值（setNewDamage）；取消=桥映射 setNewDamage(0)（伤害链走完，damagePost 仍以 0 触发；D4 修复 2026-09-29，修复前状态见 REPORT §8.3——Pre 不实现 ICancellableEvent、总线曾不可取消）；fabric：仅可取消（整体免除），不可改值 |
| damagePost | LivingDamageEvent.Post | SERVER | EntityType | false | |
| death | LivingDeathEvent | SERVER | EntityType | true | |
| drops | LivingDropsEvent | SERVER | EntityType | true | fabric：LivingDropsEventJS（drops 恒空列表，已知数据面差异） |
| finalizeSpawn | FinalizeSpawnEvent | SERVER | EntityType | true | fabric：MobFinalizeSpawnEventJS（通知型，不可取消——能力差异） |
| tickPre / tickPost | EntityTickEvent.Pre/Post | SERVER | EntityType | false | 双逻辑侧过滤 |
| joinLevel | EntityJoinLevelEvent | SERVER | EntityType | true | 双逻辑侧过滤 |
| leaveLevel | EntityLeaveLevelEvent | SERVER | EntityType | false | 双逻辑侧过滤 |
| useItemStarted | LivingEntityUseItemEvent.Start | SERVER | Item | true | |
| useItemStopped | LivingEntityUseItemEvent.Stop | SERVER | Item | false | |
| useItemFinished | LivingEntityUseItemEvent.Finish | SERVER | Item | false | |
| useItemTick | LivingEntityUseItemEvent.Tick | SERVER | Item | false | fabric 无 useItem* 四成员（显式缺席） |

## 与票 33 基线的差异记录（不修基线，owner 33/34）

1. **fabric BlockEvents/LevelEvents 低记**：`event-surface-domains.txt` 只驱动
   `registerEvents` 钩子，fabric 侧由 `NekoJSFabricMod.onInitialize` 早期 Adapter 类初始化
   （`FabricBlockEventBindings`/`V2`、`FabricLevelEventBindingsV2` 的 `GROUP.add`）追加的
   10 个 BlockEvents 成员与 5 个 LevelEvents 成员不在其输入内。真实运行面见上表；
   基线再生成归票 33/34。
2. 其余六族两视图一致（Item/Player/Command/Capability/Goal/Entity）。
