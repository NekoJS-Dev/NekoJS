package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.wrapper.event.block.BlockEntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFarmlandTrampleEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFluidPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockNeighborNotifyEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPortalSpawnEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRandomTickEventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;

/**
 * 方块事件面的 fabric 桥 v2：portalSpawn / neighborNotify / farmlandTrample /
 * placed / entityPlaced / fluidPlaced / randomTick / blockEntityTick
 * （NeoForge 侧由 {@code NeoForgeBlockEvents} 直传原生事件，fabric 无对应
 * fabric-api 回调，按任务批次以 mixin 接入）。
 *
 * <p>总线声明在**本类**、注册进共享树 {@link BlockEvents} 的 {@code GROUP}
 * （与 broken 及 v1 的 rightClicked/leftClicked 同名合并），按 Block 分发
 * （与 NeoForge 侧 {@code dispatchByBlock} 的 {@code state.getBlock()} 键一致）：
 * <ul>
 *   <li>{@code portalSpawn}——可取消（取消则传送门方块不生成）；</li>
 *   <li>{@code neighborNotify}——可取消（取消则本次邻居通知不送达）；</li>
 *   <li>{@code farmlandTrample}——可取消（取消则耕地不塌）。</li>
 * </ul>
 *
 * <p><b>关键初始化纪律</b>：与 {@code FabricLevelEventBindingsV2} 相同——
 * {@code EventGroup} 在插件引导完成后冻结，本类总线的 {@code GROUP.add} 必须
 * 发生在冻结之前（{@code NekoJSFabricMod.onInitialize} 早期
 * {@code FabricBlockEventBindings.register()} 内追加一行 {@code bootstrap()}）。
 */
public final class FabricBlockEventBindingsV2 {

    private FabricBlockEventBindingsV2() {}

    /** 传送门生成前（server 脚本，按传送门方块 dispatch；可取消）。 */
    public static final EventBusJS<BlockPortalSpawnEventJS, Block> PORTAL_SPAWN =
            BlockEvents.GROUP.add("portalSpawn", ScriptType.SERVER, EventBusJS.of(
                    BlockPortalSpawnEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPortalSpawnEventJS::getBlock)));

    /** 方块邻居通知（server 脚本，按被通知方块 dispatch；可取消）。 */
    public static final EventBusJS<BlockNeighborNotifyEventJS, Block> NEIGHBOR_NOTIFY =
            BlockEvents.GROUP.add("neighborNotify", ScriptType.SERVER, EventBusJS.of(
                    BlockNeighborNotifyEventJS.class, true,
                    DispatchKey.of(Block.class, BlockNeighborNotifyEventJS::getBlock)));

    /** 耕地被踩塌（server 脚本，按耕地方块 dispatch；可取消）。 */
    public static final EventBusJS<BlockFarmlandTrampleEventJS, Block> FARMLAND_TRAMPLE =
            BlockEvents.GROUP.add("farmlandTrample", ScriptType.SERVER, EventBusJS.of(
                    BlockFarmlandTrampleEventJS.class, true,
                    DispatchKey.of(Block.class, BlockFarmlandTrampleEventJS::getBlock)));

    /**
     * 方块放置（server 脚本；可取消）。26.x 事实（补丁源码实证）：NeoForge 侧
     * placed/entityPlaced 同绑 {@code BlockEvent.EntityPlaceEvent}，其唯一 post 点
     * 是末影人放置——fabric 侧 {@code MixinEndermanLeaveBlockGoal} 同构，一次放置
     * 双总线投递。分发键取被放置方块（NF 侧取快照脚下方块，差异记入载荷 javadoc）。
     */
    public static final EventBusJS<BlockPlacedEventJS, Block> PLACED =
            BlockEvents.GROUP.add("placed", ScriptType.SERVER, EventBusJS.of(
                    BlockPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPlacedEventJS::getBlock)));

    /** 实体放置（server 脚本；可取消）——与 {@link #PLACED} 同点双投递。 */
    public static final EventBusJS<BlockPlacedEventJS, Block> ENTITY_PLACED =
            BlockEvents.GROUP.add("entityPlaced", ScriptType.SERVER, EventBusJS.of(
                    BlockPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPlacedEventJS::getBlock)));

    /**
     * 流体硬化成方块（server 脚本；可取消）。覆盖 LiquidBlock 的黑曜石/圆石/玄武岩
     * 转换点 + LavaFluid.spreadTo 成石点——是 NF 26.1.2 覆盖范围的超集（NF 不含
     * LiquidBlock 三处、多火焰蔓延两处），语义差异记入移植台账。
     */
    public static final EventBusJS<BlockFluidPlacedEventJS, Block> FLUID_PLACED =
            BlockEvents.GROUP.add("fluidPlaced", ScriptType.SERVER, EventBusJS.of(
                    BlockFluidPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockFluidPlacedEventJS::getBlock)));

    /** 方块随机 tick（server 脚本，按方块 dispatch；不可取消；高频，无监听器短路）。 */
    public static final EventBusJS<BlockRandomTickEventJS, Block> RANDOM_TICK =
            BlockEvents.GROUP.server("randomTick", BlockRandomTickEventJS.class,
                    DispatchKey.of(Block.class, BlockRandomTickEventJS::getBlock));

    /** 方块实体 tick（server 脚本，按 BlockEntityType dispatch；不可取消；高频）。 */
    public static final EventBusJS<BlockEntityTickEventJS, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            BlockEvents.GROUP.server("blockEntityTick", BlockEntityTickEventJS.class,
                    DispatchKey.of(BlockEntityType.class, BlockEntityTickEventJS::getType));

    /** 触发本类初始化把总线注册进 {@code BlockEvents.GROUP}（详见类注释的冻结纪律）。 */
    public static void bootstrap() {
        // 空方法体：访问任一总线常量即完成类初始化
    }

    /**
     * 传送门生成前：{@code MixinPortalShape} 在
     * {@code PortalShape#createPortalBlocks(LevelAccessor)} 的 HEAD 调用。
     *
     * @return true 表示监听器取消了传送门生成（mixin 应 {@code ci.cancel()}）
     */
    public static boolean postPortalSpawn(LevelAccessor level, BlockPos pos, BlockState state,
                                          PortalShape portalSize) {
        if (!PORTAL_SPAWN.hasListeners()) {
            return false;
        }
        return PORTAL_SPAWN.post(new BlockPortalSpawnEventJS(level, pos, state, portalSize),
                state.getBlock());
    }

    /**
     * 方块邻居通知：{@code MixinNeighborUpdater} 在
     * {@code NeighborUpdater#executeUpdate(Level, BlockState, BlockPos, Block,
     * Orientation, boolean)} 的 HEAD 调用（26.x 单邻居变更与 updateNeighborsAt
     * 都收敛到此汇点，javap 实证）。
     *
     * @return true 表示监听器取消了本次通知（mixin 应 {@code ci.cancel()}）
     */
    public static boolean postNeighborNotify(Level level, BlockState state, BlockPos pos,
                                             Block sourceBlock, Orientation orientation,
                                             boolean forceRedstone) {
        if (!NEIGHBOR_NOTIFY.hasListeners()) {
            return false;
        }
        return NEIGHBOR_NOTIFY.post(
                new BlockNeighborNotifyEventJS(level, pos, state, sourceBlock, orientation,
                        forceRedstone),
                state.getBlock());
    }

    /**
     * 耕地被踩塌：{@code MixinFarmlandBlock} 在
     * {@code FarmlandBlock#fallOn} 的 {@code turnToDirt} INVOKE 点调用
     * （原版判定通过后才触达，javap 实证）。
     *
     * @return true 表示监听器取消了踩塌（mixin 应 {@code ci.cancel()}，
     *         {@code turnToDirt} 不再执行）
     */
    public static boolean postFarmlandTrample(Level level, BlockPos pos, BlockState state,
                                              float fallDistance, Entity entity) {
        if (!FARMLAND_TRAMPLE.hasListeners()) {
            return false;
        }
        return FARMLAND_TRAMPLE.post(
                new BlockFarmlandTrampleEventJS(level, pos, state, fallDistance, entity),
                state.getBlock());
    }

    /**
     * 方块放置：{@code MixinEndermanLeaveBlockGoal} 在 {@code canPlaceBlock} HEAD 调用
     * （javap 实证：NF 26.1.2 的 {@code EntityPlaceEvent} 唯一 post 点同位次）。
     * 一次放置向 placed 与 entityPlaced 双总线投递（与 NF 侧双绑定同构），共用同一
     * 只读载荷实例。
     *
     * @return true 表示任一总线的监听器取消了放置（mixin 应
     *         {@code cir.setReturnValue(false)}，放置不发生、实体保留方块）
     */
    public static boolean postPlaced(Level level, BlockPos pos, BlockState state,
                                     BlockState placedAgainst, Entity entity) {
        if (!PLACED.hasListeners() && !ENTITY_PLACED.hasListeners()) {
            return false;
        }
        BlockPlacedEventJS payload = new BlockPlacedEventJS(level, pos, state, placedAgainst, entity);
        // 不短路：两总线都要收到事件（NF 侧同一事件对象投两条绑定，取消态共享）
        boolean cancelled = PLACED.post(payload, state.getBlock());
        cancelled |= ENTITY_PLACED.post(payload, state.getBlock());
        return cancelled;
    }

    /**
     * 流体硬化成方块：{@code MixinLiquidBlock}（黑曜石/圆石/玄武岩转换点）与
     * {@code MixinLavaFluid}（spreadTo 成石点）在各自 setBlock INVOKE 点调用。
     *
     * @return true 表示监听器取消了硬化（mixin 侧语义：流体不转化、继续流动）
     */
    public static boolean postFluidPlaced(LevelAccessor level, BlockPos pos,
                                          BlockState newState, BlockState oldState) {
        if (!FLUID_PLACED.hasListeners()) {
            return false;
        }
        return FLUID_PLACED.post(new BlockFluidPlacedEventJS(level, pos, newState, oldState),
                newState.getBlock());
    }

    /**
     * 方块随机 tick：{@code MixinBlockBehaviourRandomTick} 在
     * {@code BlockBehaviour#randomTick} HEAD 调用（与 NeoForge 侧既有 mixin 同点孪生）。
     */
    public static void postRandomTick(ServerLevel level, BlockPos pos, BlockState state,
                                      RandomSource random) {
        if (!RANDOM_TICK.hasListeners()) {
            return;
        }
        RANDOM_TICK.post(new BlockRandomTickEventJS(level, pos, state, random), state.getBlock());
    }

    /**
     * 方块实体 tick：{@code MixinLevelChunkBoundTickingBlockEntity} 在
     * {@code LevelChunk$BoundTickingBlockEntity#tick} HEAD 调用（失效实体由 mixin 过滤）。
     */
    public static void postBlockEntityTick(BlockEntity blockEntity) {
        if (!BLOCK_ENTITY_TICK.hasListeners()) {
            return;
        }
        BLOCK_ENTITY_TICK.post(new BlockEntityTickEventJS(blockEntity), blockEntity.getType());
    }
}
