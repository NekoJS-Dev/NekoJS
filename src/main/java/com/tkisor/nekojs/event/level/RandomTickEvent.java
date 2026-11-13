//? if neoforge {
package com.tkisor.nekojs.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

/**
 * Block random tick event ({@code BlockEvents.randomTick}).
 *
 * <p>Vanilla has no matching NeoForge event, so {@code BlockBehaviourMixin} injects at the
 * HEAD of {@code BlockBehaviour.BlockStateBase.randomTick} and posts to
 * {@code NeoForge.EVENT_BUS}. It fires only for blocks whose {@code isRandomlyTicking()}
 * returns true (matching vanilla / KubeJS semantics).
 */
public class RandomTickEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final RandomSource random;

    public RandomTickEvent(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.random = random;
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }
    public RandomSource getRandom() { return random; }
    public Block getBlock() { return state.getBlock(); }
}
//?}
