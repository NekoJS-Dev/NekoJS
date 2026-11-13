package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral payload for the block-broken event ({@code BlockEvents.broken}).
 *
 * <p>This class only uses vanilla MC types, so the three NeoForge nodes, fabric and forge all
 * share it. Each loader converts its native event into this class at its own binding point; the
 * NeoForge side does it with {@code bindTransformed} on {@code BlockEvents.FORGE_BRIDGE}.
 *
 * <p>The neutral payload is what makes this workable: exposing the NeoForge event class directly
 * would make the script API change with the MC version (26.x has {@code BreakBlockEvent}, 1.21.1
 * has {@code BlockEvent.BreakEvent}), fabric has no matching class at all, and the public API
 * declarations would need version guards. All three problems go away with this payload.
 *
 * <p>Listeners returning {@code true} cancel the native break through the loader bridge.
 * This payload does not retain the native event or expose loader types.
 *
 * <p>It deliberately has no drop-experience value: 1.21.1's {@code BlockEvent.BreakEvent} has
 * {@code getExpToDrop()}, while 26.x's {@code BreakBlockEvent} removed that API. A neutral
 * payload surfaces this real version difference at the data level; rather than faking 0 on one
 * version, the value is not exposed (use the version-guarded extension surface when you need it).
 */
@Doc("Fired when a block is broken (BlockEvents.broken). Listeners are dispatched by block id.")
@Doc("Return true to cancel the break.")
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
