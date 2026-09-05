# Spec: Fabric 平台 BlockEvents 放置/Tick 簇与 PlayerEvents.inventoryChanged 移植

> **Triage Label**: `ready-for-agent`（已实施，2026-09-05）
> **Scope**: 26.1.2-fabric 及共享层事件中立化（P1 剩余事件面，移植台账第 14 批）
> **修订说明**: 初版 spec 的注入点断言未经实证，评审后全部重写为 javap/补丁源码实证版；
> 实证过程中推翻了两处初版假设（entityMultiPlaced 死事件、fluidPlaced 覆盖面），见下。

---

## Problem Statement

Fabric 节点脚本相对 NeoForge 缺失以下事件面（NeoForge 侧均为已有总线）：

- 方块放置：`BlockEvents.placed` / `entityPlaced` / `entityMultiPlaced`
- 流体硬化：`BlockEvents.fluidPlaced`
- 高频 tick：`BlockEvents.randomTick` / `blockEntityTick`
- 物品栏变动：`PlayerEvents.inventoryChanged`（共享树监听器已有，fabric 无挂载点）

26.x 原生 fabric-api 无对应回调（`PlayerBlockPlaceEvents` 已被 fabric-api 移除），
需要 mixin 面。

## Solution

沿用第 6/13 批确立的三层模式：**共享树中立载荷**（`wrapper/event/block`）+
**fabric 孪生总线**（`FabricBlockEventBindingsV2` / 孪生 `PlayerEvents`）+
**fabric mixin**（单 L INVOKE 形式、全描述符、宿主参数 handler——第 13 批七条经验）。

### 实证覆盖表（初版 spec 的三处修正）

| 事件 | 初版假设 | 实证事实（javap + NF 补丁源码） | 最终实现 |
|---|---|---|---|
| placed / entityPlaced | `BlockItem#placeBlock` 单缝全覆盖 | NF 26.1.2 补丁中 `EntityPlaceEvent` **唯一 post 点 = 末影人放置**（`EndermanLeaveBlockGoal` 内 `EventHooks.onBlockPlace`）；BlockItem/FallingBlock/Wither 无挂点 | 末影人 `canPlaceBlock` HEAD（全部上下文在声明参数里，无需 LocalCapture），一次放置双总线投递，两平台语义一致 |
| entityMultiPlaced | mixin 实现 | NF 补丁 `EventHooks.onMultiBlockPlace` **零调用点**——NF 26.x 上就是死事件 | 不实现，台账记"NF 死事件"；等 NF 侧复活时再对齐 |
| fluidPlaced | LiquidBlock 传播逻辑 | NF 26.1.2 只挂 LavaFluid 三处（spreadTo 成石 + randomTick 火焰蔓延×2），**不覆盖**黑曜石/圆石/玄武岩（在 `LiquidBlock#shouldSpreadLiquid`） | fabric 覆盖 LiquidBlock 两处转换点（ordinal 0/1）+ LavaFluid.spreadTo——是 NF 的超集（缺火焰蔓延，无脚本价值） |

### 关键缝的 before/after

**末影人放置（fabric 挂点选择）**：

```java
// 原版 tick()（26.1.2，简化）——事件上下文全是局部变量：
BlockState carried = this.enderman.getCarriedBlock();
if (this.canPlaceBlock(level, pos, carried, targetState, belowState, below)
        && !EventHooks.onBlockPlace(enderman, BlockSnapshot.create(..., below), UP)) {  // ← NF post 点
    level.setBlock(pos, carried, 3);
}

// fabric 挂点：不追 tick 的 INVOKE+LocalCapture（局部变量脆），
// 挂 canPlaceBlock HEAD——上下文全部在声明参数里：
@Inject(method = "canPlaceBlock(...)Z", at = @At("HEAD"), cancellable = true)
private void nekojs$onEndermanPlace(Level level, BlockPos pos, BlockState carried, ..., CIR<Boolean> cir) {
    if (FabricBlockEventBindingsV2.postPlaced(level, pos, carried, belowState, enderman)) {
        cir.setReturnValue(false);   // ← && 短路，与 NF 取消语义一致（保留手中方块）
    }
}
```

**inventoryChanged（初版开错药方，改 fabric-api 回调）**：

```java
// NeoForge 侧现状：PlayerEventListener 在 PlayerLoggedInEvent + PlayerEvent.Clone 挂载
// fabric 等价物 = fabric-api 现成回调，零 mixin：
ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
        InventoryChangeListener.getOrCreate(handler.player));      // ≙ PlayerLoggedInEvent
ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
        InventoryChangeListener.getOrCreate(newPlayer));           // ≙ PlayerEvent.Clone
```

共享树 `InventoryChangeListener` 本就是加载器中立的（只依赖 vanilla
`ContainerListener`）——去掉整文件 `//? if neoforge` 守卫即复用。

---

## User Stories

1. As a script author, I want `BlockEvents.placed`/`entityPlaced` to fire when an enderman places a block, so that I can monitor or block enderman griefing.
2. As a script author, I want to cancel placement by returning true, so that the enderman keeps the carried block and the world is untouched.
3. As a script author, I want `BlockEvents.fluidPlaced` to fire with the new block state, so that I can track obsidian/cobblestone/basalt/stone formation.
4. As a script author, I want to cancel `fluidPlaced`, so that lava does not harden in protected zones and keeps flowing.
5. As a script author, I want `BlockEvents.randomTick('minecraft:wheat', ...)`, so that I can accelerate or gate crop growth.
6. As a performance-conscious packmaker, I want randomTick/blockEntityTick to zero-allocate with no listeners, so that server TPS is preserved.
7. As a script author, I want `BlockEvents.blockEntityTick` filtered by block entity type, so that I only receive events for my machines.
8. As a script author, I want `PlayerEvents.inventoryChanged('minecraft:diamond', ...)`, so that quests trigger the moment the item enters the inventory.
9. As a server admin, I want inventory listeners to use WeakHashMap caching, so that disconnected players never leak.
10. As a cross-platform author, I want identical bus names and dispatch keys on both loaders, so that scripts port without changes (documented divergences excepted).

## Implementation Decisions

- **载荷**（共享树，vanilla 类型 + `@Getter @Doc`，同 BlockRightClickEventJS 先例）：
  `BlockPlacedEventJS`（level/pos/state/placedAgainst/entity；placed+entityPlaced 共用，
  一次放置双投递共用同一只读载荷）、`BlockFluidPlacedEventJS`（level/pos/newState/oldState）、
  `BlockRandomTickEventJS`（level/pos/state/random，与 NF 原生载荷同形）、
  `BlockEntityTickEventJS`（blockEntity，按 BlockEntityType 分发）。
- **孪生决策**（randomTick/blockEntityTick 派发路径）：NF 侧**不动**（继续经 NF 总线投
  NF Event 子类），fabric 侧孪生同名总线直投 `EventBusJS`——脚本面同形、实现面解耦，
  避免触碰 NF 活路径。这与 rightClicked 的两载荷先例一致。
- **mixin 五件**：`MixinEndermanLeaveBlockGoal`（canPlaceBlock HEAD，CIR<Boolean>）、
  `MixinLiquidBlock`（shouldSpreadLiquid 两处 INVOKE ordinal 0/1，取消 =
  `setReturnValue(true)` = 不硬化继续流动）、`MixinLavaFluid`（spreadTo INVOKE，取消 =
  整方法早退，与 NF"参数换旧状态"有细微差异，台账记录）、
  `MixinBlockBehaviourRandomTick` / `MixinLevelChunkBoundTickingBlockEntity`（HEAD，
  与 NF 同名 mixin 逐行孪生）。
- **取消语义差异（已记录）**：fluidPlaced 的 NF 取消 = setBlock 换旧状态（fizz 照放）；
  fabric LiquidBlock 取消 = 方法早退返回 true；fabric spreadTo 取消 = 整方法跳过。
  净效果一致（不硬化），中间观感（fizz 音效）有差。
- **分发键差异（已记录）**：placed/entityPlaced NF 侧 dispatch 键取事件快照（脚下方块）
  ——NF 原生事件形态；fabric 侧取被放置方块（合理语义）。跨平台脚本按放置物过滤以
  fabric 为准。
- **mixin 登记纪律**：全部进 `nekojs-fabric.mixins.json`（第 13 批曾漏登记 5 个，本批
  核对过）；`FabricBlockEventBindingsV2` 常量初始化随既有 bootstrap 链生效。

## Testing Decisions

- **判据**：只验外部行为——mixin 注入是否成立（PREPARE/APPLY 不炸）、启动是否干净、
  总线是否有监听器短路；不针对字节码偏移做断言。
- **验证集**（实际执行）：`:26.1.2-fabric:compileJava` + `runServer` 无头冒烟
  （`Done (1.043s)`、零 Critical injection failure；启动期类加载覆盖 LiquidBlock/
  LavaFluid/BlockBehaviour/BoundTickingBlockEntity 四个 mixin，末影人 goal 属实体加载期，
  由 `defaultRequire=1` 保证失败必崩）+ 五节点编译（common/26.1.2/26.1.2-fabric/
  26.2.0/**1.21.1**）+ guardLint（320 文件 0 警告）。
- **顺带修复**：1.21.1 节点因第 13 批 `BlockNeighborNotifyEventJS`（Orientation 为
  26.x-only 类型）整树编译红——补 `//? if >=26` 文件守卫后恢复绿。
- **先验**：第 13 批 runServer 冒烟迭代法（smoke 日志 grep `Critical injection|FAILED during`）。

## Out of Scope

- FluidBuilder / 流体配方面（独立架构重设计）。
- 客户端放置预测与网络同步。
- entityMultiPlaced 的 fabric 实现（NF 死事件，无对齐目标）。
- LavaFluid.randomTick 火焰蔓延两处的 fabric 覆盖（无脚本价值，台账记录）。
- common-api-processor 的 fabric 平台模型。

## Further Notes

- 所有修改仅本地提交，不 push；用户 9 个 WIP 文件未触碰。
- 修订记录：初版（本文件第一版）的"BlockItem#placeBlock 单缝覆盖 entityPlaced"、
  "统一撤回"、"切入容器初始化挂监听器"三处断言在实证核查后推翻/简化， replaced by
  上文覆盖表与 before/after。
