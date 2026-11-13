package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral payload for the event where a fluid hardens into a block
 * ({@code BlockEvents.fluidPlaced}).
 *
 * <p><b>26.x coverage (verified against the patch sources)</b>: in NeoForge 26.1.2,
 * {@code BlockEvent.FluidPlaceBlockEvent} is only posted from three places in
 * {@code LavaFluid} (the stone conversion in {@code spreadTo} plus the two fire spreads in
 * {@code randomTick}). It does <b>not</b> cover obsidian, cobblestone or basalt, because those
 * conversions live in {@code LiquidBlock#shouldSpreadLiquid}, where NeoForge has no patch
 * point. On fabric, {@code MixinLiquidBlock} + {@code MixinLavaFluid} cover the
 * obsidian/cobblestone/basalt conversion points in LiquidBlock plus the stone point in
 * LavaFluid.spreadTo, so fabric covers a superset of NeoForge except the two fire-spread
 * places, which have no scripting value.
 *
 * <p>Cancellable: when a listener returns {@code true}, the fluid does not harden this time
 * (lava keeps flowing and spreading). Fires on the server only (the binding filters the
 * client).
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
