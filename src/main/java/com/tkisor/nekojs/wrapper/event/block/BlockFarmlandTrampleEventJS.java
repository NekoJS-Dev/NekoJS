package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral payload for the farmland trample event ({@code BlockEvents.farmlandTrample}).
 *
 * <p>The member names follow NeoForge 26.x {@code BlockEvent.FarmlandTrampleEvent}:
 * {@code event.level} / {@code event.pos} / {@code event.state} (the farmland block) /
 * {@code event.entity} / {@code event.fallDistance} (NeoForge's
 * getLevel/getPos/getState/getEntity/getFallDistance). Dispatched by {@code state.getBlock()},
 * the same as {@code dispatchByBlock} on the NeoForge side.
 *
 * <p>Cancellable: when a listener returns {@code true}, the entity does not trample the
 * farmland. The farmland keeps its state and {@code FarmlandBlock#turnToDirt} is not called,
 * which matches the ICancellableEvent semantics of NeoForge FarmlandTrampleEvent.
 *
 * <p>fabric hook: {@code MixinFarmlandBlock} posts at the {@code turnToDirt} call site in
 * {@code FarmlandBlock#fallOn}, so it only fires after the vanilla check has passed
 * (ServerLevel, probability, LivingEntity, mobGriefing or player, volume threshold). NeoForge
 * also fires only after that check passes.
 */
@Doc("Fired when an entity tramples farmland (BlockEvents.farmlandTrample).")
@Doc("Return true to prevent the farmland from turning to dirt.")
@Getter
public class BlockFarmlandTrampleEventJS {

    @Doc("The level the farmland is in.")
    private final Level level;

    @Doc("Position of the farmland block.")
    private final BlockPos pos;

    @Doc("Block state of the farmland block.")
    private final BlockState state;

    @Doc("The entity landing on the farmland.")
    private final Entity entity;

    @Doc("The fall distance of the landing entity.")
    private final float fallDistance;

    public BlockFarmlandTrampleEventJS(Level level, BlockPos pos, BlockState state,
                                       float fallDistance, Entity entity) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.fallDistance = fallDistance;
        this.entity = entity;
    }

    @Doc("The farmland block.")
    public net.minecraft.world.level.block.Block getBlock() {
        return state.getBlock();
    }
}
