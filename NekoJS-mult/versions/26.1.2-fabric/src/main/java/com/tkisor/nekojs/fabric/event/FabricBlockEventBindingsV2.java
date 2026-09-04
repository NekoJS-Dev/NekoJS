package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.wrapper.event.block.BlockFarmlandTrampleEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockNeighborNotifyEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPortalSpawnEventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.redstone.Orientation;

/**
 * 方块事件面的 fabric 桥 v2：portalSpawn / neighborNotify / farmlandTrample
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
}
