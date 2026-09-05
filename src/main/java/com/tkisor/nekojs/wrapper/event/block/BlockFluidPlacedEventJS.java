package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 流体硬化成方块事件（{@code BlockEvents.fluidPlaced}）的**加载器中立**载荷。
 *
 * <p><b>26.x 覆盖范围事实（补丁源码实证）</b>：NeoForge 26.1.2 的
 * {@code BlockEvent.FluidPlaceBlockEvent} 只从 {@code LavaFluid} 三处 post
 * （{@code spreadTo} 的岩浆遇水成石 + {@code randomTick} 两处火焰蔓延），
 * <b>不覆盖</b>黑曜石/圆石/玄武岩（那三处在 {@code LiquidBlock#shouldSpreadLiquid}
 * 里，NF 无补丁点）。fabric 侧 {@code MixinLiquidBlock} + {@code MixinLavaFluid}
 * 覆盖：LiquidBlock 的黑曜石/圆石/玄武岩转换点 + LavaFluid.spreadTo 成石点
 * ——fabric 是 NF 覆盖范围的超集（缺火焰蔓延两处，无脚本价值），语义差异
 * 记入移植台账。
 *
 * <p>可取消：监听器返回 {@code true} → 流体本次不硬化（岩浆继续流动/扩散）。
 * 仅服务端触发（绑定处过滤客户端）。
 */
@Doc("Fired when a fluid hardens into a block (obsidian, cobblestone, basalt, stone).")
@Doc("Return true to prevent the conversion (the fluid keeps flowing).")
@Getter
public class BlockFluidPlacedEventJS {

    @Doc("The level the conversion happens in.")
    private final LevelAccessor level;

    @Doc("Position of the fluid that hardens.")
    private final BlockPos pos;

    @Doc("The block state the fluid converts into.")
    private final BlockState newState;

    @Doc("The fluid block state being replaced.")
    private final BlockState oldState;

    public BlockFluidPlacedEventJS(LevelAccessor level, BlockPos pos,
                                   BlockState newState, BlockState oldState) {
        this.level = level;
        this.pos = pos;
        this.newState = newState;
        this.oldState = oldState;
    }

    @Doc("The block the fluid hardens into.")
    public Block getBlock() {
        return newState.getBlock();
    }
}
