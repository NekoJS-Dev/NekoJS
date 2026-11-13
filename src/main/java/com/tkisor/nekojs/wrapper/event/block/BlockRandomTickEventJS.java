package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral payload for the block random tick event ({@code BlockEvents.randomTick}).
 *
 * <p>The member names match the NeoForge native payload {@code RandomTickEvent} (an NF Event
 * subclass): level/pos/state/random. The script surface is the same on both loaders, but each
 * loader owns its payload class: NeoForge posts through its own bus, fabric posts straight to
 * {@code EventBusJS} from the mixin.
 *
 * <p>Fires at the HEAD of {@code BlockBehaviour#randomTick}, the same point on both loaders.
 * Vanilla only calls it for blocks where {@code isRandomlyTicking()} is true, and blocks whose
 * subclass overrides the method never reach the base implementation, which matches the
 * existing mixin on the NeoForge side. Not cancellable. High-frequency channel: no event
 * object is built when nothing is listening.
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
