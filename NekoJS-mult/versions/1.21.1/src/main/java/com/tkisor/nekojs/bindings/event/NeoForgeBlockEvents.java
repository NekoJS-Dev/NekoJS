// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

/**
 * {@code BlockEvents} 的 **NeoForge 适配层**：把原生事件接到
 * {@link BlockEvents} 的中立总线上，并声明**只有 NeoForge 才有**的那些事件。
 *
 * <p>为什么分成两个类：跨加载器共用的总线声明必须住在无加载器依赖的 `src-common/`
 * （见 {@link BlockEvents}），而 {@code BlockEvent.PortalSpawnEvent} 这类没有 fabric
 * 对应物的事件本就该留在加载器侧。两边的总线都注册进 {@link BlockEvents#GROUP}，
 * 所以脚本看到的仍是**一个完整的 {@code BlockEvents} 命名空间**。
 *
 * <p>⚠️ 初始化是显式的：本类的绑定写在 {@link #FORGE_BRIDGE} 常量里，只有类被首次访问
 * 时才生效。以前靠 {@code NekoJSCorePlugin} 访问 {@code BlockEvents.GROUP} 顺带触发；
 * 现在 GROUP 搬去共享层，那条路断了——**必须显式调用 {@link #bootstrap()}**，
 * 否则 NeoForge 侧事件会静默不触发（这类"类初始化副作用"是本次重构里最危险的一环）。
 */
public final class NeoForgeBlockEvents {

    private NeoForgeBlockEvents() {}

    public static final EventBusJS<BlockEvent.EntityPlaceEvent, Block> ENTITY_PLACED =
            BlockEvents.GROUP.server("entityPlaced", BlockEvent.EntityPlaceEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.EntityMultiPlaceEvent, Block> ENTITY_MULTI_PLACED =
            BlockEvents.GROUP.server("entityMultiPlaced", BlockEvent.EntityMultiPlaceEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.NeighborNotifyEvent, Block> NEIGHBOR_NOTIFY =
            BlockEvents.GROUP.server("neighborNotify", BlockEvent.NeighborNotifyEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.FluidPlaceBlockEvent, Block> FLUID_PLACED =
            BlockEvents.GROUP.server("fluidPlaced", BlockEvent.FluidPlaceBlockEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.FarmlandTrampleEvent, Block> FARMLAND_TRAMPLE =
            BlockEvents.GROUP.server("farmlandTrample", BlockEvent.FarmlandTrampleEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.PortalSpawnEvent, Block> PORTAL_SPAWN =
            BlockEvents.GROUP.server("portalSpawn", BlockEvent.PortalSpawnEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.BlockToolModificationEvent, Block> TOOL_MODIFICATION =
            BlockEvents.GROUP.server("toolModification", BlockEvent.BlockToolModificationEvent.class, dispatchByBlock());

    public static final EventBusJS<PlayerInteractEvent.RightClickBlock, Block> RIGHT_CLICKED =
            BlockEvents.GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class,
                    BlockEvents.dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));
    public static final EventBusJS<BlockEvent.EntityPlaceEvent, Block> PLACED =
            BlockEvents.GROUP.server("placed", BlockEvent.EntityPlaceEvent.class, dispatchByBlock());
    public static final EventBusJS<PlayerInteractEvent.LeftClickBlock, Block> LEFT_CLICKED =
            BlockEvents.GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class,
                    BlockEvents.dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));

    /**
     * 方块随机 tick（仅对 {@code isRandomlyTicking()} 的方块触发；由 mixin 注入，按 Block 分发）。
     *
     * <p>留在 NF 适配层的原因见 {@link BlockEvents} 头注释：{@link RandomTickEvent} 继承
     * NeoForge 的 {@code Event}、经 NF 总线投递，按定义不是中立事件。
     */
    public static final EventBusJS<RandomTickEvent, Block> RANDOM_TICK =
            BlockEvents.GROUP.server("randomTick", RandomTickEvent.class,
                    BlockEvents.dispatchByBlock(RandomTickEvent::getBlock));

    /** 方块实体 tick（所有有 ticker 的方块实体，按 BlockEntityType 分发）。 */
    public static final EventBusJS<BlockEntityTickEvent, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            BlockEvents.GROUP.server("blockEntityTick", BlockEntityTickEvent.class,
                    DispatchKey.of(BlockEntityType.class, BlockEntityTickEvent::getType));

    /**
     * 运行时方块属性修改（server 脚本）：每次服务器启动（about-to-start）与
     * {@code /nekojs reload server} 时由平台侧手动 post（快照恢复模型，见
     * {@link BlockModificationEventJS}），不挂 NeoForge 总线（同
     * {@code ItemEvents.MODIFICATION} 的 posted-object 模式）。
     */

    private static <T extends BlockEvent> DispatchKey<T, Block> dispatchByBlock() {
        return BlockEvents.dispatchByBlock(event -> event.getState().getBlock());
    }

    public static final EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        // 中立总线的绑定点：这里是加载器代码，写加载器类型与版本守卫是恰当的
        .bindTransformed(
                BlockEvents.BROKEN,
                (BreakEvent event) -> new BlockBrokenEventJS(
                        event.getLevel(), event.getPos(), event.getState(), event.getPlayer()),
                BreakEvent.class)
        .bind(ENTITY_PLACED)
        .bind(ENTITY_MULTI_PLACED)
        .bind(NEIGHBOR_NOTIFY)
        .bind(FLUID_PLACED)
        .bind(FARMLAND_TRAMPLE)
        .bind(PORTAL_SPAWN)
        .bind(TOOL_MODIFICATION)
        // PlayerInteract 双逻辑侧触发：SERVER 总线只投递服务端实例（客户端交互在 Render 线程）
        .bind(RIGHT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(PLACED)
        .bind(LEFT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(RANDOM_TICK)
        .bind(BLOCK_ENTITY_TICK);

    /** 触发本类初始化，从而登记上面的总线与 {@link #FORGE_BRIDGE} 绑定。 */
    public static void bootstrap() {
        // 访问任一常量即可完成类初始化；留空方法体让调用点语义清晰
    }
}
