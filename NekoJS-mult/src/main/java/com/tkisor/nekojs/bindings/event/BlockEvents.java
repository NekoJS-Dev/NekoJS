package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import net.minecraft.world.level.block.Block;
import java.util.function.Function;

/**
 * 脚本命名空间 {@code BlockEvents} 的**规范声明**——住在 `src-common/`，被根分支的三个
 * NeoForge 节点与 fabric 分支同时挂载，因此这些总线在所有加载器上是同一个对象。
 *
 * <p>本层规矩（`src-common/` 全体适用）：
 * <ul>
 *   <li>只允许原版 MC 类型与 NekoJS 自己的类型，**不得** import
 *       {@code net.neoforged} / {@code net.minecraftforge} / {@code net.fabricmc}；</li>
 *   <li>**不得写 `//?` 守卫**——stonecutter 只预处理分支的 `src` 树，这个源根不经预处理，
 *       写了守卫会原样进编译。需要版本分叉的事件请留在加载器侧。</li>
 * </ul>
 *
 * <p>加载器侧只负责**把原生事件转换成这里的载荷**：
 * NeoForge 见 {@link NeoForgeBlockEvents}，Fabric 见 {@code FabricBlockEventBindings}。
 * 加载器专属的事件依然声明在加载器侧，并注册进本类的 {@link #GROUP}——脚本命名空间
 * 因此仍是完整的一个。哪些事件能进本层，取决于**投递方式**而不只是字段类型：
 * NekoJS 自己的 {@code RandomTickEvent}/{@code BlockEntityTickEvent} 继承
 * {@code net.neoforged.bus.api.Event}（经 NF 总线投递），所以它们按定义就不中立，
 * 留在 {@link NeoForgeBlockEvents}；要让它们中立得先改投递路径（mixin 直接 post 到
 * {@code EventBusJS}，不再过 NF 总线）——那是独立的一步。
 */
public final class BlockEvents {

    private BlockEvents() {}

    /** 脚本命名空间。{@code EventGroup.of} 每次都新建实例，所以只能在这里创建一次。 */
    public static final EventGroup GROUP = EventGroup.of("BlockEvents");

    /** 方块被破坏（脚本 {@code BlockEvents.broken}），载荷为加载器中立的 {@link BlockBrokenEventJS}。 */
    public static final EventBusJS<BlockBrokenEventJS, Block> BROKEN =
            GROUP.server("broken", BlockBrokenEventJS.class, dispatchByBlock(BlockBrokenEventJS::getBlock));

    static <T> DispatchKey<T, Block> dispatchByBlock(Function<T, Block> toKey) {
        return EventBusFactory.createDispatchKey(Block.class, toKey);
    }
}
