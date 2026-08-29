package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块被破坏事件（{@code BlockEvents.broken}）的**加载器中立**载荷。
 *
 * <p>B3 SPI 样板：这个类只用原版 MC 类型，所以三个 NeoForge 节点、fabric、forge 都能共用。
 * 各加载器在自己的绑定点把原生事件转换成本类（NeoForge 侧见
 * {@code BlockEvents.FORGE_BRIDGE} 的 {@code bindTransformed}）。
 *
 * <p>为什么值得这么做：原先 {@code BlockEvents.BROKEN} 直接把 NeoForge 的事件类
 * 暴露给脚本（26.x 是 {@code BreakBlockEvent}、1.21.1 是 {@code BlockEvent.BreakEvent}），
 * 于是 ① 脚本 API 随 MC 版本变形 ② fabric 根本没有对应类 ③ 公共 API 声明处需要版本守卫。
 * 换成中立载荷后三个问题一起消失。
 *
 * <p>取消语义：脚本 {@code return false} 时由绑定点回写到原生事件（本类不持有原生事件引用，
 * 避免把加载器类型再泄回来）。
 *
 * <p>**刻意不含掉落经验**：1.21.1 的 {@code BlockEvent.BreakEvent} 有 {@code getExpToDrop()}，
 * 26.x 的 {@code BreakBlockEvent} **删掉了**这个 API。设计中立载荷会迫使这类**数据层面**的
 * 真实版本差异浮出水面——与其在某一版伪造 0，不如不暴露（要用就走版本守卫的扩展面）。
 */
@Doc("Fired when a block is broken (BlockEvents.broken). Listeners are dispatched by block id.")
@Doc("Return false to cancel the break.")
@Getter
public class BlockBrokenEventJS {

    @Doc("The level the block was broken in.")
    private final LevelAccessor level;

    @Doc("Position of the broken block.")
    private final BlockPos pos;

    @Doc("Block state before the break.")
    private final BlockState state;

    @Doc("The player breaking the block.")
    private final Player player;

    public BlockBrokenEventJS(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.player = player;
    }

    @Doc("The block that was broken.")
    public net.minecraft.world.level.block.Block getBlock() {
        return state.getBlock();
    }
}
