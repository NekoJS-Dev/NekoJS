package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块随机 tick 事件（{@code BlockEvents.randomTick}）的**加载器中立**载荷。
 *
 * <p>成员名与 NeoForge 侧原生载荷 {@code RandomTickEvent}（NF Event 子类）
 * 同形（level/pos/state/random）——两加载器脚本面一致，载荷类各自持有
 * （NF 侧经 NF 总线投递，fabric 侧 mixin 直投 {@code EventBusJS}，孪生决策见
 * 移植台账第 14 批）。
 *
 * <p>触发点：{@code BlockBehaviour#randomTick} HEAD（两加载器同点）。原版只对
 * {@code isRandomlyTicking()} 的方块调用，且被子类覆写的方块不经过基类实现
 * ——语义与 NeoForge 侧既有 mixin 一致。不可取消。高频通道：无监听器时零
 * 事件对象。
 */
@Doc("Fired when a block gets a random tick (crop growth, leaf decay, etc).")
@Getter
public class BlockRandomTickEventJS {

    @Doc("The server level the block ticks in.")
    private final ServerLevel level;

    @Doc("Position of the ticking block.")
    private final BlockPos pos;

    @Doc("Block state of the ticking block.")
    private final BlockState state;

    @Doc("The level's random source.")
    private final RandomSource random;

    public BlockRandomTickEventJS(ServerLevel level, BlockPos pos,
                                  BlockState state, RandomSource random) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.random = random;
    }

    @Doc("The ticking block.")
    public Block getBlock() {
        return state.getBlock();
    }
}
